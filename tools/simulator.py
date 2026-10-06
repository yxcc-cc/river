"""
ESP32 河边安全预警系统 - 硬件数据模拟器
模拟 ESP32 设备定时向后端上传水位和入侵数据
"""
import requests
import time
import random
import sys
import json
from datetime import datetime

BASE_URL = "http://localhost:8081"

# 设备配置
DEVICES = [
    {"id": "DEV-001", "name": "东湖公园1号监测点", "base_level": 30.0, "threshold": 50.0},
    {"id": "DEV-001", "name": "东湖公园1号监测点", "base_level": 25.0, "threshold": 45.0},
]

def upload_data(device, water_level, human_detected):
    """上传一条监测数据到后端"""
    payload = {
        "deviceId": device["id"],
        "waterLevel": round(water_level, 2),
        "humanDetected": human_detected,
        "batteryVoltage": round(random.uniform(3.3, 4.2), 2),
    }
    try:
        resp = requests.post(f"{BASE_URL}/api/hardware/data/upload", json=payload, timeout=5)
        data = resp.json()
        status = "OK"
        if data.get("code") != 200:
            status = f"ERR: {data.get('message')}"
    except Exception as e:
        status = f"FAIL: {e}"

    alarm = ""
    if water_level > device["threshold"]:
        alarm = " [!! 水位超限预警 !!]"
    if human_detected:
        alarm += " [!! 人员入侵预警 !!]"

    now = datetime.now().strftime("%H:%M:%S")
    print(f"[{now}] {device['id']} 水位={water_level:.1f}cm 人={'检测到' if human_detected else '无'} {status}{alarm}")

def heartbeat(device):
    """发送心跳"""
    try:
        requests.get(f"{BASE_URL}/api/hardware/heartbeat", params={"deviceId": device["id"]}, timeout=5)
    except:
        pass

def run_simulation():
    print("=" * 60)
    print("  ESP32 河边安全预警系统 - 硬件数据模拟器")
    print("  按 Ctrl+C 停止")
    print("=" * 60)

    cycle = 0
    while True:
        for device in DEVICES:
            # 模拟水位变化: 基础水位 + 随机波动 + 偶尔暴涨
            base = device["base_level"]
            noise = random.uniform(-3, 3)

            # 每20个周期有一次水位暴涨 (模拟汛期)
            if cycle > 0 and cycle % 20 == random.randint(10, 19):
                surge = random.uniform(25, 40)
                water_level = base + surge
            else:
                water_level = base + noise

            water_level = max(0, water_level)

            # 模拟人员入侵: 10% 概率
            human_detected = random.random() < 0.10

            upload_data(device, water_level, human_detected)

            # 每5个周期发一次心跳
            if cycle % 5 == 0:
                heartbeat(device)

        cycle += 1
        time.sleep(3)  # 每3秒上传一次

if __name__ == "__main__":
    print("等待后端启动...")
    for i in range(10):
        try:
            r = requests.get(f"{BASE_URL}/api/auth/info", timeout=3)
            if r.status_code == 200:
                print("后端已就绪!")
                break
        except:
            pass
        time.sleep(2)
    else:
        print("无法连接后端，请确保后端在运行")
        sys.exit(1)

    run_simulation()
