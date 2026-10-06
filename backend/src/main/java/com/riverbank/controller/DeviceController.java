package com.riverbank.controller;

import com.riverbank.common.Result;
import com.riverbank.dto.ThresholdRequest;
import com.riverbank.entity.Device;
import com.riverbank.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public Result<List<Device>> listAll() {
        return Result.success(deviceService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable String id) {
        return Result.success(deviceService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public Result<?> add(@Valid @RequestBody Device device) {
        deviceService.add(device);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<?> update(@PathVariable String id, @RequestBody Device device) {
        device.setId(id);
        deviceService.update(device);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<?> delete(@PathVariable String id) {
        deviceService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/threshold")
    @PreAuthorize("hasRole('admin')")
    public Result<?> updateThreshold(@PathVariable String id, @Valid @RequestBody ThresholdRequest request) {
        deviceService.updateThreshold(id, request.getWaterThreshold());
        return Result.success();
    }
}
