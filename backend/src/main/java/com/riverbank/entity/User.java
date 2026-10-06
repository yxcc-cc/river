package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private String realName;
    private Integer roleId;
    private String phone;
    private LocalDateTime createTime;
    private Integer status;

    @TableField(exist = false)
    private String roleName;
    @TableField(exist = false)
    private String roleKey;
}
