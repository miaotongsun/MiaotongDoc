package com.miaotong.doc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.service.AiProxyService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * AI 聊天 SSE 控制器
 *
 * 替代原来的 WebSocket 实现，改用 Server-Sent Events：
 * - 浏览器原生 EventSource 友好（但这里 POST 用 fetch + ReadableStream 更灵活）
 * - 与项目内 /chat-stream、/generate-stream 风格一致
 * - 通过 nginx / 反向代理时穿透性更好
 * - 实现真正的逐字流式打字输出
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatSseController {

    private final ObjectMapper objectMapper;
    private final AiProxyService aiProxyService;
    private final JwtUtil jwtUtil;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        // 从 Authorization header 校验 token（用 JwtUtil 的现有方法）
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("未登录"));
            return e;
        }
        String token = auth.substring(7);
        if (!jwtUtil.validateToken(token)) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("Token 无效"));
            return e;
        }

        // 关闭 servlet buffer，强制 nginx 不缓冲
        response.setBufferSize(0);
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                String question = String.valueOf(body.getOrDefault("question", ""));
                String docIdStr = String.valueOf(body.getOrDefault("docId", ""));
                String realtimeContent = String.valueOf(body.getOrDefault("content", ""));

                if (question.isBlank()) {
                    sendEvent(emitter, "error", Map.of("message", "问题不能为空"));
                    emitter.complete();
                    return;
                }

                log.info("AI SSE chat: question={}", question.substring(0, Math.min(50, question.length())));

                // 文档内容由前端传，避免后端反向查库
                String docContent = "";
                boolean hasContent = realtimeContent != null && !realtimeContent.isBlank();
                if (hasContent) {
                    docContent = realtimeContent;
                }

                // 构建 system prompt
                String systemPrompt = buildSystemPrompt(hasContent, docContent);

                sendEvent(emitter, "docStatus", Map.of("hasContent", hasContent));

                // 调用 LLM
                String model = aiProxyService.getDefaultModel();
                String baseUrl = aiProxyService.getTargetUrl();
                String apiKey = aiProxyService.getApiKey();
                if (baseUrl != null && baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
                }

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.add(Map.of("role", "user", "content", question));

                String jsonBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", true,
                    "messages", messages
                ));

                URL url = new URL(baseUrl + "/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(300000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "text/event-stream");

                try (var os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code >= 400) {
                    String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    sendEvent(emitter, "error", Map.of("message", "AI 服务错误: " + code + " " + err));
                    emitter.complete();
                    return;
                }

                // 流式读取 SSE → 立即推送给客户端
                StringBuilder contentBuf = new StringBuilder();
                StringBuilder thinkBuf = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // isComplete 在 SseEmitter 中不存在，改用 try/catch 检测 sendMessage 异常
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue;
                        String data = trimmed.substring(5).trim();
                        if ("[DONE]".equals(data)) break;

                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode delta = node.path("choices").path(0).path("delta");
                            String reason = delta.path("reasoning_content").asText("");
                            String content = delta.path("content").asText("");

                            if (!reason.isEmpty()) {
                                thinkBuf.append(reason);
                                sendEvent(emitter, "thinking", Map.of("content", reason));
                            }
                            if (!content.isEmpty()) {
                                contentBuf.append(content);
                                sendEvent(emitter, "content", Map.of("content", content));
                            }
                        } catch (Exception ignore) {}
                    }
                }

                sendEvent(emitter, "done", Map.of(
                    "content", contentBuf.toString(),
                    "thinking", thinkBuf.toString()
                ));
                emitter.complete();
            } catch (Exception e) {
                log.error("AI chat stream error", e);
                try { sendEvent(emitter, "error", Map.of("message", e.getMessage())); } catch (Exception ignore) {}
                emitter.completeWithError(e);
            }
        }, "ai-chat-sse").start();

        return emitter;
    }

    private String buildSystemPrompt(boolean hasContent, String docContent) {
        if (hasContent) {
            return "You are a professional document assistant. The user is reading a document and has questions about it. Always answer based on the document content provided below.\n\n" +
                "## Document Content:\n" + docContent + "\n\n" +
                "## Your Capabilities:\n" +
                "1. Answer questions about the document content\n" +
                "2. Suggest edits, improvements, or modifications to the document\n" +
                "3. Help users understand complex parts of the document\n" +
                "4. Summarize, translate, or reorganize content\n\n" +
                "## Instructions:\n" +
                "1. Always answer based on the document content above\n" +
                "2. When suggesting edits, provide clear before/after examples\n" +
                "3. Use Markdown formatting for clarity\n" +
                "4. Be helpful and proactive in improving the document\n" +
                "5. Respond in the same language as the user's question";
        }
        return "You are a creative document assistant helping users create new content.";
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            // 客户端断开或已 complete，忽略
        }
    }
}
