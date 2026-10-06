package com.riverbank.controller;

import com.riverbank.common.Result;
import com.riverbank.dto.LoginRequest;
import com.riverbank.dto.LoginResponse;
import com.riverbank.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/info")
    public Result<String> info() {
        return Result.success("token valid");
    }
}
