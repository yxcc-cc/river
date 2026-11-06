/*
 * 河边安全预警系统 - ESP32-CAM 超声波水位传感器
 * 硬件：ESP32-CAM + JSN-SR04T 防水超声波模块
 * 功能：用摄像头空闲引脚(GPIO1/GPIO3)接传感器，定时测距上传
 *
 * ⚠️ 本固件是「只有一块 ESP32-CAM」时的折中方案 —— 让摄像头板兼职测水位。
 *    现在已有独立的 ESP32 开发板（见 esp32_water_sensor），
 *    正常部署不再需要这份：CAM 专职跑视频流，水位交给独立板。
 *    保留仅作回顾/备用（例如再买一块 CAM 时可用）。
 *
 * ⚠️ 注意：此固件会占用串口引脚，烧录后无法用Serial监视器调试
 *         调试方式：查看后端日志 / 前端数据页面
 *
 * 接线：
 *   ESP32-CAM 5V    → JSN-SR04T VCC
 *   ESP32-CAM GND   → JSN-SR04T GND
 *   ESP32-CAM U0T(GPIO1) → JSN-SR04T Trig(标RX的那根线)
 *   ESP32-CAM U0R(GPIO3) → JSN-SR04T Echo(标TX的那根线)
 *
 * JSN-SR04T模块上通常标的是 RX 和 TX：
 *   RX = Trig（接GPIO1）
 *   TX = Echo（接GPIO3）
 */

#include <WiFi.h>
#include <HTTPClient.h>

// ==================== 配置区 ====================
// WiFi 凭据存放在同目录的 secrets.h（不入库）
// 首次使用请复制 secrets.h.example 为 secrets.h
#include "secrets.h"

// 后端服务器（阿里云公网）
const char* SERVER_URL = "http://47.111.116.134:8081/api/hardware/data/upload";

// 超声波引脚（摄像头空闲引脚）
const int TRIG_PIN = 1;   // U0T - 串口TX
const int ECHO_PIN = 3;   // U0R - 串口RX

// 传感器安装高度（传感器到河底的距离，单位cm）
const float MAX_DISTANCE_CM = 200.0;

// 上传间隔
const unsigned long UPLOAD_INTERVAL = 30000;   // 30秒
const unsigned long HEARTBEAT_INTERVAL = 60000; // 60秒

// ==================== 全局变量 ====================
String DEVICE_ID;
unsigned long lastUploadTime = 0;
unsigned long lastHeartbeatTime = 0;

// ==================== GPIO直接操作（绕过Serial）====================
// IO_MUX寄存器地址
#define IO_MUX_GPIO1  (*(volatile uint32_t*)0x3FF49044)
#define IO_MUX_GPIO3  (*(volatile uint32_t*)0x3FF49014)
// GPIO寄存器
#define GPIO_IN_REG   (*(volatile uint32_t*)0x3FF44038)
#define GPIO_OUT_W1TS (*(volatile uint32_t*)0x3FF44008)
#define GPIO_OUT_W1TC (*(volatile uint32_t*)0x3FF4400C)
#define GPIO_ENABLE   (*(volatile uint32_t*)0x3FF44020)

void initSensorPins() {
  // 将GPIO1和GPIO3从UART模式切换到GPIO模式
  // bit[1:0] = 00 表示GPIO功能（而非UART）
  IO_MUX_GPIO1 = (IO_MUX_GPIO1 & ~0x13) | 0x02;  // GPIO模式 + 上拉
  IO_MUX_GPIO3 = (IO_MUX_GPIO3 & ~0x13) | 0x02;  // GPIO模式 + 上拉

  // GPIO1 = 输出（Trig）
  GPIO_ENABLE |= (1 << 1);
  // GPIO3 = 输入（Echo），清除对应bit
  GPIO_ENABLE &= ~(1 << 3);
}

void trigWrite(bool high) {
  if (high) GPIO_OUT_W1TS = (1 << 1);
  else      GPIO_OUT_W1TC = (1 << 1);
}

bool echoRead() {
  return (GPIO_IN_REG >> 3) & 1;
}

// ==================== 超声波测距 ====================
float readWaterLevel() {
  // 发送10μs触发脉冲
  trigWrite(false);
  delayMicroseconds(2);
  trigWrite(true);
  delayMicroseconds(10);
  trigWrite(false);

  // 等待Echo变高，然后计时
  unsigned long timeout = micros();
  while (!echoRead()) {
    if (micros() - timeout > 10000) return -1;  // 10ms超时
  }

  unsigned long start = micros();
  while (echoRead()) {
    if (micros() - start > 30000) return -1;  // 30ms超时
  }
  unsigned long duration = micros() - start;

  // 距离 = 时间(μs) × 声速(0.034cm/μs) / 2
  float distance = duration * 0.034 / 2.0;
  float waterLevel = MAX_DISTANCE_CM - distance;

  if (waterLevel < 0) waterLevel = 0;
  if (waterLevel > MAX_DISTANCE_CM) waterLevel = MAX_DISTANCE_CM;

  return waterLevel;
}

// ==================== 电池电压 ====================
float readBattery() {
  int raw = analogRead(34);
  float voltage = raw * 3.3 / 4095.0 * 2;
  if (voltage < 2.0 || voltage > 5.0) voltage = 3.7;
  return voltage;
}

// ==================== 上传数据 ====================
void uploadData(float waterLevel) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  http.begin(SERVER_URL);
  http.addHeader("Content-Type", "application/json");

  String json = "{";
  json += "\"deviceId\":\"" + DEVICE_ID + "\"";
  json += ",\"waterLevel\":" + String(waterLevel, 2);
  json += ",\"batteryVoltage\":" + String(readBattery(), 2);
  json += "}";

  int code = http.POST(json);
  http.end();
}

// ==================== 心跳 ====================
void sendHeartbeat() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SERVER_URL).replace("/data/upload", "/heartbeat");
  url += "?deviceId=" + DEVICE_ID;

  http.begin(url);
  http.GET();
  http.end();
}

// ==================== WiFi ====================
void connectWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.setSleep(false);

  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 30) {
    delay(500);
    retries++;
  }
}

// ==================== 主程序 ====================
void setup() {
  // 不初始化Serial！引脚留给传感器

  // MAC地址作为设备ID
  DEVICE_ID = WiFi.macAddress();
  DEVICE_ID.replace(":", "");

  // 初始化传感器引脚
  initSensorPins();

  // 连接WiFi
  connectWiFi();
}

void loop() {
  // 断线重连
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
    delay(5000);
    return;
  }

  unsigned long now = millis();

  // 定时上传水位
  if (now - lastUploadTime >= UPLOAD_INTERVAL) {
    lastUploadTime = now;
    float waterLevel = readWaterLevel();
    if (waterLevel >= 0) {
      uploadData(waterLevel);
    }
  }

  // 定时心跳
  if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
    lastHeartbeatTime = now;
    sendHeartbeat();
  }

  delay(1000);
}
