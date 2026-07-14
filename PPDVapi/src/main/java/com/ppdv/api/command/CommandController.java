package com.ppdv.api.command;

import com.ppdv.api.command.dto.SendCommandRequest;
import com.ppdv.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/devices/{deviceId}/commands")
public class CommandController {

    private final CommandService service;

    public CommandController(CommandService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, String>> send(@PathVariable String deviceId,
                                                 @Valid @RequestBody SendCommandRequest req) {
        String msgId = service.sendCommand(deviceId, req.cmd(), req.args());
        return ApiResponse.ok(Map.of("msgId", msgId));
    }
}
