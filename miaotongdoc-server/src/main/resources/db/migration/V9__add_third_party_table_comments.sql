-- V9: 为第三方组件内部表添加中文注释
-- 便于数据库运维识别这些表的来源和用途

-- ============================================================
-- 1. flyway_schema_history Flyway迁移历史表
-- 由 Flyway 框架自动创建和管理，记录所有数据库迁移的执行情况
-- 不要手动修改此表，否则会导致迁移校验失败
-- ============================================================
COMMENT ON TABLE flyway_schema_history IS 'Flyway数据库迁移历史表（框架自动管理，勿手动修改）';
COMMENT ON COLUMN flyway_schema_history.installed_rank IS '执行顺序排名，从1开始递增';
COMMENT ON COLUMN flyway_schema_history.version IS '迁移版本号，如 V1、V2，基线迁移为NULL';
COMMENT ON COLUMN flyway_schema_history.description IS '迁移描述，取自文件名';
COMMENT ON COLUMN flyway_schema_history.type IS '迁移类型：SQL=SQL脚本，JDBC=Java代码';
COMMENT ON COLUMN flyway_schema_history.script IS '迁移脚本文件名，如 V1__init_schema.sql';
COMMENT ON COLUMN flyway_schema_history.checksum IS '脚本内容的CRC32校验和，用于检测脚本是否被篡改';
COMMENT ON COLUMN flyway_schema_history.installed_by IS '执行迁移的数据库用户';
COMMENT ON COLUMN flyway_schema_history.installed_on IS '迁移执行时间';
COMMENT ON COLUMN flyway_schema_history.execution_time IS '迁移执行耗时（毫秒）';
COMMENT ON COLUMN flyway_schema_history.success IS '是否执行成功：true=成功，false=失败（失败时启动会阻断）';

-- ============================================================
-- 2. task_result OnlyOffice编辑会话表
-- 由 OnlyOffice Document Server 自动创建，跟踪每个文档的编辑会话
-- 存储会话状态、回调地址、用户索引等信息
-- ============================================================
COMMENT ON TABLE task_result IS 'OnlyOffice编辑会话表（Document Server自动管理）';
COMMENT ON COLUMN task_result.tenant IS '租户标识，多租户场景下的隔离字段';
COMMENT ON COLUMN task_result.id IS '文档标识，对应OnlyOffice的document key';
COMMENT ON COLUMN task_result.status IS '文档状态：1=编辑中，2=已保存，3=已关闭，4=强制保存，6=转换中，7=转换失败';
COMMENT ON COLUMN task_result.status_info IS '状态附加信息，如错误码';
COMMENT ON COLUMN task_result.created_at IS '会话创建时间';
COMMENT ON COLUMN task_result.last_open_date IS '最后打开时间，用于超时清理';
COMMENT ON COLUMN task_result.user_index IS '当前用户索引，用于分配不同用户颜色';
COMMENT ON COLUMN task_result.change_id IS '当前变更ID，标识文档修改的版本点';
COMMENT ON COLUMN task_result.callback IS '回调URL，OnlyOffice通过此地址通知服务端文档状态变化';
COMMENT ON COLUMN task_result.baseurl IS '文档基础URL，用于生成文档访问地址';
COMMENT ON COLUMN task_result.password IS '文档密码（加密存储）';
COMMENT ON COLUMN task_result.additional IS '附加参数（JSON格式），存储扩展信息';

-- ============================================================
-- 3. doc_changes OnlyOffice文档变更表
-- 由 OnlyOffice Document Server 自动创建，存储协同编辑的增量变更数据
-- 每次用户操作（输入、删除、格式化等）生成一条变更记录
-- ============================================================
COMMENT ON TABLE doc_changes IS 'OnlyOffice文档变更表（Document Server自动管理，存储协同编辑增量数据）';
COMMENT ON COLUMN doc_changes.tenant IS '租户标识，多租户场景下的隔离字段';
COMMENT ON COLUMN doc_changes.id IS '文档标识，对应OnlyOffice的document key';
COMMENT ON COLUMN doc_changes.change_id IS '变更序号，从1开始递增，标识每次用户操作';
COMMENT ON COLUMN doc_changes.user_id IS '操作者用户ID（OnlyOffice内部生成的唯一标识）';
COMMENT ON COLUMN doc_changes.user_id_original IS '操作者原始用户ID（外部系统传入的用户标识）';
COMMENT ON COLUMN doc_changes.user_name IS '操作者显示名称';
COMMENT ON COLUMN doc_changes.change_data IS '变更数据（JSON格式），包含具体的编辑操作指令';
COMMENT ON COLUMN doc_changes.change_date IS '变更时间';
