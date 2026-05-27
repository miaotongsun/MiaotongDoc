package com.miaotong.doc.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.Notification;
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
    // userId -> session
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

    public void sendNotification(Long userId, Notification notification) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) return;

        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("id", notification.getId());
            if (notification.getFromUserId() != null) data.put("fromUserId", notification.getFromUserId());
            if (notification.getDocumentId() != null) data.put("documentId", notification.getDocumentId());
            data.put("notificationType", notification.getType());
            data.put("content", notification.getContent());
            if (notification.getCreatedAt() != null) data.put("createdAt", notification.getCreatedAt().toString());

            Map<String, Object> message = Map.of(
                    "type", "notification",
                    "data", data
            );

            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送通知失败: userId={}", userId, e);
        }
    }
}
