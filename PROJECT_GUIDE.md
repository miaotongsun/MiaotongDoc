# MiaotongDoc 项目说明书

> **版本**: v1.0.0 | **最后更新**: 2024年
> **定位**: 企业级多格式在线文档协作与签署平台
> **目标**: 让企业文档"管得住、找得到、用得好"

---

## 一、项目概述

**MiaotongDoc（妙同文档）** 是一个面向企业（特别是金融行业）的在线文档协同编辑系统，提供文档在线编辑、多人实时协作、合同管理、签署审批等功能。

### 核心特性

- **多格式文档支持**: Word/Excel/PPT（OnlyOffice）、Markdown（TipTap + Yjs）、PDF
- **实时多人协作**: 基于 CRDT 的协同编辑，在线状态感知
- **企业级权限管理**: 用户/部门/文档级权限控制
- **合同全生命周期**: 创建、审批、签署、归档
- **AI 能力集成**: 文档问答、摘要、翻译、改写
- **安全合规**: JWT 认证、审计日志、水印

---

## 二、技术栈

### 前端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.4+ | SPA 框架（Composition API） |
| Element Plus | 2.5+ | UI 组件库 |
| Vite 5 | 5.x | 构建工具 |
| TypeScript | 5.x | 类型系统 |
| Pinia | 2.1+ | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| Axios | 1.6+ | HTTP 客户端 |
| Tiptap | 3.26+ | Markdown 编辑器（ProseMirror） |
| Yjs | 13.6+ | CRDT 实时协同 |
| OnlyOffice | 9.3 | Office 文档编辑 |
| pdfjs-dist | 4.8+ | PDF 渲染 |

### 后端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | 后端框架 |
| Java | 17 (LTS) | 运行环境 |
| Spring Security | - | 安全框架 |
| Spring Data JPA | - | ORM |
| PostgreSQL | 12 | 关系型数据库 |
| Flyway | - | 数据库迁移 |
| Redis | 7 | 缓存、会话 |
| Elasticsearch | 8.11 | 全文搜索 |
| RabbitMQ | 3 | 消息队列 |
| MinIO | - | 对象存储 |
| Apache POI | 5.2.5 | Office 文档处理 |
| PDFBox | 3.0.3 | PDF 处理 |
| JWT (jjwt) | 0.12.5 | 认证令牌 |

### 基础设施

| 技术 | 用途 |
|------|------|
| Docker + Docker Compose | 容器化部署 |
| Nginx | 反向代理、负载均衡 |
| Prometheus + Micrometer | 监控指标 |

---

## 三、项目结构

```
MiaotongDoc/
├── miaotongdoc-web/                # Vue 3 前端应用
│   ├── src/
│   │   ├── api/                    # API 层（19个模块）
│   │   ├── components/             # 通用组件（16个）
│   │   ├── router/                 # 路由配置（7条路由）
│   │   ├── stores/                 # Pinia 状态管理（4个）
│   │   ├── utils/                  # 工具函数（5个）
│   │   └── views/                  # 页面视图（8个）
│   ├── package.json
│   └── vite.config.ts
│
├── miaotongdoc-server/             # Spring Boot 后端
│   ├── src/main/java/com/miaotong/doc/
│   │   ├── config/                 # 配置类（12个）
│   │   ├── controller/             # REST 控制器（22个）
│   │   ├── dto/                    # 数据传输对象（18个）
│   │   ├── entity/                 # JPA 实体（18个）
│   │   ├── exception/              # 异常处理（3个）
│   │   ├── repository/             # 数据仓库（18个）
│   │   ├── scheduler/              # 定时任务（2个）
│   │   ├── service/                # 业务服务（19个）
│   │   ├── util/                   # 工具类（5个）
│   │   └── websocket/              # WebSocket 处理器（3个）
│   ├── pom.xml
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/           # Flyway 迁移脚本（V1-V21）
│
├── MiaotongDoc-Docker/             # Docker 部署编排
│   ├── docker-compose.yml
│   ├── deploy.sh
│   ├── app/
│   │   ├── editor/                 # OnlyOffice 定制镜像
│   │   ├── web/dist/               # 前端构建产物
│   │   ├── server/                 # 后端 JAR
│   │   └── yjs/                    # Yjs 协同服务器
│   ├── config/
│   │   ├── nginx/nginx.conf
│   │   ├── postgres/init.sql
│   │   └── redis/redis.conf
│   └── scripts/                    # 运维脚本
│
├── MiaotongDoc-Editor/             # OnlyOffice 编辑器定制
│   ├── Dockerfile
│   ├── branding/                   # 品牌 Logo
│   ├── fonts/                      # 中文字体
│   └── plugins/                    # 签署/AI 插件
│
├── MiaotongDoc-AI/                 # AI 插件模块
│
├── FEATURE_DESIGN.md               # 产品功能规划
├── MiaotongDoc-Architecture.md     # 系统架构文档
└── SYSTEM_AUDIT.md                 # 系统审查报告
```

---

## 四、功能模块详解

### 4.1 文档管理

**核心功能**:
- 创建文档（Word/Excel/PPT/Markdown/PDF）
- 上传文件（支持拖拽）
- 文档列表（卡片视图/列表视图）
- 搜索（全文搜索 + 搜索建议）
- 重命名、软删除/恢复、永久删除
- 收藏/标星
- 批量操作（删除、导出 ZIP）
- 文件夹管理（无限层级、颜色标记、拖拽排序）
- 部门树筛选

**API 端点**: `POST/GET/PUT/DELETE /api/documents/*`

### 4.2 在线编辑

**编辑器类型**:
| 文档类型 | 编辑器 | 技术方案 |
|----------|--------|----------|
| Word/Excel/PPT | DocumentEditor | OnlyOffice Document Server |
| Markdown | MarkdownEditor | TipTap + Yjs 实时协同 |
| PDF | PdfViewer | pdfjs-dist |

**OnlyOffice 集成**:
- 3 实例负载均衡（Nginx hash 路由）
- JWT 认证（独立于应用 JWT）
- 自动保存（forcesave）
- 品牌定制（Logo、中文字体）
- 文档锁定（签署/审批期间只读）

**Markdown 协同编辑**:
- 基于 Tiptap v3 构建
- Yjs CRDT 实时同步
- 支持：标题、列表、表格、代码块、图片、链接等

### 4.3 版本管理

**功能**:
- 版本历史查看
- 手动保存版本（含变更说明）
- 版本下载
- 版本恢复
- 版本号自动递增

**API 端点**: `GET/POST /api/documents/{id}/versions`

### 4.4 共享与权限

**权限级别**:
| 级别 | 权限 |
|------|------|
| view | 只读 |
| comment | 可评论 |
| edit | 可编辑 |
| admin | 管理权限 |

**共享方式**:
- 按用户共享
- 按部门共享

**API 端点**: `POST/GET/PUT/DELETE /api/shares/*`

### 4.5 评论与协作

**功能**:
- 文档评论（支持行内引用）
- 评论回复（树形结构）
- @提及（自动通知）
- 评论解决/未解决
- 在线状态感知
- WebSocket 实时推送

**API 端点**: `POST/GET/PUT/DELETE /api/comments/*`

### 4.6 通知系统

**通知类型**:
- 签署请求/确认/拒绝/超期
- 评论/回复
- 版本更新
- @提及
- 文档共享

**功能**:
- 通知铃铛（未读计数）
- 通知列表（分页）
- 标记已读/全部已读
- WebSocket 实时推送

**API 端点**: `GET/PUT /api/notifications/*`

### 4.7 签署管理

**签署流程**:
1. 发起人创建签署任务（指定签署人 + 截止时间）
2. 系统自动共享文档给签署人（view 权限）
3. 签署人收到通知（WebSocket 推送）
4. 签署人确认签署（记录 IP/UA/文档哈希）
5. 所有人签完 → 文档状态变为 signed，锁定编辑
6. 超时未签 → 定时任务自动标记 expired

**功能**:
- 发起/确认/拒绝/取消签署
- 签署任务列表（我发起的/待我签的）
- 签署记录（IP、UA、时间戳、文档哈希）

**API 端点**: `POST/GET/PUT /api/signing/*`

### 4.8 合同管理

**合同生命周期**:
```
draft → pending_approval → approved/rejected → expired
```

**审批流程**:
1. 从 Word 文档智能解析合同字段（AI 辅助）
2. 创建合同（状态 draft）
3. 提交审批（指定审批人顺序链）
4. 顺序审批（每个审批人通过后推进到下一个）
5. 全部通过 → 状态 approved，解锁文档
6. 任一拒绝 → 状态 rejected，解锁文档

**功能**:
- 合同 CRUD
- 智能解析（合同编号、类型、甲乙方、金额、日期）
- 顺序审批流程
- 审批链可视化
- 文档完整性校验（哈希对比）
- 合同到期提醒（每天 9:00 检查）

**API 端点**: `POST/GET/PUT/DELETE /api/contracts/*`

### 4.9 用户与部门管理

**用户管理**:
- 登录/注册（工号 + 密码）
- 用户列表（分页、搜索）
- 角色管理（admin/user）
- 密码重置
- 启用/禁用

**部门管理**:
- 部门树（无限层级）
- 部门 CRUD
- 部门停用

**API 端点**: `/api/auth/*`, `/api/admin/*`, `/api/departments/*`

### 4.10 SSO 单点登录

**支持**:
- OAuth2 授权码流程
- 自动用户创建（auto-provision）
- 邮箱关联已有账号
- SSO 用户标记（禁止密码登录）

**API 端点**: `/api/sso/*`

### 4.11 AI 功能

**AI 插件**:
- LLM API 代理转发（不暴露 API Key）
- 支持普通请求和 SSE 流式响应
- 模型列表和配置管理

**文档 AI**:
- 文档问答
- 文档摘要
- 文档翻译
- 文档改写

**API 端点**: `/api/ai/*`, `/api/documents/*/ai/*`

### 4.12 系统运维

**审计日志**:
- 记录所有关键操作
- 包含 IP、UA、时间戳
- 支持查询和导出

**活动流**:
- 记录文档操作历史
- 按时间线展示

**其他**:
- PDF 导出（支持水印）
- Redis 缓存
- JWT 黑名单
- 主题切换（6 套预设 + 自定义）

---

## 五、数据库设计

### 核心表结构

#### 系统管理表
| 表名 | 用途 |
|------|------|
| `sys_department` | 部门表（树形结构） |
| `sys_user` | 用户表（8位工号、角色、SSO 标识） |

#### 文档核心表
| 表名 | 用途 |
|------|------|
| `mt_document` | 文档主表（doc_key、类型、状态、版本、收藏、文件夹） |
| `mt_document_share` | 文档共享表（4 级权限） |
| `mt_document_version` | 文档版本表 |
| `mt_folder` | 文件夹表（无限层级、颜色标记） |
| `mt_document_template` | 文档模板表 |
| `mt_template_category` | 模板分类 |
| `mt_watermark_config` | 水印配置表 |

#### 评论与协作表
| 表名 | 用途 |
|------|------|
| `mt_comment` | 评论批注表（支持回复、引用、解决） |
| `mt_mention` | @提及关联表 |
| `mt_activity` | 协作动态表 |
| `mt_notification` | 通知表 |

#### 签署与合同表
| 表名 | 用途 |
|------|------|
| `mt_signing_task` | 签署任务表 |
| `mt_signing_record` | 签署记录表（IP、UA、文档哈希） |
| `mt_contract` | 合同主表 |
| `mt_contract_approval` | 合同审批记录 |
| `mt_contract_approval_node` | 审批节点表（顺序审批链） |

#### 安全与审计表
| 表名 | 用途 |
|------|------|
| `mt_token_blacklist` | JWT 黑名单表 |
| `mt_sso_identity` | SSO 身份关联表 |
| `mt_audit_log` | 审计日志表 |
| `mt_audit_log_archive` | 审计日志归档表 |

### 数据库迁移

使用 Flyway 管理，共 21 个迁移脚本（V1-V21），位于：
`miaotongdoc-server/src/main/resources/db/migration/`

---

## 六、API 接口总览

### 认证模块 (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 |
| POST | `/register` | 用户注册 |
| GET | `/me` | 获取当前用户信息 |
| PUT | `/password` | 修改密码 |
| GET | `/users` | 获取所有活跃用户 |
| GET | `/users/search` | 搜索用户 |

### 文档模块 (`/api/documents`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建文档 |
| POST | `/upload` | 上传文件 |
| GET | `/list` | 文档列表 |
| GET | `/{id}` | 文档详情 |
| GET | `/{id}/config` | OnlyOffice 编辑器配置 |
| GET | `/{id}/file` | 下载文件 |
| GET | `/{id}/export/pdf` | 导出 PDF |
| PUT | `/{id}/rename` | 重命名 |
| DELETE | `/{id}` | 软删除 |
| DELETE | `/batch` | 批量删除 |
| POST | `/{id}/move` | 移动到文件夹 |
| POST | `/{id}/versions` | 创建版本 |
| PUT | `/{id}/star` | 收藏/取消收藏 |
| GET | `/trash` | 回收站列表 |
| POST | `/{id}/restore` | 恢复文档 |
| DELETE | `/{id}/permanent` | 永久删除 |
| DELETE | `/trash/empty` | 清空回收站 |
| POST | `/export/zip` | 批量导出 ZIP |
| POST | `/reindex` | 重建索引 |
| GET | `/suggest` | 搜索建议 |

### 文件夹模块 (`/api/folders`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取所有文件夹 |
| POST | `/` | 创建文件夹 |
| PUT | `/{id}` | 更新文件夹 |
| PUT | `/{id}/rename` | 重命名 |
| PUT | `/{id}/color` | 更新颜色 |
| DELETE | `/{id}` | 删除文件夹 |
| POST | `/reorder` | 拖拽排序 |
| GET | `/{id}/download` | 下载文件夹 |

### 共享模块 (`/api/shares`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 共享给用户 |
| POST | `/department` | 共享给部门 |
| GET | `/document/{docId}` | 获取文档共享列表 |
| PUT | `/{id}/permission` | 修改权限 |
| DELETE | `/{id}` | 取消共享 |

### 评论模块 (`/api/comments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建评论 |
| GET | `/document/{docId}` | 获取文档评论 |
| PUT | `/{id}/resolve` | 标记已解决 |
| DELETE | `/{id}` | 删除评论 |

### 签署模块 (`/api/signing`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建签署任务 |
| GET | `/tasks` | 获取任务列表 |
| GET | `/tasks/{id}` | 任务详情 |
| GET | `/tasks/by-document/{docId}` | 按文档获取任务 |
| POST | `/confirm` | 确认签署 |
| POST | `/reject` | 拒绝签署 |
| PUT | `/tasks/{id}/cancel` | 取消签署 |

### 合同模块 (`/api/contracts`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/parse/{documentId}` | 解析合同 |
| POST | `/` | 创建合同 |
| GET | `/` | 合同列表 |
| GET | `/{id}` | 合同详情 |
| PUT | `/{id}` | 编辑合同 |
| POST | `/{id}/submit` | 提交审批 |
| POST | `/{id}/approve` | 审批通过 |
| POST | `/{id}/reject` | 拒绝审批 |
| POST | `/{id}/cancel` | 撤回审批 |
| GET | `/{id}/integrity` | 完整性校验 |
| DELETE | `/{id}` | 删除合同 |
| GET | `/stats` | 合同统计 |

### 通知模块 (`/api/notifications`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 通知列表 |
| GET | `/unread-count` | 未读计数 |
| PUT | `/{id}/read` | 标记已读 |
| PUT | `/read-all` | 全部已读 |

### 管理后台 (`/api/admin`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/users` | 用户列表 |
| GET | `/users/search` | 搜索用户 |
| POST | `/users` | 创建用户 |
| PUT | `/users/{id}` | 更新用户 |
| PUT | `/users/{id}/role` | 修改角色 |
| PUT | `/users/{id}/status` | 启用/禁用 |
| PUT | `/users/{id}/reset-password` | 重置密码 |

### AI 模块 (`/api/ai`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/proxy` | LLM API 代理 |
| GET | `/models` | 模型列表 |
| GET | `/config` | AI 配置 |

---

## 七、前端页面结构

### 路由配置

| 路径 | 页面 | 认证要求 | 说明 |
|------|------|----------|------|
| `/login` | Login | 无 | 登录页 |
| `/home` | Home | 需要 | 主页（文档列表） |
| `/editor/:id` | DocEditor | 需要 | 文档编辑页 |
| `/contracts/:id` | ContractDetail | 需要 | 合同详情页 |
| `/signing` | SigningTask | 需要 | 签署任务列表 |
| `/activity` | ActivityFeed | 需要 | 个人动态 |
| `/admin` | Admin | 需要 + admin | 管理后台 |

### 状态管理 (Pinia Stores)

| Store | 文件 | 用途 |
|-------|------|------|
| `useUserStore` | `stores/user.ts` | 用户信息、JWT Token |
| `useDocumentStore` | `stores/document.ts` | 文档列表、分页 |
| `useNotificationStore` | `stores/notification.ts` | 通知、未读计数 |
| `usePresenceStore` | `stores/presence.ts` | 在线状态 |

### 组件结构

**页面组件** (`views/`):
- `Login.vue` - 登录页
- `Home.vue` - 主页（文档列表、侧边栏、文件夹树）
- `DocEditor.vue` - 文档编辑页
- `ContractDetail.vue` - 合同详情页
- `SigningTask.vue` - 签署任务页
- `ActivityFeed.vue` - 个人动态页
- `Admin.vue` - 管理后台

**通用组件** (`components/`):
- `DocumentEditor.vue` - OnlyOffice 编辑器容器
- `MarkdownEditor.vue` - Markdown 编辑器
- `PdfViewer.vue` - PDF 查看器
- `DocCard.vue` - 文档卡片
- `ShareDialog.vue` - 共享对话框
- `CommentPanel.vue` - 评论面板
- `VersionHistory.vue` - 版本历史
- `SigningDialog.vue` - 签署对话框
- `ContractCreateDialog.vue` - 合同创建对话框
- `NotificationBell.vue` - 通知铃铛
- `ThemeSwitch.vue` - 主题切换
- `AiPanel.vue` - AI 助手面板

---

## 八、WebSocket 通道

### 通道列表

| 通道 | 路径 | 用途 |
|------|------|------|
| 通知推送 | `/ws/notifications` | 实时推送通知到用户 |
| 在线状态 | `/ws/presence/{docId}` | 文档级在线用户感知 |
| 协同编辑 | `/ws/yjs` | Yjs CRDT 同步（独立端口 1234） |

### 通信协议

**通知通道**:
```json
{
  "type": "notification",
  "data": {
    "id": 1,
    "type": "signing_request",
    "title": "签署请求",
    "content": "...",
    "documentId": 123,
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

**在线状态通道**:
```json
// 加入
{ "type": "join", "userId": 1, "username": "user1", "realName": "用户1" }

// 离开
{ "type": "leave", "userId": 1 }
```

---

## 九、部署架构

### Docker 服务清单（13 个容器）

| 服务 | 容器名 | 镜像 | 端口 | IP | 用途 |
|------|--------|------|------|-----|------|
| nginx | miaotongdoc-nginx | nginx:latest | 80 | 172.20.0.10 | 反向代理 |
| postgres | miaotongdoc-postgres | postgres:12 | 5432 | 172.20.0.20 | 数据库 |
| redis | miaotongdoc-redis | redis:7-alpine | 6379 | 172.20.0.30 | 缓存 |
| rabbitmq | miaotongdoc-rabbitmq | rabbitmq:3-management | 5672/15672 | 172.20.0.35 | 消息队列 |
| web-server | miaotongdoc-server | eclipse-temurin:17-jre | 9004 | 172.20.0.40 | Spring Boot 后端 |
| editor | miaotongdoc-editor | miaotongdoc-editor:latest | (内部80) | 172.20.0.50 | OnlyOffice 主实例 |
| editor2 | miaotongdoc-editor2 | (同上) | (内部80) | 172.20.0.51 | OnlyOffice 副实例1 |
| editor3 | miaotongdoc-editor3 | (同上) | (内部80) | 172.20.0.52 | OnlyOffice 副实例2 |
| cache-cleaner | miaotongdoc-cache-cleaner | alpine:latest | - | - | 定时清理缓存 |
| logrotate | miaotongdoc-logrotate | alpine:latest | - | - | 日志轮转 |
| minio | miaotongdoc-minio | minio/minio:latest | 9000/9001 | 172.20.0.60 | 对象存储 |
| elasticsearch | miaotongdoc-elasticsearch | elasticsearch:8.11.0 | (内部9200) | 172.20.0.70 | 全文搜索 |
| yjs-server | miaotongdoc-yjs | 自建 | 1234 | 172.20.0.80 | Yjs 协同服务器 |

### 网络架构

```
所有容器位于 mtd-net 网络（172.20.0.0/16 子网）

客户端请求流程:
Client → Nginx (80) → 
  ├─ /api/* → Spring Boot (9004)
  ├─ /ws/presence/* → Spring Boot (9004)
  ├─ /ws/notifications → Spring Boot (9004)
  ├─ /ws/yjs → Yjs Server (1234)
  ├─ /editor/* → OnlyOffice (80)
  └─ /* → 前端静态资源
```

### 快速部署

```bash
# 1. 克隆项目
git clone <repo-url>

# 2. 进入 Docker 目录
cd MiaotongDoc-Docker

# 3. 复制环境变量
cp .env.example .env

# 4. 编辑环境变量
vi .env

# 5. 一键部署
./deploy.sh start

# 6. 查看状态
./deploy.sh status

# 7. 查看日志
./deploy.sh logs
```

---

## 十、开发指南

### 本地开发环境

**前端开发**:
```bash
cd miaotongdoc-web
npm install
npm run dev  # 启动开发服务器（端口 3000）
```

**后端开发**:
```bash
cd miaotongdoc-server
mvn spring-boot:run  # 启动后端（端口 9004）
```

**依赖服务**:
- PostgreSQL 12
- Redis 7
- Elasticsearch 8.11（可选）
- RabbitMQ 3（可选）
- OnlyOffice Document Server（可选，编辑 Office 文档需要）

### 构建部署

**前端构建**:
```bash
cd miaotongdoc-web
npm run build
# 产物在 dist/ 目录
```

**后端构建**:
```bash
cd miaotongdoc-server
mvn clean package
# 产物在 target/ 目录
```

**Docker 构建**:
```bash
cd MiaotongDoc-Docker
./deploy.sh build
```

---

## 十一、安全设计

### 认证流程

```
客户端登录 → AuthController.login()
  → UserService.login() 校验密码
  → JwtUtil.generateToken(userId, employeeId, username, role)
  → 返回 Bearer Token

客户端请求 → JwtAuthFilter
  → 解析 Authorization: Bearer xxx
  → JwtUtil.validateToken()
  → TokenBlacklistService.isBlacklisted() 检查黑名单
  → 设置认证上下文
```

### 权限模型

**系统角色**:
- `admin` - 管理员（可访问管理后台）
- `user` - 普通用户

**文档权限**:
- `view` - 只读
- `comment` - 可评论
- `edit` - 可编辑
- `admin` - 管理权限

### 安全特性

- JWT 认证（2 小时过期）
- JWT 黑名单（登出时失效）
- BCrypt 密码加密
- CORS 跨域配置
- XSS 防护
- 审计日志
- 文档水印

---

## 十二、定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| 合同到期检查 | 每天 9:00 | 检查到期/即将到期合同 |
| OnlyOffice 缓存清理 | 每分钟 | 清理过期 task_result 和 doc_changes |
| 签署超期检查 | 每分钟 | 检查超期签署任务 |

---

## 十三、核心业务流程

### 文档编辑流程

```
1. 用户创建/上传文档
   → DocumentService 生成 docKey，存储文件，创建版本记录

2. 前端获取编辑器配置
   → DocumentController.getEditorConfig() 返回 OnlyOffice 配置

3. OnlyOffice 加载文档
   → 从 /api/documents/{id}/file 下载

4. 用户编辑
   → OnlyOffice 服务器处理协作

5. 保存/关闭
   → 回调 /api/callback/editor
   → CallbackService.saveDocument() 下载并更新文件
```

### 文档签署流程

```
1. 发起人创建签署任务
   → SigningService.createTask()
   → 自动共享文档给签署人（view 权限）

2. 签署人收到通知
   → WebSocket 实时推送

3. 签署人确认签署
   → 记录 IP/UA/文档哈希

4. 所有人签完
   → 文档状态变为 signed，锁定编辑

5. 超时未签
   → 定时任务自动标记 expired
```

### 合同审批流程

```
1. 解析合同
   → ContractParser 从 Word 文档提取字段

2. 创建合同
   → 状态 draft

3. 提交审批
   → 创建审批节点链，锁定文档，状态 pending_approval

4. 顺序审批
   → 每个审批人通过后推进到下一个节点

5. 全部通过
   → 状态 approved，解锁文档

6. 任一拒绝
   → 状态 rejected，解锁文档
```

---

## 十四、配置文件说明

### 后端配置 (`application.yml`)

```yaml
# 数据库配置
spring.datasource:
  url: jdbc:postgresql://localhost:5432/miaotongdocdb
  username: postgres
  password: ${DB_PASSWORD}

# Redis 配置
spring.data.redis:
  host: localhost
  port: 6379
  password: ${REDIS_PASSWORD}

# Elasticsearch 配置
spring.elasticsearch:
  uris: http://localhost:9200

# 存储配置
storage:
  type: local  # local 或 minio
  local:
    path: ./data/documents
  minio:
    endpoint: http://localhost:9000
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: miaotongdoc

# JWT 配置
jwt:
  secret: ${JWT_SECRET}
  expiration: 7200000  # 2小时

# AI 代理配置
ai:
  proxy:
    url: ${AI_API_URL}
    key: ${AI_API_KEY}
    model: ${AI_MODEL}
    timeout: 60
```

### 前端配置 (`vite.config.ts`)

```typescript
export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:9004',
      '/ws/presence': {
        target: 'ws://localhost:9004',
        ws: true
      },
      '/ws/notifications': {
        target: 'ws://localhost:9004',
        ws: true
      },
      '/ws/yjs': {
        target: 'ws://localhost:1234',
        ws: true
      }
    }
  }
})
```

---

## 十五、相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 产品功能规划 | `FEATURE_DESIGN.md` | 7 个模块、页面清单、优先级排序 |
| 系统架构文档 | `MiaotongDoc-Architecture.md` | 架构图、Docker 服务清单、问题排查 |
| 系统审查报告 | `SYSTEM_AUDIT.md` | 已开发功能、43 个问题、修复计划 |
| Docker 部署文档 | `MiaotongDoc-Docker/README.md` | 详细部署指南 |
| 编辑器说明 | `MiaotongDoc-Editor/README.md` | 编辑器镜像构建说明 |
| AI 插件变更日志 | `MiaotongDoc-AI/CHANGELOG.md` | AI 插件版本记录 |

---

## 十六、快速上手指南

### 新开发者入门

1. **阅读本文档** - 了解项目整体架构
2. **查看 `FEATURE_DESIGN.md`** - 了解产品功能规划
3. **查看 `MiaotongDoc-Architecture.md`** - 了解系统架构
4. **本地启动开发环境** - 按照"开发指南"章节操作
5. **熟悉 API 接口** - 查看"API 接口总览"章节
6. **了解数据库结构** - 查看"数据库设计"章节

### AI Agent 快速熟悉

1. **项目定位**: 企业级在线文档协作与签署平台
2. **技术栈**: Vue 3 + Spring Boot + PostgreSQL + Redis + Elasticsearch
3. **核心功能**: 文档管理、在线编辑、版本控制、共享协作、签署审批、合同管理
4. **代码结构**: 前端 `miaotongdoc-web/`，后端 `miaotongdoc-server/`
5. **部署方式**: Docker Compose（13 个容器）
6. **API 风格**: RESTful，JWT 认证
7. **实时通信**: WebSocket（通知、在线状态、协同编辑）

---

## 十七、环境变量配置详解

环境变量配置文件位于 `MiaotongDoc-Docker/.env`（从 `.env.example` 复制）。

### 数据库配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POSTGRES_DB` | `miaotongdocdb` | 数据库名称（自动创建） |
| `POSTGRES_USER` | `miaotong` | 数据库用户（自动创建） |
| `DB_PASSWORD` | - | 数据库密码（必填，≥8位） |

### Redis 配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `REDIS_PASSWORD` | - | Redis 密码（必填，≥8位） |

### RabbitMQ 配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `RABBITMQ_PASSWORD` | - | RabbitMQ 密码（必填，≥8位） |

### JWT 配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `APP_JWT_SECRET` | - | 应用 JWT 密钥（必填，≥32位） |
| `APP_JWT_EXPIRATION` | `7200000` | JWT 过期时间（毫秒，默认2小时） |
| `EDITOR_JWT_SECRET` | - | 编辑器 JWT 密钥（必填，≥32位） |

### 存储配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `STORAGE_TYPE` | `minio` | 存储类型：`local` 或 `minio` |
| `MINIO_ACCESS_KEY` | - | MinIO 访问密钥（minio 模式必填） |
| `MINIO_SECRET_KEY` | - | MinIO 秘密密钥（minio 模式必填） |
| `MINIO_BUCKET` | `miaotongdoc` | MinIO 存储桶名称 |

### SSO 配置（可选）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SSO_ENABLED` | `false` | 是否启用 SSO |
| `SSO_PROVIDER_NAME` | `统一认证` | SSO 提供商显示名称 |
| `SSO_ISSUER_URI` | - | SSO 服务器地址 |
| `SSO_CLIENT_ID` | - | SSO 客户端 ID |
| `SSO_CLIENT_SECRET` | - | SSO 客户端密钥 |
| `SSO_AUTO_PROVISION` | `true` | 是否自动创建用户 |

### AI 配置（可选）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `LLM_API_URL` | - | LLM API 地址（OpenAI 兼容格式） |
| `LLM_API_KEY` | - | LLM API 密钥 |
| `LLM_TIMEOUT` | `300` | LLM 请求超时（秒） |

### 其他配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `CORS_ORIGINS` | `https://doc.bank.com` | 允许的跨域来源 |
| `SECURE_LINK_SECRET` | - | OnlyOffice 安全链接密钥（≥16位） |
| `CACHE_MAX_AGE_HOURS` | `24` | 编辑器缓存保留时间（小时） |

---

## 十八、常见问题排查

### 1. OnlyOffice 编辑器无法打开文档

**可能原因**:
- OnlyOffice 容器未启动
- JWT 密钥配置不一致
- 网络连接问题

**排查步骤**:
```bash
# 检查编辑器容器状态
docker ps | grep editor

# 查看编辑器日志
docker logs miaotongdoc-editor

# 检查 JWT 密钥配置
# 确保 .env 中 EDITOR_JWT_SECRET 与编辑器配置一致
```

### 2. WebSocket 连接失败

**可能原因**:
- Nginx 代理配置问题
- JWT Token 无效或过期
- 防火墙阻止 WebSocket

**排查步骤**:
```bash
# 检查 Nginx 配置
docker exec miaotongdoc-nginx nginx -t

# 查看浏览器控制台 WebSocket 连接错误
# 确保 ws:// 或 wss:// 协议正确
```

### 3. 文件上传失败

**可能原因**:
- MinIO 服务未启动
- 存储配置错误
- 文件大小超过限制

**排查步骤**:
```bash
# 检查 MinIO 状态
docker logs miaotongdoc-minio

# 检查存储配置
cat .env | grep STORAGE_TYPE

# 检查文件大小限制（默认 100MB）
# Nginx 配置: client_max_body_size 100m
```

### 4. 数据库连接失败

**可能原因**:
- PostgreSQL 容器未启动
- 密码配置错误
- 数据库未初始化

**排查步骤**:
```bash
# 检查 PostgreSQL 状态
docker ps | grep postgres

# 查看 PostgreSQL 日志
docker logs miaotongdoc-postgres

# 测试数据库连接
docker exec -it miaotongdoc-postgres psql -U miaotong -d miaotongdocdb
```

### 5. Elasticsearch 索引问题

**可能原因**:
- Elasticsearch 容器未启动
- 索引未创建
- 内存不足

**排查步骤**:
```bash
# 检查 Elasticsearch 状态
docker logs miaotongdoc-elasticsearch

# 检查索引
curl http://localhost:9200/_cat/indices

# 手动触发重建索引
curl -X POST http://localhost:9004/api/documents/reindex \
  -H "Authorization: Bearer <token>"
```

### 6. 前端构建失败

**可能原因**:
- Node.js 版本不兼容
- 依赖安装失败
- TypeScript 类型错误

**排查步骤**:
```bash
# 检查 Node.js 版本（需要 18+）
node -v

# 清除缓存重新安装
rm -rf node_modules package-lock.json
npm install

# 查看详细错误
npm run build --verbose
```

---

## 十九、开发规范

### 代码风格

**前端 (TypeScript/Vue)**:
- 使用 Composition API (`<script setup>`)
- 组件命名：PascalCase（如 `DocCard.vue`）
- 文件命名：camelCase（如 `document.ts`）
- 使用 TypeScript 类型定义
- 遵循 ESLint 规则

**后端 (Java/Spring Boot)**:
- 遵循 Spring Boot 分层架构
- Controller → Service → Repository
- 使用 DTO 隔离实体和 API
- 异常统一处理
- 日志规范：使用 SLF4J

### Git 提交规范

```
<type>(<scope>): <subject>

类型:
- feat: 新功能
- fix: 修复
- docs: 文档
- style: 格式
- refactor: 重构
- test: 测试
- chore: 构建/工具

示例:
feat(document): 添加文档版本管理功能
fix(signing): 修复签署超期检查逻辑
docs(readme): 更新部署文档
```

### API 设计规范

- RESTful 风格
- 统一响应格式:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {}
  }
  ```
- 错误响应:
  ```json
  {
    "code": 400,
    "message": "参数错误",
    "data": null
  }
  ```
- 分页响应:
  ```json
  {
    "code": 200,
    "data": {
      "content": [],
      "totalElements": 100,
      "totalPages": 10,
      "number": 0
    }
  }
  ```

### 数据库规范

- 表名前缀：
  - `sys_` - 系统表
  - `mt_` - 业务表
- 字段命名：snake_case
- 必须字段：
  - `id` - 主键（BIGSERIAL）
  - `created_at` - 创建时间
  - `updated_at` - 更新时间
- 使用 Flyway 管理迁移

---

## 二十、性能优化建议

### 前端优化

1. **代码分割**: 使用 Vue Router 懒加载
2. **图片优化**: 压缩图片，使用 WebP 格式
3. **缓存策略**: 静态资源使用 CDN
4. **虚拟滚动**: 大列表使用虚拟滚动
5. **防抖节流**: 搜索、滚动等事件添加防抖

### 后端优化

1. **数据库索引**: 为常用查询字段添加索引
2. **查询优化**: 避免 N+1 查询，使用 JOIN FETCH
3. **缓存**: Redis 缓存热点数据
4. **异步处理**: 耗时操作使用异步
5. **连接池**: 合理配置数据库连接池

### Docker 优化

1. **资源限制**: 为容器设置 CPU/内存限制
2. **健康检查**: 配置容器健康检查
3. **日志轮转**: 配置日志大小限制
4. **数据持久化**: 使用 Volume 持久化数据

---

## 二十一、已知问题与限制

### 当前限制

1. **文件大小限制**: 单文件上传最大 100MB（可配置）
2. **并发编辑**: OnlyOffice 最大 20 人同时编辑
3. **存储限制**: 本地存储受磁盘空间限制
4. **全文搜索**: Elasticsearch 需要额外内存（建议 2GB+）

### 已知问题

1. **OnlyOffice 缓存**: 长时间使用后可能产生大量临时文件
   - 解决方案: 定时任务自动清理（已配置）

2. **WebSocket 断连**: 网络不稳定时可能断连
   - 解决方案: 前端自动重连机制（指数退避）

3. **大文件处理**: 上传大文件时可能超时
   - 解决方案: 调整 Nginx 和后端超时配置

4. **中文搜索**: 分词精度依赖 Elasticsearch 配置
   - 解决方案: 使用 IK 分词器（需额外配置）

---

## 二十二、监控与日志

### 监控指标

**Prometheus 端点**: `/actuator/prometheus`

**关键指标**:
- `http_server_requests_seconds` - HTTP 请求耗时
- `jvm_memory_used_bytes` - JVM 内存使用
- `hikaricp_connections_active` - 数据库连接池活跃数
- `redisson_connections_active` - Redis 连接数

### 日志配置

**日志级别**:
- `com.miaotong.doc` - DEBUG（开发）/ INFO（生产）
- `org.springframework.security` - DEBUG（调试认证）

**日志文件**:
- 后端日志: `data/logs/miaotongdoc.log`
- Nginx 日志: `data/nginx/logs/`
- PostgreSQL 日志: `data/postgres/logs/`

**日志轮转**:
- 每天轮转
- 保留 30 天
- 单文件最大 100MB

### 健康检查

```bash
# 检查所有服务状态
./deploy.sh health

# 检查单个服务
curl http://localhost:9004/actuator/health
curl http://localhost:9200/_cluster/health
```

---

## 二十三、备份与恢复

### 数据库备份

```bash
# 手动备份
docker exec miaotongdoc-postgres pg_dump -U miaotong miaotongdocdb > backup.sql

# 自动备份（添加到 crontab）
0 2 * * * docker exec miaotongdoc-postgres pg_dump -U miaotong miaotongdocdb > /path/to/backup/$(date +\%Y\%m\%d).sql
```

### 数据库恢复

```bash
# 恢复备份
docker exec -i miaotongdoc-postgres psql -U miaotong miaotongdocdb < backup.sql
```

### 文件备份

```bash
# 备份文档文件
tar -czf documents-backup.tar.gz data/documents/

# 备份 MinIO 数据
tar -czf minio-backup.tar.gz data/minio/
```

### 完整备份

```bash
# 使用部署脚本备份
./deploy.sh backup

# 备份文件位于 data/backups/ 目录
```

---

## 二十四、扩展开发指南

### 添加新功能模块

1. **后端**:
   - 创建 Entity: `entity/NewEntity.java`
   - 创建 Repository: `repository/NewRepository.java`
   - 创建 Service: `service/NewService.java`
   - 创建 Controller: `controller/NewController.java`
   - 添加数据库迁移: `db/migration/V22__add_new_table.sql`

2. **前端**:
   - 创建 API 模块: `api/newModule.ts`
   - 创建页面组件: `views/NewPage.vue`
   - 添加路由配置: `router/index.ts`
   - 如需要，创建 Store: `stores/newModule.ts`

### 自定义编辑器插件

编辑器插件位于 `MiaotongDoc-Editor/plugins/`:

```
plugins/
├── signing/          # 签署插件
└── ai/              # AI 插件
```

插件开发参考 OnlyOffice 官方文档。

### AI 功能扩展

AI 功能通过 `AiProxyService` 代理转发，支持：
- OpenAI 兼容格式
- SSE 流式响应
- 自定义模型

配置 AI 服务地址和密钥即可使用。

---

*本文档由 Claude Code 自动生成，旨在帮助开发者和 AI Agent 快速了解项目结构和功能。*
