/*
 * 河边安全预警系统 - ESP32 水位传感器 + 蜂鸣器报警（一体板）
 * 硬件：ESP32-WROOM-32 + JSN-SR04T 防水超声波 + KY-006 无源蜂鸣器
 *
 * 一块板干三件事：
 *   1. 定时测水位 → 主动 POST 上报后端
 *   2. 定时心跳
 *   3. 轮询后端「有没有报警」→ 有就让蜂鸣器响
 *
 * 接线：
 *   ESP32 5V     → JSN-SR04T VCC      （想避免 5V 电平问题可改接 3V3）
 *   ESP32 GND    → JSN-SR04T GND
 *   ESP32 GPIO16 → JSN-SR04T Trig(模块标 RX)
 *   ESP32 GPIO17 → JSN-SR04T Echo(模块标 TX)
 *
 *   ESP32 GPIO25 → KY-006  S   （信号，PWM）
 *   ESP32 3V3    → KY-006  +   （接 3V3，别接 5V）
 *   ESP32 GND    → KY-006  -   （照丝印，不同批次脚序会翻）
 *
 * 调试：串口 115200
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <string.h>

// ==================== 配置区 ====================
// WiFi 凭据存放在同目录的 secrets.h（不入库）
#include "secrets.h"

// 后端（阿里云公网）—— 换服务器时只改这一行
const char* SERVER_BASE = "http://47.111.116.134:8081";

// 若 secrets.h 里没有配 DEVICE_KEY，就按「后端未开启鉴权」处理
#ifndef DEVICE_KEY
  #define DEVICE_KEY ""
#endif

// ==================== 引脚 ====================
const int TRIG_PIN = 16;
const int ECHO_PIN = 17;
const int BUZZER_PIN = 25;

// 传感器安装高度（传感器到河底的距离，单位 cm）
// ⚠️ 按你实际安装高度改！测出来的「距离」是水面到探头，
//    水位 = MAX_DISTANCE_CM - 距离。设小了水位会被压成 0。
const float MAX_DISTANCE_CM = 300.0;

// ==================== 时间参数 ====================
const unsigned long MEASURE_INTERVAL        = 30000;   // 30 秒测一次并上报
const unsigned long HEARTBEAT_INTERVAL      = 60000;   // 60 秒心跳
const unsigned long ALERT_POLL_INTERVAL     = 10000;   // 10 秒问一次后端有没有报警
const unsigned long CONFIG_REFRESH_INTERVAL = 300000;  // 5 分钟刷一次阈值
const unsigned long BEEP_DURATION           = 3000;    // 每次响 3 秒
const unsigned long BEEP_COOLDOWN           = 15000;   // 两次响之间至少隔 15 秒
const unsigned long HTTP_TIMEOUT_MS         = 8000;

// KY-006 是无源压电蜂鸣器：直流不响，必须给方波
#define BUZZER_PWM_FREQ 2700
#define BUZZER_PWM_RES  8
#define BUZZER_PWM_DUTY 128
#define BUZZER_LEDC_CH  0

// ==================== 全局变量 ====================
String DEVICE_ID;
float currentWaterLevel = 0.0;
float currentDistance = 0.0;
float waterThreshold = 100.0;   // 后端拉不到时的兜底

unsigned long lastUploadTime = 0;
unsigned long lastHeartbeatTime = 0;
unsigned long lastAlertPollTime = 0;
unsigned long lastConfigTime = 0;
unsigned long lastBeepTime = 0;

bool buzzerSounding = false;
unsigned long buzzerOffAt = 0;
bool firstUploadDone = false;

// 已经响过的报警 id：同一条报警只响一次，不然后端一直返回未处理，
// 蜂鸣器就会无限循环响下去
long lastAlertId = -1;

// ==================== 蜂鸣器 ====================
void buzzerInit() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);   // 上电静音
}

void buzzerStart() {
  ledcAttachChannel(BUZZER_PIN, BUZZER_PWM_FREQ, BUZZER_PWM_RES, BUZZER_LEDC_CH);
  ledcWriteChannel(BUZZER_LEDC_CH, BUZZER_PWM_DUTY);
  buzzerSounding = true;
  unsigned long now = millis();
  buzzerOffAt = now + BEEP_DURATION;
  lastBeepTime = now;
  Serial.println("[蜂鸣器] 响");
}

void buzzerStop() {
  ledcDetach(BUZZER_PIN);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);
  buzzerSounding = false;
  Serial.println("[蜂鸣器] 停");
}

void buzzerTick() {
  if (buzzerSounding && (long)(millis() - buzzerOffAt) >= 0) {
    buzzerStop();
  }
}

// 有报警时调用；冷却期内不重复响，避免一直吵
void beepIfAllowed() {
  if (buzzerSounding) return;
  unsigned long now = millis();
  if (lastBeepTime != 0 && now - lastBeepTime < BEEP_COOLDOWN) return;
  buzzerStart();
}

// ==================== 超声波测距 ====================
// 单次测量，返回距离（cm）；失败返回 -1。
// JSN-SR04T 的触发脉宽按手册 ≥10μs，但 3.3V 打 5V 输入属于擦边，给到 20μs。
#define TRIG_PULSE_US 20
#define ECHO_TIMEOUT_US 40000   // 对应约 6.8m，留足余量
#define SAMPLE_COUNT 5          // 一次读取采集几组，取中位数
#define SAMPLE_GAP_MS 70        // 采样间隔，模块要求 ≥60ms
#define MAX_SPREAD_CM 80.0      // 5 次采样的最大跨度，超过就当这次测量无效

float measureDistanceOnce() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(4);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(TRIG_PULSE_US);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, ECHO_TIMEOUT_US);
  if (duration == 0) return -1;

  float distance = duration * 0.034 / 2.0;
  if (distance < 20.0 || distance > 500.0) return -1;   // 模块量程之外，当没测到
  return distance;
}

// 中值滤波：连测 5 次，排序取中间值。
// 超声波对同一平面也会上下抖（多次反射、声波散射），单次读数直接用会跳得很厉害，
// 中位数能把偶发的大跳变（打到别的东西）直接丢掉。
float readWaterLevel() {
  float samples[SAMPLE_COUNT];
  int n = 0;
  String raw = "";

  for (int i = 0; i < SAMPLE_COUNT; i++) {
    float d = measureDistanceOnce();
    if (d > 0) {
      samples[n++] = d;
      raw += String(d, 1);
      raw += " ";
    } else {
      raw += "-- ";
    }
    delay(SAMPLE_GAP_MS);
  }

  if (n == 0) {
    Serial.println("[超声波] 5 次全没回波（检查探头接线/供电，或前方太空）");
    return -1;
  }

  // 冒泡排序（n ≤ 5，够用）
  for (int i = 0; i < n - 1; i++) {
    for (int j = 0; j < n - 1 - i; j++) {
      if (samples[j] > samples[j + 1]) {
        float t = samples[j];
        samples[j] = samples[j + 1];
        samples[j + 1] = t;
      }
    }
  }

  float median = samples[n / 2];
  float spread = samples[n - 1] - samples[0];

  // 跨度太大 = 回波打在了不同物体上，这个中位数也不可信。
  // 这种数据不能上报，否则前端曲线会跳、还会误触发报警。
  if (spread > MAX_SPREAD_CM) {
    Serial.printf("[超声波] 采样[%s]→ 跨度 %.1fcm 超过 %.0f，本次数据作废\n",
                  raw.c_str(), spread, MAX_SPREAD_CM);
    return -1;
  }

  currentDistance = median;
  float waterLevel = MAX_DISTANCE_CM - median;
  if (waterLevel < 0) waterLevel = 0;
  if (waterLevel > MAX_DISTANCE_CM) waterLevel = MAX_DISTANCE_CM;
  currentWaterLevel = waterLevel;

  Serial.printf("[超声波] 采样[%s]→ 中位 %.1fcm（跨度 %.1f）→ 水位 %.1fcm（下限 %.0f / 阈值 %.0f）\n",
                raw.c_str(), median, spread, waterLevel, MAX_DISTANCE_CM, waterThreshold);

  if (spread > 80.0) {
    Serial.println("[超声波] ⚠ 跨度太大，回波可能打在多个物体上（装上支架垂直对水面会稳很多）");
  }
  return waterLevel;
}

// ==================== HTTP 小工具 ====================
bool httpGet(const String& url, String& body) {
  HTTPClient http;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (!http.begin(url)) return false;
  if (strlen(DEVICE_KEY) > 0) http.addHeader("X-Device-Key", DEVICE_KEY);

  int code = http.GET();
  if (code == 200) body = http.getString();
  http.end();
  return code == 200;
}

// 从 {"alertId":366,...} 里抠出整数
long parseLongField(const String& body, const char* field) {
  int idx = body.indexOf(field);
  if (idx < 0) return -1;
  String tail = body.substring(idx + strlen(field));
  tail.trim();
  int end = 0;
  while (end < (int)tail.length()) {
    char c = tail.charAt(end);
    if (c >= '0' && c <= '9') end++;
    else break;
  }
  if (end == 0) return -1;
  return tail.substring(0, end).toInt();
}

// 从 {"waterThreshold":100.0,...} 里抠出数字
float parseFloatField(const String& body, const char* field) {
  int idx = body.indexOf(field);
  if (idx < 0) return -1;
  String tail = body.substring(idx + strlen(field));
  tail.trim();
  int end = 0;
  while (end < (int)tail.length()) {
    char c = tail.charAt(end);
    if ((c >= '0' && c <= '9') || c == '.' || c == '-') end++;
    else break;
  }
  if (end == 0) return -1;
  return tail.substring(0, end).toFloat();
}

// ==================== 拉取阈值 ====================
void fetchConfig() {
  String url = String(SERVER_BASE) + "/api/hardware/config?deviceId=" + DEVICE_ID;
  String body;
  if (!httpGet(url, body)) {
    Serial.println("[配置] 拉取失败，沿用旧阈值");
    return;
  }
  float v = parseFloatField(body, "\"waterThreshold\":");
  if (v > 0) {
    waterThreshold = v;
    Serial.printf("[配置] 阈值更新为 %.1fcm\n", waterThreshold);
  }
}

// ==================== 上报水位 ====================
bool uploadData(float waterLevel) {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);

  String url = String(SERVER_BASE) + "/api/hardware/data/upload";
  if (!http.begin(url)) {
    Serial.println("[上报] HTTP 初始化失败");
    return false;
  }
  http.addHeader("Content-Type", "application/json");
  if (strlen(DEVICE_KEY) > 0) http.addHeader("X-Device-Key", DEVICE_KEY);

  String json = "{";
  json += "\"deviceId\":\"" + DEVICE_ID + "\"";
  json += ",\"waterLevel\":" + String(waterLevel, 2);
  json += ",\"batteryVoltage\":3.70";
  json += "}";

  int code = http.POST(json);
  if (code == 200) {
    Serial.printf("[上报] OK  %s\n", http.getString().c_str());
  } else {
    Serial.printf("[上报] 失败 HTTP %d\n", code);
  }
  http.end();
  return code == 200;
}

// ==================== 心跳 ====================
void sendHeartbeat() {
  String url = String(SERVER_BASE) + "/api/hardware/heartbeat?deviceId=" + DEVICE_ID;
  String body;
  Serial.printf("[心跳] %s\n", httpGet(url, body) ? "OK" : "失败");
}

// ==================== 问后端要不要报警 ====================
// 蜂鸣器只负责一件事：有人靠近河边时劝离。
//
// 所以只有 INTRUSION_ALARM（摄像头看到人）才响；
// WATER_ALARM 只上报给后端，不响 —— 岸边没人，响了也没人听。
//
// 另外必须记住上次响过的报警 id：后端会一直返回「这条还没处理」，
// 不记住的话蜂鸣器会十几秒响一次、响到有人去后台点处理为止。
void pollAlert() {
  String url = String(SERVER_BASE) + "/api/hardware/alert/check?deviceId=" + DEVICE_ID;
  String body;
  if (!httpGet(url, body)) return;

  if (body.indexOf("\"alarm\":true") < 0) return;

  long aid = parseLongField(body, "\"alertId\":");
  if (aid > 0 && aid == lastAlertId) {
    return;   // 这条已经处理过了，安静
  }

  // 有人靠近 → 劝离；水位超标 → 只记录不响
  bool isIntrusion = body.indexOf("INTRUSION_ALARM") >= 0;

  if (aid > 0) {
    lastAlertId = aid;
  }

  if (isIntrusion) {
    Serial.printf("[报警] 入侵报警 id=%ld，蜂鸣器劝离\n", aid);
    beepIfAllowed();
  } else {
    Serial.printf("[报警] 水位报警 id=%ld，只上报不响铃\n", aid);
  }
}

// ==================== 连接 WiFi ====================
void connectWiFi() {
  Serial.printf("[WiFi] 正在连接 %s ...\n", WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 30) {
    delay(500);
    Serial.print(".");
    retries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n[WiFi] 已连接! IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("\n[WiFi] 连接失败，稍后重试");
  }
}

// ==================== 主程序 ====================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n================================");
  Serial.println("  河边安全预警系统 - 水位 + 报警");
  Serial.println("================================");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);
  buzzerInit();

  connectWiFi();

  DEVICE_ID = WiFi.macAddress();
  DEVICE_ID.replace(":", "");
  Serial.printf("[设备] ID: %s\n", DEVICE_ID.c_str());
  Serial.printf("[设备] 服务器: %s\n", SERVER_BASE);
  Serial.printf("[设备] 鉴权: %s\n", strlen(DEVICE_KEY) > 0 ? "已配置 X-Device-Key" : "未配置");

  if (WiFi.status() == WL_CONNECTED) {
    fetchConfig();
    lastConfigTime = millis();
  }
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] 掉线，重连中...");
    connectWiFi();
    delay(5000);
    return;
  }

  buzzerTick();

  unsigned long now = millis();

  // 定时测量并上报（开机后立刻先报一次）
  if (!firstUploadDone || now - lastUploadTime >= MEASURE_INTERVAL) {
    firstUploadDone = true;
    lastUploadTime = now;

    float waterLevel = readWaterLevel();
    if (waterLevel >= 0) {
      uploadData(waterLevel);
      // 水位超标只上报，蜂鸣器不响。
      // 蜂鸣器的职责是「看到人靠近就劝离」，岸边没人时响了没人听，
      // 反而会让附近居民投诉扰民。
      if (waterLevel > waterThreshold) {
        Serial.println("[水位] 超阈值，已上报（不触发蜂鸣器）");
      }
    }
  }

  // 定时心跳
  if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
    lastHeartbeatTime = now;
    sendHeartbeat();
  }

  // 定时问后端有没有报警
  if (now - lastAlertPollTime >= ALERT_POLL_INTERVAL) {
    lastAlertPollTime = now;
    pollAlert();
  }

  // 定时刷新阈值
  if (now - lastConfigTime >= CONFIG_REFRESH_INTERVAL) {
    lastConfigTime = now;
    fetchConfig();
  }

  delay(200);
}
