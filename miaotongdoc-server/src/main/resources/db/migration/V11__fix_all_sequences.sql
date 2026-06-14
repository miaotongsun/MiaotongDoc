-- 修复所有表的自增序列，确保序列值 >= 表中最大 ID
-- 解决 Flyway 迁移脚本直接 INSERT 指定 ID 导致序列不同步的问题

SELECT setval('sys_user_id_seq', COALESCE((SELECT MAX(id) FROM sys_user), 1));
SELECT setval('sys_department_id_seq', COALESCE((SELECT MAX(id) FROM sys_department), 1));
SELECT setval('mt_document_id_seq', COALESCE((SELECT MAX(id) FROM mt_document), 1));
SELECT setval('mt_document_version_id_seq', COALESCE((SELECT MAX(id) FROM mt_document_version), 1));
SELECT setval('mt_document_share_id_seq', COALESCE((SELECT MAX(id) FROM mt_document_share), 1));
SELECT setval('mt_signing_task_id_seq', COALESCE((SELECT MAX(id) FROM mt_signing_task), 1));
SELECT setval('mt_signing_record_id_seq', COALESCE((SELECT MAX(id) FROM mt_signing_record), 1));
SELECT setval('mt_contract_id_seq', COALESCE((SELECT MAX(id) FROM mt_contract), 1));
SELECT setval('mt_contract_approval_id_seq', COALESCE((SELECT MAX(id) FROM mt_contract_approval), 1));
SELECT setval('mt_contract_approval_node_id_seq', COALESCE((SELECT MAX(id) FROM mt_contract_approval_node), 1));
SELECT setval('mt_comment_id_seq', COALESCE((SELECT MAX(id) FROM mt_comment), 1));
SELECT setval('mt_mention_id_seq', COALESCE((SELECT MAX(id) FROM mt_mention), 1));
SELECT setval('mt_notification_id_seq', COALESCE((SELECT MAX(id) FROM mt_notification), 1));
SELECT setval('mt_activity_id_seq', COALESCE((SELECT MAX(id) FROM mt_activity), 1));
SELECT setval('mt_audit_log_id_seq', COALESCE((SELECT MAX(id) FROM mt_audit_log), 1));
SELECT setval('mt_audit_log_archive_id_seq', COALESCE((SELECT MAX(id) FROM mt_audit_log_archive), 1));
SELECT setval('mt_token_blacklist_id_seq', COALESCE((SELECT MAX(id) FROM mt_token_blacklist), 1));
SELECT setval('mt_sso_identity_id_seq', COALESCE((SELECT MAX(id) FROM mt_sso_identity), 1));
