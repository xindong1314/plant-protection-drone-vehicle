package com.ppdv.api.task.repository;

import com.ppdv.api.task.domain.SprayTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SprayTaskRepository extends JpaRepository<SprayTask, Long>, JpaSpecificationExecutor<SprayTask> {

    List<SprayTask> findByDeviceIdOrderByCreateTimeDesc(String deviceId);

    List<SprayTask> findByAreaIdOrderByCreateTimeDesc(Long areaId);

    List<SprayTask> findByStatusOrderByCreateTimeAsc(String status);

    Page<SprayTask> findByStatus(String status, Pageable pageable);

    Optional<SprayTask> findFirstByAreaIdAndStatusOrderByCompletedAtDesc(Long areaId, String status);

    boolean existsByAreaId(Long areaId);

    boolean existsByAreaIdAndStatusIn(Long areaId, Set<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SprayTask t where t.id = :id")
    Optional<SprayTask> findByIdForUpdate(@Param("id") Long id);
}
