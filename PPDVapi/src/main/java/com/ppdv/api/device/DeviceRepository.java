package com.ppdv.api.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(String deviceId);

    boolean existsByDeviceId(String deviceId);

    boolean existsByDeviceIdAndSecret(String deviceId, String secret);
}
