package com.ppdv.api.track.service;

import com.ppdv.api.track.TrackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话空闲自动结束（见 docs/protocol.md §5.6 的会话生命周期）：车端未发 sessionEnd 时，
 * 周期清扫服务器侧最后活动时间超过 {@code ppdv.track.session-idle-minutes} 的 ACTIVE 会话，避免会话永远 ACTIVE。
 * 仅在 ppdv.track.enabled=true 时注册；复用 track-shadow 调度线程池（随容器关闭一起销毁，无需单独取消任务）。
 * 结束的会话补发 WS 事件（status=ENDED），与车端 sessionEnd 的实时可见性一致。
 */
@Component
@ConditionalOnProperty(prefix = "ppdv.track", name = "enabled", havingValue = "true")
public class TrackSessionIdleCleaner {

    private static final Logger log = LoggerFactory.getLogger(TrackSessionIdleCleaner.class);

    /** 清扫周期（秒）：空闲分钟数默认 15，分钟级周期足够。 */
    private static final long SWEEP_PERIOD_SECONDS = 60;

    private final TrackProperties props;
    private final TrackEventBroadcaster broadcaster;

    public TrackSessionIdleCleaner(TrackStoreService storeService, TrackProperties props,
                                   ScheduledExecutorService trackShadowScheduler,
                                   TrackEventBroadcaster broadcaster) {
        this.props = props;
        this.broadcaster = broadcaster;
        trackShadowScheduler.scheduleWithFixedDelay(() -> sweep(storeService),
                SWEEP_PERIOD_SECONDS, SWEEP_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void sweep(TrackStoreService storeService) {
        try {
            List<TrackStoreService.IdleEndedSession> ended = storeService.endIdleSessions(props.getSessionIdleMinutes());
            if (ended.isEmpty()) {
                return;
            }
            log.info("[track] 空闲超时自动结束会话 {} 个", ended.size());
            for (TrackStoreService.IdleEndedSession session : ended) {
                broadcaster.broadcast(session.deviceId(), session.endTs(), session.result());
            }
        } catch (Exception e) {
            // 单轮清扫失败不影响下一轮与消息消费。
            log.warn("[track] 空闲会话清扫失败: {}", e.getMessage());
        }
    }
}
