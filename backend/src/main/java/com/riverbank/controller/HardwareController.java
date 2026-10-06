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
     * Upload a capture image from the vision detector.
     */
    @PostMapping("/image/upload")
    public Result<Map<String, String>> uploadImage(@RequestParam String deviceId,
                                                   @RequestParam MultipartFile image) {
        if (image.isEmpty()) {
            return Result.error(400, "图片不能为空");
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
     */
    @DeleteMapping("/image/delete")
    public Result<?> deleteCapture(@RequestParam String url) {
        // url like: /uploads/captures/20260601/DEV-001_143025_123.jpg
        String relativePath = url.startsWith("/") ? url.substring(1) : url;
        File file = new File(relativePath);
        if (file.exists() && file.delete()) {
            log.info("Deleted capture: {}", url);
            return Result.success();
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
}
