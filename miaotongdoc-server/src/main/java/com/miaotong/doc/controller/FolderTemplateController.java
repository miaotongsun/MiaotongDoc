package com.miaotong.doc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<FolderTemplate>> getTemplates() {
        return ResponseEntity.ok(folderTemplateRepository.findAllByOrderByIdDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderTemplate> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(folderTemplateRepository.findById(id).orElse(null));
    }

    @PostMapping
    public ResponseEntity<FolderTemplate> createTemplate(@RequestBody Map<String, Object> body) throws Exception {
        FolderTemplate tpl = new FolderTemplate();
        tpl.setName((String) body.get("name"));
        tpl.setDescription((String) body.get("description"));
        // structure 必须序列化为合法 JSON 字符串存入 JSONB 列
        Object structure = body.get("structure");
        tpl.setStructure(structure != null ? objectMapper.writeValueAsString(structure) : "[]");
        tpl.setIsActive(body.get("isActive") != null ? (Boolean) body.get("isActive") : true);
        return ResponseEntity.ok(folderTemplateRepository.save(tpl));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderTemplate> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) throws Exception {
        FolderTemplate tpl = folderTemplateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();
        if (body.containsKey("name")) tpl.setName((String) body.get("name"));
        if (body.containsKey("description")) tpl.setDescription((String) body.get("description"));
        if (body.containsKey("structure")) {
            Object structure = body.get("structure");
            tpl.setStructure(structure != null ? objectMapper.writeValueAsString(structure) : "[]");
        }
        if (body.containsKey("isActive")) tpl.setIsActive((Boolean) body.get("isActive"));
        return ResponseEntity.ok(folderTemplateRepository.save(tpl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTemplate(@PathVariable Long id) {
        folderTemplateRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "模板已删除"));
    }
}
