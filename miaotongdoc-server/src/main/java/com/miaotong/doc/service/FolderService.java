package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.Folder;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.FolderRepository;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    /** 特殊标记值：表示不移动文件夹的父级 */
    public static final Long NO_MOVE = Long.MIN_VALUE;

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    public List<Folder> getUserFolders(Long userId) {
        return folderRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Folder> getChildFolders(Long parentId) {
        return folderRepository.findByParentIdOrderByCreatedAtDesc(parentId);
    }

    public List<Folder> getDepartmentFolders(Long departmentId) {
        return folderRepository.findByDepartmentIdOrderByCreatedAtDesc(departmentId);
    }

    public Folder getFolder(Long id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文件夹不存在"));
    }

    @Transactional
    public Folder createFolder(String name, Long parentId, Long userId, Long departmentId, String color) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setOwnerUserId(userId);
        folder.setDepartmentId(departmentId);
        folder.setColor(color);
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder renameFolder(Long id, String newName) {
        Folder folder = getFolder(id);
        folder.setName(newName);
        return folderRepository.save(folder);
    }

    /**
     * 更新文件夹属性（名称、颜色、上级文件夹）
     * @param parentId 传 NO_MOVE 表示不移动，传 null 表示移到根目录，传具体 ID 表示移动到该文件夹下
     */
    @Transactional
    public Folder updateFolder(Long id, String name, String color, Long parentId) {
        Folder folder = getFolder(id);
        if (name != null && !name.isBlank()) {
            folder.setName(name.trim());
        }
        if (color != null) {
            folder.setColor(color);
        }
        if (parentId != null && !parentId.equals(NO_MOVE)) {
            // 防止将文件夹移动到自身或自己的子文件夹下
            if (parentId.equals(id)) {
                throw new BusinessException("不能将文件夹移动到自身下");
            }
            if (isDescendantOf(id, parentId)) {
                throw new BusinessException("不能将文件夹移动到其子文件夹下");
            }
            folder.setParentId(parentId);
        } else if (parentId == null) {
            // parentId 显式为 null，移到根目录
            folder.setParentId(null);
        }
        // parentId == NO_MOVE 时不修改 parentId
        return folderRepository.save(folder);
    }

    /**
     * 检查 candidateChildId 是否是 ancestorId 的后代
     */
    private boolean isDescendantOf(Long ancestorId, Long candidateChildId) {
        Folder current = folderRepository.findById(candidateChildId).orElse(null);
        while (current != null) {
            if (current.getParentId() == null) return false;
            if (current.getParentId().equals(ancestorId)) return true;
            current = folderRepository.findById(current.getParentId()).orElse(null);
        }
        return false;
    }

    @Transactional
    public void deleteFolder(Long id, Long moveToParentId) {
        Folder folder = getFolder(id);
        // 将当前文件夹下的文档移动到目标文件夹
        List<Document> docs = documentRepository.findByFolderIdAndIsDeletedFalse(id,
                org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        for (Document doc : docs) {
            doc.setFolderId(moveToParentId);
            documentRepository.save(doc);
        }
        // 递归删除子文件夹（子文件夹的文档也移到目标文件夹）
        List<Folder> children = folderRepository.findByParentIdOrderByCreatedAtDesc(id);
        for (Folder child : children) {
            deleteFolder(child.getId(), moveToParentId);
        }
        folderRepository.delete(folder);
    }

    /** 兼容旧调用 */
    @Transactional
    public void deleteFolder(Long id) {
        deleteFolder(id, null);
    }

    /**
     * 下载文件夹内所有文档（ZIP打包）
     */
    public byte[] downloadFolderAsZip(Long folderId) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 添加当前文件夹下的文档
            List<Document> docs = documentRepository.findByFolderIdAndIsDeletedFalse(folderId,
                    org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
            for (Document doc : docs) {
                byte[] content = storageService.load(doc.getFilePath());
                String filename = doc.getTitle() + "." + doc.getFileType();
                zos.putNextEntry(new ZipEntry(filename));
                zos.write(content);
                zos.closeEntry();
            }

            // 递归添加子文件夹的文档
            List<Folder> children = folderRepository.findByParentIdOrderByCreatedAtDesc(folderId);
            for (Folder child : children) {
                addFolderToZip(zos, child, "");
            }
        }
        return baos.toByteArray();
    }

    private void addFolderToZip(ZipOutputStream zos, Folder folder, String parentPath) throws Exception {
        String folderPath = parentPath.isEmpty() ? folder.getName() : parentPath + "/" + folder.getName();

        // 添加文件夹内的文档
        List<Document> docs = documentRepository.findByFolderIdAndIsDeletedFalse(folder.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        for (Document doc : docs) {
            byte[] content = storageService.load(doc.getFilePath());
            String filename = folderPath + "/" + doc.getTitle() + "." + doc.getFileType();
            zos.putNextEntry(new ZipEntry(filename));
            zos.write(content);
            zos.closeEntry();
        }

        // 递归添加子文件夹
        List<Folder> children = folderRepository.findByParentIdOrderByCreatedAtDesc(folder.getId());
        for (Folder child : children) {
            addFolderToZip(zos, child, folderPath);
        }
    }
}
