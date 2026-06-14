package com.miaotong.doc.controller;

import com.miaotong.doc.entity.DocumentTemplate;
import com.miaotong.doc.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<List<DocumentTemplate>> getTemplates(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String category) {
        if (docType != null && !docType.isEmpty()) {
            return ResponseEntity.ok(templateService.getTemplatesByType(docType));
        }
        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(templateService.getTemplatesByCategory(category));
        }
        return ResponseEntity.ok(templateService.getActiveTemplates());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(templateService.getCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<?> addCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "分类名称不能为空"));
        }
        try {
            return ResponseEntity.ok(templateService.addCategory(name));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        templateService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("message", "分类已删除"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentTemplate> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<DocumentTemplate> createTemplate(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String docType,
            @RequestParam(required = false) String category,
            @RequestParam MultipartFile file,
            HttpServletRequest httpRequest) throws Exception {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return ResponseEntity.ok(templateService.createTemplate(name, description, docType, category, file, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentTemplate> updateTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String category = (String) body.get("category");
        Boolean isActive = body.containsKey("isActive") ? (Boolean) body.get("isActive") : null;
        return ResponseEntity.ok(templateService.updateTemplate(id, name, description, category, isActive));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(Map.of("message", "模板已删除"));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long id) {
        DocumentTemplate template = templateService.getTemplate(id);
        byte[] content = templateService.getTemplateContent(id);
        String filename = template.getName() + "." + getFileExtension(template.getDocType());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.builder("attachment")
                .filename(filename).build());

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private String getFileExtension(String docType) {
        return switch (docType) {
            case "word" -> "docx";
            case "cell" -> "xlsx";
            case "slide" -> "pptx";
            default -> "docx";
        };
    }
}
