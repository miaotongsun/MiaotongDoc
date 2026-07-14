-- =============================================================
-- V25: AI Provider 多 Provider 动态配置
-- 目的：支持管理后台动态配置 LLM/PaddleOCR/Vision 等 AI 服务
-- 设计：
--   - mt_ai_provider: 多个 Provider（按 type 分类：LLM/OCR_PADDLE/VISION）
--   - 每个 Provider 可独立启用/禁用
--   - key 加密存储（AES 简单加密，密钥从环境变量 APP_AI_KEY_SECRET）
--   - 兼容旧版本：保留 /data/config/ai-config.json 作为 fallback
-- =============================================================

CREATE TABLE IF NOT EXISTS mt_ai_provider (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL,        -- LLM | OCR_PADDLE | VISION | DOCLING | OCR_TESSERACT
    name            VARCHAR(64)  NOT NULL,        -- 显示名（OpenAI / DeepSeek / 阿里云 / 自建）
    base_url        VARCHAR(512) NOT NULL,        -- http://api.openai.com/v1
    api_key         TEXT,                         -- 加密存储（可选）
    default_model   VARCHAR(128),                 -- gpt-4o-mini / Qwen3-32B ...
    timeout         INTEGER      DEFAULT 300,     -- 秒
    enabled         BOOLEAN      DEFAULT true,
    is_default      BOOLEAN      DEFAULT false,   -- 同 type 多 Provider 时默认用哪个
    remark          VARCHAR(512),
    extra           TEXT,                         -- JSON 扩展（temperature/max_tokens 等）
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(type, name)
);

CREATE INDEX IF NOT EXISTS idx_mt_ai_provider_type ON mt_ai_provider(type);
CREATE INDEX IF NOT EXISTS idx_mt_ai_provider_enabled ON mt_ai_provider(enabled);

COMMENT ON TABLE  mt_ai_provider IS 'AI Provider 动态配置表（多 Provider + 热更新）';
COMMENT ON COLUMN mt_ai_provider.type IS 'AI 服务类型：LLM / OCR_PADDLE / VISION / DOCLING / OCR_TESSERACT';
COMMENT ON COLUMN mt_ai_provider.base_url IS '服务地址（含 /v1）';
COMMENT ON COLUMN mt_ai_provider.api_key IS '加密存储的 API Key';
COMMENT ON COLUMN mt_ai_provider.is_default IS '同 type 多个 Provider 时的默认选择';

-- 初始化默认 LLM Provider（从环境变量 LLM_API_URL / LLM_API_KEY 读取）
INSERT INTO mt_ai_provider (type, name, base_url, api_key, default_model, timeout, enabled, is_default, remark)
SELECT
    'LLM',
    'default',
    COALESCE(NULLIF(MAX(CASE WHEN key = 'LLM_API_URL' THEN value END), ''), 'https://api.openai.com/v1'),
    COALESCE(NULLIF(MAX(CASE WHEN key = 'LLM_API_KEY' THEN value END), ''), ''),
    COALESCE(NULLIF(MAX(CASE WHEN key = 'LLM_DEFAULT_MODEL' THEN value END), ''), 'gpt-4o-mini'),
    300,
    true,
    true,
    '系统初始默认 LLM Provider（从环境变量迁移）'
FROM (VALUES
    ('LLM_API_URL',     COALESCE(current_setting('app.llm_api_url',     true), '')),
    ('LLM_API_KEY',     COALESCE(current_setting('app.llm_api_key',     true), '')),
    ('LLM_DEFAULT_MODEL', COALESCE(current_setting('app.llm_default_model', true), ''))
) AS kv(key, value)
WHERE NOT EXISTS (SELECT 1 FROM mt_ai_provider WHERE type = 'LLM' AND is_default = true);

-- 初始化 PaddleOCR 默认 Provider
INSERT INTO mt_ai_provider (type, name, base_url, enabled, is_default, remark)
SELECT 'OCR_PADDLE', 'default', 'http://ocr-paddle:5003', true, true,
       'PaddleOCR 中文扫描件（容器内置）'
WHERE NOT EXISTS (SELECT 1 FROM mt_ai_provider WHERE type = 'OCR_PADDLE' AND is_default = true);

-- 初始化 VLM（视觉问答）默认 Provider
INSERT INTO mt_ai_provider (type, name, base_url, api_key, default_model, enabled, is_default, remark)
SELECT 'VISION', 'default',
       COALESCE(NULLIF(current_setting('app.llm_api_url', true), ''), 'https://api.openai.com/v1'),
       COALESCE(NULLIF(current_setting('app.llm_api_key', true), ''), ''),
       COALESCE(NULLIF(current_setting('app.llm_vision_model', true), ''), 'gpt-4o'),
       true, true, 'VLM 视觉问答（默认复用 LLM 端点）'
WHERE NOT EXISTS (SELECT 1 FROM mt_ai_provider WHERE type = 'VISION' AND is_default = true);
