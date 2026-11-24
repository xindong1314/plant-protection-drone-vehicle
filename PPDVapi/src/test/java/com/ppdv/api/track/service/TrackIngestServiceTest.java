package com.ppdv.api.track.service;

import com.ppdv.api.track.TrackProperties;
import com.ppdv.api.track.dto.TrackObservation;
import com.ppdv.api.track.dto.TrackStoreResult;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.device.Device;
import com.ppdv.api.device.DeviceService;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.service.AreaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackIngestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrackProperties props = new TrackProperties();
    private final Device device = new Device();

    @Mock RedisCache redisCache;
    @Mock DeviceService deviceService;
    @Mock AreaService areaService;
    @Mock TrackStoreService storeService;
    @Mock TrackRiskAdapter riskAdapter;
    @Mock TrackEventBroadcaster broadcaster;

    private TrackIngestService ingest;

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        props.setMaxTracks(50);
        props.setMaxTrajectoryPoints(20);
        props.setMsgDedupTtlHours(24);
        ingest = new TrackIngestService(props, objectMapper, redisCache, deviceService, areaService,
                storeService, riskAdapter, broadcaster);
        lenient().when(deviceService.ensure(anyString())).thenReturn(device);
        lenient().when(areaService.existsById(anyLong())).thenReturn(true);
        lenient().when(redisCache.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
        lenient().when(storeService.apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean()))
                .thenReturn(new TrackStoreResult("S1", 12L, "ACTIVE", 1, List.of("leaf_late_blight"), 0.8));
    }

    @Test
    void duplicateMsgIdIsIgnored() {
        when(redisCache.setIfAbsent(eq("track:msg:D1:m-1"), eq("1"), eq(24L), eq(TimeUnit.HOURS))).thenReturn(false);

        ingest.onMessage(topic(), message("m-1", "D1", "S1", 12L, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        // 去重键带 deviceId：两台车的同 msgId 互不干扰。
        verify(redisCache).setIfAbsent(eq("track:msg:D1:m-1"), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void persistFailureDeletesDedupeKeySoRedeliveryIsNotSwallowed() {
        when(storeService.apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean()))
                .thenThrow(new RuntimeException("db down"));

        ingest.onMessage(topic(), message("m-15", "D1", "S1", 12L, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        // 落库失败回删去重键：QoS1 补传可重放（落库幂等），避免消息被永久吞掉。
        verify(redisCache).delete("track:msg:D1:m-15");
        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void envelopeDeviceMismatchIsDropped() {
        ingest.onMessage(topic(), message("m-2", "OTHER", "S1", 12L, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void unresolvedAreaIsDropped() {
        ingest.onMessage(topic(), message("m-3", "D1", "S1", null, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(deviceService).ensure("D1");
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(storeService, never()).endSession(anyString(), anyLong(), anyString());
        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void sessionEndWithUnresolvedAreaStillEndsExistingSession() {
        when(storeService.endSession(eq("D1"), anyLong(), eq("S1")))
                .thenReturn(new TrackStoreResult("S1", 12L, "ENDED", 3, List.of(), 0));

        ingest.onMessage(topic(), messageEnd("m-16", "D1", "S1", null, true,
                track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        // 区域不可解析但 sessionEnd=true：仍要结束既有会话，结束信号不丢。
        verify(storeService).endSession(eq("D1"), anyLong(), eq("S1"));
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(riskAdapter).schedule(eq(12L), eq(true));
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void sessionEndWithUnresolvedAreaAndNoExistingSessionIsDropped() {
        when(storeService.endSession(eq("D1"), anyLong(), eq("S1"))).thenReturn(null);

        ingest.onMessage(topic(), messageEnd("m-17", "D1", "S1", null, true,
                track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
        verify(riskAdapter, never()).schedule(anyLong(), anyBoolean());
    }

    @Test
    void areaIsResolvedFromDeviceCurrentZoneWhenPayloadHasNoAreaId() {
        device.setCurrentZone("A1");
        GreenhouseArea area = new GreenhouseArea();
        area.setId(12L);
        area.setAreaCode("A1");
        when(areaService.getByCode("A1")).thenReturn(area);

        ingest.onMessage(topic(), message("m-4", "D1", "S1", null, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), anyList(), eq(false));
        verify(riskAdapter).schedule(eq(12L), eq(false));
    }

    @Test
    void unknownClassIsStoredAsUnknownPlaceholder() {
        ingest.onMessage(topic(), message("m-5", "D1", "S1", 12L, track("T1", "EP-T1", "mystery_class", 0.8, 2000)));

        ArgumentCaptor<List<TrackObservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), captor.capture(), eq(false));
        assertEquals(TrackClassCatalog.UNKNOWN, captor.getValue().get(0).category());
    }

    @Test
    void retiredClassIsRejectedAndMessageDropped() {
        ingest.onMessage(topic(), message("m-6", "D1", "S1", 12L, track("T1", "EP-T1", "虫害", 0.8, 2000)));

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void tooManyTracksIsDropped() {
        props.setMaxTracks(2);
        ingest.onMessage(topic(), message("m-7", "D1", "S1", 12L,
                track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000),
                track("T2", "EP-T2", "leaf_late_blight", 0.8, 2000),
                track("T3", "EP-T3", "leaf_late_blight", 0.8, 2000)));

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void oversizedMessageIsDropped() {
        String padded = messageWithPad("m-8", "a".repeat(300_000));
        ingest.onMessage(topic(), padded);

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void nonTrackTypeIsDropped() {
        String payload = "{\"msgId\":\"m-9\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"state\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":[]}}";
        ingest.onMessage(topic(), payload);

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void missingMsgIdIsDropped() {
        String payload = "{\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":[]}}";
        ingest.onMessage(topic(), payload);

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void nonArrayTracksIsDropped() {
        String payload = "{\"msgId\":\"m-10\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":{}}}";
        ingest.onMessage(topic(), payload);

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void validMessageStoresBroadcastsAndSchedulesShadow() {
        ingest.onMessage(topic(), message("m-11", "D1", "S1", 12L, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        ArgumentCaptor<List<TrackObservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), captor.capture(), eq(false));
        TrackObservation observation = captor.getValue().get(0);
        assertEquals("T1", observation.trackId());
        assertEquals("EP-T1", observation.episodeId());
        assertEquals(2, observation.episodeNo());
        // 帧数已从协议删除：旧报文携带 frameCount 也被忽略（TrackObservation 无该字段）。

        verify(riskAdapter).schedule(eq(12L), eq(false));
        // 广播内容（kind/episodeCount/不含 ended）由 TrackEventBroadcasterTest 校验，这里只验证调用。
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void missingEpisodeIdFallsBackToTrackIdAtFirstSeenTs() {
        String payload = "{\"msgId\":\"m-12\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":["
                + "{\"trackId\":\"T9\",\"class\":\"leaf_late_blight\",\"confidence\":0.8,"
                + "\"firstSeenTs\":1000,\"lastSeenTs\":2000,\"trackState\":\"ACTIVE\"}]}}";
        ingest.onMessage(topic(), payload);

        ArgumentCaptor<List<TrackObservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), captor.capture(), eq(false));
        assertEquals("T9@1000", captor.getValue().get(0).episodeId());
        assertNull(captor.getValue().get(0).episodeNo());
    }

    @Test
    void missingEpisodeIdWithMissingFirstSeenTsIsRejected() {
        String payload = "{\"msgId\":\"m-18\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":["
                + "{\"trackId\":\"T9\",\"class\":\"leaf_late_blight\",\"confidence\":0.8,"
                + "\"lastSeenTs\":2000,\"trackState\":\"ACTIVE\"}]}}";
        ingest.onMessage(topic(), payload);

        // firstSeenTs 是兜底值时兜底键随消息漂移（同一出现每条消息各落一行）→ 直接拒收该 track。
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void fallbackKeyExceeding64CharsIsRejected() {
        String payload = "{\"msgId\":\"m-19\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"tracks\":["
                + "{\"trackId\":\"" + "T".repeat(60) + "\",\"class\":\"leaf_late_blight\",\"confidence\":0.8,"
                + "\"firstSeenTs\":1000,\"lastSeenTs\":2000,\"trackState\":\"ACTIVE\"}]}}";
        ingest.onMessage(topic(), payload);

        // 兜底键 trackId@firstSeenTs 超过 64 字符 → 拒收该 track，不产生静默数据丢失。
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void missingEpisodeIdIsRejectedWhenRequireEpisode() {
        props.setRequireEpisode(true);
        ingest.onMessage(topic(), message("m-13", "D1", "S1", 12L, trackWithoutEpisode("T1", "leaf_late_blight", 0.8, 2000)));

        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
    }

    @Test
    void sessionEndFlagIsPassedToStoreAndSchedulesImmediateShadow() {
        when(storeService.apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean()))
                .thenReturn(new TrackStoreResult("S1", 12L, "ENDED", 1, List.of("leaf_late_blight"), 0.8));

        ingest.onMessage(topic(), messageEnd("m-14", "D1", "S1", 12L, true,
                track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), anyList(), eq(true));
        verify(riskAdapter).schedule(eq(12L), eq(true));
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void emptyTracksWithSessionEndStillEndsSession() {
        when(storeService.apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean()))
                .thenReturn(new TrackStoreResult("S1", 12L, "ENDED", 0, List.of(), 0));

        // tracks=[] 但 sessionEnd=true：结束信号不能因"无有效 track"早退而丢失。
        ingest.onMessage(topic(), messageEnd("m-20", "D1", "S1", 12L, true));

        ArgumentCaptor<List<TrackObservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), captor.capture(), eq(true));
        assertTrue(captor.getValue().isEmpty());
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
        verify(riskAdapter).schedule(eq(12L), eq(true));
    }

    @Test
    void allInvalidTracksWithSessionEndStillEndsSession() {
        when(storeService.apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean()))
                .thenReturn(new TrackStoreResult("S1", 12L, "ENDED", 0, List.of(), 0));

        // 全部 track 因旧类别拒识时，sessionEnd 仍必须透传落库，不能整条丢弃。
        ingest.onMessage(topic(), messageEnd("m-21", "D1", "S1", 12L, true,
                track("T1", "EP-T1", "虫害", 0.8, 2000)));

        ArgumentCaptor<List<TrackObservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), captor.capture(), eq(true));
        assertTrue(captor.getValue().isEmpty());
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void allInvalidTracksWithoutSessionEndIsDropped() {
        ingest.onMessage(topic(), message("m-22", "D1", "S1", 12L,
                track("T1", "EP-T1", "虫害", 0.8, 2000)));

        // 无有效 track 且无 sessionEnd：整条丢弃（不产生空落库）。
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(broadcaster, never()).broadcast(anyString(), anyLong(), any(TrackStoreResult.class));
    }

    @Test
    void sessionEndWithUnresolvedAreaAndAllInvalidTracksStillEndsExisting() {
        when(storeService.endSession(eq("D1"), anyLong(), eq("S1")))
                .thenReturn(new TrackStoreResult("S1", 12L, "ENDED", 0, List.of(), 0));

        // 区域不可解析 + 全部 track 拒识 + sessionEnd=true：仍结束既有会话并广播。
        ingest.onMessage(topic(), messageEnd("m-23", "D1", "S1", null, true,
                track("T1", "EP-T1", "虫害", 0.8, 2000)));

        verify(storeService).endSession(eq("D1"), anyLong(), eq("S1"));
        verify(storeService, never()).apply(anyString(), anyLong(), any(), anyString(), anyList(), anyBoolean());
        verify(broadcaster).broadcast(eq("D1"), anyLong(), any(TrackStoreResult.class));
        verify(riskAdapter).schedule(eq(12L), eq(true));
    }

    @Test
    void dedupeDisabledWhenTtlNonPositive() {
        props.setMsgDedupTtlHours(0);

        ingest.onMessage(topic(), message("m-24", "D1", "S1", 12L,
                track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        // TTL≤0 表示关闭去重：不写 Redis 键，直接处理消息。
        verify(redisCache, never()).setIfAbsent(anyString(), any(), anyLong(), any());
        verify(storeService).apply(eq("D1"), anyLong(), eq(12L), eq("S1"), anyList(), eq(false));
    }

    private String topic() {
        return "ppdv/v1/D1/tele/track";
    }

    private String message(String msgId, String deviceId, String sessionKey, Long areaId, String... tracks) {
        return messageEnd(msgId, deviceId, sessionKey, areaId, false, tracks);
    }

    private String messageEnd(String msgId, String deviceId, String sessionKey, Long areaId, boolean sessionEnd, String... tracks) {
        String area = areaId == null ? "" : "\"areaId\":" + areaId + ",";
        return "{\"msgId\":\"" + msgId + "\",\"deviceId\":\"" + deviceId + "\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{" + area + "\"trackSessionId\":\"" + sessionKey + "\",\"sessionEnd\":" + sessionEnd
                + ",\"tracks\":[" + String.join(",", tracks) + "]}}";
    }

    private String messageWithPad(String msgId, String pad) {
        return "{\"msgId\":\"" + msgId + "\",\"deviceId\":\"D1\",\"ts\":1000,\"type\":\"track\","
                + "\"data\":{\"trackSessionId\":\"S1\",\"areaId\":12,\"pad\":\"" + pad + "\",\"tracks\":[]}}";
    }

    /** 新协议：episodeId/episodeNo 必传，无 frameCount。 */
    private String track(String trackId, String episodeId, String category, double confidence, long lastSeenTs) {
        return "{\"trackId\":\"" + trackId + "\",\"episodeId\":\"" + episodeId + "\",\"episodeNo\":2,"
                + "\"class\":\"" + category + "\",\"confidence\":" + confidence
                + ",\"firstSeenTs\":" + (lastSeenTs - 1000) + ",\"lastSeenTs\":" + lastSeenTs
                + ",\"boxX\":1,\"boxY\":2,\"boxW\":3,\"boxH\":4,\"trackState\":\"ACTIVE\"}";
    }

    /** 旧协议形状：无 episodeId、带 frameCount（用于兼容路径验证）。 */
    private String trackWithoutEpisode(String trackId, String category, double confidence, long lastSeenTs) {
        return "{\"trackId\":\"" + trackId + "\",\"class\":\"" + category + "\",\"confidence\":" + confidence
                + ",\"firstSeenTs\":" + (lastSeenTs - 1000) + ",\"lastSeenTs\":" + lastSeenTs
                + ",\"frameCount\":3,\"boxX\":1,\"boxY\":2,\"boxW\":3,\"boxH\":4,\"trackState\":\"ACTIVE\"}";
    }

    @Test
    void unresolvedAreaDeletesDedupeKeySoRetryWithSameMsgIdCanSucceed() {
        ingest.onMessage(topic(), message("m-3", "D1", "S1", null, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        // 区域暂不可解析属于可恢复条件：回删去重键，否则同 msgId 的重发会被当重复消息永久吞掉。
        verify(redisCache).delete("track:msg:D1:m-3");
    }

    @Test
    void deterministicallyInvalidMessageKeepsDedupeKey() {
        ingest.onMessage(topic(), message("m-4", "OTHER", "S1", 12L, track("T1", "EP-T1", "leaf_late_blight", 0.8, 2000)));

        verify(redisCache, never()).delete(anyString());
    }
}
