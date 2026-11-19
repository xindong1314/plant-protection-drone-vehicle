package com.ppdv.api.track.dto;

import com.ppdv.api.track.domain.DetectionTrack;

import java.util.List;

/** 会话详情：会话摘要 + 该会话全部 track。 */
public record TrackSessionDetail(TrackSessionSummary summary, List<DetectionTrack> tracks) {
}
