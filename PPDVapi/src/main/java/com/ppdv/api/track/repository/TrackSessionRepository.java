package com.ppdv.api.track.repository;

import com.ppdv.api.track.domain.TrackSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrackSessionRepository extends JpaRepository<TrackSession, Long>, JpaSpecificationExecutor<TrackSession> {

    Optional<TrackSession> findByDeviceIdAndSessionKey(String deviceId, String sessionKey);

    /** 会话空闲自动结束：取全部进行中会话逐个判定空闲。 */
    List<TrackSession> findByStatus(String status);

    /**
     * 空闲清扫落库用条件更新：仅当仍是 ACTIVE 才置 ENDED（end_reason=idle、end_ts=服务器侧最后活动时间），
     * 并同步刷新 update_time（批量更新不经过 {@code @PreUpdate}）。已被并发 apply 写走时放弃，不做 last-commit-wins 覆盖。
     *
     * @return 实际更新的行数（0 = 已被并发写走，放弃本次结束）
     */
    @Modifying
    @Query("update TrackSession s set s.status = 'ENDED', s.endTs = :endTs, s.endReason = 'idle', "
            + "s.updateTime = :updateTime where s.id = :id and s.status = 'ACTIVE'")
    int endIdleSession(@Param("id") Long id, @Param("endTs") Long endTs,
                       @Param("updateTime") LocalDateTime updateTime);
}
