package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysLogininfor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysLogininforRepository extends JpaRepository<SysLogininfor, Long>, JpaSpecificationExecutor<SysLogininfor> {
}
