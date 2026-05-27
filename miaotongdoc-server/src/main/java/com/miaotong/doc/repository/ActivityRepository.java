package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Page<Activity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Activity> findByDocumentIdOrderByCreatedAtDesc(Long documentId, Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.userId IN :userIds ORDER BY a.createdAt DESC")
    Page<Activity> findByUserIdsOrderByCreatedAtDesc(@Param("userIds") List<Long> userIds, Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.documentId IN :docIds ORDER BY a.createdAt DESC")
    Page<Activity> findByDocumentIdsOrderByCreatedAtDesc(@Param("docIds") List<Long> docIds, Pageable pageable);
}
