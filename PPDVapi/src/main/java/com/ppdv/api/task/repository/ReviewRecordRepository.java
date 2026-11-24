package com.ppdv.api.task.repository;

import com.ppdv.api.task.domain.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Collection;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long>, JpaSpecificationExecutor<ReviewRecord> {

    List<ReviewRecord> findBySprayTaskIdInOrderByReviewTimeDescIdDesc(Collection<Long> sprayTaskIds);

    boolean existsByAreaId(Long areaId);
}
