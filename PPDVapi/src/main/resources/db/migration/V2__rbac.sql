-- =============================================================
-- 若依风格 RBAC（用户/角色/菜单/部门/岗位/字典/配置/日志）
-- 结构对齐 RuoYi，列名 snake_case，供 com.ppdv.api.system.* 实体映射
-- =============================================================

-- ---------------------------- 部门表 ----------------------------
CREATE TABLE sys_dept (
    dept_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '部门id',
    parent_id   BIGINT      DEFAULT 0 COMMENT '父部门id',
    ancestors   VARCHAR(50) DEFAULT '' COMMENT '祖级列表',
    dept_name   VARCHAR(30) DEFAULT '' COMMENT '部门名称',
    order_num   INT         DEFAULT 0 COMMENT '显示顺序',
    leader      VARCHAR(20) DEFAULT NULL COMMENT '负责人',
    phone       VARCHAR(11) DEFAULT NULL COMMENT '联系电话',
    email       VARCHAR(50) DEFAULT NULL COMMENT '邮箱',
    status      CHAR(1)     DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)     DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64) DEFAULT '',
    create_time DATETIME    DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time DATETIME    DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='部门表';

-- ---------------------------- 用户表 ----------------------------
CREATE TABLE sys_user (
    user_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    dept_id     BIGINT       DEFAULT NULL COMMENT '部门ID',
    user_name   VARCHAR(30)  NOT NULL COMMENT '用户账号',
    nick_name   VARCHAR(30)  NOT NULL COMMENT '用户昵称',
    user_type   VARCHAR(2)   DEFAULT '00' COMMENT '用户类型（00系统用户）',
    email       VARCHAR(50)  DEFAULT '' COMMENT '用户邮箱',
    phonenumber VARCHAR(11)  DEFAULT '' COMMENT '手机号码',
    sex         CHAR(1)      DEFAULT '0' COMMENT '性别（0男 1女 2未知）',
    avatar      VARCHAR(100) DEFAULT '' COMMENT '头像地址',
    password    VARCHAR(100) DEFAULT '' COMMENT '密码',
    status      CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)      DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    login_ip    VARCHAR(128) DEFAULT '' COMMENT '最后登录IP',
    login_date  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_by   VARCHAR(64)  DEFAULT '',
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT '',
    update_time DATETIME     DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户信息表';

-- ---------------------------- 岗位表 ----------------------------
CREATE TABLE sys_post (
    post_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    post_code   VARCHAR(64) NOT NULL COMMENT '岗位编码',
    post_name   VARCHAR(50) NOT NULL COMMENT '岗位名称',
    post_sort   INT         NOT NULL COMMENT '显示顺序',
    status      CHAR(1)     NOT NULL COMMENT '状态（0正常 1停用）',
    create_by   VARCHAR(64) DEFAULT '',
    create_time DATETIME    DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time DATETIME    DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='岗位信息表';

-- ---------------------------- 角色表 ----------------------------
CREATE TABLE sys_role (
    role_id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name           VARCHAR(30)  NOT NULL COMMENT '角色名称',
    role_key            VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
    role_sort           INT          NOT NULL COMMENT '显示顺序',
    data_scope          CHAR(1)      DEFAULT '1' COMMENT '数据范围（1全部 2自定义 3本部门 4本部门及以下 5仅本人）',
    menu_check_strictly TINYINT(1)   DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
    dept_check_strictly TINYINT(1)   DEFAULT 1 COMMENT '部门树选择项是否关联显示',
    status              CHAR(1)      NOT NULL COMMENT '角色状态（0正常 1停用）',
    del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by           VARCHAR(64)  DEFAULT '',
    create_time         DATETIME     DEFAULT NULL,
    update_by           VARCHAR(64)  DEFAULT '',
    update_time         DATETIME     DEFAULT NULL,
    remark              VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色信息表';

-- ---------------------------- 菜单表 ----------------------------
CREATE TABLE sys_menu (
    menu_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    menu_name   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父菜单ID',
    order_num   INT          DEFAULT 0 COMMENT '显示顺序',
    path        VARCHAR(200) DEFAULT '' COMMENT '路由地址',
    component   VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
    query       VARCHAR(255) DEFAULT NULL COMMENT '路由参数',
    is_frame    CHAR(1)      DEFAULT '1' COMMENT '是否为外链（0是 1否）',
    is_cache    CHAR(1)      DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
    menu_type   CHAR(1)      DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
    visible     CHAR(1)      DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
    status      CHAR(1)      DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
    perms       VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    icon        VARCHAR(100) DEFAULT '#' COMMENT '菜单图标',
    create_by   VARCHAR(64)  DEFAULT '',
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT '',
    update_time DATETIME     DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT '',
    PRIMARY KEY (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜单权限表';

-- ---------------------------- 关联表 ----------------------------
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户和角色关联表';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色和菜单关联表';

CREATE TABLE sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色和部门关联表';

CREATE TABLE sys_user_post (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    post_id BIGINT NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (user_id, post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户与岗位关联表';

-- ---------------------------- 字典 ----------------------------
CREATE TABLE sys_dict_type (
    dict_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    dict_name   VARCHAR(100) DEFAULT '' COMMENT '字典名称',
    dict_type   VARCHAR(100) DEFAULT '' COMMENT '字典类型',
    status      CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    create_by   VARCHAR(64)  DEFAULT '',
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT '',
    update_time DATETIME     DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_dict_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='字典类型表';

CREATE TABLE sys_dict_data (
    dict_code   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    dict_sort   INT          DEFAULT 0 COMMENT '字典排序',
    dict_label  VARCHAR(100) DEFAULT '' COMMENT '字典标签',
    dict_value  VARCHAR(100) DEFAULT '' COMMENT '字典键值',
    dict_type   VARCHAR(100) DEFAULT '' COMMENT '字典类型',
    css_class   VARCHAR(100) DEFAULT NULL COMMENT '样式属性',
    list_class  VARCHAR(100) DEFAULT NULL COMMENT '表格回显样式',
    is_default  CHAR(1)      DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    status      CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    create_by   VARCHAR(64)  DEFAULT '',
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT '',
    update_time DATETIME     DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='字典数据表';

-- ---------------------------- 参数配置 ----------------------------
CREATE TABLE sys_config (
    config_id    INT          NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    config_name  VARCHAR(100) DEFAULT '' COMMENT '参数名称',
    config_key   VARCHAR(100) DEFAULT '' COMMENT '参数键名',
    config_value VARCHAR(500) DEFAULT '' COMMENT '参数键值',
    config_type  CHAR(1)      DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
    create_by    VARCHAR(64)  DEFAULT '',
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(64)  DEFAULT '',
    update_time  DATETIME     DEFAULT NULL,
    remark       VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (config_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='参数配置表';

-- ---------------------------- 日志 ----------------------------
CREATE TABLE sys_logininfor (
    info_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    user_name      VARCHAR(50)  DEFAULT '' COMMENT '用户账号',
    ipaddr         VARCHAR(128) DEFAULT '' COMMENT '登录IP地址',
    login_location VARCHAR(255) DEFAULT '' COMMENT '登录地点',
    browser        VARCHAR(50)  DEFAULT '' COMMENT '浏览器类型',
    os             VARCHAR(50)  DEFAULT '' COMMENT '操作系统',
    status         CHAR(1)      DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
    msg            VARCHAR(255) DEFAULT '' COMMENT '提示消息',
    login_time     DATETIME     DEFAULT NULL COMMENT '访问时间',
    PRIMARY KEY (info_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统访问记录';

CREATE TABLE sys_oper_log (
    oper_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    title          VARCHAR(50)   DEFAULT '' COMMENT '模块标题',
    business_type  INT           DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    method         VARCHAR(200)  DEFAULT '' COMMENT '方法名称',
    request_method VARCHAR(10)   DEFAULT '' COMMENT '请求方式',
    operator_type  INT           DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
    oper_name      VARCHAR(50)   DEFAULT '' COMMENT '操作人员',
    dept_name      VARCHAR(50)   DEFAULT '' COMMENT '部门名称',
    oper_url       VARCHAR(255)  DEFAULT '' COMMENT '请求URL',
    oper_ip        VARCHAR(128)  DEFAULT '' COMMENT '主机地址',
    oper_location  VARCHAR(255)  DEFAULT '' COMMENT '操作地点',
    oper_param     VARCHAR(2000) DEFAULT '' COMMENT '请求参数',
    json_result    VARCHAR(2000) DEFAULT '' COMMENT '返回参数',
    status         INT           DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
    error_msg      VARCHAR(2000) DEFAULT '' COMMENT '错误消息',
    oper_time      DATETIME      DEFAULT NULL COMMENT '操作时间',
    cost_time      BIGINT        DEFAULT 0 COMMENT '消耗时间(毫秒)',
    PRIMARY KEY (oper_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志记录';

-- =============================================================
-- 种子数据
-- =============================================================

-- 部门
INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status, create_by, create_time)
VALUES (100, 0, '0', 'PPDV 植保科技', 0, 'admin', '15888888888', 'ppdv@example.com', '0', 'admin', NOW()),
       (101, 100, '0,100', '研发部门', 1, 'admin', '15888888888', 'dev@example.com', '0', 'admin', NOW()),
       (102, 100, '0,100', '运营部门', 2, 'admin', '15888888888', 'ops@example.com', '0', 'admin', NOW());

-- 用户（admin / admin123，密码为 BCrypt 编码）
INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, remark)
VALUES (1, 101, 'admin', '管理员', '00', 'admin@ppdv.com', '15888888888', '1', '',
        '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', NOW(), 'admin', NOW(), '超级管理员');

-- 岗位
INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status, create_by, create_time)
VALUES (1, 'ceo', '董事长', 1, '0', 'admin', NOW()),
       (2, 'se', '项目经理', 2, '0', 'admin', NOW()),
       (3, 'user', '普通员工', 3, '0', 'admin', NOW());

-- 角色（admin 超级管理员数据范围=全部；common 普通角色）
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, create_by, create_time, remark)
VALUES (1, '超级管理员', 'admin', 1, '1', '0', 'admin', NOW(), '超级管理员'),
       (2, '普通角色', 'common', 2, '2', '0', 'admin', NOW(), '普通角色');

-- 关联：admin 用户 -> admin 角色；用户岗位
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1);

-- ---------------------------- 菜单 ----------------------------
-- 一级目录
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (1, '系统管理', 0, 1, 'system', NULL, '', 1, 0, 'M', '0', '0', '', 'system', 'admin', NOW(), '系统管理目录'),
       (2, '系统监控', 0, 2, 'monitor', NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', NOW(), '系统监控目录'),
       (10, '植保无人车', 0, 0, 'ppdv', NULL, '', 1, 0, 'M', '0', '0', '', 'guide', 'admin', NOW(), '植保业务目录');

-- 系统管理二级菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', NOW(), '用户管理菜单'),
       (101, '角色管理', 1, 2, 'role', 'system/role/index', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', NOW(), '角色管理菜单'),
       (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', NOW(), '菜单管理菜单'),
       (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', NOW(), '部门管理菜单'),
       (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', NOW(), '岗位管理菜单'),
       (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', NOW(), '字典管理菜单'),
       (106, '参数设置', 1, 7, 'config', 'system/config/index', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', NOW(), '参数设置菜单'),
       (108, '日志管理', 1, 9, 'log', NULL, '', 1, 0, 'M', '0', '0', '', 'log', 'admin', NOW(), '日志管理目录');

-- 系统监控二级菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', NOW(), '在线用户菜单');

-- 日志三级菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', NOW(), '操作日志菜单'),
       (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', NOW(), '登录日志菜单');

-- 植保业务菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (600, '设备管理', 10, 1, 'device', 'ppdv/device/index', '', 1, 0, 'C', '0', '0', 'ppdv:device:list', 'druid', 'admin', NOW(), '无人车设备台账'),
       (601, '实时监控', 10, 2, 'realtime', 'ppdv/realtime/index', '', 1, 0, 'C', '0', '0', 'ppdv:device:query', 'monitor', 'admin', NOW(), '设备实时监控');

-- 按钮权限（用户/角色/菜单/部门/岗位/字典/配置/设备）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES
    (1001, '用户查询', 100, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', NOW()),
    (1002, '用户新增', 100, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', NOW()),
    (1003, '用户修改', 100, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', NOW()),
    (1004, '用户删除', 100, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', NOW()),
    (1005, '重置密码', 100, 7, '', NULL, 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', NOW()),
    (1006, '角色查询', 101, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', NOW()),
    (1007, '角色新增', 101, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', NOW()),
    (1008, '角色修改', 101, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', NOW()),
    (1009, '角色删除', 101, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', NOW()),
    (1010, '菜单查询', 102, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', NOW()),
    (1011, '菜单新增', 102, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', NOW()),
    (1012, '菜单修改', 102, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', NOW()),
    (1013, '菜单删除', 102, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', NOW()),
    (1014, '部门查询', 103, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', NOW()),
    (1015, '部门新增', 103, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', NOW()),
    (1016, '部门修改', 103, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', NOW()),
    (1017, '部门删除', 103, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', NOW()),
    (1018, '岗位查询', 104, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', NOW()),
    (1019, '岗位新增', 104, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', NOW()),
    (1020, '岗位修改', 104, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', NOW()),
    (1021, '岗位删除', 104, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', NOW()),
    (1022, '字典查询', 105, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', NOW()),
    (1023, '字典新增', 105, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', NOW()),
    (1024, '字典修改', 105, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', NOW()),
    (1025, '字典删除', 105, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', NOW()),
    (1026, '参数查询', 106, 1, '', NULL, 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', NOW()),
    (1027, '参数新增', 106, 2, '', NULL, 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', NOW()),
    (1028, '参数修改', 106, 3, '', NULL, 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', NOW()),
    (1029, '参数删除', 106, 4, '', NULL, 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', NOW()),
    (1030, '设备查询', 600, 1, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:device:query', '#', 'admin', NOW()),
    (1031, '设备新增', 600, 2, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:device:add', '#', 'admin', NOW()),
    (1032, '设备删除', 600, 3, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:device:remove', '#', 'admin', NOW()),
    (1033, '指令下发', 600, 4, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:device:command', '#', 'admin', NOW());

-- 普通角色(common) 授予业务菜单 + 只读系统查看（示例）
INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (2, 10), (2, 600), (2, 601), (2, 1030), (2, 1033);

-- ---------------------------- 字典 ----------------------------
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark)
VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', NOW(), '用户性别列表'),
       (2, '菜单状态', 'sys_show_hide', '0', 'admin', NOW(), '菜单显示状态'),
       (3, '系统开关', 'sys_normal_disable', '0', 'admin', NOW(), '系统开关列表'),
       (4, '系统是否', 'sys_yes_no', '0', 'admin', NOW(), '系统是否列表'),
       (5, '设备状态', 'ppdv_device_status', '0', 'admin', NOW(), '无人车在线状态'),
       (6, '作业模式', 'ppdv_work_mode', '0', 'admin', NOW(), '无人车作业模式');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', NOW(), ''),
       (2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', NOW(), ''),
       (3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', NOW(), ''),
       (1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
       (2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
       (2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', NOW(), ''),
       (2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', NOW(), ''),
       (1, '在线', 'ONLINE', 'ppdv_device_status', '', 'success', 'N', '0', 'admin', NOW(), ''),
       (2, '离线', 'OFFLINE', 'ppdv_device_status', '', 'info', 'Y', '0', 'admin', NOW(), ''),
       (1, '手动', 'MANUAL', 'ppdv_work_mode', '', 'info', 'N', '0', 'admin', NOW(), ''),
       (2, '自动', 'AUTO', 'ppdv_work_mode', '', 'success', 'Y', '0', 'admin', NOW(), ''),
       (3, '暂停', 'PAUSED', 'ppdv_work_mode', '', 'warning', 'N', '0', 'admin', NOW(), ''),
       (4, '急停', 'ESTOP', 'ppdv_work_mode', '', 'danger', 'N', '0', 'admin', NOW(), '');

-- ---------------------------- 参数配置 ----------------------------
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
VALUES ('主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', NOW(), '蓝色 skin-blue'),
       ('账号自助-验证码开关', 'sys.account.captchaEnabled', 'false', 'Y', 'admin', NOW(), '是否开启验证码'),
       ('账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', NOW(), '是否开启注册');
