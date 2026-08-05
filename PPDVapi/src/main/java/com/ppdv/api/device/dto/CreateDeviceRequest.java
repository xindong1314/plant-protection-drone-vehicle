package com.ppdv.api.device.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceRequest(
        @NotBlank(message = "deviceId 不能为空") String deviceId,
        String name,
        String model
) {
}
