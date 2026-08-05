-- 植保无人车设备台账（对齐 com.ppdv.api.device.Device 实体，snake_case 列名）
CREATE TABLE device (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id    VARCHAR(64)  NOT NULL COMMENT '设备编号（唯一）',
    name         VARCHAR(128) DEFAULT NULL COMMENT '名称',
    model        VARCHAR(64)  DEFAULT NULL COMMENT '型号',
    status       VARCHAR(16)  DEFAULT 'OFFLINE' COMMENT '状态 ONLINE/OFFLINE',
    firmware     VARCHAR(32)  DEFAULT NULL COMMENT '固件版本',
    secret       VARCHAR(64)  DEFAULT NULL COMMENT '设备密钥（一机一密，MQTT 接入口令）',
    last_seen_at DATETIME     DEFAULT NULL COMMENT '最近在线时间',
    last_lat     DOUBLE       DEFAULT NULL COMMENT '最近纬度 WGS84',
    last_lon     DOUBLE       DEFAULT NULL COMMENT '最近经度 WGS84',
    battery      INT          DEFAULT NULL COMMENT '电量 %',
    mode         VARCHAR(16)  DEFAULT NULL COMMENT '作业模式 MANUAL/AUTO/PAUSED/ESTOP',
    created_at   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='植保无人车设备台账';
