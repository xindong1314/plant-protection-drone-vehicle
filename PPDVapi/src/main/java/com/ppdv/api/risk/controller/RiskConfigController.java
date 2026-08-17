package com.ppdv.api.risk.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.risk.service.RiskConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ppdv/risk/config")
public class RiskConfigController extends BaseController {

    private final RiskConfigService service;

    public RiskConfigController(RiskConfigService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:risk:config:query')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(service.listGrouped());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:risk:config:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:risk:config:edit')")
    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return AjaxResult.success(service.update(id, body.get("configValue")));
    }
}
