-- 回收站清理调度器：删除30天前的已删除文档
-- 由 OnlyOfficeCleanupScheduler 或单独的定时任务执行

-- 为审计日志添加索引，提升查询性能
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON mt_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON mt_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON mt_audit_log(action);

-- 为文档表添加回收站相关索引
CREATE INDEX IF NOT EXISTS idx_document_is_deleted ON mt_document(is_deleted);
CREATE INDEX IF NOT EXISTS idx_document_deleted_at ON mt_document(deleted_at);
