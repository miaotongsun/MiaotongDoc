package com.miaotong.doc.service;

import com.miaotong.doc.entity.Notification;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.NotificationRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public void notify(Long fromUserId, Long toUserId, Long documentId, String type, String content) {
        Notification notification = new Notification();
        notification.setUserId(toUserId);
        notification.setFromUserId(fromUserId);
        notification.setDocumentId(documentId);
        notification.setType(type);
        notification.setContent(content);
        notificationRepository.save(notification);

        // 构建完整的推送数据（包含关联信息）
        Map<String, Object> data = new HashMap<>();
        data.put("id", notification.getId());
        data.put("type", notification.getType());
        data.put("content", notification.getContent());
        data.put("isRead", false);
        if (notification.getCreatedAt() != null) data.put("createdAt", notification.getCreatedAt().toString());
        if (fromUserId != null) {
            data.put("fromUserId", fromUserId);
            userRepository.findById(fromUserId).ifPresent(user -> {
                data.put("fromUserName", user.getRealName());
                data.put("fromEmployeeId", user.getEmployeeId());
            });
        }
        if (documentId != null) {
            data.put("documentId", documentId);
            documentRepository.findById(documentId).ifPresent(doc ->
                data.put("documentTitle", doc.getTitle())
            );
        }

        notificationWebSocketHandler.sendNotification(toUserId, data);
    }

    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsRead(notificationId, userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }
}
