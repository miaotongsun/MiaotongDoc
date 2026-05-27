package com.miaotong.doc.service;

import com.miaotong.doc.entity.AuditLog;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.AuditLogRepository;
import com.miaotong.doc.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(Long userId, String action, String resourceType, Long resourceId, Object detail) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        HttpServletRequest request = getCurrentRequest();

        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setEmployeeId(user.getEmployeeId());
        auditLog.setUserName(user.getRealName());
        auditLog.setAction(action);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setDetail(detail != null ? detail.toString() : null);
        auditLog.setIpAddress(request != null ? request.getRemoteAddr() : null);
        auditLog.setUserAgent(request != null ? request.getHeader("User-Agent") : null);

        auditLogRepository.save(auditLog);
    }

    public Page<AuditLog> getDocumentAuditLogs(Long documentId, Pageable pageable) {
        return auditLogRepository.findByDocumentIdOrderByCreatedAtDesc(documentId, pageable);
    }

    public Page<AuditLog> getUserAuditLogs(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<AuditLog> getUserAuditLogs(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        return auditLogRepository.findByUserIdAndDateRange(userId, start, end, pageable);
    }

    public Page<AuditLog> getEmployeeAuditLogs(String employeeId, Pageable pageable) {
        return auditLogRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void archiveOldAuditLogs() {
        LocalDateTime hotThreshold = LocalDateTime.now().minusDays(90);
        List<AuditLog> oldLogs = auditLogRepository.findOlderThan(hotThreshold);

        if (!oldLogs.isEmpty()) {
            auditLogRepository.archiveOlderThan(hotThreshold);
            auditLogRepository.deleteOlderThan(hotThreshold);
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
