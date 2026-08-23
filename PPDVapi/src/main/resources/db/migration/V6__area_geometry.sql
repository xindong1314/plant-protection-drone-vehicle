-- 区域 GIS 地块：采用高德地图 GCJ-02 坐标，边界以 [[lng,lat], ...] JSON 数组保存。
ALTER TABLE coer_greenhouse_area
    ADD COLUMN boundary_json LONGTEXT DEFAULT NULL COMMENT '地块多边形边界 JSON（GCJ-02）' AFTER aruco_id,
    ADD COLUMN coordinate_system VARCHAR(16) DEFAULT 'GCJ-02' COMMENT '边界坐标系' AFTER boundary_json;

-- 为区域列表接口补齐权限；不创建后台动态菜单，业务前端自行注册路由。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES (6020, '区域列表', 10, 0, '', NULL, 1, 0, 'F', '0', '0', 'ppdv:area:list', '#', 'admin', NOW());

-- 普通业务角色需要此权限加载巡检、点喷、复查等页面的区域选项。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 6020
FROM sys_role r
WHERE r.role_key = 'common'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 6020
  );
