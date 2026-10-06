package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_intrusion_record")
public class IntrusionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Integer humanDetected;
    private LocalDateTime detectTime;
}
