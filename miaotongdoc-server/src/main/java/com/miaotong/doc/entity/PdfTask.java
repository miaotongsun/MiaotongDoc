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
@Table(name = "mt_pdf_task")
public class PdfTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Convert(converter = com.miaotong.doc.config.JsonbConverter.class)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @Column(name = "result_document_id")
    private Long resultDocumentId;

    @Column(name = "result_file_path", length = 500)
    private String resultFilePath;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "progress")
    private Integer progress = 0;

    @Column(name = "current_page")
    private Integer currentPage = 0;

    @Column(name = "total_pages")
    private Integer totalPages = 0;

    @Column(name = "model", length = 50)
    private String model = "mobile";

    @Column(name = "language", length = 20)
    private String language = "ch";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
}