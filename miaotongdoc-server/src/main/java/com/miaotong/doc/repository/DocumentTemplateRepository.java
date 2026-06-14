package com.miaotong.doc.repository;

import com.miaotong.doc.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    List<DocumentTemplate> findByIsActiveTrueOrderBySortOrderAsc();
    List<DocumentTemplate> findByDocTypeAndIsActiveTrueOrderBySortOrderAsc(String docType);
    List<DocumentTemplate> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(String category);
    List<DocumentTemplate> findByIsSystemTrue();

    @Query("SELECT DISTINCT t.category FROM DocumentTemplate t WHERE t.category IS NOT NULL AND t.isActive = true ORDER BY t.category")
    List<String> findDistinctCategories();
}
