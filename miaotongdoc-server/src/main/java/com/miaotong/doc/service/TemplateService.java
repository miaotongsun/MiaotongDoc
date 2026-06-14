package com.miaotong.doc.service;

import com.miaotong.doc.entity.DocumentTemplate;
import com.miaotong.doc.entity.TemplateCategory;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentTemplateRepository;
import com.miaotong.doc.repository.TemplateCategoryRepository;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final DocumentTemplateRepository templateRepository;
    private final TemplateCategoryRepository categoryRepository;
    private final StorageService storageService;

    public List<DocumentTemplate> getActiveTemplates() {
        return templateRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    public List<DocumentTemplate> getTemplatesByType(String docType) {
        return templateRepository.findByDocTypeAndIsActiveTrueOrderBySortOrderAsc(docType);
    }

    public List<DocumentTemplate> getTemplatesByCategory(String category) {
        return templateRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category);
    }

    public List<String> getCategories() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(TemplateCategory::getName)
                .collect(Collectors.toList());
    }

    public List<TemplateCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public TemplateCategory addCategory(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new BusinessException("分类已存在");
        }
        TemplateCategory category = new TemplateCategory();
        category.setName(name);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    public DocumentTemplate getTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("模板不存在"));
    }

    @Transactional
    public DocumentTemplate createTemplate(String name, String description, String docType,
                                            String category, MultipartFile file, Long userId) throws IOException {
        // 验证文件类型
        String fileType = getFileType(docType);
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.endsWith("." + fileType)) {
            throw new BusinessException("文件类型与文档类型不匹配");
        }

        // 存储模板文件
        String objectKey = "templates/" + docType + "/" + System.currentTimeMillis() + "." + fileType;
        String filePath = storageService.store(objectKey, file.getBytes());

        DocumentTemplate template = new DocumentTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setDocType(docType);
        template.setFilePath(filePath);
        template.setFileSize(file.getSize());
        template.setCategory(category);
        template.setIsSystem(false);
        template.setIsActive(true);
        template.setCreatedBy(userId);

        return templateRepository.save(template);
    }

    @Transactional
    public DocumentTemplate updateTemplate(Long id, String name, String description,
                                            String category, Boolean isActive) {
        DocumentTemplate template = getTemplate(id);
        if (name != null) template.setName(name);
        if (description != null) template.setDescription(description);
        if (category != null) template.setCategory(category);
        if (isActive != null) template.setIsActive(isActive);
        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        DocumentTemplate template = getTemplate(id);
        if (Boolean.TRUE.equals(template.getIsSystem())) {
            throw new BusinessException("系统预置模板不能删除");
        }
        // 删除文件
        if (template.getFilePath() != null) {
            storageService.delete(template.getFilePath());
        }
        templateRepository.delete(template);
    }

    public byte[] getTemplateContent(Long id) {
        DocumentTemplate template = getTemplate(id);
        return storageService.load(template.getFilePath());
    }

    private String getFileType(String docType) {
        return switch (docType) {
            case "word" -> "docx";
            case "cell" -> "xlsx";
            case "slide" -> "pptx";
            default -> "docx";
        };
    }
}
