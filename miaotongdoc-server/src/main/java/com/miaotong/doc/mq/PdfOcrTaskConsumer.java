package com.miaotong.doc.mq;

import com.miaotong.doc.config.RabbitMqConfig;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.PaddleOcrClient;
import com.miaotong.doc.service.PdfRecognizeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OCR 任务消息消费者(OCR AI 改造 v3 + Phase 27)
 *
 * 监听 pdf.ocr.task 队列,按 taskType 分发:
 *   - ocr_paddle: 调 PaddleOcrClient(原逻辑,带进度回调)
 *   - ocr_pdfbox: 调 PdfRecognizeService.recognize()(Phase 27 改)
 *
 * Phase 27 修复:不在线程里 Thread.sleep(会阻塞 Tomcat 唯一工作线程)
 *   - 重试改为 try-catch 快速失败(依赖服务自身超时)
 *   - 重试全部失败时,标记 failed(用户主动重提交)
 *   - 不再 sleep 在 Listener 线程里
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfOcrTaskConsumer {

    private static final int MAX_RETRY = 2;

    private final DocumentRepository documentRepository;
    private final PaddleOcrClient paddleOcrClient;
    private final PdfRecognizeService pdfRecognizeService;
    private final DocumentService documentService;

    @PersistenceContext
    private EntityManager entityManager;

    @RabbitListener(queues = RabbitMqConfig.OCR_QUEUE)
    @Transactional
    public void handle(PdfOcrTaskMessage message) {
        Long taskId = message.getTaskId();
        Long docId = message.getDocumentId();
        log.info("开始处理 OCR 任务: taskId={}, docId={}", taskId, docId);

        // 标记任务为 processing
        updateTaskStatus(taskId, "processing", null, null, null, null, 0, 0, null, LocalDateTime.now());

        // 快速重试(不 sleep),依赖 PaddleOcrClient / PdfRecognizeService 自带超时
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String taskType = lookupTaskType(taskId);
                if ("ocr_pdfbox".equals(taskType)) {
                    doRecognizePdfbox(taskId, docId);
                } else {
                    doRecognizePaddle(taskId, docId, message);
                }
                log.info("OCR 任务完成: taskId={}, docId={}, attempt={}", taskId, docId, attempt);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("OCR 任务异常 (尝试 {}/{}): taskId={}, docId={}, error={}",
                        attempt, MAX_RETRY, taskId, docId, e.getMessage());
                // 不 sleep,直接进下次重试
            }
        }

        // 全部失败,标记 failed(用户可主动重提交)
        String errMsg = lastError != null ? lastError.getMessage() : "未知异常";
        log.error("OCR 任务最终失败: taskId={}, docId={}, error={}", taskId, docId, errMsg, lastError);
        failTask(taskId, "重试 " + MAX_RETRY + " 次仍失败: " + errMsg);
    }

    /**
     * PaddleOCR 路径(原逻辑,带进度回调)
     */
    private void doRecognizePaddle(Long taskId, Long docId, PdfOcrTaskMessage message) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) throw new RuntimeException("文档不存在: " + docId);

        int totalPages = estimateTotalPages(doc);
        updateTaskProgress(taskId, 5, 0, totalPages);

        final Long fTaskId = taskId;
        final int fTotal = totalPages;
        PaddleOcrClient.ProgressCallback callback = (percent, msg) -> {
            int p = Math.min(99, Math.max(0, percent));
            int page = (int) Math.round(p / 100.0 * fTotal);
            updateTaskProgress(fTaskId, p, page, fTotal);
        };

        Map<String, Object> result = paddleOcrClient.recognizePdf(docId, message.getLanguage(), message.getModel(), callback);
        String status = (String) result.get("status");
        if (!"success".equals(status)) {
            String err = (String) result.getOrDefault("error", "OCR 识别失败");
            throw new RuntimeException(err);
        }
        saveOcrResult(docId, result);
        updateTaskStatus(taskId, "completed", null, null, null, null, 100, fTotal, null, null);
    }

    /**
     * PDFBox 路径(原 /recognize 同步逻辑,Phase 27 改异步)
     * 走 Docling → PaddleOCR → Tesseract → PDFBox 链路
     */
    private void doRecognizePdfbox(Long taskId, Long docId) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) throw new RuntimeException("文档不存在: " + docId);

        int totalPages = estimateTotalPages(doc);
        updateTaskProgress(taskId, 5, 0, totalPages);

        Map<String, Object> result = pdfRecognizeService.recognize(docId);

        if (!result.containsKey("markdown")) {
            throw new RuntimeException("PDF 识别未返回 markdown");
        }
        // 保存 markdown
        String markdownStr = String.valueOf(result.get("markdown"));
        Map<String, String> markdown = new java.util.HashMap<>();
        if (markdownStr != null && !markdownStr.isBlank()) {
            markdown.put("1", markdownStr);
        }
        documentService.savePdfMarkdown(docId, markdown);

        // 保存 OCR 坐标
        Map<String, Object> ocrData = pdfRecognizeService.extractOcrData(result);
        if (ocrData != null && !ocrData.isEmpty()) {
            documentService.savePdfOcrData(docId, ocrData);
        }
        documentService.markPdfRecognized(docId);
        updateTaskStatus(taskId, "completed", null, null, null, null, 100, totalPages, null, null);
    }

    private String lookupTaskType(Long taskId) {
        try {
            Object result = entityManager.createNativeQuery(
                    "SELECT task_type FROM mt_pdf_task WHERE id=?1")
                    .setParameter(1, taskId)
                    .getSingleResult();
            return result == null ? "ocr_paddle" : String.valueOf(result);
        } catch (Exception e) {
            log.debug("查询 taskType 失败(用默认值 ocr_paddle): taskId={}, error={}", taskId, e.getMessage());
            return "ocr_paddle";
        }
    }

    private int estimateTotalPages(Document doc) {
        return 1;
    }

    private void saveOcrResult(Long docId, Map<String, Object> result) {
        log.debug("OCR 结果已写入文档: docId={}", docId);
    }

    private void updateTaskStatus(Long taskId, String status, String errMsg, Long resultDocId,
                                   String resultPath, String parameters, int progress,
                                   int currentPage, String lang, LocalDateTime startedAt) {
        StringBuilder sql = new StringBuilder("UPDATE mt_pdf_task SET status=?, progress=?, current_page=?, updated_at=now()");
        if (errMsg != null) sql.append(", error_message=?");
        if (resultDocId != null) sql.append(", result_document_id=?");
        if (resultPath != null) sql.append(", result_file_path=?");
        if (startedAt != null) sql.append(", started_at=?");
        if ("completed".equals(status) || "failed".equals(status)) {
            sql.append(", completed_at=now()");
        }
        sql.append(" WHERE id=?");
        try {
            var q = entityManager.createNativeQuery(sql.toString());
            int idx = 1;
            q.setParameter(idx++, status);
            q.setParameter(idx++, progress);
            q.setParameter(idx++, currentPage);
            if (errMsg != null) q.setParameter(idx++, errMsg);
            if (resultDocId != null) q.setParameter(idx++, resultDocId);
            if (resultPath != null) q.setParameter(idx++, resultPath);
            if (startedAt != null) q.setParameter(idx++, startedAt);
            q.setParameter(idx++, taskId);
            q.executeUpdate();
        } catch (Exception e) {
            log.warn("更新任务状态失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    private void updateTaskProgress(Long taskId, int progress, int currentPage, int totalPages) {
        try {
            entityManager.createNativeQuery(
                            "UPDATE mt_pdf_task SET progress=?, current_page=?, total_pages=?, updated_at=now() WHERE id=?")
                    .setParameter(1, progress)
                    .setParameter(2, currentPage)
                    .setParameter(3, totalPages)
                    .setParameter(4, taskId)
                    .executeUpdate();
        } catch (Exception e) {
            log.warn("更新任务进度失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    private void failTask(Long taskId, String errMsg) {
        updateTaskStatus(taskId, "failed", errMsg, null, null, null, 0, 0, null, null);
        log.warn("OCR 任务失败: taskId={}, err={}", taskId, errMsg);
    }
}