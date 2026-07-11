-- V22: PDF 工具操作异步任务表
CREATE TABLE IF NOT EXISTS mt_pdf_task (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id) ON DELETE CASCADE,
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    parameters JSONB,
    result_document_id BIGINT,
    result_file_path VARCHAR(500),
    error_message TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pdf_task_status ON mt_pdf_task(status);
CREATE INDEX IF NOT EXISTS idx_pdf_task_doc ON mt_pdf_task(document_id);
CREATE INDEX IF NOT EXISTS idx_pdf_task_created_by ON mt_pdf_task(created_by);
