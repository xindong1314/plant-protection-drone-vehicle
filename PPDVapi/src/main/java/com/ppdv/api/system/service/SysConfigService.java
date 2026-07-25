package com.ppdv.api.system.service;

import com.ppdv.api.system.domain.SysConfig;
import com.ppdv.api.system.repository.SysConfigRepository;
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
public class SysConfigService {

    private final SysConfigRepository configRepository;

    public SysConfigService(SysConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public Page<SysConfig> selectPage(SysConfig filter, Pageable pageable) {
        return configRepository.findAll(buildSpec(filter), pageable);
    }

    private Specification<SysConfig> buildSpec(SysConfig filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getConfigName())) {
                    ps.add(cb.like(root.get("configName"), "%" + filter.getConfigName() + "%"));
                }
                if (StringUtils.hasText(filter.getConfigKey())) {
                    ps.add(cb.like(root.get("configKey"), "%" + filter.getConfigKey() + "%"));
                }
                if (StringUtils.hasText(filter.getConfigType())) {
                    ps.add(cb.equal(root.get("configType"), filter.getConfigType()));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public SysConfig selectById(Long configId) {
        return configRepository.findById(configId).orElse(null);
    }

    public String selectConfigByKey(String configKey) {
        return configRepository.findByConfigKey(configKey).map(SysConfig::getConfigValue).orElse("");
    }

    public boolean checkConfigKeyUnique(SysConfig config) {
        return !configRepository.existsByConfigKey(config.getConfigKey());
    }

    public SysConfig insert(SysConfig config) {
        config.setCreateTime(LocalDateTime.now());
        return configRepository.save(config);
    }

    public SysConfig update(SysConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return configRepository.save(config);
    }

    public void deleteByIds(Long[] configIds) {
        for (Long id : configIds) {
            configRepository.deleteById(id);
        }
    }
}
