package com.miaotong.doc.config;

import com.miaotong.doc.service.OpenApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动自检
 *
 * 当前任务：
 *   - 对外 API Key 自检（无 Key 告警、即将过期告警）
 *   - 后续可扩展更多启动检查项
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Integer.MIN_VALUE + 1)
public class StartupSelfCheck {

    private final OpenApiKeyService openApiKeyService;
    private final OpenApiProperties openApiProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (Boolean.TRUE.equals(openApiProperties.getStartupSelfCheck())) {
            try {
                openApiKeyService.selfCheck();
            } catch (Exception e) {
                log.error("对外 API Key 自检异常: {}", e.getMessage(), e);
            }
        }
    }
}