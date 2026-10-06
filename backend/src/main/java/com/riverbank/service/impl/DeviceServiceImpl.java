package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riverbank.entity.Device;
import com.riverbank.mapper.DeviceMapper;
import com.riverbank.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;

    @Override
    public List<Device> listAll() {
        return deviceMapper.selectList(null);
    }

    @Override
    public Device getById(String id) {
        return deviceMapper.selectById(id);
    }

    @Override
    public void add(Device device) {
        Device existing = deviceMapper.selectById(device.getId());
        if (existing != null) {
            throw new IllegalArgumentException("设备编号已存在: " + device.getId());
        }
        deviceMapper.insert(device);
    }

    @Override
    public void update(Device device) {
        deviceMapper.updateById(device);
    }

    @Override
    public void delete(String id) {
        deviceMapper.deleteById(id);
    }

    @Override
    public void updateThreshold(String deviceId, BigDecimal threshold) {
        Device device = new Device();
        device.setId(deviceId);
        device.setWaterThreshold(threshold);
        deviceMapper.updateById(device);
    }
}
