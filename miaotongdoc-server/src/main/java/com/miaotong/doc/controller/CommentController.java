package com.miaotong.doc.controller;

import com.miaotong.doc.dto.CreateCommentRequest;
import com.miaotong.doc.dto.CommentDTO;
import com.miaotong.doc.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentDTO> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        CommentDTO comment = commentService.createComment(request, userId);
        return ResponseEntity.ok(comment);
    }

    @GetMapping("/document/{docId}")
    public ResponseEntity<List<CommentDTO>> getDocumentComments(@PathVariable Long docId) {
        return ResponseEntity.ok(commentService.getDocumentComments(docId));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolveComment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        commentService.resolveComment(id, userId);
        return ResponseEntity.ok(Map.of("message", "已解决"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteComment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        commentService.deleteComment(id, userId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }
}
