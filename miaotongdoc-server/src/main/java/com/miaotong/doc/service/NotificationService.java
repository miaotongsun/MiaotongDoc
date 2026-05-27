package com.miaotong.doc.service;

import com.miaotong.doc.entity.Notification;
import com.miaotong.doc.repository.NotificationRepository;
import com.miaotong.doc.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @Transactional
    public void notify(Long fromUserId, Long toUserId, Long documentId, String type, String content) {
        Notification notification = new Notification();
        notification.setUserId(toUserId);
        notification.setFromUserId(fromUserId);
        notification.setDocumentId(documentId);
        notification.setType(type);
        notification.setContent(content);
        notificationRepository.save(notification);

        notificationWebSocketHandler.sendNotification(toUserId, notification);
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
