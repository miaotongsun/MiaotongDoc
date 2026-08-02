package com.miaotong.doc.config;

import com.miaotong.doc.config.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;

import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableAsync
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OpenApiAuthFilter openApiAuthFilter;

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
                // 对外 API（鉴权由 OpenApiAuthFilter 完成）
                .requestMatchers("/api/open/**").permitAll()
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
            .exceptionHandling(ex -> ex
                // 未认证访问受保护资源:返回 401 而非 403(RFC 7235 合规)
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    // 用 HashMap 允许 null 值(Map.of 不允许)
                    Map<String, Object> body = new java.util.HashMap<>();
                    body.put("code", 40101);
                    body.put("message", "未登录或令牌无效");
                    body.put("data", null);
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 单独注册 OpenApiAuthFilter（不走 Spring Security 过滤器链，
     * 这样可以早于 Security 拦截器执行，并自行返回 401 响应）
     */
    @Bean
    public FilterRegistrationBean<OpenApiAuthFilter> openApiAuthFilterRegistration() {
        FilterRegistrationBean<OpenApiAuthFilter> registration = new FilterRegistrationBean<>(openApiAuthFilter);
        registration.addUrlPatterns("/api/open/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 显式禁用 JwtAuthFilter 的自动 servlet 注册(由 SecurityFilterChain.addFilterBefore 加入 Security 链)。
     * 不加这个 Bean 会导致 JwtAuthFilter 被注册两次:
     *   1) Spring Boot 默认把 @Component Filter 注册到 servlet 链(`/*`)
     *   2) SecurityConfig.addFilterBefore 加入 SecurityFilterChain
     * 第二次执行时 response 已 commit,write 抛 IOException → Tomcat 默认错误 500
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);  // 关键:禁用 servlet 自动注册
        return registration;
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
