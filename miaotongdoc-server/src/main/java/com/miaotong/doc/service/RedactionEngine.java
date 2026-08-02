package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 密文遮盖引擎（真脱敏）
 *
 * 设计要点：
 *  - 文字 PDF：先通过 OCR 或内嵌坐标确认每个被遮盖的区域坐标，
 *    然后用 PDFRenderer 栅格化整页 → 涂黑/涂白区域 → 嵌回 PDF
 *  - 扫描件 PDF：直接调 PaddleOCR → 行级 bbox → 涂白
 *  - 兜底：黑框覆盖（视觉遮盖，但底层像素仍可提取，仅作为最后手段）
 *
 * 与旧实现区别：
 *  - 旧实现（{@code PdfToolService.applyRedaction}）只画黑色矩形，底层文字 token 仍可被复制/提取
 *  - 新实现：核心路径用 **pdf → 图像 → 涂黑 → 图像回填** 的方式，
 *    从根本上把文字层变成图片层，**真正实现"不可还原"的脱敏**
 *
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedactionEngine {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * OCR 客户端通过 ObjectProvider 注入，避免 Spring 循环依赖
     */
    private ObjectProvider<PaddleOcrClient> ocrBeanProvider;

    @Autowired
    public void setOcrBeanProvider(ObjectProvider<PaddleOcrClient> ocrBeanProvider) {
        this.ocrBeanProvider = ocrBeanProvider;
    }

    // ==================== 公共入口 ====================

    /**
     * 主入口：对 PDF 字节流应用密文遮盖
     *
     * @param documentId 文档 ID（用于 OCR 备查）
     * @param pdfBytes 原始 PDF 字节
     * @param regions 遮盖区域（pdf pt，左下原点，1-indexed pageNum）
     * @return 已脱敏的 PDF 字节
     */
    public byte[] redact(Long documentId, byte[] pdfBytes, List<RedactRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("密文区域不能为空");
        }
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            int n = pdf.getNumberOfPages();
            // 按页分组
            var byPage = new java.util.LinkedHashMap<Integer, List<RedactRegion>>();
            for (RedactRegion r : regions) {
                if (r.pageNum < 1 || r.pageNum > n) {
                    log.warn("[redact] 区域页码超出范围: pageNum={} (doc pages={})", r.pageNum, n);
                    continue;
                }
                byPage.computeIfAbsent(r.pageNum, k -> new ArrayList<>()).add(r);
            }

            for (Map.Entry<Integer, List<RedactRegion>> e : byPage.entrySet()) {
                int pageIdx = e.getKey() - 1;
                PDPage page = pdf.getPage(pageIdx);
                applyOnPage(pdf, page, pageIdx, e.getValue(), documentId, pdfBytes);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdf.save(baos);
            log.info("[redact] 完成: docId={}, regions={}", documentId, regions.size());
            return baos.toByteArray();
        } catch (IOException ex) {
            log.error("[redact] 处理失败: docId={}", documentId, ex);
            throw new RuntimeException("密文处理失败: " + ex.getMessage(), ex);
        }
    }

    // ==================== 单页处理 ====================

    /**
     * 单页密文处理（核心：栅格化 + 涂黑 + 嵌回）
     *
     * 流程：
     *  1. PDFRenderer 渲染整页（200 DPI = scale 2.0）
     *  2. 用 OCR 行级 bbox 判定哪些行与用户 region 相交（若可拿到 OCR 结果）
     *  3. 绘制命中区域（覆盖黑色矩形）
     *  4. 整页图像覆盖到 PDF（这下底层文字彻底变成像素）
     */
    private void applyOnPage(PDDocument pdf, PDPage page, int pageIdx,
                             List<RedactRegion> regions, Long documentId, byte[] pdfBytes) throws IOException {
        // 先尝试 OCR 拿行级 bbox（用于路径 1）
        List<LineBox> hitLines = null;
        if (documentId != null && ocrBeanProvider != null) {
            hitLines = tryGetOcrLineBoxes(documentId, pageIdx);
        }

        // 渲染整页
        PDFRenderer renderer = new PDFRenderer(pdf);
        float scale = 2.0f;
        BufferedImage pageImage = renderer.renderImage(pageIdx, scale);
        int imgWidth = pageImage.getWidth();
        int imgHeight = pageImage.getHeight();

        PDRectangle pageBox = page.getMediaBox();
        float ptWidth = pageBox.getWidth();
        float ptHeight = pageBox.getHeight();
        float pxPerPtX = imgWidth / ptWidth;
        float pxPerPtY = imgHeight / ptHeight;

        Graphics2D g = pageImage.createGraphics();
        g.setColor(Color.BLACK);
        g.setComposite(AlphaComposite.Src);

        if (hitLines != null && !hitLines.isEmpty()) {
            // 路径 1：用 OCR 拿到的行级 bbox 精确涂黑每行
            // 注: OCR bbox 是顶向下像素,与 BufferedImage 坐标系一致;region 是底向上 PDF pt,需翻转
            for (LineBox lb : hitLines) {
                if (anyRegionIntersectsPx(lb.x, lb.y, lb.w, lb.h, regions, ptWidth, ptHeight)) {
                    // OCR bbox 已经是顶向下像素,直接画
                    int px = Math.round(lb.x);
                    int py = Math.round(lb.y);
                    int pw = Math.round(lb.w);
                    int ph = Math.round(lb.h);
                    g.fillRect(px, py, pw, ph);
                }
            }
        } else {
            // 路径 2：扫描件 OCR 不可用 → 直接对每个用户 region 涂黑整块
            // 重要: region.y 是底向上 PDF pt,需翻转成顶向下像素
            for (RedactRegion r : regions) {
                int px = Math.round((float) r.x * pxPerPtX);
                int py = Math.round((float) (ptHeight - r.y - r.height) * pxPerPtY); // Y 翻转
                int pw = Math.round((float) r.width * pxPerPtX);
                int ph = Math.round((float) r.height * pxPerPtY);
                g.fillRect(px, py, pw, ph);
            }
            log.warn("[redact-fallback-blackbox] page {} OCR 不可用,区域涂黑", pageIdx + 1);
        }
        g.dispose();

        // 图像回填到 PDF（彻底抹掉原始文字层）
        replacePageWithImage(pdf, page, pageImage);
    }

    // ==================== OCR 调用 ====================

    /**
     * 调 PaddleOCR 拿指定页的行级 bbox（像素坐标，顶向下原点）
     */
    private List<LineBox> tryGetOcrLineBoxes(Long documentId, int pageIdx) {
        try {
            PaddleOcrClient paddleOcr = ocrBeanProvider != null ? ocrBeanProvider.getIfAvailable() : null;
            if (paddleOcr == null) return null;

            Map<String, Object> ocrResult = paddleOcr.recognizePdf(documentId, "ch", "mobile", null);
            if (!"success".equals(ocrResult.get("status"))) {
                log.info("[redact] OCR 状态: {}", ocrResult.get("status"));
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pages = (List<Map<String, Object>>) ocrResult.get("pages");
            if (pages == null) return null;

            List<LineBox> result = new ArrayList<>();
            for (Map<String, Object> p : pages) {
                Object pn = p.get("pageNum");
                if (!(pn instanceof Number) || ((Number) pn).intValue() != pageIdx + 1) continue;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lineRegions = (List<Map<String, Object>>) p.get("regions");
                if (lineRegions == null) continue;
                for (Map<String, Object> line : lineRegions) {
                    @SuppressWarnings("unchecked")
                    List<Number> bbox = (List<Number>) line.get("bbox");
                    if (bbox == null || bbox.size() < 4) continue;
                    result.add(new LineBox(
                            bbox.get(0).floatValue(),
                            bbox.get(1).floatValue(),
                            bbox.get(2).floatValue(),
                            bbox.get(3).floatValue()));
                }
            }
            return result.isEmpty() ? null : result;
        } catch (Exception ex) {
            log.warn("[redact] OCR 失败: {}", ex.getMessage());
            return null;
        }
    }

    // ==================== 图像回填 ====================

    /**
     * 用图像替换整页内容（彻底抹掉原始文字层）
     *
     * 关键步骤：先擦除原 page 的 Resources 和 ContentStream（删除所有底层 token），
     * 再用 PREPEND 模式画新背景+图像。这样 PDFTextStripper 读不到任何原始文字。
     */
    private void replacePageWithImage(PDDocument pdf, PDPage page, BufferedImage image) throws IOException {
        PDRectangle box = page.getMediaBox();

        // 1. 只擦除 ContentStream（删除原始文字 token），保留 Resources 字典
        //    保留 Resources 确保后续 drawImage 创建的 PDImageXObject 能被正确注册和序列化
        page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.CONTENTS);

        // 2. 用 APPEND 模式画新内容（白底 + 栅格化图像）
        //    注意: 使用 APPEND 而非 PREPEND，因为 CONTENTS 已删除，APPEND 即唯一内容流
        //    PREPEND + 空 Resources 会导致 PDFBox 无法序列化新注册的 XObject，PDF.js 渲染时 Do 操作符找不到资源
        try (PDPageContentStream cs = new PDPageContentStream(
                pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            // PDFBox 颜色参数范围 0..1,不是 0..255
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, box.getWidth(), box.getHeight());
            cs.fill();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage =
                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
                            pdf, baos.toByteArray(), "redact-flat");
            cs.drawImage(pdImage, 0, 0, box.getWidth(), box.getHeight());
        }
    }

    // ==================== 命中判定 ====================

    private static boolean anyRegionIntersects(List<RedactRegion> regions, float x, float y, float w, float h) {
        for (RedactRegion r : regions) {
            if (x < r.x + r.width && x + w > r.x && y < r.y + r.height && y + h > r.y) {
                return true;
            }
        }
        return false;
    }

    /**
     * 命中判定 (用于 OCR 路径):OCR bbox 是顶向下像素,region 是底向上 PDF pt
     * 内部统一转到顶向下像素坐标后再比较
     */
    private static boolean anyRegionIntersectsPx(float pxX, float pxY, float pxW, float pxH,
                                                 List<RedactRegion> regions, float ptWidth, float ptHeight) {
        // 像素/pt 比例 (从传入的 px 坐标反推,够用)
        float pxPerPtX = (pxX + pxW) / ptWidth;
        float pxPerPtY = (pxY + pxH) / ptHeight;
        // OCR bbox 顶向下像素 → 底向上 PDF pt
        float ocrPtX = pxX / pxPerPtX;
        float ocrPtY = ptHeight - (pxY / pxPerPtY) - (pxH / pxPerPtY);
        float ocrPtW = pxW / pxPerPtX;
        float ocrPtH = pxH / pxPerPtY;

        // 与 region (底向上 PDF pt) 比较
        for (RedactRegion r : regions) {
            if (ocrPtX < r.x + r.width && ocrPtX + ocrPtW > r.x
                    && ocrPtY < r.y + r.height && ocrPtY + ocrPtH > r.y) {
                return true;
            }
        }
        return false;
    }

    // ==================== DTO ====================

    /**
     * 密文区域（与前端契约一致）
     *  - pdf pt 坐标
     *  - 1-indexed pageNum
     *  - 左下原点
     */
    public record RedactRegion(int pageNum, double x, double y, double width, double height) {}

    /**
     * OCR 行级 bbox（像素坐标，顶向下原点）
     */
    private record LineBox(float x, float y, float w, float h) {}
}
