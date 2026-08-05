package com.ppdv.api.system.service;

import com.ppdv.api.system.domain.SysDictData;
import com.ppdv.api.system.repository.SysDictDataRepository;
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
public class SysDictDataService {

    private final SysDictDataRepository dictDataRepository;

    public SysDictDataService(SysDictDataRepository dictDataRepository) {
        this.dictDataRepository = dictDataRepository;
    }

    public Page<SysDictData> selectPage(SysDictData filter, Pageable pageable) {
        return dictDataRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysDictData> buildSpec(SysDictData filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getDictType())) {
                    ps.add(cb.equal(root.get("dictType"), filter.getDictType()));
                }
                if (StringUtils.hasText(filter.getDictLabel())) {
                    ps.add(cb.like(root.get("dictLabel"), "%" + filter.getDictLabel() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 供前端 useDict 使用：按类型取启用的字典项。 */
    public List<SysDictData> selectByType(String dictType) {
        return dictDataRepository.findByDictTypeAndStatusOrderByDictSortAsc(dictType, "0");
    }

    public SysDictData selectByCode(Long dictCode) {
        return dictDataRepository.findById(dictCode).orElse(null);
    }

    public SysDictData insert(SysDictData data) {
        data.setCreateTime(LocalDateTime.now());
        return dictDataRepository.save(data);
    }

    public SysDictData update(SysDictData data) {
        data.setUpdateTime(LocalDateTime.now());
        return dictDataRepository.save(data);
    }

    public void deleteByCodes(Long[] dictCodes) {
        for (Long code : dictCodes) {
            dictDataRepository.deleteById(code);
        }
    }
}
