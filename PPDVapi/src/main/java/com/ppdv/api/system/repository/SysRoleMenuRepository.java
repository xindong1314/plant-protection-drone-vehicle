package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysRoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenu.Pk> {

    long countByMenuId(Long menuId);

    @Query("select rm.menuId from SysRoleMenu rm where rm.roleId = :roleId")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("delete from SysRoleMenu rm where rm.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
