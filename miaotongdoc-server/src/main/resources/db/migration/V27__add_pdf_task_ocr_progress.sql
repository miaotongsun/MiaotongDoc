-- V27__add_pdf_task_ocr_progress.sql
-- OCR AI 改造 v3:扩展 mt_pdf_task 表,支持 OCR 异步任务进度追踪
ALTER TABLE mt_pdf_task
    ADD COLUMN IF NOT EXISTS progress INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_page INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_pages INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS model VARCHAR(50) DEFAULT 'mobile',
    ADD COLUMN IF NOT EXISTS language VARCHAR(20) DEFAULT 'ch',
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- 给进度查询加索引
CREATE INDEX IF NOT EXISTS idx_mt_pdf_task_status_updated
    ON mt_pdf_task (status, updated_at);

-- 给文档+状态查询加索引
CREATE INDEX IF NOT EXISTS idx_mt_pdf_task_doc_status
    ON mt_pdf_task (document_id, status);

COMMENT ON COLUMN mt_pdf_task.progress IS 'OCR 识别进度 0-100';
COMMENT ON COLUMN mt_pdf_task.current_page IS '当前识别页(1-based)';
COMMENT ON COLUMN mt_pdf_task.total_pages IS '总页数';
COMMENT ON COLUMN mt_pdf_task.model IS 'OCR 模型:mobile / server';
COMMENT ON COLUMN mt_pdf_task.language IS 'OCR 语言:ch/en/japan/korean';
COMMENT ON COLUMN mt_pdf_task.started_at IS '任务开始处理时间';
COMMENT ON COLUMN mt_pdf_task.updated_at IS '进度最后更新时间(SSE 推送用)';