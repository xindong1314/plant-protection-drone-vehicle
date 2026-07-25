package com.ppdv.api.system.service;

import com.ppdv.api.system.domain.SysUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 解析用户的角色标识与权限标识（供登录构建 LoginUser 使用）。
 */
@Service
public class SysPermissionService {

    private final SysMenuService menuService;
    private final SysRoleService roleService;

    public SysPermissionService(SysMenuService menuService, SysRoleService roleService) {
        this.menuService = menuService;
        this.roleService = roleService;
    }

    public Set<String> getMenuPermission(SysUser user) {
        Set<String> perms = new HashSet<>();
        if (user.isAdmin()) {
            perms.add("*:*:*");
        } else {
            perms.addAll(menuService.selectMenuPermsByUserId(user.getUserId()));
        }
        return perms;
    }

    public Set<String> getRolePermission(SysUser user) {
        Set<String> roles = new HashSet<>();
        if (user.isAdmin()) {
            roles.add("admin");
        } else {
            roles.addAll(roleService.selectRoleKeysByUserId(user.getUserId()));
        }
        return roles;
    }
}
