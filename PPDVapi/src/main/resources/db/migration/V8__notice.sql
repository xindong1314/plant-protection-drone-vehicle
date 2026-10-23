-- =============================================================
-- 通知公告（公告管理 + 公告已读记录 + 字典 + 菜单 + 种子数据）
-- 结构对齐 RuoYi，列名 snake_case，供 com.ppdv.api.system.* 实体映射
-- =============================================================

-- ---------------------------- 公告表 ----------------------------
CREATE TABLE sys_notice (
    notice_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    notice_title   VARCHAR(64)  NOT NULL COMMENT '公告标题',
    notice_type    CHAR(1)      NOT NULL DEFAULT '1' COMMENT '公告类型（1通知 2公告）',
    notice_content LONGTEXT     DEFAULT NULL COMMENT '公告内容（富文本HTML）',
    status         CHAR(1)      DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
    create_by      VARCHAR(64)  DEFAULT '',
    create_time    DATETIME     DEFAULT NULL,
    update_by      VARCHAR(64)  DEFAULT '',
    update_time    DATETIME     DEFAULT NULL,
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (notice_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通知公告表';

-- ---------------------------- 公告已读记录表 ----------------------------
CREATE TABLE sys_notice_read (
    read_id   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '阅读记录ID',
    notice_id BIGINT   NOT NULL COMMENT '公告ID',
    user_id   BIGINT   NOT NULL COMMENT '用户ID',
    read_time DATETIME DEFAULT NULL COMMENT '阅读时间',
    PRIMARY KEY (read_id),
    UNIQUE KEY uk_notice_user (notice_id, user_id),
    KEY idx_notice_read_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='公告用户阅读记录表';

-- =============================================================
-- 种子数据
-- =============================================================

-- ---------------------------- 字典 ----------------------------
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark)
VALUES (7, '公告类型', 'sys_notice_type', '0', 'admin', NOW(), '公告类型列表'),
       (8, '公告状态', 'sys_notice_status', '0', 'admin', NOW(), '公告状态列表'),
       (9, '操作类型', 'sys_oper_type', '0', 'admin', NOW(), '操作日志的业务操作类型'),
       (10, '系统状态', 'sys_common_status', '0', 'admin', NOW(), '登录状态/操作状态');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (1, '通知', '1', 'sys_notice_type', '', 'success', 'Y', '0', 'admin', NOW(), ''),
       (2, '公告', '2', 'sys_notice_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
       (2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (1, '新增', '1', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', NOW(), ''),
       (2, '修改', '2', 'sys_oper_type', '', 'success', 'N', '0', 'admin', NOW(), ''),
       (3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (4, '授权', '4', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (8, '清空', '9', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (9, '其它', '0', 'sys_oper_type', '', 'info', 'Y', '0', 'admin', NOW(), ''),
       (1, '成功', '0', 'sys_common_status', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
       (2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', NOW(), '');

-- ---------------------------- 菜单 ----------------------------
-- 公告管理二级菜单（挂在系统管理目录下）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (107, '公告管理', 1, 8, 'notice', 'system/notice/index', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'admin', NOW(), '公告管理菜单');

-- 日志按钮权限（操作日志/登录日志）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES
    (502, '日志查询', 500, 1, '', NULL, 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', NOW()),
    (503, '日志删除', 500, 2, '', NULL, 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', NOW()),
    (504, '日志导出', 500, 3, '', NULL, 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', NOW()),
    (505, '日志删除', 501, 1, '', NULL, 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', NOW()),
    (506, '解锁账户', 501, 2, '', NULL, 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', NOW()),
    (507, '日志导出', 501, 3, '', NULL, 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', NOW());

-- 公告按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES
    (1071, '公告查询', 107, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', NOW()),
    (1072, '公告新增', 107, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', NOW()),
    (1073, '公告修改', 107, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', NOW()),
    (1074, '公告删除', 107, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', NOW());

-- ---------------------------- 种子公告 ----------------------------
INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, create_by, create_time, remark)
VALUES ('欢迎使用植保无人车管理后台', '1', '<p>欢迎使用植保无人车管理后台，请及时处理高风险区域。</p>', '0', 'admin', NOW(), '通知'),
       ('系统上线公告', '2', '<p>植保无人车管理后台已正式上线，各功能模块可正常使用，如有问题请联系管理员。</p>', '0', 'admin', NOW(), '公告');
