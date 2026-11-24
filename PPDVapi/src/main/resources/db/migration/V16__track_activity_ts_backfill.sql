-- =============================================================
-- V15 给 coer_track_session 新增 last_activity_ts NOT NULL 且无默认值，
-- 升级前已存在的行会得到 0：空闲清扫按 now - last_activity_ts 判定，
-- 这些存量 ACTIVE 会话会在首次清扫时被全部误判为"空闲"结束并广播。
-- 本迁移用已有时间信息回填（不改已执行的 V15，避免 Flyway 校验和不一致）。
-- 回填优先级：update_time → end_ts → start_ts；幂等，只处理仍为 0 的行。
-- =============================================================

UPDATE coer_track_session
SET last_activity_ts = COALESCE(UNIX_TIMESTAMP(update_time) * 1000, end_ts, start_ts)
WHERE last_activity_ts = 0;
