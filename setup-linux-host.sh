#!/bin/bash
# MiaotongDoc Linux 宿主机一键初始化脚本
#
# 用途：内网新机器部署前运行一次，自动完成：
#   1. 文件句柄与进程数限制
#   2. 内核参数优化
#   3. Docker 守护进程配置
#   4. 时间同步（chrony）
#   5. swap 配置
#   6. 防火墙规则（firewalld）
#   7. 日志归档（logrotate）
#   8. SSH 安全加固
#   9. 自动备份 cron
#
# 用法：sudo ./setup-linux-host.sh [选项]
#   选项：
#     --skip-firewall    跳过防火墙配置（已有其他防火墙时）
#     --skip-ssh         跳过 SSH 加固
#     --swap-size 16G    swap 大小（默认 16G）
#     --no-backup        不配置自动备份 cron
#     -h | --help        显示帮助
#
# 注意：
#   - 必须以 root 运行（或 sudo）
#   - 部分配置重启 SSH 后生效
#   - 建议在内网部署机器上首次启动时执行

set -e

# ===== 默认配置 =====
SKIP_FIREWALL=false
SKIP_SSH=false
SKIP_BACKUP=false
SWAP_SIZE="16G"

# ===== 参数解析 =====
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-firewall) SKIP_FIREWALL=true; shift ;;
        --skip-ssh) SKIP_SSH=true; shift ;;
        --skip-backup) SKIP_BACKUP=true; shift ;;
        --swap-size) SWAP_SIZE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,30p' "$0"
            exit 0
            ;;
        *)
            echo "未知参数: $1"; exit 1 ;;
    esac
done

# ===== 颜色输出 =====
RED='\033[0;31m'
GREEN='\033[0:32m'
YELLOW='\033[1;33m'
NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ===== 前置检查 =====
if [[ $EUID -ne 0 ]]; then
    log_error "必须以 root 权限运行"
    exit 1
fi

if ! command -v systemctl &>/dev/null; then
    log_error "需要 systemd"
    exit 1
fi

log_info "===== MiaotongDoc Linux 宿主机初始化 ====="
log_info "swap 大小: $SWAP_SIZE"
log_info "跳过防火墙: $SKIP_FIREWALL"
log_info "跳过 SSH 加固: $SKIP_SSH"
log_info "跳过备份: $SKIP_BACKUP"
echo ""

# ===== 1. 文件句柄与进程数 =====
log_info "[1/9] 配置文件句柄与进程数限制..."
cat > /etc/security/limits.d/99-miaotongdoc.conf <<'EOF'
*       soft    nofile    65536
*       hard    nofile    65536
*       soft    nproc     16384
*       hard    nproc     16384
root    soft    nofile    65536
root    hard    nofile    65536
EOF

# ===== 2. 内核参数 =====
log_info "[2/9] 配置内核参数..."
cat > /etc/sysctl.d/99-miaotongdoc.conf <<'EOF'
vm.swappiness = 10
vm.overcommit_memory = 1
vm.overcommit_ratio = 50
net.core.somaxconn = 4096
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 4096
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
fs.file-max = 2097152
fs.nr_open = 1048576
vm.oom_kill_allocating_task = 0
EOF
sysctl --system

# ===== 3. Docker 守护进程 =====
log_info "[3/9] 配置 Docker 守护进程..."
if ! command -v docker &>/dev/null; then
    log_warn "Docker 未安装，请先安装 Docker"
else
    mkdir -p /etc/docker
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
    log_info "Docker daemon.json 已配置"
fi

# ===== 4. 时间同步 =====
log_info "[4/9] 配置时间同步 chrony..."
if command -v apt-get &>/dev/null; then
    apt-get install -y chrony
elif command -v yum &>/dev/null; then
    yum install -y chrony
fi
systemctl enable --now chronyd
chronyc tracking 2>/dev/null || log_warn "chrony 启动失败，请手动检查"

# ===== 5. swap =====
log_info "[5/9] 配置 swap (${SWAP_SIZE})..."
if swapon --show | grep -q "/swapfile"; then
    log_warn "swapfile 已存在，跳过"
else
    fallocate -l "$SWAP_SIZE" /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    if ! grep -q "^/swapfile" /etc/fstab; then
        echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
    log_info "swap 已创建并持久化"
fi

# ===== 6. 防火墙 =====
if [[ "$SKIP_FIREWALL" == "false" ]]; then
    log_info "[6/9] 配置防火墙规则..."
    if command -v firewall-cmd &>/dev/null; then
        firewall-cmd --permanent --add-port=80/tcp
        firewall-cmd --permanent --add-port=9004/tcp
        firewall-cmd --permanent --add-port=9000-9001/tcp
        firewall-cmd --permanent --add-port=1234/tcp
        # 15672 RabbitMQ 管理端口（建议仅内网限定）
        firewall-cmd --permanent --add-port=15672/tcp
        firewall-cmd --reload
        log_info "firewalld 规则已应用"
    elif command -v ufw &>/dev/null; then
        ufw allow 80/tcp
        ufw allow 9004/tcp
        ufw allow 9000:9001/tcp
        ufw allow 1234/tcp
        ufw allow 15672/tcp
        log_warn "ufw 规则已添加，请手动 ufw enable"
    else
        log_warn "未检测到 firewalld 或 ufw，请手动配置防火墙"
    fi
else
    log_warn "[6/9] 跳过防火墙配置（--skip-firewall）"
fi

# ===== 7. 日志归档 =====
log_info "[7/9] 配置 Docker 日志归档..."
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

# ===== 8. SSH 加固 =====
if [[ "$SKIP_SSH" == "false" ]]; then
    log_info "[8/9] SSH 安全加固..."
    log_warn "将修改 SSH 端口为 2222，禁止 root 登录和密码登录"
    read -p "确认修改 SSH 配置？(y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak
        sed -i 's/^#Port 22/Port 2222/; s/^Port 22/Port 2222/' /etc/ssh/sshd_config
        sed -i 's/^PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
        sed -i 's/^#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config
        sed -i 's/^PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
        sed -i 's/^#MaxAuthTries 6/MaxAuthTries 3/' /etc/ssh/sshd_config
        systemctl restart sshd
        log_info "SSH 已加固，新端口 2222，禁止 root 和密码登录"
    else
        log_warn "已跳过 SSH 加固"
    fi
else
    log_warn "[8/9] 跳过 SSH 加固（--skip-ssh）"
fi

# ===== 9. 自动备份 =====
if [[ "$SKIP_BACKUP" == "false" ]]; then
    log_info "[9/9] 配置自动备份 cron..."
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    CRON_LINE="0 3 * * * root cd ${SCRIPT_DIR} && ./deploy.sh backup >>/var/log/miaotongdoc-backup.log 2>&1 && find ${SCRIPT_DIR}/backup_* -maxdepth 0 -mtime +7 -exec rm -rf {} \;"
    cat > /etc/cron.d/miaotongdoc-backup <<EOF
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
${CRON_LINE}
EOF
    chmod 644 /etc/cron.d/miaotongdoc-backup
    log_info "已配置每日凌晨 3 点自动备份，保留 7 天"
else
    log_warn "[9/9] 跳过自动备份（--skip-backup）"
fi

echo ""
log_info "===== 初始化完成 ====="
log_info "下一步："
log_info "  1. 重新登录 SSH 使 limits 生效"
log_info "  2. cd MiaotongDoc-Docker && docker compose up -d"
log_info "  3. 验证部署见 DEPLOY.md 第 5 步"