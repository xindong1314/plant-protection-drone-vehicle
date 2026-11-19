-- =============================================================
-- V12: 清理 V11 删除种子区域（A1~B3）后残留的悬空引用（v1.1 评审 S5）
--   V11 按 area_code 直接删除了 A1~B3 区域及其档案，但未处理
--   device.current_zone 的引用。本迁移把指向已不存在区域的
--   current_zone 置空（自建同名区域不受影响，幂等安全）。
--   历史业务行（点喷/巡检/复查等）按 v1.1 版本记录的处理策略保留，
--   运行期按"区域不存在"容错处理，不做破坏性删除。
-- =============================================================

UPDATE device
SET current_zone = NULL
WHERE current_zone IN ('A1', 'A2', 'A3', 'B1', 'B2', 'B3')
  AND NOT EXISTS (
      SELECT 1 FROM coer_greenhouse_area a WHERE a.area_code = device.current_zone
  );
