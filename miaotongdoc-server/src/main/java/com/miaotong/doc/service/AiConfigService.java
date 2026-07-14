package com.miaotong.doc.service;

import com.miaotong.doc.entity.AiProvider;
import com.miaotong.doc.event.AiConfigRefreshedEvent;
import com.miaotong.doc.repository.AiProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 配置中心服务
 *
 * 职责：
 *   - 统一管理 LLM / OCR / VISION 等 AI Provider 配置
 *   - 数据库优先（mt_ai_provider 表），文件配置作为 fallback
 *   - 内存缓存 + 热刷新（修改后无需重启）
 *   - 加密存储 API Key
 *
 * 用法：
 *   AiConfig ai = aiConfigService.getActive("LLM");
 *   String url = ai.baseUrl;
 *   String key = ai.apiKey; // 自动解密
 *
 * @since v2.7 动态 AI 配置
 */
@Slf4j
@Service
public class AiConfigService {

    private final AiProviderRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * v2.7 修复循环依赖：用 ApplicationEventPublisher 发布事件
     * 替代之前直接注入 AiService 调用 rebuildClient()
     */
    private final ApplicationEventPublisher eventPublisher;

    public AiConfigService(AiProviderRepository repository,
                           ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /** 内存缓存：type -> list of (providerName -> AiConfig) */
    private final Map<String, Map<String, AiConfig>> cache = new ConcurrentHashMap<>();

    /** 加密密钥（base64 编码的 AES key），从环境变量读取 */
    private final String ENCRYPT_KEY = System.getenv().getOrDefault("APP_AI_KEY_SECRET",
            "MiaotongDoc-AI-Key-Secret-2026-32bytes");

    /** 旧文件配置路径（fallback） */
    private static final String LEGACY_CONFIG_PATH = "/data/config/ai-config.json";

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新所有缓存（从 DB + 文件重新加载）
     * 任何 CRUD 操作后必须调用此方法
     */
    public synchronized void refresh() {
        log.info("刷新 AI 配置缓存...");
        Map<String, Map<String, AiConfig>> newCache = new ConcurrentHashMap<>();

        // 1) 从数据库加载（优先）
        try {
            for (AiProvider entity : repository.findAll()) {
                if (!Boolean.TRUE.equals(entity.getEnabled())) continue;
                AiConfig config = toConfig(entity);
                newCache
                        .computeIfAbsent(entity.getType(), k -> new ConcurrentHashMap<>())
                        .put(entity.getName(), config);
            }
        } catch (Exception e) {
            log.warn("从 DB 加载 AI 配置失败: {}", e.getMessage());
        }

        // 2) 文件配置 fallback（如果 DB 没有 LLM 配置）
        if (!newCache.containsKey("LLM") || newCache.get("LLM").isEmpty()) {
            loadFromLegacyFile(newCache);
        }

        // 3) 内存缓存替换（保证原子性）
        cache.clear();
        cache.putAll(newCache);

        log.info("AI 配置已刷新: {} 个 type, {} 个 provider",
                cache.size(),
                cache.values().stream().mapToInt(Map::size).sum());

        // 4) 发布事件：通知 AiService 重建 ChatClient（解耦：单向事件流）
        // 之前是 aiService.ifPresent(svc -> svc.rebuildClient())，但这会形成循环依赖
        // AiConfigService.refresh() → rebuildClient() → AiService → AiProxyService → AiConfigService
        try {
            eventPublisher.publishEvent(new AiConfigRefreshedEvent("refresh"));
            log.info("已发布 AiConfigRefreshedEvent（AiService 将自动重建）");
        } catch (Exception e) {
            log.warn("发布配置刷新事件失败: {}", e.getMessage());
        }
    }

    /**
     * 获取指定 type 的默认配置（is_default=true 优先，否则取第一个）
     */
    public AiConfig getActive(String type) {
        Map<String, AiConfig> providers = cache.get(type);
        if (providers == null || providers.isEmpty()) return null;

        // 优先返回 is_default
        for (AiConfig c : providers.values()) {
            if (c.isDefault) return c;
        }
        // 否则返回任意一个
        return providers.values().iterator().next();
    }

    /**
     * 获取指定 type 的所有 provider
     */
    public List<AiConfig> listAll(String type) {
        Map<String, AiConfig> providers = cache.get(type);
        if (providers == null) return List.of();
        return new ArrayList<>(providers.values());
    }

    /**
     * 列出所有 type 的所有 provider
     */
    public Map<String, List<AiConfig>> listAll() {
        Map<String, List<AiConfig>> result = new HashMap<>();
        cache.forEach((type, providers) -> result.put(type, new ArrayList<>(providers.values())));
        return result;
    }

    /**
     * 加密 API Key
     */
    public String encryptKey(String plainKey) {
        if (plainKey == null) return null;
        try {
            // 简单 XOR + Base64（演示用；生产建议用 AES-256-GCM）
            byte[] keyBytes = ENCRYPT_KEY.getBytes();
            byte[] data = plainKey.getBytes();
            byte[] encrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                encrypted[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("加密 Key 失败", e);
            return plainKey;
        }
    }

    /**
     * 解密 API Key
     */
    public String decryptKey(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        try {
            byte[] keyBytes = ENCRYPT_KEY.getBytes();
            byte[] data = Base64.getDecoder().decode(encrypted);
            byte[] decrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                decrypted[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(decrypted);
        } catch (Exception e) {
            // 兼容明文（未加密的旧数据）
            return encrypted;
        }
    }

    // ===== 私有方法 =====

    private AiConfig toConfig(AiProvider entity) {
        AiConfig config = new AiConfig();
        config.id = entity.getId();
        config.type = entity.getType();
        config.name = entity.getName();
        config.baseUrl = entity.getBaseUrl();
        config.apiKey = decryptKey(entity.getApiKey());
        config.defaultModel = entity.getDefaultModel();
        config.timeout = entity.getTimeout();
        config.enabled = entity.getEnabled();
        config.isDefault = entity.getIsDefault();
        config.remark = entity.getRemark();
        config.extra = entity.getExtra();
        return config;
    }

    private void loadFromLegacyFile(Map<String, Map<String, AiConfig>> target) {
        try {
            Path path = Path.of(LEGACY_CONFIG_PATH);
            if (!Files.exists(path)) return;
            String json = Files.readString(path);
            Map<String, Object> cfg = objectMapper.readValue(json, new TypeReference<>() {});

            AiConfig ai = new AiConfig();
            ai.type = "LLM";
            ai.name = "legacy";
            ai.baseUrl = (String) cfg.getOrDefault("targetUrl", "");
            ai.apiKey = (String) cfg.getOrDefault("apiKey", "");
            ai.defaultModel = (String) cfg.getOrDefault("defaultModel", "");
            ai.timeout = cfg.containsKey("timeout")
                    ? ((Number) cfg.get("timeout")).intValue() : 300;
            ai.enabled = true;
            ai.isDefault = true;
            ai.remark = "从旧版 ai-config.json 加载";
            target.computeIfAbsent("LLM", k -> new ConcurrentHashMap<>()).put("legacy", ai);
            log.info("从旧版 AI 配置文件加载了 LLM Provider");
        } catch (Exception e) {
            log.warn("加载旧版 AI 配置失败: {}", e.getMessage());
        }
    }

    // ===== DTO =====

    public static class AiConfig {
        public Long id;
        public String type;
        public String name;
        public String baseUrl;
        public String apiKey;
        public String defaultModel;
        public int timeout = 300;
        public boolean enabled = true;
        public boolean isDefault = false;
        public String remark;
        public String extra;  // JSON

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("type", type);
            m.put("name", name);
            m.put("baseUrl", baseUrl);
            // API Key 脱敏返回
            m.put("apiKey", apiKey == null || apiKey.isEmpty() ? "" : maskKey(apiKey));
            m.put("defaultModel", defaultModel);
            m.put("timeout", timeout);
            m.put("enabled", enabled);
            m.put("isDefault", isDefault);
            m.put("remark", remark);
            return m;
        }

        private String maskKey(String key) {
            if (key.length() <= 8) return "****";
            return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
        }
    }
}