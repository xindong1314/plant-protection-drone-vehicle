package com.ppdv.api.common.web;

import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.security.SecurityUtils;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 控制器基类：分页包装与当前登录人。
 */
public abstract class BaseController {

    protected TableDataInfo getDataTable(Page<?> page) {
        return new TableDataInfo(page.getContent(), page.getTotalElements());
    }

    protected TableDataInfo getDataTable(List<?> list) {
        return new TableDataInfo(list, list.size());
    }

    protected String username() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
