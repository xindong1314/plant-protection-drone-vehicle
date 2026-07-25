package com.ppdv.api.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.protocol.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 上行遥测入库：解析主题与信封，更新设备快照（MySQL）并写入时序库（TDengine，若启用）。
 * 对应 docs/protocol.md 第 5 节。
 */
@Service
public class TelemetryIngestService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestService.class);

    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TelemetryTsService> tsServiceProvider;

    public TelemetryIngestService(DeviceService deviceService, ObjectMapper objectMapper,
                                  ObjectProvider<TelemetryTsService> tsServiceProvider) {
        this.deviceService = deviceService;
        this.objectMapper = objectMapper;
        this.tsServiceProvider = tsServiceProvider;
    }

    public void onMessage(String topic, String payload) {
        String deviceId = Topics.deviceIdOf(topic);
        String kind = Topics.kindOf(topic);
        if (deviceId == null) {
            log.warn("无法解析 deviceId，忽略主题: {}", topic);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.path("data");
            long ts = root.path("ts").asLong(System.currentTimeMillis());
            switch (kind) {
                case "status" -> handleStatus(deviceId, data);
                case "tele/state" -> handleState(deviceId, ts, data);
                case "tele/work" -> handleWork(deviceId, ts, data);
                case "task/progress", "cmd/ack" -> log.debug("[{}] {} -> {}", deviceId, kind, payload);
                case "event/alarm" -> handleAlarm(deviceId, data);
                default -> log.debug("未处理的主题种类 {}: {}", kind, topic);
            }
        } catch (Exception e) {
            log.warn("解析上行消息失败, topic={}, err={}", topic, e.getMessage());
        }
    }

    private void handleStatus(String deviceId, JsonNode data) {
        boolean online = data.path("online").asBoolean(false);
        String fw = data.hasNonNull("fw") ? data.get("fw").asText() : null;
        deviceService.markOnline(deviceId, online, fw);
    }

    private void handleState(String deviceId, long ts, JsonNode data) {
        Double lat = data.hasNonNull("lat") ? data.get("lat").asDouble() : null;
        Double lon = data.hasNonNull("lon") ? data.get("lon").asDouble() : null;
        Integer battery = data.hasNonNull("battery") ? data.get("battery").asInt() : null;
        String mode = data.hasNonNull("mode") ? data.get("mode").asText() : null;
        deviceService.updateState(deviceId, lat, lon, battery, mode);
        TelemetryTsService ts_ = tsServiceProvider.getIfAvailable();
        if (ts_ != null) {
            ts_.writeState(deviceId, ts, data);
        }
    }

    private void handleWork(String deviceId, long ts, JsonNode data) {
        TelemetryTsService ts_ = tsServiceProvider.getIfAvailable();
        if (ts_ != null) {
            ts_.writeWork(deviceId, ts, data);
        } else {
            log.debug("[{}] tele/work（未启用时序库）", deviceId);
        }
    }

    private void handleAlarm(String deviceId, JsonNode data) {
        log.warn("[告警] device={}, code={}, level={}, msg={}",
                deviceId, data.path("code").asText(), data.path("level").asText(),
                data.path("msg").asText());
    }
}
