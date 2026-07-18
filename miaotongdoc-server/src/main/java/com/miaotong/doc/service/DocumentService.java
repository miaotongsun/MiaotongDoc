package com.miaotong.doc.service;

import com.miaotong.doc.constants.NotificationType;
import com.miaotong.doc.dto.CreateDocumentRequest;
import com.miaotong.doc.dto.DocumentDTO;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentVersion;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.DocumentShareRepository;
import com.miaotong.doc.repository.DocumentVersionRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.util.DocGenerator;
import com.miaotong.doc.util.JsonUtil;
import com.miaotong.doc.util.FileHashUtil;
import com.miaotong.doc.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentShareRepository shareRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ActivityService activityService;
    private final FileValidator fileValidator;
    private final JdbcTemplate jdbcTemplate;
    private final StorageService storageService;
    private final ContentIndexService contentIndexService;
    private final TemplateService templateService;

    @Transactional
    public Document createDocument(CreateDocumentRequest request, Long userId) {
        String docKey = UUID.randomUUID().toString();
        String fileType = switch (request.getDocType()) {
            case "word" -> "docx";
            case "cell" -> "xlsx";
            case "slide" -> "pptx";
            case "markdown" -> "md";
            case "pdf" -> "pdf";
            default -> throw new BusinessException("不支持的文档类型");
        };

        String title = request.getTitle() != null ? request.getTitle() : "未命名文档";
        byte[] content;

        // 如果指定了模板，使用模板内容
        if (request.getTemplateId() != null && request.getTemplateId() > 0) {
            content = templateService.getTemplateContent(request.getTemplateId());
        } else {
            try {
                content = DocGenerator.create(request.getDocType(), title);
            } catch (IOException e) {
                throw new BusinessException("创建文档失败");
            }
        }

        String filePath = saveFile(docKey, 1, fileType, content);
        String hash = FileHashUtil.calculateSHA256(content);

        Document doc = new Document();
        doc.setDocKey(docKey);
        doc.setTitle(title);
        doc.setDocType(request.getDocType());
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize((long) content.length);
        doc.setFileHash(hash);
        doc.setOwnerUserId(userId);
        doc.setStatus("draft");
        doc.setCurrentVersion(1);

        Long deptId = userRepository.findById(userId).map(User::getDepartmentId).orElse(null);
        doc.setDepartmentId(deptId);

        doc = documentRepository.save(doc);

        // 保存初始版本记录
        DocumentVersion initialVersion = new DocumentVersion();
        initialVersion.setDocumentId(doc.getId());
        initialVersion.setVersionNumber(1);
        initialVersion.setFilePath(filePath);
        initialVersion.setFileSize((long) content.length);
        initialVersion.setFileHash(hash);
        initialVersion.setChangeSummary("初始版本");
        initialVersion.setCreatedBy(userId);
        versionRepository.save(initialVersion);

        auditService.log(userId, "CREATE", "DOCUMENT", doc.getId(), null);
        activityService.log(userId, doc.getId(), "CREATE", null);

        return doc;
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId) {
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new BusinessException("文件名不能为空");
        }

        String docType;
        String fileType;
        if (originalName.endsWith(".docx")) {
            docType = "word";
            fileType = "docx";
        } else if (originalName.endsWith(".xlsx")) {
            docType = "cell";
            fileType = "xlsx";
        } else if (originalName.endsWith(".pptx")) {
            docType = "slide";
            fileType = "pptx";
        } else if (originalName.endsWith(".md")) {
            docType = "markdown";
            fileType = "md";
        } else if (originalName.endsWith(".pdf")) {
            docType = "pdf";
            fileType = "pdf";
        } else {
            throw new BusinessException("不支持的文件格式");
        }

        fileValidator.validate(file, fileType);

        String docKey = UUID.randomUUID().toString();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }

        String filePath = saveFile(docKey, 1, fileType, content);
        String hash = FileHashUtil.calculateSHA256(content);
        String title = originalName.substring(0, originalName.lastIndexOf('.'));

        Document doc = new Document();
        doc.setDocKey(docKey);
        doc.setTitle(title);
        doc.setDocType(docType);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize((long) content.length);
        doc.setFileHash(hash);
        doc.setOwnerUserId(userId);
        doc.setStatus("draft");
        doc.setCurrentVersion(1);

        Long deptId = userRepository.findById(userId).map(User::getDepartmentId).orElse(null);
        doc.setDepartmentId(deptId);

        doc = documentRepository.save(doc);

        // 保存初始版本记录
        DocumentVersion initialVersion = new DocumentVersion();
        initialVersion.setDocumentId(doc.getId());
        initialVersion.setVersionNumber(1);
        initialVersion.setFilePath(filePath);
        initialVersion.setFileSize((long) content.length);
        initialVersion.setFileHash(hash);
        initialVersion.setChangeSummary("上传版本");
        initialVersion.setCreatedBy(userId);
        versionRepository.save(initialVersion);

        auditService.log(userId, "UPLOAD", "DOCUMENT", doc.getId(), null);
        activityService.log(userId, doc.getId(), "UPLOAD", null);

        return doc;
    }

    public Document getDocument(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (doc.getIsDeleted()) {
            throw new NotFoundException("文档已删除");
        }
        return doc;
    }

    public Document getDocumentByKey(String docKey) {
        return documentRepository.findByDocKey(docKey)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
    }

    /**
     * 搜索建议：返回标题和内容匹配的文档，带内容片段
     */
    public java.util.List<Map<String, Object>> suggest(String keyword, Long userId, String role) {
        boolean isAdmin = "admin".equals(role);
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

        // 搜索标题匹配的文档
        java.util.List<Document> titleMatches;
        if (isAdmin) {
            titleMatches = documentRepository.searchByKeyword(keyword, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        } else {
            titleMatches = documentRepository.searchAccessibleByUser(userId, keyword, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        }

        for (Document doc : titleMatches) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", doc.getId());
            item.put("title", doc.getTitle());
            item.put("docType", doc.getDocType());
            item.put("matchType", "title");
            item.put("snippet", "");
            results.add(item);
        }

        // 搜索内容匹配的文档
        java.util.List<Long> contentIds = contentIndexService.searchContent(keyword);
        if (!contentIds.isEmpty()) {
            // 过滤已有标题匹配的结果
            java.util.Set<Long> existingIds = new java.util.HashSet<>();
            for (Map<String, Object> item : results) {
                existingIds.add((Long) item.get("id"));
            }

            java.util.List<Document> contentDocs;
            if (isAdmin) {
                contentDocs = documentRepository.findAllById(contentIds);
            } else {
                contentDocs = documentRepository.findAllById(contentIds).stream()
                        .filter(d -> d.getOwnerUserId().equals(userId) ||
                                shareRepository.existsByDocumentIdAndUserId(d.getId(), userId))
                        .collect(java.util.stream.Collectors.toList());
            }

            for (Document doc : contentDocs) {
                if (existingIds.contains(doc.getId())) continue;
                if (results.size() >= 5) break;

                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", doc.getId());
                item.put("title", doc.getTitle());
                item.put("docType", doc.getDocType());
                item.put("matchType", "content");

                // 从 ES 获取内容片段
                String snippet = contentIndexService.getContentSnippet(doc.getId(), keyword);
                item.put("snippet", snippet);

                results.add(item);
            }
        }

        return results;
    }

    public Page<Document> listDocuments(String docType, String keyword, Long ownerUserId, Long userId, Long departmentId, Long folderId, String systemRole, Pageable pageable) {
        boolean isAdmin = "admin".equals(systemRole);

        // 按文件夹筛选
        if (folderId != null) {
            return documentRepository.findByFolderIdAndIsDeletedFalse(folderId, pageable);
        }

        if (keyword != null && !keyword.isEmpty()) {
            // 全文搜索：使用 Elasticsearch 搜索内容，再用数据库查询标题
            java.util.List<Long> contentMatches = contentIndexService.searchContent(keyword);
            if (contentMatches.isEmpty()) {
                // ES 无结果，降级为标题模糊搜索
                return isAdmin
                        ? documentRepository.searchByKeyword(keyword, pageable)
                        : documentRepository.searchAccessibleByUser(userId, keyword, pageable);
            }
            // ES 搜索结果 + 标题匹配
            if (isAdmin) {
                return documentRepository.searchByKeywordAndContent(keyword, contentMatches, pageable);
            } else {
                return documentRepository.searchAccessibleByUserAndContent(userId, keyword, contentMatches, pageable);
            }
        }
        if ("shared".equals(docType)) {
            return documentRepository.findSharedWithUser(userId, pageable);
        }
        if ("starred".equals(docType)) {
            return isAdmin
                    ? documentRepository.findByIsStarredTrueAndIsDeletedFalse(pageable)
                    : documentRepository.findStarredByUser(userId, pageable);
        }
        if (departmentId != null && docType != null && !docType.isEmpty()) {
            return isAdmin
                    ? documentRepository.findByDocTypeAndDepartmentTreeAndIsDeletedFalse(docType, departmentId, pageable)
                    : documentRepository.findAccessibleByUserAndDocTypeAndDepartmentTree(userId, docType, departmentId, pageable);
        }
        if (departmentId != null) {
            return isAdmin
                    ? documentRepository.findAllByDepartmentTree(departmentId, pageable)
                    : documentRepository.findAccessibleByUserAndDepartmentTree(userId, departmentId, pageable);
        }
        if (docType != null && !docType.isEmpty()) {
            return isAdmin
                    ? documentRepository.findByDocTypeAndIsDeletedFalse(docType, pageable)
                    : documentRepository.findAccessibleByUserAndDocType(userId, docType, pageable);
        }
        if (ownerUserId != null) {
            return documentRepository.findByOwnerUserIdAndIsDeletedFalse(ownerUserId, pageable);
        }
        return isAdmin
                ? documentRepository.findByIsDeletedFalse(pageable)
                : documentRepository.findAccessibleByUser(userId, pageable);
    }

    @Transactional
    public Document renameDocument(Long id, String newTitle, Long userId) {
        Document doc = getDocument(id);
        doc.setTitle(newTitle);
        doc = documentRepository.save(doc);
        auditService.log(userId, "RENAME", "DOCUMENT", id, null);
        activityService.log(userId, id, "RENAME", null);
        return doc;
    }

    /**
     * Phase 13.11: 复制文档为新文档(另存为)
     * 复制文件字节 + 创建新 Document + 初始版本,不复制版本历史
     */
    @Transactional
    public Document copyDocument(Long sourceDocId, String newTitle, Long userId) {
        Document source = getDocument(sourceDocId);
        if (!storageService.exists(source.getFilePath())) {
            throw new BusinessException("源文档文件不存在");
        }
        byte[] content = storageService.load(source.getFilePath());

        String docKey = UUID.randomUUID().toString();
        String fileType = source.getFileType();
        String filePath = saveFile(docKey, 1, fileType, content);
        String hash = com.miaotong.doc.util.FileHashUtil.calculateSHA256(content);
        String title = (newTitle != null && !newTitle.isBlank()) ? newTitle : (source.getTitle() + " (副本)");

        Document doc = new Document();
        doc.setDocKey(docKey);
        doc.setTitle(title);
        doc.setDocType(source.getDocType());
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize((long) content.length);
        doc.setFileHash(hash);
        doc.setOwnerUserId(userId);
        doc.setStatus("draft");
        doc.setCurrentVersion(1);
        doc = documentRepository.save(doc);

        DocumentVersion initialVersion = new DocumentVersion();
        initialVersion.setDocumentId(doc.getId());
        initialVersion.setVersionNumber(1);
        initialVersion.setFilePath(filePath);
        initialVersion.setFileSize((long) content.length);
        initialVersion.setFileHash(hash);
        initialVersion.setChangeSummary("另存为新文档");
        initialVersion.setCreatedBy(userId);
        versionRepository.save(initialVersion);

        auditService.log(userId, "COPY_DOCUMENT", "DOCUMENT", doc.getId(), "from " + sourceDocId);
        return doc;
    }

    @Transactional
    public DocumentVersion createVersion(Long docId, String summary, Long userId) {
        Document doc = getDocument(docId);
        if (!storageService.exists(doc.getFilePath())) {
            throw new BusinessException("文档文件不存在");
        }

        int newVersion = doc.getCurrentVersion() + 1;
        byte[] content = storageService.load(doc.getFilePath());
        String newObjectKey = buildObjectKey(doc.getDocKey(), newVersion, doc.getFileType());
        storageService.store(newObjectKey, content);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(docId);
        version.setVersionNumber(newVersion);
        version.setFilePath(newObjectKey);
        version.setFileSize((long) content.length);
        version.setFileHash(doc.getFileHash());
        version.setChangeSummary(summary != null ? summary : "手动保存版本");
        version.setCreatedBy(userId);
        version = versionRepository.save(version);

        doc.setCurrentVersion(newVersion);
        doc.setFilePath(newObjectKey);
        doc.setFileSize((long) content.length);
        documentRepository.save(doc);

        auditService.log(userId, "CREATE_VERSION", "DOCUMENT", docId, "v" + newVersion);
        activityService.log(userId, docId, "SAVE_VERSION", null);

        // 通知文档共享者有新版本（排除操作者自己）
        shareRepository.findByDocumentId(docId).stream()
                .filter(s -> !s.getUserId().equals(userId))
                .forEach(s -> notificationService.notify(userId, s.getUserId(), docId,
                        NotificationType.VERSION, "保存了新版本 v" + newVersion));

        return version;
    }

    @Transactional
    public void moveToFolder(Long docId, Long folderId, Long userId) {
        Document doc = getDocument(docId);
        doc.setFolderId(folderId);
        documentRepository.save(doc);
        auditService.log(userId, "MOVE", "DOCUMENT", docId, null);
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        Document doc = getDocument(id);
        doc.setIsDeleted(true);
        doc.setDeletedAt(java.time.LocalDateTime.now());
        doc.setDeletedBy(userId);
        documentRepository.save(doc);

        // 清理 OnlyOffice coauthoring 内部状态，防止新文档复用旧状态
        cleanupOnlyOfficeState(doc.getDocKey(), true);

        auditService.log(userId, "DELETE", "DOCUMENT", id, null);
        activityService.log(userId, id, "DELETE", null);
    }

    // ===== 回收站功能 =====

    public java.util.List<DocumentDTO> getTrashDocuments(Long userId) {
        java.util.List<Document> docs;
        if (userId == null) {
            docs = documentRepository.findByIsDeletedTrueOrderByDeletedAtDesc();
        } else {
            docs = documentRepository.findByIsDeletedTrueAndOwnerUserIdOrderByDeletedAtDesc(userId);
        }
        return docs.stream().map(doc -> {
            DocumentDTO dto = new DocumentDTO();
            dto.setId(doc.getId());
            dto.setTitle(doc.getTitle());
            dto.setDocType(doc.getDocType());
            dto.setFileType(doc.getFileType());
            dto.setFileSize(doc.getFileSize());
            dto.setOwnerUserId(doc.getOwnerUserId());
            dto.setCurrentVersion(doc.getCurrentVersion());
            dto.setUpdatedAt(doc.getDeletedAt());

            // 创建人
            userRepository.findById(doc.getOwnerUserId()).ifPresent(user -> {
                dto.setOwnerName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            });

            // 删除人
            if (doc.getDeletedBy() != null) {
                userRepository.findById(doc.getDeletedBy()).ifPresent(user -> {
                    dto.setUpdatedByName(user.getRealName() != null ? user.getRealName() : user.getUsername());
                });
            }

            return dto;
        }).toList();
    }

    @Transactional
    public void restoreFromTrash(Long id, Long userId) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (!Boolean.TRUE.equals(doc.getIsDeleted())) {
            throw new BusinessException("文档不在回收站中");
        }
        doc.setIsDeleted(false);
        doc.setDeletedAt(null);
        doc.setDeletedBy(null);
        documentRepository.save(doc);
        auditService.log(userId, "RESTORE", "DOCUMENT", id, null);
        activityService.log(userId, id, "RESTORE", null);
    }

    @Transactional
    public void permanentDelete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        // 删除文件
        if (doc.getFilePath() != null && storageService.exists(doc.getFilePath())) {
            storageService.delete(doc.getFilePath());
        }
        // 删除版本文件
        versionRepository.findByDocumentId(id).forEach(v -> {
            if (v.getFilePath() != null && storageService.exists(v.getFilePath())) {
                storageService.delete(v.getFilePath());
            }
        });
        // 删除数据库记录
        versionRepository.deleteByDocumentId(id);
        shareRepository.deleteByDocumentId(id);
        documentRepository.delete(doc);
    }

    @Transactional
    public int emptyTrash(Long userId) {
        java.util.List<Document> docs;
        if (userId == null) {
            docs = documentRepository.findByIsDeletedTrueOrderByDeletedAtDesc();
        } else {
            docs = documentRepository.findByIsDeletedTrueAndOwnerUserIdOrderByDeletedAtDesc(userId);
        }
        int count = 0;
        for (Document doc : docs) {
            permanentDelete(doc.getId());
            count++;
        }
        return count;
    }

    /**
     * 批量导出文档为 ZIP 文件
     */
    public byte[] exportDocumentsAsZip(java.util.List<Long> documentIds, Long userId, String role) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (Long docId : documentIds) {
                Document doc = getDocument(docId);
                // 检查权限
                if (!"admin".equals(role)) {
                    boolean isOwner = doc.getOwnerUserId().equals(userId);
                    boolean hasShare = shareRepository.existsByDocumentIdAndUserId(docId, userId);
                    if (!isOwner && !hasShare) {
                        continue; // 无权限，跳过
                    }
                }
                // 读取文件
                byte[] content = storageService.load(doc.getFilePath());
                String filename = doc.getTitle() + "." + doc.getFileType();
                // 写入 ZIP
                zos.putNextEntry(new java.util.zip.ZipEntry(filename));
                zos.write(content);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * 清理 OnlyOffice coauthoring 服务器的内部状态（task_result + doc_changes）
     * 防止新建同名文档时复用旧的 coauthoring 状态导致"连接失败"
     *
     * @param docKey 文档 key
     * @param fullCleanup true: 删除 task_result + doc_changes（用于文档删除）
     *                    false: 只删除 doc_changes（用于保存版本，避免其他编辑者报"版本已更改"）
     */
    private void cleanupOnlyOfficeState(String docKey, boolean fullCleanup) {
        try {
            int changes = jdbcTemplate.update("DELETE FROM doc_changes WHERE id = ?", docKey);
            int tasks = 0;
            if (fullCleanup) {
                tasks = jdbcTemplate.update("DELETE FROM task_result WHERE id = ?", docKey);
            }
            log.info("清理 OnlyOffice 状态: docKey={}, full={}, doc_changes删除{}条, task_result删除{}条", docKey, fullCleanup, changes, tasks);
        } catch (Exception e) {
            log.warn("清理 OnlyOffice 状态失败（不影响删除操作）: docKey={}", docKey, e);
        }
    }

    @Transactional
    public void restore(Long id, Long userId) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文档不存在"));
        if (!doc.getIsDeleted()) return;
        doc.setIsDeleted(false);
        doc.setDeletedAt(null);
        doc.setDeletedBy(null);
        documentRepository.save(doc);
        auditService.log(userId, "RESTORE", "DOCUMENT", id, null);
    }

    @Transactional
    public Document toggleStar(Long id, Long userId) {
        Document doc = getDocument(id);
        doc.setIsStarred(!doc.getIsStarred());
        return documentRepository.save(doc);
    }

    /**
     * 更新文档元数据（文件哈希、大小、更新人等）
     */
    @Transactional
    public Document updateDocument(Document doc) {
        return documentRepository.save(doc);
    }

    public byte[] getFileContent(Long id) {
        Document doc = getDocument(id);
        return storageService.load(doc.getFilePath());
    }

    public byte[] getFileContentForVersion(Long docId, Integer versionNumber) {
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumber(docId, versionNumber)
                .orElseThrow(() -> new NotFoundException("版本不存在"));
        return storageService.load(version.getFilePath());
    }

    @Transactional
    public void restoreVersion(Long docId, Integer versionNumber, Long userId) {
        Document doc = getDocument(docId);
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumber(docId, versionNumber)
                .orElseThrow(() -> new NotFoundException("版本不存在"));

        byte[] content = storageService.load(version.getFilePath());

        int newVersion = doc.getCurrentVersion() + 1;
        String filePath = saveFile(doc.getDocKey(), newVersion, doc.getFileType(), content);
        String hash = FileHashUtil.calculateSHA256(content);

        DocumentVersion newVersionEntity = new DocumentVersion();
        newVersionEntity.setDocumentId(docId);
        newVersionEntity.setVersionNumber(newVersion);
        newVersionEntity.setFilePath(filePath);
        newVersionEntity.setFileSize((long) content.length);
        newVersionEntity.setFileHash(hash);
        newVersionEntity.setChangeSummary("恢复自 v" + versionNumber);
        newVersionEntity.setCreatedBy(userId);
        versionRepository.save(newVersionEntity);

        doc.setCurrentVersion(newVersion);
        doc.setFilePath(filePath);
        doc.setFileSize((long) content.length);
        doc.setFileHash(hash);
        documentRepository.save(doc);

        auditService.log(userId, "RESTORE_VERSION", "DOCUMENT", docId, "v" + versionNumber);
        activityService.log(userId, docId, "RESTORE_VERSION", null);

        // 恢复版本后必须清理 task_result + doc_changes
        // 因为文件内容已改变，旧的协作状态不再有效
        // 其他编辑者会收到"版本已更改"并刷新，这是合理的
        cleanupOnlyOfficeState(doc.getDocKey(), true);
    }

    private String saveFile(String docKey, int version, String fileType, byte[] content) {
        String objectKey = buildObjectKey(docKey, version, fileType);
        return storageService.store(objectKey, content);
    }

    private String buildObjectKey(String docKey, int version, String fileType) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "documents/" + datePath + "/" + docKey + "/v" + version + "." + fileType;
    }

    // ==================== PDF 文字编辑 ====================

    /**
     * 保存 PDF 文字编辑
     * 使用 JSONB 列存储编辑操作
     */
    @Transactional
    public String saveTextEdits(Long docId, List<Map<String, Object>> edits) {
        Document doc = getDocument(docId);

        // 将编辑列表转为 JSON 字符串存储
        String editsJson = JsonUtil.toJson(edits);

        // 使用 SQL 直接更新（假设已有 text_edits 列）
        // 如果列不存在，需要创建：ALTER TABLE mt_document ADD COLUMN text_edits JSONB DEFAULT '[]'
        jdbcTemplate.update(
            "UPDATE mt_document SET text_edits = ?::jsonb, updated_at = ? WHERE id = ?",
            editsJson,
            LocalDateTime.now(),
            docId
        );

        log.info("保存 PDF 文字编辑: docId={}, editsCount={}", docId, edits.size());
        return editsJson;
    }

    /**
     * 获取 PDF 文字编辑
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTextEdits(Long docId) {
        Document doc = getDocument(docId);

        // 从数据库读取 text_edits JSONB 列
        String editsJson = jdbcTemplate.queryForObject(
            "SELECT text_edits FROM mt_document WHERE id = ?",
            new Object[]{docId},
            String.class
        );

        if (editsJson == null || editsJson.isBlank() || editsJson.equals("[]")) {
            return List.of();
        }

        try {
            return JsonUtil.parseJsonList(editsJson);
        } catch (Exception e) {
            log.warn("解析 PDF 文字编辑失败: docId={}, error={}", docId, e.getMessage());
            return List.of();
        }
    }

    // ==================== PDF Markdown 内容管理 ====================

    /**
     * 保存 PDF 识别后的 Markdown 内容（按页分组）
     */
    @Transactional
    public void savePdfMarkdown(Long docId, Map<String, String> markdown) {
        Document doc = getDocument(docId);
        doc.setPdfMarkdown(markdown);
        documentRepository.save(doc);
        log.info("保存 PDF Markdown 内容: docId={}, pages={}", docId, markdown.size());
    }

    /**
     * 标记 PDF 已完成识别
     */
    @Transactional
    public void markPdfRecognized(Long docId) {
        Document doc = getDocument(docId);
        doc.setPdfRecognized(true);
        doc.setPdfRecognizedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("PDF 识别完成: docId={}", docId);
    }

    /**
     * 获取 PDF Markdown 内容
     */
    public Map<String, String> getPdfMarkdown(Long docId) {
        Document doc = getDocument(docId);
        return doc.getPdfMarkdown() != null ? doc.getPdfMarkdown() : Map.of();
    }

    /**
     * 保存 PDF OCR 坐标数据（用于在 PDF 原图位置叠加文字层）
     * @param ocrData 按页分组的 OCR 数据：{ "1": {"dpi": 200, "width": 1728, "height": 2400, "regions": [...]}, ... }
     */
    @Transactional
    public void savePdfOcrData(Long docId, Map<String, Object> ocrData) {
        Document doc = getDocument(docId);
        doc.setPdfOcrData(ocrData);
        documentRepository.save(doc);
        log.info("保存 PDF OCR 坐标: docId={}, pages={}", docId, ocrData.size());
    }

    /**
     * 获取 PDF OCR 坐标数据
     */
    public Map<String, Object> getPdfOcrData(Long docId) {
        Document doc = getDocument(docId);
        return doc.getPdfOcrData() != null ? doc.getPdfOcrData() : Map.of();
    }
}
