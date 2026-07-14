package com.miaotong.doc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.PdfRecognizeService;
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
 * PDF 关键条款抽取 SSE 控制器
 *
 * 用例：从合同/协议 PDF 中自动抽取结构化字段（金额/日期/违约责任/...）
 * 强制 LLM 输出 JSON，前端按字段渲染成结构化卡片
 *
 * 接口：POST /api/pdf/{docId}/ai/extract-terms/stream
 *
 * 请求体：
 * {
 *   "fields": ["amount", "date", "party_a", "party_b", "breach_liability"]  // 可选字段
 * }
 *
 * 响应：SSE 流
 *   event: docStatus
 *   event: delta     { content: "..." }     // 流式 JSON 字符
 *   event: done      { terms: {...}, engine: "..." }
 *   event: error     { message: "..." }
 *
 * @since v2.6 PDF 关键条款抽取
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf/{docId}/ai/extract-terms")
@RequiredArgsConstructor
public class PdfExtractTermsSseController {

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final PdfRecognizeService pdfRecognizeService;
    private final com.miaotong.doc.service.AiProxyService aiProxyService;
    private final JwtUtil jwtUtil;

    /** 默认抽取字段（中文合同常见） */
    private static final List<String> DEFAULT_FIELDS = List.of(
            "amount",          // 总金额
            "currency",        // 币种
            "effective_date",  // 生效日期
            "expire_date",     // 到期日期
            "party_a",         // 甲方
            "party_b",         // 乙方
            "breach_liability",// 违约责任
            "payment_terms",   // 付款方式
            "delivery_date"    // 交付日期
    );

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter extractStream(
            @PathVariable Long docId,
            @RequestBody(required = false) Map<String, Object> body,
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
                // 1) 解析请求字段（默认字段）
                List<String> fields = DEFAULT_FIELDS;
                if (body != null && body.get("fields") instanceof List) {
                    fields = (List<String>) body.get("fields");
                }

                // 2) 读取文档 markdown（识别结果）
                Document doc = documentService.getDocument(docId);
                if (!"pdf".equals(doc.getFileType())) {
                    sendEvent(emitter, "error", Map.of("message", "该文档不是 PDF"));
                    emitter.complete();
                    return;
                }
                Map<String, String> markdown = documentService.getPdfMarkdown(docId); // 每页 markdown
                if (markdown == null || markdown.isEmpty()) {
                    sendEvent(emitter, "error", Map.of("message", "文档尚未识别，请先识别 OCR"));
                    emitter.complete();
                    return;
                }

                // 拼接所有页 markdown
                StringBuilder fullMd = new StringBuilder();
                markdown.forEach((pageNum, content) -> {
                    if (content != null && !content.isBlank()) {
                        fullMd.append("【第 ").append(pageNum).append(" 页】\n");
                        fullMd.append(content).append("\n\n");
                    }
                });
                final String fullMarkdown = fullMd.toString();

                log.info("Extract terms: docId={}, fields={}, contentLen={}",
                        docId, fields.size(), fullMarkdown.length());

                // 3) 构建系统提示（强制 JSON 输出）
                String systemPrompt = buildSystemPrompt(fields);
                String userPrompt = buildUserPrompt(fullMarkdown, fields);

                // 4) 调用 LLM
                String model = aiProxyService.getDefaultModel();
                String baseUrl = aiProxyService.getTargetUrl();
                String apiKey = aiProxyService.getApiKey();
                if (baseUrl != null && baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
                }

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.add(Map.of("role", "user", "content", userPrompt));

                Map<String, Object> requestBody2 = new HashMap<>();
                requestBody2.put("model", model);
                requestBody2.put("stream", true);
                requestBody2.put("temperature", 0.1);  // 极低温度，保证结构稳定
                requestBody2.put("max_tokens", 2000);
                // 尝试启用 JSON mode（如果模型支持）
                requestBody2.put("response_format", Map.of("type", "json_object"));
                requestBody2.put("messages", messages);

                String jsonBody = objectMapper.writeValueAsString(requestBody2);

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
                    log.error("LLM 调用失败: HTTP {} {}", code, err);
                    sendEvent(emitter, "error", Map.of("message", "LLM 服务错误: " + code));
                    emitter.complete();
                    return;
                }

                sendEvent(emitter, "docStatus", Map.of(
                        "docId", docId,
                        "fields", fields,
                        "model", model));

                // 5) 流式读取 + 累积 JSON
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
                                log.debug("解析 LLM 流失败: {}", data);
                            }
                        }
                    }
                }

                // 6) 解析最终 JSON
                Map<String, Object> terms = parseTerms(contentBuf.toString());

                sendEvent(emitter, "done", Map.of(
                        "engine", "terms-extract",
                        "model", model,
                        "terms", terms,
                        "fieldCount", terms.size()));
                emitter.complete();

            } catch (Exception e) {
                log.error("Extract terms 失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "服务异常: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }, "pdf-extract-terms-" + docId).start();

        return emitter;
    }

    /**
     * 系统提示：强制 JSON 输出 + 字段定义
     */
    private String buildSystemPrompt(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的合同条款抽取助手。请从给定的合同文本中抽取以下字段，并以严格 JSON 格式输出。\n\n");
        sb.append("【抽取字段】\n");
        for (String field : fields) {
            sb.append("- ").append(field).append("\n");
        }
        sb.append("\n【输出格式】\n");
        sb.append("严格输出 JSON 对象，键名使用上述字段英文名，值为字符串。\n");
        sb.append("示例：{\"amount\": \"100万元\", \"party_a\": \"甲公司\"}\n\n");
        sb.append("【规则】\n");
        sb.append("1. 找不到的字段，值设为 null 或 \"未提及\"\n");
        sb.append("2. 金额保留原文（不转换为数字）\n");
        sb.append("3. 日期保留原文格式\n");
        sb.append("4. 不要输出 JSON 以外的解释文字\n");
        sb.append("5. 仅基于提供的合同文本，不要编造\n");
        return sb.toString();
    }

    /**
     * 用户提示：附合同文本（限制长度避免超 token）
     */
    private String buildUserPrompt(String markdown, List<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("【待抽取的合同文本】\n\n");
        // 限制最长 12000 字符（约 4000 tokens），超出截断
        int maxLen = Math.min(markdown.length(), 12000);
        if (markdown.length() > maxLen) {
            sb.append(markdown, 0, maxLen);
            sb.append("\n\n...(文本过长已截断)");
        } else {
            sb.append(markdown);
        }
        sb.append("\n\n【请按上述字段抽取，并以 JSON 格式输出】");
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的 JSON 文本（兼容 markdown 代码块包裹的情况）
     */
    private Map<String, Object> parseTerms(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) return result;

        String trimmed = content.trim();
        // 去掉 markdown 代码块包裹
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();

        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isObject()) {
                node.fields().forEachRemaining(e ->
                        result.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
            }
        } catch (Exception e) {
            log.warn("解析抽取 JSON 失败: {} | 原文: {}", e.getMessage(), trimmed);
        }
        return result;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.debug("SSE 发送失败 ({}): {}", name, e.getMessage());
        }
    }
}