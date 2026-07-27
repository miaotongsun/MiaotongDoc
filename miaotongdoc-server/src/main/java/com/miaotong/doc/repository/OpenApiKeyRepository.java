package com.miaotong.doc.repository;

import com.miaotong.doc.entity.OpenApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OpenApiKeyRepository extends JpaRepository<OpenApiKey, Long> {

    /**
     * 查询有效 Key（启用 + 未吊销 + 未过期）
     * 用于 OpenApiAuthFilter 鉴权
     */
    @Query("SELECT k FROM OpenApiKey k WHERE k.accessKey = :accessKey " +
            "AND k.enabled = true " +
            "AND k.revokedAt IS NULL " +
            "AND (k.expiresAt IS NULL OR k.expiresAt > :now)")
    Optional<OpenApiKey> findValidKey(@Param("accessKey") String accessKey,
                                      @Param("now") LocalDateTime now);

    /** 列表展示（含已吊销的） */
    List<OpenApiKey> findAllByOrderByCreatedAtDesc();

    /** 仅查询启用的 Key（用于启动自检） */
    List<OpenApiKey> findAllByEnabledTrue();
}