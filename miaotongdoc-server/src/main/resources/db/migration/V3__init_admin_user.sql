-- 初始化管理员账户
-- 密码: Admin@123 (BCrypt 加密)
INSERT INTO sys_user (employee_id, username, password, real_name, email, role, is_active, password_changed_at)
VALUES (
    '10000001',
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
    '系统管理员',
    'admin@miaotong.com',
    'admin',
    true,
    CURRENT_TIMESTAMP
);
