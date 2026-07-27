package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对外 API 配置（v1 规范）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiProperties {

    /** 总开关（默认启用） */
    private Boolean enabled = true;

    /** 单 Key 默认限流（每分钟） */
    private Integer rateLimitDefault = 60;

    /** 幂等键缓存 TTL（小时） */
    private Integer idempotencyTtlHours = 24;

    /** 是否启用启动自检 */
    private Boolean startupSelfCheck = true;
}