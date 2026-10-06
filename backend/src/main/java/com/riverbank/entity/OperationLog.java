package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private String username;
    private String operation;
    private String method;
    private String params;
    private String ip;
    private LocalDateTime createTime;
}
