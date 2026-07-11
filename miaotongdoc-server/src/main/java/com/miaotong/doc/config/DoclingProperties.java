package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "docling")
public class DoclingProperties {

    private String serverUrl = "http://docling:5001";

    private boolean enabled = false;

    private int timeout = 120;

    private String ocrLanguages = "chi_sim+eng";
}
