-- DeviceController.resetSecret 使用的修改权限。仅登记为可分配按钮权限，不默认授予 common。
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time
)
VALUES (
    1034, '设备密钥重置', 600, 5, '', NULL,
    1, 0, 'F', '0', '0', 'ppdv:device:edit', '#', 'admin', NOW()
);
