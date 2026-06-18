package com.miaotong.doc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // 运行时可变的配置（优先级高于环境变量）
    private String targetUrl;
    private String apiKey;
    private String defaultModel;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置文件路径（独立于文档目录）
    private static final String CONFIG_PATH = "/data/config/ai-config.json";

    @PostConstruct
    public void init() {
        // 优先从配置文件加载，否则用环境变量
        loadConfigFromFile();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout * 1000);
        factory.setReadTimeout(timeout * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 从配置文件加载 AI 设置
     */
    private void loadConfigFromFile() {
        try {
            Path path = Path.of(CONFIG_PATH);
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Map<String, Object> cfg = objectMapper.readValue(json, new TypeReference<>() {});
                this.targetUrl = (String) cfg.getOrDefault("targetUrl", envTargetUrl);
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
        // 回退到环境变量
        this.targetUrl = envTargetUrl;
        this.apiKey = envApiKey;
    }

    /**
     * 保存 AI 配置到文件
     */
    public void saveConfig(Map<String, Object> config) {
        try {
            Path path = Path.of(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
            // 更新运行时配置
            this.targetUrl = (String) config.getOrDefault("targetUrl", "");
            this.apiKey = (String) config.getOrDefault("apiKey", "");
            this.defaultModel = (String) config.getOrDefault("defaultModel", "");
            if (config.containsKey("timeout")) {
                this.timeout = ((Number) config.get("timeout")).intValue();
            }
            // 重建 RestTemplate（超时可能变了）
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeout * 1000);
            factory.setReadTimeout(timeout * 1000);
            this.restTemplate = new RestTemplate(factory);
            log.info("AI 配置已保存: {}", CONFIG_PATH);
        } catch (Exception e) {
            log.error("保存 AI 配置失败: {}", e.getMessage());
            throw new RuntimeException("保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前 AI 配置（用于前端展示）
     */
    public Map<String, Object> getCurrentConfig() {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>();
        cfg.put("targetUrl", targetUrl != null ? targetUrl : "");
        cfg.put("apiKey", apiKey != null ? apiKey : "");
        cfg.put("defaultModel", defaultModel != null ? defaultModel : "");
        cfg.put("timeout", timeout);
        return cfg;
    }

    /**
     * 返回完整 AI 配置（供插件初始化使用）
     */
    public Object getConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("proxy", "/api/ai/proxy");

        // Provider 配置（不暴露 API 密钥给前端）
        Map<String, Object> provider = new java.util.LinkedHashMap<>();
        provider.put("name", "OpenAI");
        provider.put("url", targetUrl != null ? targetUrl : "");
        provider.put("key", "***");  // 不返回真实密钥

        // 获取模型列表
        Object modelsResp = getModels();
        java.util.List<Map<String, Object>> models = new java.util.ArrayList<>();
        if (modelsResp instanceof String) {
            try {
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue((String) modelsResp, Map.class);
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
        }
        provider.put("models", models);

        config.put("providers", Map.of("OpenAI", provider));

        // 全局模型列表 - 设置所有能力位，让所有模型可用于所有功能
        // Chat=1, Image=2, Embeddings=4, Audio=8, Moderations=16, Realtime=32, Code=64, Vision=128, Tools=256
        // All = 511
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

        // 默认 Actions - 优先使用配置中保存的默认模型
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
     * 从 LLM API 获取可用模型列表
     */
    public Object getModels() {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Map.of("data", java.util.List.of());
        }
        String modelsUrl;
        if (targetUrl.endsWith("/v1")) {
            modelsUrl = targetUrl + "/models";
        } else if (targetUrl.endsWith("/")) {
            modelsUrl = targetUrl + "v1/models";
        } else {
            modelsUrl = targetUrl + "/v1/models";
        }

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

    public Object proxy(Map<String, Object> body) {
        String target = (String) body.getOrDefault("target", "");
        String method = (String) body.getOrDefault("method", "POST");
        Map<String, String> headers = (Map<String, String>) body.getOrDefault("headers", Map.of());
        String data = (String) body.getOrDefault("data", "");

        String forwardUrl = buildForwardUrl(target);
        log.info("AI Proxy: {} {} -> {}", method, target, forwardUrl);

        try {
            boolean isStream = data.contains("\"stream\":true") || data.contains("\"stream\": true");

            if (isStream) {
                return streamForward(forwardUrl, method, headers, data);
            } else {
                return bufferForward(forwardUrl, method, headers, data);
            }
        } catch (Exception e) {
            log.error("AI Proxy error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("message", "Proxy error: " + e.getMessage())));
        }
    }

    private String buildForwardUrl(String target) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return target;
        }
        String base = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;
        try {
            URL targetURL = new URL(target);
            String targetPath = targetURL.getPath();
            // 如果 base 已经以 /v1 结尾，而 targetPath 也以 /v1 开头，去掉 targetPath 的 /v1 前缀避免重复
            if (base.endsWith("/v1") && targetPath.startsWith("/v1")) {
                targetPath = targetPath.substring(3); // 去掉 "/v1"
            }
            return base + targetPath;
        } catch (Exception e) {
            String path = target.startsWith("/") ? target : "/" + target;
            if (base.endsWith("/v1") && path.startsWith("/v1")) {
                path = path.substring(3);
            }
            return base + path;
        }
    }

    private ResponseEntity<String> bufferForward(String url, String method, Map<String, String> headers, String data) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (!"content-length".equalsIgnoreCase(key) && !"host".equalsIgnoreCase(key)) {
                httpHeaders.set(key, entry.getValue());
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
                    String key = entry.getKey();
                    if (!"content-length".equalsIgnoreCase(key) && !"host".equalsIgnoreCase(key)) {
                        conn.setRequestProperty(key, entry.getValue());
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
