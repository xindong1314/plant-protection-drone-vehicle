package com.ppdv.api.track.service;

import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.domain.TrackSession;
import com.ppdv.api.track.dto.TrackObservation;
import com.ppdv.api.track.dto.TrackStoreResult;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import com.ppdv.api.track.repository.TrackSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackStoreServiceTest {

    @Mock TrackSessionRepository sessionRepo;
    @Mock DetectionTrackRepository trackRepo;

    private TrackStoreService service;

    @BeforeEach
    void setUp() {
        service = new TrackStoreService(sessionRepo, trackRepo);
    }

    @Test
    void createsSessionAndEpisodeOnFirstMessage() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.empty());
        when(sessionRepo.save(any(TrackSession.class))).thenAnswer(inv -> {
            inv.getArgument(0, TrackSession.class).setId(1L);
            return inv.getArgument(0);
        });
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), any(), eq("EP1")))
                .thenReturn(Optional.empty());
        when(trackRepo.countByTrackSessionId(1L)).thenReturn(1L);

        TrackStoreResult result = service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        ArgumentCaptor<TrackSession> sessionCaptor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(sessionCaptor.capture());
        TrackSession session = sessionCaptor.getValue();
        assertEquals("ACTIVE", session.getStatus());
        assertEquals(1000L, session.getStartTs());
        assertEquals(12L, session.getAreaId());
        // 服务器侧最后活动时间在 apply 时写入，空闲清扫据此判定。
        assertNotNull(session.getLastActivityTs());

        ArgumentCaptor<DetectionTrack> trackCaptor = ArgumentCaptor.forClass(DetectionTrack.class);
        verify(trackRepo).save(trackCaptor.capture());
        DetectionTrack track = trackCaptor.getValue();
        assertEquals("T1", track.getTrackKey());
        assertEquals("EP1", track.getEpisodeKey());
        assertEquals(0, track.getEpisodeNo());
        assertEquals(0.8, track.getConfidence(), 0.001);
        assertNotNull(track.getCapturedAt());

        // episodeCount 为会话累计出现行数（与查询口径一致）。
        assertEquals(1, result.episodeCount());
        assertEquals("ACTIVE", result.status());
        assertEquals(List.of("leaf_late_blight"), result.categories());
    }

    @Test
    void sameEpisodeMessagesUpdateSingleRow() {
        TrackSession session = session(1L, "ACTIVE", null, null);
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.of(session));
        DetectionTrack existing = episode(1L, "EP1", 0.5, 100L);
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP1")).thenReturn(Optional.of(existing));

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.6, 1000L, 150L, "ACTIVE")), false);

        assertEquals(150L, existing.getLastSeenTs());
        assertEquals(1000L, existing.getFirstSeenTs());
        assertEquals(0.6, existing.getConfidence(), 0.001);
        assertEquals("EP1", existing.getEpisodeKey());
        verify(trackRepo).save(existing);
    }

    @Test
    void firstSeenTsTakesEarliestValue() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        // 已有 episode firstSeenTs=1000；新观测 firstSeenTs=800 更早，必须取 min 而不是覆盖。
        DetectionTrack existing = episode(1L, "EP1", 0.5, 1000L);
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP1")).thenReturn(Optional.of(existing));

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.9, 800L, 1500L, "ACTIVE")), false);

        // 旧值 1000 时覆盖与取 min 无法区分，必须断言 800。
        assertEquals(800L, existing.getFirstSeenTs());
        assertEquals(1500L, existing.getLastSeenTs());
        assertEquals(0.9, existing.getConfidence(), 0.001);
    }

    @Test
    void startTsBackfillsEarlierFirstSeen() {
        TrackSession session = session(1L, "ACTIVE", null, null);
        session.setStartTs(2000L);
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.of(session));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), any(), eq("EP1")))
                .thenReturn(Optional.empty());

        // 后到的更早观测回溯修正会话开始时间。
        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals(1000L, captor.getValue().getStartTs());
    }

    @Test
    void staleObservationIsIgnored() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        DetectionTrack existing = episode(1L, "EP1", 0.5, 100L);
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP1")).thenReturn(Optional.of(existing));

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.9, 10L, 50L, "ACTIVE")), false);

        assertEquals(100L, existing.getLastSeenTs());
        assertEquals(0.5, existing.getConfidence(), 0.001);
        verify(trackRepo, never()).save(any());
    }

    @Test
    void sameTrackIdSecondEpisodeCreatesNewRow() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP2")).thenReturn(Optional.empty());
        when(trackRepo.countByTrackSessionId(1L)).thenReturn(1L);

        TrackStoreResult result = service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP2", 1, "leaf_late_blight", 0.7, 3000L, 4000L, "ACTIVE")), false);

        // 同 trackId 再次出现 → 按 episodeId 查不到旧行 → 落新行（不按 trackKey 合并第一次出现）。
        ArgumentCaptor<DetectionTrack> captor = ArgumentCaptor.forClass(DetectionTrack.class);
        verify(trackRepo).save(captor.capture());
        assertEquals("EP2", captor.getValue().getEpisodeKey());
        assertEquals("T1", captor.getValue().getTrackKey());
        assertEquals(1, captor.getValue().getEpisodeNo());
        assertEquals(1, result.episodeCount());
    }

    @Test
    void partialEpisodeUpdateKeepsMissingFields() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        DetectionTrack existing = episode(1L, "EP1", 0.5, 100L);
        existing.setBoxX(1);
        existing.setBoxY(2);
        existing.setBoxW(3);
        existing.setBoxH(4);
        existing.setImageWidth(640);
        existing.setImageHeight(480);
        existing.setTrajectoryJson("[[1,2,100]]");
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP1")).thenReturn(Optional.of(existing));

        // 部分更新：新消息缺省 bbox/图像尺寸/轨迹，不清空已有值。
        service.apply("D1", 5000L, 12L, "S1",
                List.of(new TrackObservation("T1", "EP1", 0, "leaf_late_blight", 0.9, 1000L, 3000L,
                        null, null, null, null, null, null, null, "ACTIVE", null)), false);

        assertEquals(3000L, existing.getLastSeenTs());
        assertEquals(1, existing.getBoxX());
        assertEquals(2, existing.getBoxY());
        assertEquals(3, existing.getBoxW());
        assertEquals(4, existing.getBoxH());
        assertEquals(640, existing.getImageWidth());
        assertEquals(480, existing.getImageHeight());
        assertEquals("[[1,2,100]]", existing.getTrajectoryJson());
        assertEquals(0.9, existing.getConfidence(), 0.001);
    }

    @Test
    void unknownCategoryDoesNotOverwriteKnownCategory() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        DetectionTrack existing = episode(1L, "EP1", 0.5, 100L);
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey("D1", 1L, "EP1")).thenReturn(Optional.of(existing));

        // 类别中途被判 __unknown__ 不覆盖已知类别；时间/置信度仍正常更新。
        service.apply("D1", 5000L, 12L, "S1",
                List.of(new TrackObservation("T1", "EP1", 0, TrackClassCatalog.UNKNOWN, 0.9, 1000L, 3000L,
                        5, 6, 7, 8, null, null, null, "ACTIVE", null)), false);

        assertEquals("leaf_late_blight", existing.getCategory());
        assertEquals(0.9, existing.getConfidence(), 0.001);
        assertEquals(3000L, existing.getLastSeenTs());
    }

    @Test
    void sessionAreaFollowsLatestMessage() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), any(), eq("EP1")))
                .thenReturn(Optional.empty());

        // 设备跨区域巡检：会话区域跟随最新消息，与会话内 track 区保持一致。
        service.apply("D1", 5000L, 2L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals(2L, captor.getValue().getAreaId());
    }

    @Test
    void sessionEndEndsSessionRegardlessOfTrackStates() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), anyLong(), eq("EP1")))
                .thenReturn(Optional.empty());

        TrackStoreResult result = service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), true);

        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ENDED", captor.getValue().getStatus());
        assertEquals(5000L, captor.getValue().getEndTs());
        assertEquals("session_end", captor.getValue().getEndReason());
        assertTrue(result.ended());
    }

    @Test
    void singleEpisodeEndedDoesNotEndSession() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), anyLong(), eq("EP1")))
                .thenReturn(Optional.empty());

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ENDED")), false);

        // trackState=ENDED 只表示本次出现结束，会话仍 ACTIVE。
        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNull(captor.getValue().getEndTs());
    }

    @Test
    void vehicleEndedSessionDoesNotReactivate() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ENDED", 999L, "session_end")));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), anyLong(), eq("EP1")))
                .thenReturn(Optional.empty());

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        // 车端 sessionEnd 结束的会话：迟到补传只落 track，不回退会话状态。
        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ENDED", captor.getValue().getStatus());
        assertEquals(999L, captor.getValue().getEndTs());
        assertEquals("session_end", captor.getValue().getEndReason());
        verify(trackRepo).save(any(DetectionTrack.class));
    }

    @Test
    void idleEndedSessionReactivatesOnNewActivity() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ENDED", 999L, "idle")));
        when(trackRepo.findByDeviceIdAndTrackSessionIdAndEpisodeKey(eq("D1"), anyLong(), eq("EP1")))
                .thenReturn(Optional.empty());

        service.apply("D1", 5000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        // 云端空闲超时误判可被新活动复归：清空 endTs/endReason。
        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNull(captor.getValue().getEndTs());
        assertNull(captor.getValue().getEndReason());
    }

    @Test
    void emptyTracksWithoutSessionEndKeepSessionActive() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));

        service.apply("D1", 5000L, 12L, "S1", List.of(), false);

        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNull(captor.getValue().getEndTs());
        verify(trackRepo, never()).save(any());
    }

    @Test
    void endsExistingSessionWithoutArea() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ACTIVE", null, null)));

        TrackStoreResult result = service.endSession("D1", 5000L, "S1");

        // 区域不可解析 + sessionEnd：结束既有会话（end_reason=session_end）。
        assertNotNull(result);
        assertEquals("ENDED", result.status());
        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        assertEquals("ENDED", captor.getValue().getStatus());
        assertEquals(5000L, captor.getValue().getEndTs());
        assertEquals("session_end", captor.getValue().getEndReason());
    }

    @Test
    void endSessionReturnsNullWhenSessionMissing() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.empty());

        assertNull(service.endSession("D1", 5000L, "S1"));
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void endSessionIsIdempotentWhenAlreadyEnded() {
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1"))
                .thenReturn(Optional.of(session(1L, "ENDED", 999L, "session_end")));

        TrackStoreResult result = service.endSession("D1", 5000L, "S1");

        assertEquals("ENDED", result.status());
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void endSessionUpgradesIdleToSessionEnd() {
        TrackSession session = session(1L, "ENDED", 999L, "idle");
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.of(session));

        TrackStoreResult result = service.endSession("D1", 5000L, "S1");

        // 空闲清扫先置 ENDED/idle；随后车端 sessionEnd 到达必须升级为不可复归的 session_end。
        ArgumentCaptor<TrackSession> captor = ArgumentCaptor.forClass(TrackSession.class);
        verify(sessionRepo).save(captor.capture());
        TrackSession saved = captor.getValue();
        assertEquals("ENDED", saved.getStatus());
        assertEquals(5000L, saved.getEndTs());
        assertEquals("session_end", saved.getEndReason());
        assertEquals("ENDED", result.status());
    }

    @Test
    void idleEndedSessionCannotReactivateAfterSessionEndUpgrade() {
        // 同一会话实例：先被空闲清扫置 ENDED/idle，再被 sessionEnd 升级；之后的新活动不能再复归。
        TrackSession session = session(1L, "ENDED", 999L, "idle");
        when(sessionRepo.findByDeviceIdAndSessionKey("D1", "S1")).thenReturn(Optional.of(session));

        service.endSession("D1", 5000L, "S1");
        service.apply("D1", 6000L, 12L, "S1",
                List.of(observation("T1", "EP1", 0, "leaf_late_blight", 0.8, 1000L, 2000L, "ACTIVE")), false);

        assertEquals("ENDED", session.getStatus());
        assertEquals(5000L, session.getEndTs());
        assertEquals("session_end", session.getEndReason());
    }

    @Test
    void endsIdleSessionsByServerActivityTime() {
        long now = System.currentTimeMillis();
        TrackSession idle = session(1L, "ACTIVE", null, null, now - 30 * 60_000L);
        TrackSession fresh = session(2L, "ACTIVE", null, null, now);
        when(sessionRepo.findByStatus("ACTIVE")).thenReturn(List.of(idle, fresh));
        when(sessionRepo.endIdleSession(eq(1L), eq(idle.getLastActivityTs()), any(LocalDateTime.class)))
                .thenReturn(1);
        // 结束结果摘要的批量聚合：stats 行 [sessionId, count, count(distinct trackKey), maxConfidence]。
        when(trackRepo.findStatsBySessionIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L, 2L, 0.9}));
        when(trackRepo.findCategoriesBySessionIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "leaf_late_blight"}));

        List<TrackStoreService.IdleEndedSession> ended = service.endIdleSessions(15);

        assertEquals(1, ended.size());
        TrackStoreService.IdleEndedSession row = ended.get(0);
        assertEquals("D1", row.deviceId());
        assertEquals(idle.getLastActivityTs(), row.endTs());
        assertEquals("S1", row.result().sessionKey());
        assertEquals("ENDED", row.result().status());
        assertEquals(3, row.result().episodeCount());
        assertEquals(List.of("leaf_late_blight"), row.result().categories());
        assertEquals(0.9, row.result().maxConfidence(), 0.001);
        // 仍在活动的会话不参与结束。
        verify(sessionRepo, never()).endIdleSession(eq(2L), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void futureVehicleLastSeenDoesNotPreventSweep() {
        // M3 修复后空闲判定只读服务器侧 lastActivityTs；设备上报的 lastSeenTs 即使在未来也不参与倒计时。
        TrackSession idle = session(1L, "ACTIVE", null, null, System.currentTimeMillis() - 30 * 60_000L);
        when(sessionRepo.findByStatus("ACTIVE")).thenReturn(List.of(idle));
        when(sessionRepo.endIdleSession(eq(1L), eq(idle.getLastActivityTs()), any(LocalDateTime.class)))
                .thenReturn(1);

        List<TrackStoreService.IdleEndedSession> ended = service.endIdleSessions(15);

        assertEquals(1, ended.size());
        assertEquals(idle.getLastActivityTs(), ended.get(0).endTs());
    }

    @Test
    void idleSweepDisabledWhenMinutesNonPositive() {
        List<TrackStoreService.IdleEndedSession> ended = service.endIdleSessions(0);

        assertTrue(ended.isEmpty());
        verify(sessionRepo, never()).findByStatus(anyString());
    }

    private static TrackSession session(Long id, String status, Long endTs, String endReason) {
        return session(id, status, endTs, endReason, 1000L);
    }

    private static TrackSession session(Long id, String status, Long endTs, String endReason, long lastActivityTs) {
        TrackSession session = new TrackSession();
        session.setId(id);
        session.setDeviceId("D1");
        session.setSessionKey("S1");
        session.setAreaId(12L);
        session.setStartTs(1000L);
        session.setStatus(status);
        session.setEndTs(endTs);
        session.setEndReason(endReason);
        session.setLastActivityTs(lastActivityTs);
        return session;
    }

    private static DetectionTrack episode(Long sessionId, String episodeKey, double confidence, long lastSeenTs) {
        DetectionTrack track = new DetectionTrack();
        track.setId(9L);
        track.setDeviceId("D1");
        track.setTrackSessionId(sessionId);
        track.setTrackKey("T1");
        track.setEpisodeKey(episodeKey);
        track.setCategory("leaf_late_blight");
        track.setConfidence(confidence);
        track.setFirstSeenTs(1000L);
        track.setLastSeenTs(lastSeenTs);
        return track;
    }

    private static TrackObservation observation(String trackId, String episodeId, Integer episodeNo, String category,
                                                double confidence, long firstSeenTs, long lastSeenTs, String state) {
        return new TrackObservation(trackId, episodeId, episodeNo, category, confidence, firstSeenTs, lastSeenTs,
                1, 2, 3, 4, null, null, null, state, null);
    }
}
