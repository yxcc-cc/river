package com.riverbank.controller;

import com.riverbank.service.SensorPollingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 传感器轮询管理接口
 */
@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final SensorPollingService sensorPollingService;

    /**
     * 查看当前监控的传感器列表
     */
    @GetMapping("/list")
    public Map<String, Object> listSensors() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", sensorPollingService.getSensors());
        return result;
    }

    /**
     * 添加或更新传感器端点
     * POST /api/sensor/config {"deviceId":"2462ABEFC95C","endpoint":"http://10.234.223.69/water"}
     */
    @PostMapping("/config")
    public Map<String, Object> configSensor(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        String endpoint = body.get("endpoint");
        sensorPollingService.addSensor(deviceId, endpoint);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "已更新: " + deviceId + " → " + endpoint);
        return result;
    }
}
