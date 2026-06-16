package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
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

    /**
     * 提取 PDF 全文文本（按页分段）
     */
    @GetMapping("/{id}/text")
    public ResponseEntity<Map<String, Object>> extractText(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);

        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }

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
                    pages.add(Map.of(
                        "pageNum", i,
                        "text", pageText
                    ));
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

        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) {
                    throw new BusinessException("页码超出范围");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(pdf);
                return ResponseEntity.ok(Map.of("text", text));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提取 PDF 页面文本失败: docId={}, page={}", id, pageNum, e);
            throw new BusinessException("提取文本失败");
        }
    }

    /**
     * 获取 PDF 页数
     */
    @GetMapping("/{id}/info")
    public ResponseEntity<Map<String, Object>> getPdfInfo(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Document doc = documentService.getDocument(id);

        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }

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
}
