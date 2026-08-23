package com.ppdv.api.inspection.repository;

import com.ppdv.api.inspection.domain.DetectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface DetectionResultRepository extends JpaRepository<DetectionResult, Long>, JpaSpecificationExecutor<DetectionResult> {

    List<DetectionResult> findByImageIdOrderByIdAsc(Long imageId);

    List<DetectionResult> findByAreaIdOrderByIdDesc(Long areaId);

    List<DetectionResult> findByCategoryAndAreaId(String category, Long areaId);

    long countByAreaIdAndStatus(Long areaId, String status);

    boolean existsByAreaId(Long areaId);
}
