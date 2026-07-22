# MiaotongDoc 开发参考手册

> **版本**: v1.2.0 | **维护者**: Claude Code
> **项目**: 企业级多格式在线文档协作与签署平台
> **最后更新**: 2026年7月21日

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
- [工程标准](#工程标准)
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
│       ├── components/             # 组件 (43个)
│       ├── composables/            # 组合式函数 (12个: 1个通用 + 11个PDF)
│       ├── router/                 # 路由配置
│       ├── stores/                 # 状态管理 (4个)
│       ├── styles/                 # 样式 (1个CSS)
│       ├── types/                  # 类型声明 (2个)
│       ├── utils/                  # 工具函数 (8个)
│       └── views/                  # 页面 (8个)
│
├── miaotongdoc-server/             # Spring Boot 后端
│   └── src/main/java/com/miaotong/doc/
│       ├── config/                 # 配置类 (15个)
│       ├── controller/             # 控制器 (29个)
│       ├── entity/                 # 实体类 (26个)
│       ├── repository/             # 数据仓库 (25个)
│       ├── service/                # 业务服务 (38个: 30个根级 + 5个AI + 3个存储)
│       ├── websocket/              # WebSocket (3个)
│       ├── dto/                    # 数据传输对象 (21个)
│       ├── exception/              # 异常处理 (3个)
│       ├── util/                   # 工具类 (6个)
│       ├── scheduler/              # 定时任务 (2个)
│       ├── event/                  # 事件 (1个)
│       └── constants/              # 常量 (1个)
│
├── MiaotongDoc-Docker/             # Docker 部署
│   ├── docker-compose.yml          # 16 个服务编排
│   ├── deploy.sh                   # 分阶段启动脚本（A→E）
│   └── app/                        # 构建产物 (9个目录)
│
├── MiaotongDoc-Editor/             # MTOffice 定制
│   ├── Dockerfile
│   ├── branding/                   # 品牌 Logo (5个SVG)
│   ├── fonts/                      # 中文字体 (22个)
│   └── plugins/                    # 插件 (652个文件, 含ai-plugin + signing)
│
├── MiaotongDoc-AI/                 # AI 插件 (651个文件, Editor ai-plugin 镜像)
├── CLAUDE.md                       # 本文件 (开发参考手册)
├── DEPLOY.md                       # 详细内网部署指南
├── SYSTEM_AUDIT.md                 # 系统审查报告
├── CHANGELOG.md                    # 变更日志
└── CLAUDE.md                       # 本文件 (开发参考手册)
```

### 前端关键文件

| 文件 | 用途 |
|------|------|
| `src/api/index.ts` | Axios 实例配置 |
| `src/router/index.ts` | 路由配置 |
| `src/stores/user.ts` | 用户状态管理 |
| `src/views/Home.vue` | 主页（核心页面） |
| `src/views/DocEditor.vue` | 文档编辑页 |
| `src/views/Admin.vue` | 管理后台 |
| `src/views/Login.vue` | 登录页 |
| `src/views/SigningTask.vue` | 签署任务列表 |
| `src/views/ContractList.vue` | 合同列表 |
| `src/views/ContractDetail.vue` | 合同详情 |
| `src/views/ActivityFeed.vue` | 协作动态 |
| `src/components/MarkdownEditor.vue` | Markdown 编辑器 (Tiptap) |
| `src/components/DocumentEditor.vue` | MTOffice 编辑器 |
| `src/components/CommentPanel.vue` | 评论面板 |
| `src/components/ShareDialog.vue` | 共享对话框 |
| `src/components/VersionHistory.vue` | 版本历史 |
| `src/components/SigningBar.vue` | 签署工具栏 |
| `src/components/SigningDialog.vue` | 签署对话框 |
| `src/components/ContractCreateDialog.vue` | 合同创建对话框 |
| `src/components/ContractSubmitDialog.vue` | 合同提交审批对话框 |
| `src/components/AiPanel.vue` | AI 聊天面板 |
| `src/components/NotificationBell.vue` | 通知铃铛 |
| `src/components/CollaborationBar.vue` | 协作用户栏 |
| `src/components/MentionInput.vue` | @提及输入框 |
| `src/components/DocCard.vue` | 文档卡片 |
| `src/components/CreateDocDialog.vue` | 创建文档对话框 |
| `src/components/MergeDialog.vue` | 文档合并对话框 |
| **PDF 编辑器 V3** (Phase 7-13) |
| `src/components/PdfEditor.vue` | PDF 编辑器 V3 主壳 |
| `src/components/PdfRibbon.vue` | PDF 多 tab 顶栏 |
| `src/components/PdfThumbPanel.vue` | PDF 缩略图侧栏 (2x 高清+懒加载+拖拽) |
| `src/components/PdfCanvas.vue` | PDF 单页渲染容器 (4 层堆叠) |
| `src/components/PdfToolsRail.vue` | PDF 右侧快捷工具栏 |
| `src/components/PdfRightPanel.vue` | PDF 右侧任务面板 (大纲/搜索/批注/表单/信息) |
| `src/components/PdfFloatingToolbar.vue` | PDF 浮动工具栏 |
| `src/components/PdfPageOpsDialog.vue` | PDF 页面操作 (插入/裁剪/水印/页眉页脚) |
| `src/components/PdfSignatureDialog.vue` | PDF 签名创建 (键入/绘制/上传) |
| `src/components/PdfSecurityDialog.vue` | PDF 保护 (加密/解密) |
| `src/components/PdfAiFloatPanel.vue` | PDF AI 浮动面板 |
| `src/components/PdfOcrLayer.vue` | PDF OCR 识别层 |
| `src/components/PdfTextEditorLayer.vue` | PDF 文本编辑层 |
| `src/components/PdfCanvasContextMenu.vue` | PDF 画布右键菜单 |
| `src/components/PdfSaveModeDialog.vue` | PDF 保存模式对话框 |
| `src/components/PdfCreateDialog.vue` | PDF 创建对话框 |
| `src/components/PdfPageOpsMenu.vue` | PDF 页面操作菜单 |
| `src/components/PdfExportMenu.vue` | PDF 导出菜单 |
| `src/components/PdfAiMenu.vue` | PDF AI 菜单 |
| `src/components/PdfThumbnailContextMenu.vue` | PDF 缩略图右键菜单 |
| `src/components/PdfIcon.vue` | PDF 图标组件 |
| `src/components/PdfTermsPanel.vue` | PDF 术语面板 |
| `src/components/PdfToolbar.vue` | PDF 工具栏 |
| `src/composables/useAiChat.ts` | AI 聊天组合式函数 |
| `src/composables/pdf/usePdfRenderer.ts` | PDF.js 渲染封装 (worker 本地化+cMap) |
| `src/composables/pdf/usePdfAnnotation.ts` | PDF 标注 |
| `src/composables/pdf/usePdfTextEditor.ts` | PDF 文本编辑 |
| `src/composables/pdf/usePdfCollaborate.ts` | PDF 协同 |
| `src/composables/pdf/usePdfViewMode.ts` | PDF 视图模式 |
| `src/composables/pdf/usePdfAiVision.ts` | PDF AI 视觉 |
| `src/composables/pdf/usePdfAiFloat.ts` | PDF AI 浮动 |
| `src/composables/pdf/usePdfExtractTerms.ts` | PDF 术语提取 |
| `src/composables/pdf/usePdfOcrProgress.ts` | PDF OCR 进度 |
| `src/composables/pdf/usePdfOptimizeOcr.ts` | PDF OCR 优化 |
| `src/composables/pdf/usePdfPageOps.ts` | PDF 页面操作 |

### 后端关键文件

| 文件 | 用途 |
|------|------|
| `config/SecurityConfig.java` | Spring Security 安全配置 |
| `config/JwtAuthFilter.java` | JWT 认证过滤器 |
| `config/CorsConfig.java` | CORS 跨域配置 |
| `config/WebSocketConfig.java` | WebSocket 配置 |
| `config/StorageConfig.java` | 存储配置 (MinIO/Local) |
| `config/RedisConfig.java` | Redis 缓存配置 |
| `controller/DocumentController.java` | 文档管理 API |
| `controller/AuthController.java` | 用户认证 API |
| `controller/AdminController.java` | 管理后台 API |
| `controller/PdfController.java` | PDF 工具 API |
| `controller/SigningController.java` | 电子签署 API |
| `controller/ContractController.java` | 合同管理 API |
| `controller/ShareController.java` | 文档共享 API |
| `controller/CommentController.java` | 评论协作 API |
| `controller/AiProxyController.java` | AI 代理 API |
| `controller/AiChatSseController.java` | AI 聊天 SSE 流式 API |
| `controller/FolderController.java` | 文件夹管理 API |
| `controller/VersionController.java` | 版本管理 API |
| `controller/NotificationController.java` | 通知 API |
| `controller/AuditController.java` | 审计日志 API |
| `controller/SsoController.java` | SSO 单点登录 API |
| `controller/WatermarkController.java` | 水印配置 API |
| `controller/TemplateController.java` | 模板管理 API |
| `controller/FolderTemplateController.java` | 文件夹模板 API |
| `controller/PdfExtractTermsSseController.java` | PDF 术语提取 SSE |
| `controller/PdfOptimizeOcrSseController.java` | PDF OCR 优化 SSE |
| `controller/PdfVisionSseController.java` | PDF 视觉分析 SSE |
| `service/DocumentService.java` | 文档业务逻辑 |
| `service/AiService.java` | AI 业务逻辑 (service/ai/) |
| `service/PdfToolService.java` | PDF 工具服务 |
| `service/storage/StorageService.java` | 存储接口抽象 |
| `service/storage/MinioStorageService.java` | MinIO 存储实现 |
| `service/storage/FileSystemStorageService.java` | 本地文件系统存储实现 |
| `service/UserService.java` | 用户业务逻辑 |
| `service/PresenceService.java` | 在线状态服务 |
| `service/ShareService.java` | 共享权限服务 |
| `service/SigningService.java` | 签署服务 |
| `service/ContractService.java` | 合同服务 |
| `service/ContractParser.java` | 合同 AI 解析 |
| `service/DoclingService.java` | Docling 文档解析 |
| `service/OcrService.java` | OCR 服务 |
| `service/PaddleOcrClient.java` | PaddleOCR 客户端 |
| `util/JwtUtil.java` | JWT 工具类 |
| `util/EditorJwtUtil.java` | 编辑器 JWT 工具类 |
| `util/FileHashUtil.java` | 文件哈希工具 |
| `util/FileValidator.java` | 文件校验工具 |
| `exception/GlobalExceptionHandler.java` | 全局异常处理 |
| `exception/BusinessException.java` | 业务异常 |
| `scheduler/ContractExpirationScheduler.java` | 合同过期定时任务 |
| `scheduler/OnlyOfficeCleanupScheduler.java` | 编辑器缓存清理定时任务 |

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
| **PDF 编辑器 V3** | Ribbon/缩略图/ToolsRail/标注/页面操作/OCR/表单/签名/加密/创建 | `/api/pdf` |

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
| POST | `/{id}/upload-image` | 上传图片到文档 |
| POST | `/{id}/mention` | @提及用户 |
| GET | `/list` | 文档列表（支持分页、排序、筛选） |
| GET | `/{id}` | 文档详情 |
| GET | `/{id}/config` | MTOffice 编辑器配置 |
| GET | `/{id}/file` | 下载文件 |
| GET | `/file/**` | 文件直链服务 |
| GET | `/{id}/export/pdf` | 导出 PDF |
| PUT | `/{id}/rename` | 重命名 |
| DELETE | `/{id}` | 软删除（移入回收站） |
| DELETE | `/batch` | 批量删除 |
| POST | `/{id}/move` | 移动到文件夹 |
| POST | `/{id}/versions` | 创建版本 |
| PUT | `/{id}/star` | 收藏/取消收藏 |
| PUT | `/{id}/restore` | 恢复指定版本 |
| GET | `/trash` | 回收站列表 |
| POST | `/{id}/restore` | 从回收站恢复 |
| DELETE | `/{id}/permanent` | 永久删除 |
| DELETE | `/trash/empty` | 清空回收站 |
| POST | `/export/zip` | 批量导出 ZIP |
| POST | `/reindex` | 重建索引 |
| GET | `/suggest` | 搜索建议 |

### 文件夹模块 (`/api/folders`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 文件夹列表（树形结构） |
| POST | `/` | 创建文件夹 |
| PUT | `/{id}` | 更新文件夹（名称、颜色） |
| DELETE | `/{id}` | 删除文件夹 |
| PUT | `/reorder` | 文件夹排序 |
| GET | `/{id}/download` | 下载文件夹内所有文档 |

### 版本管理模块 (`/api/versions`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{docId}` | 版本列表 |
| GET | `/{docId}/{versionNumber}` | 版本详情 |
| GET | `/{docId}/{versionNumber}/download` | 下载指定版本 |
| GET | `/{docId}/{versionNumber}/preview` | 预览指定版本 |
| POST | `/{docId}/{versionNumber}/restore` | 恢复到指定版本 |

### 评论模块 (`/api/comments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建评论 |
| GET | `/document/{docId}` | 获取文档评论列表 |
| PUT | `/{id}/resolve` | 解决评论 |
| DELETE | `/{id}` | 删除评论 |

### 通知模块 (`/api/notifications`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 通知列表 |
| PUT | `/{id}/read` | 标记已读 |
| PUT | `/read-all` | 全部标记已读 |
| GET | `/unread-count` | 未读计数 |

### 部门模块 (`/api/departments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 部门列表 |
| GET | `/tree` | 部门树形结构 |
| GET | `/{id}/children` | 子部门列表 |
| POST | `/` | 创建部门 |
| PUT | `/{id}` | 更新部门 |
| DELETE | `/{id}` | 删除部门 |

### 在线状态模块 (`/api/presence`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/document/{docId}` | 获取文档在线用户 |
| POST | `/document/{docId}/join` | 加入文档 |
| POST | `/document/{docId}/leave` | 离开文档 |
| POST | `/document/{docId}/heartbeat` | 心跳维持 |

### 协作动态模块 (`/api/activities`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/feed` | 协作动态流 |
| GET | `/document/{docId}` | 文档协作历史 |

### Markdown 模块 (`/api/markdown`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{id}/content` | 获取 Markdown 内容 |
| POST | `/{id}/save` | 保存 Markdown 内容 |

### 模板模块 (`/api/templates`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 模板列表 |
| GET | `/{id}` | 模板详情 |
| POST | `/` | 创建模板 |
| PUT | `/{id}` | 更新模板 |
| DELETE | `/{id}` | 删除模板 |
| GET | `/{id}/download` | 下载模板 |
| GET | `/categories` | 模板分类列表 |
| POST | `/categories` | 创建模板分类 |
| DELETE | `/categories/{id}` | 删除模板分类 |

### 文件夹模板模块 (`/api/folder-templates`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 文件夹模板列表 |
| GET | `/{id}` | 文件夹模板详情 |
| POST | `/` | 创建文件夹模板 |
| PUT | `/{id}` | 更新文件夹模板 |
| DELETE | `/{id}` | 删除文件夹模板 |
| PUT | `/reorder` | 文件夹模板排序 |

### 水印模块 (`/api/watermark`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/config` | 获取水印配置 |
| PUT | `/config` | 更新水印配置 |
| GET | `/preview` | 预览水印效果 |

### 审计日志模块 (`/api/audit`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/document/{docId}` | 文档审计日志 |
| GET | `/me` | 我的操作记录 |
| GET | `/all` | 全部审计日志（管理员） |

### 编辑器回调模块 (`/api/callback`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/editor` | MTOffice 编辑器回调 |

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

### SSO 模块 (`/api/sso`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/providers` | 获取 SSO 提供商列表 |
| GET | `/callback` | SSO 回调处理 |
| POST | `/logout` | SSO 登出 |

### AI 模块 (`/api/ai`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/proxy` | LLM API 代理（通用） |
| POST | `/refresh-models` | 刷新模型列表 |
| GET | `/config` | AI 配置 |
| GET | `/settings` | AI 设置 |
| GET | `/test` | AI 连接测试 |
| POST | `/test/chat` | AI 聊天测试（SSE） |
| POST | `/chat/stream` | AI 聊天（SSE 流式） |

### AI 文档模块 (`/api/documents/{id}/ai`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat-stream` | 文档 AI 聊天（SSE 流式） |
| POST | `/chat` | 文档 AI 聊天 |
| POST | `/summarize` | 文档摘要 |
| POST | `/translate` | 文档翻译 |
| POST | `/rewrite` | 文档改写 |
| POST | `/generate` | 文档生成 |
| POST | `/generate-stream` | 文档生成（SSE 流式） |

### AI 提供商管理 (`/api/admin/ai/providers`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 提供商列表 |
| GET | `/{id}` | 提供商详情 |
| GET | `/{id}/reveal-key` | 查看密钥 |
| POST | `/` | 创建提供商 |
| PUT | `/{id}` | 更新提供商 |
| DELETE | `/{id}` | 删除提供商 |
| POST | `/{id}/set-default` | 设为默认 |
| POST | `/refresh` | 刷新配置 |
| POST | `/fetch-models` | 拉取模型列表 |
| POST | `/test-connection` | 测试连接 |

### PDF 工具模块 (`/api/pdf`)

| 方法 | 路径 | 说明 |
|------|------|------|
| **PDF 创建** | | |
| POST | `/create/blank` | **13.1** 创建空白 PDF |
| POST | `/create/from-images` | **13.2** 从图片创建 PDF |
| **PDF 信息** | | |
| GET | `/{id}/info` | PDF 基本信息(页数/尺寸/版本) |
| GET | `/{id}/metadata` | PDF 元数据(标题/作者/创建时间) |
| GET | `/{id}/outline` | PDF 大纲/书签结构 |
| GET | `/{id}/text` | 全文提取 |
| GET | `/{id}/pages/{pageNum}/text` | 单页文本 |
| GET | `/{id}/markdown` | 导出为 Markdown |
| GET | `/{id}/text-positions` | 文本位置信息 |
| GET | `/{id}/recognize-status` | OCR 识别状态查询 |
| **PDF 编辑** | | |
| POST | `/{id}/text-edits` | 保存文本编辑 |
| GET | `/{id}/text-edits` | 获取文本编辑记录 |
| POST | `/{id}/export-edited` | 导出编辑后的 PDF |
| POST | `/{id}/save-as-new` | 另存为新文档 |
| POST | `/{id}/convert` | 转 docx/md/png/txt |
| **页面操作** | | |
| POST | `/merge` | 多文档合并 |
| POST | `/{id}/split` | 拆分 |
| POST | `/{id}/split-by-ranges` | 按页码范围拆分 |
| POST | `/{id}/pages/rotate` | 旋转页面 |
| DELETE | `/{id}/pages/{pageNum}` | 删除单页 |
| POST | `/{id}/pages/extract` | 提取页面 |
| POST | `/{id}/extract-pages-batch` | 批量提取页面 |
| POST | `/{id}/pages/reorder` | 重排页面 |
| POST | `/{id}/pages/insert-blank` | 插入空白页 |
| POST | `/{id}/pages/crop` | 裁剪页面 |
| **PDF 安全** | | |
| POST | `/{id}/watermark` | 添加水印 |
| POST | `/{id}/header-footer` | 添加页眉页脚 |
| POST | `/{id}/encrypt` | 密码加密 |
| POST | `/{id}/decrypt` | 密码解密 |
| POST | `/{id}/redact` | 密文遮盖 |
| **PDF 表单与签名** | | |
| GET | `/{id}/form-fields` | 表单字段识别 |
| POST | `/{id}/form-fields/fill` | 表单填充 |
| POST | `/{id}/signature` | 签名图片嵌入 |
| **OCR 识别** | | |
| POST | `/{id}/recognize` | OCR 识别 |
| POST | `/{id}/recognize-paddle` | PaddleOCR 识别 |
| POST | `/{id}/recognize-old` | OCR 旧版接口 |
| **搜索** | | |
| GET | `/{id}/search` | 全文搜索 |
| POST | `/{id}/search` | 全文搜索 (POST) |
| **压缩** | | |
| POST | `/{id}/compress` | 压缩 PDF |

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
| `mt_document_index` | 文档全文索引表 |
| `mt_document_template` | 文档模板表 |
| `mt_folder` | 文件夹表（无限层级、颜色标记） |
| `mt_folder_template` | 文件夹模板关联表 |
| `mt_template_category` | 模板分类表 |
| `mt_watermark_config` | 水印配置表 |
| `mt_pdf_task` | PDF 任务表（OCR/识别状态跟踪） |

#### 评论与协作表

| 表名 | 用途 |
|------|------|
| `mt_comment` | 评论批注表（支持回复、引用、解决） |
| `mt_mention` | @提及关联表 |
| `mt_activity` | 协作动态表 |
| `mt_notification` | 通知表 |

#### AI 功能表

| 表名 | 用途 |
|------|------|
| `mt_ai_provider` | AI 提供商配置表（模型地址、密钥） |

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

### Docker 服务清单（16 个容器）

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

## 工程标准

> 本文定义了从需求到交付的完整开发生命周期，是 Claude Code 每次开发调用的执行标准。
> 核心理念借鉴自：Google Engineering Practices、Amazon Working Backwards、Kent Beck TDD、Martin Fowler Refactoring、Michael Nygard ADR、OWASP、Clean Architecture。

### 0. 核心原则

| # | 原则 | 说明 | 来源 |
|---|------|------|------|
| 1 | **文档先行** | 先写设计文档，再写代码。代码是设计的实现，不是设计本身 | Google Design Docs, Amazon Working Backwards |
| 2 | **小步提交** | 每次改动只解决一个问题，提交粒度要小（< 200 行）。小步快跑，频繁集成 | Google Small CLs, Trunk-Based Development |
| 3 | **测试代言** | 业务逻辑必须有测试保护。无测试的代码等于"不知道能不能跑" | Kent Beck TDD |
| 4 | **架构可见** | 每个架构决策都要记录上下文和理由。不写 ADR 等于没做决策 | Michael Nygard ADR |
| 5 | **安全内置** | 安全不是事后补的，是设计时就考虑进去的。参数化查询、权限校验、日志脱敏是默认行为，不是加分项 | OWASP, Microsoft SDL |
| 6 | **验证闭环** | 每项功能必须经过"计划→实现→验证→归档"的完整闭环，缺失任何一环都不算完成 | Scrum Definition of Done |

### 1. 角色与职责

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             角色分工矩阵                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                    │              │                             │
│  你 (Human)                        │  Claude (AI)  │  共同                      │
│  ───────────────────               │  ──────────   │  ──────                    │
│  • 提出需求 / 定义问题              │  • 方案设计     │  • 需求澄清                │
│  • 评审并批准计划                   │  • 代码实现     │  • 代码审查                │
│  • 验收功能 / 确认效果              │  • 编写文档     │  • 架构决策                │
│  • 提供业务领域知识                 │  • 进度跟踪     │  • 安全审计                │
│  • 部署上线决策                     │  • 代码审查     │  • 测试策略                │
│                                    │  • 功能验证     │                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2. 开发六阶段

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                       开发六阶段 + 质量门禁                                    │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ① 需求分析 ──→ ② 计划设计 ──→ ③ 代码实现 ──→ ④ 代码审查 ──→ ⑤ 验证 ──→ ⑥ 归档 │
│      │              │              │              │            │          │    │
│      门禁1          门禁2          门禁3          门禁4        门禁5      门禁6 │
│     需求确认        计划批准        实现自检        审查通过    验证通过   归档完成│
│                                                                              │
│  ←←←←←←←←←←←←←←←←←← 迭代反馈循环（验证失败则回退到对应阶段） →→→→→→→→→→→→→→→→→│
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

#### 阶段 ①：需求分析 — "理解问题"

**目标**：在动手之前，彻底理解要解决的问题是什么、为什么、影响谁。

**做法**：

| 步 | 动作 | 输出 |
|----|------|------|
| 1.1 | **阅读上下文**：扫一遍相关代码、配置、API、现有文档，理解当前状态 | 已知信息清单 |
| 1.2 | **影响范围分析**：该改动会动到哪些层？用[变更影响矩阵](#变更影响矩阵)判断 | 影响范围列表 |
| 1.3 | **逆向思考**：如果这个功能做错了，最坏的结果是什么？边界在哪里？ | 边界条件清单 |
| 1.4 | **记录决策**：非显而易见的业务逻辑结论，用 `/memory` 持久化 | 记忆文件 |

**输出文档**：需求分析摘要（2-5 句话 + 受影响文件列表）

**质量门禁 G1 — 需求确认**：
- [ ] 需求是否清晰无歧义？
- [ ] 影响范围是否已穷举？
- [ ] 是否有未澄清的假设需要用户确认？

> **复杂度评估**：需求分析结束后，判断任务复杂度，决定是否进入计划阶段：
> | 等级 | 判断标准 | 是否要计划 |
> |------|---------|-----------|
> | 🟢 简单 | 单文件、≤10 行改动、纯 bugfix | 跳过计划，直接编码 |
> | 🟡 中等 | 2-5 文件、涉及逻辑修改 | 口头计划（EnterPlanMode）或短文计划 |
> | 🔴 复杂 | 多文件、新功能、架构变更、数据库迁移 | 必须写正式计划文档 |

---

#### 阶段 ②：计划设计 — "设计方案"

**目标**：编码前将方案想清楚，用文档固化，避免"边写边想"导致返工。

**做法**：

| 步 | 动作 | 输出 |
|----|------|------|
| 2.1 | **方案拟定**：设计 1-2 种技术方案，对比优劣，给出推荐 | 方案对比 |
| 2.2 | **架构决策记录**：如果涉及架构级选择，写 ADR（见[ADR 模板](#架构决策记录-adr)） | 追加到 CLAUDE.md |
| 2.3 | **BDD 场景**：用 Given/When/Then 描述关键业务场景 | 场景清单 |
| 2.4 | **编写计划文档**：按模板追加到 CLAUDE.md 末尾 | 计划文档 |
| 2.5 | **更新计划索引**：在 CLAUDE.md 末尾的计划列表中登记 | 索引更新 |

**计划文档结构**（正式计划）：

```
📋 需求            — 一句话说清做什么
🎯 目标            — 可衡量的完成标准
🔧 方案            — 技术方案 + 关键决策理由
📁 涉及文件         — 所有改动的文件清单（增/删/改）
📝 实现步骤         — 可操作的分步计划（可勾选）
🧪 测试策略         — 单元测试 / 集成测试 / 手动测试
⚠️ 风险与回退       — 潜在风险 + 回退方案
✅ 验证标准         — 如何验证功能正确（与 G5 对应）
```

**质量门禁 G2 — 计划批准**：
- [ ] 方案是否覆盖了所有需求？
- [ ] 边界条件是否已考虑？
- [ ] 风险是否有应对方案？
- [ ] 验证标准是否可操作？

---

#### 阶段 ③：代码实现 — "写出代码"

**目标**：遵循架构约束，写出可读、可维护、安全的代码。

**做法**：

**3.1 编码纪律**

| 原则 | 具体要求 |
|------|---------|
| **单一职责** | 一个函数/类只做一件事。如果做不到，说明需要拆 | 参考：SRP |
| **开闭原则** | 对扩展开放，对修改关闭。新增功能尽量不修改现有代码 | 参考：OCP |
| **最少知识** | 不链式调用超过 2 层（`a.b.c()` 即坏味道） | 参考：Law of Demeter |
| **防御编程** | 所有外部输入必须校验，所有 null 必须处理 | 参考：Defensive Programming |
| **失败快速** | 错误尽早暴露，不吞异常 | 参考：Fail Fast |
| **DRY** | 同样的逻辑只写一次。三次重复即需抽象 | 参考：DRY |

**3.2 分层规范**

```
后端分层（不可跳过层）：
  Controller → Service → Repository
      ↑           ↑          ↑
  参数校验     业务逻辑    数据访问

前端分层（不可跳过层）：
  View/Component → API/Store → Axios
      ↑               ↑          ↑
  页面渲染         状态管理      HTTP
```

**3.3 安全编码（默认行为）**

| 检查项 | 说明 |
|--------|------|
| ✅ SQL 注入防护 | 必须用参数化查询，禁止字符串拼接 |
| ✅ XSS 防护 | 所有用户输入输出必须转义 |
| ✅ 权限校验 | 每个 API 接口都必须校验操作权限，不依赖前端隐藏 |
| ✅ 日志脱敏 | 密码、Token、身份证号等敏感信息禁止打印 |
| ✅ 输入校验 | 所有外部输入做类型、长度、格式校验 |

**3.4 进度跟踪**

- 编码开始时用 `TodoWrite` 创建任务列表
- 每完成一步，更新状态：`pending` → `in_progress` → `completed`
- 遇到阻塞时，记录问题并请求用户确认

**示例**：
```
[✅] 创建数据库迁移脚本 V27__add_signing_table.sql
[🔄] 实现后端签署 API (SigningController)
[⏳] 实现前端签署页面组件
[⏳] 集成测试与验证
```

**质量门禁 G3 — 实现自检**：
- [ ] 代码是否符合分层架构？（没有跳过层）
- [ ] 是否遵循了安全编码规范？（SQL/XSS/权限）
- [ ] 是否处理了 null 和异常路径？
- [ ] 日志是否合理（不缺失、不冗余、不泄露敏感信息）？
- [ ] 是否有未使用的导入、变量、死代码？

---

#### 阶段 ④：代码审查 — "检查代码"

**目标**：用审查者的视角重新审视代码，发现逻辑缺陷、安全隐患、可维护性问题。

**做法**：

**4.1 自审查清单**（每次改动后执行）：

| 维度 | 检查项 |
|------|--------|
| **正确性** | 逻辑是否覆盖了所有分支？边界条件是否处理？ |
| **安全性** | 是否有 SQL 注入风险？权限校验是否遗漏？有无敏感信息泄露？ |
| **可读性** | 命名是否清晰？是否需要注释来解释"为什么"而不是"是什么"？ |
| **简洁性** | 是否有重复代码？是否有过度设计？是否有可简化的逻辑？ |
| **健壮性** | 空值、越界、异常输入是否处理？错误提示是否友好？ |
| **兼容性** | 是否破坏了向后兼容？是否需要版本迁移？ |

**4.2 审查流程**：

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 自审查    │ ──→ │ /code-   │ ──→ │ 修复审查  │ ──→ │ 最终确认  │
│ (清单)    │     │ review   │     │ 发现的问题 │     │          │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
```

**质量门禁 G4 — 审查通过**：
- [ ] 自审查清单所有项通过
- [ ] `/code-review` 报告无 P0/P1 级别问题
- [ ] 所有审查发现的问题已修复或确认接受

---

#### 阶段 ⑤：功能验证 — "证明它能跑"

**目标**：用实际证据证明功能按预期工作，不只是"看起来没问题"。

**做法**：

**5.1 验证金字塔**

```
        ╱╲
       ╱  ╲         手动验证（UI 效果、业务流程）
      ╱    ╲        ─── 用户最终确认
     ╱──────╲
    ╱        ╲     集成验证（API 请求/响应、数据库状态）
   ╱          ╲    ─── Claude 自动执行
  ╱────────────╲
 ╱              ╲  代码级验证（逻辑正确性、边界条件、错误处理）
╱                ╲ ─── Claude 自动执行
```

**5.2 验证流程**：

| 步 | 动作 | 谁做 |
|----|------|------|
| 5.1 | **代码级验证**：逐行审查关键逻辑，确认边界条件和错误处理正确 | Claude |
| 5.2 | **集成验证**：调用真实 API，检查请求/响应、数据库状态变更 | Claude |
| 5.3 | **回归验证**：检查改动是否影响相关已有功能 | Claude |
| 5.4 | **用户验收**：展示效果，确认是否符合预期 | 你 |

**5.3 验证内容**：

```
功能验证清单（对照计划阶段定义的验证标准逐项检查）：
  [ ] 功能是否按预期工作
  [ ] 边界条件是否已处理（空值、超长、特殊字符、并发）
  [ ] 错误场景是否优雅降级（友好提示，不崩溃）
  [ ] 权限校验是否正确（不同角色访问结果符合预期）
  [ ] 数据一致性（数据库状态变更正确，无脏数据）
  [ ] 性能是否可接受（响应时间无明显劣化）
  [ ] 用户界面是否友好（布局、提示、交互反馈）
```

**5.4 验证记录**：在计划文档末尾更新 `✅ 验证结果` 表格

**质量门禁 G5 — 验证通过**：
- [ ] 所有验证项通过
- [ ] 回归验证无副作用
- [ ] 用户已确认验收

---

#### 阶段 ⑥：文档归档 — "让知识留下"

**目标**：确保这次改动的知识不会丢失，下一个开发者（包括未来 3 个月的你自己）能理解为什么这么做。

**做法**：

**6.1 文档同步**（对照清单检查）：

| 文档 | 是否需要更新 | 判断标准 |
|------|------------|---------|
| `CLAUDE.md` | 新增关键文件/API/配置/功能时 | 如果项目结构发生变化 |
| 计划文档 | 每次开发 | 追加到 CLAUDE.md 末尾 |
| 架构决策 | 有架构级选择时 | 追加到 CLAUDE.md 末尾 |
| `README.md` | 对外接口或使用方式变化时 | 如果用户能看到变化 |
| 记忆文件 | 业务逻辑结论非显而易见时 | 如果不记下来下次会忘 |

**6.2 提交规范**：

```
格式：类型(模块): 一句话描述

类型: feat / fix / refactor / test / docs / style / chore / perf / security

内容要求：
  1. 第一行：类型 + 范围 + 一句话
  2. 空一行
  3. 正文：列出关键变更点（每行一个）
  4. 空一行
  5. 引用：Plan / ADR / Issue 引用

示例：
feat(signing): 添加电子签署功能

- 实现签署任务的创建/确认/拒绝/取消全流程
- 新增 mt_signing_task 和 mt_signing_record 表
- 签署完成后文档状态自动变更为 signed
- 签署记录包含签署人 IP、UA、文档哈希校验

关联计划：签署功能
关联 ADR：签署状态机
```

**6.3 经验总结**：在计划文档末尾添加 `📝 经验总结`，记录：
- 这次开发中踩了什么坑？
- 有什么可以复用到下次的知识？
- 有什么流程可以改进？

**质量门禁 G6 — 归档完成**：
- [ ] 相关文档已更新
- [ ] 提交信息符合规范并引用了计划文档
- [ ] 经验总结已记录

---

### 3. 文档体系

所有文档统一写在 **CLAUDE.md** 末尾，按以下顺序追加：

```
CLAUDE.md
├── 主体（项目说明 + 工程标准）
└── 末尾追加（按时间倒序）
    ├── ## 计划：YYYY-MM-DD-title    # 每次开发
    ├── ## ADR-NNN-title             # 架构决策
    └── ## 📝 经验总结               # 踩坑记录
```

#### 架构决策记录 (ADR)

架构决策记录用于记录每次架构级选择的背景、方案和后果。参考 Michael Nygard 的 ADR 模式：

```markdown
# ADR-{NNN}: {决策标题}

> 日期：YYYY-MM-DD | 状态：已接受 / 已废弃 / 已替代

## 上下文

[描述需要做决策的背景和动机]

## 决策

[我们决定采用什么方案]

## 理由

[为什么选这个方案，不选其他方案]

## 后果

[这个决策带来的正面和负面影响]

## 替代方案

[考虑过的其他方案及未选理由]
```

#### 计划文档模板

```markdown
# 计划：[功能名称]

> 日期：YYYY-MM-DD | 作者：Claude Code | 状态：进行中 / 已完成

## 📋 需求

[一句话描述需求]

## 🎯 目标

[可衡量的完成标准]

## 🔧 方案

[技术方案描述，包括架构设计、关键决策理由]

### 方案对比

| 方案 | 优点 | 缺点 | 推荐 |
|------|------|------|------|
| A | ... | ... | ✅ |
| B | ... | ... | ❌ |

## 📁 涉及文件

### 后端
- `src/main/java/...` - 新增/修改/删除（理由）

### 前端
- `src/api/...` - 新增/修改/删除（理由）

### 数据库
- `V27__add_new_table.sql` - 新增/修改（理由）

## 📝 实现步骤

> 按执行顺序排列，每步完成后勾选

1. [ ] 步骤一
2. [ ] 步骤二
3. [ ] 步骤三

## 🧪 测试策略

- 单元测试覆盖：*核心逻辑方法*
- 集成测试覆盖：*API 端点*
- 手动测试覆盖：*UI 交互流程*

## ⚠️ 风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|------|------|------|------|---------|
| ... | 低/中/高 | 低/中/高 | ... | ... |

## ✅ 验证标准

> 与 G5 对应，验证通过后在此勾选

- [ ] 标准 1
- [ ] 标准 2

---

## ✅ 验证结果

> 验证日期：YYYY-MM-DD | 验证人：Claude Code

| 验证项 | 结果 | 证据 |
|--------|------|------|
| 标准 1 | ✅ 通过 | 请求/响应截图或日志 |
| 标准 2 | ✅ 通过 | 数据库查询结果 |

## 📝 经验总结

- **踩坑**：
- **复用**：
- **流程改进**：
```

---

### 4. 质量门禁总表

每个阶段结束后，必须通过对应的质量门禁才能进入下一阶段：

```
阶段 ① → G1 需求确认     → 阶段 ②
阶段 ② → G2 计划批准     → 阶段 ③
阶段 ③ → G3 实现自检     → 阶段 ④
阶段 ④ → G4 审查通过     → 阶段 ⑤
阶段 ⑤ → G5 验证通过     → 阶段 ⑥
阶段 ⑥ → G6 归档完成     → 完成
```

---

### 5. 变更影响矩阵

开发前快速判断改动范围：

| 改动类型 | 前端 | 后端 | 数据库 | 配置 | 部署 | 文档 |
|---------|------|------|--------|------|------|------|
| 新增页面/组件 | ✅ | - | - | - | 构建 | 可选 |
| 新增 API 接口 | ✅ | ✅ | 可选 | - | 重启后端 | API 文档 |
| 新增数据库表 | - | ✅ | ✅ | - | Flyway 迁移 | 表结构 |
| 修改业务逻辑 | 可选 | ✅ | 可选 | - | 重启后端 | 可选 |
| 安全加固 | 可选 | ✅ | - | - | 重启后端 | 安全指南 |
| 修改配置 | - | - | - | ✅ | 重启容器 | 配置文档 |
| 部署脚本 | - | - | - | ✅ | 执行脚本 | 部署文档 |
| 数据库迁移 | - | ✅ | ✅ | - | Flyway 自动 | 迁移文档 |

---

### 6. 验证命令速查

| 验证场景 | 命令 |
|---------|------|
| 后端 API 正常 | `curl -s -X POST "http://localhost:9004/api/auth/login" -H "Content-Type: application/json" -d '{"username":"10000000","password":"123456"}'` |
| 数据库状态 | `docker exec miaotongdoc-postgres psql -U miaotong -d miaotongdocdb -c "SELECT ..."` |
| 容器状态 | `docker compose ps \| grep healthy` |
| 后端日志 | `docker compose logs --tail=50 web-server` |
| 前端构建 | `npm run build` |
| 代码审查 | 在 Claude 中运行 `/code-review` |

---

### 7. 常见场景速查

| 场景 | 流程 | 关键门禁 |
|------|------|---------|
| 修复一个 bug | ① 需求分析 → ③ 代码实现 → ④ 审查 → ⑤ 验证 → ⑥ 归档 | G3, G4, G5 |
| 添加一个简单页面 | ① 需求分析 → ② 计划（口头）→ ③ 实现 → ④ 审查 → ⑤ 验证 → ⑥ 归档 | G2, G4, G5 |
| 新功能模块（前后端+数据库） | ① 需求分析 → ② 计划（正式文档+ADR）→ ③ 实现 → ④ 审查 → ⑤ 验证 → ⑥ 归档 | G1-G6 全部 |
| 架构重构 | ① 需求分析 → ② 计划（正式文档+ADR）→ ③ 实现（分阶段）→ ④ 审查 → ⑤ 验证 → ⑥ 归档 | G1-G6 全部，可能多次迭代 |
| 数据库迁移 | ① 需求分析 → ② 计划（ADR）→ ③ 实现 → ④ 审查 → ⑤ 验证（回滚测试）→ ⑥ 归档 | G2, G4, G5（回滚验证必做） |

---

### 8. AI 行为规范

以下规范约束 Claude Code 在每次开发调用中的行为：

1. **流程强制**：严格按照六阶段 + 六门禁执行，不允许跳过任何阶段或门禁
2. **文档先行**：中等以上复杂度任务，必须先写计划文档再编码
3. **进度可见**：编码过程中用 `TodoWrite` 维护可见的任务列表
4. **验证闭环**：每项功能完成，必须用真实请求/响应验证，不能只"看代码没问题"
5. **文档同步**：项目结构变化时，同步更新 CLAUDE.md 相关章节
6. **记忆机制**：用户反馈非显而易见的业务逻辑，用 `/memory` 记录
7. **问题上报**：遇到模糊需求或阻塞问题，立即确认，不自行假设
8. **提交规范**：提交信息必须包含 `Plan:` 引用，方便追溯

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
| 系统审查报告 | `SYSTEM_AUDIT.md` | 已开发功能、43 个问题、修复计划 |
| 变更日志 | `CHANGELOG.md` | 项目版本变更记录 |
| Docker 部署文档 | `MiaotongDoc-Docker/README.md` | 详细部署指南（如存在） |
| 编辑器说明 | `MiaotongDoc-Editor/README.md` | 编辑器镜像构建说明（如存在） |
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
