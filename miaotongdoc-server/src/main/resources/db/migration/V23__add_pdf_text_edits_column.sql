-- V23__add_pdf_text_edits_column.sql
-- 添加 PDF 文字编辑支持：在 mt_document 表添加 text_edits JSONB 列

ALTER TABLE mt_document ADD COLUMN IF NOT EXISTS text_edits JSONB DEFAULT '[]';

COMMENT ON COLUMN mt_document.text_edits IS 'PDF 文字编辑记录（JSONB 格式）：存储添加、删除、修改文字的编辑操作列表';

-- 创建索引以加速查询
CREATE INDEX IF NOT EXISTS idx_mt_document_text_edits ON mt_document USING GIN (text_edits);
