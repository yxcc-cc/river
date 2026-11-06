# 河边安全智能预警系统

基于 ESP32 的河道 / 水域安全智能预警系统，打通「**数据采集 → 智能预警 → 处置闭环 → 事后追溯**」全流程。

> 毕业设计项目 · 从硬件固件、AI 视觉、后端服务到前端平台四层全部独立完成。

---

## 系统架构

```
┌─────────────────────┐
│  ESP32-CAM          │  MJPEG 视频流 (HTTP)
│  ESP32 + 超声波      │  水位数据 (HTTP)
│  蜂鸣器报警模块      │  报警控制
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Python 视觉服务     │  YOLOv8n 目标检测 + OpenCV 图像处理
│  (多线程)            │  ROI 裁剪 / 连续帧确认 / 告警冷却
└──────────┬──────────┘
           │ HTTP 上报
           ▼
┌─────────────────────┐
│  Spring Boot 3.5     │  MyBatis-Plus · JWT · RBAC · WebSocket
│  后端服务            │  预警处置 / 设备管理 / 数据统计 / 人脸考勤
└──────────┬──────────┘
           │ STOMP 实时推送
           ▼
┌─────────────────────┐
│  Vue 3 前端平台      │  数据大屏 / 视频监控 / GIS 地图
│                      │  预警处置 / 人员考勤
└─────────────────────┘
```

| 模块 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 后端，57 个 Java 类、8 张数据表 |
| `frontend/` | Vue 3 前端，10 个业务页面 |
| `hardware/` | 4 个 ESP32 固件工程（摄像头推流 / 水位采集 / 蜂鸣器 / CAM 一体机） |
| `database/` | 建表脚本与演示数据 |
| `tools/` | Python 视觉检测服务、数据模拟器 |

---

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 硬件 | ESP32-WROOM-32E、ESP32-CAM、JSN-SR04T 防水超声波、蜂鸣器模块 |
| 视觉 | Python、YOLOv8n、OpenCV、多线程、requests 流式读取 |
| 后端 | Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、JJWT、WebSocket |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、ECharts、高德地图 |
| 存储 | MySQL 8 |

---

## 关键实现

### 1. 自研 MJPEG 流式解码器

ESP32-CAM 输出的 MJPEG 流在 OpenCV 默认的 FFmpeg 后端下经常读不到帧。改为自行处理：

- 用 `requests` 以流式方式读取原始字节流
- 按 JPEG 帧头尾标记（`SOI = FF D8` / `EOI = FF D9`）手动切分出完整帧
- 再交由 `cv2.imdecode` 解码

绕开了 FFmpeg 对嵌入式摄像头流兼容性差的问题。

### 2. 三级降误报机制

河道场景下水面反光、行人路过极易误报，设计三层过滤：

1. **ROI 中心区域裁剪** —— 只检测画面中央的有效水域，排除边缘干扰
2. **连续 3 帧确认** —— 单帧检出不算，需连续多帧稳定命中
3. **10 秒告警冷却** —— 同一事件在冷却期内不重复触发

### 3. 告警副作用异步化

入侵判定后需要同时做三件事：上报后端、截图归档、回调硬件蜂鸣器。全部放到独立线程并发执行，**检出主循环不被阻塞**；多路摄像头按「每设备一线程 + 独立抓帧线程」并行处理。

### 4. 人脸签到双引擎降级

优先使用 `dlib` 人脸编码比对；当运行环境缺少 dlib 或模型不可用时，**自动降级**为 OpenCV Haar 级联检测 + 余弦相似度比对，保证服务不中断。

---

## 快速开始

### 后端

```bash
cd backend
# 先复制配置模板并填入自己的数据库密码与 JWT 密钥
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
./mvnw spring-boot:run
```

默认端口 `8081`。

### 前端

```bash
cd frontend
npm install
npm run dev
```

### 硬件固件

各固件工程位于 `hardware/` 下，使用 Arduino IDE 打开对应的 `.ino` 文件，先复制 `secrets.h.example` 为 `secrets.h` 并填入 WiFi 凭据，再编译烧录。

---

## 配置与安全说明

以下文件含敏感信息，**已加入 `.gitignore`，不会提交到仓库**：

| 文件 | 内容 | 模板 |
| --- | --- | --- |
| `hardware/*/secrets.h` | WiFi SSID 与密码 | `secrets.h.example` |
| `backend/src/main/resources/application-local.yml` | 数据库密码、JWT 签名密钥 | `application-local.yml.example` |

首次运行请参照对应的 `.example` 模板自行创建。

此外，`backend/uploads/` 与 `tools/captures/` 存放告警抓拍与人脸照片，涉及个人隐私，同样不入库。
