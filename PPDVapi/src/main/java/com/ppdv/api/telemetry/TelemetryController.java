package com.ppdv.api.telemetry;

import com.ppdv.api.common.AjaxResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 遥测历史查询（TDengine）。未启用时序库时返回空列表。
 */
@RestController
@RequestMapping("/ppdv/device/{deviceId}/telemetry")
public class TelemetryController {

    private final ObjectProvider<TelemetryTsService> tsServiceProvider;

    public TelemetryController(ObjectProvider<TelemetryTsService> tsServiceProvider) {
        this.tsServiceProvider = tsServiceProvider;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:query')")
    @GetMapping("/state")
    public AjaxResult stateHistory(@PathVariable String deviceId,
                                   @RequestParam(defaultValue = "200") int limit) {
        TelemetryTsService ts = tsServiceProvider.getIfAvailable();
        if (ts == null) {
            return AjaxResult.success(List.of());
        }
        return AjaxResult.success(ts.queryStateHistory(deviceId, limit));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:device:query')")
    @GetMapping("/env")
    public AjaxResult envHistory(@PathVariable String deviceId,
                                 @RequestParam(defaultValue = "200") int limit) {
        TelemetryTsService ts = tsServiceProvider.getIfAvailable();
        if (ts == null) {
            return AjaxResult.success(List.of());
        }
        return AjaxResult.success(ts.queryEnvHistory(deviceId, limit));
    }
}
