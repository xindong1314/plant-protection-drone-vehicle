package com.ppdv.api.risk.repository;

import com.ppdv.api.risk.domain.RiskConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskConfigRepository extends JpaRepository<RiskConfig, Long> {

    Optional<RiskConfig> findByConfigKey(String configKey);

    List<RiskConfig> findAllByOrderBySortOrderAsc();

    List<RiskConfig> findByCategoryOrderBySortOrderAsc(String category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RiskConfig c where c.id = :id")
    Optional<RiskConfig> findByIdForUpdate(@Param("id") Long id);

    /** 固定按 configKey 顺序锁住两个阈值行，避免并发交叉更新形成无效组合。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RiskConfig c "
            + "where c.configKey in ('risk.threshold.high', 'risk.threshold.suspected') "
            + "order by c.configKey asc")
    List<RiskConfig> findThresholdsForUpdate();
}
