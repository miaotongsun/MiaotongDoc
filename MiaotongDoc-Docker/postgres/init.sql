-- MiaotongDoc 数据库初始化脚本
-- 数据库 miaotongdocdb 由 POSTGRES_DB 环境变量自动创建
-- 注意：CREATE EXTENSION 需要超级用户权限，此处仅做安全检查

-- 使用 DO 块捕获权限错误，避免阻塞后续初始化
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping uuid-ossp extension (requires superuser)';
    END;
    BEGIN
        CREATE EXTENSION IF NOT EXISTS "pg_trgm";
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping pg_trgm extension (requires superuser)';
    END;
END $$;
