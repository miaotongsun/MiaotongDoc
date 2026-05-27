package com.miaotong.doc.service;

import com.miaotong.doc.dto.CreateDocumentRequest;
import com.miaotong.doc.dto.DocumentDTO;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentVersion;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.DocumentVersionRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.util.DocGenerator;
import com.miaotong.doc.util.FileHashUtil;
import com.miaotong.doc.util.FileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final FileValidator fileValidator;

    @Value("${app.storage-path:/data/documents}")
    private String storagePath;

    @Transactional
    public Document createDocument(CreateDocumentRequest request, Long userId) {
        String docKey = UUID.randomUUID().toString();
        String fileType = switch (request.getDocType()) {
            case "word" -> "docx";
            case "cell" -> "xlsx";
            case "slide" -> "pptx";
            default -> throw new BusinessException("不支持的文档类型");
        };

        String title = request.getTitle() != null ? request.getTitle() : "未命名文档";
        byte[] content;
        try {
            content = DocGenerator.create(request.getDocType(), title);
        } catch (IOException e) {
            throw new BusinessException("创建文档失败");
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

    public Page<Document> listDocuments(String docType, String keyword, Long ownerUserId, Long userId, Long departmentId, String systemRole, Pageable pageable) {
        boolean isAdmin = "admin".equals(systemRole);

        if (keyword != null && !keyword.isEmpty()) {
            return isAdmin
                    ? documentRepository.searchByKeyword(keyword, pageable)
                    : documentRepository.searchAccessibleByUser(userId, keyword, pageable);
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
        return doc;
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        Document doc = getDocument(id);
        doc.setIsDeleted(true);
        doc.setDeletedAt(java.time.LocalDateTime.now());
        doc.setDeletedBy(userId);
        documentRepository.save(doc);
        auditService.log(userId, "DELETE", "DOCUMENT", id, null);
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

    public byte[] getFileContent(Long id) {
        Document doc = getDocument(id);
        try {
            Path path = Paths.get(doc.getFilePath());
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }
    }

    public byte[] getFileContentForVersion(Long docId, Integer versionNumber) {
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumber(docId, versionNumber)
                .orElseThrow(() -> new NotFoundException("版本不存在"));
        try {
            Path path = Paths.get(version.getFilePath());
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException("读取版本文件失败");
        }
    }

    @Transactional
    public void restoreVersion(Long docId, Integer versionNumber, Long userId) {
        Document doc = getDocument(docId);
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumber(docId, versionNumber)
                .orElseThrow(() -> new NotFoundException("版本不存在"));

        byte[] content;
        try {
            content = Files.readAllBytes(Paths.get(version.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException("读取版本文件失败");
        }

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
    }

    private String saveFile(String docKey, int version, String fileType, byte[] content) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path dir = Paths.get(storagePath, datePath, docKey);
            Files.createDirectories(dir);

            String fileName = "v" + version + "." + fileType;
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, content);

            return filePath.toString();
        } catch (IOException e) {
            throw new BusinessException("保存文件失败");
        }
    }
}
