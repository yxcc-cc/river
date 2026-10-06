package com.riverbank.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.common.PageResult;
import com.riverbank.common.Result;
import com.riverbank.entity.WaterData;
import com.riverbank.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final DataService dataService;

    @GetMapping("/realtime")
    public Result<?> getRealtime(@RequestParam String deviceId) {
        var waterData = dataService.getLatestWaterData(deviceId);
        var intrusion = dataService.getLatestIntrusion(deviceId);
        var result = new java.util.HashMap<String, Object>();
        result.put("waterData", waterData);
        result.put("intrusion", intrusion);
        return Result.success(result);
    }

    @GetMapping("/water")
    public Result<PageResult<WaterData>> queryWaterHistory(
            @RequestParam String deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WaterData> data = dataService.queryWaterHistory(deviceId, start, end, page, size);
        return Result.success(PageResult.of(data));
    }

    @GetMapping("/water/trend")
    public Result<List<WaterData>> getWaterTrend(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "24") int hours) {
        return Result.success(dataService.queryWaterTrend(deviceId, hours));
    }
}
