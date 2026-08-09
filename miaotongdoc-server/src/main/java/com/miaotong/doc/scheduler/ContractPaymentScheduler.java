package com.miaotong.doc.scheduler;

import com.miaotong.doc.constants.NotificationType;
import com.miaotong.doc.entity.Contract;
import com.miaotong.doc.entity.ContractPayment;
import com.miaotong.doc.repository.ContractPaymentRepository;
import com.miaotong.doc.repository.ContractRepository;
import com.miaotong.doc.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 合同付款计划到期提醒
 * 2026-08-09 新增 - 每天 09:05 扫描
 *
 * 规则:
 *   - 距到期日 ≤ 7 天 且 > 0 天 → PAYMENT_REMIND(7天前提醒)
 *   - 到期日 == 今天 → PAYMENT_DUE(当日提醒)
 *   - 到期日 < 今天 → 状态自动变 overdue,不发提醒(避免骚扰)
 *   - 每个付款计划只提醒一次(reminder_sent 防重复)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractPaymentScheduler {

    private final ContractPaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 5 9 * * ?")
    @Transactional
    public void checkPaymentDue() {
        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(7);

        // 查询所有 pending 且 dueDate <= warningDate 且 reminderSent=false
        List<ContractPayment> payments = paymentRepository.findPendingPaymentsToRemind(warningDate);
        if (payments.isEmpty()) {
            log.debug("无付款计划到期提醒");
            return;
        }
        log.info("付款计划到期扫描: 找到 {} 条候选", payments.size());

        for (ContractPayment payment : payments) {
            Contract contract = contractRepository.findById(payment.getContractId()).orElse(null);
            if (contract == null) continue;

            String content;
            String type;
            if (payment.getDueDate().isBefore(today)) {
                // 已过期,自动更新状态为 overdue,不发提醒
                payment.setStatus("overdue");
                paymentRepository.save(payment);
                continue;
            } else if (payment.getDueDate().equals(today)) {
                type = NotificationType.PAYMENT_DUE;
                content = String.format("您的合同 %s 付款计划【%s】今天到期(¥%s),请及时处理",
                        contract.getContractNo() != null ? contract.getContractNo() : "#" + contract.getId(),
                        payment.getTitle() != null ? payment.getTitle() : "第" + payment.getSequence() + "期",
                        payment.getAmount() != null ? payment.getAmount().toPlainString() : "0");
            } else {
                type = NotificationType.PAYMENT_REMIND;
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, payment.getDueDate());
                content = String.format("您的合同 %s 付款计划【%s】将于 %d 天后到期(¥%s)",
                        contract.getContractNo() != null ? contract.getContractNo() : "#" + contract.getId(),
                        payment.getTitle() != null ? payment.getTitle() : "第" + payment.getSequence() + "期",
                        daysLeft,
                        payment.getAmount() != null ? payment.getAmount().toPlainString() : "0");
            }

            // 通知合同的 owner(创建人)
            Long toUserId = contract.getOwnerUserId();
            if (toUserId != null) {
                notificationService.notify(
                        null,             // 系统通知
                        toUserId,         // 接收人 = 合同 owner
                        contract.getDocumentId(), // 关联文档
                        contract.getId(), // 关联合同
                        type, content
                );
            }
            // 标记已发提醒
            paymentRepository.markReminderSent(payment.getId());
            log.info("发送付款提醒: contractId={}, paymentId={}, type={}", contract.getId(), payment.getId(), type);
        }
    }
}