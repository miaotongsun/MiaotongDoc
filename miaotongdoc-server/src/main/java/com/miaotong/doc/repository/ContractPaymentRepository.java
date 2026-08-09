package com.miaotong.doc.repository;

import com.miaotong.doc.entity.ContractPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContractPaymentRepository extends JpaRepository<ContractPayment, Long> {

    List<ContractPayment> findByContractIdOrderBySequenceAsc(Long contractId);

    /**
     * 查询待提醒的付款计划(dueDate <= 截止日 且 status=pending 且 reminderSent=false)
     */
    @Query("SELECT p FROM ContractPayment p " +
            "WHERE p.status = 'pending' AND p.reminderSent = false AND p.dueDate <= :cutoffDate")
    List<ContractPayment> findPendingPaymentsToRemind(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * 标记已发送提醒
     */
    @Modifying
    @Query("UPDATE ContractPayment p SET p.reminderSent = true, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    int markReminderSent(@Param("id") Long id);
}