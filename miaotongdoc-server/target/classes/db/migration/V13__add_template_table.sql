-- 文档模板表
CREATE TABLE mt_document_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    doc_type VARCHAR(20) NOT NULL,          -- word/cell/slide
    file_path VARCHAR(1000) NOT NULL,       -- 模板文件存储路径
    file_size BIGINT DEFAULT 0,
    thumbnail_path VARCHAR(1000),           -- 缩略图路径
    category VARCHAR(100),                  -- 模板分类
    is_system BOOLEAN DEFAULT FALSE,        -- 是否系统预置模板
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_template_doc_type ON mt_document_template(doc_type);
CREATE INDEX idx_template_category ON mt_document_template(category);
CREATE INDEX idx_template_active ON mt_document_template(is_active);
