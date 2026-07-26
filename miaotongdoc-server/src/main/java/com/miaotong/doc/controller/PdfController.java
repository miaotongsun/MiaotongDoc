package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.*;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final PdfToolService pdfToolService;
    private final PdfCompareService pdfCompareService;

    private final DoclingService doclingService;
    private final PdfRecognizeService pdfRecognizeService;
    private final com.miaotong.doc.mq.PdfOcrTaskProducer pdfOcrTaskProducer;
    private final com.miaotong.doc.repository.PdfTaskRepository pdfTaskRepository;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Phase 26 fix:从 request 中以 UTF-8 读取 JSON body 并转 Map。
     * Spring/Jackson 在某些环境下会用 ISO-8859-1 解析 request body,导致中文 body 被误解码抛错。
     * 这是 Phase 13.12 createBlank 已用的模式,这里提取成 helper 供其它中文接口复用。
     *
     * 关键:先 byte→String(UTF-8)再 readValue(String),不要直接 readValue(InputStream)
     * —— 后者 Jackson 会再次尝试按 UTF-8 解码 byte[],如果某些 byte 不符合 UTF-8 规则就抛
     * "Invalid UTF-8 start byte 0xXX"。
     */
    private Map<String, Object> readUtf8Body(HttpServletRequest request) throws java.io.IOException {
        String bodyStr = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (bodyStr.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(bodyStr, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 中文 UTF-8 byte 解析失败时,尝试 ISO-8859-1 fallback(可能是 Spring 已预先解码)
            String latin1 = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!latin1.equals(bodyStr)) {
                log.warn("JSON UTF-8 解析失败,尝试 ISO-8859-1 fallback: {}", e.getMessage());
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(latin1, Map.class);
            }
            throw e;
        }
    }

    // ==================== 文本提取（已有） ====================

    /**
     * 提取 PDF 全文文本（按页分段）
     *
     * Phase 26 fix:对返回文本做乱码清理
     * - 检测到 surrogate pair(高代理 + 低代理)区域字符(常见于 PDFBox 对嵌入子集字体无 ToUnicode 时)
     * - 把孤立的 surrogate pair 替换为 U+FFFD ('�'),避免前端拿到不可显示字符
     */
    @GetMapping("/{id}/text")
    public ResponseEntity<Map<String, Object>> extractText(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = pdf.getNumberOfPages();

                List<Map<String, Object>> pages = new ArrayList<>();
                StringBuilder fullText = new StringBuilder();

                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(pdf);
                    String clean = sanitizeText(pageText);
                    fullText.append(clean);
                    pages.add(Map.of("pageNum", i, "text", clean));
                }

                return ResponseEntity.ok(Map.of(
                    "totalPages", totalPages,
                    "fullText", fullText.toString(),
                    "pages", pages
                ));
            }
        } catch (Exception e) {
            log.error("提取 PDF 文本失败: docId={}", id, e);
            throw new BusinessException("提取 PDF 文本失败");
        }
    }

    /**
     * 清理 PDFBox 抽出的乱码字符。
     *
     * 实际场景:reportlab 用嵌入子集字体生成中文 PDF 时,PDFBox 因缺 ToUnicode CMap 抽出
     * 形如 0xDxxx 0xDxxx 0xDxxx 的"假字符"(高代理 + 高代理),这些是 mojibake,
     * 应当整段替换为 U+FFFD。
     *
     * 处理策略:
     * - 检测到任何 high/low surrogate 字符(单独或成对) → 整段替换为 U+FFFD
     * - 控制字符(0x00-0x08, 0x0B-0x1F, 0x7F 除 tab/newline/cr) → 替换为空格
     * - 合法字符(ASCII 可见字符 + 中日韩正常范围 + 表情符号等) → 保留
     */
    private String sanitizeText(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                // 任何 surrogate(合法或非法的 mojibake)在此场景下都是不可显示,
                // 都替换为 U+FFFD,后续 high/low 也跳过
                sb.append('�');
                if (Character.isHighSurrogate(c) && i + 1 < chars.length
                    && Character.isLowSurrogate(chars[i + 1])) {
                    i++; // 跳过配对的 low surrogate
                }
                continue;
            }
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                sb.append(' ');
            } else if (c == 0x7F) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 提取 PDF 指定页文本
     */
    @GetMapping("/{id}/pages/{pageNum}/text")
    public ResponseEntity<Map<String, String>> extractPageText(
            @PathVariable Long id,
            @PathVariable int pageNum,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) {
                    throw new BusinessException("页码超出范围");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                return ResponseEntity.ok(Map.of("text", stripper.getText(pdf)));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提取 PDF 页面文本失败: docId={}, page={}", id, pageNum, e);
            throw new BusinessException("提取文本失败");
        }
    }

    /**
     * 获取 PDF 信息
     */
    @GetMapping("/{id}/info")
    public ResponseEntity<Map<String, Object>> getPdfInfo(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                return ResponseEntity.ok(Map.of(
                    "totalPages", pdf.getNumberOfPages(),
                    "title", doc.getTitle(),
                    "fileSize", doc.getFileSize()
                ));
            }
        } catch (Exception e) {
            log.error("获取 PDF 信息失败: docId={}", id, e);
            throw new BusinessException("获取 PDF 信息失败");
        }
    }

    // ==================== 格式转换 ====================

    /**
     * PDF 格式转换
     */
    @PostMapping("/{id}/convert")
    public ResponseEntity<byte[]> convert(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String targetFormat = body.get("targetFormat");
        if (targetFormat == null || targetFormat.isBlank()) {
            throw new BusinessException("目标格式不能为空");
        }

        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        byte[] result;
        String filename;
        MediaType mediaType;

        switch (targetFormat) {
            case "png" -> {
                result = pdfToolService.renderPageToImage(id, 1);
                filename = doc.getTitle() + ".png";
                mediaType = MediaType.IMAGE_PNG;
            }
            case "txt" -> {
                String text = doclingService.parse(id);
                result = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                filename = doc.getTitle() + ".txt";
                mediaType = MediaType.TEXT_PLAIN;
            }
            case "md" -> {
                String md = doclingService.parse(id);
                result = md.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                filename = doc.getTitle() + ".md";
                mediaType = MediaType.TEXT_PLAIN;
            }
            case "docx" -> {
                String content = doclingService.parse(id);
                result = pdfToolService.createDocxFromText(content);
                filename = doc.getTitle() + ".docx";
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }
            default -> throw new BusinessException("不支持的目标格式: " + targetFormat);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    // ==================== 页面操作 ====================

    /**
     * 合并多个 PDF(Phase 3:原子化覆盖当前文档)
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> merge(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> docIds = (List<Number>) body.get("documentIds");
        if (docIds == null || docIds.size() < 2) {
            throw new BusinessException("至少需要两个文档");
        }

        List<Long> ids = docIds.stream().map(Number::longValue).toList();
        // 第一个文档为"目标文档"(操作后 filePath 指向新 PDF)
        Long targetDocId = ids.get(0);
        byte[] result = pdfToolService.merge(ids);
        String newFilePath = pdfToolService.replacePdfBytes(targetDocId, result, "merge");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已合并 " + ids.size() + " 个 PDF",
            "filePath", newFilePath,
            "targetDocId", targetDocId
        ));
    }

    /**
     * Phase 13.29: 提取页面到新文档(非破坏性,生成新 Document)
     * POST /api/pdf/{id}/pages/extract-to-new
     * body: { pages: [1,2], title?: "新文档标题" }
     * 返回: { success, docId, message }
     */
    @PostMapping("/{id}/pages/extract-to-new")
    public ResponseEntity<Map<String, Object>> extractPagesToNew(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        try {
            @SuppressWarnings("unchecked")
            List<Number> pagesNum = (List<Number>) body.get("pages");
            if (pagesNum == null || pagesNum.isEmpty()) {
                throw new BusinessException("请选择至少 1 页");
            }
            List<Integer> pages = pagesNum.stream().map(Number::intValue).toList();
            String newTitle = (String) body.get("title");
            Long userId = getCurrentUserId();
            Long newDocId = pdfToolService.extractPagesToNew(id, pages, newTitle, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "docId", newDocId,
                "message", "已提取 " + pages.size() + " 页到新文档"
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提取页面到新文档失败: docId={}", id, e);
            throw new BusinessException("提取到新文档失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.29: 高级合并(页区间 + 目标 new/overwrite)
     * POST /api/pdf/merge-advanced
     * body: { documents: [{docId, pageRanges}], target: {mode: 'new'|'overwrite', docId?, title?} }
     */
    @PostMapping("/merge-advanced")
    public ResponseEntity<Map<String, Object>> mergeAdvanced(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> documents = (List<Map<String, Object>>) body.get("documents");
            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) body.get("target");
            if (documents == null || documents.isEmpty()) {
                throw new BusinessException("至少需要 1 个文档");
            }
            String mode = target != null && "overwrite".equals(target.get("mode")) ? "overwrite" : "new";
            Long targetDocId = target != null && target.get("docId") != null
                ? ((Number) target.get("docId")).longValue() : null;
            String newTitle = target != null ? (String) target.get("title") : null;
            Long userId = getCurrentUserId();
            Map<String, Object> result = pdfToolService.mergeAdvanced(documents, mode, targetDocId, newTitle, userId);
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("高级合并失败", e);
            throw new BusinessException("高级合并失败: " + e.getMessage());
        }
    }

    /**
     * Phase 14.U6: 文档对比 —— 逐页文本 LCS diff
     * POST /api/pdf/compare { docIdA, docIdB }
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(@RequestBody Map<String, Object> body) {
        try {
            Object aObj = body.get("docIdA");
            Object bObj = body.get("docIdB");
            if (!(aObj instanceof Number) || !(bObj instanceof Number)) {
                throw new BusinessException("docIdA / docIdB 必填");
            }
            Long docIdA = ((Number) aObj).longValue();
            Long docIdB = ((Number) bObj).longValue();
            return ResponseEntity.ok(pdfCompareService.compare(docIdA, docIdB));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档对比失败", e);
            throw new BusinessException("文档对比失败: " + e.getMessage());
        }
    }

    /**
     * 拆分 PDF
     */
    @PostMapping("/{id}/split")
    public ResponseEntity<Map<String, Object>> split(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        List<byte[]> pages = pdfToolService.split(id);
        return ResponseEntity.ok(Map.of(
            "totalPages", pages.size(),
            "message", "拆分完成，共 " + pages.size() + " 页"
        ));
    }

    /**
     * 旋转页面(Phase 3:原子化)
     */
    @PostMapping("/{id}/pages/rotate")
    public ResponseEntity<Map<String, Object>> rotatePages(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> pages = (List<Number>) body.get("pages");
        int degrees = ((Number) body.getOrDefault("degrees", 90)).intValue();

        if (pages == null || pages.isEmpty()) {
            throw new BusinessException("页码列表不能为空");
        }

        List<Integer> pageList = pages.stream().map(Number::intValue).toList();
        byte[] result = pdfToolService.rotatePages(id, pageList, degrees);
        String newFilePath = pdfToolService.replacePdfBytes(id, result, "rotate");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已旋转 " + pageList.size() + " 页 (" + degrees + "°)",
            "filePath", newFilePath,
            "rotatedPages", pageList
        ));
    }

    /**
     * 删除页面(Phase 3:原子化)
     */
    @DeleteMapping("/{id}/pages/{pageNum}")
    public ResponseEntity<Map<String, Object>> deletePage(
            @PathVariable Long id,
            @PathVariable int pageNum,
            HttpServletRequest httpRequest) {
        byte[] result = pdfToolService.deletePages(id, List.of(pageNum));
        String newFilePath = pdfToolService.replacePdfBytes(id, result, "delete_page");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已删除第 " + pageNum + " 页",
            "filePath", newFilePath,
            "deletedPage", pageNum
        ));
    }

    /**
     * Phase 13.12-D: 批量删除页面
     * POST /api/pdf/{id}/pages/delete-batch { pages: [1,3,5] }
     * service 层已按降序删除避免索引偏移
     */
    @PostMapping("/{id}/pages/delete-batch")
    public ResponseEntity<Map<String, Object>> deletePagesBatch(
            @PathVariable Long id,
            @RequestBody Map<String, List<Integer>> body,
            HttpServletRequest httpRequest) {
        List<Integer> pages = body.get("pages");
        if (pages == null || pages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "pages 不能为空"));
        }
        byte[] result = pdfToolService.deletePages(id, pages);
        String newFilePath = pdfToolService.replacePdfBytes(id, result, "delete_pages_batch");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已删除 " + pages.size() + " 页",
            "filePath", newFilePath,
            "deletedPages", pages.size()
        ));
    }

    /**
     * 提取页面(Phase 3:原子化)
     */
    @PostMapping("/{id}/pages/extract")
    public ResponseEntity<Map<String, Object>> extractPages(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> pages = (List<Number>) body.get("pages");
        if (pages == null || pages.isEmpty()) {
            throw new BusinessException("页码列表不能为空");
        }

        List<Integer> pageList = pages.stream().map(Number::intValue).toList();
        byte[] result = pdfToolService.extractPages(id, pageList);
        String newFilePath = pdfToolService.replacePdfBytes(id, result, "extract");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已提取 " + pageList.size() + " 页",
            "filePath", newFilePath,
            "extractedPages", pageList
        ));
    }

    /**
     * Phase 13.37: 替换页面 - 用上传 PDF 的指定页替换目标文档选中页
     * POST /api/pdf/{id}/pages/replace (multipart)
     *   targetPages: "2,5" 逗号分隔的目标页
     *   sourceStartPage: 源 PDF 起始页(1-based)
     *   file: 源 PDF 文件
     */
    @PostMapping("/{id}/pages/replace")
    public ResponseEntity<Map<String, Object>> replacePages(
            @PathVariable Long id,
            @RequestParam("targetPages") String targetPagesStr,
            @RequestParam("sourceStartPage") int sourceStartPage,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) throw new BusinessException("未上传源 PDF 文件");
        List<Integer> targetPages = new ArrayList<>();
        for (String s : targetPagesStr.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) targetPages.add(Integer.parseInt(t));
        }
        byte[] sourceBytes = file.getBytes();
        String filePath = pdfToolService.replacePages(id, targetPages, sourceBytes, sourceStartPage);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已替换 " + targetPages.size() + " 页",
            "filePath", filePath
        ));
    }

    /**
     * 重排页面(Phase 3:原子化)
     */
    @PostMapping("/{id}/pages/reorder")
    public ResponseEntity<Map<String, Object>> reorderPages(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> newOrder = (List<Number>) body.get("newOrder");
        if (newOrder == null || newOrder.isEmpty()) {
            throw new BusinessException("页面顺序不能为空");
        }

        List<Integer> order = newOrder.stream().map(Number::intValue).toList();
        byte[] result = pdfToolService.reorderPages(id, order);
        String newFilePath = pdfToolService.replacePdfBytes(id, result, "reorder");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已重排页面",
            "filePath", newFilePath,
            "newOrder", order
        ));
    }

    // ==================== Phase 11: 页面操作(插入/裁剪/水印/页眉页脚) ====================

    /**
     * 插入空白页
     * body: { "afterPage": int } 0 = 末尾
     */
    @PostMapping("/{id}/pages/insert-blank")
    public ResponseEntity<Map<String, Object>> insertBlankPage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        int afterPage = 0;
        if (body != null && body.get("afterPage") instanceof Number) {
            afterPage = ((Number) body.get("afterPage")).intValue();
        }
        byte[] newBytes = pdfToolService.insertBlankPage(id, afterPage);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已插入空白页",
            "bustUrl", newBytes != null ? System.currentTimeMillis() : null
        ));
    }

    /**
     * 裁剪页面
     * body: { "pages": [1,2], "cropBox": {x, y, width, height} }
     */
    @PostMapping("/{id}/pages/crop")
    public ResponseEntity<Map<String, Object>> cropPages(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> rawPages = (List<Number>) body.get("pages");
        @SuppressWarnings("unchecked")
        Map<String, Number> rawBox = (Map<String, Number>) body.get("cropBox");
        if (rawPages == null || rawBox == null) throw new BusinessException("pages 和 cropBox 必填");
        List<Integer> pages = new ArrayList<>();
        for (Number n : rawPages) pages.add(n.intValue());
        Map<String, Double> cropBox = new HashMap<>();
        for (Map.Entry<String, Number> e : rawBox.entrySet()) cropBox.put(e.getKey(), e.getValue().doubleValue());
        byte[] newBytes = pdfToolService.cropPages(id, pages, cropBox);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "已裁剪 " + pages.size() + " 页",
            "bustUrl", newBytes != null ? System.currentTimeMillis() : null
        ));
    }

    /**
     * 添加水印
     * body: { "text": str, "opacity": 0-1, "rotation": degrees, "pages": [int] }
     *
     * Phase 26 fix:手动 UTF-8 读 body,避免 Jackson ISO-8859-1 误解中文(导致后续字体查找失败 500)。
     */
    @PostMapping("/{id}/watermark")
    public ResponseEntity<Map<String, Object>> addWatermark(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Map<String, Object> body;
        try {
            body = readUtf8Body(httpRequest);
        } catch (java.io.IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        }
        String text = (String) body.getOrDefault("text", "CONFIDENTIAL");
        double opacity = body.get("opacity") instanceof Number ? ((Number) body.get("opacity")).doubleValue() : 0.3;
        double rotation = body.get("rotation") instanceof Number ? ((Number) body.get("rotation")).doubleValue() : 45;
        // Phase 14.U4: position/fontSize/clearExisting
        String position = (String) body.getOrDefault("position", "diagonal");
        float fontSize = body.get("fontSize") instanceof Number ? ((Number) body.get("fontSize")).floatValue() : 0;
        boolean clearExisting = body.get("clearExisting") instanceof Boolean ? (Boolean) body.get("clearExisting") : true;
        @SuppressWarnings("unchecked")
        List<Number> rawPages = (List<Number>) body.get("pages");
        List<Integer> pages = new ArrayList<>();
        if (rawPages != null) for (Number n : rawPages) pages.add(n.intValue());
        byte[] newBytes = pdfToolService.addWatermark(id, text, opacity, rotation, position, fontSize, clearExisting, pages);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", clearExisting ? "已替换水印" : "已添加水印(追加)",
            "bustUrl", newBytes != null ? System.currentTimeMillis() : null
        ));
    }

    /**
     * Phase 13.23 + 14.U4: 去水印
     * POST /api/pdf/{id}/watermark/remove { mode: "annotation"|"all" }
     */
    @PostMapping("/{id}/watermark/remove")
    public ResponseEntity<Map<String, Object>> removeWatermark(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        String mode = body.getOrDefault("mode", "all").toString();
        byte[] newBytes = pdfToolService.removeWatermark(id, mode);
        String newFilePath = pdfToolService.replacePdfBytes(id, newBytes, "remove-watermark");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "去水印完成",
            "filePath", newFilePath
        ));
    }

    /**
     * Phase 13.23: 智能目录(AI 生成 + 写入 PDF outline)
     * POST /api/pdf/{id}/ai/auto-outline
     */
    @PostMapping("/{id}/ai/auto-outline")
    public ResponseEntity<Map<String, Object>> autoOutline(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = pdfToolService.autoOutline(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 13.23: 智能提取(文字+表格+图片+结构化 JSON)
     * POST /api/pdf/{id}/ai/extract-structured
     */
    @PostMapping("/{id}/ai/extract-structured")
    public ResponseEntity<Map<String, Object>> extractStructured(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = pdfToolService.extractStructured(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 13.23: 提取嵌入图片 zip
     * GET /api/pdf/{id}/extract-images
     *
     * Phase 26 fix:无嵌入图时返回 204 + X-No-Images: true 头,
     * 让前端能区分"无图"和"真 zip"(原行为返回 22 字节空 zip 易让用户误以为下载失败)。
     */
    @GetMapping("/{id}/extract-images")
    public ResponseEntity<byte[]> extractImages(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        byte[] zip = pdfToolService.extractImagesZip(id);
        if (zip == null) {
            return ResponseEntity.noContent()
                .header("X-No-Images", "true")
                .build();
        }
        return ResponseEntity.ok()
            .header("Content-Type", "application/zip")
            .header("Content-Disposition", "attachment; filename=\"pdf-images.zip\"")
            .body(zip);
    }

    /**
     * 添加页眉/页脚
     * body: { "position": "header"|"footer", "content": str, "fontSize": num, "pages": [int] }
     *
     * Phase 26 fix:手动 UTF-8 读 body。
     */
    @PostMapping("/{id}/header-footer")
    public ResponseEntity<Map<String, Object>> addHeaderFooter(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Map<String, Object> body;
        try {
            body = readUtf8Body(httpRequest);
        } catch (java.io.IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        }
        String position = (String) body.getOrDefault("position", "footer");
        String content = (String) body.getOrDefault("content", "Page {page} of {total}");
        double fontSize = body.get("fontSize") instanceof Number ? ((Number) body.get("fontSize")).doubleValue() : 10;
        // Phase 27: alignment = left|center|right(默认 left,向后兼容)
        String alignment = (String) body.getOrDefault("alignment", "left");
        // Phase 14.U2: clearExisting 默认 true(覆盖模式)
        boolean clearExisting = body.get("clearExisting") instanceof Boolean ? (Boolean) body.get("clearExisting") : true;
        @SuppressWarnings("unchecked")
        List<Number> rawPages = (List<Number>) body.get("pages");
        List<Integer> pages = new ArrayList<>();
        if (rawPages != null) for (Number n : rawPages) pages.add(n.intValue());
        byte[] newBytes = pdfToolService.addHeaderFooter(id, position, content, fontSize, alignment, clearExisting, pages);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", clearExisting ? "已替换页眉页脚" : "已追加页眉页脚",
            "bustUrl", newBytes != null ? System.currentTimeMillis() : null
        ));
    }

    // ==================== 优化 ====================

    /**
     * 压缩 PDF
     */
    @PostMapping("/{id}/compress")
    public ResponseEntity<byte[]> compress(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest) {
        String level = body != null ? body.get("level") : "medium";
        byte[] result = pdfToolService.compress(id, level);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("compressed.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    // ==================== 安全 ====================

    /**
     * 加密 PDF
     */
    @PostMapping("/{id}/encrypt")
    public ResponseEntity<byte[]> encrypt(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String password = body.get("password");
        byte[] result = pdfToolService.encrypt(id, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("encrypted.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    /**
     * 解密 PDF
     */
    @PostMapping("/{id}/decrypt")
    public ResponseEntity<byte[]> decrypt(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String password = body.get("password");
        byte[] result = pdfToolService.decrypt(id, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("decrypted.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }


    // ==================== 识别 ====================

    /**
     * 智能识别 PDF（Docling 优先，PDFBox 回退）
     * @deprecated 使用 /{id}/recognize 替代（会自动保存结果）
     */
    @PostMapping("/{id}/recognize-old")
    public ResponseEntity<Map<String, Object>> recognizeOld(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = pdfRecognizeService.recognize(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 导出编辑后的内容
     */
    @PostMapping("/{id}/export-edited")
    public ResponseEntity<byte[]> exportEdited(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String content = body.get("content");
        String format = body.getOrDefault("format", "md");

        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }

        byte[] result = pdfRecognizeService.exportEdited(content, format);

        String filename;
        MediaType mediaType;
        switch (format) {
            case "md" -> { filename = "document.md"; mediaType = MediaType.TEXT_PLAIN; }
            case "txt" -> { filename = "document.txt"; mediaType = MediaType.TEXT_PLAIN; }
            default -> throw new BusinessException("不支持的格式: " + format);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    // ==================== PDF 内容编辑 ====================

    /**
     * 保存 PDF 文字编辑
     */
    @PostMapping("/{id}/text-edits")
    public ResponseEntity<Map<String, Object>> saveTextEdits(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> editsList = (List<Map<String, Object>>) body.get("edits");

        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            // 保存文字编辑到数据库（JSON 格式）
            String editsJson = documentService.saveTextEdits(id, editsList);

            // 同时生成修改后的 PDF 文件
            byte[] modifiedPdf = pdfToolService.applyTextEdits(id, editsList);

            // 更新文档文件
            String newFilePath = storageService.store(doc.getDocKey() + "_edited", modifiedPdf);
            doc.setFilePath(newFilePath);
            documentService.updateDocument(doc);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "编辑已保存",
                "editsId", editsJson.substring(0, Math.min(20, editsJson.length()))
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存 PDF 文字编辑失败: docId={}", id, e);
            throw new BusinessException("保存编辑失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.25: 应用文本格式修改(字号/颜色/粗/斜/下划线/高亮)持久化
     * POST /api/pdf/{id}/text-format
     * body: { pageNumber, ops: [{ range:{x,y,width,height}, format:{fontSize?,color?,bold?,italic?,underline?,highlight?} }] }
     */
    @PostMapping("/{id}/text-format")
    public ResponseEntity<Map<String, Object>> applyTextFormat(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            Number pageNumNum = (Number) body.get("pageNumber");
            int pageNumber = pageNumNum != null ? pageNumNum.intValue() : 1;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ops = (List<Map<String, Object>>) body.get("ops");
            if (ops == null || ops.isEmpty()) {
                throw new BusinessException("格式操作列表为空");
            }

            String filePath = pdfToolService.applyTextFormat(id, pageNumber, ops);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "filePath", filePath,
                    "message", "已应用 " + ops.size() + " 处格式修改"
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("应用 PDF 文本格式失败: docId={}", id, e);
            throw new BusinessException("应用格式失败: " + e.getMessage());
        }
    }

    /**
     * 加载 PDF 文字编辑
     */
    @GetMapping("/{id}/text-edits")
    public ResponseEntity<Map<String, Object>> getTextEdits(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            List<Map<String, Object>> edits = documentService.getTextEdits(id);
            return ResponseEntity.ok(Map.of(
                "edits", edits,
                "count", edits != null ? edits.size() : 0
            ));
        } catch (Exception e) {
            log.error("加载 PDF 文字编辑失败: docId={}", id, e);
            return ResponseEntity.ok(Map.of("edits", List.of(), "count", 0));
        }
    }

    /**
     * 获取 PDF 文字位置信息（用于编辑参考）
     */
    @GetMapping("/{id}/text-positions")
    public ResponseEntity<Map<String, Object>> getTextPositions(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        try {
            List<Map<String, Object>> positions = pdfToolService.extractTextPositions(id);
            int totalPages = positions.stream()
                .map(p -> p.get("pageNum"))
                .mapToInt(p -> ((Number) p).intValue())
                .max()
                .orElse(0);
            return ResponseEntity.ok(Map.of(
                "positions", positions,
                "totalPages", totalPages
            ));
        } catch (Exception e) {
            log.error("获取 PDF 文字位置失败: docId={}", id, e);
            return ResponseEntity.ok(Map.of("positions", List.of(), "totalPages", 0));
        }
    }

    // ==================== Markdown 内容管理 ====================

    /**
     * 获取识别后的 Markdown 内容
     */
    @GetMapping("/{id}/markdown")
    public ResponseEntity<Map<String, Object>> getMarkdown(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        return ResponseEntity.ok(Map.of(
            "recognized", doc.getPdfRecognized() != null && doc.getPdfRecognized(),
            "markdown", doc.getPdfMarkdown() != null ? doc.getPdfMarkdown() : Map.of(),
            "ocrData", doc.getPdfOcrData() != null ? doc.getPdfOcrData() : Map.of(),
            "recognizedAt", doc.getPdfRecognizedAt() != null ? doc.getPdfRecognizedAt().toString() : ""
        ));
    }

    /**
     * 保存 Markdown 内容
     */
    @PutMapping("/{id}/markdown")
    public ResponseEntity<Map<String, Object>> saveMarkdown(
            @PathVariable Long id,
            @RequestBody Map<String, String> markdown) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        documentService.savePdfMarkdown(id, markdown);
        return ResponseEntity.ok(Map.of("success", true, "message", "已保存"));
    }

    /**
     * Phase 27 测试报告修复:改为异步任务模式(避免大文件 74s 阻塞)
     * 流程:
     *   1. 创建 mt_pdf_task 记录(状态 pending, task_type=ocr_pdfbox)
     *   2. 发 RabbitMQ 消息
     *   3. 立即返回 {taskId, status: "pending"},前端订阅 SSE 获取进度
     * 注:Docling 仍走内嵌同步路径(模型调用前),但 PaddleOCR 入口已被改造
     */
    @PostMapping("/{id}/recognize")
    public ResponseEntity<Map<String, Object>> recognize(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        Long userId = getCurrentUserId();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Long taskId;
        try {
            jakarta.persistence.Query q = entityManager.createNativeQuery(
                    "INSERT INTO mt_pdf_task (document_id, task_type, status, parameters, model, language, progress, created_by, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), 'mobile', 'ch', 0, ?5, ?6, ?6) RETURNING id");
            q.setParameter(1, id);
            q.setParameter(2, "ocr_pdfbox");
            q.setParameter(3, "pending");
            q.setParameter(4, "{\"engine\":\"pdfbox\"}");
            q.setParameter(5, userId);
            q.setParameter(6, now);
            taskId = ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            log.error("创建 PDF 识别任务失败: docId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "code", "DB_ERROR",
                    "message", "创建任务失败: " + e.getMessage()));
        }
        try {
            com.miaotong.doc.mq.PdfOcrTaskMessage msg = new com.miaotong.doc.mq.PdfOcrTaskMessage(
                    taskId, id, userId, "mobile", "ch", System.currentTimeMillis());
            // 复用 Producer,但用特定 routing key 区分(此处直接走同一队列,Consumer 按 taskType 分发)
            pdfOcrTaskProducer.send(msg);
        } catch (Exception e) {
            log.error("PDF 识别任务入队失败: docId={}, error={}", id, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "code", "QUEUE_FAILED",
                    "message", "OCR 任务入队失败,请稍后重试"));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "pending");
        resp.put("taskId", taskId);
        resp.put("docId", id);
        resp.put("sseUrl", "/api/pdf/" + id + "/ocr-progress/" + taskId);
        resp.put("message", "PDF 识别任务已提交,正在处理");
        return ResponseEntity.ok(resp);
    }

    /**
     * Phase 11.4 + OCR AI 改造 v3: 强制走 PaddleOCR(异步任务模式)
     *
     * 流程(改造后):
     *   1. 创建 mt_pdf_task 记录(状态 pending)
     *   2. 发 RabbitMQ 消息 pdf.ocr.task
     *   3. 立即返回 {taskId, status: "pending"},前端订阅 SSE 获取进度
     *
     * Consumer 端(PdfOcrTaskConsumer):
     *   - 标记 processing
     *   - 调 PaddleOcrClient 异步识别,带进度回调
     *   - 实时更新 mt_pdf_task.progress
     *   - 完成时标记 completed/failed,SSE 自动推送 done/error
     */
    @PostMapping("/{id}/recognize-paddle")
    public ResponseEntity<Map<String, Object>> recognizePaddle(
            @PathVariable Long id,
            @RequestParam(value = "model", required = false, defaultValue = "mobile") String model) {
        // 校验文档
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        // 创建任务(用 SQL 原生 INSERT 避开 parameters jsonb 类型问题)
        Long userId = getCurrentUserId();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Long taskId;
        try {
            jakarta.persistence.Query q = entityManager.createNativeQuery(
                    "INSERT INTO mt_pdf_task (document_id, task_type, status, parameters, model, language, progress, created_by, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), ?5, ?6, 0, ?7, ?8, ?8) RETURNING id");
            q.setParameter(1, id);
            q.setParameter(2, "ocr_paddle");
            q.setParameter(3, "pending");
            q.setParameter(4, "{}");
            q.setParameter(5, model);
            q.setParameter(6, "ch");
            q.setParameter(7, userId);
            q.setParameter(8, now);
            taskId = ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            log.error("创建 OCR 任务失败: docId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "code", "DB_ERROR",
                    "message", "创建任务失败: " + e.getMessage()));
        }

        // 发 MQ 消息
        try {
            com.miaotong.doc.mq.PdfOcrTaskMessage msg = new com.miaotong.doc.mq.PdfOcrTaskMessage(
                    taskId, id, userId, model, "ch", System.currentTimeMillis());
            pdfOcrTaskProducer.send(msg);
        } catch (Exception e) {
            log.error("OCR 任务入队失败: docId={}, error={}", id, e.getMessage());
            updateTaskStatusFailed(taskId, "入队失败: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "code", "QUEUE_FAILED",
                    "message", "OCR 任务入队失败,请稍后重试"));
        }

        // 立即返回 taskId(异步模式)
        return ResponseEntity.ok(Map.of(
                "status", "pending",
                "taskId", taskId,
                "docId", id,
                "model", model,
                "message", "OCR 任务已提交,正在处理",
                "sseUrl", "/api/pdf/" + id + "/ocr-progress/" + taskId));
    }

    /** 标记 OCR 任务为 failed(SQL 原生 UPDATE 避免 jsonb 字段) */
    private void updateTaskStatusFailed(Long taskId, String errMsg) {
        try {
            jakarta.persistence.Query q = entityManager.createNativeQuery(
                    "UPDATE mt_pdf_task SET status='failed', error_message=?1, completed_at=now(), updated_at=now() WHERE id=?2");
            q.setParameter(1, errMsg);
            q.setParameter(2, taskId);
            q.executeUpdate();
        } catch (Exception e) {
            log.warn("更新任务失败状态出错: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    /**
     * 把单字符串 markdown 切成 Map<pageNum, content>。
     * 策略:遇到水平线 "===" 或 "# Page N" 标记就分页,否则整体存为 "1"。
     */
    private Map<String, String> splitMarkdownByPage(String markdown) {
        Map<String, String> result = new LinkedHashMap<>();
        if (markdown == null || markdown.isBlank()) return result;
        String[] parts = markdown.split("(?m)^={3,}\\s*$|\\n# Page\\s+\\d+");
        if (parts.length <= 1) {
            result.put("1", markdown);
        } else {
            for (int i = 0; i < parts.length; i++) {
                String trimmed = parts[i].trim();
                if (!trimmed.isEmpty()) result.put(String.valueOf(i + 1), trimmed);
            }
        }
        return result;
    }

    // ==================== Phase 8: 书签/大纲 + 搜索 + 元数据 ====================

    /**
     * 获取 PDF 书签/大纲(树形结构,平铺数组带 level 字段)
     * GET /api/pdf/{id}/outline
     */
    @GetMapping("/{id}/outline")
    public ResponseEntity<Map<String, Object>> getOutline(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        try {
            List<Map<String, Object>> outline = pdfToolService.extractOutline(id);
            return ResponseEntity.ok(Map.of(
                "outline", outline,
                "count", outline.size()
            ));
        } catch (Exception e) {
            log.error("获取 PDF 大纲失败: docId={}", id, e);
            return ResponseEntity.ok(Map.of("outline", List.of(), "count", 0));
        }
    }

    /**
     * 全文搜索
     * GET /api/pdf/{id}/search?q=keyword&caseSensitive=false
     */
    @GetMapping("/{id}/search")
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable Long id,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "caseSensitive", required = false, defaultValue = "false") boolean caseSensitive) {
        return doSearch(id, q, caseSensitive);
    }

    /**
     * POST 版本搜索(支持中文 query,绕过 Tomcat 严格 URL 解析)
     * body: { "q": "搜索词", "caseSensitive": false }
     *
     * Phase 26 fix:手动 UTF-8 读 body。
     */
    @PostMapping("/{id}/search")
    public ResponseEntity<Map<String, Object>> searchPost(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String q;
        boolean caseSensitive = false;
        try {
            Map<String, Object> body = readUtf8Body(httpRequest);
            q = body != null ? (String) body.get("q") : null;
            if (body != null && Boolean.TRUE.equals(body.get("caseSensitive"))) {
                caseSensitive = true;
            }
        } catch (java.io.IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        }
        return doSearch(id, q, caseSensitive);
    }

    private ResponseEntity<Map<String, Object>> doSearch(Long id, String q, boolean caseSensitive) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        try {
            List<Map<String, Object>> hits = pdfToolService.searchText(id, q, caseSensitive);
            return ResponseEntity.ok(Map.of(
                "results", hits,
                "count", hits.size(),
                "query", q != null ? q : ""
            ));
        } catch (Exception e) {
            log.error("搜索失败: docId={}, q={}", id, q, e);
            return ResponseEntity.ok(Map.of("results", List.of(), "count", 0, "query", ""));
        }
    }

    /**
     * PDF 元数据
     * GET /api/pdf/{id}/metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        Map<String, Object> meta = pdfToolService.getPdfMetadata(id);
        return ResponseEntity.ok(meta);
    }

    /**
     * 查询识别状态
     */
    @GetMapping("/{id}/recognize-status")
    public ResponseEntity<Map<String, Object>> getRecognizeStatus(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);

        String status;
        if (doc.getPdfRecognized() != null && doc.getPdfRecognized()) {
            status = "completed";
        } else if (doc.getPdfMarkdown() != null && !doc.getPdfMarkdown().isEmpty()) {
            status = "processing";
        } else {
            status = "pending";
        }

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("status", status);
        resp.put("recognized", doc.getPdfRecognized() != null && doc.getPdfRecognized());
        resp.put("recognizedAt", doc.getPdfRecognizedAt() != null ? doc.getPdfRecognizedAt().toString() : null);
        return ResponseEntity.ok(resp);
    }

    // ==================== 辅助方法 ====================

    private void validatePdf(Document doc) {
        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }
    }

    // ==================== Phase 12.1: 表单字段检测 ====================

    /**
     * Phase 13.11: 另存为新文档(复制当前 PDF 为新文档,不复制版本历史)
     */
    @PostMapping("/{id}/save-as-new")
    public ResponseEntity<Map<String, Object>> saveAsNew(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        Long userId = (Long) httpRequest.getAttribute("userId");
        String title = body != null ? body.get("title") : null;
        Document newDoc = documentService.copyDocument(id, title, userId);
        log.info("Phase 13.11 另存为新文档: src={}, new={}, title={}", id, newDoc.getId(), newDoc.getTitle());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "newDocId", newDoc.getId(),
            "title", newDoc.getTitle(),
            "message", "已另存为新文档"
        ));
    }

    /**
     * 识别 PDF 的 AcroForm 表单字段
     */
    @GetMapping("/{id}/form-fields")
    public ResponseEntity<List<Map<String, Object>>> getFormFields(@PathVariable Long id) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        List<Map<String, Object>> fields = pdfToolService.getFormFields(id);
        return ResponseEntity.ok(fields);
    }

    /**
     * Phase 12.2: 填充表单字段
     * 接收 { values: { fieldName: value, ... } },返回新 PDF 字节
     *
     * Phase 26 fix:手动 UTF-8 读 body。
     */
    @PostMapping("/{id}/form-fields/fill")
    public ResponseEntity<byte[]> fillFormFields(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Map<String, Object> body = readUtf8Body(httpRequest);
            @SuppressWarnings("unchecked")
            Map<String, String> values = (Map<String, String>) body.get("values");
            byte[] result = pdfToolService.fillFormFields(id, values);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("filled.pdf").build());
            return new ResponseEntity<>(result, headers, HttpStatus.OK);
        } catch (java.io.IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.26: 表单填充 in-place(落盘 + 返回 filePath,不下载)
     *
     * Phase 26 fix:手动 UTF-8 读 body。
     */
    @PostMapping("/{id}/form-fields/fill-in-place")
    public ResponseEntity<Map<String, Object>> fillFormFieldsInPlace(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        try {
            Map<String, Object> body = readUtf8Body(httpRequest);
            @SuppressWarnings("unchecked")
            Map<String, String> values = (Map<String, String>) body.get("values");
            byte[] result = pdfToolService.fillFormFields(id, values);
            String filePath = pdfToolService.replacePdfBytes(id, result, "form-fill");
            return ResponseEntity.ok(Map.of("success", true, "filePath", filePath, "message", "表单已填充并保存"));
        } catch (java.io.IOException e) {
            throw new BusinessException("请求体解析失败: " + e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("表单 in-place 填充失败: docId={}", id, e);
            throw new BusinessException("表单填充失败: " + e.getMessage());
        }
    }

    /**
     * Phase 12.3: 嵌入签名图片
     * 接收 { image: base64, page, x, y, width, height }
     * 返回新 PDF 字节
     */
    @PostMapping("/{id}/signature")
    public ResponseEntity<byte[]> embedSignature(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String imageBase64 = (String) body.get("image");
        int page = body.get("page") instanceof Number ? ((Number) body.get("page")).intValue() : 1;
        double x = body.get("x") instanceof Number ? ((Number) body.get("x")).doubleValue() : 0;
        double y = body.get("y") instanceof Number ? ((Number) body.get("y")).doubleValue() : 0;
        double width = body.get("width") instanceof Number ? ((Number) body.get("width")).doubleValue() : 120;
        double height = body.get("height") instanceof Number ? ((Number) body.get("height")).doubleValue() : 40;
        byte[] result = pdfToolService.embedSignature(id, imageBase64, page, x, y, width, height);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("signed.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    /**
     * Phase 13.26: 签名 in-place 嵌入(落盘 + 返回 filePath,不下载)
     * 前端拿 filePath 后 reload 文档,签名直接显示在当前页
     */
    @PostMapping("/{id}/signature/in-place")
    public ResponseEntity<Map<String, Object>> embedSignatureInPlace(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Document doc = documentService.getDocument(id);
        validatePdf(doc);
        try {
            String imageBase64 = (String) body.get("image");
            int page = body.get("page") instanceof Number ? ((Number) body.get("page")).intValue() : 1;
            double x = body.get("x") instanceof Number ? ((Number) body.get("x")).doubleValue() : 0;
            double y = body.get("y") instanceof Number ? ((Number) body.get("y")).doubleValue() : 0;
            double width = body.get("width") instanceof Number ? ((Number) body.get("width")).doubleValue() : 120;
            double height = body.get("height") instanceof Number ? ((Number) body.get("height")).doubleValue() : 40;
            byte[] result = pdfToolService.embedSignature(id, imageBase64, page, x, y, width, height);
            String filePath = pdfToolService.replacePdfBytes(id, result, "signature");
            return ResponseEntity.ok(Map.of("success", true, "filePath", filePath, "message", "签名已嵌入文档"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("签名 in-place 嵌入失败: docId={}", id, e);
            throw new BusinessException("签名嵌入失败: " + e.getMessage());
        }
    }

    /**
     * Phase 12.4: 应用密文(绘制黑色矩形覆盖)
     * 接收 { regions: [{page, x, y, width, height}, ...] }
     */
    @PostMapping("/{id}/redact")
    public ResponseEntity<byte[]> applyRedaction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> regions = (List<Map<String, Object>>) body.get("regions");
        byte[] result = pdfToolService.applyRedaction(id, regions);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("redacted.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    // ==================== Phase 13.12-B: 创建 PDF + 拆分 zip + 批量 ====================

    /**
     * 创建空白 PDF:指定页数 + 尺寸
     * POST /api/pdf/create/blank { pages, width, height, title }
     * Phase 13.12: 手动读 body + UTF-8 解码,绕过 Jackson ISO-8859-1 默认编码问题
     */
    @PostMapping("/create/blank")
    public ResponseEntity<Map<String, Object>> createBlankPdf(
            HttpServletRequest httpRequest) throws java.io.IOException {
        String bodyStr = new String(httpRequest.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = bodyStr.isBlank() ? Map.of() : om.readValue(bodyStr, Map.class);

        int pages = body.get("pages") instanceof Number n ? n.intValue() : 1;
        float widthPt = body.get("width") instanceof Number w ? w.floatValue() : 595f;
        float heightPt = body.get("height") instanceof Number h ? h.floatValue() : 842f;
        byte[] pdfBytes = pdfToolService.createBlankPdf(pages, widthPt, heightPt);

        Long userId = (Long) httpRequest.getAttribute("userId");
        String title = body.get("title") != null ? body.get("title").toString() : "新建空白文档";
        com.miaotong.doc.dto.CreateDocumentRequest req = new com.miaotong.doc.dto.CreateDocumentRequest();
        req.setDocType("pdf");
        req.setTitle("blank"); // 占位英文,避免 DocGenerator 中文 title bug
        com.miaotong.doc.entity.Document doc = documentService.createDocument(req, userId);

        // 覆盖为真实空白 PDF + 正确中文 title
        String newPath = pdfToolService.replacePdfBytes(doc.getId(), pdfBytes, "create-blank");
        // Phase 13.12: replacePdfBytes 内 updateDocument 可能因事务未提交而 DB 未更新,
        // 这里重新查 + 强制 setFilePath + updateDocument 确保持久化
        com.miaotong.doc.entity.Document reloaded = documentService.getDocument(doc.getId());
        reloaded.setFilePath(newPath);
        reloaded.setFileSize((long) pdfBytes.length);
        reloaded.setTitle(title);
        documentService.updateDocument(reloaded);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("docId", doc.getId());
        resp.put("title", title);
        resp.put("pages", pages);
        return ResponseEntity.ok(resp);
    }

    /**
     * 图片转 PDF(multipart,files[]):每图 1 页 A4 居中
     * POST /api/pdf/create/from-images (multipart)
     */
    @PostMapping(value = "/create/from-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createFromImages(
            @RequestParam(value = "files", required = false) List<org.springframework.web.multipart.MultipartFile> files,
            @RequestParam(value = "title", required = false) String title,
            HttpServletRequest httpRequest) throws java.io.IOException {
        if (files == null || files.isEmpty()) throw new BusinessException("至少需要 1 张图片");
        List<byte[]> images = new ArrayList<>();
        for (org.springframework.web.multipart.MultipartFile f : files) {
            images.add(f.getBytes());
        }
        byte[] pdfBytes = pdfToolService.createFromImages(images);

        Long userId = (Long) httpRequest.getAttribute("userId");
        String finalTitle = title != null ? title : "图片合集";
        com.miaotong.doc.dto.CreateDocumentRequest req = new com.miaotong.doc.dto.CreateDocumentRequest();
        req.setDocType("pdf");
        req.setTitle("images"); // 占位英文,避免 DocGenerator 中文 title bug
        com.miaotong.doc.entity.Document doc = documentService.createDocument(req, userId);

        String newPath = pdfToolService.replacePdfBytes(doc.getId(), pdfBytes, "create-from-images");
        com.miaotong.doc.entity.Document reloaded = documentService.getDocument(doc.getId());
        reloaded.setFilePath(newPath);
        reloaded.setFileSize((long) pdfBytes.length);
        reloaded.setTitle(finalTitle);
        documentService.updateDocument(reloaded);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("docId", doc.getId());
        resp.put("title", finalTitle);
        resp.put("pages", images.size());
        return ResponseEntity.ok(resp);
    }

    /**
     * 按页码区间拆分,返回 zip(每段一个 PDF)
     * POST /api/pdf/{id}/split-by-ranges { ranges: "1-3,5,7-9" }
     */
    @PostMapping("/{id}/split-by-ranges")
    public ResponseEntity<byte[]> splitByRanges(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) throws java.io.IOException {
        String ranges = body.getOrDefault("ranges", "");
        List<byte[]> parts = pdfToolService.splitByRanges(id, ranges);
        // Phase 13.38: 只有一个 PDF 时直接返回 PDF,不打包 zip
        if (parts.size() == 1) {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(ContentDisposition.attachment().filename("split.pdf").build());
            return new ResponseEntity<>(parts.get(0), h, HttpStatus.OK);
        }
        byte[] zip = buildZip(parts, "part");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("split.zip").build());
        return new ResponseEntity<>(zip, headers, HttpStatus.OK);
    }

    /**
     * 批量提取选中页面为多个 PDF,打包 zip(每页一份独立 PDF)
     * POST /api/pdf/{id}/extract-pages-batch { pages: [1,3,5] }
     */
    @PostMapping("/{id}/extract-pages-batch")
    public ResponseEntity<byte[]> extractPagesBatch(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) throws java.io.IOException {
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.get("pages");
        List<Integer> pages = new ArrayList<>();
        if (raw != null) for (Number n : raw) pages.add(n.intValue());
        if (pages.isEmpty()) {
            return new ResponseEntity<>(new byte[0], HttpStatus.BAD_REQUEST);
        }
        // Phase 13.31: 改为"每页一份 PDF → 打包 zip",而非单 PDF;
        // 用户期望"提取 N 页为 zip" = N 个独立 PDF 文件
        // Phase 13.38: 只提取 1 页时直接返回单 PDF,不打包 zip
        if (pages.size() == 1) {
            byte[] onePage = pdfToolService.extractPages(id, java.util.List.of(pages.get(0)));
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(ContentDisposition.attachment().filename("extracted-page.pdf").build());
            return new ResponseEntity<>(onePage, h, HttpStatus.OK);
        }
        List<byte[]> pdfList = new java.util.ArrayList<>();
        for (int p : pages) {
            byte[] onePage = pdfToolService.extractPages(id, java.util.List.of(p));
            pdfList.add(onePage);
        }
        byte[] zip = buildZip(pdfList, "page");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("extracted-pages.zip").build());
        return new ResponseEntity<>(zip, headers, HttpStatus.OK);
    }

    /**
     * 把多份 PDF 字节打成 zip(服务端一次性,前端不组装)
     */
    private byte[] buildZip(List<byte[]> pdfList, String baseName) throws java.io.IOException {
        java.io.ByteArrayOutputStream zipBaos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(zipBaos)) {
            for (int i = 0; i < pdfList.size(); i++) {
                String name = String.format("%s_%02d.pdf", baseName, i + 1);
                zos.putNextEntry(new java.util.zip.ZipEntry(name));
                zos.write(pdfList.get(i));
                zos.closeEntry();
            }
        }
        return zipBaos.toByteArray();
    }

    private Long getCurrentUserId() {
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() instanceof String) return 1L;
        try { return Long.parseLong(auth.getName()); } catch (Exception e) { return 1L; }
    }
}
