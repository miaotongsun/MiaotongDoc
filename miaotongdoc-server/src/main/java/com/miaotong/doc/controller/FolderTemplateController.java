package com.miaotong.doc.controller;

import com.miaotong.doc.entity.FolderTemplate;
import com.miaotong.doc.repository.FolderTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folder-templates")
@RequiredArgsConstructor
public class FolderTemplateController {

    private final FolderTemplateRepository folderTemplateRepository;

    @GetMapping
    public ResponseEntity<List<FolderTemplate>> getTemplates() {
        return ResponseEntity.ok(folderTemplateRepository.findByIsActiveTrue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderTemplate> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(folderTemplateRepository.findById(id).orElse(null));
    }
}
