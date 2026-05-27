package com.miaotong.doc.repository;

import com.miaotong.doc.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByDocumentIdOrderByCreatedAtDesc(Long documentId, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end, Pageable pageable);

    Page<AuditLog> findByEmployeeIdOrderByCreatedAtDesc(String employeeId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :threshold ORDER BY a.createdAt")
    List<AuditLog> findOlderThan(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query(value = "INSERT INTO mt_audit_log_archive (user_id, employee_id, user_name, action, resource_type, resource_id, detail, ip_address, user_agent, created_at) SELECT user_id, employee_id, user_name, action, resource_type, resource_id, detail, ip_address, user_agent, created_at FROM mt_audit_log WHERE created_at < :threshold", nativeQuery = true)
    int archiveOlderThan(@Param("threshold") LocalDateTime threshold);
}
