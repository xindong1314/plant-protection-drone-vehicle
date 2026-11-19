package com.ppdv.api.greenhouse.dto;

/**
 * 区域轻量摘要（评审修复#11）：数字地图聚合只需要标识与风险字段，
 * 用构造器投影避免为聚合加载含 boundary_json/route_json 大文本的全实体。
 */
public record AreaDigest(Long id, String areaCode, String areaName, String greenhouseName,
                         String arucoId, String riskStatus) {
}
