USE riverbank_db;

-- Default roles
INSERT INTO t_role (role_name, role_key, permissions) VALUES
('系统管理员', 'admin', '[\"dashboard\",\"device\",\"alert\",\"data\",\"user\",\"role\",\"log\",\"statistics\",\"threshold\"]'),
('巡查工作人员', 'patrol', '[\"dashboard\",\"alert\",\"data\"]');

-- Default admin: admin / admin123 (BCrypt)
INSERT INTO t_user (username, password, real_name, role_id, phone, status) VALUES
('admin', '$2a$10$ENZT7UvVETHBDxJIE0stau.VV8CcScneuCQm9H7Casr2QPimYRq7C', '系统管理员', 1, '13800000000', 1);

-- Demo devices
INSERT INTO t_device (id, location, longitude, latitude, water_threshold, installed_date, description) VALUES
('DEV-001', '东湖公园1号监测点', 114.3524390, 30.5304560, 50.00, '2026-05-28', '东湖公园主河道监测点'),
('DEV-002', '南湖社区河段监测点', 114.3689210, 30.5187650, 45.00, '2026-05-28', '南湖社区沿河步道监测点');
