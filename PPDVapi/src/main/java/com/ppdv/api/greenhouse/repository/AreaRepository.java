package com.ppdv.api.greenhouse.repository;

import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<GreenhouseArea, Long>, JpaSpecificationExecutor<GreenhouseArea> {

    Optional<GreenhouseArea> findByAreaCode(String areaCode);

    boolean existsByAreaCode(String areaCode);

    boolean existsByAreaCodeAndIdNot(String areaCode, Long id);

    List<GreenhouseArea> findAllByOrderBySortOrderAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GreenhouseArea a where a.id = :id")
    Optional<GreenhouseArea> findByIdForUpdate(@Param("id") Long id);
}
