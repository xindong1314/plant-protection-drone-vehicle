package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.security.LoginBody;
import com.ppdv.api.security.LoginUser;
import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.security.SysLoginService;
import com.ppdv.api.system.domain.SysMenu;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.service.SysMenuService;
import com.ppdv.api.system.service.SysUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录 / 用户信息 / 动态路由（对齐若依前端契约）。
 */
@RestController
public class SysLoginController {

    private final SysLoginService loginService;
    private final SysMenuService menuService;
    private final SysUserService userService;

    public SysLoginController(SysLoginService loginService,
                             SysMenuService menuService,
                             SysUserService userService) {
        this.loginService = loginService;
        this.menuService = menuService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody) {
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(),
                loginBody.getCode(), loginBody.getUuid());
        AjaxResult ajax = AjaxResult.success();
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = userService.selectUserById(loginUser.getUserId());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", loginUser.getRoles());
        ajax.put("permissions", loginUser.getPermissions());
        return ajax;
    }

    @GetMapping("/getRouters")
    public AjaxResult getRouters() {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
}
