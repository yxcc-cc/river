package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riverbank.entity.AlertEvent;
import com.riverbank.entity.Device;
import com.riverbank.entity.WaterData;
import com.riverbank.mapper.AlertEventMapper;
import com.riverbank.mapper.DeviceMapper;
import com.riverbank.mapper.WaterDataMapper;
import com.riverbank.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final DeviceMapper deviceMapper;
    private final WaterDataMapper waterDataMapper;
    private final AlertEventMapper alertEventMapper;

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();

        // Device stats
        List<Device> devices = deviceMapper.selectList(null);
        long totalDevices = devices.size();
        long onlineDevices = devices.stream().filter(d -> d.getStatus() != null && d.getStatus() == 1).count();

        // Alert stats today
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        LambdaQueryWrapper<AlertEvent> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.ge(AlertEvent::getAlertTime, todayStart)
                    .le(AlertEvent::getAlertTime, todayEnd);
        long todayAlerts = alertEventMapper.selectCount(alertWrapper);

        // Unhandled alerts
        LambdaQueryWrapper<AlertEvent> unhandledWrapper = new LambdaQueryWrapper<>();
        unhandledWrapper.eq(AlertEvent::getIsHandled, 0);
        long unhandledAlerts = alertEventMapper.selectCount(unhandledWrapper);

        // Water alarm count
        LambdaQueryWrapper<AlertEvent> waterWrapper = new LambdaQueryWrapper<>();
        waterWrapper.eq(AlertEvent::getAlertType, "WATER_ALARM");
        long waterAlarms = alertEventMapper.selectCount(waterWrapper);

        // Intrusion alarm count
        LambdaQueryWrapper<AlertEvent> intrusionWrapper = new LambdaQueryWrapper<>();
        intrusionWrapper.eq(AlertEvent::getAlertType, "INTRUSION_ALARM");
        long intrusionAlarms = alertEventMapper.selectCount(intrusionWrapper);

        // Total data records
        long totalRecords = waterDataMapper.selectCount(null);

        result.put("totalDevices", totalDevices);
        result.put("onlineDevices", onlineDevices);
        result.put("todayAlerts", todayAlerts);
        result.put("unhandledAlerts", unhandledAlerts);
        result.put("waterAlarms", waterAlarms);
        result.put("intrusionAlarms", intrusionAlarms);
        result.put("totalRecords", totalRecords);

        return result;
    }
}
