package com.ppdv.api.system.service;

import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.system.domain.SysMenu;
import com.ppdv.api.system.domain.vo.MetaVo;
import com.ppdv.api.system.domain.vo.RouterVo;
import com.ppdv.api.system.domain.vo.TreeSelect;
import com.ppdv.api.system.repository.SysMenuRepository;
import com.ppdv.api.system.repository.SysRoleMenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单/权限服务：动态路由构建、菜单树、菜单 CRUD。buildMenus 逻辑对齐若依。
 */
@Service
public class SysMenuService {

    private static final String TYPE_DIR = "M";
    private static final String TYPE_MENU = "C";
    private static final String NO_FRAME = "1";
    private static final String LAYOUT = "Layout";
    private static final String PARENT_VIEW = "ParentView";
    private static final String INNER_LINK = "InnerLink";

    private final SysMenuRepository menuRepository;
    private final SysRoleMenuRepository roleMenuRepository;

    public SysMenuService(SysMenuRepository menuRepository, SysRoleMenuRepository roleMenuRepository) {
        this.menuRepository = menuRepository;
        this.roleMenuRepository = roleMenuRepository;
    }

    // ---------------- 权限 ----------------

    public Set<String> selectMenuPermsByUserId(Long userId) {
        List<String> perms = SecurityUtils.isAdmin(userId)
                ? menuRepository.selectAllPerms()
                : menuRepository.selectPermsByUserId(userId);
        return new HashSet<>(perms);
    }

    // ---------------- 动态路由 ----------------

    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus = SecurityUtils.isAdmin(userId)
                ? menuRepository.selectMenuTreeAll()
                : menuRepository.selectMenuTreeByUserId(userId);
        return buildMenuTree(menus);
    }

    public List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            RouterVo router = new RouterVo();
            router.setHidden("1".equals(menu.getVisible()));
            router.setName(getRouteName(menu));
            router.setPath(getRouterPath(menu));
            router.setComponent(getComponent(menu));
            router.setQuery(menu.getQuery());
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), "1".equals(menu.getIsCache()), menu.getPath()));
            List<SysMenu> cMenus = menu.getChildren();
            if (cMenus != null && !cMenus.isEmpty() && TYPE_DIR.equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            } else if (isMenuFrame(menu)) {
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(StringUtils.capitalize(menu.getPath()));
                children.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), "1".equals(menu.getIsCache()), menu.getPath()));
                children.setQuery(menu.getQuery());
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    private String getRouteName(SysMenu menu) {
        return isMenuFrame(menu) ? "" : StringUtils.capitalize(menu.getPath());
    }

    private String getRouterPath(SysMenu menu) {
        String routerPath = menu.getPath();
        if (isParentZero(menu) && TYPE_DIR.equals(menu.getMenuType()) && NO_FRAME.equals(menu.getIsFrame())) {
            routerPath = "/" + menu.getPath();
        } else if (isMenuFrame(menu)) {
            routerPath = "/";
        }
        return routerPath;
    }

    private String getComponent(SysMenu menu) {
        String component = LAYOUT;
        if (StringUtils.hasText(menu.getComponent()) && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (!StringUtils.hasText(menu.getComponent()) && isParentView(menu)) {
            component = PARENT_VIEW;
        }
        return component;
    }

    private boolean isMenuFrame(SysMenu menu) {
        return isParentZero(menu) && TYPE_MENU.equals(menu.getMenuType()) && NO_FRAME.equals(menu.getIsFrame());
    }

    private boolean isParentView(SysMenu menu) {
        return !isParentZero(menu) && TYPE_DIR.equals(menu.getMenuType());
    }

    private boolean isParentZero(SysMenu menu) {
        return menu.getParentId() != null && menu.getParentId() == 0L;
    }

    // ---------------- 菜单树 ----------------

    public List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<>();
        List<Long> ids = menus.stream().map(SysMenu::getMenuId).collect(Collectors.toList());
        for (SysMenu menu : menus) {
            if (!ids.contains(menu.getParentId())) {
                recursionFn(menus, menu);
                returnList.add(menu);
            }
        }
        return returnList.isEmpty() ? menus : returnList;
    }

    private void recursionFn(List<SysMenu> list, SysMenu node) {
        List<SysMenu> children = list.stream()
                .filter(m -> node.getMenuId().equals(m.getParentId()))
                .collect(Collectors.toList());
        node.setChildren(children);
        for (SysMenu child : children) {
            recursionFn(list, child);
        }
    }

    public List<TreeSelect> buildMenuTreeSelect(List<SysMenu> menus) {
        return buildMenuTree(menus).stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    // ---------------- CRUD ----------------

    public List<SysMenu> selectMenuList(SysMenu filter) {
        return menuRepository.findByOrderByParentIdAscOrderNumAsc().stream()
                .filter(m -> !StringUtils.hasText(filter.getMenuName()) || m.getMenuName().contains(filter.getMenuName()))
                .filter(m -> !StringUtils.hasText(filter.getStatus()) || filter.getStatus().equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    public List<SysMenu> selectMenuAll() {
        return menuRepository.findByOrderByParentIdAscOrderNumAsc();
    }

    public SysMenu selectMenuById(Long menuId) {
        return menuRepository.findById(menuId).orElse(null);
    }

    public List<Long> selectMenuIdsByRoleId(Long roleId) {
        return roleMenuRepository.selectMenuIdsByRoleId(roleId);
    }

    public boolean hasChildByMenuId(Long menuId) {
        return menuRepository.countByParentId(menuId) > 0;
    }

    public boolean checkMenuExistRole(Long menuId) {
        return roleMenuRepository.countByMenuId(menuId) > 0;
    }

    public boolean checkMenuNameUnique(SysMenu menu) {
        return menuRepository.findByOrderByParentIdAscOrderNumAsc().stream()
                .noneMatch(m -> m.getMenuName().equals(menu.getMenuName())
                        && m.getParentId().equals(menu.getParentId())
                        && !m.getMenuId().equals(menu.getMenuId()));
    }

    public SysMenu insertMenu(SysMenu menu) {
        menu.setCreateTime(LocalDateTime.now());
        return menuRepository.save(menu);
    }

    public SysMenu updateMenu(SysMenu menu) {
        menu.setUpdateTime(LocalDateTime.now());
        return menuRepository.save(menu);
    }

    public void deleteMenuById(Long menuId) {
        menuRepository.deleteById(menuId);
    }
}
