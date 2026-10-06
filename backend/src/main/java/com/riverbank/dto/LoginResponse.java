package com.riverbank.dto;

import lombok.Data;
import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private Integer userId;
    private String username;
    private String realName;
    private String roleKey;
    private String roleName;
    private List<String> permissions;
}
