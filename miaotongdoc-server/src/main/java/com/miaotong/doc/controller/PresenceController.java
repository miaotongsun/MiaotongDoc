package com.miaotong.doc.controller;

import com.miaotong.doc.dto.PresenceInfo;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.PresenceService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @GetMapping("/document/{docId}")
    public ResponseEntity<List<PresenceInfo>> getOnlineUsers(@PathVariable Long docId) {
        return ResponseEntity.ok(presenceService.getOnlineUsers(docId));
    }

    @PostMapping("/document/{docId}/join")
    public ResponseEntity<Map<String, String>> join(
            @PathVariable Long docId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String userName = resolveUserName(userId);
        presenceService.joinDocument(docId, userId, userName);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("/document/{docId}/leave")
    public ResponseEntity<Map<String, String>> leave(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        presenceService.leaveDocument(docId, userId);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("/document/{docId}/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        presenceService.heartbeat(docId, userId);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getRealName)
                .filter(name -> name != null && !name.isEmpty())
                .orElse("用户" + userId);
    }
}
