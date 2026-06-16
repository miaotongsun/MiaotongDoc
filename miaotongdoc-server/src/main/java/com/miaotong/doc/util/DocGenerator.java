package com.miaotong.doc.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DocGenerator {

    public static byte[] create(String docType, String title) throws IOException {
        return switch (docType) {
            case "word" -> createDocx(title);
            case "cell" -> createXlsx(title);
            case "slide" -> createPptx(title);
            case "markdown" -> createMarkdown(title);
            case "pdf" -> createPdf(title);
            default -> throw new IllegalArgumentException("不支持的文档类型: " + docType);
        };
    }

    private static byte[] createDocx(String title) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.getProperties().getCoreProperties().setTitle(title);
            doc.getProperties().getCoreProperties().setCreator("MiaotongDoc");
            doc.getProperties().getExtendedProperties().setApplication("MiaotongDoc");
            doc.getProperties().getExtendedProperties().setAppVersion("1.0");
            doc.createParagraph();
            byte[] raw = toBytes(doc);
            return setDocxLanguage(raw, "zh-CN");
        }
    }

    /**
     * 修改docx，注入中文语言的styles.xml
     */
    private static byte[] setDocxLanguage(byte[] docxBytes, String lang) {
        try {
            var baos = new java.io.ByteArrayOutputStream();
            boolean hasStyles = false;
            try (var zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docxBytes));
                 var zout = new java.util.zip.ZipOutputStream(baos)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    if ("word/styles.xml".equals(entry.getName())) {
                        hasStyles = true;
                        String xml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                        if (!xml.contains("<w:lang ")) {
                            xml = xml.replace("</w:rPr></w:rPrDefault>",
                                    "<w:lang w:val=\"" + lang + "\" w:eastAsia=\"" + lang + "\"/></w:rPr></w:rPrDefault>");
                        }
                        zout.write(xml.getBytes(StandardCharsets.UTF_8));
                    } else {
                        zout.write(zin.readAllBytes());
                    }
                    zout.closeEntry();
                }
                // 如果没有styles.xml，创建一个包含中文语言的
                if (!hasStyles) {
                    zout.putNextEntry(new java.util.zip.ZipEntry("word/styles.xml"));
                    String stylesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:docDefaults><w:rPrDefault><w:rPr>"
                            + "<w:rFonts w:ascii=\"等线\" w:eastAsia=\"等线\" w:hAnsi=\"等线\"/>"
                            + "<w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>"
                            + "<w:lang w:val=\"" + lang + "\" w:eastAsia=\"" + lang + "\"/>"
                            + "</w:rPr></w:rPrDefault></w:docDefaults>"
                            + "</w:styles>";
                    zout.write(stylesXml.getBytes(StandardCharsets.UTF_8));
                    zout.closeEntry();
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return docxBytes;
        }
    }

    private static byte[] createXlsx(String title) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.getProperties().getCoreProperties().setTitle(title);
            wb.getProperties().getCoreProperties().setCreator("MiaotongDoc");
            wb.getProperties().getExtendedProperties().setApplication("MiaotongDoc");
            wb.getProperties().getExtendedProperties().setAppVersion("1.0");
            wb.createSheet("Sheet1");
            return toBytes(wb);
        }
    }

    private static byte[] createPptx(String title) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.getProperties().getCoreProperties().setTitle(title);
            ppt.getProperties().getCoreProperties().setCreator("MiaotongDoc");
            ppt.getProperties().getExtendedProperties().setApplication("MiaotongDoc");
            ppt.getProperties().getExtendedProperties().setAppVersion("1.0");

            // 设置默认字体为中文
            if (ppt.getCTPresentation().getDefaultTextStyle() == null) {
                ppt.getCTPresentation().addNewDefaultTextStyle();
            }

            ppt.createSlide();
            return toBytes(ppt);
        }
    }

    /**
     * 创建空白 Markdown 文档
     */
    private static byte[] createMarkdown(String title) {
        String content = "# " + title + "\n\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 创建空白 PDF 文档（使用 Apache PDFBox）
     */
    private static byte[] createPdf(String title) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(FontName.HELVETICA_BOLD), 18);
                cs.setLeading(24);
                cs.newLineAtOffset(50, 750);
                cs.showText(title);
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] toBytes(XWPFDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] toBytes(XMLSlideShow ppt) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ppt.write(baos);
            return baos.toByteArray();
        }
    }
}
