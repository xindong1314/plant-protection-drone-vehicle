package com.ppdv.api.archive.domain;

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
@Table(name = "coer_greenhouse_archive")
@Getter
@Setter
public class GreenhouseArchive extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long areaId;
    private Integer totalInspections;
    private Integer totalAnomalies;
    private Integer totalSprays;
    private LocalDateTime lastInspectionTime;
    private LocalDateTime lastAnomalyTime;
    private LocalDateTime lastSprayTime;
    private String lastRiskStatus;
}
