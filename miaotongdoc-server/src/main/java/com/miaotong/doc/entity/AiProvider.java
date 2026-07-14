package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * AI Provider 配置实体
 * 支持 LLM / OCR / VISION 等多 Provider 动态配置
 *
 * @since v2.7
 */
@Data
@Entity
@Table(name = "mt_ai_provider", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_provider_type_name", columnNames = {"type", "name"})
})
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 类型：LLM / OCR_PADDLE / VISION / DOCLING / OCR_TESSERACT */
    @Column(nullable = false, length = 32)
    private String type;

    /** 显示名：OpenAI / DeepSeek / 阿里云 / 自建 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 服务地址（含 /v1） */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /** 加密存储的 API Key */
    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    /** 默认模型名 */
    @Column(name = "default_model", length = 128)
    private String defaultModel;

    /** 超时（秒） */
    @Column
    private Integer timeout = 300;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 同 type 多 Provider 时的默认选择 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /** 备注 */
    @Column(length = 512)
    private String remark;

    /** JSON 扩展（temperature / max_tokens 等） */
    @Column(columnDefinition = "TEXT")
    private String extra;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}