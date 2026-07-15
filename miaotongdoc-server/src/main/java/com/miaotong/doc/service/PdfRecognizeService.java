package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.config.DoclingProperties;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfRecognizeService {

    private final DoclingProperties doclingProperties;
    private final DocumentService documentService;
    private final StorageService storageService;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15000);
            factory.setReadTimeout(900000); // 15 分钟
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    /**
     * 识别 PDF 文档，返回结构化结果
     */
    public Map<String, Object> recognize(Long documentId) {
        Document doc = documentService.getDocument(documentId);
        if (!"pdf".equals(doc.getFileType())) {
            throw new BusinessException("该文档不是 PDF 类型");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", documentId);
        result.put("title", doc.getTitle());

        // 尝试 Docling 解析（重试 1 次）
        if (doclingProperties.isEnabled() && isDoclingAvailable()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    Map<String, Object> doclingResult = recognizeWithDocling(doc);
                    result.putAll(doclingResult);
                    result.put("engine", "docling");
                    return result;
                } catch (Exception e) {
                    log.warn("Docling 识别失败 (尝试 {}/2): {}", attempt, e.getMessage());
                    if (attempt < 2) {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }

        // 回退到 OCR（扫描件识别）
        Map<String, Object> ocrResult = ocrService.recognizePdf(documentId, "auto");
        if ("success".equals(ocrResult.get("status"))) {
            result.putAll(ocrResult);
            result.put("engine", "tesseract");
            return result;
        }

        // 最后回退到 PDFBox
        Map<String, Object> pdfBoxResult = recognizeWithPdfBox(doc);
        result.putAll(pdfBoxResult);
        result.put("engine", "pdfbox");
        return result;
    }

    /**
     * 从识别结果中提取 OCR 坐标数据（按页分组）
     * 用于在 PDF 原图位置叠加文字层（用户可框选复制）
     */
    public Map<String, Object> extractOcrData(Map<String, Object> result) {
        Map<String, Object> ocrData = new LinkedHashMap<>();
        Object pagesObj = result.get("pages");
        if (!(pagesObj instanceof List)) {
            return ocrData;
        }
        List<?> pagesList = (List<?>) pagesObj;
        for (Object pageObj : pagesList) {
            if (!(pageObj instanceof Map)) continue;
            Map<?, ?> page = (Map<?, ?>) pageObj;
            Object pageNum = page.get("pageNum");
            Object regions = page.get("regions");
            if (pageNum == null || !(regions instanceof List)) continue;

            Map<String, Object> pageData = new LinkedHashMap<>();
            pageData.put("regions", regions);
            // 标注 DPI（用于前端坐标换算）
            pageData.put("dpi", 200);
            ocrData.put(String.valueOf(pageNum), pageData);
        }
        return ocrData;
    }

    /**
     * Docling 解析
     */
    private Map<String, Object> recognizeWithDocling(Document doc) throws Exception {
        byte[] pdfBytes = storageService.load(doc.getFilePath());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("files", new org.springframework.core.io.ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return doc.getTitle() + ".pdf";
            }
        });

        HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        String url = doclingProperties.getServerUrl() + "/v1/convert/file";
        log.info("Docling 识别: docId={}, url={}", doc.getId(), url);

        ResponseEntity<String> response = getRestTemplate().exchange(
                url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> apiResult = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> result = new HashMap<>();

            Object document = apiResult.get("document");
            if (document instanceof Map) {
                Map<?, ?> docMap = (Map<?, ?>) document;
                String mdContent = (String) docMap.get("md_content");
                result.put("content", mdContent != null ? mdContent : "");
                result.put("markdown", mdContent != null ? mdContent : "");

                Object tables = docMap.get("tables");
                result.put("tables", tables != null ? tables : Collections.emptyList());
            }

            result.put("status", "success");
            log.info("Docling 识别成功: docId={}, contentLength={}",
                    doc.getId(), ((String) result.get("content")).length());
            return result;
        }

        throw new RuntimeException("Docling 返回异常");
    }

    /**
     * PDFBox 回退解析
     */
    private Map<String, Object> recognizeWithPdfBox(Document doc) {
        Map<String, Object> result = new HashMap<>();
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());
            try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(pdf);
                result.put("content", text);
                result.put("markdown", text);
                result.put("tables", Collections.emptyList());
                result.put("status", "success");
                result.put("totalPages", pdf.getNumberOfPages());
                log.info("PDFBox 识别完成: docId={}, length={}", doc.getId(), text.length());
            }
        } catch (Exception e) {
            log.error("PDFBox 识别失败: docId={}", doc.getId(), e);
            result.put("content", "");
            result.put("markdown", "");
            result.put("tables", Collections.emptyList());
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 导出编辑后的内容为指定格式
     */
    public byte[] exportEdited(String content, String format) {
        switch (format) {
            case "md":
                return content.getBytes(StandardCharsets.UTF_8);
            case "txt":
                // 去除 Markdown 标记
                String plainText = content.replaceAll("#+ ", "")
                        .replaceAll("\\*\\*", "")
                        .replaceAll("\\*", "")
                        .replaceAll("`", "")
                        .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
                return plainText.getBytes(StandardCharsets.UTF_8);
            default:
                throw new BusinessException("不支持的导出格式: " + format);
        }
    }

    /**
     * 检查 Docling 是否可用
     */
    private boolean isDoclingAvailable() {
        try {
            String url = doclingProperties.getServerUrl() + "/health";
            ResponseEntity<String> response = getRestTemplate().getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
