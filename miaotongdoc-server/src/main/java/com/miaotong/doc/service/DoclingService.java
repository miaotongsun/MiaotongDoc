package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.config.DoclingProperties;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoclingService {

    private final DoclingProperties doclingProperties;
    private final DocumentService documentService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15000);
            factory.setReadTimeout(900000); // 15 分钟，处理大型扫描件
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    /**
     * 解析 PDF 为结构化 Markdown
     * 优先使用 Docling，不可用时回退到 PDFTextStripper
     */
    public String parse(Long documentId) {
        Document doc = documentService.getDocument(documentId);

        if (doclingProperties.isEnabled() && isDoclingAvailable()) {
            try {
                return parseWithDocling(doc);
            } catch (Exception e) {
                log.warn("Docling 解析失败，回退到 PDFTextStripper: {}", e.getMessage());
            }
        }

        return parseWithPdfBox(doc);
    }

    /**
     * 解析并分块
     */
    public List<String> parseWithChunks(Long documentId) {
        String content = parse(documentId);
        return smartChunk(content, 4000, 200);
    }

    /**
     * 提取表格为结构化数据
     */
    public String extractTables(Long documentId) {
        Document doc = documentService.getDocument(documentId);

        if (doclingProperties.isEnabled() && isDoclingAvailable()) {
            try {
                return extractTablesWithDocling(doc);
            } catch (Exception e) {
                log.warn("Docling 表格提取失败: {}", e.getMessage());
            }
        }

        // 回退：返回纯文本（表格结构丢失）
        return parseWithPdfBox(doc);
    }

    /**
     * 用 Docling 解析 PDF（同步 API，超时 10 分钟）
     */
    private String parseWithDocling(Document doc) throws Exception {
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
        log.info("调用 Docling 解析: docId={}, url={}", doc.getId(), url);

        ResponseEntity<String> response = getRestTemplate().exchange(
                url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
            Object document = result.get("document");
            if (document instanceof Map) {
                String mdContent = (String) ((Map<?, ?>) document).get("md_content");
                if (mdContent != null && !mdContent.isEmpty()) {
                    log.info("Docling 解析成功: docId={}, length={}", doc.getId(), mdContent.length());
                    return mdContent;
                }
            }
        }

        throw new RuntimeException("Docling 返回格式异常");
    }

    /**
     * 用 Docling 提取表格（同步 API）
     */
    private String extractTablesWithDocling(Document doc) throws Exception {
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
        ResponseEntity<String> response = getRestTemplate().exchange(
                url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
            Object document = result.get("document");
            if (document instanceof Map) {
                Object tables = ((Map<?, ?>) document).get("tables");
                if (tables != null) {
                    return objectMapper.writeValueAsString(tables);
                }
            }
        }

        throw new RuntimeException("Docling 表格提取返回为空");
    }

    /**
     * PDFTextStripper 回退方案
     */
    private String parseWithPdfBox(Document doc) {
        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());

            if ("pdf".equals(doc.getFileType())) {
                try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(pdfBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(pdf);
                }
            } else if ("docx".equals(doc.getFileType())) {
                return extractTextFromDocx(pdfBytes);
            } else {
                return new String(pdfBytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("PDFBox 文本提取失败: docId={}", doc.getId(), e);
            return "";
        }
    }

    /**
     * 从 docx 提取纯文本
     */
    private String extractTextFromDocx(byte[] bytes) {
        try {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replaceAll("<[^>]+>", " ")
                            .replaceAll("&amp;", "&")
                            .replaceAll("&lt;", "<")
                            .replaceAll("&gt;", ">")
                            .replaceAll("&quot;", "\"")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
            }
        } catch (Exception e) {
            log.warn("解析 docx 失败", e);
        }
        return "";
    }

    /**
     * 智能分块：按语义边界分割文本
     */
    public List<String> smartChunk(String text, int maxChunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();

        // 优先按段落分割（连续两个换行）
        String[] paragraphs = text.split("\n{2,}");

        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                // 保留最后 overlap 字符作为上下文重叠
                String chunkStr = currentChunk.toString();
                if (chunkStr.length() > overlap) {
                    currentChunk = new StringBuilder(chunkStr.substring(chunkStr.length() - overlap));
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(paragraph).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 检测文档类型
     */
    public String detectDocumentType(Long documentId) {
        String content = parse(documentId);
        if (content.isEmpty()) return "unknown";

        // 简单启发式判断
        String lower = content.toLowerCase();
        if (lower.contains("合同") || lower.contains("contract") || lower.contains("甲方") || lower.contains("乙方")) {
            return "contract";
        } else if (lower.contains("发票") || lower.contains("invoice")) {
            return "invoice";
        } else if (lower.contains("报告") || lower.contains("report")) {
            return "report";
        } else if (lower.contains("制度") || lower.contains("规定") || lower.contains("办法")) {
            return "policy";
        }
        return "general";
    }

    /**
     * 检测文档语言
     */
    public String detectLanguage(Long documentId) {
        String content = parse(documentId);
        if (content.isEmpty()) return "unknown";

        long chineseCount = content.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long englishCount = content.chars().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();

        if (chineseCount > englishCount * 2) return "zh";
        if (englishCount > chineseCount * 2) return "en";
        return "mixed";
    }

    /**
     * 检查 Docling 服务是否可用
     */
    private boolean isDoclingAvailable() {
        try {
            String url = doclingProperties.getServerUrl() + "/health";
            ResponseEntity<String> response = getRestTemplate().getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Docling 服务不可用: {}", e.getMessage());
            return false;
        }
    }
}
