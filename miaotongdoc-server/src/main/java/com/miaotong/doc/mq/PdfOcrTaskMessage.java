package com.miaotong.doc.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * OCR 任务消息(OCR AI 改造 v3)
 *
 * 字段说明:
 *   - taskId: mt_pdf_task.id,用于前端 SSE 订阅
 *   - documentId: 文档 ID
 *   - userId: 创建任务的用户 ID
 *   - model: mobile / server
 *   - language: ch / en / japan / korean
 *   - submittedAt: 提交时间戳
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfOcrTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long documentId;
    private Long userId;
    private String model;
    private String language;
    private Long submittedAt;
    /** 2026-08-02 PR3: 单页识别(null = 全文,否则只识别该页) */
    private Integer pageNum;
}