package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "mt_contract_approval_node")
public class ContractApprovalNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(nullable = false, length = 20)
    private String status = "waiting"; // waiting/pending/approved/rejected

    private String remark;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
