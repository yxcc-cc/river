"""
河边安全预警系统 - 多设备AI视觉入侵检测
支持同时监控多个摄像头（ESP32-CAM / USB摄像头）
每个摄像头独立线程，独立预警

使用方法：
  python tools/vision_multi.py                    # 使用默认配置
  python tools/vision_multi.py --devices 1,2,3    # 只启动指定设备
"""
import cv2
import numpy as np
import requests
import time
import sys
import os
import threading
import argparse
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from ultralytics import YOLO

# ==================== 全局配置 ====================
BASE_URL = "http://47.111.116.134:8081"   # 后端在阿里云；本地调试改回 http://localhost:8081
CONFIDENCE_THRESHOLD = 0.5
ALERT_COOLDOWN = 10
MODEL_NAME = "yolov8n.pt"
STREAM_PORT = 8083
CONFIRM_FRAMES = 3

# ROI 检测区域
ROI_X1, ROI_Y1 = 0.05, 0.0
ROI_X2, ROI_Y2 = 0.95, 1.0

PERSON_CLASS_ID = 0

# ==================== 性能参数 ====================
INFER_SIZE = 320            # 推理输入边长（640 → 320，快约4倍）
INFER_MIN_INTERVAL = 0.05   # 每个设备两次推理之间至少间隔（秒）
# 多个设备线程共用同一个模型，推理必须加锁，否则会互相踩
model_lock = threading.Lock()
INFER_DEVICE = "cpu"        # 启动时自动改成 0（GPU）

# ==================== 设备列表 ====================
# id 必须和后端 t_device 表里的记录一致，报警、抓拍才对得上。
# source 支持三种：
#   "http://x.x.x.x:8082/stream"   ESP32-CAM 的 MJPEG 流
#   0 / 1 / 2                      USB 摄像头编号
#   "D:/xx/yy.mp4"                 本地视频文件（用来模拟第二个点位）
# buzzer_url 留空：蜂鸣器现在由现场那块 ESP32 自己轮询后端报警来触发，不用这边推。
DEVICES = [
    {
        "id": "2462ABEFC95C",
        "name": "护池河公园(ESP32-CAM)",
        "source": "http://192.168.31.77:8082/stream",
        "buzzer_url": "",
        "enabled": True,
    },
    # 模拟点位：测试多路功能时临时打开。
    # 用 tools 目录下任意视频文件或上面提到的方式都行，跑完记得关掉，
    # 不然它会一直报"有人"、一直上传截图。
    # {
    #     "id": "TEST-002",
    #     "name": "测试点位2(模拟视频)",
    #     "source": r"D:\harness\tmp\sim_cam.mp4",
    #     "buzzer_url": "",
    #     "enabled": False,
    # },
]


# ==================== 全局状态 ====================
class DeviceState:
    def __init__(self, device_id, name):
        self.device_id = device_id
        self.name = name
        self.latest_frame = None
        self.frame_lock = threading.Lock()
        self.fps = 0
        self.person_count = 0
        self.alert_count = 0
        self.consecutive_detect = 0
        self.last_alert_time = 0
        self.status = "INITIALIZING"  # INITIALIZING / SAFE / DETECTING / ALERT
        self.error = None


device_states = {}  # {device_id: DeviceState}
model = None


# ==================== 工具函数 ====================
def upload_alert(device_id, human_detected):
    payload = {"deviceId": device_id, "humanDetected": human_detected}
    try:
        resp = requests.post(f"{BASE_URL}/api/hardware/data/upload", json=payload, timeout=5)
        return resp.json().get("code") == 200
    except Exception as e:
        print(f"[{device_id}] 预警上传失败: {e}")
        return False


def upload_capture(device_id, frame):
    try:
        _, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
        files = {'image': ('capture.jpg', buffer.tobytes(), 'image/jpeg')}
        resp = requests.post(f"{BASE_URL}/api/hardware/image/upload",
                             params={'deviceId': device_id}, files=files, timeout=10)
        data = resp.json()
        if data.get("code") == 200:
            print(f"[{device_id}] 截图已上传")
        else:
            print(f"[{device_id}] 截图上传失败: {data.get('message')}")
    except Exception as e:
        print(f"[{device_id}] 截图上传失败: {e}")


def trigger_buzzer(buzzer_url, duration=3000):
    """触发蜂鸣器模块报警（POST JSON）"""
    if not buzzer_url or "XXX" in buzzer_url:
        return
    try:
        requests.post(buzzer_url, json={"action": "on", "duration": duration}, timeout=3)
    except Exception:
        pass


def get_roi_coords(h, w):
    return int(w * ROI_X1), int(h * ROI_Y1), int(w * ROI_X2), int(h * ROI_Y2)


# ==================== MJPEG流读取器（替代OpenCV VideoCapture）====================
class MJPEGReader:
    """用requests读取MJPEG流，解决OpenCV FFmpeg后端兼容性问题"""
    def __init__(self, url):
        self.url = url
        self.frame = None
        self.lock = threading.Lock()
        self.running = False
        self.thread = None

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._read_stream, daemon=True)
        self.thread.start()
        # 等待第一帧
        for _ in range(50):
            with self.lock:
                if self.frame is not None:
                    return True
            time.sleep(0.1)
        return False

    def _read_stream(self):
        while self.running:
            try:
                resp = requests.get(self.url, stream=True, timeout=10)
                buf = b''
                for chunk in resp.iter_content(chunk_size=4096):
                    if not self.running:
                        break
                    buf += chunk
                    # 找JPEG帧边界
                    start = buf.find(b'\xff\xd8')
                    end = buf.find(b'\xff\xd9')
                    if start != -1 and end != -1 and end > start:
                        jpg_data = buf[start:end+2]
                        buf = buf[end+2:]
                        img = cv2.imdecode(
                            np.frombuffer(jpg_data, np.uint8),
                            cv2.IMREAD_COLOR
                        )
                        if img is not None:
                            with self.lock:
                                self.frame = img
            except Exception:
                if self.running:
                    time.sleep(2)

    def read(self):
        with self.lock:
            if self.frame is not None:
                return True, self.frame.copy()
        return False, None

    def isOpened(self):
        return True  # 总是返回True，连接问题在start()里处理

    def release(self):
        self.running = False


# ==================== 单设备检测线程 ====================
# ==================== 视频源统一打开 ====================
class VideoFileReader:
    """本地视频文件循环播放，接口和 cv2.VideoCapture 对齐（read/release/isOpened）"""

    def __init__(self, path):
        self.path = path
        self.cap = cv2.VideoCapture(path)
        self._total = int(self.cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)

    def isOpened(self):
        return self.cap.isOpened() and self._total > 0

    def read(self):
        ret, frame = self.cap.read()
        if not ret:
            # 播到结尾就从头再来，模拟一台一直在拍的摄像头
            self.cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            ret, frame = self.cap.read()
        return ret, frame

    def release(self):
        try:
            self.cap.release()
        except Exception:
            pass


def open_source(source):
    """
    统一打开视频源，返回的对象只需要支持 read() 和 release()。
    支持：USB 摄像头编号(int) / 本地视频文件 / MJPEG 流地址。
    """
    try:
        if isinstance(source, int):
            cap = cv2.VideoCapture(source)
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
            return cap if cap.isOpened() else None

        is_url = isinstance(source, str) and source.lower().startswith(
            ("http://", "https://", "rtsp://")
        )
        if is_url:
            reader = MJPEGReader(source)
            return reader if reader.start() else None

        # 其余情况当本地视频文件
        reader = VideoFileReader(source)
        return reader if reader.isOpened() else None
    except Exception as e:
        print(f"打开视频源失败 {source}: {e}")
        return None


def device_worker(device_info):
    """每个设备一个独立线程"""
    dev_id = device_info["id"]
    source = device_info["source"]
    name = device_info["name"]
    buzzer_url = device_info.get("buzzer_url")

    state = DeviceState(dev_id, name)
    device_states[dev_id] = state

    print(f"[{dev_id}] {name} - 启动中... 视频源: {source}")

    cap = open_source(source)
    if cap is None:
        state.error = "无法连接视频源"
        state.status = "ERROR"
        print(f"[{dev_id}] {name} - 无法连接视频源: {source}")
        return

    state.status = "SAFE"
    print(f"[{dev_id}] {name} - 已连接，开始检测")

    fps_timer = time.time()
    frame_count = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            state.error = "视频流断开"
            state.status = "ERROR"
            print(f"[{dev_id}] {name} - 视频流断开，5秒后重连...")
            time.sleep(5)
            cap.release()
            cap = open_source(source)
            if cap is not None:
                state.error = None
                state.status = "SAFE"
                print(f"[{dev_id}] {name} - 重连成功")
            continue

        # FPS计算
        frame_count += 1
        if time.time() - fps_timer >= 1.0:
            state.fps = frame_count
            frame_count = 0
            fps_timer = time.time()

        h, w = frame.shape[:2]
        roi_x1, roi_y1, roi_x2, roi_y2 = get_roi_coords(h, w)

        # 绘制ROI
        overlay = frame.copy()
        cv2.rectangle(overlay, (roi_x1, roi_y1), (roi_x2, roi_y2), (0, 255, 0), 2)
        cv2.addWeighted(overlay, 0.3, frame, 0.7, 0, frame)

        # YOLO检测（低分辨率 + 全局锁：多设备共用同一模型）
        roi_frame = frame[roi_y1:roi_y2, roi_x1:roi_x2]
        try:
            with model_lock:
                results = model(roi_frame,
                                conf=CONFIDENCE_THRESHOLD,
                                classes=[PERSON_CLASS_ID],
                                imgsz=INFER_SIZE,
                                device=INFER_DEVICE,
                                verbose=False)
        except Exception as e:
            print(f"[{device_info['id']}] 推理出错: {e}")
            time.sleep(0.5)
            continue

        person_count = 0
        for result in results:
            for box in result.boxes:
                if int(box.cls[0]) == PERSON_CLASS_ID:
                    bx1, by1, bx2, by2 = map(int, box.xyxy[0])
                    x1, y1 = bx1 + roi_x1, by1 + roi_y1
                    x2, y2 = bx2 + roi_x1, by2 + roi_y1
                    conf = float(box.conf[0])
                    person_count += 1
                    cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 0, 255), 2)
                    cv2.putText(frame, f"Person {conf:.2f}", (x1, y1 - 10),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

        # 多帧确认
        if person_count > 0:
            state.consecutive_detect += 1
        else:
            state.consecutive_detect = 0

        confirmed = state.consecutive_detect >= CONFIRM_FRAMES
        state.person_count = person_count

        # 状态
        if confirmed:
            state.status = "ALERT"
        elif person_count > 0:
            state.status = "DETECTING"
        else:
            state.status = "SAFE"

        # 设备名 + 状态水印
        cv2.putText(frame, f"[{dev_id}] {name}", (10, 25),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 0), 2)

        if confirmed:
            cv2.putText(frame, f"ALERT! {person_count} person(s)", (10, 55),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)
        elif person_count > 0:
            cv2.putText(frame, f"Detecting ({state.consecutive_detect}/{CONFIRM_FRAMES})",
                        (10, 55), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 165, 255), 2)

        # 更新帧（供HTTP服务器使用）
        with state.frame_lock:
            state.latest_frame = frame.copy()

        # 预警触发
        current_time = time.time()
        if confirmed and current_time - state.last_alert_time > ALERT_COOLDOWN:
            state.last_alert_time = current_time
            state.alert_count += 1
            state.consecutive_detect = 0
            now = datetime.now().strftime("%H:%M:%S")
            print(f"[{now}] [{dev_id}] {name} - 确认入侵！{person_count} 人")

            # 本地截图（保存到后端uploads目录，确保前端能访问）
            capture_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                       "backend", "uploads", "captures", datetime.now().strftime("%Y%m%d"))
            os.makedirs(capture_dir, exist_ok=True)
            filename = f"{dev_id}_{datetime.now().strftime('%H%M%S_%f')[:13]}.jpg"
            cv2.imwrite(os.path.join(capture_dir, filename), frame)

            # 异步上传预警
            threading.Thread(target=upload_alert, args=(dev_id, True), daemon=True).start()
            threading.Thread(target=upload_capture, args=(dev_id, frame.copy()), daemon=True).start()

            # 触发蜂鸣器报警（异步，不阻塞检测）
            if buzzer_url:
                threading.Thread(target=trigger_buzzer, args=(buzzer_url, 800), daemon=True).start()

        time.sleep(0.03)


# ==================== MJPEG 多路视频流服务器 ====================
class MultiMJPEGHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        # 解析路径和查询参数
        path = self.path.split('?')[0] if '?' in self.path else self.path

        if path in ('/stream', '/stream/stream'):
            # 默认显示第一个设备，或通过 ?dev=DEV-002 指定
            dev_id = None
            if '?' in self.path:
                params = self.path.split('?')[1]
                for p in params.split('&'):
                    if p.startswith('dev='):
                        dev_id = p.split('=')[1]

            # 找到对应设备
            state = None
            if dev_id and dev_id in device_states:
                state = device_states[dev_id]
            elif device_states:
                state = list(device_states.values())[0]

            if state is None:
                self.send_response(503)
                self.end_headers()
                return

            self.send_response(200)
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=frame')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            try:
                while True:
                    with state.frame_lock:
                        if state.latest_frame is None:
                            time.sleep(0.05)
                            continue
                        _, buffer = cv2.imencode('.jpg', state.latest_frame, [cv2.IMWRITE_JPEG_QUALITY, 70])
                        jpg_bytes = buffer.tobytes()

                    self.wfile.write(b'--frame\r\n')
                    self.wfile.write(b'Content-Type: image/jpeg\r\n')
                    self.wfile.write(f'Content-Length: {len(jpg_bytes)}\r\n\r\n'.encode())
                    self.wfile.write(jpg_bytes)
                    self.wfile.write(b'\r\n')
                    time.sleep(0.033)
            except (BrokenPipeError, ConnectionResetError, OSError):
                pass

        elif path.endswith('/status'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            import json
            status = {}
            for dev_id, state in device_states.items():
                status[dev_id] = {
                    "name": state.name,
                    "fps": state.fps,
                    "person_count": state.person_count,
                    "alert_count": state.alert_count,
                    "status": state.status,
                    "error": state.error,
                }
            self.wfile.write(json.dumps(status, ensure_ascii=False).encode())

        elif path == '/':
            # 多画面页面
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            html = """<!DOCTYPE html>
<html><head><title>多设备监控</title>
<style>
  body { margin:0; background:#1a1a2e; color:#fff; font-family:Arial; }
  .header { text-align:center; padding:10px; background:#16213e; }
  .grid { display:grid; grid-template-columns:1fr 1fr; gap:4px; padding:4px; }
  .cam { position:relative; }
  .cam img { width:100%; display:block; }
  .cam .label { position:absolute; top:8px; left:8px; background:rgba(0,0,0,0.7);
    padding:4px 8px; border-radius:4px; font-size:14px; }
</style></head><body>
<div class="header"><h2>河边安全预警系统 - 多设备实时监控</h2></div>
<div class="grid">"""
            for dev_id, state in device_states.items():
                html += f'<div class="cam"><img src="/stream?dev={dev_id}">'
                html += f'<div class="label">[{dev_id}] {state.name}</div></div>'
            html += "</div></body></html>"
            self.wfile.write(html.encode())

        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def start_stream_server():
    server = ThreadedHTTPServer(('0.0.0.0', STREAM_PORT), MultiMJPEGHandler)
    print(f"\n[OK] 多路视频流服务器已启动:")
    print(f"     总览页面: http://localhost:{STREAM_PORT}/")
    print(f"     单路流:   http://localhost:{STREAM_PORT}/stream?dev=DEV-001")
    print(f"     状态API:  http://localhost:{STREAM_PORT}/status\n")
    server.serve_forever()


# ==================== 主程序 ====================
def main():
    global model

    # 解析参数
    parser = argparse.ArgumentParser()
    parser.add_argument('--devices', type=str, default=None, help='指定设备编号，逗号分隔，如 1,2,3')
    args = parser.parse_args()

    # 过滤设备
    devices = DEVICES
    if args.devices:
        indices = [int(x) - 1 for x in args.devices.split(',')]
        devices = [DEVICES[i] for i in indices if 0 <= i < len(DEVICES)]

    enabled = [d for d in devices if d.get("enabled", True)]

    print("=" * 60)
    print("  河边安全预警系统 - 多设备AI视觉入侵检测")
    print(f"  视频流总览: http://localhost:{STREAM_PORT}/")
    print("=" * 60)
    print(f"[*] 启动 {len(enabled)} 个设备:")
    for i, d in enumerate(enabled, 1):
        print(f"    {i}. [{d['id']}] {d['name']} -> {d['source']}")
    print(f"[*] 参数: 置信度={CONFIDENCE_THRESHOLD}, 确认帧数={CONFIRM_FRAMES}, 冷却={ALERT_COOLDOWN}s")
    print("=" * 60)

    # 检查后端
    print("[*] 检查后端连接...")
    try:
        r = requests.get(f"{BASE_URL}/api/auth/info", timeout=3)
        if r.status_code != 200:
            raise Exception
        print("[OK] 后端已连接")
    except:
        print("[!] 无法连接后端，请确保后端在运行 (localhost:8081)")
        sys.exit(1)

    # 加载模型
    print(f"[*] 加载YOLOv8模型 ({MODEL_NAME})...")
    model = YOLO(MODEL_NAME)

    # 有 GPU 就用 GPU（需要装 CUDA 版的 torch）
    global INFER_DEVICE
    try:
        import torch
        if torch.cuda.is_available():
            INFER_DEVICE = 0
            print(f"[OK] 用 GPU 推理: {torch.cuda.get_device_name(0)}")
        else:
            print("[!] torch 是 CPU 版，用 CPU 推理（慢）")
    except ImportError:
        print("[!] 没装 torch，用 CPU 推理")
    print(f"[OK] 模型加载完成 | 推理分辨率 {INFER_SIZE}px | device={INFER_DEVICE}")

    # 启动视频流服务器
    stream_thread = threading.Thread(target=start_stream_server, daemon=True)
    stream_thread.start()

    # 为每个设备启动检测线程
    threads = []
    for device in enabled:
        t = threading.Thread(target=device_worker, args=(device,), daemon=True)
        t.start()
        threads.append(t)
        time.sleep(0.5)  # 错开启动

    print(f"\n[*] 所有设备已启动，按 Ctrl+C 退出\n")

    # 主线程：定期打印状态
    try:
        while True:
            time.sleep(10)
            print(f"\n--- 状态报告 {datetime.now().strftime('%H:%M:%S')} ---")
            for dev_id, state in device_states.items():
                status_icon = {"SAFE": "OK", "DETECTING": "!!", "ALERT": "XX", "ERROR": "??", "INITIALIZING": ".."}
                icon = status_icon.get(state.status, "??")
                err = f" ({state.error})" if state.error else ""
                print(f"  [{icon}] {dev_id} {state.name:10s} | FPS:{state.fps:2d} "
                      f"| 人:{state.person_count} | 预警:{state.alert_count}{err}")
            print("-" * 50)
    except KeyboardInterrupt:
        print("\n[*] 用户退出")
        for dev_id, state in device_states.items():
            print(f"  {dev_id} {state.name}: 共触发 {state.alert_count} 次预警")


if __name__ == "__main__":
    main()
