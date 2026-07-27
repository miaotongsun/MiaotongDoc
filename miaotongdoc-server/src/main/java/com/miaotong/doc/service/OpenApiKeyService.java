package com.miaotong.doc.service;

import com.miaotong.doc.entity.OpenApiKey;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.OpenApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外 API Key 管理服务
 *
 * 核心原则：
 *   - 密钥明文仅在 createKey 时返回一次，列表只显示前缀
 *   - 吊销是软删除（设置 enabled=false 和 revoked_at）
 *   - 启动时自检：无可用 Key 记录 WARN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiKeyService {

    private static final String KEY_PREFIX = "ak_";
    private static final int KEY_RANDOM_LEN = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RANDOM_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final OpenApiKeyRepository repository;

    /**
     * 颁发新 Key
     *
     * @return 明文密钥（仅此次返回，后续无法再获取）
     */
    @Transactional
    public OpenApiKey createKey(String name, String ownerSystem, String contact,
                                 LocalDateTime expiresAt, String allowedIps,
                                 Integer rateLimit, Long createdBy) {
        OpenApiKey key = new OpenApiKey();
        String plainKey = generateKey();
        key.setAccessKey(plainKey);
        key.setSecretPrefix(plainKey.substring(0, Math.min(8, plainKey.length())));
        key.setName(name);
        key.setOwnerSystem(ownerSystem);
        key.setContact(contact);
        key.setEnabled(true);
        key.setExpiresAt(expiresAt);
        key.setRateLimitPerMinute(rateLimit != null ? rateLimit : 60);
        key.setAllowedIps(allowedIps);
        key.setCreatedBy(createdBy);
        OpenApiKey saved = repository.save(key);
        log.info("对外 API Key 颁发成功: id={}, name={}, prefix={}, issuedBy={}",
                saved.getId(), saved.getName(), saved.getSecretPrefix(), createdBy);
        return saved;
    }

    /** 吊销 Key */
    @Transactional
    public void revokeKey(Long id) {
        OpenApiKey key = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Key 不存在"));
        if (key.getRevokedAt() != null) {
            throw new BusinessException("Key 已被吊销");
        }
        key.setEnabled(false);
        key.setRevokedAt(LocalDateTime.now());
        repository.save(key);
        log.info("对外 API Key 吊销: id={}, prefix={}", id, key.getSecretPrefix());
    }

    /** 列表（不含明文） */
    public List<OpenApiKey> listKeys() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 查看完整 Key 明文（仅管理员操作）
     * 用于"查看"按钮，会被记录审计日志
     */
    public String revealKey(Long id) {
        OpenApiKey key = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Key 不存在"));
        log.warn("【审计】管理员查看 API Key 明文: id={}, name={}, prefix={}",
                id, key.getName(), key.getSecretPrefix());
        return key.getAccessKey();
    }

    /** 启用 Key */
    @Transactional
    public void enableKey(Long id) {
        OpenApiKey key = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Key 不存在"));
        if (key.getRevokedAt() != null) {
            throw new BusinessException("Key 已吊销，无法启用");
        }
        key.setEnabled(true);
        repository.save(key);
        log.info("对外 API Key 启用: id={}, prefix={}", id, key.getSecretPrefix());
    }

    /** 禁用 Key（软禁用，可再启用） */
    @Transactional
    public void disableKey(Long id) {
        OpenApiKey key = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Key 不存在"));
        if (key.getRevokedAt() != null) {
            throw new BusinessException("Key 已吊销，无需禁用");
        }
        key.setEnabled(false);
        repository.save(key);
        log.info("对外 API Key 禁用: id={}, prefix={}", id, key.getSecretPrefix());
    }

    /** 删除 Key（硬删除） */
    @Transactional
    public void deleteKey(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException("Key 不存在");
        }
        repository.deleteById(id);
        log.warn("【审计】对外 API Key 已删除: id={}", id);
    }

    /** 启动自检 */
    public void selfCheck() {
        List<OpenApiKey> active = repository.findAllByEnabledTrue();
        if (active.isEmpty()) {
            log.warn("【对外 API 自检】当前无可用 API Key，对外服务(/api/open/v1/**)将全部 40102");
            return;
        }
        LocalDateTime sevenDaysLater = LocalDateTime.now().plusDays(7);
        for (OpenApiKey key : active) {
            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(sevenDaysLater)) {
                log.warn("【对外 API 自检】Key 即将过期: id={}, name={}, prefix={}, expiresAt={}",
                        key.getId(), key.getName(), key.getSecretPrefix(), key.getExpiresAt());
            } else {
                log.info("【对外 API 自检】可用 Key: id={}, name={}, prefix={}, rateLimit={}/min",
                        key.getId(), key.getName(), key.getSecretPrefix(), key.getRateLimitPerMinute());
            }
        }
    }

    /** 生成 ak_ 前缀的 32 位随机密钥 */
    private String generateKey() {
        StringBuilder sb = new StringBuilder(KEY_PREFIX.length() + KEY_RANDOM_LEN);
        sb.append(KEY_PREFIX);
        for (int i = 0; i < KEY_RANDOM_LEN; i++) {
            sb.append(RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)]);
        }
        return sb.toString();
    }
}