package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.AiProxyService;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents/{id}/ai")
@RequiredArgsConstructor
public class DocumentAiController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final AiProxyService aiProxyService;

    private static final int MAX_CONTEXT_LENGTH = 12000; // 最大上下文长度

    /**
     * 文档问答
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String question = body.get("question");
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
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String text = body.get("text");
        String targetLang = body.getOrDefault("targetLang", "en");

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
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String text = body.get("text");
        String instruction = body.getOrDefault("instruction", "改写以下文本，保持原意但使用不同的表达方式");

        if (text == null || text.isBlank()) {
            throw new BusinessException("待改写文本不能为空");
        }

        String prompt = instruction + "。只输出改写后的文本，不要添加解释。\n\n"
                + "=== 原文 ===\n" + text + "\n=== 原文结束 ===";

        String rewritten = callLlm(prompt);
        return ResponseEntity.ok(Map.of("content", rewritten));
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
     * 调用 LLM（通过 AI 代理）
     */
    private String callLlm(String prompt) {
        try {
            // 构建 OpenAI 格式的请求体
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("messages", java.util.List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);

            String dataJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(requestBody);

            // 构建代理请求
            Map<String, Object> proxyBody = new java.util.HashMap<>();
            proxyBody.put("target", "/v1/chat/completions");
            proxyBody.put("method", "POST");
            proxyBody.put("headers", new java.util.HashMap<String, String>());
            proxyBody.put("data", dataJson);

            Object result = aiProxyService.proxy(proxyBody);
            if (result instanceof org.springframework.http.ResponseEntity<?> resp) {
                Object respBody = resp.getBody();
                if (respBody instanceof String bodyStr) {
                    Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(bodyStr, Map.class);
                    java.util.List<?> choices = (java.util.List<?>) parsed.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Object first = choices.get(0);
                        if (first instanceof Map<?, ?> choice) {
                            Object message = choice.get("message");
                            if (message instanceof Map<?, ?> msg) {
                                Object content = msg.get("content");
                                if (content instanceof String text) return text;
                            }
                        }
                    }
                }
            }
            return "AI 服务响应格式异常";
        } catch (Exception e) {
            log.error("调用 LLM 失败: {}", e.getMessage(), e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }
}
