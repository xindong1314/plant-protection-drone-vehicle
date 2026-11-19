-- =============================================================
-- V13: 区域路线点数冗余列（评审修复#11）
--   coer_greenhouse_area 新增 route_point_count：由 saveRecordedRoute
--   落库时维护，区域列表摘要改读该列，不再对最长 512KB 的
--   route_json 逐行 JSON_LENGTH 解析。
-- =============================================================

ALTER TABLE coer_greenhouse_area
    ADD COLUMN route_point_count INT NULL COMMENT '作业路线点数（冗余列，落库时维护）' AFTER route_json;

-- 回填存量路线的点数；坏 JSON 置 NULL（列表按 0 处理），避免回填中断
UPDATE coer_greenhouse_area
SET route_point_count = CASE WHEN JSON_VALID(route_json) THEN JSON_LENGTH(route_json) END
WHERE route_json IS NOT NULL;
