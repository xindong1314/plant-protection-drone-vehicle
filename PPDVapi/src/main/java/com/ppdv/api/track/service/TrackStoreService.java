package com.ppdv.api.track.service;

import com.ppdv.api.track.domain.DetectionTrack;
import com.ppdv.api.track.domain.TrackSession;
import com.ppdv.api.track.dto.TrackObservation;
import com.ppdv.api.track.dto.TrackStoreResult;
import com.ppdv.api.track.repository.DetectionTrackRepository;
import com.ppdv.api.track.repository.TrackSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 跟踪观测落库：会话 + track 幂等 upsert 与会话状态机。
 * 身份模型（docs/protocol.md §5.6，v0.4）：按 episodeId upsert（同一出现一行、再次出现新行）；
 * 巡检会话只由 data.sessionEnd（与空闲超时）结束，单条 episode ENDED 不影响会话；
 * 车端 sessionEnd 结束不可复归，云端空闲超时结束（end_reason=idle）可被新活动复归；
 * 唯一键由 V14 uk_episode 兜底；同一 episode 以 lastSeenTs 单调更新，乱序不新建行。
 * 并发限定：按单 MQTT 回调线程串行消费设计；多实例/并行消费下同一 episode 并发 INSERT 会撞 uk_episode，
 * 需改为 upsert + 冲突重试（当前不触发）。
 */
@Service
public class TrackStoreService {

    private static final Logger log = LoggerFactory.getLogger(TrackStoreService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 空闲清扫结束时的一行结果（供事务提交后的 WS 广播）：deviceId/endTs + 会话摘要。 */
    public record IdleEndedSession(String deviceId, long endTs, TrackStoreResult result) {
    }

    private final TrackSessionRepository sessionRepo;
    private final DetectionTrackRepository trackRepo;

    public TrackStoreService(TrackSessionRepository sessionRepo, DetectionTrackRepository trackRepo) {
        this.sessionRepo = sessionRepo;
        this.trackRepo = trackRepo;
    }

    @Transactional
    public TrackStoreResult apply(String deviceId, long msgTs, Long areaId, String sessionKey,
                                  List<TrackObservation> observations, boolean sessionEnd) {
        TrackSession session = sessionRepo.findByDeviceIdAndSessionKey(deviceId, sessionKey).orElse(null);
        Long minFirstSeen = observations.stream()
                .map(TrackObservation::firstSeenTs)
                .min(Long::compareTo)
                .orElse(null);
        long serverNow = System.currentTimeMillis();

        if (session == null) {
            session = new TrackSession();
            session.setDeviceId(deviceId);
            session.setSessionKey(sessionKey);
            session.setAreaId(areaId);
            // 无有效 track（tracks=[] 或全部拒识）时取消息 ts。
            session.setStartTs(minFirstSeen == null ? msgTs : minFirstSeen);
            session.setStatus("ACTIVE");
        } else {
            // 会话区域跟随最新消息：设备跨区域巡检后会话区与 track 区保持一致。
            session.setAreaId(areaId);
            // 会话开始取会话内最早 firstSeenTs：后到的更早观测回溯修正 startTs。
            if (minFirstSeen != null && minFirstSeen < session.getStartTs()) {
                session.setStartTs(minFirstSeen);
            }
        }
        // 服务器侧最后活动时间：空闲清扫只按服务器时钟判定，车端 lastSeenTs 超前/滞后不影响倒计时。
        session.setLastActivityTs(serverNow);
        if (sessionEnd) {
            // 巡检结束只认 data.sessionEnd；重复 sessionEnd 幂等。
            session.setStatus("ENDED");
            session.setEndTs(msgTs);
            session.setEndReason("session_end");
        } else if ("ENDED".equals(session.getStatus())) {
            if ("idle".equals(session.getEndReason())) {
                // 云端空闲超时结束的会话可被新活动复归；车端 sessionEnd 结束的会话不可复归。
                log.debug("[{}] 云端空闲结束的会话收到新活动，复归 ACTIVE session={}", deviceId, sessionKey);
                session.setStatus("ACTIVE");
                session.setEndTs(null);
                session.setEndReason(null);
            } else {
                // 车端明确结束（或存量无 end_reason）的会话收到迟到补传：只更新记录，不回退会话状态。
                log.debug("[{}] 会话已结束，迟到数据仅落库 session={}", deviceId, sessionKey);
            }
        }
        sessionRepo.save(session);

        for (TrackObservation observation : observations) {
            DetectionTrack existing = trackRepo
                    .findByDeviceIdAndTrackSessionIdAndEpisodeKey(deviceId, session.getId(), observation.episodeId())
                    .orElse(null);
            if (existing == null) {
                trackRepo.save(newTrack(deviceId, session.getId(), areaId, observation));
            } else if (observation.lastSeenTs() >= existing.getLastSeenTs()) {
                updateTrack(existing, observation);
                trackRepo.save(existing);
            } else {
                log.debug("[{}] 跟踪观测乱序忽略: episode={}, lastSeenTs={} < {}",
                        deviceId, observation.episodeId(), observation.lastSeenTs(), existing.getLastSeenTs());
            }
        }

        List<String> categories = observations.stream().map(TrackObservation::category).distinct().toList();
        double maxConfidence = observations.stream().mapToDouble(TrackObservation::confidence).max().orElse(0);
        // 会话累计出现行数：与查询列表口径一致（WS 摘要直接消费）。
        long cumulativeEpisodes = trackRepo.countByTrackSessionId(session.getId());
        return new TrackStoreResult(sessionKey, session.getAreaId(), session.getStatus(),
                (int) cumulativeEpisodes, categories, maxConfidence);
    }

    /**
     * 区域不可解析但消息携带 sessionEnd=true 时结束既有会话（兜底，避免结束信号丢失只能等空闲清扫）：
     * 会话存在才结束；重复 sessionEnd 幂等；已被空闲清扫置 ENDED/idle 时补写为 session_end（不可复归）。
     * 不存在返回 null 由调用方丢弃。
     */
    @Transactional
    public TrackStoreResult endSession(String deviceId, long msgTs, String sessionKey) {
        return sessionRepo.findByDeviceIdAndSessionKey(deviceId, sessionKey)
                .map(session -> {
                    if (!"session_end".equals(session.getEndReason())) {
                        session.setStatus("ENDED");
                        session.setEndTs(msgTs);
                        session.setEndReason("session_end");
                        sessionRepo.save(session);
                    }
                    return new TrackStoreResult(session.getSessionKey(), session.getAreaId(), "ENDED",
                            (int) trackRepo.countByTrackSessionId(session.getId()), List.of(), 0);
                })
                .orElse(null);
    }

    /**
     * 会话空闲自动结束（见 docs/protocol.md §5.6 的会话生命周期）：车端未发 sessionEnd 时，
     * 服务器侧最后活动时间（apply 时写入 last_activity_ts）超过空闲分钟数的 ACTIVE 会话置 ENDED。
     * 按服务器时钟判定，车端时钟超前不影响清扫；时钟滞后的误判由 idle 可复归兜底。
     *
     * @return 本次结束的会话及其摘要（供 WS 广播；idleMinutes ≤ 0 时返回空列表）
     */
    @Transactional
    public List<IdleEndedSession> endIdleSessions(long idleMinutes) {
        if (idleMinutes <= 0) {
            return List.of();
        }
        List<TrackSession> activeSessions = sessionRepo.findByStatus("ACTIVE");
        if (activeSessions.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        LocalDateTime updateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZONE);
        long idleMs = idleMinutes * 60_000L;
        List<TrackSession> endedSessions = new ArrayList<>();
        for (TrackSession session : activeSessions) {
            long lastActivity = session.getLastActivityTs();
            if (now - lastActivity <= idleMs) {
                continue;
            }
            // 条件更新（仅 ACTIVE 才置 ENDED）：已被并发 apply 写走时放弃，不做 last-commit-wins 覆盖。
            if (sessionRepo.endIdleSession(session.getId(), lastActivity, updateTime) > 0) {
                endedSessions.add(session);
                log.info("[{}] 会话空闲超过 {} 分钟自动结束 session={}", session.getDeviceId(), idleMinutes, session.getSessionKey());
            }
        }
        return idleResults(endedSessions);
    }

    /** 空闲结束会话的 WS 摘要：出现次数/类别/最高置信度与查询口径一致（批量查询，避免逐会话 N+1）。 */
    private List<IdleEndedSession> idleResults(List<TrackSession> endedSessions) {
        List<IdleEndedSession> results = new ArrayList<>(endedSessions.size());
        if (endedSessions.isEmpty()) {
            return results;
        }
        List<Long> ids = endedSessions.stream().map(TrackSession::getId).toList();
        Map<Long, List<String>> categoriesById = new HashMap<>();
        for (Object[] row : trackRepo.findCategoriesBySessionIds(ids)) {
            Long sessionId = (Long) row[0];
            categoriesById.computeIfAbsent(sessionId, key -> new ArrayList<>()).add((String) row[1]);
        }
        Map<Long, Object[]> statsById = new HashMap<>();
        for (Object[] row : trackRepo.findStatsBySessionIds(ids)) {
            statsById.put((Long) row[0], row);
        }
        for (TrackSession session : endedSessions) {
            Object[] stats = statsById.get(session.getId());
            long episodeCount = stats == null ? 0 : ((Number) stats[1]).longValue();
            double maxConfidence = stats == null || stats[3] == null ? 0 : ((Number) stats[3]).doubleValue();
            results.add(new IdleEndedSession(session.getDeviceId(), session.getLastActivityTs(),
                    new TrackStoreResult(session.getSessionKey(), session.getAreaId(), "ENDED", (int) episodeCount,
                            categoriesById.getOrDefault(session.getId(), List.of()), maxConfidence)));
        }
        return results;
    }

    private DetectionTrack newTrack(String deviceId, Long sessionId, Long areaId, TrackObservation o) {
        DetectionTrack t = new DetectionTrack();
        t.setDeviceId(deviceId);
        t.setTrackSessionId(sessionId);
        t.setAreaId(areaId);
        t.setTrackKey(o.trackId());
        t.setEpisodeKey(o.episodeId());
        t.setEpisodeNo(o.episodeNo() == null ? 0 : o.episodeNo());
        t.setCategory(o.category());
        t.setConfidence(o.confidence());
        t.setFirstSeenTs(o.firstSeenTs());
        t.setLastSeenTs(o.lastSeenTs());
        // bbox 缺省补 0（与列默认一致），仅新建行；更新行缺省时保留已有值（见 updateTrack）。
        t.setBoxX(o.boxX() == null ? 0 : o.boxX());
        t.setBoxY(o.boxY() == null ? 0 : o.boxY());
        t.setBoxW(o.boxW() == null ? 0 : o.boxW());
        t.setBoxH(o.boxH() == null ? 0 : o.boxH());
        t.setImageWidth(o.imageWidth());
        t.setImageHeight(o.imageHeight());
        t.setTrajectoryJson(o.trajectoryJson());
        t.setTrackState(o.trackState());
        t.setSeverity(o.severity());
        t.setSeveritySource(o.severity() == null ? null : "EDGE");
        t.setCapturedAt(toLocal(o.firstSeenTs()));
        return t;
    }

    private void updateTrack(DetectionTrack t, TrackObservation o) {
        // 同一 episode 更新：firstSeen 取最早、lastSeen 取最新、置信度取 max；
        // bbox/图像尺寸/轨迹缺省（null）时保留已有值（部分更新不清空），__unknown__ 不覆盖已知类别。
        t.setFirstSeenTs(Math.min(t.getFirstSeenTs(), o.firstSeenTs()));
        t.setLastSeenTs(o.lastSeenTs());
        t.setConfidence(Math.max(t.getConfidence(), o.confidence()));
        if (!TrackClassCatalog.UNKNOWN.equals(o.category()) || TrackClassCatalog.UNKNOWN.equals(t.getCategory())) {
            t.setCategory(o.category());
        }
        if (o.boxX() != null) {
            t.setBoxX(o.boxX());
        }
        if (o.boxY() != null) {
            t.setBoxY(o.boxY());
        }
        if (o.boxW() != null) {
            t.setBoxW(o.boxW());
        }
        if (o.boxH() != null) {
            t.setBoxH(o.boxH());
        }
        if (o.imageWidth() != null) {
            t.setImageWidth(o.imageWidth());
        }
        if (o.imageHeight() != null) {
            t.setImageHeight(o.imageHeight());
        }
        if (o.trajectoryJson() != null) {
            t.setTrajectoryJson(o.trajectoryJson());
        }
        t.setTrackState(o.trackState());
        if (o.severity() != null) {
            t.setSeverity(o.severity());
            t.setSeveritySource("EDGE");
        }
        t.setCapturedAt(toLocal(t.getFirstSeenTs()));
    }

    private static LocalDateTime toLocal(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZONE);
    }
}
