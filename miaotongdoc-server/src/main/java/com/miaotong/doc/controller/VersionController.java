package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentVersion;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.ShareService;
import com.miaotong.doc.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;
    private final DocumentService documentService;
    private final ShareService shareService;

    private static final Map<String, Integer> PERM_LEVEL = Map.of(
            "view", 1, "comment", 2, "edit", 3, "admin", 4
    );

    private void requirePermission(Long docId, Long userId, String role, String minLevel) {
        if ("admin".equals(role)) return;
        String perm = shareService.getUserPermission(docId, userId);
        if (perm == null) throw new BusinessException("无权访问此文档");
        int userLevel = PERM_LEVEL.getOrDefault(perm, 0);
        int required = PERM_LEVEL.getOrDefault(minLevel, 0);
        if (userLevel < required) throw new BusinessException("权限不足");
    }

    @GetMapping("/{docId}")
    public ResponseEntity<List<DocumentVersion>> getVersionHistory(
            @PathVariable Long docId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(docId, userId, role, "view");
        return ResponseEntity.ok(versionService.getVersionHistory(docId));
    }

    @GetMapping("/{docId}/{versionNumber}")
    public ResponseEntity<DocumentVersion> getVersion(
            @PathVariable Long docId,
            @PathVariable Integer versionNumber,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(docId, userId, role, "view");
        return ResponseEntity.ok(versionService.getVersion(docId, versionNumber));
    }

    @GetMapping("/{docId}/{versionNumber}/download")
    public ResponseEntity<byte[]> downloadVersion(
            @PathVariable Long docId,
            @PathVariable Integer versionNumber,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(docId, userId, role, "view");
        Document doc = documentService.getDocument(docId);
        DocumentVersion version = versionService.getVersion(docId, versionNumber);
        byte[] content = documentService.getFileContentForVersion(docId, versionNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(doc.getTitle() + "_v" + versionNumber + "." + doc.getFileType())
                .build());

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @GetMapping("/{docId}/{versionNumber}/preview")
    public ResponseEntity<byte[]> previewVersion(
            @PathVariable Long docId,
            @PathVariable Integer versionNumber,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(docId, userId, role, "view");
        Document doc = documentService.getDocument(docId);
        byte[] content = documentService.getFileContentForVersion(docId, versionNumber);

        MediaType mediaType = switch (doc.getFileType()) {
            case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pptx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.builder("inline")
                .filename(doc.getTitle() + "_v" + versionNumber + "." + doc.getFileType())
                .build());

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @PostMapping("/{docId}/{versionNumber}/restore")
    public ResponseEntity<Map<String, String>> restoreVersion(
            @PathVariable Long docId,
            @PathVariable Integer versionNumber,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        // 恢复版本需要 admin 权限（文档管理员）
        requirePermission(docId, userId, role, "admin");
        documentService.restoreVersion(docId, versionNumber, userId);
        return ResponseEntity.ok(Map.of("message", "版本恢复成功"));
    }
}
