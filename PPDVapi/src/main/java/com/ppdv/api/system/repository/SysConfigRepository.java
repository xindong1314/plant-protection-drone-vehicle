package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysConfigRepository extends JpaRepository<SysConfig, Long>, JpaSpecificationExecutor<SysConfig> {

    Optional<SysConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
