package com.ppdv.api.inspection.domain;

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
@Table(name = "coer_inspection_record")
@Getter
@Setter
public class InspectionRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private Long areaId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer imageCount;
    private Integer anomalyCount;
    private String status;
}
