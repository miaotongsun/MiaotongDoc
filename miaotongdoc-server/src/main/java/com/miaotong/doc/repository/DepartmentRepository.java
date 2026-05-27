package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    List<Department> findByParentId(Long parentId);

    List<Department> findByIsActiveTrue();

    @Query("SELECT d FROM Department d WHERE d.path LIKE CONCAT(:pathPrefix, '%')")
    List<Department> findAllByPathPrefix(@Param("pathPrefix") String pathPrefix);

    @Query("SELECT d FROM Department d WHERE d.level = :level AND d.isActive = true ORDER BY d.sortOrder")
    List<Department> findByLevel(@Param("level") Short level);
}
