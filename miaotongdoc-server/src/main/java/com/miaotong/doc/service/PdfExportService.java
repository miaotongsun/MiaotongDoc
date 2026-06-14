package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.util.EditorJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final DocumentRepository documentRepository;
    private final EditorJwtUtil editorJwtUtil;

    @Value("${editor.server-url}")
    private String editorServerUrl;

    @Value("${editor.download-url}")
    private String downloadUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] convertToPdf(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        String fileUrl = downloadUrl + "/" + documentId + "/file";
        String key = doc.getDocKey() + "_v" + doc.getCurrentVersion() + "_pdf";

        Map<String, Object> request = new HashMap<>();
        request.put("async", false);
        request.put("filetype", doc.getFileType());
        request.put("key", key);
        request.put("outputtype", "pdf");
        request.put("title", doc.getTitle() + "." + doc.getFileType());
        request.put("url", fileUrl);
        request.put("token", editorJwtUtil.generateToken(request));

        log.info("PDF转换请求: docId={}, key={}, url={}", documentId, key, fileUrl);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    editorServerUrl + "/ConvertService.ashx", request, Map.class);

            Map body = response.getBody();
            log.info("PDF转换响应: {}", body);

            if (body == null) {
                throw new BusinessException("PDF转换失败：响应为空");
            }

            // 检查是否有错误
            if (body.containsKey("error")) {
                throw new BusinessException("PDF转换失败：错误码 " + body.get("error"));
            }

            if (!body.containsKey("fileUrl")) {
                // 可能是异步转换，检查 endConvert 字段
                if (Boolean.FALSE.equals(body.get("endConvert"))) {
                    throw new BusinessException("PDF转换未完成，请稍后重试");
                }
                throw new BusinessException("PDF转换失败：未返回文件地址");
            }

            String resultFileUrl = (String) body.get("fileUrl");
            log.info("PDF文件地址: {}", resultFileUrl);

            ResponseEntity<byte[]> fileResponse = restTemplate.getForEntity(resultFileUrl, byte[].class);
            byte[] result = fileResponse.getBody();
            if (result == null || result.length == 0) {
                throw new BusinessException("PDF文件下载为空");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF转换异常: docId={}", documentId, e);
            throw new BusinessException("PDF转换失败：" + e.getMessage());
        }
    }
}
