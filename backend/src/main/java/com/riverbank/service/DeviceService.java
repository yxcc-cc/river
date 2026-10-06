package com.riverbank.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.Device;

import java.math.BigDecimal;
import java.util.List;

public interface DeviceService {
    List<Device> listAll();
    Device getById(String id);
    void add(Device device);
    void update(Device device);
    void delete(String id);
    void updateThreshold(String deviceId, BigDecimal threshold);
}
