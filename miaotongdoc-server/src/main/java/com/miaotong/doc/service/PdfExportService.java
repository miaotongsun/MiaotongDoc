package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final DocumentRepository documentRepository;
    private final com.miaotong.doc.util.EditorJwtUtil editorJwtUtil;

    @Value("${editor.server-url}")
    private String editorServerUrl;

    @Value("${editor.download-url}")
    private String downloadUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] convertToPdf(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        Map<String, Object> request = new HashMap<>();
        request.put("async", false);
        request.put("filetype", doc.getFileType());
        request.put("key", doc.getDocKey() + "_v" + doc.getCurrentVersion() + "_pdf");
        request.put("outputtype", "pdf");
        request.put("title", doc.getTitle() + "." + doc.getFileType());
        request.put("url", downloadUrl + "/" + documentId + "/file");
        request.put("token", editorJwtUtil.generateToken(request));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                editorServerUrl + "/ConvertService.ashx", request, Map.class);

        Map body = response.getBody();
        if (body == null || !body.containsKey("fileUrl")) {
            throw new BusinessException("PDF转换失败");
        }

        String fileUrl = (String) body.get("fileUrl");
        ResponseEntity<byte[]> fileResponse = restTemplate.getForEntity(fileUrl, byte[].class);
        byte[] result = fileResponse.getBody();
        if (result == null || result.length == 0) {
            throw new BusinessException("PDF文件下载为空");
        }
        return result;
    }
}
