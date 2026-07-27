-- V28__create_openapi_key.sql
-- 对外服务 API Key 管理表（API 规范 v1）
CREATE TABLE IF NOT EXISTS sys_openapi_key (
    id BIGSERIAL PRIMARY KEY,
    access_key VARCHAR(64) NOT NULL UNIQUE,
    secret_prefix VARCHAR(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    owner_system VARCHAR(100),
    contact VARCHAR(200),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    rate_limit_per_minute INT DEFAULT 60,
    allowed_ips TEXT,
    last_used_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP
);

-- 索引：access_key 用于鉴权查表（高频）
CREATE INDEX IF NOT EXISTS idx_openapi_key_access
    ON sys_openapi_key (access_key);

-- 索引：enabled 用于启动自检和列表筛选
CREATE INDEX IF NOT EXISTS idx_openapi_key_enabled
    ON sys_openapi_key (enabled);

COMMENT ON TABLE sys_openapi_key IS '对外服务 API Key 管理';
COMMENT ON COLUMN sys_openapi_key.access_key IS '密钥明文(ak_前缀),每次请求匹配';
COMMENT ON COLUMN sys_openapi_key.secret_prefix IS '密钥前 8 位用于列表显示';
COMMENT ON COLUMN sys_openapi_key.name IS '密钥用途描述';
COMMENT ON COLUMN sys_openapi_key.owner_system IS '外部系统标识';
COMMENT ON COLUMN sys_openapi_key.contact IS '联系人/邮箱';
COMMENT ON COLUMN sys_openapi_key.expires_at IS '过期时间(NULL=永不过期)';
COMMENT ON COLUMN sys_openapi_key.rate_limit_per_minute IS '单 Key 每分钟限流';
COMMENT ON COLUMN sys_openapi_key.allowed_ips IS 'IP 白名单(逗号分隔)';
COMMENT ON COLUMN sys_openapi_key.revoked_at IS '吊销时间(软删除)';