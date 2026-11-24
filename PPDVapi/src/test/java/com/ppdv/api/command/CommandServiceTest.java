package com.ppdv.api.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.greenhouse.service.RouteRecordingService;
import com.ppdv.api.mqtt.MqttPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandServiceTest {

    @Mock ObjectProvider<MqttPublisher> publisherProvider;
    @Mock MqttPublisher publisher;
    @Mock DeviceService deviceService;
    @Mock RouteRecordingService routeRecordingService;

    private CommandService service;

    @BeforeEach
    void setUp() {
        service = new CommandService(publisherProvider, deviceService, new ObjectMapper(), routeRecordingService);
    }

    @Test
    void sendTaskRejectedWhileRecordingRoute() {
        when(routeRecordingService.hasActiveSessionForDevice("car-1")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.sendTask("car-1", Map.of("k", "v")));
        verify(publisher, never()).publish(anyString(), anyString());
    }

    @Test
    void sendSprayRejectedWhileRecordingRoute() {
        when(routeRecordingService.hasActiveSessionForDevice("car-1")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.sendSpray("car-1", "start", "A1", 5));
        verify(publisher, never()).publish(anyString(), anyString());
    }

    @Test
    void sendCommandIsNotBlockedWhileRecordingRoute() {
        // 急停/暂停等控制指令必须始终可达，不受录制保护影响。
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);

        assertNotNull(service.sendCommand("car-1", "stop", null));
        verify(publisher).publish(anyString(), anyString());
    }
}
