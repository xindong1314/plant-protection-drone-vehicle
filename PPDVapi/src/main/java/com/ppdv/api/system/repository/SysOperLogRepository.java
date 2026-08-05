package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysOperLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysOperLogRepository extends JpaRepository<SysOperLog, Long>, JpaSpecificationExecutor<SysOperLog> {
}
