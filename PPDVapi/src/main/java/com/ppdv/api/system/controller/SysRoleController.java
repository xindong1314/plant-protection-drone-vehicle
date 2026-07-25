package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysRole;
import com.ppdv.api.system.domain.SysUser;
import com.ppdv.api.system.service.SysRoleService;
import com.ppdv.api.system.service.SysUserService;
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
@RequestMapping("/system/role")
public class SysRoleController extends BaseController {

    private final SysRoleService roleService;
    private final SysUserService userService;

    public SysRoleController(SysRoleService roleService, SysUserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysRole filter, PageQuery pageQuery) {
        return getDataTable(roleService.selectRolePage(filter, pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/{roleId}")
    public AjaxResult getInfo(@PathVariable Long roleId) {
        return AjaxResult.success(roleService.selectRoleById(roleId));
    }

    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysRole role) {
        if (!roleService.checkRoleNameUnique(role)) {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        if (!roleService.checkRoleKeyUnique(role)) {
            return AjaxResult.error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(username());
        roleService.insertRole(role);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysRole role) {
        role.setUpdateBy(username());
        roleService.updateRole(role);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysRole role) {
        roleService.updateRoleStatus(role);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/dataScope")
    public AjaxResult dataScope(@RequestBody SysRole role) {
        roleService.authDataScope(role);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @DeleteMapping("/{roleIds}")
    public AjaxResult remove(@PathVariable Long[] roleIds) {
        roleService.deleteRoleByIds(roleIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        return AjaxResult.success(roleService.selectRoleAll());
    }

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/allocatedList")
    public TableDataInfo allocatedList(Long roleId) {
        return getDataTable(userService.selectAllocatedList(roleId));
    }

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/unallocatedList")
    public TableDataInfo unallocatedList(Long roleId) {
        return getDataTable(userService.selectUnallocatedList(roleId));
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/authUser/cancel")
    public AjaxResult cancelAuthUser(@RequestBody SysUser userRole) {
        roleService.deleteAuthUser(userRole.getRoleId(), userRole.getUserId());
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/authUser/cancelAll")
    public AjaxResult cancelAuthUserAll(Long roleId, Long[] userIds) {
        roleService.deleteAuthUsers(roleId, userIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/authUser/selectAll")
    public AjaxResult selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.insertAuthUsers(roleId, userIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/deptTree/{roleId}")
    public AjaxResult deptTree(@PathVariable Long roleId) {
        return AjaxResult.success(); // 由 SysDeptController.roleDeptTreeselect 提供
    }
}
