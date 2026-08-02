package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Department;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.repository.OpenApiKeyRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.DepartmentService;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.UserService;
import com.miaotong.doc.service.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外 API Controller（v1）
 *
 * 鉴权由 OpenApiAuthFilter 在 Spring Security 之前完成
 * 鉴权失败直接返回 401/403/429，不会进入此 Controller
 *
 * 端点：
 *   GET  /api/open/v1/health              健康检查
 *   POST /api/open/v1/users               创建用户
 *   POST /api/open/v1/departments         创建部门
 *   GET  /api/open/v1/documents           文档列表（分页）
 *   GET  /api/open/v1/documents/{id}      文档详情
 *   GET  /api/open/v1/documents/{id}/file 下载文档
 *   POST /api/open/v1/documents/upload    上传文档
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
    private final DocumentService documentService;
    private final StorageService storageService;
    private final OpenApiKeyRepository openApiKeyRepository;

    /** 从请求中获取 API Key 对应的操作用户 ID */
    private Long getApiUserId(HttpServletRequest request) {
        Long keyId = (Long) request.getAttribute("openApiKeyId");
        if (keyId == null) {
            var key = (com.miaotong.doc.entity.OpenApiKey) request.getAttribute("openApiKey");
            if (key != null) {
                request.setAttribute("openApiKeyId", key.getId());
                return key.getCreatedBy();
            }
            return 1L;
        }
        return openApiKeyRepository.findById(keyId)
                .map(com.miaotong.doc.entity.OpenApiKey::getCreatedBy)
                .orElse(1L);
    }

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

    // ========== 文档相关 ==========

    /** 文档列表（分页查询） */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = getApiUserId(request);
        Sort sort = Sort.by("updatedAt").descending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Document> documents = documentService.listDocuments(type, keyword, null, userId, null, null, "admin", pageRequest);
        Map<String, Object> resp = new HashMap<>();
        resp.put("content", documents.getContent().stream().map(this::toDocBrief).toList());
        resp.put("totalElements", documents.getTotalElements());
        resp.put("totalPages", documents.getTotalPages());
        resp.put("number", documents.getNumber());
        resp.put("size", documents.getSize());
        return ResponseEntity.ok(resp);
    }

    /** 文档详情 */
    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        return ResponseEntity.ok(toDocDetail(doc));
    }

    /** 下载文档文件 */
    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        try {
            byte[] content = storageService.load(doc.getFilePath());
            ByteArrayResource resource = new ByteArrayResource(content);
            String filename = doc.getTitle() + "." + doc.getFileType();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    /** 上传文档（multipart/form-data） */
    @PostMapping("/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = getApiUserId(request);
        Document doc = documentService.uploadDocument(file, userId);
        return ResponseEntity.ok(toDocDetail(doc));
    }

    /** 读取 xlsx 文档的结构化数据（按行列输出单元格内容） */
    @GetMapping("/documents/{id}/sheet-data")
    public ResponseEntity<Map<String, Object>> getSheetData(
            @PathVariable Long id,
            @RequestParam(required = false) String sheet) {
        Document doc = documentService.getDocument(id);
        if (!"cell".equals(doc.getDocType())) {
            throw new BusinessException("仅支持 Excel 文档（docType=cell）");
        }
        try {
            byte[] content = storageService.load(doc.getFilePath());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("documentId", doc.getId());
            result.put("title", doc.getTitle());
            result.put("fileType", doc.getFileType());

            List<Map<String, Object>> sheets = new ArrayList<>();
            try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                    new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                        new java.io.ByteArrayInputStream(content))) {
                org.apache.poi.ss.usermodel.DataFormatter formatter =
                        new org.apache.poi.ss.usermodel.DataFormatter();

                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    org.apache.poi.ss.usermodel.Sheet poiSheet = workbook.getSheetAt(i);
                    String sheetName = poiSheet.getSheetName();

                    // 如果指定了 sheet 参数，跳过不匹配的工作表
                    if (sheet != null && !sheet.trim().isEmpty()) {
                        boolean match = false;
                        // 按名称匹配（忽略大小写）
                        if (sheet.equalsIgnoreCase(sheetName)) {
                            match = true;
                        }
                        // 按索引匹配（0-based）
                        try {
                            int idx = Integer.parseInt(sheet);
                            if (idx == i) match = true;
                        } catch (NumberFormatException ignored) {}
                        if (!match) continue;
                    }

                    Map<String, Object> sheetData = new LinkedHashMap<>();
                    sheetData.put("name", sheetName);
                    sheetData.put("index", i);
                    sheetData.put("rowCount", poiSheet.getPhysicalNumberOfRows());
                    sheetData.put("columnCount", poiSheet.getRow(0) != null
                            ? poiSheet.getRow(0).getPhysicalNumberOfCells() : 0);

                    // 读取表头行（第一行）
                    org.apache.poi.ss.usermodel.Row headerRow = poiSheet.getRow(0);
                    List<String> headers = new ArrayList<>();
                    if (headerRow != null) {
                        for (int c = 0; c < headerRow.getPhysicalNumberOfCells(); c++) {
                            headers.add(formatter.formatCellValue(headerRow.getCell(c)));
                        }
                    }
                    sheetData.put("headers", headers);

                    // 读取数据行（从第二行开始，最多返回 1000 行）
                    List<Map<String, Object>> rows = new ArrayList<>();
                    int maxRows = Math.min(poiSheet.getLastRowNum() + 1, 1001);
                    for (int r = 1; r < maxRows; r++) {
                        org.apache.poi.ss.usermodel.Row row = poiSheet.getRow(r);
                        if (row == null) continue;
                        Map<String, Object> rowData = new LinkedHashMap<>();
                        rowData.put("rowNum", r + 1);
                        List<String> cells = new ArrayList<>();
                        for (int c = 0; c < headers.size(); c++) {
                            cells.add(formatter.formatCellValue(row.getCell(c)));
                        }
                        rowData.put("cells", cells);
                        rows.add(rowData);
                    }
                    sheetData.put("rows", rows);
                    sheets.add(sheetData);
                }
            }
            result.put("sheets", sheets);
            result.put("totalSheets", sheets.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            if (e instanceof org.apache.poi.openxml4j.exceptions.InvalidFormatException
                    || e.getCause() instanceof org.apache.poi.openxml4j.exceptions.InvalidFormatException) {
                throw new BusinessException("文件不是有效的 Excel 格式");
            }
            throw new BusinessException("读取表格数据失败: " + e.getMessage());
        }
    }

    /** 文档摘要（列表用） */
    private Map<String, Object> toDocBrief(Document doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("docType", doc.getDocType());
        m.put("fileType", doc.getFileType());
        m.put("fileSize", doc.getFileSize());
        m.put("status", doc.getStatus());
        m.put("isStarred", doc.getIsStarred());
        m.put("createdAt", doc.getCreatedAt());
        m.put("updatedAt", doc.getUpdatedAt());
        m.put("ownerUserId", doc.getOwnerUserId());
        return m;
    }

    /** 文档详情 */
    private Map<String, Object> toDocDetail(Document doc) {
        Map<String, Object> m = toDocBrief(doc);
        m.put("docKey", doc.getDocKey());
        m.put("fileHash", doc.getFileHash());
        m.put("currentVersion", doc.getCurrentVersion());
        m.put("folderId", doc.getFolderId());
        m.put("departmentId", doc.getDepartmentId());
        m.put("signingLocked", doc.getSigningLocked());
        return m;
    }
}