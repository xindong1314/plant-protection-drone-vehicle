package com.ppdv.api.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.greenhouse.service.RouteRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestServiceTest {

    @Mock DeviceService deviceService;
    @Mock ObjectProvider<TelemetryTsService> tsProvider;
    @Mock ObjectProvider<TelemetryWebSocketHandler> wsProvider;
    @Mock RouteRecordingService routeRecordingService;

    private TelemetryIngestService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryIngestService(deviceService, new ObjectMapper(), tsProvider, wsProvider,
                routeRecordingService);
    }

    private void state(String dataJson) {
        service.onMessage("ppdv/v1/car-1/tele/state", "{\"ts\":1760000000000,\"data\":" + dataJson + "}");
    }

    @Test
    void numericLatLonAreAccepted() {
        state("{\"lat\":31.2,\"lon\":121.4}");

        verify(routeRecordingService).append("car-1", 31.2, 121.4, 1760000000000L);
    }

    @Test
    void numericStringLatLonAreStillAccepted() {
        // 兼容旧 asDouble() 行为：数值字符串解析后使用，不能静默丢位置。
        state("{\"lat\":\"31.2\",\"lon\":\"121.4\"}");

        verify(deviceService).updateState(eq("car-1"), eq(31.2), eq(121.4), any(), any());
        verify(routeRecordingService).append("car-1", 31.2, 121.4, 1760000000000L);
    }

    @Test
    void nonNumericLatLonAreTreatedAsMissingNotZero() {
        // v1.1 评审 M4：非数值经纬度按缺失处理，不得回落 (0,0)。
        state("{\"lat\":\"abc\",\"lon\":null}");

        verify(deviceService).updateState(eq("car-1"), isNull(), isNull(), any(), any());
        verify(routeRecordingService).append(eq("car-1"), isNull(), isNull(), anyLong());
    }
}
