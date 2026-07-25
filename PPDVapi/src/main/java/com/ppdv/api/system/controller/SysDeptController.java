package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysDept;
import com.ppdv.api.system.service.SysDeptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController {

    private final SysDeptService deptService;

    public SysDeptController(SysDeptService deptService) {
        this.deptService = deptService;
    }

    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list")
    public AjaxResult list(SysDept filter) {
        return AjaxResult.success(deptService.selectDeptList(filter));
    }

    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list/exclude/{deptId}")
    public AjaxResult excludeChild(@PathVariable Long deptId) {
        return AjaxResult.success(deptService.selectDeptListExcludeChild(deptId));
    }

    @PreAuthorize("@ss.hasPermi('system:dept:query')")
    @GetMapping("/{deptId}")
    public AjaxResult getInfo(@PathVariable Long deptId) {
        return AjaxResult.success(deptService.selectDeptById(deptId));
    }

    @GetMapping("/roleDeptTreeselect/{roleId}")
    public AjaxResult roleDeptTreeselect(@PathVariable Long roleId) {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", deptService.selectDeptIdsByRoleId(roleId));
        ajax.put("depts", deptService.buildDeptTreeSelect(deptService.selectDeptList(new SysDept())));
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:dept:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return AjaxResult.error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        dept.setCreateBy(username());
        deptService.insertDept(dept);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysDept dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return AjaxResult.error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        if (dept.getDeptId().equals(dept.getParentId())) {
            return AjaxResult.error("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        }
        dept.setUpdateBy(username());
        deptService.updateDept(dept);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    @DeleteMapping("/{deptId}")
    public AjaxResult remove(@PathVariable Long deptId) {
        if (deptService.hasChildByDeptId(deptId)) {
            return AjaxResult.warn("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            return AjaxResult.warn("部门存在用户,不允许删除");
        }
        deptService.deleteDeptById(deptId);
        return AjaxResult.success();
    }
}
