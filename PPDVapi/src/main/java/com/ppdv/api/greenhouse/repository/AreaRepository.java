package com.ppdv.api.greenhouse.repository;

import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<GreenhouseArea, Long>, JpaSpecificationExecutor<GreenhouseArea> {

    Optional<GreenhouseArea> findByAreaCode(String areaCode);

    boolean existsByAreaCode(String areaCode);

    List<GreenhouseArea> findAllByOrderBySortOrderAsc();
}
