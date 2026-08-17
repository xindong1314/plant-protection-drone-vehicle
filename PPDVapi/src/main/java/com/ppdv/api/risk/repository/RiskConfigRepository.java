package com.ppdv.api.risk.repository;

import com.ppdv.api.risk.domain.RiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskConfigRepository extends JpaRepository<RiskConfig, Long> {

    Optional<RiskConfig> findByConfigKey(String configKey);

    List<RiskConfig> findAllByOrderBySortOrderAsc();

    List<RiskConfig> findByCategoryOrderBySortOrderAsc(String category);
}
