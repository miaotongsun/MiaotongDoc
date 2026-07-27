package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Department;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.DepartmentService;
import com.miaotong.doc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 对外 API Controller（v1）
 *
 * 鉴权由 OpenApiAuthFilter 在 Spring Security 之前完成
 * 鉴权失败直接返回 401/403/429，不会进入此 Controller
 *
 * 端点：
 *   POST /api/open/v1/users         创建用户
 *   POST /api/open/v1/departments   创建部门
 *   GET  /api/open/v1/health        健康检查
 */
@Slf4j
@RestController
@RequestMapping("/api/open/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;

    /** 健康检查（供外部系统探活） */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("version", "v1");
        resp.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(resp);
    }

    /** 创建用户 */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        // 必填参数校验
        String employeeId = (String) body.get("employeeId");
        String username = (String) body.get("username");
        String realName = (String) body.get("realName");

        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new BusinessException("工号不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (realName == null || realName.trim().isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        if (employeeId.length() > 8) {
            throw new BusinessException("工号最长 8 位");
        }
        if (username.length() > 50) {
            throw new BusinessException("用户名最长 50 位");
        }

        String password = (String) body.getOrDefault("password", "123456");
        String email = (String) body.get("email");
        String phone = (String) body.get("phone");
        String position = (String) body.get("position");
        String role = (String) body.getOrDefault("role", "user");
        String departmentCode = (String) body.get("departmentCode");

        // 部门编码 → departmentId
        Long departmentId = null;
        if (departmentCode != null && !departmentCode.trim().isEmpty()) {
            departmentId = departmentRepository.findByCode(departmentCode)
                    .map(Department::getId)
                    .orElseThrow(() -> new BusinessException("部门编码不存在: " + departmentCode));
        }

        User user = userService.adminCreateUser(
                employeeId, username, password, realName,
                email, phone, departmentId, position, role);

        log.info("对外 API 创建用户: id={}, employeeId={}, username={}",
                user.getId(), user.getEmployeeId(), user.getUsername());

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", user.getId());
        resp.put("employeeId", user.getEmployeeId());
        resp.put("username", user.getUsername());
        resp.put("realName", user.getRealName());
        resp.put("email", user.getEmail());
        resp.put("phone", user.getPhone());
        resp.put("departmentId", user.getDepartmentId());
        resp.put("position", user.getPosition());
        resp.put("role", user.getRole());
        resp.put("isActive", user.getIsActive());
        return ResponseEntity.ok(resp);
    }

    /** 创建部门 */
    @PostMapping("/departments")
    public ResponseEntity<Map<String, Object>> createDepartment(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");

        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("部门编码不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("部门名称不能为空");
        }
        if (code.length() > 20) {
            throw new BusinessException("部门编码最长 20 位");
        }
        if (name.length() > 200) {
            throw new BusinessException("部门名称最长 200 位");
        }

        String parentCode = (String) body.get("parentCode");
        Integer sortOrder = body.get("sortOrder") != null
                ? Integer.valueOf(body.get("sortOrder").toString()) : 0;

        // 上级部门编码 → parentId
        Long parentId = null;
        if (parentCode != null && !parentCode.trim().isEmpty()) {
            parentId = departmentRepository.findByCode(parentCode)
                    .map(Department::getId)
                    .orElseThrow(() -> new BusinessException("上级部门编码不存在: " + parentCode));
        }

        Department dept = departmentService.create(code, name, parentId, sortOrder);

        log.info("对外 API 创建部门: id={}, code={}, name={}, parentId={}",
                dept.getId(), dept.getCode(), dept.getName(), dept.getParentId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", dept.getId());
        resp.put("code", dept.getCode());
        resp.put("name", dept.getName());
        resp.put("parentId", dept.getParentId());
        resp.put("level", dept.getLevel());
        resp.put("path", dept.getPath());
        resp.put("sortOrder", dept.getSortOrder());
        resp.put("isActive", dept.getIsActive());
        return ResponseEntity.ok(resp);
    }
}