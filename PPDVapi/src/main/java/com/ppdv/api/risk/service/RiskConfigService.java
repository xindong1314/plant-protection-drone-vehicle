package com.ppdv.api.risk.service;

import com.ppdv.api.risk.domain.RiskConfig;
import com.ppdv.api.risk.repository.RiskConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RiskConfigService {

    private final RiskConfigRepository repository;

    public RiskConfigService(RiskConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RiskConfig> listAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    /** 按分类分组返回配置列表。 */
    @Transactional(readOnly = true)
    public Map<String, List<RiskConfig>> listGrouped() {
        return repository.findAllByOrderBySortOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        RiskConfig::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public RiskConfig get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: " + id));
    }

    @Transactional
    public RiskConfig update(Long id, String configValue) {
        RiskConfig cfg = get(id);
        cfg.setConfigValue(configValue);
        return repository.save(cfg);
    }

    /** 读取整数值，查不到返回 defaultValue。 */
    public int getInt(String configKey, int defaultValue) {
        return repository.findByConfigKey(configKey)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
