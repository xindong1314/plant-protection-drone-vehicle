package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.security.PermissionService;
import com.ppdv.api.security.SecurityUtils;
import com.ppdv.api.system.domain.SysNotice;
import com.ppdv.api.system.domain.vo.SysNoticeReadVo;
import com.ppdv.api.system.service.SysNoticeService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知公告。管理端接口挂 system:notice:* 权限；头部铃铛/详情/已读标记对所有登录用户开放。
 */
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController {

    private final SysNoticeService noticeService;
    private final PermissionService permissionService;

    public SysNoticeController(SysNoticeService noticeService, PermissionService permissionService) {
        this.noticeService = noticeService;
        this.permissionService = permissionService;
    }

    /** 公告管理分页列表。 */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysNotice filter, PageQuery pageQuery) {
        return getDataTable(noticeService.selectPage(filter, pageQuery.toPageable()));
    }

    /** 头部公告列表（所有登录用户）：data 为最近 10 条，另附 unreadCount。 */
    @GetMapping("/listTop")
    public AjaxResult listTop() {
        Map<String, Object> result = noticeService.listTop(SecurityUtils.getUserId());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("data", result.get("rows"));
        ajax.put("unreadCount", result.get("unreadCount"));
        return ajax;
    }

    /**
     * 公告详情（头部详情弹窗使用）：已发布（status='0'）对所有登录用户可见；
     * 未发布公告仅持有 system:notice:list 权限的账号可见，否则按不存在处理。
     */
    @GetMapping("/{noticeId}")
    public AjaxResult getInfo(@PathVariable Long noticeId) {
        SysNotice notice = noticeService.selectById(noticeId);
        if (notice == null) {
            return AjaxResult.error("公告不存在");
        }
        if (!"0".equals(notice.getStatus()) && !permissionService.hasPermi("system:notice:list")) {
            return AjaxResult.error("公告不存在");
        }
        return AjaxResult.success(notice);
    }

    /** 标记单条已读（所有登录用户）。 */
    @PostMapping("/markRead")
    public AjaxResult markRead(@RequestParam Long noticeId) {
        noticeService.markRead(SecurityUtils.getUserId(), noticeId);
        return AjaxResult.success();
    }

    /** 批量标记已读（所有登录用户），ids 形如 1,2,3。 */
    @PostMapping("/markReadAll")
    public AjaxResult markReadAll(@RequestParam List<Long> ids) {
        noticeService.markReadAll(SecurityUtils.getUserId(), ids);
        return AjaxResult.success();
    }

    /** 公告已读用户分页列表。 */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @GetMapping("/readUsers/list")
    public TableDataInfo readUsers(@RequestParam Long noticeId, @RequestParam(required = false) String searchValue, PageQuery pageQuery) {
        Page<SysNoticeReadVo> page = noticeService.readUsers(noticeId, searchValue, pageQuery.toPageable());
        return getDataTable(page);
    }

    @PreAuthorize("@ss.hasPermi('system:notice:add')")
    @PostMapping
    public AjaxResult add(@RequestBody SysNotice notice) {
        notice.setCreateBy(username());
        noticeService.insert(notice);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:notice:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody SysNotice notice) {
        notice.setUpdateBy(username());
        noticeService.update(notice);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:notice:remove')")
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds) {
        noticeService.deleteByIds(noticeIds);
        return AjaxResult.success();
    }
}
