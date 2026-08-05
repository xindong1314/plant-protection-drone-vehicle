package com.ppdv.api.system.service;

import com.ppdv.api.system.domain.SysDictType;
import com.ppdv.api.system.repository.SysDictTypeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SysDictTypeService {

    private final SysDictTypeRepository dictTypeRepository;

    public SysDictTypeService(SysDictTypeRepository dictTypeRepository) {
        this.dictTypeRepository = dictTypeRepository;
    }

    public Page<SysDictType> selectPage(SysDictType filter, Pageable pageable) {
        return dictTypeRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysDictType> buildSpec(SysDictType filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getDictName())) {
                    ps.add(cb.like(root.get("dictName"), "%" + filter.getDictName() + "%"));
                }
                if (StringUtils.hasText(filter.getDictType())) {
                    ps.add(cb.like(root.get("dictType"), "%" + filter.getDictType() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public List<SysDictType> selectAll() {
        return dictTypeRepository.findAll();
    }

    public SysDictType selectById(Long dictId) {
        return dictTypeRepository.findById(dictId).orElse(null);
    }

    public boolean checkDictTypeUnique(SysDictType dictType) {
        return !dictTypeRepository.existsByDictType(dictType.getDictType());
    }

    public SysDictType insert(SysDictType dictType) {
        dictType.setCreateTime(LocalDateTime.now());
        return dictTypeRepository.save(dictType);
    }

    public SysDictType update(SysDictType dictType) {
        dictType.setUpdateTime(LocalDateTime.now());
        return dictTypeRepository.save(dictType);
    }

    public void deleteByIds(Long[] dictIds) {
        for (Long id : dictIds) {
            dictTypeRepository.deleteById(id);
        }
    }
}
