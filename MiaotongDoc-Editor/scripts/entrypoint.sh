#!/bin/bash
# MiaotongDoc Editor 自定义入口脚本
# 在容器启动前处理配置模板中的环境变量

CONFIG_TEMPLATE="/etc/onlyoffice/documentserver/local.json.template"
CONFIG_FILE="/etc/onlyoffice/documentserver/local.json"

# 如果模板文件存在，使用 envsubst 替换环境变量
if [ -f "$CONFIG_TEMPLATE" ]; then
    echo "[MiaotongDoc] Processing configuration template..."
    envsubst '${DB_PASSWORD} ${EDITOR_JWT_SECRET} ${REDIS_PASSWORD} ${RABBITMQ_PASSWORD} ${SECURE_LINK_SECRET}' < "$CONFIG_TEMPLATE" > "$CONFIG_FILE"
    echo "[MiaotongDoc] Configuration ready."
fi

# 确保 AI 代理 URL 为浏览器可访问的相对路径（而非 Docker 内部地址）
if command -v python3 &>/dev/null && [ -f "$CONFIG_FILE" ]; then
    python3 -c "
import json
with open('$CONFIG_FILE', 'r') as f:
    cfg = json.load(f)
changed = False
ai = cfg.get('services', {}).get('CoAuthoring', {}).get('aiSettings', {})
if ai.get('proxy', '').startswith('http'):
    ai['proxy'] = '/api/ai/proxy'
    changed = True
if changed:
    with open('$CONFIG_FILE', 'w') as f:
        json.dump(cfg, f, indent=2)
    print('[MiaotongDoc] Fixed aiSettings.proxy to relative URL')
" 2>/dev/null || true
fi
