package com.ppdv.api.inspection.repository;

import com.ppdv.api.inspection.domain.InspectionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface InspectionImageRepository extends JpaRepository<InspectionImage, Long>, JpaSpecificationExecutor<InspectionImage> {

    List<InspectionImage> findByInspectionIdOrderByCapturedAtAsc(Long inspectionId);

    List<InspectionImage> findByAreaIdOrderByCapturedAtDesc(Long areaId);
}
