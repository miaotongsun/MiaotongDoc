package com.miaotong.doc.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.service.AiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 统一 AI 能力服务
 * 基于 Spring AI ChatClient，动态使用 AiProxyService 中的配置，带 Redis 缓存
 */
@Slf4j
@Service
public class AiService {

    private final AiProxyService aiProxyService;
    private final DocumentContentService contentService;
    private final AiCacheService aiCacheService;
    private final AiMonitor aiMonitor;
    private final ObjectMapper objectMapper;
    private ChatClient chatClient;
    private final RestTemplate restTemplate;
    private final HttpServletResponse httpResponse;

    public AiService(AiProxyService aiProxyService, DocumentContentService contentService,
                     AiCacheService aiCacheService, AiMonitor aiMonitor, ObjectMapper objectMapper,
                     @Autowired(required = false) HttpServletResponse httpResponse) {
        this.aiProxyService = aiProxyService;
        this.contentService = contentService;
        this.aiCacheService = aiCacheService;
        this.aiMonitor = aiMonitor;
        this.objectMapper = objectMapper;
        this.httpResponse = httpResponse;
        this.restTemplate = new RestTemplate();
        rebuildClient();
        log.info("AiService 初始化完成");
    }

    /**
     * v2.7 监听 AiConfigRefreshedEvent（管理后台修改 Provider 后触发）
     * 调用 rebuildClient() 让 ChatClient 立即使用最新配置
     */
    @org.springframework.context.event.EventListener
    public void onAiConfigRefreshed(com.miaotong.doc.event.AiConfigRefreshedEvent event) {
        log.info("收到 AiConfigRefreshedEvent（来源: {}），重建 ChatClient", event.getTriggerSource());
        try {
            rebuildClient();
        } catch (Exception e) {
            log.warn("重建 ChatClient 失败: {}", e.getMessage());
        }
    }

    /**
     * 重建 ChatClient（使用 AiProxyService 中的最新配置）
     */
    public void rebuildClient() {
        String baseUrl = aiProxyService.getTargetUrl();
        String apiKey = aiProxyService.getApiKey();

        if (baseUrl == null || baseUrl.isEmpty()) {
            log.warn("AI 服务未配置 targetUrl，使用默认配置");
            baseUrl = "https://api.openai.com";
        }

        // 确保 baseUrl 不以 /v1 结尾（Spring AI 会自动添加）
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey != null ? apiKey : "sk-placeholder")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();

        this.chatClient = ChatClient.builder(chatModel).build();
        log.info("AiService ChatClient 已重建, baseUrl: {}, model: {}", baseUrl, getCurrentModel());
    }

    /**
     * 获取当前配置的模型名称
     */
    private String getCurrentModel() {
        String model = aiProxyService.getDefaultModel();
        return (model != null && !model.isEmpty()) ? model : "Qwen36-35B-A3B";
    }

    private String getBaseUrl() {
        String baseUrl = aiProxyService.getTargetUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com";
        }
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        return baseUrl;
    }

    private String getApiKey() {
        return aiProxyService.getApiKey() != null ? aiProxyService.getApiKey() : "sk-placeholder";
    }

    /**
     * 判断模型是否支持思考链（reasoning_content / enable_thinking）
     * - DeepSeek-R1 / DeepSeek-V3：默认 reasoning_content（不需要 enable_thinking）
     * - Qwen3 / Qwen3-Coder / Qwen3-235B 等：需要 enable_thinking=true
     * - Qwen36-35B-A3B 等带 "A3B" 后缀的：实测不支持 thinking，强制普通模式
     * - GPT-4 / Claude：通常不支持思考
     */
    private boolean isThinkingCapableModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        // 显式黑名单：这些模型是普通指令模型，发了 enable_thinking 会导致输出混乱
        // A3B 命名规则：Qwen/xxx-A3B 表示 Activation 3B 的 MoE，普通指令模式
        if (m.contains("a3b")) return false;
        if (m.contains("instruct") && !m.contains("thinking") && !m.contains("qwen3")) return false;
        if (m.contains("coder") && m.contains("qwen2") || m.contains("qwen2.5") && m.contains("coder")) return false;
        // Qwen3 系列：明确支持 thinking
        if (m.contains("qwen3") || m.contains("qwen-3")) return true;
        if (m.startsWith("qwen3-")) return true;
        // QwQ / QVQ 系列：明确支持 thinking
        if (m.startsWith("qwq") || m.contains("qvq")) return true;
        // DeepSeek-R1：默认就支持
        if (m.contains("deepseek-r1") || m.contains("deepseek_r1")) return true;
        // 通用 Qwen Plus / Max：通常支持
        if (m.startsWith("qwen") && (m.contains("plus") || m.contains("max"))) return true;
        return false;
    }

    /**
     * 获取带动态模型配置的 ChatClient 请求
     */
    private ChatClient.ChatClientRequestSpec getChatSpec() {
        String model = getCurrentModel();
        return chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .maxTokens(2000)
                        .build());
    }

    // ===== 基础对话能力 =====

    /**
     * 同步对话（无系统提示）
     */
    public String chat(String userPrompt) {
        return chat(null, userPrompt);
    }

    /**
     * 同步对话（带系统提示）
     */
    public String chat(String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();
        String model = getCurrentModel();
        try {
            var spec = getChatSpec();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                spec = spec.system(systemPrompt);
            }
            String result = spec.user(userPrompt).call().content();
            long duration = System.currentTimeMillis() - start;
            aiMonitor.recordSuccess("chat", model, duration, -1, -1);
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            aiMonitor.recordError("chat", model, duration, e.getClass().getSimpleName());
            log.error("AI 对话失败: {}", e.getMessage(), e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        return chatStream(systemPrompt, userPrompt, null);
    }

    /**
     * 流式对话（带历史）
     * 使用 HttpURLConnection 直接调用 API，避免 Spring AI 的严格解析问题
     */
    public Flux<String> chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        return Flux.create(emitter -> {
            try {
                // 构建消息列表
                List<Map<String, String>> messages = new java.util.ArrayList<>();
                if (systemPrompt != null && !systemPrompt.isBlank()) {
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                }
                if (history != null) {
                    for (ChatMessage msg : history) {
                        messages.add(Map.of("role", msg.role(), "content", msg.content()));
                    }
                }
                messages.add(Map.of("role", "user", "content", userPrompt));

                String model = getCurrentModel();
                Map<String, Object> requestBody = Map.of(
                        "model", model,
                        "stream", true,
                        "messages", messages
                );

                String apiUrl = getBaseUrl() + "/v1/chat/completions";
                String apiKey = getApiKey();
                String jsonBody = objectMapper.writeValueAsString(requestBody);

                log.info("AI 流式请求: url={}, model={}", apiUrl, model);

                // 使用 HttpURLConnection 进行流式请求
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(300000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "text/event-stream");

                // 发送请求体
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                // 读取响应
                log.info("等待 AI API 响应...");
                int responseCode = conn.getResponseCode();
                log.info("AI API 响应码: {}", responseCode);
                if (responseCode >= 400) {
                    String error = "";
                    try {
                        error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    } catch (Exception ex) {
                        error = "无法读取错误响应";
                    }
                    log.error("AI API 错误: {} - {}", responseCode, error);
                    emitter.error(new RuntimeException("AI API 错误: " + responseCode + " - " + error));
                    conn.disconnect();
                    return;
                }

                // 流式读取上游 SSE 响应
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (emitter.isCancelled()) break;
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.startsWith("data:")) {
                            String data = trimmed.substring(5).trim();
                            if ("[DONE]".equals(data)) break;
                            try {
                                JsonNode node = objectMapper.readTree(data);
                                JsonNode choices = node.path("choices");
                                if (choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).path("delta");
                                    String content = delta.path("content").asText("");
                                    if (content.isEmpty()) {
                                        content = delta.path("reasoning_content").asText("");
                                    }
                                    if (!content.isEmpty()) {
                                        emitter.next(objectMapper.writeValueAsString(
                                            Map.of("content", content)) + "\n\n");
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("解析 chunk 失败: {}",
                                    data.substring(0, Math.min(80, data.length())));
                            }
                        }
                    }
                }

                emitter.complete();
                conn.disconnect();
            } catch (Exception e) {
                log.error("AI 流式对话失败: {}", e.getMessage(), e);
                emitter.error(e);
            }
        });
    }

    /**
     * 直接用 OutputStream 写入 SSE 字节流
     *
     * 关键改进：不再用 SseEmitter/BlockingQueue/推送线程（这些都被 Tomcat
     * 内部缓冲），而是直接在 StreamingResponseBody 提供的 OutputStream 上
     * 写 SSE 字节 + 立即 flush()。这样绕开 Tomcat 抽象层，实现真正的逐字流式。
     *
     * 流程：
     * 1. 主线程同步读 LLM SSE
     * 2. 每解析一个 token，直接构造 SSE 字节，写入 OutputStream
     * 3. 立即 flush()，让数据立即推到 Tomcat NIO socket
     * 4. 由于 StreamingResponseBody 在 Tomcat 异步线程中执行，flush 是真 flush
     */
    public void chatStreamSse(String systemPrompt, String userPrompt, List<ChatMessage> history,
                              java.io.OutputStream outputStream) {
        java.util.concurrent.atomic.AtomicBoolean clientClosed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> upstreamError = new java.util.concurrent.atomic.AtomicReference<>();

        // ===== 主线程：同步读 LLM → 写 SSE → flush =====
        try {
            // 构建消息列表
            List<Map<String, String>> messages = new java.util.ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            if (history != null) {
                for (ChatMessage msg : history) {
                    messages.add(Map.of("role", msg.role(), "content", msg.content()));
                }
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            String model = getCurrentModel();
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", true);
            requestBody.put("messages", messages);
            if (isThinkingCapableModel(model)) {
                requestBody.put("enable_thinking", true);
                if (model.toLowerCase().contains("qwen")) {
                    requestBody.put("thinking_budget", 4096);
                }
            }

            String apiUrl = getBaseUrl() + "/v1/chat/completions";
            String apiKey = getApiKey();
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            log.info("AI 流式请求 (SSE): url={}, model={}", apiUrl, model);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(300000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setUseCaches(false);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                String error = "";
                try {
                    error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    error = "无法读取错误响应";
                }
                log.error("AI API 错误: {} - {}", responseCode, error);
                writeSseEvent(outputStream, "error", Map.of("message", "AI API 错误: " + responseCode));
                outputStream.flush();
                conn.disconnect();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                long t0 = System.currentTimeMillis();
                long lastTokenAt = t0;
                long lastLogAt = t0;
                int tokenCount = 0;
                StringBuilder contentBuf = new StringBuilder();
                StringBuilder thinkBuf = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted() || clientClosed.get()) break;
                    lineCount++;
                    long now = System.currentTimeMillis();
                    long gap = now - lastTokenAt;
                    String trimmed = line.trim();
                    if (trimmed.startsWith("data:") && !trimmed.substring(5).trim().equals("[DONE]")) {
                        tokenCount++;
                        lastTokenAt = now;
                        if (tokenCount % 100 == 0 || now - lastLogAt > 5000) {
                            log.info("[LLM token] 累计 {} tokens, 上一间隔 {} ms", tokenCount, gap);
                            lastLogAt = now;
                        }
                    }
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (trimmed.startsWith("data:")) {
                        String data = trimmed.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choices = node.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).path("delta");
                                String reasoningContent = delta.path("reasoning_content").asText("");
                                String content = delta.path("content").asText("");

                                if (!reasoningContent.isEmpty()) {
                                    thinkBuf.append(reasoningContent);
                                    writeSseEvent(outputStream, "thinking", Map.of("content", reasoningContent));
                                    outputStream.flush();  // 关键：每个 event 立即 flush
                                }
                                if (!content.isEmpty()) {
                                    contentBuf.append(content);
                                    writeSseEvent(outputStream, "content", Map.of("content", content));
                                    outputStream.flush();  // 关键：每个 event 立即 flush
                                    if (tokenCount <= 5 || tokenCount % 20 == 0) {
                                        log.info("[READ] token#{} +{}ms (LLM→socket)，payload={}B", tokenCount, now - t0, content.length());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 忽略解析错误
                        }
                    }
                }
                log.info("[READ] done, {} lines, {} tokens in {} ms", lineCount, tokenCount, System.currentTimeMillis() - t0);

                // 写 done 事件
                Map<String, Object> doneData = new java.util.HashMap<>();
                doneData.put("content", contentBuf.toString());
                doneData.put("thinking", thinkBuf.toString());
                doneData.put("done", true);
                writeSseEvent(outputStream, "done", doneData);
                outputStream.flush();
            }
            conn.disconnect();
        } catch (java.io.IOException ioe) {
            log.warn("客户端断开: {}", ioe.getMessage());
        } catch (Exception e) {
            log.error("AI 流式对话失败", e);
            upstreamError.set(e);
            try {
                writeSseEvent(outputStream, "error", Map.of("message", String.valueOf(e.getMessage())));
                outputStream.flush();
            } catch (Exception ignore) {}
        }
    }

    /**
     * 写 SSE event 到 OutputStream（直接写字节，绕过所有框架抽象）
     * 格式：
     *   event: <name>\n
     *   data: <json>\n
     *   \n
     */
    private void writeSseEvent(java.io.OutputStream out, String eventName, Map<String, Object> data) throws java.io.IOException {
        String json = objectMapper.writeValueAsString(data);
        StringBuilder sb = new StringBuilder();
        sb.append("event:").append(eventName).append('\n');
        sb.append("data:").append(json).append('\n');
        sb.append('\n');
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 兼容旧 SseEmitter 调用的方法
     */
    public void chatStreamSse(String systemPrompt, String userPrompt, List<ChatMessage> history, SseEmitter emitter) {
        // 不再实现，请调用 chatStreamSse(systemPrompt, userPrompt, history, outputStream) 版本
        throw new UnsupportedOperationException("请用 OutputStream 版本");
    }

    /**
     * 手动 flush Tomcat 输出缓冲，确保每个 SSE event 立即发送到客户端。
     * - flushBuffer() 把 servlet 内部 buffer 推到 connector（真正发到 socket）
     * - 同时 flush OutputStream 让 NIO connector 提交到 socket
     */
    private void flushSse(HttpServletResponse response) {
        if (response == null) return;
        try {
            // 强制 servlet 提交 buffer
            response.flushBuffer();
        } catch (Exception ex1) {
            try {
                response.getOutputStream().flush();
            } catch (Exception ex2) {
                // 已经断开等情况，忽略
            }
        }
    }

    public record ChatMessage(String role, String content) {}

    /**
     * 多轮对话（带历史）
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userPrompt) {
        try {
            var spec = getChatSpec();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                spec = spec.system(systemPrompt);
            }
            // 构建消息列表
            List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
            if (history != null) {
                for (Map<String, String> msg : history) {
                    String role = msg.getOrDefault("role", "user");
                    String content = msg.getOrDefault("content", "");
                    if ("user".equals(role)) {
                        messages.add(new org.springframework.ai.chat.messages.UserMessage(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(new org.springframework.ai.chat.messages.AssistantMessage(content));
                    }
                }
            }
            messages.add(new org.springframework.ai.chat.messages.UserMessage(userPrompt));
            String result = spec.messages(messages).call().content();
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.error("AI 多轮对话失败: {}", e.getMessage(), e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }

    // ===== 文档 AI 能力 =====

    /**
     * 文档问答（基础版）
     */
    public String documentQa(String docContent, String question) {
        String prompt = PromptTemplates.DOCUMENT_QA
                .replace("{content}", docContent)
                .replace("{question}", question);
        return chat(prompt);
    }

    /**
     * 文档问答（增强版 - 智能分块 + 关键词检索）
     */
    public String documentQaEnhanced(Long documentId, String question) {
        String content = contentService.extractText(documentId);
        if (content.isBlank()) {
            return "文档内容为空，无法回答问题。";
        }

        // 智能分块
        List<String> chunks = contentService.smartChunk(content, 4000, 200);
        // 选择最相关的块
        String context = contentService.selectRelevantChunks(chunks, question, 3, 12000);

        String prompt = "你是一个文档助手。请根据以下文档内容回答用户的问题。" +
                "如果文档中没有相关信息，请说明。回答要准确、简洁。\n\n" +
                "=== 文档内容 ===\n" + context + "\n=== 文档结束 ===\n\n" +
                "用户问题：" + question;

        return chat(prompt);
    }

    /**
     * 多轮文档问答（基础版）
     */
    public String documentQaWithHistory(String docContent, List<Map<String, String>> history, String question) {
        StringBuilder historyStr = new StringBuilder();
        if (history != null) {
            for (Map<String, String> msg : history) {
                String role = msg.getOrDefault("role", "user");
                String msgContent = msg.getOrDefault("content", "");
                historyStr.append("用户：").append(msgContent).append("\n");
                if ("assistant".equals(role)) {
                    historyStr.insert(historyStr.length() - msgContent.length() - 1, "助手：");
                }
            }
        }
        String prompt = PromptTemplates.DOCUMENT_QA_MULTI_TURN
                .replace("{content}", docContent)
                .replace("{history}", historyStr.toString())
                .replace("{question}", question);
        return chat(prompt);
    }

    /**
     * 多轮文档问答（增强版）
     */
    public String documentQaWithHistoryEnhanced(Long documentId, List<Map<String, String>> history, String question) {
        String content = contentService.extractText(documentId);
        if (content.isBlank()) {
            return "文档内容为空，无法回答问题。";
        }

        List<String> chunks = contentService.smartChunk(content, 4000, 200);
        String context = contentService.selectRelevantChunks(chunks, question, 3, 12000);

        StringBuilder historyStr = new StringBuilder();
        if (history != null) {
            for (Map<String, String> msg : history) {
                String role = msg.getOrDefault("role", "user");
                String msgContent = msg.getOrDefault("content", "");
                historyStr.append("用户：").append(msgContent).append("\n");
                if ("assistant".equals(role)) {
                    historyStr.insert(historyStr.length() - msgContent.length() - 1, "助手：");
                }
            }
        }

        String prompt = "你是一个文档助手。请根据文档内容和对话历史回答用户的问题。" +
                "如果文档中没有相关信息，请说明。回答要准确、简洁。\n\n" +
                "=== 文档内容 ===\n" + context + "\n=== 文档结束 ===\n\n" +
                "对话历史：\n" + historyStr.toString() + "\n\n" +
                "用户问题：" + question;

        return chat(prompt);
    }

    /**
     * 内容生成（带缓存）
     */
    public String generate(String userPrompt) {
        String cacheKey = aiCacheService.generateCacheKey("generate", -1L, userPrompt);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.GENERATE.replace("{prompt}", userPrompt);
            return chat(prompt);
        });
    }

    /**
     * 文档摘要（带缓存）
     */
    public String summarize(String content) {
        String cacheKey = aiCacheService.generateCacheKey("summarize", -1L, content);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.SUMMARIZE.replace("{content}", content);
            return chat(prompt);
        });
    }

    /**
     * 结构化摘要（带缓存）
     */
    public String structuredSummarize(String content) {
        String cacheKey = aiCacheService.generateCacheKey("structuredSummarize", -1L, content);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.STRUCTURED_SUMMARIZE.replace("{content}", content);
            return chat(prompt);
        });
    }

    /**
     * 翻译（上下文感知）
     */
    public String translate(String text, String targetLang) {
        return translateWithContext(text, targetLang, null);
    }

    /**
     * 上下文感知翻译（增强版，带缓存）
     */
    public String translateWithContext(String text, String targetLang, String context) {
        String langName = switch (targetLang) {
            case "zh" -> "中文";
            case "en" -> "English";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            case "fr" -> "Français";
            case "de" -> "Deutsch";
            default -> targetLang;
        };
        String cacheKey = aiCacheService.generateCacheKey("translate:" + targetLang, -1L, text + (context != null ? context : ""));
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt;
            if (context != null && !context.isBlank()) {
                prompt = PromptTemplates.CONTEXT_AWARE_TRANSLATE
                        .replace("{text}", text)
                        .replace("{lang}", langName)
                        .replace("{context}", context);
            } else {
                prompt = PromptTemplates.TRANSLATE
                        .replace("{text}", text)
                        .replace("{lang}", langName);
            }
            return chat(prompt);
        });
    }

    /**
     * 改写（带缓存）
     */
    public String rewrite(String text, String instruction) {
        String cacheKey = aiCacheService.generateCacheKey("rewrite:" + instruction, -1L, text);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.REWRITE
                    .replace("{text}", text)
                    .replace("{instruction}", instruction);
            return chat(prompt);
        });
    }

    /**
     * 续写
     */
    public String continueWriting(String context) {
        String prompt = PromptTemplates.CONTINUE_WRITING.replace("{context}", context);
        return chat(prompt);
    }

    /**
     * 表格提取（带缓存）
     */
    public String extractTables(String content) {
        String cacheKey = aiCacheService.generateCacheKey("extractTables", -1L, content);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.EXTRACT_TABLES.replace("{content}", content);
            return chat(prompt);
        });
    }

    /**
     * 文档对比
     */
    public String compareDocuments(String doc1, String doc2) {
        String prompt = PromptTemplates.COMPARE_DOCUMENTS
                .replace("{doc1}", doc1)
                .replace("{doc2}", doc2);
        return chat(prompt);
    }

    /**
     * 合同审查
     */
    public String reviewContract(String content) {
        String prompt = PromptTemplates.CONTRACT_REVIEW.replace("{content}", content);
        return chat(prompt);
    }

    /**
     * 智能标签（带缓存）
     */
    public String suggestTags(String content) {
        String cacheKey = aiCacheService.generateCacheKey("suggestTags", -1L, content);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.TAG_SUGGESTION.replace("{content}", content);
            return chat(prompt);
        });
    }

    /**
     * 智能分类
     */
    public String suggestFolder(String content, String folders) {
        String prompt = PromptTemplates.FOLDER_SUGGESTION
                .replace("{content}", content)
                .replace("{folders}", folders);
        return chat(prompt);
    }

    /**
     * 关键词提取（带缓存）
     */
    public String extractKeywords(String content) {
        String cacheKey = aiCacheService.generateCacheKey("extractKeywords", -1L, content);
        return aiCacheService.getOrCompute(cacheKey, () -> {
            String prompt = PromptTemplates.EXTRACT_KEYWORDS.replace("{content}", content);
            return chat(prompt);
        });
    }

    // ===== 多模态能力 =====

    /**
     * 视觉问答（图片理解）
     */
    public String visionChat(String question, byte[] imageBytes) {
        try {
            String model = getCurrentModel();
            String result = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.7)
                            .maxTokens(2000)
                            .build())
                    .user(u -> u.text(question)
                            .media(MediaType.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                    .call()
                    .content();
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.error("视觉问答失败: {}", e.getMessage(), e);
            return "AI 服务调用失败：" + e.getMessage();
        }
    }
}
