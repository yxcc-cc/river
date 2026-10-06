package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_water_data")
public class WaterData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private BigDecimal waterLevel;
    private BigDecimal batteryVoltage;
    private LocalDateTime collectTime;
}
