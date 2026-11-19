package com.ppdv.api.greenhouse.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Entity
@Table(name = "coer_greenhouse_area")
// v1.1 评审 S6：只更新变化列，避免风险状态等局部更新把 route_json 整行写回旧快照。
@DynamicUpdate
@Getter
@Setter
public class GreenhouseArea extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String areaCode;
    private String areaName;
    private String greenhouseName;
    private String arucoId;

    @Column(name = "boundary_json")
    private String boundaryJson;

    private String coordinateSystem;

    @Column(name = "route_json")
    private String routeJson;

    /** 路线点数冗余列：由 saveRecordedRoute 落库时维护，列表摘要无需对 route_json 逐行 JSON_LENGTH。 */
    @Column(name = "route_point_count")
    private Integer routePointCount;

    private LocalDateTime routeRecordedAt;

    private String riskStatus;
    private Integer riskScore;
    private LocalDateTime lastEvalAt;
    private Integer sortOrder;
}
