package com.miaotong.doc.controller;

import com.miaotong.doc.entity.FolderTemplate;
import com.miaotong.doc.repository.FolderTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folder-templates")
@RequiredArgsConstructor
public class FolderTemplateController {

    private final FolderTemplateRepository folderTemplateRepository;

    @GetMapping
    public ResponseEntity<List<FolderTemplate>> getTemplates() {
        return ResponseEntity.ok(folderTemplateRepository.findAllByOrderByIdDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderTemplate> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(folderTemplateRepository.findById(id).orElse(null));
    }

    @PostMapping
    public ResponseEntity<FolderTemplate> createTemplate(@RequestBody Map<String, Object> body) {
        FolderTemplate tpl = new FolderTemplate();
        tpl.setName((String) body.get("name"));
        tpl.setDescription((String) body.get("description"));
        tpl.setStructure(body.get("structure") != null ? body.get("structure").toString() : "[]");
        tpl.setIsActive(body.get("isActive") != null ? (Boolean) body.get("isActive") : true);
        return ResponseEntity.ok(folderTemplateRepository.save(tpl));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderTemplate> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FolderTemplate tpl = folderTemplateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();
        if (body.containsKey("name")) tpl.setName((String) body.get("name"));
        if (body.containsKey("description")) tpl.setDescription((String) body.get("description"));
        if (body.containsKey("structure")) tpl.setStructure(body.get("structure").toString());
        if (body.containsKey("isActive")) tpl.setIsActive((Boolean) body.get("isActive"));
        return ResponseEntity.ok(folderTemplateRepository.save(tpl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTemplate(@PathVariable Long id) {
        folderTemplateRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "模板已删除"));
    }
}
