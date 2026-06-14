# MiaotongDoc 部署指南

## 项目简介

MiaotongDoc 是一个企业级多格式在线文档协作与签署平台，支持 Word、Excel、PowerPoint 在线编辑和协同办公。

### 技术栈

- **前端**：Vue 3 + Element Plus + TypeScript
- **后端**：Spring Boot 3.2 + Java 17
- **数据库**：PostgreSQL 12
- **缓存**：Redis 7
- **消息队列**：RabbitMQ 3
- **文档编辑**：OnlyOffice Document Server 9.3
- **对象存储**：MinIO（可选）
- **部署**：Docker Compose

---

## 目录结构

```
MiaotongDoc-Docker/
├── .env                          # 环境变量（密码、密钥等）
├── .env.example                  # 环境变量模板
├── .gitignore                    # Git 忽略规则
├── README.md                     # 本文件
├── docker-compose.yml            # Docker Compose 编排文件
├── deploy.sh                     # 一键部署脚本
│
├── app/                          # 应用构建产物
│   ├── server/
│   │   └── miaotongdoc.jar       # 后端 JAR 包
│   ├── web/
│   │   └── dist/                 # 前端构建产物
│   └── editor/                   # OnlyOffice 编辑器配置
│       ├── Dockerfile
│       ├── config/
│       │   └── local.json.template
│       └── scripts/
│           └── entrypoint.sh
│
├── config/                       # 服务配置文件
│   ├── nginx/
│   │   ├── nginx.conf            # Nginx 配置（含 WebSocket）
│   │   ├── service-worker-override.js
│   │   └── ssl/                  # SSL 证书目录（生产环境）
│   ├── postgres/
│   │   └── init.sql              # PostgreSQL 初始化脚本
│   ├── redis/
│   │   └── redis.conf            # Redis 配置
│   ├── logback-spring.xml        # 后端日志配置
│   └── logrotate/
│       └── app-logs              # 日志轮转配置
│
├── scripts/                      # 运维脚本
│   ├── clean-editor-cache.sh     # OnlyOffice 缓存清理
│   └── logrotate.sh              # 日志轮转执行脚本
│
└── data/                         # 运行时数据（.gitignore，不提交到 Git）
    ├── documents/                # 文档存储（本地模式）
    ├── pgdata/                   # PostgreSQL 数据
    ├── minio/                    # MinIO 数据
    ├── rabbitmq/                 # RabbitMQ 数据
    ├── editor/                   # OnlyOffice 数据（配置、字体）
    ├── editor-cache/             # OnlyOffice 缓存
    └── logs/                     # 所有日志
        ├── server/               # 后端应用日志
        ├── nginx/                # Nginx 日志
        ├── postgres/             # PostgreSQL 日志
        ├── editor/               # OnlyOffice 编辑器1 日志
        ├── editor2/              # OnlyOffice 编辑器2 日志
        ├── editor3/              # OnlyOffice 编辑器3 日志
        ├── redis/                # Redis 日志
        ├── rabbitmq/             # RabbitMQ 日志
        └── minio/                # MinIO 日志
```

---

## 快速部署

### 1. 环境要求

- Docker >= 20.10
- Docker Compose >= 2.0
- 至少 4GB 内存
- 至少 20GB 磁盘空间

### 2. 准备环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，修改所有密码和密钥
# 重要：生产环境必须修改所有默认密码！
vi .env
```

**必须修改的配置：**

| 配置项 | 说明 | 要求 |
|--------|------|------|
| `DB_PASSWORD` | 数据库密码 | >= 8 位 |
| `REDIS_PASSWORD` | Redis 密码 | >= 8 位 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | >= 8 位 |
| `APP_JWT_SECRET` | 应用 JWT 密钥 | >= 32 位 |
| `EDITOR_JWT_SECRET` | 编辑器 JWT 密钥 | >= 32 位 |
| `SECURE_LINK_SECRET` | OnlyOffice 安全链接密钥 | >= 16 位 |
| `MINIO_ACCESS_KEY` | MinIO 访问密钥 | >= 8 位 |
| `MINIO_SECRET_KEY` | MinIO 秘密密钥 | >= 8 位 |

### 3. 构建应用（首次部署）

```bash
# 构建后端 JAR
cd ../miaotongdoc-server
mvn clean package -DskipTests

# 构建前端
cd ../miaotongdoc-web
npm install
npm run build

# 复制构建产物到部署目录
cp ../miaotongdoc-server/target/miaotongdoc.jar ../MiaotongDoc-Docker/app/server/
cp -r dist/* ../MiaotongDoc-Docker/app/web/dist/
```

### 4. 启动服务

```bash
cd ../MiaotongDoc-Docker

# 方式一：使用部署脚本（推荐）
chmod +x deploy.sh
./deploy.sh start

# 方式二：使用 Docker Compose
docker compose up -d
```

### 5. 访问系统

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost | 用户登录和文档管理 |
| 后端 API | http://localhost:9004 | REST API |
| MinIO 控制台 | http://localhost:9001 | 对象存储管理 |
| RabbitMQ 管理 | http://localhost:15672 | 消息队列管理 |

**默认管理员账号：**
- 工号：`10000001`
- 密码：`Admin@123`

---

## 服务说明

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| nginx | miaotongdoc-nginx | 80 | 反向代理 + 前端静态资源 |
| postgres | miaotongdoc-postgres | 5432 | PostgreSQL 数据库 |
| redis | miaotongdoc-redis | 6379 | Redis 缓存 |
| rabbitmq | miaotongdoc-rabbitmq | 5672, 15672 | RabbitMQ 消息队列 |
| web-server | miaotongdoc-server | 9004 | Spring Boot 后端 |
| editor | miaotongdoc-editor | - | OnlyOffice 编辑器1 |
| editor2 | miaotongdoc-editor2 | - | OnlyOffice 编辑器2 |
| editor3 | miaotongdoc-editor3 | - | OnlyOffice 编辑器3 |
| minio | miaotongdoc-minio | 9000, 9001 | MinIO 对象存储 |
| cache-cleaner | miaotongdoc-cache-cleaner | - | OnlyOffice 缓存清理 |
| logrotate | miaotongdoc-logrotate | - | 日志轮转 |

---

## 数据库说明

### 连接信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 数据库类型 | PostgreSQL 12 | — |
| 数据库名 | `miaotongdocdb` | 由 `POSTGRES_DB` 环境变量定义 |
| 用户名 | `miaotong` | 由 `POSTGRES_USER` 环境变量定义 |
| 密码 | 见 `.env` 中 `DB_PASSWORD` | 生产环境必须修改 |
| Schema | `public` | 默认 schema，所有表均在此 schema 下 |
| 端口 | `5432` | 容器内外均为 5432 |
| JDBC URL | `jdbc:postgresql://postgres:5432/miaotongdocdb` | Docker 内部连接地址 |

### 数据库表（15 张）

| 表名 | 说明 | 前缀 |
|------|------|------|
| `sys_department` | 部门表（多层级架构） | sys_ |
| `sys_user` | 用户表（8 位工号） | sys_ |
| `mt_document` | 文档主表 | mt_ |
| `mt_document_share` | 文档共享权限表 | mt_ |
| `mt_document_version` | 文档版本表 | mt_ |
| `mt_comment` | 评论批注表 | mt_ |
| `mt_mention` | @提及关联表 | mt_ |
| `mt_activity` | 协作动态表 | mt_ |
| `mt_notification` | 通知表 | mt_ |
| `mt_signing_task` | 签署任务表 | mt_ |
| `mt_signing_record` | 签署记录表 | mt_ |
| `mt_audit_log` | 审计日志表 | mt_ |
| `mt_token_blacklist` | JWT 黑名单表 | mt_ |
| `mt_sso_identity` | SSO 身份关联表 | mt_ |
| `mt_audit_log_archive` | 审计日志归档表（温数据） | mt_ |

### 表命名规则

- `sys_` 前缀：系统基础数据（部门、用户）
- `mt_` 前缀：业务数据（文档、签署、审计等）

### PostgreSQL 扩展

初始化脚本自动创建以下扩展（需要超级用户权限）：

| 扩展 | 用途 | 验证命令 |
|------|------|----------|
| `uuid-ossp` | UUID 生成函数（文档 key 生成） | `SELECT uuid_generate_v4();` |
| `pg_trgm` | 三元组文本搜索（文档标题模糊搜索） | `SELECT show_trgm('测试');` |

> **注意**：扩展创建使用了 `EXCEPTION WHEN insufficient_privilege` 容错处理。如果权限不足，扩展会静默跳过。部署后请手动验证：
> ```bash
> docker compose exec postgres psql -U miaotong -d miaotongdocdb -c "SELECT extname FROM pg_extension WHERE extname IN ('uuid-ossp','pg_trgm');"
> ```

---

## Redis 配置说明

### 连接信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 镜像 | `redis:7-alpine` | Alpine 精简版 |
| 端口 | `6379` | 容器内外均为 6379 |
| 密码 | 见 `.env` 中 `REDIS_PASSWORD` | 通过 `--requirepass` 启动参数设置 |
| 固定 IP | `172.20.0.30` | Docker 内部网络地址 |

### 持久化策略（RDB + AOF 双保险）

| 方式 | 触发条件 | 说明 |
|------|----------|------|
| RDB 快照 | `save 900 1` | 900 秒内至少 1 次写入则快照 |
| RDB 快照 | `save 300 10` | 300 秒内至少 10 次写入则快照 |
| RDB 快照 | `save 60 10000` | 60 秒内至少 10000 次写入则快照 |
| AOF 追加 | `appendonly yes` | 每秒同步（`appendfsync everysec`） |

数据文件存储在 Redis 容器内部（未挂载外部卷），重启容器数据保留。

### 内存配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 最大内存 | `512mb` | 超出后按淘汰策略清理 |
| 淘汰策略 | `allkeys-lru` | 所有 key 中淘汰最近最少使用的 |
| 最大连接数 | `10000` | 默认值 |

### 安全加固

以下危险命令已被禁用或重命名：

| 原命令 | 处理方式 | 说明 |
|--------|----------|------|
| `FLUSHALL` | 禁用（重命名为空） | 防止误删所有数据 |
| `FLUSHDB` | 禁用（重命名为空） | 防止误删当前库数据 |
| `DEBUG` | 禁用（重命名为空） | 防止调试命令滥用 |
| `CONFIG` | 重命名为 `CONFIG_mt_2026` | 运维需要时用 `redis-cli CONFIG_mt_2026 GET *` |

### 慢查询日志

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 阈值 | `10000` 微秒（10ms） | 超过此时间的命令记录到慢查询日志 |
| 日志长度 | `128` 条 | 最多保留 128 条慢查询记录 |

```bash
# 查看慢查询日志
docker compose exec redis redis-cli -a ${REDIS_PASSWORD} SLOWLOG GET 10
```

---

## RabbitMQ 配置说明

### 连接信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 镜像 | `rabbitmq:3-management` | 含管理插件 |
| AMQP 端口 | `5672` | 应用连接端口 |
| 管理端口 | `15672` | Web 管理界面 |
| 用户名 | `miaotong` | 硬编码在 docker-compose.yml |
| 密码 | 见 `.env` 中 `RABBITMQ_PASSWORD` | — |
| 固定 IP | `172.20.0.35` | Docker 内部网络地址 |
| VHost | `/` | 默认 vhost |

### 在系统中的作用

RabbitMQ 用于 **OnlyOffice 多编辑器实例之间的协同编辑消息传递**。当多个用户同时编辑同一文档时，编辑器实例通过 RabbitMQ 交换光标位置、编辑操作等实时消息。

- 队列类型：`rabbitmq`（在 `local.json.template` 中配置）
- AMQP 连接串：`amqp://miaotong:${RABBITMQ_PASSWORD}@rabbitmq:5672`

### 管理界面

访问 http://localhost:15672，使用 `.env` 中的 `RABBITMQ_PASSWORD` 登录。

在管理界面可以：
- 查看队列、交换机、绑定关系
- 监控消息吞吐量和连接数
- 手动清空队列或删除消息

### 数据持久化

数据存储在 `data/rabbitmq/` 目录（挂载到容器 `/var/lib/rabbitmq`）。

---

## OnlyOffice 编辑器配置说明

### 基本信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 基础镜像 | `onlyoffice/documentserver:9.3` | 通过 Dockerfile 构建自定义镜像 |
| 自定义镜像名 | `miaotongdoc-editor:latest` | 3 个编辑器实例共用同一镜像 |
| 容器内部端口 | `80` | 不对外暴露，通过 Nginx 代理 |
| 健康检查 | `curl -f http://localhost/healthcheck` | 30s 间隔，60s 启动等待 |

### 实例分布

| 实例 | 容器名 | 固定 IP | 日志目录 |
|------|--------|---------|----------|
| editor1 | miaotongdoc-editor | 172.20.0.50 | data/logs/editor/ |
| editor2 | miaotongdoc-editor2 | 172.20.0.51 | data/logs/editor2/ |
| editor3 | miaotongdoc-editor3 | 172.20.0.52 | data/logs/editor3/ |

### 自定义构建内容

Dockerfile 对原始 OnlyOffice 镜像做了以下定制：

| 修改项 | 说明 |
|--------|------|
| 并发限制提升 | `docservice` 并发数从 20 提升到 127（二进制补丁） |
| Nginx Connection 头修复 | `Connection: close` 改为空，修复 Socket.IO 兼容性 |
| 代理超时增加 | `proxy_read_timeout` 等从 300s 增加到 900s |
| Service Worker 替换 | 替换为自卸载版本，防止拦截 Socket.IO 请求 |
| 品牌替换 | 所有 "OnlyOffice" 文本替换为 "MiaotongDoc Editor" |
| 许可限制移除 | `connectionsLimit: 999999`，`usersCountLimit: 999999` |

### 配置模板机制

编辑器配置通过 `app/editor/config/local.json.template` 模板生成，在容器启动时由 `entrypoint.sh` 使用 `envsubst` 替换环境变量。

**模板变量白名单**（entrypoint.sh 中定义）：

| 变量 | 来源 | 用途 |
|------|------|------|
| `${DB_PASSWORD}` | .env | PostgreSQL 连接密码 |
| `${EDITOR_JWT_SECRET}` | .env | JWT 签名密钥 |
| `${REDIS_PASSWORD}` | .env | Redis 连接密码 |
| `${RABBITMQ_PASSWORD}` | .env | RabbitMQ 连接密码 |
| `${SECURE_LINK_SECRET}` | .env | 文件访问安全链接密钥 |

### 关键环境变量

| 变量 | 值 | 说明 |
|------|-----|------|
| `JWT_ENABLED` | `true` | 启用 JWT 认证 |
| `JWT_SECRET` | `${EDITOR_JWT_SECRET}` | JWT 签名密钥 |
| `JWT_IN_BODY` | `true` | 允许 JWT 在请求体中传递 |
| `COAUTH_ALLOW_PRIVATE_IP` | `true` | 允许协同编辑回调到 Docker 内部 IP |
| `DB_TYPE` | `postgres` | 数据库类型 |

### 数据卷

| 容器路径 | 宿主机路径 | 用途 |
|----------|------------|------|
| `/var/www/onlyoffice/Data` | `data/editor/` | 编辑器配置、字体等持久数据 |
| `/var/lib/onlyoffice/documentserver/App_Data` | `data/editor-cache/` | 文件缓存（临时文件） |
| `/var/log/onlyoffice` | `data/logs/editor*/` | 日志 |

### 缓存清理

`cache-cleaner` 容器每天凌晨 3 点自动清理超过 24 小时的缓存文件：
- 清理目录：`cache/files/data`（临时编辑文件）、`cache/files/forgotten`（已关闭文档的残留文件）
- 清理阈值：由 `CACHE_MAX_AGE_HOURS` 环境变量控制（默认 24 小时）

```bash
# 手动触发缓存清理
docker compose exec cache-cleaner /bin/sh /scripts/clean.sh
```

---

## MinIO 配置说明

### 连接信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 镜像 | `minio/minio:latest` | S3 兼容对象存储 |
| S3 API 端口 | `9000` | 应用连接端口 |
| 管理控制台端口 | `9001` | Web 管理界面 |
| Root 用户 | 见 `.env` 中 `MINIO_ACCESS_KEY` | 控制台登录用户名 |
| Root 密码 | 见 `.env` 中 `MINIO_SECRET_KEY` | 控制台登录密码 |
| 固定 IP | `172.20.0.60` | Docker 内部网络地址 |
| 存储桶 | 见 `.env` 中 `MINIO_BUCKET` | 默认 `miaotongdoc` |

### 在系统中的作用

当 `STORAGE_TYPE=minio` 时，所有文档文件存储在 MinIO 中，而非本地文件系统。后端通过 S3 API 进行文件的上传、下载、删除操作。

**存储路径格式**：`documents/{yyyy/MM}/{docKey}/v{version}.{fileType}`

### 存储桶初始化

存储桶由后端应用在首次启动时自动创建（`MinioStorageService.init()`），无需手动创建。

### 管理控制台

访问 http://localhost:9001，使用 `.env` 中的 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` 登录。

在控制台可以：
- 浏览存储桶和文件
- 设置桶策略（公开/私有）
- 查看存储使用量

### 数据持久化

数据存储在 `data/minio/` 目录（挂载到容器 `/data`）。

### 本地存储 vs MinIO

| 特性 | 本地存储（`STORAGE_TYPE=local`） | MinIO（`STORAGE_TYPE=minio`） |
|------|----------------------------------|-------------------------------|
| 适用场景 | 开发环境、单机部署 | 生产环境、多机部署 |
| 文件位置 | `data/documents/` | MinIO 服务 `data/minio/` |
| 水平扩展 | 不支持 | 支持分布式部署 |
| 数据备份 | 文件系统拷贝 | MinIO 原生复制 |
| 切换方式 | 修改 `.env` 中 `STORAGE_TYPE` | 同左，需手动迁移文件 |

---

## Nginx 配置说明

### 基本配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 镜像 | `nginx:latest` | — |
| 端口 | `80` | 对外服务端口 |
| 最大上传大小 | `200MB` | `client_max_body_size 200m` |
| 固定 IP | `172.20.0.10` | Docker 内部网络地址 |

### 路由规则

| 路径 | 目标 | 说明 |
|------|------|------|
| `/` | 静态文件 | 前端 Vue 应用（`data/web/dist/`） |
| `/api/*` | `web-server:9004` | 后端 REST API |
| `/ws/presence/` | WebSocket | 在线状态推送（900s 超时） |
| `/ws/notifications` | WebSocket | 通知推送（900s 超时） |
| `/socket.io/*` | 编辑器 | OnlyOffice Socket.IO 协同编辑 |
| `/ds-vpath/*` | 编辑器 | OnlyOffice 文档服务代理 |

### 编辑器负载均衡

Nginx 使用 **一致性哈希** 算法将请求路由到 3 个编辑器实例：

```nginx
hash $doc_key$request_uri consistent;
```

- **路由亲和性**：同一文档始终路由到同一编辑器实例，保证协同编辑状态一致性
- **文档 key 提取**：通过正则从 URL 中提取 UUID 格式的 `doc_key`
- **Socket.IO 参数注入**：当请求缺少 `EIO` 和 `transport` 参数时，自动注入 `EIO=4&transport=polling`

### 缓存控制

编辑器资源设置了强制不缓存头，防止浏览器缓存旧版本编辑器文件：

```
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Expires: 0
```

---

## Docker 网络配置

所有容器运行在自定义桥接网络 `mtd-net` 上：

| 配置项 | 值 |
|--------|-----|
| 网络名称 | `mtd-net` |
| 驱动 | `bridge` |
| 子网 | `172.20.0.0/16` |

### 容器 IP 分配

| 容器 | IP |
|------|-----|
| nginx | 172.20.0.10 |
| postgres | 172.20.0.20 |
| redis | 172.20.0.30 |
| rabbitmq | 172.20.0.35 |
| web-server | 172.20.0.40 |
| editor | 172.20.0.50 |
| editor2 | 172.20.0.51 |
| editor3 | 172.20.0.52 |
| minio | 172.20.0.60 |

> **生产环境注意**：`postgres`、`redis`、`rabbitmq`、`minio` 端口均对外暴露，仅用于开发调试。生产环境应通过防火墙关闭这些端口，只保留 `80`（或 `443`）端口对外。

---

## 环境变量依赖关系

以下表格列出 `.env` 中每个变量被哪些服务使用：

| 变量 | postgres | web-server | editor/2/3 | redis | rabbitmq | minio | nginx |
|------|----------|------------|------------|-------|----------|-------|-------|
| `POSTGRES_DB` | ✓ | — | — | — | — | — | — |
| `POSTGRES_USER` | ✓ | — | — | — | — | — | — |
| `DB_PASSWORD` | ✓ | ✓ | ✓ | — | — | — | — |
| `REDIS_PASSWORD` | — | ✓ | ✓ | ✓ | — | — | — |
| `RABBITMQ_PASSWORD` | — | — | ✓ | — | ✓ | — | — |
| `APP_JWT_SECRET` | — | ✓ | — | — | — | — | — |
| `APP_JWT_EXPIRATION` | — | ✓ | — | — | — | — | — |
| `EDITOR_JWT_SECRET` | — | ✓ | ✓ | — | — | — | — |
| `CORS_ORIGINS` | — | ✓ | — | — | — | — | — |
| `STORAGE_TYPE` | — | ✓ | — | — | — | — | — |
| `MINIO_ACCESS_KEY` | — | ✓ | — | — | — | ✓ | — |
| `MINIO_SECRET_KEY` | — | ✓ | — | — | — | ✓ | — |
| `MINIO_BUCKET` | — | ✓ | — | — | — | — | — |
| `SECURE_LINK_SECRET` | — | — | ✓ | — | — | — | — |
| `SSO_ENABLED` | — | ✓ | — | — | — | — | — |
| `SSO_ISSUER_URI` | — | ✓ | — | — | — | — | — |
| `SSO_CLIENT_ID` | — | ✓ | — | — | — | — | — |
| `SSO_CLIENT_SECRET` | — | ✓ | — | — | — | — | — |
| `SSO_PROVIDER_NAME` | — | ✓ | — | — | — | — | — |

---

## 日志管理

### 日志位置

所有日志都在 `data/logs/` 目录下，按服务分类：

```
data/logs/
├── server/          # 后端应用日志（按天+100MB切割，压缩）
├── nginx/           # Nginx 访问日志和错误日志
├── postgres/        # PostgreSQL 日志
├── editor/          # OnlyOffice 编辑器1 日志
├── editor2/         # OnlyOffice 编辑器2 日志
├── editor3/         # OnlyOffice 编辑器3 日志
├── redis/           # Redis 日志（Docker 日志）
├── rabbitmq/        # RabbitMQ 日志（Docker 日志）
└── minio/           # MinIO 日志（Docker 日志）
```

### 日志轮转策略

| 服务 | 切割方式 | 压缩 | 保留策略 |
|------|----------|------|----------|
| web-server | 按天 + 100MB | .gz 压缩 | 保留 30 天，总量上限 3GB |
| nginx | 按天 | .gz 压缩 | 保留 30 天 |
| postgres | 按天 | .gz 压缩 | 保留 7 天 |
| editor | 按天 | .gz 压缩 | 保留 7 天 |
| redis | Docker 日志驱动 | 无 | max-size: 10m, max-file: 3 |
| rabbitmq | Docker 日志驱动 | 无 | max-size: 10m, max-file: 3 |
| minio | Docker 日志驱动 | 无 | max-size: 10m, max-file: 3 |

### 查看日志

```bash
# 查看所有服务日志
./deploy.sh logs

# 查看指定服务日志
./deploy.sh logs web-server
./deploy.sh logs nginx

# 查看日志文件
tail -f data/logs/server/server.log
tail -f data/logs/nginx/access.log
```

---

## 常用命令

### 服务管理

```bash
# 启动服务
./deploy.sh start

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看服务状态
./deploy.sh status

# 检查服务健康状态
./deploy.sh health
```

### 日志管理

```bash
# 查看日志
./deploy.sh logs [服务名]

# 清理旧日志（超过 30 天）
./deploy.sh clean-logs
```

### 数据备份

```bash
# 备份数据
./deploy.sh backup
```

### Docker Compose 命令

```bash
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f [服务名]

# 停止服务
docker compose down

# 重启服务
docker compose restart

# 完全清理（包括数据）
docker compose down -v
```

---

## 存储配置

### 本地存储（开发环境）

```bash
# .env 配置
STORAGE_TYPE=local
```

文件存储在 `data/documents/` 目录。

### MinIO 存储（生产环境）

```bash
# .env 配置
STORAGE_TYPE=minio
MINIO_ACCESS_KEY=your_access_key
MINIO_SECRET_KEY=your_secret_key
MINIO_BUCKET=miaotongdoc
```

文件存储在 MinIO 服务中。

**MinIO 管理控制台：** http://localhost:9001

---

## SSO 单点登录配置

如需启用 SSO 单点登录，在 `.env` 中配置：

```bash
SSO_ENABLED=true
SSO_ISSUER_URI=https://sso.bank.com/realms/miaotong
SSO_CLIENT_ID=miaotongdoc
SSO_CLIENT_SECRET=your_client_secret
SSO_PROVIDER_NAME=银行统一认证
```

---

## SSL/HTTPS 配置（生产环境）

### 1. 准备 SSL 证书

将证书文件放到 `config/nginx/ssl/` 目录：

```
config/nginx/ssl/
├── server.crt    # 证书文件
└── server.key    # 私钥文件
```

### 2. 修改 Nginx 配置

编辑 `config/nginx/nginx.conf`，添加 HTTPS 配置：

```nginx
server {
    listen 443 ssl;
    server_name doc.bank.com;

    ssl_certificate /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;

    # ... 其他配置
}

server {
    listen 80;
    server_name doc.bank.com;
    return 301 https://$host$request_uri;
}
```

### 3. 重启 Nginx

```bash
docker compose restart nginx
```

---

## 数据备份与恢复

### 备份

```bash
# 使用部署脚本备份
./deploy.sh backup

# 手动备份 PostgreSQL
docker compose exec postgres pg_dump -U miaotong miaotongdocdb > backup.sql

# 手动备份文档
cp -r data/documents /backup/documents
```

### 恢复

```bash
# 恢复 PostgreSQL
docker compose exec -T postgres psql -U miaotong miaotongdocdb < backup.sql

# 恢复文档
cp -r /backup/documents data/documents
```

---

## 故障排查

### 服务无法启动

```bash
# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs [服务名]

# 检查健康状态
./deploy.sh health
```

### 数据库连接失败

```bash
# 检查 PostgreSQL 状态
docker compose exec postgres pg_isready -U miaotong -d miaotongdocdb

# 查看 PostgreSQL 日志
tail -f data/logs/postgres/postgresql.log
```

### OnlyOffice 编辑器无法打开

```bash
# 检查编辑器状态
docker compose ps editor

# 查看编辑器日志
tail -f data/logs/editor/docservice.log

# 检查编辑器健康状态
docker compose exec editor curl http://localhost/healthcheck
```

### MinIO 连接失败

```bash
# 检查 MinIO 状态
docker compose ps minio

# 查看 MinIO 日志
docker compose logs minio

# 测试 MinIO 连接
docker compose exec minio mc alias set local http://localhost:9000 miaotong miaotong2024minio
```

---

## 生产环境部署清单

- [ ] 修改所有默认密码（.env 文件）
- [ ] 配置 SSL 证书（config/nginx/ssl/）
- [ ] 修改 CORS_ORIGINS 为生产域名
- [ ] 配置 STORAGE_TYPE=minio（推荐）
- [ ] 配置日志轮转（已默认配置）
- [ ] 配置数据备份策略
- [ ] 配置监控告警（可选）
- [ ] 配置防火墙规则（只开放 80、443 端口）

---

## 常见问题

### Q: 忘记管理员密码怎么办？

```bash
# 重置管理员密码
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c "
UPDATE sys_user SET password = '\$2a\$10\$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH' WHERE username = 'admin';
"
# 重置后密码为：Admin@123
```

### Q: 如何修改 OnlyOffice 编辑器端口？

编辑 `docker-compose.yml`，修改 nginx 配置中的 editor 代理端口。

### Q: 如何扩容 OnlyOffice 编辑器？

在 `docker-compose.yml` 中添加更多 editor 实例（editor4、editor5...），并更新 nginx 配置。

### Q: 数据库满了怎么办？

```bash
# 查看数据库大小
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c "SELECT pg_size_pretty(pg_database_size('miaotongdocdb'));"

# 清理过期数据
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c "DELETE FROM mt_audit_log WHERE created_at < NOW() - INTERVAL '90 days';"
```

---

## 技术支持

如有问题，请联系系统管理员或查看日志排查。
