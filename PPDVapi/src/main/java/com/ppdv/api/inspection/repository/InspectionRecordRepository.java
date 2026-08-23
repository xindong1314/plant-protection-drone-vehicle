package com.ppdv.api.inspection.repository;

import com.ppdv.api.inspection.domain.InspectionRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, Long>, JpaSpecificationExecutor<InspectionRecord> {

    List<InspectionRecord> findByDeviceIdOrderByStartTimeDesc(String deviceId);

    List<InspectionRecord> findByAreaIdOrderByStartTimeDesc(Long areaId);

    boolean existsByAreaId(Long areaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InspectionRecord r where r.id = :id")
    java.util.Optional<InspectionRecord> findByIdForUpdate(@Param("id") Long id);
}
