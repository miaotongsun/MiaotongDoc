-- 模板分类表
CREATE TABLE mt_template_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 插入预设分类
INSERT INTO mt_template_category (name, sort_order) VALUES
('合同', 1),
('报告', 2),
('项目管理', 3),
('简历', 4),
('通知', 5),
('会议纪要', 6),
('其他', 99);
