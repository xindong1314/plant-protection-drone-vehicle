package com.ppdv.api.device;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.device.dto.CreateDeviceRequest;
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

    public DeviceController(DeviceService service) {
        this.service = service;
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
        service.deleteByIds(ids);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:edit')")
    @PutMapping("/{deviceId}/resetSecret")
    public AjaxResult resetSecret(@PathVariable String deviceId) {
        return AjaxResult.success("密钥已重置", service.resetSecret(deviceId));
    }
}
