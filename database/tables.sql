USE riverbank_db;

CREATE TABLE t_role (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT 'role ID',
    role_name   VARCHAR(50)  NOT NULL COMMENT 'role name',
    role_key    VARCHAR(50)  NOT NULL COMMENT 'role key',
    permissions TEXT                  COMMENT 'permissions JSON',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role table';

CREATE TABLE t_user (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT 'user ID',
    username    VARCHAR(50)  NOT NULL COMMENT 'login username',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt password',
    real_name   VARCHAR(50)           COMMENT 'real name',
    role_id     INT          NOT NULL COMMENT 'role id',
    phone       VARCHAR(20)           COMMENT 'phone number',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-active, 0-disabled',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user table';

CREATE TABLE t_device (
    id               VARCHAR(32)   NOT NULL COMMENT 'device ID, e.g. DEV-001',
    location         VARCHAR(100)  NOT NULL COMMENT 'deploy location',
    longitude        DECIMAL(10,7)          COMMENT 'longitude',
    latitude         DECIMAL(10,7)          COMMENT 'latitude',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0-offline, 1-online',
    last_heartbeat   DATETIME               COMMENT 'last heartbeat time',
    water_threshold  DECIMAL(5,2)           COMMENT 'water alarm threshold cm',
    installed_date   DATE                   COMMENT 'install date',
    description      VARCHAR(255)           COMMENT 'description',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='device table';

CREATE TABLE t_water_data (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    device_id       VARCHAR(32)   NOT NULL COMMENT 'device id FK',
    water_level     DECIMAL(5,2)  NOT NULL COMMENT 'water level cm',
    battery_voltage DECIMAL(3,2)           COMMENT 'battery voltage',
    collect_time    DATETIME      NOT NULL COMMENT 'collect time',
    PRIMARY KEY (id),
    KEY idx_device_collect (device_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='water level data table';

CREATE TABLE t_intrusion_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    device_id     VARCHAR(32)  NOT NULL COMMENT 'device id FK',
    human_detected TINYINT     NOT NULL COMMENT '1-detected, 0-none',
    detect_time   DATETIME     NOT NULL COMMENT 'detect time',
    PRIMARY KEY (id),
    KEY idx_device_detect (device_id, detect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='intrusion record table';

CREATE TABLE t_alert_event (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    device_id    VARCHAR(32)   NOT NULL COMMENT 'device id FK',
    alert_type   VARCHAR(20)   NOT NULL COMMENT 'WATER_ALARM / INTRUSION_ALARM',
    alert_time   DATETIME      NOT NULL COMMENT 'alert trigger time',
    water_level  DECIMAL(5,2)           COMMENT 'water level at alert',
    is_handled   TINYINT       NOT NULL DEFAULT 0 COMMENT '0-pending, 1-handled',
    handler_id   INT                    COMMENT 'handler user ID',
    handle_time  DATETIME               COMMENT 'handle time',
    handle_note  VARCHAR(255)           COMMENT 'handle note',
    PRIMARY KEY (id),
    KEY idx_device_alert (device_id, alert_time),
    KEY idx_is_handled (is_handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='alert event table';

CREATE TABLE t_operation_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    user_id      INT          NOT NULL COMMENT 'operator user ID',
    username     VARCHAR(50)  NOT NULL COMMENT 'operator username',
    operation    VARCHAR(200) NOT NULL COMMENT 'operation description',
    method       VARCHAR(200)          COMMENT 'request method',
    params       TEXT                  COMMENT 'request params',
    ip           VARCHAR(50)           COMMENT 'operator IP',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'operate time',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation log table';

CREATE TABLE t_attendance (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    user_id     INT          NOT NULL COMMENT 'user ID',
    check_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'check-in/out time',
    type        TINYINT      NOT NULL COMMENT '1-check-in, 2-check-out',
    face_image  VARCHAR(255)          COMMENT 'face capture image path',
    longitude   DECIMAL(10,7)         COMMENT 'longitude',
    latitude    DECIMAL(10,7)         COMMENT 'latitude',
    location    VARCHAR(100)          COMMENT 'location description',
    remark      VARCHAR(255)          COMMENT 'remark',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='attendance record table';
