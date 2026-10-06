/*
 * 河边安全预警系统 - ESP32-CAM 视频流 + 蜂鸣器报警
 * 硬件：ESP32-CAM + OV3660 摄像头 + 有源蜂鸣器模块
 * 功能：提供MJPEG视频流 + 入侵报警蜂鸣器控制
 *
 * 接线（烧录模式）：
 *   USB转TTL TX  → ESP32-CAM U0R (GPIO3)
 *   USB转TTL RX  → ESP32-CAM U0T (GPIO1)
 *   USB转TTL GND → ESP32-CAM GND
 *   USB转TTL 5V  → ESP32-CAM 5V
 *   ESP32-CAM GPIO0 → GND（仅烧录时，烧完断开）
 *
 * 接线（运行模式）：
 *   只需要 USB 供电即可（GPIO0 断开 GND）
 *
 * 蜂鸣器接线：
 *   ESP32-CAM GPIO14 → 蜂鸣器信号线(IVO/S)
 *   ESP32-CAM 5V     → 蜂鸣器 VCC
 *   ESP32-CAM GND    → 蜂鸣器 GND
 *
 * HTTP接口：
 *   GET  /stream   → MJPEG视频流
 *   POST /buzzer   → 蜂鸣器控制 {"action":"on"} / {"action":"off"}
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"

// ==================== 配置区 ====================
// WiFi 配置
// 凭据存放在同目录的 secrets.h 中，该文件不会提交到版本控制。
// 首次使用：复制 secrets.h.example 为 secrets.h，填入你的 WiFi 名称与密码。
#include "secrets.h"

// 视频流端口
const int STREAM_PORT = 8082;

// ==================== ESP32-CAM 引脚定义 ====================
// AI-Thinker ESP32-CAM 模块
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// 闪光灯
#define LED_GPIO_NUM       4

// 蜂鸣器
#define BUZZER_GPIO_NUM   14

// ==================== HTTP服务器 ====================
httpd_handle_t stream_httpd = NULL;

// MJPEG流处理
static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t *fb = NULL;
  esp_err_t res = ESP_OK;
  char part_buf[64];

  // 设置响应头
  res = httpd_resp_set_type(req, "multipart/x-mixed-replace;boundary=frame");
  if (res != ESP_OK) return res;

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("[!] 摄像头捕获失败");
      res = ESP_FAIL;
      break;
    }

    // 发送JPEG帧
    size_t hlen = snprintf(part_buf, 64,
      "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n", fb->len);

    res = httpd_resp_send_chunk(req, "--frame\r\n", 9);
    if (res == ESP_OK)
      res = httpd_resp_send_chunk(req, part_buf, hlen);
    if (res == ESP_OK)
      res = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
    if (res == ESP_OK)
      res = httpd_resp_send_chunk(req, "\r\n", 2);

    esp_camera_fb_return(fb);

    if (res != ESP_OK) {
      Serial.println("[*] 客户端断开");
      break;
    }
  }

  return res;
}

// 蜂鸣器控制处理
static esp_err_t buzzer_handler(httpd_req_t *req) {
  char buf[128];
  int ret = httpd_req_recv(req, buf, sizeof(buf) - 1);
  if (ret <= 0) {
    httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "empty body");
    return ESP_FAIL;
  }
  buf[ret] = '\0';

  // 解析 {"action":"on"} 或 {"action":"off"}
  bool turnOn = strstr(buf, "\"on\"") != NULL;
  bool turnOff = strstr(buf, "\"off\"") != NULL;

  if (turnOn) {
    digitalWrite(BUZZER_GPIO_NUM, HIGH);
    Serial.println("[蜂鸣器] 响");
  } else if (turnOff) {
    digitalWrite(BUZZER_GPIO_NUM, LOW);
    Serial.println("[蜂鸣器] 停");
  }

  httpd_resp_set_type(req, "application/json");
  httpd_resp_send(req, "{\"status\":\"ok\"}", 14);
  return ESP_OK;
}

// 首页
static esp_err_t index_handler(httpd_req_t *req) {
  const char html[] = "<html><head><title>ESP32-CAM Stream</title></head>"
    "<body><h1>河边安全预警系统 - 摄像头</h1>"
    "<img src=\"/stream\" style=\"width:100%;max-width:800px;\">"
    "</body></html>";
  httpd_resp_set_type(req, "text/html");
  return httpd_resp_send(req, html, strlen(html));
}

// ==================== 初始化摄像头 ====================
void initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_LATEST;
  config.fb_location = CAMERA_FB_IN_PSRAM;

  // 根据PSRAM设置分辨率
  if (psramFound()) {
    config.frame_size = FRAMESIZE_VGA;  // 640x480
    config.jpeg_quality = 12;
    config.fb_count = 2;
    Serial.println("[摄像头] PSRAM已找到，使用VGA分辨率");
  } else {
    config.frame_size = FRAMESIZE_SVGA;  // 800x600
    config.jpeg_quality = 16;
    config.fb_count = 1;
    Serial.println("[摄像头] 未找到PSRAM，使用SVGA分辨率");
  }

  // 初始化摄像头
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[!] 摄像头初始化失败: 0x%x\n", err);
    return;
  }

  // 获取传感器并调整设置
  sensor_t *s = esp_camera_sensor_get();
  if (s) {
    s->set_brightness(s, 1);     // 亮度+1
    s->set_contrast(s, 1);       // 对比度+1
    s->set_saturation(s, 0);     // 饱和度默认
    s->set_whitebal(s, 1);       // 自动白平衡
    s->set_awb_gain(s, 1);       // AWB增益
    s->set_wb_mode(s, 0);        // 自动模式
    s->set_exposure_ctrl(s, 1);  // 自动曝光
    s->set_aec2(s, 1);           // AEC DSP
    s->set_gain_ctrl(s, 1);      // 自动增益
    s->set_agc_gain(s, 0);       // 增益0
    s->set_gainceiling(s, (gainceiling_t)6);  // 增益上限
    s->set_bpc(s, 1);            // 坏点补偿
    s->set_wpc(s, 1);            // 白点补偿
    s->set_lenc(s, 1);           // 镜头校正
    s->set_hmirror(s, 0);        // 水平镜像
    s->set_vflip(s, 0);          // 垂直翻转
    s->set_dcw(s, 1);            // DCW
    s->set_colorbar(s, 0);       // 关闭测试条
  }

  Serial.println("[摄像头] 初始化完成");
}

// ==================== 启动HTTP服务器 ====================
void startServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = STREAM_PORT;
  config.ctrl_port = STREAM_PORT + 1;

  // 首页
  httpd_uri_t index_uri = {
    .uri       = "/",
    .method    = HTTP_GET,
    .handler   = index_handler,
    .user_ctx  = NULL
  };

  // 视频流
  httpd_uri_t stream_uri = {
    .uri       = "/stream",
    .method    = HTTP_GET,
    .handler   = stream_handler,
    .user_ctx  = NULL
  };

  // 蜂鸣器控制
  httpd_uri_t buzzer_uri = {
    .uri       = "/buzzer",
    .method    = HTTP_POST,
    .handler   = buzzer_handler,
    .user_ctx  = NULL
  };

  if (httpd_start(&stream_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &index_uri);
    httpd_register_uri_handler(stream_httpd, &stream_uri);
    httpd_register_uri_handler(stream_httpd, &buzzer_uri);
    Serial.printf("[HTTP] 服务器启动，端口: %d\n", STREAM_PORT);
  }
}

// ==================== 连接WiFi ====================
void connectWiFi() {
  Serial.printf("[WiFi] 正在连接 %s ...\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.setSleep(false);  // 关闭WiFi休眠，减少延迟

  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 30) {
    delay(500);
    Serial.print(".");
    retries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n[WiFi] 已连接!\n");
    Serial.printf("[WiFi] IP地址: %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("[视频流] http://%s:%d/stream\n",
      WiFi.localIP().toString().c_str(), STREAM_PORT);
  } else {
    Serial.println("\n[WiFi] 连接失败！");
  }
}

// ==================== 主程序 ====================
void setup() {
  Serial.begin(115200);
  Serial.println("\n================================");
  Serial.println("  河边安全预警系统 - ESP32-CAM");
  Serial.println("================================");

  // 初始化摄像头
  initCamera();

  // 连接WiFi
  connectWiFi();

  // 启动HTTP服务器
  if (WiFi.status() == WL_CONNECTED) {
    startServer();
  }

  // 关闭闪光灯
  pinMode(LED_GPIO_NUM, OUTPUT);
  digitalWrite(LED_GPIO_NUM, LOW);

  // 初始化蜂鸣器（默认关闭）
  pinMode(BUZZER_GPIO_NUM, OUTPUT);
  digitalWrite(BUZZER_GPIO_NUM, LOW);
}

void loop() {
  // WiFi 断线重连
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[*] WiFi断开，重连中...");
    connectWiFi();
    if (WiFi.status() == WL_CONNECTED) {
      startServer();
    }
    delay(5000);
  }

  delay(1000);
}
