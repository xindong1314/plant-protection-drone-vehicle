package com.ppdv.api.risk.service;

import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceRepository;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.inspection.repository.DetectionResultRepository;
import com.ppdv.api.task.repository.SprayTaskRepository;
import com.ppdv.api.telemetry.TelemetryTsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final RiskConfigService configService;
    private final AreaService areaService;
    private final DetectionResultRepository detectionRepo;
    private final SprayTaskRepository sprayTaskRepo;
    private final DeviceRepository deviceRepo;
    private final ObjectProvider<TelemetryTsService> tsProvider;

    /** 检测类别中文名 → config key 后缀 */
    private static final Map<String, String> CATEGORY_KEY = Map.of(
            "虫害", "pest",
            "病斑", "disease",
            "萎蔫", "wilt",
            "黄化", "yellow",
            "斑点", "spot"
    );

    public RiskEvaluationService(RiskConfigService configService,
                                 AreaService areaService,
                                 DetectionResultRepository detectionRepo,
                                 SprayTaskRepository sprayTaskRepo,
                                 DeviceRepository deviceRepo,
                                 ObjectProvider<TelemetryTsService> tsProvider) {
        this.configService = configService;
        this.areaService = areaService;
        this.detectionRepo = detectionRepo;
        this.sprayTaskRepo = sprayTaskRepo;
        this.deviceRepo = deviceRepo;
        this.tsProvider = tsProvider;
    }

    /** 对单个区域执行风险评估，返回 {score, status}。 */
    @Transactional
    public Map<String, Object> evaluate(Long areaId) {
        GreenhouseArea area = areaService.get(areaId);

        int detectionScore = calcDetectionScore(area);
        int envScore = calcEnvScore(area);
        int historyScore = calcHistoryScore(area);

        int total = Math.min(100, detectionScore + envScore + historyScore);
        if (total < 0) total = 0;

        String status;
        int suspectedThreshold = configService.getInt("risk.threshold.suspected", 21);
        int highThreshold = configService.getInt("risk.threshold.high", 51);
        if (total < suspectedThreshold) {
            status = "NORMAL";
        } else if (total < highThreshold) {
            status = "SUSPICIOUS";
        } else {
            status = "HIGH_RISK";
        }

        areaService.updateRiskScoreAndStatus(areaId, total, status);
        log.info("区域 {} ({}): 检测={} 环境={} 历史={} 总分={} 状态={}",
                area.getAreaCode(), area.getAreaName(), detectionScore, envScore, historyScore, total, status);

        return Map.of("score", total, "status", status,
                "detectionScore", detectionScore,
                "envScore", envScore,
                "historyScore", historyScore);
    }

    // ---- 检测分 ----
    private int calcDetectionScore(GreenhouseArea area) {
        List<DetectionResult> results = detectionRepo.findByAreaIdOrderByIdDesc(area.getId());
        if (results == null || results.isEmpty()) return 0;

        int score = 0;
        int cap = configService.getInt("risk.score.detection_max", 80);
        for (DetectionResult r : results) {
            if ("IGNORED".equalsIgnoreCase(r.getStatus())) continue;
            if (r.getCategory() == null) continue;
            String key = CATEGORY_KEY.getOrDefault(r.getCategory(), null);
            if (key == null) continue;
            int baseScore = configService.getInt("risk.score." + key, 0);
            double conf = r.getConfidence() != null ? r.getConfidence() : 1.0;
            score += (int) (baseScore * conf);
            if (score >= cap) return cap;
        }
        return score;
    }

    // ---- 环境分 ----
    private int calcEnvScore(GreenhouseArea area) {
        TelemetryTsService ts = tsProvider.getIfAvailable();
        if (ts == null) return 0;

        // 找到当前在该区域的小车
        List<Device> devices = deviceRepo.findByCurrentZone(area.getAreaCode());
        if (devices.isEmpty()) return 0;

        // 取第一个设备的最近环境数据
        List<Map<String, Object>> envData = ts.queryEnvHistory(devices.get(0).getDeviceId(), 1);
        if (envData.isEmpty()) return 0;

        Map<String, Object> latest = envData.get(0);
        double temperature = toDouble(latest.get("temperature"));
        double humidity = toDouble(latest.get("humidity"));
        double light = toDouble(latest.get("light"));
        double soilMoisture = toDouble(latest.get("soil_moisture"));

        int score = 0;
        // 高温高湿 >30℃ >80%
        if (temperature > 30 && humidity > 80) {
            score += configService.getInt("risk.env.hot_humid", 5);
        }
        // 干旱胁迫 <20%
        if (soilMoisture > 0 && soilMoisture < 20) {
            score += configService.getInt("risk.env.dry", 3);
        }
        // 强光高温 >80000lx >35℃
        if (light > 80000 && temperature > 35) {
            score += configService.getInt("risk.env.glare", 2);
        }
        return score;
    }

    // ---- 历史分 ----
    private int calcHistoryScore(GreenhouseArea area) {
        int score = 0;

        // 查该区域最近一次完成的点喷任务
        var lastSpray = sprayTaskRepo.findFirstByAreaIdAndStatusOrderByCompletedAtDesc(area.getId(), "COMPLETED");

        // 查该区域最近的异常记录
        List<DetectionResult> anomalies = detectionRepo.findByAreaIdOrderByIdDesc(area.getId());
        if (anomalies != null) {
            anomalies = anomalies.stream()
                    .filter(result -> !"IGNORED".equalsIgnoreCase(result.getStatus()))
                    .toList();
        }
        boolean hasRecentAnomaly = anomalies != null && !anomalies.isEmpty()
                && isAnomalyCategory(anomalies.get(0).getCategory());

        if (lastSpray.isPresent()) {
            LocalDateTime sprayTime = lastSpray.get().getCompletedAt();
            // 点喷之后还有异常 → 喷药无效
            boolean anomalyAfterSpray = anomalies != null && anomalies.stream()
                    .anyMatch(d -> d.getCreateTime() != null
                            && sprayTime != null
                            && d.getCreateTime().isAfter(sprayTime)
                            && isAnomalyCategory(d.getCategory()));
            if (anomalyAfterSpray) {
                score += configService.getInt("risk.history.spray_fail", 5);
            } else if (!hasRecentAnomaly) {
                // 喷药后无异常 → 有效减分
                score += configService.getInt("risk.history.spray_ok", -10);
            }
        }

        // 连续异常
        int consecutive = 0;
        if (anomalies != null) {
            for (DetectionResult d : anomalies) {
                if (isAnomalyCategory(d.getCategory())) {
                    consecutive++;
                    if (consecutive >= 2) break;
                }
            }
        }
        if (consecutive >= 2) {
            score += configService.getInt("risk.history.consecutive", 5);
        }

        return score;
    }

    private boolean isAnomalyCategory(String category) {
        return category != null && CATEGORY_KEY.containsKey(category);
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
