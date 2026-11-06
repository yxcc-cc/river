package com.riverbank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 硬件接口（/api/hardware/**）的设备鉴权。
 *
 * 这些接口在 SecurityConfig 里是 permitAll（设备端没有 JWT），
 * 所以另外用一个共享密钥校验：请求头 X-Device-Key 必须匹配配置项 hardware.api-key。
 *
 * 若 hardware.api-key 未配置（留空），则放行并打印警告 —— 方便本地调试，
 * 但生产环境务必配置，否则任何人都能伪造传感器数据。
 */
@Slf4j
public class HardwareApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Device-Key";

    private final String expectedKey;
    private boolean warned = false;

    public HardwareApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey == null ? "" : expectedKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/hardware/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (expectedKey.isEmpty()) {
            if (!warned) {
                log.warn("hardware.api-key 未配置，/api/hardware/** 处于无鉴权状态（仅建议本地调试使用）");
                warned = true;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (expectedKey.equals(provided)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("硬件接口鉴权失败: uri={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"无效的设备密钥\"}");
    }
}
