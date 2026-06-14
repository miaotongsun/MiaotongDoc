package com.miaotong.doc.controller;

import com.miaotong.doc.entity.AuditLog;
import com.miaotong.doc.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/document/{docId}")
    public ResponseEntity<Page<AuditLog>> getDocumentAuditLogs(
            @PathVariable Long docId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getDocumentAuditLogs(docId, PageRequest.of(page, size)));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<AuditLog>> getMyAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            return ResponseEntity.ok(auditService.getUserAuditLogs(userId, start, end, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(auditService.getUserAuditLogs(userId, PageRequest.of(page, size)));
    }

    // 管理员查看所有操作日志
    @GetMapping("/all")
    public ResponseEntity<Page<AuditLog>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            return ResponseEntity.ok(auditService.getAllAuditLogs(start, end, userId, action, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(auditService.getAllAuditLogs(userId, action, PageRequest.of(page, size)));
    }
}
