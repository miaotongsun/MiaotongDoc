package com.miaotong.doc.repository;

import com.miaotong.doc.entity.ContractApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContractApprovalRepository extends JpaRepository<ContractApproval, Long> {

    List<ContractApproval> findByContractIdOrderByCreatedAtDesc(Long contractId);

    List<ContractApproval> findBySigningTaskId(Long signingTaskId);
}
