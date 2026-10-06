/*
 * 河边安全预警系统 - ESP32 超声波水位传感器
 * 硬件：ESP32 + JSN-SR04T 防水超声波模块
 * 功能：定时读取水位 → 开HTTP接口供后端拉取
 *
 * 接线：
 *   ESP32 5V    → JSN-SR04T VCC
 *   ESP32 GND   → JSN-SR04T GND
 *   ESP32 GPIO16 → JSN-SR04T Trig(标RX)
 *   ESP32 GPIO17 → JSN-SR04T Echo(标TX)
 *
 * HTTP接口：
 *   GET /water  → 返回当前水位JSON
 *   GET /status → 查看状态
 */

#include <WiFi.h>
#include <WebServer.h>

// ==================== 配置区 ====================
// WiFi 凭据存放在同目录的 secrets.h（不入库）
// 首次使用请复制 secrets.h.example 为 secrets.h
#include "secrets.h"

// 设备ID（自动使用MAC地址，每台ESP32唯一）
String DEVICE_ID;

// 超声波引脚
const int TRIG_PIN = 16;
const int ECHO_PIN = 17;

// 传感器安装高度（传感器到水面的最大距离，单位 cm）
const float MAX_DISTANCE_CM = 200.0;

// 测量间隔（毫秒）
const unsigned long MEASURE_INTERVAL = 5000;  // 5秒测一次

// ==================== 全局变量 ====================
WebServer server(80);
float currentWaterLevel = 0.0;
float currentDistance = 0.0;
unsigned long lastMeasureTime = 0;

// ==================== 超声波测距 ====================
float readWaterLevel() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  if (duration == 0) {
    Serial.println("[!] 超声波测量超时");
    return -1;
  }

  float distance = duration * 0.034 / 2.0;
  float waterLevel = MAX_DISTANCE_CM - distance;
  if (waterLevel < 0) waterLevel = 0;
  if (waterLevel > MAX_DISTANCE_CM) waterLevel = MAX_DISTANCE_CM;

  currentDistance = distance;
  currentWaterLevel = waterLevel;

  Serial.printf("[超声波] 距离: %.1fcm → 水位: %.1fcm\n", distance, waterLevel);
  return waterLevel;
}

// ==================== HTTP 接口 ====================
void handleWater() {
  String json = "{";
  json += "\"deviceId\":\"" + DEVICE_ID + "\"";
  json += ",\"waterLevel\":" + String(currentWaterLevel, 2);
  json += ",\"distance\":" + String(currentDistance, 2);
  json += ",\"batteryVoltage\":3.70";
  json += "}";
  server.send(200, "application/json", json);
}

void handleStatus() {
  String json = "{";
  json += "\"deviceId\":\"" + DEVICE_ID + "\"";
  json += ",\"ip\":\"" + WiFi.localIP().toString() + "\"";
  json += ",\"waterLevel\":" + String(currentWaterLevel, 2);
  json += ",\"uptime\":" + String(millis() / 1000);
  json += "}";
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
    Serial.println("\n[WiFi] 连接失败，将重试...");
  }
}

// ==================== 主程序 ====================
void setup() {
  Serial.begin(115200);
  Serial.println("\n================================");
  Serial.println("  河边安全预警系统 - 水位传感器");
  Serial.println("================================");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  connectWiFi();

  DEVICE_ID = WiFi.macAddress();
  DEVICE_ID.replace(":", "");
  Serial.printf("[设备] ID: %s\n", DEVICE_ID.c_str());

  // 启动HTTP服务
  server.on("/water", HTTP_GET, handleWater);
  server.on("/status", HTTP_GET, handleStatus);
  server.begin();
  Serial.println("[HTTP] 服务器启动，端口: 80");
  Serial.printf("[HTTP] 水位接口: http://%s/water\n", WiFi.localIP().toString().c_str());
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
    delay(5000);
    return;
  }

  server.handleClient();

  unsigned long now = millis();
  if (now - lastMeasureTime >= MEASURE_INTERVAL) {
    lastMeasureTime = now;
    readWaterLevel();
  }

  delay(10);
}
