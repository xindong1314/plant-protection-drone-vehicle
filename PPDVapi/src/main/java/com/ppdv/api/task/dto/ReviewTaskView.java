package com.ppdv.api.task.dto;

import java.time.LocalDateTime;

public record ReviewTaskView(
        Long sprayTaskId,
        Long areaId,
        LocalDateTime completedAt,
        LocalDateTime reviewTime,
        String result,
        Long beforeImageId,
        Long afterImageId
) {
}
