-- 为文件夹和文件夹模板添加排序字段
ALTER TABLE mt_folder ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;
ALTER TABLE mt_folder_template ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;

-- 初始化排序值为 id 顺序
UPDATE mt_folder SET sort_order = id WHERE sort_order = 0;
UPDATE mt_folder_template SET sort_order = id WHERE sort_order = 0;
