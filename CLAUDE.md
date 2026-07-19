# MiaotongDoc 开发参考手册

> **版本**: v1.0.3 | **维护者**: Claude Code
> **项目**: 企业级多格式在线文档协作与签署平台
> **最后更新**: 2026年7月18日

---

## 📋 目录

- [快速开始](#快速开始)
- [项目架构](#项目架构)
- [技术栈](#技术栈)
- [代码结构](#代码结构)
- [核心功能](#核心功能)
- [API 接口](#api-接口)
- [数据库设计](#数据库设计)
- [部署指南](#部署指南)
- [更新部署包](#更新部署包)
- [内网迁移部署](#内网迁移部署)（详细方案见 [DEPLOY.md](DEPLOY.md)）
- [开发规范](#开发规范)
- [调试技巧](#调试技巧)
- [常见问题](#常见问题)
- [安全指南](#安全指南)
- [性能优化](#性能优化)
- [扩展开发](#扩展开发)

---

## 快速开始

### 本地开发环境启动

```bash
# 1. 启动基础设施（Docker）
cd MiaotongDoc-Docker
docker compose up -d postgres redis elasticsearch rabbitmq minio

# 2. 启动后端（端口 9004）
cd miaotongdoc-server
mvn spring-boot:run

# 3. 启动前端（端口 3000）
cd miaotongdoc-web
npm install
npm run dev

# 4. 访问 http://localhost:3000
```

### 默认账号

| 用途 | 账号 | 密码 |
|------|------|------|
| 管理员 | `10000000` | `123456` |
| 数据库 | `miaotong` | 见 `.env` |

> 注：管理员初始密码 `Admin@123` 无效，需按 [DEPLOY.md 第 4 步](DEPLOY.md#第-4-步重置管理员密码)重置为 `123456`。

### 一键部署（Docker）

> **生产环境（Linux）**：首次部署先运行 `sudo ./setup-linux-host.sh` 完成宿主机基线配置（见 [DEPLOY.md - Linux 生产环境基线配置](DEPLOY.md#linux-生产环境基线配置)），再执行下面的 `deploy.sh start`。

```bash
cd MiaotongDoc-Docker
cp .env.example .env
vi .env  # 修改密码
./deploy.sh start    # 自动按 A→E 分阶段启动，无需手动排序
```

`deploy.sh` 子命令：`start / stop / status / health / logs / build / backup / clean-logs / restart`。

---

## 项目架构

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端 (浏览器)                         │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Nginx (反向代理)                          │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │ /api/*   │ /ws/*    │ /editor/*│ /ws/yjs  │ /*       │  │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┘  │
└───────┼──────────┼──────────┼──────────┼──────────┼─────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│ Spring   │ │ Spring   │ │ MTOffice│ │ Yjs      │ │ 前端静态  │
│ Boot     │ │ Boot     │ │ Editor   │ │ Server   │ │ 资源     │
│ (9004)   │ │ (9004)   │ │ (80)     │ │ (1234)   │ │          │
└────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────┘
     │            │            │            │
     └────────────┴────────────┴────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────┐        ┌──────────┐        ┌──────────┐
│PostgreSQL│        │  Redis   │        │   ES     │
│ (5432)   │        │ (6379)   │        │ (9200)   │
└──────────┘        └──────────┘        └──────────┘
```

### 请求流转路径

| 请求类型 | 路径 | 目标服务 | 路由策略 |
|----------|------|----------|----------|
| API 请求 | `/api/*` | Spring Boot (9004) | 直接代理 |
| WebSocket | `/ws/presence/*`, `/ws/notifications` | Spring Boot (9004) | 升级协议 |
| AI 聊天（SSE） | `/api/ai/chat/stream`, `/ws/ai/` | Spring Boot (9004) | 流式输出 |
| 协同编辑 | `/ws/yjs` | Yjs Server (1234) | WebSocket 升级 |
| 编辑器 | `/editor/*` | MTOffice (80) × 3 实例 | **hash 分流**：按文档 key + IP 哈希到固定 editor，保证同一文档编辑请求落到同一实例 |
| socket.io | `/ds-vpath/.../vendor/socket.io` | MTOffice × 3 实例 | 按 remote_addr 哈希保持长连接 |
| 静态资源 | `/*` | Nginx 直接返回 | 无代理 |

---

## 技术栈

### 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.4+ | SPA 框架（Composition API） |
| Element Plus | 2.5+ | UI 组件库 |
| Vite 5 | 5.x | 构建工具 |
| TypeScript | 5.x | 类型系统 |
| Pinia | 2.1+ | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| Axios | 1.6+ | HTTP 客户端 |
| Tiptap | 3.26+ | Markdown 编辑器 |
| Yjs | 13.6+ | CRDT 实时协同 |
| MTOffice | 9.3 | Office 文档编辑 |
| pdfjs-dist | 4.8+ | PDF 渲染 |

### 后端技术栈

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
| JWT (jjwt) | 0.12.5 | 认证令牌 |

---

## 代码结构

### 项目目录

```
MiaotongDoc/
├── miaotongdoc-web/                # Vue 3 前端
│   └── src/
│       ├── api/                    # API 模块 (22个)
│       ├── components/             # 组件 (41个)
│       ├── router/                 # 路由 (8条)
│       ├── stores/                 # 状态管理 (4个)
│       ├── utils/                  # 工具函数 (8个)
│       └── views/                  # 页面 (8个)
│
├── miaotongdoc-server/             # Spring Boot 后端
│   └── src/main/java/com/miaotong/doc/
│       ├── config/                 # 配置类 (15个)
│       ├── controller/             # 控制器 (29个)
│       ├── entity/                 # 实体类 (25个)
│       ├── repository/             # 数据仓库 (25个)
│       ├── service/                # 业务服务 (30个)
│       └── websocket/              # WebSocket (3个)
│
├── MiaotongDoc-Docker/             # Docker 部署
│   ├── docker-compose.yml          # 13 个服务编排
│   ├── deploy.sh                   # 分阶段启动脚本（A→E）
│   └── app/                        # 构建产物（前端/后端/编辑器/yjs/docling/ocr）
│
├── MiaotongDoc-Editor/             # MTOffice 定制
│   ├── Dockerfile
│   ├── branding/                   # 品牌 Logo
│   ├── fonts/                      # 中文字体
│   └── plugins/                    # 插件
│
├── MiaotongDoc-AI/                 # AI 插件
├── setup-linux-host.sh             # Linux 宿主机一键基线配置（生产部署前运行）
├── DEPLOY.md                       # 详细内网部署指南
└── CLAUDE.md                       # 本文件
```

### 前端关键文件

| 文件 | 用途 |
|------|------|
| `src/api/index.ts` | Axios 实例配置 |
| `src/router/index.ts` | 路由配置 |
| `src/stores/user.ts` | 用户状态管理 |
| `src/views/Home.vue` | 主页（核心页面） |
| `src/views/DocEditor.vue` | 文档编辑页 |
| `src/components/MarkdownEditor.vue` | Markdown 编辑器 |
| `src/components/DocumentEditor.vue` | MTOffice 编辑器 |
| `src/components/PdfEditor.vue` | **PDF 编辑器 V3 主壳**(Phase 7-12) |
| `src/components/PdfRibbon.vue` | PDF 多 tab 顶栏 |
| `src/components/PdfThumbPanel.vue` | PDF 缩略图侧栏(2x 高清+懒加载+拖拽) |
| `src/components/PdfCanvas.vue` | PDF 单页渲染容器(4 层堆叠) |
| `src/components/PdfToolsRail.vue` | PDF 右侧快捷工具栏 |
| `src/components/PdfRightPanel.vue` | PDF 右侧任务面板(大纲/搜索/批注/表单/信息) |
| `src/components/PdfPageOpsDialog.vue` | PDF 页面操作(插入/裁剪/水印/页眉页脚) |
| `src/components/PdfSignatureDialog.vue` | PDF 签名创建(键入/绘制/上传) |
| `src/components/PdfSecurityDialog.vue` | PDF 保护(加密/解密) |
| `src/composables/pdf/usePdfRenderer.ts` | PDF.js 渲染封装(worker 本地化+cMap) |

### 后端关键文件

| 文件 | 用途 |
|------|------|
| `config/SecurityConfig.java` | 安全配置 |
| `config/JwtAuthFilter.java` | JWT 过滤器 |
| `controller/DocumentController.java` | 文档 API |
| `service/DocumentService.java` | 文档业务逻辑 |
| `service/storage/StorageService.java` | 存储接口 |
| `websocket/PresenceWebSocketHandler.java` | 在线状态 |

---

## 核心功能

### 功能模块

| 模块 | 说明 | API 前缀 |
|------|------|----------|
| 文档管理 | 创建、上传、列表、搜索、删除 | `/api/documents` |
| 文件夹 | 无限层级、颜色标记、拖拽排序 | `/api/folders` |
| 在线编辑 | MTOffice、Markdown、PDF | `/api/documents/{id}/config` |
| 版本管理 | 版本历史、恢复、下载 | `/api/documents/{id}/versions` |
| 共享权限 | 用户级、部门级、4级权限 | `/api/shares` |
| 评论协作 | 评论、@提及、解决 | `/api/comments` |
| 通知系统 | 实时推送、未读计数 | `/api/notifications` |
| 签署管理 | 发起、确认、拒绝、取消 | `/api/signing` |
| 合同管理 | 创建、审批、完整性校验 | `/api/contracts` |
| 用户管理 | 登录、注册、角色、部门 | `/api/auth`, `/api/admin` |
| SSO | OAuth2 单点登录 | `/api/sso` |
| AI 功能 | 问答、摘要、翻译、改写 | `/api/ai` |
| **PDF 编辑器 V3** | Ribbon/缩略图/ToolsRail/标注/页面操作/OCR/表单/签名/加密 | `/api/pdf` |

### 文档状态机

```
draft (草稿)
  ↓ 发起签署
signing (签署中)
  ↓ 所有人签署完成
signed (已签署，锁定)
  ↓ 取消签署
draft (草稿)
```

### 合同审批状态机

```
draft (草稿)
  ↓ 提交审批
pending_approval (审批中)
  ↓ 全部通过
approved (已批准)
  ↓ 任一拒绝
rejected (已拒绝)
  ↓ 撤回审批
draft (草稿)
```

### 权限检查优先级

```
1. 管理员 (admin role) → 绕过所有检查
2. 文档所有者 (owner) → 全部权限
3. 文档共享权限 (DocumentShare)
   - admin: 管理权限
   - edit: 编辑权限
   - comment: 评论权限
   - view: 只读权限
4. 未共享 → 无权限
```

---

## API 接口

### 认证模块 (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 推荐命令 curl -s -X POST "http://localhost:9004/api/auth/login" -H "Content-Type: application/json" -d '{"username":"10000000","password":"123456"}' 2>&1 |
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
| GET | `/list` | 文档列表（支持分页、排序、筛选） |
| GET | `/{id}` | 文档详情 |
| GET | `/{id}/config` | MTOffice 编辑器配置 |
| GET | `/{id}/file` | 下载文件 |
| GET | `/{id}/export/pdf` | 导出 PDF |
| PUT | `/{id}/rename` | 重命名 |
| DELETE | `/{id}` | 软删除（移入回收站） |
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

### 共享模块 (`/api/shares`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 共享给用户（view/comment/edit 权限） |
| POST | `/department` | 共享给部门 |
| GET | `/document/{docId}` | 获取文档共享列表 |
| PUT | `/{id}/permission` | 修改权限 |
| DELETE | `/{id}` | 取消共享 |

### 签署模块 (`/api/signing`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建签署任务 |
| GET | `/tasks` | 获取任务列表（type=initiated/todo） |
| GET | `/tasks/{id}` | 任务详情 |
| GET | `/tasks/by-document/{docId}` | 按文档获取任务 |
| POST | `/confirm` | 确认签署 |
| POST | `/reject` | 拒绝签署 |
| PUT | `/tasks/{id}/cancel` | 取消签署 |

### 合同模块 (`/api/contracts`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/parse/{documentId}` | 解析合同（AI 辅助） |
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

### PDF 工具模块 (`/api/pdf`) — Phase 11-12 新增

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{id}/info` | PDF 基本信息(页数/尺寸/版本) |
| GET | `/{id}/metadata` | PDF 元数据(标题/作者/创建时间) |
| GET | `/{id}/text` | 全文提取 |
| GET | `/{id}/pages/{pageNum}/text` | 单页文本 |
| POST | `/{id}/convert` | 转 docx/md/png/txt |
| POST | `/merge` | 多文档合并 |
| POST | `/{id}/split` | 拆分 |
| POST | `/{id}/pages/rotate` | 旋转页面 |
| POST | `/{id}/pages/delete` | 删除页面 |
| POST | `/{id}/pages/extract` | 提取页面 |
| POST | `/{id}/pages/reorder` | 重排页面 |
| POST | `/{id}/pages/insert-blank` | 插入空白页 |
| POST | `/{id}/pages/crop` | 裁剪页面 |
| POST | `/{id}/watermark` | 添加水印 |
| POST | `/{id}/header-footer` | 添加页眉页脚 |
| POST | `/{id}/compress` | 压缩 PDF |
| GET | `/{id}/form-fields` | **12.1** 表单字段识别 |
| POST | `/{id}/form-fields/fill` | **12.2** 表单填充 |
| POST | `/{id}/signature` | **12.3** 签名图片嵌入 |
| POST | `/{id}/encrypt` | **12.4** 密码加密 |
| POST | `/{id}/decrypt` | **12.4** 密码解密 |
| POST | `/{id}/redact` | **12.4** 密文遮盖 |
| POST | `/{id}/recognize-paddle` | PaddleOCR 识别 |
| POST | `/{id}/search` | 全文搜索 |

### 响应格式

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**分页响应**:
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

**错误响应**:
```json
{
  "code": 400,
  "message": "参数错误",
  "data": null
}
```

---

## 数据库设计

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

### 常用 SQL

```sql
-- 查看所有表
\dt

-- 查看表结构
\d mt_document

-- 查看用户
SELECT id, employee_id, username, real_name, role FROM sys_user;

-- 查看文档
SELECT id, title, doc_key, doc_type, status, owner_id FROM mt_document;

-- 重置管理员密码为 123456（详细步骤见 DEPLOY.md 第 4 步；注意 $ 符号需用 SQL 文件方式避免 shell 转义）
UPDATE sys_user SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm' 
WHERE employee_id = '10000000';

-- 解锁文档
UPDATE mt_document SET status = 'draft', signing_locked = false WHERE id = ?;

-- 重置合同状态
UPDATE mt_contract SET status = 'draft' WHERE id = ?;
```

### 数据库迁移

使用 Flyway 管理，共 26 个迁移脚本（V1-V26），位于：
`miaotongdoc-server/src/main/resources/db/migration/`

**新增迁移**:
```bash
# 文件命名: V{next_version}__description.sql
# 示例: V27__add_new_feature_table.sql
```

---

## 部署指南

### Docker 服务清单（15 个容器）

| 服务 | 容器名 | 端口 | IP | 用途 |
|------|--------|------|-----|------|
| nginx | miaotongdoc-nginx | 80 | 172.20.0.10 | 反向代理 |
| postgres | miaotongdoc-postgres | 5432 | 172.20.0.20 | 数据库 |
| redis | miaotongdoc-redis | 6379 | 172.20.0.30 | 缓存 |
| rabbitmq | miaotongdoc-rabbitmq | 5672/15672 | 172.20.0.35 | 消息队列 |
| web-server | miaotongdoc-server | 9004 | 172.20.0.40 | Spring Boot 后端 |
| editor | miaotongdoc-editor | (内部80) | 172.20.0.50 | MTOffice 主实例 |
| editor2 | miaotongdoc-editor2 | (内部80) | 172.20.0.51 | MTOffice 副实例1 |
| editor3 | miaotongdoc-editor3 | (内部80) | 172.20.0.52 | MTOffice 副实例2 |
| cache-cleaner | miaotongdoc-cache-cleaner | - | - | 定时清理 MTOffice 缓存（cron） |
| logrotate | miaotongdoc-logrotate | - | - | 定时日志轮转（cron） |
| minio | miaotongdoc-minio | 9000/9001 | 172.20.0.60 | 对象存储（单节点） |
| elasticsearch | miaotongdoc-elasticsearch | (内部9200) | 172.20.0.70 | 全文搜索 |
| yjs-server | miaotongdoc-yjs | 1234 | 172.20.0.80 | Yjs 协同服务器 |
| docling | miaotongdoc-docling | 5001 | 172.20.0.90 | AI 文档结构化解析（可选 profile） |
| ocr | miaotongdoc-ocr | 5002 | 172.20.0.95 | OCR 服务（可选 profile） |
| ocr-paddle | miaotongdoc-ocr-paddle | (内部5003) | 172.20.0.96 | PaddleOCR 中文扫描件（可选 profile） |

> **可选 profile**：`docling` / `ocr` / `ocr-paddle` 默认不启动，需用 `docker compose --profile ocr up -d ocr` 等命令启用。完整启用：`--profile all`。
>
> **架构图说明**：nginx 通过 `/editor/*` 路由用 `hash $doc_key$remote_addr consistent` 策略将请求分流到 3 个 editor 实例（按文档 key + IP 哈希保持会话亲和性），参见 [config/nginx/nginx.conf](MiaotongDoc-Docker/config/nginx/nginx.conf)。

### 环境变量配置

环境变量配置文件位于 `MiaotongDoc-Docker/.env`（从 `.env.example` 复制）。

**必填配置**:

| 变量 | 说明 | 最小长度 |
|------|------|----------|
| `DB_PASSWORD` | 数据库密码 | 8 位 |
| `REDIS_PASSWORD` | Redis 密码 | 8 位 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | 8 位 |
| `APP_JWT_SECRET` | 应用 JWT 密钥 | 32 位 |
| `EDITOR_JWT_SECRET` | 编辑器 JWT 密钥 | 32 位 |

**可选配置**:

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `STORAGE_TYPE` | `minio` | 存储类型：`local` 或 `minio` |
| `MINIO_ACCESS_KEY` | - | MinIO 访问密钥 |
| `MINIO_SECRET_KEY` | - | MinIO 秘密密钥 |
| `SSO_ENABLED` | `false` | 是否启用 SSO |
| `LLM_API_URL` | - | LLM API 地址 |
| `LLM_API_KEY` | - | LLM API 密钥 |
| `CORS_ORIGINS` | `https://doc.bank.com` | 允许的跨域来源 |

### 部署命令

```bash
# 启动服务（自动分阶段 A→E）
./deploy.sh start

# 停止服务（⚠️ 会 docker compose down，非 stop）
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看状态
./deploy.sh status

# 健康检查
./deploy.sh health

# 查看日志
./deploy.sh logs

# 构建镜像
./deploy.sh build

# 备份数据
./deploy.sh backup

# 清理 30 天前的日志
./deploy.sh clean-logs
./deploy.sh backup
```

---

## 更新部署包

> 源码修改后，需要重新构建并部署到 Docker 容器。本节说明如何更新各组件的部署包。

### 组件概览

| 组件 | 源码目录 | 部署位置 | 容器名称 |
|------|----------|----------|----------|
| 前端 | `miaotongdoc-web/` | `MiaotongDoc-Docker/app/web/` | `miaotongdoc-nginx` (挂载) |
| 后端 | `miaotongdoc-server/` | `MiaotongDoc-Docker/app/server/` | `miaotongdoc-server` |
| 编辑器 | `MiaotongDoc-Editor/` | `MiaotongDoc-Docker/app/editor/` | `miaotongdoc-editor` |

### 更新前端

> **构建规范**(2026-07-18):
> - **生产部署/CI**:用 `npm run build`(= `vue-tsc && vite build`,含类型检查,内网隔离部署必用)
> - **本地快速调试**:用 `npx vite build`(跳过 vue-tsc,10-20s 出 bundle)
> - **内网部署最佳实践**:外网构建 dist 产物带入内网 Docker 镜像,内网不构建(避免依赖 devDependencies)

```bash
# 1. 进入前端目录
cd miaotongdoc-web

# 2. 安装依赖（如需要,含 devDependencies: typescript/vue-tsc）
npm install

# 3. 构建生产版本(类型检查 + 打包)
npm run build

# 4. 将构建产物复制到 Docker 部署目录
cp -r dist/* ../MiaotongDoc-Docker/app/web/dist/

# 5. 重启 Nginx 容器加载新静态资源
cd ../MiaotongDoc-Docker
docker compose restart nginx
```

**效果**: 用户刷新页面即可看到更新后的前端。

### 更新后端

```bash
# 1. 进入后端目录
cd miaotongdoc-server

# 2. 打包（跳过测试）
mvn clean package -DskipTests

# 3. 复制 JAR 包到 Docker 部署目录
cp target/miaotongdoc.jar ../MiaotongDoc-Docker/app/server/miaotongdoc.jar

# 4. 重启后端容器
cd ../MiaotongDoc-Docker
docker compose restart web-server
```

**效果**: 后端 API 立即使用新版本。

### 更新编辑器（MTOffice）

编辑器的更新需要重新构建 Docker 镜像：

```bash
# 1. 进入编辑器目录
cd MiaotongDoc-Editor

# 2. 构建编辑器镜像
docker build -t miaotongdoc-editor:latest .

# 3. 如果有多个编辑器实例，逐个构建
docker build -t miaotongdoc-editor2:latest .
docker build -t miaotongdoc-editor3:latest .

# 4. 推送到镜像仓库（如需要）
docker tag miaotongdoc-editor:latest your-registry.com/miaotongdoc-editor:latest
docker push your-registry.com/miaotongdoc-editor:latest

# 5. 更新并重启编辑器容器
cd ../MiaotongDoc-Docker
docker compose up -d editor editor2 editor3
```

**注意**: 编辑器镜像构建时间较长（约 5-10 分钟），期间文档编辑功能不可用。

### 一键更新脚本

项目根目录下提供 `update-deploy.sh` 脚本，可一键更新所有组件：

```bash
# 给脚本添加执行权限
chmod +x update-deploy.sh

# 执行更新（可指定组件：frontend, backend, editor, all）
./update-deploy.sh all          # 更新全部
./update-deploy.sh frontend     # 仅更新前端
./update-deploy.sh backend      # 仅更新后端
./update-deploy.sh editor       # 仅更新编辑器
```

### 热更新 vs 冷更新

详见 [开发规范 - 容器热更新 vs 冷更新](#容器热更新-vs-冷更新)

### 数据库迁移

如果代码修改包含数据库结构变更（新增 Flyway 迁移脚本）：

```bash
# 迁移会自动在应用启动时执行
# 也可以手动触发
cd MiaotongDoc-Docker
docker compose restart web-server
docker compose logs -f web-server | grep -i flyway
```

**注意**: 数据库迁移可能导致短暂的服务不可用，建议在低峰期执行。

### 完整部署流程

```bash
# 1. 更新前端
cd miaotongdoc-web && npm run build && cp -r dist/* ../MiaotongDoc-Docker/app/web/dist/

# 2. 更新后端
cd ../miaotongdoc-server && mvn clean package -DskipTests && cp target/miaotongdoc.jar ../MiaotongDoc-Docker/app/server/

# 3. 重启服务
cd ../MiaotongDoc-Docker
docker compose restart nginx web-server

# 4. 验证更新
./deploy.sh health
```

---

## 内网迁移部署

> 详细的从零部署流程、启动顺序、故障排查、已知陷阱见 **[DEPLOY.md](DEPLOY.md)**。

**关键要点**：
- **启动顺序**：基础设施 → RabbitMQ → editor → web-server → yjs+nginx（editor 必须先于 web-server，否则 Flyway V9 因 `task_result` 表不存在而失败）
- **Linux 宿主机**（生产环境）：首次部署先运行 `sudo ./setup-linux-host.sh`，完成内核参数/文件句柄/Docker daemon/防火墙/swap/SSH 加固/自动备份 cron 共 9 项配置
- **Windows 端口陷阱**：yjs 端口 1234 可能被 `winnat` 排除，需 `net stop/start winnat`
- **admin 密码**：初始 `Admin@123` 无效，需重置为 `123456`（见 DEPLOY.md 第 4 步）
- **RabbitMQ**：Windows 下 bind mount 可能遇 cookie 权限问题，改命名卷解决（见 DEPLOY.md 阶段 B）

---

## 开发规范

### 源码目录规范（重要！）

**MTOffice 编辑器相关文件存在两个镜像位置，必须同步**：

| 路径 | 用途 |
|------|------|
| `MiaotongDoc-Editor/` | **源码主目录**（git 跟踪、CI/CD 构建源）|
| `MiaotongDoc-Docker/app/editor/` | Docker 构建 context（被 docker-compose 的 `build.context: ./app/editor` 使用）|

**两个目录的插件文件必须保持完全一致**（MD5 相同）。

**修改顺序（不能错！）**：

1. **先**修改 `MiaotongDoc-Editor/...`（源码主目录）
2. **再**同步到 `MiaotongDoc-Docker/app/editor/...`（Docker 构建目录）
3. （可选）`docker cp` 到运行中的容器做热更新（不重建镜像）

**同步验证**：

```bash
# 验证两个目录 MD5 一致
md5sum MiaotongDoc-Editor/plugins/ai-plugin/scripts/engine/engine.js \
       MiaotongDoc-Docker/app/editor/plugins/ai-plugin/scripts/engine/engine.js
# 两个 MD5 必须相同
```

**常见错误**：只改一处 → 重建镜像时用旧版 → 行为倒退。

### 容器热更新 vs 冷更新

| 方式 | 命令 | 停机 | 适用场景 |
|------|------|------|---------|
| **热更新** | `docker cp` + `nginx -s reload` | 无 | 修改 JS/HTML/CSS 等静态文件 |
| **热更新** | `mvn package` + `docker cp *.jar` + `docker restart` | 秒级 | 修改 Java 后端 |
| **冷更新** | `docker compose build editor` + `up -d` | 5-10 分钟 | 改 Dockerfile、改 docservice 二进制、升级 MTOffice |

**优先用热更新**，只在必须重建时才冷更新。

### 命名约定

**数据库**:
- 表名: `sys_`（系统表）、`mt_`（业务表）
- 字段: `snake_case`
- 主键: `id`（BIGSERIAL）
- 外键: `{table}_id`
- 时间: `created_at`、`updated_at`

**Java**:
- 类名: `PascalCase`
- 方法名: `camelCase`
- 常量: `UPPER_SNAKE_CASE`
- 包名: `com.miaotong.doc.{module}`

**TypeScript**:
- 组件: `PascalCase`
- 文件: `camelCase`
- 变量: `camelCase`
- 常量: `UPPER_SNAKE_CASE`

### 代码风格

**前端 (TypeScript/Vue)**:
- 使用 Composition API (`<script setup>`)
- 样式使用 `<style scoped>`
- 响应式数据使用 `ref` 或 `reactive`
- 组件复用使用 props + emit

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

---

## 调试技巧

### 后端调试

**查看 SQL 日志**:
```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**查看请求日志**:
```yaml
logging:
  level:
    com.miaotong.doc: DEBUG
    org.springframework.web: DEBUG
```

### 前端调试

**Vue DevTools**:
- 安装 Vue DevTools 浏览器扩展
- 查看组件树和状态
- 检查 Pinia Store 数据

**网络请求调试**:
- 打开浏览器开发者工具
- Network 面板查看 API 请求
- 检查请求头和响应

**WebSocket 调试**:
- Network → WS 面板
- 查看消息收发
- 检查连接状态

### 常用命令

**后端命令**:
```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包（跳过测试）
mvn clean package -DskipTests

# 运行应用
mvn spring-boot:run
```

**前端命令**:
```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run type-check

# 代码检查
npm run lint
```

**Docker 命令**:
```bash
# 推荐：使用 deploy.sh 分阶段启动（避免 Flyway V9 失败）
cd Miaotongdoc-Docker
./deploy.sh start          # 自动按 A→E 分阶段
./deploy.sh status         # 查看所有容器状态
./deploy.sh health         # 检查 PostgreSQL/Redis/web-server/MinIO 健康
./deploy.sh logs [service]  # 查看日志（可指定服务名）
./deploy.sh backup         # 备份数据
./deploy.sh clean-logs     # 清理 30 天前的日志
./deploy.sh restart        # 重启所有服务（保留容器数据）

# 底层 docker compose 命令（调试时用，不推荐日常使用）
docker compose up -d                  # ⚠️ 不分阶段,可能 Flyway V9 失败
docker compose stop                  # ✅ 保留容器和数据(推荐)
docker compose down                  # ⚠️ 删除容器,只保留挂载的 host 数据
docker compose logs -f [service]     # 查看日志
docker compose build --no-cache [s]  # 重建镜像
docker compose exec [service] bash   # 进入容器
docker compose ps                     # 查看状态

# 单容器操作
docker compose restart web-server
docker stats                          # 实时内存使用
docker system df                      # 磁盘占用
docker volume ls                      # 命名卷列表

# 数据备份
./deploy.sh backup                    # 一键备份 PostgreSQL + documents + config
```

**Docker Compose 命令修饰符（内存不卡顿的关键）**:
- 容器间网络隔离由 `172.20.0.0/16` 桥接网络 `mtd-net` 提供
- 所有服务通过 `docker compose ps` 中的 `healthy` 状态确认就绪
- 健康检查失败容器会显示 `Restarting (1)`,需查 `docker compose logs <service>` 诊断

---

## 常见问题

### 1. MTOffice 编辑器无法打开文档

**可能原因**:
- MTOffice 容器未启动
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

# 检查文件大小限制（默认 200MB，可在 application.yml 调整）
# Nginx 配置: client_max_body_size 200m
# Tomcat: server.tomcat.max-http-form-post-size: 200MB
# Spring: spring.servlet.multipart.max-file-size: 200MB
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

> **更多部署相关问题**（RabbitMQ 重启、yjs 端口绑定、Flyway 迁移失败、编辑器 nginx 启动失败、管理员登录等）见 [DEPLOY.md - 常见部署问题详解](DEPLOY.md#常见部署问题详解)。

---

## 安全指南

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

### 安全注意事项

**禁止硬编码**:
```java
// ❌ 错误
String secret = "my-secret-key";

// ✅ 正确：从配置读取
@Value("${app.jwt-secret}")
private String jwtSecret;
```

**日志脱敏**:
```java
// ❌ 错误：记录敏感信息
log.info("User password: {}", password);

// ✅ 正确：脱敏处理
log.info("User login attempt: {}", employeeId);
```

**SQL 注入防护**:
```java
// ❌ 错误：字符串拼接
String sql = "SELECT * FROM mt_document WHERE title = '" + title + "'";

// ✅ 正确：使用参数化查询
@Query("SELECT d FROM Document d WHERE d.title = :title")
List<Document> findByTitle(@Param("title") String title);
```

---

## 性能优化

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

### 数据库查询优化

**避免 N+1 查询**:
```java
// ❌ 错误：会产生 N+1 查询
@OneToMany(mappedBy = "document")
private List<DocumentShare> shares;

// ✅ 正确：使用 JOIN FETCH
@Query("SELECT d FROM Document d LEFT JOIN FETCH d.shares WHERE d.id = :id")
Optional<Document> findByIdWithShares(@Param("id") Long id);
```

**分页查询**:
```java
// 使用 Spring Data 分页
Page<Document> findAll(Pageable pageable);

// 前端传递参数
GET /api/documents/list?page=0&size=20&sort=updatedAt,desc
```

---

## 扩展开发

### 添加新功能模块

**后端**:
1. 创建 Entity: `entity/NewEntity.java`
2. 创建 Repository: `repository/NewRepository.java`
3. 创建 Service: `service/NewService.java`
4. 创建 Controller: `controller/NewController.java`
5. 添加数据库迁移: `db/migration/V27__add_new_table.sql`

**前端**:
1. 创建 API 模块: `api/newModule.ts`
2. 创建页面组件: `views/NewPage.vue`
3. 添加路由配置: `router/index.ts`
4. 如需要，创建 Store: `stores/newModule.ts`

### 自定义编辑器插件

编辑器插件位于 `MiaotongDoc-Editor/plugins/`:

```
plugins/
├── signing/          # 签署插件
└── ai/              # AI 插件
```

插件开发参考 MTOffice 官方文档。

### AI 功能扩展

AI 功能通过 `AiProxyService` 代理转发，支持：
- OpenAI 兼容格式
- SSE 流式响应
- 自定义模型

配置 AI 服务地址和密钥即可使用。

---

## 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| **内网部署指南** | `DEPLOY.md` | **从零部署流程、启动顺序、故障排查、已知陷阱** |
| 产品功能规划 | `FEATURE_DESIGN.md` | 7 个模块、页面清单、优先级排序 |
| 系统架构文档 | `MiaotongDoc-Architecture.md` | 架构图、Docker 服务清单、问题排查 |
| 系统审查报告 | `SYSTEM_AUDIT.md` | 已开发功能、43 个问题、修复计划 |
| Docker 部署文档 | `MiaotongDoc-Docker/README.md` | 详细部署指南 |
| 编辑器说明 | `MiaotongDoc-Editor/README.md` | 编辑器镜像构建说明 |
| AI 插件变更日志 | `MiaotongDoc-AI/CHANGELOG.md` | AI 插件版本记录 |

---

## 快速参考卡片

### 常用端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发 | 3000 | Vite 开发服务器 |
| 后端 API | 9004 | Spring Boot |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Elasticsearch | 9200 | 全文搜索 |
| RabbitMQ | 5672/15672 | 消息队列/管理界面 |
| MinIO | 9000/9001 | 对象存储/控制台 |
| Yjs Server | 1234 | 协同编辑 |
| Docling | 5001 | AI 文档解析（可选） |
| OCR | 5002 | OCR 服务（可选） |
| Nginx | 80 | 反向代理 |

### 关键配置文件

| 文件 | 用途 |
|------|------|
| `MiaotongDoc-Docker/.env` | 环境变量配置 |
| `miaotongdoc-server/src/main/resources/application.yml` | 后端配置 |
| `miaotongdoc-web/vite.config.ts` | 前端配置 |
| `MiaotongDoc-Docker/docker-compose.yml` | Docker 编排 |
| `MiaotongDoc-Docker/config/nginx/nginx.conf` | Nginx 配置 |

### 状态码速查

**HTTP 状态码**:
- 200: 成功
- 400: 参数错误
- 401: 未认证（JWT 无效或过期）
- 403: 权限不足
- 404: 资源不存在
- 500: 服务器内部错误

**文档状态**:
- `draft`: 草稿
- `signing`: 签署中
- `signed`: 已签署
- `expired`: 已过期

**合同状态**:
- `draft`: 草稿
- `pending_approval`: 审批中
- `approved`: 已批准
- `rejected`: 已拒绝
- `expired`: 已过期

---

*本文档由 Claude Code 维护，旨在帮助开发者和 AI Agent 快速了解项目结构和功能。*
