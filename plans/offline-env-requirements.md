---
title: 内网离线部署 — 环境依赖清单
status: 生效中
created: 2026-08-10
updated: 2026-08-10
owner: 部署/运维
---

# 内网离线部署 — 环境依赖清单

> **用途**：交付给内网运维方的**硬性环境要求**。部署前逐项确认，任一项不满足需先协商解决。
> **配套文档**：[offline-export-command.md](offline-export-command.md)（镜像导出）· [../DEPLOY.md](../DEPLOY.md)（部署步骤）
>
> ⚠️ 本文所有数值均从 `MiaotongDoc-Docker/docker-compose.yml` 与 `setup-linux-host.sh` 实测倒推，非经验估算。

---

## 📊 状态摘要

| 项 | 值 |
|---|---|
| 依赖项总数 | 26 |
| 阻断级（❌ 不满足无法部署） | 9 |
| 建议级（⚠️ 影响稳定性） | 11 |
| 参考级（ℹ️ 可选优化） | 6 |
| 已知待修 | 7（**5 项已修**，2 项待定） |
| 配套脚本 | export-images.sh（新增）· setup-linux-host.sh（已修） |
| 最后核对 | 2026-08-10（对照 compose 实际配置 + 导出实测） |

---

## 0. 部署前必答的 3 个问题

这 3 项决定方案是否成立，**必须在导出镜像前问清楚**：

| # | 问题 | 若答案不符的后果 |
|---|---|---|
| 1 | **CPU 架构是 x86_64 还是 ARM64（鲲鹏/飞腾/海光）？** | ARM 则本套 tar 包**全部作废**，13 个镜像需在 ARM 机重新构建；editor(7.2GB) 与 PaddleOCR 的 ARM 可用性未验证，风险极高 |
| 2 | **Docker Compose 是 V1（`docker-compose`）还是 V2（`docker compose`）？** | V1 **必然启动失败**，见 §2 |
| 3 | **宿主机网段 `172.20.0.0/16` 是否已被占用？** | 冲突则所有容器无法创建网络 |

---

## 1. 硬件要求

| 项 | 最低 | 推荐 | 依据 |
|---|---|---|---|
| **CPU 架构** | **x86_64 / amd64** ❌ | x86_64 | 镜像不跨架构；`docker save` 产物绑定架构 |
| CPU 核数 | 4 核 | 8 核+ | 16 容器并发；PaddleOCR/Docling 为 CPU 密集 |
| 内存 | **16 GB** ❌ | 32 GB | 见下方内存分解 |
| 磁盘 | **200 GB** ❌ | 500 GB SSD | 见下方磁盘分解 |
| 磁盘类型 | — | SSD/NVMe | ES 与 PostgreSQL 对 IOPS 敏感 |

### 内存分解（实测依据）

| 服务 | 常驻内存 | 说明 |
|---|---|---|
| Elasticsearch | ~1.2 GB | `ES_JAVA_OPTS=-Xms512m -Xmx512m`（compose:420）+ 堆外 |
| web-server (JVM) | 1.5–2.5 GB | Spring Boot + 文档处理 |
| editor ×3 实例 | 3–6 GB | 每实例 1–2 GB，MTOffice docservice |
| PostgreSQL | 0.5–1 GB | |
| PaddleOCR | 1.5–2.5 GB | 模型常驻，默认启动 |
| Docling（可选） | **3–4 GB** | 启用 `--with-docling` 时额外占用 |
| Redis/RabbitMQ/MinIO/nginx/yjs | ~1 GB | 合计 |
| **合计（不含 Docling）** | **~12 GB** | 故最低 16 GB |
| **合计（含 Docling）** | **~16 GB** | 故推荐 32 GB |

> compose 头部注释明确：**除 ES 外所有容器不设内存硬限，跟随宿主机伸缩**。内存不足时由内核 OOM 随机杀容器，表现为"服务莫名重启"。

### 磁盘分解

| 用途 | 空间 | 说明 |
|---|---|---|
| Docker 镜像（不含 Docling） | ~15.4 GB | 见 §6 镜像清单（解压后占用） |
| Docker 镜像（含 Docling） | ~28.5 GB | Docling 单镜像 12 GB |
| 传输用 tar 包（可事后删） | ~4 GB（核心）/ ~8 GB（全量） | 实测值，`docker load` 后可删除 |
| `data/` 业务数据 | 按需，建议 ≥100 GB | 文档/PDF/版本/MinIO 对象 |
| Docker 运行时与日志 | ~20 GB | 已配 logrotate 限制 |
| swap | 16 GB | `setup-linux-host.sh` 默认创建 |

---

## 2. Docker 与 Compose 版本（最关键）

| 组件 | 最低版本 | 推荐版本 | 验证命令 |
|---|---|---|---|
| **Docker Engine** | **20.10.0** ❌ | 24.0.x / 25.0.x | `docker --version` |
| **Docker Compose** | **v2.0.0（必须 V2）** ❌ | v2.24.x+ | `docker compose version` |
| containerd | 1.6+ | 1.7.x | `containerd --version` |

### ⛔ 红线：绝不能用 Compose V1

带横杠的 `docker-compose`（Python 版，1.x）**必然失败**，原因有三，任一即致命：

| 项目实际用法 | 位置 | V1 行为 |
|---|---|---|
| `profiles: ["docling","all"]` | compose:470, 514 | V1 完全不支持该字段 |
| `depends_on: { condition: service_healthy }` | compose:191-201 等 | V1 不支持长语法 condition |
| compose 文件**无 `version:` 字段** | 文件头 | V1 报错要求 version |
| `deploy.sh` 全文调用 `docker compose`（无横杠） | deploy.sh:85-130 | V1 无此子命令 |

**确认方法**：
```bash
docker compose version
# ✅ 期望：Docker Compose version v2.x.x
# ❌ 若提示 "docker: 'compose' is not a docker command" → 是 V1，必须升级
```

### 为什么不建议装最新版

外网构建机为 Docker 29.6 / Compose v5.3，但内网**建议 24.0/25.0**：
- 镜像 tar 格式向下兼容良好，24/25 加载 29 导出的镜像无问题
- 29.x 在部分 CentOS 7 / 麒麟内核上 `userland-proxy: false`（daemon.json 已配）存在兼容问题
- 24.0/25.0 为社区验证充分的稳定线

---

## 3. 操作系统与内核

| 项 | 要求 | 验证 | 级别 |
|---|---|---|---|
| 发行版 | CentOS 7.9+ / RHEL 8+ / Ubuntu 20.04+ / 麒麟 V10 / 统信 UOS | `cat /etc/os-release` | ❌ |
| 内核 | **≥ 3.10**，建议 4.18+ / 5.x | `uname -r` | ❌ |
| **init 系统** | **必须 systemd** | `systemctl --version` | ❌ |
| 存储驱动 | **overlay2** | `docker info \| grep "Storage Driver"` | ❌ |
| cgroup | v1 或 v2 均可，需确认版本 | `stat -fc %T /sys/fs/cgroup` | ⚠️ |
| 文件系统 | ext4 / xfs（xfs 需 `ftype=1`） | `df -T` | ⚠️ |
| SELinux | 建议 permissive 或配好策略 | `getenforce` | ⚠️ |

**说明**：
- `setup-linux-host.sh:66` 硬性检查 systemd，无则退出
- daemon.json 写死 `"storage-driver": "overlay2"`（setup:124），devicemapper 老机器会启动失败
- xfs 若 `ftype=0`，overlay2 无法使用（CentOS 7 默认分区常见坑），检查：`xfs_info / | grep ftype`

---

## 4. 内核参数与系统限制

`setup-linux-host.sh` 自动配置以下项。**若运维不跑该脚本，需手工配等价值**：

| 参数 | 值 | 来源 | 影响 |
|---|---|---|---|
| `vm.swappiness` | 10 | setup:92 | 减少换页 |
| `vm.overcommit_memory` | 1 | setup:93 | Redis 要求 |
| `net.core.somaxconn` | 4096 | setup:94 | 高并发连接 |
| `fs.file-max` | 2097152 | setup:101 | 句柄总数 |
| `nofile` (soft/hard) | 65536 | setup:80-81 | 16 容器 + ES |
| `nproc` (soft/hard) | 16384 | setup:82-83 | |

### ⚠️ 脚本缺失项：`vm.max_map_count`

**Elasticsearch 8.x 要求 `vm.max_map_count >= 262144`，当前 `setup-linux-host.sh` 未配置。**
默认值 65530 在多数发行版下 ES 可能启动失败（报 `max virtual memory areas vm.max_map_count [65530] is too low`）。

**部署前手工补上**：
```bash
echo "vm.max_map_count = 262144" >> /etc/sysctl.d/99-miaotongdoc.conf
sysctl -w vm.max_map_count=262144
```

---

## 5. 网络与端口

### 5.1 Docker 内部网段（阻断级）

| 项 | 值 | 位置 |
|---|---|---|
| 桥接网络名 | `mtd-net` | compose 末尾 |
| **子网** | **`172.20.0.0/16`（写死）** | compose 末尾 |

各容器为**静态 IP**（172.20.0.10 ~ 172.20.0.96）。若宿主机已有路由/VPN/其他 Docker 网络占用 `172.20.x.x`，容器无法创建。

**冲突检查**：
```bash
ip route | grep 172.20
docker network ls && docker network inspect bridge | grep Subnet
```
冲突时需改 compose 末尾 subnet + 全部 12 处 `ipv4_address`，**改动面大，务必提前确认**。

### 5.2 宿主机需放行的端口

| 端口 | 服务 | 对外必需 | firewalld 已配 |
|---|---|---|---|
| 80 | nginx（用户入口） | ✅ 必需 | ✅ setup:165 |
| 9004 | web-server API | ✅ 必需 | ✅ setup:166 |
| 1234 | yjs 协同 | ✅ 必需 | ✅ setup:168 |
| 9000/9001 | MinIO / 控制台 | 视情况 | ✅ setup:167 |
| 15672 | RabbitMQ 管理台 | ⚠️ **建议仅限内网** | ✅ setup:170 |
| 5432 | PostgreSQL | ❌ 建议不对外 | 未放行 |
| 6379 | Redis | ❌ 建议不对外 | 未放行 |
| 5672 | RabbitMQ AMQP | ❌ 建议不对外 | 未放行 |
| 5003 | PaddleOCR | ❌ 仅容器内 | 未放行 |

> ⚠️ compose 中 5432/6379/5672 **均有 `ports:` 映射到宿主机**（compose:39/68/93）。生产环境建议注释掉这些映射，仅保留容器网络访问。

### 5.3 外网访问（离线要求）

| 项 | 要求 |
|---|---|
| 公网出口 | **不需要**。全部镜像/模型离线，OCR 模型已烧入镜像 |
| DNS | 内网 DNS 即可，容器间用 compose 服务名解析 |
| NTP | ⚠️ **需要内网 NTP 源**。setup 脚本装 chrony（setup:137-142），默认指向公网池，**离线环境须改为内网 NTP 服务器**，否则时间漂移导致 JWT 校验失败 |

**离线 NTP 配置**：
```bash
# /etc/chrony.conf 中替换 pool 行为内网源
server <内网NTP地址> iburst
```

---

## 6. 镜像清单（离线导入用）

以下为 compose **实际引用**的全部镜像，共 13 个。

> ⚠️ **两套尺寸口径，别混淆**（2026-08-10 实测）：
> - `docker images` 显示的是**解压后磁盘占用**（含跨镜像共享层，会重复计算）
> - `docker save` 出的 tar 是**压缩层的实际字节数**，明显更小
> - 采购磁盘看前者，估传输量看后者

| 镜像 | 磁盘占用 | tar 实测 | 启动方式 | compose 位置 |
|---|---|---|---|---|
| `miaotongdoc-editor:latest` | 7.22 GB | 1.98 GB | 默认（×3 实例） | 213/264/310 |
| `miaotongdoc-docker-ocr-paddle:latest` | 2.81 GB | 0.74 GB | **默认启动** | 543 |
| `elasticsearch:8.11.0` | 2.17 GB | 0.69 GB | 默认 | 414 |
| `postgres:12` | 598 MB | 0.14 GB | 默认 | 36 |
| `rabbitmq:3-management` | 392 MB | 0.11 GB | 默认 | 90 |
| `eclipse-temurin:17-jre` | 430 MB | 0.10 GB | 默认（web-server） | 116 |
| `minio/minio:latest` | 241 MB | 0.06 GB | 默认 | 386 |
| `nginx:latest` | 240 MB | 0.06 GB | 默认 | 9 |
| `miaotongdoc-docker-yjs-server:latest` | 233 MB | 0.05 GB | 默认 | 440 |
| `redis:7-alpine` | 57.8 MB | 0.02 GB | 默认 | 65 |
| `alpine:latest` | 13 MB | ~0 | 默认（cache-cleaner/logrotate） | 357/372 |
| **小计（默认必需）** | **~15.4 GB** | **4.0 GB（实测）** | | |
| `miaotongdoc-docker-ocr:latest` | 1.07 GB | ~0.26 GB | 可选 `--with-ocr` | 512 |
| `miaotongdoc-docker-docling:latest` | 12 GB | ~3.8 GB | 可选 `--with-docling` | 468 |
| **合计（全量）** | **~28.5 GB** | **~8.1 GB** | | |

**传输量结论**：核心包 tar 仅 **4.0 GB**，gzip 后约 3.5 GB（镜像层本已压缩，gzip 增益有限）。**一个 U 盘即可**，无需按旧文档预估的 8-10 GB 准备。全量含 Docling 约 8.1 GB。

**磁盘规划仍按解压后算**：内网 `docker load` 后实际占用约 15.4 GB（全量 28.5 GB），§1 的磁盘要求不变。

### ❌ 不需要携带的镜像

| 镜像 | 原因 |
|---|---|
| `httpd:alpine` | **全项目零引用**（旧文档误列，已核实） |
| `python:3.11` / `python:3.11-slim` | 仅为 docling/ocr 的构建期 base，内网不构建则不需要 |
| `redis:latest` | 项目用的是 `redis:7-alpine` |
| `eclipse-temurin:8-jdk` | 项目用的是 `17-jre` |

---

## 7. 除镜像外必须携带的文件

| 内容 | 来源 | 注意 |
|---|---|---|
| `MiaotongDoc-Docker/` 整个目录 | 源码库 | **目录名不可改**，见 §8 |
| `app/web/dist/` | 外网 `npm run build` 产物 | 内网**不构建**（无 devDependencies） |
| `app/server/miaotongdoc.jar` | 外网 `mvn clean package -DskipTests` | |
| `.env` | 由 `.env.example` 复制并改密码 | 见 §9 |
| `setup-linux-host.sh` | 项目根目录 | 首次部署必跑 |
| Docker 离线安装包 | 见 §10 | 内网无外网，Docker 本身也要离线装 |

### ⚠️ 不要携带 `data/` 目录

`data/` 下 13 个子目录全部是 **bind mount**（非 named volume），包含外网测试期的 pgdata / MinIO 对象 / ES 索引。带过去会把测试数据污染进生产。

**正确做法**：只创建空目录结构：
```bash
cd MiaotongDoc-Docker
mkdir -p data/{config,documents,logs,pgdata,redis,rabbitmq,minio,elasticsearch,yjs,editor,editor-cache,docling-cache,paddleocr-cache}
```

---

## 8. ⛔ 目录名依赖（隐蔽陷阱）

compose 文件**未设置 `name:` 字段**，`.env` 中也**无 `COMPOSE_PROJECT_NAME`**。

这意味着 `miaotongdoc-docker-yjs-server`、`miaotongdoc-docker-ocr-paddle`、`miaotongdoc-docker-docling`、`miaotongdoc-docker-ocr` 这 4 个镜像名是**由所在目录名 `MiaotongDoc-Docker` 自动推导**的。

**后果**：内网若把目录改名（如放到 `/opt/mtdoc/`），compose 会去找 `mtdoc-yjs-server:latest` → 找不到 → 尝试联网构建 → 离线环境直接失败。

**两个解法（二选一）**：
```bash
# 方案 A：保持目录名为 MiaotongDoc-Docker（推荐，零改动）

# 方案 B：在 .env 中显式固定项目名
echo "COMPOSE_PROJECT_NAME=miaotongdoc-docker" >> .env
```

---

## 9. 配置项要求（`.env`）

| 变量 | 要求 | 级别 |
|---|---|---|
| `DB_PASSWORD` | ≥ 8 位 | ❌ |
| `REDIS_PASSWORD` | ≥ 8 位 | ❌ |
| `RABBITMQ_PASSWORD` | ≥ 8 位 | ❌ |
| `APP_JWT_SECRET` | **≥ 32 位** | ❌ |
| `EDITOR_JWT_SECRET` | **≥ 32 位**，与编辑器侧一致 | ❌ |
| `CORS_ORIGINS` | 改为内网实际域名/IP | ❌ |
| `STORAGE_TYPE` | `minio`（默认）或 `local` | ℹ️ |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 生产必改默认值 | ⚠️ |

---

## 10. Docker 本身的离线安装

内网无外网，需提前下载安装介质。

### 方式 A：官方静态二进制（不依赖发行版仓库，推荐）
```
https://download.docker.com/linux/static/stable/x86_64/docker-24.0.9.tgz
https://github.com/docker/compose/releases/download/v2.24.6/docker-compose-linux-x86_64
```
安装：解压 `docker-*.tgz` 到 `/usr/bin/`，compose 二进制放 `/usr/libexec/docker/cli-plugins/docker-compose` 并 `chmod +x`。

### 方式 B：发行版离线包
- **CentOS/RHEL**：需 `containerd.io`、`docker-ce`、`docker-ce-cli`、`docker-compose-plugin`、`docker-buildx-plugin` 五个 rpm + 依赖（`yum install --downloadonly` 在同版本外网机上打包）
- **Ubuntu/Debian**：对应 deb 包
- **麒麟/统信**：优先用系统自带源的 docker 包，注意确认 compose 是否为 V2

---

## 11. 部署前自检脚本

交给运维在内网机执行，逐项打印实际值：

```bash
#!/bin/bash
echo "=== MiaotongDoc 内网环境自检 ==="
echo "[架构]     $(uname -m)                # 期望 x86_64"
echo "[内核]     $(uname -r)                # 期望 >= 3.10"
echo "[系统]     $(. /etc/os-release && echo $PRETTY_NAME)"
echo "[systemd]  $(systemctl --version 2>/dev/null | head -1 || echo '缺失 ❌')"
echo "[CPU]      $(nproc) 核               # 期望 >= 4"
echo "[内存]     $(free -g | awk '/Mem:/{print $2}') GB   # 期望 >= 16"
echo "[磁盘]     $(df -h / | awk 'NR==2{print $4}') 可用   # 期望 >= 200G"
echo "[Docker]   $(docker --version 2>/dev/null || echo '未安装 ❌')"
echo "[Compose]  $(docker compose version 2>/dev/null || echo '非 V2 ❌')"
echo "[存储驱动] $(docker info 2>/dev/null | grep 'Storage Driver')"
echo "[cgroup]   $(stat -fc %T /sys/fs/cgroup)"
echo "[map_count]$(sysctl -n vm.max_map_count)   # 期望 >= 262144"
echo "[nofile]   $(ulimit -n)               # 期望 >= 65536"
echo "[网段冲突] $(ip route | grep -c '172.20') 条 172.20 路由  # 期望 0"
echo "[xfs ftype]$(xfs_info / 2>/dev/null | grep -o 'ftype=[01]' || echo 'N/A(非xfs)')"
echo "[时间]     $(date)  同步状态: $(chronyc tracking 2>/dev/null | head -1 || echo '未装 chrony')"
```

---

## 12. 交付确认表（请运维签字）

| # | 确认项 | 期望 | 实际 | 通过 |
|---|---|---|---|---|
| 1 | CPU 架构 | x86_64 | | ☐ |
| 2 | 内存 | ≥ 16 GB | | ☐ |
| 3 | 磁盘可用 | ≥ 200 GB | | ☐ |
| 4 | Docker Engine | ≥ 20.10 | | ☐ |
| 5 | Docker Compose | **V2** | | ☐ |
| 6 | 存储驱动 | overlay2 | | ☐ |
| 7 | init 系统 | systemd | | ☐ |
| 8 | 内核 | ≥ 3.10 | | ☐ |
| 9 | `172.20.0.0/16` 未占用 | 无冲突 | | ☐ |
| 10 | `vm.max_map_count` | ≥ 262144 | | ☐ |
| 11 | 端口 80/9004/1234 可用 | 未被占 | | ☐ |
| 12 | 内网 NTP 源可达 | 可同步 | | ☐ |
| 13 | 部署目录名为 `MiaotongDoc-Docker` | 是 | | ☐ |

---

## 13. 已知待修项（本次核对发现）

| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| 1 | `vm.max_map_count` 未配置，ES 可能启动失败 | `setup-linux-host.sh` §2 | ✅ **已修** 2026-08-10 |
| 2 | Docker 未安装时仅 warn 不中断，后续 `systemctl restart docker` 静默失败 | `setup-linux-host.sh:109-111` | ✅ **已修**（改 exit 1 + 补 Engine≥20.10 与 Compose V2 版本校验） |
| 3 | 颜色变量 `GREEN='\033[0:32m'` 冒号应为分号 | `setup-linux-host.sh:53` | ✅ **已修** |
| 4 | chrony 默认指向公网 NTP 池，离线环境不可用 | `setup-linux-host.sh:137-142` | ✅ **已修**（新增 `--ntp-server` 参数，自动注释公网 pool 并备份原配置） |
| 5 | 5432/6379/5672 端口映射到宿主机 | `docker-compose.yml:39/68/93` | ⬜ 待定：生产建议注释，需确认是否有外部直连需求 |
| 6 | `offline-export-command.md` 含不存在的 `httpd:alpine`、尺寸估算偏差大 | 该文档 | ✅ **已修** 2026-08-10（重写，删 httpd、改实测值、补三大坑） |
| 7 | 无 `COMPOSE_PROJECT_NAME`，镜像名依赖目录名 | `.env` | ⬜ 待定：见 §8，二选一方案 |

## 14. 配套工具

| 工具 | 路径 | 用途 |
|---|---|---|
| 镜像导出脚本 | `MiaotongDoc-Docker/export-images.sh` | 一键导出 + 镜像存在性校验 + 磁盘预检 + 清单 + SHA256 |
| 宿主机初始化 | `setup-linux-host.sh` | 9 项基线配置，新增 `--ntp-server` |
| 分阶段启动 | `MiaotongDoc-Docker/deploy.sh` | A→F 分阶段，保证 editor 先于 web-server |
| 环境自检 | 本文 §11 | 交运维执行，逐项打印实际值 |

---

## 相关文档

- [offline-export-command.md](offline-export-command.md) — 镜像导出命令
- [2026-07-26-offline-deployment.md](2026-07-26-offline-deployment.md) — 离线部署方案设计
- [2026-08-09-ocr-models-offline-deploy.md](2026-08-09-ocr-models-offline-deploy.md) — OCR 模型离线化
- [../DEPLOY.md](../DEPLOY.md) — 部署步骤与故障排查
