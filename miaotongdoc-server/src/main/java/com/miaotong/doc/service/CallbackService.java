package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentVersion;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.DocumentVersionRepository;
import com.miaotong.doc.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.storage-path:/data/documents}")
    private String storagePath;

    /**
     * 保存文档（OnlyOffice 回调触发）
     * - 只在文件内容真正变化时才创建新版本
     * - 通过 hash 对比判断是否有变化
     */
    @Transactional
    public void saveDocument(String key, String url, Long userId) {
        Document doc = documentRepository.findByDocKeyForUpdate(key)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + key));

        // 拒绝已锁定文档的保存回调
        if (Boolean.TRUE.equals(doc.getSigningLocked())) {
            log.warn("文档已锁定，拒绝保存回调: key={}", key);
            return;
        }

        // OnlyOffice 回调 URL 使用浏览器的 Host（localhost），但 web-server 容器内
        // 需要通过 nginx 容器访问，将 localhost 替换为 nginx 容器名
        String downloadUrl = url.replace("http://localhost", "http://nginx");
        byte[] fileBytes = restTemplate.getForEntity(downloadUrl, byte[].class).getBody();
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("下载文件为空: " + url);
        }

        String newHash = FileHashUtil.calculateSHA256(fileBytes);

        // 检查内容是否真的变化了
        if (doc.getFileHash() != null && doc.getFileHash().equals(newHash)) {
            log.info("文档内容未变化，跳过保存: key={}, hash={}", key, newHash);
            // 只更新文件大小（因为 OnlyOffice 可能微调了元数据）
            doc.setFileSize((long) fileBytes.length);
            documentRepository.save(doc);
            return;
        }

        log.info("文档内容变化，开始保存: key={}, oldHash={}, newHash={}", key, doc.getFileHash(), newHash);

        String filePath = saveFileAtomic(doc.getDocKey(), doc.getCurrentVersion(), doc.getFileType(), fileBytes);

        // 更新文档主记录（不创建版本记录，由 owner 手动创建）
        doc.setFilePath(filePath);
        doc.setFileHash(newHash);
        doc.setFileSize((long) fileBytes.length);
        documentRepository.save(doc);

        log.info("文档保存成功（自动保存，未创建版本）: docId={}, version={}", doc.getId(), doc.getCurrentVersion());
    }

    private String saveFileAtomic(String docKey, int version, String fileType, byte[] content) {
        try {
            String datePath = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM"));
            Path dir = Paths.get(storagePath, datePath, docKey);
            Files.createDirectories(dir);

            Path tempFile = dir.resolve("v" + version + "." + fileType + ".tmp");
            Path targetFile = dir.resolve("v" + version + "." + fileType);

            Files.write(tempFile, content);
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
            return targetFile.toString();
        } catch (Exception e) {
            throw new RuntimeException("保存文件失败", e);
        }
    }
}
