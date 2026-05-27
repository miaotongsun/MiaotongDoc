package com.miaotong.doc.repository;

import com.miaotong.doc.entity.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentShareRepository extends JpaRepository<DocumentShare, Long> {

    List<DocumentShare> findByDocumentId(Long documentId);

    Optional<DocumentShare> findByDocumentIdAndUserId(Long documentId, Long userId);

    @Query("SELECT ds FROM DocumentShare ds WHERE ds.documentId = :docId AND ds.permission = :permission")
    List<DocumentShare> findByDocumentIdAndPermission(@Param("docId") Long docId, @Param("permission") String permission);

    void deleteByDocumentId(Long documentId);

    boolean existsByDocumentIdAndUserId(Long documentId, Long userId);

    List<DocumentShare> findByDocumentIdAndUserIdIn(Long documentId, List<Long> userIds);
}
