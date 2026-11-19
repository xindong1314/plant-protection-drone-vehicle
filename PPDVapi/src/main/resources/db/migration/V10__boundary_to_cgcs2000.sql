-- =============================================================
-- V10: 区域边界坐标系 GCJ-02 → CGCS2000（WGS84 同大地基准，与北斗/天地图一致）
-- 只处理 coer_greenhouse_area：
--   1. 临时表暂存待转换行（游标读临时表、回写主表，避免边读边改）；
--   2. 存储过程逐行逐顶点做 GCJ-02 → WGS84 迭代逆解，按索引顺序保序重建 [[lng,lat], ...]；
--   3. 无法转换的坏数据行（非法 JSON / 顶点格式不对 / 顶点数不足）跳过，
--      保留 coordinate_system='GCJ-02'，需人工修复或重画；
--      只有没有坐标数据的空边界（NULL / 空串 / []）改标为 CGCS2000。
-- 可重复执行：只处理 coordinate_system='GCJ-02' 的行。
-- =============================================================

-- 清理上次中断可能残留的临时对象（正常路径在末尾同样删除）
DROP PROCEDURE IF EXISTS ppdv_migrate_gcj_boundary;
DROP TEMPORARY TABLE IF EXISTS tmp_gcj02_boundary;

-- ---------------------------- 1. 暂存待转换行 ----------------------------
-- CASE 包裹保证 JSON_VALID 先于 JSON_LENGTH 求值，非法 JSON 行不会中断迁移。
CREATE TEMPORARY TABLE tmp_gcj02_boundary (
    id            BIGINT   NOT NULL,
    boundary_json LONGTEXT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO tmp_gcj02_boundary (id, boundary_json)
SELECT id, boundary_json
FROM coer_greenhouse_area
WHERE coordinate_system = 'GCJ-02'
  AND boundary_json IS NOT NULL
  AND JSON_VALID(boundary_json)
  AND JSON_LENGTH(CASE WHEN JSON_VALID(boundary_json) THEN boundary_json END) >= 3;

DELIMITER ;;

-- ---------------------------- 2. 逐行逐顶点转换 ----------------------------
CREATE PROCEDURE ppdv_migrate_gcj_boundary()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_id BIGINT;
    DECLARE v_doc LONGTEXT;
    DECLARE v_len INT;
    DECLARE v_idx INT;
    DECLARE v_bad INT;
    DECLARE v_iter INT;
    DECLARE v_json LONGTEXT;
    DECLARE v_pt_txt VARCHAR(64);
    DECLARE v_pt JSON;
    DECLARE v_el0 JSON;
    DECLARE v_el1 JSON;
    DECLARE v_lng DOUBLE;
    DECLARE v_lat DOUBLE;
    DECLARE v_wgs_lng DOUBLE;
    DECLARE v_wgs_lat DOUBLE;
    DECLARE v_x DOUBLE;
    DECLARE v_y DOUBLE;
    DECLARE v_dlat DOUBLE;
    DECLARE v_dlng DOUBLE;
    DECLARE v_radlat DOUBLE;
    DECLARE v_magic DOUBLE;
    DECLARE v_sqrtmagic DOUBLE;
    DECLARE v_mglat DOUBLE;
    DECLARE v_mglng DOUBLE;
    -- Krasovsky 椭球参数（GCJ-02 正向偏移公式）
    DECLARE v_a DOUBLE DEFAULT 6378245.0;
    DECLARE v_ee DOUBLE DEFAULT 0.00669342162296594323;
    -- 游标只读临时表，回写目标是主表
    DECLARE cur CURSOR FOR SELECT id, boundary_json FROM tmp_gcj02_boundary;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;
    row_loop: LOOP
        FETCH cur INTO v_id, v_doc;
        IF v_done = 1 THEN
            LEAVE row_loop;
        END IF;

        SET v_len = JSON_LENGTH(v_doc);
        SET v_json = '[';
        SET v_bad = 0;
        SET v_idx = 0;

        -- 按顶点索引顺序逐个转换后拼接，保序重建（不用 GROUP_CONCAT，顺序无保证）
        vertex_loop: WHILE v_idx < v_len AND v_bad = 0 DO
            SET v_pt = JSON_EXTRACT(v_doc, CONCAT('$[', v_idx, ']'));
            IF v_pt IS NULL OR JSON_TYPE(v_pt) <> 'ARRAY' OR JSON_LENGTH(v_pt) <> 2 THEN
                SET v_bad = 1;
            ELSE
                SET v_el0 = JSON_EXTRACT(v_pt, '$[0]');
                SET v_el1 = JSON_EXTRACT(v_pt, '$[1]');
                IF v_el0 IS NULL OR v_el1 IS NULL
                        OR JSON_TYPE(v_el0) NOT IN ('INTEGER', 'UNSIGNED INTEGER', 'DOUBLE', 'DECIMAL')
                        OR JSON_TYPE(v_el1) NOT IN ('INTEGER', 'UNSIGNED INTEGER', 'DOUBLE', 'DECIMAL') THEN
                    -- 顶点格式不对：整行跳过
                    SET v_bad = 1;
                ELSE
                    SET v_lng = v_el0 + 0e0;
                    SET v_lat = v_el1 + 0e0;
                    IF v_lng < 72.004 OR v_lng > 137.8347 OR v_lat < 0.8293 OR v_lat > 55.8271 THEN
                        -- 中国境外点原样保留
                        SET v_pt_txt = CONCAT('[', v_lng, ',', v_lat, ']');
                    ELSE
                        -- 国内点 GCJ-02 → WGS84/CGCS2000 迭代逆解：
                        -- 先把当前点当 WGS84 做正向偏移，再按差值修正，迭代 3 次
                        SET v_wgs_lng = v_lng;
                        SET v_wgs_lat = v_lat;
                        SET v_iter = 1;
                        WHILE v_iter <= 3 DO
                            SET v_x = v_wgs_lng - 105.0;
                            SET v_y = v_wgs_lat - 35.0;
                            SET v_dlat = -100.0 + 2.0 * v_x + 3.0 * v_y + 0.2 * v_y * v_y + 0.1 * v_x * v_y
                                    + 0.2 * SQRT(ABS(v_x))
                                    + (20.0 * SIN(6.0 * v_x * PI()) + 20.0 * SIN(2.0 * v_x * PI())) * 2.0 / 3.0
                                    + (20.0 * SIN(v_y * PI()) + 40.0 * SIN(v_y * PI() / 3.0)) * 2.0 / 3.0
                                    + (160.0 * SIN(v_y * PI() / 12.0) + 320.0 * SIN(v_y * PI() / 30.0)) * 2.0 / 3.0;
                            SET v_dlng = 300.0 + v_x + 2.0 * v_y + 0.1 * v_x * v_x + 0.1 * v_x * v_y
                                    + 0.1 * SQRT(ABS(v_x))
                                    + (20.0 * SIN(6.0 * v_x * PI()) + 20.0 * SIN(2.0 * v_x * PI())) * 2.0 / 3.0
                                    + (20.0 * SIN(v_x * PI()) + 40.0 * SIN(v_x * PI() / 3.0)) * 2.0 / 3.0
                                    + (150.0 * SIN(v_x * PI() / 12.0) + 300.0 * SIN(v_x * PI() / 30.0)) * 2.0 / 3.0;
                            SET v_radlat = v_wgs_lat / 180.0 * PI();
                            SET v_magic = 1.0 - v_ee * SIN(v_radlat) * SIN(v_radlat);
                            SET v_sqrtmagic = SQRT(v_magic);
                            SET v_mglat = v_wgs_lat + v_dlat * 180.0
                                    / ((v_a * (1.0 - v_ee)) / (v_magic * v_sqrtmagic) * PI());
                            SET v_mglng = v_wgs_lng + v_dlng * 180.0
                                    / (v_a / v_sqrtmagic * COS(v_radlat) * PI());
                            SET v_wgs_lat = v_wgs_lat + (v_lat - v_mglat);
                            SET v_wgs_lng = v_wgs_lng + (v_lng - v_mglng);
                            SET v_iter = v_iter + 1;
                        END WHILE;
                        SET v_pt_txt = CONCAT('[', ROUND(v_wgs_lng, 7), ',', ROUND(v_wgs_lat, 7), ']');
                    END IF;
                    SET v_json = CONCAT(v_json, CASE WHEN v_idx = 0 THEN '' ELSE ',' END, v_pt_txt);
                END IF;
            END IF;
            SET v_idx = v_idx + 1;
        END WHILE;

        IF v_bad = 0 THEN
            UPDATE coer_greenhouse_area
            SET boundary_json = CONCAT(v_json, ']'),
                coordinate_system = 'CGCS2000'
            WHERE id = v_id
              AND coordinate_system = 'GCJ-02';
        END IF;
        -- 坏数据行不动 boundary_json；坐标系保持 GCJ-02，留给人工修复/重画
    END LOOP;
    CLOSE cur;
END;;

DELIMITER ;

CALL ppdv_migrate_gcj_boundary();

-- ---------------------------- 3. 清理与兜底 ----------------------------
DROP PROCEDURE IF EXISTS ppdv_migrate_gcj_boundary;
DROP TEMPORARY TABLE IF EXISTS tmp_gcj02_boundary;

-- 兜底：只把没有坐标数据的空边界（NULL / 空串 / []）改标为 CGCS2000。
-- 非法 JSON / 顶点格式不对 / 顶点数不足的行保留 GCJ-02，避免未转换坐标被冒充为 CGCS2000。
UPDATE coer_greenhouse_area
SET coordinate_system = 'CGCS2000'
WHERE coordinate_system = 'GCJ-02'
  AND (
      boundary_json IS NULL
      OR TRIM(boundary_json) = ''
      OR (
          JSON_VALID(boundary_json)
          AND JSON_TYPE(CASE WHEN JSON_VALID(boundary_json) THEN boundary_json END) = 'ARRAY'
          AND JSON_LENGTH(CASE WHEN JSON_VALID(boundary_json) THEN boundary_json END) = 0
      )
  );

-- 列默认值与注释同步为 CGCS2000
ALTER TABLE coer_greenhouse_area
    MODIFY COLUMN boundary_json LONGTEXT DEFAULT NULL COMMENT '地块多边形边界 JSON（CGCS2000；未能转换的历史坏数据为 GCJ-02）',
    MODIFY COLUMN coordinate_system VARCHAR(16) DEFAULT 'CGCS2000' COMMENT '边界坐标系（CGCS2000；未能转换的历史坏数据为 GCJ-02）';
