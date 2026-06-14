package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    List<Folder> findByParentIdOrderByCreatedAtDesc(Long parentId);
    List<Folder> findByDepartmentIdOrderByCreatedAtDesc(Long departmentId);
    boolean existsByParentId(Long parentId);
}
