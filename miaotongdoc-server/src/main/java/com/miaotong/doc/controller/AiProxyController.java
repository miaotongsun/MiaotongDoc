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

    /**
     * 刷新模型列表 - AI 插件刷新按钮调用
     */
    @PostMapping("/refresh-models")
    public Object refreshModels() {
        return aiProxyService.refreshModels();
    }

    /**
     * 返回完整 AI 配置（供插件初始化使用，包含 proxy、providers、models）
     */
    @GetMapping("/config")
    public Object getConfig() {
        return aiProxyService.getConfig();
    }

    /**
     * 获取当前 AI 配置（管理后台展示用）
     */
    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        return aiProxyService.getCurrentConfig();
    }

    /**
     * 保存 AI 配置到文件（管理后台调用）
     */
    @PutMapping("/settings")
    public ResponseEntity<Map<String, String>> saveSettings(@RequestBody Map<String, Object> body) {
        aiProxyService.saveConfig(body);
        return ResponseEntity.ok(Map.of("message", "AI 配置已保存"));
    }
}
