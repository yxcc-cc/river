package com.riverbank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riverbank.entity.AlertEvent;
import com.riverbank.entity.Device;
import com.riverbank.entity.WaterData;
import com.riverbank.mapper.AlertEventMapper;
import com.riverbank.mapper.DeviceMapper;
import com.riverbank.mapper.WaterDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时拉取ESP32水位传感器数据
 * 解决手机热点下ESP32无法主动推送数据的问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorPollingService {

    private final DeviceMapper deviceMapper;
    private final WaterDataMapper waterDataMapper;
    private final AlertEventMapper alertEventMapper;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // 传感器设备列表：设备ID → ESP32的IP地址
    // 启动后可通过 /api/sensor/config 接口动态修改
    private final Map<String, String> sensorEndpoints = new HashMap<>();

    private boolean initialized = false;

    /**
     * 初始化传感器端点（从数据库读取设备信息）
     */
    private void initIfNeeded() {
        if (initialized) return;
        initialized = true;

        // 水位传感器：根据实际IP添加
        sensorEndpoints.put("2462ABEFC95C", "http://10.234.223.69/water");

        log.info("[传感器轮询] 初始化完成，监控 {} 个传感器", sensorEndpoints.size());
    }

    /**
     * 每30秒拉取一次传感器数据
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void pollSensors() {
        initIfNeeded();

        for (Map.Entry<String, String> entry : sensorEndpoints.entrySet()) {
            String deviceId = entry.getKey();
            String url = entry.getKey();
            try {
                fetchAndSave(deviceId, entry.getValue());
            } catch (Exception e) {
                log.warn("[传感器轮询] 设备 {} 拉取失败: {}", deviceId, e.getMessage());
            }
        }
    }

    private void fetchAndSave(String deviceId, String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            log.warn("[传感器轮询] 设备 {} 返回 HTTP {}", deviceId, conn.getResponseCode());
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        JsonNode json = objectMapper.readTree(sb.toString());
        BigDecimal waterLevel = json.has("waterLevel") ?
                new BigDecimal(json.get("waterLevel").asText()) : null;

        if (waterLevel == null) {
            log.warn("[传感器轮询] 设备 {} 返回数据无水位字段", deviceId);
            return;
        }

        // 更新设备心跳
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            device.setStatus(1);
            device.setLastHeartbeat(LocalDateTime.now());
            deviceMapper.updateById(device);
        }

        // 保存水位数据
        WaterData waterData = new WaterData();
        waterData.setDeviceId(deviceId);
        waterData.setWaterLevel(waterLevel);
        waterData.setBatteryVoltage(new BigDecimal("3.70"));
        waterData.setCollectTime(LocalDateTime.now());
        waterDataMapper.insert(waterData);

        log.info("[传感器轮询] 设备 {} 水位: {}cm", deviceId, waterLevel);

        // 检查是否超过阈值，触发报警
        if (device != null && device.getWaterThreshold() != null
                && waterLevel.compareTo(device.getWaterThreshold()) > 0) {
            // 创建报警事件
            AlertEvent alert = new AlertEvent();
            alert.setDeviceId(deviceId);
            alert.setAlertTime(LocalDateTime.now());
            alert.setAlertType("WATER_ALARM");
            alert.setWaterLevel(waterLevel);
            alert.setIsHandled(0);
            alertEventMapper.insert(alert);

            log.warn("[水位报警] 设备 {} 水位 {}cm 超过阈值 {}cm!",
                    deviceId, waterLevel, device.getWaterThreshold());

            // 推送WebSocket报警
            Map<String, Object> alertMsg = new HashMap<>();
            alertMsg.put("id", alert.getId());
            alertMsg.put("deviceId", deviceId);
            alertMsg.put("deviceLocation", device.getLocation());
            alertMsg.put("alertType", "WATER_ALARM");
            alertMsg.put("alertTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            alertMsg.put("waterLevel", waterLevel);
            messagingTemplate.convertAndSend("/topic/alert", alertMsg);
        }

        // 推送实时数据
        Map<String, Object> dataMsg = new HashMap<>();
        dataMsg.put("deviceId", deviceId);
        dataMsg.put("deviceLocation", device != null ? device.getLocation() : "");
        dataMsg.put("waterLevel", waterLevel);
        dataMsg.put("batteryVoltage", new BigDecimal("3.70"));
        dataMsg.put("collectTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        dataMsg.put("deviceStatus", 1);
        messagingTemplate.convertAndSend("/topic/data", dataMsg);
    }

    /**
     * 动态添加传感器端点
     */
    public void addSensor(String deviceId, String endpoint) {
        sensorEndpoints.put(deviceId, endpoint);
        log.info("[传感器轮询] 添加设备 {} → {}", deviceId, endpoint);
    }

    /**
     * 获取当前监控的传感器列表
     */
    public Map<String, String> getSensors() {
        return new HashMap<>(sensorEndpoints);
    }
}
