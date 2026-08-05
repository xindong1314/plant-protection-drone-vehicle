package com.ppdv.api.task.domain;

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
@Table(name = "coer_spray_task")
@Getter
@Setter
public class SprayTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private Long areaId;
    private String status;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}
