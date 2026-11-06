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

    /**
     * 设备没配水位阈值时的兜底值（cm）。
     * t_device.water_threshold 没有默认值，自动注册的设备会是 NULL，
     * 那样水位报警永远不会触发 —— 所以这里给一个安全默认值。
     */
    private static final BigDecimal DEFAULT_WATER_THRESHOLD = new BigDecimal("100.00");

    /**
     * 报警合并窗口（分钟）：同一设备同一类型的报警，在这个窗口内只保留一条未处理的。
     *
     * 设备 30 秒上报一次。窗口设 5 分钟的话，水位持续超标时一天会堆 288 条报警，
     * 一年就是十万条级别 —— 数据库和前端都受不了。
     * 设成 30 分钟：一天 48 条，既能看出「持续超标」，又不会把表刷爆。
     * 想要「报警没处理就一直不重复提醒」，把窗口调大到 99999 即可。
     */
    private static final long ALERT_MERGE_MINUTES = 30;

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
        // 顺便把缺失的阈值补上，否则这台设备的水位报警永远不会触发
        if (device.getWaterThreshold() == null) {
            device.setWaterThreshold(DEFAULT_WATER_THRESHOLD);
            log.info("Device {} has no water threshold, defaulting to {}cm", deviceId, DEFAULT_WATER_THRESHOLD);
        }
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
            // 报警合并：设备每 30 秒上报一次，水位持续超标时不能每 30 秒建一条报警，
            // 否则一小时就是 120 条，数据库和前端都会被刷爆。
            // 同一设备、同一类型，在 ALERT_MERGE_MINUTES 分钟内只保留一条未处理的。
            String alertType = waterAlarm ? "WATER_ALARM" : "INTRUSION_ALARM";
            AlertEvent recent = alertEventMapper.selectOne(
                    new LambdaQueryWrapper<AlertEvent>()
                            .eq(AlertEvent::getDeviceId, deviceId)
                            .eq(AlertEvent::getAlertType, alertType)
                            .eq(AlertEvent::getIsHandled, 0)
                            .ge(AlertEvent::getAlertTime,
                                    LocalDateTime.now().minusMinutes(ALERT_MERGE_MINUTES))
                            .last("LIMIT 1"));

            if (recent != null) {
                // 已有未处理记录：不再建新报警，但下面的实时数据推送要继续
                log.info("报警合并：设备 {} 的 {} 在 {} 分钟内已有未处理记录(id={})，不重复创建",
                        deviceId, alertType, ALERT_MERGE_MINUTES, recent.getId());
            } else {
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

    /**
     * 设备端轮询用：这台设备有没有还没处理的报警。
     * 只看最近 30 分钟，避免陈年未处理报警让蜂鸣器一直响。
     */
    @Override
    public AlertEvent findUnhandledAlert(String deviceId) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getDeviceId, deviceId)
               .eq(AlertEvent::getIsHandled, 0)
               .ge(AlertEvent::getAlertTime, LocalDateTime.now().minusMinutes(30))
               .orderByDesc(AlertEvent::getAlertTime)
               .last("LIMIT 1");
        return alertEventMapper.selectOne(wrapper);
    }
}
