package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.WatermarkConfig;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.util.EditorJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final DocumentRepository documentRepository;
    private final EditorJwtUtil editorJwtUtil;
    private final WatermarkService watermarkService;

    @Value("${editor.server-url}")
    private String editorServerUrl;

    @Value("${editor.download-url}")
    private String downloadUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String APP_NAME = "MiaotongDoc";
    private static final String APP_VERSION = "1.0";

    public byte[] convertToPdf(Long documentId, String username) {
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

            if (body.containsKey("error")) {
                throw new BusinessException("PDF转换失败：错误码 " + body.get("error"));
            }

            if (!body.containsKey("fileUrl")) {
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

            // 修改 PDF 元数据，替换应用程序名称
            result = replacePdfMetadata(result, doc.getTitle());

            // 添加水印（如果启用）
            result = addWatermark(result, username);

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF转换异常: docId={}", documentId, e);
            throw new BusinessException("PDF转换失败：" + e.getMessage());
        }
    }

    /**
     * 替换 PDF 文件的元数据
     * 删除 XMP 元数据（避免读取到 OnlyOffice 写入的值），只保留 Info 字典
     */
    private byte[] replacePdfMetadata(byte[] pdfBytes, String title) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // 1. 修改 Info 字典
            PDDocumentInformation info = document.getDocumentInformation();
            info.setProducer(APP_NAME + "/" + APP_VERSION);
            info.setCreator(APP_NAME + "/" + APP_VERSION);
            if (title != null) {
                info.setTitle(title);
            }

            // 2. 删除 XMP 元数据（避免 PDF 读取器显示 OnlyOffice 的值）
            document.getDocumentCatalog().setMetadata(null);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("已替换PDF元数据为 {}/{}", APP_NAME, APP_VERSION);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("替换PDF元数据失败，使用原始文件: {}", e.getMessage());
            return pdfBytes;
        }
    }

    /**
     * 为 PDF 添加水印
     */
    private byte[] addWatermark(byte[] pdfBytes, String username) {
        WatermarkConfig config = watermarkService.getEnabledConfig();
        if (config == null) return pdfBytes;

        String watermarkText = watermarkService.generateWatermarkText(username);
        if (watermarkText == null || watermarkText.isEmpty()) return pdfBytes;

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDType0Font font = PDType0Font.load(document, new java.io.ByteArrayInputStream(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
            ));

            for (PDPage page : document.getPages()) {
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(config.getOpacity());

                try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gs);
                    cs.setNonStrokingColor(Color.decode(config.getFontColor()));
                    cs.setFont(font, config.getFontSize());

                    if ("tiled".equals(config.getPosition())) {
                        // 平铺水印
                        float stepX = pageWidth / 3;
                        float stepY = pageHeight / 3;
                        for (int row = 0; row < 3; row++) {
                            for (int col = 0; col < 3; col++) {
                                float x = col * stepX + stepX / 2;
                                float y = row * stepY + stepY / 2;
                                cs.saveGraphicsState();
                                cs.transform(Matrix.getTranslateInstance(x, y));
                                cs.transform(Matrix.getRotateInstance(Math.toRadians(config.getRotation()), 0, 0));
                                cs.showText(watermarkText);
                                cs.restoreGraphicsState();
                            }
                        }
                    } else {
                        // 居中水印
                        float x = pageWidth / 2;
                        float y = pageHeight / 2;
                        cs.saveGraphicsState();
                        cs.transform(Matrix.getTranslateInstance(x, y));
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(config.getRotation()), 0, 0));
                        cs.showText(watermarkText);
                        cs.restoreGraphicsState();
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("已添加水印: {}", watermarkText);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("添加水印失败，返回原始文件: {}", e.getMessage());
            return pdfBytes;
        }
    }
}
