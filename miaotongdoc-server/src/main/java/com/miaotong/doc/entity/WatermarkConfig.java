package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mt_watermark_config")
public class WatermarkConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name = "default";

    @Column(name = "is_enabled")
    private Boolean isEnabled = false;

    @Column(name = "text_template", length = 500)
    private String textTemplate = "{username} {datetime}";

    @Column(name = "font_size")
    private Integer fontSize = 30;

    @Column(name = "font_color", length = 20)
    private String fontColor = "#CCCCCC";

    @Column
    private Integer rotation = -45;

    @Column
    private Float opacity = 0.3f;

    @Column(name = "position", length = 20)
    private String position = "center";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
