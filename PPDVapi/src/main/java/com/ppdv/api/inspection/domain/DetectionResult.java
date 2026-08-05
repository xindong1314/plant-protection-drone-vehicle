package com.ppdv.api.inspection.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coer_detection_result")
@Getter
@Setter
public class DetectionResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long imageId;
    private Long areaId;
    private String category;
    private Double confidence;
    @Column(name = "box_x")
    private Integer boxX;

    @Column(name = "box_y")
    private Integer boxY;

    @Column(name = "box_w")
    private Integer boxW;

    @Column(name = "box_h")
    private Integer boxH;
    private String status;
}
