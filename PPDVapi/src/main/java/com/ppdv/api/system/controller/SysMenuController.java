package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysMenu;
import com.ppdv.api.system.service.SysMenuService;
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
@RequestMapping("/system/menu")
public class SysMenuController extends BaseController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @GetMapping("/list")
    public AjaxResult list(SysMenu filter) {
        return AjaxResult.success(menuService.selectMenuList(filter));
    }

    @PreAuthorize("@ss.hasPermi('system:menu:query')")
    @GetMapping("/{menuId}")
    public AjaxResult getInfo(@PathVariable Long menuId) {
        return AjaxResult.success(menuService.selectMenuById(menuId));
    }

    @GetMapping("/treeselect")
    public AjaxResult treeselect(SysMenu filter) {
        return AjaxResult.success(menuService.buildMenuTreeSelect(menuService.selectMenuList(filter)));
    }

    @GetMapping("/roleMenuTreeselect/{roleId}")
    public AjaxResult roleMenuTreeselect(@PathVariable Long roleId) {
        List<SysMenu> menus = menuService.selectMenuAll();
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", menuService.selectMenuIdsByRoleId(roleId));
        ajax.put("menus", menuService.buildMenuTreeSelect(menus));
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('system:menu:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysMenu menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return AjaxResult.error("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        menu.setCreateBy(username());
        menuService.insertMenu(menu);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysMenu menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return AjaxResult.error("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        if (menu.getMenuId().equals(menu.getParentId())) {
            return AjaxResult.error("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        menu.setUpdateBy(username());
        menuService.updateMenu(menu);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:menu:remove')")
    @DeleteMapping("/{menuId}")
    public AjaxResult remove(@PathVariable Long menuId) {
        if (menuService.hasChildByMenuId(menuId)) {
            return AjaxResult.warn("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId)) {
            return AjaxResult.warn("菜单已分配,不允许删除");
        }
        menuService.deleteMenuById(menuId);
        return AjaxResult.success();
    }
}
