package com.miaotong.doc.repository;

import com.miaotong.doc.entity.PdfTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PdfTaskRepository extends JpaRepository<PdfTask, Long> {

    List<PdfTask> findByDocumentIdAndStatus(Long documentId, String status);

    List<PdfTask> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    List<PdfTask> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    /** OCR 改造 v3:取文档最新未完成任务(用于 SSE 初始化查询当前状态) */
    Optional<PdfTask> findFirstByDocumentIdAndTaskTypeAndStatusInOrderByIdDesc(
            Long documentId, String taskType, List<String> statuses);

    /** Phase 26:取文档最新任意 task(智能目录旁路数据用) */
    Optional<PdfTask> findFirstByDocumentIdOrderByIdDesc(Long documentId);
}
