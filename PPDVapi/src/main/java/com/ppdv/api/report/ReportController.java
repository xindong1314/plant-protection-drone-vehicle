package com.ppdv.api.report;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/ppdv/report")
public class ReportController extends BaseController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping
    public AjaxResult report(@RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime) {
        LocalDateTime start = startTime != null
                ? LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
        LocalDateTime end = endTime != null
                ? LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null;
        return AjaxResult.success(service.buildReport(start, end));
    }
}
