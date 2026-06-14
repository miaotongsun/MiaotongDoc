-- 添加 updated_by 字段到 mt_document 表，记录最后更新人
ALTER TABLE mt_document ADD COLUMN updated_by BIGINT;
