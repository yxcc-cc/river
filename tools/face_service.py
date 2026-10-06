"""
河边安全预警系统 - 人脸签到服务
提供人脸注册和验证HTTP接口
端口: 8084

使用方法:
  python tools/face_service.py

依赖:
  pip install face_recognition flask opencv-python numpy
  或者（如果face_recognition安装困难）:
  pip install opencv-python flask numpy
"""
import os
import sys
import json
import base64
import time
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from datetime import datetime

import cv2
import numpy as np

# ==================== 配置 ====================
PORT = 8084
# 人脸匹配阈值（越小越严格，建议 0.25~0.35）
FACE_TOLERANCE = 0.3
# OpenCV 模式余弦相似度阈值（越大越严格，建议 0.93~0.97）
OPENCV_SIMILARITY_THRESHOLD = 0.95
FACES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                         "backend", "uploads", "faces")
CAPTURES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "backend", "uploads", "captures", "attendance")
ENCODINGS_FILE = os.path.join(FACES_DIR, "encodings.json")

# 确保目录存在
os.makedirs(FACES_DIR, exist_ok=True)
os.makedirs(CAPTURES_DIR, exist_ok=True)

# ==================== 人脸检测 ====================
# 使用OpenCV DNN人脸检测器（比Haar级联更准确）
FACE_NET = None
OPENCV_DIR = os.path.dirname(cv2.__file__)
PROTOTXT = os.path.join(OPENCV_DIR, "data", "haarcascade_frontalface_default.xml")
FACE_CASCADE = None

# 存储已注册的人脸编码
known_face_encodings = []
known_face_user_ids = []

# 尝试加载face_recognition库
FACE_RECOGNITION_AVAILABLE = False
try:
    import face_recognition
    FACE_RECOGNITION_AVAILABLE = True
    print("[OK] face_recognition 库已加载（高精度模式）")
except ImportError:
    print("[!] face_recognition 未安装，使用 OpenCV 模式（基础精度）")
    print("    安装高精度模式: pip install face_recognition dlib")


def init_face_detector():
    """初始化人脸检测器"""
    global FACE_CASCADE
    # 复制级联文件到项目目录，避免中文路径问题
    src = os.path.join(cv2.data.haarcascades, "haarcascade_frontalface_default.xml")
    dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "haarcascade_frontalface_default.xml")
    if not os.path.exists(dst):
        import shutil
        shutil.copy2(src, dst)
    FACE_CASCADE = cv2.CascadeClassifier(dst)
    if FACE_CASCADE.empty():
        print("[!] 人脸级联文件加载失败")
        return False
    print("[OK] OpenCV 人脸检测器已加载")
    return True


def load_encodings():
    """加载已注册的人脸编码"""
    global known_face_encodings, known_face_user_ids
    if os.path.exists(ENCODINGS_FILE):
        with open(ENCODINGS_FILE, "r") as f:
            data = json.load(f)
            known_face_encodings = [np.array(enc) for enc in data.get("encodings", [])]
            known_face_user_ids = data.get("user_ids", [])
        print(f"[OK] 已加载 {len(known_face_user_ids)} 个人脸注册记录")
    else:
        known_face_encodings = []
        known_face_user_ids = []


def save_encodings():
    """保存人脸编码到文件"""
    data = {
        "encodings": [enc.tolist() for enc in known_face_encodings],
        "user_ids": known_face_user_ids
    }
    with open(ENCODINGS_FILE, "w") as f:
        json.dump(data, f)


def detect_faces(frame):
    """检测人脸，返回人脸区域列表"""
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = FACE_CASCADE.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(80, 80))
    return faces


def get_face_encoding(frame, face_location):
    """提取人脸编码"""
    if FACE_RECOGNITION_AVAILABLE:
        # face_recognition模式：高精度
        top, right, bottom, left = face_location
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        encodings = face_recognition.face_encodings(rgb_frame, [(top, right, bottom, left)])
        if encodings:
            return encodings[0]
        return None
    else:
        # OpenCV模式：使用LBP特征
        x, y, w, h = face_location
        face_roi = frame[y:y+h, x:x+w]
        face_roi = cv2.resize(face_roi, (128, 128))
        gray = cv2.cvtColor(face_roi, cv2.COLOR_BGR2GRAY)
        # 使用直方图作为简易特征
        hist = cv2.calcHist([gray], [0], None, [64], [0, 256])
        hist = cv2.normalize(hist, hist).flatten()
        return hist


def compare_faces(encoding):
    """比对人脸，返回 (matched, user_id, distance)"""
    if encoding is None:
        return False, None, 1.0

    if len(known_face_encodings) == 0:
        return False, None, 1.0

    if FACE_RECOGNITION_AVAILABLE:
        distances = face_recognition.face_distance(known_face_encodings, encoding)
        min_idx = np.argmin(distances)
        if distances[min_idx] < FACE_TOLERANCE:
            return True, known_face_user_ids[min_idx], float(distances[min_idx])
        return False, None, float(distances[min_idx])
    else:
        # OpenCV模式：余弦相似度
        best_similarity = -1
        best_idx = -1
        for i, known_enc in enumerate(known_face_encodings):
            similarity = np.dot(encoding, known_enc) / (np.linalg.norm(encoding) * np.linalg.norm(known_enc) + 1e-6)
            if similarity > best_similarity:
                best_similarity = similarity
                best_idx = i
        if best_similarity > OPENCV_SIMILARITY_THRESHOLD:
            return True, known_face_user_ids[best_idx], float(1 - best_similarity)
        return False, None, float(1 - best_similarity)


def decode_image(base64_str):
    """解码base64图片"""
    if "," in base64_str:
        base64_str = base64_str.split(",")[1]
    img_bytes = base64.b64decode(base64_str)
    np_arr = np.frombuffer(img_bytes, np.uint8)
    return cv2.imdecode(np_arr, cv2.IMREAD_COLOR)


# ==================== HTTP处理器 ====================
class FaceHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        path = self.path.split("?")[0]
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)
        data = json.loads(body.decode("utf-8"))

        if path == "/register":
            self.handle_register(data)
        elif path == "/verify":
            self.handle_verify(data)
        else:
            self.send_json(404, {"error": "Not found"})

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/status":
            self.send_json(200, {
                "status": "running",
                "registered_count": len(known_face_user_ids),
                "mode": "face_recognition" if FACE_RECOGNITION_AVAILABLE else "opencv"
            })
        elif path == "/check-register":
            self.handle_check_register()
        else:
            self.send_json(404, {"error": "Not found"})

    def handle_check_register(self):
        """检查用户是否已注册人脸"""
        query = self.path.split("?")[1] if "?" in self.path else ""
        params = dict(p.split("=") for p in query.split("&") if "=" in p) if query else {}
        user_id = params.get("userId")

        if not user_id:
            self.send_json(400, {"code": 400, "message": "缺少userId参数"})
            return

        registered = str(user_id) in known_face_user_ids
        self.send_json(200, {
            "code": 200,
            "data": {"registered": registered}
        })

    def handle_register(self, data):
        """注册人脸"""
        user_id = data.get("userId")
        image_base64 = data.get("image")

        if not user_id or not image_base64:
            self.send_json(400, {"code": 400, "message": "缺少userId或image参数"})
            return

        frame = decode_image(image_base64)
        if frame is None:
            self.send_json(400, {"code": 400, "message": "图片解码失败"})
            return

        # 检测人脸
        faces = detect_faces(frame)
        if len(faces) == 0:
            self.send_json(400, {"code": 400, "message": "未检测到人脸，请正对摄像头"})
            return

        if len(faces) > 1:
            self.send_json(400, {"code": 400, "message": "检测到多张人脸，请确保只有一个人"})
            return

        # 提取人脸编码
        x, y, w, h = faces[0]
        if FACE_RECOGNITION_AVAILABLE:
            encoding = get_face_encoding(frame, (y, x+w, y+h, x))
        else:
            encoding = get_face_encoding(frame, (x, y, w, h))

        if encoding is None:
            self.send_json(400, {"code": 400, "message": "人脸特征提取失败"})
            return

        # 检查是否已注册，已注册则更新
        str_user_id = str(user_id)
        if str_user_id in known_face_user_ids:
            idx = known_face_user_ids.index(str_user_id)
            known_face_encodings[idx] = encoding
        else:
            known_face_encodings.append(encoding)
            known_face_user_ids.append(str_user_id)

        # 保存注册照片
        face_img = frame[y:y+h, x:x+w]
        face_path = os.path.join(FACES_DIR, f"user_{str_user_id}.jpg")
        cv2.imwrite(face_path, face_img)

        save_encodings()

        self.send_json(200, {
            "code": 200,
            "message": "人脸注册成功",
            "data": {"userId": user_id, "faceCount": len(known_face_user_ids)}
        })
        print(f"[注册] 用户 {user_id} 人脸注册成功")

    def handle_verify(self, data):
        """验证人脸"""
        image_base64 = data.get("image")

        if not image_base64:
            self.send_json(400, {"code": 400, "message": "缺少image参数"})
            return

        frame = decode_image(image_base64)
        if frame is None:
            self.send_json(400, {"code": 400, "message": "图片解码失败"})
            return

        # 检测人脸
        faces = detect_faces(frame)
        if len(faces) == 0:
            self.send_json(200, {
                "code": 400,
                "message": "未检测到人脸，请正对摄像头",
                "data": {"matched": False}
            })
            return

        # 取最大的人脸
        x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
        if FACE_RECOGNITION_AVAILABLE:
            encoding = get_face_encoding(frame, (y, x+w, y+h, x))
        else:
            encoding = get_face_encoding(frame, (x, y, w, h))

        # 比对
        matched, user_id, distance = compare_faces(encoding)

        # 保存签到截图
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        capture_path = os.path.join(CAPTURES_DIR, f"verify_{timestamp}.jpg")
        cv2.imwrite(capture_path, frame)

        mode = "face_recognition" if FACE_RECOGNITION_AVAILABLE else "opencv"
        if matched:
            self.send_json(200, {
                "code": 200,
                "message": "人脸验证成功",
                "data": {
                    "matched": True,
                    "registered": True,
                    "userId": int(user_id),
                    "confidence": round(1 - distance, 3),
                    "capturePath": capture_path
                }
            })
            print(f"[验证] 用户 {user_id} 匹配成功 | 模式={mode} | 距离={distance:.4f} | 置信度={1-distance:.3f}")
        else:
            # 区分"系统无注册人脸"和"人脸不匹配"
            has_registered = len(known_face_encodings) > 0
            self.send_json(200, {
                "code": 200,
                "message": "未匹配到已注册人脸" if has_registered else "系统中尚未注册任何人脸，请先注册",
                "data": {
                    "matched": False,
                    "registered": has_registered,
                    "confidence": round(1 - distance, 3)
                }
            })
            threshold = str(FACE_TOLERANCE) if FACE_RECOGNITION_AVAILABLE else str(OPENCV_SIMILARITY_THRESHOLD)
            print(f"[验证] 未匹配 | 模式={mode} | 最近距离={distance:.4f} | 最高置信度={1-distance:.3f} | 阈值={threshold}")

    def send_json(self, status, data):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def log_message(self, format, *args):
        pass


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


# ==================== 主程序 ====================
def main():
    print("=" * 50)
    print("  河边安全预警系统 - 人脸签到服务")
    print(f"  端口: {PORT}")
    print("=" * 50)

    if not init_face_detector():
        sys.exit(1)

    load_encodings()

    server = ThreadedHTTPServer(("0.0.0.0", PORT), FaceHandler)
    print(f"\n[OK] 人脸服务已启动: http://localhost:{PORT}/")
    print(f"  注册: POST /register")
    print(f"  验证: POST /verify")
    print(f"  状态: GET /status")
    print(f"  已注册: {len(known_face_user_ids)} 人")
    print(f"  模式: {'face_recognition (高精度)' if FACE_RECOGNITION_AVAILABLE else 'OpenCV (基础精度)'}")
    if FACE_RECOGNITION_AVAILABLE:
        print(f"  匹配阈值: {FACE_TOLERANCE} (距离, 越小越严格, 当前: 0~{FACE_TOLERANCE}通过)")
    else:
        print(f"  匹配阈值: {OPENCV_SIMILARITY_THRESHOLD} (相似度, 越大越严格, 当前: {OPENCV_SIMILARITY_THRESHOLD}~1.0通过)")
        print(f"  [!] OpenCV 模式精度有限，建议安装 face_recognition: pip install face_recognition dlib")
    print("=" * 50)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] 服务已停止")


if __name__ == "__main__":
    main()
