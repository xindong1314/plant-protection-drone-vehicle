-- 区域 GIS 地块：采用高德地图 GCJ-02 坐标，边界以 [[lng,lat], ...] JSON 数组保存。
ALTER TABLE coer_greenhouse_area
    ADD COLUMN boundary_json LONGTEXT DEFAULT NULL COMMENT '地块多边形边界 JSON（GCJ-02）' AFTER aruco_id,
    ADD COLUMN coordinate_system VARCHAR(16) DEFAULT 'GCJ-02' COMMENT '边界坐标系' AFTER boundary_json;

-- 为区域列表接口补齐权限；不创建后台动态菜单，业务前端自行注册路由。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES (6020, '区域列表', 10, 0, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:list', '#', 'admin', NOW());
