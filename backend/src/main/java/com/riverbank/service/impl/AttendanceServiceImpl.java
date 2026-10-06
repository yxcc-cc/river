package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.Attendance;
import com.riverbank.entity.User;
import com.riverbank.mapper.AttendanceMapper;
import com.riverbank.mapper.UserMapper;
import com.riverbank.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final UserMapper userMapper;

    @Override
    public void checkIn(Integer userId, String faceImage, Double longitude, Double latitude, String location, String remark) {
        // 获取今日最后一条考勤记录
        Attendance lastRecord = getLastTodayRecord(userId);
        if (lastRecord != null && lastRecord.getType() == 1) {
            throw new IllegalStateException("当前已签到状态，请先签退后再签到");
        }

        Attendance attendance = new Attendance();
        attendance.setUserId(userId);
        attendance.setCheckTime(LocalDateTime.now());
        attendance.setType(1);  // 签到
        attendance.setFaceImage(faceImage);
        if (longitude != null) attendance.setLongitude(BigDecimal.valueOf(longitude));
        if (latitude != null) attendance.setLatitude(BigDecimal.valueOf(latitude));
        attendance.setLocation(location);
        attendance.setRemark(remark);
        attendanceMapper.insert(attendance);

        log.info("用户{}签到成功", userId);
    }

    @Override
    public void checkOut(Integer userId, String faceImage, Double longitude, Double latitude, String location, String remark) {
        // 获取今日最后一条考勤记录
        Attendance lastRecord = getLastTodayRecord(userId);
        if (lastRecord == null || lastRecord.getType() != 1) {
            throw new IllegalStateException("尚未签到，请先签到");
        }

        Attendance attendance = new Attendance();
        attendance.setUserId(userId);
        attendance.setCheckTime(LocalDateTime.now());
        attendance.setType(2);  // 签退
        attendance.setFaceImage(faceImage);
        if (longitude != null) attendance.setLongitude(BigDecimal.valueOf(longitude));
        if (latitude != null) attendance.setLatitude(BigDecimal.valueOf(latitude));
        attendance.setLocation(location);
        attendance.setRemark(remark);
        attendanceMapper.insert(attendance);

        log.info("用户{}签退成功", userId);
    }

    @Override
    public Map<String, Object> getTodayStatus(Integer userId) {
        Attendance lastRecord = getLastTodayRecord(userId);

        boolean checkedIn = lastRecord != null && lastRecord.getType() == 1;
        boolean checkedOut = lastRecord != null && lastRecord.getType() == 2;

        Map<String, Object> result = new HashMap<>();
        result.put("checkedIn", checkedIn);
        result.put("checkedOut", checkedOut);

        // 获取今日最新的签到和签退记录
        Attendance latestCheckIn = getLatestTodayRecord(userId, 1);
        Attendance latestCheckOut = getLatestTodayRecord(userId, 2);

        result.put("checkInTime", latestCheckIn != null ? latestCheckIn.getCheckTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null);
        result.put("checkOutTime", latestCheckOut != null ? latestCheckOut.getCheckTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null);

        // 计算当前周期工作时长
        if (checkedIn && latestCheckIn != null) {
            // 已签到未签退：当前时间 - 签到时间
            long minutes = java.time.Duration.between(latestCheckIn.getCheckTime(), LocalDateTime.now()).toMinutes();
            result.put("workHours", String.format("%d小时%d分钟(进行中)", minutes / 60, minutes % 60));
        } else if (latestCheckIn != null && latestCheckOut != null) {
            // 已签退：签退时间 - 签到时间
            long minutes = java.time.Duration.between(latestCheckIn.getCheckTime(), latestCheckOut.getCheckTime()).toMinutes();
            result.put("workHours", String.format("%d小时%d分钟", minutes / 60, minutes % 60));
        }

        return result;
    }

    @Override
    public Page<Attendance> listRecords(Integer userId, String startDate, String endDate, int page, int size) {
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(Attendance::getUserId, userId);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(Attendance::getCheckTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(Attendance::getCheckTime, LocalDate.parse(endDate).atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(Attendance::getCheckTime);

        Page<Attendance> result = attendanceMapper.selectPage(new Page<>(page, size), wrapper);

        // 填充用户名
        result.getRecords().forEach(record -> {
            User user = userMapper.selectById(record.getUserId());
            if (user != null) {
                record.setUserName(user.getUsername());
                record.setRealName(user.getRealName());
            }
        });

        return result;
    }

    @Override
    public Map<String, Object> getMonthlyStatistics(Integer userId, String month) {
        YearMonth ym = YearMonth.parse(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getUserId, userId);
        wrapper.ge(Attendance::getCheckTime, start);
        wrapper.le(Attendance::getCheckTime, end);
        wrapper.eq(Attendance::getType, 1);  // 只统计签到记录

        List<Attendance> checkIns = attendanceMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("totalDays", checkIns.size());
        result.put("month", month);

        // 迟到次数（假设9:00为上班时间）
        long lateCount = checkIns.stream()
                .filter(a -> a.getCheckTime().toLocalTime().isAfter(LocalTime.of(9, 0)))
                .count();
        result.put("lateCount", lateCount);

        return result;
    }

    /**
     * 获取用户今日最后一条考勤记录（不限类型）
     */
    private Attendance getLastTodayRecord(Integer userId) {
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getUserId, userId);
        wrapper.ge(Attendance::getCheckTime, LocalDate.now().atStartOfDay());
        wrapper.le(Attendance::getCheckTime, LocalDate.now().atTime(LocalTime.MAX));
        wrapper.orderByDesc(Attendance::getCheckTime);
        wrapper.last("LIMIT 1");
        return attendanceMapper.selectOne(wrapper);
    }

    /**
     * 获取用户今日指定类型的最新记录
     */
    private Attendance getLatestTodayRecord(Integer userId, int type) {
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getUserId, userId);
        wrapper.eq(Attendance::getType, type);
        wrapper.ge(Attendance::getCheckTime, LocalDate.now().atStartOfDay());
        wrapper.le(Attendance::getCheckTime, LocalDate.now().atTime(LocalTime.MAX));
        wrapper.orderByDesc(Attendance::getCheckTime);
        wrapper.last("LIMIT 1");
        return attendanceMapper.selectOne(wrapper);
    }
}
