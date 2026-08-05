package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByUserNameAndDelFlag(String userName, String delFlag);

    boolean existsByUserNameAndDelFlag(String userName, String delFlag);

    boolean existsByPhonenumberAndDelFlag(String phonenumber, String delFlag);

    boolean existsByEmailAndDelFlag(String email, String delFlag);

    long countByDeptIdAndDelFlag(Long deptId, String delFlag);

    @Query("select u from SysUser u, SysUserRole ur where u.userId = ur.userId " +
            "and ur.roleId = :roleId and u.delFlag = '0'")
    List<SysUser> selectAllocatedByRoleId(@Param("roleId") Long roleId);
}
