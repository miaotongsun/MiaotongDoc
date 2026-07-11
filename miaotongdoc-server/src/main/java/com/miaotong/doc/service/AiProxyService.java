package com.miaotong.doc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Service
public class AiProxyService {

    @Value("${ai-proxy.target-url:}")
    private String envTargetUrl;

    @Value("${ai-proxy.api-key:}")
    private String envApiKey;

    @Value("${ai-proxy.timeout:300}")
    private int timeout;

    private String targetUrl;
    private String apiKey;
    private String defaultModel;

    public String getDefaultModel() {
        return (defaultModel != null && !defaultModel.isEmpty()) ? defaultModel : "gpt-4o-mini";
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy
    @Autowired
    private com.miaotong.doc.service.ai.AiService aiService;

    private static final String CONFIG_PATH = "/data/config/ai-config.json";

    @PostConstruct
    public void init() {
        loadConfigFromFile();
        rebuildRestTemplate();
    }

    private void loadConfigFromFile() {
        try {
            Path path = Path.of(CONFIG_PATH);
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Map<String, Object> cfg = objectMapper.readValue(json, new TypeReference<>() {});
                this.targetUrl = normalizeTargetUrl((String) cfg.getOrDefault("targetUrl", envTargetUrl));
                this.apiKey = (String) cfg.getOrDefault("apiKey", envApiKey);
                this.defaultModel = (String) cfg.getOrDefault("defaultModel", "");
                if (cfg.containsKey("timeout")) {
                    this.timeout = ((Number) cfg.get("timeout")).intValue();
                }
                log.info("AI 配置已从文件加载: {}", CONFIG_PATH);
                return;
            }
        } catch (Exception e) {
            log.warn("读取 AI 配置文件失败: {}", e.getMessage());
        }
        this.targetUrl = normalizeTargetUrl(envTargetUrl);
        this.apiKey = envApiKey;
    }

    /**
     * 规范化 targetUrl：去尾部斜杠、去重 /v1
     */
    private String normalizeTargetUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String result = url.replaceAll("/+$", "");
        result = result.replaceAll("(/v1)+$", "/v1");
        result = result.replaceAll("(/v1)(/v1)+", "/v1");
        return result;
    }

    private void rebuildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout * 1000);
        factory.setReadTimeout(timeout * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void saveConfig(Map<String, Object> config) {
        try {
            Path path = Path.of(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
            this.targetUrl = normalizeTargetUrl((String) config.getOrDefault("targetUrl", ""));
            this.apiKey = (String) config.getOrDefault("apiKey", "");
            this.defaultModel = (String) config.getOrDefault("defaultModel", "");
            if (config.containsKey("timeout")) {
                this.timeout = ((Number) config.get("timeout")).intValue();
            }
            rebuildRestTemplate();
            if (aiService != null) {
                aiService.rebuildClient();
            }
            log.info("AI 配置已保存: {}", CONFIG_PATH);
        } catch (Exception e) {
            log.error("保存 AI 配置失败: {}", e.getMessage());
            throw new RuntimeException("保存配置失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getCurrentConfig() {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>();
        cfg.put("targetUrl", targetUrl != null ? targetUrl : "");
        cfg.put("apiKey", apiKey != null ? apiKey : "");
        cfg.put("defaultModel", defaultModel != null ? defaultModel : "");
        cfg.put("timeout", timeout);
        return cfg;
    }

    /**
     * 解析 LLM API 返回的 models JSON，过滤掉 embedding/reranker
     */
    private java.util.List<Map<String, Object>> parseModels(Object modelsResp) {
        java.util.List<Map<String, Object>> models = new java.util.ArrayList<>();
        try {
            String json = null;
            if (modelsResp instanceof String) {
                json = (String) modelsResp;
            } else if (modelsResp instanceof Map && ((Map<?, ?>) modelsResp).containsKey("error")) {
                log.warn("获取模型失败: {}", ((Map<?, ?>) modelsResp).get("error"));
                return models;
            }
            if (json == null || json.isEmpty()) return models;

            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Object data = parsed.get("data");
            if (data instanceof java.util.List) {
                for (Object item : (java.util.List<?>) data) {
                    if (item instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) item;
                        String id = (String) m.get("id");
                        if (id != null && !id.matches(".*(?i)(embed|rerank).*")) {
                            Map<String, Object> modelInfo = new java.util.LinkedHashMap<>();
                            modelInfo.put("id", id);
                            modelInfo.put("name", id);
                            modelInfo.put("endpoints", java.util.List.of(1));
                            modelInfo.put("options", Map.of());
                            models.add(modelInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析模型列表失败: {}", e.getMessage());
        }
        return models;
    }

    /**
     * 构造 Provider 配置（包含真实模型列表）
     */
    private Map<String, Object> buildProvider() {
        Map<String, Object> provider = new java.util.LinkedHashMap<>();
        provider.put("name", "OpenAI");
        provider.put("url", targetUrl != null ? targetUrl : "");
        provider.put("key", "***");
        provider.put("models", parseModels(getModels()));
        return provider;
    }

    /**
     * 返回完整 AI 配置（供插件初始化使用）
     */
    public Object getConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("proxy", "/api/ai/proxy");

        java.util.List<Map<String, Object>> models = parseModels(getModels());
        Map<String, Object> provider = buildProvider();

        config.put("providers", Map.of("OpenAI", provider));

        // 全局模型列表 (capabilities = 511 = 所有能力)
        java.util.List<Map<String, Object>> globalModels = new java.util.ArrayList<>();
        for (Map<String, Object> m : models) {
            Map<String, Object> gm = new java.util.LinkedHashMap<>();
            gm.put("id", m.get("id"));
            gm.put("name", m.get("name"));
            gm.put("provider", "OpenAI");
            gm.put("capabilities", 511);
            globalModels.add(gm);
        }
        config.put("models", globalModels);

        String effectiveModel = (this.defaultModel != null && !this.defaultModel.isEmpty())
                ? this.defaultModel
                : (models.isEmpty() ? "" : (String) models.get(0).get("id"));
        Map<String, Object> actions = new java.util.LinkedHashMap<>();
        for (String action : java.util.List.of("Chat", "Summarization", "Translation", "TextAnalyze",
                "ImageGeneration", "OCR", "Vision")) {
            actions.put(action, Map.of("model", effectiveModel));
        }
        config.put("actions", actions);

        return config;
    }

    /**
     * c'c（原始 JSON）
     */
    public Object getModels() {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Map.of("data", java.util.List.of());
        }
        String modelsUrl = targetUrl.endsWith("/v1")
                ? targetUrl + "/models"
                : targetUrl + (targetUrl.endsWith("/") ? "v1/models" : "/v1/models");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(modelsUrl, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取模型列表失败: {}", e.getMessage());
            return Map.of("data", java.util.List.of(), "error", e.getMessage());
        }
    }

    /**
     * 刷新模型列表（AI 插件刷新按钮调用）
     */
    public Object refreshModels() {
        loadConfigFromFile();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (targetUrl == null || targetUrl.isEmpty()) {
            result.put("success", false);
            result.put("message", "LLM URL 未配置");
            result.put("models", java.util.List.of());
            return result;
        }

        java.util.List<Map<String, Object>> models = parseModels(getModels());
        if (models.isEmpty()) {
            result.put("success", false);
            result.put("message", "未获取到任何模型");
            result.put("models", java.util.List.of());
            return result;
        }

        result.put("success", true);
        result.put("message", "已刷新 " + models.size() + " 个模型");
        result.put("models", models);
        result.put("providers", Map.of("OpenAI", Map.of(
                "name", "OpenAI",
                "url", targetUrl,
                "key", "***",
                "models", models
        )));
        return result;
    }

    public Object proxy(Map<String, Object> body) {
        String target = (String) body.getOrDefault("target", "");
        String method = (String) body.getOrDefault("method", "POST");
        Map<String, String> headers = (Map<String, String>) body.getOrDefault("headers", Map.of());
        String data = (String) body.getOrDefault("data", "");

        String forwardUrl = buildForwardUrl(target);
        log.info("AI Proxy: {} {} -> {}", method, target, forwardUrl);

        try {
            boolean isStream = data.contains("\"stream\":true") || data.contains("\"stream\": true");
            return isStream
                    ? streamForward(forwardUrl, method, headers, data)
                    : bufferForward(forwardUrl, method, headers, data);
        } catch (Exception e) {
            log.error("AI Proxy error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("message", "Proxy error: " + e.getMessage())));
        }
    }

    private String buildForwardUrl(String target) {
        if (targetUrl == null || targetUrl.isEmpty()) return target;
        // 完整 URL 直接返回（避免与 base 重复拼接）
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        String base = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;
        String path = target.startsWith("/") ? target : "/" + target;
        return base + path;
    }

    private ResponseEntity<String> bufferForward(String url, String method, Map<String, String> headers, String data) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!"content-length".equalsIgnoreCase(entry.getKey()) && !"host".equalsIgnoreCase(entry.getKey())) {
                httpHeaders.set(entry.getKey(), entry.getValue());
            }
        }
        if (apiKey != null && !apiKey.isEmpty()) {
            httpHeaders.set("Authorization", "Bearer " + apiKey);
        }

        HttpEntity<String> entity = new HttpEntity<>(data, httpHeaders);
        HttpMethod httpMethod = "GET".equalsIgnoreCase(method) ? HttpMethod.GET : HttpMethod.POST;
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(response.getBody(), respHeaders, response.getStatusCode());
    }

    private SseEmitter streamForward(String url, String method, Map<String, String> headers, String data) {
        SseEmitter emitter = new SseEmitter((long) timeout * 1000);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeout * 1000);
                conn.setReadTimeout(timeout * 1000);
                conn.setRequestProperty("Content-Type", "application/json");
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (!"content-length".equalsIgnoreCase(entry.getKey()) && !"host".equalsIgnoreCase(entry.getKey())) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        emitter.send(SseEmitter.event().data(line));
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Stream proxy error: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":{\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();

        return emitter;
    }
}
