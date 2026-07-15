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
    
    private final DoclingService doclingService;
    private final PdfRecognizeService pdfRecognizeService;

    // ==================== 文本提取（已有） ====================

    /**
     * 提取 PDF 全文文本（按页分段）
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
                    fullText.append(pageText);
                    pages.add(Map.of("pageNum", i, "text", pageText));
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
     * 合并多个 PDF
     */
    @PostMapping("/merge")
    public ResponseEntity<byte[]> merge(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<Number> docIds = (List<Number>) body.get("documentIds");
        if (docIds == null || docIds.size() < 2) {
            throw new BusinessException("至少需要两个文档");
        }

        List<Long> ids = docIds.stream().map(Number::longValue).toList();
        byte[] result = pdfToolService.merge(ids);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("merged.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
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
     * 旋转页面
     */
    @PostMapping("/{id}/pages/rotate")
    public ResponseEntity<byte[]> rotatePages(
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("rotated.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    /**
     * 删除页面
     */
    @DeleteMapping("/{id}/pages/{pageNum}")
    public ResponseEntity<byte[]> deletePage(
            @PathVariable Long id,
            @PathVariable int pageNum,
            HttpServletRequest httpRequest) {
        byte[] result = pdfToolService.deletePages(id, List.of(pageNum));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("modified.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    /**
     * 提取页面
     */
    @PostMapping("/{id}/pages/extract")
    public ResponseEntity<byte[]> extractPages(
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("extracted.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    /**
     * 重排页面
     */
    @PostMapping("/{id}/pages/reorder")
    public ResponseEntity<byte[]> reorderPages(
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("reordered.pdf").build());
        return new ResponseEntity<>(result, headers, HttpStatus.OK);
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
     * 触发识别（同步，直接返回识别结果）
     */
    @PostMapping("/{id}/recognize")
    public ResponseEntity<Map<String, Object>> recognize(@PathVariable Long id) {
        Map<String, Object> result = pdfRecognizeService.recognize(id);

        // 保存识别结果到文档
        if (result.containsKey("markdown")) {
            @SuppressWarnings("unchecked")
            Map<String, String> markdown = (Map<String, String>) result.get("markdown");
            documentService.savePdfMarkdown(id, markdown);

            // 提取并保存 OCR 坐标数据（用于在 PDF 原图位置叠加文字层，支持框选复制）
            Map<String, Object> ocrData = pdfRecognizeService.extractOcrData(result);
            if (!ocrData.isEmpty()) {
                documentService.savePdfOcrData(id, ocrData);
                log.info("已保存 OCR 坐标数据: docId={}, pages={}", id, ocrData.size());
            }

            documentService.markPdfRecognized(id);
        }

        return ResponseEntity.ok(result);
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

        return ResponseEntity.ok(Map.of(
            "status", status,
            "recognized", doc.getPdfRecognized() != null && doc.getPdfRecognized(),
            "recognizedAt", doc.getPdfRecognizedAt() != null ? doc.getPdfRecognizedAt().toString() : null
        ));
    }

    // ==================== 辅助方法 ====================

    private void validatePdf(Document doc) {
        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }
    }
}
