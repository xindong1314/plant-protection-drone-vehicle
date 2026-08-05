package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysRoleRepository extends JpaRepository<SysRole, Long>, JpaSpecificationExecutor<SysRole> {

    List<SysRole> findByDelFlagOrderByRoleSortAsc(String delFlag);

    boolean existsByRoleKeyAndDelFlag(String roleKey, String delFlag);

    boolean existsByRoleNameAndDelFlag(String roleName, String delFlag);

    @Query("select r from SysRole r, SysUserRole ur where r.roleId = ur.roleId " +
            "and ur.userId = :userId and r.delFlag = '0'")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
