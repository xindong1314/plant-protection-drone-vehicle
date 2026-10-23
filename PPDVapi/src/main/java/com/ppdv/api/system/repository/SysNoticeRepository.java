package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysNotice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long>, JpaSpecificationExecutor<SysNotice> {

    /** 指定状态下按 createTime 倒序取一页（头部公告取前 10 条）。 */
    List<SysNotice> findByStatusOrderByCreateTimeDescNoticeIdDesc(String status, Pageable pageable);
}
