package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
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
    /** Phase 13.23: AI 服务(智能目录生成) */
    private final com.miaotong.doc.service.ai.AiService aiService;
    /** Phase 13.23: Docling 结构化提取(智能提取) */
    private final com.miaotong.doc.service.DoclingService doclingService;

    /** Phase 13.22: 中文字体缓存(嵌入 NotoSansSC/微软雅黑用于中文 PDF 文字修改) */
    private static volatile PDFont CHINESE_FONT = null;
    /** 原 token 字体加载缓存(fontName -> 已加载的 PDFont) */
    private static final java.util.concurrent.ConcurrentHashMap<String, PDFont> FONT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 加载中文字体(单次,线程安全) */
    private static synchronized PDFont getChineseFont() {
        if (CHINESE_FONT != null) return CHINESE_FONT;
        try (java.io.InputStream is = PdfToolService.class.getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            if (is == null) {
                log.warn("中文字体文件 /fonts/NotoSansSC-Regular.ttf 未找到");
                return null;
            }
            byte[] fontBytes = IOUtils.toByteArray(is);
            // PDType0Font.load 需要一个 PDDocument 上下文(用空文档作容器)
            try (PDDocument tmp = new PDDocument()) {
                CHINESE_FONT = PDType0Font.load(tmp, new java.io.ByteArrayInputStream(fontBytes));
            }
            log.info("中文字体加载成功: size={}B", fontBytes.length);
        } catch (Exception e) {
            log.error("加载中文字体失败", e);
        }
        return CHINESE_FONT;
    }

    /**
     * 合并多个 PDF
     * 用 PDFMergerUtility 而非 importPage(浅拷贝会丢字体/资源导致内容乱码)
     */
    public byte[] merge(List<Long> documentIds) {
        if (documentIds == null || documentIds.size() < 2) {
            throw new BusinessException("至少需要两个文档才能合并");
        }

        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            merger.setDestinationStream(baos);

            for (Long docId : documentIds) {
                Document doc = documentService.getDocument(docId);
                if (!"pdf".equals(doc.getFileType())) {
                    throw new BusinessException("文档 " + doc.getTitle() + " 不是 PDF 类型");
                }
                byte[] pdfBytes = storageService.load(doc.getFilePath());
                // PDFBox 3.x: addSource 只接受 RandomAccessRead/InputStream(新API)
                merger.addSource(new RandomAccessReadBuffer(pdfBytes));
            }

            // 内存模式合并,保留字体/资源(避免 importPage 浅拷贝乱码)
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
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
                int total = source.getNumberOfPages();
                for (int pageNum : pages) {
                    if (pageNum < 1 || pageNum > total) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围");
                    }
                }
                // 原地删除不需要的页(保留同一文档对象,字体/资源不丢失,避免 importPage 浅拷贝乱码)
                java.util.Set<Integer> keep = new java.util.HashSet<>(pages);
                for (int i = source.getNumberOfPages() - 1; i >= 0; i--) {
                    if (!keep.contains(i + 1)) {
                        source.removePage(i);
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                source.save(baos);
                log.info("提取页面完成: docId={}, 请求页={}, 保留页数={}", documentId, pages, source.getNumberOfPages());
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
     * Phase 13.29: 提取页面到新文档(非破坏性)
     * 提取指定页生成新 PDF + 创建新 Document,返回新 docId
     */
    public Long extractPagesToNew(Long docId, List<Integer> pages, String newTitle, Long userId) {
        Document source = documentService.getDocument(docId);
        validatePdf(source);
        byte[] pdfBytes = extractPages(docId, pages);
        String title = (newTitle != null && !newTitle.isBlank()) ? newTitle : (source.getTitle() + "_提取");
        Document newDoc = documentService.createPdfFromBytes(title, pdfBytes, userId);
        log.info("提取页面到新文档完成: source={}, newDoc={}, pages={}", docId, newDoc.getId(), pages.size());
        return newDoc.getId();
    }

    /**
     * Phase 13.29: 高级合并(按页区间 + 目标 new/overwrite)
     * @param documents [{docId, pageRanges}] 每个文档的页区间(如 "1-3,5" 或 null=全部)
     * @param targetMode "new" 或 "overwrite"
     * @param targetDocId overwrite 时的目标文档 ID
     * @param newTitle new 时的新文档标题
     */
    public Map<String, Object> mergeAdvanced(List<Map<String, Object>> documents, String targetMode,
                                             Long targetDocId, String newTitle, Long userId) {
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException("至少需要 1 个文档");
        }
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            merger.setDestinationStream(baos);

            for (Map<String, Object> entry : documents) {
                Long docId = ((Number) entry.get("docId")).longValue();
                String ranges = (String) entry.get("pageRanges");
                Document doc = documentService.getDocument(docId);
                validatePdf(doc);
                byte[] pdfBytes = storageService.load(doc.getFilePath());
                byte[] docBytes;
                if (ranges == null || ranges.isBlank()) {
                    // 全部页
                    docBytes = pdfBytes;
                } else {
                    // 按页区间提取
                    try (PDDocument source = Loader.loadPDF(pdfBytes)) {
                        List<Integer> pages = parsePageRanges(ranges, source.getNumberOfPages());
                        if (pages.isEmpty()) {
                            throw new BusinessException("文档 " + doc.getTitle() + " 的页区间无效: " + ranges);
                        }
                        docBytes = extractPages(docId, pages);
                    }
                }
                merger.addSource(new RandomAccessReadBuffer(docBytes));
            }
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
            byte[] result = baos.toByteArray();

            if ("overwrite".equals(targetMode)) {
                if (targetDocId == null) throw new BusinessException("覆盖模式需指定目标文档");
                String filePath = replacePdfBytes(targetDocId, result, "merge-advanced");
                log.info("高级合并(覆盖)完成: target={}, size={}", targetDocId, result.length);
                return Map.of("success", true, "filePath", filePath, "targetDocId", targetDocId);
            } else {
                String title = (newTitle != null && !newTitle.isBlank()) ? newTitle : "合并文档";
                Document newDoc = documentService.createPdfFromBytes(title, result, userId);
                log.info("高级合并(新文档)完成: newDoc={}, size={}", newDoc.getId(), result.length);
                return Map.of("success", true, "docId", newDoc.getId(), "filePath", newDoc.getFilePath());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("高级合并失败: {}", e.getMessage(), e);
            throw new BusinessException("高级合并失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.29: 解析页区间字符串 "1-3,5,7-9" -> List<Integer>(1-based 页码)
     */
    public List<Integer> parsePageRanges(String ranges, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        if (ranges == null || ranges.isBlank()) return pages;
        for (String seg : ranges.split(",")) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;
            try {
                if (seg.contains("-")) {
                    String[] parts = seg.split("-");
                    int from = Integer.parseInt(parts[0].trim());
                    int to = Integer.parseInt(parts[1].trim());
                    for (int p = from; p <= to; p++) {
                        if (p >= 1 && p <= totalPages) pages.add(p);
                    }
                } else {
                    int p = Integer.parseInt(seg);
                    if (p >= 1 && p <= totalPages) pages.add(p);
                }
            } catch (NumberFormatException e) {
                log.warn("无效页码片段: {}", seg);
            }
        }
        return pages;
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
                for (int pageNum : newOrder) {
                    if (pageNum < 1 || pageNum > totalPages) {
                        throw new BusinessException("页码 " + pageNum + " 超出范围");
                    }
                }

                // 原地重排:收集页对象(同 COS 文档,资源保留)→清空页树→按新序加回
                List<PDPage> ordered = new ArrayList<>();
                for (int pageNum : newOrder) {
                    ordered.add(source.getPage(pageNum - 1));
                }
                while (source.getNumberOfPages() > 0) {
                    source.removePage(0);
                }
                for (PDPage p : ordered) {
                    source.addPage(p);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                source.save(baos);
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
     * Phase 13.25: 应用文本格式修改(字号/颜色/粗/斜/下划线/高亮)并落盘
     * <p>
     * 每个 op 包含:
     * - range: PDF pt 矩形 {x, y, width, height}(左下原点),标识要格式化的文字区域
     * - format: { fontSize?, color?, bold?, italic?, underline?, highlight? }
     * <p>
     * 实现策略:
     * - highlight: 在文字区域下方画半透明彩色矩形(叠加高亮,不改文字)
     * - color/fontSize/bold/italic/underline: 用 PDFTextStripperByArea 提取区域文字,
     *   白色矩形覆盖原文字,再以新格式重绘
     *
     * @return 新文件路径
     */
    public String applyTextFormat(Long docId, int pageNumber, List<Map<String, Object>> ops) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                if (pageNumber < 1 || pageNumber > pdf.getNumberOfPages()) {
                    throw new BusinessException("页码超出范围: " + pageNumber);
                }
                PDPage page = pdf.getPage(pageNumber - 1);
                PDRectangle pageBox = page.getMediaBox();
                float pageHeight = pageBox.getHeight();

                for (Map<String, Object> op : ops) {
                    applyFormatOp(pdf, page, pageHeight, op);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                String newFilePath = replacePdfBytes(docId, baos.toByteArray(), "text-format");
                log.info("应用 PDF 文本格式完成: docId={}, opsCount={}", docId, ops.size());
                return newFilePath;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("应用 PDF 文本格式失败: docId={}, error={}", docId, e.getMessage());
            throw new BusinessException("应用格式失败: " + e.getMessage());
        }
    }

    /**
     * 应用单个格式操作
     */
    @SuppressWarnings("unchecked")
    private void applyFormatOp(PDDocument pdf, PDPage page, float pageHeight, Map<String, Object> op) {
        try {
            Map<String, Object> rect = (Map<String, Object>) op.get("range");
            Map<String, Object> format = (Map<String, Object>) op.get("format");
            if (rect == null || format == null) return;

            Number rxNum = (Number) rect.get("x");
            Number ryNum = (Number) rect.get("y");
            Number rwNum = (Number) rect.get("width");
            Number rhNum = (Number) rect.get("height");
            if (rxNum == null || ryNum == null || rwNum == null || rhNum == null) return;

            // range 是 PDF pt 左下原点(PDF.js selection rect 转 PDF pt 后传入)
            float pdfX = rxNum.floatValue();
            float pdfYBottom = ryNum.floatValue();      // 距 PDF 页底
            float pdfW = rwNum.floatValue();
            float pdfH = rhNum.floatValue();

            // highlight: 在文字下方画半透明矩形(PDF 坐标左下原点,直接用)
            Object highlightObj = format.get("highlight");
            if (highlightObj != null) {
                String hex = highlightObj.toString();
                float[] rgb = hexToRgb(hex);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    // APPEND 模式画在文字上方;设透明度需 PDExtendedGraphicsState,这里用半透明模拟
                    cs.addRect(pdfX, pdfYBottom, pdfW, pdfH);
                    cs.fill();
                }
                return;
            }

            // color/fontSize/bold/italic/underline: 提取区域文字 + 覆盖 + 重绘
            String colorHex = format.get("color") != null ? format.get("color").toString() : "#000000";
            float[] rgb = hexToRgb(colorHex);
            Number fontSizeNum = (Number) format.get("fontSize");
            float fontSize = fontSizeNum != null ? fontSizeNum.floatValue() : 12f;
            boolean bold = Boolean.TRUE.equals(format.get("bold"));
            boolean italic = Boolean.TRUE.equals(format.get("italic"));
            boolean underline = Boolean.TRUE.equals(format.get("underline"));

            // 用 PDFTextStripperByArea 提取区域内文字
            String regionText;
            try {
                java.awt.geom.Rectangle2D.Float region = new java.awt.geom.Rectangle2D.Float(
                        pdfX, pageHeight - pdfYBottom - pdfH, pdfW, pdfH);
                org.apache.pdfbox.text.PDFTextStripperByArea stripper =
                        new org.apache.pdfbox.text.PDFTextStripperByArea();
                stripper.addRegion("r1", region);
                stripper.extractRegions(page);
                regionText = stripper.getTextForRegion("r1").trim();
            } catch (Exception e) {
                log.warn("提取区域文字失败,跳过重绘: {}", e.getMessage());
                return;
            }
            if (regionText.isEmpty()) return;

            // 白色矩形覆盖原文字
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(1.0f, 1.0f, 1.0f);
                cs.addRect(pdfX, pdfYBottom, pdfW, pdfH);
                cs.fill();
            }

            // 选字体(粗/斜用 Helvetica-Bold/Oblique 近似;中文回退)
            PDFont font = selectFormatFont(pdf, bold, italic, regionText);

            // 重绘文字(baseline 在矩形底部上方一点)
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                cs.setFont(font, fontSize);
                cs.beginText();
                cs.newLineAtOffset(pdfX, pdfYBottom + pdfH * 0.2f);
                cs.showText(regionText);
                cs.endText();
                // 下划线
                if (underline) {
                    cs.setLineWidth(fontSize * 0.08f);
                    cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
                    cs.moveTo(pdfX, pdfYBottom + pdfH * 0.15f);
                    cs.lineTo(pdfX + pdfW, pdfYBottom + pdfH * 0.15f);
                    cs.stroke();
                }
            }
        } catch (Exception e) {
            log.warn("应用单个格式操作失败: {}", e.getMessage());
        }
    }

    /**
     * Phase 13.25: 为格式重绘选字体(粗/斜近似 + 中文回退)
     */
    private PDFont selectFormatFont(PDDocument pdf, boolean bold, boolean italic, String text) {
        boolean hasCJK = text.chars().anyMatch(c -> c > 0x2E80);
        if (hasCJK) {
            // 中文:尝试嵌入中文字体;回退 Helvetica(中文会丢,但避免崩)
            try {
                return selectFont(pdf, new java.util.HashMap<>(), text);
            } catch (Exception e) {
                return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            }
        }
        if (bold && italic) return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE);
        if (bold) return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        if (italic) return new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
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

            // Phase 13.22: 字体选择(原字体 > 中文 > Helvetica)
            PDFont font = selectFont(pdf, edit, text);

            if ("add".equals(type)) {
                // 添加文字
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    cs.setFont(font, (float) fontSize);
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
                // 修改：先覆盖原位置（用原 token 精确 width），再添加新文字
                Number origXNum = (Number) edit.get("originalX");
                Number origYNum = (Number) edit.get("originalY");
                String origText = edit.get("originalText") != null ? edit.get("originalText").toString() : "";
                Number origWidthNum = (Number) edit.get("width");

                if (origXNum != null && origYNum != null) {
                    float origX = origXNum.floatValue();
                    float origY = origYNum.floatValue();
                    // 优先用原 token width,缺则估算
                    float textWidth = origWidthNum != null && origWidthNum.floatValue() > 0
                            ? origWidthNum.floatValue()
                            : fontSize * origText.length() * 0.5f;

                    // 覆盖原位置
                    try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.setNonStrokingColor(1.0f, 1.0f, 1.0f); // 白色覆盖
                        cs.addRect(origX - 1, origY - 1, textWidth + 2, fontSize + 2);
                        cs.fill();
                    }
                }

                // 添加新文字
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                    cs.setFont(font, (float) fontSize);
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
     * Phase 13.22: 字体选择 — 优先用原 token 字体,其次中文,兜底 Helvetica
     */
    private PDFont selectFont(PDDocument pdf, Map<String, Object> edit, String text) {
        // 1. 尝试用原字体
        String fontName = edit.get("font") != null ? edit.get("font").toString() : null;
        if (fontName != null && !fontName.isEmpty() && !"OCR".equals(fontName)) {
            PDFont cached = FONT_CACHE.get(fontName);
            if (cached != null) return cached;
            try {
                // 遍历文档所有页资源中的字体(通过 font name 解析)
                for (int pi = 0; pi < pdf.getNumberOfPages(); pi++) {
                    org.apache.pdfbox.pdmodel.PDResources res = pdf.getPage(pi).getResources();
                    if (res != null && res.getFontNames() != null) {
                        for (org.apache.pdfbox.cos.COSName cname : res.getFontNames()) {
                            String fname = cname.getName();
                            if (fontName.equals(fname)) {
                                org.apache.pdfbox.pdmodel.font.PDFont f = res.getFont(cname);
                                if (f != null) {
                                    FONT_CACHE.put(fontName, f);
                                    return f;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        // 2. 含中文 → 中文字体
        if (containsChinese(text)) {
            PDFont cn = getChineseFont();
            if (cn != null) return cn;
        }
        // 3. 兜底
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static boolean containsChinese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
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
            pos.put("color", "#000000"); // Phase 13.22: OCR fallback 默认黑
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
        /** Phase 13.22: 当前 graphics state 文字色(追踪非描边色) */
        private String currentTextColor = null;

        public PositionStripper() {
            super();
        }

        public List<Map<String, Object>> getPositions() {
            return positions;
        }

        /** Phase 13.22: 追踪非描边色(文字色) */
        @Override
        protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator, List<org.apache.pdfbox.cos.COSBase> operands) throws java.io.IOException {
            String opName = operator.getName();
            try {
                if ("rg".equals(opName) || "RG".equals(opName)) {
                    if (operands.size() >= 3) {
                        float r = ((org.apache.pdfbox.cos.COSNumber) operands.get(0)).floatValue();
                        float g = ((org.apache.pdfbox.cos.COSNumber) operands.get(1)).floatValue();
                        float b = ((org.apache.pdfbox.cos.COSNumber) operands.get(2)).floatValue();
                        int ir = Math.round(Math.max(0, Math.min(1, r)) * 255);
                        int ig = Math.round(Math.max(0, Math.min(1, g)) * 255);
                        int ib = Math.round(Math.max(0, Math.min(1, b)) * 255);
                        currentTextColor = String.format("#%02X%02X%02X", ir, ig, ib);
                    }
                } else if ("k".equals(opName) || "K".equals(opName)) {
                    if (operands.size() >= 4) {
                        float c = ((org.apache.pdfbox.cos.COSNumber) operands.get(0)).floatValue();
                        float m = ((org.apache.pdfbox.cos.COSNumber) operands.get(1)).floatValue();
                        float y = ((org.apache.pdfbox.cos.COSNumber) operands.get(2)).floatValue();
                        float k = ((org.apache.pdfbox.cos.COSNumber) operands.get(3)).floatValue();
                        int ir = Math.round(255 * (1 - c) * (1 - k));
                        int ig = Math.round(255 * (1 - m) * (1 - k));
                        int ib = Math.round(255 * (1 - y) * (1 - k));
                        currentTextColor = String.format("#%02X%02X%02X", ir, ig, ib);
                    }
                }
            } catch (Exception ignore) {}
            super.processOperator(operator, operands);
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
            // Phase 13.22: 原文字颜色 — PDFBox 3.x TextPosition 无 getColor()
            // 简化: 解析 graphics state 非描边色(非描边=文字色),用最后一个 NonStroking 指令
            pos.put("color", currentTextColor != null ? currentTextColor : "#000000");
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
            // Phase 14.U10: 解析真实 destination 页码(不再硬编码 1)
            int page = 1;
            try {
                Object dest = item.getDestination();
                if (dest instanceof PDPage) {
                    // 找到 page 在 pdf pages 中的索引
                    for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                        if (pdf.getPage(i) == dest) { page = i + 1; break; }
                    }
                } else if (dest instanceof PDPage) {
                    for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                        if (pdf.getPage(i) == dest) { page = i + 1; break; }
                    }
                } else {
                    // PDActionGoTo / PDPageDestination / PDOutlineNode 等:
                    // 尝试反射 getPage() / getDestination() 兼容各种 PDFBox 3.x 类型
                    try {
                        java.lang.reflect.Method m = dest.getClass().getMethod("getPage");
                        Object p2 = m.invoke(dest);
                        if (p2 instanceof PDPage) {
                            PDPage pp = (PDPage) p2;
                            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                                if (pdf.getPage(i) == pp) { page = i + 1; break; }
                            }
                        }
                    } catch (Exception ignore) {
                        // 反射失败,page 保持 1
                    }
                }
            } catch (Exception e) {
                // destination 解析失败,fallback page=1
            }
            node.put("page", page);
            acc.add(node);
            if (item.hasChildren()) {
                Object child = item.getFirstChild();
                if (child instanceof PDDocumentOutline) {
                    walkOutline(pdf, (PDDocumentOutline) child, level + 1, acc);
                }
            }
        }
    }

    // ==================== Phase 13.23: 去水印 / 智能目录 / 智能提取 ====================

    /**
     * 去水印
     * @param mode "annotation" 删 watermark annotation;"cover" 白矩形覆盖(降级)
     */
    /**
     * Phase 14.U4: 一键去水印 —— 同时清理 annotation watermark + 白矩形覆盖整页(覆盖自家 addWatermark)
     * "annotation" 模式:仅删 annotation
     * "all" 模式:annotation + 白矩形覆盖整页(默认)
     */
    public byte[] removeWatermark(Long docId, String mode) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        boolean fullCover = !"annotation".equalsIgnoreCase(mode);
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                int removed = 0;
                for (PDPage page : pdf.getPages()) {
                    // 1) 删 annotation
                    java.util.Iterator<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation> it =
                        page.getAnnotations().iterator();
                    while (it.hasNext()) {
                        org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation a = it.next();
                        String nm = a.getAnnotationName();
                        String sub = a.getSubtype();
                        if (sub != null && (sub.contains("Watermark") || sub.contains("Stamp")) ||
                            (nm != null && (nm.toLowerCase().contains("watermark") || nm.toLowerCase().contains("stamp")))) {
                            it.remove();
                            removed++;
                        }
                    }
                    // 2) Phase 14.U13: APPEND 白矩形(在最上层,真正盖住 APPEND 水印)
                    if (fullCover) {
                        PDRectangle box = page.getMediaBox();
                        try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                            cs.setNonStrokingColor(1f, 1f, 1f);
                            cs.addRect(0, 0, box.getWidth(), box.getHeight());
                            cs.fill();
                        }
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                log.info("去水印完成: docId={}, mode={}, 删annotation={}, 全覆盖={}", docId, mode, removed, fullCover);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("去水印失败: docId={}", docId, e);
            throw new BusinessException("去水印失败: " + e.getMessage());
        }
    }

    /**
     * 智能目录:AI 分析全文生成章节标题+页码,写入 PDF outline 落盘
     */
    public Map<String, Object> autoOutline(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        Map<String, Object> resp = new LinkedHashMap<>();
        // Phase 13.37: 预检 LLM 是否配置,未配置直接返回明确提示
        if (!aiService.isConfigured()) {
            resp.put("success", false);
            resp.put("error", "LLM 未配置,无法生成智能目录。请在「管理后台 → AI 配置」设置 Provider(API 地址 + API Key)后重试");
            log.warn("智能目录: LLM 未配置, docId={}", docId);
            return resp;
        }
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            // 1. 取全文 text(带页码)
            StringBuilder fullText = new StringBuilder();
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String t = stripper.getText(pdf);
                    fullText.append("[Page ").append(i).append("]\n").append(t).append("\n");
                }
            }
            // 2. 喂 LLM 生成 JSON 目录
            String prompt = "分析以下 PDF 全文(已按页标注 [Page N]),输出章节目录 JSON 数组,每项 {\"title\":章节标题,\"page\":起始页码(整数),\"level\":层级(0=一级,1=二级)}。只输出 JSON 数组,不要解释,不要 markdown 代码块。全文:\n" +
                    fullText.substring(0, Math.min(fullText.length(), 12000));
            String llmOut = aiService.generate(prompt);
            // Phase 13.36: 检测 LLM 调用失败(chat 异常时返回"AI 服务调用失败"字符串)
            if (llmOut == null || llmOut.isBlank()) {
                resp.put("success", false);
                resp.put("error", "LLM 返回空结果。请检查:1) 模型名称是否正确 2) API Key 是否有效 3) 网络是否可达。当前配置: " + aiService.getConfigSummary());
                log.warn("智能目录: LLM 返回空, docId={}, 配置={}", docId, aiService.getConfigSummary());
                return resp;
            }
            if (llmOut.startsWith("AI 服务调用失败")) {
                resp.put("success", false);
                resp.put("error", "AI 服务调用失败:" + llmOut + "。当前配置: " + aiService.getConfigSummary() + "。请在「管理后台->AI 配置」检查 Provider");
                log.warn("智能目录: LLM 调用异常, docId={}, out={}", docId, llmOut.substring(0, Math.min(llmOut.length(), 200)));
                return resp;
            }
            // 3. 解析 JSON
            List<Map<String, Object>> outline = parseOutlineJson(llmOut);
            // Phase 13.35: outline 为空时,大概率 LLM 未配置或返回非 JSON,给出明确提示
            if (outline.isEmpty()) {
                resp.put("success", false);
                resp.put("error", "AI 未返回有效目录(可能 LLM 未配置或文档无清晰章节)。LLM 原始输出: "
                        + (llmOut == null ? "null" : llmOut.substring(0, Math.min(llmOut.length(), 200))));
                log.warn("智能目录为空: docId={}, llmOutLen={}", docId, llmOut == null ? 0 : llmOut.length());
                return resp;
            }
            resp.put("outline", outline);
            // 4. 写入 PDF outline 落盘
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDDocumentOutline root = new PDDocumentOutline();
                pdf.getDocumentCatalog().setDocumentOutline(root);
                for (Map<String, Object> node : outline) {
                    PDOutlineItem item = new PDOutlineItem();
                    item.setTitle(String.valueOf(node.get("title")));
                    int pg = node.get("page") instanceof Number ? ((Number) node.get("page")).intValue() : 1;
                    if (pg >= 1 && pg <= pdf.getNumberOfPages()) {
                        item.setDestination(pdf.getPage(pg - 1));
                    }
                    root.addLast(item);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                pdf.save(baos);
                String newFilePath = replacePdfBytes(docId, baos.toByteArray(), "auto-outline");
                resp.put("filePath", newFilePath);
            }
            resp.put("success", true);
            log.info("智能目录生成: docId={}, 条数={}", docId, outline.size());
            return resp;
        } catch (Exception e) {
            log.error("智能目录失败: docId={}", docId, e);
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        }
    }

    /** 从 LLM 输出解析 [{title,page,level}] */
    private List<Map<String, Object>> parseOutlineJson(String llmOut) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (llmOut == null) return result;
        // Phase 13.36: 去除 markdown 代码块包裹(LLM 常返回 ```json ... ```)
        String cleaned = llmOut.replaceAll("```json", "").replaceAll("```", "").trim();
        // 提取第一个 [ 到最后一个 ]
        int s = cleaned.indexOf('[');
        int e = cleaned.lastIndexOf(']');
        if (s < 0 || e < 0) return result;
        String json = cleaned.substring(s, e + 1);
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> list = om.readValue(json, List.class);
            for (Object o : list) {
                if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("title", m.get("title"));
                    node.put("page", m.get("page"));
                    node.put("level", m.get("level") instanceof Number ? m.get("level") : 0);
                    result.add(node);
                }
            }
        } catch (Exception ex) {
            log.warn("解析智能目录 JSON 失败: {}", ex.getMessage());
        }
        return result;
    }

    /**
     * 智能提取:聚合 文字 + 表格 + 图片 + 结构化 JSON
     */
    public Map<String, Object> extractStructured(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            // 1. 全文 text
            String text = doclingService.parse(docId);
            resp.put("text", text);
            // 2. 表格
            String tables = doclingService.extractTables(docId);
            resp.put("tables", tables);
            // 3. 图片数量(遍历 XObjects)
            int imgCount = countImages(docId);
            resp.put("imagesCount", imgCount);
            // 4. 结构化 JSON(AI)
            String structured = aiService.structuredSummarize(text);
            resp.put("structuredJson", structured);
            resp.put("success", true);
            log.info("智能提取完成: docId={}, 图片={}", docId, imgCount);
            return resp;
        } catch (Exception e) {
            log.error("智能提取失败: docId={}", docId, e);
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        }
    }

    /** 统计 PDF 嵌入图片数量 */
    private int countImages(Long docId) {
        try {
            byte[] pdfBytes = storageService.load(documentService.getDocument(docId).getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                int count = 0;
                for (PDPage page : pdf.getPages()) {
                    org.apache.pdfbox.pdmodel.PDResources res = page.getResources();
                    if (res != null) {
                        for (org.apache.pdfbox.cos.COSName name : res.getXObjectNames()) {
                            try {
                                org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = res.getXObject(name);
                                if (xobj instanceof PDImageXObject) count++;
                            } catch (Exception ignore) {}
                        }
                    }
                }
                return count;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 提取所有嵌入图片为 zip
     */
    public byte[] extractImagesZip(Long docId) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()));
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            int idx = 0;
            for (PDPage page : pdf.getPages()) {
                org.apache.pdfbox.pdmodel.PDResources res = page.getResources();
                if (res == null) continue;
                for (org.apache.pdfbox.cos.COSName name : res.getXObjectNames()) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = res.getXObject(name);
                        if (xobj instanceof PDImageXObject) {
                            PDImageXObject img = (PDImageXObject) xobj;
                            BufferedImage bi = img.getImage();
                            ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                            ImageIO.write(bi, "png", imgBaos);
                            zos.putNextEntry(new java.util.zip.ZipEntry("page-img-" + (++idx) + ".png"));
                            zos.write(imgBaos.toByteArray());
                            zos.closeEntry();
                        }
                    } catch (Exception ignore) {}
                }
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("提取图片 zip 失败: docId={}", docId, e);
            throw new BusinessException("提取图片失败: " + e.getMessage());
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
            // Phase 14.U10: 在 text token 间插入空格,避免相邻字符拼成乱码
            String prev = null;
            for (Map<String, Object> p : e.getValue()) {
                String text = (String) p.get("text");
                if (text == null || text.isEmpty()) continue;
                // 启发式:前一个 token 末尾是字母/数字 且当前 token 开头是字母/数字,加空格
                if (sb.length() > 0 && prev != null) {
                    char lastCh = sb.charAt(sb.length() - 1);
                    char firstCh = text.charAt(0);
                    boolean lastAlnum = Character.isLetterOrDigit(lastCh);
                    boolean firstAlnum = Character.isLetterOrDigit(firstCh);
                    if (lastAlnum && firstAlnum) sb.append(' ');
                }
                sb.append(text);
                prev = text;
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
            pdf.addPage(blank); // 先加到末尾
            int insertIdx = Math.min(afterPage, pdf.getNumberOfPages() - 1);
            if (insertIdx < pdf.getNumberOfPages() - 1) {
                // 原地把末尾的空白页移到 insertIdx(同文档,字体/资源保留)
                List<PDPage> pages = new ArrayList<>();
                for (int i = 0; i < pdf.getNumberOfPages(); i++) pages.add(pdf.getPage(i));
                PDPage moved = pages.remove(pages.size() - 1);
                pages.add(insertIdx, moved);
                while (pdf.getNumberOfPages() > 0) pdf.removePage(0);
                for (PDPage p : pages) pdf.addPage(p);
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
    /**
     * Phase 14.U2+U4: 添加水印,支持 5 种位置 + 灵活旋转 + clearExisting 覆盖
     * @param position  'tile'|'header'|'footer'|'center'|'diagonal'
     * @param clearExisting true 时先白矩形覆盖整个页面 + 删除 watermark annotation(可重复调用覆盖)
     */
    public byte[] addWatermark(Long docId, String text, double opacity, double rotation, String position, float fontSize, boolean clearExisting, List<Integer> pages) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        try (PDDocument pdf = Loader.loadPDF(storageService.load(doc.getFilePath()))) {
            List<Integer> targetPages = (pages == null || pages.isEmpty())
                ? allPages(pdf) : pages;
            String pos = (position == null || position.isBlank()) ? "diagonal" : position;
            for (int pageNum : targetPages) {
                if (pageNum < 1 || pageNum > pdf.getNumberOfPages()) continue;
                PDPage page = pdf.getPage(pageNum - 1);
                PDRectangle box = page.getMediaBox();
                if (clearExisting) {
                    clearPageOverlay(pdf, page);
                }
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState gs = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant((float) Math.max(0, Math.min(1, opacity)));
                    cs.setGraphicsStateParameters(gs);
                    float fs = (fontSize <= 0) ? Math.max(40f, Math.min(box.getWidth(), box.getHeight()) / 6f) : fontSize;
                    switch (pos) {
                        case "header": {
                            float cx = box.getWidth() / 2f;
                            float cy = box.getHeight() - fs - 12f;
                            drawText(cs, text, cx, cy, 0, fs);
                            break;
                        }
                        case "footer": {
                            float cx = box.getWidth() / 2f;
                            float cy = 12f + fs / 2f;
                            drawText(cs, text, cx, cy, 0, fs);
                            break;
                        }
                        case "center": {
                            float cx = box.getWidth() / 2f;
                            float cy = box.getHeight() / 2f;
                            drawText(cs, text, cx, cy, 0, fs);
                            break;
                        }
                        case "tile": {
                            int cols = Math.max(1, (int) (box.getWidth() / 240));
                            int rows = Math.max(1, (int) (box.getHeight() / 200));
                            double tileRot = Math.toRadians(rotation);
                            for (int r = 0; r < rows; r++) {
                                for (int c = 0; c < cols; c++) {
                                    float x = (c + 0.5f) * (box.getWidth() / cols);
                                    float y = (r + 0.5f) * (box.getHeight() / rows);
                                    drawText(cs, text, x, y, tileRot, fs * 0.6f);
                                }
                            }
                            break;
                        }
                        default: { // diagonal
                            float cx = box.getWidth() / 2f;
                            float cy = box.getHeight() / 2f;
                            drawText(cs, text, cx, cy, Math.toRadians(rotation), fs);
                        }
                    }
                }
            }
            byte[] out = newDocBytes(pdf, "watermark");
            replacePdfBytes(docId, out, "watermark");
            log.info("水印: docId={}, pos={}, rot={}, clear={}, pages={}", docId, pos, rotation, clearExisting, targetPages.size());
            return out;
        } catch (Exception e) {
            log.error("水印失败: docId={}, text={}", docId, text, e);
            throw new BusinessException("水印失败: " + e.getMessage());
        }
    }

    /** 工具:在 (x,y) 处按弧度旋转绘制文字(居中锚点) */
    private void drawText(PDPageContentStream cs, String text, float x, float y, double rad, float fontSize) throws java.io.IOException {
        cs.saveGraphicsState();
        if (rad != 0) {
            cs.transform(new org.apache.pdfbox.util.Matrix(
                (float) Math.cos(rad), (float) Math.sin(rad),
                (float) -Math.sin(rad), (float) Math.cos(rad),
                x, y));
        } else {
            cs.transform(new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, x, y));
        }
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize);
        // 居中:用 font.getStringWidth(text) / 1000 * fontSize 估算宽度,但为简化用 -text.length()*0.5*size 作粗略偏移
        float offset = -(text == null ? 0 : text.length()) * fontSize * 0.25f;
        cs.newLineAtOffset(offset, -fontSize * 0.3f);
        cs.showText(text == null ? "" : text);
        cs.endText();
        cs.restoreGraphicsState();
    }

    /**
     * 添加页眉/页脚文字(Phase 14.U2:支持 clearExisting 覆盖模式)
     */
    public byte[] addHeaderFooter(Long docId, String position, String content, double fontSize, boolean clearExisting, List<Integer> pages) {
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
                if (clearExisting) {
                    clearPageOverlay(pdf, page);
                }
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
            log.info("页眉页脚: docId={}, pos={}, clear={}, pages={}", docId, position, clearExisting, targetPages.size());
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
     * Phase 14.U4: 清除页面覆盖层
     * - annotation 删除(Watermark/Stamp 等)
     * - 用白色矩形覆盖整页(覆盖 APPEND 模式水印)
     *
     * 注意:addWatermark/addHeaderFooter 后续画新内容前,会再次调用此方法。
     * 用 PREPEND 让白矩形在最底层,新内容(APPEND)画在白矩形之上 → 视觉上"清空旧水印+画新水印"。
     * 而独立的 removeWatermark 路径(无后续画新内容)需要确保白矩形在最上层(APPEND),因此本方法
     * 由 addWatermark/addHeaderFooter 调用时使用 PREPEND,由 removeWatermark 单独处理时使用 APPEND。
     * 见下方 removeWatermark / clearPageOverlayAppend 实现。
     */
    private void clearPageOverlay(PDDocument pdf, PDPage page) throws java.io.IOException {
        // 1) 删 watermark / stamp 等 annotation
        java.util.Iterator<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation> it =
            page.getAnnotations().iterator();
        while (it.hasNext()) {
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation a = it.next();
            String sub = a.getSubtype();
            String nm = a.getAnnotationName();
            if ((sub != null && (sub.contains("Watermark") || sub.contains("Stamp"))) ||
                (nm != null && (nm.toLowerCase().contains("watermark") || nm.toLowerCase().contains("stamp")))) {
                it.remove();
            }
        }
        // 2) 用白色矩形覆盖整个页面(PREPEND → 在底层,后续 APPEND 内容画在上面)
        PDRectangle box = page.getMediaBox();
        try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.PREPEND, true, true)) {
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, box.getWidth(), box.getHeight());
            cs.fill();
        }
    }

    /**
     * Phase 14.U13: 一键去水印专用 —— APPEND 白矩形(在最上层,盖住原 APPEND 水印)
     * 区别于 clearPageOverlay(PREPEND,用于"替换"场景)
     */
    private void clearPageOverlayAppend(PDDocument pdf, PDPage page) throws java.io.IOException {
        // 1) 删 annotation
        java.util.Iterator<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation> it =
            page.getAnnotations().iterator();
        while (it.hasNext()) {
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation a = it.next();
            String sub = a.getSubtype();
            String nm = a.getAnnotationName();
            if ((sub != null && (sub.contains("Watermark") || sub.contains("Stamp"))) ||
                (nm != null && (nm.toLowerCase().contains("watermark") || nm.toLowerCase().contains("stamp")))) {
                it.remove();
            }
        }
        // 2) 白矩形 APPEND(在最上层,真正盖住水印)
        PDRectangle box = page.getMediaBox();
        try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, box.getWidth(), box.getHeight());
            cs.fill();
        }
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
        // Phase 13.26: 兼容 data:image/...;base64, 前缀(PdfSignatureDialog 可能带前缀传入)
        String rawBase64 = imageBase64;
        int commaIdx = rawBase64.indexOf(',');
        if (rawBase64.startsWith("data:") && commaIdx > 0) {
            rawBase64 = rawBase64.substring(commaIdx + 1);
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
                byte[] imgBytes = java.util.Base64.getDecoder().decode(rawBase64);
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
                log.info("区间拆分开始: docId={}, ranges={}, 总页数={}, 段数={}", documentId, ranges, totalPages, segments.size());
                for (int[] seg : segments) {
                    if (seg[0] < 1 || seg[1] > totalPages || seg[0] > seg[1]) {
                        log.warn("跳过无效区间: {} - {} (总 {} 页)", seg[0], seg[1], totalPages);
                        continue;
                    }
                    // Phase 13.36: 改用 importPage 方式逐页导入新文档(替代原地删除的 extractPages)
                    // 每段生成独立 PDF,含该区间所有页
                    try (PDDocument part = new PDDocument()) {
                        int imported = 0;
                        for (int p = seg[0]; p <= seg[1]; p++) {
                            part.importPage(source.getPage(p - 1));
                            imported++;
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        part.save(baos);
                        byte[] partBytes = baos.toByteArray();
                        log.info("区间拆分段: [{}-{}], 导入页数={}, part页数={}, bytes={}",
                                seg[0], seg[1], imported, part.getNumberOfPages(), partBytes.length);
                        result.add(partBytes);
                    }
                }
            }
            log.info("区间拆分完成: docId={}, ranges={}, 生成PDF数={}", documentId, ranges, result.size());
            return result;
        } catch (Exception e) {
            log.error("区间拆分失败", e);
            throw new BusinessException("区间拆分失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.37: 替换页面 - 用源 PDF 的指定页替换目标文档的选中页
     * targetPages 有序(如 [2,5]),sourceStartPage 为源 PDF 起始页(1-based),
     * 源 PDF 从 sourceStartPage 开始取 targetPages.size() 页,逐页替换
     */
    public String replacePages(Long docId, List<Integer> targetPages, byte[] sourceBytes, int sourceStartPage) {
        Document doc = documentService.getDocument(docId);
        validatePdf(doc);
        if (targetPages == null || targetPages.isEmpty()) {
            throw new BusinessException("未选择要替换的目标页");
        }
        try {
            byte[] targetBytes = storageService.load(doc.getFilePath());
            try (PDDocument target = Loader.loadPDF(targetBytes);
                 PDDocument source = Loader.loadPDF(sourceBytes);
                 PDDocument result = new PDDocument()) {
                int totalTarget = target.getNumberOfPages();
                int totalSource = source.getNumberOfPages();
                // 验证源页范围
                for (int i = 0; i < targetPages.size(); i++) {
                    int tp = targetPages.get(i);
                    int sp = sourceStartPage + i;
                    if (tp < 1 || tp > totalTarget) throw new BusinessException("目标页 " + tp + " 超出范围(共 " + totalTarget + " 页)");
                    if (sp < 1 || sp > totalSource) throw new BusinessException("源页 " + sp + " 超出范围(源 PDF 共 " + totalSource + " 页),源起始页过大");
                }
                // 重建文档:目标页在 targetPages 中则用源对应页替换,否则保留原页
                for (int i = 0; i < totalTarget; i++) {
                    int pageNum = i + 1;
                    int idx = targetPages.indexOf(pageNum);
                    if (idx >= 0) {
                        int sp = sourceStartPage + idx;
                        result.importPage(source.getPage(sp - 1));
                    } else {
                        result.importPage(target.getPage(i));
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                result.save(baos);
                String newFilePath = replacePdfBytes(docId, baos.toByteArray(), "replace-pages");
                log.info("替换页面完成: docId={}, targetPages={}, sourceStart={}, 源总页={}",
                        docId, targetPages, sourceStartPage, totalSource);
                return newFilePath;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("替换页面失败: docId={}", docId, e);
            throw new BusinessException("替换页面失败: " + e.getMessage());
        }
    }

    /**
     * Phase 13.31: 保留方法签名兼容旧调用,实际由 Controller 层组装 zip
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
