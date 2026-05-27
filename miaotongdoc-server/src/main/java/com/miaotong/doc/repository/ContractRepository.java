package com.miaotong.doc.repository;

import com.miaotong.doc.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    Page<Contract> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId, Pageable pageable);

    Page<Contract> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Contract> findByDepartmentIdOrderByCreatedAtDesc(Long departmentId, Pageable pageable);

    Page<Contract> findByContractTypeOrderByCreatedAtDesc(String contractType, Pageable pageable);

    Page<Contract> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.status = :status AND c.departmentId = :deptId ORDER BY c.createdAt DESC")
    Page<Contract> findByStatusAndDepartmentId(@Param("status") String status, @Param("deptId") Long deptId, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.contractNo LIKE %:kw% OR c.partyA LIKE %:kw% OR c.partyB LIKE %:kw% ORDER BY c.createdAt DESC")
    Page<Contract> searchByKeyword(@Param("kw") String keyword, Pageable pageable);

    long countByStatus(String status);

    List<Contract> findByStatusAndExpiryDateBefore(String status, LocalDate date);

    List<Contract> findByStatusAndExpiryDateBetweenAndReminderSentFalse(String status, LocalDate start, LocalDate end);
}
