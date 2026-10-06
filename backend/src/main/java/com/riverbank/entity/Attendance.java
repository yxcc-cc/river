package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_attendance")
public class Attendance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private LocalDateTime checkTime;
    private Integer type;  // 1-签到, 2-签退
    private String faceImage;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String location;
    private String remark;

    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String realName;
}
