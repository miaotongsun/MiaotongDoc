package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * PDF 五维画像服务（PDF Profiler）
 *
 * 职责：分析 PDF 的 5 个维度特征，自动分类 T1-T8 类型
 *
 * 五维：
 *   - textDensity    : 文字密度（页平均字符数）
 *   - hasTextLayer  : 是否含可提取文字层
 *   - hasImages     : 是否含图片（>50KB 算扫描件候选）
 *   - hasFormFields : 是否含表单字段（合同标志）
 *   - hasTables     : 是否含结构化表格（基于 layout 算法推断）
 *
 * T1-T8 类型定义：
 *   T1 纯文本型         → PDFBox 直接读
 *   T2 文本+表格        → Docling
 *   T3 文本+复杂版式    → Docling + VLM
 *   T4 扫描件简单       → PaddleOCR
 *   T5 扫描件+表格     → PaddleOCR-VL
 *   T6 扫描件+复杂版式 → PaddleOCR-VL
 *   T7 混合型（部分扫描）→ 分页路由
 *   T8 加密 PDF         → 先解密再分类
 *
 * @since v2.6 PDF 五维画像
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfProfilerService {

    private final DocumentService documentService;
    private final StorageService storageService;

    /** 五维画像结果 */
    public static class PdfProfile {
        /** T1-T8 类型 */
        public String type;
        /** 文字密度（页平均字符数） */
        public int textDensity;
        /** 是否含文字层 */
        public boolean hasTextLayer;
        /** 是否含图片 */
        public boolean hasImages;
        /** 是否含表单字段 */
        public boolean hasFormFields;
        /** 是否含表格（推断） */
        public boolean hasTables;
        /** 总页数 */
        public int totalPages;
        /** 是否加密 */
        public boolean encrypted;
        /** 推荐引擎 */
        public String recommendedEngine;
        /** 备注 */
        public String remark;

        @Override
        public String toString() {
            return String.format(
                "PdfProfile{type=%s, pages=%d, textDensity=%d, hasText=%s, hasImg=%s, hasForm=%s, hasTable=%s, engine=%s}",
                type, totalPages, textDensity, hasTextLayer, hasImages, hasFormFields, hasTables, recommendedEngine
            );
        }
    }

    /**
     * 对 PDF 做五维画像，返回分类结果
     */
    public PdfProfile profile(Long documentId) {
        PdfProfile profile = new PdfProfile();
        Document doc = documentService.getDocument(documentId);
        if (!"pdf".equals(doc.getFileType())) {
            profile.type = "INVALID";
            profile.remark = "非 PDF 文档";
            return profile;
        }

        byte[] pdfBytes = storageService.load(doc.getFilePath());

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            profile.encrypted = pdf.isEncrypted();
            if (profile.encrypted) {
                profile.type = "T8";
                profile.recommendedEngine = "decrypt-first";
                profile.remark = "PDF 已加密，需先解密";
                return profile;
            }

            profile.totalPages = pdf.getNumberOfPages();

            // 取前 3 页采样（避免全文档分析太慢）
            int sampleSize = Math.min(profile.totalPages, 3);

            // 维度 1: 文字密度
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(sampleSize);
            String sampleText = stripper.getText(pdf);
            profile.textDensity = sampleText.length() / Math.max(sampleSize, 1);
            profile.hasTextLayer = profile.textDensity > 50;  // 每页 >50 字符算有文字层

            // 维度 2: 图片（基于资源数和大小粗略判断）
            int imageCount = countImages(pdf);
            profile.hasImages = imageCount > 0;

            // 维度 3: 表单字段
            PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
            profile.hasFormFields = form != null && form.getFields().size() > 0;

            // 维度 4: 表格（启发式：含连续制表符或多个数字块）
            profile.hasTables = detectTables(sampleText);

            // 推断 T1-T8
            classify(profile);

            log.info("PDF 画像: docId={}, {}", documentId, profile);
            return profile;

        } catch (Exception e) {
            log.error("PDF 画像分析失败: docId={}", documentId, e);
            profile.type = "T_UNKNOWN";
            profile.remark = "分析失败: " + e.getMessage();
            profile.recommendedEngine = "paddleocr";  // 兜底用 OCR
            return profile;
        }
    }

    /**
     * T1-T8 分类决策树
     */
    private void classify(PdfProfile p) {
        // 规则矩阵：
        //   - 有文字层 + 无图 = T1
        //   - 有文字层 + 有图 + 有表格 = T2/T3
        //   - 无文字层 + 有图（少量） = T4
        //   - 无文字层 + 有图 + 有表格 = T5/T6
        //   - 部分页有文字 + 部分无 = T7

        if (p.hasTextLayer && !p.hasImages) {
            // T1 纯文本
            p.type = "T1";
            p.recommendedEngine = "pdfbox";
            p.remark = "纯文本 PDF，PDFBox 直接提取即可";
        } else if (p.hasTextLayer && p.hasImages && p.hasTables) {
            // T3 文本+复杂版式（带图带表）
            p.type = "T3";
            p.recommendedEngine = "docling+vlm";
            p.remark = "文本+复杂版式，推荐 Docling + VLM";
        } else if (p.hasTextLayer && p.hasImages) {
            // T2 文本+表格
            p.type = "T2";
            p.recommendedEngine = "docling";
            p.remark = "文本+表格，推荐 Docling 结构化识别";
        } else if (!p.hasTextLayer && p.hasImages && p.hasTables) {
            // T5 扫描件+表格
            p.type = "T5";
            p.recommendedEngine = "paddleocr-vl";
            p.remark = "扫描件+表格，推荐 PaddleOCR-VL";
        } else if (!p.hasTextLayer && p.hasImages && p.hasFormFields) {
            // T6 扫描件+表单（复杂版式）
            p.type = "T6";
            p.recommendedEngine = "paddleocr-vl";
            p.remark = "扫描件含表单字段，推荐 PaddleOCR-VL";
        } else if (!p.hasTextLayer && p.hasImages) {
            // T4 扫描件简单
            p.type = "T4";
            p.recommendedEngine = "paddleocr";
            p.remark = "扫描件简单版式，推荐 PaddleOCR";
        } else {
            // 未知
            p.type = "T_UNKNOWN";
            p.recommendedEngine = "paddleocr";
            p.remark = "无法分类，默认走 OCR";
        }
    }

    /**
     * 统计 PDF 图片资源数
     */
    private int countImages(PDDocument pdf) {
        try {
            int count = 0;
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                var page = pdf.getPage(i);
                if (page.getResources() != null) {
                    var xobjects = page.getResources().getXObjectNames();
                    for (java.util.Iterator<?> it = xobjects.iterator(); it.hasNext(); it.next()) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 启发式检测表格（基于文字模式）
     * 简单规则：含 5+ 个数字块，或 3+ 个制表符密集行
     */
    private boolean detectTables(String text) {
        if (text == null || text.length() < 100) return false;

        // 数字密集行（疑似表格行）
        long numberLines = text.lines()
                .filter(line -> line.trim().matches(".*\\d+.*") && line.length() < 80)
                .filter(line -> line.split("\\s+").length >= 3)
                .count();

        // 多列对齐特征：制表符 + 空格密集
        long tabLines = text.lines()
                .filter(line -> line.split("\\s{2,}|\\t").length >= 3)
                .count();

        return numberLines >= 5 || tabLines >= 3;
    }
}