package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysUserPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUserPostRepository extends JpaRepository<SysUserPost, SysUserPost.Pk> {

    long countByPostId(Long postId);

    @Modifying
    @Query("delete from SysUserPost up where up.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
