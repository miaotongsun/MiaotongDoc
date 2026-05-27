-- MiaotongDoc 数据库初始化脚本
-- 14张核心表

-- 1. 部门表（多层架构）
CREATE TABLE IF NOT EXISTS sys_department (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES sys_department(id),
    level SMALLINT NOT NULL DEFAULT 1,
    path VARCHAR(500) NOT NULL,
    leader_user_id BIGINT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dept_parent ON sys_department(parent_id);
CREATE INDEX idx_dept_path ON sys_department(path);
CREATE INDEX idx_dept_code ON sys_department(code);

-- 2. 用户表（8位工号）
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    employee_id CHAR(8) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    department_id BIGINT REFERENCES sys_department(id),
    position VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    is_active BOOLEAN DEFAULT true,
    sso_only BOOLEAN DEFAULT false,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    password_changed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_dept ON sys_user(department_id);
CREATE INDEX idx_user_empid ON sys_user(employee_id);

-- 3. 文档主表
CREATE TABLE IF NOT EXISTS mt_document (
    id BIGSERIAL PRIMARY KEY,
    doc_key VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL DEFAULT '未命名文档',
    doc_type VARCHAR(10) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_hash VARCHAR(128),
    owner_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    department_id BIGINT REFERENCES sys_department(id),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    current_version INT DEFAULT 1,
    is_deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    is_starred BOOLEAN DEFAULT false,
    share_scope VARCHAR(20) DEFAULT 'private',
    signing_locked BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_doc_type ON mt_document(doc_type);
CREATE INDEX idx_mt_doc_dept ON mt_document(department_id);
CREATE INDEX idx_mt_doc_owner ON mt_document(owner_user_id);
CREATE INDEX idx_mt_doc_status ON mt_document(status);

-- 4. 文档共享表
CREATE TABLE IF NOT EXISTS mt_document_share (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    permission VARCHAR(20) NOT NULL DEFAULT 'view',
    shared_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, user_id)
);
CREATE INDEX idx_mt_share_doc ON mt_document_share(document_id);
CREATE INDEX idx_mt_share_user ON mt_document_share(user_id);

-- 5. 文档版本表
CREATE TABLE IF NOT EXISTS mt_document_version (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_hash VARCHAR(128),
    change_summary VARCHAR(500),
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, version_number)
);
CREATE INDEX idx_mt_ver_doc ON mt_document_version(document_id, version_number);

-- 6. 评论批注表
CREATE TABLE IF NOT EXISTS mt_comment (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    parent_id BIGINT REFERENCES mt_comment(id),
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    content TEXT NOT NULL,
    quote_text VARCHAR(1000),
    page_number INT,
    position VARCHAR(200),
    is_resolved BOOLEAN DEFAULT false,
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_comment_doc ON mt_comment(document_id);
CREATE INDEX idx_mt_comment_user ON mt_comment(user_id);

-- 7. @提及关联表
CREATE TABLE IF NOT EXISTS mt_mention (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES mt_comment(id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_mention_comment ON mt_mention(comment_id);
CREATE INDEX idx_mt_mention_user ON mt_mention(mentioned_user_id);

-- 8. 协作动态表
CREATE TABLE IF NOT EXISTS mt_activity (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES mt_document(id) ON DELETE SET NULL,
    document_title VARCHAR(500),
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    user_name VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    target_user_id BIGINT,
    detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_activity_doc ON mt_activity(document_id);
CREATE INDEX idx_mt_activity_user ON mt_activity(user_id);
CREATE INDEX idx_mt_activity_target ON mt_activity(target_user_id);
CREATE INDEX idx_mt_activity_time ON mt_activity(created_at DESC);

-- 9. 通知表
CREATE TABLE IF NOT EXISTS mt_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    from_user_id BIGINT REFERENCES sys_user(id),
    document_id BIGINT REFERENCES mt_document(id),
    type VARCHAR(30) NOT NULL,
    content TEXT,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_notif_user ON mt_notification(user_id, is_read, created_at DESC);

-- 10. 签署任务表
CREATE TABLE IF NOT EXISTS mt_signing_task (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    title VARCHAR(500),
    description TEXT,
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    required_count INT NOT NULL DEFAULT 0,
    completed_count INT DEFAULT 0,
    deadline TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_by BIGINT,
    cancel_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. 签署记录表
CREATE TABLE IF NOT EXISTS mt_signing_record (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES mt_signing_task(id) ON DELETE CASCADE,
    signer_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    sign_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    confirmed_at TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    document_hash VARCHAR(128),
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(task_id, signer_user_id)
);
CREATE INDEX idx_mt_sign_rec_task ON mt_signing_record(task_id);
CREATE INDEX idx_mt_sign_rec_user ON mt_signing_record(signer_user_id);

-- 12. 审计日志表
CREATE TABLE IF NOT EXISTS mt_audit_log (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT,
    user_id BIGINT NOT NULL,
    employee_id CHAR(8) NOT NULL,
    user_name VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30),
    resource_id BIGINT,
    detail TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_audit_doc ON mt_audit_log(document_id);
CREATE INDEX idx_mt_audit_user ON mt_audit_log(user_id);
CREATE INDEX idx_mt_audit_empid ON mt_audit_log(employee_id);
CREATE INDEX idx_mt_audit_time ON mt_audit_log(created_at DESC);
CREATE INDEX idx_mt_audit_action ON mt_audit_log(action);

-- 13. JWT黑名单表
CREATE TABLE IF NOT EXISTS mt_token_blacklist (
    id BIGSERIAL PRIMARY KEY,
    token_jti VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_bl_jti ON mt_token_blacklist(token_jti);
CREATE INDEX idx_mt_bl_expires ON mt_token_blacklist(expires_at);

-- 14. SSO身份关联表
CREATE TABLE IF NOT EXISTS mt_sso_identity (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    provider_id VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_email VARCHAR(200),
    external_name VARCHAR(200),
    raw_claims TEXT,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    UNIQUE(provider_id, external_id)
);
CREATE INDEX idx_sso_identity_user ON mt_sso_identity(user_id);
CREATE INDEX idx_sso_identity_lookup ON mt_sso_identity(provider_id, external_id);

-- 审计日志归档表（温数据）
CREATE TABLE IF NOT EXISTS mt_audit_log_archive (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT,
    user_id BIGINT NOT NULL,
    employee_id CHAR(8) NOT NULL,
    user_name VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30),
    resource_id BIGINT,
    detail TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mt_audit_archive_time ON mt_audit_log_archive(created_at DESC);
