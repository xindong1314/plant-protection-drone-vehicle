package com.ppdv.api.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 前端路由（对齐若依 getRouters 返回结构）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouterVo {

    private String name;
    private String path;
    private boolean hidden;
    private String redirect;
    private String component;
    private String query;
    private Boolean alwaysShow;
    private MetaVo meta;
    private List<RouterVo> children;
}
