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

    @Column(columnDefinition = "jsonb")
    private String parameters;

    @Column(name = "result_document_id")
    private Long resultDocumentId;

    @Column(name = "result_file_path", length = 500)
    private String resultFilePath;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
