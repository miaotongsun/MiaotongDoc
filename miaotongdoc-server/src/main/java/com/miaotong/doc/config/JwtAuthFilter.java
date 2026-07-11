package com.miaotong.doc.config;

import com.miaotong.doc.service.TokenBlacklistService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

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
        log.debug("JwtAuthFilter: path={}, authHeader={}", request.getRequestURI(), authHeader != null ? "present" : "null");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.debug("JwtAuthFilter: token={}", token.substring(0, Math.min(20, token.length())) + "...");

            try {
                if (jwtUtil.validateToken(token)) {
                    String jti = jwtUtil.getJti(token);
                    log.debug("JwtAuthFilter: token valid, jti={}", jti);

                    if (tokenBlacklistService.isBlacklisted(jti)) {
                        log.debug("JwtAuthFilter: token blacklisted");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"code\":401,\"message\":\"Token已失效\"}");
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
                } else {
                    log.debug("JwtAuthFilter: token invalid");
                }
            } catch (Exception e) {
                log.debug("JwtAuthFilter: token parse error: {}", e.getMessage());
            }
        } else {
            log.debug("JwtAuthFilter: no Bearer token");
        }

        filterChain.doFilter(request, response);
    }
}
