package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
     * 提取 PDF 文字位置信息（用于编辑参考）
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
                    String text = stripper.getText(pdf);

                    List<Map<String, Object>> positions = stripper.getPositions();
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
}
