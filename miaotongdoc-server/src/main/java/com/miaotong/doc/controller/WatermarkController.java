package com.miaotong.doc.controller;

import com.miaotong.doc.entity.WatermarkConfig;
import com.miaotong.doc.service.WatermarkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/watermark")
@RequiredArgsConstructor
public class WatermarkController {

    private final WatermarkService watermarkService;

    @GetMapping("/config")
    public ResponseEntity<WatermarkConfig> getConfig() {
        return ResponseEntity.ok(watermarkService.getConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<WatermarkConfig> updateConfig(
            @RequestBody Map<String, Object> updates,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("role");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(watermarkService.updateConfig(updates));
    }

    @GetMapping("/preview")
    public ResponseEntity<Map<String, String>> previewWatermark(HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("username");
        String text = watermarkService.generateWatermarkText(username);
        return ResponseEntity.ok(Map.of("text", text != null ? text : ""));
    }
}
