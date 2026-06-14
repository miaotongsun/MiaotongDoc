package com.miaotong.doc.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class DocGenerator {

    public static byte[] create(String docType, String title) throws IOException {
        return switch (docType) {
            case "word" -> createDocx(title);
            case "cell" -> createXlsx(title);
            case "slide" -> createPptx(title);
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
            return toBytes(doc);
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
            ppt.createSlide();
            return toBytes(ppt);
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
