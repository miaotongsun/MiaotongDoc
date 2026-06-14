package com.miaotong.doc.service.storage;

import lombok.extern.slf4j.Slf4j;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
public class FileSystemStorageService implements StorageService {

    private final String basePath;

    public FileSystemStorageService(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public String store(String objectKey, byte[] content) {
        try {
            Path target = Paths.get(basePath, objectKey);
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("保存文件失败: " + objectKey, e);
        }
    }

    @Override
    public byte[] load(String objectKey) {
        try {
            return Files.readAllBytes(Paths.get(basePath, objectKey));
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(Paths.get(basePath, objectKey));
        } catch (Exception e) {
            log.warn("删除文件失败: objectKey={}", objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(Paths.get(basePath, objectKey));
    }
}
