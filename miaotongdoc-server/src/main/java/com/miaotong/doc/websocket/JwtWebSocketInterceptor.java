package com.miaotong.doc.websocket;

import com.miaotong.doc.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtWebSocketInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        String userName = jwtUtil.getUsername(token);
        attributes.put("userId", userId);
        attributes.put("userName", userName);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        // 从 URL 参数中提取
        String query = request.getURI().getQuery();
        if (query != null) {
            Map<String, String> params = UriComponentsBuilder
                    .fromUriString("?" + query).build()
                    .getQueryParams().toSingleValueMap();
            String token = params.get("token");
            if (token != null) return token;
        }

        // 从 Header 中提取
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
