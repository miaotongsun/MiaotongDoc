package com.miaotong.doc.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * AI 调用缓存服务
 * 使用 Redis 缓存相同的 AI 请求结果，减少 API 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 缓存 TTL：24 小时
     */
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 获取缓存
     * @param cacheKey 缓存键
     * @return 缓存内容，如果不存在返回 null
     */
    public String get(String cacheKey) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey);
            if (value != null) {
                log.debug("AI 缓存命中: {}", cacheKey);
                return value;
            }
            log.debug("AI 缓存未命中: {}", cacheKey);
            return null;
        } catch (Exception e) {
            log.warn("获取 AI 缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置缓存
     * @param cacheKey 缓存键
     * @param content 内容
     */
    public void set(String cacheKey, String content) {
        try {
            redisTemplate.opsForValue().set(cacheKey, content, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("AI 缓存已设置: {}", cacheKey);
        } catch (Exception e) {
            log.warn("设置 AI 缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 生成缓存键
     * @param type 类型（generate, chat, summarize, translate, rewrite 等）
     * @param docId 文档 ID
     * @param prompt 提示词
     * @return 缓存键
     */
    public String generateCacheKey(String type, Long docId, String prompt) {
        // 使用 SHA-256 哈希提示词，避免过长
        String promptHash = hashPrompt(prompt);
        return String.format("ai:cache:%s:%d:%s", type, docId, promptHash);
    }

    /**
     * 简单哈希提示词（使用 MD5）
     */
    private String hashPrompt(String prompt) {
        try {
            byte[] bytes = prompt.getBytes(StandardCharsets.UTF_8);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // 降级：直接使用提示词前 100 字符
            return prompt.length() > 100 ? prompt.substring(0, 100) : prompt;
        }
    }

    /**
     * 清除文档的所有 AI 缓存
     * @param docId 文档 ID
     */
    public void clearDocumentCache(Long docId) {
        try {
            // 扫描并删除该文档的所有缓存
            redisTemplate.keys("ai:cache:*:" + docId + ":*").forEach(key -> {
                redisTemplate.delete(key);
                log.debug("清除 AI 缓存: {}", key);
            });
        } catch (Exception e) {
            log.warn("清除文档 AI 缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 尝试从缓存获取，如果未命中则调用提供者获取并缓存
     * @param cacheKey 缓存键
     * @param provider 提供者（当缓存未命中时调用）
     * @return 结果内容
     */
    public String getOrCompute(String cacheKey, java.util.function.Supplier<String> provider) {
        String cached = get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String result = provider.get();
        if (result != null && !result.isBlank()) {
            set(cacheKey, result);
        }
        return result;
    }
}
