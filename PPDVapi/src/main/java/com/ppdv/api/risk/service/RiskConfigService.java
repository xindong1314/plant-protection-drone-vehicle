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

    private static final String SUSPECTED_THRESHOLD = "risk.threshold.suspected";
    private static final String HIGH_THRESHOLD = "risk.threshold.high";
    private static final Map<String, IntRange> VALUE_RANGES = Map.ofEntries(
            Map.entry("risk.score.pest", new IntRange(0, 100)),
            Map.entry("risk.score.disease", new IntRange(0, 100)),
            Map.entry("risk.score.wilt", new IntRange(0, 100)),
            Map.entry("risk.score.yellow", new IntRange(0, 100)),
            Map.entry("risk.score.spot", new IntRange(0, 100)),
            Map.entry("risk.score.detection_max", new IntRange(0, 100)),
            Map.entry("risk.env.hot_humid", new IntRange(0, 100)),
            Map.entry("risk.env.dry", new IntRange(0, 100)),
            Map.entry("risk.env.glare", new IntRange(0, 100)),
            Map.entry("risk.history.consecutive", new IntRange(0, 100)),
            Map.entry("risk.history.spray_fail", new IntRange(0, 100)),
            Map.entry("risk.history.spray_ok", new IntRange(-100, 0)),
            Map.entry(SUSPECTED_THRESHOLD, new IntRange(0, 99)),
            Map.entry(HIGH_THRESHOLD, new IntRange(1, 100))
    );

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
        RiskConfig snapshot = get(id);
        Map<String, RiskConfig> lockedThresholds = Map.of();
        RiskConfig cfg;
        if (isThreshold(snapshot.getConfigKey())) {
            lockedThresholds = repository.findThresholdsForUpdate().stream()
                    .collect(Collectors.toMap(RiskConfig::getConfigKey, config -> config));
            cfg = lockedThresholds.get(snapshot.getConfigKey());
            if (cfg == null || !cfg.getId().equals(id)) {
                throw new IllegalStateException("风险阈值配置不存在: " + snapshot.getConfigKey());
            }
        } else {
            cfg = repository.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalArgumentException("配置不存在: " + id));
        }
        int value = parseAndValidate(cfg.getConfigKey(), configValue);
        validateThresholdOrder(cfg.getConfigKey(), value, lockedThresholds);
        // 统一保存规范化后的十进制表示，避免空白、前导符号等歧义。
        cfg.setConfigValue(Integer.toString(value));
        return repository.save(cfg);
    }

    /** 读取整数值，查不到返回 defaultValue。 */
    public int getInt(String configKey, int defaultValue) {
        return repository.findByConfigKey(configKey)
                .map(c -> parseAndValidate(configKey, c.getConfigValue()))
                .orElse(defaultValue);
    }

    private int parseAndValidate(String configKey, String configValue) {
        IntRange range = VALUE_RANGES.get(configKey);
        if (range == null) {
            throw new IllegalArgumentException("不支持修改的风险配置项: " + configKey);
        }
        if (configValue == null || configValue.isBlank()) {
            throw new IllegalArgumentException("风险配置值不能为空: " + configKey);
        }

        final int value;
        try {
            value = Integer.parseInt(configValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("风险配置值必须是 Java int 范围内的整数: " + configKey, e);
        }
        if (value < range.min() || value > range.max()) {
            throw new IllegalArgumentException("风险配置值超出允许范围: " + configKey
                    + " 必须在 [" + range.min() + "," + range.max() + "] 之间");
        }
        return value;
    }

    private void validateThresholdOrder(String configKey,
                                        int candidateValue,
                                        Map<String, RiskConfig> lockedThresholds) {
        if (!isThreshold(configKey)) {
            return;
        }

        int suspected = SUSPECTED_THRESHOLD.equals(configKey)
                ? candidateValue
                : getLockedInt(lockedThresholds, SUSPECTED_THRESHOLD);
        int high = HIGH_THRESHOLD.equals(configKey)
                ? candidateValue
                : getLockedInt(lockedThresholds, HIGH_THRESHOLD);
        if (suspected >= high) {
            throw new IllegalArgumentException("风险阈值必须满足疑似阈值 < 高风险阈值");
        }
    }

    private int getLockedInt(Map<String, RiskConfig> lockedThresholds, String configKey) {
        RiskConfig config = lockedThresholds.get(configKey);
        if (config == null) {
            throw new IllegalStateException("缺少风险配置项: " + configKey);
        }
        return parseAndValidate(configKey, config.getConfigValue());
    }

    private boolean isThreshold(String configKey) {
        return SUSPECTED_THRESHOLD.equals(configKey) || HIGH_THRESHOLD.equals(configKey);
    }

    private record IntRange(int min, int max) {
    }
}
