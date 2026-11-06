/*
 * 河边安全预警系统 - ESP32 蜂鸣器报警模块（无源蜂鸣器版）
 * 硬件：ESP32-WROOM-32 + 无源蜂鸣器模块
 * 功能：接收HTTP请求，用PWM驱动蜂鸣器发声
 *
 * 无源蜂鸣器 = 内部没有振荡电路，必须给PWM信号才响
 *
 * 接线：
 *   ESP32 GPIO25 → 蜂鸣器信号线(S)
 *   ESP32 5V     → 蜂鸣器 VCC
 *   ESP32 GND    → 蜂鸣器 GND
 *
 * HTTP接口：
 *   POST /buzzer   → {"action":"on"} 响 / {"action":"off"} 停
 *   POST /buzzer   → {"action":"on","duration":3000} 响3秒后自动停
 *   GET  /status   → 查看状态
 */

#include <WiFi.h>
#include <WebServer.h>

// ==================== 配置区 ====================
// WiFi 凭据存放在同目录的 secrets.h（不入库）
// 首次使用请复制 secrets.h.example 为 secrets.h
#include "secrets.h"

// 蜂鸣器引脚
const int BUZZER_PIN = 25;

// PWM配置（无源蜂鸣器需要PWM才能响）
const int PWM_FREQ = 2700;      // 频率 2700Hz（无源蜂鸣器典型共振频率）
const int PWM_RESOLUTION = 8;   // 8位分辨率 (0-255)
const int PWM_DUTY = 128;       // 占空比 50%（128/255），声音最响

// 默认响铃时长（毫秒），0表示不自动停
const unsigned long DEFAULT_DURATION = 3000;

// ==================== 全局变量 ====================
WebServer server(80);
bool buzzerOn = false;
unsigned long buzzerOffTime = 0;

// ==================== 蜂鸣器控制（PWM驱动）====================
// KY-006 是无源压电蜂鸣器：直流不响，必须给方波。
void turnBuzzerOn(unsigned long duration = 0) {
  ledcAttach(BUZZER_PIN, PWM_FREQ, PWM_RESOLUTION);  // 重复调用是幂等的
  ledcWrite(BUZZER_PIN, PWM_DUTY);
  buzzerOn = true;
  if (duration > 0) {
    buzzerOffTime = millis() + duration;
    Serial.printf("[蜂鸣器] 响 %lu ms\n", duration);
  } else {
    buzzerOffTime = 0;
    Serial.println("[蜂鸣器] 响（持续）");
  }
}

void turnBuzzerOff() {
  ledcWrite(BUZZER_PIN, 0);
  ledcDetach(BUZZER_PIN);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);   // 三极管彻底关断，不再给蜂鸣器供电
  buzzerOn = false;
  buzzerOffTime = 0;
  Serial.println("[蜂鸣器] 停");
}

// ==================== HTTP处理 ====================
void handleBuzzer() {
  if (server.method() != HTTP_POST) {
    server.send(405, "application/json", "{\"error\":\"POST only\"}");
    return;
  }

  String body = server.arg("plain");
  Serial.printf("[HTTP] 收到: %s\n", body.c_str());

  bool turnOn = body.indexOf("\"on\"") >= 0;
  bool turnOff = body.indexOf("\"off\"") >= 0;

  unsigned long duration = DEFAULT_DURATION;
  int durIdx = body.indexOf("\"duration\":");
  if (durIdx >= 0) {
    String durStr = body.substring(durIdx + 11);
    durStr.trim();
    int endIdx = durStr.indexOf('}');
    if (endIdx < 0) endIdx = durStr.indexOf(',');
    if (endIdx >= 0) durStr = durStr.substring(0, endIdx);
    durStr.trim();
    duration = durStr.toInt();
  }

  if (turnOn) {
    turnBuzzerOn(duration);
    server.send(200, "application/json", "{\"status\":\"on\"}");
  } else if (turnOff) {
    turnBuzzerOff();
    server.send(200, "application/json", "{\"status\":\"off\"}");
  } else {
    server.send(400, "application/json", "{\"error\":\"invalid action\"}");
  }
}

void handleStatus() {
  String json = "{\"buzzer\":";
  json += buzzerOn ? "\"on\"" : "\"off\"";
  json += ",\"ip\":\"";
  json += WiFi.localIP().toString();
  json += "\"}";
  server.send(200, "application/json", json);
}

// ==================== 连接WiFi ====================
void connectWiFi() {
  Serial.printf("[WiFi] 正在连接 %s ...\n", WIFI_SSID);
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
    Serial.println("\n[WiFi] 连接失败！");
  }
}

// ==================== 主程序 ====================
void setup() {
  Serial.begin(115200);
  Serial.println("\n================================");
  Serial.println("  河边安全预警系统 - 蜂鸣器模块");
  Serial.println("  （无源蜂鸣器 PWM 驱动版）");
  Serial.println("================================");

  // 初始化：先保证上电静音
  // KY-006 模块上的 PNP 三极管是「低电平导通」，S 保持高电平才是真正的关断
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);

  Serial.printf("[蜂鸣器] GPIO%d, PWM %dHz（KY-006 无源压电）\n", BUZZER_PIN, PWM_FREQ);

  connectWiFi();

  server.on("/buzzer", HTTP_POST, handleBuzzer);
  server.on("/status", HTTP_GET, handleStatus);
  server.begin();
  Serial.println("[HTTP] 服务器启动，端口: 80");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[*] WiFi断开，重连中...");
    connectWiFi();
    delay(5000);
    return;
  }

  server.handleClient();

  if (buzzerOn && buzzerOffTime > 0 && millis() >= buzzerOffTime) {
    turnBuzzerOff();
  }

  delay(10);
}
