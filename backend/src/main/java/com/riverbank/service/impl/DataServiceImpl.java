package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.IntrusionRecord;
import com.riverbank.entity.WaterData;
import com.riverbank.mapper.IntrusionRecordMapper;
import com.riverbank.mapper.WaterDataMapper;
import com.riverbank.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {

    private final WaterDataMapper waterDataMapper;
    private final IntrusionRecordMapper intrusionRecordMapper;

    @Override
    public WaterData getLatestWaterData(String deviceId) {
        return waterDataMapper.selectLatestByDeviceId(deviceId);
    }

    @Override
    public IntrusionRecord getLatestIntrusion(String deviceId) {
        IntrusionRecord record = intrusionRecordMapper.selectLatestByDeviceId(deviceId);
        // Only return intrusion if detected within last 2 minutes
        if (record != null && record.getHumanDetected() != null && record.getHumanDetected() == 1) {
            if (record.getDetectTime() != null && record.getDetectTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
                // Intrusion is stale, return as not detected
                record.setHumanDetected(0);
            }
        }
        return record;
    }

    @Override
    public Page<WaterData> queryWaterHistory(String deviceId, LocalDateTime start, LocalDateTime end, int page, int size) {
        LambdaQueryWrapper<WaterData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WaterData::getDeviceId, deviceId);
        if (start != null) {
            wrapper.ge(WaterData::getCollectTime, start);
        }
        if (end != null) {
            wrapper.le(WaterData::getCollectTime, end);
        }
        wrapper.orderByDesc(WaterData::getCollectTime);
        return waterDataMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<WaterData> queryWaterTrend(String deviceId, int hours) {
        LambdaQueryWrapper<WaterData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WaterData::getDeviceId, deviceId);
        wrapper.ge(WaterData::getCollectTime, LocalDateTime.now().minusHours(hours));
        wrapper.orderByAsc(WaterData::getCollectTime);
        return waterDataMapper.selectList(wrapper);
    }
}
