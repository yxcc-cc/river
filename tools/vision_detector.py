"""
河边安全预警系统 - AI视觉入侵检测
使用USB摄像头 + YOLOv8 实时检测人体，触发入侵预警
提供 MJPEG 实时视频流，前端可直接显示

优化策略：
1. 高置信度阈值（0.7）过滤低质量误检
2. 检测区域（ROI）限定画面中间区域，忽略边缘支架
3. 多帧确认（连续5帧）才触发报警，过滤偶发误检
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

# ==================== 配置 ====================
BASE_URL = "http://localhost:8081"
DEVICE_ID = "DEV-001"
CAMERA_SOURCE = "http://10.234.223.134:8082/stream"  # ESP32-CAM MJPEG流地址
# CAMERA_SOURCE = 1               # 或使用USB摄像头（取消注释这行）
CONFIDENCE_THRESHOLD = 0.5      # 置信度阈值
ALERT_COOLDOWN = 5              # 预警冷却时间（秒）
MODEL_NAME = "yolov8n.pt"
STREAM_PORT = 8083              # AI处理后的视频流端口（避免与ESP32-CAM冲突）

# 多帧确认：连续检测到多少帧才触发报警
CONFIRM_FRAMES = 3

# 检测区域（ROI）：占画面的比例 [x1%, y1%, x2%, y2%]
# 只检测画面中心区域，忽略边缘（支架、边框等）
ROI_X1 = 0.05   # 左边 5% 不检测
ROI_Y1 = 0.0    # 顶部不检测
ROI_X2 = 0.95   # 右边 5% 不检测
ROI_Y2 = 1.0    # 底部不检测

PERSON_CLASS_ID = 0

# 全局变量
latest_frame = None
raw_frame = None
frame_lock = threading.Lock()
raw_frame_lock = threading.Lock()
stats = {"fps": 0, "alert_count": 0, "person_count": 0}


def grab_frames(cap):
    """独立线程持续抓取最新帧"""
    global raw_frame
    while True:
        ret, f = cap.read()
        if ret:
            with raw_frame_lock:
                raw_frame = f.copy()
        else:
            time.sleep(0.01)


def upload_alert(human_detected):
    """上传检测结果到后端"""
    payload = {"deviceId": DEVICE_ID, "humanDetected": human_detected}
    try:
        resp = requests.post(f"{BASE_URL}/api/hardware/data/upload", json=payload, timeout=5)
        return resp.json().get("code") == 200
    except Exception as e:
        print(f"[错误] 预警上传失败: {e}")
        return False


def upload_capture(frame):
    """截图上传到后端"""
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
    except:
        return False


def get_roi_coords(frame_h, frame_w):
    """根据 ROI 比例计算实际像素坐标"""
    x1 = int(frame_w * ROI_X1)
    y1 = int(frame_h * ROI_Y1)
    x2 = int(frame_w * ROI_X2)
    y2 = int(frame_h * ROI_Y2)
    return x1, y1, x2, y2


def is_in_roi(box_x1, box_y1, box_x2, box_y2, roi_x1, roi_y1, roi_x2, roi_y2):
    """判断检测框的中心点是否在 ROI 区域内"""
    center_x = (box_x1 + box_x2) / 2
    center_y = (box_y1 + box_y2) / 2
    return roi_x1 <= center_x <= roi_x2 and roi_y1 <= center_y <= roi_y2


# ==================== MJPEG 视频流服务器 ====================
class MJPEGHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == '/stream':
            self.send_response(200)
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=frame')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            try:
                while True:
                    with frame_lock:
                        if latest_frame is None:
                            time.sleep(0.05)
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
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
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
    global latest_frame, stats

    print("=" * 60)
    print("  河边安全预警系统 - AI视觉入侵检测")
    print(f"  视频流地址: http://localhost:{STREAM_PORT}/stream")
    print("=" * 60)
    print(f"[*] 优化参数:")
    print(f"    置信度阈值: {CONFIDENCE_THRESHOLD}")
    print(f"    检测区域: 左{int(ROI_X1*100)}% 右{int(ROI_X2*100)}% 上{int(ROI_Y1*100)}% 下{int(ROI_Y2*100)}%")
    print(f"    多帧确认: 连续 {CONFIRM_FRAMES} 帧")
    print("=" * 60)

    # 检查后端
    print("[*] 检查后端连接...")
    if not check_backend():
        print("[!] 无法连接后端，请确保后端在运行 (localhost:8081)")
        sys.exit(1)
    print("[✓] 后端已连接")

    # 加载模型
    print(f"[*] 加载YOLOv8模型 ({MODEL_NAME})...")
    model = YOLO(MODEL_NAME)
    print("[✓] 模型加载完成")

    # 打开摄像头
    print(f"[*] 打开视频源: {CAMERA_SOURCE}")
    cap = cv2.VideoCapture(CAMERA_SOURCE)
    if not cap.isOpened():
        print("[!] 无法打开视频源，请检查连接")
        print("[!] 如果使用ESP32-CAM，请确保它已连接WiFi并运行")
        sys.exit(1)
    # 如果是USB摄像头，设置分辨率
    if isinstance(CAMERA_SOURCE, int):
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    print("[✓] 视频源已打开")

    # 启动视频流
    stream_thread = threading.Thread(target=start_stream_server, daemon=True)
    stream_thread.start()

    # 启动抓帧线程
    grab_thread = threading.Thread(target=grab_frames, args=(cap,), daemon=True)
    grab_thread.start()
    time.sleep(1)  # 等待第一帧

    last_alert_time = 0
    alert_count = 0
    frame_count = 0
    fps = 0
    fps_timer = time.time()

    # 多帧确认：连续检测计数器
    consecutive_detect = 0

    print("\n[*] 开始检测... (按 Ctrl+C 退出)\n")

    try:
        while True:
            # 从抓帧线程获取最新帧
            with raw_frame_lock:
                if raw_frame is None:
                    time.sleep(0.01)
                    continue
                frame = raw_frame.copy()

            frame_count += 1
            if time.time() - fps_timer >= 1.0:
                fps = frame_count
                frame_count = 0
                fps_timer = time.time()

            h, w = frame.shape[:2]
            roi_x1, roi_y1, roi_x2, roi_y2 = get_roi_coords(h, w)

            # 绘制 ROI 区域（半透明绿色边框）
            overlay = frame.copy()
            cv2.rectangle(overlay, (roi_x1, roi_y1), (roi_x2, roi_y2), (0, 255, 0), 2)
            cv2.addWeighted(overlay, 0.3, frame, 0.7, 0, frame)

            # YOLOv8 检测（只在 ROI 区域内裁剪检测，提高性能）
            roi_frame = frame[roi_y1:roi_y2, roi_x1:roi_x2]
            results = model(roi_frame, conf=CONFIDENCE_THRESHOLD, classes=[PERSON_CLASS_ID], verbose=False)

            person_count = 0
            for result in results:
                for box in result.boxes:
                    if int(box.cls[0]) == PERSON_CLASS_ID:
                        # 坐标转换：ROI 裁剪坐标 → 原图坐标
                        bx1, by1, bx2, by2 = map(int, box.xyxy[0])
                        x1 = bx1 + roi_x1
                        y1 = by1 + roi_y1
                        x2 = bx2 + roi_x1
                        y2 = by2 + roi_y1
                        conf = float(box.conf[0])

                        person_count += 1
                        # 红色框
                        cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 0, 255), 2)
                        cv2.putText(frame, f"Person {conf:.2f}", (x1, y1 - 10),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

            # 多帧确认逻辑
            if person_count > 0:
                consecutive_detect += 1
            else:
                consecutive_detect = 0

            # 只有连续检测到足够帧数才认为是真正入侵
            confirmed = consecutive_detect >= CONFIRM_FRAMES

            # 状态栏
            if confirmed:
                cv2.putText(frame, f"ALERT: {person_count} person(s) confirmed!",
                            (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
            elif person_count > 0:
                cv2.putText(frame, f"Detecting... ({consecutive_detect}/{CONFIRM_FRAMES})",
                            (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 165, 255), 2)

            status_color = (0, 0, 255) if confirmed else (0, 165, 255) if person_count > 0 else (0, 255, 0)
            status_text = "ALERT" if confirmed else "DETECTING" if person_count > 0 else "SAFE"
            cv2.putText(frame, f"Status: {status_text}", (10, frame.shape[0] - 55),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, status_color, 2)
            cv2.putText(frame, f"FPS: {fps} | Alerts: {alert_count}", (10, frame.shape[0] - 35),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
            cv2.putText(frame, f"ROI: {int(ROI_X1*100)}%-{int(ROI_X2*100)}% | Conf: {CONFIDENCE_THRESHOLD} | Frames: {CONFIRM_FRAMES}",
                        (10, frame.shape[0] - 15),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.4, (200, 200, 200), 1)

            # 更新全局帧
            with frame_lock:
                latest_frame = frame.copy()

            stats["fps"] = fps
            stats["alert_count"] = alert_count
            stats["person_count"] = person_count

            # 预警逻辑（需要多帧确认）
            current_time = time.time()
            if confirmed and current_time - last_alert_time > ALERT_COOLDOWN:
                last_alert_time = current_time
                alert_count += 1
                consecutive_detect = 0  # 重置计数器
                now = datetime.now().strftime("%H:%M:%S")
                print(f"[{now}] ⚠ 确认入侵！检测到 {person_count} 人（连续 {CONFIRM_FRAMES} 帧确认）")

                # 本地截图
                capture_dir = "tools/captures"
                os.makedirs(capture_dir, exist_ok=True)
                cv2.imwrite(f"{capture_dir}/intrusion_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg", frame)

                # 异步上传
                threading.Thread(target=upload_alert, args=(True,), daemon=True).start()
                threading.Thread(target=upload_capture, args=(frame.copy(),), daemon=True).start()

            time.sleep(0.03)  # ~30fps处理

    except KeyboardInterrupt:
        print("\n[*] 用户退出")
    finally:
        cap.release()
        print(f"\n[*] 本次会话共触发 {alert_count} 次预警")


if __name__ == "__main__":
    main()
