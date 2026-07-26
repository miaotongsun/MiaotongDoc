package com.miaotong.doc.controller;

import com.miaotong.doc.service.AiProxyService;
import com.miaotong.doc.service.PaddleOcrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 服务状态查询端点(OCR AI 改造 v2)
 *
 * 用途:前端编辑器/管理后台启动时检测 AI Provider 配置状态
 * 返回 4 个 type 的配置情况 + 详细信息
 *
 * 接口:GET /api/ai/status
 * 权限:需登录(已认证用户)
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiStatusController {

    private final AiProxyService aiProxyService;
    private final PaddleOcrClient paddleOcrClient;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        // LLM
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("configured", aiProxyService.getDefaultModel() != null);
        llm.put("defaultModel", aiProxyService.getDefaultModel());
        llm.put("baseUrl", aiProxyService.getTargetUrl() != null);
        result.put("llm", llm);

        // VISION
        result.put("vision", aiProxyService.getVisionStatus());

        // OCR_PADDLE
        Map<String, Object> ocrPaddle = new LinkedHashMap<>();
        boolean paddleAvailable = paddleOcrClient.isAvailable();
        ocrPaddle.put("configured", paddleAvailable);
        ocrPaddle.put("available", paddleAvailable);
        result.put("ocrPaddle", ocrPaddle);

        // DOCLING(暂保留占位,后续可扩展)
        result.put("docling", Map.of("configured", false, "available", false));

        // 总体可用性
        boolean anyAiAvailable = (boolean) llm.get("configured")
                || (boolean) ((Map<?, ?>) result.get("vision")).get("configured")
                || paddleAvailable;
        result.put("anyAvailable", anyAiAvailable);

        log.debug("AI 状态查询: llm={}, vision={}, ocrPaddle={}",
                llm.get("configured"),
                ((Map<?, ?>) result.get("vision")).get("configured"),
                paddleAvailable);

        return ResponseEntity.ok(result);
    }
}