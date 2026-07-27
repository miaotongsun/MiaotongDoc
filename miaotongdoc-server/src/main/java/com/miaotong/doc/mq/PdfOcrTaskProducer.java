package com.miaotong.doc.mq;

import com.miaotong.doc.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * OCR 任务消息生产者(OCR AI 改造 v3)
 *
 * 负责将 OCR 任务消息发送到 pdf.ocr.task 队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfOcrTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送 OCR 任务
     * @param message 任务消息(包含 taskId/documentId/model 等)
     */
    public void send(PdfOcrTaskMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.OCR_EXCHANGE,
                    RabbitMqConfig.OCR_ROUTING_KEY,
                    message
            );
            log.info("OCR 任务已入队: taskId={}, docId={}, model={}",
                    message.getTaskId(), message.getDocumentId(), message.getModel());
        } catch (Exception e) {
            log.error("OCR 任务入队失败: taskId={}, docId={}, error={}",
                    message.getTaskId(), message.getDocumentId(), e.getMessage());
            throw e;
        }
    }
}