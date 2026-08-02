package com.miaotong.doc.config;

import com.miaotong.doc.service.TokenBlacklistService;
import com.miaotong.doc.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String path = request.getRequestURI();
        log.debug("JwtAuthFilter: path={}, authHeader={}", path, authHeader != null ? "present" : "null");

        // permitAll 路径:不要求 token,直接放行(由 Security 过滤器后续决定)
        if (isPermitAllPath(path)) {
            log.debug("JwtAuthFilter: permitAll path, skip");
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("JwtAuthFilter: no Bearer token, write 401");
            writeUnauthorized(response, "未提供 Token");
            return;
        }

        String token = authHeader.substring(7);
        log.debug("JwtAuthFilter: token={}", token.substring(0, Math.min(20, token.length())) + "...");

        try {
            if (!jwtUtil.validateToken(token)) {
                log.debug("JwtAuthFilter: token invalid, write 401");
                writeUnauthorized(response, "Token 无效或已过期");
                return;
            }
            String jti = jwtUtil.getJti(token);
            log.debug("JwtAuthFilter: token valid, jti={}", jti);

            if (tokenBlacklistService.isBlacklisted(jti)) {
                log.debug("JwtAuthFilter: token blacklisted, write 401");
                writeUnauthorized(response, "Token 已失效");
                return;
            }

            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            log.debug("JwtAuthFilter: userId={}, username={}", userId, username);

            request.setAttribute("userId", userId);
            request.setAttribute("employeeId", jwtUtil.getEmployeeId(token));
            request.setAttribute("role", jwtUtil.getRole(token));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JwtAuthFilter: authentication set");
        } catch (Exception e) {
            log.debug("JwtAuthFilter: token parse error: {}", e.getMessage());
            writeUnauthorized(response, "Token 解析失败: " + e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 用 HashMap(不允许 null 值用 Map.of)
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", 40101);
        body.put("message", message);
        body.put("data", null);
        new ObjectMapper().writeValue(response.getWriter(), body);
    }

    private boolean isPermitAllPath(String path) {
        if (path.equals("/")) return true;  // 根路径
        if (path.equals("/index.html")) return true;
        // 静态资源(前端 SPA):不走 /api/ 前缀,不需 JWT
        if (!path.startsWith("/api/") && !path.startsWith("/ws/") && !path.startsWith("/actuator/")) {
            return true;
        }
        // /api/ 下的白名单
        if (path.startsWith("/actuator/")) return true;
        if (path.startsWith("/ws/")) return true;
        return path.equals("/api/auth/login")
            || path.equals("/api/auth/register")
            || path.equals("/api/ai/proxy")
            || path.equals("/api/ai/refresh-models")
            || path.equals("/api/ai/config")
            || path.startsWith("/api/ai/chat/stream")
            || path.startsWith("/api/callback/")
            || (path.startsWith("/api/documents/") && path.endsWith("/file") && !path.contains("/list"))
            || path.startsWith("/api/documents/file/")
            || path.startsWith("/api/sso/providers")
            || path.startsWith("/api/sso/callback")
            || path.startsWith("/api/ai/test/")
            || (path.startsWith("/api/documents/") && path.contains("/ai/"))
            || path.startsWith("/api/open/");
    }
}
