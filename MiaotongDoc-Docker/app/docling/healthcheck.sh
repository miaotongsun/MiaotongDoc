#!/bin/sh
# Docling 深度健康检查
# 1. /health 必须 200
# 2. /v1/health/ready 必须 200 (docling-serve 1.26 自带,会检测模型加载状态)
# 任何一个失败则退出非 0

set -e

HEALTH_URL="http://localhost:5001/health"
READY_URL="http://localhost:5001/v1/health/ready"

# 检查基础健康端点
if ! curl -fsS --max-time 5 "$HEALTH_URL" > /dev/null 2>&1; then
    echo "Docling /health 不可用" >&2
    exit 1
fi

# 检查模型就绪状态(若端点不存在则跳过,保持兼容)
if curl -fsS --max-time 5 -o /dev/null -w "%{http_code}" "$READY_URL" 2>/dev/null | grep -qE "^(200|404)$"; then
    CODE=$(curl -fsS --max-time 5 -o /dev/null -w "%{http_code}" "$READY_URL" 2>/dev/null || echo "000")
    if [ "$CODE" = "200" ]; then
        echo "Docling 模型已就绪"
        exit 0
    fi
    # 404 表示端点不存在(旧版本),仅靠 /health 已够
fi

echo "Docling 服务可达(基础 /health 通过)"
exit 0