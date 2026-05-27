package com.miaotong.doc.controller;

import com.miaotong.doc.dto.ShareRequest;
import com.miaotong.doc.entity.DocumentShare;
import com.miaotong.doc.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public ResponseEntity<DocumentShare> shareDocument(
            @Valid @RequestBody ShareRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        DocumentShare share = shareService.shareDocument(
                request.getDocumentId(), request.getUserId(), userId, request.getPermission(), role);
        return ResponseEntity.ok(share);
    }

    @GetMapping("/document/{docId}")
    public ResponseEntity<List<DocumentShare>> getDocumentShares(@PathVariable Long docId) {
        return ResponseEntity.ok(shareService.getDocumentShares(docId));
    }

    @PostMapping("/department")
    public ResponseEntity<Map<String, Object>> shareToDepartment(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Long docId = Long.valueOf(request.get("documentId").toString());
        Long deptId = Long.valueOf(request.get("departmentId").toString());
        String permission = (String) request.getOrDefault("permission", "view");
        int count = shareService.shareToDepartment(docId, deptId, permission, userId, role);
        return ResponseEntity.ok(Map.of("message", "已共享给部门", "count", count));
    }

    @PutMapping("/{id}/permission")
    public ResponseEntity<Map<String, String>> updatePermission(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        shareService.updatePermission(id, request.get("permission"), userId, role);
        return ResponseEntity.ok(Map.of("message", "权限更新成功"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> removeShare(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        shareService.removeShare(id, userId, role);
        return ResponseEntity.ok(Map.of("message", "取消共享成功"));
    }
}
