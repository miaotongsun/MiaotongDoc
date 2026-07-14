package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.config.PaddleOcrProperties;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * PaddleOCR 服务客户端
 *
 * 用途：中文扫描件 PDF 文字识别（主力），精度 90%+
 *
 * 引擎能力：
 *   - PP-OCRv4：中文 / 英文 / 多语言 OCR
 *   - PP-Structure：表格识别（输出 HTML/Markdown 表格）
 *   - PP-Layout：版面分析（标题/段落/图片分类）
 *
 * 接口约定（与 Tesseract OCR 服务兼容）：
 *   POST /ocr/pdf   multipart: file, language, use_table, use_layout, return_coords
 *
 * 输出格式：
 *   {
 *     "status": "success",
 *     "engine": "paddleocr",
 *     "totalPages": 10,
 *     "fullText": "全文（Markdown 含表格）",
 *     "pages": [
 *       {
 *         "pageNum": 1,
 *         "text": "本页文本",
 *         "tables": [{"html": "<table>...</table>", "markdown": "|...|"}],
 *         "regions": [{"type": "title", "text": "...", "bbox": [x,y,w,h]}]
 *       }
 *     ]
 *   }
 *
 * @since v2.5 PDF OCR 中文优化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaddleOcrClient {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final PaddleOcrProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15000);
            // setReadTimeout 上限约 25 天，转 int 安全（PaddleOCR 默认 600s）
            int readTimeout = Math.min(properties.getTimeout(), 25 * 60 * 60) * 1000;
            factory.setReadTimeout(readTimeout);
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    /**
     * 对 PDF 进行 PaddleOCR 识别
     *
     * @param documentId 文档 ID
     * @param language 语言（ch/en/japan/korean）
     * @param progressCallback 进度回调（percent 0-100, message）
     */
    public Map<String, Object> recognizePdf(Long documentId, String language,
                                            ProgressCallback progressCallback) {
        Map<String, Object> result = new HashMap<>();
        Document doc = documentService.getDocument(documentId);

        if (!properties.isEnabled()) {
            result.put("status", "unavailable");
            result.put("error", "PaddleOCR 服务未启用");
            return result;
        }

        if (!isAvailable()) {
            result.put("status", "unavailable");
            result.put("error", "PaddleOCR 服务不可用");
            return result;
        }

        try {
            if (progressCallback != null) progressCallback.onProgress(20, "准备上传 PDF...");

            byte[] pdfBytes = storageService.load(doc.getFilePath());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return doc.getTitle() + ".pdf";
                }
            });
            body.add("language", language != null ? language : properties.getLanguage());
            body.add("use_table", String.valueOf(properties.isUseTableRecognition()));
            body.add("use_layout", String.valueOf(properties.isUseLayout()));
            body.add("return_coords", String.valueOf(properties.isReturnCoordinates()));

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            String url = properties.getServerUrl() + "/ocr/pdf";
            log.info("PaddleOCR 识别: docId={}, url={}, lang={}, table={}, layout={}",
                    doc.getId(), url, language, properties.isUseTableRecognition(), properties.isUseLayout());

            if (progressCallback != null) progressCallback.onProgress(30, "PaddleOCR 处理中...");

            ResponseEntity<String> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (progressCallback != null) progressCallback.onProgress(80, "解析返回结果...");

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> apiResult = objectMapper.readValue(response.getBody(), Map.class);

                String fullText = (String) apiResult.getOrDefault("fullText", "");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pagesList = (List<Map<String, Object>>) apiResult.get("pages");

                // 按页号构造 Map<pageNum, markdownContent>，供 savePdfMarkdown 使用
                Map<String, String> markdownByPage = new LinkedHashMap<>();
                if (pagesList != null) {
                    for (Map<String, Object> page : pagesList) {
                        Object pageNum = page.get("pageNum");
                        Object pageText = page.get("text");
                        markdownByPage.put(String.valueOf(pageNum), String.valueOf(pageText));
                    }
                }
                // 若无分页信息，则放入单页全文
                if (markdownByPage.isEmpty()) {
                    markdownByPage.put("1", fullText);
                }

                result.put("content", fullText);
                result.put("markdown", markdownByPage);
                result.put("pages", apiResult.get("pages"));
                result.put("totalPages", apiResult.get("totalPages"));
                result.put("engine", "paddleocr");
                result.put("language", language);
                result.put("status", "success");

                log.info("PaddleOCR 识别成功: docId={}, pages={}, chars={}",
                        doc.getId(), apiResult.get("totalPages"), fullText.length());
                return result;
            }

            throw new RuntimeException("PaddleOCR 返回异常: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("PaddleOCR 识别失败: docId={}", doc.getId(), e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /** 健康检查 */
    public boolean isAvailable() {
        try {
            String url = properties.getServerUrl() + "/health";
            ResponseEntity<String> response = getRestTemplate().getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("PaddleOCR 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int percent, String message);
    }
}