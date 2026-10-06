package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.dto.HardwareDataRequest;
import com.riverbank.entity.*;
import com.riverbank.mapper.*;
import com.riverbank.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final DeviceMapper deviceMapper;
    private final WaterDataMapper waterDataMapper;
    private final IntrusionRecordMapper intrusionRecordMapper;
    private final AlertEventMapper alertEventMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void processHardwareData(HardwareDataRequest request) {
        String deviceId = request.getDeviceId();
        Device device = deviceMapper.selectById(deviceId);

        // Auto-register unknown device
        if (device == null) {
            device = new Device();
            device.setId(deviceId);
            device.setLocation("待设置");
            device.setStatus(1);
            device.setLastHeartbeat(LocalDateTime.now());
            deviceMapper.insert(device);
            log.info("Auto-registered new device: {}", deviceId);
        }

        // Update device online status and heartbeat
        device.setStatus(1);
        device.setLastHeartbeat(LocalDateTime.now());
        deviceMapper.updateById(device);

        // Save water level data (skip if not provided, e.g. camera-only mode)
        if (request.getWaterLevel() != null) {
            WaterData waterData = new WaterData();
            waterData.setDeviceId(deviceId);
            waterData.setWaterLevel(request.getWaterLevel());
            waterData.setBatteryVoltage(request.getBatteryVoltage());
            waterData.setCollectTime(LocalDateTime.now());
            waterDataMapper.insert(waterData);
        }

        // Save intrusion record if detected
        if (request.getHumanDetected() != null && request.getHumanDetected()) {
            IntrusionRecord intrusion = new IntrusionRecord();
            intrusion.setDeviceId(deviceId);
            intrusion.setHumanDetected(1);
            intrusion.setDetectTime(LocalDateTime.now());
            intrusionRecordMapper.insert(intrusion);
        }

        // Check alarm conditions
        BigDecimal threshold = device.getWaterThreshold();
        boolean waterAlarm = threshold != null && request.getWaterLevel() != null
                && request.getWaterLevel().compareTo(threshold) > 0;
        boolean intrusionAlarm = request.getHumanDetected() != null && request.getHumanDetected();

        if (waterAlarm || intrusionAlarm) {
            AlertEvent alert = new AlertEvent();
            alert.setDeviceId(deviceId);
            alert.setAlertTime(LocalDateTime.now());
            alert.setIsHandled(0);

            if (waterAlarm) {
                alert.setAlertType("WATER_ALARM");
                alert.setWaterLevel(request.getWaterLevel());
                log.info("Water alarm triggered: device={}, level={}cm, threshold={}cm",
                        deviceId, request.getWaterLevel(), threshold);
            } else {
                alert.setAlertType("INTRUSION_ALARM");
                log.info("Intrusion alarm triggered: device={}", deviceId);
            }

            alertEventMapper.insert(alert);

            // Push alert via WebSocket
            Map<String, Object> alertMsg = new HashMap<>();
            alertMsg.put("id", alert.getId());
            alertMsg.put("deviceId", deviceId);
            alertMsg.put("deviceLocation", device.getLocation());
            alertMsg.put("alertType", alert.getAlertType());
            alertMsg.put("alertTime", alert.getAlertTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            alertMsg.put("waterLevel", request.getWaterLevel());
            alertMsg.put("humanDetected", request.getHumanDetected());

            messagingTemplate.convertAndSend("/topic/alert", alertMsg);
        }

        // Push real-time data via WebSocket
        Map<String, Object> dataMsg = new HashMap<>();
        dataMsg.put("deviceId", deviceId);
        dataMsg.put("deviceLocation", device.getLocation());
        dataMsg.put("waterLevel", request.getWaterLevel());
        dataMsg.put("humanDetected", request.getHumanDetected());
        dataMsg.put("batteryVoltage", request.getBatteryVoltage());
        dataMsg.put("collectTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        dataMsg.put("deviceStatus", 1);

        messagingTemplate.convertAndSend("/topic/data", dataMsg);
    }

    @Override
    public Page<AlertEvent> listAlerts(String status, String deviceId, int page, int size) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AlertEvent::getIsHandled, "handled".equals(status) ? 1 : 0);
        }
        if (deviceId != null && !deviceId.isEmpty()) {
            wrapper.eq(AlertEvent::getDeviceId, deviceId);
        }
        wrapper.orderByDesc(AlertEvent::getAlertTime);

        Page<AlertEvent> pageResult = alertEventMapper.selectPage(new Page<>(page, size), wrapper);

        // Fill device location and handler name
        pageResult.getRecords().forEach(alert -> {
            Device device = deviceMapper.selectById(alert.getDeviceId());
            if (device != null) {
                alert.setDeviceLocation(device.getLocation());
            }
        });

        return pageResult;
    }

    @Override
    public void handleAlert(Long alertId, Integer handlerId, String handleNote) {
        AlertEvent alert = alertEventMapper.selectById(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("预警记录不存在");
        }
        alert.setIsHandled(1);
        alert.setHandlerId(handlerId);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleNote(handleNote);
        alertEventMapper.updateById(alert);
    }

    @Override
    public long countUnhandled() {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getIsHandled, 0);
        return alertEventMapper.selectCount(wrapper);
    }

    @Override
    public void deleteAlert(Long alertId) {
        alertEventMapper.deleteById(alertId);
    }

    @Override
    public void clearAlerts(String status) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if ("handled".equals(status)) {
            wrapper.eq(AlertEvent::getIsHandled, 1);
        } else if ("unhandled".equals(status)) {
            wrapper.eq(AlertEvent::getIsHandled, 0);
        }
        alertEventMapper.delete(wrapper);
    }
}
