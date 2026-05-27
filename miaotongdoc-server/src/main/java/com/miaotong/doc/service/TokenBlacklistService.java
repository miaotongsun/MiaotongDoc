package com.miaotong.doc.service;

import com.miaotong.doc.entity.TokenBlacklist;
import com.miaotong.doc.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository repository;
    private final RedisTemplate<String, String> redis;
    private static final String CACHE_PREFIX = "token:blacklist:";

    public void blacklist(String jti, Long userId, Instant expiresAt, String reason) {
        TokenBlacklist entry = new TokenBlacklist();
        entry.setTokenJti(jti);
        entry.setUserId(userId);
        entry.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        entry.setReason(reason);
        repository.save(entry);

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) {
            redis.opsForValue().set(CACHE_PREFIX + jti, "1", ttl);
        }
    }

    public boolean isBlacklisted(String jti) {
        if (Boolean.TRUE.equals(redis.hasKey(CACHE_PREFIX + jti))) {
            return true;
        }
        return repository.existsByTokenJti(jti);
    }

    public void blacklistAllUserTokens(Long userId, String reason) {
        List<TokenBlacklist> userTokens = repository.findByUserId(userId);
        for (TokenBlacklist token : userTokens) {
            redis.opsForValue().set(CACHE_PREFIX + token.getTokenJti(), "1",
                    Duration.between(LocalDateTime.now(), token.getExpiresAt()));
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        repository.deleteExpiredTokens(LocalDateTime.now());
    }
}
