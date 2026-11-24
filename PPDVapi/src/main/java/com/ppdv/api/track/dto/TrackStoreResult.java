package com.ppdv.api.track.dto;

import java.util.List;

/** 一次落库结果，供事务提交后的 WS 推送与影子分调度使用；episodeCount 为会话累计出现行数（与查询口径一致）。 */
public record TrackStoreResult(
        String sessionKey,
        Long areaId,
        String status,
        int episodeCount,
        List<String> categories,
        double maxConfidence) {

    public boolean ended() {
        return "ENDED".equals(status);
    }
}
