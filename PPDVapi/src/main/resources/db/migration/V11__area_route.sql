-- =============================================================
-- V11: 区域作业路线（小车随 tele/state 遥测录制，CGCS2000）
--   1. coer_greenhouse_area 新增路线列：route_json / route_recorded_at；
--   2. 清理 V3 种子演示区域（A1~B3）及其档案：演示区域无真实边界与路线，
--      避免残留无法用于路线录制的空壳数据。
-- =============================================================

ALTER TABLE coer_greenhouse_area
    ADD COLUMN route_json LONGTEXT NULL COMMENT '作业路线 [[lng,lat],...]（CGCS2000，由车辆遥测录制）' AFTER coordinate_system;

ALTER TABLE coer_greenhouse_area
    ADD COLUMN route_recorded_at datetime NULL COMMENT '路线录入完成时间' AFTER route_json;

-- 先删引用区域的档案，再删区域本身
DELETE FROM coer_greenhouse_archive
WHERE area_id IN (SELECT id FROM coer_greenhouse_area WHERE area_code IN ('A1','A2','A3','B1','B2','B3'));

DELETE FROM coer_greenhouse_area
WHERE area_code IN ('A1','A2','A3','B1','B2','B3');
