package com.riverbank.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.common.PageResult;
import com.riverbank.common.Result;
import com.riverbank.entity.Attendance;
import com.riverbank.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * 签到
     */
    @PostMapping("/check-in")
    public Result<?> checkIn(Authentication authentication,
                             @RequestBody(required = false) Map<String, Object> body) {
        Integer userId = (Integer) authentication.getPrincipal();
        String faceImage = body != null ? (String) body.get("faceImage") : null;
        Double longitude = body != null && body.get("longitude") != null ? ((Number) body.get("longitude")).doubleValue() : null;
        Double latitude = body != null && body.get("latitude") != null ? ((Number) body.get("latitude")).doubleValue() : null;
        String location = body != null ? (String) body.get("location") : null;
        String remark = body != null ? (String) body.get("remark") : null;

        attendanceService.checkIn(userId, faceImage, longitude, latitude, location, remark);
        return Result.success();
    }

    /**
     * 签退
     */
    @PostMapping("/check-out")
    public Result<?> checkOut(Authentication authentication,
                              @RequestBody(required = false) Map<String, Object> body) {
        Integer userId = (Integer) authentication.getPrincipal();
        String faceImage = body != null ? (String) body.get("faceImage") : null;
        Double longitude = body != null && body.get("longitude") != null ? ((Number) body.get("longitude")).doubleValue() : null;
        Double latitude = body != null && body.get("latitude") != null ? ((Number) body.get("latitude")).doubleValue() : null;
        String location = body != null ? (String) body.get("location") : null;
        String remark = body != null ? (String) body.get("remark") : null;

        attendanceService.checkOut(userId, faceImage, longitude, latitude, location, remark);
        return Result.success();
    }

    /**
     * 获取今日考勤状态
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayStatus(Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        return Result.success(attendanceService.getTodayStatus(userId));
    }

    /**
     * 考勤记录查询（分页）
     * 管理员可看所有，巡河员只看自己
     */
    @GetMapping("/records")
    public Result<PageResult<Attendance>> listRecords(
            Authentication authentication,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        String roleKey = (String) authentication.getAuthorities().iterator().next().getAuthority();
        Integer currentUserId = (Integer) authentication.getPrincipal();

        // 巡河员只能看自己的记录
        Integer queryUserId = "ROLE_admin".equals(roleKey) ? userId : currentUserId;

        Page<Attendance> records = attendanceService.listRecords(queryUserId, startDate, endDate, page, size);
        return Result.success(PageResult.of(records));
    }

    /**
     * 月度统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getMonthlyStatistics(
            Authentication authentication,
            @RequestParam(required = false) Integer userId,
            @RequestParam String month) {

        String roleKey = (String) authentication.getAuthorities().iterator().next().getAuthority();
        Integer currentUserId = (Integer) authentication.getPrincipal();

        Integer queryUserId = "ROLE_admin".equals(roleKey) ? userId : currentUserId;
        if (queryUserId == null) queryUserId = currentUserId;

        return Result.success(attendanceService.getMonthlyStatistics(queryUserId, month));
    }
}
