package com.miaotong.doc.repository;

import com.miaotong.doc.entity.SigningTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SigningTaskRepository extends JpaRepository<SigningTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SigningTask t WHERE t.id = :id")
    Optional<SigningTask> findByIdForUpdate(@Param("id") Long id);

    Page<SigningTask> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);

    @Query("SELECT t FROM SigningTask t WHERE t.status = 'in_progress' AND t.id IN (SELECT r.taskId FROM SigningRecord r WHERE r.signerUserId = :userId AND r.status = 'pending') ORDER BY t.createdAt DESC")
    Page<SigningTask> findTodoTasks(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM SigningTask t WHERE t.status = 'in_progress' AND t.deadline < :now")
    List<SigningTask> findExpiredTasks(@Param("now") LocalDateTime now);

    @Query("SELECT t FROM SigningTask t WHERE t.documentId = :docId AND t.status IN ('pending', 'in_progress')")
    List<SigningTask> findActiveByDocumentId(@Param("docId") Long docId);
}
