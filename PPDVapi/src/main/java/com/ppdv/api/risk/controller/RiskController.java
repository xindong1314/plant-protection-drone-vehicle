package com.ppdv.api.risk.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.risk.service.RiskEvaluationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ppdv/risk")
public class RiskController extends BaseController {

    private final RiskEvaluationService service;

    public RiskController(RiskEvaluationService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:risk:evaluate')")
    @PostMapping("/evaluate/{areaId}")
    public AjaxResult evaluate(@PathVariable Long areaId) {
        return AjaxResult.success(service.evaluate(areaId));
    }
}
