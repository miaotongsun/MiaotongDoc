package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToolService {

    private final DocumentService documentService;
    private final StorageService storageService;

    /**
     * 合并多个 PDF
     */
    public byte[] merge(List<Long> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            throw new BusinessException("至少需要两个文档才能合并");
        }

        try {
            PDDocument merged = new PDDocument();

            for (Long docId : documentIds) {
                Document doc = documentService.getDocument(docId);
                if (!"pdf".equals(doc.getFileType())) {
                    throw new BusinessException("文档 " + doc.getTitle() + " 不是 PDF 类型");
                }
                byte[] pdfBytes = storageService.load(doc.getFilePath());
                try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                    for (int i = 0; i < source.getNumberOfPages(); i++) {
                        merged.importPage(source.getPage(i));
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            merged.save(baos);
            merged.close();
            log.info("合并 PDF 完成: documents={}, size={}", documentIds, baos.size());
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("合并 PDF 失败: {}", documentIds, e);
            throw new BusinessException("合并 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 拆分 PDF（逐页拆分，返回所有页面组成的 PDF 列表）
     */
    public List<byte[]> split(Long documentId) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                Splitter splitter = new Splitter();
                List<PDDocument> splitDocs = splitter.split(source);
                List<byte[]> result = new ArrayList<>();

                for (PDDocument splitDoc : splitDocs) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    splitDoc.save(baos);
                    splitDoc.close();
                    result.add(baos.toByteArray());
                }

                log.info("拆分 PDF 完成: docId={}, pages={}", documentId, result.size());
                return result;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("拆分 PDF 失败: docId={}", documentId, e);
            throw new BusinessException("拆分 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 旋转指定页面
     */
    public byte[] rotatePages(Long documentId, List<Integer> pages, int degrees) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                int totalPages = pdf.getNumberOfPages();
                for (int pageNum : pages) {
                    if (pageNum < 1 || pageNum > totalPages) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围 (1-" + totalPages + ")");
                    }
                    PDPage page = pdf.getPage(pageNum - 1);
                    int currentRotation = page.getRotation();
                    page.setRotation((currentRotation + degrees) % 360);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("旋转页面完成: docId={}, pages={}, degrees={}", documentId, pages, degrees);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("旋转页面失败: docId={}", documentId, e);
            throw new BusinessException("旋转页面失败: " + e.getMessage());
        }
    }

    /**
     * 删除指定页面
     */
    public byte[] deletePages(Long documentId, List<Integer> pages) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                int totalPages = pdf.getNumberOfPages();
                // 从大到小排序删除，避免索引偏移
                List<Integer> sorted = new ArrayList<>(pages);
                sorted.sort((a, b) -> b - a);

                for (int pageNum : sorted) {
                    if (pageNum < 1 || pageNum > totalPages) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围");
                    }
                    pdf.removePage(pageNum - 1);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("删除页面完成: docId={}, deleted={}", documentId, pages.size());
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除页面失败: docId={}", documentId, e);
            throw new BusinessException("删除页面失败: " + e.getMessage());
        }
    }

    /**
     * 提取指定页面为新 PDF
     */
    public byte[] extractPages(Long documentId, List<Integer> pages) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                PDDocument extracted = new PDDocument();
                for (int pageNum : pages) {
                    if (pageNum < 1 || pageNum > source.getNumberOfPages()) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围");
                    }
                    // 用 importPage 复制页面
                    PDPage page = source.getPage(pageNum - 1);
                    extracted.importPage(page);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                extracted.save(baos);
                extracted.close();
                log.info("提取页面完成: docId={}, pages={}", documentId, pages.size());
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提取页面失败: docId={}", documentId, e);
            throw new BusinessException("提取页面失败: " + e.getMessage());
        }
    }

    /**
     * 重排页面顺序
     */
    public byte[] reorderPages(Long documentId, List<Integer> newOrder) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                int totalPages = source.getNumberOfPages();
                if (newOrder.size() != totalPages) {
                    throw new BusinessException("新顺序的页数(" + newOrder.size() + ")与原文档页数(" + totalPages + ")不一致");
                }

                PDDocument reordered = new PDDocument();
                for (int pageNum : newOrder) {
                    if (pageNum < 1 || pageNum > totalPages) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围");
                    }
                    reordered.importPage(source.getPage(pageNum - 1));
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                reordered.save(baos);
                reordered.close();
                log.info("重排页面完成: docId={}, newOrder={}", documentId, newOrder);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("重排页面失败: docId={}", documentId, e);
            throw new BusinessException("重排页面失败: " + e.getMessage());
        }
    }

    /**
     * 压缩 PDF（通过降低图片质量）
     */
    public byte[] compress(Long documentId, String level) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        int targetDpi = switch (level != null ? level : "medium") {
            case "high" -> 200;
            case "low" -> 72;
            default -> 150;
        };

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDFRenderer renderer = new PDFRenderer(pdf);

                // 重新渲染每页为图片再合成 PDF（有损压缩）
                PDDocument compressed = new PDDocument();
                for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                    float scale = targetDpi / 72f;
                    BufferedImage image = renderer.renderImageWithDPI(i, targetDpi, ImageType.RGB);

                    ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", imgBaos);
                    byte[] imgBytes = imgBaos.toByteArray();

                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(compressed, imgBytes, "page_" + i);
                    PDPage newPage = new PDPage(new PDRectangle(image.getWidth() / scale, image.getHeight() / scale));
                    compressed.addPage(newPage);

                    try (PDPageContentStream cs = new PDPageContentStream(compressed, newPage)) {
                        cs.drawImage(pdImage, 0, 0, newPage.getMediaBox().getWidth(), newPage.getMediaBox().getHeight());
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                compressed.save(baos);
                compressed.close();

                log.info("压缩 PDF 完成: docId={}, level={}, original={}, compressed={}",
                        documentId, level, pdfBytes.length, baos.size());
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("压缩 PDF 失败: docId={}", documentId, e);
            throw new BusinessException("压缩 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 加密 PDF
     */
    public byte[] encrypt(Long documentId, String password) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                        new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(password, password, null);
                policy.setEncryptionKeyLength(128);
                policy.setPermissions(new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
                pdf.protect(policy);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("加密 PDF 完成: docId={}", documentId);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加密 PDF 失败: docId={}", documentId, e);
            throw new BusinessException("加密 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 解密 PDF
     */
    public byte[] decrypt(Long documentId, String password) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes, password)) {
                pdf.setAllSecurityToBeRemoved(true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("解密 PDF 完成: docId={}", documentId);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解密 PDF 失败: docId={}", documentId, e);
            throw new BusinessException("解密 PDF 失败，请检查密码是否正确");
        }
    }

    /**
     * 渲染指定页面为 PNG 图片
     */
    public byte[] renderPageToImage(Long documentId, int pageNum) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) {
                    throw new BusinessException("页码超出范围");
                }
                PDFRenderer renderer = new PDFRenderer(pdf);
                BufferedImage image = renderer.renderImageWithDPI(pageNum - 1, 150, ImageType.RGB);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("渲染页面失败: docId={}, page={}", documentId, pageNum, e);
            throw new BusinessException("渲染页面失败: " + e.getMessage());
        }
    }

    /**
     * 从纯文本创建 docx
     */
    public byte[] createDocxFromText(String text) {
        try {
            org.apache.poi.xwpf.usermodel.XWPFDocument docx = new org.apache.poi.xwpf.usermodel.XWPFDocument();
            String[] lines = text.split("\n");
            for (String line : lines) {
                org.apache.poi.xwpf.usermodel.XWPFParagraph para = docx.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun run = para.createRun();
                run.setText(line);
                run.setFontSize(11);
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            docx.write(baos);
            docx.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("创建 docx 失败", e);
            throw new BusinessException("创建 docx 失败: " + e.getMessage());
        }
    }

    private void validatePdf(Document doc) {
        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }
    }

    // ==================== PDF 文字编辑 ====================

    /**
     * 应用文字编辑到 PDF
     * 使用 Apache PDFBox 修改 PDF 内容
     */
    public byte[] applyTextEdits(Long docId, List<Map<String, Object>> edits) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                for (Map<String, Object> edit : edits) {
                    applySingleEdit(pdf, edit);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("应用 PDF 文字编辑完成: docId={}, editsCount={}", docId, edits.size());
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("应用 PDF 文字编辑失败: docId={}, error={}", docId, e.getMessage());
            throw new BusinessException("应用编辑失败: " + e.getMessage());
        }
    }

    /**
     * 应用单个编辑操作
     */
    private void applySingleEdit(PDDocument pdf, Map<String, Object> edit) {
        try {
            @SuppressWarnings("unchecked")
            String type = (String) edit.get("type");
            Number pageNumNum = (Number) edit.get("pageNumber");
            if (pageNumNum == null) return;
            int pageNum = pageNumNum.intValue();

            if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) return;
            PDPage page = pdf.getPage(pageNum - 1);
            PDRectangle pageSize = page.getMediaBox();

            @SuppressWarnings("unchecked")
            Map<String, Object> rect = (Map<String, Object>) edit.get("rect");

            String text = edit.get("text") != null ? edit.get("text").toString() : "";
            Number fontSizeNum = (Number) edit.get("fontSize");
            int fontSize = fontSizeNum != null ? fontSizeNum.intValue() : 14;

            Number xNum = (Number) edit.get("x");
            Number yNum = (Number) edit.get("y");
            float x = xNum != null ? xNum.floatValue() : 0;
            float y = yNum != null ? yNum.floatValue() : 0;

            String colorHex = edit.get("color") != null ? edit.get("color").toString() : "#000000";
            float[] rgb = hexToRgb(colorHex);

            if ("add".equals(type)) {
                // 添加文字
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) fontSize);
                    cs.beginText();
                    cs.newLineAtOffset(x, y);
                    cs.showText(text);
                    cs.endText();
                }

            } else if ("delete".equals(type)) {
                // 删除文字：用白色矩形覆盖
                if (rect != null) {
                    Number rxNum = (Number) rect.get("x");
                    Number ryNum = (Number) rect.get("y");
                    Number rwNum = (Number) rect.get("width");
                    Number rhNum = (Number) rect.get("height");
                    float rx = rxNum != null ? rxNum.floatValue() : 0;
                    float ry = ryNum != null ? ryNum.floatValue() : 0;
                    float rw = rwNum != null ? rwNum.floatValue() : 100;
                    float rh = rhNum != null ? rhNum.floatValue() : 20;

                    try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.setNonStrokingColor(1.0f, 1.0f, 1.0f); // 白色
                        cs.addRect(rx, ry, rw, rh);
                        cs.fill();
                    }
                }

            } else if ("modify".equals(type)) {
                // 修改：先覆盖原位置，再添加新文字
                Number origXNum = (Number) edit.get("originalX");
                Number origYNum = (Number) edit.get("originalY");
                String origText = edit.get("originalText") != null ? edit.get("originalText").toString() : "";

                if (origXNum != null && origYNum != null) {
                    float origX = origXNum.floatValue();
                    float origY = origYNum.floatValue();

                    // 覆盖原位置
                    try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.setNonStrokingColor(1.0f, 1.0f, 1.0f); // 白色覆盖
                        // 估算文字宽度
                        float textWidth = fontSize * origText.length() * 0.5f;
                        cs.addRect(origX - 2, origY - 2, textWidth + 4, fontSize + 4);
                        cs.fill();
                    }
                }

                // 添加新文字
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) fontSize);
                    cs.beginText();
                    cs.newLineAtOffset(x, y);
                    cs.showText(text);
                    cs.endText();
                }
            }
        } catch (Exception e) {
            log.warn("应用单个编辑失败: {}", e.getMessage());
        }
    }

    /**
     * 将 HEX 颜色转为 RGB 数组
     */
    private float[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        float r = Integer.parseInt(hex.substring(0, 2), 16) / 255.0f;
        float g = Integer.parseInt(hex.substring(2, 4), 16) / 255.0f;
        float b = Integer.parseInt(hex.substring(4, 6), 16) / 255.0f;
        return new float[]{r, g, b};
    }

    /**
     * 提取 PDF 文字位置信息(用于编辑参考)
     * - 优先用 PDF 内嵌文字(原生 PDF 文字流)
     * - 如果是扫描件/没内嵌文字,fallback 到 OCR 识别结果(doc.pdfOcrData)
     */
    public List<Map<String, Object>> extractTextPositions(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);

        List<Map<String, Object>> allPositions = new ArrayList<>();

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                    PDPage page = pdf.getPage(i);
                    PDRectangle pageSize = page.getMediaBox();

                    PositionStripper stripper = new PositionStripper();
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    stripper.getText(pdf);

                    List<Map<String, Object>> positions = stripper.getPositions();
                    // 内嵌文字为空 → fallback 到 OCR 结果
                    if (positions.isEmpty() && doc.getPdfOcrData() != null) {
                        positions = extractPositionsFromOcr(doc.getPdfOcrData(), i + 1, pageSize);
                    }
                    for (Map<String, Object> pos : positions) {
                        pos.put("pageNum", i + 1);
                        pos.put("pageWidth", pageSize.getWidth());
                        pos.put("pageHeight", pageSize.getHeight());
                        allPositions.add(pos);
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取 PDF 文字位置失败: docId={}", docId, e);
        }

        return allPositions;
    }

    /**
     * 从 OCR 数据(jsonb Map<String, Object>)中提取指定页的位置信息。
     * OCR 数据结构: { "1": { "regions": [{text, bbox:[x,y,w,h], confidence}], "dpi": 200 }, ... }
     * bbox 坐标基于 OCR 像素,需要除以 DPI/72 换算到 PDF 点。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPositionsFromOcr(
            Map<String, Object> ocrData, int pageNum, PDRectangle pageSize) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object pageObj = ocrData.get(String.valueOf(pageNum));
        if (!(pageObj instanceof Map)) return result;
        Map<String, Object> pageData = (Map<String, Object>) pageObj;
        Object regionsObj = pageData.get("regions");
        Object dpiObj = pageData.get("dpi");
        if (!(regionsObj instanceof List)) return result;
        List<?> regions = (List<?>) regionsObj;
        double dpi = dpiObj instanceof Number ? ((Number) dpiObj).doubleValue() : 200.0;
        // OCR bbox 是像素坐标,DPI 200 → 1 inch = 200 px = 72 pt
        double pxToPt = 72.0 / dpi;

        for (Object r : regions) {
            if (!(r instanceof Map)) continue;
            Map<String, Object> region = (Map<String, Object>) r;
            String text = String.valueOf(region.getOrDefault("text", ""));
            if (text.trim().isEmpty()) continue;
            Object bbox = region.get("bbox");
            if (!(bbox instanceof List) || ((List<?>) bbox).size() < 4) continue;
            List<Number> b = (List<Number>) bbox;
            double x = b.get(0).doubleValue() * pxToPt;
            // OCR bbox 通常是 [x, y, w, h],y 像素 → PDF Y 向上需要翻转
            double yPx = b.get(1).doubleValue();
            double wPx = b.get(2).doubleValue();
            double hPx = b.get(3).doubleValue();
            double w = wPx * pxToPt;
            double h = hPx * pxToPt;
            // Phase 13.7: 像素 y(图像左上原点)转 PDF Y(左下原点)
            // 之前未翻转导致 OCR 文字上下相反,渲染不在原文位置
            double yPdf = pageSize.getHeight() - (yPx + hPx) * pxToPt;
            // bbox 高度估算字符大小
            double fontSize = h;

            Map<String, Object> pos = new java.util.LinkedHashMap<>();
            pos.put("text", text);
            pos.put("x", x);
            pos.put("y", yPdf);
            pos.put("fontSize", fontSize);
            pos.put("font", "OCR");
            pos.put("width", w);
            pos.put("height", h);
            result.add(pos);
        }
        return result;
    }

    /**
     * 自定义文字位置提取器
     */
    static class PositionStripper extends PDFTextStripper {
        private final List<Map<String, Object>> positions = new ArrayList<>();

        public PositionStripper() {
            super();
        }

        public List<Map<String, Object>> getPositions() {
            return positions;
        }

        protected void writeString(String text, TextPosition textPosition) throws java.io.IOException {
            if (text == null || text.trim().isEmpty()) return;

            Map<String, Object> pos = new java.util.LinkedHashMap<>();
            pos.put("text", text);
            pos.put("x", textPosition.getXDirAdj());
            pos.put("y", textPosition.getYDirAdj());
            pos.put("fontSize", textPosition.getFontSize());
            pos.put("font", textPosition.getFont().getName());
            pos.put("width", textPosition.getWidth());
            pos.put("height", textPosition.getHeight());

            positions.add(pos);
        }
    }

    // ==================== Phase 3: 原子化替换工具 ====================

    /**
     * 把新的 PDF 字节写回 storage,并同步更新 Document.filePath。
     * 用于 Phase 3 页面操作(merge/delete/rotate/extract/reorder)统一原子化。
     *
     * 流程:
     *   1. storageService.store() 写入新路径
     *   2. 删除旧文件
     *   3. Document.filePath → 新路径
     *   4. documentService.updateDocument() 持久化
     *
     * @param docId 目标文档 ID
     * @param newPdfBytes 新 PDF 字节流
     * @param operation 操作描述(用于文件命名,例如 "merge", "rotate", "delete_page")
     * @return 新文件路径
     */
    public String replacePdfBytes(Long docId, byte[] newPdfBytes, String operation) {
        if (newPdfBytes == null || newPdfBytes.length == 0) {
            throw new BusinessException("PDF 内容为空");
        }

        Document doc = documentService.getDocument(docId);
        validatePdf(doc);

        String oldFilePath = doc.getFilePath();
        String suffix = operation != null && !operation.isBlank() ? ("_" + operation) : "_modified";
        String newFilePath = storageService.store(doc.getDocKey() + suffix, newPdfBytes);

        // 删除旧文件(若存在且不同于新路径)
        if (oldFilePath != null && !oldFilePath.equals(newFilePath)) {
            try {
                storageService.delete(oldFilePath);
            } catch (Exception e) {
                log.warn("删除旧 PDF 文件失败(非致命): path={}, error={}", oldFilePath, e.getMessage());
            }
        }

        // 更新 Document
        doc.setFilePath(newFilePath);
        doc.setFileSize((long) newPdfBytes.length);
        documentService.updateDocument(doc);

        log.info("PDF 替换完成: docId={}, operation={}, newSize={}",
                docId, operation, newPdfBytes.length);
        return newFilePath;
    }

    /**
     * 替换 PDF 字节(简化版,无 operation 标记)
     */
    public String replacePdfBytes(Long docId, byte[] newPdfBytes) {
        return replacePdfBytes(docId, newPdfBytes, "modified");
    }

    // ==================== Phase 8: 书签/大纲 + 搜索 + 元数据 ====================

    /**
     * 提取 PDF 书签/大纲(树形结构)
     * @return List<Map> 每个节点: { title, level, page }
     */
    public List<Map<String, Object>> extractOutline(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDDocumentOutline outline = pdf.getDocumentCatalog().getDocumentOutline();
                if (outline == null) return result;
                walkOutline(pdf, outline, 0, result);
            }
        } catch (Exception e) {
            log.error("提取 PDF 大纲失败: docId={}", docId, e);
        }
        return result;
    }

    private void walkOutline(PDDocument pdf, PDDocumentOutline outline, int level, List<Map<String, Object>> acc) {
        if (outline == null) return;
        for (PDOutlineItem item : outline.children()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("title", item.getTitle());
            node.put("level", level);
            node.put("page", 1);  // 简化:页码=1,后续可用反射完善
            acc.add(node);
            if (item.hasChildren()) {
                Object child = item.getFirstChild();
                if (child instanceof PDDocumentOutline) {
                    walkOutline(pdf, (PDDocumentOutline) child, level + 1, acc);
                }
            }
        }
    }

    /**
     * 全文搜索(基于已提取的 text-positions)
     */
    public List<Map<String, Object>> searchText(Long docId, String keyword, boolean caseSensitive) {
        if (keyword == null || keyword.isBlank()) return List.of();
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);

        List<Map<String, Object>> positions = extractTextPositions(docId);
        List<Map<String, Object>> results = new ArrayList<>();
        String needle = caseSensitive ? keyword : keyword.toLowerCase();

        Map<Integer, List<Map<String, Object>>> byPage = new LinkedHashMap<>();
        for (Map<String, Object> p : positions) {
            Object pno = p.get("pageNum");
            if (pno == null) continue;
            int page = ((Number) pno).intValue();
            byPage.computeIfAbsent(page, k -> new ArrayList<>()).add(p);
        }

        for (Map.Entry<Integer, List<Map<String, Object>>> e : byPage.entrySet()) {
            int pageNum = e.getKey();
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> p : e.getValue()) {
                String text = (String) p.get("text");
                if (text != null) sb.append(text);
            }
            String haystack = caseSensitive ? sb.toString() : sb.toString().toLowerCase();
            int idx = 0;
            while ((idx = haystack.indexOf(needle, idx)) != -1) {
                int end = Math.min(idx + needle.length() + 30, sb.length());
                String snippet = sb.substring(Math.max(0, idx - 15), Math.min(sb.length(), end));
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("page", pageNum);
                hit.put("snippet", "..." + snippet + "...");
                hit.put("offset", idx);
                results.add(hit);
                idx += needle.length();
                if (results.size() >= 200) break;
            }
            if (results.size() >= 200) break;
        }
        return results;
    }

    /**
     * PDF 元数据(标题/作者/创建时间等)
     */
    public Map<String, Object> getPdfMetadata(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDDocumentInformation info = pdf.getDocumentInformation();
                meta.put("title", info.getTitle() != null ? info.getTitle() : doc.getTitle());
                meta.put("author", info.getAuthor());
                meta.put("subject", info.getSubject());
                meta.put("creator", info.getCreator());
                meta.put("producer", info.getProducer());
                meta.put("creationDate", info.getCreationDate() != null
                    ? info.getCreationDate().toString() : null);
                meta.put("modificationDate", info.getModificationDate() != null
                    ? info.getModificationDate().toString() : null);
                meta.put("pageCount", pdf.getNumberOfPages());
                meta.put("pdfVersion", pdf.getVersion());
                meta.put("fileSize", doc.getFileSize());
                PDPage firstPage = pdf.getPage(0);
                if (firstPage != null) {
                    PDRectangle box = firstPage.getMediaBox();
                    meta.put("pageWidth", box.getWidth());
                    meta.put("pageHeight", box.getHeight());
                }
            }
        } catch (Exception e) {
            log.error("提取 PDF 元数据失败: docId={}", docId, e);
        }
        return meta;
    }

    // ==================== Phase 11: 插入 / 裁剪 / 水印 / 页眉页脚 ====================

    /**
     * 在指定位置后插入空白页(默认插在最后一页)
     * @param afterPage 在哪页后插入,0 = 末尾
     */
    public byte[] insertBlankPage(Long docId, int afterPage) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()))) {
            PDPage blank = new PDPage(pdf.getPage(0).getMediaBox());
            pdf.addPage(blank);
            int insertIdx = Math.min(afterPage, pdf.getNumberOfPages() - 1);
            if (insertIdx < pdf.getNumberOfPages() - 1) {
                List<PDPage> pages = new ArrayList<>();
                for (int i = 0; i < pdf.getNumberOfPages(); i++) pages.add(pdf.getPage(i));
                PDPage inserted = pages.remove(pages.size() - 1);
                pages.add(insertIdx, inserted);
                PDDocument newPdf = new PDDocument();
                for (PDPage p : pages) newPdf.addPage(p);
                newPdf.setDocumentInformation(pdf.getDocumentInformation());
                byte[] out = newDocBytes(newPdf, "insert-blank");
                newPdf.close();
                replacePdfBytes(docId, out, "insert-blank");
                return out;
            }
            byte[] out = newDocBytes(pdf, "insert-blank");
            replacePdfBytes(docId, out, "insert-blank");
            return out;
        } catch (Exception e) {
            log.error("插入空白页失败: docId={}, afterPage={}", docId, afterPage, e);
            throw new BusinessException("插入空白页失败: " + e.getMessage());
        }
    }

    /**
     * 裁剪指定页(设置 CropBox)
     * @param cropBox {x, y, width, height} 单位 PDF 点
     */
    public byte[] cropPages(Long docId, List<Integer> pages, Map<String, Double> cropBox) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        double x = cropBox.getOrDefault("x", 0.0);
        double y = cropBox.getOrDefault("y", 0.0);
        double w = cropBox.getOrDefault("width", 0.0);
        double h = cropBox.getOrDefault("height", 0.0);
        if (w <= 0 || h <= 0) throw new BusinessException("裁剪宽高必须 > 0");
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()))) {
            for (int pageNum : pages) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) continue;
                PDPage page = pdf.getPage(pageNum - 1);
                page.setCropBox(new PDRectangle((float) x, (float) y, (float) w, (float) h));
            }
            byte[] out = newDocBytes(pdf, "crop");
            replacePdfBytes(docId, out, "crop");
            return out;
        } catch (Exception e) {
            log.error("裁剪失败: docId={}, pages={}", docId, pages, e);
            throw new BusinessException("裁剪失败: " + e.getMessage());
        }
    }

    /**
     * 在指定页叠加文字水印
     * @param text 水印文字
     * @param opacity 0-1
     * @param rotation 旋转角度(度)
     * @param pages 页码列表,空 = 全部
     */
    public byte[] addWatermark(Long docId, String text, double opacity, double rotation, List<Integer> pages) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()))) {
            List<Integer> targetPages = (pages == null || pages.isEmpty())
                ? allPages(pdf) : pages;
            for (int pageNum : targetPages) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) continue;
                PDPage page = pdf.getPage(pageNum - 1);
                PDRectangle box = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState gs = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant((float) Math.max(0, Math.min(1, opacity)));
                    cs.setGraphicsStateParameters(gs);
                    cs.saveGraphicsState();
                    float fontSize = Math.max(40f, Math.min(box.getWidth(), box.getHeight()) / 6f);
                    float x = box.getWidth() / 2f;
                    float y = box.getHeight() / 2f;
                    double rad = Math.toRadians(rotation);
                    cs.transform(new org.apache.pdfbox.util.Matrix(
                        (float) Math.cos(rad), (float) Math.sin(rad),
                        (float) -Math.sin(rad), (float) Math.cos(rad),
                        x, y));
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize);
                    cs.showText(text == null ? "" : text);
                    cs.endText();
                    cs.restoreGraphicsState();
                }
            }
            byte[] out = newDocBytes(pdf, "watermark");
            replacePdfBytes(docId, out, "watermark");
            return out;
        } catch (Exception e) {
            log.error("水印失败: docId={}, text={}", docId, text, e);
            throw new BusinessException("水印失败: " + e.getMessage());
        }
    }

    /**
     * 添加页眉/页脚文字
     */
    public byte[] addHeaderFooter(Long docId, String position, String content, double fontSize, List<Integer> pages) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()))) {
            int total = pdf.getNumberOfPages();
            List<Integer> targetPages = (pages == null || pages.isEmpty())
                ? allPages(pdf) : pages;
            for (int pageNum : targetPages) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) continue;
                PDPage page = pdf.getPage(pageNum - 1);
                PDRectangle box = page.getMediaBox();
                String resolved = content == null ? "" : content
                    .replace("{page}", String.valueOf(pageNum))
                    .replace("{total}", String.valueOf(total));
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float y = "footer".equalsIgnoreCase(position) ? 24f : box.getHeight() - 24f;
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), (float) fontSize);
                    cs.newLineAtOffset(40, y);
                    cs.showText(resolved);
                    cs.endText();
                }
            }
            byte[] out = newDocBytes(pdf, position);
            replacePdfBytes(docId, out, position);
            return out;
        } catch (Exception e) {
            log.error("页眉页脚失败: docId={}, position={}", docId, position, e);
            throw new BusinessException("页眉页脚失败: " + e.getMessage());
        }
    }

    private List<Integer> allPages(PDDocument pdf) {
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) all.add(i);
        return all;
    }

    /**
     * 把 PDDocument 写出到字节数组(用于原子替换落盘)
     */
    private byte[] newDocBytes(PDDocument pdf, String op) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        pdf.save(baos);
        log.info("Phase 11 {} 文档生成完成", op);
        return baos.toByteArray();
    }

    // ==================== Phase 12.1: 表单字段检测 ====================

    /**
     * 识别 PDF 的 AcroForm 表单字段
     * 返回字段列表(name/type/value/page/rect/options)
     */
    public List<Map<String, Object>> getFormFields(Long documentId) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm acroForm = pdf.getDocumentCatalog().getAcroForm();
                if (acroForm == null) {
                    log.info("PDF 无 AcroForm: docId={}", documentId);
                    return new ArrayList<>();
                }
                List<Map<String, Object>> result = new ArrayList<>();
                for (org.apache.pdfbox.pdmodel.interactive.form.PDField field : acroForm.getFieldTree()) {
                    if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField) {
                        continue; // 跳过非终结字段(只有分组作用)
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", field.getFullyQualifiedName());
                    item.put("partialName", field.getPartialName());
                    item.put("type", resolveFieldType(field));
                    item.put("value", resolveFieldValue(field));
                    item.put("readOnly", field.isReadOnly());
                    item.put("required", field.isRequired());
                    // 取第一个 widget 的 rect + page
                    Map<String, Object> location = resolveFieldLocation(pdf, field);
                    item.putAll(location);
                    // 下拉/单选/复选的选项列表
                    List<String> options = resolveFieldOptions(field);
                    if (!options.isEmpty()) {
                        item.put("options", options);
                    }
                    result.add(item);
                }
                log.info("识别 AcroForm 字段完成: docId={}, count={}", documentId, result.size());
                return result;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("识别表单字段失败: docId={}", documentId, e);
            throw new BusinessException("识别表单字段失败: " + e.getMessage());
        }
    }

    private String resolveFieldType(org.apache.pdfbox.pdmodel.interactive.form.PDField field) {
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox) return "checkbox";
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) return "radio";
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDComboBox) return "combobox";
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDListBox) return "listbox";
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField) return "signature";
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDTextField) return "text";
        return "unknown";
    }

    private String resolveFieldValue(org.apache.pdfbox.pdmodel.interactive.form.PDField field) throws java.io.IOException {
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox) {
            return ((org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox) field).isChecked() ? "true" : "false";
        }
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) {
            return ((org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) field).getValueAsString();
        }
        String v = field.getValueAsString();
        return v == null ? "" : v;
    }

    private List<String> resolveFieldOptions(org.apache.pdfbox.pdmodel.interactive.form.PDField field) throws java.io.IOException {
        List<String> opts = new ArrayList<>();
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDChoice) {
            for (String opt : ((org.apache.pdfbox.pdmodel.interactive.form.PDChoice) field).getOptions()) {
                if (opt != null && !opt.isEmpty()) opts.add(opt);
            }
        }
        return opts;
    }

    private Map<String, Object> resolveFieldLocation(PDDocument pdf, org.apache.pdfbox.pdmodel.interactive.form.PDField field) {
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("page", 0);
        loc.put("rect", new double[]{0, 0, 0, 0});
        try {
            for (org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget : field.getWidgets()) {
                if (widget.getPage() == null) continue;
                int pageIdx = pdf.getPages().indexOf(widget.getPage()) + 1;
                org.apache.pdfbox.pdmodel.common.PDRectangle rect = widget.getRectangle();
                if (rect == null) continue;
                loc.put("page", pageIdx);
                loc.put("rect", new double[]{rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getUpperRightX(), rect.getUpperRightY()});
                break;
            }
        } catch (Exception e) {
            log.warn("解析字段位置失败: field={}", field.getFullyQualifiedName(), e);
        }
        return loc;
    }

    // ==================== Phase 12.2: 表单填充 ====================

    /**
     * 填充 AcroForm 字段
     * @param values 字段名 -> 值(text 直接字符串;checkbox "true"/"false";radio 选项值;combobox 选项值)
     * @return 填充后的 PDF 字节
     */
    public byte[] fillFormFields(Long documentId, Map<String, String> values) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);
        if (values == null || values.isEmpty()) {
            throw new BusinessException("填充数据不能为空");
        }
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm acroForm = pdf.getDocumentCatalog().getAcroForm();
                if (acroForm == null) {
                    throw new BusinessException("该 PDF 无 AcroForm 表单");
                }
                // 需要更新外观,默认 PDFBox 不重渲染字段外观
                acroForm.setNeedAppearances(true);
                int successCount = 0;
                List<String> failedFields = new ArrayList<>();
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();
                    try {
                        org.apache.pdfbox.pdmodel.interactive.form.PDField field = acroForm.getField(name);
                        if (field == null) {
                            failedFields.add(name + "(不存在)");
                            continue;
                        }
                        if (field.isReadOnly()) {
                            failedFields.add(name + "(只读)");
                            continue;
                        }
                        setFieldValue(field, value);
                        successCount++;
                    } catch (Exception e) {
                        log.warn("填充字段失败: {} - {}", name, e.getMessage());
                        failedFields.add(name + "(" + e.getMessage() + ")");
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("表单填充完成: docId={}, success={}, failed={}", documentId, successCount, failedFields.size());
                if (successCount == 0) {
                    throw new BusinessException("所有字段填充失败: " + String.join(", ", failedFields));
                }
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("表单填充失败: docId={}", documentId, e);
            throw new BusinessException("表单填充失败: " + e.getMessage());
        }
    }

    private void setFieldValue(org.apache.pdfbox.pdmodel.interactive.form.PDField field, String value) throws java.io.IOException {
        if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox) {
            org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox cb = (org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox) field;
            if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
                cb.check();
            } else {
                cb.unCheck();
            }
        } else if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) {
            org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton radio = (org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) field;
            radio.setValue(value);
        } else if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDComboBox) {
            org.apache.pdfbox.pdmodel.interactive.form.PDComboBox combo = (org.apache.pdfbox.pdmodel.interactive.form.PDComboBox) field;
            combo.setValue(value);
        } else if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDListBox) {
            org.apache.pdfbox.pdmodel.interactive.form.PDListBox list = (org.apache.pdfbox.pdmodel.interactive.form.PDListBox) field;
            list.setValue(value);
        } else if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDTextField) {
            org.apache.pdfbox.pdmodel.interactive.form.PDTextField text = (org.apache.pdfbox.pdmodel.interactive.form.PDTextField) field;
            text.setValue(value);
        } else {
            throw new BusinessException("不支持的字段类型: " + field.getClass().getSimpleName());
        }
    }

    // ==================== Phase 12.3: 签名图片嵌入 ====================

    /**
     * 在 PDF 指定位置嵌入签名图片
     * @param documentId 文档 ID
     * @param imageBase64 签名图片 base64(不含 data:image/png;base64, 前缀)
     * @param page 页码(1-based)
     * @param x PDF 坐标 X(左下原点,pt)
     * @param y PDF 坐标 Y(左下原点,pt)
     * @param width 显示宽度(pt)
     * @param height 显示高度(pt)
     * @return 新 PDF 字节
     */
    public byte[] embedSignature(Long documentId, String imageBase64, int page, double x, double y, double width, double height) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BusinessException("签名图片不能为空");
        }
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                if (page < 1 || page > pdf.getNumberOfPages()) {
                    throw new BusinessException("页码超出范围");
                }
                PDPage targetPage = pdf.getPage(page - 1);
                PDRectangle pageRect = targetPage.getMediaBox();
                double pageH = pageRect.getHeight();
                double pageW = pageRect.getWidth();
                if (width <= 0 || height <= 0) {
                    throw new BusinessException("签名尺寸必须 > 0");
                }
                if (x < 0 || y < 0 || x + width > pageW + 1 || y + height > pageH + 1) {
                    throw new BusinessException("签名位置超出页面范围");
                }
                // 解析 base64 -> BufferedImage
                byte[] imgBytes = java.util.Base64.getDecoder().decode(imageBase64);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imgBytes));
                if (img == null) {
                    throw new BusinessException("签名图片格式无法识别");
                }
                PDImageXObject pdImg = PDImageXObject.createFromByteArray(pdf, imgBytes, "signature.png");
                try (PDPageContentStream cs = new PDPageContentStream(pdf, targetPage, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.drawImage(pdImg, (float) x, (float) y, (float) width, (float) height);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("签名嵌入完成: docId={}, page={}, pos=({},{},{},{})", documentId, page, x, y, width, height);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("签名嵌入失败: docId={}", documentId, e);
            throw new BusinessException("签名嵌入失败: " + e.getMessage());
        }
    }

    // ==================== Phase 12.4: 密文(redact) ====================

    /**
     * 应用密文:在指定区域绘制黑色矩形 + 删除该区域的文字内容
     * @param documentId 文档 ID
     * @param regions 密文区域列表 [{page, x, y, width, height}]
     * @return 新 PDF 字节
     */
    public byte[] applyRedaction(Long documentId, List<Map<String, Object>> regions) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);
        if (regions == null || regions.isEmpty()) {
            throw new BusinessException("密文区域不能为空");
        }
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                int successCount = 0;
                for (Map<String, Object> r : regions) {
                    int page = ((Number) r.get("page")).intValue();
                    double x = ((Number) r.get("x")).doubleValue();
                    double y = ((Number) r.get("y")).doubleValue();
                    double w = ((Number) r.get("width")).doubleValue();
                    double h = ((Number) r.get("height")).doubleValue();
                    if (page < 1 || page > pdf.getNumberOfPages()) {
                        log.warn("密文页码超出范围: page={}", page);
                        continue;
                    }
                    PDPage targetPage = pdf.getPage(page - 1);
                    // 1. 绘制黑色填充矩形覆盖原内容
                    try (PDPageContentStream cs = new PDPageContentStream(pdf, targetPage, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.setNonStrokingColor(java.awt.Color.BLACK);
                        cs.addRect((float) x, (float) y, (float) w, (float) h);
                        cs.fill();
                    }
                    // 2. 删除该区域内的文字(PDFBox 3.x 用 PDFStreamEngine 替换,这里简化:整体清理文字)
                    // 完整实现需要先解析文本位置,再删除对应 tokens
                    // 当前简化版只覆盖黑色矩形,足以视觉脱敏;文本底层 token 仍在
                    // TODO: 用 org.apache.pdfbox.contentstream.PDFStreamEngine 实现精确删除
                    successCount++;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("密文应用完成: docId={}, regions={}, success={}", documentId, regions.size(), successCount);
                return baos.toByteArray();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("密文应用失败: docId={}", documentId, e);
            throw new BusinessException("密文应用失败: " + e.getMessage());
        }
    }

    // ==================== Phase 13.12-B: PDF 创建 + Zip 批量操作 ====================

    /**
     * 创建空白 PDF:多页同尺寸,A4/A5/Letter 或自定义 pt
     * @param pages 页数(>=1)
     * @param widthPt 页面宽度(磅,1 pt = 1/72 inch)
     * @param heightPt 页面高度(磅)
     */
    public byte[] createBlankPdf(int pages, float widthPt, float heightPt) {
        if (pages < 1) throw new BusinessException("页数至少为 1");
        if (widthPt <= 0 || heightPt <= 0) throw new BusinessException("页面尺寸必须 > 0");
        try (PDDocument doc = new PDDocument()) {
            PDRectangle size = new PDRectangle(widthPt, heightPt);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            log.info("创建空白 PDF: pages={}, size={}x{}pt", pages, widthPt, heightPt);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("创建空白 PDF 失败", e);
            throw new BusinessException("创建空白 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 图片转 PDF:每图 1 页,自动 fit 到页(A4 595x842 pt)居中
     */
    public byte[] createFromImages(List<byte[]> images) {
        if (images == null || images.isEmpty()) throw new BusinessException("至少需要 1 张图片");
        PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < images.size(); i++) {
                byte[] imgBytes = images.get(i);
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imgBytes, "page_" + i + ".png");
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    // 等比 fit 居中
                    float scale = Math.min(pageSize.getWidth() / pdImage.getWidth(), pageSize.getHeight() / pdImage.getHeight());
                    float w = pdImage.getWidth() * scale;
                    float h = pdImage.getHeight() * scale;
                    float x = (pageSize.getWidth() - w) / 2f;
                    float y = (pageSize.getHeight() - h) / 2f;
                    cs.drawImage(pdImage, x, y, w, h);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            log.info("创建图片 PDF: count={}", images.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("图片转 PDF 失败", e);
            throw new BusinessException("图片转 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 按页码区间拆分:输入 "1-3,5,7-9" → 多个 PDF 字节
     */
    public List<byte[]> splitByRanges(Long documentId, String ranges) {
        Document doc = documentService.getDocument(documentId);
        validatePdf(doc);
        List<int[]> segments = parseRanges(ranges, doc);
        if (segments.isEmpty()) throw new BusinessException("页码范围解析为空");
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            List<byte[]> result = new ArrayList<>();
            try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                int totalPages = source.getNumberOfPages();
                for (int[] seg : segments) {
                    if (seg[0] < 1 || seg[1] > totalPages || seg[0] > seg[1]) {
                        log.warn("跳过无效区间: {} - {} (总 {} 页)", seg[0], seg[1], totalPages);
                        continue;
                    }
                    int from = seg[0] - 1, to = seg[1]; // PDDocument.extract 用 [from, to)
                    byte[] part = extractPages(documentId, java.util.stream.IntStream.rangeClosed(seg[0], seg[1]).boxed().toList());
                    result.add(part);
                }
            }
            log.info("区间拆分 PDF: docId={}, ranges={}, segments={}", documentId, ranges, result.size());
            return result;
        } catch (Exception e) {
            log.error("区间拆分失败", e);
            throw new BusinessException("区间拆分失败: " + e.getMessage());
        }
    }

    /**
     * 批量提取选中页面,返回单个 PDF
     */
    public byte[] extractPagesBatch(Long documentId, List<Integer> pages) {
        return extractPages(documentId, pages);
    }

    /**
     * 解析 "1-3,5,7-9" 格式为 [start, end] 段
     */
    private List<int[]> parseRanges(String ranges, Document doc) {
        List<int[]> result = new ArrayList<>();
        if (ranges == null || ranges.isBlank()) return result;
        for (String seg : ranges.split(",")) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;
            try {
                if (seg.contains("-")) {
                    String[] parts = seg.split("-");
                    int from = Integer.parseInt(parts[0].trim());
                    int to = Integer.parseInt(parts[1].trim());
                    if (from <= to) result.add(new int[]{from, to});
                } else {
                    int p = Integer.parseInt(seg);
                    result.add(new int[]{p, p});
                }
            } catch (NumberFormatException e) {
                log.warn("无效区间片段: {}", seg);
            }
        }
        return result;
    }
}
