package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysDictType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long>, JpaSpecificationExecutor<SysDictType> {

    Optional<SysDictType> findByDictType(String dictType);

    boolean existsByDictType(String dictType);
}
