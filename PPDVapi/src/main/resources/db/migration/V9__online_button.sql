-- =============================================================
-- V9：在线用户强退按钮权限（页面菜单 109 已在 V2 中创建）
-- =============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES (508, '强退', 109, 1, '', NULL, 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', NOW());
