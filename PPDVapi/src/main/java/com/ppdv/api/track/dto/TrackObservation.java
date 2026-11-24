package com.ppdv.api.track.dto;

/** 单条跟踪观测（车端 tracks 数组中已校验的一条，对应一次出现 episode）；bbox/图像尺寸缺失时为 null（不覆盖已有值）。 */
public record TrackObservation(
        String trackId,
        String episodeId,
        Integer episodeNo,
        String category,
        double confidence,
        long firstSeenTs,
        long lastSeenTs,
        Integer boxX,
        Integer boxY,
        Integer boxW,
        Integer boxH,
        Integer imageWidth,
        Integer imageHeight,
        String trajectoryJson,
        String trackState,
        Integer severity) {
}
