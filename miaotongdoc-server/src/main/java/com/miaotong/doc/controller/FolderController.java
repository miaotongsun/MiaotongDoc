package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Folder;
import com.miaotong.doc.service.FolderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<List<Folder>> getFolders(
            @RequestParam(required = false) Long parentId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (parentId != null) {
            return ResponseEntity.ok(folderService.getChildFolders(parentId));
        }
        return ResponseEntity.ok(folderService.getUserFolders(userId));
    }

    @PostMapping
    public ResponseEntity<Folder> createFolder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        Long departmentId = body.get("departmentId") != null ? ((Number) body.get("departmentId")).longValue() : null;
        String color = (String) body.get("color");
        return ResponseEntity.ok(folderService.createFolder(name, parentId, userId, departmentId, color));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Folder> updateFolder(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String color = body.get("color") != null ? body.get("color").toString() : null;
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        // 区分：body 中没有 parentId 字段时不移动，有 parentId 字段时才移动（含设为 null 移到根目录）
        boolean hasParentId = body.containsKey("parentId");
        Long moveTarget = hasParentId ? parentId : FolderService.NO_MOVE;
        return ResponseEntity.ok(folderService.updateFolder(id, name, color, moveTarget));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFolder(
            @PathVariable Long id,
            @RequestParam(required = false) Long moveToParentId) {
        folderService.deleteFolder(id, moveToParentId);
        return ResponseEntity.ok(Map.of("message", "文件夹已删除"));
    }

    /** 批量更新文件夹排序 */
    @PutMapping("/reorder")
    public ResponseEntity<Map<String, String>> reorderFolders(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.get(i).longValue();
                folderService.updateSortOrder(id, i);
            }
        }
        return ResponseEntity.ok(Map.of("message", "排序已更新"));
    }

    // 下载文件夹内所有文档（ZIP打包）
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFolder(@PathVariable Long id) {
        try {
            byte[] zipContent = folderService.downloadFolderAsZip(id);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/zip"));
            headers.setContentDisposition(org.springframework.http.ContentDisposition.builder("attachment")
                    .filename("folder_" + id + ".zip").build());
            return new ResponseEntity<>(zipContent, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
