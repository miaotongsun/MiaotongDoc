# 镜像导出速查（外网 → 内网）

> ✅ **推荐用一键脚本**：`MiaotongDoc-Docker/export-images.sh`（自动校验镜像存在性、磁盘空间、生成清单与 SHA256）
> 📖 环境依赖与完整要求见 **[offline-env-requirements.md](offline-env-requirements.md)**
>
> ⚠️ 本文 2026-08-10 已按实际 `docker-compose.yml` 核对更正：删除了不存在的 `httpd:alpine`，尺寸改为实测值。

---

## 方式 A：一键脚本（推荐）

```bash
cd MiaotongDoc-Docker

./export-images.sh                    # 核心包（11 镜像，tar 约 4.0GB）
./export-images.sh --all              # 核心 + Tesseract + Docling（约 8.1GB）
./export-images.sh --with-docling     # 核心 + Docling
./export-images.sh -o /d/Docker       # 指定输出目录
./export-images.sh --no-compress      # 跳过 gzip（镜像层已压缩，增益有限）
```

输出到 `offline-package/`：镜像 tar + `manifest.txt`（清单与导入步骤）+ `SHA256SUMS`。

脚本会自动拦截：镜像缺失、磁盘不足、非 x86_64 架构告警。

---

## 方式 B：手工命令

### 核心包（默认启动必需，11 个镜像）

```bash
docker save -o /d/Docker/miaotongdoc-core.tar \
  miaotongdoc-editor:latest \
  miaotongdoc-docker-ocr-paddle:latest \
  miaotongdoc-docker-yjs-server:latest \
  elasticsearch:8.11.0 \
  postgres:12 \
  eclipse-temurin:17-jre \
  rabbitmq:3-management \
  minio/minio:latest \
  nginx:latest \
  redis:7-alpine \
  alpine:latest
```

### 可选镜像（按 profile 启用）

```bash
# Tesseract 多语言兜底（--with-ocr 时需要）
docker save -o /d/Docker/miaotongdoc-ocr.tar miaotongdoc-docker-ocr:latest

# Docling 结构化解析（--with-docling 时需要，最大）
docker save -o /d/Docker/miaotongdoc-docling.tar miaotongdoc-docker-docling:latest
```

### 校验和（强烈建议）

```bash
cd /d/Docker && sha256sum *.tar > SHA256SUMS
```

---

## 尺寸参考（2026-08-10 实测）

| 包 | tar 实测 | 解压后磁盘占用 |
|---|---|---|
| 核心包（11 镜像） | **4.0 GB** | ~15.4 GB |
| + Tesseract | +0.26 GB | +1.07 GB |
| + Docling | +3.8 GB | +12 GB |
| **全量** | **~8.1 GB** | **~28.5 GB** |

> `docker images` 显示的是解压后占用（含共享层重复计算），`docker save` 的 tar 是压缩层实际字节数。
> **传输按 tar 算，磁盘按解压后算。**
>
> gzip 收益有限（镜像层本已压缩），4.0GB → 约 3.5GB。赶时间可用 `--no-compress`。

---

## ❌ 不需要携带的镜像

| 镜像 | 原因 |
|---|---|
| `httpd:alpine` | **全项目零引用**（旧版本文档误列，2026-08-10 核实删除） |
| `python:3.11` / `python:3.11-slim` | 仅 docling/ocr 的构建期 base；内网不构建则不需要 |
| `redis:latest` | 项目用的是 `redis:7-alpine` |
| `eclipse-temurin:8-jdk` | 项目用的是 `17-jre` |

---

## 内网导入

```bash
# 1. 校验完整性
sha256sum -c SHA256SUMS

# 2. 加载（gzip 过则先 gunzip）
docker load -i miaotongdoc-core.tar

# 3. 核对镜像名与数量
docker images

# 4. 启动（必须用 deploy.sh，不能裸 docker compose up -d）
cd MiaotongDoc-Docker
./deploy.sh start
./deploy.sh health
```

---

## ⚠️ 三个必看的坑

**1. 部署目录名必须是 `MiaotongDoc-Docker`**
compose 无 `name:` 字段、`.env` 无 `COMPOSE_PROJECT_NAME`，`miaotongdoc-docker-*` 这 4 个镜像名由目录名推导。改名会导致 compose 找不到镜像 → 尝试联网构建 → 离线环境失败。
解法：保持目录名，或 `echo "COMPOSE_PROJECT_NAME=miaotongdoc-docker" >> .env`。

**2. 不要拷贝 `data/` 目录**
13 个子目录全是 bind mount，含外网测试期的 pgdata / MinIO 对象 / ES 索引。内网只需创建空目录结构：
```bash
mkdir -p data/{config,documents,logs,pgdata,redis,rabbitmq,minio,elasticsearch,yjs,editor,editor-cache,docling-cache,paddleocr-cache}
```

**3. 必须用 `./deploy.sh start`**
editor 要先于 web-server 启动，否则 Flyway V9 因 `task_result` 表不存在而失败。裸 `docker compose up -d` 不保证顺序。

---

## 完整流程

| 步骤 | 操作 | 位置 |
|---|---|---|
| 1 | 构建镜像 `docker compose --profile all build` | 外网 |
| 2 | 构建前端 `npm run build` → `app/web/dist/` | 外网 |
| 3 | 构建后端 `mvn clean package -DskipTests` → `app/server/` | 外网 |
| 4 | 导出镜像 `./export-images.sh --all` | 外网 |
| 5 | 拷贝 `offline-package/` + `MiaotongDoc-Docker/`（不含 data/）+ `setup-linux-host.sh` | U 盘 / 内网共享 |
| 6 | 环境自检（见 [offline-env-requirements.md §11](offline-env-requirements.md)） | 内网 |
| 7 | 宿主机初始化 `sudo ./setup-linux-host.sh --ntp-server <内网NTP>` | 内网 |
| 8 | 加载镜像 `docker load -i *.tar` | 内网 |
| 9 | 配置 `.env`（密码 + JWT 密钥 + CORS） | 内网 |
| 10 | 启动 `./deploy.sh start` + 重置 admin 密码（DEPLOY.md 第 4 步） | 内网 |
