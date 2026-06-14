-- V7: 将 file_path 从绝对路径转为相对路径（objectKey 格式）
-- 绝对路径: /data/documents/2026/05/uuid/v1.docx
-- 相对路径: documents/2026/05/uuid/v1.docx

UPDATE mt_document
SET file_path = 'documents/' || SUBSTRING(file_path FROM '/data/documents/(.*)')
WHERE file_path LIKE '/data/documents/%';

UPDATE mt_document_version
SET file_path = 'documents/' || SUBSTRING(file_path FROM '/data/documents/(.*)')
WHERE file_path LIKE '/data/documents/%';
