package com.ppdv.api.track.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 跟踪会话列表行：出现次数/去重目标数/类别/最高置信度由会话 id 批量聚合得出。 */
public record TrackSessionSummary(
        Long id,
        String deviceId,
        Long areaId,
        String sessionKey,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        int episodeCount,
        long distinctTrackCount,
        List<String> categories,
        double maxConfidence) {
}
