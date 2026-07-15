package com.miaotong.doc.controller;

import com.miaotong.doc.entity.AiProvider;
import com.miaotong.doc.repository.AiProviderRepository;
import com.miaotong.doc.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Provider 管理 API（CRUD）
 *
 * 用例：管理后台"AI 配置"页面
 *   - 列出所有 Provider
 *   - 创建/更新/删除 Provider
 *   - 启用/禁用、设为默认
 *   - 触发 AiConfigService 热刷新（修改立即生效）
 *
 * 权限：仅管理员（依赖 SecurityConfig 中 /api/admin/** 鉴权）
 *
 * @since v2.7
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AiProviderAdminController {

    private final AiProviderRepository repository;
    private final AiConfigService configService;

    /** 列出所有 Provider（支持按 type 过滤） */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String type) {
        List<AiProvider> list = type != null
                ? repository.findByType(type)
                : repository.findAll();
        return list.stream().map(this::toSafeMap).toList();
    }

    /** 获取单个 Provider（不含明文 key） */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> ResponseEntity.ok(toSafeMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * v2.7.3 临时展示明文 API Key（点眼睛才调用，单独审计）
     * 仅 admin，前后端 HTTPS 加密传输时仍能避免 key 常驻前端内存
     */
    @GetMapping("/{id}/reveal-key")
    public ResponseEntity<Map<String, Object>> revealKey(@PathVariable Long id,
                                                        java.security.Principal principal) {
        return repository.findById(id)
                .map(p -> {
                    String plain = p.getApiKey() == null ? "" : configService.decryptKey(p.getApiKey());
                    log.warn("[AUDIT] reveal apiKey: providerId={}, name={}, admin={}",
                            p.getId(), p.getName(),
                            principal != null ? principal.getName() : "unknown");
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("apiKey", plain);
                    return ResponseEntity.ok((Map<String, Object>) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 创建 Provider */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AiProviderRequest req) {
        if (req.type == null || req.name == null || req.baseUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "type/name/baseUrl 必填"));
        }
        AiProvider entity = new AiProvider();
        applyRequest(entity, req);
        AiProvider saved = repository.save(entity);
        configService.refresh();
        log.info("AI Provider 创建: type={}, name={}", saved.getType(), saved.getName());
        return ResponseEntity.ok(toSafeMap(saved));
    }

    /** 更新 Provider */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AiProviderRequest req) {
        return repository.findById(id)
                .map(entity -> {
                    applyRequest(entity, req);
                    AiProvider saved = repository.save(entity);
                    configService.refresh();
                    log.info("AI Provider 更新: id={}, type={}, name={}", id, saved.getType(), saved.getName());
                    return ResponseEntity.ok(toSafeMap(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 删除 Provider */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        configService.refresh();
        log.info("AI Provider 删除: id={}", id);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    /**
     * v2.7.2 设为默认（同 type 内其他默认自动取消）
     * 专门端点，避免 PATCH 误清空其他列
     */
    @PostMapping("/{id}/set-default")
    public ResponseEntity<Map<String, Object>> setDefault(@PathVariable Long id) {
        return repository.findById(id)
                .map(entity -> {
                    entity.setIsDefault(true);
                    // 同一 type 的其他 is_default 取消
                    repository.findByType(entity.getType()).forEach(p -> {
                        if (!p.getId().equals(entity.getId())) {
                            p.setIsDefault(false);
                            repository.save(p);
                        }
                    });
                    AiProvider saved = repository.save(entity);
                    configService.refresh();
                    log.info("AI Provider 设为默认: id={}, type={}, name={}", id, saved.getType(), saved.getName());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "status", "ok",
                            "message", "已设为默认",
                            "provider", toSafeMap(saved)
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 手动触发配置刷新（不修改数据，仅清缓存） */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        configService.refresh();
        return ResponseEntity.ok(Map.of("message", "配置已刷新"));
    }

    /**
     * v2.7.1 拉取 Provider 可用模型列表
     * POST /api/admin/ai/fetch-models
     * body: { id? , baseUrl? , apiKey? }
     * 优先用 id 取 DB；找不到则用前端临时传
     */
    @PostMapping("/fetch-models")
    public ResponseEntity<Map<String, Object>> fetchModels(@RequestBody Map<String, Object> body) {
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");
        Object idObj = body.get("id");
        // v2.7.2：优先用 id 取 DB（编辑已有 Provider 时前端 apiKey 可能为空）
        if (idObj instanceof Number) {
            AiProvider p = repository.findById(((Number) idObj).longValue()).orElse(null);
            if (p != null) {
                // DB 里有完整配置 → 用 DB 的 baseUrl + apiKey（避免空 apiKey 调外部 LLM 服务失败）
                baseUrl = p.getBaseUrl();
                apiKey = configService.decryptKey(p.getApiKey());
            }
        }
        // 前端临时填的 apiKey/baseUrl 优先（用于新增 Provider 测试）
        if (body.get("baseUrl") != null && !((String) body.get("baseUrl")).isBlank()) {
            baseUrl = (String) body.get("baseUrl");
        }
        if (body.get("apiKey") != null && !((String) body.get("apiKey")).isBlank()) {
            apiKey = (String) body.get("apiKey");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "baseUrl 必填"));
        }

        try {
            String url = baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl.replaceAll("/+$", "") + "/v1/models";
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            if (apiKey != null && !apiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = "";
                try { err = new String(conn.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignore) {}
                return ResponseEntity.status(code).body(Map.of(
                    "error", "模型服务返回 " + code,
                    "detail", err.length() > 500 ? err.substring(0, 500) : err
                ));
            }
            String body2 = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(body2);
            java.util.List<String> models = new java.util.ArrayList<>();
            if (root.has("data") && root.get("data").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode m : root.get("data")) {
                    String id = m.path("id").asText("");
                    if (!id.isBlank() && !id.toLowerCase().contains("embed")
                            && !id.toLowerCase().contains("rerank")) {
                        models.add(id);
                    }
                }
            }
            models.sort(String.CASE_INSENSITIVE_ORDER);
            log.info("Provider fetch-models: url={}, models={}", url, models.size());
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "url", url,
                "models", models
            ));
        } catch (Exception e) {
            log.error("fetch-models 失败: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * v2.7.1 统一测试连接
     * POST /api/admin/ai/test-connection
     * body: { id? , baseUrl? , apiKey? , model? }
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String baseUrl = (String) body.get("baseUrl");
        String apiKey = (String) body.get("apiKey");
        String model = (String) body.getOrDefault("model", "");
        Object idObj = body.get("id");
        if ((baseUrl == null || baseUrl.isBlank()) && idObj instanceof Number) {
            AiProvider p = repository.findById(((Number) idObj).longValue()).orElse(null);
            if (p != null) {
                baseUrl = p.getBaseUrl();
                apiKey = configService.decryptKey(p.getApiKey());
                if (model.isEmpty()) model = p.getDefaultModel();
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "baseUrl 必填"));
        }

        try {
            String url = baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl.replaceAll("/+$", "") + "/v1/models";
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            if (apiKey != null && !apiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            int code = conn.getResponseCode();
            long start = System.currentTimeMillis();
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", code >= 200 && code < 300 ? "ok" : "fail");
            result.put("url", url);
            result.put("httpCode", code);
            result.put("model", model);

            if (code >= 200 && code < 300) {
                String body2 = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(body2);
                int count = 0;
                if (root.has("data") && root.get("data").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode m : root.get("data")) {
                        String id = m.path("id").asText("");
                        if (!id.isBlank() && !id.toLowerCase().contains("embed")
                                && !id.toLowerCase().contains("rerank")) {
                            count++;
                        }
                    }
                }
                result.put("modelCount", count);
                result.put("message", "连接成功，发现 " + count + " 个可用模型");
            } else {
                String err = "";
                try { err = new String(conn.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignore) {}
                result.put("error", "服务返回 " + code);
                result.put("detail", err.length() > 500 ? err.substring(0, 500) : err);
                result.put("message", "连接失败（HTTP " + code + "）");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("test-connection 失败: {}", e.getMessage());
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", "fail");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("message", "连接失败：" + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // ===== 私有方法 =====

    private void applyRequest(AiProvider entity, AiProviderRequest req) {
        // v2.7.2：只更新显式传入的字段（null 表示"不修改"），避免 PATCH 式调用清空其他列
        if (req.type != null) entity.setType(req.type);
        if (req.name != null) entity.setName(req.name);
        if (req.baseUrl != null) entity.setBaseUrl(req.baseUrl);
        // API Key：null 表示不修改；空字符串表示"清空"
        if (req.apiKey != null) {
            String encrypted = req.apiKey.isEmpty() ? null : configService.encryptKey(req.apiKey);
            entity.setApiKey(encrypted);
        }
        if (req.defaultModel != null) entity.setDefaultModel(req.defaultModel);
        if (req.timeout != null) entity.setTimeout(req.timeout);
        if (req.enabled != null) entity.setEnabled(req.enabled);
        if (req.isDefault != null) {
            entity.setIsDefault(req.isDefault);
            if (Boolean.TRUE.equals(req.isDefault)) {
                // 同一 type 的其他 is_default 取消（需要 type 才能定位）
                String typeForCleanup = req.type != null ? req.type : entity.getType();
                if (typeForCleanup != null) {
                    repository.findByType(typeForCleanup).forEach(p -> {
                        if (!p.getId().equals(entity.getId())) {
                            p.setIsDefault(false);
                            repository.save(p);
                        }
                    });
                }
            }
        }
        if (req.remark != null) entity.setRemark(req.remark);
        if (req.extra != null) entity.setExtra(req.extra);
    }

    /**
     * v2.7.3 列表/详情/打开弹窗等"只读"场景：不返回明文 API Key，只返密文 + 掩码。
     * 前端展示密文即可，需要查看明文时走 /reveal-key。
     */
    private Map<String, Object> toSafeMap(AiProvider p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("type", p.getType());
        m.put("name", p.getName());
        m.put("baseUrl", p.getBaseUrl());
        String stored = p.getApiKey();
        m.put("hasKey", stored != null && !stored.isEmpty());
        m.put("apiKeyCipher", stored == null ? "" : stored);
        m.put("apiKeyMask", maskKey(stored));
        m.put("defaultModel", p.getDefaultModel());
        m.put("timeout", p.getTimeout());
        m.put("enabled", p.getEnabled());
        m.put("isDefault", p.getIsDefault());
        m.put("remark", p.getRemark());
        m.put("extra", p.getExtra());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    /** 掩码：保留前 4 + 后 2，中间 ******；无 key 时返回 "" */
    private String maskKey(String storedCipher) {
        if (storedCipher == null || storedCipher.isEmpty()) return "";
        String plain = configService.decryptKey(storedCipher);
        if (plain == null || plain.isEmpty()) return "";
        if (plain.length() <= 8) return "********";
        return plain.substring(0, 4) + "******" + plain.substring(plain.length() - 2);
    }

    /** DTO（接收前端 JSON） */
    public static class AiProviderRequest {
        public String type;
        public String name;
        public String baseUrl;
        public String apiKey;
        public String defaultModel;
        public Integer timeout;
        public Boolean enabled;
        public Boolean isDefault;
        public String remark;
        public String extra;
    }
}