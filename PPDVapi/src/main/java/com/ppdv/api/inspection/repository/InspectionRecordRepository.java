package com.ppdv.api.inspection.repository;

import com.ppdv.api.inspection.domain.InspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, Long>, JpaSpecificationExecutor<InspectionRecord> {

    List<InspectionRecord> findByDeviceIdOrderByStartTimeDesc(String deviceId);

    List<InspectionRecord> findByAreaIdOrderByStartTimeDesc(Long areaId);
}
