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
            // 先检查是否已有styles.xml
            boolean hasStyles = false;
            try (var zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docxBytes))) {
                java.util.zip.ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    if ("word/styles.xml".equals(ze.getName())) {
                        hasStyles = true;
                        break;
                    }
                }
            }

            var baos = new java.io.ByteArrayOutputStream();
            try (var zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docxBytes));
                 var zout = new java.util.zip.ZipOutputStream(baos)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    byte[] data = zin.readAllBytes();
                    if ("[Content_Types].xml".equals(entry.getName()) && !hasStyles) {
                        // 添加styles.xml的Content Type声明
                        String ct = new String(data, StandardCharsets.UTF_8);
                        ct = ct.replace("</Types>",
                                "<Override ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\" PartName=\"/word/styles.xml\"/></Types>");
                        zout.write(ct.getBytes(StandardCharsets.UTF_8));
                    } else if ("word/_rels/document.xml.rels".equals(entry.getName()) && !hasStyles) {
                        // 添加styles.xml的关系引用
                        String rels = new String(data, StandardCharsets.UTF_8);
                        rels = rels.replace("</Relationships>",
                                "<Relationship Id=\"rIdStyles\" Target=\"styles.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\"/></Relationships>");
                        zout.write(rels.getBytes(StandardCharsets.UTF_8));
                    } else {
                        zout.write(data);
                    }
                    zout.closeEntry();
                }
                // 添加styles.xml
                if (!hasStyles) {
                    zout.putNextEntry(new java.util.zip.ZipEntry("word/styles.xml"));
                    String stylesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:docDefaults><w:rPrDefault><w:rPr>"
                            + "<w:lang w:val=\"" + lang + "\" w:eastAsia=\"" + lang + "\"/>"
                            + "<w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>"
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
