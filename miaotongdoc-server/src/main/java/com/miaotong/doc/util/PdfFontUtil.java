package com.miaotong.doc.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PdfFontUtil — PDF 字体加载工具(支持中文)
 *
 * 设计:
 * - 内置字体: 文泉驿微米黑(wqy-microhei.ttc)在 classpath:fonts/ 下
 * - 加载策略:第一次需要时按需加载并缓存到 PDFDocument 上(避免每个页面都重新解析 TrueType)
 * - Fallback: 内置字体不可用时回退 Helvetica(只支持英文/数字)
 *
 * 使用方式:
 *   PDFont font = PdfFontUtil.getFontForText(pdf, "中文 hello"); // 自动选字体
 *   cs.setFont(font, size);
 *   cs.showText(text);
 *
 * 注意:
 * - PDType0Font 必须在 PDDocument 上下文中创建(load() 绑定 document)
 * - showText() 对非内嵌子集字符会失败;TrueType 字体天然支持 CJK
 * - Helvetica (PDType1Font) 只支持 WinAnsi 编码(ASCII)
 */
public final class PdfFontUtil {

    private static final Logger log = LoggerFactory.getLogger(PdfFontUtil.class);

    /** classpath 下的字体文件名(按顺序尝试;PDFBox PDType0Font.load 不支持 TTC 和 OTF,只支持单 TTF)
     *
     * 字体来源:wqy-microhei.ttf 是从 wqy-microhei.ttc 用 fonttools 抽出的第一个 TTF
     * (原版 TTC 是 TrueType Collection,PDFBox 解析时找不到 head table 失败)
     */
    private static final String[] FONT_RESOURCES = {
        "fonts/wqy-microhei.ttf",
    };

    /** Helvetica (ASCII only) — fallback */
    private static final PDFont FALLBACK_PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FALLBACK_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** 每个 PDDocument 实例的字体缓存(避免重复解析 TrueType) */
    private static final ConcurrentHashMap<PDDocument, PDFont> CJK_CACHE = new ConcurrentHashMap<>();

    /** 标记是否内置字体可用 */
    private static volatile Boolean cjkFontAvailable = null;

    private PdfFontUtil() {}

    /**
     * 检测文本是否含非 ASCII 字符(需要中文/日文/韩文字体)
     */
    public static boolean needsCjk(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) return true;
        }
        return false;
    }

    /**
     * 为指定文本自动选字体:
     * - 纯 ASCII: Helvetica
     * - 含中文/CJK: PDType0Font (TrueType)
     *
     * @param pdf 当前 PDF 文档(用于绑定字体资源)
     * @param text 要绘制的文本
     * @param bold 是否粗体(目前中文只有 Regular,Bold 暂用 Regular)
     */
    public static PDFont getFontForText(PDDocument pdf, String text, boolean bold) {
        if (pdf == null) {
            return bold ? FALLBACK_BOLD : FALLBACK_PLAIN;
        }
        if (!needsCjk(text)) {
            return bold ? FALLBACK_BOLD : FALLBACK_PLAIN;
        }
        // CJK 路径
        PDFont cached = CJK_CACHE.get(pdf);
        if (cached != null) return cached;
        synchronized (CJK_CACHE) {
            cached = CJK_CACHE.get(pdf);
            if (cached != null) return cached;
            PDFont loaded = loadCjkFont(pdf);
            if (loaded != null) {
                CJK_CACHE.put(pdf, loaded);
                return loaded;
            }
        }
        // Fallback(中文文本+无字体:后续 showText 会抛错,调用方需 catch)
        log.warn("CJK 字体不可用,fallback 到 Helvetica — 中文文本会渲染失败");
        return bold ? FALLBACK_BOLD : FALLBACK_PLAIN;
    }

    /** getFontForText 默认非粗体 */
    public static PDFont getFontForText(PDDocument pdf, String text) {
        return getFontForText(pdf, text, false);
    }

    /**
     * 加载内置 CJK 字体(按顺序尝试资源)
     * @return 加载成功返回 PDType0Font;失败返回 null
     */
    private static PDFont loadCjkFont(PDDocument pdf) {
        if (cjkFontAvailable != null && !cjkFontAvailable) {
            return null;
        }
        for (String res : FONT_RESOURCES) {
            try (InputStream is = openResource(res)) {
                if (is == null) continue;
                PDFont font = PDType0Font.load(pdf, is);
                log.info("已加载 CJK 字体: {} (fontName={})", res, font.getName());
                cjkFontAvailable = true;
                return font;
            } catch (IOException | IllegalArgumentException e) {
                log.warn("加载字体失败: {} - {} {}", res, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        cjkFontAvailable = false;
        log.error("CJK 字体加载失败,中文 PDF 操作将不可用");
        return null;
    }

    private static InputStream openResource(String resource) {
        // ClassLoader.getResourceAsStream 在 jar 和 classpath 都生效
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is == null) {
            is = PdfFontUtil.class.getClassLoader().getResourceAsStream(resource);
        }
        return is;
    }

    private static int fontToSize(PDFont font) {
        try { return font.toString().length(); } catch (Exception e) { return 0; }
    }

    /**
     * 文档关闭时清空缓存(防止内存泄漏)
     */
    public static void clearCache(PDDocument pdf) {
        if (pdf != null) CJK_CACHE.remove(pdf);
    }
}