package com.ppdv.api.security;

import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.service.SysPermissionService;
import com.ppdv.api.system.service.SysUserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 登录时按账号加载用户并组装权限。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserService userService;
    private final SysPermissionService permissionService;

    public UserDetailsServiceImpl(SysUserService userService, SysPermissionService permissionService) {
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userService.selectUserByUserName(username)
                .orElseThrow(() -> new ServiceException("登录用户：" + username + " 不存在"));
        if ("1".equals(user.getStatus())) {
            throw new ServiceException("对不起，您的账号：" + username + " 已停用");
        }
        return createLoginUser(user);
    }

    private LoginUser createLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUserName(user.getUserName());
        loginUser.setNickName(user.getNickName());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setStatus(user.getStatus());
        loginUser.setPassword(user.getPassword());
        loginUser.setRoles(permissionService.getRolePermission(user));
        loginUser.setPermissions(permissionService.getMenuPermission(user));
        return loginUser;
    }
}
