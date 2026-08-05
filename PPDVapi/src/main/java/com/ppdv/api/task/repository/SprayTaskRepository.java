package com.ppdv.api.task.repository;

import com.ppdv.api.task.domain.SprayTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SprayTaskRepository extends JpaRepository<SprayTask, Long>, JpaSpecificationExecutor<SprayTask> {

    List<SprayTask> findByDeviceIdOrderByCreateTimeDesc(String deviceId);

    List<SprayTask> findByAreaIdOrderByCreateTimeDesc(Long areaId);

    List<SprayTask> findByStatusOrderByCreateTimeAsc(String status);

    Optional<SprayTask> findFirstByAreaIdAndStatusOrderByCompletedAtDesc(Long areaId, String status);
}
