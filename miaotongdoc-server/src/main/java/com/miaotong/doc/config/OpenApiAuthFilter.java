package com.miaotong.doc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.OpenApiKey;
import com.miaotong.doc.repository.OpenApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 对外 API 鉴权过滤器（/api/open/**）
 *
 * 流程：
 *   1. 检查路径 → 非 /api/open/** 放行
 *   2. 读取 X-API-Key → 缺失返回 40101
 *   3. 数据库查询（带本地缓存）→ 不存在/吊销/过期 → 40102
 *   4. IP 白名单检查 → 不匹配 → 40301
 *   5. Redis 限流计数 → 超限 → 42901
 *   6. 检查 Idempotency-Key（Redis 缓存）→ 已存在 → 返回缓存响应
 *   7. 生成 requestId，写入 request 属性供后续读取
 *   8. 异步更新 last_used_at
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/open/";
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String ATTR_KEY = "openApiKey";
    private static final String ATTR_REQUEST_ID = "openApiRequestId";

    private static final long CACHE_TTL_SECONDS = 60; // Key 缓存 1 分钟
    private static final String RATE_LIMIT_KEY = "openapi:rate:";
    private static final String IDEMPOTENCY_KEY = "openapi:idem:";

    private final OpenApiKeyRepository keyRepository;
    private final OpenApiProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        if (!properties.getEnabled()) {
            writeError(response, 50001, "对外 API 已禁用", null);
            return;
        }

        // 1. 生成 requestId
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTR_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        // 2. 读取 API Key
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            writeError(response, 40101, "缺少 X-API-Key 头", requestId);
            return;
        }

        // 3. 校验 Key
        Optional<OpenApiKey> keyOpt = keyRepository.findValidKey(apiKey, LocalDateTime.now());
        if (keyOpt.isEmpty()) {
            writeError(response, 40102, "API Key 无效、已吊销或已过期", requestId);
            return;
        }
        OpenApiKey key = keyOpt.get();

        // 4. IP 白名单检查
        if (key.getAllowedIps() != null && !key.getAllowedIps().trim().isEmpty()) {
            String clientIp = getClientIp(request);
            String[] allowed = key.getAllowedIps().split(",");
            boolean match = Arrays.stream(allowed)
                    .map(String::trim)
                    .anyMatch(ip -> ip.equals(clientIp));
            if (!match) {
                writeError(response, 40301, "客户端 IP 不在白名单内", requestId);
                return;
            }
        }

        // 5. 限流检查（Redis 计数）
        int rateLimit = key.getRateLimitPerMinute() != null
                ? key.getRateLimitPerMinute() : properties.getRateLimitDefault();
        try {
            String rateKey = RATE_LIMIT_KEY + key.getId();
            Long count = redisTemplate.opsForValue().increment(rateKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(rateKey, 60, TimeUnit.SECONDS);
            }
            if (count != null && count > rateLimit) {
                writeError(response, 42901, "请求过于频繁，请稍后再试", requestId);
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时降级放行，不阻塞正常请求
            log.warn("Redis 限流检查失败，降级放行: {}", e.getMessage());
        }

        // 6. 幂等性检查（仅 POST/PUT 创建类请求）
        String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()
                && ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod()))) {
            try {
                String cacheKey = IDEMPOTENCY_KEY + key.getId() + ":" + idempotencyKey;
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    response.setStatus(200);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(cached);
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis 幂等检查失败: {}", e.getMessage());
            }
        }

        // 7. 写入 request 属性
        request.setAttribute(ATTR_KEY, key);

        // 8. 异步更新 last_used_at（不阻塞）
        Long keyId = key.getId();
        new Thread(() -> {
            try {
                keyRepository.findById(keyId).ifPresent(k -> {
                    k.setLastUsedAt(LocalDateTime.now());
                    keyRepository.save(k);
                });
            } catch (Exception e) {
                log.warn("更新 last_used_at 失败: {}", e.getMessage());
            }
        }).start();

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.error("对外 API 处理异常: requestId={}", requestId, e);
            writeError(response, 50001, "服务内部错误: " + e.getMessage(), requestId);
        }
    }

    private void writeError(HttpServletResponse response, int code,
                             String message, String requestId) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (requestId != null) err.put("requestId", requestId);
        // 业务码 → HTTP 状态码映射:
        //   401xx(认证失败) → 401
        //   403xx(权限不足) → 403
        //   429xx(限流) → 429
        //   400xx(参数/业务错误) → 400
        //   5xxxx(服务器错误) → 500
        //   其他 → 200(成功)
        int httpStatus;
        if (code >= 40100 && code < 40200) httpStatus = 401;
        else if (code >= 40300 && code < 40400) httpStatus = 403;
        else if (code >= 42900 && code < 43000) httpStatus = 429;
        else if (code >= 40000 && code < 50000) httpStatus = 400;
        else if (code >= 50000) httpStatus = 500;
        else httpStatus = 200;
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(err));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    public static Long getRequestKeyId(HttpServletRequest request) {
        OpenApiKey key = (OpenApiKey) request.getAttribute(ATTR_KEY);
        return key != null ? key.getId() : null;
    }

    public static String getRequestId(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_REQUEST_ID);
    }
}