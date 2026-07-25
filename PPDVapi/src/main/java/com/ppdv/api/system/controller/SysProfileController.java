package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.security.LoginUser;
import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.security.TokenService;
import com.ppdv.api.system.domain.SysRole;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.service.SysRoleService;
import com.ppdv.api.system.service.SysUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * 个人中心：查看资料、修改资料、修改密码。
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final TokenService tokenService;

    public SysProfileController(SysUserService userService, SysRoleService roleService, TokenService tokenService) {
        this.userService = userService;
        this.roleService = roleService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public AjaxResult profile() {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userService.selectUserById(userId);
        AjaxResult ajax = AjaxResult.success(user);
        String roleGroup = roleService.selectRolesByUserId(userId).stream()
                .map(SysRole::getRoleName).collect(Collectors.joining(","));
        ajax.put("roleGroup", roleGroup);
        ajax.put("postGroup", "");
        return ajax;
    }

    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        user.setUserId(loginUser.getUserId());
        userService.updateUserProfile(user);
        // 同步刷新会话中的昵称
        loginUser.setNickName(user.getNickName());
        tokenService.setLoginUser(loginUser);
        return AjaxResult.success();
    }

    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(String oldPassword, String newPassword) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser db = userService.selectUserById(loginUser.getUserId());
        if (db == null) {
            return AjaxResult.error("用户不存在");
        }
        if (!SecurityUtils.matchesPassword(oldPassword, db.getPassword())) {
            return AjaxResult.error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(newPassword, db.getPassword())) {
            return AjaxResult.error("新密码不能与旧密码相同");
        }
        userService.updateUserPwd(loginUser.getUserId(), SecurityUtils.encryptPassword(newPassword));
        return AjaxResult.success();
    }
}
