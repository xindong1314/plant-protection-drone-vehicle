package com.ppdv.api.greenhouse.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.dto.StartRouteRecordingRequest;
import com.ppdv.api.greenhouse.service.AreaService;
import com.ppdv.api.greenhouse.service.RouteRecordingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ppdv/area")
public class AreaController extends BaseController {

    private final AreaService service;
    private final RouteRecordingService routeRecordingService;

    public AreaController(AreaService service, RouteRecordingService routeRecordingService) {
        this.service = service;
        this.routeRecordingService = routeRecordingService;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(service.list());
    }

    /** 区域详情：标准 REST 详情形态，按接口清理审计决议保留契约。 */
    @PreAuthorize("@ss.hasPermi('ppdv:area:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/{id}/route")
    public AjaxResult getRoute(@PathVariable Long id) {
        // v1.1 评审 M1：列表不再下发 routeJson，规划/展示按 id 拉取完整路线。
        return AjaxResult.success(service.getRoute(id));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/routes")
    public AjaxResult listRoutes() {
        // 评审修复#11：区域页地图一次批量拉取全部已录路线，替代逐区域的并行 N+1 请求。
        return AjaxResult.success(service.listRoutes());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:add')")
    @PostMapping
    public AjaxResult add(@RequestBody GreenhouseArea area) {
        area.setCreateBy(username());
        return AjaxResult.success(service.create(area));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody GreenhouseArea area) {
        area.setUpdateBy(username());
        return AjaxResult.success(service.update(area));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        // v1.1 评审修复#5：先删区域（存在关联数据时 409 拒绝，录制会话原样保留），
        // 删除成功后再幂等取消其录制会话；先取消的话删除被拒会不可逆销毁已录的路线点。
        service.delete(id);
        routeRecordingService.cancel(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:edit')")
    @PostMapping("/{id}/route/recording")
    public AjaxResult startRouteRecording(@PathVariable Long id,
                                          @Valid @RequestBody StartRouteRecordingRequest request) {
        routeRecordingService.start(id, request.deviceId());
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:edit')")
    @PostMapping("/{id}/route/recording/stop")
    public AjaxResult stopRouteRecording(@PathVariable Long id) {
        int pointCount = routeRecordingService.stop(id);
        return AjaxResult.success(Map.of("areaId", id, "pointCount", pointCount));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:edit')")
    @DeleteMapping("/{id}/route/recording")
    public AjaxResult cancelRouteRecording(@PathVariable Long id) {
        routeRecordingService.cancel(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/route/recording")
    public AjaxResult routeRecordingStatus() {
        return AjaxResult.success(routeRecordingService.status());
    }
}
