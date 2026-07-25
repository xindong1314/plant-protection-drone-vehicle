package com.ppdv.api.system.repository;

import com.ppdv.api.system.domain.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findByDelFlagOrderByParentIdAscOrderNumAsc(String delFlag);

    List<SysDept> findByParentIdAndDelFlag(Long parentId, String delFlag);

    long countByParentIdAndDelFlag(Long parentId, String delFlag);
}
