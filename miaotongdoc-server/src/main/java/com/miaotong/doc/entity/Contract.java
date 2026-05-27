package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mt_contract")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "contract_type", length = 50)
    private String contractType;

    @Column(name = "party_a", length = 200)
    private String partyA;

    @Column(name = "party_b", length = 200)
    private String partyB;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency = "CNY";

    @Column(name = "signing_date")
    private LocalDate signingDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "department_id")
    private Long departmentId;

    private String remarks;

    @Column(name = "current_step")
    private Integer currentStep = 0;

    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;

    @Column(name = "approved_hash", length = 64)
    private String approvedHash;

    @Column(name = "approved_version")
    private Integer approvedVersion;

    @OneToMany(mappedBy = "contractId", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.OrderBy(clause = "step_order ASC")
    private List<ContractApprovalNode> approvalNodes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
