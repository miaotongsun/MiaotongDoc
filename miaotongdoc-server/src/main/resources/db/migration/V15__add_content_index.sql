-- 文档内容索引表（用于全文搜索）
CREATE TABLE mt_document_content_index (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL UNIQUE REFERENCES mt_document(id) ON DELETE CASCADE,
    content_text TEXT,                      -- 提取的纯文本内容
    content_updated_at TIMESTAMP DEFAULT NOW()
);

-- PostgreSQL 全文搜索索引
CREATE INDEX idx_content_index_text ON mt_document_content_index USING gin(to_tsvector('simple', content_text));
CREATE INDEX idx_content_index_doc_id ON mt_document_content_index(document_id);
