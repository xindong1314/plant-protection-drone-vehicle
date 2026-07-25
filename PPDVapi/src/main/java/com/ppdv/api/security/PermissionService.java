package com.ppdv.api.security;

import com.ppdv.api.common.constant.Constants;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 供 @PreAuthorize("@ss.hasPermi('...')") 使用的权限校验 bean。
 */
@Service("ss")
public class PermissionService {

    /** 是否具备某权限（管理员的 *:*:* 通配全部）。 */
    public boolean hasPermi(String permission) {
        if (!StringUtils.hasText(permission)) {
            return false;
        }
        LoginUser loginUser = currentUser();
        if (loginUser == null) {
            return false;
        }
        Set<String> perms = loginUser.getPermissions();
        return perms != null && (perms.contains(Constants.ALL_PERMISSION) || perms.contains(permission));
    }

    /** 具备任一权限（逗号分隔）。 */
    public boolean hasAnyPermi(String permissions) {
        if (!StringUtils.hasText(permissions)) {
            return false;
        }
        for (String p : permissions.split(",")) {
            if (hasPermi(p.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 是否具备某角色（admin 通配全部）。 */
    public boolean hasRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        LoginUser loginUser = currentUser();
        if (loginUser == null) {
            return false;
        }
        Set<String> roles = loginUser.getRoles();
        return roles != null && (roles.contains(Constants.SUPER_ADMIN_ROLE) || roles.contains(role));
    }

    public boolean hasAnyRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return false;
        }
        for (String r : roles.split(",")) {
            if (hasRole(r.trim())) {
                return true;
            }
        }
        return false;
    }

    private LoginUser currentUser() {
        try {
            return SecurityUtils.getLoginUser();
        } catch (Exception e) {
            return null;
        }
    }
}
