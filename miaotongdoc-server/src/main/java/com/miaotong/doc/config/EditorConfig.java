package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "editor")
public class EditorConfig {

    /** 编辑器 JWT 密钥 */
    private String jwtSecret;

    /** 编辑器服务 URL */
    private String serverUrl;

    /** 编辑器回调 URL */
    private String callbackUrl;

    /** 文档下载 URL */
    private String downloadUrl;
}
