package com.ppdv.api.archive.repository;

import com.ppdv.api.archive.domain.GreenhouseArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ArchiveRepository extends JpaRepository<GreenhouseArchive, Long>, JpaSpecificationExecutor<GreenhouseArchive> {

    Optional<GreenhouseArchive> findByAreaId(Long areaId);
}
