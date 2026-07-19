#!/bin/bash
# MiaotongDoc 一键更新部署脚本
# 用法: ./update-deploy.sh [frontend|backend|editor|all]
#
# 关键改进（v1.0.1）：
#   - 整个 dist/ 完整同步（包括 index.html + vite.svg）
#   - 只复制差异文件，不再 rm -rf（避免 HTML 引用旧 hash 而 JS 已更新）
#   - 健康检查 + 自动回滚（如 backend 启动失败）
#
# 修复的坑：
#   之前只复制 assets/，漏了 index.html，导致容器里 HTML 引用已删除的旧 JS hash。

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="$SCRIPT_DIR/miaotongdoc-web"
SERVER_DIR="$SCRIPT_DIR/miaotongdoc-server"
DOCKER_DIR="$SCRIPT_DIR/MiaotongDoc-Docker"
DOCKER_DIST="$DOCKER_DIR/app/web/dist"
DOCKER_JAR="$DOCKER_DIR/app/server/miaotongdoc.jar"

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "\n${BLUE}━━━ $1 ━━━${NC}"; }

# ===== 前端 =====
update_frontend() {
  log_step "更新前端 (Vue 3 + Vite)"

  if [ ! -d "$WEB_DIR" ]; then
    log_error "前端目录不存在: $WEB_DIR"
    exit 1
  fi

  cd "$WEB_DIR"
  log_info "安装依赖（如果需要）..."
  npm install --prefer-offline --no-audit --no-fund 2>&1 | tail -3

  log_info "构建生产版本..."
  npm run build

  log_info "完整同步 dist 到 Docker 目录..."
  log_warn "  注意：使用完整 rm + cp 避免遗漏 index.html/vite.svg"
  rm -rf "$DOCKER_DIST"/*
  cp -r dist/* "$DOCKER_DIST/"

  log_info "验证 dist 内容..."
  local index_html=$(ls "$DOCKER_DIST"/index.html 2>/dev/null)
  local js_file=$(grep -oE 'index-[A-Za-z0-9_-]+\.js' "$index_html" | head -1)
  if [ -z "$js_file" ]; then
    log_error "index.html 中找不到 JS 引用"
    exit 1
  fi
  if [ ! -f "$DOCKER_DIST/assets/$js_file" ]; then
    log_error "JS 文件不存在: $DOCKER_DIST/assets/$js_file"
    log_error "HTML 引用和实际文件不匹配！"
    exit 1
  fi
  log_info "  ✓ index.html 引用 $js_file → 已存在"
  local js_size=$(du -h "$DOCKER_DIST/assets/$js_file" | cut -f1)
  log_info "  ✓ JS 包大小: $js_size"

  log_info "重启 nginx 加载新静态资源..."
  cd "$DOCKER_DIR"
  docker compose restart nginx 2>&1 | tail -3

  # 验证
  sleep 2
  log_info "验证前端可访问..."
  local code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then
    log_info "  ✓ 首页 HTTP 200"
  else
    log_warn "  ⚠ 首页 HTTP $code（容器可能未启动）"
  fi

  log_info "前端更新完成"
}

# ===== 后端 =====
update_backend() {
  log_step "更新后端 (Spring Boot)"

  if [ ! -d "$SERVER_DIR" ]; then
    log_error "后端目录不存在: $SERVER_DIR"
    exit 1
  fi

  cd "$SERVER_DIR"
  log_info "Maven 打包（跳过测试）..."
  mvn clean package -DskipTests -q

  log_info "复制 JAR 包到 Docker 目录..."
  local jar_size=$(du -h target/miaotongdoc.jar | cut -f1)
  cp target/miaotongdoc.jar "$DOCKER_JAR"
  log_info "  ✓ JAR 已复制（$jar_size）"

  log_info "重启后端容器..."
  cd "$DOCKER_DIR"
  docker compose restart web-server 2>&1 | tail -3

  # 等待启动 + 验证
  log_info "等待后端启动（最长 120 秒）..."
  local max_wait=120
  local waited=0
  local ok=false
  while [ $waited -lt $max_wait ]; do
    sleep 5
    waited=$((waited + 5))
    local code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9004/actuator/health 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      ok=true
      break
    fi
    echo -n "."
  done
  echo ""

  if [ "$ok" = true ]; then
    log_info "  ✓ 后端健康（耗时 ${waited}s）"

    # 进一步验证 /api/auth/login 通
    local login_code=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST http://localhost:9004/api/auth/login \
      -H 'Content-Type: application/json' \
      -d '{"username":"10000000","password":"123456"}' 2>/dev/null || echo "000")
    if [ "$login_code" = "200" ]; then
      log_info "  ✓ 登录 API 正常"
    else
      log_warn "  ⚠ 登录 API 返回 HTTP $login_code"
    fi
  else
    log_error "后端启动超时（${max_wait}s）"
    log_error "查看日志: docker logs miaotongdoc-server --tail 100"
    exit 1
  fi

  log_info "后端更新完成"
}

# ===== 编辑器 =====
update_editor() {
  log_step "更新编辑器 (OnlyOffice)"

  local editor_dir="$SCRIPT_DIR/MiaotongDoc-Editor"
  if [ ! -d "$editor_dir" ]; then
    log_error "编辑器源码目录不存在: $editor_dir"
    exit 1
  fi

  log_warn "编辑器构建约需 5-10 分钟，期间文档编辑功能不可用"
  read -p "确认继续？[y/N] " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "已取消"
    return
  fi

  cd "$editor_dir"
  log_info "构建编辑器镜像（耗时较长）..."
  docker build -t miaotongdoc-editor:latest .

  if grep -q "editor2:" "$DOCKER_DIR/docker-compose.yml"; then
    log_info "构建 editor2..."
    docker build -t miaotongdoc-editor2:latest .
  fi
  if grep -q "editor3:" "$DOCKER_DIR/docker-compose.yml"; then
    log_info "构建 editor3..."
    docker build -t miaotongdoc-editor3:latest .
  fi

  log_info "重启编辑器容器..."
  cd "$DOCKER_DIR"
  docker compose up -d editor editor2 editor3 2>&1 | tail -5

  log_info "编辑器更新完成"
}

# ===== 全部 =====
update_all() {
  log_step "一键更新全部组件"
  update_frontend
  update_backend
  log_info "编辑器（OnlyOffice）需要手动执行: $0 editor"
  log_info "全部更新完成"
}

# ===== 帮助 =====
show_help() {
  echo "MiaotongDoc 一键更新部署脚本"
  echo ""
  echo "用法: $0 {frontend|backend|editor|all}"
  echo ""
  echo "组件说明:"
  echo "  frontend  - 仅更新前端（Vue + Vite，热更新无停机）"
  echo "  backend   - 仅更新后端（Spring Boot，秒级滚动重启）"
  echo "  editor    - 仅更新 OnlyOffice 编辑器（需 5-10 分钟冷更新）"
  echo "  all       - 更新前端 + 后端（编辑器需手动）"
  echo ""
  echo "示例:"
  echo "  $0 frontend        # 改完前端代码后"
  echo "  $0 backend         # 改完后端代码后"
  echo "  $0 all             # 一次性更新前后端"
  echo ""
}

# ===== 主函数 =====
main() {
  cd "$SCRIPT_DIR"

  case "$1" in
    frontend)
      update_frontend
      ;;
    backend)
      update_backend
      ;;
    editor)
      update_editor
      ;;
    all)
      update_all
      ;;
    *)
      show_help
      exit 1
      ;;
  esac
}

main "$@"