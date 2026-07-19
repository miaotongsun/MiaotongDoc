#!/bin/bash
# MiaotongDoc 一键部署脚本
# 使用方法: ./deploy.sh [start|stop|restart|status|logs|build]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查前置条件
check_prerequisites() {
    log_info "检查前置条件..."

    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi

    # 检查 Docker Compose
    if ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi

    # 检查 .env 文件
    if [ ! -f .env ]; then
        log_error ".env 文件不存在，请先创建 .env 文件"
        exit 1
    fi

    log_info "前置条件检查通过"
}

# 创建数据目录
create_data_dirs() {
    log_info "创建数据目录..."

    mkdir -p data/{documents,pgdata,minio,rabbitmq,editor,editor-cache,redis,yjs}
    mkdir -p data/logs/{server,nginx,postgres,editor,editor2,editor3,redis,rabbitmq,minio}

    log_info "数据目录创建完成"
}

# 构建镜像
build_images() {
    log_info "构建 Docker 镜像..."

    # 构建 OnlyOffice 编辑器镜像
    docker compose build editor

    log_info "镜像构建完成"
}

# 分阶段启动服务（按依赖顺序，避免 Flyway V9 等需要 editor 先启动的坑）
start_services() {
    log_info "按依赖顺序分阶段启动服务..."

    # 创建数据目录
    create_data_dirs

    # 阶段 A: 基础设施（postgres/redis/elasticsearch/minio）
    log_info "[阶段 A] 启动基础设施..."
    docker compose up -d postgres redis elasticsearch minio
    wait_for_healthy "postgres" "redis" "elasticsearch" "minio"

    # 阶段 B: RabbitMQ
    log_info "[阶段 B] 启动 RabbitMQ..."
    docker compose up -d rabbitmq
    wait_for_healthy "rabbitmq"

    # 阶段 C: OnlyOffice 编辑器（会创建 task_result 表，供 Flyway V9 使用）
    log_info "[阶段 C] 启动 OnlyOffice 编辑器..."
    docker compose up -d editor editor2 editor3
    log_info "等待编辑器初始化（约 1-2 分钟）..."
    sleep 60
    wait_for_healthy "editor"

    # 阶段 D: 后端 web-server（执行 Flyway 迁移）
    log_info "[阶段 D] 启动后端 web-server..."
    docker compose up -d web-server
    log_info "等待 Flyway 迁移完成..."
    wait_for_healthy "web-server"

    # 阶段 E: yjs-server + nginx
    log_info "[阶段 E] 启动 yjs + nginx..."
    docker compose up -d yjs-server nginx
    wait_for_healthy "nginx"

    # 最终状态
    log_info "部署完成！"
    echo ""
    echo "访问地址:"
    echo "  - 前端: http://localhost"
    echo "  - 后端 API: http://localhost:9004"
    echo "  - MinIO 控制台: http://localhost:9001"
    echo "  - RabbitMQ 管理: http://localhost:15672"
    echo ""
}

# 等待指定服务 healthy(超时 5 分钟)
wait_for_healthy() {
    local timeout=300
    local interval=10
    local elapsed=0
    for svc in "$@"; do
        log_info "等待 $svc healthy..."
        while ! docker compose ps "$svc" 2>/dev/null | grep -q "(healthy)"; do
            if [ $elapsed -ge $timeout ]; then
                log_warn "$svc 未在 ${timeout}s 内 healthy，继续执行（可能是已启动或 unhealthy 但可运行）"
                break
            fi
            sleep $interval
            elapsed=$((elapsed + interval))
        done
        elapsed=0  # 重置给下一个服务
    done
}

# 停止服务（保留容器，不丢失数据）
stop_services() {
    log_info "停止服务（保留容器和数据）..."
    docker compose stop --timeout 60
    log_info "服务已停止"
}

# 重启服务
restart_services() {
    log_info "重启服务..."
    docker compose stop --timeout 60
    docker compose up -d
    log_info "服务已重启"
}

# 检查服务状态
check_status() {
    log_info "服务状态:"
    docker compose ps
}

# 检查健康状态
check_health() {
    log_info "检查服务健康状态..."

    # 检查 PostgreSQL
    if docker compose exec -T postgres pg_isready -U miaotong -d miaotongdocdb &> /dev/null; then
        log_info "PostgreSQL: 健康"
    else
        log_warn "PostgreSQL: 未就绪"
    fi

    # 检查 Redis
    if docker compose exec -T redis redis-cli -a ${REDIS_PASSWORD} ping &> /dev/null; then
        log_info "Redis: 健康"
    else
        log_warn "Redis: 未就绪"
    fi

    # 检查 Web Server
    if docker compose exec -T web-server wget -q --spider http://localhost:9004/actuator/health &> /dev/null; then
        log_info "Web Server: 健康"
    else
        log_warn "Web Server: 未就绪"
    fi

    # 检查 MinIO
    if docker compose exec -T minio mc ready local &> /dev/null; then
        log_info "MinIO: 健康"
    else
        log_warn "MinIO: 未就绪"
    fi
}

# 查看日志
view_logs() {
    if [ -z "$1" ]; then
        docker compose logs -f
    else
        docker compose logs -f "$1"
    fi
}

# 清理日志
clean_logs() {
    log_warn "清理超过 30 天的日志..."
    find data/logs -name "*.gz" -mtime +30 -delete 2>/dev/null || true
    log_info "日志清理完成"
}

# 备份数据
backup_data() {
    BACKUP_DIR="backup_$(date +%Y%m%d_%H%M%S)"
    log_info "备份数据到 ${BACKUP_DIR}..."

    mkdir -p "${BACKUP_DIR}"

    # 备份 PostgreSQL
    docker compose exec -T postgres pg_dump -U miaotong miaotongdocdb > "${BACKUP_DIR}/database.sql"

    # 备份文档
    cp -r data/documents "${BACKUP_DIR}/documents"

    # 备份配置
    cp -r config "${BACKUP_DIR}/config"
    cp .env "${BACKUP_DIR}/.env"

    log_info "备份完成: ${BACKUP_DIR}"
}

# 主函数
main() {
    cd "$(dirname "$0")"

    case "$1" in
        start)
            check_prerequisites
            start_services
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        status)
            check_status
            ;;
        health)
            check_health
            ;;
        logs)
            view_logs "$2"
            ;;
        build)
            check_prerequisites
            build_images
            ;;
        backup)
            backup_data
            ;;
        clean-logs)
            clean_logs
            ;;
        *)
            echo "MiaotongDoc 部署脚本"
            echo ""
            echo "使用方法: $0 {start|stop|restart|status|health|logs|build|backup|clean-logs}"
            echo ""
            echo "命令说明:"
            echo "  start       - 启动所有服务"
            echo "  stop        - 停止所有服务"
            echo "  restart     - 重启所有服务"
            echo "  status      - 查看服务状态"
            echo "  health      - 检查服务健康状态"
            echo "  logs        - 查看日志 (可指定服务名)"
            echo "  build       - 构建 Docker 镜像"
            echo "  backup      - 备份数据"
            echo "  clean-logs  - 清理旧日志"
            exit 1
            ;;
    esac
}

main "$@"
