package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysDictType;
import com.ppdv.api.system.service.SysDictTypeService;
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
@RequestMapping("/system/dict/type")
public class SysDictTypeController extends BaseController {

    private final SysDictTypeService dictTypeService;

    public SysDictTypeController(SysDictTypeService dictTypeService) {
        this.dictTypeService = dictTypeService;
    }

    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysDictType filter, PageQuery pageQuery) {
        return getDataTable(dictTypeService.selectPage(filter, pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    @GetMapping("/{dictId}")
    public AjaxResult getInfo(@PathVariable Long dictId) {
        return AjaxResult.success(dictTypeService.selectById(dictId));
    }

    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        return AjaxResult.success(dictTypeService.selectAll());
    }

    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysDictType dictType) {
        if (!dictTypeService.checkDictTypeUnique(dictType)) {
            return AjaxResult.error("新增字典'" + dictType.getDictName() + "'失败，字典类型已存在");
        }
        dictType.setCreateBy(username());
        dictTypeService.insert(dictType);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysDictType dictType) {
        dictType.setUpdateBy(username());
        dictTypeService.update(dictType);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @DeleteMapping("/{dictIds}")
    public AjaxResult remove(@PathVariable Long[] dictIds) {
        dictTypeService.deleteByIds(dictIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache() {
        return AjaxResult.success();
    }
}
