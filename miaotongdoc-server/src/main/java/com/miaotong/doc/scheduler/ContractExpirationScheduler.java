package com.miaotong.doc.scheduler;

import com.miaotong.doc.entity.Contract;
import com.miaotong.doc.entity.ContractApproval;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.repository.ContractRepository;
import com.miaotong.doc.repository.ContractApprovalRepository;
import com.miaotong.doc.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpirationScheduler {

    private final ContractRepository contractRepository;
    private final ContractApprovalRepository approvalRepository;
    private final DocumentRepository documentRepository;

    /**
     * 每天早上9点检查合同到期情况
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void checkContractExpiration() {
        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(7);

        // 1. 自动过期：expiry_date < today 且 status=approved
        List<Contract> expiredContracts = contractRepository.findByStatusAndExpiryDateBefore("approved", today);
        for (Contract contract : expiredContracts) {
            contract.setStatus("expired");
            // 解锁文档
            Document doc = documentRepository.findById(contract.getDocumentId()).orElse(null);
            if (doc != null) {
                doc.setSigningLocked(false);
                documentRepository.save(doc);
            }
            contractRepository.save(contract);

            ContractApproval approval = new ContractApproval();
            approval.setContractId(contract.getId());
            approval.setAction("expire");
            approval.setRemark("合同到期自动过期");
            approvalRepository.save(approval);

            log.info("合同自动过期: id={}, contractNo={}", contract.getId(), contract.getContractNo());
        }

        // 2. 到期提醒：expiry_date 在 [today, warningDate] 且 status=approved 且 reminderSent=false
        List<Contract> nearExpiry = contractRepository.findByStatusAndExpiryDateBetweenAndReminderSentFalse(
                "approved", today, warningDate);
        for (Contract contract : nearExpiry) {
            contract.setReminderSent(true);
            contractRepository.save(contract);

            ContractApproval approval = new ContractApproval();
            approval.setContractId(contract.getId());
            approval.setAction("reminder");
            approval.setRemark("合同即将在 " + contract.getExpiryDate() + " 到期");
            approvalRepository.save(approval);

            log.info("合同到期提醒: id={}, contractNo={}, expiryDate={}",
                    contract.getId(), contract.getContractNo(), contract.getExpiryDate());
        }

        if (!expiredContracts.isEmpty() || !nearExpiry.isEmpty()) {
            log.info("合同到期检查完成: 过期={}, 提醒={}", expiredContracts.size(), nearExpiry.size());
        }
    }
}
