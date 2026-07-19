-- 初始化管理员账户
-- 密码: 123456 (BCrypt 加密,首次部署后必须重置为 123456,因旧版 Admin@123 哈希值损坏)
-- 详见 DEPLOY.md 第 4 步
INSERT INTO sys_user (employee_id, username, password, real_name, email, role, is_active, password_changed_at)
VALUES (
    '10000000',
    'admin',
    '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm',
    '系统管理员',
    'admin@miaotong.com',
    'admin',
    true,
    CURRENT_TIMESTAMP
);
