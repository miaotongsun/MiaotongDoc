# Podman on Windows 使用攻略

> **维护者**: Claude Code | **最后更新**: 2026-08-15
> **适用版本**: Podman 5.4.0 + Podman Desktop 1.29.1 + Windows 11 WSL2
> **血泪经验**: 三天踩坑总结，避免后人重蹈覆辙

---

## 📋 目录

- [TL;DR — 速查](#tldr--速查)
- [为什么用 Podman 不用 Docker Desktop](#为什么用-podman-不用-docker-desktop)
- [安装](#安装)
- [数据存储位置（D 盘配置）](#数据存储位置d-盘配置)
- [日常操作](#日常操作)
- [端口配置](#端口配置)
- [踩坑大全（按出现顺序）](#踩坑大全按出现顺序)
- [和 Docker Desktop 的核心差异](#和-docker-desktop-的核心差异)
- [故障排查](#故障排查)

---

## TL;DR — 速查

```bash
# 启动项目
cd "D:\tiany\stycode\MiaotongDoc\MiaotongDoc-Docker"
./podman-deploy.sh start

# 或者直接用 docker-compose
docker-compose -f docker-compose.yml up -d

# 访问
# 前端: http://localhost
# 后端: http://localhost:9004
# MinIO: http://localhost:9001
# RabbitMQ: http://localhost:15672

# 停止
./podman-deploy.sh stop

# 状态
./podman-deploy.sh status
```

**关键铁律**：
1. **用 Podman 5.4.0，不要用 6.0.x**（6.0.x 在 Windows WSL 上不工作）
2. **数据放 D 盘用 `wsl --export/import`，不要用 junction 软链接**
3. **`podman compose` = `docker-compose.exe`**（5.4.0 内部就是调 docker-compose.exe）
4. **端口偏移是 docker-compose.yml 配置，不是 Podman 自动行为**

---

## 为什么用 Podman 不用 Docker Desktop

| 维度 | Docker Desktop | Podman 5.4.0 |
|---|---|---|
| 商业授权 | 企业 ≥ 250 人需付费 | 完全开源免费 |
| daemon | 常驻 dockerd | 无 daemon（按需启动） |
| rootless | 默认 rootful | 默认 rootless，可切 rootful |
| WSL 集成 | 自带定制 WSL distro | 用标准 WSL2 + Fedora |
| 兼容 docker-compose | ✅ 原生 | ✅ 通过 named pipe 兼容 |
| GUI | Docker Desktop GUI | Podman Desktop GUI |
| 数据隔离 | 独立 vhdx | 独立 vhdx（可放 D 盘） |

**实际感受**：Docker Desktop 开箱即用（所有坑都填好了），Podman 5.4.0 配好后体验等价，但**初次配置要趟过几个坑**（见下文）。

---

## 安装

### 步骤 1：装 Podman 5.4.0 Engine

**下载 MSI**：
https://github.com/podman-container-tools/podman/releases/download/v5.4.0/podman-5.4.0-setup.exe

**双击安装**（必须点 UAC 弹窗"是"，选 Complete 安装）：
- 安装到 `C:\Program Files\RedHat\Podman\`
- 包含 `podman.exe`、`win-sshproxy.exe`、`gvproxy.exe`

**验证**：
```bash
"/c/Program Files/RedHat/Podman/podman.exe" --version
# 输出: podman version 5.4.0
```

⚠️ **不要用 Podman 6.0.x**：6.0.x 在 Windows WSL 上有严重兼容问题（cgroup v2 强制、machine.State() 误判、netavark nftables 不工作），完全不可用。

### 步骤 2：装 Podman Desktop 1.29.1（GUI，可选但推荐）

**用 winget 装（比 GitHub 下载快很多）**：
```powershell
winget install RedHat.Podman-Desktop --silent --accept-package-agreements --accept-source-agreements
```

或者从 GitHub 下载（慢）：
https://github.com/podman-desktop/podman-desktop/releases/download/v1.29.1/podman-desktop-1.29.1-setup-x64.exe

装到 `C:\Users\tiany\AppData\Local\Programs\Podman Desktop\`

### 步骤 3：初始化 Podman Machine

```bash
# 初始化（下载 Fedora rootfs，约 800MB，国内 quay.io 慢）
"/c/Program Files/RedHat/Podman/podman.exe" machine init

# 启动
"/c/Program Files/RedHat/Podman/podman.exe" machine start

# 切 rootful（项目需要 root 权限）
"/c/Program Files/RedHat/Podman/podman.exe" machine stop
"/c/Program Files/RedHat/Podman/podman.exe" machine set --rootful
"/c/Program Files/RedHat/Podman/podman.exe" machine start
```

**验证**：
```bash
"/c/Program Files/RedHat/Podman/podman.exe" info | grep -E "rootless|cgroupVersion"
# 应输出: rootless: false  +  cgroupVersion: v2
```

---

## 数据存储位置（D 盘配置）

默认 Podman 数据在 C 盘：
- `C:\Users\tiany\.local\share\containers\podman\machine\wsl\wsldist\podman-machine-default\ext4.vhdx`（WSL distro，包含所有镜像和容器层）
- `C:\Users\tiany\.config\containers\podman\machine\wsl\podman-machine-default.json`（machine 配置）
- `C:\Users\tiany\.local\share\containers\podman\machine\machine` 和 `machine.pub`（SSH 密钥）

### 迁移到 D 盘（推荐做法）

**用 `wsl --export` + `wsl --import`**（不要用 junction 软链接，会报 "path not found"）：

```powershell
# 1. 停 podman machine
& "C:\Program Files\RedHat\Podman\podman.exe" machine stop
wsl --shutdown

# 2. 导出 WSL distro 到 D 盘
mkdir D:\containers\wsl
wsl --export podman-machine-default D:\containers\wsl\export.tar

# 3. 卸载旧 distro
wsl --unregister podman-machine-default

# 4. 复制 SSH 密钥 + 配置到 D 盘
mkdir D:\containers\podman\machine
copy C:\Users\tiany\.local\share\containers\podman\machine\machine D:\containers\podman\machine\
copy C:\Users\tiany\.local\share\containers\podman\machine\machine.pub D:\containers\podman\machine\
copy C:\Users\tiany\.local\share\containers\podman\machine\port-alloc.* D:\containers\podman\machine\

# 5. 删 C 盘原目录，建 junction 指向 D 盘
rmdir /S /Q C:\Users\tiany\.local\share\containers
mklink /J C:\Users\tiany\.local\share\containers D:\containers

# 6. 把 WSL distro 重新导入到 D 盘
mkdir D:\containers\wsl\rootfs
wsl --import podman-machine-default D:\containers\wsl\rootfs D:\containers\wsl\export.tar

# 7. 启动
& "C:\Program Files\RedHat\Podman\podman.exe" machine start
```

迁移后所有数据（vhdx 12GB+、镜像层、容器文件系统）都在 D 盘。

⚠️ **不要用 junction 软链接迁移 vhdx**——Podman 拉镜像时通过软链接访问会报 `The system cannot find the path specified`。junction 只用于配置目录，vhdx 必须用 `wsl --import` 放到 D 盘。

---

## 日常操作

### 启动/停止项目

```bash
cd "D:\tiany\stycode\MiaotongDoc\MiaotongDoc-Docker"

# 启动所有 12 容器
./podman-deploy.sh start

# 停止（保留容器数据）
./podman-deploy.sh stop

# 重启
./podman-deploy.sh restart

# 状态
./podman-deploy.sh status

# 健康检查
./podman-deploy.sh health

# 查日志
./podman-deploy.sh logs web-server
```

### 直接用 docker-compose

```bash
# 启动
docker-compose -f docker-compose.yml up -d

# 单独启动某服务
docker-compose -f docker-compose.yml up -d postgres

# 看状态
docker-compose -f docker-compose.yml ps

# 看日志
docker-compose -f docker-compose.yml logs -f web-server

# 停止
docker-compose -f docker-compose.yml stop

# 完全清理（含网络）
docker-compose -f docker-compose.yml down
```

### podman 原生命令

```bash
# 列容器
podman ps

# 列所有（含停的）
podman ps -a

# 看容器日志
podman logs miaotongdoc-nginx

# 进入容器
podman exec -it miaotongdoc-server bash

# 看端口映射
podman port --all

# 看镜像
podman images

# 加载本地 tar 镜像
podman load -i C:\miaotongdoc-images\miaotongdoc-core.tar
```

### Podman Machine 管理

```bash
# 启动/停止 machine
podman machine start
podman machine stop

# 状态
podman machine list

# 切 rootful/rootless
podman machine stop
podman machine set --rootful       # 切 rootful
podman machine set --rootful=false # 切 rootless
podman machine start

# 删除 machine（会清掉所有容器和镜像！）
podman machine stop
podman machine rm -f

# 重建 machine
podman machine init
```

### 镜像管理

```bash
# 加载本地 tar 包到 Podman
podman load -i C:\miaotongdoc-images\miaotongdoc-core.tar      # 4.0GB
podman load -i C:\miaotongdoc-images\miaotongdoc-docling.tar   # 3.6GB
podman load -i C:\miaotongdoc-images\miaotongdoc-ocr.tar       # 0.25GB

# 列镜像
podman images

# 删镜像
podman rmi localhost/nginx:latest

# 导出镜像为 tar
podman save -o myimage.tar localhost/myimage:latest
```

---

## 端口配置

### 端口偏移的真相

**Podman 5.4.0 rootful 模式没有自动端口偏移**——容器 `-p 80:80` 直接 bind Windows 80。

之前看到的 `0.0.0.0:8080->80/tcp` 是 **docker-compose.yml 项目配置**（手动写 `8080:80`），不是 Podman 自动偏移。

### 修改端口

`docker-compose.yml` 里改 `ports` 配置（所有服务已改为标准端口）：

```yaml
nginx:
  ports:
    - "80:80"        # Windows 80 → 容器 80（前端 http://localhost）

web-server:
  ports:
    - "9004:9004"    # Windows 9004 → 容器 9004（后端 API）

postgres:
  ports:
    - "5432:5432"    # 标准端口

redis:
  ports:
    - "6379:6379"    # 标准端口（需先停本地 Redis 服务）
```

修改后重启容器：
```bash
docker-compose -f docker-compose.yml up -d
```

### 当前端口映射表

所有容器使用标准端口（无偏移）。网段 `172.25.0.0/16`（避开 WSL 默认 `172.20.32.0/20`）。

| 服务 | 容器端口 | Windows 端口 | 访问方式 |
|---|---|---|---|
| nginx (前端) | 80 | 80 | http://localhost |
| web-server (后端) | 9004 | 9004 | http://localhost:9004 |
| postgres | 5432 | 5432 | localhost:5432 |
| redis | 6379 | 6379 | localhost:6379 |
| minio API | 9000 | 9000 | http://localhost:9000 |
| minio 控制台 | 9001 | 9001 | http://localhost:9001 |
| rabbitmq AMQP | 5672 | 5672 | localhost:5672 |
| rabbitmq 管理 | 15672 | 15672 | http://localhost:15672 |
| yjs | 1234 | 1234 | localhost:1234 |
| ocr-paddle | 5003 | 5003 | localhost:5003 |
| docling | 5001 | 5001 | localhost:5001 |

**容器静态 IP**（网段 172.25.0.0/16）：

| 容器 | 静态 IP |
|---|---|
| nginx | 172.25.0.10 |
| postgres | 172.25.0.20 |
| redis | 172.25.0.30 |
| rabbitmq | 172.25.0.35 |
| web-server | 172.25.0.40 |
| editor | 172.25.0.50 |
| editor2 | 172.25.0.51 |
| editor3 | 172.25.0.52 |
| minio | 172.25.0.60 |
| elasticsearch | 172.25.0.70 |
| yjs | 172.25.0.80 |
| docling | 172.25.0.90 |
| ocr-paddle | 172.25.0.95 |
| ocr (Tesseract) | 172.25.0.96 |

---

## 踩坑大全（按出现顺序）

### 坑 1：Podman 6.0.2 在 Windows WSL 完全不能用

**症状**：
- `podman system service` 报 `Cgroups v1 not supported`
- `podman machine start` 报 exit 125 "machine did not transition into running state"
- `podman compose` 报 "machine not running but in state stopped"（即使 machine 在跑）
- netavark 报 `nft did not return successfully`
- Podman Desktop 显示 "No container engine"

**根因**：
- Podman 6.0.2 强制要求 cgroup v2，但 WSL 默认 cgroup v1
- `machine.State()` 在 WSL 上误判为 Stopped（WSL `--list` 的 UTF-16 输出解析问题）
- netavark 2.0 依赖 nftables，但 WSL 内核没 nft 模块
- win-sshproxy 启动失败，named pipe 没创建

**解决**：**降到 Podman 5.4.0**。5.4.0 没有这些问题。

### 坑 2：用 junction 软链接迁移数据失败

**症状**：`podman machine init` 报 `The system cannot find the path specified`

**根因**：Podman 通过 Go 的 `os.Stat` 访问 junction 路径，symlink 类型在拉镜像时解析失败。

**解决**：vhdx 文件用 `wsl --export` + `wsl --import` 移动到 D 盘，配置目录可以用 junction（但 vhdx 不行）。

### 坑 3：`podman compose` 报 "machine not running"

**症状**：`podman compose version` 报 "machine podman-machine-default is not running but in state stopped"

**根因**：Podman 6.0.2 的 `machine.State()` bug。5.4.0 没这个问题。

**解决**：用 5.4.0。如果是 6.0.2，设 `DOCKER_HOST=npipe:////./pipe/docker_engine` 绕过（但治标不治本）。

### 坑 4：win-sshproxy 没启动，named pipe 没创建

**症状**：`podman machine start` 输出 "could not start api proxy since expected pipe is not available"，docker-compose 连不上。

**根因**：
- Podman 6.0.2：win-sshproxy 启动逻辑有 bug
- Podman 5.4.0：machine state 不干净（之前 stop 失败留下的残留 .tid 文件）

**解决**：
1. 清理 `C:\Users\tiany\AppData\Local\Temp\podman\` 下所有文件
2. `podman machine stop` → `podman machine start`
3. 如果还不行，`wsl --shutdown` 后重启 machine

### 坑 5：SSH 端口不断变化

**症状**：每次 `podman machine start` 都报 "detected port conflict on machine ssh port [XXXX], reassigning"，端口一直在变。

**根因**：WSL 内 sshd 没正确释放端口，wslrelay 占着旧端口。

**解决**：
1. `wsl --shutdown` 完全停 WSL
2. 等 5 秒
3. `podman machine start`

### 坑 6：WSL 网络冲突（172.20 网段）

**症状**：容器能启动但 Windows 访问不到，或者 WSL 没外网。`docker-compose up` 报 `subnet 172.20.0.0/16 is already used on the host`。

**根因**：docker-compose.yml 原始配置用 `172.20.0.0/16` 网段，和 WSL 默认的 `172.20.32.0/20`（vEthernet WSL 网关）冲突。

**解决**：改 docker-compose.yml 网段到 `172.25.0.0/16`，所有 `ipv4_address` 从 `172.20.0.x` → `172.25.0.x`。

```yaml
networks:
  mtd-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.25.0.0/16
```

每个容器的 `ipv4_address` 也要改：
```yaml
networks:
  mtd-net:
    ipv4_address: 172.25.0.10   # 原来是 172.20.0.10
```

### 坑 7：Podman Desktop 显示 "No container engine"

**症状**：Podman Desktop GUI 显示无引擎，看不到容器。

**根因**：
- Podman Desktop 缓存了旧状态
- 或者 win-sshproxy 死了，named pipe 没了

**解决**：
1. 关闭 Podman Desktop（任务管理器结束所有 Podman Desktop 进程）
2. 确认 `podman machine list` 显示 "Currently running"
3. 重新打开 Podman Desktop

### 坑 8：容器启动后 curl 连不上（HTTP 000）

**症状**：`docker-compose up -d` 显示 Started，但 `curl http://localhost` 返回 000。

**根因**：
- wslrelay 进程是旧的（持有过期端口转发配置）
- 或者 `podman machine stop` 后容器被停了，但 docker-compose up 没真正重启

**解决**：
1. `podman machine stop` → `wsl --shutdown` → 等 5 秒 → `podman machine start`
2. `docker-compose -f docker-compose.yml up -d` 重启容器
3. 等 30 秒让服务完全启动

### 坑 9：端口偏移（误判 → 已解决）

**症状**：容器端口 80 映射到 Windows 8080，9004 映射到 19004 等。

**误判**：以为 Podman 自动偏移特权端口。

**真相**：docker-compose.yml 里项目配置就是 `8080:80`、`19004:9004`（当初 Docker Desktop 环境下避免冲突的配置）。**Podman 5.4.0 rootful 模式没有自动端口偏移**——直接 `-p 80:80` 可以 bind Windows 80。

**解决**：改 docker-compose.yml 为标准端口 `80:80`、`9004:9004`、`5432:5432`、`6379:6379` 等。Podman 5.4.0 rootful 可以直接 bind 特权端口（< 1024）。

**注意**：如果 Windows 本地有 Redis 服务（`redis-server.exe`）占着 6379，需要先停掉本地 Redis 服务（`Stop-Service Redis`），否则容器 Redis 绑 6379 会冲突。

### 坑 10：镜像下载从 quay.io 极慢

**症状**：`podman machine init` 下载 Fedora rootfs 速度 ~0.5MB/min，要几小时。

**根因**：quay.io 在国内没有 CDN，直连慢。

**解决**：
- 耐心等（首次 init 约 2-3 小时）
- 或者用 VPN/代理加速
- 之后 `podman load -i C:\miaotongdoc-images\*.tar` 加载本地镜像（不用再下）

---

## 和 Docker Desktop 的核心差异

### 端口转发

**Docker Desktop**：用 `docker-proxy` 用户态进程转发端口（不依赖 iptables/nftables）。

**Podman 5.4.0**：用 `wslrelay` 转发 WSL 内端口到 Windows（也不依赖 iptables/nftables，rootful 模式下 pasta 网络后端处理）。

两者效果一样：容器 `-p 80:80` 都能 bind Windows 80。

### WSL distro

**Docker Desktop**：自带定制 `docker-desktop` + `docker-desktop-data` distro，内核配置好。

**Podman 5.4.0**：用标准 WSL2 + Fedora rootfs，通过 `podman-machine-default` distro 运行。

### 数据隔离

两者都用独立 vhdx，**互相不影响**——可以在同一台机器上同时安装 Docker Desktop 和 Podman，数据完全隔离（但不要同时启动两个引擎跑同一项目）。

### docker-compose 兼容性

**Docker Desktop**：`docker-compose.exe` 通过 `\\.\pipe\docker_engine` 连 dockerd。

**Podman 5.4.0**：`docker-compose.exe` 通过 `\\.\pipe\docker_engine` 连 podman service（win-sshproxy 转发到 WSL podman.sock）。**完全兼容，docker-compose 把 podman 当 docker 用，无感知**。

---

## ⚠️ MiaotongDoc 项目特定差异（用 podman 跑本项目必读）

通用 podman 踩坑上面已写。但**用 podman 跑 MiaotongDoc 这个项目**，有 4 个**项目级**问题在 Docker Desktop 上不存在：

### 项目差异 1：bind mount 跨 fs 写文件 EPERM（编辑器打不开文件）

**症状**：浏览器打开 xlsx/docx 文档，OnlyOffice 报"打开文件错误"。converter 日志持续刷：

```
[ERROR] receiveTask Error: EPERM: operation not permitted, copyfile
  '/tmp/ASC_CONVERT.../result/Editor.bin'
  -> '/var/lib/onlyoffice/documentserver/App_Data/cache/files/data/<uuid>/Editor.bin'
```

**根因**：

| | Docker Desktop (Linux) | Podman on WSL2 |
|---|---|---|
| 文件挂载方式 | **virtiofs 中转** → 容器看是 ext4 | **9p drvfs 直挂** → 容器看是 9p |
| `/tmp` 容器内 | tmpfs（容器本地）| tmpfs |
| `App_Data` 容器内 | ext4 | **9p** |
| 跨 fs `copy_file_range()` | ✅ ext4 ↔ tmpfs 都是 Linux 原生 | ❌ 9p 不支持该 syscall → EPERM |

**为什么 Docker Desktop 没事**：Docker Desktop 在 Windows 上通过 virtiofs 把 D: 盘**先同步到 WSL2 VM 内部 ext4**，再 bind mount 到容器——容器看到的是 Linux 原生 fs，跨 fs copyfile 工作正常。podman 直接用 Windows 自带的 9p 协议挂 D: 盘到容器——9p 不实现 `copy_file_range` syscall。

**修复**：让 converter 临时目录 `/tmp` 也走 9p（跟 cache 同 fs），不再跨 mount。在 docker-compose.yml 给 3 个 editor 容器**加 1 行**：

```yaml
volumes:
  - ./data/editor:/var/www/onlyoffice/Data
  - ./data/editor-cache:/var/lib/onlyoffice/documentserver/App_Data
  - ./data/editor-tmp:/tmp               # ← 新增这行
  - ./data/logs/editor:/var/log/onlyoffice
```

**原理**：让 `/tmp` 也成为 host D: 上的 9p bind mount，converter 临时目录和 cache 目录都在 9p 上 → 同 fs → 不跨 mount → EPERM 消失。

**为什么不用 named volume**：podman named volume 是 podman 自管 ext4，**完美绕过 9p**。但 Docker Desktop 上 named volume 也是 ext4（Docker 自管），所以**两个环境都能跑**。**取舍**：用户接受 bind mount 方案，新增 bind mount 比换 named volume 改动小。

### 项目差异 2：多人协同 nginx hash 算法不一致

**症状**：多人协同编辑同一文档（Chrome 一个窗口、Chrome 无痕一个窗口），**光标看不到对方**。单人编辑无问题。

**根因**：`MiaotongDoc-Docker/config/nginx/nginx.conf` 中两个 upstream 用**不同 hash 算法**：

```nginx
upstream editors {
    hash $doc_key$remote_addr consistent;  # HTTP: doc_key + IP
}
upstream editors_socketio {
    hash $remote_addr consistent;           # WebSocket: 只用 IP
}
```

同一用户同一文档：HTTP 路由到 editor1，**WebSocket 路由到 editor2 或 3**（hash 值不一样）→ OnlyOffice 编辑器拒绝连接（HTTP 返回的 session token 跟 WS 实例不一致）。

**为什么 Docker Desktop 没事**：之前 editor ×3 经常 unhealthy（nginx 静默挂掉），实际服务的实例少（1-2 个），多人协同"凑巧"命中同一实例。修了 healthcheck 后，3 个 editor 都健康，路由分散问题暴露。

**修复**：两个 upstream 用相同 hash 算法——改 `nginx.conf` 1 行：

```nginx
upstream editors_socketio {
    hash $doc_key$remote_addr consistent;  # 跟 editors 一致
}
```

**操作**：`nginx -s reload` 热加载（不重建容器）。

### 项目差异 3：editor 容器内 nginx 不受 supervisor 管

**症状**：editor 容器 `docker compose ps` 显示 unhealthy（虽然容器进程在跑）。`/ds-vpath/` 返 502 Bad Gateway。

**根因**：OnlyOffice 9.3 base 镜像里，`/etc/supervisor/conf.d/` 下没有 `ds-nginx.conf`——**只有 docs/converter/example/metrics 4 个 process**。nginx 是由 `run-document-server.sh` 一次性 `service nginx start` 拉起，**挂了没人拉**。

**修复**：在 docker-compose.yml 给 editor ×3 和 web-server 加 healthcheck（项目 8 个其他服务都配了，**唯独这俩没配**——架构缺口）：

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q --spider http://localhost/healthcheck || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
```

**附带必做**：颠倒启动顺序——**web-server 先于 editor**。因为 editor nginx 配置里有 `upstream web-server:9004` 解析，启动时 web-server 必须可达（之前 C 阶段 editor → D 阶段 web-server 颠倒成 C: web-server → D: editor）。

**为什么不影响 Docker Desktop**：Docker Desktop 上 editor nginx 静默挂掉时 Docker 的健康检查会触发整个容器重启（跟加 healthcheck 等效），**但加了更明确**。

### 项目差异 4：`resolver 127.0.0.11` 在 nginx 容器里访问不到

**症状**：nginx.conf 加了 `resolver 127.0.0.11 valid=10s ipv6=off;` + `server editor:80 resolve;` 后，nginx 周期性解析容器名失败，**整个 upstream 失联**，所有请求 502：

```
send() failed (111: Connection refused) while resolving, resolver: 127.0.0.11:53
web-server could not be resolved (110: Operation timed out)
editor could not be resolved (110: Operation timed out)
no live upstreams while connecting to upstream
```

**根因**：`127.0.0.11:53` 是 Docker daemon 监听的**内置 DNS**（在 docker0 bridge 上）。Docker Desktop 容器**默认连接 docker0** → 能访问 `127.0.0.11`。但 podman on WSL2 用**自定义 mtd-net bridge**，**nginx 在 mtd-net 上访问不到 docker0** → DNS 请求全部 connection refused。

**为什么 Docker Desktop 没事**：Docker Desktop 默认所有容器在 docker0 bridge 上，能访问 `127.0.0.11` DNS。

**修复**：**`resolve` 是为了容器 IP 动态变化的场景**（Kubernetes 这类）。MiaotongDoc 用固定 IP（docker-compose 配了 `ipv4_address`），**根本不需要**：

```nginx
# ❌ 删掉这两行
# resolver 127.0.0.11 valid=10s ipv6=off;
# server editor:80 resolve;

# ✅ 保持默认（启动时一次性解析容器名）
server editor:80;
```

### 必做修改清单（podman 上跑 MiaotongDoc 必须改）

| # | 改什么 | 文件 | 修什么问题 |
|---|---|---|---|
| 1 | 子网 172.20 → 172.21（或 172.25）| docker-compose.yml | 避 WSL2 NAT 冲突 |
| 2 | 3 个 editor 加 `./data/editor-tmp:/tmp` | docker-compose.yml | 修 EPERM |
| 3 | 颠倒 deploy.sh 阶段 C↔D | deploy.sh | 修 web-server DNS 依赖 |
| 4 | editor ×3 + web-server 加 healthcheck | docker-compose.yml | 修 nginx 静默挂 |
| 5 | `editors_socketio` 改 `hash $doc_key$remote_addr` | nginx.conf | 修多人协同 |
| 6 | **不要**加 `resolver 127.0.0.11` + `resolve` | nginx.conf | 避免 DNS 跑不通 |

**Linux 生产环境（Docker Desktop / Docker Engine）**：以上 6 项**全部不需要**——Docker daemon 自动注入容器名到 /etc/hosts，bind mount 是 Linux fs ↔ Linux fs 没 EPERM，nginx upstream 用容器名自动解析。

**所以建议**：docker-compose.yml + nginx.conf 用一个**分支配置文件**（`docker-compose.podman.yml` 或类似），覆盖差异。生产用原 compose，开发用 podman 分支。

---

## 故障排查

### 通用排查顺序

```bash
# 1. machine 在跑吗？
podman machine list
# 应显示 "Currently running"

# 2. 容器在跑吗？
podman ps
# 应列出 12 个容器

# 3. 端口映射对吗？
podman port --all
# 应显示 80/tcp -> 0.0.0.0:80 等

# 4. Windows 上端口监听吗？
netstat -ano | findstr ":80 "
# 应显示 127.0.0.1:80 LISTENING

# 5. 应用响应吗？
curl http://localhost
# 应返回 200
```

### 容器起不来

```bash
# 看具体容器日志
docker-compose -f docker-compose.yml logs <service-name>

# 看健康检查状态
docker-compose -f docker-compose.yml ps <service-name>

# 强制重建容器
docker-compose -f docker-compose.yml up -d --force-recreate <service-name>
```

### Podman Desktop 不显示容器

1. 关闭 Podman Desktop（任务管理器结束所有 Podman Desktop 进程）
2. `podman machine stop` → `podman machine start`
3. 重开 Podman Desktop

### WSL distro 损坏

```powershell
# 完全清理 WSL
wsl --shutdown
wsl --unregister podman-machine-default

# 重建
& "C:\Program Files\RedHat\Podman\podman.exe" machine init
& "C:\Program Files\RedHat\Podman\podman.exe" machine start

# 数据迁移到 D 盘（见上文"数据存储位置"章节）
```

### 镜像丢失

```bash
# 重新加载本地 tar 包
podman load -i C:\miaotongdoc-images\miaotongdoc-core.tar
podman load -i C:\miaotongdoc-images\miaotongdoc-docling.tar
podman load -i C:\miaotongdoc-images\miaotongdoc-ocr.tar

# 验证
podman images
```

---

## 相关文档

- [Podman 官方 Windows 安装指南](https://podman.io/docs/installation#windows)
- [迁移计划文档](plans/2026-08-12-podman-migration.md)
- [项目部署指南](DEPLOY.md)
- [podman-deploy.sh 脚本](MiaotongDoc-Docker/podman-deploy.sh)

---

## 经验总结

**三天踩坑的核心教训**：

1. **不要用 Podman 6.0.x on Windows WSL**——根本不工作，无解
2. **用 Podman 5.4.0 + Podman Desktop 1.29.1**——稳定可靠
3. **数据放 D 盘用 `wsl --export/import`**——不要用 junction 软链接
4. **启动容器用 `docker-compose.exe`**——和 `podman compose` 等价但更直接
5. **端口偏移是项目配置，不是 Podman 行为**——改 docker-compose.yml 就行
6. **遇到 "No container engine" 重启 Podman Desktop**——缓存问题
7. **遇到端口冲突 `wsl --shutdown`** ——彻底清理 WSL 状态
8. **不要在 rootless 和 rootful 之间反复切**——storage 隔离，镜像要重新加载

**最重要的一条**：**如果 Docker Desktop 能用就继续用**。Podman 5.4.0 配好后体验等价，但初次配置要踩坑。如果有强烈理由（开源、免费、daemonless）再迁移。
