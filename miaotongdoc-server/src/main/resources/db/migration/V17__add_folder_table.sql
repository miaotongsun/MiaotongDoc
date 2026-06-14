-- 文档文件夹表
CREATE TABLE mt_folder (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES mt_folder(id) ON DELETE CASCADE,
    owner_user_id BIGINT NOT NULL,
    department_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 文档表新增文件夹字段
ALTER TABLE mt_document ADD COLUMN folder_id BIGINT REFERENCES mt_folder(id) ON DELETE SET NULL;

-- 索引
CREATE INDEX idx_folder_parent ON mt_folder(parent_id);
CREATE INDEX idx_folder_owner ON mt_folder(owner_user_id);
CREATE INDEX idx_document_folder ON mt_document(folder_id);
