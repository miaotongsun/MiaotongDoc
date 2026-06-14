-- V8: 为所有表和字段添加中文注释
-- 便于数据库运维和团队协作

-- ============================================================
-- 1. sys_department 部门表
-- ============================================================
COMMENT ON TABLE sys_department IS '部门表（支持多层级树形架构）';
COMMENT ON COLUMN sys_department.id IS '主键ID';
COMMENT ON COLUMN sys_department.code IS '部门编码，全局唯一';
COMMENT ON COLUMN sys_department.name IS '部门名称';
COMMENT ON COLUMN sys_department.parent_id IS '上级部门ID，NULL表示顶级部门';
COMMENT ON COLUMN sys_department.level IS '部门层级，1=顶级';
COMMENT ON COLUMN sys_department.path IS '部门路径，如 /1/3/7/，用于快速查询子树';
COMMENT ON COLUMN sys_department.leader_user_id IS '部门负责人用户ID';
COMMENT ON COLUMN sys_department.sort_order IS '排序号，升序排列';
COMMENT ON COLUMN sys_department.is_active IS '是否启用：true=启用，false=停用';
COMMENT ON COLUMN sys_department.created_at IS '创建时间';
COMMENT ON COLUMN sys_department.updated_at IS '最后更新时间';

-- ============================================================
-- 2. sys_user 用户表
-- ============================================================
COMMENT ON TABLE sys_user IS '用户表（8位工号体系）';
COMMENT ON COLUMN sys_user.id IS '主键ID';
COMMENT ON COLUMN sys_user.employee_id IS '工号，8位字符，全局唯一';
COMMENT ON COLUMN sys_user.username IS '登录用户名，全局唯一';
COMMENT ON COLUMN sys_user.password IS '登录密码，BCrypt加密存储';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.email IS '邮箱地址';
COMMENT ON COLUMN sys_user.phone IS '手机号码';
COMMENT ON COLUMN sys_user.avatar_url IS '头像URL地址';
COMMENT ON COLUMN sys_user.department_id IS '所属部门ID，关联sys_department.id';
COMMENT ON COLUMN sys_user.position IS '职位/岗位';
COMMENT ON COLUMN sys_user.role IS '系统角色：admin=管理员，user=普通用户';
COMMENT ON COLUMN sys_user.is_active IS '是否启用：true=启用，false=停用';
COMMENT ON COLUMN sys_user.sso_only IS '是否仅SSO登录：true=只能SSO登录，false=可密码登录';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.last_login_ip IS '最后登录IP地址';
COMMENT ON COLUMN sys_user.password_changed_at IS '密码最后修改时间';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '最后更新时间';

-- ============================================================
-- 3. mt_document 文档主表
-- ============================================================
COMMENT ON TABLE mt_document IS '文档主表，记录所有文档的元数据';
COMMENT ON COLUMN mt_document.id IS '主键ID';
COMMENT ON COLUMN mt_document.doc_key IS '文档唯一标识（UUID格式），用于外部引用和存储路径';
COMMENT ON COLUMN mt_document.title IS '文档标题';
COMMENT ON COLUMN mt_document.doc_type IS '文档类型：word、excel、ppt';
COMMENT ON COLUMN mt_document.file_path IS '文件存储路径（相对路径），如 documents/2026/05/uuid/v1.docx';
COMMENT ON COLUMN mt_document.file_type IS '文件扩展名：docx、xlsx、pptx';
COMMENT ON COLUMN mt_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN mt_document.file_hash IS '文件内容哈希值（SHA-256），用于完整性校验';
COMMENT ON COLUMN mt_document.owner_user_id IS '文档所有者用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_document.department_id IS '所属部门ID，关联sys_department.id';
COMMENT ON COLUMN mt_document.status IS '文档状态：draft=草稿，signing=签署中，signed=已签署';
COMMENT ON COLUMN mt_document.current_version IS '当前版本号，从1开始递增';
COMMENT ON COLUMN mt_document.is_deleted IS '是否已删除（软删除）：true=已删除';
COMMENT ON COLUMN mt_document.deleted_at IS '删除时间';
COMMENT ON COLUMN mt_document.deleted_by IS '执行删除操作的用户ID';
COMMENT ON COLUMN mt_document.is_starred IS '是否星标收藏：true=已收藏';
COMMENT ON COLUMN mt_document.share_scope IS '共享范围：private=私有，department=部门公开，public=全员公开';
COMMENT ON COLUMN mt_document.signing_locked IS '签署锁定：true=签署完成后锁定，禁止编辑';
COMMENT ON COLUMN mt_document.created_at IS '创建时间';
COMMENT ON COLUMN mt_document.updated_at IS '最后更新时间';

-- ============================================================
-- 4. mt_document_share 文档共享权限表
-- ============================================================
COMMENT ON TABLE mt_document_share IS '文档共享权限表，记录文档的共享关系';
COMMENT ON COLUMN mt_document_share.id IS '主键ID';
COMMENT ON COLUMN mt_document_share.document_id IS '文档ID，关联mt_document.id，级联删除';
COMMENT ON COLUMN mt_document_share.user_id IS '被共享用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_document_share.permission IS '共享权限：view=只读，comment=可评论，edit=可编辑，admin=管理';
COMMENT ON COLUMN mt_document_share.shared_by IS '发起共享的用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_document_share.created_at IS '共享时间';

-- ============================================================
-- 5. mt_document_version 文档版本表
-- ============================================================
COMMENT ON TABLE mt_document_version IS '文档版本表，记录文档的历史版本';
COMMENT ON COLUMN mt_document_version.id IS '主键ID';
COMMENT ON COLUMN mt_document_version.document_id IS '文档ID，关联mt_document.id，级联删除';
COMMENT ON COLUMN mt_document_version.version_number IS '版本号，从1开始递增';
COMMENT ON COLUMN mt_document_version.file_path IS '该版本文件的存储路径（相对路径）';
COMMENT ON COLUMN mt_document_version.file_size IS '该版本文件大小（字节）';
COMMENT ON COLUMN mt_document_version.file_hash IS '该版本文件内容哈希值';
COMMENT ON COLUMN mt_document_version.change_summary IS '版本变更说明';
COMMENT ON COLUMN mt_document_version.created_by IS '创建该版本的用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_document_version.created_at IS '版本创建时间';

-- ============================================================
-- 6. mt_comment 评论批注表
-- ============================================================
COMMENT ON TABLE mt_comment IS '评论批注表，支持文档内评论和回复';
COMMENT ON COLUMN mt_comment.id IS '主键ID';
COMMENT ON COLUMN mt_comment.document_id IS '文档ID，关联mt_document.id，级联删除';
COMMENT ON COLUMN mt_comment.parent_id IS '父评论ID，NULL表示顶级评论，非NULL表示回复';
COMMENT ON COLUMN mt_comment.user_id IS '评论者用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_comment.content IS '评论内容';
COMMENT ON COLUMN mt_comment.quote_text IS '引用的文档原文（批注场景）';
COMMENT ON COLUMN mt_comment.page_number IS '评论所在页码';
COMMENT ON COLUMN mt_comment.position IS '评论在文档中的位置坐标（JSON格式）';
COMMENT ON COLUMN mt_comment.is_resolved IS '是否已解决：true=已解决';
COMMENT ON COLUMN mt_comment.resolved_by IS '标记解决的用户ID';
COMMENT ON COLUMN mt_comment.resolved_at IS '标记解决的时间';
COMMENT ON COLUMN mt_comment.is_deleted IS '是否已删除（软删除）：true=已删除';
COMMENT ON COLUMN mt_comment.created_at IS '评论时间';
COMMENT ON COLUMN mt_comment.updated_at IS '最后更新时间';

-- ============================================================
-- 7. mt_mention @提及关联表
-- ============================================================
COMMENT ON TABLE mt_mention IS '@提及关联表，记录评论中被@的用户';
COMMENT ON COLUMN mt_mention.id IS '主键ID';
COMMENT ON COLUMN mt_mention.comment_id IS '评论ID，关联mt_comment.id，级联删除';
COMMENT ON COLUMN mt_mention.mentioned_user_id IS '被@的用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_mention.created_at IS '创建时间';

-- ============================================================
-- 8. mt_activity 协作动态表
-- ============================================================
COMMENT ON TABLE mt_activity IS '协作动态表，记录文档的操作日志';
COMMENT ON COLUMN mt_activity.id IS '主键ID';
COMMENT ON COLUMN mt_activity.document_id IS '文档ID，关联mt_document.id，删除时置NULL';
COMMENT ON COLUMN mt_activity.document_title IS '文档标题快照（文档删除后仍可显示）';
COMMENT ON COLUMN mt_activity.user_id IS '操作者用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_activity.user_name IS '操作者姓名快照';
COMMENT ON COLUMN mt_activity.action IS '操作类型：COMMENT、SHARE、RENAME、UPLOAD等';
COMMENT ON COLUMN mt_activity.target_user_id IS '被操作的目标用户ID（如被共享者）';
COMMENT ON COLUMN mt_activity.detail IS '操作详情（JSON格式）';
COMMENT ON COLUMN mt_activity.created_at IS '操作时间';

-- ============================================================
-- 9. mt_notification 通知表
-- ============================================================
COMMENT ON TABLE mt_notification IS '通知表，存储用户通知消息';
COMMENT ON COLUMN mt_notification.id IS '主键ID';
COMMENT ON COLUMN mt_notification.user_id IS '接收者用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_notification.from_user_id IS '发送者用户ID，关联sys_user.id，系统通知为NULL';
COMMENT ON COLUMN mt_notification.document_id IS '关联文档ID，关联mt_document.id';
COMMENT ON COLUMN mt_notification.type IS '通知类型：SHARE、REVOKE、PERMISSION_CHANGE、SIGN_REQUEST、SIGN_CONFIRM、SIGN_REJECT、SIGN_CANCEL、SIGN_EXPIRED、COMMENT、MENTION、VERSION';
COMMENT ON COLUMN mt_notification.content IS '通知内容摘要';
COMMENT ON COLUMN mt_notification.is_read IS '是否已读：true=已读';
COMMENT ON COLUMN mt_notification.read_at IS '阅读时间';
COMMENT ON COLUMN mt_notification.created_at IS '创建时间';

-- ============================================================
-- 10. mt_signing_task 签署任务表
-- ============================================================
COMMENT ON TABLE mt_signing_task IS '签署任务表，管理文档签署流程';
COMMENT ON COLUMN mt_signing_task.id IS '主键ID';
COMMENT ON COLUMN mt_signing_task.document_id IS '文档ID，关联mt_document.id，级联删除';
COMMENT ON COLUMN mt_signing_task.title IS '签署任务标题';
COMMENT ON COLUMN mt_signing_task.description IS '签署任务说明';
COMMENT ON COLUMN mt_signing_task.created_by IS '发起人用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_signing_task.status IS '任务状态：in_progress=进行中，completed=已完成，cancelled=已取消，expired=已超期';
COMMENT ON COLUMN mt_signing_task.required_count IS '所需签署人数';
COMMENT ON COLUMN mt_signing_task.completed_count IS '已完成签署人数';
COMMENT ON COLUMN mt_signing_task.deadline IS '签署截止时间';
COMMENT ON COLUMN mt_signing_task.completed_at IS '全部签署完成时间';
COMMENT ON COLUMN mt_signing_task.cancelled_by IS '取消人用户ID';
COMMENT ON COLUMN mt_signing_task.cancel_reason IS '取消原因';
COMMENT ON COLUMN mt_signing_task.created_at IS '创建时间';
COMMENT ON COLUMN mt_signing_task.updated_at IS '最后更新时间';

-- ============================================================
-- 11. mt_signing_record 签署记录表
-- ============================================================
COMMENT ON TABLE mt_signing_record IS '签署记录表，记录每个签署人的签署状态';
COMMENT ON COLUMN mt_signing_record.id IS '主键ID';
COMMENT ON COLUMN mt_signing_record.task_id IS '签署任务ID，关联mt_signing_task.id，级联删除';
COMMENT ON COLUMN mt_signing_record.signer_user_id IS '签署人用户ID，关联sys_user.id';
COMMENT ON COLUMN mt_signing_record.sign_order IS '签署顺序号，从1开始';
COMMENT ON COLUMN mt_signing_record.status IS '签署状态：pending=待签署，confirmed=已确认，rejected=已拒绝，cancelled=已取消，expired=已超期';
COMMENT ON COLUMN mt_signing_record.confirmed_at IS '确认签署时间';
COMMENT ON COLUMN mt_signing_record.ip_address IS '签署时的IP地址';
COMMENT ON COLUMN mt_signing_record.user_agent IS '签署时的浏览器UA';
COMMENT ON COLUMN mt_signing_record.document_hash IS '签署时的文档哈希值（用于事后校验文档是否被篡改）';
COMMENT ON COLUMN mt_signing_record.remark IS '签署备注（拒绝原因等）';
COMMENT ON COLUMN mt_signing_record.created_at IS '创建时间';

-- ============================================================
-- 12. mt_audit_log 审计日志表
-- ============================================================
COMMENT ON TABLE mt_audit_log IS '审计日志表，记录所有关键操作（热数据）';
COMMENT ON COLUMN mt_audit_log.id IS '主键ID';
COMMENT ON COLUMN mt_audit_log.document_id IS '关联文档ID';
COMMENT ON COLUMN mt_audit_log.user_id IS '操作者用户ID';
COMMENT ON COLUMN mt_audit_log.employee_id IS '操作者工号';
COMMENT ON COLUMN mt_audit_log.user_name IS '操作者姓名';
COMMENT ON COLUMN mt_audit_log.action IS '操作类型：LOGIN、LOGOUT、CREATE、UPLOAD、DOWNLOAD、SHARE、DELETE、SIGN_INIT、SIGN_CONFIRM等';
COMMENT ON COLUMN mt_audit_log.resource_type IS '资源类型：DOCUMENT、USER、SIGNING等';
COMMENT ON COLUMN mt_audit_log.resource_id IS '资源ID';
COMMENT ON COLUMN mt_audit_log.detail IS '操作详情（JSON格式）';
COMMENT ON COLUMN mt_audit_log.ip_address IS '操作者IP地址';
COMMENT ON COLUMN mt_audit_log.user_agent IS '操作者浏览器UA';
COMMENT ON COLUMN mt_audit_log.created_at IS '操作时间';

-- ============================================================
-- 13. mt_token_blacklist JWT黑名单表
-- ============================================================
COMMENT ON TABLE mt_token_blacklist IS 'JWT令牌黑名单表，用于注销/强制下线';
COMMENT ON COLUMN mt_token_blacklist.id IS '主键ID';
COMMENT ON COLUMN mt_token_blacklist.token_jti IS 'JWT的唯一标识（jti声明）';
COMMENT ON COLUMN mt_token_blacklist.user_id IS '所属用户ID';
COMMENT ON COLUMN mt_token_blacklist.expires_at IS '令牌过期时间（过期后可清理记录）';
COMMENT ON COLUMN mt_token_blacklist.reason IS '加入黑名单原因：logout=主动登出，force=强制下线';
COMMENT ON COLUMN mt_token_blacklist.created_at IS '创建时间';

-- ============================================================
-- 14. mt_sso_identity SSO身份关联表
-- ============================================================
COMMENT ON TABLE mt_sso_identity IS 'SSO身份关联表，绑定外部SSO账号与本地用户';
COMMENT ON COLUMN mt_sso_identity.id IS '主键ID';
COMMENT ON COLUMN mt_sso_identity.user_id IS '本地用户ID，关联sys_user.id，级联删除';
COMMENT ON COLUMN mt_sso_identity.provider_id IS 'SSO提供商标识，如 keycloak、okta';
COMMENT ON COLUMN mt_sso_identity.external_id IS 'SSO外部用户唯一标识';
COMMENT ON COLUMN mt_sso_identity.external_email IS 'SSO外部邮箱';
COMMENT ON COLUMN mt_sso_identity.external_name IS 'SSO外部显示名称';
COMMENT ON COLUMN mt_sso_identity.raw_claims IS 'SSO原始Claims（JSON格式）';
COMMENT ON COLUMN mt_sso_identity.linked_at IS '关联时间';
COMMENT ON COLUMN mt_sso_identity.last_login_at IS '最后SSO登录时间';

-- ============================================================
-- 15. mt_audit_log_archive 审计日志归档表
-- ============================================================
COMMENT ON TABLE mt_audit_log_archive IS '审计日志归档表（温数据），超过90天的日志迁移到此表';
COMMENT ON COLUMN mt_audit_log_archive.id IS '主键ID';
COMMENT ON COLUMN mt_audit_log_archive.document_id IS '关联文档ID';
COMMENT ON COLUMN mt_audit_log_archive.user_id IS '操作者用户ID';
COMMENT ON COLUMN mt_audit_log_archive.employee_id IS '操作者工号';
COMMENT ON COLUMN mt_audit_log_archive.user_name IS '操作者姓名';
COMMENT ON COLUMN mt_audit_log_archive.action IS '操作类型';
COMMENT ON COLUMN mt_audit_log_archive.resource_type IS '资源类型';
COMMENT ON COLUMN mt_audit_log_archive.resource_id IS '资源ID';
COMMENT ON COLUMN mt_audit_log_archive.detail IS '操作详情';
COMMENT ON COLUMN mt_audit_log_archive.ip_address IS '操作者IP地址';
COMMENT ON COLUMN mt_audit_log_archive.user_agent IS '操作者浏览器UA';
COMMENT ON COLUMN mt_audit_log_archive.created_at IS '操作时间';

-- ============================================================
-- 16. mt_contract 合同表
-- ============================================================
COMMENT ON TABLE mt_contract IS '合同表，记录合同业务元数据';
COMMENT ON COLUMN mt_contract.id IS '主键ID';
COMMENT ON COLUMN mt_contract.document_id IS '关联文档ID，关联mt_document.id';
COMMENT ON COLUMN mt_contract.contract_no IS '合同编号';
COMMENT ON COLUMN mt_contract.contract_type IS '合同类型';
COMMENT ON COLUMN mt_contract.party_a IS '甲方名称';
COMMENT ON COLUMN mt_contract.party_b IS '乙方名称';
COMMENT ON COLUMN mt_contract.amount IS '合同金额';
COMMENT ON COLUMN mt_contract.currency IS '币种，默认CNY（人民币）';
COMMENT ON COLUMN mt_contract.signing_date IS '签订日期';
COMMENT ON COLUMN mt_contract.effective_date IS '生效日期';
COMMENT ON COLUMN mt_contract.expiry_date IS '到期日期';
COMMENT ON COLUMN mt_contract.status IS '合同状态：draft=草稿，approving=审批中，approved=已审批，rejected=已驳回，signed=已签署，expired=已过期';
COMMENT ON COLUMN mt_contract.owner_user_id IS '合同负责人用户ID';
COMMENT ON COLUMN mt_contract.department_id IS '所属部门ID';
COMMENT ON COLUMN mt_contract.remarks IS '备注说明';
COMMENT ON COLUMN mt_contract.current_step IS '当前审批步骤序号，从0开始';
COMMENT ON COLUMN mt_contract.reminder_sent IS '是否已发送催办提醒：true=已发送';
COMMENT ON COLUMN mt_contract.approved_hash IS '审批通过时的文档哈希值（用于完整性校验）';
COMMENT ON COLUMN mt_contract.approved_version IS '审批通过时的文档版本号';
COMMENT ON COLUMN mt_contract.created_at IS '创建时间';
COMMENT ON COLUMN mt_contract.updated_at IS '最后更新时间';

-- ============================================================
-- 17. mt_contract_approval 合同审批记录表
-- ============================================================
COMMENT ON TABLE mt_contract_approval IS '合同审批记录表，记录审批操作历史';
COMMENT ON COLUMN mt_contract_approval.id IS '主键ID';
COMMENT ON COLUMN mt_contract_approval.contract_id IS '合同ID，关联mt_contract.id';
COMMENT ON COLUMN mt_contract_approval.signing_task_id IS '关联签署任务ID，关联mt_signing_task.id';
COMMENT ON COLUMN mt_contract_approval.action IS '审批动作：submit=提交审批，approve=通过，reject=驳回，cancel=撤回';
COMMENT ON COLUMN mt_contract_approval.operator_id IS '操作者用户ID';
COMMENT ON COLUMN mt_contract_approval.operator_name IS '操作者姓名快照';
COMMENT ON COLUMN mt_contract_approval.remark IS '审批意见';
COMMENT ON COLUMN mt_contract_approval.created_at IS '操作时间';

-- ============================================================
-- 18. mt_contract_approval_node 合同审批节点表
-- ============================================================
COMMENT ON TABLE mt_contract_approval_node IS '合同审批节点表，定义审批流程的每个步骤';
COMMENT ON COLUMN mt_contract_approval_node.id IS '主键ID';
COMMENT ON COLUMN mt_contract_approval_node.contract_id IS '合同ID，关联mt_contract.id';
COMMENT ON COLUMN mt_contract_approval_node.step_order IS '审批步骤序号，从1开始';
COMMENT ON COLUMN mt_contract_approval_node.approver_id IS '审批人用户ID';
COMMENT ON COLUMN mt_contract_approval_node.approver_name IS '审批人姓名快照';
COMMENT ON COLUMN mt_contract_approval_node.status IS '节点状态：waiting=待审批，approved=已通过，rejected=已驳回，skipped=已跳过';
COMMENT ON COLUMN mt_contract_approval_node.remark IS '审批意见';
COMMENT ON COLUMN mt_contract_approval_node.acted_at IS '审批操作时间';
COMMENT ON COLUMN mt_contract_approval_node.created_at IS '创建时间';
