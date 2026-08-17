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
@Table(name = "coer_review_record")
@Getter
@Setter
public class ReviewRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sprayTaskId;
    private Long areaId;
    private Long beforeImageId;
    private Long afterImageId;
    private String result;
    private LocalDateTime reviewTime;
}
