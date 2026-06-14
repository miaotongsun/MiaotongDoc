package com.miaotong.doc.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        userSessions.put(userId, session);
        log.info("用户 {} 建立通知WebSocket连接", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        userSessions.remove(userId);
        log.info("用户 {} 断开通知WebSocket连接", userId);
    }

    public void sendNotification(Long userId, Map<String, Object> notificationData) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) return;

        try {
            Map<String, Object> message = Map.of(
                    "type", "notification",
                    "data", notificationData
            );

            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送通知失败: userId={}", userId, e);
        }
    }
}
