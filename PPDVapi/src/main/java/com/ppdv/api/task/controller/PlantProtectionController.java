package com.ppdv.api.task.controller;

import com.ppdv.api.command.CommandService;
import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.task.domain.ReviewRecord;
import com.ppdv.api.task.domain.SprayTask;
import com.ppdv.api.task.service.PlantProtectionService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ppdv/task")
public class PlantProtectionController extends BaseController {

    private final PlantProtectionService service;
    private final CommandService commandService;

    public PlantProtectionController(PlantProtectionService service, CommandService commandService) {
        this.service = service;
        this.commandService = commandService;
    }

    // ---- 点喷任务 ----

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @GetMapping("/spray/list")
    public TableDataInfo sprayList(PageQuery pageQuery) {
        Page<SprayTask> page = service.listSprayTasks(pageQuery.toPageable());
        return getDataTable(page);
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @GetMapping("/spray/{id}")
    public AjaxResult getSprayTask(@PathVariable Long id) {
        return AjaxResult.success(service.getSprayTask(id));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @GetMapping("/spray/pending")
    public AjaxResult pendingSprayTasks() {
        return AjaxResult.success(service.getPendingSprayTasks());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:add')")
    @PostMapping("/spray")
    public AjaxResult createSprayTask(@RequestBody SprayTask task) {
        task.setCreateBy(username());
        return AjaxResult.success("点喷任务已创建", service.createSprayTask(task));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:add')")
    @PutMapping("/spray/{id}/assign")
    public AjaxResult assignSprayTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        return AjaxResult.success(service.assignSprayTask(id, deviceId));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @PutMapping("/spray/{id}/status")
    public AjaxResult updateSprayStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return AjaxResult.success(service.updateSprayTaskStatus(id, body.get("status")));
    }

    /** 执行点喷：更新任务为 IN_PROGRESS，通过 MQTT 下发 spray 指令到小车。 */
    @PreAuthorize("@ss.hasPermi('ppdv:spray:add')")
    @PutMapping("/spray/{id}/execute")
    public AjaxResult executeSpray(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SprayTask task = service.updateSprayTaskStatus(id, "IN_PROGRESS");
        String action = (String) body.getOrDefault("action", "start");
        Integer duration = body.get("duration") != null ? ((Number) body.get("duration")).intValue() : null;
        String msgId = commandService.sendSpray(task.getDeviceId(), action,
                body.get("areaCode") != null ? (String) body.get("areaCode") : null, duration);
        return AjaxResult.success("点喷指令已下发", msgId);
    }

    // ---- 复查记录 ----

    @PreAuthorize("@ss.hasPermi('ppdv:review:add')")
    @PostMapping("/review/{sprayTaskId}")
    public AjaxResult createReview(@PathVariable Long sprayTaskId, @RequestBody ReviewRecord review) {
        review.setSprayTaskId(sprayTaskId);
        review.setCreateBy(username());
        return AjaxResult.success(service.createReview(review));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @GetMapping("/review/spray/{sprayTaskId}")
    public AjaxResult getReviewsBySprayTask(@PathVariable Long sprayTaskId) {
        return AjaxResult.success(service.getReviewsBySprayTask(sprayTaskId));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:spray:query')")
    @GetMapping("/review/area/{areaId}")
    public AjaxResult getReviewsByArea(@PathVariable Long areaId) {
        return AjaxResult.success(service.getReviewsByArea(areaId));
    }
}
