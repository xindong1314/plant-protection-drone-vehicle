-- =============================================================
-- 跟踪（V14）修订增量
-- V14 已在开发库/CI 执行过，按“不改已执行迁移”约定，其后对跟踪表的
-- 结构修订统一由本迁移承载：
--   1. coer_track_session 补 last_activity_ts（空闲清扫按服务器时钟判定）
--   2. 两张跟踪表 device_id 放宽到 64，与 coer_spray_task / coer_inspection_record 一致
-- 注释表述的调整不落库。
-- =============================================================

ALTER TABLE coer_track_session
    MODIFY COLUMN device_id VARCHAR(64) NOT NULL COMMENT '车端编号（与 device.device_id 一致）',
    ADD COLUMN last_activity_ts BIGINT NOT NULL COMMENT '服务器侧最后活动时间（epoch ms，apply 时刷新；空闲清扫按此判定，车端时钟超前不影响）' AFTER end_reason;

ALTER TABLE coer_detection_track
    MODIFY COLUMN device_id VARCHAR(64) NOT NULL COMMENT '车端（与 device.device_id 一致）';
