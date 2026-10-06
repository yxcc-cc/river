package com.riverbank.service.impl;

import com.riverbank.dto.LoginRequest;
import com.riverbank.dto.LoginResponse;
import com.riverbank.entity.User;
import com.riverbank.mapper.UserMapper;
import com.riverbank.service.AuthService;
import com.riverbank.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new org.springframework.security.authentication.DisabledException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRoleKey());

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoleKey(user.getRoleKey());
        resp.setRoleName(user.getRoleName());

        // Parse permissions from JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // Get permissions from role
            com.riverbank.entity.Role role = userMapper.selectById(user.getRoleId()) != null ? null : null;
            // Simplified: use role_key to determine permissions
            if ("admin".equals(user.getRoleKey())) {
                resp.setPermissions(List.of("dashboard","device","alert","data","user","role","log","statistics","threshold"));
            } else {
                resp.setPermissions(List.of("dashboard","alert","data"));
            }
        } catch (Exception e) {
            resp.setPermissions(List.of("dashboard","alert","data"));
        }

        return resp;
    }
}
