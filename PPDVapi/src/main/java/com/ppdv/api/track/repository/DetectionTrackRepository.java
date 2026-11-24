package com.ppdv.api.track.repository;

import com.ppdv.api.track.domain.DetectionTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DetectionTrackRepository extends JpaRepository<DetectionTrack, Long>, JpaSpecificationExecutor<DetectionTrack> {

    /** 身份键查询：同一 (device, session, episode) 只有一行（V14 uk_episode 兜底）。 */
    Optional<DetectionTrack> findByDeviceIdAndTrackSessionIdAndEpisodeKey(String deviceId, Long trackSessionId, String episodeKey);

    /** 会话累计出现行数（WS 摘要与查询列表同口径）。 */
    long countByTrackSessionId(Long trackSessionId);

    /** 会话列表/详情聚合：一次 GROUP BY 返回 [sessionId, count, count(distinct trackKey), max(confidence)]，不加载 trajectory_json 等大字段。 */
    @Query("select t.trackSessionId, count(t), count(distinct t.trackKey), max(t.confidence) from DetectionTrack t "
            + "where t.trackSessionId in :sessionIds group by t.trackSessionId")
    List<Object[]> findStatsBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    /** 会话类别（只取 sessionId+category 两个小列，避免加载完整实体）；按 category 字典序保证接口输出/单测稳定。 */
    @Query("select distinct t.trackSessionId, t.category from DetectionTrack t "
            + "where t.trackSessionId in :sessionIds order by t.trackSessionId, t.category")
    List<Object[]> findCategoriesBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    /** 会话详情：按 id 升序取前 N 条（Pageable 控制上限）。 */
    List<DetectionTrack> findByTrackSessionId(Long trackSessionId, Pageable pageable);

    /** 影子分查询：限定区域 + 近期窗口 [since, until] + 行数上限（until=服务器 now，排除车端超前时间戳）。 */
    Page<DetectionTrack> findByAreaIdAndLastSeenTsBetween(Long areaId, Long since, Long until, Pageable pageable);
}
