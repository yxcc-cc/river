package com.riverbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riverbank.entity.Device;
import com.riverbank.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 后台维护任务：让系统"自己保持干净"。
 *
 * 一、设备离线判定
 *    设备断网后不会再上报，但 t_device.status 会一直停在 1，
 *    前端就会显示一台"在线"却没有任何数据的僵尸设备。
 *    这里定期把心跳超时的设备标成离线。
 *
 * 二、抓拍图清理
 *    YOLO 每次确认入侵都上传一张图，没人管的话一个月就能堆几个 G。
 *    uploads/captures 下面是按 yyyyMMdd 分目录的，这里删掉过期日期目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final DeviceMapper deviceMapper;

    /** 心跳超过这个分钟数就算离线 */
    private static final long OFFLINE_MINUTES = 3;

    /** 抓拍图保留天数 */
    @Value("${capture.keep-days:7}")
    private int captureKeepDays;

    /**
     * 每分钟检查一次设备心跳。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void markOfflineDevices() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(OFFLINE_MINUTES);
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, 1)
                        .and(w -> w.isNull(Device::getLastHeartbeat)
                                   .or().lt(Device::getLastHeartbeat, deadline)));

        if (devices.isEmpty()) return;

        for (Device d : devices) {
            d.setStatus(0);
            deviceMapper.updateById(d);
        }
        log.info("设备离线判定：{} 台心跳超时，已标记为离线（{}）",
                devices.size(),
                devices.stream().map(Device::getId).reduce((a, b) -> a + "," + b).orElse(""));
    }

    /**
     * 每天凌晨 3:20 清理过期抓拍图。
     */
    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanOldCaptures() {
        File base = new File("uploads/captures");
        if (!base.exists() || !base.isDirectory()) return;

        String cutoff = LocalDateTime.now().minusDays(captureKeepDays)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        String[] dirs = base.list();
        if (dirs == null || dirs.length == 0) return;

        int removedDirs = 0;
        int removedFiles = 0;

        for (String name : dirs) {
            // 目录名是 yyyyMMdd，比 cutoff 小的就是过期
            if (!name.matches("\\d{8}")) continue;
            if (name.compareTo(cutoff) >= 0) continue;

            File dir = new File(base, name);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.delete()) removedFiles++;
                }
            }
            if (dir.delete()) removedDirs++;
        }

        if (removedFiles > 0) {
            log.info("抓拍图清理：删除 {} 个过期目录、{} 张图片（保留 {} 天）",
                    removedDirs, removedFiles, captureKeepDays);
        }
    }

    /**
     * 供前端/运维手动触发清理（后台接口用）。
     */
    public int cleanCapturesNow() {
        File base = new File("uploads/captures");
        if (!base.exists()) return 0;
        int[] count = {0};
        Arrays.stream(base.listFiles())
                .filter(File::isDirectory)
                .forEach(dir -> {
                    File[] files = dir.listFiles();
                    if (files == null) return;
                    for (File f : files) {
                        if (f.isFile() && f.delete()) count[0]++;
                    }
                });
        return count[0];
    }
}
