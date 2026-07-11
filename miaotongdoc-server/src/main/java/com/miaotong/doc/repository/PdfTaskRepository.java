package com.miaotong.doc.repository;

import com.miaotong.doc.entity.PdfTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PdfTaskRepository extends JpaRepository<PdfTask, Long> {

    List<PdfTask> findByDocumentIdAndStatus(Long documentId, String status);

    List<PdfTask> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    List<PdfTask> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
