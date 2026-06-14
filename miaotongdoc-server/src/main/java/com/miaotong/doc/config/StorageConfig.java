package com.miaotong.doc.config;

import com.miaotong.doc.service.storage.FileSystemStorageService;
import com.miaotong.doc.service.storage.MinioStorageService;
import com.miaotong.doc.service.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
    public StorageService fileSystemStorageService(StorageProperties props) {
        return new FileSystemStorageService(props.getBasePath());
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
    public StorageService minioStorageService(StorageProperties props) {
        StorageProperties.Minio minio = props.getMinio();
        return new MinioStorageService(
                minio.getEndpoint(),
                minio.getAccessKey(),
                minio.getSecretKey(),
                minio.getBucket()
        );
    }
}
