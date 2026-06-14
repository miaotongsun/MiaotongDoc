package com.miaotong.doc.repository;

import com.miaotong.doc.entity.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {
    List<TemplateCategory> findByIsActiveTrueOrderBySortOrderAsc();
    boolean existsByName(String name);
}
