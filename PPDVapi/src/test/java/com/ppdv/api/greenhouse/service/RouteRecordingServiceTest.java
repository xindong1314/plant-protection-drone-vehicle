package com.ppdv.api.greenhouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.device.DeviceStatus;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteRecordingServiceTest {

    /** 遥测 ts 为 epoch 毫秒（协议 §8），测试用同一量级的时间基准。 */
    private static final long T0 = 1_760_000_000_000L;

    @Mock AreaService areaService;
    @Mock DeviceService deviceService;

    private RouteRecordingService service;
    private AtomicLong now;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(T0);
        service = new RouteRecordingService(areaService, deviceService, new ObjectMapper(), now::get);
    }

    @Test
    void startRejectsUnknownArea() {
        when(areaService.get(9L)).thenThrow(new IllegalArgumentException("区域不存在: 9"));

        assertThrows(IllegalArgumentException.class, () -> service.start(9L, "car-1"));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void startRejectsAreaWhoseBoundaryIsNotCgcs2000() {
        GreenhouseArea area = area(9L, "A1");
        area.setCoordinateSystem("GCJ-02");
        when(areaService.get(9L)).thenReturn(area);

        assertThrows(IllegalArgumentException.class, () -> service.start(9L, "car-1"));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void startRejectsOfflineDevice() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.OFFLINE));

        assertThrows(IllegalStateException.class, () -> service.start(9L, "car-1"));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void startRejectsDeviceAlreadyRecordingForAnotherArea() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(areaService.get(10L)).thenReturn(area(10L, "B1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));

        service.start(9L, "car-1");

        assertThrows(IllegalStateException.class, () -> service.start(10L, "car-1"));
        assertEquals(1, service.status().size());
    }

    @Test
    void startRejectsAreaAlreadyRecordingByAnotherDevice() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        when(deviceService.get("car-2")).thenReturn(device("car-2", DeviceStatus.ONLINE));

        service.start(9L, "car-1");

        assertThrows(IllegalStateException.class, () -> service.start(9L, "car-2"));
        assertEquals(1, service.status().size());
    }

    @Test
    void appendIgnoresNullInvalidOutOfRangeAndDuplicatePoints() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");

        service.append("car-1", null, 116.0, T0);
        service.append("car-1", 39.0, null, T0 + 1);
        service.append("car-1", Double.NaN, 116.0, T0 + 2);
        service.append("car-1", 39.0, Double.POSITIVE_INFINITY, T0 + 3);
        service.append("car-1", 91.0, 116.0, T0 + 4);
        service.append("car-1", 39.0, 181.0, T0 + 5);
        service.append("car-1", 39.0, 116.0, T0 + 6);
        service.append("car-1", 39.0, 116.0, T0 + 7);
        service.append("other-car", 39.0, 116.0, T0 + 8);
        service.append("car-1", 39.1, 116.1, T0 + 9);

        assertEquals(2, service.stop(9L));
        ArgumentCaptor<String> routeJson = ArgumentCaptor.forClass(String.class);
        verify(areaService).saveRecordedRoute(eq(9L), routeJson.capture());
        assertEquals("[[116.0,39.0],[116.1,39.1]]", routeJson.getValue());
    }

    @Test
    void appendRejectsStaleTimestampBackfill() {
        // v1.1 评审 M5：断网补传的旧遥测不允许插到路线头部。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        service.append("car-1", 39.2, 116.2, T0 + 2000);
        service.append("car-1", 39.1, 116.1, T0 + 1000);

        assertEquals(2, service.stop(9L));
        ArgumentCaptor<String> routeJson = ArgumentCaptor.forClass(String.class);
        verify(areaService).saveRecordedRoute(eq(9L), routeJson.capture());
        assertEquals("[[116.0,39.0],[116.2,39.2]]", routeJson.getValue());
    }

    @Test
    void appendRoundsCoordinatesToSevenDecimals() {
        // v1.1 评审 S3：坐标按 7 位小数取整后 20000 点不会超 512KB。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.123456789012, 116.123456789012, T0);
        service.append("car-1", 39.223456789012, 116.223456789012, T0 + 1);

        service.stop(9L);
        verify(areaService).saveRecordedRoute(9L, "[[116.1234568,39.1234568],[116.2234568,39.2234568]]");
    }

    @Test
    void stopRejectsWhenNoSessionStarted() {
        assertThrows(IllegalArgumentException.class, () -> service.stop(9L));
        verify(areaService, never()).saveRecordedRoute(anyLong(), anyString());
    }

    @Test
    void stopKeepsSessionWhenTooFewPointsAndAllowsRetry() {
        // v1.1 评审 S2：点数不足保留会话，用户可继续行驶补点后再结束。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);

        assertThrows(IllegalArgumentException.class, () -> service.stop(9L));
        verify(areaService, never()).saveRecordedRoute(anyLong(), anyString());
        assertEquals(1, service.status().size());

        service.append("car-1", 39.1, 116.1, T0 + 1000);
        assertEquals(2, service.stop(9L));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void stopSerializesPointsSavesRouteAndClearsSession() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        service.append("car-1", 39.1, 116.1, T0 + 1);

        int pointCount = service.stop(9L);

        assertEquals(2, pointCount);
        verify(areaService).saveRecordedRoute(9L, "[[116.0,39.0],[116.1,39.1]]");
        assertTrue(service.status().isEmpty());
    }

    @Test
    void appendStopsCollectingWhenRoutePointLimitReached() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        for (int i = 0; i < AreaService.MAX_ROUTE_POINTS + 10; i++) {
            service.append("car-1", 39.0 + i * 1e-6, 116.0, T0 + i);
        }

        assertEquals(AreaService.MAX_ROUTE_POINTS, service.stop(9L));
    }

    @Test
    void appendRespectsByteBudgetSoHighPrecisionFullRouteStillSaves() {
        // v1.1 评审 S3：满 20000 点 + 高精度坐标（最坏宽度）仍能通过 512KB 落库校验。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        for (int i = 0; i < AreaService.MAX_ROUTE_POINTS; i++) {
            service.append("car-1", -89.9999999 + i * 1e-7, -179.9999999 + i * 1e-7, T0 + i);
        }

        int pointCount = service.stop(9L);
        ArgumentCaptor<String> routeJson = ArgumentCaptor.forClass(String.class);
        verify(areaService).saveRecordedRoute(eq(9L), routeJson.capture());
        assertTrue(pointCount <= AreaService.MAX_ROUTE_POINTS);
        assertTrue(routeJson.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 512 * 1024);
    }

    @Test
    void stopRemovesSessionEvenWhenSaveFails() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        service.append("car-1", 39.1, 116.1, T0 + 1);
        doThrow(new IllegalArgumentException("区域坐标系不是 CGCS2000"))
                .when(areaService).saveRecordedRoute(anyLong(), anyString());

        assertThrows(IllegalArgumentException.class, () -> service.stop(9L));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void cancelRemovesSessionWithoutSavingAndIsIdempotent() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);

        service.cancel(9L);
        // v1.1 评审 S2：无会话时取消幂等成功，不再抛“区域没有进行中的路线录制”。
        service.cancel(9L);

        assertTrue(service.status().isEmpty());
        verify(areaService, never()).saveRecordedRoute(anyLong(), anyString());
    }

    @Test
    void idleSessionExpiresAndReleasesDevice() {
        // v1.1 评审 S4：空闲超时自动清理，设备不再被死会话永久占用。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);

        now.set(T0 + 9 * 60 * 1000L);
        assertEquals(1, service.status().size());

        now.set(T0 + 10 * 60 * 1000L);
        assertTrue(service.status().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.stop(9L));
        verify(areaService, never()).saveRecordedRoute(anyLong(), anyString());

        // 会话被清理后设备可重新开始录制。
        service.start(9L, "car-1");
        assertEquals(1, service.status().size());
    }

    @Test
    void sessionExpiresByMaxDurationEvenWhenActive() {
        // v1.1 评审 S4：持续有定位点的会话也受最大时长限制。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        for (int minute = 0; minute <= 120; minute += 5) {
            now.set(T0 + minute * 60 * 1000L);
            service.append("car-1", 39.0 + minute * 1e-5, 116.0, T0 + minute * 60 * 1000L);
        }

        now.set(T0 + 121 * 60 * 1000L);
        assertTrue(service.status().isEmpty());
    }

    @Test
    void stationaryVehicleKeepsSessionAliveWhileReporting() {
        // 评审修复#2：静止车辆的重复点虽然不入库，但会话活跃时间必须刷新，10 分钟空闲不再清会话。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        for (int minute = 1; minute <= 11; minute++) {
            now.set(T0 + minute * 60 * 1000L);
            service.append("car-1", 39.0, 116.0, T0 + minute * 60 * 1000L);
        }

        assertEquals(1, service.status().size());

        // 车辆继续行驶后仍能正常结束落库。
        service.append("car-1", 39.1, 116.1, T0 + 11 * 60 * 1000L);
        assertEquals(2, service.stop(9L));
        verify(areaService).saveRecordedRoute(eq(9L), eq("[[116.0,39.0],[116.1,39.1]]"));
    }

    @Test
    void pointCappedSessionKeepsAliveWhileDeviceKeepsReporting() {
        // 评审修复#2：满 20000 点封顶后继续上报，会话不再因“未新增点”被空闲超时清理。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        for (int i = 0; i < AreaService.MAX_ROUTE_POINTS; i++) {
            service.append("car-1", 39.0 + i * 1e-6, 116.0, T0 + i);
        }
        now.set(T0 + 9 * 60 * 1000L);
        service.append("car-1", 39.5, 116.5, T0 + 9 * 60 * 1000L);
        now.set(T0 + 11 * 60 * 1000L);
        service.append("car-1", 39.6, 116.6, T0 + 11 * 60 * 1000L);

        assertEquals(1, service.status().size());
        assertEquals(AreaService.MAX_ROUTE_POINTS, service.stop(9L));
    }

    @Test
    void slowDeviceClockDoesNotDropPointsOrKillSession() {
        // 评审修复#3：设备时钟慢 10 分钟时，首点及后续点按设备时钟自洽入库，不再被服务器 startedAt 下限过滤。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        long deviceTs = T0 - 10 * 60 * 1000L;
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, deviceTs);
        service.append("car-1", 39.1, 116.1, deviceTs + 1000);
        service.append("car-1", 39.2, 116.2, deviceTs + 2000);

        assertEquals(3, service.stop(9L));
        verify(areaService).saveRecordedRoute(eq(9L), eq("[[116.0,39.0],[116.1,39.1],[116.2,39.2]]"));
    }

    @Test
    void fastDeviceClockStillExpiresWhenTelemetryStops() {
        // 评审修复#3：设备时钟快 1 小时（lastFixTs 在未来）时，空闲超时仍按服务器时钟触发。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0 + 60 * 60 * 1000L);

        now.set(T0 + 10 * 60 * 1000L);
        assertTrue(service.status().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.stop(9L));
    }

    @Test
    void hasActiveSessionForDevicePurgesExpiredSession() {
        // 评审修复#4：过期会话经懒清理后不再把任务下发误拒为 409，设备立即可重新占用。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);

        now.set(T0 + 10 * 60 * 1000L);
        assertFalse(service.hasActiveSessionForDevice("car-1"));

        service.start(9L, "car-1");
        assertTrue(service.hasActiveSessionForDevice("car-1"));
    }

    @Test
    void cancelForDevicesReleasesOnlyTargetedSessions() {
        // v1.1 评审 S4：删除设备时联动取消其录制会话，其他会话不受影响。
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(areaService.get(10L)).thenReturn(area(10L, "B1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        when(deviceService.get("car-2")).thenReturn(device("car-2", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.start(10L, "car-2");

        service.cancelForDevices(List.of("car-1", "not-recording"));

        assertEquals(1, service.status().size());
        assertTrue(service.hasActiveSessionForDevice("car-2"));
        assertFalse(service.hasActiveSessionForDevice("car-1"));
    }

    private GreenhouseArea area(Long id, String code) {
        GreenhouseArea area = new GreenhouseArea();
        area.setId(id);
        area.setAreaCode(code);
        area.setCoordinateSystem("CGCS2000");
        return area;
    }

    private Device device(String deviceId, DeviceStatus status) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setStatus(status);
        return device;
    }

    @Test
    void stopKeepsSessionWhenSaveFailsWithTransientDatabaseError() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        service.append("car-1", 39.1, 116.1, T0 + 1);
        doThrow(new org.springframework.dao.QueryTimeoutException("lock wait timeout"))
                .doNothing()
                .when(areaService).saveRecordedRoute(anyLong(), anyString());

        assertThrows(org.springframework.dao.QueryTimeoutException.class, () -> service.stop(9L));
        // 瞬时数据库故障不丢已录轨迹：会话仍在，重试 stop 成功。
        assertEquals(1, service.status().size());
        assertEquals(2, service.stop(9L));
        assertTrue(service.status().isEmpty());
    }

    @Test
    void futureTimestampPointDoesNotFreezeRecording() {
        when(areaService.get(9L)).thenReturn(area(9L, "A1"));
        when(deviceService.get("car-1")).thenReturn(device("car-1", DeviceStatus.ONLINE));
        service.start(9L, "car-1");
        service.append("car-1", 39.0, 116.0, T0);
        // 一个时间戳异常超前（GPS 时间跳变）的点被接受……
        service.append("car-1", 39.1, 116.1, T0 + 24L * 60 * 60 * 1000);
        // ……之后恢复正常的时间戳不应被当作乱序丢弃。
        service.append("car-1", 39.2, 116.2, T0 + 2000);

        assertEquals(3, service.stop(9L));
    }
}
