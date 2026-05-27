package com.miaotong.doc.controller;

import com.miaotong.doc.service.CallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;

    @PostMapping("/editor")
    public ResponseEntity<Map<String, Integer>> callback(@RequestBody Map<String, Object> body) {
        String key = (String) body.get("key");
        int status = ((Number) body.get("status")).intValue();
        String url = (String) body.get("url");
        @SuppressWarnings("unchecked")
        java.util.List<String> users = (java.util.List<String>) body.get("users");
        Long userId = (users != null && !users.isEmpty()) ? Long.parseLong(users.get(0)) : 1L;

        switch (status) {
            case 1 -> log.info("文档正在编辑中: key={}", key);

            case 2, 6 -> {
                if (url == null) {
                    log.error("回调 URL 为空: key={}", key);
                    break;
                }
                try {
                    callbackService.saveDocument(key, url, userId);
                } catch (Exception e) {
                    log.error("保存文档失败: key={}", key, e);
                    return ResponseEntity.ok(Map.of("error", 1));
                }
            }

            case 3 -> {
                log.error("文档保存错误: key={}", key);
                return ResponseEntity.ok(Map.of("error", 1));
            }
            case 4 -> log.info("文档已关闭，无修改: key={}", key);
            case 5 -> {
                if (url != null) {
                    try {
                        callbackService.saveDocument(key, url, userId);
                        log.info("强制保存文档: key={}", key);
                    } catch (Exception e) {
                        log.error("强制保存文档失败: key={}", key, e);
                    }
                }
            }
            case 7 -> log.warn("回调中发生错误: key={}", key);
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }
}
