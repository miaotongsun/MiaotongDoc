package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PaddleOCR 服务配置
 *
 * 默认指向 miaotongdoc-ocr-paddle-light 容器（端口 5003）。
 * 中文扫描件 OCR 主力，精度 90%+，支持表格识别（PP-Structure）。
 *
 * @since v2.5 PDF OCR 中文优化
 */
@Data
@Component
@ConfigurationProperties(prefix = "paddle-ocr")
public class PaddleOcrProperties {

    /** PaddleOCR 服务地址（Docker 内部地址） */
    private String serverUrl = "http://ocr-paddle:5003";

    /** 是否启用 PaddleOCR（中文扫描件主力） */
    private boolean enabled = false;

    /** 请求超时（秒） */
    private int timeout = 600;

    /** OCR 语言：ch / en / japan / korean */
    private String language = "ch";

    /** 是否启用表格识别（PP-Structure） */
    private boolean useTableRecognition = true;

    /** 是否启用版面分析（layout） */
    private boolean useLayout = true;

    /** 是否返回坐标（用于画布定位） */
    private boolean returnCoordinates = true;
}