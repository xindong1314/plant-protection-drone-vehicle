package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByOrderByParentIdAscOrderNumAsc();

    long countByParentId(Long parentId);

    /** 用户拥有的权限标识 */
    @Query("select distinct m.perms from SysMenu m, SysRoleMenu rm, SysUserRole ur, SysRole r " +
            "where m.menuId = rm.menuId and rm.roleId = ur.roleId and rm.roleId = r.roleId " +
            "and ur.userId = :userId and r.status = '0' and m.status = '0' " +
            "and m.perms is not null and m.perms <> ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /** 全部权限标识（管理员用） */
    @Query("select distinct m.perms from SysMenu m where m.perms is not null and m.perms <> ''")
    List<String> selectAllPerms();

    /** 用户可见菜单树（目录+菜单） */
    @Query("select distinct m from SysMenu m, SysRoleMenu rm, SysUserRole ur, SysRole r " +
            "where m.menuId = rm.menuId and rm.roleId = ur.roleId and rm.roleId = r.roleId " +
            "and ur.userId = :userId and r.status = '0' and m.status = '0' and m.menuType in ('M','C') " +
            "order by m.parentId asc, m.orderNum asc")
    List<SysMenu> selectMenuTreeByUserId(@Param("userId") Long userId);

    /** 全部菜单树（目录+菜单，管理员用） */
    @Query("select m from SysMenu m where m.status = '0' and m.menuType in ('M','C') " +
            "order by m.parentId asc, m.orderNum asc")
    List<SysMenu> selectMenuTreeAll();
}
