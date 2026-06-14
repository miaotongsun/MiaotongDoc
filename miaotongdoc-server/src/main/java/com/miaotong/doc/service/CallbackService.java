package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.service.storage.StorageService;
import com.miaotong.doc.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ActivityService activityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String APP_NAME = "MiaotongDoc";
    private static final String APP_VERSION = "1.0";
    private static final String APP_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
            + "<Application>" + APP_NAME + "</Application>"
            + "<AppVersion>" + APP_VERSION + "</AppVersion>"
            + "</Properties>";

    @Transactional
    public void saveDocument(String key, String url, Long userId) {
        Document doc = documentRepository.findByDocKeyForUpdate(key)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + key));

        if (Boolean.TRUE.equals(doc.getSigningLocked())) {
            log.warn("文档已锁定，拒绝保存回调: key={}", key);
            return;
        }

        String downloadUrl = url.replace("http://localhost", "http://nginx");
        byte[] fileBytes = restTemplate.getForEntity(downloadUrl, byte[].class).getBody();
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("下载文件为空: " + url);
        }

        // 修改 docx 文件中的 app.xml，替换应用程序名称
        if ("docx".equals(doc.getFileType()) || "xlsx".equals(doc.getFileType()) || "pptx".equals(doc.getFileType())) {
            fileBytes = replaceAppName(fileBytes);
        }

        String newHash = FileHashUtil.calculateSHA256(fileBytes);

        if (doc.getFileHash() != null && doc.getFileHash().equals(newHash)) {
            log.info("文档内容未变化，跳过保存: key={}, hash={}", key, newHash);
            doc.setFileSize((long) fileBytes.length);
            documentRepository.save(doc);
            return;
        }

        log.info("文档内容变化，开始保存: key={}, oldHash={}, newHash={}", key, doc.getFileHash(), newHash);

        // 自动保存写入 current.docx，不覆盖版本文件
        String objectKey = buildCurrentObjectKey(doc.getDocKey(), doc.getFileType());
        String filePath = storageService.store(objectKey, fileBytes);

        doc.setFilePath(filePath);
        doc.setFileHash(newHash);
        doc.setFileSize((long) fileBytes.length);
        doc.setUpdatedBy(userId);
        documentRepository.save(doc);

        // 记录编辑活动
        activityService.log(userId, doc.getId(), "EDIT", null);

        log.info("文档保存成功（自动保存，未创建版本）: docId={}, version={}", doc.getId(), doc.getCurrentVersion());
    }

    /**
     * 替换 docx/xlsx/pptx 文件中的 app.xml 应用程序名称
     */
    private byte[] replaceAppName(byte[] fileBytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
            ZipInputStream zin = new ZipInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zout = new ZipOutputStream(baos);

            boolean replaced = false;
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                if ("docProps/app.xml".equals(entry.getName())) {
                    zout.write(APP_XML.getBytes(StandardCharsets.UTF_8));
                    replaced = true;
                } else {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zin.read(buffer)) > 0) {
                        zout.write(buffer, 0, len);
                    }
                }
                zout.closeEntry();
            }
            zin.close();
            zout.close();

            if (replaced) {
                log.info("已替换文档应用程序名称为 {}/{}", APP_NAME, APP_VERSION);
            } else {
                log.warn("未找到 docProps/app.xml，跳过替换");
            }

            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("替换应用程序名称失败，使用原始文件: {}", e.getMessage());
            return fileBytes;
        }
    }

    /**
     * 自动保存使用 current.docx，与版本文件（v1.docx, v2.docx）分开
     */
    private String buildCurrentObjectKey(String docKey, String fileType) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "documents/" + datePath + "/" + docKey + "/current." + fileType;
    }

    private String buildObjectKey(String docKey, int version, String fileType) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return "documents/" + datePath + "/" + docKey + "/v" + version + "." + fileType;
    }
}
