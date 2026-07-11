package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.ai.AiService;
import com.miaotong.doc.service.ai.AiService.ChatMessage;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.util.JwtUtil;
import com.miaotong.doc.service.AiProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@RestController
@RequestMapping("/api/documents/{id}/ai")
@RequiredArgsConstructor
public class DocumentAiController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final AiService aiService;
    private final AiProxyService aiProxyService;
    private final JwtUtil jwtUtil;

    private static final int MAX_CONTEXT_LENGTH = 12000; // 最大上下文长度
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 4000; // 自定义 system prompt 上限（防止滥用）

    /**
     * 默认 system prompt（chat-stream 场景）
     */
    private static final String DEFAULT_CHAT_SYSTEM_PROMPT_FORMAT =
            "你是一个专业的文档助手。用户正在阅读一篇文档，文档内容如下：\n\n=== 文档内容 ===\n%s\n=== 文档结束 ===\n\n请根据文档内容回答用户的问题。注意：\n1. 如果文档中有相关信息，请基于文档内容回答\n2. 如果文档中没有直接答案但有相关内容，可以进行合理推断\n3. 如果文档中完全没有相关信息，可以友好地说明，并尝试回答\n4. 回答要简洁、有条理，使用中文\n5. 如果是简单的问候（如'你好'、'hi'等），直接友好回应即可";

    /**
     * 默认 system prompt（generate-stream 场景）
     */
    private String buildDefaultGeneratePrompt(String content, String prompt) {
        if (content == null || content.isBlank()) {
            return "你是一个文档助手。用户要求你根据以下提示生成文档内容。请生成结构清晰、内容完整的文档。\n\n用户提示：" + prompt;
        }
        return "你是一个文档助手。用户正在编辑一篇文档，当前文档内容如下：\n\n=== 当前文档 ===\n" + content + "\n=== 文档结束 ===\n\n用户要求：" + prompt + "\n\n请根据用户要求和当前文档内容，生成合适的文档内容。如果用户要求续写，请在当前内容后继续。";
    }

    /**
     * 读取可选的 systemPrompt 入参（防止滥用：超长截断 + 日志）
     */
    private String resolveSystemPrompt(Object raw, String fallback) {
        if (raw == null) return fallback;
        String s = String.valueOf(raw);
        if (s.isBlank()) return fallback;
        if (s.length() > MAX_SYSTEM_PROMPT_LENGTH) {
            log.warn("systemPrompt 过长，已截断: 原长度={}", s.length());
            s = s.substring(0, MAX_SYSTEM_PROMPT_LENGTH);
        }
        return s;
    }

    /**
     * 安全地从 Object 取出 String（前端请求体字段可能为非 String 类型）
     */
    private String stringValue(Object v) {
        if (v == null) return null;
        return String.valueOf(v);
    }

    /**
     * 验证请求认证
     */
    private void validateAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new org.springframework.security.access.AccessDeniedException("未提供认证信息");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw new org.springframework.security.access.AccessDeniedException("Token无效");
        }
    }

    /**
     * CORS 预检请求处理
     */
    @RequestMapping(value = "/chat-stream", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .build();
    }

    /**
     * 文档问答（流式）
     */
    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public StreamingResponseBody chatStream(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                   HttpServletRequest request, HttpServletResponse response) {
        validateAuth(request);
        log.info("收到 chat-stream 请求: docId={}", id);

        String question = stringValue(body.get("question"));
        if (question == null || question.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        boolean enhanced = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enhanced", "false")));

        // 解析 history
        List<ChatMessage> history = null;
        Object historyObj = body.get("history");
        if (historyObj instanceof List<?> list && !list.isEmpty()) {
            history = ((List<?>) list).stream()
                    .filter(m -> m instanceof Map)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> msg = (Map<String, Object>) m;
                        return new ChatMessage(
                                String.valueOf(msg.getOrDefault("role", "user")),
                                String.valueOf(msg.getOrDefault("content", "")));
                    })
                    .toList();
        }

        String content = extractDocumentText(id);
        if (content.isBlank()) {
            return outputStream -> {
                try {
                    String json = "{\"type\":\"content\",\"content\":\"文档内容为空，无法回答问题。\"}";
                    outputStream.write(("event:content\ndata:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (Exception ignore) {}
            };
        }

        // 允许前端注入自定义 systemPrompt（用于 rewrite/translate/summarize 等场景复用本端点）
        String defaultPrompt = String.format(DEFAULT_CHAT_SYSTEM_PROMPT_FORMAT, content);
        String systemPrompt = resolveSystemPrompt(stringValue(body.get("systemPrompt")), defaultPrompt);

        // 编辑器实时 content（覆盖已持久化的 content，便于"未保存"也能引用最新内容）
        Object liveContent = body.get("content");
        if (liveContent instanceof String && !((String) liveContent).isBlank()) {
            systemPrompt = systemPrompt
                    .replace("=== 文档内容 ===\n" + content + "\n=== 文档结束 ===",
                             "=== 文档内容 ===\n" + liveContent + "\n=== 文档结束 ===");
        }

        // 用 StreamingResponseBody（最低层抽象，直接拿 OutputStream，每次 write 立即 flush）
        final String sysPromptFinal = systemPrompt;
        final String questionFinal = question;
        final List<ChatMessage> historyFinal = history;
        StreamingResponseBody emitter = outputStream -> {
            aiService.chatStreamSse(sysPromptFinal, questionFinal, historyFinal, outputStream);
        };

        // 必须在返回前设置正确的响应头
        try {
            response.setBufferSize(0);
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        } catch (Exception ignore) {}

        return emitter;
    }

    /**
     * 文档问答
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        validateAuth(httpRequest);
        String question = stringValue(body.get("question"));
        if (question == null || question.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        String content = extractDocumentText(id);
        if (content.isBlank()) {
            return ResponseEntity.ok(Map.of("content", "文档内容为空，无法回答问题。"));
        }

        // 截断过长的文档内容
        if (content.length() > MAX_CONTEXT_LENGTH) {
            content = content.substring(0, MAX_CONTEXT_LENGTH) + "\n...(文档内容过长，已截断)";
        }

        String prompt = "你是一个文档助手。请根据以下文档内容回答用户的问题。如果文档中没有相关信息，请说明。\n\n"
                + "=== 文档内容 ===\n" + content + "\n=== 文档结束 ===\n\n"
                + "用户问题：" + question;

        String answer = callLlm(prompt);
        return ResponseEntity.ok(Map.of("content", answer));
    }

    /**
     * 文档摘要
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, String>> summarize(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        validateAuth(httpRequest);
        String content = extractDocumentText(id);
        if (content.isBlank()) {
            return ResponseEntity.ok(Map.of("content", "文档内容为空，无法生成摘要。"));
        }

        if (content.length() > MAX_CONTEXT_LENGTH) {
            content = content.substring(0, MAX_CONTEXT_LENGTH) + "\n...(文档内容过长，已截断)";
        }

        String prompt = "请对以下文档内容生成一个简洁的摘要，突出关键信息和要点。使用与文档相同的语言。\n\n"
                + "=== 文档内容 ===\n" + content + "\n=== 文档结束 ===";

        String summary = callLlm(prompt);
        return ResponseEntity.ok(Map.of("content", summary));
    }

    /**
     * AI 翻译
     */
    @PostMapping("/translate")
    public ResponseEntity<Map<String, String>> translate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        validateAuth(httpRequest);
        String text = stringValue(body.get("text"));
        String targetLang = stringValue(body.getOrDefault("targetLang", "en"));

        // 如果没有提供文本，翻译全文
        if (text == null || text.isBlank()) {
            text = extractDocumentText(id);
        }

        if (text.isBlank()) {
            return ResponseEntity.ok(Map.of("content", "没有可翻译的内容。"));
        }

        if (text.length() > MAX_CONTEXT_LENGTH) {
            text = text.substring(0, MAX_CONTEXT_LENGTH);
        }

        String langName = switch (targetLang) {
            case "zh" -> "中文";
            case "en" -> "English";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            case "fr" -> "Français";
            case "de" -> "Deutsch";
            default -> targetLang;
        };

        String prompt = "请将以下文本翻译为" + langName + "。只输出翻译结果，不要添加解释。\n\n"
                + "=== 待翻译文本 ===\n" + text + "\n=== 文本结束 ===";

        String translated = callLlm(prompt);
        return ResponseEntity.ok(Map.of("content", translated));
    }

    /**
     * AI 改写
     */
    @PostMapping("/rewrite")
    public ResponseEntity<Map<String, String>> rewrite(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        validateAuth(httpRequest);
        String text = stringValue(body.get("text"));
        String instruction = stringValue(body.getOrDefault("instruction", "改写以下文本，保持原意但使用不同的表达方式"));

        if (text == null || text.isBlank()) {
            throw new BusinessException("待改写文本不能为空");
        }

        String prompt = instruction + "。只输出改写后的文本，不要添加解释。\n\n"
                + "=== 原文 ===\n" + text + "\n=== 原文结束 ===";

        String rewritten = callLlm(prompt);
        return ResponseEntity.ok(Map.of("content", rewritten));
    }

    /**
     * AI 内容生成（斜杠命令）
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        validateAuth(httpRequest);
        // 兼容两种字段名
        String prompt = stringValue(body.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            prompt = stringValue(body.get("question"));
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("提示词不能为空");
        }

        String content = extractDocumentText(id);
        String systemPrompt;
        if (body.get("systemPrompt") != null && !String.valueOf(body.get("systemPrompt")).isBlank()) {
            systemPrompt = resolveSystemPrompt(stringValue(body.get("systemPrompt")), "");
            if (systemPrompt.isBlank()) {
                systemPrompt = buildDefaultGeneratePrompt(content, prompt);
            }
        } else {
            systemPrompt = buildDefaultGeneratePrompt(content, prompt);
        }

        String generated = aiService.chat(systemPrompt, "请生成内容");
        return ResponseEntity.ok(Map.of("content", generated));
    }

    /**
     * AI 内容生成（流式，斜杠命令）
     *
     * 用于斜杠命令场景（如 /续写、/AI 生成），前端调用 generate-stream endpoint。
     * 注意：本方法返回 StreamingResponseBody，跟 chat-stream 一样真正逐字流式。
     */
    @PostMapping(value = "/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public StreamingResponseBody generateStream(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            HttpServletRequest request, HttpServletResponse response) {
        validateAuth(request);
        // 兼容两种字段名：原 generate 端点用 prompt，前端 aiSlashCommand 走 chat-stream 风格用 question
        String prompt = stringValue(body.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            prompt = stringValue(body.get("question"));
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("提示词不能为空");
        }

        String content = extractDocumentText(id);
        String defaultPrompt = buildDefaultGeneratePrompt(content, prompt);
        String systemPrompt = resolveSystemPrompt(stringValue(body.get("systemPrompt")), defaultPrompt);

        // 设置响应头（避免 nginx/tomcat 缓冲）
        try {
            response.setBufferSize(0);
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        } catch (Exception ignore) {}

        final String sysPromptFinal = systemPrompt;
        final String promptFinal = prompt;
        return outputStream -> {
            aiService.chatStreamSse(sysPromptFinal, promptFinal, null, outputStream);
        };
    }

    /**
     * 提取文档文本内容
     */
    private String extractDocumentText(Long docId) {
        Document doc = documentService.getDocument(docId);
        try {
            byte[] bytes = storageService.load(doc.getFilePath());

            if ("md".equals(doc.getFileType())) {
                return new String(bytes, StandardCharsets.UTF_8);
            } else if ("pdf".equals(doc.getFileType())) {
                try (PDDocument pdf = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(pdf);
                }
            } else if ("docx".equals(doc.getFileType())) {
                // 简单提取 docx 文本（从 ZIP 中的 word/document.xml）
                return extractTextFromDocx(bytes);
            } else {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("提取文档文本失败: docId={}", docId, e);
            return "";
        }
    }

    /**
     * 从 docx 提取纯文本
     */
    private String extractTextFromDocx(byte[] bytes) {
        try {
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    // 简单去除 XML 标签
                    return xml.replaceAll("<[^>]+>", " ")
                            .replaceAll("&amp;", "&")
                            .replaceAll("&lt;", "<")
                            .replaceAll("&gt;", ">")
                            .replaceAll("&quot;", "\"")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
            }
        } catch (Exception e) {
            log.warn("解析 docx 失败", e);
        }
        return "";
    }

    /**
     * 调用 LLM（直接通过 AiService，与智能编辑同一条成功路径）
     */
    private String callLlm(String prompt) {
        try {
            String result = aiService.chat(prompt);
            if (result == null || result.isEmpty()) {
                return "AI 服务响应为空";
            }
            if (result.startsWith("AI 服务调用失败") || result.startsWith("AI 服务响应格式异常")) {
                return result;
            }
            return result;
        } catch (Exception e) {
            log.error("调用 LLM 失败: {}", e.getMessage(), e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }
}
