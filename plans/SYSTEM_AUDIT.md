# MiaotongDoc 系统全面审查报告

> 审查日期：2026-05-28 | 审查范围：后端、前端、基础设施、数据库

---

## 一、已开发功能清单

### 1.1 文档管理

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 创建文档（Word/Excel/PPT） | ✅ | DocumentService.createDocument | CreateDocDialog |
| 上传文档 | ✅ | DocumentService.uploadDocument | Home.vue upload |
| 文档列表（卡片/列表视图） | ✅ | DocumentController.list | Home.vue |
| 文档搜索 | ✅ | DocumentRepository.searchByKeyword | Home.vue |
| 文档重命名 | ✅ | DocumentService.renameDocument | Home.vue |
| 文档删除（软删除） | ✅ | DocumentService.softDelete | Home.vue |
| 文档恢复 | ✅ | DocumentService.restore | Home.vue |
| 收藏/取消收藏 | ✅ | DocumentService.toggleStar | Home.vue |
| 文档锁定标识 | ✅ | signingLocked 字段 | 锁图标展示 |
| 部门树筛选 | ✅ | DocumentRepository 部门树查询 | Home.vue |
| 文件下载（含版本号） | ✅ | DocumentController.getFile | Home.vue |

### 1.2 在线编辑（OnlyOffice 集成）

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 文档在线编辑 | ✅ | DocumentController.getEditorConfig | DocEditor.vue |
| 协作编辑（快速模式） | ✅ | coEditing config | DocumentEditor.vue |
| 自动保存（forcesave） | ✅ | CallbackService.saveDocument | 编辑器内置 |
| 编辑器品牌定制 | ✅ | Dockerfile 替换脚本 | 自定义 logo |
| 文档锁定后只读 | ✅ | signingLocked 检查 | 编辑器只读 |
| 编辑器 JWT 认证 | ✅ | EditorJwtUtil | token 传递 |
| 多编辑器实例负载均衡 | ✅ | 3 editor + nginx hash | nginx upstream |

### 1.3 版本管理

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 版本历史查看 | ✅ | VersionController.getVersionHistory | VersionHistory.vue |
| 手动保存版本（含说明） | ✅ | DocumentService.createVersion | DocEditor.vue |
| 版本下载（含版本号） | ✅ | VersionController.downloadVersion | VersionHistory.vue |
| 版本恢复 | ✅ | DocumentService.restoreVersion | VersionHistory.vue |
| 版本创建人显示 | ✅ | UserRepository 查询 | VersionHistory.vue |

### 1.4 共享与权限

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 用户级共享 | ✅ | ShareService | ShareDialog.vue |
| 部门级共享 | ✅ | ShareService | ShareDialog.vue |
| 权限等级（view/comment/edit/admin） | ✅ | ShareService.getUserPermission | 权限控制 |
| 共享列表查看 | ✅ | DocumentRepository.findSharedWithUser | Home.vue |

### 1.5 评论与协作

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 文档评论 | ✅ | CommentService | CommentPanel.vue |
| @提及用户 | ✅ | MentionService | CommentPanel.vue |
| 评论解决/未解决 | ✅ | CommentService.resolve | CommentPanel.vue |
| 评论面板宽度拖拽 | ✅ | - | CommentPanel.vue |
| 在线状态（谁在编辑） | ✅ | PresenceService | CollaborationBar.vue |
| 实时 WebSocket 推送 | ✅ | PresenceWebSocketHandler | websocket.ts |

### 1.6 通知系统

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 通知创建（分享/签署/评论/提及/版本） | ✅ | NotificationService | - |
| 通知铃铛（未读计数） | ✅ | NotificationController | NotificationBell.vue |
| 通知已读标记 | ✅ | NotificationService.markRead | NotificationBell.vue |
| WebSocket 实时推送 | ✅ | NotificationWebSocketHandler | notification store |
| 富文本通知内容 | ✅ | - | 带颜色用户名/文档名 |

### 1.7 签署管理

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 发起签署任务 | ✅ | SigningService.createTask | SigningDialog.vue |
| 签署确认 | ✅ | SigningService.sign | SigningBar.vue |
| 签署拒绝 | ✅ | SigningService.reject | SigningBar.vue |
| 签署取消 | ✅ | SigningService.cancel | SigningBar.vue |
| 签署任务列表 | ✅ | SigningController | SigningTask.vue |
| 文档锁定（签署后） | ✅ | signingLocked 字段 | 锁图标 |

### 1.8 合同管理

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 合同创建（手动/从文档解析） | ✅ | ContractService | ContractCreateDialog.vue |
| 合同列表（统计卡片） | ✅ | ContractController | ContractList.vue |
| 合同详情 | ✅ | ContractController | ContractDetail.vue |
| 合同编辑 | ✅ | ContractService.update | ContractDetail.vue |
| 合同删除 | ✅ | ContractService.delete | ContractList.vue |
| 顺序审批流程 | ✅ | ContractService.submitForApproval | ContractSubmitDialog.vue |
| 审批通过/拒绝 | ✅ | ContractService.approve/reject | ContractDetail.vue |
| 审批撤回 | ✅ | ContractService.cancel | ContractDetail.vue |
| 审批链可视化 | ✅ | - | ContractDetail.vue |
| 文档完整性校验 | ✅ | ContractService.checkIntegrity | ContractDetail.vue |
| 合同过期定时任务 | ✅ | ContractExpirationScheduler | - |

### 1.9 用户与部门管理

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 用户登录/注册 | ✅ | AuthController | Login.vue |
| 用户管理（增删改查） | ✅ | AdminController | Admin.vue |
| 部门管理 | ✅ | DepartmentController | Admin.vue |
| 角色管理（admin/user） | ✅ | AdminController | Admin.vue |
| 密码重置 | ✅ | AdminController | Admin.vue |
| 审计日志查看 | ✅ | AuditController | Admin.vue |
| SSO 单点登录 | ✅ | SsoController/SsoService | Login.vue |

### 1.10 系统运维

| 功能 | 状态 | 后端 | 前端 |
|------|------|------|------|
| 审计日志记录 | ✅ | AuditService | - |
| 活动流记录 | ✅ | ActivityService | ActivityFeed.vue |
| PDF 导出 | ✅ | PdfExportService | DocEditor.vue |
| 编辑器缓存定时清理 | ✅ | cache-cleaner 容器 | - |
| Redis 黑名单 | ✅ | TokenBlacklistService | - |
| 主题切换 | ✅ | - | ThemeSwitch.vue |

---

## 二、问题清单（按严重程度）

### 🔴 严重（必须修复）

| # | 模块 | 问题 | 文件 | 影响 |
|---|------|------|------|------|
| 1 | 安全 | `.env` 文件含真实密码被 git 跟踪 | MiaotongDoc-Docker/.env | 密码泄露 |
| 2 | 安全 | `local.json` 含硬编码密码被 git 跟踪 | Editor/config/local.json | 密码泄露 |
| 3 | 安全 | Admin 路由守卫未生效，任何登录用户可访问 /admin | router/index.ts:54-64 | 权限绕过 |
| 4 | 安全 | `/api/documents/*/file` 允许未认证访问 | SecurityConfig.java:37 | 任何人可下载文档 |
| 5 | 安全 | CORS 允许所有来源 + credentials | CorsConfig.java:17 | CSRF 攻击 |
| 6 | 安全 | AdminController 直接返回 User 实体（含密码哈希） | AdminController.java:36 | 数据泄露 |
| 7 | 安全 | JWT 默认密钥可被猜测 | application.yml:72 | Token 伪造 |
| 8 | 安全 | 无 HTTPS/TLS | nginx.conf | 中间人攻击 |
| 9 | 安全 | WebSocket token 通过 URL 传递 | websocket.ts:15 | Token 泄露 |
| 10 | 数据 | `ddl-auto: update` 与 Flyway 冲突 | application.yml:26 | Schema 漂移 |

### 🟠 高优先级（应尽快修复）

| # | 模块 | 问题 | 文件 |
|---|------|------|------|
| 11 | 性能 | 大量 N+1 查询（DTO 转换逐条查关联） | DocumentController/CommentService/ContractController 等 |
| 12 | 安全 | 10+ 接口缺少权限校验 | Activity/Audit/Comment/Contract/Presence Controller |
| 13 | 安全 | `SECURE_LINK_SECRET` 硬编码在 docker-compose | docker-compose.yml:150 |
| 14 | 安全 | `RABBITMQ_PASSWORD` 无默认值，回退到弱密码 | docker-compose.yml:64 |
| 15 | 安全 | entrypoint.sh envsubst 白名单缺少 RABBITMQ_PASSWORD | entrypoint.sh:11 |
| 16 | 安全 | Nginx 缺少安全头（X-Frame-Options 等） | nginx.conf |
| 17 | 安全 | Nginx 无速率限制 | nginx.conf |
| 18 | Bug | 签署任务详情页路由不存在 | SigningTask.vue:105 |
| 19 | Bug | DocEditor 加载失败时永久显示 "Loading..." | DocEditor.vue:110-123 |
| 20 | Bug | DocCard 直接修改 prop | DocCard.vue:118 |
| 21 | Bug | PresenceStore 心跳定时器泄漏 | presence.ts:51 |
| 22 | 安全 | AdminController 用 raw Map 接收参数无校验 | AdminController:71-104 |
| 23 | 安全 | 合同相关接口全部用 raw Map 无校验 | ContractController 全部 |
| 24 | 数据 | V2 迁移硬编码 BIGSERIAL ID | V2__init_departments.sql |
| 25 | 数据 | 多个状态字段缺少 CHECK 约束 | signing/contract/document 表 |
| 26 | 数据 | 缺少关键索引 | mt_signing_task(document_id,status) 等 |

### 🟡 中优先级（建议修复）

| # | 模块 | 问题 |
|---|------|------|
| 27 | 代码 | 前端部门树构建逻辑重复 5 处 |
| 28 | 代码 | 前端用户选择器组件重复（ShareDialog/SigningDialog） |
| 29 | 代码 | 后端权限检查逻辑重复（DocumentController/VersionController） |
| 30 | 代码 | 后端 DTO 映射重复 3+ 处 |
| 31 | 代码 | 前端 60+ 处 `any` 类型 |
| 32 | 代码 | 后端零 Javadoc 注释 |
| 33 | 代码 | 前端零 JSDoc 注释 |
| 34 | 代码 | MentionInput.vue 未使用（死代码） |
| 35 | 代码 | types/ 目录为空 |
| 36 | 代码 | 多处 catch 块静默吞错 |
| 37 | 运维 | PostgreSQL 12 已 EOL |
| 38 | 运维 | nginx:latest 未固定版本 |
| 39 | 运维 | 无 gzip 压缩 |
| 40 | 运维 | 无访问日志 |
| 41 | 功能 | 无 404 页面 |
| 42 | 功能 | 无 API 文档（Swagger） |
| 43 | 功能 | 签署插件图标文件缺失 |

---

## 三、修复计划

### 第一阶段：安全加固（1-2 天）

**P0 - 立即修复**

```
1. .gitignore 添加 .env、local.json 等敏感文件
2. 从 git 历史中清除已提交的密码（git filter-branch 或 BFG）
3. 修复 Admin 路由守卫（router/index.ts 添加 requiresAdmin 检查）
4. /api/documents/*/file 改为需要认证或使用签名 URL
5. CORS 限制为配置的域名
6. AdminController 返回 DTO 而非 User 实体
7. JWT 默认密钥改为空，启动时强制要求配置
8. application.yml ddl-auto 改为 validate
9. WebSocket token 改为通过 Sec-WebSocket-Protocol 传递
10. entrypoint.sh 添加 ${RABBITMQ_PASSWORD} 到 envsubst 白名单
```

**P1 - 本周修复**

```
11. Nginx 添加安全头
12. Nginx 添加速率限制
13. SECURE_LINK_SECRET 外部化为环境变量
14. 所有缺失权限校验的接口补充校验
15. ContractController/AdminController 添加 DTO 和参数校验
```

### 第二阶段：质量提升（3-5 天）

**代码重构**

```
1. 抽取部门树构建工具函数（消除 5 处重复）
2. 抽取 UserPicker 公共组件（消除 ShareDialog/SigningDialog 重复）
3. 抽取 PermissionHelper 工具类（消除权限检查重复）
4. 抽取 EntityMapper/DTO 转换层（消除 N+1 和映射重复）
5. 修复 DocCard prop mutation
6. 修复 PresenceStore 心跳泄漏
7. 补充关键接口的 Javadoc/JSDoc
8. 清理死代码（MentionInput.vue、types/ 目录）
9. 替换 any 类型为具体类型
```

**数据库优化**

```
1. 添加缺失索引：
   - mt_signing_task(document_id, status, created_by)
   - mt_contract(expiry_date, signing_date)
   - mt_contract_approval_node(contract_id, step_order)
   - mt_document(owner_user_id, status)
2. 添加 CHECK 约束（状态字段）
3. 修复 V2 迁移硬编码 ID 问题
4. 评估 PostgreSQL 升级到 16
```

### 第三阶段：功能完善（5-10 天）

**已有功能补全**

```
1. 添加 404 页面
2. DocEditor 加载失败时显示错误 UI
3. 所有 catch 块添加用户可见的错误提示
4. ActivityFeed/Admin 添加 loading 状态
5. Admin 操作添加确认对话框（角色变更、账户禁用）
6. PDF 导出添加进度指示
7. 补全签署插件图标文件
```

---

## 四、下一步系统规划

### 4.1 近期（1-2 周）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| HTTPS 支持 | Nginx TLS 终端 + 证书管理（Let's Encrypt） | 高 |
| API 文档 | 集成 SpringDoc/Swagger，自动生成 OpenAPI 文档 | 高 |
| 文件签名 URL | 文档下载使用带过期时间的签名 URL，替代 permitAll | 高 |
| 操作审计增强 | 审计日志支持导出、按时间范围查询、按操作类型筛选 | 中 |
| 通知增强 | 支持批量已读、通知设置（免打扰） | 中 |
| 移动端适配 | Home.vue 响应式布局，编辑器移动端查看 | 中 |

### 4.2 中期（2-4 周）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 文档水印 | 下载/打印/预览时自动添加用户信息水印 | 高 |
| 文档有效期 | 支持设置文档过期时间，过期自动归档 | 中 |
| 批量操作 | 批量下载、批量删除、批量共享 | 中 |
| 文档模板 | 预置合同/公文/报告模板，一键创建 | 中 |
| 回收站 | 独立的回收站页面，支持永久删除和批量恢复 | 中 |
| 操作日志可视化 | 时间线视图展示文档完整操作历史 | 低 |
| 文档标签 | 自定义标签分类，支持按标签筛选 | 低 |

### 4.3 远期（1-2 月）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 权限精细化 | 字段级权限、行级数据权限、临时权限 | 高 |
| 工作流引擎 | 通用审批流（自定义节点、条件分支、会签） | 高 |
| 文档对比 | 两个版本的差异对比（文本级） | 中 |
| OCR 识别 | 扫描件/PDF 图片内容识别和搜索 | 中 |
| 全文搜索 | Elasticsearch 集成，支持文档内容全文搜索 | 中 |
| 多租户 | 组织隔离、独立数据空间 | 低 |
| 开放 API | 第三方系统集成接口（OAuth2） | 低 |
| 移动端 App | React Native / Flutter 原生应用 | 低 |

### 4.4 运维规划

| 项目 | 描述 | 优先级 |
|------|------|--------|
| 监控告警 | Prometheus + Grafana 监控系统指标 | 高 |
| 日志收集 | ELK/Loki 集中化日志 | 高 |
| 自动化部署 | CI/CD 流水线（GitHub Actions / GitLab CI） | 高 |
| 备份策略 | 数据库定时备份 + 文档文件备份 + 恢复演练 | 高 |
| 灾难恢复 | 主从复制、异地灾备方案 | 中 |
| 性能压测 | JMeter/k6 压力测试，确定系统瓶颈 | 中 |
| 安全扫描 | 定期依赖漏洞扫描（Dependabot/Snyk） | 中 |
