package com.miaotong.doc.service;

import com.miaotong.doc.dto.PresenceInfo;
import com.miaotong.doc.websocket.PresenceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final RedisTemplate<String, Object> redis;
    private final PresenceWebSocketHandler presenceWebSocketHandler;
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);

    private static final String[] COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD",
            "#98D8C8", "#F7DC6F", "#82E0AA", "#F1948A", "#85C1E9", "#F0B27A",
            "#BB8FCE", "#73C6B6", "#E74C3C", "#3498DB", "#2ECC71", "#F39C12",
            "#9B59B6", "#1ABC9C", "#E67E22", "#2980B9", "#27AE60", "#D35400"
    };

    public void joinDocument(Long docId, Long userId, String userName) {
        String key = "presence:doc:" + docId;
        Map<String, String> info = Map.of(
                "userName", userName,
                "joinedAt", Instant.now().toString(),
                "color", COLORS[(int) ((userId & 0x7FFFFFFF) % COLORS.length)]
        );
        redis.opsForHash().put(key, userId.toString(), info.toString());
        redis.expire(key, PRESENCE_TTL);
        presenceWebSocketHandler.broadcastPresence(docId, userId, "join", info);
    }

    public void heartbeat(Long docId, Long userId) {
        String key = "presence:doc:" + docId;
        redis.expire(key, PRESENCE_TTL);
    }

    public void leaveDocument(Long docId, Long userId) {
        String key = "presence:doc:" + docId;
        redis.opsForHash().delete(key, userId.toString());
        presenceWebSocketHandler.broadcastPresence(docId, userId, "leave", null);
    }

    public List<PresenceInfo> getOnlineUsers(Long docId) {
        String key = "presence:doc:" + docId;
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        List<PresenceInfo> users = new ArrayList<>();

        entries.forEach((userId, value) -> {
            try {
                String val = value.toString();
                PresenceInfo info = new PresenceInfo();
                info.setUserId(Long.parseLong(userId.toString()));
                info.setUserName(extractValue(val, "userName"));
                info.setColor(extractValue(val, "color"));
                info.setJoinedAt(extractValue(val, "joinedAt"));
                users.add(info);
            } catch (Exception e) {
                // skip invalid entries
            }
        });

        return users;
    }

    private String extractValue(String str, String key) {
        int start = str.indexOf(key + "=");
        if (start < 0) return "";
        start += key.length() + 1;
        int end = str.indexOf(",", start);
        if (end < 0) end = str.indexOf("}", start);
        if (end < 0) end = str.length();
        return str.substring(start, end).trim();
    }
}
