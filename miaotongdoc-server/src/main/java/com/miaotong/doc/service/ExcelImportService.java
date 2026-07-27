package com.miaotong.doc.service;

import com.miaotong.doc.entity.Department;
import com.miaotong.doc.entity.User;
import com.miaotong.doc.repository.DepartmentRepository;
import com.miaotong.doc.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入服务
 *
 * 设计原则：
 *   - 单行失败不影响其他行（逐行 try-catch）
 *   - 严格去重：工号/用户名/邮箱/部门编码均不可重复
 *   - 用户导入：部门编码 → departmentId 转换
 *   - 部门导入：两遍扫描策略处理层级依赖
 *   - 返回详细错误报告（行号 + 错误消息）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    /** 默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExcelImportResult {
        private int totalRows;     // 总行数
        private int successCount;  // 成功条数
        private int failCount;     // 失败条数
        private List<RowError> errors;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RowError {
            private int rowNumber;
            private String message;
        }
    }

    /** 导入用户 */
    @Transactional(noRollbackFor = Exception.class)
    public ExcelImportResult importUsers(MultipartFile file) throws IOException {
        validateFile(file);
        ExcelImportResult result = new ExcelImportResult();
        result.setErrors(new ArrayList<>());

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 读取表头
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("Excel 文件为空或缺少表头行");
            }
            Map<String, Integer> colMap = buildColumnMap(header, formatter);

            // 验证必填列
            requireColumns(colMap, "工号", "用户名", "姓名");

            int rowIdx = 0;
            int dataRowCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                rowIdx = i + 1; // 1-based 行号（含表头）
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row, formatter)) {
                    continue;
                }
                dataRowCount++;

                try {
                    String employeeId = getCell(row, colMap, "工号", formatter);
                    String username = getCell(row, colMap, "用户名", formatter);
                    String realName = getCell(row, colMap, "姓名", formatter);
                    String password = getCell(row, colMap, "密码", formatter);
                    String email = getCell(row, colMap, "邮箱", formatter);
                    String phone = getCell(row, colMap, "手机", formatter);
                    String departmentCode = getCell(row, colMap, "部门编码", formatter);
                    String position = getCell(row, colMap, "职位", formatter);
                    String role = getCell(row, colMap, "角色", formatter);

                    if (employeeId == null || employeeId.isEmpty()) {
                        throw new IllegalArgumentException("工号不能为空");
                    }
                    if (username == null || username.isEmpty()) {
                        throw new IllegalArgumentException("用户名不能为空");
                    }
                    if (realName == null || realName.isEmpty()) {
                        throw new IllegalArgumentException("姓名不能为空");
                    }
                    if (employeeId.length() > 8) {
                        throw new IllegalArgumentException("工号最长 8 位");
                    }
                    if (username.length() > 50) {
                        throw new IllegalArgumentException("用户名最长 50 位");
                    }

                    // 去重校验
                    if (userRepository.existsByEmployeeId(employeeId)) {
                        throw new IllegalArgumentException("工号已存在: " + employeeId);
                    }
                    if (userRepository.existsByUsername(username)) {
                        throw new IllegalArgumentException("用户名已存在: " + username);
                    }
                    if (email != null && !email.isEmpty() && userRepository.existsByEmail(email)) {
                        throw new IllegalArgumentException("邮箱已存在: " + email);
                    }

                    // 部门编码 → departmentId
                    Long departmentId = null;
                    if (departmentCode != null && !departmentCode.isEmpty()) {
                        departmentId = departmentRepository.findByCode(departmentCode)
                                .map(Department::getId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "部门编码不存在: " + departmentCode));
                    }

                    String finalPassword = (password == null || password.isEmpty())
                            ? DEFAULT_PASSWORD : password;

                    User user = new User();
                    user.setEmployeeId(employeeId);
                    user.setUsername(username);
                    user.setPassword(passwordEncoder.encode(finalPassword));
                    user.setRealName(realName);
                    if (email != null && !email.isEmpty()) user.setEmail(email);
                    if (phone != null && !phone.isEmpty()) user.setPhone(phone);
                    user.setDepartmentId(departmentId);
                    if (position != null && !position.isEmpty()) user.setPosition(position);
                    user.setRole(role == null || role.isEmpty() ? "user" : role);
                    user.setIsActive(true);
                    userRepository.save(user);
                } catch (Exception e) {
                    log.warn("Excel 用户导入第 {} 行失败: {}", rowIdx, e.getMessage());
                    result.getErrors().add(new ExcelImportResult.RowError(rowIdx, e.getMessage()));
                }
            }

            result.setTotalRows(dataRowCount);
            result.setSuccessCount(dataRowCount - result.getErrors().size());
            result.setFailCount(result.getErrors().size());
        }
        return result;
    }

    /** 导入部门 */
    @Transactional(noRollbackFor = Exception.class)
    public ExcelImportResult importDepartments(MultipartFile file) throws IOException {
        validateFile(file);
        ExcelImportResult result = new ExcelImportResult();
        result.setErrors(new ArrayList<>());

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("Excel 文件为空或缺少表头行");
            }
            Map<String, Integer> colMap = buildColumnMap(header, formatter);
            requireColumns(colMap, "部门编码", "部门名称");

            // 第一遍：读取所有行，构建内存结构
            List<RowData> rows = new ArrayList<>();
            int dataRowCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row, formatter)) {
                    continue;
                }
                int rowNum = i + 1;
                String code = getCell(row, colMap, "部门编码", formatter);
                String name = getCell(row, colMap, "部门名称", formatter);
                String parentCode = getCell(row, colMap, "上级部门编码", formatter);
                String sortOrderStr = getCell(row, colMap, "排序号", formatter);
                Integer sortOrder = 0;
                if (sortOrderStr != null && !sortOrderStr.isEmpty()) {
                    try { sortOrder = Integer.parseInt(sortOrderStr); } catch (NumberFormatException ignored) {}
                }
                rows.add(new RowData(rowNum, code, name, parentCode, sortOrder));
                dataRowCount++;
            }

            // 第二遍：按层级顺序处理（无 parentCode 的先创建）
            // 分两阶段：第一阶段处理所有根部门，第二阶段处理子部门
            Map<String, Long> codeToId = new HashMap<>(); // 缓存已创建的部门 ID
            // 先收集现有部门的 code → id
            departmentRepository.findAll().forEach(d -> codeToId.put(d.getCode(), d.getId()));

            List<RowData> pending = new ArrayList<>(rows);
            int maxPasses = pending.size() + 1; // 防止无限循环
            int passes = 0;
            while (!pending.isEmpty() && passes < maxPasses) {
                passes++;
                List<RowData> nextPass = new ArrayList<>();
                for (RowData rd : pending) {
                    try {
                        if (rd.code == null || rd.code.isEmpty()) {
                            throw new IllegalArgumentException("部门编码不能为空");
                        }
                        if (rd.name == null || rd.name.isEmpty()) {
                            throw new IllegalArgumentException("部门名称不能为空");
                        }
                        if (rd.code.length() > 20) {
                            throw new IllegalArgumentException("部门编码最长 20 位");
                        }

                        // 去重校验（含本次会话内已创建的）
                        if (codeToId.containsKey(rd.code)) {
                            throw new IllegalArgumentException("部门编码已存在: " + rd.code);
                        }

                        Long parentId = null;
                        if (rd.parentCode != null && !rd.parentCode.isEmpty()) {
                            parentId = codeToId.get(rd.parentCode);
                            if (parentId == null) {
                                // 父级还未处理，留到下一轮
                                nextPass.add(rd);
                                continue;
                            }
                        }

                        Department dept = new Department();
                        dept.setCode(rd.code);
                        dept.setName(rd.name);
                        dept.setSortOrder(rd.sortOrder);

                        if (parentId != null) {
                            Department parent = departmentRepository.findById(parentId).orElseThrow();
                            dept.setParentId(parentId);
                            dept.setLevel((short) (parent.getLevel() + 1));
                            dept.setPath(parent.getPath() + "/" + rd.code);
                        } else {
                            dept.setParentId(null);
                            dept.setLevel((short) 1);
                            dept.setPath("/" + rd.code);
                        }

                        Department saved = departmentRepository.save(dept);
                        codeToId.put(rd.code, saved.getId());
                    } catch (Exception e) {
                        log.warn("Excel 部门导入第 {} 行失败: {}", rd.rowNum, e.getMessage());
                        result.getErrors().add(new ExcelImportResult.RowError(rd.rowNum, e.getMessage()));
                    }
                }
                if (nextPass.size() == pending.size()) {
                    // 没有进展，剩下的都是父级缺失
                    for (RowData rd : nextPass) {
                        result.getErrors().add(new ExcelImportResult.RowError(
                                rd.rowNum, "上级部门编码不存在或在本批次前未创建: " + rd.parentCode));
                    }
                    break;
                }
                pending = nextPass;
            }

            result.setTotalRows(dataRowCount);
            result.setSuccessCount(dataRowCount - result.getErrors().size());
            result.setFailCount(result.getErrors().size());
        }
        return result;
    }

    /** 生成用户导入模板 */
    public byte[] generateUserTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("用户");
            Row header = sheet.createRow(0);
            String[] cols = {"工号*", "用户名*", "姓名*", "密码", "邮箱", "手机", "部门编码", "职位", "角色"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.getCellStyle().setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            }
            // 示例行
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("10001");
            example.createCell(1).setCellValue("zhangsan");
            example.createCell(2).setCellValue("张三");
            example.createCell(3).setCellValue("123456");
            example.createCell(4).setCellValue("zhangsan@example.com");
            example.createCell(5).setCellValue("13800138000");
            example.createCell(6).setCellValue("HR");
            example.createCell(7).setCellValue("员工");
            example.createCell(8).setCellValue("user");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** 生成部门导入模板 */
    public byte[] generateDeptTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("部门");
            Row header = sheet.createRow(0);
            String[] cols = {"部门编码*", "部门名称*", "上级部门编码", "排序号"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.getCellStyle().setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("ROOT");
            example.createCell(1).setCellValue("总行");
            example.createCell(2).setCellValue("");
            example.createCell(3).setCellValue(0);
            Row example2 = sheet.createRow(2);
            example2.createCell(0).setCellValue("HR");
            example2.createCell(1).setCellValue("人力资源部");
            example2.createCell(2).setCellValue("ROOT");
            example2.createCell(3).setCellValue(0);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".XLSX"))) {
            throw new IllegalArgumentException("仅支持 .xlsx 格式文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
    }

    private Map<String, Integer> buildColumnMap(Row header, DataFormatter formatter) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell == null) continue;
            String name = formatter.formatCellValue(cell).trim();
            // 去掉 * 标记
            if (name.endsWith("*")) name = name.substring(0, name.length() - 1);
            if (!name.isEmpty()) map.put(name, i);
        }
        return map;
    }

    private void requireColumns(Map<String, Integer> colMap, String... required) {
        List<String> missing = new ArrayList<>();
        for (String col : required) {
            if (!colMap.containsKey(col)) missing.add(col);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Excel 缺少必填列: " + String.join(", ", missing));
        }
    }

    private String getCell(Row row, Map<String, Integer> colMap, String name, DataFormatter formatter) {
        Integer idx = colMap.get(name);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 临时行数据结构 */
    private record RowData(int rowNum, String code, String name, String parentCode, Integer sortOrder) {}
}