package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.docKey = :docKey")
    Optional<Document> findByDocKeyForUpdate(@Param("docKey") String docKey);

    Optional<Document> findByDocKey(String docKey);

    Page<Document> findByIsDeletedFalse(Pageable pageable);

    Page<Document> findByOwnerUserIdAndIsDeletedFalse(Long ownerUserId, Pageable pageable);

    Page<Document> findByDocTypeAndIsDeletedFalse(String docType, Pageable pageable);

    Page<Document> findByIsStarredTrueAndIsDeletedFalse(Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.departmentId IN :deptIds ORDER BY d.updatedAt DESC")
    Page<Document> findByDepartmentIdsAndIsDeletedFalse(@Param("deptIds") List<Long> deptIds, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.title LIKE %:keyword% OR d.docKey LIKE %:keyword%) ORDER BY d.updatedAt DESC")
    Page<Document> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId) ORDER BY d.updatedAt DESC")
    Page<Document> findSharedWithUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.isStarred = true AND d.ownerUserId = :userId ORDER BY d.updatedAt DESC")
    Page<Document> findStarredByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.ownerUserId = :userId OR d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId)) ORDER BY d.updatedAt DESC")
    Page<Document> findAccessibleByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.ownerUserId = :userId OR d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId)) AND d.docType = :docType ORDER BY d.updatedAt DESC")
    Page<Document> findAccessibleByUserAndDocType(@Param("userId") Long userId, @Param("docType") String docType, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.ownerUserId = :userId OR d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId)) AND (d.title LIKE %:keyword% OR d.docKey LIKE %:keyword%) ORDER BY d.updatedAt DESC")
    Page<Document> searchAccessibleByUser(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.ownerUserId = :userId OR d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId)) AND d.departmentId IN (SELECT dep.id FROM Department dep WHERE dep.path LIKE CONCAT((SELECT dep2.path FROM Department dep2 WHERE dep2.id = :deptId), '%')) ORDER BY d.updatedAt DESC")
    Page<Document> findAccessibleByUserAndDepartmentTree(@Param("userId") Long userId, @Param("deptId") Long deptId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.departmentId IN (SELECT dep.id FROM Department dep WHERE dep.path LIKE CONCAT((SELECT dep2.path FROM Department dep2 WHERE dep2.id = :deptId), '%')) ORDER BY d.updatedAt DESC")
    Page<Document> findAllByDepartmentTree(@Param("deptId") Long deptId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.docType = :docType AND d.departmentId IN (SELECT dep.id FROM Department dep WHERE dep.path LIKE CONCAT((SELECT dep2.path FROM Department dep2 WHERE dep2.id = :deptId), '%')) ORDER BY d.updatedAt DESC")
    Page<Document> findByDocTypeAndDepartmentTreeAndIsDeletedFalse(@Param("docType") String docType, @Param("deptId") Long deptId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND (d.ownerUserId = :userId OR d.id IN (SELECT ds.documentId FROM DocumentShare ds WHERE ds.userId = :userId)) AND d.docType = :docType AND d.departmentId IN (SELECT dep.id FROM Department dep WHERE dep.path LIKE CONCAT((SELECT dep2.path FROM Department dep2 WHERE dep2.id = :deptId), '%')) ORDER BY d.updatedAt DESC")
    Page<Document> findAccessibleByUserAndDocTypeAndDepartmentTree(@Param("userId") Long userId, @Param("docType") String docType, @Param("deptId") Long deptId, Pageable pageable);
}
