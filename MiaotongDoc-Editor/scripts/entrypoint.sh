#!/bin/bash
# MiaotongDoc Editor 自定义入口脚本
# 在容器启动前处理配置模板中的环境变量

CONFIG_TEMPLATE="/etc/onlyoffice/documentserver/local.json.template"
CONFIG_FILE="/etc/onlyoffice/documentserver/local.json"

# 如果模板文件存在，使用 envsubst 替换环境变量
if [ -f "$CONFIG_TEMPLATE" ]; then
    echo "[MiaotongDoc] Processing configuration template..."
    envsubst '${DB_PASSWORD} ${EDITOR_JWT_SECRET} ${REDIS_PASSWORD}' < "$CONFIG_TEMPLATE" > "$CONFIG_FILE"
    echo "[MiaotongDoc] Configuration ready."
fi
