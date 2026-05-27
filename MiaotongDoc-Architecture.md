# MiaotongDoc 项目架构与问题解决总结

## 一、项目概述

MiaotongDoc 是一个企业级在线文档协同编辑系统，基于 OnlyOffice DocumentServer 构建，提供文档在线编辑、多人协同、签署流程等功能。

## 二、系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         浏览器 (Browser)                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Vue.js 前端 (miaotongdoc-web)                            │  │
│  │  - 文档管理、协同状态、签署流程                              │  │
│  │  - OnlyOffice JS API (DocsAPI.DocEditor)                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP :80
┌──────────────────────────▼──────────────────────────────────────┐
│                    Nginx 反向代理 (miaotongdoc-nginx)             │
│                                                                  │
│  /                  → 前端静态资源 (Vue dist)                    │
│  /api/              → web-server:9004 (Spring Boot 后端)         │
│  /ws/presence/      → web-server:9004 (WebSocket 协同状态)       │
│  /ws/notifications  → web-server:9004 (WebSocket 通知推送)       │
│  /ds-vpath/.../doc/ → editor:80 (Socket.IO 长轮询/WebSocket)    │
│  /ds-vpath/         → editor:80 (OnlyOffice 编辑器资源)          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│  web-server   │  │    editor     │  │   postgres    │
│  Spring Boot  │  │  OnlyOffice   │  │  PostgreSQL   │
│  :9004        │  │  DocServer    │  │  :5432        │
│               │  │  :80 (nginx)  │  │               │
│  - REST API   │  │  :8000 (node) │  │  - miaotongdb │
│  - Callback   │  │  - Socket.IO  │  │  - 文档元数据  │
│  - JWT Auth   │  │  - 协同引擎    │  │  - 用户数据    │
└───────┬───────┘  └───────┬───────┘  └───────────────┘
        │                  │
        └────────┬─────────┘
                 ▼
        ┌───────────────┐
        │     redis     │
        │   Redis 7     │
        │   :6379       │
        │  - 会话缓存    │
        │  - 协同状态    │
        └───────────────┘
```

### 2.1 Docker 服务清单

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| nginx | nginx:latest | 80 | 反向代理，前端静态资源 |
| web-server | eclipse-temurin:17-jre | 9004 | Spring Boot 后端 API |
| editor | onlyoffice/documentserver:latest | 80 (内部) | OnlyOffice 文档编辑器 |
| postgres | postgres:12 | 5432 (内部) | 数据库 |
| redis | redis:7-alpine | 6379 (内部) | 缓存与会话存储 |

### 2.2 网络配置

所有容器位于 `mtd-net` 网络，使用固定 IP：

- nginx: 172.20.0.10
- postgres: 172.20.0.20
- redis: 172.20.0.30
- web-server: 172.20.0.40
- editor: 172.20.0.50

### 2.3 请求流转路径

**文档加载流程：**

```
1. 浏览器 → GET /api/documents/{id}/config
2. web-server 生成 EditorConfig：
   - document.url = http://web-server:9004/api/documents/{id}/file
   - callbackUrl = http://web-server:9004/api/callback/editor
   - document.key = {uuid}_v{version}
   - token = JWT(editorSecret, 5min)
3. 浏览器加载 OnlyOffice API JS
4. 浏览器创建 DocsAPI.DocEditor(config)
5. Editor 内部：
   a. 通过 document.url 下载文件
   b. 建立 Socket.IO 连接进行协同
   c. 通过 callbackUrl 回调保存状态
```

**Socket.IO 连接路径：**

```
浏览器 → Socket.IO path: /ds-vpath/{version}/doc/{docKey}/c
  → 外层 nginx (rewrite 去掉 /ds-vpath/ 前缀)
  → 编辑器内部 nginx (版本化路径匹配)
  → docservice (Node.js :8000)
  → Socket.IO 握手 (GET) + 数据传输 (POST/WebSocket)
```

## 三、问题解决过程

### 问题 1：编辑器容器启动失败（502 Healthcheck）

**现象：** 编辑器容器无法启动，healthcheck 返回 502。

**根本原因：** `docker-compose.yml` 中的环境变量名称与 OnlyOffice 启动脚本期望的名称不匹配。

OnlyOffice 的 `run-document-server.sh` 启动脚本读取特定的环境变量名：
```bash
DB_PWD=${DB_PWD:-${POSTGRESQL_SERVER_PASS:-...}}
REDIS_SERVER_HOST=${REDIS_SERVER_HOST:-...}
REDIS_SERVER_PASS=${REDIS_SERVER_PASS:-...}
```

但 `docker-compose.yml` 中使用了：
```yaml
DB_PASSWORD: ...        # 脚本期望 DB_PWD
REDIS_PASSWORD: ...     # 脚本期望 REDIS_SERVER_PASS
REDIS_HOST: ...         # 脚本期望 REDIS_SERVER_HOST
```

**修复：** 修改 `docker-compose.yml` 中编辑器的环境变量名：
```yaml
environment:
  - DB_PWD=${DB_PASSWORD}
  - REDIS_SERVER_HOST=redis
  - REDIS_SERVER_PASS=${REDIS_PASSWORD}
```

**关键文件：** `MiaotongDoc-Docker/docker-compose.yml`

---

### 问题 2：Socket.IO 握手失败 — Connection: close 头

**现象：** 浏览器无法建立 Socket.IO 连接，请求返回错误。

**根本原因：** 两层 nginx 的 `Connection` 头配置都强制设置了 `close`，导致 Socket.IO 长轮询无法保持连接。

**外层 nginx（MiaotongDoc-Docker/nginx/nginx.conf）：**
```nginx
# 问题配置
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;    # ← 非 WebSocket 请求时强制 close
}

# 修复后
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      '';       # ← 空字符串，让 nginx 默认处理（keep-alive）
}
```

**编辑器内部 nginx（/etc/nginx/includes/http-common.conf）：**
```nginx
# 问题配置
map $http_upgrade $proxy_connection {
    websocket upgrade;
    default close;    # ← 强制 close
}

# 修复后（在 Dockerfile 中通过 sed 修改）
map $http_upgrade $proxy_connection {
    websocket upgrade;
    default "";       # ← 空字符串
}
```

**修复文件：**
- `MiaotongDoc-Docker/nginx/nginx.conf`
- `MiaotongDoc-Editor/Dockerfile`（添加 sed 命令修改内部 nginx 配置）

---

### 问题 3：Socket.IO POST 握手失败 — "Bad handshake method"（核心阻塞问题）

**现象：** 浏览器发送 POST 请求到 Socket.IO 端点，返回 400 Bad Request，错误信息 `{"code":2,"message":"Bad handshake method"}`。

**排查过程：**

1. **验证 Socket.IO 服务端正常** — 用 curl 发送 GET 请求握手成功，返回 sid
2. **验证 POST 正常** — 用 curl 发送带 sid 的 POST 请求成功
3. **分析编辑器日志** — 发现请求 URL 异常：
   ```
   url=/doc/c49a4c5d-e9f5-4d12-b814-4b2307c19bb3?EIO=4&transport=polling
   ```
   期望的 URL 应该是：`/doc/{docKey}/c/socket.io/?EIO=4&transport=polling`
4. **发现关键线索** — 文档 key 包含 `#` 字符：`c49a4c5d-e9f5-4d12-b814-4b2307c19bb3#v1`

**根本原因：** 文档 key 中的 `#` 字符是 URL 片段标识符（Fragment Identifier）。

当 OnlyOffice SDK 构造 Socket.IO 路径时：
```javascript
this.path = basePath + "../../../../doc/" + docKey + "/c"
// 实际值: /ds-vpath/9.3.1-hash/../../../../doc/c49a4c5d...#v1/c
```

浏览器解析 URL 时，`#v1/c` 被当作 Fragment，实际发送的 HTTP 请求路径被截断为：
```
/ds-vpath/9.3.1-hash/doc/c49a4c5d...（没有 #v1/c/socket.io/）
```

导致请求到达错误的端点，Socket.IO 服务端返回 "Bad handshake method"。

**修复：** 将文档 key 中的 `#v` 分隔符改为 `_v`：

```java
// DocumentController.java
document.setKey(doc.getDocKey() + "_v" + doc.getCurrentVersion());

// PdfExportService.java
request.put("key", doc.getDocKey() + "_v" + doc.getCurrentVersion() + "_pdf");

// CallbackService.java
private String[] parseDocKey(String key) {
    int idx = key.lastIndexOf("_v");
    // ...
}
```

**修复文件：**
- `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/DocumentController.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfExportService.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/service/CallbackService.java`

---

### 问题 4：编辑器回调失败 — 私有 IP 访问被拒绝

**现象：** 文档能加载但无法保存，编辑器日志显示：
```
DNS lookup 172.20.0.40(family:4, host:web-server) is not allowed.
Because, It is private IP address.
```

**根本原因：** OnlyOffice DocumentServer 9.x 使用的 Node.js/axios 版本默认禁止请求私有 IP 地址（安全特性）。回调 URL `http://web-server:9004/api/callback/editor` 解析到 Docker 内网 IP 172.20.0.40，被拦截。

**修复：** 在 `docker-compose.yml` 中添加环境变量：
```yaml
editor:
  environment:
    - COAUTH_ALLOW_PRIVATE_IP=true
```

**关键文件：** `MiaotongDoc-Docker/docker-compose.yml`

---

### 问题 5：Service Worker 干扰 Socket.IO 请求

**现象：** 浏览器缓存的旧 Service Worker 可能拦截 Socket.IO 请求。

**修复：** 替换 OnlyOffice 的 Service Worker 为自注销版本：

```javascript
// service-worker-override.js
self.addEventListener('install', function(event) {
  self.skipWaiting();
});
self.addEventListener('activate', function(event) {
  event.waitUntil(
    Promise.all([
      caches.keys().then(function(names) {
        return Promise.all(names.map(function(n) { return caches.delete(n); }));
      }),
      self.registration.unregister(),
      self.clients.claim()
    ])
  );
});
```

**修复文件：**
- `MiaotongDoc-Editor/service-worker-override.js`
- `MiaotongDoc-Editor/Dockerfile`（COPY 到编辑器目录）

---

### 问题 6：品牌 Logo 替换

**现象：** 编辑器显示 OnlyOffice 原始 Logo。

**修复：** 创建 MiaotongDoc SVG Logo 文件，替换编辑器中的所有 Logo 资源：

| 原始文件 | 用途 |
|---------|------|
| `common/main/resources/img/header/header-logo_s.svg` | 亮色主题 Header Logo |
| `common/main/resources/img/header/dark-logo_s.svg` | 暗色主题 Header Logo |
| `common/main/resources/img/about/logo_s.svg` | 关于页面 Logo |
| `common/main/resources/img/about/logo-white_s.svg` | 关于页面白色 Logo |
| `common/embed/resources/img/logo.svg` | 嵌入模式 Logo |

**注意：** 必须同时更新 `.gz` 压缩文件，否则 nginx 会提供旧的 gzip 版本。

**修复文件：**
- `MiaotongDoc-Editor/branding/` 目录下的 SVG 文件
- `MiaotongDoc-Editor/Dockerfile`（复制并压缩 Logo 文件）

## 四、关键配置文件说明

### 4.1 docker-compose.yml

```yaml
editor:
  environment:
    - JWT_ENABLED=true
    - JWT_SECRET=${EDITOR_JWT_SECRET}      # JWT 密钥
    - JWT_IN_BODY=true                       # 允许 JWT 在请求体中传递
    - DB_PWD=${DB_PASSWORD}                  # 必须用 DB_PWD（不是 DB_PASSWORD）
    - REDIS_SERVER_HOST=redis                # 必须用 REDIS_SERVER_HOST
    - REDIS_SERVER_PASS=${REDIS_PASSWORD}    # 必须用 REDIS_SERVER_PASS
    - COAUTH_ALLOW_PRIVATE_IP=true           # 允许回调到私有 IP
```

### 4.2 编辑器 local.json

启动脚本会用环境变量覆盖 `local.json` 中的值，但以下配置需要预设：

```json
{
  "services": {
    "CoAuthoring": {
      "token": {
        "enable": {
          "request": { "inbox": false, "outbox": false },
          "browser": false
        }
      },
      "secret": {
        "inbox": { "string": "..." },
        "outbox": { "string": "..." },
        "session": { "string": "..." },
        "browser": { "string": "..." }
      }
    }
  }
}
```

### 4.3 外层 nginx.conf Socket.IO 路由

```nginx
# Socket.IO 请求路由（必须在 catch-all /ds-vpath/ 之前）
location ~ ^/ds-vpath/(.*/doc/.*)$ {
    # 自动补上 Socket.IO 参数（如果没有）
    set $args $args$socketio_default_args;

    rewrite ^/ds-vpath/(.*)$ /$1 break;
    proxy_pass http://editor:80;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_read_timeout 86400s;
}
```

## 五、构建与部署

### 5.1 构建后端 JAR

```bash
# 使用 Maven Docker 镜像构建（本地 Java 版本可能不匹配）
docker run --rm \
  -v "$(pwd)/miaotongdoc-server:/usr/src/app" \
  -v "$(pwd)/.m2:/root/.m2" \
  -w /usr/src/app \
  maven:3.9-eclipse-temurin-17 \
  mvn package -DskipTests
```

### 5.2 构建编辑器镜像

```bash
cd MiaotongDoc-Docker
docker compose build editor
```

### 5.3 启动服务

```bash
cd MiaotongDoc-Docker
docker compose up -d
```

### 5.4 验证服务状态

```bash
# 检查所有容器状态
docker compose ps

# 检查编辑器健康状态
docker exec miaotongdoc-editor curl http://localhost/healthcheck

# 测试 Socket.IO 握手
curl "http://localhost/ds-vpath/9.3.1-{cache-tag}/doc/{doc-key}/c/socket.io/?EIO=4&transport=polling"
```

## 六、已知限制与待优化项

1. **JWT Token** — 当前编辑器配置为禁用 JWT 验证（`token.enable.browser = false`），生产环境应启用
2. **HTTPS** — 当前仅支持 HTTP，生产环境需要配置 SSL
3. **文件存储** — 使用本地文件系统，大规模部署应使用对象存储
4. **日志** — 编辑器 nginx 的 `access_log off;` 在主配置中覆盖了 access log 设置
5. **Logo 品牌化** — 部分移动端 Logo 和嵌入模式 Logo 可能需要进一步适配

## 七、版本信息

| 组件 | 版本 |
|------|------|
| OnlyOffice DocumentServer | 9.3.1 (build:10) |
| Spring Boot | 3.2.5 |
| PostgreSQL | 12 |
| Redis | 7 |
| Nginx | 1.27.5 |
| Socket.IO (服务端) | 4.5.3 |
| Socket.IO (客户端) | 4.5.3 |
