package com.miaotong.doc.controller;

import com.miaotong.doc.dto.*;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.SigningRecord;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.ShareService;
import com.miaotong.doc.service.PdfExportService;
import com.miaotong.doc.service.SigningService;
import com.miaotong.doc.service.ContentIndexService;
import com.miaotong.doc.service.NotificationService;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.constants.NotificationType;
import com.miaotong.doc.util.JwtUtil;
import com.miaotong.doc.util.EditorJwtUtil;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;
    private final PdfExportService pdfExportService;
    private final SigningService signingService;
    private final ContentIndexService contentIndexService;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final EditorJwtUtil editorJwtUtil;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    /** v2.7.2：注入 AI 插件配置 */
    private final com.miaotong.doc.service.AiProxyService aiProxyService;

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
        // 管理员在没有显式授权时默认拥有 view 权限
        String perm = shareService.getUserPermission(documentId, userId, systemRole);
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

    /**
     * 上传图片（用于编辑器内图片插入）
     * 图片存在文档同目录下的 images/ 子目录
     */
    @PostMapping("/{id}/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.getDocument(id);

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("只支持上传图片文件");
        }

        // 生成存储路径：与文档同目录 + images/ 子目录
        String ext = switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
        // 从文档路径提取目录部分，如 documents/2026/06/{docKey}
        String docDir = doc.getFilePath().substring(0, doc.getFilePath().lastIndexOf("/"));
        String objectKey = docDir + "/images/" + java.util.UUID.randomUUID() + ext;

        try {
            storageService.store(objectKey, file.getBytes());
        } catch (Exception e) {
            throw new BusinessException("图片上传失败: " + e.getMessage());
        }

        String url = "/api/documents/file/" + objectKey;
        return ResponseEntity.ok(Map.of("url", url));
    }

    // 手动触发全文索引
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindex(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        contentIndexService.indexNewDocuments();
        return ResponseEntity.ok(Map.of("message", "索引任务已触发"));
    }

    @GetMapping("/suggest")
    public ResponseEntity<?> suggest(
            @RequestParam String keyword,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        java.util.List<Map<String, Object>> suggestions = documentService.suggest(keyword, userId, role);
        return ResponseEntity.ok(Map.of("suggestions", suggestions));
    }

    @GetMapping("/list")
    public ResponseEntity<Page<DocumentDTO>> listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long owner,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Sort pageSort = parseSort(sort);
        PageRequest pageRequest = PageRequest.of(page, size, pageSort);
        Page<Document> documents = documentService.listDocuments(type, keyword, owner, userId, departmentId, folderId, role, pageRequest);
        return ResponseEntity.ok(documents.map(doc -> toDTO(doc, userId, role)));
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<Map<String, String>> moveToFolder(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Long folderId = body.get("folderId") != null ? ((Number) body.get("folderId")).longValue() : null;
        documentService.moveToFolder(id, folderId, userId);
        return ResponseEntity.ok(Map.of("message", "文档已移动"));
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
        return ResponseEntity.ok(toDTO(doc, userId, role));
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
        return ResponseEntity.ok(toDTO(doc, userId, role));
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

    // ===== 回收站 =====

    @GetMapping("/trash")
    public ResponseEntity<?> getTrashDocuments(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        java.util.List<DocumentDTO> docs;
        if ("admin".equals(role)) {
            docs = documentService.getTrashDocuments(null);
        } else {
            docs = documentService.getTrashDocuments(userId);
        }
        return ResponseEntity.ok(docs);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, String>> restoreFromTrash(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        documentService.restoreFromTrash(id, userId);
        return ResponseEntity.ok(Map.of("message", "文档已恢复"));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Map<String, String>> permanentDelete(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "admin", role);
        documentService.permanentDelete(id);
        return ResponseEntity.ok(Map.of("message", "文档已永久删除"));
    }

    @DeleteMapping("/trash/empty")
    public ResponseEntity<Map<String, Object>> emptyTrash(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        int count;
        if ("admin".equals(role)) {
            count = documentService.emptyTrash(null);
        } else {
            count = documentService.emptyTrash(userId);
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("message", "回收站已清空");
        result.put("deleted", count);
        return ResponseEntity.ok(result);
    }

    // ===== 批量导出 =====

    @PostMapping("/export/zip")
    public ResponseEntity<byte[]> exportAsZip(
            @RequestBody java.util.List<Long> documentIds,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");

        try {
            byte[] zipContent = documentService.exportDocumentsAsZip(documentIds, userId, role);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/zip"));
            headers.setContentDisposition(org.springframework.http.ContentDisposition.builder("attachment")
                    .filename("documents_" + java.time.LocalDate.now() + ".zip").build());
            headers.setContentLength(zipContent.length);

            return new ResponseEntity<>(zipContent, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("批量导出失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<Map<String, Object>> createVersion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        requirePermission(id, userId, "admin", role);
        String summary = body != null ? body.get("summary") : null;
        var version = documentService.createVersion(id, summary, userId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("message", "版本已保存");
        result.put("versionNumber", version.getVersionNumber());
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
        return ResponseEntity.ok(toDTO(doc, userId, role));
    }

    @GetMapping("/{id}/config")
    public ResponseEntity<EditorConfig> getEditorConfig(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Document doc = documentService.getDocument(id);
        String permission = shareService.getUserPermission(id, userId, role);

        boolean canEdit = "edit".equals(permission) || "admin".equals(permission);
        boolean reviewMode = false;

        // 签署锁定后（signed状态），所有人只读
        if (Boolean.TRUE.equals(doc.getSigningLocked())) {
            canEdit = false;
        }

        // 签署中：文档发起人可编辑，签署人进入修订模式，其他人只读
        if ("signing".equals(doc.getStatus()) && !Boolean.TRUE.equals(doc.getSigningLocked())) {
            boolean isOwner = doc.getOwnerUserId().equals(userId);
            if (isOwner) {
                canEdit = true;
            } else {
                // 检查是否是签署人
                java.util.List<SigningRecord> records = signingService.getActiveTasksByDocumentId(id)
                    .stream().flatMap(t -> signingService.getTaskRecords(t.getId()).stream())
                    .filter(r -> r.getSignerUserId().equals(userId) && "pending".equals(r.getStatus()))
                    .toList();
                if (!records.isEmpty()) {
                    canEdit = false;
                    reviewMode = true; // 修订模式：只能通过修订来编辑
                }
            }
        }

        EditorConfig config = new EditorConfig();

        EditorConfig.Document document = new EditorConfig.Document();
        document.setFileType(doc.getFileType());
        document.setKey(doc.getDocKey());
        document.setTitle(doc.getTitle() + "." + doc.getFileType());
        document.setUrl(downloadUrl + "/" + id + "/file");

        EditorConfig.Permissions permissions = new EditorConfig.Permissions();
        boolean canComment = "comment".equals(permission) || canEdit;
        permissions.setComment(canComment);
        permissions.setDownload(true);
        permissions.setEdit(canEdit);
        permissions.setPrint(true);
        permissions.setReview(reviewMode || canEdit);
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
        // 禁用强制保存，避免触发 status=8 导致"版本已更改"
        // 自动保存已足够保证数据安全
        customization.setForcesave(false);
        editorConfigData.setCustomization(customization);

        // 强制快速协作模式，防止用户切换到严格模式后无法改回
        EditorConfig.CoEditing coEditing = new EditorConfig.CoEditing();
        coEditing.setMode("fast");
        // 不设置 change=true，避免触发强制保存（status=8）导致"版本已更改"
        editorConfigData.setCoEditing(coEditing);

        // 启用文件刷新请求，确保版本变更时能重新加载最新文档
        editorConfigData.setCanRequestRefreshFile(true);

        // MiaotongDoc v2.7.2：注入 AI 插件配置（aiPluginSettings）
        // 让 OnlyOffice 启动 AI 插件时 window.Asc.plugin.info.aiPluginSettings 有值
        // 插件会用它初始化 AI.serverSettings，覆盖 localStorage 旧配置
        try {
            Object aiConfig = aiProxyService.getConfig();
            // 包一层（加上 version=4 匹配 AI.Storage.Version + customProviders 字段）
            // 否则 AI.Storage.load() 会因 version 不匹配把 serverSettings 当 null
            java.util.Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
            wrapped.put("version", 4);
            wrapped.put("providers", ((java.util.Map<?, ?>) aiConfig).get("providers"));
            wrapped.put("models", ((java.util.Map<?, ?>) aiConfig).get("models"));
            wrapped.put("actions", ((java.util.Map<?, ?>) aiConfig).get("actions"));
            wrapped.put("customProviders", new java.util.HashMap<>());

            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            String aiConfigJson = om.writeValueAsString(wrapped);
            EditorConfig.Plugins aiPlugin = new EditorConfig.Plugins();
            aiPlugin.setAiPluginSettings(aiConfigJson);
            java.util.Map<String, EditorConfig.Plugins> plugins = new java.util.LinkedHashMap<>();
            plugins.put("ai", aiPlugin);
            editorConfigData.setPlugins(plugins);
            log.debug("AI Plugin 注入成功: providers={}", ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) aiConfig).get("providers")).keySet());
        } catch (Exception e) {
            log.warn("AI Plugin 配置注入失败: {}", e.getMessage());
        }

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

        String filename = doc.getTitle() + "_v" + doc.getCurrentVersion() + "." + doc.getFileType();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Disposition",
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    /**
     * 通用文件访问（仅限图片）
     */
    @GetMapping("/file/**")
    public ResponseEntity<byte[]> getFileByPath(HttpServletRequest httpRequest) {
        String uri = httpRequest.getRequestURI();
        String objectKey = uri.replaceFirst("^/api/documents/file/", "");

        // 安全限制：只允许访问 images/ 目录下的文件
        if (!objectKey.startsWith("images/") && !objectKey.contains("/images/")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            byte[] content = storageService.load(objectKey);
            HttpHeaders headers = new HttpHeaders();
            // 根据扩展名设置 Content-Type
            if (objectKey.endsWith(".png")) headers.setContentType(MediaType.IMAGE_PNG);
            else if (objectKey.endsWith(".jpg") || objectKey.endsWith(".jpeg")) headers.setContentType(MediaType.IMAGE_JPEG);
            else if (objectKey.endsWith(".gif")) headers.setContentType(MediaType.IMAGE_GIF);
            else if (objectKey.endsWith(".webp")) headers.setContentType(MediaType.parseMediaType("image/webp"));
            else headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setCacheControl("public, max-age=31536000");
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = "用户";
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                // username will be set below
            });
        }
        // Get username for watermark
        final String[] usernameArr = {"用户"};
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                usernameArr[0] = user.getRealName() != null ? user.getRealName() : user.getUsername();
            });
        }

        Document doc = documentService.getDocument(id);
        byte[] pdfContent = pdfExportService.convertToPdf(id, usernameArr[0]);

        String filename = doc.getTitle() + "_v" + doc.getCurrentVersion() + ".pdf";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set("Content-Disposition",
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

        return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
    }

    /**
     * @提及用户：发送通知 + 授权文档查看权限
     */
    @PostMapping("/{id}/mention")
    public ResponseEntity<Map<String, Object>> mentionUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Long fromUserId = (Long) httpRequest.getAttribute("userId");
        Long mentionedUserId = Long.valueOf(body.get("userId").toString());
        String mentionedName = (String) body.getOrDefault("userName", "用户");

        Document doc = documentService.getDocument(id);

        // 如果被提及用户没有文档权限，授予查看权限
        shareService.grantViewIfAbsent(id, mentionedUserId, fromUserId);

        // 发送提及通知
        notificationService.notify(fromUserId, mentionedUserId, id,
                NotificationType.DOC_MENTION,
                "在文档中@了您");

        return ResponseEntity.ok(Map.of("message", "通知已发送"));
    }

    private DocumentDTO toDTO(Document doc) {
        return toDTO(doc, null, null);
    }

    private DocumentDTO toDTO(Document doc, Long userId) {
        return toDTO(doc, userId, null);
    }

    private DocumentDTO toDTO(Document doc, Long userId, String systemRole) {
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
        dto.setUpdatedBy(doc.getUpdatedBy());
        dto.setFolderId(doc.getFolderId());

        userRepository.findById(doc.getOwnerUserId()).ifPresent(user -> {
            dto.setOwnerName(user.getRealName());
            if (user.getDepartmentId() != null) {
                departmentRepository.findById(user.getDepartmentId())
                        .ifPresent(dept -> dto.setDepartmentName(dept.getName()));
            }
        });

        if (doc.getUpdatedBy() != null) {
            userRepository.findById(doc.getUpdatedBy()).ifPresent(user -> {
                dto.setUpdatedByName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            });
        }

        if (userId != null) {
            try {
                dto.setCurrentUserPermission(shareService.getUserPermission(doc.getId(), userId, systemRole));
            } catch (Exception e) {
                dto.setCurrentUserPermission(null);
            }
        }

        return dto;
    }
}
