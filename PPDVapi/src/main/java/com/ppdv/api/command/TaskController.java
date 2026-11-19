package com.ppdv.api.command;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.greenhouse.service.RouteRecordingService;
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
    private final RouteRecordingService routeRecordingService;

    public TaskController(CommandService service, RouteRecordingService routeRecordingService) {
        this.service = service;
        this.routeRecordingService = routeRecordingService;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:command')")
    @PostMapping
    public AjaxResult assign(@PathVariable String deviceId, @RequestBody Map<String, Object> task) {
        // v1.1 评审 L4：正在录制路线的设备不允许下发任务，避免任务遥测污染当次录制。
        if (routeRecordingService.hasActiveSessionForDevice(deviceId)) {
            throw new IllegalStateException("设备正在录制路线，不能下发任务: " + deviceId);
        }
        String msgId = service.sendTask(deviceId, task);
        return AjaxResult.success("任务已下发", msgId);
    }
}
