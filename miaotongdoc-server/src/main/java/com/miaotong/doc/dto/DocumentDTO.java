package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDTO {

    private Long id;
    private String docKey;
    private String title;
    private String docType;
    private String fileType;
    private Long fileSize;
    private String fileHash;
    private Long ownerUserId;
    private String ownerName;
    private Long departmentId;
    private String departmentName;
    private String status;
    private Integer currentVersion;
    private Boolean isStarred;
    private String shareScope;
    private Boolean signingLocked;
    private String currentUserPermission;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private String updatedByName;
    private Long folderId;
    private String templateName;
}
