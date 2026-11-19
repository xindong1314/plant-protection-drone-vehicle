package com.ppdv.api.track.service;

import com.ppdv.api.track.TrackProperties;
import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackRiskAdapterTest {

    @Mock DetectionTrackRepository trackRepo;

    private final TrackProperties props = new TrackProperties();
    private ScheduledExecutorService scheduler;
    private TrackRiskAdapter adapter;

    @BeforeEach
    void setUp() {
        props.setShadowMaxRows(10);
        props.setShadowWindowDays(7);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        adapter = new TrackRiskAdapter(trackRepo, props, scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void mergesEpisodesOfSameCategoryWithNoisyOr() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(
                        episode("EP1", "leaf_late_blight", 0.5),
                        episode("EP2", "leaf_late_blight", 0.5)), 2));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        // 同类跨 episode：1 − (1−0.5)(1−0.5) = 0.75；55 × 0.75 = 41.25（大于单次 27.5、小于 SUM 55）。
        assertEquals(41.25, score.trackScore(), 0.001);
        // contributions 键带会话 id：车端跨会话复用 episodeId 时不会互相覆盖。
        assertTrue(score.contributions().containsKey("leaf_late_blight@1@EP1"));
        assertTrue(score.contributions().containsKey("leaf_late_blight@1@EP2"));
        assertEquals(27.5, score.contributions().get("leaf_late_blight@1@EP1"), 0.001);
        assertTrue(score.hasEvidence());
        assertFalse(score.truncated());
    }

    @Test
    void sumModeAccumulatesFullWeightPerEpisode() {
        props.setEpisodeAggregation("SUM");
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(
                        episode("EP1", "leaf_late_blight", 0.5),
                        episode("EP2", "leaf_late_blight", 0.5)), 2));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        // SUM：每次出现按完整权重累加 0.5+0.5=1.0；55 × 1.0 = 55（大于 NOISY_OR 的 41.25）。
        assertEquals(55, score.trackScore(), 0.001);
    }

    @Test
    void sumModeIsCappedAtEighty() {
        props.setEpisodeAggregation("SUM");
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(
                        episode("EP1", "leaf_late_blight", 0.9),
                        episode("EP2", "leaf_late_blight", 0.9),
                        episode("EP3", "leaf_late_blight", 0.9)), 3));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        // 0.9×3=2.7；55 × 2.7 = 148.5 → 封顶 80。
        assertEquals(80, score.trackScore(), 0.001);
    }

    @Test
    void phenologyClassesDoNotContribute() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(episode("EP1", "fruit_ripe", 1.0)), 1));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        assertEquals(0, score.trackScore(), 0.001);
        assertTrue(score.contributions().isEmpty());
        assertFalse(score.hasEvidence());
    }

    @Test
    void scoreIsCappedAtEighty() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(
                        episode("EP1", "leaf_yellow_leaf_curl_virus", 1.0),
                        episode("EP2", "leaf_late_blight", 1.0)), 2));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        assertEquals(80, score.trackScore(), 0.001);
    }

    @Test
    void flagsTruncationWhenTotalRowsExceedLimit() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page(List.of(episode("EP1", "leaf_late_blight", 0.9)), 3000));

        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        assertTrue(score.truncated());
    }

    @Test
    void nonPositiveMaxRowsIsClampedInsteadOfThrowing() {
        props.setShadowMaxRows(0);
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        // shadow-max-rows<=0 若不钳制，PageRequest.of(0,0) 直接抛异常（shadow-in-evaluate 时 evaluate 500）。
        TrackRiskAdapter.ShadowScore score = adapter.computeShadow(12L);

        assertFalse(score.hasEvidence());
        assertFalse(score.truncated());
        assertEquals(1, props.getShadowMaxRows());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(trackRepo, atLeastOnce()).findByAreaIdAndLastSeenTsBetween(
                eq(12L), anyLong(), anyLong(), pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void maxRowsIsCappedAtUpperBound() {
        props.setShadowMaxRows(100000);
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        adapter.computeShadow(12L);

        // 配置上限 10000，避免单次查询行数失控。
        assertEquals(10000, props.getShadowMaxRows());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(trackRepo, atLeastOnce()).findByAreaIdAndLastSeenTsBetween(
                eq(12L), anyLong(), anyLong(), pageableCaptor.capture());
        assertEquals(10000, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void negativeWindowDaysIsClampedToZero() {
        props.setShadowWindowDays(-1);
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertEquals(0, props.getShadowWindowDays());
        adapter.computeShadow(12L);

        // 窗口按 0 天：since ≈ until，查询区间不超过一分钟。
        ArgumentCaptor<Long> sinceCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> untilCaptor = ArgumentCaptor.forClass(Long.class);
        verify(trackRepo, atLeastOnce()).findByAreaIdAndLastSeenTsBetween(
                eq(12L), sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertTrue(untilCaptor.getValue() - sinceCaptor.getValue() <= 60_000L);
    }

    @Test
    void futureLastSeenRowsAreExcludedByWindowUpperBound() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());
        long before = System.currentTimeMillis();

        adapter.computeShadow(12L);

        // m7：查询上界 until=服务器当前时间，车端超前的 lastSeenTs 行不会挤占 maxRows。
        ArgumentCaptor<Long> untilCaptor = ArgumentCaptor.forClass(Long.class);
        verify(trackRepo, atLeastOnce()).findByAreaIdAndLastSeenTsBetween(
                eq(12L), anyLong(), untilCaptor.capture(), any(Pageable.class));
        assertTrue(untilCaptor.getValue() >= before);
        assertTrue(untilCaptor.getValue() <= System.currentTimeMillis());
    }

    @Test
    void maxDelayForcesRefreshUnderContinuousDebounce() {
        props.setDebounceMs(5000);
        props.setShadowMaxDelayMs(100);
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        adapter.schedule(12L, false);

        // m4：去抖 5s，但最大等待 100ms —— 持续消息下仍会到点强制刷新。
        verify(trackRepo, timeout(1500)).findByAreaIdAndLastSeenTsBetween(
                eq(12L), anyLong(), anyLong(), any(Pageable.class));
    }

    @Test
    void immediateScheduleRunsShadowRefreshAsynchronously() {
        when(trackRepo.findByAreaIdAndLastSeenTsBetween(eq(12L), anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        adapter.schedule(12L, true);

        verify(trackRepo, timeout(2000)).findByAreaIdAndLastSeenTsBetween(
                eq(12L), anyLong(), anyLong(), any(Pageable.class));
    }

    private static Page<DetectionTrack> page(List<DetectionTrack> rows, long total) {
        return new PageImpl<>(rows, PageRequest.of(0, 10), total);
    }

    private static DetectionTrack episode(String episodeKey, String category, double confidence) {
        DetectionTrack track = new DetectionTrack();
        track.setTrackSessionId(1L);
        track.setEpisodeKey(episodeKey);
        track.setCategory(category);
        track.setConfidence(confidence);
        return track;
    }
}
