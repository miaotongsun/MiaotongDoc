package com.miaotong.doc.service;

import com.miaotong.doc.entity.WatermarkConfig;
import com.miaotong.doc.repository.WatermarkConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatermarkService {

    private final WatermarkConfigRepository watermarkConfigRepository;

    public WatermarkConfig getConfig() {
        return watermarkConfigRepository.findByName("default")
                .orElseGet(() -> {
                    WatermarkConfig config = new WatermarkConfig();
                    config.setName("default");
                    return watermarkConfigRepository.save(config);
                });
    }

    public WatermarkConfig getEnabledConfig() {
        return watermarkConfigRepository.findByIsEnabledTrue().orElse(null);
    }

    @Transactional
    public WatermarkConfig updateConfig(Map<String, Object> updates) {
        WatermarkConfig config = getConfig();

        if (updates.containsKey("isEnabled")) config.setIsEnabled((Boolean) updates.get("isEnabled"));
        if (updates.containsKey("textTemplate")) config.setTextTemplate((String) updates.get("textTemplate"));
        if (updates.containsKey("fontSize")) config.setFontSize((Integer) updates.get("fontSize"));
        if (updates.containsKey("fontColor")) config.setFontColor((String) updates.get("fontColor"));
        if (updates.containsKey("rotation")) config.setRotation((Integer) updates.get("rotation"));
        if (updates.containsKey("opacity")) {
            Object opacity = updates.get("opacity");
            if (opacity instanceof Double) config.setOpacity(((Double) opacity).floatValue());
            else if (opacity instanceof Float) config.setOpacity((Float) opacity);
        }
        if (updates.containsKey("position")) config.setPosition((String) updates.get("position"));

        return watermarkConfigRepository.save(config);
    }

    /**
     * 生成水印文字，替换模板中的变量
     * {username} - 用户名
     * {datetime} - 当前时间
     * {date} - 当前日期
     */
    public String generateWatermarkText(String username) {
        WatermarkConfig config = getEnabledConfig();
        if (config == null) return null;

        String text = config.getTextTemplate();
        LocalDateTime now = LocalDateTime.now();

        text = text.replace("{username}", username != null ? username : "");
        text = text.replace("{datetime}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        text = text.replace("{date}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return text;
    }
}
