package com.miaotong.doc.config;

import com.miaotong.doc.config.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableAsync
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @PostConstruct
    public void init() {
        // 确保异步请求也能访问 SecurityContext
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .authorizeHttpRequests(auth -> auth
                // 认证相关（无需登录）
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
                // AI 代理（AI 插件使用 OnlyOffice JWT，非应用 JWT）
                .requestMatchers("/api/ai/proxy").permitAll()
                .requestMatchers("/api/ai/refresh-models").permitAll()
                .requestMatchers("/api/ai/config").permitAll()
                // AI 聊天 SSE 流（手动在 Controller 中验证 token）
                .requestMatchers("/api/ai/chat/stream").permitAll()
                // 编辑器回调（验证编辑器 JWT，无需应用 JWT）
                .requestMatchers("/api/callback/**").permitAll()
                // 文档文件下载（编辑器拉取用）
                .requestMatchers("/api/documents/*/file").permitAll()
                .requestMatchers("/api/documents/file/**").permitAll()
                // SSO 端点（logout 需要认证）
                .requestMatchers("/api/sso/providers").permitAll()
                .requestMatchers("/api/sso/callback").permitAll()
                .requestMatchers("/api/sso/logout").authenticated()
                // WebSocket 路径
                .requestMatchers("/ws/**").permitAll()
                // Actuator 健康检查
                .requestMatchers("/actuator/**").permitAll()
                // AI 测试端点（无需文档权限）
                .requestMatchers("/api/ai/test/**").permitAll()
                // 文档 AI 功能（手动在 Controller 中验证 token）
                .requestMatchers("/api/documents/*/ai/**").permitAll()
                // 其他 API 需要认证
                .requestMatchers("/api/**").authenticated()
                // 静态资源
                .anyRequest().permitAll()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
