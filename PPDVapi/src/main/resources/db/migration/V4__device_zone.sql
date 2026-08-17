-- 设备表追加 current_zone 字段（小车 ArUco/二维码识别后上报当前区域编号）
ALTER TABLE device ADD COLUMN current_zone VARCHAR(16) DEFAULT NULL COMMENT '当前所在区域编号（A1, B2...）' AFTER mode;
