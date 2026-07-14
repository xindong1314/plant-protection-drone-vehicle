package com.ppdv.api.device;

import com.ppdv.api.common.ApiResponse;
import com.ppdv.api.device.dto.CreateDeviceRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Device>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<Device> get(@PathVariable String deviceId) {
        return ApiResponse.ok(service.get(deviceId));
    }

    @PostMapping
    public ApiResponse<Device> create(@Valid @RequestBody CreateDeviceRequest req) {
        return ApiResponse.ok(service.create(req));
    }
}
