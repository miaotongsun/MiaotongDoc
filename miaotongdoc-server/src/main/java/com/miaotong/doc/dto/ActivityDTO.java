package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityDTO {

    private Long id;
    private Long documentId;
    private String documentTitle;
    private Long userId;
    private String userName;
    private String action;
    private Long targetUserId;
    private String targetUserName;
    private String detail;
    private LocalDateTime createdAt;
}
