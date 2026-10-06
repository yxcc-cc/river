package com.riverbank.service;

import com.riverbank.entity.IntrusionRecord;
import com.riverbank.entity.WaterData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;

public interface DataService {
    WaterData getLatestWaterData(String deviceId);
    IntrusionRecord getLatestIntrusion(String deviceId);
    Page<WaterData> queryWaterHistory(String deviceId, LocalDateTime start, LocalDateTime end, int page, int size);
    List<WaterData> queryWaterTrend(String deviceId, int hours);
}
