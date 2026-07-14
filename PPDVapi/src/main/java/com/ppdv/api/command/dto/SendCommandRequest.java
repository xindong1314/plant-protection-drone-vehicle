package com.ppdv.api.command.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 下发控制指令。cmd 见 docs/protocol.md 6.1：START|PAUSE|RESUME|STOP|ESTOP|RTL|SET_MODE
 */
public record SendCommandRequest(
        @NotBlank(message = "cmd 不能为空") String cmd,
        Map<String, Object> args
) {
}
