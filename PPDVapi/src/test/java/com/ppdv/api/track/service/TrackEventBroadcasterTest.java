package com.ppdv.api.track.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.telemetry.TelemetryWebSocketHandler;
import com.ppdv.api.track.dto.TrackStoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackEventBroadcasterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock ObjectProvider<TelemetryWebSocketHandler> wsHandlerProvider;
    @Mock TelemetryWebSocketHandler ws;

    private TrackEventBroadcaster broadcaster() {
        return new TrackEventBroadcaster(objectMapper, wsHandlerProvider);
    }

    @Test
    void broadcastSerializesTrackSummaryEvent() {
        when(wsHandlerProvider.getIfAvailable()).thenReturn(ws);

        broadcaster().broadcast("D1", 1000L,
                new TrackStoreResult("S1", 12L, "ENDED", 3, List.of("leaf_late_blight"), 0.8));

        ArgumentCaptor<String> event = ArgumentCaptor.forClass(String.class);
        verify(ws).broadcast(event.capture());
        String json = event.getValue();
        assertTrue(json.contains("\"kind\":\"tele/track\""));
        assertTrue(json.contains("\"deviceId\":\"D1\""));
        assertTrue(json.contains("\"episodeCount\":3"));
        assertTrue(json.contains("\"status\":\"ENDED\""));
        assertTrue(json.contains("\"areaId\":12"));
        assertTrue(json.contains("\"maxConfidence\":0.8"));
        // 状态只下发 status，不再冗余下发 ended。
        assertFalse(json.contains("\"ended\""));
    }

    @Test
    void missingWebSocketHandlerIsIgnored() {
        // 无 WS 连接（handler 不存在）时只跳过广播，不影响落库链路。
        assertDoesNotThrow(() -> broadcaster().broadcast("D1", 1000L,
                new TrackStoreResult("S1", 12L, "ACTIVE", 1, List.of(), 0)));
        verifyNoInteractions(ws);
    }

    @Test
    void broadcastFailureIsSwallowed() {
        when(wsHandlerProvider.getIfAvailable()).thenReturn(ws);
        doThrow(new RuntimeException("ws down")).when(ws).broadcast(anyString());

        // 发送失败只记 debug，不向调用方抛异常。
        assertDoesNotThrow(() -> broadcaster().broadcast("D1", 1000L,
                new TrackStoreResult("S1", 12L, "ACTIVE", 1, List.of(), 0)));
    }
}
