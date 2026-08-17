-- =============================================================
-- 植保无人车核心业务表（coer_ 前缀）
-- 对应闭环：巡检采集 → 异常识别 → 风险提示 → 清水点喷 → 喷后复查 → 档案更新
-- =============================================================

-- ---------------------------- 1. 区域管理表 ----------------------------
CREATE TABLE coer_greenhouse_area (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    area_code        VARCHAR(16)  NOT NULL COMMENT '区域编号（A1, B2...）',
    area_name        VARCHAR(64)  DEFAULT '' COMMENT '区域名称',
    greenhouse_name  VARCHAR(64)  DEFAULT '' COMMENT '所属大棚',
    aruco_id         VARCHAR(32)  DEFAULT NULL COMMENT 'ArUco 标记值',
    risk_status      VARCHAR(32)  DEFAULT 'NORMAL' COMMENT '风险状态 NORMAL/SUSPICIOUS/HIGH_RISK/SPRAYED_PENDING_REVIEW',
    sort_order       INT          DEFAULT 0 COMMENT '排序',
    create_by        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_area_code (area_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='区域管理表';

-- ---------------------------- 2. 巡检记录表 ----------------------------
CREATE TABLE coer_inspection_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id      VARCHAR(64)  NOT NULL COMMENT '执行巡检的小车编号',
    area_id        BIGINT       DEFAULT NULL COMMENT '巡检区域ID',
    start_time     DATETIME     DEFAULT NULL COMMENT '巡检开始时间',
    end_time       DATETIME     DEFAULT NULL COMMENT '巡检结束时间',
    image_count    INT          DEFAULT 0 COMMENT '采集图片数',
    anomaly_count  INT          DEFAULT 0 COMMENT '检测异常数',
    status         VARCHAR(16)  DEFAULT 'IN_PROGRESS' COMMENT '状态 IN_PROGRESS/COMPLETED',
    create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_device_id (device_id),
    KEY idx_area_id (area_id),
    KEY idx_start_time (start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='巡检记录表';

-- ---------------------------- 3. 巡检图片表 ----------------------------
CREATE TABLE coer_inspection_image (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    inspection_id  BIGINT       DEFAULT NULL COMMENT '巡检记录ID',
    area_id        BIGINT       DEFAULT NULL COMMENT '所在区域ID',
    file_path      VARCHAR(500) DEFAULT '' COMMENT '原图文件路径',
    captured_at    DATETIME     DEFAULT NULL COMMENT '拍摄时间',
    create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_inspection_id (inspection_id),
    KEY idx_area_id (area_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='巡检图片表';

-- ---------------------------- 4. 识别结果表 ----------------------------
CREATE TABLE coer_detection_result (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    image_id       BIGINT       DEFAULT NULL COMMENT '图片ID',
    area_id        BIGINT       DEFAULT NULL COMMENT '所在区域ID',
    category       VARCHAR(32)  DEFAULT '' COMMENT '识别类别 病斑/虫害/黄化/萎蔫/花朵/幼果/成熟果',
    confidence     DOUBLE       DEFAULT 0 COMMENT '置信度',
    box_x          INT          DEFAULT 0 COMMENT '目标框 X 坐标',
    box_y          INT          DEFAULT 0 COMMENT '目标框 Y 坐标',
    box_w          INT          DEFAULT 0 COMMENT '目标框宽度',
    box_h          INT          DEFAULT 0 COMMENT '目标框高度',
    status         VARCHAR(16)  DEFAULT 'PENDING' COMMENT '处理状态 PENDING/CONFIRMED/IGNORED',
    create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_image_id (image_id),
    KEY idx_area_id (area_id),
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='识别结果表';

-- ---------------------------- 5. 点喷任务表 ----------------------------
CREATE TABLE coer_spray_task (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id      VARCHAR(64)  DEFAULT NULL COMMENT '执行小车编号',
    area_id        BIGINT       DEFAULT NULL COMMENT '目标区域ID',
    status         VARCHAR(16)  DEFAULT 'PENDING' COMMENT '任务状态 PENDING/ASSIGNED/IN_PROGRESS/COMPLETED/FAILED',
    assigned_at    DATETIME     DEFAULT NULL COMMENT '下发时间',
    completed_at   DATETIME     DEFAULT NULL COMMENT '完成时间',
    create_by      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time    DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_area_id (area_id),
    KEY idx_device_id (device_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='点喷任务表';

-- ---------------------------- 6. 复查记录表 ----------------------------
CREATE TABLE coer_review_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spray_task_id    BIGINT       DEFAULT NULL COMMENT '点喷任务ID',
    area_id          BIGINT       DEFAULT NULL COMMENT '复查区域ID',
    before_image_id  BIGINT       DEFAULT NULL COMMENT '喷前图片ID',
    after_image_id   BIGINT       DEFAULT NULL COMMENT '喷后图片ID',
    result           VARCHAR(16)  DEFAULT 'NEED_RETRY' COMMENT '复查结果 PASSED/FAILED/NEED_RETRY',
    review_time      DATETIME     DEFAULT NULL COMMENT '复查时间',
    create_by        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_spray_task_id (spray_task_id),
    KEY idx_area_id (area_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='复查记录表';

-- ---------------------------- 7. 区域档案表 ----------------------------
CREATE TABLE coer_greenhouse_archive (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    area_id             BIGINT       NOT NULL COMMENT '区域ID',
    total_inspections   INT          DEFAULT 0 COMMENT '累计巡检次数',
    total_anomalies     INT          DEFAULT 0 COMMENT '累计异常次数',
    total_sprays        INT          DEFAULT 0 COMMENT '累计点喷次数',
    last_inspection_time DATETIME    DEFAULT NULL COMMENT '最近巡检时间',
    last_anomaly_time   DATETIME     DEFAULT NULL COMMENT '最近异常时间',
    last_spray_time     DATETIME     DEFAULT NULL COMMENT '最近点喷时间',
    last_risk_status    VARCHAR(32)  DEFAULT 'NORMAL' COMMENT '最近风险状态',
    create_by           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time         DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time         DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_area_id (area_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='区域档案表';

-- =============================================================
-- 种子数据
-- =============================================================

-- 模拟大棚区域（2×3 网格：A1-A3, B1-B3）
INSERT INTO coer_greenhouse_area (area_code, area_name, greenhouse_name, sort_order, create_by, create_time)
VALUES ('A1', 'A1 区', '模拟大棚 1 号', 1, 'admin', NOW()),
       ('A2', 'A2 区', '模拟大棚 1 号', 2, 'admin', NOW()),
       ('A3', 'A3 区', '模拟大棚 1 号', 3, 'admin', NOW()),
       ('B1', 'B1 区', '模拟大棚 1 号', 4, 'admin', NOW()),
       ('B2', 'B2 区', '模拟大棚 1 号', 5, 'admin', NOW()),
       ('B3', 'B3 区', '模拟大棚 1 号', 6, 'admin', NOW());

-- 区域档案种子数据
INSERT INTO coer_greenhouse_archive (area_id, total_inspections, total_anomalies, total_sprays, last_risk_status, create_by, create_time)
SELECT id, 0, 0, 0, 'NORMAL', 'admin', NOW()
FROM coer_greenhouse_area;

-- ---------------------------- API 权限 ----------------------------
-- 暂不创建页面菜单：对应 PPDVadmin 页面尚未实现。权限记录挂在业务目录下，避免动态路由指向不存在的组件。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES
    (6021, '区域查询', 10, 1, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:query', '#', 'admin', NOW()),
    (6022, '区域新增', 10, 2, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:add',   '#', 'admin', NOW()),
    (6023, '区域编辑', 10, 3, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:edit',  '#', 'admin', NOW()),
    (6024, '区域删除', 10, 4, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:remove','#', 'admin', NOW()),
    (6031, '巡检查询', 10, 5, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:inspection:query',  '#', 'admin', NOW()),
    (6032, '图片上传', 10, 6, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:inspection:upload',  '#', 'admin', NOW()),
    (6041, '点喷任务查询', 10, 7, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:spray:query', '#', 'admin', NOW()),
    (6042, '点喷任务管理', 10, 8, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:spray:add',   '#', 'admin', NOW()),
    (6043, '复查记录创建', 10, 9, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:review:add',   '#', 'admin', NOW()),
    (6051, '档案查询', 10, 10, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:archive:query', '#', 'admin', NOW());
