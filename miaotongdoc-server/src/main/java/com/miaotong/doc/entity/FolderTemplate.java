package com.miaotong.doc.entity;

import com.miaotong.doc.config.JsonbType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mt_folder_template")
public class FolderTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Type(JsonbType.class)
    @Column(columnDefinition = "JSONB")
    private String structure;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
