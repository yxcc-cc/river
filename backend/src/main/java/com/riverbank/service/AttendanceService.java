package com.riverbank.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.Attendance;

import java.util.Map;

public interface AttendanceService {

    /**
     * 签到
     */
    void checkIn(Integer userId, String faceImage, Double longitude, Double latitude, String location, String remark);

    /**
     * 签退
     */
    void checkOut(Integer userId, String faceImage, Double longitude, Double latitude, String location, String remark);

    /**
     * 获取今日考勤状态
     */
    Map<String, Object> getTodayStatus(Integer userId);

    /**
     * 分页查询考勤记录
     */
    Page<Attendance> listRecords(Integer userId, String startDate, String endDate, int page, int size);

    /**
     * 月度统计
     */
    Map<String, Object> getMonthlyStatistics(Integer userId, String month);
}
