package com.riverbank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riverbank.common.Result;
import com.riverbank.security.HardwareApiKeyFilter;
import com.riverbank.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    /** 硬件设备共享密钥；留空表示不启用校验（仅建议本地调试）。 */
    @Value("${hardware.api-key:}")
    private String hardwareApiKey;

    @Bean
    public HardwareApiKeyFilter hardwareApiKeyFilter() {
        return new HardwareApiKeyFilter(hardwareApiKey);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                // Hardware endpoints (no JWT required, device uses API key or whitelist)
                .requestMatchers("/api/hardware/**").permitAll()
                // WebSocket
                .requestMatchers("/ws/**").permitAll()
                // Uploaded files
                .requestMatchers("/uploads/**").permitAll()
                // 运行配置（视频流地址等）：前端未登录时也要能拿到
                .requestMatchers("/api/config/**").permitAll()
                // Static resources
                .requestMatchers("/actuator/**").permitAll()
                // Everything else requires auth
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.setStatus(401);
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Result.error(401, "未登录或token已过期")));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.setStatus(403);
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Result.error(403, "没有权限访问")));
                })
            )
            .addFilterBefore(hardwareApiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
