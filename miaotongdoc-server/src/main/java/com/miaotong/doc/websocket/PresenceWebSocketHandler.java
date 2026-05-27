package com.miaotong.doc.websocket;

import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final Map<Long, Map<Long, WebSocketSession>> documentSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        String path = session.getUri().getPath();
        Long docId = extractDocId(path);

        if (docId == null || userId == null) {
            session.close();
            return;
        }

        documentSessions.computeIfAbsent(docId, k -> new ConcurrentHashMap<>())
                .put(userId, session);

        log.info("用户 {} 加入文档 {} 的协作", userId, docId);

        String userName = resolveUserName(userId);
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("userName", userName);
        userInfo.put("color", "#409EFF");
        userInfo.put("joinedAt", java.time.Instant.now().toString());
        broadcastPresence(docId, userId, "join", userInfo);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        String path = session.getUri().getPath();
        Long docId = extractDocId(path);

        if (docId != null && userId != null) {
            Map<Long, WebSocketSession> sessions = documentSessions.get(docId);
            if (sessions != null) {
                sessions.remove(userId);
                if (sessions.isEmpty()) {
                    documentSessions.remove(docId);
                }
            }
            log.info("用户 {} 离开文档 {} 的协作", userId, docId);
            broadcastPresence(docId, userId, "leave", null);
        }
    }

    public void broadcastPresence(Long docId, Long userId, String action, Map<String, String> userInfo) {
        Map<Long, WebSocketSession> sessions = documentSessions.get(docId);
        if (sessions == null) return;

        Map<String, Object> message = new HashMap<>();
        message.put("type", "presence");
        message.put("action", action);
        message.put("userId", userId);
        message.put("userInfo", userInfo);

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("发送消息失败", e);
                }
            });
        } catch (Exception e) {
            log.error("序列化消息失败", e);
        }
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getRealName)
                .filter(name -> name != null && !name.isEmpty())
                .orElse("用户" + userId);
    }

    private Long extractDocId(String path) {
        try {
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}
