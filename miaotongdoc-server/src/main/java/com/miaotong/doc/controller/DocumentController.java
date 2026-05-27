package com.miaotong.doc.controller;

import com.miaotong.doc.dto.*;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.ShareService;
import com.miaotong.doc.service.PdfExportService;
import com.miaotong.doc.util.JwtUtil;
import com.miaotong.doc.util.EditorJwtUtil;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;
    private final PdfExportService pdfExportService;
    private final EditorJwtUtil editorJwtUtil;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${editor.server-url}")
    private String editorServerUrl;

    @Value("${editor.callback-url}")
    private String callbackUrl;

    @Value("${editor.download-url}")
    private String downloadUrl;

    private static final java.util.Map<String, Integer> PERM_LEVEL = java.util.Map.of(
            "view", 1, "comment", 2, "edit", 3, "admin", 4
    );

    private void requirePermission(Long documentId, Long userId, String minLevel, String systemRole) {
        // 系统管理员绕过所有权限检查
        if ("admin".equals(systemRole)) return;

        String perm = shareService.getUserPermission(documentId, userId);
        if (perm == null) {
            throw new BusinessException("无权访问此文档");
        }
        int userLevel = PERM_LEVEL.getOrDefault(perm, 0);
        int required = PERM_LEVEL.getOrDefault(minLevel, 0);
        if (userLevel < required) {
            throw new BusinessException("权限不足，需要" + minLevel + "权限");
        }
    }

    @PostMapping("/create")
    public ResponseEntity<DocumentDTO> createDocument(
            @Valid @RequestBody CreateDocumentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.createDocument(request, userId);
        return ResponseEntity.ok(toDTO(doc));
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.uploadDocument(file, userId);
        return ResponseEntity.ok(toDTO(doc));
    }

    @GetMapping("/list")
    public ResponseEntity<Page<DocumentDTO>> listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long owner,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Sort pageSort = parseSort(sort);
        PageRequest pageRequest = PageRequest.of(page, size, pageSort);
        Page<Document> documents = documentService.listDocuments(type, keyword, owner, userId, departmentId, role, pageRequest);
        return ResponseEntity.ok(documents.map(doc -> toDTO(doc, userId)));
    }

    private Sort parseSort(String sort) {
        return switch (sort) {
            case "createdAt" -> Sort.by("createdAt").ascending();
            case "createdAtDesc" -> Sort.by("createdAt").descending();
            case "updatedAt" -> Sort.by("updatedAt").descending();
            case "updatedAtAsc" -> Sort.by("updatedAt").ascending();
            case "title" -> Sort.by("title").ascending();
            case "titleDesc" -> Sort.by("title").descending();
            case "fileSize" -> Sort.by("fileSize").descending();
            default -> Sort.by("updatedAt").descending();
        };
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocument(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "view", role);
        Document doc = documentService.getDocument(id);
        return ResponseEntity.ok(toDTO(doc, userId));
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<DocumentDTO> renameDocument(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "edit", role);
        Document doc = documentService.renameDocument(id, request.get("title"), userId);
        return ResponseEntity.ok(toDTO(doc, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "admin", role);
        documentService.softDelete(id, userId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDelete(
            @RequestBody java.util.List<Long> ids,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        int deleted = 0;
        for (Long id : ids) {
            try {
                requirePermission(id, userId, "admin", role);
                documentService.softDelete(id, userId);
                deleted++;
            } catch (Exception ignored) {}
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("message", "批量删除成功");
        result.put("deleted", deleted);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Map<String, String>> restoreDocument(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "admin", role);
        documentService.restore(id, userId);
        return ResponseEntity.ok(Map.of("message", "恢复成功"));
    }

    @PutMapping("/{id}/star")
    public ResponseEntity<DocumentDTO> toggleStar(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "view", role);
        Document doc = documentService.toggleStar(id, userId);
        return ResponseEntity.ok(toDTO(doc, userId));
    }

    @GetMapping("/{id}/config")
    public ResponseEntity<EditorConfig> getEditorConfig(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.getDocument(id);
        String permission = shareService.getUserPermission(id, userId);

        boolean canEdit = "edit".equals(permission) || "admin".equals(permission);
        if (doc.getSigningLocked()) canEdit = false;

        EditorConfig config = new EditorConfig();

        EditorConfig.Document document = new EditorConfig.Document();
        document.setFileType(doc.getFileType());
        document.setKey(doc.getDocKey());
        document.setTitle(doc.getTitle() + "." + doc.getFileType());
        document.setUrl(downloadUrl + "/" + id + "/file");

        EditorConfig.Permissions permissions = new EditorConfig.Permissions();
        permissions.setComment(true);
        permissions.setDownload(true);
        permissions.setEdit(canEdit);
        permissions.setPrint(true);
        permissions.setReview(true);
        document.setPermissions(permissions);

        config.setDocument(document);
        config.setDocumentType(doc.getDocType());

        EditorConfig.EditorConfigData editorConfigData = new EditorConfig.EditorConfigData();
        editorConfigData.setCallbackUrl(callbackUrl);

        // 获取用户真实信息
        EditorConfig.UserInfo user = new EditorConfig.UserInfo();
        user.setId(String.valueOf(userId));
        String userName = "User";
        String firstname = "";
        String lastname = "";
        try {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                var userEntity = userOpt.get();
                userName = userEntity.getRealName() != null ? userEntity.getRealName() : userEntity.getUsername();
                firstname = userEntity.getRealName() != null ? userEntity.getRealName() : "";
                lastname = "";
            }
        } catch (Exception e) {
            // 忽略，使用默认值
        }
        user.setName(userName);
        user.setFirstname(firstname);
        user.setLastname(lastname);
        user.setGroup("MiaotongDoc");
        editorConfigData.setUser(user);

        editorConfigData.setLang("zh-CN");
        editorConfigData.setMode(canEdit ? "edit" : "view");

        EditorConfig.Customization customization = new EditorConfig.Customization();
        customization.setForcesave(true);
        editorConfigData.setCustomization(customization);

        // 强制快速协作模式，防止用户切换到严格模式后无法改回
        EditorConfig.CoEditing coEditing = new EditorConfig.CoEditing();
        coEditing.setMode("fast");
        coEditing.setChange(true);
        editorConfigData.setCoEditing(coEditing);

        config.setEditorConfig(editorConfigData);

        // token 中不包含 coEditing（减小 token 体积）
        EditorConfig.EditorConfigData tokenConfig = new EditorConfig.EditorConfigData();
        tokenConfig.setCallbackUrl(editorConfigData.getCallbackUrl());
        tokenConfig.setUser(editorConfigData.getUser());
        tokenConfig.setLang(editorConfigData.getLang());
        tokenConfig.setMode(editorConfigData.getMode());
        tokenConfig.setCustomization(editorConfigData.getCustomization());
        config.setToken(editorJwtUtil.generateToken(Map.of("document", document, "editorConfig", tokenConfig)));

        return ResponseEntity.ok(config);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getFile(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        byte[] content = documentService.getFileContent(id);

        String filename = doc.getTitle() + "." + doc.getFileType();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition",
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        byte[] pdfContent = pdfExportService.convertToPdf(id);

        String filename = doc.getTitle() + ".pdf";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set("Content-Disposition",
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

        return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
    }

    private DocumentDTO toDTO(Document doc) {
        return toDTO(doc, null);
    }

    private DocumentDTO toDTO(Document doc, Long userId) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(doc.getId());
        dto.setDocKey(doc.getDocKey());
        dto.setTitle(doc.getTitle());
        dto.setDocType(doc.getDocType());
        dto.setFileType(doc.getFileType());
        dto.setFileSize(doc.getFileSize());
        dto.setFileHash(doc.getFileHash());
        dto.setOwnerUserId(doc.getOwnerUserId());
        dto.setDepartmentId(doc.getDepartmentId());
        dto.setStatus(doc.getStatus());
        dto.setCurrentVersion(doc.getCurrentVersion());
        dto.setIsStarred(doc.getIsStarred());
        dto.setShareScope(doc.getShareScope());
        dto.setSigningLocked(doc.getSigningLocked());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());

        userRepository.findById(doc.getOwnerUserId()).ifPresent(user -> {
            dto.setOwnerName(user.getRealName());
            if (user.getDepartmentId() != null) {
                departmentRepository.findById(user.getDepartmentId())
                        .ifPresent(dept -> dto.setDepartmentName(dept.getName()));
            }
        });

        if (userId != null) {
            try {
                dto.setCurrentUserPermission(shareService.getUserPermission(doc.getId(), userId));
            } catch (Exception e) {
                dto.setCurrentUserPermission(null);
            }
        }

        return dto;
    }
}
