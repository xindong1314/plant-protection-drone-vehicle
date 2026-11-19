package com.ppdv.api.greenhouse.dto;

import java.time.LocalDateTime;

/**
 * 区域列表摘要（v1.1 评审 M1）：列表不下发完整 routeJson（单条最大 512KB），
 * 只携带路线元信息；需要完整路线时按 id 调 GET /ppdv/area/{id}/route 拉取。
 */
public interface AreaSummary {

    Long getId();

    String getAreaCode();

    String getAreaName();

    String getGreenhouseName();

    String getArucoId();

    String getRemark();

    String getBoundaryJson();

    String getCoordinateSystem();

    Integer getSortOrder();

    String getRiskStatus();

    Integer getRiskScore();

    LocalDateTime getLastEvalAt();

    /** 已录路线点数（读 route_point_count 冗余列，无路线为 0）。 */
    Integer getRoutePointCount();

    LocalDateTime getRouteRecordedAt();

    /** 路线是否可用于下发（点数 ≥ 2）。 */
    default boolean getHasRoute() {
        return getRoutePointCount() != null && getRoutePointCount() >= 2;
    }
}
