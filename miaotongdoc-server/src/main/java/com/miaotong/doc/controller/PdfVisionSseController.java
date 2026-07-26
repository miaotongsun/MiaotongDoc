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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PDF 视觉问答（VLM）SSE 控制器
 *
 * 用例：用户在 PDF 上框选区域 → 前端截图（base64）→ 调用本接口
 *       → 后端用多模态 LLM 识别图片并回答问题
 *
 * 接口：POST /api/pdf/{docId}/ai/vision/stream
 *
 * 请求体：
 * {
 *   "question": "这段文字写了什么？",
 *   "image":   "data:image/png;base64,iVBOR...",   // 用户框选的 PDF 区域
 *   "context": "第 3 页"                            // 上下文描述（可选）
 * }
 *
 * 响应：SSE 流（与 /api/ai/chat/stream 风格一致）
 *   event: docStatus
 *   event: delta     { content: "..." }
 *   event: done      { engine: "vlm" }
 *   event: error     { message: "..." }
 *
 * @since v2.6 PDF VLM 视觉问答
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf/{docId}/ai/vision")
@RequiredArgsConstructor
public class PdfVisionSseController {

    private final ObjectMapper objectMapper;
    private final AiProxyService aiProxyService;
    private final JwtUtil jwtUtil;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter visionStream(
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
                String question = String.valueOf(body.getOrDefault("question", ""));
                Object imageObj = body.get("image");
                String image = imageObj == null ? "" : String.valueOf(imageObj);
                Object contextObj = body.get("context");
                String context = contextObj == null ? "" : String.valueOf(contextObj);

                if (question.isBlank()) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "INVALID_PARAMS",
                            "message", "问题不能为空"));
                    emitter.complete();
                    return;
                }
                if (image.isBlank() || !image.startsWith("data:image/")) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "INVALID_PARAMS",
                            "message", "图片格式无效，需要 data:image/xxx;base64,..."));
                    emitter.complete();
                    return;
                }

                // OCR AI 改造 v2:VISION Provider 未配置检测
                if (!aiProxyService.isVisionConfigured()) {
                    log.warn("VISION Provider 未配置,终止 SSE 流: docId={}", docId);
                    sendEvent(emitter, "error", Map.of(
                            "code", "AI_NOT_CONFIGURED",
                            "message", "视觉问答 AI 服务未配置,请前往管理后台 → AI 配置 → 添加 VISION 类型 Provider"));
                    emitter.complete();
                    return;
                }

                log.info("VLM vision: docId={}, question={}, context={}, imageSize={}KB",
                        docId, question.substring(0, Math.min(50, question.length())),
                        context, image.length() / 1024);

                String systemPrompt = buildSystemPrompt(docId, context);
                Object userContent = buildUserContent(question, image, context);

                // 准备消息（OpenAI 兼容格式）
                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.add(Map.of("role", "user", "content", userContent));

                // OCR AI 改造 v2:从 AiProxyService 取 VISION 配置(与 MD 编辑器同源)
                String model = aiProxyService.getVisionModel();
                String baseUrl = aiProxyService.getVisionUrl();
                String apiKey = aiProxyService.getVisionKey();
                if (baseUrl != null && baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
                }

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("stream", true);
                requestBody.put("temperature", 0.3);  // VLM 用低温度，更精确
                requestBody.put("max_tokens", 1500);
                requestBody.put("messages", messages);

                String jsonBody = objectMapper.writeValueAsString(requestBody);

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
                    log.error("VLM 调用失败: HTTP {} {}", code, err);
                    sendEvent(emitter, "error", Map.of("message", "VLM 服务错误: " + code));
                    emitter.complete();
                    return;
                }

                sendEvent(emitter, "docStatus", Map.of(
                        "hasImage", true,
                        "model", model,
                        "context", context));

                // 流式读取 SSE
                StringBuilder contentBuf = new StringBuilder();
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
                                    contentBuf.append(content);
                                    sendEvent(emitter, "delta", Map.of("content", content));
                                }
                            } catch (Exception e) {
                                log.debug("解析 VLM 流失败: {}", data);
                            }
                        }
                    }
                }

                sendEvent(emitter, "done", Map.of(
                        "engine", "vlm",
                        "model", model,
                        "totalChars", contentBuf.length()));
                emitter.complete();

            } catch (Exception e) {
                log.error("VLM vision 失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "VLM 服务异常: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }, "pdf-vision-stream-" + docId).start();

        return emitter;
    }

    /**
     * VISION 模型选择已统一到 AiProxyService.getVisionModel()(OCR AI 改造 v2)
     * 保留此方法为兼容旧调用,实际不再使用
     */
    @Deprecated
    private String pickVisionModel() {
        return aiProxyService.getVisionModel();
    }

    /**
     * 系统提示：强调只回答图片内容
     */
    private String buildSystemPrompt(Long docId, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的 PDF 视觉问答助手。用户会提供 PDF 文档某一区域的截图，并提出问题。\n");
        sb.append("请严格基于图片内容回答，如果图片中没有相关信息，请明确说明。\n");
        sb.append("\n回答要求：\n");
        sb.append("1. 准确：内容必须与图片一致，不要编造\n");
        sb.append("2. 简洁：直接回答问题，避免冗余\n");
        sb.append("3. 结构：合同/表格等用 Markdown 格式\n");
        sb.append("4. 中文：默认中文回答\n");
        if (!context.isBlank()) {
            sb.append("\n上下文：").append(context).append("\n");
        }
        return sb.toString();
    }

    /**
     * 用户消息：OpenAI 多模态格式（text + image_url）
     */
    private Object buildUserContent(String question, String image, String context) {
        // 多模态消息 content 是数组
        List<Map<String, Object>> parts = new ArrayList<>();
        // 文本部分
        StringBuilder text = new StringBuilder();
        if (!context.isBlank()) text.append("【位置】").append(context).append("\n\n");
        text.append("【问题】").append(question);
        parts.add(Map.of("type", "text", "text", text.toString()));
        // 图片部分
        parts.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", image)
        ));
        return parts;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.debug("SSE 发送失败 ({}): {}", name, e.getMessage());
        }
    }
}