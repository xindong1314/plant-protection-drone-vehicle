package com.ppdv.api.track;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 车端目标跟踪数据上云配置（docs/protocol.md §5.6）。
 */
@ConfigurationProperties(prefix = "ppdv.track")
public class TrackProperties {

    /** 功能开关：关闭时不订阅 tele/track、不落库、不推送。 */
    private boolean enabled = false;

    /** 设备白名单：为空表示不限制，非空时只处理名单内设备（灰度联调用）。 */
    private List<String> deviceWhitelist = new ArrayList<>();

    /** 单条消息 tracks 上限；超限整条丢弃。 */
    private int maxTracks = 50;

    /** 单条轨迹抽稀点数上限；超出截断。 */
    private int maxTrajectoryPoints = 20;

    /** msgId 去重键在 Redis 的保留时长（小时）。 */
    private long msgDedupTtlHours = 24;

    /** 影子分统计窗口（天）。 */
    private int shadowWindowDays = 7;

    /** 影子分单次加载的 track 行数上限（1–10000），超出记 SHADOW_TRUNCATED。 */
    private int shadowMaxRows = 2000;

    /** 是否在 RiskEvaluationService.evaluate 结果里追加影子分（默认 false，不影响总分）。 */
    private boolean shadowInEvaluate = false;

    /** 同区域影子分去抖窗口（毫秒），尾沿必触发。 */
    private long debounceMs = 3000;

    /** 同区域影子分最大等待（毫秒）：持续有消息时每满该时长强制刷新一次；≤0 表示不设上限。 */
    private long shadowMaxDelayMs = 30000;

    /** episodeId 缺失时是否直接拒收该 track；false=兜底 trackId@firstSeenTs（过渡期），车端升级后置 true。 */
    private boolean requireEpisode = false;

    /** 会话空闲自动结束分钟数：车端未发 sessionEnd 时，最近活动超过该时长的 ACTIVE 会话置 ENDED。 */
    private int sessionIdleMinutes = 15;

    /** 同类跨 episode 证据聚合方式：NOISY_OR（默认，有界）/ SUM（按完整权重累加，总分封顶 80）。 */
    private String episodeAggregation = "NOISY_OR";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getDeviceWhitelist() {
        return deviceWhitelist;
    }

    /** 空配置归一为空列表，消费方无需判 null。 */
    public void setDeviceWhitelist(List<String> deviceWhitelist) {
        this.deviceWhitelist = deviceWhitelist == null ? new ArrayList<>() : deviceWhitelist;
    }

    public int getMaxTracks() {
        return maxTracks;
    }

    /** 配置非法（≤0）时按 1 处理：与 shadow-* 一致，避免整条消息因配置错误被静默全丢。 */
    public void setMaxTracks(int maxTracks) {
        this.maxTracks = Math.max(1, maxTracks);
    }

    public int getMaxTrajectoryPoints() {
        return maxTrajectoryPoints;
    }

    public void setMaxTrajectoryPoints(int maxTrajectoryPoints) {
        this.maxTrajectoryPoints = Math.max(1, maxTrajectoryPoints);
    }

    public long getMsgDedupTtlHours() {
        return msgDedupTtlHours;
    }

    /** ≤0 表示关闭 msgId 去重（避免把非法 TTL 当成 Redis 故障，见 dedupe）。 */
    public void setMsgDedupTtlHours(long msgDedupTtlHours) {
        this.msgDedupTtlHours = Math.max(0, msgDedupTtlHours);
    }

    public int getShadowWindowDays() {
        return shadowWindowDays;
    }

    public void setShadowWindowDays(int shadowWindowDays) {
        this.shadowWindowDays = Math.max(0, shadowWindowDays);
    }

    public int getShadowMaxRows() {
        return shadowMaxRows;
    }

    /** 配置钳制到 1–10000：0 会让 PageRequest 抛异常、过大则单次查询行数失控。 */
    public void setShadowMaxRows(int shadowMaxRows) {
        this.shadowMaxRows = Math.min(10000, Math.max(1, shadowMaxRows));
    }

    public boolean isShadowInEvaluate() {
        return shadowInEvaluate;
    }

    public void setShadowInEvaluate(boolean shadowInEvaluate) {
        this.shadowInEvaluate = shadowInEvaluate;
    }

    public long getDebounceMs() {
        return debounceMs;
    }

    public void setDebounceMs(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    public long getShadowMaxDelayMs() {
        return shadowMaxDelayMs;
    }

    public void setShadowMaxDelayMs(long shadowMaxDelayMs) {
        this.shadowMaxDelayMs = shadowMaxDelayMs;
    }

    public boolean isRequireEpisode() {
        return requireEpisode;
    }

    public void setRequireEpisode(boolean requireEpisode) {
        this.requireEpisode = requireEpisode;
    }

    public int getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public void setSessionIdleMinutes(int sessionIdleMinutes) {
        this.sessionIdleMinutes = sessionIdleMinutes;
    }

    public String getEpisodeAggregation() {
        return episodeAggregation;
    }

    public void setEpisodeAggregation(String episodeAggregation) {
        this.episodeAggregation = episodeAggregation;
    }
}
