package com.miaotong.doc.controller;

import com.miaotong.doc.service.ai.AiService;
import com.miaotong.doc.service.ai.AiService.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 测试端点 - 用于验证 AI API 是否正常工作
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiTestController {

    private final AiService aiService;

    @GetMapping("/test")
    public String test() {
        return "AI Test endpoint working!";
    }

    /**
     * 测试 AI 流式对话（无需文档权限）
     */
    @PostMapping(value = "/test/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testChat(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "hello");
        log.info("Test chat request: question={}", question);

        // 解析 history 参数
        List<ChatMessage> history = null;
        Object historyObj = body.get("history");
        if (historyObj instanceof List<?> historyList && !historyList.isEmpty()) {
            history = historyList.stream()
                    .filter(m -> m instanceof Map)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> msg = (Map<String, String>) m;
                        String role = msg.getOrDefault("role", "user");
                        String content = msg.getOrDefault("content", "");
                        return new ChatMessage(role, content);
                    })
                    .toList();
            log.info("Parsed {} history messages", history.size());
        }

        return aiService.chatStream(null, question, history);
    }
}
