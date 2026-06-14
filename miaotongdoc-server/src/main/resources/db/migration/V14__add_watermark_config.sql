-- 水印配置表
CREATE TABLE mt_watermark_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL DEFAULT 'default',
    is_enabled BOOLEAN DEFAULT FALSE,
    text_template VARCHAR(500) DEFAULT '{username} {datetime}',  -- 水印文字模板
    font_size INTEGER DEFAULT 30,
    font_color VARCHAR(20) DEFAULT '#CCCCCC',
    rotation INTEGER DEFAULT -45,               -- 旋转角度
    opacity REAL DEFAULT 0.3,                    -- 透明度 0-1
    position VARCHAR(20) DEFAULT 'center',       -- center/tiled
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 插入默认配置
INSERT INTO mt_watermark_config (name, is_enabled, text_template)
VALUES ('default', FALSE, '{username} {datetime}');
