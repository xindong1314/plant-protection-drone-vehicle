package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysConfig;
import com.ppdv.api.system.service.SysConfigService;
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
@RequestMapping("/system/config")
public class SysConfigController extends BaseController {

    private final SysConfigService configService;

    public SysConfigController(SysConfigService configService) {
        this.configService = configService;
    }

    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysConfig filter, PageQuery pageQuery) {
        return getDataTable(configService.selectPage(filter, pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('system:config:query')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId) {
        return AjaxResult.success(configService.selectById(configId));
    }

    @GetMapping("/configKey/{configKey}")
    public AjaxResult getConfigKey(@PathVariable String configKey) {
        return AjaxResult.success(configService.selectConfigByKey(configKey));
    }

    @PreAuthorize("@ss.hasPermi('system:config:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysConfig config) {
        if (!configService.checkConfigKeyUnique(config)) {
            return AjaxResult.error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(username());
        configService.insert(config);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysConfig config) {
        config.setUpdateBy(username());
        configService.update(config);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds) {
        configService.deleteByIds(configIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache() {
        return AjaxResult.success();
    }
}
