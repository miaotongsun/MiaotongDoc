package com.miaotong.doc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OCR 识别进度推送服务（SSE）
 *
 * 流程：
 *   1. 前端调用 POST /api/pdf/{id}/recognize（异步执行，立即返回 202）
 *   2. 前端通过 GET /api/pdf/{id}/recognize-stream 建立 SSE 连接
 *   3. 识别过程中调用 send(docId, percent, message) 推送进度
 *   4. 完成后 send "done" 或 "error" 事件，关闭连接
 *
 * 设计权衡：
 *   - 用 Map 持有 emitter，文档级隔离
 *   - 超时 5 分钟（与 OCR 上限匹配）
 *   - 异常时 send error 事件并 complete
 *
 * @since v2.4 OCR SSE 进度推送
 */
@Slf4j
@Service
public class OcrProgressService {

    /** 单文档的最大 SSE 连接数（防止前端多次挂载导致资源泄漏） */
    private static final int MAX_EMITTERS_PER_DOC = 3;

    /** 文档 ID → SseEmitter 列表 */
    private final Map<Long, java.util.List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 注册 SSE 连接 */
    public SseEmitter register(Long docId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        emitter.onCompletion(() -> removeEmitter(docId, emitter));
        emitter.onTimeout(() -> removeEmitter(docId, emitter));
        emitter.onError(e -> removeEmitter(docId, emitter));

        emitters.compute(docId, (k, list) -> {
            if (list == null) list = new java.util.concurrent.CopyOnWriteArrayList<>();
            // 防止单个文档挂太多连接
            if (list.size() >= MAX_EMITTERS_PER_DOC) {
                SseEmitter oldest = list.remove(0);
                try { oldest.complete(); } catch (Exception ignored) {}
            }
            list.add(emitter);
            return list;
        });

        // 立即发送一个 connected 事件（让前端确认连接成功）
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("docId", docId, "ts", System.currentTimeMillis())));
        } catch (IOException e) {
            removeEmitter(docId, emitter);
        }

        log.debug("SSE 注册: docId={}, 当前连接数={}", docId, emitters.get(docId).size());
        return emitter;
    }

    /** 推送进度 */
    public void send(Long docId, int percent, String message) {
        var list = emitters.get(docId);
        if (list == null || list.isEmpty()) return;

        var payload = Map.of(
                "percent", percent,
                "message", message,
                "ts", System.currentTimeMillis()
        );

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(payload));
            } catch (IOException e) {
                log.debug("SSE 发送失败: {}", e.getMessage());
                removeEmitter(docId, emitter);
            }
        }
    }

    /** 完成事件 */
    public void sendDone(Long docId, String engine) {
        var list = emitters.get(docId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("engine", engine, "ts", System.currentTimeMillis())));
                emitter.complete();
            } catch (Exception ignored) {}
            removeEmitter(docId, emitter);
        }
    }

    /** 错误事件 */
    public void sendError(Long docId, String error) {
        var list = emitters.get(docId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", error, "ts", System.currentTimeMillis())));
                emitter.complete();
            } catch (Exception ignored) {}
            removeEmitter(docId, emitter);
        }
    }

    private void removeEmitter(Long docId, SseEmitter emitter) {
        emitters.computeIfPresent(docId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}