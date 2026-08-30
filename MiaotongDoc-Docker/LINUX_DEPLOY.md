# MiaotongDoc Linux 部署手册（小白版）

> **版本**: v1.0 | **适用**: 全新 Linux 服务器从零部署 MiaotongDoc
> **维护者**: Claude Code | **最后更新**: 2026年8月22日

---

## 📖 这本手册是给谁看的？

如果你符合下面任意一条，这份手册就是为你准备的：

- 🐣 **第一次接触 Linux**：没装过系统、不熟命令行、不知道 `sudo` 是什么
- 🛠️ **零运维经验的开发**：会写代码但不熟悉服务器、网络、安全配置
- 📚 **想把 15 个容器稳稳跑起来的 IT 同事**：希望有一份按步骤照着做就能成功的清单

### 这本手册**不**包含什么？

- ❌ Windows / macOS 本地部署 → 改用根目录的 [`DEPLOY.md`](../DEPLOY.md)（Windows + Docker Desktop）
- ❌ 应用代码开发 → 改用 [`CLAUDE.md`](../CLAUDE.md)（开发参考手册）
- ❌ 从已有环境迁移升级 → 改用根目录的 [`DEPLOY.md`](../DEPLOY.md)（内网迁移部署指南）

### 阅读路径建议

```
完全没接触过 Linux
   ↓ 必读第 1-2 章（基础 + 服务器准备）
   ↓ 必读第 3 章（Linux 速成）
   ↓ 必读第 4 章（宿主机初始化）
   ↓ 必读第 5 章（安装 Docker）
   ↓ 必读第 6 章（部署 MiaotongDoc）← 核心
   ↓ 推荐第 7 章（域名 HTTPS）
   ↓ 推荐第 8 章（日常运维）
   ↓ 选读第 9-10 章（监控 + FAQ）
```

---

## 📋 目录

- [第 1 章：开始之前](#第-1-章开始之前)
- [第 2 章：服务器准备](#第-2-章服务器准备)
- [第 3 章：Linux 入门速成](#第-3-章linux-入门速成)
- [第 4 章：宿主机初始化](#第-4-章宿主机初始化)
- [第 5 章：安装 Docker](#第-5-章安装-docker)
- [第 6 章：部署 MiaotongDoc](#第-6-章部署-miaotongdoc)
- [第 7 章：域名与 HTTPS](#第-7-章域名与-https)
- [第 8 章：日常运维](#第-8-章日常运维)
- [第 9 章：监控与告警](#第-9-章监控与告警)
- [第 10 章：常见问题 FAQ](#第-10-章常见问题-faq)
- [附录 A：一键脚本汇总](#附录-a一键脚本汇总)
- [附录 B：端口对照表](#附录-b端口对照表)
- [附录 C：关键命令速查](#附录-c关键命令速查)
- [附录 D：相关文档](#附录-d相关文档)
- [🎉 部署完成检查清单](#-部署完成检查清单)

---

## 第 1 章：开始之前

### 1.1 系统要求

#### 硬件最低要求

| 项目 | 最低配置 | 推荐配置 | 备注 |
|------|---------|---------|------|
| **CPU** | 4 核 | 8 核+ | MTOffice 编辑器需要单核性能 |
| **内存** | 8 GB | 16 GB+ | 16 GB 可支撑 50 人并发，32 GB 可支撑 200 人 |
| **系统盘** | 50 GB SSD | 100 GB SSD | 装系统和 Docker 镜像 |
| **数据盘** | 100 GB | 500 GB+ SSD | 文档存储、数据库、ES 索引 |
| **网络** | 100 Mbps | 1 Gbps | 多人同时上传/下载文档 |
| **公网 IP** | 1 个 | 2 个（主备） | 用户访问入口 |

#### 软件要求

| 软件 | 版本 | 用途 |
|------|------|------|
| **操作系统** | Ubuntu 22.04 LTS / 24.04 LTS<br>CentOS Stream 9 / Rocky Linux 9<br>Debian 12 | 任选一个，社区稳定支持到 2027-2030 |
| **Docker Engine** | ≥ 20.10 | 容器引擎（强依赖 V2 compose） |
| **Docker Compose** | V2.x | 容器编排（用 `docker compose`，不是 `docker-compose`） |
| **SSH 客户端** | 系统自带 | 远程登录服务器 |

#### 为什么这么挑？

- **Ubuntu 22.04 LTS**：国内云厂商（阿里云、腾讯云）默认系统，apt 源国内镜像齐全，遇到问题搜得到答案
- **CentOS Stream 9 / Rocky**：CentOS 7 已 EOL（2024-06-30），不要再用；新部署必须用 9 系
- **Debian 12**：最接近上游，社区源稳定，但中文资料比 Ubuntu 少

### 1.2 推荐配置

#### 小团队 / 试跑（≤ 20 用户）

```
1 台云服务器：
  CPU   : 4 核
  内存   : 8 GB
  硬盘   : 系统盘 50GB + 数据盘 100GB（SSD）
  网络   : 5 Mbps 公网带宽
  月费用 : 约 ¥200-400（按阿里云 ECS 通用型估算）
```

#### 中型企业（≤ 100 用户）

```
1 台云服务器：
  CPU   : 8 核
  内存   : 16 GB
  硬盘   : 系统盘 100GB + 数据盘 500GB（SSD）
  网络   : 10 Mbps 公网带宽 + SLB 负载均衡
  月费用 : 约 ¥800-1500
```

#### 大型 / 高可用（100+ 用户）

```
3 台云服务器：
  2 台应用服务器（8 核 16GB，做主备）
  1 台数据库服务器（8 核 32GB，PG + ES + MinIO）
  配合 SLB 负载均衡
  月费用 : 约 ¥3000-6000
```

> 本手册只覆盖"1 台服务器"的最简部署。分布式 / 高可用需要额外设计数据库主从、MinIO 分布式、共享存储等，本手册不展开。

### 1.3 准备工作清单

开始前请确认你已经有：

```
□ 一台 Linux 服务器（云服务器或物理机都行）
□ 能 SSH 远程登录（root 或 sudo 账号）
□ 一个域名（可选，但强烈推荐，用于 HTTPS）
□ 一个邮箱（用于 SSL 证书申请）
□ 本手册（已保存可随时查阅）
□ 至少 2 小时的整块时间（首次部署不可中断）
□ 心态：不慌、不急、报错就查日志
```

#### 心态准备 ⚠️

部署**一定会遇到问题**。重要的是：

1. ✅ 报错先**复制完整错误信息**（不要只截图）
2. ✅ 用搜索引擎 / 官方文档搜错误信息
3. ✅ 看日志（90% 的问题在日志里）
4. ❌ 不要瞎猜、瞎改、瞎重启
5. ❌ 不要把生产服务器当测试场

---

## 第 2 章：服务器准备

### 2.1 选择服务器

#### 云服务器 vs 物理机

| | 云服务器（推荐） | 物理机 |
|---|---|---|
| **优势** | 5 分钟开通、按月付费、有公网 IP、快照备份 | 性能稳定、数据自主 |
| **劣势** | 月费长期累加、受云厂商约束 | 需要机房、UPS、网络 |
| **适合** | 中小企业、上线初期 | 政府/金融/超大规模 |

**小白强烈建议从云服务器开始**：阿里云、腾讯云、华为云、AWS 都行，国内推荐前两个。

#### 推荐云厂商对比（2026 年）

| 厂商 | 优势 | 国内访问 | 价格档位 |
|------|------|---------|---------|
| **阿里云 ECS** | 文档最全、镜像最丰富 | ★★★★★ | 中 |
| **腾讯云 CVM** | 价格略低、微信生态 | ★★★★ | 中 |
| **华为云 ECS** | 政企客户多、稳定性好 | ★★★★★ | 中高 |
| **AWS EC2** | 全球节点、生态完整 | ★★★（直连慢） | 高 |

### 2.2 安装 Linux 操作系统

#### 方式 A：云服务器（推荐小白）

买服务器时，云厂商会让你选操作系统：

```
推荐选择：
  Ubuntu Server 22.04 LTS 64位
    或
  Ubuntu Server 24.04 LTS 64位
```

其他步骤：
1. **选择地域**：选离用户最近的（如广州、上海、北京）
2. **选择实例规格**：至少 4 核 8GB（参见 1.1 推荐配置）
3. **系统盘**：默认 40 GB 就够，不够再扩
4. **数据盘**：单独挂一块 100GB+ 的 SSD（**重要**：系统盘和数据盘要分开）
5. **网络**：分配公网 IP（必须是带宽计费，包年包月更划算）
6. **安全组**：开放 22（SSH）、80（HTTP）、443（HTTPS）
7. **登录方式**：选"密码"或"密钥对"（推荐密钥对）

#### 方式 B：物理机

需要：
- U 盘（≥ 8 GB）
- 显示器 + 键盘（一次性）
- 下载 Ubuntu Server ISO：`https://releases.ubuntu.com/22.04/`
- 用 balenaEtcher / Rufus 烧录到 U 盘
- 服务器 BIOS 设为 U 盘启动，按提示安装

#### 验证系统安装成功

SSH 登录后执行：

```bash
# 查看系统版本
cat /etc/os-release

# 期望输出（Ubuntu 22.04）：
# NAME="Ubuntu"
# VERSION="22.04.x LTS (Jammy Jellyfish)"
# ID=ubuntu
# VERSION_ID="22.04"
# PRETTY_NAME="Ubuntu 22.04.x LTS"

# 查看内核
uname -r
# 期望：6.x.x-generic（或类似）

# 查看是否 root
whoami
# 期望：root（云服务器首次登录通常是 root）
```

### 2.3 网络配置

#### 公网 IP

云服务器会自动分配公网 IP，不需要手动配。物理机需要联系运营商或自己 NAT。

#### 域名解析（DNS）

如果你已经有域名（如 `doc.mycompany.com`），需要做 A 记录：

```
主机记录    记录类型    记录值
doc        A          <服务器公网IP>
```

操作步骤（以阿里云为例）：

1. 登录阿里云控制台 → 域名 → 解析设置
2. 添加记录：
   - 主机记录：`doc`
   - 记录类型：`A`
   - 记录值：服务器公网 IP
   - TTL：`600`（10 分钟）
3. 等待 5-10 分钟生效

验证：

```bash
# 在本地电脑执行
ping doc.mycompany.com

# 应该能 ping 通，且 IP 是你的服务器公网 IP
```

#### 端口规划

| 端口 | 服务 | 必须对外开放？ | 说明 |
|------|------|--------------|------|
| 22 | SSH | ✅ 必须（建议改 2222，见 4.x） | 远程登录 |
| 80 | Nginx HTTP | ✅ 必须 | 用户访问入口 |
| 443 | Nginx HTTPS | ✅ 必须（强烈推荐） | 加密访问 |
| 9004 | 后端 API | ⚠️ 可选 | 直接访问 API，建议用 80/443 走代理 |
| 9001 | MinIO 控制台 | ❌ 仅内网 | 内部管理用 |
| 15672 | RabbitMQ 管理 | ❌ 仅内网 | 内部管理用 |
| 1234 | Yjs 协同 | ⚠️ 可选 | 客户端 WebSocket 会用到 |
| 其他 | DB / Redis / ES / RabbitMQ | ❌ 仅内网 | 数据库端口**绝对不要**暴露公网 |

> ⚠️ **安全铁律**：**5432**（PG）、**6379**（Redis）、**5672**（RabbitMQ AMQP）、**9200**（ES）**永远不要暴露公网**！只允许 Docker 网络内部访问。

#### 云服务器安全组配置

以阿里云为例：

1. 进入 ECS 控制台 → 安全组 → 选择实例的安全组 → 配置规则
2. 入方向添加：
   ```
   端口 22     源 0.0.0.0/0     SSH（生产建议限定 IP 段）
   端口 80     源 0.0.0.0/0     HTTP
   端口 443    源 0.0.0.0/0     HTTPS
   端口 9004   源 0.0.0.0/0     API（可选）
   ```
3. 入方向**不要**加：5432、6379、5672、9200、9000、9001、15672（这些只在 Docker 网络内部用）
4. 出方向：默认全开即可

#### 物理机 / 内网环境

需要：

```bash
# 1. 开放端口（firewalld，CentOS/RHEL）
sudo firewall-cmd --permanent --add-port=22/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload

# 或者（ufw，Ubuntu/Debian）
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# 验证
sudo firewall-cmd --list-all   # 或 sudo ufw status
```

### 2.4 SSH 远程登录

#### 什么是 SSH？

SSH（Secure Shell）是 Linux 的"远程桌面"。你在自己电脑上输入命令，传到服务器执行，结果回显给你。

#### 第一次连接

**Windows 用户**：下载 [PuTTY](https://www.putty.org/) 或 [MobaXterm](https://mobaxterm.mobatek.net/)（推荐）

**macOS / Linux 用户**：直接用系统终端

```bash
# 格式：ssh 用户名@服务器IP
# 第一次连接会提示"是否信任主机指纹"，输入 yes
ssh root@你的服务器IP

# 如果用了密钥对（推荐）：
ssh -i /path/to/your-key.pem root@你的服务器IP
```

#### 修改 SSH 端口（强烈推荐）

默认 22 端口是黑客扫描的首选目标。改成高位端口（如 2222）：

```bash
# 编辑 SSH 配置
sudo vi /etc/ssh/sshd_config

# 找到 #Port 22 这一行
# 改成（去掉 #）：
Port 2222
PermitRootLogin prohibit-password   # 禁止 root 密码登录（用密钥）
PasswordAuthentication no          # 禁止密码登录（全部用密钥）

# 保存退出，重启 SSH
sudo systemctl restart sshd

# 验证：用新端口连接
ssh -p 2222 root@你的服务器IP
```

⚠️ **修改后**别忘了：
1. 云服务器安全组要开放新端口 2222
2. 保持当前 SSH 连接不断开，新开一个窗口验证能连上后再断开旧的

#### 使用密钥对登录（强烈推荐）

密码登录容易被爆破，密钥对登录无法破解。

**生成密钥对**（在本地电脑执行）：

```bash
# 生成 RSA 密钥对（4096 位最安全）
ssh-keygen -t rsa -b 4096 -f ~/.ssh/miaotongdoc_server

# 生成过程中会问：
#   Enter passphrase: 设置一个密码（可选，但推荐）
# 生成完成后：
#   ~/.ssh/miaotongdoc_server       ← 私钥（绝对不能泄露！）
#   ~/.ssh/miaotongdoc_server.pub   ← 公钥（要传到服务器）
```

**上传公钥到服务器**：

```bash
# 方法 1：ssh-copy-id（一键）
ssh-copy-id -i ~/.ssh/miaotongdoc_server.pub -p 22 root@你的服务器IP

# 方法 2：手动复制
cat ~/.ssh/miaotongdoc_server.pub | ssh root@你的服务器IP "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

**禁用密码登录**（在服务器上执行）：

```bash
sudo vi /etc/ssh/sshd_config
# 修改：
PasswordAuthentication no
sudo systemctl restart sshd
```

---

## 第 3 章：Linux 入门速成

> 本章面向完全没接触过 Linux 的小白。如果你是熟手，可以跳过。

### 3.1 什么是 Linux？

Linux 是一个**开源操作系统内核**，由 Linus Torvalds 在 1991 年创建。常见的"Ubuntu""CentOS"都是 Linux 内核 + 各种软件打包成的"发行版"。

Linux 与 Windows 的核心差异：

| 维度 | Windows | Linux |
|------|---------|-------|
| 操作方式 | 图形界面 + 命令行 | **主要是命令行**（图形可有可无） |
| 用户权限 | 单用户为主 | 多用户，每个文件都有严格的权限位 |
| 软件安装 | 下 .exe 双击 | 用包管理器（apt / yum / dnf） |
| 远程管理 | 远程桌面 | SSH 命令行 |
| 服务进程 | "服务"管理器 | systemctl / service |

### 3.2 文件系统结构

```
/                     ← 根目录（所有目录都在它下面）
├── home/             ← 普通用户家目录
│   └── alice/        ← 用户 alice 的家目录
├── root/             ← root 用户家目录
├── etc/              ← 配置文件（Everything Configurable）
│   ├── ssh/
│   └── nginx/
├── var/              ← 可变数据（Variable）
│   ├── log/          ← 日志
│   └── www/          ← 网站
├── usr/              ← 用户软件（Unix System Resources）
│   ├── local/        ← 自编译软件
│   └── bin/          ← 系统命令
├── tmp/              ← 临时文件（重启清空）
├── opt/              ← 第三方软件
├── dev/              ← 设备文件
├── proc/             ← 进程信息（虚拟文件系统）
└── data/             ← 我们的应用数据（自己建的）
```

### 3.3 必学命令（30 个就够用）

> 命令记不住？收藏本节，**用到再查**。

#### 文件与目录

```bash
pwd                  # Print Working Directory: 显示当前目录
ls                   # List: 列出文件
ls -la               # 列出全部（含隐藏文件 + 详细信息）
cd /path/to/dir      # Change Directory: 切换目录
cd ~                 # 回到自己的家目录
cd ..                # 回到上一级目录
mkdir mydir          # MaKe DIR: 创建目录
mkdir -p a/b/c       # 递归创建多级目录
rm file.txt          # ReMove: 删除文件
rm -rf mydir         # 强制递归删除（⚠️ 危险！）
rm -rf /             # 💀 自杀式命令，禁用！
cp a.txt b.txt       # CoPy: 复制
mv a.txt b.txt       # MoVe: 移动 / 重命名
find / -name "*.log" # 查找文件
```

#### 文件查看

```bash
cat file.txt         # 全部打印
less file.txt        # 分页查看（q 退出）
head -n 20 file.txt  # 前 20 行
tail -n 20 file.txt  # 末尾 20 行
tail -f file.txt     # 实时跟踪文件末尾（看日志神器）
```

#### 文本处理

```bash
grep "关键词" file   # 查找包含关键词的行
grep -r "关键词" /etc/  # 递归查找
wc -l file.txt       # 统计行数
sort file.txt        # 排序
uniq file.txt        # 去重
```

#### 系统信息

```bash
uname -a             # 内核版本
cat /etc/os-release  # 操作系统版本
df -h                # 磁盘使用
free -h              # 内存使用
top                  # 进程实时（q 退出，按 P/M 按 CPU/内存排序）
htop                 # top 的升级版（需要 apt install htop）
ps aux               # 进程快照
uptime               # 运行时间 + 负载
```

#### 用户与权限

```bash
whoami               # 当前用户
id                   # 当前用户 + 组信息
sudo 命令            # 以 root 权限执行
chmod 755 file       # 修改权限：755 = rwxr-xr-x
chown user:group file # 修改所有者
passwd               # 改密码
adduser alice        # 加用户
deluser alice        # 删用户
```

#### 网络

```bash
ip addr              # 查看网卡 IP（取代 ifconfig）
ip route             # 路由表
ping 8.8.8.8         # 测试网络连通
curl http://...      # 发送 HTTP 请求
ss -tlnp             # 查看监听端口（取代 netstat）
wget URL             # 下载文件
```

#### 软件包管理

```bash
# Ubuntu / Debian
sudo apt update      # 更新软件源
sudo apt install nginx    # 安装
sudo apt remove nginx     # 卸载
apt search keyword       # 搜索

# CentOS / RHEL / Rocky
sudo dnf update      # 更新
sudo dnf install nginx
sudo dnf remove nginx
```

#### 服务管理（systemd）

```bash
systemctl status nginx     # 查看服务状态
sudo systemctl start nginx # 启动
sudo systemctl stop nginx  # 停止
sudo systemctl restart nginx  # 重启
sudo systemctl enable nginx   # 开机自启
sudo systemctl disable nginx  # 取消开机自启
journalctl -u nginx -f     # 看 nginx 日志
```

### 3.4 文件权限详解

`ls -l` 看到的文件信息：

```
-rw-r--r--  1 root root   1234 Aug 22 10:00 file.txt
↑↑↑↑↑↑↑↑↑  ↑ ↑    ↑      ↑    ↑            ↑
权限位    链接 所有者 组   大小 修改时间     文件名
```

权限位 10 个字符：
- 第 1 位：`-` 普通文件 / `d` 目录 / `l` 软链接
- 第 2-4 位：**所有者**权限（`r` 读 `w` 写 `x` 执行）
- 第 5-7 位：**所属组**权限
- 第 8-10 位：**其他人**权限

**数字表示**：
- `r` = 4（读）
- `w` = 2（写）
- `x` = 1（执行）
- 三位组合：`rwx` = 7、`rw-` = 6、`r-x` = 5、`r--` = 4

常用数字：
- `755`（目录默认）：所有者全权，其他人可读可执行
- `644`（文件默认）：所有者可写，其他人只读
- `700`（私密目录）：只有所有者能动

```bash
chmod 755 file       # 改权限
chown alice:alice file  # 改所有者 + 组
chmod +x script.sh   # 给所有人加执行权限（不用算数字）
```

### 3.5 进程与服务

Linux 上跑的每个程序都是一个**进程**，每个进程都有 PID（Process ID）。

```bash
# 查看所有进程
ps aux | less

# 找到 PID 后
kill 1234            # 优雅结束（发送 SIGTERM）
kill -9 1234         # 强制结束（SIGKILL，⚠️ 可能丢数据）

# 服务管理（systemd）
systemctl list-units --type=service   # 列出所有服务
systemctl status sshd                # 看 sshd 服务状态
```

### 3.6 文本编辑器

远程登录后改配置文件，需要用**命令行编辑器**：

| 编辑器 | 上手难度 | 推荐度 | 命令 |
|--------|---------|--------|------|
| **nano** | ★☆☆ 最简单 | ★★★★★ 新手首选 | `nano file.txt` |
| vi / vim | ★★★ 较难 | ★★★★ 强大 | `vi file.txt` |
| emacs | ★★★★ 难 | ★★ 学习曲线陡 | `emacs file.txt` |

**nano 基础操作**：

```bash
nano file.txt
# 进入后：
# - 输入文字直接编辑
# - Ctrl+O 保存（底部会提示 File Name to Write）
# - Ctrl+X 退出
# - Ctrl+W 搜索
# - Ctrl+K 剪切一行
# - Ctrl+U 粘贴
```

**vi 最小生存指南**（用于 nano 不可用的极端情况）：

```bash
vi file.txt
# 按 i 进入"插入模式"（可以打字了）
# 编辑完成后：
#   按 Esc 退回"命令模式"
#   输入 :wq 保存退出
#   输入 :q! 强制退出（不保存）
```

### 3.7 常用快捷键

| 快捷键 | 作用 |
|--------|------|
| `Ctrl + C` | 中断当前命令 |
| `Ctrl + D` | 退出当前 shell（相当于输入 `exit`） |
| `Ctrl + L` | 清屏 |
| `Ctrl + A` | 光标移到行首 |
| `Ctrl + E` | 光标移到行尾 |
| `Ctrl + U` | 剪切光标到行首的内容 |
| `Ctrl + K` | 剪切光标到行尾的内容 |
| `↑ / ↓` | 翻历史命令 |
| `Tab` | 自动补全命令 / 路径 |
| `!!` | 重复上一条命令 |
| `history` | 查看历史命令 |

### 3.8 SSH 使用进阶

#### ssh 免确认指纹

第一次连接会问"是否信任主机"，加 `-o StrictHostKeyChecking=no` 可跳过（⚠️ 公网不安全用）：

```bash
ssh -o StrictHostKeyChecking=no root@你的IP
```

#### 免密登录（用密钥）

参见 [2.4 SSH 远程登录](#24-ssh-远程登录)。

#### 配置别名

编辑本地电脑的 `~/.ssh/config`：

```
Host mtd
    HostName 你的服务器IP
    User root
    Port 2222
    IdentityFile ~/.ssh/miaotongdoc_server
```

之后就可以 `ssh mtd` 直接登录。

#### 传文件

```bash
# 本地 → 服务器
scp file.txt root@你的IP:/path/to/dest/

# 服务器 → 本地
scp root@你的IP:/path/to/file.txt ./

# 传目录（加 -r）
scp -r mydir/ root@你的IP:/path/to/dest/
```

#### SFTP（推荐新手）

如果觉得 scp 不直观，用 FileZilla / MobaXterm / WinSCP 这些图形化 SFTP 客户端：
- 主机：sftp://你的IP
- 端口：22（或你改的 SSH 端口）
- 用户名/密码 / 私钥：同 SSH

---

## 第 4 章：宿主机初始化

> ⚠️ **本章是部署成功与否的关键**。MiaotongDoc 包含 15 个容器，每个都会打开多个文件、占用不少内存、跑 Java/Go/Python 等多种运行时。**宿主机默认配置扛不住**。

### 4.1 为什么需要宿主机初始化？

默认 Linux 是为"个人桌面"或"小型服务器"配置的。生产环境跑容器，至少要解决 7 个问题：

| # | 问题 | 默认值 | 生产值 | 影响 |
|---|------|--------|--------|------|
| 1 | **文件句柄** | 1024 | 65536 | ES / PG 会"too many open files" |
| 2 | **进程数** | 4096 | 16384 | 高并发时新连接被拒 |
| 3 | **swap 倾向** | swappiness=60 | 10 | 默认太爱用 swap，性能差 |
| 4 | **内存超额申请** | overcommit_memory=0 | 1 | OOM killer 误杀进程 |
| 5 | **TCP 连接队列** | somaxconn=128 | 4096 | 高并发连接被丢 |
| 6 | **Docker 日志** | 无限制 | max-size=50m max-file=5 | 撑爆根分区 |
| 7 | **时间漂移** | 不强制同步 | chrony 内网 NTP | JWT 校验失败 |

不要被这些数字吓到，**不用记**——有个一键脚本帮你搞定。

### 4.2 一键脚本：setup-linux-host.sh

项目根目录已经准备好了 **`setup-linux-host.sh`**，会自动完成 9 项配置：

1. 文件句柄 + 进程数限制
2. 内核参数（vm / net / fs / oom）
3. Docker 守护进程配置
4. 时间同步（chrony）
5. swap 配置（默认 16GB）
6. 防火墙规则（firewalld / ufw）
7. Docker 日志归档（logrotate）
8. SSH 安全加固（改端口、禁密码）
9. 自动备份 cron（每天凌晨 3 点）

#### 用法

```bash
# 1. 上传脚本到服务器（任选一种）
# 方法 A：用 scp（在本地电脑执行）
scp ../setup-linux-host.sh root@你的IP:/root/

# 方法 B：直接 wget / curl（如果有访问权限）
wget -O /root/setup-linux-host.sh https://your-domain/setup-linux-host.sh

# 2. SSH 登录服务器
ssh root@你的IP

# 3. 给脚本执行权限
chmod +x /root/setup-linux-host.sh

# 4. 查看帮助
./setup-linux-host.sh --help

# 5. 一键运行（标准场景）
sudo ./setup-linux-host.sh

# 离线内网环境（关键参数：指定内网 NTP 服务器）
sudo ./setup-linux-host.sh --ntp-server 192.168.1.10

# 自定义 swap 大小
sudo ./setup-linux-host.sh --swap-size 32G

# 跳过某项（比如已有防火墙，不想重复配）
sudo ./setup-linux-host.sh --skip-firewall
```

#### 脚本会做的事（按顺序）

```
[1/9] 配置文件句柄与进程数限制...
   → 写入 /etc/security/limits.d/99-miaotongdoc.conf

[2/9] 配置内核参数...
   → 写入 /etc/sysctl.d/99-miaotongdoc.conf 并应用

[3/9] 配置 Docker 守护进程...
   → 写入 /etc/docker/daemon.json 并重启 dockerd
   → 自动校验 docker / compose 版本（必须 V2）

[4/9] 配置时间同步 chrony...
   → 安装 chrony、启动服务
   → 如果指定 --ntp-server，改用内网 NTP 源

[5/9] 配置 swap (16G)...
   → 创建 /swapfile、启用、写 /etc/fstab

[6/9] 配置防火墙规则...
   → firewalld: 开放 80, 443, 9004, 9000-9001, 1234, 15672
   → ufw: 等效命令

[7/9] 配置 Docker 日志归档...
   → 写入 /etc/logrotate.d/docker-miaotongdoc

[8/9] SSH 安全加固...
   → 提示后改端口 2222、禁密码登录、重启 sshd
   → ⚠️ 这一步会问你"确认修改 SSH 配置？(y/N)"，先确认新端口能通再 y

[9/9] 配置自动备份 cron...
   → 写入 /etc/cron.d/miaotongdoc-backup
```

#### 重要提示 ⚠️

1. **脚本必须以 root 权限运行**（用 `sudo` 或 root 登录）
2. **脚本不会重启系统**，但部分配置需要重新登录 SSH 才生效（文件句柄等）
3. **SSH 加固步骤**（第 8/9 步）会问你是否确认。**先在另一个窗口验证新端口能通再输入 y**，否则可能锁死自己！
4. **离线内网环境必须传 `--ntp-server`**，否则 chrony 指向公网池不可达，时间会漂移
5. **脚本运行过程中可能弹出 sshd 重启**，保持当前 SSH 会话，**另开一个窗口**测新端口

#### 验证初始化结果

```bash
# 验证 1：文件句柄
ulimit -n
# 期望：65536

# 验证 2：内核参数
sysctl vm.swappiness vm.overcommit_memory net.core.somaxconn
# 期望：
#   vm.swappiness = 10
#   vm.overcommit_memory = 1
#   net.core.somaxconn = 4096

# 验证 3：swap
swapon --show
# 期望显示 /swapfile 16G（你指定的大小）

# 验证 4：Docker 配置
docker info | grep -E 'Storage Driver|Logging Driver'
# 期望：
#   Storage Driver: overlay2
#   Logging Driver: json-file

# 验证 5：时间同步
chronyc tracking | head -5
# 期望：Reference ID 不为 00000000（说明已同步）

# 验证 6：防火墙
sudo firewall-cmd --list-all
# 期望：包含 ports: 80/tcp 443/tcp 9004/tcp 等

# 验证 7：SSH 配置
sudo sshd -T | grep -E '^(port|passwordauthentication|permitrootlogin)'
# 期望：port 2222 / passwordauthentication no / permitrootlogin prohibit-password
```

### 4.3 手动初始化（不推荐，了解即可）

> 如果你用了上面的 `setup-linux-host.sh`，**这一节可以跳过**。这里写出来是为了：
> - 排查"脚本跑失败 / 我想跳过某项 / 离线环境脚本不能跑"的情况
> - 理解每一步在干什么

#### 4.3.1 文件句柄 + 进程数

```bash
sudo cat > /etc/security/limits.d/99-miaotongdoc.conf <<'EOF'
*       soft    nofile    65536
*       hard    nofile    65536
*       soft    nproc     16384
*       hard    nproc     16384
root    soft    nofile    65536
root    hard    nofile    65536
EOF
```

**重新登录 SSH 后生效**。Docker 容器内的进程也需要在 daemon.json 里设（见 4.3.3）。

#### 4.3.2 内核参数

```bash
sudo cat > /etc/sysctl.d/99-miaotongdoc.conf <<'EOF'
vm.swappiness = 10                  # 降低 swap 使用倾向（默认 60 太爱用 swap）
vm.overcommit_memory = 1           # 允许内存超额申请
vm.overcommit_ratio = 50           # 超额申请的物理内存比例
net.core.somaxconn = 4096          # TCP 最大连接队列（高并发需要）
net.core.netdev_max_backlog = 5000 # 网卡 backlog
net.ipv4.tcp_max_syn_backlog = 4096
net.ipv4.tcp_tw_reuse = 1          # TIME_WAIT 连接复用
net.ipv4.tcp_fin_timeout = 15      # FIN 等待时间（默认 60 太长）
fs.file-max = 2097152              # 系统总文件句柄数
fs.nr_open = 1048576               # 单进程最大文件句柄
vm.oom_kill_allocating_task = 0    # OOM 时杀占用最多的进程（更可控）
vm.max_map_count = 262144          # Elasticsearch 必需（默认 65530 会启动失败）
EOF

sudo sysctl --system
```

> **`vm.max_map_count = 262144` 是 Elasticsearch 8.x 的硬性要求**。如果不设，ES 容器启动时直接报错退出。

#### 4.3.3 Docker 守护进程

```bash
sudo mkdir -p /etc/docker
sudo cat > /etc/docker/daemon.json <<'EOF'
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

sudo systemctl restart docker
```

**关键参数说明**：

| 参数 | 作用 |
|------|------|
| `log-driver: json-file` | Docker 日志用 json 文件（默认） |
| `log-opts.max-size: 50m` | 单个日志文件最大 50MB |
| `log-opts.max-file: 5` | 最多保留 5 个日志文件（轮转） |
| `default-ulimits.nofile: 65536` | 容器内进程文件句柄（与宿主机一致） |
| `storage-driver: overlay2` | 推荐存储驱动（性能好） |
| `live-restore: true` | dockerd 重启时容器不中断 |
| `userland-proxy: false` | 减少 iptables 规则，提升性能 |
| `metrics-addr: 127.0.0.1:9323` | Docker 自身监控端点（仅本机访问） |

#### 4.3.4 时间同步（chrony）

```bash
# 安装
sudo apt install -y chrony        # Ubuntu/Debian
# 或
sudo dnf install -y chrony        # CentOS/RHEL

# 启用 + 启动
sudo systemctl enable --now chronyd

# 验证
chronyc tracking
```

**离线内网环境**（无法访问公网 NTP 池）：

```bash
# 编辑配置文件
sudo vi /etc/chrony/chrony.conf    # Ubuntu
# 或
sudo vi /etc/chrony.conf           # CentOS

# 注释掉所有 pool / server 行（行首加 #）
# 添加：
# server 192.168.1.10 iburst      ← 改成你的内网 NTP 服务器 IP

sudo systemctl restart chronyd
chronyc tracking   # 验证同步成功
```

#### 4.3.5 swap

```bash
# 创建 16GB swapfile（推荐 = 物理内存的 25-50%）
sudo fallocate -l 16G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 持久化（重启后自动启用）
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 验证
free -h
# 应该能看到 swap: 16G
```

#### 4.3.6 防火墙

**firewalld**（CentOS / RHEL / Rocky）：

```bash
sudo firewall-cmd --permanent --add-port=22/tcp      # SSH
sudo firewall-cmd --permanent --add-port=80/tcp      # HTTP
sudo firewall-cmd --permanent --add-port=443/tcp     # HTTPS
sudo firewall-cmd --permanent --add-port=9004/tcp    # 后端 API（可选）
sudo firewall-cmd --reload

# 验证
sudo firewall-cmd --list-all
```

**ufw**（Ubuntu / Debian）：

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 9004/tcp
sudo ufw enable          # 默认未启用

# 验证
sudo ufw status
```

#### 4.3.7 Docker 日志归档

```bash
sudo cat > /etc/logrotate.d/docker-miaotongdoc <<'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    missingok
    notifempty
    copytruncate
}
EOF

# logrotate 是 cron 自动跑的，无需启动。手动测试：
sudo logrotate -d /etc/logrotate.d/docker-miaotongdoc
```

#### 4.3.8 SSH 安全加固

> ⚠️ **本节会改 SSH 配置，操作不慎可能锁死服务器！**

**先准备好备用登录方式**：
- 云服务器：准备 VNC / 控制台（云厂商网页上的"远程连接"）
- 物理机：现场或 IPMI 准备好

```bash
# 1. 备份原配置
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak

# 2. 修改配置
sudo sed -i 's/^#Port 22/Port 2222/' /etc/ssh/sshd_config
sudo sed -i 's/^Port 22/Port 2222/' /etc/ssh/sshd_config
sudo sed -i 's/^PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#MaxAuthTries 6/MaxAuthTries 3/' /etc/ssh/sshd_config

# 3. 验证配置语法
sudo sshd -t
# 没报错就 OK

# 4. 重启 SSH（保持当前会话，另开窗口测试新端口！）
sudo systemctl restart sshd

# 5. 在新窗口测试（不要关当前会话）
ssh -p 2222 root@你的IP
```

#### 4.3.9 自动备份 cron

```bash
# 假设应用部署在 /opt/MiaotongDoc/MiaotongDoc-Docker
SCRIPT_DIR="/opt/MiaotongDoc/MiaotongDoc-Docker"

sudo cat > /etc/cron.d/miaotongdoc-backup <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
0 3 * * * root cd ${SCRIPT_DIR} && ./deploy.sh backup >>/var/log/miaotongdoc-backup.log 2>&1 && find ${SCRIPT_DIR}/backup_* -maxdepth 0 -mtime +7 -exec rm -rf {} \;
EOF

sudo chmod 644 /etc/cron.d/miaotongdoc-backup
```

效果：每天凌晨 3 点自动备份 PostgreSQL + 文档 + 配置，保留 7 天。

### 4.4 初始化常见问题

#### Q1：脚本跑失败，提示"Docker 未安装"

这是正常的——脚本**不会**自动安装 Docker。Docker 安装见 [第 5 章](#第-5-章安装-docker)。

#### Q2：脚本跑失败，提示"Docker 版本过低"

要求 Docker Engine ≥ 20.10 + Compose V2。如果是 V1（命令是 `docker-compose` 带连字符），需要装 V2：

```bash
# Ubuntu/Debian
sudo apt install -y docker-compose-plugin

# CentOS
sudo dnf install -y docker-compose-plugin
```

#### Q3：跑完脚本，文件句柄还是 1024

`/etc/security/limits.d/` 的配置对**新登录的 SSH 会话**生效。当前会话不会变。

```bash
# 重新登录 SSH 后验证
ulimit -n
```

如果是 sudo 执行命令，可能要 `sudo -i` 切到 root 再查。

#### Q4：chrony 显示"未同步"

```bash
# 看状态
chronyc tracking
# Reference ID: 00000000 (...)    ← 没同步
# System time: 0.000000 seconds slow of NTP time

# 等待几分钟后看
chronyc sources -v
# 期望：列表里有 ^* (当前源) 或 ^+ (优质源)

# 如果一直不同步：
# 1. 检查防火墙是否放通 UDP 123（NTP 用 UDP 协议）
# 2. 检查 NTP 服务器 IP 是否可达（ping 或 ntpdate -q）
# 3. 内网环境必须配 --ntp-server
```

---

## 第 5 章：安装 Docker

### 5.1 什么是 Docker？

如果把服务器比作"一栋大楼"，传统的"在系统上直接装软件"就像"租户改造自己的房间"——多个租户互相干扰（端口冲突、依赖冲突）。

**Docker** 是"集装箱"：每个程序（数据库、缓存、Web）都装在一个隔离的"集装箱"里，集装箱之间互不干扰，又可以一起运输。

> **Docker ≠ 虚拟机**
> - 虚拟机：模拟整个硬件，每个 VM 都有自己的内核（重、慢、占资源）
> - Docker：共享宿主机内核，只隔离文件系统 + 网络 + 进程（轻、快、近原生性能）

MiaotongDoc 的所有服务（PostgreSQL、Redis、Spring Boot、MTOffice 等）都跑在 Docker 容器里。

### 5.2 在线安装 Docker Engine + Compose V2

> 适用：服务器能访问公网（最简单）

#### Ubuntu / Debian

```bash
# 1. 卸载旧版本（如果之前装过）
sudo apt remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

# 2. 安装依赖
sudo apt update
sudo apt install -y ca-certificates curl gnupg lsb-release

# 3. 添加 Docker 官方 GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 4. 设置 Docker 仓库（Ubuntu）
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 5. 安装
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 6. 启动 + 开机自启
sudo systemctl enable --now docker

# 7. 验证
sudo docker --version
# 期望：Docker version 20.10.x 或更高
sudo docker compose version
# 期望：Docker Compose version v2.x.x（V2 才是正确的，命令是 docker compose 没有 -）
```

#### CentOS / RHEL / Rocky

```bash
# 1. 卸载旧版本
sudo dnf remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine 2>/dev/null || true

# 2. 安装依赖
sudo dnf install -y dnf-plugins-core

# 3. 添加 Docker 仓库
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# 4. 安装
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. 启动 + 开机自启
sudo systemctl enable --now docker

# 6. 验证（同 Ubuntu）
sudo docker --version
sudo docker compose version
```

### 5.3 内网离线安装 Docker

> 适用：服务器完全断网（金融/政府内网）

#### 方法 A：用别人下载好的 deb/rpm 包

```bash
# 1. 在能上网的电脑上下载（参考网址 https://download.docker.com/linux/）
# 必备包（Ubuntu 22.04 为例）：
#   containerd.io_<version>.deb
#   docker-ce_<version>.deb
#   docker-ce-cli_<version>.deb
#   docker-buildx-plugin_<version>.deb
#   docker-compose-plugin_<version>.deb

# 2. 传到服务器
scp *.deb root@你的IP:/tmp/

# 3. 批量安装
sudo dpkg -i /tmp/*.deb

# 4. 启动 + 验证
sudo systemctl enable --now docker
sudo docker --version
sudo docker compose version
```

#### 方法 B：用 Podman 替代（部分内网合规场景）

部分内网不允许 Docker daemon（要求所有进程无 root），可以用 Podman 替代。Podman 是 Docker 的兼容替代品，命令几乎一样：

```bash
# Ubuntu
sudo apt install -y podman podman-compose

# CentOS
sudo dnf install -y podman podman-compose

# Podman 的 compose 命令位置不同：
#   Docker:  docker compose up -d
#   Podman:  podman-compose up -d  或  docker compose up -d（用 docker 别名指向 podman）

# ⚠️ 注意：MiaotongDoc 的 docker-compose.yml 在 Podman 下有几个小差异
#   - cgroup v2: 某些容器需要额外配置
#   - tmpfs 权限: Podman tmpfs 默认 0700，需要在容器内 chmod
#   - 命名卷: Podman 默认 rootless 时行为不同
# 详见项目根目录的 podman-deploy.sh 和 PODMAN_GUIDE.md
```

### 5.4 配置 Docker（非 root 用户使用）

默认只有 root 能用 `docker` 命令。让普通用户也能用：

```bash
# 1. 创建 docker 用户组（docker 安装时通常已自动创建）
sudo groupadd docker 2>/dev/null || true

# 2. 把当前用户加进 docker 组（替换 alice 为你的用户名）
sudo usermod -aG docker alice

# 3. 重新登录后生效
# 验证：
docker ps
# 不加 sudo 也能列出容器
```

### 5.5 验证 Docker 安装

```bash
# 跑一个 hello-world 容器（首次会下载镜像）
sudo docker run --rm hello-world

# 期望输出：
# Hello from Docker!
# This message shows that your installation appears to be working correctly.
# ...

# 验证 docker compose
sudo docker compose version
# 期望：Docker Compose version v2.x.x

# 验证存储驱动
sudo docker info | grep -E 'Storage Driver|Server Version'
# 期望：
#   Storage Driver: overlay2
#   Server Version: 20.10.x 或更高
```

如果 hello-world 跑成功，Docker 就装好了。

### 5.6 镜像加速器（国内服务器强烈推荐）

国内拉 Docker Hub 镜像很慢（经常超时）。配置镜像加速器：

```bash
sudo mkdir -p /etc/docker
sudo cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker

# 验证
sudo docker info | grep -A 5 'Registry Mirrors'
# 期望看到上面三个镜像地址
```

**如果用了 setup-linux-host.sh**，daemon.json 已经创建了。**在原有配置基础上追加**镜像加速器（注意 JSON 末尾加逗号）：

```bash
sudo vi /etc/docker/daemon.json
# 在 storage-driver 那行后加：
#   "registry-mirrors": [
#     "https://mirror.ccs.tencentyun.com"
#   ],
# 注意 JSON 语法：前一项加逗号
```

### 5.7 离线环境镜像导入

> 适用：服务器完全断网（金融/政府内网）

如果服务器完全断网，先在能上网的机器上导出镜像，再传过去导入：

```bash
# 在能上网的机器上（项目根目录有 export-images.sh 脚本）：
cd /path/to/MiaotongDoc
chmod +x export-images.sh
./export-images.sh

# 生成 docker-images-*.tar 文件（约 5-8 GB）

# 传到服务器（用 U 盘或内网文件传输）
scp docker-images-*.tar root@你的IP:/tmp/

# 在服务器上导入
sudo docker load -i /tmp/docker-images-postgres-redis.tar
sudo docker load -i /tmp/docker-images-minio-es.tar
sudo docker load -i /tmp/docker-images-web-nginx.tar
sudo docker load -i /tmp/docker-images-editor-yjs.tar
sudo docker load -i /tmp/docker-images-ocr.tar

# 验证
sudo docker images
# 应该能看到所有需要的镜像
```

详细脚本逻辑见 `MiaotongDoc-Docker/export-images.sh`。

---

## 第 6 章：部署 MiaotongDoc

> 🎯 **本章是核心**。跟着做，把所有服务跑起来。

### 6.1 准备部署包

#### 情况 A：服务器能访问代码仓库

```bash
# 在服务器上 clone（或用 git pull 已有代码）
sudo mkdir -p /opt/MiaotongDoc
sudo chown $USER:$USER /opt/MiaotongDoc
cd /opt/MiaotongDoc
git clone <仓库地址> .
```

#### 情况 B：本地构建产物上传

如果你在外网构建好部署包（参见 `CLAUDE.md` §更新部署包）：

```bash
# 本地：构建 + 打包
cd /path/to/MiaotongDoc
# 前端
cd miaotongdoc-web && npm run build && cd ..
# 后端
cd miaotongdoc-server && mvn clean package -DskipTests && cd ..

# 把整个 MiaotongDoc-Docker 打包（不包含 data/ 和 *.tar）
tar czf miaotongdoc-deploy.tar.gz \
    --exclude='MiaotongDoc-Docker/data' \
    --exclude='*.tar' \
    --exclude='.git' \
    MiaotongDoc-Docker/ miaotongdoc-server/ miaotongdoc-web/

# 传到服务器
scp miaotongdoc-deploy.tar.gz root@你的IP:/tmp/

# 服务器：解压到 /opt
ssh root@你的IP
mkdir -p /opt/MiaotongDoc
tar xzf /tmp/miaotongdoc-deploy.tar.gz -C /opt/MiaotongDoc/
```

#### 情况 C：纯离线部署

完整离线部署方案见项目根目录的 `plans/2026-07-26-offline-deployment.md`。要点：

1. 用 `export-images.sh` 导出所有 Docker 镜像
2. 用 `setup-linux-host.sh`（带 `--ntp-server`）初始化
3. 用离线 deb/rpm 包装 Docker
4. 加载镜像（`docker load`）
5. 修改 `docker-compose.yml` 的 `image:` 字段为本地镜像名（不走远端拉取）

### 6.2 目录结构

部署后 `MiaotongDoc-Docker/` 的结构：

```
/opt/MiaotongDoc/MiaotongDoc-Docker/
├── .env                          # ⚠️ 你的密码和密钥（从 .env.example 复制）
├── .env.example                  # 模板（不要动）
├── docker-compose.yml            # 容器编排
├── deploy.sh                     # 一键部署脚本
├── README.md                     # 简版说明
├── LINUX_DEPLOY.md               # ← 你正在读的手册
│
├── app/                          # 应用构建产物
│   ├── server/
│   │   └── miaotongdoc.jar       # 后端（Spring Boot JAR）
│   ├── web/
│   │   └── dist/                 # 前端（Vite 构建产物）
│   ├── editor/                   # MTOffice 编辑器（Dockerfile + 插件 + 字体）
│   ├── yjs/                      # Yjs 协同服务器源码
│   ├── ocr/                      # Tesseract OCR 镜像构建
│   └── ocr-paddle/               # PaddleOCR 镜像构建
│
├── config/                       # 配置文件
│   ├── nginx/
│   │   ├── nginx.conf
│   │   └── service-worker-override.js
│   ├── postgres/
│   │   └── init.sql              # PG 初始化脚本（自动建扩展）
│   ├── redis/
│   │   └── redis.conf
│   ├── logback-spring.xml        # 后端日志格式
│   └── logrotate/
│       └── app-logs              # 日志轮转配置
│
├── scripts/
│   ├── clean-editor-cache.sh     # 编辑器缓存清理（容器内 cron 跑）
│   └── logrotate.sh              # 日志轮转执行
│
└── data/                         # 运行时数据（部署时不存在，启动后自动创建）
    ├── documents/                # 文档文件（local 模式）
    ├── pgdata/                   # PostgreSQL 数据
    ├── minio/                    # MinIO 对象存储
    ├── rabbitmq/                 # RabbitMQ 队列
    ├── editor/                   # MTOffice 编辑器数据
    ├── editor-cache/             # 编辑器临时缓存
    ├── yjs/                      # Yjs 协同状态
    ├── config/                   # 应用配置
    └── logs/                     # 所有日志
```

### 6.3 配置 .env（最关键的一步）

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 1. 复制模板
cp .env.example .env

# 2. 给 .env 加权限（只有自己能读写）
chmod 600 .env

# 3. 编辑（用 nano 或 vi）
nano .env
```

#### 必须修改的项（生产环境）

> ⚠️ **生产环境必须全部修改默认值！** 默认值是公开仓库里能看到的，被人扫到就完蛋了。

| 变量 | 说明 | 推荐做法 |
|------|------|---------|
| `DB_PASSWORD` | PostgreSQL 密码 | `openssl rand -base64 16` 生成 16 位 |
| `REDIS_PASSWORD` | Redis 密码 | `openssl rand -base64 16` |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | `openssl rand -base64 16` |
| `APP_JWT_SECRET` | 应用 JWT 密钥 | `openssl rand -base64 48` ≥ 32 字符 |
| `EDITOR_JWT_SECRET` | 编辑器 JWT 密钥 | `openssl rand -base64 48` ≥ 32 字符 |
| `SECURE_LINK_SECRET` | MTOffice 安全链接密钥 | `openssl rand -base64 24` ≥ 16 字符 |
| `MINIO_ACCESS_KEY` | MinIO 用户名 | 自定义（如 `miniouser`） |
| `MINIO_SECRET_KEY` | MinIO 密码 | `openssl rand -base64 24` ≥ 8 字符 |

**一键生成所有密码**（粘贴到 .env 即可）：

```bash
echo "==== 复制下面的内容到 .env ===="
echo "DB_PASSWORD=$(openssl rand -base64 16)"
echo "REDIS_PASSWORD=$(openssl rand -base64 16)"
echo "RABBITMQ_PASSWORD=$(openssl rand -base64 16)"
echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
echo "EDITOR_JWT_SECRET=$(openssl rand -base64 48)"
echo "SECURE_LINK_SECRET=$(openssl rand -base64 24)"
echo "MINIO_ACCESS_KEY=miniouser"
echo "MINIO_SECRET_KEY=$(openssl rand -base64 24)"
echo "==== 结束 ===="
```

#### 关键变量详解

| 变量 | 作用 | 不改的后果 |
|------|------|----------|
| `CORS_ORIGINS` | 跨域白名单 | 改错会导致浏览器拒绝前端请求 |
| `SSO_ENABLED` | 是否启用单点登录 | 默认 `false`，企业内网需要改 `true` |
| `STORAGE_TYPE` | `local` 或 `minio` | 小规模用 local，大规模用 minio |
| `MINIO_BUCKET` | MinIO 桶名 | 默认 `miaotongdoc`，可改成自定义 |
| `LLM_API_URL` / `LLM_API_KEY` | 内网大模型地址 | AI 功能需要配，否则 AI 不可用 |
| `DOCLING_ENABLED` / `OCR_ENABLED` / `PADDLE_OCR_ENABLED` | 三个 OCR 引擎开关 | 默认只启用 PaddleOCR（中文主力） |

#### 编辑示例

```bash
nano .env
```

找到 `DB_PASSWORD=your_db_password_here`，改成：

```
DB_PASSWORD=aX7N3vK9pQ2mZ8sL
```

按 `Ctrl+O` 保存，`Ctrl+X` 退出。

对其他变量做相同操作。改完后**仔细检查一遍**：

```bash
# 显示所有还带默认值的项
grep -E 'your_|here|change_me' .env
# 期望：没有输出（说明都改完了）
```

### 6.4 构建产物

#### 情况 A：已经构建好（部署包里有了 .jar 和 dist/）

跳过本节。

#### 情况 B：服务器上没构建产物

```bash
# 前端
cd /opt/MiaotongDoc/miaotongdoc-web
npm install
npm run build        # = vue-tsc && vite build
# 产物在 dist/

# 后端
cd /opt/MiaotongDoc/miaotongdoc-server
mvn clean package -DskipTests
# 产物在 target/miaotongdoc.jar

# 复制到部署目录
mkdir -p /opt/MiaotongDoc/MiaotongDoc-Docker/app/web/dist
rm -rf /opt/MiaotongDoc/MiaotongDoc-Docker/app/web/dist/*
cp -r /opt/MiaotongDoc/miaotongdoc-web/dist/* \
      /opt/MiaotongDoc/MiaotongDoc-Docker/app/web/dist/
cp /opt/MiaotongDoc/miaotongdoc-server/target/miaotongdoc.jar \
   /opt/MiaotongDoc/MiaotongDoc-Docker/app/server/
```

#### 情况 C：编辑器首次启动需要构建镜像

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 构建 MTOffice 编辑器镜像（约 5-10 分钟，下载约 1.6GB 基础镜像）
docker compose build editor

# 验证镜像已建好
docker images | grep miaotongdoc-editor
# 期望：miaotongdoc-editor  latest  <镜像ID>
```

### 6.5 分阶段启动（顺序至关重要！）

> ⚠️ **不能直接 `docker compose up -d` 全部启动**。Flyway V9 迁移依赖 MTOffice 创建的 `task_result` 表，顺序错了会启动失败。

#### 一键启动（推荐）

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 标准启动（基础服务 + PaddleOCR）
./deploy.sh start

# 额外启用 Tesseract OCR（多语言兜底）
./deploy.sh start --with-ocr

# 额外启用 Docling AI 文档解析（重型）
./deploy.sh start --with-docling

# 全部启用（4 层 OCR 引擎）
./deploy.sh start --with-ocr --with-docling
```

#### 启动过程拆解

脚本会按 6 个阶段启动（每个阶段等上一阶段 healthy 才继续）：

```
阶段 A: postgres + redis + elasticsearch + minio  （基础数据服务）
   ↓ 等待全部 healthy（约 1 分钟）
阶段 B: rabbitmq                                （消息队列）
   ↓ 等待 healthy
阶段 C: web-server                              （Spring Boot，执行 Flyway 迁移）
   ↓ 等待 Started
阶段 D: editor + editor2 + editor3              （MTOffice 3 实例）
   ↓ 等待 healthy（60s+）
阶段 E: yjs-server + nginx                      （协同 + 反代）
   ↓ 等待 healthy
阶段 F: ocr-paddle（+可选 ocr/docling）         （OCR 服务）
```

完整过程约 5-8 分钟。看到 `部署完成！` 就说明成功了。

#### 查看启动进度

另开一个 SSH 窗口：

```bash
# 看所有容器状态
cd /opt/MiaotongDoc/MiaotongDoc-Docker
docker compose ps

# 输出类似：
# NAME                      STATUS              PORTS
# miaotongdoc-nginx         Up (healthy)        0.0.0.0:80->80/tcp
# miaotongdoc-server        Up (healthy)        0.0.0.0:9004->9004/tcp
# miaotongdoc-editor        Up (healthy)
# miaotongdoc-editor2       Up (healthy)
# miaotongdoc-editor3       Up (healthy)
# miaotongdoc-postgres      Up (healthy)        0.0.0.0:5432->5432/tcp
# miaotongdoc-redis         Up (healthy)        0.0.0.0:6379->6379/tcp
# miaotongdoc-rabbitmq      Up (healthy)        0.0.0.0:5672->5672/tcp, ...
# miaotongdoc-minio         Up (healthy)        0.0.0.0:9000-9001->9000-9001/tcp
# miaotongdoc-elasticsearch Up (healthy)
# miaotongdoc-yjs           Up (healthy)        0.0.0.0:1234->1234/tcp
# miaotongdoc-ocr-paddle    Up (healthy)        0.0.0.0:5003->5003/tcp
# miaotongdoc-cache-cleaner Up
# miaotongdoc-logrotate     Up
```

#### 看实时日志

```bash
# 所有服务
docker compose logs -f

# 单个服务（如 web-server）
docker compose logs -f web-server

# 退出来按 Ctrl+C
```

### 6.6 重置管理员密码

> V3 迁移脚本里的初始 BCrypt 哈希值是损坏的，`Admin@123` 无法登录。**首次部署后必须重置**为 `123456`。

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 创建 SQL 文件（用 here-doc 避免 shell 转义 $ 符号）
cat > reset_pw.sql <<'EOF'
UPDATE sys_user
SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm'
WHERE employee_id = '10000000';
EOF

# 执行
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < reset_pw.sql

# 验证：登录
curl -s -X POST http://localhost:9004/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"10000000","password":"123456"}' | head -c 200
# 期望输出包含："token":"eyJ..."
```

**重置成功后的默认账号**：

| 字段 | 值 |
|------|-----|
| 工号 | `10000000` |
| 密码 | `123456` |

**首次登录后请立刻改密码**：
- 登录 → 右上角用户菜单 → 修改密码

### 6.7 部署后验证清单

挨个跑一遍，全过才算成功：

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker
echo "==== 验证清单 ===="

# 1. 容器全部 healthy
echo "[1] 容器状态:"
docker compose ps | grep -E 'Up|healthy' | wc -l
# 期望：≥ 13（核心服务 + 2 个定时任务）

# 2. PostgreSQL
echo "[2] PostgreSQL:"
docker compose exec -T postgres pg_isready -U miaotong -d miaotongdocdb
# 期望：accepting connections

# 3. Redis
echo "[3] Redis:"
docker compose exec -T redis redis-cli -a "$(grep ^REDIS_PASSWORD= .env | cut -d= -f2)" ping 2>/dev/null
# 期望：PONG

# 4. MinIO
echo "[4] MinIO:"
curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:9000/minio/health/live
# 期望：HTTP 200

# 5. 后端 API
echo "[5] 后端 API:"
curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:9004/actuator/health/liveness
# 期望：HTTP 200（或 503 但能访问）

# 6. 前端页面
echo "[6] 前端页面:"
curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost/
# 期望：HTTP 200

# 7. 编辑器
echo "[7] MTOffice 编辑器:"
docker compose exec -T editor curl -fsS http://localhost/healthcheck
# 期望：true

# 8. Yjs 协同
echo "[8] Yjs 协同:"
curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:1234/health
# 期望：HTTP 200

# 9. 登录测试
echo "[9] 登录测试:"
curl -s -X POST http://localhost:9004/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"10000000","password":"123456"}' | head -c 100
# 期望：{"code":200,"data":{"token":"eyJ..."
```

### 6.8 访问地址

部署成功后，下面这些地址都能用了：

| 服务 | 地址 | 凭据 |
|------|------|------|
| **前端 Web**（用户访问） | http://你的IP/ | `10000000` / `123456` |
| 后端 API | http://你的IP:9004/api/ | - |
| MinIO 控制台 | http://你的IP:9001/ | `.env` 中 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| RabbitMQ 管理 | http://你的IP:15672/ | 用户名 `miaotong`，密码 `RABBITMQ_PASSWORD` |
| Yjs 健康 | http://你的IP:1234/health | - |

打开浏览器访问 `http://你的IP/`，用 `10000000` / `123456` 登录。**登录成功 = 部署完成** 🎉

### 6.9 部署常见问题

#### Q1：Flyway V9 失败：`relation "task_result" does not exist`

**原因**：web-server 比 editor 启动早，task_result 表还没建。

**解决**：

```bash
# 1. 确保 editor 全部 healthy
docker compose ps editor editor2 editor3

# 2. 重启 web-server
docker compose restart web-server

# 3. 看日志确认成功
docker compose logs -f web-server | grep -E 'Started|ERROR'
```

#### Q2：Flyway V2 失败：`relation "sys_department" does not exist`

**原因**：数据库有残留数据，Flyway baseline 跳过了 V1 建表脚本。

**解决**：

```bash
# 清空数据库重新跑
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb <<'EOF'
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO miaotong;
EOF

docker compose restart web-server
docker compose logs -f web-server | grep -E 'Started|ERROR'
```

#### Q3：editor 容器一直 Restarting

看日志：

```bash
docker compose logs --tail=50 editor
```

**常见原因**：

| 错误关键字 | 原因 | 解决 |
|-----------|------|------|
| `out of memory` | 内存不够 | 升级服务器内存，或减少 editor 实例数 |
| `port is already allocated` | 端口冲突 | 检查 80/443/9004 端口是否被其他进程占用 |
| `Bind for 0.0.0.0:80 failed` | Nginx 端口被占 | `sudo lsof -i :80` 查占用进程 |

#### Q4：admin 登录返回"密码错误"

密码没重置。回到 [6.6 重置管理员密码](#66-重置管理员密码)。

#### Q5：访问前端报"CORS"错误

`.env` 的 `CORS_ORIGINS` 没配对。改成实际访问的域名：

```bash
# 如果用 IP 访问
CORS_ORIGINS=http://你的IP

# 如果用域名访问
CORS_ORIGINS=https://doc.mycompany.com

# 多个域名（逗号分隔，不要空格）
CORS_ORIGINS=http://doc.mycompany.com,https://doc.mycompany.com

# 重启后端生效
docker compose restart web-server
```

#### Q6：登录后上传文件报"对象存储不可用"

MinIO 没启起来或密码错：

```bash
# 看 MinIO 状态
docker compose ps minio

# 测连通性
docker compose exec -T web-server curl -sf http://minio:9000/minio/health/live

# 重新配置 .env 后重启 web-server
docker compose restart web-server
```

---

## 第 7 章：域名与 HTTPS

> 默认部署只能通过 `http://IP` 访问，浏览器会标记"不安全"。生产环境**强烈推荐**配置域名 + HTTPS。

### 7.1 为什么需要 HTTPS？

| 不配 HTTPS 的代价 | 配 HTTPS 的好处 |
|------------------|----------------|
| 浏览器标"不安全" | 地址栏绿色锁 |
| 用户密码明文传输 | 全程加密 |
| SEO 排名差 | Google/Baidu 加分 |
| 无法用 HTTP/2（性能差） | HTTP/2 多路复用 |
| 微信/小程序无法对接 | 小程序强制 HTTPS |
| API 调用被中间人篡改 | 完整性校验 |

### 7.2 准备域名

#### 注册域名

国内：阿里云、腾讯云、华为云（需要实名认证）
国外：Cloudflare、Namecheap、GoDaddy

假设你注册了 `mycompany.com`，想用 `doc.mycompany.com` 访问 MiaotongDoc。

#### DNS 解析

在域名服务商控制台添加 A 记录：

```
主机记录    记录类型    记录值              TTL
doc        A          <服务器公网IP>       600
```

#### 验证 DNS 生效

```bash
# 在服务器或本地电脑执行
nslookup doc.mycompany.com 8.8.8.8
# 或
dig doc.mycompany.com
# 或
ping doc.mycompany.com

# 期望：返回你的服务器公网 IP
```

DNS 生效需要几分钟到几小时。

### 7.3 申请 SSL 证书

#### 方式 A：Let's Encrypt（免费，90 天自动续期）⭐推荐

需要：
- 公网可访问的服务器（80 端口临时用于验证）
- 域名解析已生效
- 能访问 `https://acme-v02.api.letsencrypt.org`（如离线环境不能用）

```bash
# 安装 certbot
sudo apt install -y certbot        # Ubuntu/Debian
sudo dnf install -y certbot        # CentOS

# 申请证书（用 nginx 插件验证）
sudo certbot certonly --nginx \
  -d doc.mycompany.com \
  --email admin@mycompany.com \
  --agree-tos \
  --no-eff-email

# 生成的文件：
#   /etc/letsencrypt/live/doc.mycompany.com/fullchain.pem   ← 证书链
#   /etc/letsencrypt/live/doc.mycompany.com/privkey.pem     ← 私钥
```

#### 方式 B：自签证书（内网 / 测试用）

> ⚠️ 自签证书浏览器会标红，仅适合内网或测试。

```bash
# 创建临时目录
mkdir -p /opt/MiaotongDoc/MiaotongDoc-Docker/config/nginx/ssl
cd /opt/MiaotongDoc/MiaotongDoc-Docker/config/nginx/ssl

# 生成自签证书（CN 写你的域名）
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout server.key \
  -out server.crt \
  -subj "/C=CN/ST=Beijing/L=Beijing/O=MyCompany/CN=doc.mycompany.com"

# 把私钥权限设严（重要）
chmod 600 server.key

# 客户端需要在浏览器/系统里"信任"这个证书
```

#### 方式 C：商业证书（阿里云 / 腾讯云免费 DV 证书）

- 阿里云免费 DV：每年可领 20 个，绑定域名即可
- 腾讯云免费 DV：TrustAsia 签发，每年 50 个

下载 Nginx 格式的证书包（`.crt` 和 `.key`），放到 `config/nginx/ssl/`。

### 7.4 配置 Nginx HTTPS

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 1. 复制证书到 nginx 配置目录（如果是 Let's Encrypt）
sudo cp /etc/letsencrypt/live/doc.mycompany.com/fullchain.pem config/nginx/ssl/server.crt
sudo cp /etc/letsencrypt/live/doc.mycompany.com/privkey.pem   config/nginx/ssl/server.key
sudo chmod 644 config/nginx/ssl/server.crt
sudo chmod 600 config/nginx/ssl/server.key
```

编辑 `config/nginx/nginx.conf`，在文件顶部或合适位置加入 HTTPS server 块：

```nginx
# HTTPS server
server {
    listen 443 ssl;
    server_name doc.mycompany.com;

    ssl_certificate     /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    # 复制原 80 端口 server 块的所有 location 进来
    # 包括 /api/, /editor/, /ws/, /ds-vpath/ 等
    # 详见同文件里的 80 server 块
    location / {
        # ... 同 80 server
    }
    # ... 其他 location
}

# HTTP 自动跳转 HTTPS
server {
    listen 80;
    server_name doc.mycompany.com;
    return 301 https://$host$request_uri;
}
```

由于项目原 `nginx.conf` 已经按端口 80 写了完整路由，**最简单的做法是改端口 + 加跳转**：

```bash
# 备份原配置
cp config/nginx/nginx.conf config/nginx/nginx.conf.bak

# 找到 listen 80 改成 listen 443 ssl（手动编辑）
nano config/nginx/nginx.conf
```

> 具体 nginx.conf 修改涉及项目全部 11 类路由，建议直接咨询项目方或参考 nginx 文档。本手册不展示完整配置文件（会过长）。

### 7.5 重启 Nginx 应用 HTTPS

```bash
docker compose restart nginx

# 验证：
curl -I https://doc.mycompany.com/
# 期望：HTTP/2 200
```

### 7.6 证书自动续期（Let's Encrypt）

Let's Encrypt 证书有效期 90 天，需自动续期。

```bash
# 1. 测试续期命令
sudo certbot renew --dry-run

# 2. 添加 cron 自动续期
echo "0 3 * * * root certbot renew --quiet --post-hook 'docker restart miaotongdoc-nginx' >> /var/log/certbot-renew.log 2>&1" \
  | sudo tee /etc/cron.d/certbot-renew

sudo chmod 644 /etc/cron.d/certbot-renew
```

每天凌晨 3 点检查证书，剩余 < 30 天自动续签，续签后重启 nginx。

### 7.7 CORS 配置同步更新

配了 HTTPS 后，`.env` 的 `CORS_ORIGINS` 必须同步更新：

```bash
# .env
CORS_ORIGINS=https://doc.mycompany.com

# 重启后端
docker compose restart web-server
```

否则浏览器会因为 CORS 不匹配拒绝跨域请求。

### 7.8 离线环境的证书方案

纯内网无法访问 Let's Encrypt，只能：

1. 用 OpenSSL 生成自签证书（见 7.3 方式 B）
2. 把 CA 根证书分发到所有客户端，让客户端信任
3. 或购买商业证书（DV 证书申请可以线下完成，签发后邮寄 U 盾）

---

## 第 8 章：日常运维

### 8.1 服务生命周期管理

#### 一键脚本

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

./deploy.sh start          # 启动（按 6 阶段顺序）
./deploy.sh start --with-docling    # 启动 + 启用 Docling
./deploy.sh stop           # 停止（保留容器）
./deploy.sh restart        # 重启（先 stop 再 up）
./deploy.sh status         # 状态
./deploy.sh health         # 健康检查
./deploy.sh logs           # 所有日志（Ctrl+C 退出）
./deploy.sh logs web-server  # 单个服务日志
./deploy.sh build          # 构建镜像
./deploy.sh backup         # 备份数据
./deploy.sh clean-logs     # 清理 30 天前的日志
```

#### 单服务操作

```bash
# 重启单个服务（不影响其他）
docker compose restart web-server

# 停止 + 删除容器（数据保留在 ./data/）
docker compose stop web-server
docker compose rm -f web-server

# 重新创建并启动
docker compose up -d web-server

# 看资源占用
docker stats
# 输出：每个容器的 CPU%、内存使用、网络 I/O
```

> ⚠️ **生产环境不要用 `./deploy.sh stop`**：脚本里的 stop 实际是 `docker compose down`，会**删除容器并丢失未持久化的数据**（虽然 `data/` 在宿主机，但容器内 PID 文件等会丢）。
> **推荐**：直接 `docker compose stop` 或 `docker compose restart`。

### 8.2 查看日志

#### 容器日志（标准输出）

```bash
# 所有服务，实时跟踪
docker compose logs -f

# 单个服务，实时跟踪
docker compose logs -f web-server

# 最近 100 行
docker compose logs --tail=100 web-server

# 最近 10 分钟
docker compose logs --since=10m web-server

# 带时间戳
docker compose logs -t web-server
```

#### 应用日志（写到文件的）

```bash
# 后端 Java 应用日志
ls data/logs/server/
tail -f data/logs/server/server.log          # 主日志
tail -f data/logs/server/error.log           # 错误日志

# PostgreSQL
ls data/logs/postgres/
tail -f data/logs/postgres/postgresql.log

# Nginx 访问日志
tail -f data/logs/nginx/access.log
tail -f data/logs/nginx/error.log

# MTOffice 编辑器
ls data/logs/editor/
tail -f data/logs/editor/docservice.log
```

#### 日志轮转清理

```bash
# 清理 30 天前的日志（默认）
./deploy.sh clean-logs

# 手动清理其他日志
sudo journalctl --vacuum-time=30d   # systemd 日志
sudo find /var/log -name "*.gz" -mtime +30 -delete
```

### 8.3 数据备份

#### 自动备份（推荐）

`setup-linux-host.sh` 已配置每天凌晨 3 点自动备份（保留 7 天）。验证：

```bash
# 看 cron 是否设置
cat /etc/cron.d/miaotongdoc-backup

# 看最近备份
ls -lht /opt/MiaotongDoc/MiaotongDoc-Docker/backup_* | head
# 应该看到多个 backup_20260822_030001 这样的目录

# 看备份内容
ls /opt/MiaotongDoc/MiaotongDoc-Docker/backup_20260822_030001/
# 期望：database.sql / documents/ / config/ / .env
```

#### 手动备份

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

# 一键备份
./deploy.sh backup

# 备份结果：当前目录下生成 backup_YYYYMMDD_HHMMSS/
```

#### 异地备份（强烈推荐）

备份只在本机磁盘，**机毁则数据全无**。生产环境必须异地备份。

```bash
# 方法 1：rsync 推送到异地（推荐，差异传输）
rsync -avz --delete \
  /opt/MiaotongDoc/MiaotongDoc-Docker/backup_*/ \
  backupuser@backup-server:/backup/miaotongdoc/

# 方法 2：云对象存储（OSS / S3 / MinIO）
# 先安装 s3cmd 或 aws cli
pip install awscli
aws s3 sync /opt/MiaotongDoc/MiaotongDoc-Docker/backup_*/ \
  s3://my-backup-bucket/miaotongdoc/ \
  --endpoint-url https://oss-cn-beijing.aliyuncs.com

# 自动执行（每天凌晨 4 点）
echo "0 4 * * * root rsync -avz --delete /opt/MiaotongDoc/MiaotongDoc-Docker/backup_*/ backupuser@backup-server:/backup/miaotongdoc/ >> /var/log/miaotongdoc-remote-backup.log 2>&1" \
  | sudo tee /etc/cron.d/miaotongdoc-remote-backup
```

### 8.4 数据恢复

> ⚠️ 恢复会**覆盖当前数据**，确认无误再执行。

```bash
# 1. 停止应用（保留数据库）
docker compose stop web-server nginx

# 2. 恢复 PostgreSQL
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < backup_20260822_030001/database.sql

# 3. 恢复文档（MinIO 模式不需要，local 模式需要）
docker compose stop minio
rm -rf data/documents/*
cp -r backup_20260822_030001/documents/* data/documents/
docker compose start minio

# 4. 恢复配置（如 .env）
cp backup_20260822_030001/.env .

# 5. 启动所有服务
docker compose up -d
```

### 8.5 应用升级

#### 升级流程

```bash
# 1. 备份当前数据
cd /opt/MiaotongDoc/MiaotongDoc-Docker
./deploy.sh backup

# 2. 拉取新代码
cd /opt/MiaotongDoc
git pull

# 3. 重新构建产物
cd miaotongdoc-web && npm install && npm run build && cd ..
cd miaotongdoc-server && mvn clean package -DskipTests && cd ..

# 4. 复制产物到部署目录
rm -rf MiaotongDoc-Docker/app/web/dist/*
cp -r miaotongdoc-web/dist/* MiaotongDoc-Docker/app/web/dist/
cp miaotongdoc-server/target/miaotongdoc.jar MiaotongDoc-Docker/app/server/

# 5. 重启服务
cd MiaotongDoc-Docker
docker compose restart nginx web-server
# 编辑器有改动才需要：
# docker compose build editor && docker compose up -d editor editor2 editor3
```

#### 检查 Flyway 是否有新迁移

```bash
# 看后端 Flyway 日志
docker compose logs web-server | grep -i flyway

# 看版本号
docker compose exec postgres psql -U miaotong -d miaotongdocdb \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

如果有新迁移，重启 `web-server` 时会自动执行（第一次启动可能慢 30 秒-1 分钟）。

#### 回滚

```bash
# 如果升级后出问题
git checkout <上一个稳定版本>
# 重新走 3-5 步

# 数据库回滚（⚠️ 危险，会丢失升级后的数据）
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < backup_最新时间戳/database.sql
```

### 8.6 性能调优

#### 查看当前资源使用

```bash
# 整体
docker stats

# 单个容器详情
docker stats miaotongdoc-server --no-stream
```

#### 常见调优点

| 症状 | 调优 |
|------|------|
| web-server 内存持续 > 80% | 加 JVM 参数：在 docker-compose.yml 的 `command:` 后追加 `-Xmx2g` |
| 编辑器启动慢 | 检查 `shm_size: 4g` 是否生效 |
| ES 启动失败 | 检查 `vm.max_map_count=262144`（见 4.3.2） |
| 数据库查询慢 | 见下"PostgreSQL 调优" |

#### PostgreSQL 调优

```bash
# 看慢查询
docker compose exec postgres psql -U miaotong -d miaotongdocdb \
  -c "SELECT pid, query, state, age(clock_timestamp(), query_start) AS duration FROM pg_stat_activity WHERE state != 'idle' ORDER BY duration DESC LIMIT 10;"

# 看索引使用
docker compose exec postgres psql -U miaotong -d miaotongdocdb \
  -c "SELECT schemaname, tablename, indexname, idx_scan FROM pg_stat_user_indexes WHERE idx_scan = 0 ORDER BY pg_relation_size(indexrelid) DESC;"
```

### 8.7 服务扩缩容

#### 加一个编辑器实例

编辑 `docker-compose.yml`，复制 editor 服务块改成 `editor4`：

```yaml
editor4:
  <<: *editor-default   # 用了 YAML 锚点的话（需要先定义）
  # 或者完全复制 editor 的所有配置
  container_name: miaotongdoc-editor4
  networks:
    mtd-net:
      ipv4_address: 172.21.0.53
```

同时改 `nginx.conf` 的 `upstream editor_cluster { ... }` 加入 `172.21.0.53:80`。

重启：

```bash
docker compose up -d editor4
docker compose restart nginx
```

#### 缩容同理

```bash
docker compose stop editor3
# 注释掉 docker-compose.yml 里 editor3 的配置
docker compose restart nginx
```

---

## 第 9 章：监控与告警

> 没有监控的服务器就是"盲驾"。本章教你最基本的 4 类监控：**磁盘、内存、容器健康、应用指标**。

### 9.1 系统级监控

#### 磁盘空间（最常出问题）

```bash
# 1. 当前磁盘使用
df -h

# 输出示例：
# Filesystem      Size  Used Avail Use% Mounted on
# /dev/vda1        99G   12G   82G  13% /
# /dev/vdb1       500G  180G  300G  38% /data

# 2. 看哪几个目录最大
sudo du -h --max-depth=1 /data | sort -h | tail -10

# 3. 看 Docker 占多少
docker system df
```

**配置自动告警**（磁盘 > 80% 邮件告警）：

```bash
sudo cat > /etc/cron.daily/disk-alert <<'EOF'
#!/bin/bash
THRESHOLD=85
USAGE=$(df /data | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$USAGE" -ge "$THRESHOLD" ]; then
    echo "WARNING: /data usage ${USAGE}% >= ${THRESHOLD}%" | mail -s "Disk Alert" admin@mycompany.com
fi
EOF
sudo chmod +x /etc/cron.daily/disk-alert

# 没装 mail 的话：
sudo apt install -y mailutils    # Ubuntu
sudo dnf install -y mailx        # CentOS
```

#### 内存使用

```bash
free -h
# 输出：
#               total        used        free      shared  buff/cache   available
# Mem:           16Gi        8.2Gi       1.5Gi       350Mi       6.3Gi       7.0Gi
# Swap:          16Gi        100Mi        15Gi

# 详细看哪些进程吃内存
ps aux --sort=-%mem | head -10
```

#### CPU 使用

```bash
# 实时（按 P 排 CPU）
top

# 安装 htop（更友好）
sudo apt install -y htop && htop
```

### 9.2 容器级监控

#### 实时查看

```bash
docker stats
# 输出：
# CONTAINER                  CPU %     MEM USAGE / LIMIT     MEM %     NET I/O          BLOCK I/O
# miaotongdoc-server         0.50%     1.2GiB / 15.5GiB      7.74%     1.2MB / 890kB   12MB / 0B
# miaotongdoc-editor         2.10%     1.5GiB / 15.5GiB      9.68%     ...
# ...

# 单次快照（不持续）
docker stats --no-stream
```

#### Docker 自带监控端点

Docker daemon.json 已开 `metrics-addr: 127.0.0.1:9323`：

```bash
curl http://127.0.0.1:9323/metrics
# Prometheus 格式的指标
```

### 9.3 应用级监控

#### 后端 API 健康检查

后端 Spring Boot Actuator 暴露：

```bash
# 详细健康检查
curl http://localhost:9004/actuator/health | head -c 500
# 输出 JSON 包含每个组件的健康状态

# Liveness（是否活着）
curl http://localhost:9004/actuator/health/liveness

# Readiness（是否就绪）
curl http://localhost:9004/actuator/health/readiness
```

#### 配置外部监控（Prometheus + Grafana，可选）

适合 50+ 用户、需要看历史趋势的场景。

```bash
# 1. 安装 Prometheus（用 docker）
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v /opt/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus

# 2. 写 prometheus.yml
cat > /opt/prometheus/prometheus.yml <<'EOF'
global:
  scrape_interval: 30s
scrape_configs:
  - job_name: 'docker'
    static_configs:
      - targets: ['你的IP:9323']   # Docker daemon metrics
  - job_name: 'miaotongdoc'
    static_configs:
      - targets: ['你的IP:9004']
    metrics_path: '/actuator/prometheus'
EOF

# 3. 安装 Grafana 看图（参考 grafana.com 文档）
```

### 9.4 告警配置

#### 方式 1：健康检查脚本 + cron（最简单）

```bash
cat > /opt/miaotongdoc/scripts/health-check.sh <<'EOF'
#!/bin/bash
DOMAIN="${1:-http://localhost}"
ALERT_EMAIL="admin@mycompany.com"

ERRORS=()

# 检查关键服务
for url in \
  "$DOMAIN/actuator/health" \
  "$DOMAIN/" \
  "http://localhost:9000/minio/health/live" \
  "http://localhost:1234/health"; do
  CODE=$(curl -sk -o /dev/null -w '%{http_code}' "$url")
  if [ "$CODE" != "200" ]; then
    ERRORS+=("$url 返回 $CODE（期望 200）")
  fi
done

if [ ${#ERRORS[@]} -gt 0 ]; then
    SUBJECT="[MiaotongDoc] 健康检查失败"
    BODY=$(printf '%s\n' "${ERRORS[@]}")
    echo "$BODY" | mail -s "$SUBJECT" "$ALERT_EMAIL"
fi
EOF
chmod +x /opt/miaotongdoc/scripts/health-check.sh

# 每 5 分钟检查一次
echo "*/5 * * * * root /opt/miaotongdoc/scripts/health-check.sh" | sudo tee /etc/cron.d/miaotongdoc-health
```

#### 方式 2：第三方监控（推荐生产）

| 服务 | 类型 | 价格 | 适合 |
|------|------|------|------|
| **UptimeRobot** | 外部 HTTP 监控 | 免费 50 个检查 | 基础可用性 |
| **阿里云监控 / 云监控** | 综合监控 | 部分免费 | 已在阿里云的用户 |
| **Zabbix** | 自建监控 | 开源 | 大型企业 |
| **Prometheus + Alertmanager** | 自建告警 | 开源 | 云原生场景 |

---

## 第 10 章：常见问题 FAQ

### 部署期问题

#### Q: `docker compose version` 报"command not found"

V2 没装。装 `docker-compose-plugin`：

```bash
sudo apt install -y docker-compose-plugin    # Ubuntu
sudo dnf install -y docker-compose-plugin    # CentOS
```

#### Q: `docker compose up` 报"port is already allocated"

端口被占用：

```bash
# 查谁占了 80
sudo lsof -i :80
# 或
sudo ss -tlnp | grep :80

# 杀掉占用进程（看清楚再杀）
sudo kill <PID>
```

#### Q: `docker pull` 卡死 / 超时

国内服务器配置镜像加速器（见 5.6）。

#### Q: Flyway 报 "Found non-empty schema(s) without schema history table"

数据库有残留。回到 6.9 节"Flyway V2 失败"处理。

#### Q: 启动后 `/actuator/health` 返回 DOWN，但服务能访问

这是 Spring Boot Actuator 的特性——health 组件包含磁盘空间检查，容器内磁盘使用方式可能与预期不同，触发 DOWN，但实际服务正常。

**解决方法**：编辑后端配置（`application.yml`），去掉 `diskSpace` 检查，或调低阈值：

```yaml
management:
  endpoint:
    health:
      show-details: always
```

或在 `docker-compose.yml` 的 `web-server` 加环境变量：

```yaml
environment:
  MANAGEMENT_HEALTH_DISKSPACE_ENABLED: "false"
```

#### Q: 部署成功后，访问 `http://IP` 显示 502

Nginx 后端连不上 web-server：

```bash
# 1. 看 web-server 状态
docker compose ps web-server

# 2. 看 web-server 日志
docker compose logs --tail=50 web-server

# 3. 进 nginx 容器测试连通
docker compose exec nginx wget -O- http://web-server:9004/actuator/health
# 期望：返回健康检查 JSON
```

### 运行期问题

#### Q: 服务偶尔报"数据库连接失败"

可能连接池耗尽或 PG OOM。看：

```bash
docker compose logs --since=10m postgres | grep -E 'OOM|killed|out of memory'

docker compose exec postgres psql -U miaotong -d miaotongdocdb \
  -c "SELECT count(*) FROM pg_stat_activity;"
# 期望：< 100（默认 max 100）
```

如果连接数高，考虑加 PG 的 `max_connections` 或优化代码。

#### Q: 用户上传大文件失败

Nginx 默认 `client_max_body_size 200m`，改大：

```bash
# 编辑 config/nginx/nginx.conf
nano config/nginx/nginx.conf
# 找到 client_max_body_size，改大（如 1g）：
# client_max_body_size 1024m;

docker compose restart nginx
```

#### Q: 编辑器打开文档一直 loading

MTOffice 后端有问题：

```bash
# 看编辑器日志
docker compose logs --tail=100 editor | grep -i error

# 测编辑器健康
docker compose exec editor curl -fsS http://localhost/healthcheck
# 期望：true

# 重启编辑器（耗时 1-2 分钟）
docker compose restart editor editor2 editor3
```

#### Q: AI 功能（对话 / 摘要）不可用

`LLM_API_URL` 没配：

```bash
# .env 配内网大模型地址
LLM_API_URL=http://192.168.1.100:8080
LLM_API_KEY=sk-xxx

docker compose restart web-server
```

#### Q: 内存持续上涨，触发 OOM

Java 应用常见。临时解决：加内存限制 + 重启。永久解决：排查内存泄漏。

```bash
# 看哪些容器占用最多内存
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'

# 重启最占内存的容器
docker compose restart editor

# 看 Java 堆（web-server）
docker compose exec web-server jcmd 1 GC.heap_info
```

#### Q: 磁盘满了

按 9.1 节"磁盘空间"处理。临时清理：

```bash
# 清理 Docker 无用资源
docker system prune -a    # ⚠️ 会删所有未用镜像
docker image prune         # 只清未用镜像

# 清理旧备份
find MiaotongDoc-Docker/backup_* -maxdepth 0 -mtime +7 -exec rm -rf {} \;

# 清理日志
./deploy.sh clean-logs
```

### 性能问题

#### Q: 系统响应慢

按以下顺序排查：

```bash
# 1. CPU 占用
top    # 找 CPU 占用最高的进程

# 2. 内存
free -h
# 关注 Swap 使用，如果 > 50% 说明物理内存吃紧

# 3. 磁盘 IO
iostat -xz 1 5
# 关注 %util（> 80% 说明磁盘是瓶颈）
# 没装 iostat：sudo apt install -y sysstat

# 4. 网络
ss -s
# 看连接数
```

#### Q: PostgreSQL 查询慢

```bash
# 看当前在跑的慢查询
docker compose exec postgres psql -U miaotong -d miaotongdocdb <<'EOF'
SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
  AND state != 'idle'
ORDER BY duration DESC;
EOF
```

### 安全问题

#### Q: 怀疑服务器被入侵

```bash
# 1. 看最近登录
last

# 2. 看失败的登录尝试（爆破痕迹）
sudo lastb | head

# 3. 看陌生进程
ps aux | grep -v '\[.*\]' | sort -k3 -nr | head

# 4. 看最近修改的文件
sudo find /etc /usr/local/bin /opt -mtime -3 -type f 2>/dev/null

# 5. 看 crontab 有没有陌生任务
for user in $(cut -f1 -d: /etc/passwd); do crontab -l -u $user 2>/dev/null && echo "--- $user ---"; done

# 如果发现异常：
# - 立即断网或改 SSH 端口封堵
# - 备份日志和数据
# - 重装系统（推荐，不要尝试"清理"后门）
```

#### Q: 怎么升级所有依赖的 Docker 镜像？

```bash
# 1. 备份
./deploy.sh backup

# 2. 拉最新镜像
docker compose pull

# 3. 重启（用新镜像重建容器）
docker compose up -d

# 注意：编辑器、yjs、ocr、docling 这些有 build 步骤的镜像不会被 pull，要重新 build：
docker compose build editor
docker compose up -d editor editor2 editor3
```

---

## 附录 A：一键脚本汇总

### A.1 宿主机初始化

```bash
# 一键跑完所有宿主机初始化（root 权限）
sudo ./setup-linux-host.sh
```

### A.2 部署包构建

```bash
# 假设当前在 MiaotongDoc 项目根目录

# 前端
cd miaotongdoc-web && npm install && npm run build && cd ..
rm -rf MiaotongDoc-Docker/app/web/dist/*
cp -r miaotongdoc-web/dist/* MiaotongDoc-Docker/app/web/dist/

# 后端
cd miaotongdoc-server && mvn clean package -DskipTests && cd ..
cp miaotongdoc-server/target/miaotongdoc.jar MiaotongDoc-Docker/app/server/

# 编辑器镜像（首次或源码有改）
cd MiaotongDoc-Docker && docker compose build editor
```

### A.3 生成强密码

```bash
DB_PASSWORD=$(openssl rand -base64 16)
REDIS_PASSWORD=$(openssl rand -base64 16)
RABBITMQ_PASSWORD=$(openssl rand -base64 16)
APP_JWT_SECRET=$(openssl rand -base64 48)
EDITOR_JWT_SECRET=$(openssl rand -base64 48)
SECURE_LINK_SECRET=$(openssl rand -base64 24)
MINIO_ACCESS_KEY=miniouser
MINIO_SECRET_KEY=$(openssl rand -base64 24)

echo "DB_PASSWORD=$DB_PASSWORD"
echo "REDIS_PASSWORD=$REDIS_PASSWORD"
echo "RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD"
echo "APP_JWT_SECRET=$APP_JWT_SECRET"
echo "EDITOR_JWT_SECRET=$EDITOR_JWT_SECRET"
echo "SECURE_LINK_SECRET=$SECURE_LINK_SECRET"
echo "MINIO_ACCESS_KEY=$MINIO_ACCESS_KEY"
echo "MINIO_SECRET_KEY=$MINIO_SECRET_KEY"
```

复制上面的输出到 `.env` 即可。

### A.4 健康检查一键脚本

```bash
cat > /opt/miaotongdoc/scripts/health-check.sh <<'EOF'
#!/bin/bash
DOMAIN="${1:-http://localhost}"
ERRORS=()

for url in "$DOMAIN/actuator/health" "$DOMAIN/" "http://localhost:9000/minio/health/live" "http://localhost:1234/health"; do
  CODE=$(curl -sk -o /dev/null -w '%{http_code}' "$url")
  if [ "$CODE" != "200" ]; then
    ERRORS+=("$url 返回 $CODE")
  fi
done

if [ ${#ERRORS[@]} -gt 0 ]; then
  echo "健康检查失败:" "${ERRORS[@]}" | mail -s "[MiaotongDoc] 告警" admin@mycompany.com
fi
EOF
chmod +x /opt/miaotongdoc/scripts/health-check.sh
```

### A.5 完整部署流程（从零到上线）

```bash
# === 1. 服务器准备 ===
# 买云服务器，安装 Ubuntu 22.04 LTS，开放安全组 22/80/443

# === 2. SSH 连接 ===
ssh root@你的IP

# === 3. 宿主机初始化（root 权限） ===
wget -O setup-linux-host.sh https://your-domain/setup-linux-host.sh
chmod +x setup-linux-host.sh
sudo ./setup-linux-host.sh

# 重新登录 SSH

# === 4. 安装 Docker ===
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# === 5. 上传部署包 ===
mkdir -p /opt/MiaotongDoc
# scp 上传代码到 /opt/MiaotongDoc/

# === 6. 配置 .env ===
cd /opt/MiaotongDoc/MiaotongDoc-Docker
cp .env.example .env
nano .env       # 改密码（用附录 A.3 生成）
chmod 600 .env

# === 7. 构建产物（如未预构建） ===
cd /opt/MiaotongDoc/miaotongdoc-web && npm install && npm run build && cd ../..
cd /opt/MiaotongDoc/miaotongdoc-server && mvn clean package -DskipTests && cd ../..

# === 8. 启动服务 ===
cd /opt/MiaotongDoc/MiaotongDoc-Docker
./deploy.sh start
# 等待 5-8 分钟

# === 9. 重置管理员密码 ===
cat > reset_pw.sql <<'EOF'
UPDATE sys_user SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm' WHERE employee_id = '10000000';
EOF
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < reset_pw.sql

# === 10. 验证 ===
docker compose ps
curl http://localhost:9004/actuator/health

# === 11. 配置 HTTPS（可选，强烈推荐） ===
# 见第 7 章

# === 12. 部署完成！浏览器访问 http://你的IP/，用 10000000 / 123456 登录 ===
```

---

## 附录 B：端口对照表

| 端口 | 服务 | 用途 | 对外暴露？ |
|------|------|------|----------|
| 22 / 2222 | SSH | 远程登录 | ✅ 必须 |
| 443 | Nginx HTTPS | 用户加密访问 | ✅ 必须 |
| 80 | Nginx HTTP | 用户访问（自动跳转 443） | ✅ 必须 |
| 9004 | web-server | 后端 API | ⚠️ 可选（建议走 80/443 反代） |
| 1234 | yjs-server | WebSocket 协同 | ⚠️ 可选（建议走 80/443 反代） |
| 9000 | MinIO S3 API | 对象存储 | ❌ 内网 |
| 9001 | MinIO Console | 存储管理界面 | ⚠️ 仅内网限定 |
| 15672 | RabbitMQ Management | 队列管理界面 | ⚠️ 仅内网限定 |
| 5432 | PostgreSQL | 数据库 | ❌ 内网（仅 Docker 网络） |
| 6379 | Redis | 缓存 | ❌ 内网 |
| 5672 | RabbitMQ AMQP | 消息队列 | ❌ 内网 |
| 9200 | Elasticsearch | 全文搜索 | ❌ 内网 |
| 5001 | Docling | AI 文档解析 | ❌ 内网（profile） |
| 5002 | Tesseract OCR | OCR 兜底 | ❌ 内网（profile） |
| 5003 | PaddleOCR | 中文 OCR 主力 | ❌ 内网 |

**铁律**：标 ❌ 的端口**绝对不要**在云安全组里开放！

---

## 附录 C：关键命令速查

### C.1 服务管理

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker

./deploy.sh start                          # 启动（分阶段）
./deploy.sh start --with-docling           # + Docling
./deploy.sh start --with-ocr --with-docling  # + Tesseract + Docling
./deploy.sh stop                            # 停（⚠️ 实际是 docker compose down）
docker compose stop                        # 停（推荐，保留容器）
./deploy.sh restart
./deploy.sh status
./deploy.sh health
./deploy.sh logs                            # 所有服务
./deploy.sh logs web-server                 # 单服务
./deploy.sh backup
./deploy.sh clean-logs
./deploy.sh build                           # 构建所有镜像
docker compose build editor                 # 构建编辑器
```

### C.2 容器操作

```bash
docker ps                          # 运行中的容器
docker ps -a                       # 所有容器
docker compose ps                  # 当前 compose 的容器
docker compose logs -f web-server  # 实时日志
docker compose exec web-server bash  # 进容器
docker compose restart web-server  # 重启
docker stats                       # 资源占用
docker system df                   # Docker 占用
docker system prune -a             # 清理所有未用资源（⚠️ 慎用）
```

### C.3 数据库操作

```bash
# 进 PG
docker compose exec postgres psql -U miaotong -d miaotongdocdb

# 备份
docker compose exec -T postgres pg_dump -U miaotong miaotongdocdb > backup.sql

# 恢复
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < backup.sql

# 看表
docker compose exec postgres psql -U miaotong -d miaotongdocdb -c '\dt'

# 看表大小
docker compose exec postgres psql -U miaotong -d miaotongdocdb \
  -c "SELECT tablename, pg_size_pretty(pg_total_relation_size(tablename::regclass)) FROM pg_tables WHERE schemaname='public' ORDER BY pg_total_relation_size(tablename::regclass) DESC LIMIT 10;"
```

### C.4 重置密码

```bash
cd /opt/MiaotongDoc/MiaotongDoc-Docker
cat > reset_pw.sql <<'EOF'
UPDATE sys_user SET password = '$2a$10$V.BH63HYFT1VHugUozl7r.oKJ9cAWI.4FlbGPojh1rIh7Lj.kHqPm' WHERE employee_id = '10000000';
EOF
docker compose exec -T postgres psql -U miaotong -d miaotongdocdb < reset_pw.sql
```

### C.5 SSH 客户端连接

```bash
# 密码登录
ssh -p 2222 root@你的IP

# 密钥登录
ssh -i ~/.ssh/miaotongdoc_server -p 2222 root@你的IP

# 配置文件别名（~/.ssh/config）
# Host mtd
#     HostName 你的IP
#     User root
#     Port 2222
#     IdentityFile ~/.ssh/miaotongdoc_server

ssh mtd    # 用别名登录
```

---

## 附录 D：相关文档

| 文档 | 路径 | 何时看 |
|------|------|--------|
| 项目开发参考手册 | [`CLAUDE.md`](../CLAUDE.md) | 改代码、查 API |
| 综合部署指南（含 Windows） | [`DEPLOY.md`](../DEPLOY.md) | 内网迁移 / Windows 部署 |
| Docker 部署基础说明 | [`README.md`](README.md) | 日常运维速查 |
| Docker Compose 编排 | `docker-compose.yml` | 改容器配置 |
| 一键部署脚本 | `deploy.sh` | 启动 / 停止 / 备份 |
| 宿主机初始化脚本 | `../setup-linux-host.sh` | 首次部署 / 重装系统 |
| OCR 离线部署详解 | `../plans/2026-08-09-ocr-models-offline-deploy.md` | OCR 模型烧入镜像 |
| 离线部署完整方案 | `../plans/2026-07-26-offline-deployment.md` | 纯内网部署 |

---

## 🎉 部署完成检查清单

确认以下全部通过：

```
□ 服务器：Linux 22.04/24.04 或 CentOS Stream 9/Rocky 9
□ 内存：≥ 8 GB
□ 磁盘：系统盘 50GB + 数据盘 100GB+
□ Docker Engine ≥ 20.10 + Compose V2
□ setup-linux-host.sh 跑过（文件句柄、内核、swap、firewalld、SSH）
□ .env 所有默认密码已改
□ deploy.sh start 跑过，13+ 容器 Up (healthy)
□ admin 密码已重置为 123456
□ docker compose ps 显示全 healthy
□ 浏览器访问 http://IP/ 能登录
□ 能上传/下载文档
□ 能打开 Word/Excel/PDF 在线编辑
□ （推荐）配了域名 + HTTPS
□ （推荐）配了异地备份
□ （推荐）配了监控告警
```

**全部勾上 = 你的 MiaotongDoc 已经稳定运行了！** 🎉

*本文档由 Claude Code 维护，遵循 CLAUDE.md 工程标准。*
*如有问题，先看 FAQ（10 章），再看项目根目录的 DEPLOY.md，最后问项目方。*