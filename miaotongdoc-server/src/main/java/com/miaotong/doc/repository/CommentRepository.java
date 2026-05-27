package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByDocumentIdAndIsDeletedFalseOrderByCreatedAtDesc(Long documentId);

    List<Comment> findByDocumentIdAndParentIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(Long documentId);

    List<Comment> findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(Long parentId);

    @Query("SELECT c FROM Comment c WHERE c.documentId = :docId AND c.isResolved = false AND c.isDeleted = false ORDER BY c.createdAt DESC")
    List<Comment> findUnresolvedByDocumentId(@Param("docId") Long docId);
}
