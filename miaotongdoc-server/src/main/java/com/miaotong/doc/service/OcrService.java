package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * OCR 服务 - 使用 Tesseract 进行文字识别
 * 用于扫描件 PDF 的文字提取（Docling 的备用方案）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ocr.server-url:http://ocr:5002}")
    private String ocrServerUrl;

    @Value("${ocr.enabled:false}")
    private boolean ocrEnabled;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15000);
            factory.setReadTimeout(600000); // 10 分钟
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    /**
     * 对 PDF 进行 OCR 识别
     */
    public Map<String, Object> recognizePdf(Long documentId, String language) {
        Document doc = documentService.getDocument(documentId);
        Map<String, Object> result = new HashMap<>();

        if (!ocrEnabled || !isOcrAvailable()) {
            log.warn("OCR 服务不可用");
            result.put("status", "unavailable");
            result.put("error", "OCR 服务未启用或不可用");
            return result;
        }

        try {
            byte[] pdfBytes = storageService.load(doc.getFilePath());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return doc.getTitle() + ".pdf";
                }
            });
            body.add("language", language != null ? language : "auto");

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            String url = ocrServerUrl + "/ocr/pdf";
            log.info("OCR 识别: docId={}, url={}", doc.getId(), url);

            ResponseEntity<String> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> apiResult = objectMapper.readValue(response.getBody(), Map.class);

                result.put("content", apiResult.get("fullText"));
                result.put("markdown", apiResult.get("fullText"));
                result.put("pages", apiResult.get("pages"));
                result.put("totalPages", apiResult.get("totalPages"));
                result.put("engine", "tesseract");
                result.put("status", "success");

                log.info("OCR 识别成功: docId={}, pages={}", doc.getId(), apiResult.get("totalPages"));
                return result;
            }

            throw new RuntimeException("OCR 返回异常");
        } catch (Exception e) {
            log.error("OCR 识别失败: docId={}", doc.getId(), e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 检查 OCR 服务是否可用
     */
    private boolean isOcrAvailable() {
        try {
            String url = ocrServerUrl + "/health";
            ResponseEntity<String> response = getRestTemplate().getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("OCR 服务不可用: {}", e.getMessage());
            return false;
        }
    }
}
