package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Department;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.service.DepartmentService;
import com.miaotong.doc.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;

    private void checkAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            throw new BusinessException("需要管理员权限");
        }
    }

    @GetMapping
    public ResponseEntity<List<Department>> getAllDepartments() {
        return ResponseEntity.ok(departmentRepository.findByIsActiveTrue());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<Department>> getDepartmentTree() {
        return ResponseEntity.ok(departmentRepository.findByLevel((short) 1));
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<Department>> getChildren(@PathVariable Long id) {
        return ResponseEntity.ok(departmentRepository.findByParentId(id));
    }

    @PostMapping
    public ResponseEntity<Department> createDepartment(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        Department dept = departmentService.create(
                (String) request.get("code"),
                (String) request.get("name"),
                request.get("parentId") != null ? Long.valueOf(request.get("parentId").toString()) : null,
                request.get("sortOrder") != null ? Integer.valueOf(request.get("sortOrder").toString()) : 0
        );
        return ResponseEntity.ok(dept);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Department> updateDepartment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        Department dept = departmentService.update(
                id,
                (String) request.get("name"),
                (String) request.get("code"),
                request.get("sortOrder") != null ? Integer.valueOf(request.get("sortOrder").toString()) : null
        );
        return ResponseEntity.ok(dept);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateDepartment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        departmentService.deactivate(id);
        return ResponseEntity.ok(Map.of("message", "部门已停用"));
    }
}
