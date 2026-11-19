package com.ppdv.api.track.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.track.dto.TrackSessionDetail;
import com.ppdv.api.track.service.TrackClassCatalog;
import com.ppdv.api.track.service.TrackQueryService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跟踪记录查询（P1）：会话列表/详情、类别目录。
 */
@RestController
@RequestMapping("/ppdv/track")
public class TrackController extends BaseController {

    private final TrackQueryService service;

    public TrackController(TrackQueryService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:track:query')")
    @GetMapping("/sessions")
    public TableDataInfo sessions(@RequestParam(required = false) Long areaId,
                                  @RequestParam(required = false) Long from,
                                  @RequestParam(required = false) Long to,
                                  @RequestParam(required = false) String deviceId,
                                  @RequestParam(required = false) String category,
                                  PageQuery pageQuery) {
        Page<?> page = service.listSessions(areaId, from, to, deviceId, category, pageQuery.toPageable());
        return getDataTable(page);
    }

    @PreAuthorize("@ss.hasPermi('ppdv:track:query')")
    @GetMapping("/sessions/{id}")
    public AjaxResult sessionInfo(@PathVariable Long id) {
        TrackSessionDetail detail = service.getSession(id);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("session", detail.summary());
        ajax.put("tracks", detail.tracks());
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:track:query')")
    @GetMapping("/classes")
    public AjaxResult classes() {
        return AjaxResult.success(TrackClassCatalog.classes());
    }
}
