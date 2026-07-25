package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysDictData;
import com.ppdv.api.system.service.SysDictDataService;
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
@RequestMapping("/system/dict/data")
public class SysDictDataController extends BaseController {

    private final SysDictDataService dictDataService;

    public SysDictDataController(SysDictDataService dictDataService) {
        this.dictDataService = dictDataService;
    }

    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysDictData filter, PageQuery pageQuery) {
        return getDataTable(dictDataService.selectPage(filter, pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    @GetMapping("/{dictCode}")
    public AjaxResult getInfo(@PathVariable Long dictCode) {
        return AjaxResult.success(dictDataService.selectByCode(dictCode));
    }

    /** 供前端 useDict 按类型取字典项 */
    @GetMapping("/type/{dictType}")
    public AjaxResult dictType(@PathVariable String dictType) {
        return AjaxResult.success(dictDataService.selectByType(dictType));
    }

    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysDictData data) {
        data.setCreateBy(username());
        dictDataService.insert(data);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysDictData data) {
        data.setUpdateBy(username());
        dictDataService.update(data);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @DeleteMapping("/{dictCodes}")
    public AjaxResult remove(@PathVariable Long[] dictCodes) {
        dictDataService.deleteByCodes(dictCodes);
        return AjaxResult.success();
    }
}
