package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysDictData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SysDictDataRepository extends JpaRepository<SysDictData, Long>, JpaSpecificationExecutor<SysDictData> {

    List<SysDictData> findByDictTypeAndStatusOrderByDictSortAsc(String dictType, String status);

    List<SysDictData> findByDictTypeOrderByDictSortAsc(String dictType);

    long countByDictType(String dictType);
}
