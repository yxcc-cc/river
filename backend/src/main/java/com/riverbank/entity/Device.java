package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_device")
public class Device {
    @TableId(type = IdType.INPUT)
    private String id;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer status;
    private LocalDateTime lastHeartbeat;
    private BigDecimal waterThreshold;
    private LocalDate installedDate;
    private String description;

    /**
     * 该点位的视频流地址。
     * 留空表示用全局配置 stream.public-url（单点位场景）。
     * 多点位时每台设备可以配自己的一路流。
     */
    private String streamUrl;
}
