#!/bin/sh
# 日志轮转执行脚本
# 在容器中运行 logrotate

logrotate /etc/logrotate.d/app-logs --state /var/lib/logrotate/logrotate.status --verbose
