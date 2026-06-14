package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mt_document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_key", nullable = false, unique = true, length = 64)
    private String docKey;

    @Column(nullable = false, length = 500)
    private String title = "未命名文档";

    @Column(name = "doc_type", nullable = false, length = 10)
    private String docType;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize = 0L;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "current_version")
    private Integer currentVersion = 1;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "is_starred")
    private Boolean isStarred = false;

    @Column(name = "share_scope", length = 20)
    private String shareScope = "private";

    @Column(name = "signing_locked")
    private Boolean signingLocked = false;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
