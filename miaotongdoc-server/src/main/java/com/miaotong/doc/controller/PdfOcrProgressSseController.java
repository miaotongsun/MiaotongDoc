package com.miaotong.doc.controller;

import com.miaotong.doc.entity.PdfTask;
import com.miaotong.doc.repository.PdfTaskRepository;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * OCR 任务进度 SSE 推送(OCR AI 改造 v3)
 *
 * 接口:GET /api/pdf/{docId}/ocr-progress/{taskId} (SSE)
 * 流程:
 *   1. SSE 连接建立 → 立即发送当前任务快照(connected)
 *   2. 启动 1s 间隔轮询 mt_pdf_task 表的 progress 字段
 *   3. 检测到变化则推送 progress 事件
 *   4. 任务完成(completed/failed)→ 推送 done/error 事件,关闭连接
 *
 * 简化策略:用 1s 轮询而不是数据库 CDC(更简单,延迟 1s 可接受)
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf/{docId}/ocr-progress")
@RequiredArgsConstructor
public class PdfOcrProgressSseController {

    private final PdfTaskRepository pdfTaskRepository;
    private final JwtUtil jwtUtil;

    @GetMapping(value = "/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter progress(
            @PathVariable Long docId,
            @PathVariable Long taskId,
            HttpServletRequest request) {

        // 鉴权(用标准 Spring Security 注解放行更优雅,这里简化为直接校验)
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("未登录"));
            return e;
        }
        String token = auth.substring(7);
        if (!jwtUtil.validateToken(token)) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("Token 无效"));
            return e;
        }

        SseEmitter emitter = new SseEmitter(600_000L); // 10 分钟超时
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ocr-progress-" + taskId);
            t.setDaemon(true);
            return t;
        });

        // 标记最近推送的 progress,避免重复发
        final int[] lastProgress = {-1};
        final String[] lastStatus = {null};

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                PdfTask task = pdfTaskRepository.findById(taskId).orElse(null);
                if (task == null) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "TASK_NOT_FOUND",
                            "message", "任务不存在"));
                    emitter.complete();
                    scheduler.shutdown();
                    return;
                }
                // Phase 27 测试报告修复:校验 task 属于请求的 docId,防止越权访问
                if (!task.getDocumentId().equals(docId)) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "TASK_DOC_MISMATCH",
                            "message", "任务不属于该文档"));
                    emitter.complete();
                    scheduler.shutdown();
                    return;
                }

                // 状态变化或进度变化时推送
                String currentStatus = task.getStatus();
                int currentProgress = task.getProgress() == null ? 0 : task.getProgress();

                boolean changed = !java.util.Objects.equals(currentStatus, lastStatus[0])
                        || currentProgress != lastProgress[0];

                if (changed) {
                    sendEvent(emitter, "progress", Map.of(
                            "taskId", taskId,
                            "docId", docId,
                            "status", currentStatus,
                            "progress", currentProgress,
                            "currentPage", task.getCurrentPage() == null ? 0 : task.getCurrentPage(),
                            "totalPages", task.getTotalPages() == null ? 0 : task.getTotalPages(),
                            "message", statusMessage(currentStatus, currentProgress),
                            "engine", "paddleocr"));
                    lastStatus[0] = currentStatus;
                    lastProgress[0] = currentProgress;
                }

                // 终态:推送 done/error 并关闭
                if ("completed".equals(currentStatus)) {
                    sendEvent(emitter, "done", Map.of(
                            "taskId", taskId,
                            "status", "completed",
                            "progress", 100,
                            "message", "OCR 识别完成"));
                    emitter.complete();
                    scheduler.shutdown();
                } else if ("failed".equals(currentStatus)) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "OCR_FAILED",
                            "message", task.getErrorMessage() == null ? "OCR 失败" : task.getErrorMessage(),
                            "taskId", taskId));
                    emitter.complete();
                    scheduler.shutdown();
                }
            } catch (Exception e) {
                log.warn("OCR 进度推送异常: taskId={}, error={}", taskId, e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
                scheduler.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            future.cancel(true);
            scheduler.shutdown();
        });
        emitter.onTimeout(() -> {
            future.cancel(true);
            scheduler.shutdown();
        });
        emitter.onError(t -> {
            future.cancel(true);
            scheduler.shutdown();
        });

        return emitter;
    }

    private String statusMessage(String status, int progress) {
        if ("pending".equals(status)) return "任务排队中";
        if ("processing".equals(status)) return "正在识别(" + progress + "%)";
        if ("completed".equals(status)) return "识别完成";
        if ("failed".equals(status)) return "识别失败";
        return "处理中";
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.debug("SSE 发送失败 ({}): {}", name, e.getMessage());
        }
    }
}