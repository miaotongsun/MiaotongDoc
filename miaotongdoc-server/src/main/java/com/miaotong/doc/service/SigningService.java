package com.miaotong.doc.service;

import com.miaotong.doc.dto.CreateSigningTaskRequest;
import com.miaotong.doc.dto.SigningTaskDTO;
import com.miaotong.doc.dto.SigningRecordDTO;
import com.miaotong.doc.entity.*;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SigningService {

    private final SigningTaskRepository signingTaskRepository;
    private final SigningRecordRepository signingRecordRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public SigningTask createTask(CreateSigningTaskRequest request, Long creatorId) {
        Document doc = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new NotFoundException("文档不存在"));

        if ("signing".equals(doc.getStatus())) {
            throw new BusinessException("文档已在签署中");
        }

        if (request.getSignerUserIds() == null || request.getSignerUserIds().isEmpty()) {
            throw new BusinessException("签署人列表不能为空");
        }

        SigningTask task = new SigningTask();
        task.setDocumentId(request.getDocumentId());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCreatedBy(creatorId);
        task.setRequiredCount(request.getSignerUserIds().size());
        task.setStatus("in_progress");
        task.setDeadline(request.getDeadline());
        task = signingTaskRepository.save(task);

        for (int i = 0; i < request.getSignerUserIds().size(); i++) {
            SigningRecord record = new SigningRecord();
            record.setTaskId(task.getId());
            record.setSignerUserId(request.getSignerUserIds().get(i));
            record.setSignOrder(i + 1);
            record.setStatus("pending");
            signingRecordRepository.save(record);

            notificationService.notify(creatorId, request.getSignerUserIds().get(i),
                    request.getDocumentId(), "SIGN_REQUEST",
                    "请求你签署文档: " + doc.getTitle());
        }

        doc.setStatus("signing");
        doc.setSigningLocked(true);
        documentRepository.save(doc);

        auditService.log(creatorId, "SIGN_INIT", "SIGNING", task.getId(), null);

        return task;
    }

    @Transactional
    public void confirmSign(Long taskId, Long userId, HttpServletRequest request) {
        SigningRecord record = signingRecordRepository
                .findByTaskIdAndSignerUserIdForUpdate(taskId, userId)
                .orElseThrow(() -> new BusinessException("你不是该任务的签署人"));

        if (!"pending".equals(record.getStatus())) {
            throw new BusinessException("该签署已完成或已拒绝");
        }

        SigningTask task = signingTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("签署任务不存在"));

        Document doc = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new NotFoundException("文档不存在"));

        record.setStatus("confirmed");
        record.setConfirmedAt(LocalDateTime.now());
        record.setIpAddress(request.getRemoteAddr());
        record.setUserAgent(request.getHeader("User-Agent"));
        record.setDocumentHash(doc.getFileHash());
        signingRecordRepository.save(record);

        task.setCompletedCount(task.getCompletedCount() + 1);

        if (task.getCompletedCount().equals(task.getRequiredCount())) {
            task.setStatus("completed");
            task.setCompletedAt(LocalDateTime.now());
            doc.setStatus("signed");
            doc.setSigningLocked(false);
            documentRepository.save(doc);
        }

        signingTaskRepository.save(task);

        notificationService.notify(userId, task.getCreatedBy(), task.getDocumentId(),
                "SIGN_CONFIRM", "确认签署了文档");
    }

    @Transactional
    public void rejectSign(Long taskId, Long userId, String remark) {
        SigningRecord record = signingRecordRepository
                .findByTaskIdAndSignerUserIdForUpdate(taskId, userId)
                .orElseThrow(() -> new BusinessException("你不是该任务的签署人"));

        if (!"pending".equals(record.getStatus())) {
            throw new BusinessException("该签署已完成或已拒绝");
        }

        record.setStatus("rejected");
        record.setRemark(remark);
        signingRecordRepository.save(record);

        SigningTask task = signingTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("签署任务不存在"));

        task.setStatus("cancelled");
        task.setCancelReason("签署被拒绝");
        task.setCancelledBy(userId);
        signingTaskRepository.save(task);

        Document doc = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        doc.setStatus("draft");
        doc.setSigningLocked(false);
        documentRepository.save(doc);

        notificationService.notify(userId, task.getCreatedBy(), task.getDocumentId(),
                "SIGN_REJECT", "拒绝签署文档");
    }

    @Transactional
    public void cancelTask(Long taskId, Long userId) {
        SigningTask task = signingTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("签署任务不存在"));

        if (!task.getCreatedBy().equals(userId)) {
            throw new BusinessException("只有发起人可以取消签署");
        }

        if (!"in_progress".equals(task.getStatus())) {
            throw new BusinessException("当前状态不可取消");
        }

        task.setStatus("cancelled");
        task.setCancelledBy(userId);
        task.setCancelReason("发起人取消");
        signingTaskRepository.save(task);

        Document doc = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        doc.setStatus("draft");
        doc.setSigningLocked(false);
        documentRepository.save(doc);

        auditService.log(userId, "SIGN_CANCEL", "SIGNING", taskId, null);

        List<SigningRecord> records = signingRecordRepository.findByTaskIdOrderBySignOrderAsc(taskId);
        for (SigningRecord record : records) {
            if ("pending".equals(record.getStatus())) {
                record.setStatus("cancelled");
                signingRecordRepository.save(record);
                notificationService.notify(userId, record.getSignerUserId(), task.getDocumentId(),
                        "SIGN_CANCEL", "签署任务已取消: " + task.getTitle());
            }
        }
    }

    public Page<SigningTask> getInitiatedTasks(Long userId, Pageable pageable) {
        return signingTaskRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<SigningTask> getTodoTasks(Long userId, Pageable pageable) {
        return signingTaskRepository.findTodoTasks(userId, pageable);
    }

    public SigningTask getTask(Long taskId) {
        return signingTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("签署任务不存在"));
    }

    public List<SigningRecord> getTaskRecords(Long taskId) {
        return signingRecordRepository.findByTaskIdOrderBySignOrderAsc(taskId);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkExpiredSigningTasks() {
        List<SigningTask> expiredTasks = signingTaskRepository.findExpiredTasks(LocalDateTime.now());

        for (SigningTask task : expiredTasks) {
            task.setStatus("expired");
            task.setCancelReason("签署超期");
            signingTaskRepository.save(task);

            Document doc = documentRepository.findById(task.getDocumentId()).orElse(null);
            if (doc != null) {
                doc.setStatus("draft");
                doc.setSigningLocked(false);
                documentRepository.save(doc);
            }

            List<SigningRecord> records = signingRecordRepository.findByTaskIdOrderBySignOrderAsc(task.getId());
            for (SigningRecord record : records) {
                if ("pending".equals(record.getStatus())) {
                    record.setStatus("expired");
                    signingRecordRepository.save(record);
                }
            }
        }
    }
}
