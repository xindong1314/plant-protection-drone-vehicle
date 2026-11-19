package com.ppdv.api.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ppdv.api.telemetry.TelemetryWebSocketHandler;
import com.ppdv.api.track.dto.TrackStoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 跟踪摘要 WebSocket 广播（复用遥测通道）：入站落库后与空闲清扫结束后共同使用。
 * 事务提交后调用；WS 缺席或发送失败只记 debug，不影响落库。
 */
@Component
@ConditionalOnProperty(prefix = "ppdv.track", name = "enabled", havingValue = "true")
public class TrackEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrackEventBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final ObjectProvider<TelemetryWebSocketHandler> wsHandlerProvider;

    public TrackEventBroadcaster(ObjectMapper objectMapper,
                                 ObjectProvider<TelemetryWebSocketHandler> wsHandlerProvider) {
        this.objectMapper = objectMapper;
        this.wsHandlerProvider = wsHandlerProvider;
    }

    public void broadcast(String deviceId, long ts, TrackStoreResult result) {
        TelemetryWebSocketHandler ws = wsHandlerProvider.getIfAvailable();
        if (ws == null) {
            return;
        }
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("deviceId", deviceId);
            event.put("kind", "tele/track");
            event.put("ts", ts);
            ObjectNode data = event.putObject("data");
            data.put("trackSessionId", result.sessionKey());
            // 会话累计出现次数，与查询列表口径一致（状态用 status 判断，不再冗余下发 ended）。
            data.put("episodeCount", result.episodeCount());
            ArrayNode categories = data.putArray("categories");
            result.categories().forEach(categories::add);
            data.put("maxConfidence", result.maxConfidence());
            if (result.areaId() != null) {
                data.put("areaId", result.areaId());
            }
            data.put("status", result.status());
            ws.broadcast(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.debug("广播跟踪事件失败: {}", e.getMessage());
        }
    }
}
