package com.ppdv.api.greenhouse.dto;

import java.time.LocalDateTime;

/**
 * 单个区域的完整作业路线（v1.1 评审 M1）：供规划页选中区域后按 id 拉取，
 * routeJson 为 [[lng,lat], ...]（CGCS2000），未录入路线时为 null。
 */
public record AreaRoute(
        Long areaId,
        String routeJson,
        LocalDateTime routeRecordedAt
) {
}
