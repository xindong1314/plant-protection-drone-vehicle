package com.ppdv.api.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ExecuteSprayRequest(
        @NotBlank(message = "点喷动作不能为空")
        @Pattern(regexp = "start", message = "执行点喷仅支持 start 动作")
        String action,

        @NotNull(message = "喷洒时长不能为空")
        @Min(value = 1, message = "喷洒时长不能小于 1 秒")
        @Max(value = 3600, message = "喷洒时长不能超过 3600 秒")
        Integer duration
) {
}
