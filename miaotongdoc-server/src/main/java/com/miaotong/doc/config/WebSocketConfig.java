package com.miaotong.doc.config;

import com.miaotong.doc.websocket.JwtWebSocketInterceptor;
import com.miaotong.doc.websocket.NotificationWebSocketHandler;
import com.miaotong.doc.websocket.PresenceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceWebSocketHandler presenceHandler;
    private final NotificationWebSocketHandler notificationHandler;
    private final JwtWebSocketInterceptor jwtInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 协作状态通道
        registry.addHandler(presenceHandler, "/ws/presence/{docId}")
                .addInterceptors(jwtInterceptor)
                .setAllowedOrigins("*");

        // 通知推送通道
        registry.addHandler(notificationHandler, "/ws/notifications")
                .addInterceptors(jwtInterceptor)
                .setAllowedOrigins("*");
    }
}
