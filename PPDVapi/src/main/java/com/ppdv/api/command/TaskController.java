package com.ppdv.api.command;

import com.ppdv.api.common.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 作业任务下发（航线/地块/参数），发布到 ppdv/v1/{deviceId}/task/assign。
 */
@RestController
@RequestMapping("/ppdv/device/{deviceId}/task")
public class TaskController {

    private final CommandService service;

    public TaskController(CommandService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:command')")
    @PostMapping
    public AjaxResult assign(@PathVariable String deviceId, @RequestBody Map<String, Object> task) {
        String msgId = service.sendTask(deviceId, task);
        return AjaxResult.success("任务已下发", msgId);
    }
}
