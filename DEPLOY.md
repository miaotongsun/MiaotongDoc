# MiaotongDoc 内网部署指南

> **适用场景**：将项目从一台机器迁移到内网新机器，从零开始部署全部 15 个容器。
> **基于 2026-07-19 实际全新部署验证**，覆盖 Windows + Docker Desktop 环境下全部已知陷阱。
> **维护者**：Claude Code | **最后更新**：2026年7月19日

---

## 📋 目录

- [部署架构概览](#部署架构概览)
- [第 0 步：前置环境检查](#第-0-步前置环境检查必须先做)
- [第 1 步：配置 .env](#第-1-步配置-env)
- [第 2 步：构建产物](#第-2-步构建产物)
- [第 3 步：分阶段启动](#第-3-步分阶段启动顺序至关重要)
- [第 4 步：重置管理员密码](#第-4-步重置管理员密码)
- [第 5 步：部署后验证清单](#第-5-步部署后验证清单)
- [内存与稳定性配置](#内存与稳定性配置)
- [Linux 生产环境基线配置](#linux-生产环境基线配置)
- [部署故障速查表](#部署故障速查表)
- [常见部署问题详解](#常见部署问题详解)
- [访问地址](#访问地址)
- [相关文档](#相关文档)

---

## 部署架构概览

```
源码构建（宿主机）              Docker 容器（按顺序启动）
┌─────────────────────┐        ┌──────────────────────────────────┐
│ miaotongdoc-web     │──build──│-> nginx (80) 前端静态资源          │
│   npm run build     │        ├──────────────────────────────────┤
│ miaotongdoc-server  │──mvn───│-> web-server (9004) Spring Boot   │
│   mvn package       │        ├──────────────────────────────────┤
│ MiaotongDoc-Editor  │──build─│-> editor/2/3 (80内) MTOffice    │
│   docker build      │        ├──────────────────────────────────┤
│ MiaotongDoc-Docker  │        │-> postgres/redis/rabbitmq/es/minio│
│   /app/yjs          │──build─│-> yjs-server (1234)               │
└─────────────────────┘        └──────────────────────────────────┘
```

**启动顺序总览**（顺序至关重要）：

```
A. 基础设施 (postgres/redis/es/minio)
       ↓ 全部 healthy
B. RabbitMQ
       ↓ healthy
C. MTOffice 编辑器 (editor/2/3)  ← 会创建 task_result 表
       ↓ healthy
D. web-server (Spring Boot)        ← Flyway V9 依赖 task_result 表
       ↓ Started
E. yjs-server + nginx
```

---

## 第 0 步：前置环境检查（必须先做）

### 0.1 Docker Desktop

```bash
# 确认 Docker 已运行（Windows 下需先启动 Docker Desktop）
docker info
docker compose version
```

若 `docker info` 报 `cannot connect to Docker daemon`，手动启动 Docker Desktop 后等待 30 秒再试。

### 0.2 端口占用检查（Windows 关键陷阱）

Windows 的 `winnat` 服务会动态保留端口段给 Hyper-V，导致 Docker 无法绑定。**1234 端口（yjs-server）极易落入排除范围**。

```powershell
# 查看被排除的端口段（管理员 PowerShell）
netsh int ipv4 show excludedportrange protocol=tcp

# 若 1234 落在某个排除段内（如 1142-1241），重启 winnat 释放：
net stop winnat
net start winnat

# 再次确认 1234 不在排除列表
netsh int ipv4 show excludedportrange protocol=tcp
```

> **必检端口**：80(nginx)、9004(web-server)、5432(pg)、6379(redis)、5672/15672(rabbitmq)、9000/9001(minio)、1234(yjs)。若任一端口在排除段内，先 `net stop/start winnat`。

### 0.3 源码目录同步检查（编辑器）

MTOffice 编辑器源码有**两个镜像位置必须完全一致**：

```bash
# 确认 Docker 构建目录下 config/nginx 子目录存在（首次部署常缺失）
ls MiaotongDoc-Docker/app/editor/config/

# 若缺失，从源码主目录同步
cp -r MiaotongDoc-Editor/config/* MiaotongDoc-Docker/app/editor/config/

# 验证关键文件存在
ls MiaotongDoc-Docker/app/editor/config/nginx/ds-aiproxy.conf
```

---

## 第 1 步：配置 .env

```bash
cd MiaotongDoc-Docker
cp .env.example .env
```

**必须修改的项**（生产环境全部换强密码）：

| 变量 | 说明 | 要求 |
|------|------|------|
| `DB_PASSWORD` | PostgreSQL 密码 | ≥8 位 |
| `REDIS_PASSWORD` | Redis 密码 | ≥8 位 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | ≥8 位 |
| `APP_JWT_SECRET` | 应用 JWT 密钥 | ≥32 位 |
| `EDITOR_JWT_SECRET` | 编辑器 JWT 密钥 | ≥32 位，需与编辑器 local.json 一致 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 凭证 | ≥8 位 |
| `LLM_API_URL` / `LLM_API_KEY` | 内网大模型地址 | 内网可达的 OpenAI 兼容接口 |

---

## 第 2 步：构建产物

### 2.1 前端

```bash
cd miaotongdoc-web
npm install          # 首次或依赖变更时
npm run build        # = vue-tsc && vite build
```

**陷阱**：`vue-tsc` 严格类型检查可能因历史代码报错（PdfEditor/PdfCanvas 等组件）。若仅类型错误而无语法错误，可跳过类型检查直接构建：

```bash
npx vite build       # 跳过 vue-tsc，仅打包
```

构建产物复制到 Docker 目录：

```bash
mkdir -p ../MiaotongDoc-Docker/app/web/dist
cp -r dist/* ../MiaotongDoc-Docker/app/web/dist/
```

### 2.2 后端

```bash
cd miaotongdoc-server
mvn clean package -DskipTests     # 约 4 分钟
cp target/miaotongdoc.jar ../MiaotongDoc-Docker/app/server/miaotongdoc.jar
```

### 2.3 编辑器镜像（首次或编辑器源码变更时）

```bash
cd MiaotongDoc-Docker
docker compose build editor       # 约 5-10 分钟，下载 ~1.6GB 基础镜像
```

---

## 第 3 步：分阶段启动（顺序至关重要！）

> ⚠️ **不能直接 `docker compose up -d` 全部启动**。Flyway V9 迁移依赖 MTOffice 创建的 `task_result` 表，若 web-server 先于 editor 启动会失败。

### 阶段 A：基础设施

```bash
cd MiaotongDoc-Docker
docker compose up -d postgres redis elasticsearch minio
# 等待全部 healthy（约 1 分钟）
docker compose ps
```

### 阶段 B：RabbitMQ

RabbitMQ 使用 **bind mount** `./data/rabbitmq:/var/lib/rabbitmq`。干净目录下 bind mount 通常能正常工作。

**如果遇到 cookie 权限错误**（`Restarting (1)` + 日志含 `Cookie file must be accessible by owner only`），说明 Windows 文件系统权限干扰了 `.erlang.cookie`，此时可改为命名卷解决：

```bash
# 改 docker-compose.yml 中 rabbitmq 的 volumes 为：
#   - rabbitmq-data:/var/lib/rabbitmq
# 末尾加 volumes 声明
docker compose stop rabbitmq && docker rm -f miaotongdoc-rabbitmq
rm -rf data/rabbitmq/*        # 清理旧 bind mount 数据
docker compose up -d rabbitmq
docker compose ps rabbitmq    # 等待 healthy
```

### 阶段 C：MTOffice 编辑器（会创建 task_result 表）

```bash
docker compose up -d editor editor2 editor3
# 等待约 2 分钟，编辑器内部 nginx 需时间启动
docker compose ps editor      # 等待 healthy

# 验证 MTOffice 表已创建（task_result / doc_changes）
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c '\dt' | grep -E 'task_result|doc_changes'
```

**陷阱**：编辑器内部 nginx 启动失败报 `upstream directive is not allowed here in ds-aiproxy.conf`。原因是 `config/nginx/ds-aiproxy.conf` 内含 `upstream{}` 块，却被 `include` 到 server 上下文。临时绕过（容器内执行，重启后失效）：

```bash
for ed in editor editor2 editor3; do
  docker compose exec $ed bash -c \
    "rm -f /etc/nginx/includes/ds-aiproxy.conf && \
     sed -i '/ds-aiproxy.conf/d' /etc/onlyoffice/documentserver/nginx/ds.conf /etc/nginx/nginx.conf 2>/dev/null && \
     service nginx start"
done
```

> 永久修复需改 `app/editor/Dockerfile` 第 94-96 行：把 `ds-aiproxy.conf` 的 `upstream{}` 块拆出 include 到 nginx.conf 的 http 块，或整个文件 include 到 http 块而非 server 块。

### 阶段 D：后端 web-server（Flyway 迁移）

```bash
docker compose up -d web-server
# 首次启动会执行 Flyway V1-V26 迁移，约 30 秒
docker compose logs -f web-server | grep -E 'Started|ERROR'
```

**陷阱 1**：若报 `V2__init_departments.sql failed: relation "sys_department" does not exist`，是残留数据导致 baseline 跳过 V1。清理后重启：

```bash
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c \
  "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO miaotong;"
docker compose restart web-server
```

**陷阱 2**：若报 `V9__add_third_party_table_comments.sql failed: relation "task_result" does not exist`，是 editor 还没创建表或 `DROP SCHEMA` 时被删除。回到阶段 C 确保 editor healthy 且 `task_result` 表存在后，再 `docker compose restart web-server`。

> ⚠️ **注意**：如果用了陷阱 1 的 `DROP SCHEMA`，也会同时删除 editor 创建的 `task_result` 表。此时需要重启 editor 重建该表，再重启 web-server：

```bash
# 1. 重建 task_result 表（editor 启动时会自动创建）
docker compose restart editor
# 等待 30 秒，修复 editor nginx（见阶段 C 陷阱）
docker compose exec editor bash -c \
  "rm -f /etc/nginx/includes/ds-aiproxy.conf && \
   sed -i '/ds-aiproxy.conf/d' /etc/onlyoffice/documentserver/nginx/ds.conf /etc/nginx/nginx.conf 2>/dev/null && \
   service nginx start"

# 2. 确认 task_result 表已重建
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c '\dt' | grep task_result

# 3. 重启 web-server 继续迁移
docker compose restart web-server

### 阶段 E：Yjs + Nginx 前端

```bash
docker compose up -d yjs-server nginx
docker compose ps
```

---

## 第 4 步：重置管理员密码

V3 迁移脚本里的初始 BCrypt 哈希值是损坏的，`Admin@123` 无法登录。**首次部署后必须重置**为 `123456`：

```bash
cd MiaotongDoc-Docker

# 用 SQL 文件方式避免 shell 转义 $ 符号
cat > reset_pw.sql <<'EOF'
UPDATE sys_user SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm' WHERE employee_id = '10000000';
EOF

docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < reset_pw.sql

# 验证登录
curl -s -X POST http://localhost:9004/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"10000000","password":"123456"}'
# 应返回 {"token":"eyJ...","userId":1,...}
```

> **生成新哈希的方法**（若需改其他密码）：
> ```bash
> docker run --rm httpd:alpine htpasswd -nbBC 10 '' '新密码' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
> ```
> Spring Security 的 BCryptPasswordEncoder 兼容 `$2a$` / `$2y$` 格式。

---

## 第 5 步：部署后验证清单

| # | 检查项 | 命令 | 期望结果 |
|---|--------|------|----------|
| 1 | 容器状态 | `docker compose ps` | 全部 Up，核心服务 healthy |
| 2 | PostgreSQL | `docker compose exec postgres pg_isready -U miaotong` | accepting connections |
| 3 | Redis | `docker compose exec redis redis-cli -a miaotong2024 ping`（密码取自 .env 的 `REDIS_PASSWORD`，注意子命令不会自动展开 .env 变量，需写死或 `export`） | PONG |
| 4 | RabbitMQ | `docker compose exec rabbitmq rabbitmq-diagnostics ping` | Ping succeeded |
| 5 | MinIO | `curl -s -o /dev/null -w '%{http_code}' http://localhost:9000/minio/health/live` | 200 |
| 6 | 后端 API | `curl -s -o /dev/null -w '%{http_code}' http://localhost:9004/api/auth/me` | 403（未鉴权，正常）⚠️ `docker compose ps` 可能显示 unhealthy，因 actuator 健康端点返回 DOWN，但 API 实际正常 |
| 7 | 前端页面 | `curl -s -o /dev/null -w '%{http_code}' http://localhost/` | 200 |
| 8 | 编辑器 | `docker compose exec editor curl -sf http://localhost/healthcheck` | true |
| 9 | Yjs | `curl -s -o /dev/null -w '%{http_code}' http://localhost:1234/` | 404（无根路由，正常） |
| 10 | 登录 | 浏览器访问 `http://localhost/` 用 `10000000` / `123456` | 进入主页 |

---

## 内存与稳定性配置

### 容器策略：无内存限制 + ES 例外

`docker-compose.yml` 中**所有容器不设硬性内存限制**，跟随宿主机内存自动伸缩。**唯一例外**：

- **Elasticsearch**：通过 `ES_JAVA_OPTS=-Xms512m -Xmx512m` 显式限制 JVM heap
- **原因**：Java 应用默认会主动吃 `系统内存/2` 的 heap，无限制时会迅速占满内存
- **其他容器**（PG/Redis/RabbitMQ/MinIO/web-server/editor/nginx）都是按需使用内存，无需限制

### 宿主机层面的稳定性保证

由于容器没有硬上限，**稳定性靠宿主机层面控制**：

#### 通用措施

| 措施 | 说明 |
|------|------|
| **Elasticsearch JVM heap** | 已在 `docker-compose.yml` 第 399 行设置（512m），16GB 宿主机够用；32GB+ 可调到 1-2g |
| **宿主机 swap** | 突发内存压力的缓冲带（建议 ≥ 内存的 25%，生产环境必备） |
| **监控告警** | 部署 Prometheus node_exporter + alertmanager 监控内存使用率 |

#### Windows 宿主机（开发/测试）

| 措施 | 操作 |
|------|------|
| **Docker Desktop 内存上限** | Settings → Resources → Memory → 设为物理内存的 75%（给 Windows OS 留 25%） |
| **预留 OS 内存** | 宿主机预留 4-8GB 给 Windows 自身 + 系统缓存 |
| **swap** | `sysdm.cpl` → 高级 → 性能 → 高级 → 虚拟内存 → 自定义大小 |

#### Linux 宿主机（生产环境）★

| 措施 | 操作 |
|------|------|
| **Docker 引擎配置** | 编辑 `/etc/docker/daemon.json`，添加 `"default-ulimits": {"memlock": {"Name": "memlock", "Hard": -1, "Soft": -1}}` |
| **内核 vm.swappiness** | `sysctl vm.swappiness=10`（建议写到 `/etc/sysctl.d/99-miaotongdoc.conf`，降低 swap 使用倾向） |
| **内核 vm.overcommit_memory** | `sysctl vm.overcommit_memory=1`（允许内存超额申请，避免 OOM killer 误杀） |
| **预留 OS 内存** | 宿主机预留 1-2GB 给 Linux 内核 + 系统服务（远比 Windows 少） |
| **swap 配置** | `fallocate -l 16G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile`（写入 `/etc/fstab` 持久化） |
| **大页内存** | 若使用 ES + 高并发文档索引，启用 `vm.nr_hugepages` |

**Linux 内核 OOM killer 调优**（防止误杀容器）：

OOM killer 行为：`vm.overcommit_memory=1` + `oom_kill_allocating_task=0` 已配置后，OOM 时会杀占用内存最多的进程（通常是 web-server Java 堆）。如需保护关键进程：

```bash
# 方法 1：在 docker-compose.yml 中给容器配置 oom_score_adj（推荐）
# 在 service 配置块添加：
#   oom_score_adj: -500    # 范围 -1000 ~ 1000，越低越不容易被杀

# 例：保护 web-server（Spring Boot 进程）
services:
  web-server:
    # ...其他配置
    oom_score_adj: -500

# 方法 2：通过 /proc 在容器内调整（仅对当前进程，重启后失效）
docker exec -u root miaotongdoc-server bash -c "echo -500 > /proc/1/oom_score_adj"
```

> **不要直接在宿主机用 `echo > /proc/$(pidof java)/oom_score_adj`**：这是常见但错误的做法。容器内进程的 `pidof java` 是宿主机视角下看到的 PID，调整的是宿主机同名进程而非容器内 ES 进程。

**Linux cgroup 内存监控**：

```bash
# 查看某个容器的实时内存使用（cgroup v2，推荐）
CID=$(docker compose ps -q elasticsearch)
cat /sys/fs/cgroup/system.slice/docker-${CID}.scope/memory.current
cat /sys/fs/cgroup/system.slice/docker-${CID}.scope/memory.peak
cat /sys/fs/cgroup/system.slice/docker-${CID}.scope/memory.max    # 若设了上限

# cgroup v1（旧系统，如 CentOS 7）
cat /sys/fs/cgroup/memory/docker/${CID}/memory.usage_in_bytes
```

**Linux cgroup v2 资源监控**（推荐生产环境）：

```bash
# 查看某个容器的内存使用细节（cgroup v2）
cat /sys/fs/cgroup/system.slice/docker-<container-id>.scope/memory.current
cat /sys/fs/cgroup/system.slice/docker-<container-id>.scope/memory.peak
```

> cgroup v1 与 v2 路径不同：`memory.usage_in_bytes` 是 v1，`memory.current` 是 v2。可通过 `stat -fc %T /sys/fs/cgroup/` 判断（`tmpfs`=v1，`cgroup2fs`=v2）。

**Docker daemon 全局 ulimit 配置**（与文档第 3 节配合）：

```json
{
  "default-ulimits": {
    "nofile": { "Name": "nofile", "Hard": 65536, "Soft": 65536 },
    "nproc":  { "Name": "nproc",  "Hard": 16384, "Soft": 16384 },
    "memlock":{ "Name": "memlock","Hard": -1,    "Soft": -1    }
  }
}
```

**Linux vs Windows 关键差异**：

| 差异点 | Windows | Linux |
|--------|---------|-------|
| OS 自身内存开销 | 4-8GB | 1-2GB |
| Docker 引擎 | Docker Desktop（WSL2/Hyper-V） | 原生 Docker Engine（更高效） |
| OOM killer 行为 | 相对保守 | 更激进（需调 oom_score_adj） |
| swap 配置 | GUI 系统属性 | `swapon` 命令 |
| cgroup 版本 | 默认 v1 | 默认 v2（Ubuntu 22.04+、CentOS 9+） |
| 内存监控命令 | `wmic OS Get ...` | `free -h` / `vmstat` |

### 不同宿主机配置建议

| 宿主机内存 | ES heap | 备注 |
|-----------|---------|------|
| 8 GB | 512m | 勉强可用，建议升级内存 |
| 16 GB | 512m | 默认配置，足够支撑 15 个容器 |
| 32 GB | 1g | 适合生产环境 |
| 64 GB+ | 2g | 大文档库 / 高并发场景 |

修改 ES heap 步骤：

```bash
# 编辑 docker-compose.yml 第 399 行
sed -i 's|ES_JAVA_OPTS=.*|ES_JAVA_OPTS=-Xms1g -Xmx1g|' MiaotongDoc-Docker/docker-compose.yml
docker compose restart elasticsearch
```

### 监控内存使用

```bash
# 查看每个容器的实际内存占用
docker stats --no-stream

# 查看宿主机总内存与剩余
# Windows
wmic OS Get FreePhysicalMemory,TotalVisibleMemorySize /Value
# Linux
free -h
```

### 不需要做的事

- ❌ 给容器加 `deploy.resources.limits.memory` —— 已删除（跟随宿主机）
- ❌ 给 docling 设 4G 上限 —— 已删除（AI 模型按需加载，无任务时不占内存）
- ❌ 给 web-server/editor/PG/Redis 设上限 —— 它们都是事件驱动的低内存占用

### ⚠️ `docker compose down` 的数据丢失风险（已知陷阱）

**问题**：`docker-compose.yml` 中只有部分服务挂载了 host 路径（`./data/...`），未挂载的服务数据存在容器内层，`docker compose down` 会**永久丢失**。

**具体风险**（来自 `docker-compose.yml` 实际配置核查）：

| 服务 | 是否挂载 host 路径 | 挂载点 | `down` 后数据状态 |
|------|---------------------|---------|---------------------|
| postgres | ✅ | `./data/pgdata:/var/lib/postgresql/data` | 安全（数据库文件） |
| elasticsearch | ✅ | `./data/elasticsearch:/usr/share/elasticsearch/data` | 安全（索引数据） |
| minio | ✅ | `./data/minio:/data` | 安全（对象存储） |
| web-server | ✅ | `./data/documents:/data/documents` + `./data/config:/data/config` | 安全（用户上传文件） |
| editor/2/3 | ✅ | `./data/editor:/var/www/onlyoffice/Data` + `./data/editor-cache:/var/lib/onlyoffice/documentserver/App_Data` | 安全（编辑会话/缓存） |
| rabbitmq | ✅ | `./data/rabbitmq:/var/lib/rabbitmq` | 安全（队列/消息） |
| docling | ✅ | `./data/docling-cache:/root/.cache` | 安全（AI 模型缓存，可重下） |
| **redis** | ✅ **已修复** | `./data/redis:/data` + `./data/logs/redis:/var/log/redis` | 安全（缓存/会话） |
| **yjs-server** | ✅ **已修复** | `./data/yjs:/data` | 安全（协同状态） |
| nginx / ocr / ocr-paddle / cache-cleaner / logrotate | ❌ 无 | — | 安全（无状态服务或辅助服务） |

**说明**：redis 和 yjs-server 的持久化卷已在 docker-compose.yml 中补全（与 postgres / rabbitmq 等服务保持一致），`deploy.sh` 的 `create_data_dirs()` 也已更新创建新目录。

**deploy.sh 默认行为**：`deploy.sh stop` 调用的就是 `docker compose down`（[deploy.sh:101](MiaotongDoc-Docker/deploy.sh#L101)），会丢失 Redis 数据。生产环境**不要用 `./deploy.sh stop`**，改用：

```bash
docker compose stop --timeout 60  # 或直接 docker compose stop
```

### 建议补全的持久化卷（生产环境推荐）

✅ **已修复**：redis 和 yjs-server 的持久化卷已在 docker-compose.yml 中补全，与 postgres 等服务保持一致。`deploy.sh` 的 `create_data_dirs()` 也已更新创建新目录。

**生效方法**（旧部署升级到带持久化）：

```bash
mkdir -p MiaotongDoc-Docker/data/{redis,yjs,logs/redis}
docker compose up -d redis yjs-server
```

### docker compose down 的额外注意

即便所有服务都已挂载持久化卷，`docker compose down` 会**删除容器和未挂载的容器内数据**（如 Redis 启动时的 PID 文件、`/var/log/redis/` 中未挂载的子目录等），下次 `up` 时容器是全新的。建议生产环境改用 `docker compose stop`（保留容器，下次启动复用）。

---

## Linux 生产环境基线配置

> 以下每一项都标注了**为什么需要**和**对应本项目哪个服务**。所有命令都基于实际验证或公认的生产实践。

### 1. 文件句柄与进程数限制（必须）

**为什么需要**：本项目包含 PostgreSQL、Elasticsearch、MTOffice 等需要大量打开文件的 Java/Go 应用。Linux 默认 `nofile=1024`、`nproc=4096` 在高并发下会触顶，导致 "too many open files" 错误。

**影响服务**：elasticsearch（9200/9300）、postgres（5432）、rabbitmq（5672）、web-server、editor/2/3

**配置**：

```bash
cat > /etc/security/limits.d/99-miaotongdoc.conf <<'EOF'
*       soft    nofile    65536
*       hard    nofile    65536
*       soft    nproc     16384
*       hard    nproc     16384
root    soft    nofile    65536
root    hard    nofile    65536
EOF

# 验证（需重新登录生效）
ulimit -n
```

### 2. 内核参数优化（必须）

**为什么需要**：
- `vm.swappiness=10`：默认 60 太倾向用 swap，IO 性能下降
- `vm.overcommit_memory=1`：允许内存超额申请，避免 OOM killer 误杀
- `net.core.somaxconn=4096`：高并发 TCP 连接（Spring Boot WebSocket、长连接）

**影响服务**：所有 15 个容器

**配置**：

```bash
cat > /etc/sysctl.d/99-miaotongdoc.conf <<'EOF'
# 内存
vm.swappiness = 10
vm.overcommit_memory = 1
vm.overcommit_ratio = 50

# 网络（高并发 WebSocket + SSE 流式响应）
net.core.somaxconn = 4096
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 4096
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15

# 文件系统（大量小文件操作：ES 索引、MinIO 对象存储）
fs.file-max = 2097152
fs.nr_open = 1048576

# 内核 OOM（生产环境需要保护关键进程）
vm.oom_kill_allocating_task = 0    # 0=杀占用最多的进程；1=杀触发 OOM 的进程
EOF

sysctl --system
```

### 3. Docker 守护进程配置（必须）

**为什么需要**：
- `default-ulimits`：容器内进程默认继承宿主机 1024 文件句柄，必须提升
- `log-driver`：docker-compose.yml 已配 `max-size=10m max-file=3`，但守护进程默认 `data-root=/var/lib/docker` 可能撑爆根分区
- `live-restore`：daemon 重启时容器不中断（生产环境必备）
- `userland-proxy`：false 可减少 iptables 规则，提升性能

**配置**：

```bash
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "5"
  },
  "default-ulimits": {
    "nofile": { "Name": "nofile", "Hard": 65536, "Soft": 65536 },
    "nproc":  { "Name": "nproc",  "Hard": 16384, "Soft": 16384 }
  },
  "storage-driver": "overlay2",
  "live-restore": true,
  "userland-proxy": false,
  "metrics-addr": "127.0.0.1:9323",
  "experimental": false
}
EOF

systemctl restart docker
```

### 4. 时间同步（必须）

**为什么需要**：JWT 默认 2 小时过期（`app.jwt-expiration=7200000`，见 application.yml:102），签署/合同时间戳、ES 索引时间、Flyway 迁移校验都依赖准确时间。系统时间漂移会导致登录失败、签署时间错误。

**影响服务**：web-server（JWT）、postgres（事务时间）、elasticsearch（索引时间戳）

**配置**：

```bash
# 安装 chrony（比 ntpdate 更可靠）
apt-get install -y chrony          # Debian/Ubuntu
yum install -y chrony             # CentOS/RHEL

systemctl enable --now chronyd
chronyc tracking                   # 查看同步状态
```

### 5. swap 配置（强烈推荐）

**为什么需要**：突发内存压力（如 ES 大查询、web-server 堆转储）的缓冲带。本项目无内存硬限制，缺少 swap 在内存耗尽时 OOM killer 会立即杀进程。

**配置**：

```bash
# 创建 16GB swapfile（生产环境建议 = 物理内存的 25-50%）
fallocate -l 16G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile

# 持久化
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 验证
free -h
swapon --show
```

### 6. 防火墙配置（必须）

**为什么需要**：本项目暴露 11+ 个端口（80, 9004, 5432, 6379, 5672, 9000-9001, 9200, 1234 等），公网/内网开放全部端口存在安全风险。

**影响服务**：所有容器

**配置**（使用 firewalld，CentOS/RHEL 默认；Ubuntu 用 ufw）：

```bash
# 保留必要端口（按服务暴露的端口）
firewall-cmd --permanent --add-port=80/tcp       # nginx 前端
firewall-cmd --permanent --add-port=9004/tcp     # web-server API
firewall-cmd --permanent --add-port=9000-9001/tcp  # MinIO
firewall-cmd --permanent --add-port=15672/tcp   # RabbitMQ 管理（内网限定）
firewall-cmd --permanent --add-port=1234/tcp     # Yjs

# 数据库、Redis、ES 仅允许本机访问（用 127.0.0.1 bind 或 docker network 隔离）
# 不在防火墙层暴露 5432/6379/9200/5672 给外部

firewall-cmd --reload

# 验证
firewall-cmd --list-all
```

**Docker 网络隔离**（更安全的做法）：所有服务在 `mtd-net: 172.20.0.0/16` 内部网络通信，只有 nginx 通过宿主机 80 端口暴露。

### 7. 磁盘空间规划（必须）

**为什么需要**：本项目数据持久化在 `./data/` 下，包括 PostgreSQL（约 1GB+）、Elasticsearch（每万文档约 100MB）、MinIO 对象存储（用户上传的所有文件）、editor 缓存（MTOffice 编辑会话）。磁盘满会导致数据库崩溃、ES 索引失败。

**影响服务**：postgres、elasticsearch、minio、editor

**配置**：

```bash
# 检查当前磁盘使用
df -h

# 单独挂载数据盘（生产环境推荐）
# /var/lib/docker     - Docker 镜像、容器层
# /data               - 应用数据卷（postgres、es、minio、documents）

# 添加磁盘监控（避免 100% 满）
cat > /etc/cron.daily/disk-check <<'EOF'
#!/bin/bash
THRESHOLD=85
USAGE=$(df /data | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$USAGE" -ge "$THRESHOLD" ]; then
    echo "WARNING: /data usage ${USAGE}% >= ${THRESHOLD}%" | mail -s "Disk Alert" admin@example.com
fi
EOF
chmod +x /etc/cron.daily/disk-check
```

### 8. 日志管理（已配，需验证）

**当前状态**：docker-compose.yml 中所有服务已配置 `json-file` driver + `max-size=10m max-file=3`。**单个容器日志上限约 30MB，15 个容器约 500MB**，加上 Docker daemon 全局 50m×5=250MB，总日志占用约 750MB。

**生产环境强化**（用 logrotate 归档）：

```bash
cat > /etc/logrotate.d/docker-miaotongdoc <<'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    missingok
    notifempty
    copytruncate
}
EOF
```

### 9. SSH 安全（生产环境必须）

**为什么需要**：默认 SSH 22 端口暴露、密码登录、root 直接登录是常见攻击面。

**配置**：

```bash
# 编辑 /etc/ssh/sshd_config
sed -i 's/#Port 22/Port 2222/' /etc/ssh/sshd_config       # 改端口
sed -i 's/PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
sed -i 's/#MaxAuthTries 6/MaxAuthTries 3/' /etc/ssh/sshd_config

systemctl restart sshd
```

### 10. 备份策略（已配 deploy.sh backup）

**当前状态**：[deploy.sh](MiaotongDoc-Docker/deploy.sh) 已提供 `./deploy.sh backup` 命令，备份 PostgreSQL + 文档目录 + 配置 + .env。

**生产环境强化**（加 cron 自动备份）：

```bash
# 每天凌晨 3 点备份，保留 7 天
cat > /etc/cron.d/miaotongdoc-backup <<'EOF'
0 3 * * * root cd /opt/MiaotongDoc/MiaotongDoc-Docker && ./deploy.sh backup && find backup_* -maxdepth 0 -mtime +7 -exec rm -rf {} \;
EOF
```

**异地备份**（强烈推荐）：用 rsync 同步到另一台机器：

```bash
# 备份机执行（拉取）
0 4 * * * rsync -avz --delete miaotong@production:/opt/MiaotongDoc/MiaotongDoc-Docker/backup_*/ /backup/miaotongdoc/
```

### 11. CPU 配置（建议）

**为什么需要**：本项目无 CPU 限制，15 个容器可占用全部 CPU 核心。web-server Tomcat 默认 400 线程（`server.tomcat.threads.max`，见 application.yml:17），可能抢占其他服务。

**影响服务**：web-server 高并发时可能独占 CPU

**配置**（docker-compose.yml 给关键服务加 CPU 权重，非硬限制）：

```yaml
web-server:
  deploy:
    resources:
      limits:
        cpus: '4'          # 最多用 4 核
      reservations:
        cpus: '1'          # 至少保留 1 核
```

> 与内存策略不同，CPU 限制用 `cpus: '4'` 这种相对值（4 核），不设绝对值。

### 12. 一键初始化脚本（推荐）

将上述 1-10 项打包成 [`setup-linux-host.sh`](setup-linux-host.sh)（项目根目录），内网部署时运行一次即可：

```bash
curl -O https://your-repo/setup-linux-host.sh
chmod +x setup-linux-host.sh
sudo ./setup-linux-host.sh
```

脚本内容见仓库 `MiaotongDoc-Docker/setup-linux-host.sh`。

---

## 部署故障速查表

| 现象 | 根因 | 解决 |
|------|------|------|
| RabbitMQ 反复 Restarting | Windows cookie 权限 | 改用命名卷（见阶段 B） |
| yjs 端口绑定 forbidden | winnat 排除端口段 | `net stop/start winnat`（见 0.2） |
| web-server V2 失败 | 残留数据 baseline 跳过 | DROP SCHEMA 重建（见阶段 D 陷阱1） |
| web-server V9 失败 | editor 未先启动 | 确保 editor healthy 后 restart web-server |
| editor nginx emerg | ds-aiproxy.conf upstream 位置错 | 移除 include（见阶段 C） |
| editor config/nginx not found | 源码未同步 | `cp -r MiaotongDoc-Editor/config/* app/editor/config/` |
| 前端 vue-tsc 报错 | TS 严格检查 | `npx vite build` 跳过 |
| admin 登录密码错误 | V3 哈希损坏 | 重置为 123456（见第 4 步） |
| PostgreSQL checkpoint 损坏 | 非正常关闭 | 清空 `data/pgdata` 重启 |

---

## 常见部署问题详解

### 1. RabbitMQ 反复重启（Windows 环境）

**现象**：`docker compose ps` 显示 rabbitmq 状态 `Restarting (1)`，日志报 `Cookie file /var/lib/rabbitmq/.erlang.cookie must be accessible by owner only`。

**根因**：Windows 文件系统不支持 Linux 的 `chmod 600`，宿主机 bind mount `./data/rabbitmq` 在数据目录有残留或权限异常时，cookie 文件权限会被破坏，RabbitMQ 拒绝启动（干净目录下通常不触发）。

**解决**：改用 Docker 命名卷替代 bind mount（命名卷权限由 Docker 管理）：

```yaml
# docker-compose.yml
rabbitmq:
  volumes:
    - rabbitmq-data:/var/lib/rabbitmq   # 替换 ./data/rabbitmq

# 文件末尾 volumes 块（若已存在则追加）
volumes:
  rabbitmq-data:
```

```bash
docker compose stop rabbitmq && docker rm -f miaotongdoc-rabbitmq
rm -rf data/rabbitmq/*                                    # 清空可能损坏的旧数据
docker compose up -d rabbitmq
```

### 2. yjs-server 端口绑定失败

**现象**：`docker compose up -d yjs-server` 报 `ports are not available: exposing port TCP 0.0.0.0:1234 ... forbidden by its access permissions`。

**根因**：Windows `winnat` 服务把 1234 划入 Hyper-V 端口排除段（常见为 1142-1241）。

**解决**（管理员 PowerShell）：
```powershell
netsh int ipv4 show excludedportrange protocol=tcp   # 确认 1234 在排除段
net stop winnat
net start winnat
```

### 3. 后端启动 Flyway 迁移失败

**现象 A**：`Migration V2__init_departments.sql failed: relation "sys_department" does not exist`
**根因**：数据库有残留数据，Flyway baseline 跳过了 V1 建表脚本。
**解决**：清空 schema 重建
```bash
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c \
  "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO miaotong;"
docker compose restart web-server
```

**现象 B**：`Migration V9__add_third_party_table_comments.sql failed: relation "task_result" does not exist`
**根因**：`task_result` 表由 MTOffice Document Server 自动创建，web-server 启动早于 editor。
**解决**：确保 editor/2/3 全部 healthy 后再重启 web-server
```bash
docker compose up -d editor editor2 editor3
# 等待 healthy，确认表已创建
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c '\dt' | grep task_result
docker compose restart web-server
```

### 4. 编辑器 nginx 启动失败

**现象**：editor 容器 unhealthy，`service nginx start` 报 `upstream directive is not allowed here in /etc/nginx/includes/ds-aiproxy.conf:1`。

**根因**：`config/nginx/ds-aiproxy.conf` 内含 `upstream backend_ai {...}` 块，但被 `include` 到了 server 上下文（`upstream` 只能存在于 http 块）。

**临时解决**（容器内执行，重启失效）：
```bash
docker compose exec editor bash -c \
  "rm -f /etc/nginx/includes/ds-aiproxy.conf && \
   sed -i '/ds-aiproxy.conf/d' /etc/onlyoffice/documentserver/nginx/ds.conf /etc/nginx/nginx.conf 2>/dev/null && \
   service nginx start"
```

**永久修复**：修改 `MiaotongDoc-Docker/app/editor/Dockerfile` 第 94-96 行，把 `ds-aiproxy.conf` 的 `upstream{}` 与 `location{}` 拆分，或整体 include 到 `nginx.conf` 的 http 块。

### 5. 管理员无法登录

**现象**：用 `10000000` / `Admin@123` 登录返回 `{"code":400,"message":"密码错误"}`。

**根因**：`V3__init_admin_user.sql` 中的 BCrypt 哈希值疑似损坏（`...sl7iAt6Z5EH` 结尾与中间片段重复，不符合真实 BCrypt 输出特征），实测 `Admin@123` 无法通过校验。

**解决**：重置为 `123456`（详见 [第 4 步：重置管理员密码](#第-4-步重置管理员密码)）
```bash
cd MiaotongDoc-Docker
cat > reset_pw.sql <<'EOF'
UPDATE sys_user SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm' WHERE employee_id = '10000000';
EOF
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < reset_pw.sql
```

---

## 访问地址

部署完成后可访问：

| 服务 | 地址 | 用户/凭证 |
|------|------|-----------|
| 前端 Web | http://localhost/ | 登录后访问 |
| 后端 API | http://localhost:9004/api/ | - |
| MTOffice 编辑器 | http://localhost/editor/ | 走前端代理 |
| Yjs 协同 | ws://localhost:1234/ | WebSocket 协议 |
| MinIO API | http://localhost:9000/ | 程序访问 |
| MinIO 控制台 | http://localhost:9001/ | 用户名 `.env` 中 `MINIO_ACCESS_KEY`（默认 `miaotong`），密码 `MINIO_SECRET_KEY`（默认 `miaotong2024minio`） |
| RabbitMQ AMQP | localhost:5672 | 用户名 `miaotong`，密码 `RABBITMQ_PASSWORD` |
| RabbitMQ 管理 | http://localhost:15672/ | 用户名 `miaotong`，密码 `RABBITMQ_PASSWORD` |
| Elasticsearch | http://localhost:9200/ | 无认证（单节点模式），仅 docker 网络内部 + 可选主机端口 |
| Docling | http://localhost:5001/ | AI 文档解析（需 `DOCLING_ENABLED=true`） |
| OCR | http://localhost:5002/ | OCR 服务（需 `OCR_ENABLED=true`） |
| Yjs 健康 | http://localhost:1234/health | - |

**默认登录**：工号 `10000000` / 密码 `123456`（首次部署后需按第 4 步重置）

> **关于 V3 迁移脚本**：当前 `miaotongdoc.jar` 中 V3 迁移脚本的 employee_id 设为 `10000001`，密码哈希为损坏值。第 4 步的 UPDATE 会将工号改为 `10000000` 并用正确哈希重置密码。若重新构建 JAR（`mvn package`），V3 脚本已更新为 `10000000` 和正确哈希，首次部署后仍需重置密码。

---

## 相关文档

- [CLAUDE.md](CLAUDE.md) - 项目开发参考手册（架构、API、数据库设计等）
- [MiaotongDoc-Docker/README.md](MiaotongDoc-Docker/README.md) - Docker 部署基础说明
- [MiaotongDoc-Editor/README.md](MiaotongDoc-Editor/README.md) - 编辑器镜像构建说明

---

*本文档由 Claude Code 维护，基于 2026-07-18 实际部署复盘整理。*
