package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/markdown")
@RequiredArgsConstructor
public class MarkdownController {

    private final DocumentService documentService;
    private final StorageService storageService;

    /**
     * 获取 Markdown 文档内容
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Map<String, String>> getContent(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.getDocument(id);

        if (!"markdown".equals(doc.getDocType())) {
            throw new BusinessException("该文档不是 Markdown 类型");
        }

        try {
            byte[] bytes = storageService.load(doc.getFilePath());
            String content = new String(bytes, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (Exception e) {
            log.error("读取 Markdown 内容失败: docId={}", id, e);
            return ResponseEntity.ok(Map.of("content", ""));
        }
    }

    /**
     * 保存 Markdown 文档内容
     */
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> saveContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Document doc = documentService.getDocument(id);

        if (!"markdown".equals(doc.getDocType())) {
            throw new BusinessException("该文档不是 Markdown 类型");
        }

        String content = body.get("content");
        if (content == null) {
            throw new BusinessException("内容不能为空");
        }

        byte[] newBytes = content.getBytes(StandardCharsets.UTF_8);
        String newHash = FileHashUtil.calculateSHA256(newBytes);

        // 如果内容没有变化，跳过保存
        if (newHash.equals(doc.getFileHash())) {
            return ResponseEntity.ok(Map.of(
                "message", "内容无变化",
                "saved", false
            ));
        }

        // 保存文件
        storageService.store(doc.getFilePath(), newBytes);

        // 更新文档元数据
        doc.setFileHash(newHash);
        doc.setFileSize((long) newBytes.length);
        doc.setUpdatedBy(userId);
        documentService.updateDocument(doc);

        log.debug("Markdown 文档已保存: docId={}, size={}", id, newBytes.length);
        return ResponseEntity.ok(Map.of(
            "message", "保存成功",
            "saved", true,
            "size", newBytes.length
        ));
    }
}
