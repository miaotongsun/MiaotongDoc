package com.miaotong.doc.event;

import org.springframework.context.ApplicationEvent;

/**
 * AI 配置刷新事件
 *
 * 当 AiConfigService 刷新配置缓存后，发布此事件。
 * AiService 监听此事件以重建 Spring AI ChatClient（避免双向依赖）。
 *
 * 解耦：原本 AiConfigService.refresh() 直接调 aiService.rebuildClient()
 *       → 形成循环依赖（AiConfigService → AiService → AiProxyService → AiConfigService）
 *       改用事件后：单向 AiConfigService → EventBus → AiService
 *
 * @since v2.7 修复
 */
public class AiConfigRefreshedEvent extends ApplicationEvent {

    /** 触发的来源（"admin", "api", "file-load"） */
    private final String source;

    public AiConfigRefreshedEvent(Object source) {
        super(source);
        this.source = String.valueOf(source);
    }

    public String getTriggerSource() {
        return source;
    }
}