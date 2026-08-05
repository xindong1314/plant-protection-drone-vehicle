package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysRoleDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysRoleDeptRepository extends JpaRepository<SysRoleDept, SysRoleDept.Pk> {

    @Query("select rd.deptId from SysRoleDept rd where rd.roleId = :roleId")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("delete from SysRoleDept rd where rd.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
