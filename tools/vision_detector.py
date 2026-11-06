"""
河边安全预警系统 - AI视觉入侵检测（性能优化版）

架构改动（相对旧版）：
   旧版：抓帧 → 【主循环里推理 + 画框 + 输出】，推理卡住，画面就卡住
   新版：抓帧线程 → 推理线程（低分辨率、限频） → 渲染线程（全速、只画框）

   三条流水线互不等待：
     - 抓帧线程只管 cap.read()
     - 推理线程每 INFER_MIN_INTERVAL 秒算一次，结果放进共享变量
     - 渲染线程以原始帧率输出画面，用的是最近一次检测结果

   效果：推理慢不会拖累画面；CPU 上也能顺滑，装了 CUDA 版 torch 会自动启用 GPU。

其他：
   - 推理输入降到 INFER_SIZE（320），比 640 快约 4 倍
   - 检测结果超过 DET_MAX_AGE 秒没更新就不画框（避免框停在人已经走了的地方）
   - 多帧确认、ROI、报警冷却、截图上传逻辑保持原样
"""
import cv2
import requests
import time
import sys
import os
import threading
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from ultralytics import YOLO

try:
    import torch
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False

# ==================== 配置 ====================
# 后端地址：后端已经部署在阿里云，不是本地了
BASE_URL = "http://47.111.116.134:8081"
# 本地调试后端时用这行
# BASE_URL = "http://localhost:8081"
# 设备 ID 必须和现场那块 ESP32 板一致！
# 一个监测站（摄像头 + 水位探头 + 蜂鸣器）在系统里就是一个设备 ID，
# 这样摄像头看到人产生的报警，ESP32 板轮询时才能取到，蜂鸣器才会响。
# 2462ABEFC95C = 护池河公园这块 ESP32 的 MAC
DEVICE_ID = "2462ABEFC95C"
CAMERA_SOURCE = "http://192.168.31.77:8082/stream"  # ESP32-CAM MJPEG流地址
# CAMERA_SOURCE = 1               # 或使用USB摄像头（取消注释这行）

CONFIDENCE_THRESHOLD = 0.5      # 置信度阈值
ALERT_COOLDOWN = 5              # 预警冷却时间（秒）
MODEL_NAME = "yolov8n.pt"
STREAM_PORT = 8083              # AI处理后的视频流端口

# —— 性能参数 ——
INFER_SIZE = 320                # 推理输入边长（640 → 320，快约4倍）
INFER_MIN_INTERVAL = 0.05       # 两次推理之间至少间隔（秒），防止占满CPU
DET_MAX_AGE = 0.6               # 检测结果的有效期（秒），过期就不画框
RENDER_INTERVAL = 0.02          # 渲染节流（≈50fps 上限，实际受摄像头帧率限制）

# 多帧确认：连续检测到多少帧才触发报警
CONFIRM_FRAMES = 3

# 检测区域（ROI）：占画面的比例 [x1%, y1%, x2%, y2%]
ROI_X1 = 0.05
ROI_Y1 = 0.0
ROI_X2 = 0.95
ROI_Y2 = 1.0

PERSON_CLASS_ID = 0

# ==================== 全局状态 ====================
# 原始帧（抓帧线程写，其他线程读）
raw_frame = None
raw_frame_lock = threading.Lock()

# 渲染后的帧（渲染线程写，MJPEG 服务器读）
latest_frame = None
frame_lock = threading.Lock()

# 帧号：渲染线程每出新帧 +1。
# MJPEG 服务只在帧号变化时才重新编码 —— 否则 30fps 的发送节奏会反复编码同一张图，
# 白烧 CPU，还让画面延迟堆积
frame_id = 0
frame_id_lock = threading.Lock()

# 检测结果：[(x1, y1, x2, y2, conf), ...]
detections = []
detections_lock = threading.Lock()
last_detection_ts = 0.0

# 统计
stats = {
    "fps": 0,           # 显示帧率
    "cam_fps": 0,       # 摄像头实际帧率
    "infer_fps": 0,     # 推理帧率
    "infer_ms": 0,      # 单次推理耗时
    "device": "-",      # 推理设备
    "alert_count": 0,
    "person_count": 0,
    "confirmed": False,
}


# ==================== 抓帧线程 ====================
def grab_frames(source):
    """持续抓取最新帧；掉线自动重连"""
    global raw_frame, stats
    cap = cv2.VideoCapture(source)
    if isinstance(source, int):
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    if not cap.isOpened():
        print("[!] 无法打开视频源")
        print("[!] 如果使用ESP32-CAM，请确保它已连接WiFi并运行")
        os._exit(1)

    # 尽量减小缓冲，降低延迟（部分后端不支持，忽略即可）
    try:
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
    except Exception:
        pass

    print("[✓] 视频源已打开")

    frame_count = 0
    fps_timer = time.time()
    fail_count = 0

    while True:
        ret, f = cap.read()
        if ret:
            fail_count = 0
            with raw_frame_lock:
                raw_frame = f
            frame_count += 1
            if time.time() - fps_timer >= 1.0:
                stats["cam_fps"] = frame_count
                frame_count = 0
                fps_timer = time.time()
        else:
            fail_count += 1
            if fail_count > 50:
                # 连续读不到，重连
                print("[!] 视频源中断，正在重连...")
                cap.release()
                time.sleep(2)
                cap = cv2.VideoCapture(source)
                if isinstance(source, int):
                    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
                    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
                fail_count = 0
            time.sleep(0.01)


# ==================== 推理线程 ====================
def detect_worker(model, device):
    """独立线程做检测：低分辨率、限频，结果写进共享变量"""
    global detections, last_detection_ts, stats

    last_alert_time = 0
    alert_count = 0
    consecutive_detect = 0
    infer_count = 0
    infer_timer = time.time()

    print("[*] 推理线程已启动")

    last_seen_frame = None
    while True:
        with raw_frame_lock:
            if raw_frame is None:
                time.sleep(0.01)
                continue
            frame = raw_frame

        # 摄像头只给 9~16fps，而线程跑得更快；同一帧重复推理是纯浪费 GPU
        if frame is last_seen_frame:
            time.sleep(0.005)
            continue
        last_seen_frame = frame

        h, w = frame.shape[:2]
        roi_x1, roi_y1, roi_x2, roi_y2 = get_roi_coords(h, w)
        roi_frame = frame[roi_y1:roi_y2, roi_x1:roi_x2]

        if roi_frame.size == 0:
            time.sleep(0.05)
            continue

        t0 = time.time()
        try:
            results = model(roi_frame,
                            conf=CONFIDENCE_THRESHOLD,
                            classes=[PERSON_CLASS_ID],
                            imgsz=INFER_SIZE,
                            device=device,
                            verbose=False)
        except Exception as e:
            print(f"[!] 推理出错: {e}")
            time.sleep(0.5)
            continue
        infer_ms = (time.time() - t0) * 1000

        boxes = []
        for result in results:
            for box in result.boxes:
                if int(box.cls[0]) != PERSON_CLASS_ID:
                    continue
                bx1, by1, bx2, by2 = map(int, box.xyxy[0])
                # ROI 裁剪坐标 → 原图坐标
                boxes.append((bx1 + roi_x1, by1 + roi_y1,
                              bx2 + roi_x1, by2 + roi_y1,
                              float(box.conf[0])))

        now_ts = time.time()
        with detections_lock:
            detections = boxes
            last_detection_ts = now_ts

        person_count = len(boxes)

        # 多帧确认
        if person_count > 0:
            consecutive_detect += 1
        else:
            consecutive_detect = 0
        confirmed = consecutive_detect >= CONFIRM_FRAMES

        # 统计
        infer_count += 1
        if now_ts - infer_timer >= 1.0:
            stats["infer_fps"] = infer_count
            infer_count = 0
            infer_timer = now_ts
        stats["infer_ms"] = int(infer_ms)
        stats["person_count"] = person_count
        stats["confirmed"] = confirmed
        stats["alert_count"] = alert_count

        # 预警
        if confirmed and now_ts - last_alert_time > ALERT_COOLDOWN:
            last_alert_time = now_ts
            alert_count += 1
            consecutive_detect = 0
            stats["alert_count"] = alert_count
            now_str = datetime.now().strftime("%H:%M:%S")
            print(f"[{now_str}] ⚠ 确认入侵！检测到 {person_count} 人"
                  f"（连续 {CONFIRM_FRAMES} 帧确认，推理 {int(infer_ms)}ms）")

            # 截图带框（存档用）
            shot = frame.copy()
            for (x1, y1, x2, y2, conf) in boxes:
                cv2.rectangle(shot, (x1, y1), (x2, y2), (0, 0, 255), 2)
                cv2.putText(shot, f"Person {conf:.2f}", (x1, max(y1 - 10, 12)),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
            capture_dir = "tools/captures"
            os.makedirs(capture_dir, exist_ok=True)
            shot_path = f"{capture_dir}/intrusion_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg"
            cv2.imwrite(shot_path, shot)

            threading.Thread(target=upload_alert, args=(True,), daemon=True).start()
            threading.Thread(target=upload_capture, args=(shot,), daemon=True).start()

        time.sleep(INFER_MIN_INTERVAL)


# ==================== 渲染线程（主线程） ====================
def render_loop():
    """全速出画面，只负责画框和状态，不等待推理"""
    global latest_frame, stats, frame_id

    frame_count = 0
    fps_timer = time.time()
    fps = 0
    last_drawn_frame = None

    while True:
        with raw_frame_lock:
            if raw_frame is None:
                time.sleep(0.01)
                continue
            frame_obj = raw_frame

        # 只有新帧才重新绘制/输出，否则 50fps 的空转只是把同一张图反复编码
        if frame_obj is last_drawn_frame:
            time.sleep(0.005)
            continue
        last_drawn_frame = frame_obj
        frame = frame_obj.copy()

        h, w = frame.shape[:2]
        roi_x1, roi_y1, roi_x2, roi_y2 = get_roi_coords(h, w)

        # ROI 边框
        overlay = frame.copy()
        cv2.rectangle(overlay, (roi_x1, roi_y1), (roi_x2, roi_y2), (0, 255, 0), 2)
        cv2.addWeighted(overlay, 0.3, frame, 0.7, 0, frame)

        # 用最近一次检测结果（过期就不画）
        now_ts = time.time()
        with detections_lock:
            fresh = (now_ts - last_detection_ts) <= DET_MAX_AGE
            boxes = list(detections) if fresh else []

        for (x1, y1, x2, y2, conf) in boxes:
            cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 0, 255), 2)
            cv2.putText(frame, f"Person {conf:.2f}", (x1, max(y1 - 10, 12)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

        person_count = len(boxes)
        confirmed = stats["confirmed"] and fresh

        if confirmed:
            cv2.putText(frame, f"ALERT: {person_count} person(s) confirmed!",
                        (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
        elif person_count > 0:
            cv2.putText(frame, "Detecting...", (10, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 165, 255), 2)

        status_color = (0, 0, 255) if confirmed else (0, 165, 255) if person_count > 0 else (0, 255, 0)
        status_text = "ALERT" if confirmed else "DETECTING" if person_count > 0 else "SAFE"

        cv2.putText(frame, f"Status: {status_text}", (10, h - 55),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, status_color, 2)
        cv2.putText(frame, f"FPS: {fps} | Cam: {stats['cam_fps']} | Infer: {stats['infer_fps']}fps {stats['infer_ms']}ms",
                    (10, h - 35), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        cv2.putText(frame, f"{stats['device']} | ROI: {int(ROI_X1*100)}%-{int(ROI_X2*100)}% | Conf: {CONFIDENCE_THRESHOLD}",
                    (10, h - 15), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (200, 200, 200), 1)

        with frame_lock:
            latest_frame = frame
        with frame_id_lock:
            frame_id += 1

        frame_count += 1
        if time.time() - fps_timer >= 1.0:
            fps = frame_count
            stats["fps"] = fps
            frame_count = 0
            fps_timer = time.time()

        time.sleep(RENDER_INTERVAL)


# ==================== 工具函数 ====================
def get_roi_coords(frame_h, frame_w):
    x1 = int(frame_w * ROI_X1)
    y1 = int(frame_h * ROI_Y1)
    x2 = int(frame_w * ROI_X2)
    y2 = int(frame_h * ROI_Y2)
    return x1, y1, x2, y2


def upload_alert(human_detected):
    payload = {"deviceId": DEVICE_ID, "humanDetected": human_detected}
    try:
        resp = requests.post(f"{BASE_URL}/api/hardware/data/upload", json=payload, timeout=5)
        return resp.json().get("code") == 200
    except Exception as e:
        print(f"[错误] 预警上传失败: {e}")
        return False


def upload_capture(frame):
    try:
        _, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
        files = {'image': ('capture.jpg', buffer.tobytes(), 'image/jpeg')}
        resp = requests.post(f"{BASE_URL}/api/hardware/image/upload",
                             params={'deviceId': DEVICE_ID}, files=files, timeout=10)
        data = resp.json()
        if data.get("code") == 200:
            print(f"[✓] 截图已上传: {data['data']['url']}")
        else:
            print(f"[!] 截图上传失败: {data.get('message')}")
    except Exception as e:
        print(f"[错误] 截图上传失败: {e}")


def check_backend():
    try:
        r = requests.get(f"{BASE_URL}/api/auth/info", timeout=3)
        return r.status_code == 200
    except Exception:
        return False


# ==================== MJPEG 视频流服务器 ====================
class MJPEGHandler(BaseHTTPRequestHandler):

    def end_headers(self):
        # 统一在收尾时加 CORS / 私有网络头：
        # 页面常部署在公网（http://47.111.116.134:8080），而视频流在 localhost，
        # 属于「公网页面 → 本机资源」，Chrome 的 Private Network Access 会拦。
        # 必须显式声明允许，否则 <img> 永远是空白。
        if not getattr(self, '_cors_done', False):
            self._cors_done = True
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Private-Network', 'true')
        super().end_headers()

    def do_OPTIONS(self):
        # PNA / CORS 预检
        self.send_response(204)
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        self._cors_done = False
        self.end_headers()

    def do_GET(self):
        if self.path == '/stream':
            self.send_response(200)
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=frame')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.end_headers()

            try:
                last_sent = -1
                while True:
                    # 只在有新帧时才编码发送，避免重复编码同一张图烧 CPU
                    with frame_id_lock:
                        cur_id = frame_id
                    if cur_id == last_sent:
                        time.sleep(0.005)
                        continue
                    last_sent = cur_id

                    with frame_lock:
                        if latest_frame is None:
                            time.sleep(0.01)
                            continue
                        _, buffer = cv2.imencode('.jpg', latest_frame, [cv2.IMWRITE_JPEG_QUALITY, 70])
                        jpg_bytes = buffer.tobytes()

                    self.wfile.write(b'--frame\r\n')
                    self.wfile.write(b'Content-Type: image/jpeg\r\n')
                    self.wfile.write(f'Content-Length: {len(jpg_bytes)}\r\n\r\n'.encode())
                    self.wfile.write(jpg_bytes)
                    self.wfile.write(b'\r\n')
                    time.sleep(0.033)
            except (BrokenPipeError, ConnectionResetError, OSError):
                pass

        elif self.path == '/status':
            self.send_response(200)          # 少了这行 base handler 不会输出状态行，浏览器直接报协议冲突
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            import json
            self.wfile.write(json.dumps(stats).encode())

        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def start_stream_server():
    server = ThreadedHTTPServer(('0.0.0.0', STREAM_PORT), MJPEGHandler)
    print(f"[✓] 视频流服务器已启动: http://localhost:{STREAM_PORT}/stream")
    server.serve_forever()


# ==================== 主程序 ====================
def main():
    print("=" * 60)
    print("  河边安全预警系统 - AI视觉入侵检测（性能优化版）")
    print(f"  视频流地址: http://localhost:{STREAM_PORT}/stream")
    print("=" * 60)

    # 推理设备
    device = 'cpu'
    device_label = "CPU"
    if HAS_TORCH and torch.cuda.is_available():
        device = 0
        device_label = f"CUDA ({torch.cuda.get_device_name(0)})"
    stats["device"] = device_label

    print(f"[*] 推理设备: {device_label}")
    print(f"[*] 推理分辨率: {INFER_SIZE}px | 最小间隔: {INFER_MIN_INTERVAL}s")
    print(f"[*] 置信度阈值: {CONFIDENCE_THRESHOLD} | 多帧确认: {CONFIRM_FRAMES} 帧")
    print(f"[*] 检测区域: 左{int(ROI_X1*100)}% 右{int(ROI_X2*100)}%")
    print("=" * 60)

    print("[*] 检查后端连接...")
    if not check_backend():
        print("[!] 无法连接后端，请确保后端在运行 (localhost:8081)")
        sys.exit(1)
    print("[✓] 后端已连接")

    print(f"[*] 加载YOLOv8模型 ({MODEL_NAME})...")
    model = YOLO(MODEL_NAME)
    print("[✓] 模型加载完成")

    # 先起视频流服务，再起两个工作线程
    threading.Thread(target=start_stream_server, daemon=True).start()
    threading.Thread(target=grab_frames, args=(CAMERA_SOURCE,), daemon=True).start()
    time.sleep(1.0)
    threading.Thread(target=detect_worker, args=(model, device), daemon=True).start()

    print("\n[*] 开始检测... (按 Ctrl+C 退出)\n")

    try:
        render_loop()
    except KeyboardInterrupt:
        print("\n[*] 用户退出")
        print(f"[*] 本次会话共触发 {stats['alert_count']} 次预警")


if __name__ == "__main__":
    main()
