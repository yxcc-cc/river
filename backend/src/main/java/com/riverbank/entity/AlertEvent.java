package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String alertType;
    private LocalDateTime alertTime;
    private BigDecimal waterLevel;
    private Integer isHandled;
    private Integer handlerId;
    private LocalDateTime handleTime;
    private String handleNote;

    @TableField(exist = false)
    private String deviceLocation;
    @TableField(exist = false)
    private String handlerName;
}
