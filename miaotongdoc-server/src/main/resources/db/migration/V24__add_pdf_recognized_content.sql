-- V24__add_pdf_recognized_content.sql
-- 添加 PDF 识别内容存储支持

-- PDF 识别后的 Markdown 内容（按页分组：{"1": "# 第一页\n内容...", "2": "第二页内容..."}）
ALTER TABLE mt_document ADD COLUMN IF NOT EXISTS pdf_markdown JSONB DEFAULT '{}';

-- 是否已识别标志
ALTER TABLE mt_document ADD COLUMN IF NOT EXISTS pdf_recognized BOOLEAN DEFAULT FALSE;

-- 识别完成时间
ALTER TABLE mt_document ADD COLUMN IF NOT EXISTS pdf_recognized_at TIMESTAMP;

-- 添加注释
COMMENT ON COLUMN mt_document.pdf_markdown IS 'PDF 识别后的 Markdown 内容（按页分组，key 为页码）';
COMMENT ON COLUMN mt_document.pdf_recognized IS 'PDF 是否已完成识别';
COMMENT ON COLUMN mt_document.pdf_recognized_at IS 'PDF 识别完成时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_mt_document_pdf_recognized ON mt_document(pdf_recognized) WHERE pdf_recognized = FALSE;
