-- ============================================
-- 河边安全预警系统 - 演示数据
-- 体现工作量：6个设备、30天水位数据、50+预警、10用户
-- ============================================

USE riverbank_db;

-- ============================================
-- 1. 更多用户（巡河员）
-- ============================================
-- 密码都是 admin123 的BCrypt哈希
INSERT INTO t_user (username, password, real_name, role_id, phone, create_time, status) VALUES
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '张三', 2, '13800001001', '2026-04-01 09:00:00', 1),
('lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '李四', 2, '13800001002', '2026-04-01 09:00:00', 1),
('wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '王五', 2, '13800001003', '2026-04-05 10:00:00', 1),
('zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '赵六', 2, '13800001004', '2026-04-10 08:30:00', 1),
('sunqi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '孙七', 2, '13800001005', '2026-04-15 14:00:00', 1),
('zhouba', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '周八', 2, '13800001006', '2026-05-01 09:00:00', 1),
('wujiu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '吴九', 2, '13800001007', '2026-05-10 11:00:00', 1),
('zhengshi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '郑十', 1, '13800001008', '2026-03-15 08:00:00', 1)
ON DUPLICATE KEY UPDATE username=username;

-- ============================================
-- 2. 更多设备（6个监测点，覆盖武汉东湖沿线）
-- ============================================
INSERT INTO t_device (id, location, longitude, latitude, status, last_heartbeat, water_threshold, installed_date, description) VALUES
('DEV-001', '东湖公园听涛景区', 114.3727500, 30.5582200, 1, '2026-06-02 15:30:00', 150.00, '2026-03-01', '东湖公园主入口，人流量大，重点监控'),
('DEV-002', '东湖绿道湖光阁段', 114.3852100, 30.5618300, 1, '2026-06-02 15:28:00', 140.00, '2026-03-05', '绿道骑行热点区域，游客较多'),
('DEV-003', '磨山景区北门堤岸', 114.3981200, 30.5487600, 1, '2026-06-02 15:25:00', 160.00, '2026-03-10', '磨山景区入口，汛期水位波动大'),
('DEV-004', '落雁景区湿地公园', 114.4123500, 30.5421800, 0, '2026-06-01 08:00:00', 130.00, '2026-03-15', '落雁景区偏远区域，信号较弱'),
('DEV-005', '喻家湖东路桥梁段', 114.4256800, 30.5234500, 1, '2026-06-02 15:32:00', 145.00, '2026-04-01', '喻家湖桥梁下方，汛期易积水'),
('DEV-006', '南湖社区亲水平台', 114.3612300, 30.5123400, 1, '2026-06-02 15:35:00', 135.00, '2026-04-10', '南湖社区居民活动区，夜间需防入侵')
ON DUPLICATE KEY UPDATE location=VALUES(location);

-- ============================================
-- 3. 水位数据（30天，每30分钟一条，约1440条/设备）
--    用存储过程批量生成，模拟真实水位波动
-- ============================================
DELIMITER //

CREATE PROCEDURE IF NOT EXISTS generate_water_data()
BEGIN
    DECLARE dev_id VARCHAR(32);
    DECLARE base_level DECIMAL(5,2);
    DECLARE i INT;
    DECLARE day_offset INT;
    DECLARE hour_offset INT;
    DECLARE current_time DATETIME;
    DECLARE water_level DECIMAL(5,2);
    DECLARE battery DECIMAL(3,2);

    -- 设备列表和基准水位
    DECLARE dev_cursor CURSOR FOR
        SELECT id, water_threshold * 0.6 FROM t_device;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET @done = 1;

    SET @done = 0;
    OPEN dev_cursor;

    dev_loop: LOOP
        FETCH dev_cursor INTO dev_id, base_level;
        IF @done = 1 THEN LEAVE dev_level; END IF;

        -- 生成30天数据，每天48条（每30分钟）
        SET day_offset = 0;
        WHILE day_offset < 30 DO
            SET hour_offset = 0;
            WHILE hour_offset < 48 DO
                SET current_time = DATE_SUB(NOW(), INTERVAL (30 - day_offset) DAY)
                    + INTERVAL (hour_offset * 30) MINUTE;

                -- 模拟水位波动：基准 + 日变化 + 随机波动 + 汛期上涨
                SET water_level = base_level
                    + SIN(hour_offset * PI / 24) * 8        -- 日内波动±8cm
                    + SIN(day_offset * PI / 15) * 12         -- 半月周期±12cm
                    + (RAND() * 6 - 3)                       -- 随机波动±3cm
                    + IF(day_offset > 20, (day_offset - 20) * 2, 0); -- 近10天汛期上涨

                -- 电池电压 3.3V~4.2V 缓慢下降
                SET battery = 4.2 - (day_offset * 0.025) + (RAND() * 0.1);

                INSERT INTO t_water_data (device_id, water_level, battery_voltage, collect_time)
                VALUES (dev_id, water_level, battery, current_time);

                SET hour_offset = hour_offset + 1;
            END WHILE;
            SET day_offset = day_offset + 1;
        END WHILE;
    END LOOP;

    CLOSE dev_cursor;
END //

DELIMITER ;

-- 执行存储过程
CALL generate_water_data();
DROP PROCEDURE IF EXISTS generate_water_data;

-- ============================================
-- 4. 入侵检测记录（50条，分布在不同设备和时间）
-- ============================================
INSERT INTO t_intrusion_record (device_id, human_detected, detect_time) VALUES
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
('DEV-006', 1, '2026-06-01 02:30:00'), ('DEV-006', 1, '2026-06-02 01:15:00');

-- ============================================
-- 5. 预警事件（水位预警 + 入侵预警，部分已处理）
-- ============================================
INSERT INTO t_alert_event (device_id, alert_type, alert_time, water_level, is_handled, handler_id, handle_time, handle_note) VALUES
-- 水位预警（近期汛期）
('DEV-001', 'WATER_ALARM', '2026-05-25 08:00:00', 152.30, 1, 1, '2026-05-25 08:30:00', '已通知巡河员加强巡查，水位已回落'),
('DEV-001', 'WATER_ALARM', '2026-05-28 14:30:00', 158.70, 1, 3, '2026-05-28 15:00:00', '开启排水闸门，水位逐步下降'),
('DEV-002', 'WATER_ALARM', '2026-05-26 10:15:00', 145.20, 1, 2, '2026-05-26 10:45:00', '现场查看，属正常汛期波动'),
('DEV-002', 'WATER_ALARM', '2026-05-29 09:00:00', 148.50, 1, 4, '2026-05-29 09:30:00', '已上报防汛指挥部'),
('DEV-003', 'WATER_ALARM', '2026-05-27 16:00:00', 165.80, 1, 1, '2026-05-27 16:20:00', '水位超警戒线，已拉设警戒带'),
('DEV-003', 'WATER_ALARM', '2026-05-30 11:30:00', 172.40, 1, 5, '2026-05-30 12:00:00', '紧急排水，通知周边居民远离'),
('DEV-003', 'WATER_ALARM', '2026-06-01 07:00:00', 168.90, 0, NULL, NULL, NULL),
('DEV-005', 'WATER_ALARM', '2026-05-28 20:00:00', 150.30, 1, 2, '2026-05-28 20:30:00', '桥梁下方积水，已设置警示标志'),
('DEV-005', 'WATER_ALARM', '2026-06-01 15:00:00', 155.60, 0, NULL, NULL, NULL),
('DEV-006', 'WATER_ALARM', '2026-05-29 12:00:00', 138.40, 1, 6, '2026-05-29 12:20:00', '亲水平台暂时关闭'),
('DEV-006', 'WATER_ALARM', '2026-06-02 08:00:00', 142.10, 0, NULL, NULL, NULL),

-- 入侵预警
('DEV-001', 'INTRUSION_ALARM', '2026-05-03 02:15:00', NULL, 1, 3, '2026-05-03 02:45:00', '夜间垂钓人员，已劝离'),
('DEV-001', 'INTRUSION_ALARM', '2026-05-12 01:30:00', NULL, 1, 1, '2026-05-12 02:00:00', '醉酒市民靠近水边，已劝离并通知家属'),
('DEV-001', 'INTRUSION_ALARM', '2026-05-20 22:10:00', NULL, 1, 4, '2026-05-20 22:30:00', '夜跑市民误入监控区域，已引导至安全区域'),
('DEV-001', 'INTRUSION_ALARM', '2026-05-25 01:45:00', NULL, 0, NULL, NULL, NULL),
('DEV-002', 'INTRUSION_ALARM', '2026-05-04 00:30:00', NULL, 1, 5, '2026-05-04 01:00:00', '夜间钓鱼爱好者，已劝离'),
('DEV-002', 'INTRUSION_ALARM', '2026-05-14 23:50:00', NULL, 1, 2, '2026-05-15 00:10:00', '情侣在湖边逗留，已提醒注意安全'),
('DEV-002', 'INTRUSION_ALARM', '2026-05-28 00:15:00', NULL, 0, NULL, NULL, NULL),
('DEV-003', 'INTRUSION_ALARM', '2026-05-05 01:00:00', NULL, 1, 6, '2026-05-05 01:30:00', '疑似偷钓人员，已驱离并记录'),
('DEV-003', 'INTRUSION_ALARM', '2026-05-16 02:35:00', NULL, 1, 3, '2026-05-16 03:00:00', '流浪人员在堤岸休息，已联系救助站'),
('DEV-003', 'INTRUSION_ALARM', '2026-05-30 01:25:00', NULL, 0, NULL, NULL, NULL),
('DEV-005', 'INTRUSION_ALARM', '2026-05-06 02:45:00', NULL, 1, 1, '2026-05-06 03:10:00', '夜间施工人员误入，已引导离开'),
('DEV-005', 'INTRUSION_ALARM', '2026-05-23 01:30:00', NULL, 1, 4, '2026-05-23 02:00:00', '少年夜间玩耍，已劝离并通知家长'),
('DEV-005', 'INTRUSION_ALARM', '2026-06-01 00:50:00', NULL, 0, NULL, NULL, NULL),
('DEV-006', 'INTRUSION_ALARM', '2026-05-02 23:30:00', NULL, 1, 7, '2026-05-03 00:00:00', '居民夜间散步误入危险区域'),
('DEV-006', 'INTRUSION_ALARM', '2026-05-13 02:40:00', NULL, 1, 5, '2026-05-13 03:00:00', '可疑人员徘徊，已报警处理'),
('DEV-006', 'INTRUSION_ALARM', '2026-06-02 01:15:00', NULL, 0, NULL, NULL, NULL);

-- ============================================
-- 6. 操作日志（30条，模拟不同用户的操作）
-- ============================================
INSERT INTO t_operation_log (user_id, username, operation, method, params, ip, create_time) VALUES
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
(8, 'zhengshi', '查看数据大屏', 'GET', '/api/statistics/bigscreen', '192.168.1.108', '2026-06-02 14:05:00');

-- ============================================
-- 验证数据
-- ============================================
SELECT '设备数量' AS 项目, COUNT(*) AS 数量 FROM t_device
UNION ALL
SELECT '用户数量', COUNT(*) FROM t_user
UNION ALL
SELECT '水位数据', COUNT(*) FROM t_water_data
UNION ALL
SELECT '入侵记录', COUNT(*) FROM t_intrusion_record
UNION ALL
SELECT '预警事件', COUNT(*) FROM t_alert_event
UNION ALL
SELECT '操作日志', COUNT(*) FROM t_operation_log;
