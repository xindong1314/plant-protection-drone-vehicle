package com.ppdv.api.track.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.protocol.Topics;
import com.ppdv.api.track.TrackProperties;
import com.ppdv.api.track.dto.TrackObservation;
import com.ppdv.api.track.dto.TrackStoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * tele/track 入站处理：msgId 去重 → 校验 → 区域解析 → 事务落库 → 提交后 WS 广播 + 影子分调度。
 * 对应 docs/protocol.md §5.6；MQTT 回调线程只做解析与落库，评估在独立线程池执行。
 * 去重键带 deviceId（track:msg:{deviceId}:{msgId}）；处理失败回删去重键，QoS1 补传可重放（落库幂等）。
 */
@Service
@ConditionalOnProperty(prefix = "ppdv.track", name = "enabled", havingValue = "true")
public class TrackIngestService {

    private static final Logger log = LoggerFactory.getLogger(TrackIngestService.class);

    private static final int MAX_MESSAGE_BYTES = 256 * 1024;
    private static final Set<String> TRACK_STATES = Set.of("ACTIVE", "LOST", "ENDED", "REAPPEAR");

    private final TrackProperties props;
    private final ObjectMapper objectMapper;
    private final RedisCache redisCache;
    private final DeviceService deviceService;
    private final AreaService areaService;
    private final TrackStoreService storeService;
    private final TrackRiskAdapter riskAdapter;
    private final TrackEventBroadcaster broadcaster;

    public TrackIngestService(TrackProperties props, ObjectMapper objectMapper, RedisCache redisCache,
                              DeviceService deviceService, AreaService areaService, TrackStoreService storeService,
                              TrackRiskAdapter riskAdapter, TrackEventBroadcaster broadcaster) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.redisCache = redisCache;
        this.deviceService = deviceService;
        this.areaService = areaService;
        this.storeService = storeService;
        this.riskAdapter = riskAdapter;
        this.broadcaster = broadcaster;
    }

    public void onMessage(String topic, String payload) {
        try {
            handle(topic, payload);
        } catch (Exception e) {
            // 单条消息失败不阻塞 broker 消费与后续消息。
            log.warn("处理跟踪消息失败, topic={}, err={}", topic, e.getMessage());
        }
    }

    private void handle(String topic, String payload) {
        String deviceId = Topics.deviceIdOf(topic);
        if (deviceId == null) {
            log.warn("无法解析 deviceId，忽略跟踪主题: {}", topic);
            return;
        }
        if (!allowedDevice(deviceId)) {
            log.debug("[{}] 不在跟踪灰度白名单，忽略", deviceId);
            return;
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            log.warn("[{}] TRACK_MESSAGE_TOO_LARGE 超过 256KB，丢弃", deviceId);
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.warn("[{}] 跟踪消息 JSON 解析失败，丢弃: {}", deviceId, e.getOriginalMessage());
            return;
        }
        if (!"track".equals(root.path("type").asText())) {
            log.warn("[{}] 跟踪消息 type 非 track，丢弃", deviceId);
            return;
        }
        String envelopeDevice = root.path("deviceId").asText("");
        if (!envelopeDevice.isEmpty() && !envelopeDevice.equals(deviceId)) {
            log.warn("TRACK_DEVICE_MISMATCH 主题 deviceId={} 与信封 deviceId={} 不一致，丢弃", deviceId, envelopeDevice);
            return;
        }
        String msgId = root.path("msgId").asText("").trim();
        if (msgId.isEmpty()) {
            log.warn("[{}] 跟踪消息缺少 msgId，丢弃", deviceId);
            return;
        }
        String dedupeKey = "track:msg:" + deviceId + ":" + msgId;
        if (!dedupe(dedupeKey)) {
            log.debug("[{}] 重复 msgId={} 忽略", deviceId, msgId);
            return;
        }
        try {
            if (!process(deviceId, root)) {
                // 因区域暂不可解析而丢弃：该条件可能随后恢复，回删去重键让车端用同一 msgId 重发时仍能入库。
                deleteDedupeQuietly(dedupeKey);
            }
        } catch (Exception e) {
            // 去重键已写入但落库/处理失败：回删键让 QoS1 补传可重放（落库幂等），避免消息被永久吞掉。
            deleteDedupeQuietly(dedupeKey);
            throw e;
        }
    }

    /**
     * @return false 表示因区域暂不可解析而丢弃（可重试，调用方需回删去重键）；true 表示已处理或确定性丢弃（重发也不会成功）。
     */
    private boolean process(String deviceId, JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            log.warn("[{}] 跟踪消息缺少 data 对象，丢弃", deviceId);
            return true;
        }
        String sessionKey = data.path("trackSessionId").asText("").trim();
        if (sessionKey.isEmpty() || sessionKey.length() > 64) {
            log.warn("[{}] trackSessionId 缺失或超过 64 字符，丢弃", deviceId);
            return true;
        }
        JsonNode tracksNode = data.path("tracks");
        if (!tracksNode.isArray()) {
            log.warn("[{}] tracks 缺失或非数组，丢弃", deviceId);
            return true;
        }
        if (tracksNode.size() > props.getMaxTracks()) {
            log.warn("[{}] TRACK_TOO_MANY_TRACKS {} 条超过上限 {}，丢弃", deviceId, tracksNode.size(), props.getMaxTracks());
            return true;
        }
        List<TrackObservation> observations = parseTracks(deviceId, tracksNode);
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        // 巡检结束只认 data.sessionEnd（trackState=ENDED 只表示本次出现结束）。
        // sessionEnd 必须在"无有效 track"早退之前读取：结束信号不能因同条消息的 track 校验失败而丢失。
        boolean sessionEnd = data.path("sessionEnd").asBoolean(false);
        if (!tracksNode.isEmpty() && observations.isEmpty() && !sessionEnd) {
            log.warn("[{}] 跟踪消息无有效 track（类别拒识/字段非法/缺 episodeId），丢弃", deviceId);
            return true;
        }

        Long areaId = resolveArea(deviceId, data);
        if (areaId == null) {
            // 区域不可解析但携带 sessionEnd=true 时仍要结束既有会话，避免结束信号只能等空闲清扫兜底。
            if (!sessionEnd) {
                return false;
            }
            TrackStoreResult ended = storeService.endSession(deviceId, ts, sessionKey);
            if (ended == null) {
                log.warn("[{}] sessionEnd=true 但区域不可解析且无既有会话，丢弃", deviceId);
                return true;
            }
            log.info("[{}] 跟踪消息 session={} sessionEnd=true（区域不可解析），结束既有会话", deviceId, sessionKey);
            broadcaster.broadcast(deviceId, ts, ended);
            riskAdapter.schedule(ended.areaId(), true);
            return true;
        }

        TrackStoreResult result = storeService.apply(deviceId, ts, areaId, sessionKey, observations, sessionEnd);
        log.info("[{}] 跟踪消息 session={} episodes={}（会话累计） status={} sessionEnd={}", deviceId, sessionKey,
                result.episodeCount(), result.status(), sessionEnd);
        broadcaster.broadcast(deviceId, ts, result);
        riskAdapter.schedule(areaId, result.ended());
        return true;
    }

    private boolean allowedDevice(String deviceId) {
        List<String> whitelist = props.getDeviceWhitelist();
        return whitelist.isEmpty() || whitelist.contains(deviceId);
    }

    /** Redis SETNX 去重；TTL 配置 ≤0 表示关闭去重，Redis 不可用时放行并告警，避免整条链路上数据丢失。 */
    private boolean dedupe(String dedupeKey) {
        if (props.getMsgDedupTtlHours() <= 0) {
            log.warn("msg-dedup-ttl-hours<=0，track 消息 msgId 去重已关闭");
            return true;
        }
        try {
            return redisCache.setIfAbsent(dedupeKey, "1", props.getMsgDedupTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("track 消息去重失败（Redis 不可用），继续处理: {}", e.getMessage());
            return true;
        }
    }

    /** 处理失败回删去重键（尽力而为，Redis 再挂也只是退回"可能漏补传"的旧行为）。 */
    private void deleteDedupeQuietly(String dedupeKey) {
        try {
            redisCache.delete(dedupeKey);
        } catch (Exception e) {
            log.warn("track 消息去重键回删失败 key={}: {}", dedupeKey, e.getMessage());
        }
    }

    /** areaId → device.currentZone → 区域表；都不可用返回 null，不写脏数据。 */
    private Long resolveArea(String deviceId, JsonNode data) {
        // 与遥测一致：未建档设备自动登记。
        Device device = deviceService.ensure(deviceId);
        JsonNode areaNode = data.get("areaId");
        if (areaNode != null && areaNode.isNumber()) {
            long areaId = areaNode.asLong();
            if (!areaService.existsById(areaId)) {
                log.warn("[{}] TRACK_AREA_UNRESOLVED areaId={} 不存在，丢弃", deviceId, areaId);
                return null;
            }
            return areaId;
        }
        String zoneCode = device.getCurrentZone();
        if (zoneCode != null && !zoneCode.isBlank()) {
            try {
                return areaService.getByCode(zoneCode).getId();
            } catch (IllegalArgumentException e) {
                log.warn("[{}] TRACK_AREA_UNRESOLVED currentZone={} 不存在，丢弃", deviceId, zoneCode);
                return null;
            }
        }
        log.warn("[{}] TRACK_AREA_UNRESOLVED 既无 areaId 也无 currentZone，丢弃", deviceId);
        return null;
    }

    private List<TrackObservation> parseTracks(String deviceId, JsonNode tracks) {
        List<TrackObservation> result = new ArrayList<>();
        for (JsonNode node : tracks) {
            String trackId = node.path("trackId").asText("").trim();
            if (trackId.isEmpty() || trackId.length() > 64) {
                log.warn("[{}] trackId 缺失或超过 64 字符，跳过该 track", deviceId);
                continue;
            }
            String category = node.path("class").asText("").trim();
            if (category.isEmpty() || category.length() > 32) {
                log.warn("[{}] track={} class 缺失或超过 32 字符，跳过", deviceId, trackId);
                continue;
            }
            if (TrackClassCatalog.isRetired(category)) {
                log.warn("[{}] RETIRED_CLASS track={} class={} 旧类别拒识", deviceId, trackId, category);
                continue;
            }
            if (!TrackClassCatalog.contains(category)) {
                log.warn("[{}] UNKNOWN_CLASS track={} class={} 落 {}", deviceId, trackId, category, TrackClassCatalog.UNKNOWN);
                category = TrackClassCatalog.UNKNOWN;
            }
            JsonNode confidenceNode = node.get("confidence");
            if (confidenceNode == null || !confidenceNode.isNumber()
                    || confidenceNode.asDouble() < 0 || confidenceNode.asDouble() > 1) {
                log.warn("[{}] track={} confidence 非法，跳过", deviceId, trackId);
                continue;
            }
            JsonNode lastSeenNode = node.get("lastSeenTs");
            if (lastSeenNode == null || !lastSeenNode.isNumber() || lastSeenNode.asLong() <= 0) {
                log.warn("[{}] track={} lastSeenTs 非法，跳过", deviceId, trackId);
                continue;
            }
            long lastSeenTs = lastSeenNode.asLong();
            long firstSeenTs = node.path("firstSeenTs").asLong(0);
            boolean firstSeenFallback = false;
            if (firstSeenTs <= 0 || firstSeenTs > lastSeenTs) {
                firstSeenTs = lastSeenTs;
                firstSeenFallback = true;
            }
            // 身份键（docs/protocol.md §5.6，v0.4）：episodeId ≤64、一次出现内稳定；缺失时兜底或按开关拒收。
            String episodeId = node.path("episodeId").asText("").trim();
            if (episodeId.isEmpty()) {
                // firstSeenTs 是兜底值时兜底键随消息漂移（同一出现每条消息各落一行），必须拒收；
                // require-episode=true 时一律拒收（车端升级完成后的收紧路径）。
                if (props.isRequireEpisode()) {
                    log.warn("[{}] TRACK_EPISODE_REQUIRED track={} 缺少 episodeId（require-episode=true），跳过",
                            deviceId, trackId);
                    continue;
                }
                if (firstSeenFallback) {
                    log.warn("[{}] TRACK_EPISODE_FALLBACK_UNSTABLE track={} 缺少 episodeId 且 firstSeenTs 缺失/非法，"
                            + "兜底键不稳定，拒收该 track", deviceId, trackId);
                    continue;
                }
                episodeId = trackId + "@" + firstSeenTs;
                log.warn("[{}] TRACK_EPISODE_FALLBACK track={} 缺少 episodeId，兜底 {}", deviceId, trackId, episodeId);
            }
            if (episodeId.length() > 64) {
                log.warn("[{}] track={} episodeId 超过 64 字符，跳过", deviceId, trackId);
                continue;
            }
            Integer episodeNo = null;
            JsonNode episodeNoNode = node.get("episodeNo");
            if (episodeNoNode != null && episodeNoNode.isNumber() && episodeNoNode.asInt() >= 0) {
                episodeNo = episodeNoNode.asInt();
            }
            String trackState = node.path("trackState").asText("").trim().toUpperCase(Locale.ROOT);
            if (!TRACK_STATES.contains(trackState)) {
                trackState = "ACTIVE";
            }
            Integer severity = null;
            JsonNode severityNode = node.get("severity");
            if (severityNode != null && severityNode.isNumber()) {
                int value = severityNode.asInt();
                if (value >= 0 && value <= 4) {
                    severity = value;
                }
            }
            result.add(new TrackObservation(
                    trackId, episodeId, episodeNo, category, confidenceNode.asDouble(), firstSeenTs, lastSeenTs,
                    nonNegativeOrNull(node.path("boxX")),
                    nonNegativeOrNull(node.path("boxY")),
                    nonNegativeOrNull(node.path("boxW")),
                    nonNegativeOrNull(node.path("boxH")),
                    positiveOrNull(node.path("imageWidth")),
                    positiveOrNull(node.path("imageHeight")),
                    parseTrajectory(deviceId, trackId, node.get("trajectory")),
                    trackState, severity));
        }
        return result;
    }

    /** 轨迹只保留合法 [x,y,ts] 点，超出上限截断。 */
    private String parseTrajectory(String deviceId, String trackId, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        int limit = props.getMaxTrajectoryPoints();
        ArrayNode kept = objectMapper.createArrayNode();
        for (JsonNode point : node) {
            if (kept.size() >= limit) {
                log.warn("[{}] track={} trajectory 超过 {} 点，截断", deviceId, trackId, limit);
                break;
            }
            if (!point.isArray() || point.size() < 3
                    || !point.get(0).isNumber() || !point.get(1).isNumber() || !point.get(2).isNumber()) {
                continue;
            }
            kept.add(point);
        }
        return kept.isEmpty() ? null : kept.toString();
    }

    /** 数字字段存在且 ≥0 时返回其值，否则 null（缺省表示"本条未携带"，落库时不覆盖已有值）。 */
    private static Integer nonNegativeOrNull(JsonNode node) {
        return node.isNumber() && node.asInt() >= 0 ? node.asInt() : null;
    }

    private static Integer positiveOrNull(JsonNode node) {
        return node.isNumber() && node.asInt() > 0 ? node.asInt() : null;
    }
}
