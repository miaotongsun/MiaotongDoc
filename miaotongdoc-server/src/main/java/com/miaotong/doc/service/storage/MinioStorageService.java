package com.miaotong.doc.service.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(String endpoint, String accessKey, String secretKey, String bucket) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建 MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("初始化 MinIO bucket 失败", e);
        }
    }

    @Override
    public String store(String objectKey, byte[] content) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("上传文件到 MinIO 失败: " + objectKey, e);
        }
    }

    @Override
    public byte[] load(String objectKey) {
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("从 MinIO 读取文件失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("从 MinIO 删除文件失败: objectKey={}", objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
