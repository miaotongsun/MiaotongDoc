package com.miaotong.doc.repository;

import com.miaotong.doc.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    List<DocumentVersion> findByDocumentId(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    @Query("SELECT MAX(v.versionNumber) FROM DocumentVersion v WHERE v.documentId = :documentId")
    Optional<Integer> findMaxVersionNumber(@Param("documentId") Long documentId);

    void deleteByDocumentId(Long documentId);
}
