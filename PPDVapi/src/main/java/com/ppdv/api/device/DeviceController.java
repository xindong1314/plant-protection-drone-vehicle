package com.ppdv.api.device;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.device.dto.CreateDeviceRequest;
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

/**
 * 无人车设备管理（对齐若依契约）。
 */
@RestController
@RequestMapping("/ppdv/device")
public class DeviceController extends BaseController {

    private final DeviceService service;
    private final RouteRecordingService routeRecordingService;

    public DeviceController(DeviceService service, RouteRecordingService routeRecordingService) {
        this.service = service;
        this.routeRecordingService = routeRecordingService;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:list')")
    @GetMapping("/list")
    public TableDataInfo list() {
        return getDataTable(service.list());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:query')")
    @GetMapping("/{deviceId}")
    public AjaxResult getInfo(@PathVariable String deviceId) {
        return AjaxResult.success(service.get(deviceId));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:add')")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody CreateDeviceRequest req) {
        return AjaxResult.success(service.create(req));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:remove')")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        // v1.1 评审 S4：删除设备前联动取消其路线录制会话，避免删除后设备被会话永久占用。
        routeRecordingService.cancelForDevices(service.findDeviceIdsByIds(ids));
        service.deleteByIds(ids);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:edit')")
    @PutMapping("/{deviceId}/resetSecret")
    public AjaxResult resetSecret(@PathVariable String deviceId) {
        return AjaxResult.success("密钥已重置", service.resetSecret(deviceId));
    }
}
