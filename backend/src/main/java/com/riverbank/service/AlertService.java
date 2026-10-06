package com.riverbank.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.dto.HardwareDataRequest;
import com.riverbank.entity.AlertEvent;

public interface AlertService {
    void processHardwareData(HardwareDataRequest request);
    Page<AlertEvent> listAlerts(String status, String deviceId, int page, int size);
    void handleAlert(Long alertId, Integer handlerId, String handleNote);
    long countUnhandled();
    void deleteAlert(Long alertId);
    void clearAlerts(String status);
}
