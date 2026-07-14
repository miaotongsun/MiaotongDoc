package com.miaotong.doc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.service.AiProxyService;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OCR 结果 AI 优化 SSE 控制器
 *
 * 用例：OCR 识别后文本常常含噪声（页眉/页脚/乱码/错误断行）
 *       用 LLM 二次清理，去噪 + 段落重建
 *
 * 接口：POST /api/pdf/{docId}/ai/optimize-ocr/stream
 *
 * 请求体：
 * {
 *   "markdown": "...",   // 必填，待优化的 OCR 文本
 *   "pageNum": 1          // 可选，按页优化
 * }
 *
 * 响应：SSE 流
 *   event: docStatus
 *   event: delta     { content: "..." }
 *   event: done      { engine, optimizedLength, savedChars }
 *   event: error     { message }
 *
 * @since v2.6 OCR AI 优化
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf/{docId}/ai/optimize-ocr")
@RequiredArgsConstructor
public class PdfOptimizeOcrSseController {

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final AiProxyService aiProxyService;
    private final JwtUtil jwtUtil;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter optimizeStream(
            @PathVariable Long docId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 鉴权
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

        response.setBufferSize(0);
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                // 1) 解析请求
                Object mdObj = body.get("markdown");
                if (mdObj == null) {
                    sendEvent(emitter, "error", Map.of("message", "markdown 不能为空"));
                    emitter.complete();
                    return;
                }
                String markdown = String.valueOf(mdObj);
                if (markdown.isBlank()) {
                    sendEvent(emitter, "error", Map.of("message", "markdown 不能为空"));
                    emitter.complete();
                    return;
                }
                Object pageNumObj = body.get("pageNum");
                Integer pageNum = pageNumObj == null ? null : Integer.valueOf(String.valueOf(pageNumObj));

                int originalLen = markdown.length();
                log.info("Optimize OCR: docId={}, pageNum={}, originalLen={}",
                        docId, pageNum, originalLen);

                // 2) 构建 prompt
                String systemPrompt = buildSystemPrompt();
                String userPrompt = buildUserPrompt(markdown, pageNum);

                // 3) 调用 LLM
                String model = aiProxyService.getDefaultModel();
                String baseUrl = aiProxyService.getTargetUrl();
                String apiKey = aiProxyService.getApiKey();
                if (baseUrl != null && baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
                }

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.add(Map.of("role", "user", "content", userPrompt));

                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("model", model);
                reqBody.put("stream", true);
                reqBody.put("temperature", 0.2);
                reqBody.put("max_tokens", 3000);
                reqBody.put("messages", messages);

                String jsonBody = objectMapper.writeValueAsString(reqBody);

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
                    log.error("OCR 优化 LLM 调用失败: HTTP {} {}", code, err);
                    sendEvent(emitter, "error", Map.of("message", "LLM 服务错误: " + code));
                    emitter.complete();
                    return;
                }

                sendEvent(emitter, "docStatus", Map.of(
                        "docId", docId,
                        "pageNum", pageNum == null ? -1 : pageNum,
                        "model", model,
                        "originalLen", originalLen));

                // 4) 流式读取
                StringBuilder optimizedBuf = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) break;
                            try {
                                JsonNode node = objectMapper.readTree(data);
                                JsonNode delta = node.path("choices").path(0).path("delta");
                                String content = delta.path("content").asText("");
                                if (!content.isEmpty()) {
                                    optimizedBuf.append(content);
                                    sendEvent(emitter, "delta", Map.of("content", content));
                                }
                            } catch (Exception e) {
                                log.debug("解析 LLM 流失败: {}", data);
                            }
                        }
                    }
                }

                String optimized = stripMarkdownCodeBlock(optimizedBuf.toString());
                int optimizedLen = optimized.length();

                sendEvent(emitter, "done", Map.of(
                        "engine", "ocr-optimize",
                        "model", model,
                        "optimizedLength", optimizedLen,
                        "originalLength", originalLen,
                        "savedChars", originalLen - optimizedLen));
                emitter.complete();

            } catch (Exception e) {
                log.error("OCR 优化失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "服务异常: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }, "pdf-optimize-ocr-" + docId).start();

        return emitter;
    }

    /**
     * 系统提示：强调清理规则
     */
    private String buildSystemPrompt() {
        return "你是 OCR 结果清理助手。请按以下规则清理给定的 OCR 文本：\n\n" +
                "【清理规则】\n" +
                "1. 删除页眉、页脚、页码（如 \"第 3 页\"、\"Page 1\"）\n" +
                "2. 删除重复出现的标题/分隔线（如下划线、星号）\n" +
                "3. 合并被错误断行的段落（同一段不应换行）\n" +
                "4. 修正明显的错别字（基于上下文）\n" +
                "5. 保留表格结构（用 Markdown 表格）\n" +
                "6. 保留原始段落顺序和层级\n\n" +
                "【输出要求】\n" +
                "- 直接输出清理后的 Markdown，不要任何解释\n" +
                "- 不要输出 \"以下是清理后的内容：\" 这类前缀\n" +
                "- 如果原文已是干净的，直接原样输出\n";
    }

    /**
     * 用户提示：附 OCR 文本（限制长度）
     */
    private String buildUserPrompt(String markdown, Integer pageNum) {
        StringBuilder sb = new StringBuilder();
        if (pageNum != null) {
            sb.append("【第 ").append(pageNum).append(" 页 OCR 结果】\n\n");
        } else {
            sb.append("【OCR 全文结果】\n\n");
        }
        // 限制最长 8000 字符（约 2700 tokens）
        int maxLen = Math.min(markdown.length(), 8000);
        if (markdown.length() > maxLen) {
            sb.append(markdown, 0, maxLen);
            sb.append("\n\n...(文本过长已截断)");
        } else {
            sb.append(markdown);
        }
        sb.append("\n\n【请清理以上 OCR 结果】");
        return sb.toString();
    }

    /**
     * 去掉 markdown 代码块包裹
     */
    private String stripMarkdownCodeBlock(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.startsWith("```markdown")) {
            trimmed = trimmed.substring(10);
        } else if (trimmed.startsWith("```md")) {
            trimmed = trimmed.substring(5);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.debug("SSE 发送失败 ({}): {}", name, e.getMessage());
        }
    }
}