-- =============================================================
-- 风险评价引擎：可配置规则表 + 区域表新增评分字段
-- =============================================================

-- ---------------------------- 风险配置表 ----------------------------
CREATE TABLE coer_risk_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key    VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value  VARCHAR(32)  NOT NULL COMMENT '配置值',
    category      VARCHAR(32)  NOT NULL COMMENT '分类 DETECTION/ENVIRONMENT/HISTORY/THRESHOLD',
    description   VARCHAR(255) DEFAULT '' COMMENT '说明',
    sort_order    INT          DEFAULT 0 COMMENT '排序',
    create_by     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='风险评价规则配置表';

-- ---------------------------- 区域表新增评分字段 ----------------------------
ALTER TABLE coer_greenhouse_area ADD COLUMN risk_score   INT      DEFAULT 0   COMMENT '风险评分 0-100';
ALTER TABLE coer_greenhouse_area ADD COLUMN last_eval_at DATETIME DEFAULT NULL COMMENT '最近评估时间';

-- ---------------------------- 种子数据 ----------------------------
INSERT INTO coer_risk_config (config_key, config_value, category, description, sort_order, create_by, create_time) VALUES
-- 检测分
('risk.score.pest',           '45', 'DETECTION',   '虫害分值',         1, 'admin', NOW()),
('risk.score.disease',        '40', 'DETECTION',   '病斑分值',         2, 'admin', NOW()),
('risk.score.wilt',           '20', 'DETECTION',   '萎蔫分值',         3, 'admin', NOW()),
('risk.score.yellow',         '15', 'DETECTION',   '黄化分值',         4, 'admin', NOW()),
('risk.score.spot',           '10', 'DETECTION',   '斑点分值',         5, 'admin', NOW()),
('risk.score.detection_max',  '80', 'DETECTION',   '检测分上限',       6, 'admin', NOW()),
-- 环境分
('risk.env.hot_humid',        '5',  'ENVIRONMENT', '高温高湿加分(>30℃ >80%RH)',  10, 'admin', NOW()),
('risk.env.dry',              '3',  'ENVIRONMENT', '干旱胁迫加分(土壤<20%)',      11, 'admin', NOW()),
('risk.env.glare',            '2',  'ENVIRONMENT', '强光灼伤加分(>80000lx >35℃)', 12, 'admin', NOW()),
-- 历史分
('risk.history.consecutive',  '5',  'HISTORY',     '连续异常加分',     20, 'admin', NOW()),
('risk.history.spray_fail',   '5',  'HISTORY',     '喷药无效加分',     21, 'admin', NOW()),
('risk.history.spray_ok',     '-10','HISTORY',     '喷药有效减分',     22, 'admin', NOW()),
-- 阈值
('risk.threshold.suspected',  '21', 'THRESHOLD',   '疑似异常阈值(>=)', 30, 'admin', NOW()),
('risk.threshold.high',       '51', 'THRESHOLD',   '高风险阈值(>=)',   31, 'admin', NOW());

-- ---------------------------- 菜单 ----------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (606, '风险配置', 10, 7, 'risk-config', 'ppdv/risk-config/index', 1, 0, 'C', '0', '0', 'ppdv:risk:config:list', 'edit', 'admin', NOW(), '风险评价规则配置'),
    (607, '风险评估', 10, 8, 'risk-eval',   'ppdv/risk-eval/index',   1, 0, 'C', '0', '0', 'ppdv:risk:evaluate',   'bug', 'admin', NOW(), '手动风险评估');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES
    (6061, '规则查询', 606, 1, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:risk:config:query', '#', 'admin', NOW()),
    (6062, '规则修改', 606, 2, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:risk:config:edit',  '#', 'admin', NOW()),
    (6071, '执行评估', 607, 1, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:risk:evaluate',     '#', 'admin', NOW());
