-- =============================================================
-- 车端 YOLO 目标跟踪数据上云（P1）：跟踪会话 + 跟踪观测两张表
-- 不改旧表 coer_detection_result（图片必填、无 track 字段）
-- 身份模型（设计方案 §16 修订）：一次出现（episode）一行；
-- 唯一键按 episode_key，track_key 仅展示/关联；不建帧数列。
-- =============================================================

-- ---------------------------- 1. 跟踪会话表 ----------------------------
CREATE TABLE coer_track_session (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id    VARCHAR(32)  NOT NULL COMMENT '车端编号',
    area_id      BIGINT       NOT NULL COMMENT '区域ID',
    session_key  VARCHAR(64)  NOT NULL COMMENT '车端 trackSessionId（批键）',
    start_ts     BIGINT       NOT NULL COMMENT '会话开始（epoch ms）',
    end_ts       BIGINT       DEFAULT NULL COMMENT '会话结束（epoch ms）',
    end_reason   VARCHAR(16)  DEFAULT NULL COMMENT '结束来源：session_end=车端 sessionEnd（不可复归）/ idle=云端空闲超时（新活动可复归）',
    status       VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/ENDED（巡检级，由 sessionEnd/空闲超时控制；episode ENDED 不影响）',
    create_by    VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by    VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark       VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session (device_id, session_key),
    KEY idx_area_start (area_id, start_ts)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='目标跟踪会话表';

-- ---------------------------- 2. 跟踪观测表 ----------------------------
CREATE TABLE coer_detection_track (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id        VARCHAR(32)  NOT NULL COMMENT '车端',
    track_session_id BIGINT       NOT NULL COMMENT '逻辑外键→coer_track_session.id',
    area_id          BIGINT       NOT NULL COMMENT '区域ID',
    track_key        VARCHAR(64)  NOT NULL COMMENT '车端 trackId（仅展示/关联，不保证跨出现稳定）',
    episode_key      VARCHAR(64)  NOT NULL COMMENT '一次出现ID（身份键，同出现内稳定，再次出现必须换新）',
    episode_no       INT          DEFAULT 0 COMMENT '同 trackId 第几次出现（从 0 递增，仅展示/诊断）',
    category         VARCHAR(32)  NOT NULL COMMENT 'Q1 规范 class（未知落 __unknown__）',
    confidence       DOUBLE       DEFAULT 0 COMMENT '置信度',
    first_seen_ts    BIGINT       NOT NULL COMMENT '本次出现首帧时间（epoch ms，episode 内保持不变）',
    last_seen_ts     BIGINT       NOT NULL COMMENT '本次出现末帧/最新帧时间（epoch ms，episode 结束后定格）',
    box_x            INT          DEFAULT 0 COMMENT '目标框 X',
    box_y            INT          DEFAULT 0 COMMENT '目标框 Y',
    box_w            INT          DEFAULT 0 COMMENT '目标框宽',
    box_h            INT          DEFAULT 0 COMMENT '目标框高',
    image_width      INT          DEFAULT NULL COMMENT '图像宽',
    image_height     INT          DEFAULT NULL COMMENT '图像高',
    trajectory_json  TEXT         DEFAULT NULL COMMENT '抽稀轨迹 JSON [[x,y,ts],...]',
    track_state      VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LOST/ENDED/REAPPEAR（ENDED=本次出现结束，不结束巡检会话）',
    severity         INT          DEFAULT NULL COMMENT '严重度 0-4',
    severity_source  VARCHAR(16)  DEFAULT NULL COMMENT '严重度来源',
    captured_at      DATETIME     DEFAULT NULL COMMENT '由 first_seen_ts 转换，便于排序',
    create_by        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_episode (device_id, track_session_id, episode_key),
    KEY idx_episode_track (device_id, track_session_id, track_key),
    KEY idx_session (track_session_id),
    KEY idx_area_last_seen (area_id, last_seen_ts),
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='跟踪观测表';

-- ---------------------------- 3. API 权限 ----------------------------
-- 独立权限点 ppdv:track:query（方案 §13.3-5 建议独立；不创建后台动态菜单，业务前端自行注册路由）。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES (6081, '跟踪查询', 10, 14, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:track:query', '#', 'admin', NOW());

-- 普通业务角色默认可见跟踪记录（与区域列表同类只读权限）。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 6081
FROM sys_role r
WHERE r.role_key = 'common'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 6081
  );
