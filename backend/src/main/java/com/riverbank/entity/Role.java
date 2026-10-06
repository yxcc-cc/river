package com.riverbank.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("t_role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String roleName;
    private String roleKey;
    private String permissions;
}
