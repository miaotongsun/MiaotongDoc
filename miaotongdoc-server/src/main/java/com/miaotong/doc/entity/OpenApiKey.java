package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对外 API Key 实体（v1 规范）
 *
 * 密钥生命周期：
 *   1. 管理员在后台颁发 → 生成 ak_ 前缀的随机密钥 → 仅返回一次明文
 *   2. 外部系统调用接口时 → 通过 X-API-Key 头传递 → 数据库明文比对
 *   3. 管理员吊销 → 设置 enabled=false 和 revoked_at → 立即生效
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_openapi_key", indexes = {
        @Index(name = "idx_openapi_key_access", columnList = "access_key"),
        @Index(name = "idx_openapi_key_enabled", columnList = "enabled")
})
public class OpenApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 密钥明文（ak_ + 32位随机字符），每次请求匹配用 */
    @Column(name = "access_key", nullable = false, unique = true, length = 64)
    private String accessKey;

    /** 密钥前 8 位用于列表显示，避免明文泄漏 */
    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix;

    /** 密钥用途描述 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 外部系统标识 */
    @Column(name = "owner_system", length = 100)
    private String ownerSystem;

    /** 联系人/邮箱 */
    @Column(length = 200)
    private String contact;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** 过期时间（可空，永不过期） */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 单 Key 限流（每分钟） */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 60;

    /** IP 白名单（逗号分隔，可空） */
    @Column(name = "allowed_ips", columnDefinition = "TEXT")
    private String allowedIps;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 吊销时间（软删除标记） */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}