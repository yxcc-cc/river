package com.riverbank.service;

import com.riverbank.dto.LoginRequest;
import com.riverbank.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
