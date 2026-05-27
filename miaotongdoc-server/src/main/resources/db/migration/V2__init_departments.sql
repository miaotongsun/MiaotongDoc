-- 初始化部门数据

-- 总行
INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01000000', '总行', NULL, 1, '/1', 1);

-- 一级分行
INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01010000', '北京分行', 1, 2, '/1/2', 1);

INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01020000', '上海分行', 1, 2, '/1/3', 2);

INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01030000', '广州分行', 1, 2, '/1/4', 3);

-- 二级分行
INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01010100', '海淀支行', 2, 3, '/1/2/5', 1);

INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01010200', '朝阳支行', 2, 3, '/1/2/6', 2);

-- 部门
INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01000001', '信息技术部', 1, 3, '/1/7', 1);

INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01000002', '风险管理部', 1, 3, '/1/8', 2);

INSERT INTO sys_department (code, name, parent_id, level, path, sort_order)
VALUES ('01000003', '运营管理部', 1, 3, '/1/9', 3);
