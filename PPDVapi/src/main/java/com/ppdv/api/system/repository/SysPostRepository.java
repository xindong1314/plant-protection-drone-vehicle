package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysPostRepository extends JpaRepository<SysPost, Long>, JpaSpecificationExecutor<SysPost> {

    boolean existsByPostCode(String postCode);

    boolean existsByPostName(String postName);

    List<SysPost> findByOrderByPostSortAsc();

    @Query("select p.postId from SysPost p, SysUserPost up where p.postId = up.postId and up.userId = :userId")
    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);
}
