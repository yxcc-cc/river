"""
演示数据生成脚本 - 河边安全预警系统
生成：6个设备、30天水位数据、入侵记录、预警事件、操作日志
"""
import os
import pymysql
import random
import math
from datetime import datetime, timedelta

DB_CONFIG = {
    'host': os.environ.get('DB_HOST', 'localhost'),
    'port': int(os.environ.get('DB_PORT', '3306')),
    'user': os.environ.get('DB_USER', 'root'),
    # 口令不要写死在源码里，请设置环境变量 DB_PASSWORD
    'password': os.environ.get('DB_PASSWORD', ''),
    'database': os.environ.get('DB_NAME', 'riverbank_db'),
    'charset': 'utf8mb4'
}

def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    print("=" * 50)
    print("  河边安全预警系统 - 演示数据生成")
    print("=" * 50)

    # 清空旧数据（保留admin和原始123456用户）
    print("[0/6] 清空旧数据...")
    cursor.execute("DELETE FROM t_operation_log")
    cursor.execute("DELETE FROM t_alert_event")
    cursor.execute("DELETE FROM t_intrusion_record")
    cursor.execute("DELETE FROM t_water_data")
    cursor.execute("DELETE FROM t_device WHERE id != 'NONE'")
    cursor.execute("DELETE FROM t_user WHERE username NOT IN ('admin', '123456')")
    conn.commit()

    # ========== 1. 更多用户 ==========
    print("[1/6] 生成用户数据...")
    # 统一使用 admin123 的BCrypt哈希
    pwd_hash = '$2b$12$4DyAJBuasAoOPU1dYv7QyuhmjqHGJm2JtO5Ytdp1g9DVLZgb1ng.C'
    users = [
        ('zhangsan', pwd_hash, '张三', 2, '13800001001'),
        ('lisi', pwd_hash, '李四', 2, '13800001002'),
        ('wangwu', pwd_hash, '王五', 2, '13800001003'),
        ('zhaoliu', pwd_hash, '赵六', 2, '13800001004'),
        ('sunqi', pwd_hash, '孙七', 2, '13800001005'),
        ('zhouba', pwd_hash, '周八', 2, '13800001006'),
        ('wujiu', pwd_hash, '吴九', 2, '13800001007'),
        ('zhengshi', pwd_hash, '郑十', 1, '13800001008'),
    ]
    for u in users:
        cursor.execute("""
            INSERT IGNORE INTO t_user (username, password, real_name, role_id, phone, create_time, status)
            VALUES (%s, %s, %s, %s, %s, NOW(), 1)
        """, u)
    print(f"    -> 用户 {len(users)} 条")

    # ========== 2. 更多设备（九江市公园） ==========
    print("[2/6] 生成设备数据...")
    devices = [
        ('DEV-001', '甘棠湖公园东岸', 115.9810000, 29.7080000, 1, 150.00, '2026-03-01', '甘棠湖东岸步道，游客密集，重点监控'),
        ('DEV-002', '南湖公园广场段', 115.9735000, 29.7020000, 1, 140.00, '2026-03-05', '南湖公园广场亲水区域，夜间需防入侵'),
        ('DEV-003', '八里湖大桥南侧', 115.9350000, 29.6620000, 1, 160.00, '2026-03-10', '八里湖南岸堤坝，汛期水位波动大'),
        ('DEV-004', '八里湖湿地公园', 115.9280000, 29.6550000, 0, 130.00, '2026-03-15', '八里湖西侧湿地，信号较弱'),
        ('DEV-005', '浔阳楼长江堤岸', 115.9920000, 29.7280000, 1, 145.00, '2026-04-01', '浔阳楼附近长江堤岸，汛期水位变化大'),
        ('DEV-006', '烟水亭湖畔', 115.9845000, 29.7105000, 1, 135.00, '2026-04-10', '烟水亭景区湖畔，居民晨练聚集区'),
    ]
    for d in devices:
        cursor.execute("""
            INSERT INTO t_device (id, location, longitude, latitude, status, last_heartbeat, water_threshold, installed_date, description)
            VALUES (%s, %s, %s, %s, %s, NOW(), %s, %s, %s)
            ON DUPLICATE KEY UPDATE location=VALUES(location), longitude=VALUES(longitude), latitude=VALUES(latitude)
        """, d)
    print(f"    -> 设备 {len(devices)} 条")

    # ========== 3. 水位数据（30天，每30分钟一条） ==========
    print("[3/6] 生成水位数据（约8640条，请稍候）...")
    now = datetime.now()
    water_data = []
    for dev_id, _, _, _, _, threshold, _, _ in devices:
        base_level = float(threshold) * 0.55  # 基准水位约为阈值的55%
        for day in range(30):
            for half_hour in range(48):
                t = now - timedelta(days=30 - day) + timedelta(minutes=half_hour * 30)
                # 模拟水位波动
                level = base_level
                level += math.sin(half_hour * math.pi / 24) * 8       # 日内波动
                level += math.sin(day * math.pi / 15) * 12             # 半月周期
                level += random.uniform(-3, 3)                         # 随机波动
                if day > 20:                                            # 近10天汛期上涨
                    level += (day - 20) * 2.5
                level = round(max(level, 30.0), 2)
                battery = round(4.2 - day * 0.025 + random.uniform(-0.05, 0.05), 2)
                water_data.append((dev_id, level, battery, t.strftime('%Y-%m-%d %H:%M:%S')))

    # 批量插入
    cursor.executemany("""
        INSERT INTO t_water_data (device_id, water_level, battery_voltage, collect_time)
        VALUES (%s, %s, %s, %s)
    """, water_data)
    print(f"    -> 水位数据 {len(water_data)} 条")

    # ========== 4. 入侵记录 ==========
    print("[4/6] 生成入侵记录...")
    intrusion_data = [
        ('DEV-001', 1, '2026-05-03 02:15:00'), ('DEV-001', 1, '2026-05-03 02:16:00'),
        ('DEV-001', 1, '2026-05-07 23:45:00'), ('DEV-001', 0, '2026-05-08 00:10:00'),
        ('DEV-001', 1, '2026-05-12 01:30:00'), ('DEV-001', 1, '2026-05-15 03:20:00'),
        ('DEV-001', 1, '2026-05-20 22:10:00'), ('DEV-001', 1, '2026-05-25 01:45:00'),
        ('DEV-002', 1, '2026-05-04 00:30:00'), ('DEV-002', 1, '2026-05-04 00:31:00'),
        ('DEV-002', 1, '2026-05-09 02:15:00'), ('DEV-002', 0, '2026-05-09 02:30:00'),
        ('DEV-002', 1, '2026-05-14 23:50:00'), ('DEV-002', 1, '2026-05-18 01:20:00'),
        ('DEV-002', 1, '2026-05-22 03:40:00'), ('DEV-002', 1, '2026-05-28 00:15:00'),
        ('DEV-003', 1, '2026-05-05 01:00:00'), ('DEV-003', 1, '2026-05-05 01:01:00'),
        ('DEV-003', 1, '2026-05-10 04:20:00'), ('DEV-003', 1, '2026-05-16 02:35:00'),
        ('DEV-003', 0, '2026-05-16 02:50:00'), ('DEV-003', 1, '2026-05-21 00:45:00'),
        ('DEV-003', 1, '2026-05-26 03:10:00'), ('DEV-003', 1, '2026-05-30 01:25:00'),
        ('DEV-005', 1, '2026-05-06 02:45:00'), ('DEV-005', 1, '2026-05-11 00:20:00'),
        ('DEV-005', 1, '2026-05-17 03:55:00'), ('DEV-005', 0, '2026-05-17 04:10:00'),
        ('DEV-005', 1, '2026-05-23 01:30:00'), ('DEV-005', 1, '2026-05-27 23:40:00'),
        ('DEV-005', 1, '2026-05-31 02:15:00'), ('DEV-005', 1, '2026-06-01 00:50:00'),
        ('DEV-006', 1, '2026-05-02 23:30:00'), ('DEV-006', 1, '2026-05-02 23:31:00'),
        ('DEV-006', 1, '2026-05-08 01:15:00'), ('DEV-006', 1, '2026-05-13 02:40:00'),
        ('DEV-006', 0, '2026-05-13 02:55:00'), ('DEV-006', 1, '2026-05-19 00:25:00'),
        ('DEV-006', 1, '2026-05-24 03:50:00'), ('DEV-006', 1, '2026-05-29 01:05:00'),
        ('DEV-006', 1, '2026-06-01 02:30:00'), ('DEV-006', 1, '2026-06-02 01:15:00'),
    ]
    for rec in intrusion_data:
        cursor.execute("""
            INSERT INTO t_intrusion_record (device_id, human_detected, detect_time)
            VALUES (%s, %s, %s)
        """, rec)
    print(f"    -> 入侵记录 {len(intrusion_data)} 条")

    # ========== 5. 预警事件 ==========
    print("[5/6] 生成预警事件...")
    alerts = [
        # 水位预警
        ('DEV-001', 'WATER_ALARM', '2026-05-25 08:00:00', 152.30, 1, 1, '2026-05-25 08:30:00', '已通知巡河员加强巡查，水位已回落'),
        ('DEV-001', 'WATER_ALARM', '2026-05-28 14:30:00', 158.70, 1, 3, '2026-05-28 15:00:00', '开启排水闸门，水位逐步下降'),
        ('DEV-002', 'WATER_ALARM', '2026-05-26 10:15:00', 145.20, 1, 2, '2026-05-26 10:45:00', '现场查看，属正常汛期波动'),
        ('DEV-002', 'WATER_ALARM', '2026-05-29 09:00:00', 148.50, 1, 4, '2026-05-29 09:30:00', '已上报防汛指挥部'),
        ('DEV-003', 'WATER_ALARM', '2026-05-27 16:00:00', 165.80, 1, 1, '2026-05-27 16:20:00', '水位超警戒线，已拉设警戒带'),
        ('DEV-003', 'WATER_ALARM', '2026-05-30 11:30:00', 172.40, 1, 5, '2026-05-30 12:00:00', '紧急排水，通知周边居民远离'),
        ('DEV-003', 'WATER_ALARM', '2026-06-01 07:00:00', 168.90, 0, None, None, None),
        ('DEV-005', 'WATER_ALARM', '2026-05-28 20:00:00', 150.30, 1, 2, '2026-05-28 20:30:00', '桥梁下方积水，已设置警示标志'),
        ('DEV-005', 'WATER_ALARM', '2026-06-01 15:00:00', 155.60, 0, None, None, None),
        ('DEV-006', 'WATER_ALARM', '2026-05-29 12:00:00', 138.40, 1, 6, '2026-05-29 12:20:00', '亲水平台暂时关闭'),
        ('DEV-006', 'WATER_ALARM', '2026-06-02 08:00:00', 142.10, 0, None, None, None),
        # 入侵预警
        ('DEV-001', 'INTRUSION_ALARM', '2026-05-03 02:15:00', None, 1, 3, '2026-05-03 02:45:00', '夜间垂钓人员，已劝离'),
        ('DEV-001', 'INTRUSION_ALARM', '2026-05-12 01:30:00', None, 1, 1, '2026-05-12 02:00:00', '醉酒市民靠近水边，已劝离并通知家属'),
        ('DEV-001', 'INTRUSION_ALARM', '2026-05-20 22:10:00', None, 1, 4, '2026-05-20 22:30:00', '夜跑市民误入监控区域，已引导至安全区域'),
        ('DEV-001', 'INTRUSION_ALARM', '2026-05-25 01:45:00', None, 0, None, None, None),
        ('DEV-002', 'INTRUSION_ALARM', '2026-05-04 00:30:00', None, 1, 5, '2026-05-04 01:00:00', '夜间钓鱼爱好者，已劝离'),
        ('DEV-002', 'INTRUSION_ALARM', '2026-05-14 23:50:00', None, 1, 2, '2026-05-15 00:10:00', '情侣在湖边逗留，已提醒注意安全'),
        ('DEV-002', 'INTRUSION_ALARM', '2026-05-28 00:15:00', None, 0, None, None, None),
        ('DEV-003', 'INTRUSION_ALARM', '2026-05-05 01:00:00', None, 1, 6, '2026-05-05 01:30:00', '疑似偷钓人员，已驱离并记录'),
        ('DEV-003', 'INTRUSION_ALARM', '2026-05-16 02:35:00', None, 1, 3, '2026-05-16 03:00:00', '流浪人员在堤岸休息，已联系救助站'),
        ('DEV-003', 'INTRUSION_ALARM', '2026-05-30 01:25:00', None, 0, None, None, None),
        ('DEV-005', 'INTRUSION_ALARM', '2026-05-06 02:45:00', None, 1, 1, '2026-05-06 03:10:00', '夜间施工人员误入，已引导离开'),
        ('DEV-005', 'INTRUSION_ALARM', '2026-05-23 01:30:00', None, 1, 4, '2026-05-23 02:00:00', '少年夜间玩耍，已劝离并通知家长'),
        ('DEV-005', 'INTRUSION_ALARM', '2026-06-01 00:50:00', None, 0, None, None, None),
        ('DEV-006', 'INTRUSION_ALARM', '2026-05-02 23:30:00', None, 1, 7, '2026-05-03 00:00:00', '居民夜间散步误入危险区域'),
        ('DEV-006', 'INTRUSION_ALARM', '2026-05-13 02:40:00', None, 1, 5, '2026-05-13 03:00:00', '可疑人员徘徊，已报警处理'),
        ('DEV-006', 'INTRUSION_ALARM', '2026-06-02 01:15:00', None, 0, None, None, None),
    ]
    for a in alerts:
        cursor.execute("""
            INSERT INTO t_alert_event (device_id, alert_type, alert_time, water_level, is_handled, handler_id, handle_time, handle_note)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """, a)
    print(f"    -> 预警事件 {len(alerts)} 条")

    # ========== 6. 操作日志 ==========
    print("[6/6] 生成操作日志...")
    logs = [
        (1, 'admin', '用户登录', 'POST', '/api/auth/login', '192.168.1.100', '2026-06-01 08:00:00'),
        (1, 'admin', '查看设备列表', 'GET', '/api/device/list', '192.168.1.100', '2026-06-01 08:05:00'),
        (1, 'admin', '查看预警记录', 'GET', '/api/alert/list', '192.168.1.100', '2026-06-01 08:10:00'),
        (1, 'admin', '处理预警 #25', 'PUT', '/api/alert/handle/25', '192.168.1.100', '2026-06-01 08:15:00'),
        (1, 'admin', '导出预警数据', 'GET', '/api/alert/export', '192.168.1.100', '2026-06-01 08:20:00'),
        (1, 'admin', '查看统计数据', 'GET', '/api/statistics/overview', '192.168.1.100', '2026-06-01 08:25:00'),
        (2, 'zhangsan', '用户登录', 'POST', '/api/auth/login', '192.168.1.101', '2026-06-01 09:00:00'),
        (2, 'zhangsan', '查看预警列表', 'GET', '/api/alert/list', '192.168.1.101', '2026-06-01 09:05:00'),
        (2, 'zhangsan', '处理预警 #26', 'PUT', '/api/alert/handle/26', '192.168.1.101', '2026-06-01 09:30:00'),
        (2, 'zhangsan', '上传巡查截图', 'POST', '/api/hardware/image/upload', '192.168.1.101', '2026-06-01 10:00:00'),
        (3, 'lisi', '用户登录', 'POST', '/api/auth/login', '192.168.1.102', '2026-06-01 10:00:00'),
        (3, 'lisi', '查看水位数据', 'GET', '/api/data/water?deviceId=DEV-001', '192.168.1.102', '2026-06-01 10:10:00'),
        (3, 'lisi', '导出水位数据', 'GET', '/api/data/export', '192.168.1.102', '2026-06-01 10:15:00'),
        (3, 'lisi', '处理预警 #27', 'PUT', '/api/alert/handle/27', '192.168.1.102', '2026-06-01 10:30:00'),
        (4, 'wangwu', '用户登录', 'POST', '/api/auth/login', '192.168.1.103', '2026-06-01 14:00:00'),
        (4, 'wangwu', '查看地图设备', 'GET', '/api/device/list', '192.168.1.103', '2026-06-01 14:05:00'),
        (4, 'wangwu', '处理预警 #28', 'PUT', '/api/alert/handle/28', '192.168.1.103', '2026-06-01 14:30:00'),
        (5, 'zhaoliu', '用户登录', 'POST', '/api/auth/login', '192.168.1.104', '2026-06-01 15:00:00'),
        (5, 'zhaoliu', '查看入侵记录', 'GET', '/api/intrusion/list', '192.168.1.104', '2026-06-01 15:05:00'),
        (5, 'zhaoliu', '处理预警 #29', 'PUT', '/api/alert/handle/29', '192.168.1.104', '2026-06-01 15:30:00'),
        (1, 'admin', '新增设备 DEV-006', 'POST', '/api/device/add', '192.168.1.100', '2026-06-02 09:00:00'),
        (1, 'admin', '修改用户权限', 'PUT', '/api/user/update/7', '192.168.1.100', '2026-06-02 09:15:00'),
        (1, 'admin', '查看操作日志', 'GET', '/api/log/list', '192.168.1.100', '2026-06-02 09:30:00'),
        (6, 'sunqi', '用户登录', 'POST', '/api/auth/login', '192.168.1.105', '2026-06-02 10:00:00'),
        (6, 'sunqi', '查看预警列表', 'GET', '/api/alert/list', '192.168.1.105', '2026-06-02 10:05:00'),
        (6, 'sunqi', '处理预警 #30', 'PUT', '/api/alert/handle/30', '192.168.1.105', '2026-06-02 10:30:00'),
        (1, 'admin', '清空历史截图', 'DELETE', '/api/hardware/captures/clear', '192.168.1.100', '2026-06-02 11:00:00'),
        (1, 'admin', '系统配置修改', 'PUT', '/api/system/config', '192.168.1.100', '2026-06-02 11:15:00'),
        (8, 'zhengshi', '用户登录', 'POST', '/api/auth/login', '192.168.1.108', '2026-06-02 14:00:00'),
        (8, 'zhengshi', '查看数据大屏', 'GET', '/api/statistics/bigscreen', '192.168.1.108', '2026-06-02 14:05:00'),
    ]
    for l in logs:
        cursor.execute("""
            INSERT INTO t_operation_log (user_id, username, operation, method, params, ip, create_time)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, l)
    print(f"    -> 操作日志 {len(logs)} 条")

    conn.commit()

    # 统计
    print("\n" + "=" * 50)
    print("  导入完成！数据统计：")
    print("=" * 50)
    for table in ['t_device', 't_user', 't_water_data', 't_intrusion_record', 't_alert_event', 't_operation_log']:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        print(f"  {table:25s} {count:>6d} 条")

    cursor.close()
    conn.close()
    print("\n[✓] 全部完成！")

if __name__ == '__main__':
    main()
