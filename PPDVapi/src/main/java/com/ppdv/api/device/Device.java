package com.ppdv.api.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 无人车设备。除基础台账外，同时缓存最近一次遥测快照，供实时监控查询。
 */
@Entity
@Table(name = "device")
@Getter
@Setter
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String deviceId;

    private String name;

    private String model;

    @Enumerated(EnumType.STRING)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    private String firmware;

    // 最近遥测快照
    private Instant lastSeenAt;
    private Double lastLat;
    private Double lastLon;
    private Integer battery;
    private String mode;

    private Instant createdAt = Instant.now();
}
