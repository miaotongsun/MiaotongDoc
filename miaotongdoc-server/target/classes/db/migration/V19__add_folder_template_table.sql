-- 项目空间模板表
CREATE TABLE mt_folder_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    structure JSONB NOT NULL,  -- 文件夹结构 JSON
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 预置模板
INSERT INTO mt_folder_template (name, description, structure) VALUES
('项目管理', '适用于一般项目管理', '[
  {"name": "立项文档", "children": []},
  {"name": "需求文档", "children": []},
  {"name": "设计文档", "children": []},
  {"name": "会议纪要", "children": []},
  {"name": "验收报告", "children": []},
  {"name": "其他", "children": []}
]'),
('产品研发', '适用于产品研发项目', '[
  {"name": "产品需求", "children": ["PRD", "用户故事"]},
  {"name": "技术设计", "children": ["架构设计", "接口文档"]},
  {"name": "测试文档", "children": ["测试用例", "测试报告"]},
  {"name": "发布文档", "children": ["发布说明", "部署文档"]}
]'),
('合同管理', '适用于合同文档管理', '[
  {"name": "合同模板", "children": []},
  {"name": "待签署", "children": []},
  {"name": "已签署", "children": []},
  {"name": "已过期", "children": []}
]');
