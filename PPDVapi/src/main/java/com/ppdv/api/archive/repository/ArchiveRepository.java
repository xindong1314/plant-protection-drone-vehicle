package com.ppdv.api.archive.repository;

import com.ppdv.api.archive.domain.GreenhouseArchive;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ArchiveRepository extends JpaRepository<GreenhouseArchive, Long>, JpaSpecificationExecutor<GreenhouseArchive> {

    Optional<GreenhouseArchive> findByAreaId(Long areaId);

    boolean existsByAreaId(Long areaId);

    void deleteByAreaId(Long areaId);

    List<GreenhouseArchive> findByAreaIdIn(Collection<Long> areaIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GreenhouseArchive a where a.areaId = :areaId")
    Optional<GreenhouseArchive> findByAreaIdForUpdate(@Param("areaId") Long areaId);
}
