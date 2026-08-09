package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同付款计划
 * 2026-08-09 新增 - 合同模块内容识别重塑 Phase 3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mt_contract_payment")
public class ContractPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 期次(1,2,3...) */
    @Column(nullable = false)
    private Integer sequence = 1;

    /** 付款计划标题(首付款 / 尾款 / 月供...) */
    @Column(length = 100)
    private String title;

    /** 本期金额(单位) */
    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency = "CNY";

    /** 应付款日 */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** 实际付款日(null=未付) */
    @Column(name = "paid_date")
    private LocalDate paidDate;

    /** pending / paid / overdue */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    /** 防重复提醒标志 */
    @Column(name = "reminder_sent", nullable = false)
    private Boolean reminderSent = false;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}