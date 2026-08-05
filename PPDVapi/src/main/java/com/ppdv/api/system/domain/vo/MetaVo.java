package com.ppdv.api.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * 路由 meta（对齐若依前端）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaVo {

    private String title;
    private String icon;
    private boolean noCache;
    private String link;

    public MetaVo() {
    }

    public MetaVo(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    public MetaVo(String title, String icon, boolean noCache) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
    }

    public MetaVo(String title, String icon, String link) {
        this.title = title;
        this.icon = icon;
        this.link = link;
    }

    public MetaVo(String title, String icon, boolean noCache, String link) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        if (StringUtils.hasText(link) && isHttp(link)) {
            this.link = link;
        }
    }

    public static boolean isHttp(String link) {
        return link != null && (link.startsWith("http://") || link.startsWith("https://"));
    }
}
