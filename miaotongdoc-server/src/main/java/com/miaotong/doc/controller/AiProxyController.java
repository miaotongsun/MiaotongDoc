package com.miaotong.doc.controller;

import com.miaotong.doc.service.AiProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiProxyController {

    private final AiProxyService aiProxyService;

    @PostMapping("/proxy")
    public Object proxy(@RequestBody Map<String, Object> body) {
        return aiProxyService.proxy(body);
    }

    @GetMapping("/models")
    public Object getModels() {
        return aiProxyService.getModels();
    }

    /**
     * 返回完整 AI 配置（供插件初始化使用）
     * 包含 proxy、provider URL/key、可用模型列表
     */
    @GetMapping("/config")
    public Object getConfig() {
        return aiProxyService.getConfig();
    }
}
