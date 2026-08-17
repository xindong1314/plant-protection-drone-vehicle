package com.ppdv.api.greenhouse.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "coer_greenhouse_area")
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
    private String riskStatus;
    private Integer riskScore;
    private LocalDateTime lastEvalAt;
    private Integer sortOrder;
}
