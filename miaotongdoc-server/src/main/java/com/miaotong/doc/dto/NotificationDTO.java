package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO {

    private Long id;
    private Long userId;
    private Long fromUserId;
    private String fromUserName;
    private String fromEmployeeId;
    private Long documentId;
    private String documentTitle;
    private String type;
    private String content;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
