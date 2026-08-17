package com.ppdv.api.risk.domain;

import com.ppdv.api.system.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coer_risk_config")
@Getter
@Setter
public class RiskConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String configKey;
    private String configValue;
    private String category;
    private String description;
    private Integer sortOrder;
}
