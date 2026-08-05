package com.ppdv.api.archive.controller;

import com.ppdv.api.archive.service.ArchiveService;
import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.web.BaseController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ppdv/archive")
public class ArchiveController extends BaseController {

    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:archive:query')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(service.listAll());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:archive:query')")
    @GetMapping("/map")
    public AjaxResult digitalMap() {
        return AjaxResult.success(service.getDigitalMap());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:archive:query')")
    @GetMapping("/{areaId}")
    public AjaxResult getByArea(@PathVariable Long areaId) {
        return AjaxResult.success(service.getByArea(areaId));
    }
}
