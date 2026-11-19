package com.ppdv.api.greenhouse.dto;

import jakarta.validation.constraints.NotBlank;

public record StartRouteRecordingRequest(
        @NotBlank(message = "deviceId 不能为空") String deviceId
) {
}
