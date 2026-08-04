-- ============================================================
-- PPDVweb 报表明细测试数据
-- ============================================================
--
-- 执行方式（必须带字符集参数！否则中文变乱码）：
--   mysql -u ppdv -pppdv123 -h 127.0.0.1 -P 13306 --default-character-set=utf8mb4 ppdv < test-data.sql
--
-- 说明：
--   - 所有数据时间在 2026-07 范围内，便于用时间筛选器验证
--   - 先清脏后插入，可重复执行
--   - 覆盖 3 种风险等级、4 种识别类别、3 种任务状态
--   - 手动执行，不走 Flyway

SET NAMES utf8mb4;

-- ============================================================
-- 0. 清理旧数据（避免拉丁-1 脏数据残留）
-- ============================================================
DELETE FROM coer_detection_result WHERE id BETWEEN 601 AND 608;
DELETE FROM coer_inspection_image WHERE id BETWEEN 501 AND 504;
DELETE FROM coer_inspection_record WHERE id BETWEEN 101 AND 105;
DELETE FROM coer_review_record WHERE id BETWEEN 801 AND 802;
DELETE FROM coer_spray_task WHERE id BETWEEN 701 AND 703;
DELETE FROM coer_greenhouse_archive WHERE area_id IN (SELECT id FROM coer_greenhouse_area WHERE area_code IN ('A1', 'B2', 'C1', 'C2', 'C3'));
DELETE FROM coer_greenhouse_area WHERE area_code IN ('A1', 'B2', 'C1', 'C2', 'C3');
DELETE FROM device WHERE device_id = 'PPDV-0001';

-- ============================================================
-- 1. 温室区域（3 个不同风险等级）
-- ============================================================
-- Flyway 种子已有 A1-A3, B1-B3（默认 NORMAL），这里追加 3 个并更新风险等级
INSERT IGNORE INTO coer_greenhouse_area (area_code, area_name, greenhouse_name, risk_status, sort_order, create_by, create_time)
VALUES
  ('C1', 'C1 区', '模拟大棚 2 号', 'NORMAL',      7, 'admin', '2026-07-01 08:00:00'),
  ('C2', 'C2 区', '模拟大棚 2 号', 'SUSPICIOUS', 8, 'admin', '2026-07-01 08:00:00'),
  ('C3', 'C3 区', '模拟大棚 2 号', 'HIGH_RISK',  9, 'admin', '2026-07-01 08:00:00');

-- 已有种子数据的 A 区也更新风险等级，确保 NORMAL / SUSPICIOUS / HIGH_RISK 各至少一个
UPDATE coer_greenhouse_area SET risk_status = 'SUSPICIOUS' WHERE area_code = 'A2';
UPDATE coer_greenhouse_area SET risk_status = 'HIGH_RISK'  WHERE area_code = 'A3';

-- ============================================================
-- 2. 设备（已有则跳过）
-- ============================================================
INSERT INTO device (device_id, name, model, status, firmware, secret, created_at)
VALUES ('PPDV-0001', '无人车 1 号', 'PPDV-MK1', 'OFFLINE', '1.2.0',
        'test-secret-0001', NOW());

-- ============================================================
--   取区域 ID（后续 INSERT 复用）
-- ============================================================
SET @a1 = (SELECT id FROM coer_greenhouse_area WHERE area_code = 'A1');
SET @a2 = (SELECT id FROM coer_greenhouse_area WHERE area_code = 'A2');
SET @a3 = (SELECT id FROM coer_greenhouse_area WHERE area_code = 'A3');
SET @b1 = (SELECT id FROM coer_greenhouse_area WHERE area_code = 'B1');
SET @b2 = (SELECT id FROM coer_greenhouse_area WHERE area_code = 'B2');

-- ============================================================
-- 3. 巡检记录（5 条：4 COMPLETED + 1 IN_PROGRESS）
-- ============================================================
INSERT IGNORE INTO coer_inspection_record
  (id, device_id, area_id, start_time, end_time, image_count, anomaly_count, status, create_by, create_time)
VALUES
  (101, 'PPDV-0001', @a1, '2026-07-05 09:00:00', '2026-07-05 09:45:00', 12, 3, 'COMPLETED',   'admin', '2026-07-05 09:00:00'),
  (102, 'PPDV-0001', @a2, '2026-07-10 14:00:00', '2026-07-10 14:50:00', 20, 5, 'COMPLETED',   'admin', '2026-07-10 14:00:00'),
  (103, 'PPDV-0001', @a3, '2026-07-15 10:00:00', '2026-07-15 10:35:00', 15, 2, 'COMPLETED',   'admin', '2026-07-15 10:00:00'),
  (104, 'PPDV-0001', @b1, '2026-07-20 08:00:00', '2026-07-20 08:55:00', 18, 4, 'COMPLETED',   'admin', '2026-07-20 08:00:00'),
  (105, 'PPDV-0001', @b2, '2026-07-25 11:00:00', NULL,                       8, 0, 'IN_PROGRESS', 'admin', '2026-07-25 11:00:00');

-- ============================================================
-- 4. 巡检图片（为识别结果提供 image_id 外键）
-- ============================================================
INSERT IGNORE INTO coer_inspection_image
  (id, inspection_id, area_id, file_path, captured_at, create_by, create_time)
VALUES
  (501, 101, @a1, '/uploads/2026-07-05/img_a1_01.jpg', '2026-07-05 09:10:00', 'admin', '2026-07-05 09:10:00'),
  (502, 102, @a2, '/uploads/2026-07-10/img_a2_01.jpg', '2026-07-10 14:15:00', 'admin', '2026-07-10 14:15:00'),
  (503, 103, @a3, '/uploads/2026-07-15/img_a3_01.jpg', '2026-07-15 10:10:00', 'admin', '2026-07-15 10:10:00'),
  (504, 104, @b1, '/uploads/2026-07-20/img_b1_01.jpg', '2026-07-20 08:20:00', 'admin', '2026-07-20 08:20:00');

-- ============================================================
-- 5. 识别结果（8 条，4 种类别：病斑/虫害/黄化/萎蔫）
-- ============================================================
INSERT IGNORE INTO coer_detection_result
  (id, image_id, area_id, category, confidence, box_x, box_y, box_w, box_h, status, create_by, create_time)
VALUES
  (601, 501, @a1, '病斑', 0.92, 100, 200, 80, 60, 'CONFIRMED', 'admin', '2026-07-05 09:15:00'),
  (602, 501, @a1, '虫害', 0.85, 300, 150, 50, 40, 'CONFIRMED', 'admin', '2026-07-05 09:15:00'),
  (603, 501, @a1, '黄化', 0.78, 200, 400, 70, 55, 'PENDING',   'admin', '2026-07-05 09:16:00'),
  (604, 502, @a2, '病斑', 0.95, 150, 250, 60, 70, 'CONFIRMED', 'admin', '2026-07-10 14:20:00'),
  (605, 502, @a2, '虫害', 0.88, 400, 300, 90, 80, 'CONFIRMED', 'admin', '2026-07-10 14:20:00'),
  (606, 502, @a2, '萎蔫', 0.76, 250, 100, 65, 45, 'CONFIRMED', 'admin', '2026-07-10 14:21:00'),
  (607, 503, @a3, '病斑', 0.91, 180, 220, 75, 50, 'CONFIRMED', 'admin', '2026-07-15 10:15:00'),
  (608, 504, @b1, '虫害', 0.83, 320, 180, 55, 65, 'CONFIRMED', 'admin', '2026-07-20 08:25:00');

-- ============================================================
-- 6. 点喷任务（3 条：PENDING / COMPLETED / FAILED）
-- ============================================================
INSERT IGNORE INTO coer_spray_task
  (id, device_id, area_id, status, assigned_at, completed_at, create_by, create_time)
VALUES
  (701, 'PPDV-0001', @a2, 'COMPLETED', '2026-07-11 08:00:00', '2026-07-11 08:40:00', 'admin', '2026-07-11 08:00:00'),
  (702, 'PPDV-0001', @a3, 'FAILED',    '2026-07-16 09:00:00', '2026-07-16 09:30:00', 'admin', '2026-07-16 09:00:00'),
  (703, 'PPDV-0001', @b1, 'PENDING',   NULL,                    NULL,                    'admin', '2026-07-21 10:00:00');

-- ============================================================
-- 7. 复查记录（2 条：PASSED + NEED_RETRY，关联上面的 spray_task）
-- ============================================================
INSERT IGNORE INTO coer_review_record
  (id, spray_task_id, area_id, result, review_time, create_by, create_time)
VALUES
  (801, 701, @a2, 'PASSED',     '2026-07-12 09:00:00', 'admin', '2026-07-12 09:00:00'),
  (802, 702, @a3, 'NEED_RETRY', '2026-07-17 10:00:00', 'admin', '2026-07-17 10:00:00');
