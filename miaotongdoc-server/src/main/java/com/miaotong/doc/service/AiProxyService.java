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

    /**
     * v2.7：可选依赖 AiConfigService，DB 优先于文件
     * 用 Optional + @Autowired(required=false) 防止循环依赖
     */
    private final java.util.Optional<AiConfigService> aiConfigService;

    public AiProxyService(@org.springframework.beans.factory.annotation.Autowired(required = false)
                          AiConfigService aiConfigService) {
        this.aiConfigService = java.util.Optional.ofNullable(aiConfigService);
    }

    public String getDefaultModel() {
        // v2.7：DB 当前默认 LLM 优先
        if (aiConfigService.isPresent()) {
            AiConfigService.AiConfig dbCfg = aiConfigService.get().getActive("LLM");
            if (dbCfg != null && dbCfg.defaultModel != null && !dbCfg.defaultModel.isEmpty()) {
                return dbCfg.defaultModel;
            }
        }
        return (defaultModel != null && !defaultModel.isEmpty()) ? defaultModel : "gpt-4o-mini";
    }

    public String getTargetUrl() {
        // v2.7：DB 当前默认 LLM 优先
        if (aiConfigService.isPresent()) {
            AiConfigService.AiConfig dbCfg = aiConfigService.get().getActive("LLM");
            if (dbCfg != null && dbCfg.baseUrl != null && !dbCfg.baseUrl.isEmpty()) {
                return dbCfg.baseUrl;
            }
        }
        return targetUrl;
    }

    public String getApiKey() {
        // v2.7：DB 当前默认 LLM 优先（自动解密）
        if (aiConfigService.isPresent()) {
            AiConfigService.AiConfig dbCfg = aiConfigService.get().getActive("LLM");
            if (dbCfg != null && dbCfg.apiKey != null && !dbCfg.apiKey.isEmpty()) {
                return dbCfg.apiKey;
            }
        }
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
     * 从指定 URL + Key 拉取模型列表（v2.7.2 多 Provider 场景使用）
     */
    private Object fetchModelsFromUrl(String url, String key) {
        if (url == null || url.isEmpty()) {
            return Map.of("data", java.util.List.of());
        }
        String modelsUrl = url.endsWith("/v1")
                ? url + "/models"
                : url + (url.endsWith("/") ? "v1/models" : "/v1/models");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null && !key.isEmpty()) {
            headers.set("Authorization", "Bearer " + key);
        }

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(modelsUrl, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("从 {} 拉模型失败: {}", url, e.getMessage());
            return Map.of("data", java.util.List.of(), "error", e.getMessage());
        }
    }

    /**
     * 返回完整 AI 配置（供 Office AI 插件初始化使用）
     * v2.7.2：从 AiConfigService（DB）读取所有 LLM Provider
     *          同时为默认 Provider 提供 "OpenAI" 别名（兼容 Office 插件前端的硬编码）
     */
    public Object getConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("proxy", "/api/ai/proxy");

        // v2.7.2：从 DB 读取所有 LLM Provider
        java.util.List<AiConfigService.AiConfig> llmProviders = aiConfigService
                .map(s -> s.listAll("LLM"))
                .orElse(java.util.List.of());

        // 找出默认 Provider（指向它作为 OpenAI 别名）
        AiConfigService.AiConfig defaultProvider = aiConfigService
                .map(s -> s.getActive("LLM"))
                .orElse(null);

        Map<String, Map<String, Object>> providersMap = new java.util.LinkedHashMap<>();
        java.util.List<Map<String, Object>> globalModels = new java.util.ArrayList<>();

        // 列出所有真实 Provider（按 DB 名字）
        for (AiConfigService.AiConfig llm : llmProviders) {
            java.util.List<Map<String, Object>> models = parseModels(
                    fetchModelsFromUrl(llm.baseUrl, llm.apiKey));

            Map<String, Object> provider = new java.util.LinkedHashMap<>();
            provider.put("name", llm.name);
            provider.put("url", llm.baseUrl != null ? llm.baseUrl : "");
            provider.put("key", "***");
            provider.put("models", models);
            providersMap.put(llm.name, provider);

            for (Map<String, Object> m : models) {
                Map<String, Object> gm = new java.util.LinkedHashMap<>();
                gm.put("id", m.get("id"));
                gm.put("name", m.get("name"));
                gm.put("provider", llm.name);
                gm.put("capabilities", 511);
                globalModels.add(gm);
            }
        }

        // 兼容层："OpenAI" 别名指向默认 Provider（Office 插件前端硬编码 data.providers.OpenAI）
        if (defaultProvider != null && providersMap.containsKey(defaultProvider.name)) {
            Map<String, Object> defaultMap = providersMap.get(defaultProvider.name);
            Map<String, Object> openaiAlias = new java.util.LinkedHashMap<>(defaultMap);
            openaiAlias.put("name", "OpenAI");
            providersMap.put("OpenAI", openaiAlias);
        }

        // 兜底：DB 没有任何 LLM → 走文件（兼容老 ai-config.json）
        if (providersMap.isEmpty()) {
            java.util.List<Map<String, Object>> models = parseModels(getModels());
            Map<String, Object> provider = buildProvider();
            providersMap.put("OpenAI", provider);
            for (Map<String, Object> m : models) {
                Map<String, Object> gm = new java.util.LinkedHashMap<>();
                gm.put("id", m.get("id"));
                gm.put("name", m.get("name"));
                gm.put("provider", "OpenAI");
                gm.put("capabilities", 511);
                globalModels.add(gm);
            }
        }

        config.put("providers", providersMap);
        config.put("models", globalModels);

        // 默认模型：DB 当前默认 LLM 的 defaultModel > 第一个 > 空
        String effectiveModel = "";
        if (defaultProvider != null && defaultProvider.defaultModel != null && !defaultProvider.defaultModel.isEmpty()) {
            effectiveModel = defaultProvider.defaultModel;
        } else if (!globalModels.isEmpty()) {
            effectiveModel = (String) globalModels.get(0).get("id");
        } else if (defaultModel != null && !defaultModel.isEmpty()) {
            effectiveModel = defaultModel;
        }

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
     * 刷新模型列表（Office AI 插件刷新按钮调用）
     * v2.7.2：从 DB 当前默认 LLM 拉取，不再覆盖 DB 配置
     * 同时返回 OpenAI 别名（兼容前端硬编码）
     */
    public Object refreshModels() {
        // 不再 loadConfigFromFile() —— DB 是真相源，文件只是兜底

        // 优先从 DB 当前默认 LLM 拿
        AiConfigService.AiConfig active = aiConfigService
                .map(s -> s.getActive("LLM"))
                .orElse(null);
        String url = active != null ? active.baseUrl : targetUrl;
        String key = active != null ? active.apiKey : apiKey;

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (url == null || url.isEmpty()) {
            result.put("success", false);
            result.put("message", "LLM URL 未配置");
            result.put("models", java.util.List.of());
            return result;
        }

        java.util.List<Map<String, Object>> models = parseModels(fetchModelsFromUrl(url, key));
        if (models.isEmpty()) {
            result.put("success", false);
            result.put("message", "未获取到任何模型");
            result.put("models", java.util.List.of());
            return result;
        }

        result.put("success", true);
        result.put("message", "已刷新 " + models.size() + " 个模型");
        result.put("models", models);
        // 兼容层：返回真实 provider 名 + OpenAI 别名（前端硬编码兼容）
        String providerName = active != null ? active.name : "OpenAI";
        Map<String, Object> defaultProvider = new java.util.LinkedHashMap<>();
        defaultProvider.put("name", providerName);
        defaultProvider.put("url", url);
        defaultProvider.put("key", "***");
        defaultProvider.put("models", models);
        Map<String, Object> providers = new java.util.LinkedHashMap<>();
        providers.put(providerName, defaultProvider);
        Map<String, Object> openaiAlias = new java.util.LinkedHashMap<>(defaultProvider);
        openaiAlias.put("name", "OpenAI");
        providers.put("OpenAI", openaiAlias);
        result.put("providers", providers);
        return result;
    }

    public Object proxy(Map<String, Object> body) {
        String target = (String) body.getOrDefault("target", "");
        String method = (String) body.getOrDefault("method", "POST");
        Map<String, String> headers = (Map<String, String>) body.getOrDefault("headers", Map.of());
        String data = (String) body.getOrDefault("data", "");

        // v2.7.3：根据请求 body 中的 model + provider 字段，精确选择对应的 LLM Provider
        // 之前只根据 model+defaultModel 匹配，会导致选了"日日新"模型却用 Minimax 的 key 转发，401
        String forwardUrl = buildForwardUrl(target, data);
        String forwardKey = pickProviderKey(data);
        log.info("AI Proxy: {} {} -> {}", method, target, forwardUrl);

        try {
            boolean isStream = data.contains("\"stream\":true") || data.contains("\"stream\": true");
            return isStream
                    ? streamForward(forwardUrl, method, headers, data, forwardKey)
                    : bufferForward(forwardUrl, method, headers, data, forwardKey);
        } catch (Exception e) {
            log.error("AI Proxy error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("message", "Proxy error: " + e.getMessage())));
        }
    }

    /**
     * 从 body 里提取模型和 provider 信息（v2.7.3 支持精确 provider 匹配）
     */
    private String[] parseModelAndProvider(String data) {
        String model = null;
        String provider = null;
        try {
            // 简单 JSON 解析：找 "model":"xxx" 和 "provider":"xxx"
            int modelIdx = data.indexOf("\"model\"");
            if (modelIdx >= 0) {
                int colon = data.indexOf(':', modelIdx);
                int q1 = data.indexOf('"', colon + 1);
                int q2 = data.indexOf('"', q1 + 1);
                if (q1 > 0 && q2 > q1) {
                    model = data.substring(q1 + 1, q2);
                }
            }
            int provIdx = data.indexOf("\"provider\"");
            if (provIdx >= 0) {
                int colon = data.indexOf(':', provIdx);
                int q1 = data.indexOf('"', colon + 1);
                int q2 = data.indexOf('"', q1 + 1);
                if (q1 > 0 && q2 > q1) {
                    provider = data.substring(q1 + 1, q2);
                }
            }
        } catch (Exception ignore) {}
        return new String[]{model, provider};
    }

    /**
     * 从请求 body 解析 provider 名字，匹配 DB 中对应 Provider，返回该 Provider 的 apiKey
     * 匹配优先级：1) provider 字段  2) model+defaultModel 兼容旧请求  3) fallback 默认 Provider
     */
    private String pickProviderKey(String data) {
        String[] mp = parseModelAndProvider(data);
        String model = mp[0];
        String providerName = mp[1];

        if (aiConfigService.isPresent()) {
            java.util.List<AiConfigService.AiConfig> providers = aiConfigService.get().listAll("LLM");

            // 1) 优先按 provider 名字精确匹配（v2.7.3 新增）
            if (providerName != null && !providerName.isEmpty()) {
                for (AiConfigService.AiConfig cfg : providers) {
                    if (providerName.equals(cfg.name)) {
                        return cfg.apiKey;
                    }
                }
                // provider 没匹配上但名字匹配 OpenAI 别名 → fall through
                if ("OpenAI".equals(providerName)) {
                    for (AiConfigService.AiConfig cfg : providers) {
                        if (cfg.isDefault) return cfg.apiKey;
                    }
                }
            }

            // 2) 兼容：用 model 匹配 defaultModel
            if (model != null) {
                for (AiConfigService.AiConfig cfg : providers) {
                    if (cfg.defaultModel != null && cfg.defaultModel.equals(model)) {
                        return cfg.apiKey;
                    }
                }
            }
        }

        // fallback：默认 Provider 的 key（getter 已优先 DB）
        return getApiKey();
    }

    private String buildForwardUrl(String target, String data) {
        // v2.7.3：根据 provider 名字直接定位 baseUrl（不再只匹配 defaultModel）
        String[] mp = parseModelAndProvider(data);
        String model = mp[0];
        String providerName = mp[1];

        String base = null;
        if (aiConfigService.isPresent()) {
            java.util.List<AiConfigService.AiConfig> providers = aiConfigService.get().listAll("LLM");

            // 1) 按 provider 名字精确匹配
            if (providerName != null && !providerName.isEmpty()) {
                for (AiConfigService.AiConfig cfg : providers) {
                    if (providerName.equals(cfg.name) && cfg.baseUrl != null) {
                        base = cfg.baseUrl;
                        break;
                    }
                }
                if (base == null && "OpenAI".equals(providerName)) {
                    for (AiConfigService.AiConfig cfg : providers) {
                        if (cfg.isDefault && cfg.baseUrl != null) {
                            base = cfg.baseUrl;
                            break;
                        }
                    }
                }
            }

            // 2) 兼容：用 model 匹配 defaultModel
            if (base == null && model != null) {
                for (AiConfigService.AiConfig cfg : providers) {
                    if (cfg.defaultModel != null && cfg.defaultModel.equals(model) && cfg.baseUrl != null) {
                        base = cfg.baseUrl;
                        break;
                    }
                }
            }
        }
        if (base == null) base = getTargetUrl();  // fallback

        if (base == null || base.isEmpty()) return target;
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        String baseTrim = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String path = target.startsWith("/") ? target : "/" + target;
        return baseTrim + path;
    }

    private ResponseEntity<String> bufferForward(String url, String method, Map<String, String> headers, String data, String apiKeyOverride) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!"content-length".equalsIgnoreCase(entry.getKey()) && !"host".equalsIgnoreCase(entry.getKey())) {
                httpHeaders.set(entry.getKey(), entry.getValue());
            }
        }
        String effectiveKey = apiKeyOverride != null ? apiKeyOverride : getApiKey();
        if (effectiveKey != null && !effectiveKey.isEmpty()) {
            httpHeaders.set("Authorization", "Bearer " + effectiveKey);
        }

        HttpEntity<String> entity = new HttpEntity<>(data, httpHeaders);
        HttpMethod httpMethod = "GET".equalsIgnoreCase(method) ? HttpMethod.GET : HttpMethod.POST;
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(response.getBody(), respHeaders, response.getStatusCode());
    }

    private SseEmitter streamForward(String url, String method, Map<String, String> headers, String data, String apiKeyOverride) {
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
                String effectiveKey = apiKeyOverride != null ? apiKeyOverride : getApiKey();
                if (effectiveKey != null && !effectiveKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + effectiveKey);
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
