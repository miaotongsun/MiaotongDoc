package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String type = "local";
    private String basePath = "/data/documents";
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint = "http://minio:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "miaotongdoc";
    }
}
