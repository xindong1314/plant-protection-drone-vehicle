package com.ppdv.api.track.service;

import com.ppdv.api.track.TrackProperties;
import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 跟踪证据影子分（每条 episode 一份证据，见 docs/protocol.md §5.6）：每条出现（episode，一行）算一份证据，
 * 同类跨 episode 聚合（NOISY_OR 默认 / SUM 可配），帧数与持续时长不参与任何计算。
 * P1 只计算并输出日志/指标，不写入总分、不调用 evaluate。
 * 查询限定近期窗口 + 行数上限；同区域做真去抖（尾沿必触发），并有最大等待兜底。
 */
@Component
@ConditionalOnProperty(prefix = "ppdv.track", name = "enabled", havingValue = "true")
public class TrackRiskAdapter {

    private static final Logger log = LoggerFactory.getLogger(TrackRiskAdapter.class);
    private static final double SCORE_CAP = 80;

    /** trackScore：跟踪证据等价检测分（影子）；contributions：按 episode 贡献（key=class@sessionId@episodeKey）。 */
    public record ShadowScore(double trackScore, Map<String, Double> contributions,
                              boolean hasEvidence, boolean truncated) {
    }

    private final DetectionTrackRepository trackRepo;
    private final TrackProperties props;
    private final ScheduledExecutorService scheduler;
    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    /** 同区域去抖首个消息的到达时间：用于最大等待判定（持续消息时保证定期刷新）。 */
    private final Map<Long, Long> pendingSince = new ConcurrentHashMap<>();

    public TrackRiskAdapter(DetectionTrackRepository trackRepo, TrackProperties props,
                            ScheduledExecutorService trackShadowScheduler) {
        this.trackRepo = trackRepo;
        this.props = props;
        this.scheduler = trackShadowScheduler;
    }

    public boolean shadowInEvaluate() {
        return props.isShadowInEvaluate();
    }

    /**
     * 同区域延迟合并：新消息替换未执行的旧任务；会话结束（immediate）立即执行，保证尾沿生效。
     * 持续有消息时最高等待 shadow-max-delay-ms：到点强制刷新，避免去抖把刷新无限推迟。
     */
    public void schedule(long areaId, boolean immediate) {
        ScheduledFuture<?> previous = pending.remove(areaId);
        if (previous != null) {
            previous.cancel(false);
        }
        long delay;
        if (immediate) {
            pendingSince.remove(areaId);
            delay = 0;
        } else {
            long now = System.currentTimeMillis();
            long since = pendingSince.computeIfAbsent(areaId, key -> now);
            long debounce = Math.max(0, props.getDebounceMs());
            long maxDelay = props.getShadowMaxDelayMs();
            delay = maxDelay > 0 ? Math.min(debounce, Math.max(0, since + maxDelay - now)) : debounce;
        }
        pending.put(areaId, scheduler.schedule(() -> {
            pending.remove(areaId);
            pendingSince.remove(areaId);
            refreshShadow(areaId);
        }, delay, TimeUnit.MILLISECONDS));
    }

    void refreshShadow(Long areaId) {
        try {
            ShadowScore score = computeShadow(areaId);
            if (score.truncated()) {
                log.warn("[track-shadow] SHADOW_TRUNCATED area={} 仅统计窗口内最近 {} 行", areaId, props.getShadowMaxRows());
            }
            // contributions 最多 shadow-max-rows 项，完整 Map 只在 DEBUG 打印，避免 INFO 洪泛。
            log.info("[track-shadow] area={} trackScore={} evidence={} aggregation={} contributions={}",
                    areaId, round(score.trackScore()), score.hasEvidence(), props.getEpisodeAggregation(),
                    score.contributions().size());
            log.debug("[track-shadow] area={} contributions={}", areaId, score.contributions());
        } catch (Exception e) {
            log.warn("[track-shadow] area={} 影子分计算失败: {}", areaId, e.getMessage());
        }
    }

    public ShadowScore computeShadow(Long areaId) {
        long now = System.currentTimeMillis();
        long since = now - props.getShadowWindowDays() * 86_400_000L;
        // 排序取窗口内最近行；上界 until=服务器 now，排除车端超前时间戳挤占 maxRows。
        Pageable limit = PageRequest.of(0, props.getShadowMaxRows(), Sort.by(Sort.Direction.DESC, "lastSeenTs"));
        Page<DetectionTrack> page = trackRepo.findByAreaIdAndLastSeenTsBetween(areaId, since, now, limit);
        boolean truncated = page.getTotalElements() > props.getShadowMaxRows();

        // ① 每条 episode 一行（唯一键保证行内已是 max，不重复计）；同类跨 episode 聚合。
        Map<String, List<Double>> confidencesByClass = new HashMap<>();
        Map<String, Double> contributions = new LinkedHashMap<>();
        for (DetectionTrack track : page.getContent()) {
            double base = TrackClassCatalog.baseScore(track.getCategory());
            if (base == 0) {
                continue;
            }
            double confidence = track.getConfidence() == null ? 0 : track.getConfidence();
            confidencesByClass.computeIfAbsent(track.getCategory(), key -> new ArrayList<>()).add(confidence);
            // ④ contributions 按 episode：键带会话 id，车端跨会话复用 episodeId 时不会互相覆盖。
            // episode_key NOT NULL（V14 uk_episode），无需回退。
            contributions.put(track.getCategory() + "@" + track.getTrackSessionId() + "@" + track.getEpisodeKey(),
                    round(base * confidence));
        }

        // ② 同类跨 episode 聚合 → ③ trackScore = min(80, Σ_class base × conf_class)。
        double sum = 0;
        for (Map.Entry<String, List<Double>> entry : confidencesByClass.entrySet()) {
            double confidence = aggregate(entry.getValue());
            sum += TrackClassCatalog.baseScore(entry.getKey()) * confidence;
        }
        return new ShadowScore(round(Math.min(SCORE_CAP, sum)), contributions, !contributions.isEmpty(), truncated);
    }

    /** 同类跨 episode 聚合：NOISY_OR（有界 1−Π(1−conf)）或 SUM（每次出现按完整权重累加，总分封顶 80 兜底）。 */
    private double aggregate(List<Double> confidences) {
        if ("SUM".equalsIgnoreCase(props.getEpisodeAggregation())) {
            return confidences.stream().mapToDouble(Double::doubleValue).sum();
        }
        double remaining = 1;
        for (double confidence : confidences) {
            remaining *= 1 - confidence;
        }
        return 1 - remaining;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
