package com.miaotong.doc.repository;

import com.miaotong.doc.entity.ContractApprovalNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractApprovalNodeRepository extends JpaRepository<ContractApprovalNode, Long> {

    List<ContractApprovalNode> findByContractIdOrderByStepOrderAsc(Long contractId);

    @Query("SELECT n FROM ContractApprovalNode n WHERE n.contractId = :contractId AND n.status = 'pending' ORDER BY n.stepOrder ASC LIMIT 1")
    Optional<ContractApprovalNode> findCurrentPendingNode(@Param("contractId") Long contractId);

    @Query("SELECT n FROM ContractApprovalNode n WHERE n.approverId = :approverId AND n.status = 'pending' ORDER BY n.createdAt DESC")
    List<ContractApprovalNode> findByApproverIdAndPending(@Param("approverId") Long approverId);

    void deleteByContractId(Long contractId);
}
