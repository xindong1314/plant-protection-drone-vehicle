package com.ppdv.api.track.service;

import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.domain.TrackSession;
import com.ppdv.api.track.dto.TrackSessionDetail;
import com.ppdv.api.track.dto.TrackSessionSummary;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import com.ppdv.api.track.repository.TrackSessionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跟踪查询：会话列表/详情。
 * 列表聚合走数据库 GROUP BY 投影（只取统计列，不加载 trajectory_json 等大字段），禁止逐会话单查；
 * 类别筛选走 EXISTS 子查询，时间筛选按会话区间与查询区间交集命中；
 * 详情 tracks 只返回前 {@link #MAX_DETAIL_TRACKS} 条，摘要仍取数据库累计值。
 */
@Service
public class TrackQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DETAIL_TRACKS = 2000;
    private static final SessionStats EMPTY_STATS = new SessionStats(0, 0, 0, List.of());

    private final TrackSessionRepository sessionRepo;
    private final DetectionTrackRepository trackRepo;

    public TrackQueryService(TrackSessionRepository sessionRepo, DetectionTrackRepository trackRepo) {
        this.sessionRepo = sessionRepo;
        this.trackRepo = trackRepo;
    }

    private record SessionStats(int episodeCount, long distinctTrackCount, double maxConfidence,
                                List<String> categories) {
    }

    /**
     * 会话列表。时间口径：会话区间 [startTs, endTs]（进行中会话视为持续至今）与 [from, to] 有交集即命中，
     * 跨时间范围开始、区间内有活动的会话同样返回。
     */
    @Transactional(readOnly = true)
    public Page<TrackSessionSummary> listSessions(Long areaId, Long from, Long to, String deviceId,
                                                  String category, Pageable pageable) {
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "startTs");
        // 始终追加 id 升序次键：同 startTs 行翻页稳定，避免重复/漏行。
        sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Specification<TrackSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (areaId != null) {
                predicates.add(cb.equal(root.get("areaId"), areaId));
            }
            // 区间交集：startTs ≤ to 且（endTs ≥ from；进行中会话 endTs 为空视为持续至今）。
            if (from != null) {
                predicates.add(cb.or(cb.isNull(root.get("endTs")),
                        cb.greaterThanOrEqualTo(root.get("endTs"), from)));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTs"), to));
            }
            if (deviceId != null && !deviceId.isBlank()) {
                predicates.add(cb.like(root.get("deviceId"), "%" + deviceId.trim() + "%"));
            }
            if (category != null && !category.isBlank()) {
                // 类别筛选用 EXISTS 子查询下推数据库，避免把命中会话 id 全量捞进内存拼超长 IN。
                // 口径为会话级：会话任意时刻出现过该类即命中，不随 from/to 收紧（m6，待产品确认）。
                Subquery<Long> episode = query.subquery(Long.class);
                Root<DetectionTrack> track = episode.from(DetectionTrack.class);
                episode.select(track.get("trackSessionId"));
                episode.where(cb.equal(track.get("category"), category.trim()),
                        cb.equal(track.get("trackSessionId"), root.get("id")));
                predicates.add(cb.exists(episode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TrackSession> page = sessionRepo.findAll(spec, sorted);
        Map<Long, SessionStats> statsBySession = statsOf(page.getContent());
        return page.map(session -> summarize(session, statsBySession.getOrDefault(session.getId(), EMPTY_STATS)));
    }

    @Transactional(readOnly = true)
    public TrackSessionDetail getSession(Long id) {
        TrackSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("跟踪会话不存在: " + id));
        List<DetectionTrack> tracks = trackRepo.findByTrackSessionId(id,
                PageRequest.of(0, MAX_DETAIL_TRACKS, Sort.by(Sort.Direction.ASC, "id")));
        SessionStats stats = statsOf(List.of(session)).getOrDefault(id, EMPTY_STATS);
        return new TrackSessionDetail(summarize(session, stats), tracks);
    }

    /** 一次 IN 查询聚合会话统计：无 track 的会话不在结果中，调用方用 EMPTY_STATS 兜底。 */
    private Map<Long, SessionStats> statsOf(List<TrackSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = sessions.stream().map(TrackSession::getId).toList();
        Map<Long, List<String>> categoriesBySession = new HashMap<>();
        // category 列 NOT NULL，直接取用。
        for (Object[] row : trackRepo.findCategoriesBySessionIds(ids)) {
            categoriesBySession.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }
        Map<Long, SessionStats> stats = new HashMap<>();
        for (Object[] row : trackRepo.findStatsBySessionIds(ids)) {
            Long sessionId = (Long) row[0];
            int episodeCount = ((Number) row[1]).intValue();
            long distinctTrackCount = ((Number) row[2]).longValue();
            double maxConfidence = row[3] == null ? 0 : ((Number) row[3]).doubleValue();
            stats.put(sessionId, new SessionStats(episodeCount, distinctTrackCount, maxConfidence,
                    categoriesBySession.getOrDefault(sessionId, List.of())));
        }
        return stats;
    }

    private static TrackSessionSummary summarize(TrackSession session, SessionStats stats) {
        return new TrackSessionSummary(session.getId(), session.getDeviceId(), session.getAreaId(),
                session.getSessionKey(), toLocal(session.getStartTs()), toLocal(session.getEndTs()),
                session.getStatus(), stats.episodeCount(), stats.distinctTrackCount(), stats.categories(),
                stats.maxConfidence());
    }

    private static LocalDateTime toLocal(Long epochMs) {
        return epochMs == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZONE);
    }
}
