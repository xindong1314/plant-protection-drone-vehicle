package com.ppdv.api.greenhouse.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.greenhouse.domain.GreenhouseArea;
import com.ppdv.api.greenhouse.service.AreaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ppdv/area")
public class AreaController extends BaseController {

    private final AreaService service;

    public AreaController(AreaService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        return AjaxResult.success(service.list());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:list')")
    @GetMapping("/map")
    public AjaxResult map() {
        return AjaxResult.success(service.list());
    }

    @PreAuthorize("@ss.hasPermi('ppdv:area:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(service.get(id));
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
        service.delete(id);
        return AjaxResult.success();
    }
}
