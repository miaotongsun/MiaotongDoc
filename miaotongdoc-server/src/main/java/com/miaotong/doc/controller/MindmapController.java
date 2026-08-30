package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.ShareService;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 思维导图文档 REST 接口（2026-08-16 新增）
 *
 * 数据格式：MindElixir v5 原生 JSON 树，存 mt_document.filePath（.mm 后缀）。
 * 复用 MarkdownController 的鉴权 + 权限检查 + Hash 跳过无变化保存 的模式。
 *
 * 端点：
 * - GET  /api/mindmap/{id}/content  获取 JSON 内容（view 权限）
 * - POST /api/mindmap/{id}/save     保存 JSON 内容（edit 权限）
 *
 * 协同（Yjs 节点级）走 /ws/yjs/ 房间 mm-{docKey}，与 Markdown/PDF 协同不冲突。
 * AI 4 个能力（生成/扩写/总结/图标）复用 /api/ai/chat/stream，前端 MindmapAiService 封装。
 */
@Slf4j
@RestController
@RequestMapping("/api/mindmap")
@RequiredArgsConstructor
public class MindmapController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final ShareService shareService;

    private static final Map<String, Integer> PERM_LEVEL = Map.of(
            "view", 1, "comment", 2, "edit", 3, "admin", 4
    );

    /** 默认思维导图 JSON（空导图，仅根节点） */
    private static final String DEFAULT_MINDMAP_JSON =
            "{\"nodeData\":{\"id\":\"root\",\"topic\":\"中心主题\",\"children\":[]}}";

    /**
     * 获取思维导图内容（JSON 树）
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Map<String, String>> getContent(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Document doc = documentService.getDocument(id);

        if (!"mindmap".equals(doc.getDocType())) {
            throw new BusinessException("该文档不是思维导图类型");
        }

        // 检查 view 权限
        String perm = shareService.getUserPermission(id, userId, role);
        if (perm == null) {
            throw new BusinessException("无权访问此文档");
        }

        try {
            byte[] bytes = storageService.load(doc.getFilePath());
            String content = bytes.length == 0 ? DEFAULT_MINDMAP_JSON : new String(bytes, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (Exception e) {
            log.warn("读取思维导图内容失败,返回默认空导图: docId={}", id, e);
            return ResponseEntity.ok(Map.of("content", DEFAULT_MINDMAP_JSON));
        }
    }

    /**
     * 保存思维导图内容（JSON 树）
     */
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> saveContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        Document doc = documentService.getDocument(id);

        if (!"mindmap".equals(doc.getDocType())) {
            throw new BusinessException("该文档不是思维导图类型");
        }

        // 检查 edit 权限（管理员未授权时只有 view 权限,不允许编辑）
        String perm = shareService.getUserPermission(id, userId, role);
        if (perm == null) {
            throw new BusinessException("无权访问此文档");
        }
        int userLevel = PERM_LEVEL.getOrDefault(perm, 0);
        int required = PERM_LEVEL.getOrDefault("edit", 0);
        if (userLevel < required) {
            throw new BusinessException("权限不足,需要编辑权限");
        }

        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }

        // 轻量校验：必须是合法 JSON 且包含 nodeData 字段（防御恶意客户端）
        if (!isValidMindmapJson(content)) {
            throw new BusinessException("思维导图 JSON 格式不合法");
        }

        byte[] newBytes = content.getBytes(StandardCharsets.UTF_8);
        String newHash = FileHashUtil.calculateSHA256(newBytes);

        // 如果内容没有变化,跳过保存
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

        log.debug("思维导图已保存: docId={}, size={}", id, newBytes.length);
        return ResponseEntity.ok(Map.of(
            "message", "保存成功",
            "saved", true,
            "size", newBytes.length
        ));
    }

    /**
     * 轻量 JSON 校验：必须是合法 JSON 且包含 nodeData 字段
     */
    private boolean isValidMindmapJson(String content) {
        if (content == null || content.isBlank()) return false;
        try {
            // 用 Jackson 解析 + 检查必要字段
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(content);
            if (!root.has("nodeData")) return false;
            com.fasterxml.jackson.databind.JsonNode nodeData = root.get("nodeData");
            // root 节点必须有 id 和 topic
            return nodeData.has("id") && nodeData.has("topic");
        } catch (Exception e) {
            log.debug("思维导图 JSON 校验失败: {}", e.getMessage());
            return false;
        }
    }
}