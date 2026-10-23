package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysNoticeRead;
import com.ppdv.api.system.domain.vo.SysNoticeReadVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysNoticeReadRepository extends JpaRepository<SysNoticeRead, Long>, JpaSpecificationExecutor<SysNoticeRead> {

    boolean existsByNoticeIdAndUserId(Long noticeId, Long userId);

    List<SysNoticeRead> findByUserIdAndNoticeIdIn(Long userId, List<Long> noticeIds);

    @Modifying
    @Query("delete from SysNoticeRead r where r.noticeId in :noticeIds")
    void deleteByNoticeIds(@Param("noticeIds") List<Long> noticeIds);

    /** 已读用户分页：关联 sys_user / sys_dept 投影，searchValue 模糊匹配登录名称/用户名称。 */
    @Query("select new com.ppdv.api.system.domain.vo.SysNoticeReadVo(u.userName, u.nickName, u.phonenumber, d.deptName, r.readTime) "
            + "from SysNoticeRead r "
            + "join SysUser u on r.userId = u.userId "
            + "left join SysDept d on u.deptId = d.deptId "
            + "where r.noticeId = :noticeId "
            + "and (u.userName like concat('%', :searchValue, '%') or u.nickName like concat('%', :searchValue, '%')) "
            + "order by r.readTime desc")
    Page<SysNoticeReadVo> selectReadUsers(@Param("noticeId") Long noticeId, @Param("searchValue") String searchValue, Pageable pageable);
}
