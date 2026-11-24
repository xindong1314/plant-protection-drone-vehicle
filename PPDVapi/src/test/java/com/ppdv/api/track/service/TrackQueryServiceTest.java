package com.ppdv.api.track.service;

import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.domain.TrackSession;
import com.ppdv.api.track.dto.TrackSessionDetail;
import com.ppdv.api.track.dto.TrackSessionSummary;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import com.ppdv.api.track.repository.TrackSessionRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackQueryServiceTest {

    @Mock TrackSessionRepository sessionRepo;
    @Mock DetectionTrackRepository trackRepo;

    private TrackQueryService service;

    @BeforeEach
    void setUp() {
        service = new TrackQueryService(sessionRepo, trackRepo);
    }

    @Test
    void getSessionThrowsWhenMissing() {
        when(sessionRepo.findById(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getSession(9L));
    }

    @Test
    void getSessionAggregatesEpisodes() {
        TrackSession session = session(1L, "ACTIVE", null);
        when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        when(trackRepo.findByTrackSessionId(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
                .thenReturn(List.of(episode("EP1", "T1", "leaf_late_blight", 0.5),
                        episode("EP2", "T1", "leaf_early_blight", 0.8)));
        // 统计口径来自数据库 GROUP BY 投影，不再由加载出的 track 行在内存里累加。
        when(trackRepo.findStatsBySessionIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 2L, 1L, 0.8}));
        when(trackRepo.findCategoriesBySessionIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{1L, "leaf_late_blight"},
                        new Object[]{1L, "leaf_early_blight"}));

        TrackSessionDetail detail = service.getSession(1L);

        TrackSessionSummary summary = detail.summary();
        // 同一 trackId 的两次出现 = 2 个 episode、1 个去重目标。
        assertEquals(2, summary.episodeCount());
        assertEquals(1, summary.distinctTrackCount());
        assertEquals(List.of("leaf_late_blight", "leaf_early_blight"), summary.categories());
        assertEquals(0.8, summary.maxConfidence(), 0.001);
        assertEquals(2, detail.tracks().size());

        // 详情 tracks 有上限（m2）：pageSize=2000 且按 id ASC 取前 N 条。
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(trackRepo).findByTrackSessionId(org.mockito.ArgumentMatchers.eq(1L), pageableCaptor.capture());
        assertEquals(2000, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, pageableCaptor.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void listSessionsAggregatesViaDatabaseQueries() {
        TrackSession first = session(1L, "ACTIVE", null);
        TrackSession second = session(2L, "ENDED", 3000L);
        when(sessionRepo.findAll(ArgumentMatchers.<Specification<TrackSession>>any(),
                        ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<TrackSession>(List.of(first, second)));
        when(trackRepo.findStatsBySessionIds(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 2L, 1L, 0.8}));
        when(trackRepo.findCategoriesBySessionIds(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "leaf_late_blight"}));

        Page<TrackSessionSummary> page = service.listSessions(12L, null, null, null, null, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().get(0).episodeCount());
        assertEquals(1, page.getContent().get(0).distinctTrackCount());
        assertEquals(List.of("leaf_late_blight"), page.getContent().get(0).categories());
        assertEquals(0.8, page.getContent().get(0).maxConfidence(), 0.001);
        // 无 track 的会话不在 stats 结果里 → 全 0 兜底。
        assertEquals(0, page.getContent().get(1).episodeCount());
        assertEquals(0, page.getContent().get(1).distinctTrackCount());
        assertTrue(page.getContent().get(1).categories().isEmpty());
        assertEquals(0, page.getContent().get(1).maxConfidence(), 0.001);
        verify(trackRepo).findStatsBySessionIds(List.of(1L, 2L));
        verify(trackRepo).findCategoriesBySessionIds(List.of(1L, 2L));
    }

    @Test
    void listSessionsAppendsIdAscendingTiebreaker() {
        when(sessionRepo.findAll(ArgumentMatchers.<Specification<TrackSession>>any(),
                        ArgumentMatchers.<Pageable>any()))
                .thenReturn(Page.<TrackSession>empty());

        // 调用方未排序：默认 startTs DESC，且始终追加 id ASC 次键保证翻页稳定（m5）。
        service.listSessions(null, null, null, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepo).findAll(ArgumentMatchers.<Specification<TrackSession>>any(), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("startTs").getDirection());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
        assertEquals(List.of("startTs", "id"), sort.stream().map(Sort.Order::getProperty).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeRangeAndCategoryPredicatesAreApplied() {
        ArgumentCaptor<Specification<TrackSession>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(sessionRepo.findAll(specCaptor.capture(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(Page.<TrackSession>empty());

        service.listSessions(null, 1000L, 5000L, null, "leaf_late_blight", PageRequest.of(0, 10));

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Root<TrackSession> root = mock(Root.class);
        Subquery<Long> subquery = mock(Subquery.class);
        Root<DetectionTrack> trackRoot = mock(Root.class);
        Path<Long> endTsPath = mock(Path.class);
        Path<Long> startTsPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Path<Object> categoryPath = mock(Path.class);
        Path<Object> trackSessionIdPath = mock(Path.class);

        when(root.get("endTs")).thenReturn((Path) endTsPath);
        when(root.get("startTs")).thenReturn((Path) startTsPath);
        when(root.get("id")).thenReturn(idPath);
        when(trackRoot.get("category")).thenReturn(categoryPath);
        when(trackRoot.get("trackSessionId")).thenReturn(trackSessionIdPath);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(DetectionTrack.class)).thenReturn(trackRoot);

        Predicate endNull = mock(Predicate.class);
        Predicate endGreaterThan = mock(Predicate.class);
        Predicate endOr = mock(Predicate.class);
        Predicate startLessThan = mock(Predicate.class);
        Predicate categoryEqual = mock(Predicate.class);
        Predicate sessionEqual = mock(Predicate.class);
        when(cb.isNull(endTsPath)).thenReturn(endNull);
        when(cb.greaterThanOrEqualTo(endTsPath, 1000L)).thenReturn(endGreaterThan);
        when(cb.or(endNull, endGreaterThan)).thenReturn(endOr);
        when(cb.lessThanOrEqualTo(startTsPath, 5000L)).thenReturn(startLessThan);
        when(cb.equal(categoryPath, "leaf_late_blight")).thenReturn(categoryEqual);
        when(cb.equal(trackSessionIdPath, idPath)).thenReturn(sessionEqual);

        specCaptor.getValue().toPredicate(root, query, cb);

        // 区间交集：from 下界用 (endTs IS NULL OR endTs >= from)；to 上界 startTs <= to。
        verify(cb, atLeastOnce()).isNull(endTsPath);
        verify(cb, atLeastOnce()).greaterThanOrEqualTo(endTsPath, 1000L);
        verify(cb, atLeastOnce()).or(endNull, endGreaterThan);
        verify(cb, atLeastOnce()).lessThanOrEqualTo(startTsPath, 5000L);
        verify(cb).exists(any(Subquery.class));
        // 类别筛选是 EXISTS 子查询（会话任意 episode 出现过该类即命中）。
        verify(subquery).where(categoryEqual, sessionEqual);
        // 断言真实谓词被调用，而不是 any(Specification) 吞掉条件。
        verify(subquery).from(DetectionTrack.class);
    }

    private static TrackSession session(Long id, String status, Long endTs) {
        TrackSession session = new TrackSession();
        session.setId(id);
        session.setDeviceId("D1");
        session.setSessionKey("S-" + id);
        session.setAreaId(12L);
        session.setStartTs(1000L);
        session.setEndTs(endTs);
        session.setStatus(status);
        return session;
    }

    private static DetectionTrack episode(String episodeKey, String trackKey, String category, double confidence) {
        DetectionTrack track = new DetectionTrack();
        track.setTrackSessionId(1L);
        track.setEpisodeKey(episodeKey);
        track.setTrackKey(trackKey);
        track.setCategory(category);
        track.setConfidence(confidence);
        track.setAreaId(12L);
        track.setLastSeenTs(2000L);
        return track;
    }
}
