#!/bin/sh
# OnlyOffice 文件缓存清理脚本
# 清理超过指定时间的缓存文件，不影响正在编辑的文档

CACHE_DIR="/var/cache/onlyoffice/cache/files/data"
FORGOTTEN_DIR="/var/cache/onlyoffice/cache/files/forgotten"
MAX_AGE_HOURS=${CACHE_MAX_AGE_HOURS:-24}

echo "[$(date)] 开始清理 OnlyOffice 缓存..."
echo "[$(date)] 过期时间: ${MAX_AGE_HOURS}h"

if [ ! -d "$CACHE_DIR" ]; then
    echo "[$(date)] 缓存目录不存在，跳过"
    exit 0
fi

BEFORE_SIZE=$(du -sh "$CACHE_DIR" 2>/dev/null | cut -f1)
BEFORE_COUNT=$(find "$CACHE_DIR" -mindepth 1 -maxdepth 1 | wc -l)

# 清理超过 MAX_AGE_HOURS 的目录和文件
find "$CACHE_DIR" -mindepth 1 -maxdepth 1 -type d -mmin +$((MAX_AGE_HOURS * 60)) -exec rm -rf {} + 2>/dev/null
find "$CACHE_DIR" -mindepth 1 -maxdepth 1 -type f -mmin +$((MAX_AGE_HOURS * 60)) -delete 2>/dev/null

# 清理 forgotten 目录（文档关闭时的临时文件）
if [ -d "$FORGOTTEN_DIR" ]; then
    find "$FORGOTTEN_DIR" -mindepth 1 -mmin +$((MAX_AGE_HOURS * 60)) -exec rm -rf {} + 2>/dev/null
fi

AFTER_SIZE=$(du -sh "$CACHE_DIR" 2>/dev/null | cut -f1)
AFTER_COUNT=$(find "$CACHE_DIR" -mindepth 1 -maxdepth 1 | wc -l)

echo "[$(date)] 清理完成: ${BEFORE_COUNT}→${AFTER_COUNT} 项, ${BEFORE_SIZE}→${AFTER_SIZE}"
