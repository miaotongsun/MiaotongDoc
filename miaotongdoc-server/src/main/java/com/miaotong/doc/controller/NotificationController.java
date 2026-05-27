package com.miaotong.doc.controller;

import com.miaotong.doc.dto.NotificationDTO;
import com.miaotong.doc.entity.Notification;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.NotificationService;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.repository.DocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Page<Notification> notifications = notificationService.getUserNotifications(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(notifications.map(this::toDTO));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(Map.of("message", "已读"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "全部已读"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    private NotificationDTO toDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setUserId(notification.getUserId());
        dto.setFromUserId(notification.getFromUserId());
        dto.setDocumentId(notification.getDocumentId());
        dto.setType(notification.getType());
        dto.setContent(notification.getContent());
        dto.setIsRead(notification.getIsRead());
        dto.setReadAt(notification.getReadAt());
        dto.setCreatedAt(notification.getCreatedAt());

        if (notification.getFromUserId() != null) {
            userRepository.findById(notification.getFromUserId()).ifPresent(user ->
                dto.setFromUserName(user.getRealName())
            );
        }
        if (notification.getDocumentId() != null) {
            documentRepository.findById(notification.getDocumentId()).ifPresent(doc ->
                dto.setDocumentTitle(doc.getTitle())
            );
        }

        return dto;
    }
}
