package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, SysUserRole.Pk> {

    List<SysUserRole> findByUserId(Long userId);

    long countByRoleId(Long roleId);

    @Modifying
    @Query("delete from SysUserRole ur where ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from SysUserRole ur where ur.userId in :userIds")
    void deleteByUserIds(@Param("userIds") List<Long> userIds);

    @Modifying
    @Query("delete from SysUserRole ur where ur.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("delete from SysUserRole ur where ur.roleId = :roleId and ur.userId in :userIds")
    void deleteByRoleIdAndUserIds(@Param("roleId") Long roleId, @Param("userIds") List<Long> userIds);
}
