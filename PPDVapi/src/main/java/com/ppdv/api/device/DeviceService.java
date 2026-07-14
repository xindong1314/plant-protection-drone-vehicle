package com.ppdv.api.device;

import com.ppdv.api.device.dto.CreateDeviceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository repository;

    public DeviceService(DeviceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Device> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Device get(String deviceId) {
        return repository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
    }

    @Transactional
    public Device create(CreateDeviceRequest req) {
        if (repository.existsByDeviceId(req.deviceId())) {
            throw new IllegalStateException("设备已存在: " + req.deviceId());
        }
        Device d = new Device();
        d.setDeviceId(req.deviceId());
        d.setName(req.name());
        d.setModel(req.model());
        return repository.save(d);
    }

    /** 遥测入库：设备不存在时自动登记（IoT 常见做法）。 */
    @Transactional
    public Device ensure(String deviceId) {
        return repository.findByDeviceId(deviceId).orElseGet(() -> {
            Device d = new Device();
            d.setDeviceId(deviceId);
            d.setName(deviceId);
            return repository.save(d);
        });
    }

    @Transactional
    public void markOnline(String deviceId, boolean online, String firmware) {
        Device d = ensure(deviceId);
        d.setStatus(online ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE);
        if (firmware != null) {
            d.setFirmware(firmware);
        }
        d.setLastSeenAt(Instant.now());
        repository.save(d);
    }

    @Transactional
    public void updateState(String deviceId, Double lat, Double lon, Integer battery, String mode) {
        Device d = ensure(deviceId);
        d.setStatus(DeviceStatus.ONLINE);
        d.setLastSeenAt(Instant.now());
        if (lat != null) {
            d.setLastLat(lat);
        }
        if (lon != null) {
            d.setLastLon(lon);
        }
        if (battery != null) {
            d.setBattery(battery);
        }
        if (mode != null) {
            d.setMode(mode);
        }
        repository.save(d);
    }
}
