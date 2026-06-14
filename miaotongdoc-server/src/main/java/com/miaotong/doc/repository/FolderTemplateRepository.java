package com.miaotong.doc.repository;

import com.miaotong.doc.entity.FolderTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FolderTemplateRepository extends JpaRepository<FolderTemplate, Long> {
    List<FolderTemplate> findByIsActiveTrue();
    List<FolderTemplate> findAllByOrderByIdDesc();
}
