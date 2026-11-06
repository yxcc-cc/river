package com.riverbank.controller;

import com.riverbank.common.Result;
import com.riverbank.dto.HardwareDataRequest;
import com.riverbank.entity.Device;
import com.riverbank.service.AlertService;
import com.riverbank.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hardware device API endpoints (no JWT required).
 */
@Slf4j
@RestController
@RequestMapping("/api/hardware")
@RequiredArgsConstructor
public class HardwareController {

    private final AlertService alertService;
    private final DeviceService deviceService;

    @PostMapping("/data/upload")
    public Result<?> uploadData(@RequestBody HardwareDataRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().isEmpty()) {
            return Result.error(400, "设备ID不能为空");
        }
        log.info("Received data from device {}: waterLevel={}, humanDetected={}",
                request.getDeviceId(), request.getWaterLevel(), request.getHumanDetected());
        alertService.processHardwareData(request);
        return Result.success();
    }

    @GetMapping("/heartbeat")
    public Result<?> heartbeat(@RequestParam String deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            return Result.error(404, "设备不存在");
        }
        device.setStatus(1);
        device.setLastHeartbeat(LocalDateTime.now());
        deviceService.update(device);
        log.debug("Heartbeat from device: {}", deviceId);
        return Result.success();
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig(@RequestParam String deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            return Result.error(404, "设备不存在");
        }
        Map<String, Object> config = new HashMap<>();
        config.put("waterThreshold", device.getWaterThreshold());
        config.put("uploadInterval", 60);
        config.put("alertInterval", 10);
        return Result.success(config);
    }

    /**
     * 设备端轮询「我现在要不要报警」。
     * 因为后端在公网、设备在家里，只能由设备主动来问，不能由后端推。
     * 返回 alarm=true 时，设备上的蜂鸣器就响。
     */
    @GetMapping("/alert/check")
    public Result<Map<String, Object>> checkAlert(@RequestParam String deviceId) {
        Map<String, Object> result = new HashMap<>();
        if (!isValidDeviceId(deviceId)) {
            return Result.error(400, "非法设备ID");
        }
        var alert = alertService.findUnhandledAlert(deviceId);
        result.put("alarm", alert != null);
        if (alert != null) {
            // 带上 id：设备端靠它判断「这条响过了没有」，否则同一条报警会被反复触发
            result.put("alertId", alert.getId());
            result.put("alertType", alert.getAlertType());
            result.put("waterLevel", alert.getWaterLevel());
            result.put("alertTime", alert.getAlertTime());
        }
        return Result.success(result);
    }

    /**
     * Upload a capture image from the vision detector.
     */
    @PostMapping("/image/upload")
    public Result<Map<String, String>> uploadImage(@RequestParam String deviceId,
                                                   @RequestParam MultipartFile image) {
        if (image.isEmpty()) {
            return Result.error(400, "图片不能为空");
        }
        if (!isValidDeviceId(deviceId)) {
            return Result.error(400, "非法设备ID");
        }
        try {
            // Create upload directory: uploads/captures/date/
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String uploadDir = new File("uploads/captures/" + dateDir).getAbsolutePath() + File.separator;
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Generate filename: deviceId_timestamp.jpg
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss_SSS"));
            String filename = deviceId + "_" + timestamp + ".jpg";
            File dest = new File(uploadDir + filename);
            FileCopyUtils.copy(image.getInputStream(), new FileOutputStream(dest));

            String imageUrl = "/uploads/captures/" + dateDir + "/" + filename;
            log.info("Image uploaded from device {}: {}", deviceId, imageUrl);

            Map<String, String> result = new HashMap<>();
            result.put("url", imageUrl);
            result.put("filename", filename);
            return Result.success(result);
        } catch (IOException e) {
            log.error("Failed to save image from device {}", deviceId, e);
            return Result.error(500, "图片保存失败");
        }
    }

    /**
     * Get the latest capture image for a device.
     */
    @GetMapping("/image/latest")
    public Result<Map<String, String>> getLatestImage(@RequestParam String deviceId) {
        String baseDir = "uploads/captures/";
        File base = new File(baseDir);
        if (!base.exists()) {
            return Result.success(null);
        }

        // Find the latest date directory
        String[] dateDirs = base.list();
        if (dateDirs == null || dateDirs.length == 0) {
            return Result.success(null);
        }
        Arrays.sort(dateDirs, Collections.reverseOrder());

        // Find the latest file for this device
        for (String dateDir : dateDirs) {
            File dateFolder = new File(baseDir + dateDir);
            String[] files = dateFolder.list((dir, name) -> name.startsWith(deviceId + "_"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Collections.reverseOrder());
                String latestFile = files[0];
                Map<String, String> result = new HashMap<>();
                result.put("url", "/uploads/captures/" + dateDir + "/" + latestFile);
                result.put("filename", latestFile);
                result.put("date", dateDir);
                return Result.success(result);
            }
        }
        return Result.success(null);
    }

    /**
     * Get recent capture images for a device.
     */
    @GetMapping("/image/list")
    public Result<List<Map<String, String>>> getCaptureList(@RequestParam String deviceId,
                                                              @RequestParam(defaultValue = "10") int limit) {
        String baseDir = "uploads/captures/";
        File base = new File(baseDir);
        if (!base.exists()) {
            return Result.success(Collections.emptyList());
        }

        List<Map<String, String>> allFiles = new ArrayList<>();
        String[] dateDirs = base.list();
        if (dateDirs == null) {
            return Result.success(Collections.emptyList());
        }
        Arrays.sort(dateDirs, Collections.reverseOrder());

        for (String dateDir : dateDirs) {
            File dateFolder = new File(baseDir + dateDir);
            String[] files = dateFolder.list((dir, name) -> name.startsWith(deviceId + "_"));
            if (files != null) {
                Arrays.sort(files, Collections.reverseOrder());
                for (String file : files) {
                    Map<String, String> item = new HashMap<>();
                    item.put("url", "/uploads/captures/" + dateDir + "/" + file);
                    item.put("filename", file);
                    item.put("date", dateDir);
                    allFiles.add(item);
                    if (allFiles.size() >= limit) {
                        return Result.success(allFiles);
                    }
                }
            }
        }
        return Result.success(allFiles);
    }

    /**
     * Delete a capture image.
     * 只允许删除 uploads/captures 目录下的文件，防止路径穿越。
     */
    @DeleteMapping("/image/delete")
    public Result<?> deleteCapture(@RequestParam String url) {
        // url like: /uploads/captures/20260601/DEV-001_143025_123.jpg
        Path base = Paths.get("uploads", "captures").toAbsolutePath().normalize();
        String rel = url.replaceFirst("^/+", "").replaceFirst("^uploads/captures/", "");
        Path target = base.resolve(rel).normalize();
        if (!target.startsWith(base)) {
            log.warn("Rejected path traversal attempt: {}", url);
            return Result.error(400, "非法路径");
        }
        try {
            if (Files.isRegularFile(target) && Files.deleteIfExists(target)) {
                log.info("Deleted capture: {}", target);
                return Result.success();
            }
        } catch (IOException e) {
            log.error("Failed to delete capture {}", target, e);
            return Result.error(500, "删除失败");
        }
        return Result.error(404, "文件不存在");
    }

    /**
     * Clear all capture images for a device.
     */
    @DeleteMapping("/image/clear")
    public Result<?> clearCaptures(@RequestParam String deviceId) {
        String baseDir = "uploads/captures/";
        File base = new File(baseDir);
        if (!base.exists()) {
            return Result.success();
        }
        int count = 0;
        String[] dateDirs = base.list();
        if (dateDirs != null) {
            for (String dateDir : dateDirs) {
                File dateFolder = new File(baseDir + dateDir);
                String[] files = dateFolder.list((dir, name) -> name.startsWith(deviceId + "_"));
                if (files != null) {
                    for (String file : files) {
                        if (new File(dateFolder, file).delete()) {
                            count++;
                        }
                    }
                }
            }
        }
        log.info("Cleared {} captures for device {}", count, deviceId);
        return Result.success(count);
    }

    /**
     * 设备ID只允许字母、数字、下划线和短横线，避免被拼进文件路径时出问题。
     */
    private static boolean isValidDeviceId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,64}");
    }
}
