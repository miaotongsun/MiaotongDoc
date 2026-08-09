#!/bin/bash
# MiaotongDoc 镜像离线导出脚本
#
# 用途：在**外网构建机**上把项目全部 Docker 镜像打包成 tar，供内网离线导入。
#
# 用法：
#   ./export-images.sh                      # 导出核心包到 ./offline-package/
#   ./export-images.sh --all                # 核心 + Tesseract + Docling
#   ./export-images.sh --with-ocr           # 核心 + Tesseract 兜底
#   ./export-images.sh --with-docling       # 核心 + Docling
#   ./export-images.sh -o /d/Docker         # 指定输出目录
#   ./export-images.sh --no-compress        # 不 gzip（省时间，费空间）
#
# 输出：
#   miaotongdoc-core.tar.gz      核心镜像（11 个，~15.4GB → 压缩后约 6-8GB）
#   miaotongdoc-ocr.tar.gz       Tesseract 兜底（可选，~1.07GB）
#   miaotongdoc-docling.tar.gz   Docling（可选，~12GB）
#   manifest.txt                 镜像清单与校验信息
#   SHA256SUMS                   完整性校验
#
# 内网导入见 plans/offline-env-requirements.md

set -euo pipefail

# ===== 默认配置 =====
OUT_DIR="./offline-package"
WITH_OCR=false
WITH_DOCLING=false
COMPRESS=true

# ===== 参数解析 =====
while [[ $# -gt 0 ]]; do
    case "$1" in
        --all)          WITH_OCR=true; WITH_DOCLING=true; shift ;;
        --with-ocr)     WITH_OCR=true; shift ;;
        --with-docling) WITH_DOCLING=true; shift ;;
        --no-compress)  COMPRESS=false; shift ;;
        -o|--output)    OUT_DIR="$2"; shift 2 ;;
        -h|--help)      sed -n '2,22p' "$0"; exit 0 ;;
        *)              echo "未知参数: $1"; exit 1 ;;
    esac
done

# ===== 颜色输出 =====
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ===== 镜像清单（与 docker-compose.yml 实际引用一一对应）=====
# 注意：miaotongdoc-docker-* 前缀由目录名 MiaotongDoc-Docker 推导，
#       内网部署目录名必须一致，或在 .env 设 COMPOSE_PROJECT_NAME=miaotongdoc-docker
CORE_IMAGES=(
    "miaotongdoc-editor:latest"                 # compose:213/264/310  ×3 实例
    "miaotongdoc-docker-ocr-paddle:latest"      # compose:543          默认启动
    "elasticsearch:8.11.0"                      # compose:414
    "postgres:12"                               # compose:36
    "eclipse-temurin:17-jre"                    # compose:116          web-server
    "rabbitmq:3-management"                     # compose:90
    "minio/minio:latest"                        # compose:386
    "nginx:latest"                              # compose:9
    "miaotongdoc-docker-yjs-server:latest"      # compose:440
    "redis:7-alpine"                            # compose:65
    "alpine:latest"                             # compose:357/372      cache-cleaner/logrotate
)
OCR_IMAGES=("miaotongdoc-docker-ocr:latest")        # compose:512  profile: ocr
DOCLING_IMAGES=("miaotongdoc-docker-docling:latest") # compose:468  profile: docling

# ===== 前置检查 =====
log_info "===== MiaotongDoc 镜像离线导出 ====="

if ! command -v docker &>/dev/null; then
    log_error "Docker 未安装"
    exit 1
fi

HOST_ARCH=$(docker info --format '{{.Architecture}}' 2>/dev/null || uname -m)
log_info "构建机架构: ${HOST_ARCH}"
if [[ "$HOST_ARCH" != "x86_64" && "$HOST_ARCH" != "amd64" ]]; then
    log_warn "当前非 x86_64 架构。镜像不跨架构，请确认内网目标机架构一致！"
fi

# 校验镜像是否存在
check_images() {
    local missing=()
    for img in "$@"; do
        if ! docker image inspect "$img" &>/dev/null; then
            missing+=("$img")
        fi
    done
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "以下镜像不存在，请先构建/拉取："
        for m in "${missing[@]}"; do echo "         - $m"; done
        echo ""
        log_error "构建自有镜像：cd MiaotongDoc-Docker && docker compose --profile all build"
        exit 1
    fi
}

log_info "[1/4] 校验镜像存在性..."
ALL_CHECK=("${CORE_IMAGES[@]}")
if [ "$WITH_OCR" = true ]; then     ALL_CHECK+=("${OCR_IMAGES[@]}"); fi
if [ "$WITH_DOCLING" = true ]; then ALL_CHECK+=("${DOCLING_IMAGES[@]}"); fi
check_images "${ALL_CHECK[@]}"
log_info "全部 ${#ALL_CHECK[@]} 个镜像已就绪"

# 磁盘空间预检
mkdir -p "$OUT_DIR"
NEED_GB=18
if [ "$WITH_OCR" = true ]; then     NEED_GB=$((NEED_GB + 2)); fi
if [ "$WITH_DOCLING" = true ]; then NEED_GB=$((NEED_GB + 13)); fi
AVAIL_GB=$(df -BG "$OUT_DIR" 2>/dev/null | awk 'NR==2{gsub("G","",$4); print $4+0}')
if [ -z "${AVAIL_GB:-}" ] || [ "${AVAIL_GB}" -eq 0 ] 2>/dev/null; then
    log_warn "无法检测可用磁盘空间，跳过预检（需约 ${NEED_GB}G）"
else
    log_info "输出目录: $(cd "$OUT_DIR" && pwd)  可用 ${AVAIL_GB}G / 需要约 ${NEED_GB}G"
    if [ "$AVAIL_GB" -lt "$NEED_GB" ]; then
        log_error "磁盘空间不足（需约 ${NEED_GB}G，可用 ${AVAIL_GB}G）"
        log_error "可用 -o <其他盘路径> 指定输出目录"
        exit 1
    fi
fi

# ===== 导出 =====
save_bundle() {
    local name="$1"; shift
    local tar_path="${OUT_DIR}/${name}.tar"
    log_info "导出 ${name}（$# 个镜像）..."
    docker save -o "$tar_path" "$@"
    local size=$(du -h "$tar_path" | cut -f1)
    log_info "  → ${name}.tar  ${size}"

    if [ "$COMPRESS" = true ]; then
        log_info "  压缩中（可能需要几分钟）..."
        gzip -f "$tar_path"
        local gsize=$(du -h "${tar_path}.gz" | cut -f1)
        log_info "  → ${name}.tar.gz  ${gsize}"
    fi
}

log_info "[2/4] 导出镜像包..."
save_bundle "miaotongdoc-core" "${CORE_IMAGES[@]}"
if [ "$WITH_OCR" = true ]; then     save_bundle "miaotongdoc-ocr" "${OCR_IMAGES[@]}"; fi
if [ "$WITH_DOCLING" = true ]; then save_bundle "miaotongdoc-docling" "${DOCLING_IMAGES[@]}"; fi

# ===== 生成清单 =====
log_info "[3/4] 生成清单 manifest.txt..."
{
    echo "MiaotongDoc 离线镜像包清单"
    echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "构建机架构: ${HOST_ARCH}"
    echo "Docker: $(docker --version)"
    echo "Compose: $(docker compose version --short 2>/dev/null || echo 'N/A')"
    echo ""
    echo "== 核心镜像（默认必需）=="
    for img in "${CORE_IMAGES[@]}"; do
        printf "  %-45s %s\n" "$img" "$(docker image inspect "$img" --format '{{.Size}}' | awk '{printf "%.2f GB", $1/1024/1024/1024}')"
    done
    if [ "$WITH_OCR" = true ]; then
        echo ""; echo "== Tesseract 兜底（--profile ocr）=="
        for img in "${OCR_IMAGES[@]}"; do echo "  $img"; done
    fi
    if [ "$WITH_DOCLING" = true ]; then
        echo ""; echo "== Docling（--profile docling）=="
        for img in "${DOCLING_IMAGES[@]}"; do echo "  $img"; done
    fi
    echo ""
    echo "== 内网导入步骤 =="
    echo "  1. 校验完整性:  sha256sum -c SHA256SUMS"
    echo "  2. 解压:        gunzip miaotongdoc-core.tar.gz"
    echo "  3. 加载:        docker load -i miaotongdoc-core.tar"
    echo "  4. 核对:        docker images"
    echo "  5. 启动:        cd MiaotongDoc-Docker && ./deploy.sh start"
    echo ""
    echo "== 重要提醒 =="
    echo "  - 部署目录名必须是 MiaotongDoc-Docker（或 .env 设 COMPOSE_PROJECT_NAME=miaotongdoc-docker）"
    echo "  - 必须用 ./deploy.sh start，不能用裸 docker compose up -d（Flyway V9 依赖 editor 先启动）"
    echo "  - 环境依赖详见 plans/offline-env-requirements.md"
} > "${OUT_DIR}/manifest.txt"

log_info "[4/4] 生成校验和 SHA256SUMS..."
(cd "$OUT_DIR" && sha256sum ./*.tar* > SHA256SUMS 2>/dev/null || log_warn "sha256sum 不可用，跳过")

# ===== 完成 =====
echo ""
log_info "===== 导出完成 ====="
ls -lh "$OUT_DIR"
echo ""
log_info "下一步：将 ${OUT_DIR} 整个目录 + MiaotongDoc-Docker/ 拷贝到内网"
log_warn "注意：不要拷贝 MiaotongDoc-Docker/data/（含外网测试数据）"
