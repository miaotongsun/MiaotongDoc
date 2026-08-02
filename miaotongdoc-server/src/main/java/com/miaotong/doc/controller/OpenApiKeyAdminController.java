package com.miaotong.doc.controller;

import com.miaotong.doc.entity.OpenApiKey;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.OpenApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外 API Key 管理 Controller（管理员端）
 *
 * 设计原则：
 *   - 仅 admin 可访问
 *   - 颁发 Key 后，明文密钥仅在响应中返回一次（前端必须弹出提示用户复制）
 *   - 吊销是软删除（enabled=false + revoked_at=NOW）
 */
@RestController
@RequestMapping("/api/admin/openapi")
@RequiredArgsConstructor
public class OpenApiKeyAdminController {

    private final OpenApiKeyService openApiKeyService;

    private void checkAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            throw new BusinessException("需要管理员权限");
        }
    }

    /** 列表（仅展示前缀，不返回明文） */
    @GetMapping("/keys")
    public ResponseEntity<List<OpenApiKey>> listKeys(HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return ResponseEntity.ok(openApiKeyService.listKeys());
    }

    /** 颁发新 Key（明文仅返回一次） */
    @PostMapping("/keys")
    public ResponseEntity<Map<String, Object>> createKey(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);

        String name = (String) body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("请填写用途名称");
        }

        String ownerSystem = (String) body.get("ownerSystem");
        String contact = (String) body.get("contact");
        String allowedIps = (String) body.get("allowedIps");
        Integer rateLimit = body.get("rateLimit") != null
                ? Integer.valueOf(body.get("rateLimit").toString()) : null;

        LocalDateTime expiresAt = null;
        if (body.get("expiresAt") != null) {
            String exp = body.get("expiresAt").toString();
            // 支持 ISO 字符串如 "2026-12-31T00:00:00" 或 yyyy-MM-dd HH:mm:ss
            try {
                expiresAt = LocalDateTime.parse(exp.replace(" ", "T"));
            } catch (Exception e) {
                throw new BusinessException("过期时间格式错误");
            }
        }

        Long createdBy = (Long) httpRequest.getAttribute("userId");
        OpenApiKey saved = openApiKeyService.createKey(
                name, ownerSystem, contact, expiresAt, allowedIps, rateLimit, createdBy);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", saved.getId());
        // 统一对外字段:用 apiKey(测试/前端/SDK 期望值),accessKey 字段保留为别名兼容
        resp.put("apiKey", saved.getAccessKey());   // 明文仅返回一次
        resp.put("accessKey", saved.getAccessKey()); // 别名(向后兼容)
        resp.put("secretPrefix", saved.getSecretPrefix());
        resp.put("name", saved.getName());
        resp.put("ownerSystem", saved.getOwnerSystem());
        resp.put("expiresAt", saved.getExpiresAt());
        resp.put("rateLimit", saved.getRateLimitPerMinute());
        resp.put("createdAt", saved.getCreatedAt());
        resp.put("notice", "请立即复制保存此密钥，后续将无法再次查看完整明文");
        return ResponseEntity.ok(resp);
    }

    /** 吊销 Key */
    @DeleteMapping("/keys/{id}")
    public ResponseEntity<Map<String, String>> revokeKey(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        openApiKeyService.revokeKey(id);
        return ResponseEntity.ok(Map.of("message", "Key 已吊销"));
    }

    /** 查看 Key 明文（仅管理员，审计记录） */
    @GetMapping("/keys/{id}/reveal")
    public ResponseEntity<Map<String, String>> revealKey(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        String accessKey = openApiKeyService.revealKey(id);
        Map<String, String> body = new HashMap<>();
        body.put("apiKey", accessKey);     // 统一对外字段
        body.put("accessKey", accessKey);  // 别名兼容
        return ResponseEntity.ok(body);
    }

    /** 启用 Key */
    @PutMapping("/keys/{id}/enable")
    public ResponseEntity<Map<String, String>> enableKey(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        openApiKeyService.enableKey(id);
        return ResponseEntity.ok(Map.of("message", "Key 已启用"));
    }

    /** 禁用 Key（软禁用，可再启用） */
    @PutMapping("/keys/{id}/disable")
    public ResponseEntity<Map<String, String>> disableKey(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        openApiKeyService.disableKey(id);
        return ResponseEntity.ok(Map.of("message", "Key 已禁用"));
    }

    /** 删除 Key（硬删除，不可恢复） */
    @DeleteMapping("/keys/{id}/hard")
    public ResponseEntity<Map<String, String>> deleteKey(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        openApiKeyService.deleteKey(id);
        return ResponseEntity.ok(Map.of("message", "Key 已删除"));
    }
}