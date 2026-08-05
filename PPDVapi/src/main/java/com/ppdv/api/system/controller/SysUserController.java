package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.system.domain.SysRole;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.service.SysDeptService;
import com.ppdv.api.system.service.SysPostService;
import com.ppdv.api.system.service.SysRoleService;
import com.ppdv.api.system.service.SysUserService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/user")
public class SysUserController extends BaseController {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysPostService postService;
    private final SysDeptService deptService;

    public SysUserController(SysUserService userService, SysRoleService roleService,
                            SysPostService postService, SysDeptService deptService) {
        this.userService = userService;
        this.roleService = roleService;
        this.postService = postService;
        this.deptService = deptService;
    }

    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser filter, PageQuery pageQuery) {
        Page<SysUser> page = userService.selectUserPage(filter, pageQuery.toPageable());
        return getDataTable(page);
    }

    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping({"/", "/{userId}"})
    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        AjaxResult ajax = AjaxResult.success();
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", roles);
        ajax.put("posts", postService.selectPostAll());
        if (userId != null) {
            SysUser user = userService.selectUserById(userId);
            ajax.put("data", user);
            ajax.put("postIds", userService.selectPostIdsByUserId(userId));
            ajax.put("roleIds", userService.selectRoleIdsByUserId(userId));
        }
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysUser user) {
        if (!userService.checkUserNameUnique(user)) {
            return AjaxResult.error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        user.setCreateBy(username());
        userService.insertUser(user);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysUser user) {
        if (SecurityUtils.isAdmin(user.getUserId())) {
            return AjaxResult.error("不允许操作超级管理员用户");
        }
        user.setUpdateBy(username());
        userService.updateUser(user);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds) {
        userService.deleteUserByIds(userIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user) {
        if (SecurityUtils.isAdmin(user.getUserId())) {
            return AjaxResult.error("不允许操作超级管理员用户");
        }
        userService.resetPwd(user);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user) {
        if (SecurityUtils.isAdmin(user.getUserId())) {
            return AjaxResult.error("不允许操作超级管理员用户");
        }
        userService.updateUserStatus(user);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping("/authRole/{userId}")
    public AjaxResult authRole(@PathVariable Long userId) {
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<Long> roleIds = userService.selectRoleIdsByUserId(userId);
        List<SysRole> roles = roleService.selectRoleAll();
        roles.forEach(r -> r.setMenuIds(null));
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("roleIds", roleIds);
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @PutMapping("/authRole")
    public AjaxResult insertAuthRole(Long userId, Long[] roleIds) {
        userService.insertUserAuth(userId, roleIds);
        return AjaxResult.success();
    }

    @GetMapping("/deptTree")
    public AjaxResult deptTree(com.ppdv.api.system.domain.SysDept dept) {
        return AjaxResult.success(deptService.buildDeptTreeSelect(deptService.selectDeptList(dept)));
    }
}
