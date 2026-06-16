-- V21: 支持 Markdown 和 PDF 文档类型
-- doc_type 和 file_type 字段是 VARCHAR，无需修改表结构
-- 此迁移文件仅用于记录版本

-- 新增 PDF 注释存储表（用于注释状态持久化和版本快照）
CREATE TABLE IF NOT EXISTS mt_pdf_annotation (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    annotation_data JSONB NOT NULL DEFAULT '[]',
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(document_id)
);

CREATE INDEX IF NOT EXISTS idx_pdf_annotation_doc_id ON mt_pdf_annotation(document_id);
