package com.miaotong.doc.service.ai;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用监控日志
 * 记录 AI 调用的耗时、模型、Token 统计、错误率等信息
 */
@Slf4j
@Component
public class AiMonitor {

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong errorCalls = new AtomicLong(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private final Map<String, Long> callCountsByModel = new ConcurrentHashMap<>();
    private final Map<String, Long> errorCountsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> callsByEndpoint = new ConcurrentHashMap<>();

    /**
     * 记录成功的 AI 调用（含 Token 统计）
     */
    public void recordSuccess(String endpoint, String model, long durationMs,
                              long inputTokens, long outputTokens) {
        totalCalls.incrementAndGet();
        successCalls.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        if (inputTokens > 0) totalInputTokens.addAndGet(inputTokens);
        if (outputTokens > 0) totalOutputTokens.addAndGet(outputTokens);
        callCountsByModel.merge(model != null ? model : "unknown", 1L, Long::sum);
        callsByEndpoint.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();

        log.info("[AI-Metric] endpoint={}, model={}, duration={}ms, inputTokens={}, outputTokens={}",
                endpoint, model, durationMs, inputTokens, outputTokens);
    }

    /**
     * 记录失败的 AI 调用
     */
    public void recordError(String endpoint, String model, long durationMs, String errorType) {
        totalCalls.incrementAndGet();
        errorCalls.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        errorCountsByType.merge(errorType != null ? errorType : "UnknownError", 1L, Long::sum);
        callCountsByModel.merge(model != null ? model : "unknown", 1L, Long::sum);
        callsByEndpoint.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();

        log.warn("[AI-Error] endpoint={}, model={}, duration={}ms, error={}",
                endpoint, model, durationMs, errorType);
    }

    /**
     * 记录 AI 调用（兼容旧方法）
     */
    public void recordCall(String endpoint, String model, long durationMs, boolean success, String error) {
        if (success) {
            recordSuccess(endpoint, model, durationMs, -1, -1);
        } else {
            recordError(endpoint, model, durationMs, error != null ? error : "UnknownError");
        }
    }

    /**
     * 获取统计信息（供管理后台展示）
     */
    public AiStats getStats() {
        AiStats stats = new AiStats();
        long tc = totalCalls.get();
        stats.setTotalCalls(tc);
        stats.setSuccessCalls(successCalls.get());
        stats.setErrorCalls(errorCalls.get());
        stats.setTotalDurationMs(totalDurationMs.get());
        stats.setAvgDurationMs(tc > 0 ? totalDurationMs.get() / tc : 0);
        stats.setTotalInputTokens(totalInputTokens.get());
        stats.setTotalOutputTokens(totalOutputTokens.get());
        stats.setTotalTokens(totalInputTokens.get() + totalOutputTokens.get());
        // 估算成本（Qwen 36-35B-A3B 价格，可配置）
        double costPer1KInput = 0.001;
        double costPer1KOutput = 0.002;
        stats.setEstimatedCost((totalInputTokens.get() * costPer1KInput + totalOutputTokens.get() * costPer1KOutput) / 1000.0);
        stats.setSuccessRate(tc > 0 ? (double) successCalls.get() / tc * 100 : 0);
        stats.setCallCountsByModel(new ConcurrentHashMap<>(callCountsByModel));
        stats.setErrorCountsByType(new ConcurrentHashMap<>(errorCountsByType));

        // 按端点统计
        Map<String, Long> endpointStats = new ConcurrentHashMap<>();
        callsByEndpoint.forEach((k, v) -> endpointStats.put(k, v.get()));
        stats.setCallsByEndpoint(endpointStats);

        return stats;
    }

    /**
     * 重置统计（用于测试）
     */
    public void reset() {
        totalCalls.set(0);
        successCalls.set(0);
        errorCalls.set(0);
        totalInputTokens.set(0);
        totalOutputTokens.set(0);
        totalDurationMs.set(0);
        callCountsByModel.clear();
        errorCountsByType.clear();
        callsByEndpoint.clear();
    }

    @Data
    public static class AiStats {
        private long totalCalls;
        private long successCalls;
        private long errorCalls;
        private long totalDurationMs;
        private long avgDurationMs;
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalTokens;
        private double estimatedCost;
        private double successRate;
        private Map<String, Long> callCountsByModel;
        private Map<String, Long> errorCountsByType;
        private Map<String, Long> callsByEndpoint;
    }
}
