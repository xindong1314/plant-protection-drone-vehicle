package com.ppdv.api.command;

import com.ppdv.api.command.dto.SendCommandRequest;
import com.ppdv.api.common.AjaxResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下行指令（对齐若依契约）。
 */
@RestController
@RequestMapping("/ppdv/device/{deviceId}/command")
public class CommandController {

    private final CommandService service;

    public CommandController(CommandService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:command')")
    @PostMapping
    public AjaxResult send(@PathVariable String deviceId, @Valid @RequestBody SendCommandRequest req) {
        String msgId = service.sendCommand(deviceId, req.cmd(), req.args());
        return AjaxResult.success("指令已下发", msgId);
    }
}
