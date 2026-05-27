package com.miaotong.doc.controller;

import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.UserService;
import com.miaotong.doc.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final UserService userService;

    private void checkAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            throw new BusinessException("需要管理员权限");
        }
    }

    @GetMapping("/users")
    public ResponseEntity<Page<User>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return ResponseEntity.ok(userRepository.findAll(PageRequest.of(page, size)));
    }

    @GetMapping("/users/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam String keyword,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        return ResponseEntity.ok(userRepository.searchByKeyword(keyword));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, String>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        User user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        user.setRole(request.get("role"));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "角色更新成功"));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        User user = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "状态更新成功"));
    }

    @PostMapping("/users")
    public ResponseEntity<User> createUser(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        User user = userService.adminCreateUser(
                (String) request.get("employeeId"),
                (String) request.get("username"),
                (String) request.get("password"),
                (String) request.get("realName"),
                (String) request.get("email"),
                (String) request.get("phone"),
                request.get("departmentId") != null ? Long.valueOf(request.get("departmentId").toString()) : null,
                (String) request.get("position"),
                (String) request.getOrDefault("role", "user")
        );
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        User user = userService.adminUpdateUser(
                id,
                (String) request.get("realName"),
                (String) request.get("email"),
                (String) request.get("phone"),
                request.get("departmentId") != null ? Long.valueOf(request.get("departmentId").toString()) : null,
                (String) request.get("position")
        );
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        userService.adminResetPassword(id, "123456");
        return ResponseEntity.ok(Map.of("message", "密码已重置为 123456"));
    }
}
