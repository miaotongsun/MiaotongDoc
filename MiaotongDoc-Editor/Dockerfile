# MiaotongDoc Editor - 企业级在线文档编辑器
# 自定义品牌、解除并发限制、集成签署功能

ARG BASE_IMAGE
FROM ${BASE_IMAGE}

LABEL maintainer="MiaotongDoc Team"
LABEL description="MiaotongDoc Editor - 企业级在线文档编辑器"

ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production

# 编辑器安装目录
ENV EDITOR_HOME=/var/www/onlyoffice
ENV EDITOR_CONFIG=/etc/onlyoffice/documentserver

# 替换 docservice 二进制（解除并发限制：20 -> 127）
# 原始 LICENSE_CONNECTIONS 值在偏移 0x04a66e3c，由 0d 14(20) 改为 0d 7f(127)
COPY docservice_patched /var/www/onlyoffice/documentserver/server/DocService/docservice
RUN chmod +x /var/www/onlyoffice/documentserver/server/DocService/docservice

# 备份原始资源
RUN mkdir -p ${EDITOR_HOME}/branding/backup && \
    cp -r ${EDITOR_HOME}/web-apps/apps/*/main/resources/img/* ${EDITOR_HOME}/branding/backup/ 2>/dev/null || true && \
    cp -r ${EDITOR_HOME}/web-apps/apps/*/main/index.html ${EDITOR_HOME}/branding/backup/ 2>/dev/null || true

# 复制自定义品牌资源
COPY branding/ ${EDITOR_HOME}/branding/

# 替换 header logo（桌面端编辑器顶部 logo）
RUN cp ${EDITOR_HOME}/branding/header-logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header/header-logo_s.svg && \
    cp ${EDITOR_HOME}/branding/dark-logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header/dark-logo_s.svg && \
    cp ${EDITOR_HOME}/branding/logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about/logo_s.svg && \
    cp ${EDITOR_HOME}/branding/logo-white_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about/logo-white_s.svg && \
    cp ${EDITOR_HOME}/branding/embed-logo.svg ${EDITOR_HOME}/web-apps/apps/common/embed/resources/img/logo.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header && gzip -kf header-logo_s.svg && gzip -kf dark-logo_s.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about && gzip -kf logo_s.svg && gzip -kf logo-white_s.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/embed/resources/img && gzip -kf logo.svg 2>/dev/null || true

# 复制定制脚本
# 注意: remove-license-limit.sh 中的 JS 修改对 PKG 打包的二进制无效
# 并发限制已通过二进制补丁解除（见上方 COPY docservice_patched）
COPY scripts/remove-license-limit.sh /tmp/remove-license-limit.sh
COPY scripts/replace-branding.sh /tmp/replace-branding.sh
COPY scripts/replace-onlyoffice.sh /tmp/replace-onlyoffice.sh
COPY scripts/remove-ui-elements.sh /tmp/remove-ui-elements.sh

# 构建时执行定制（更可靠）
RUN chmod +x /tmp/remove-license-limit.sh /tmp/replace-branding.sh /tmp/replace-onlyoffice.sh /tmp/remove-ui-elements.sh && \
    /tmp/replace-branding.sh && \
    /tmp/replace-onlyoffice.sh && \
    /tmp/remove-ui-elements.sh && \
    rm -f /tmp/remove-license-limit.sh /tmp/replace-branding.sh /tmp/replace-onlyoffice.sh /tmp/remove-ui-elements.sh

# 保留到 entrypoint 目录（备用）
COPY scripts/replace-branding.sh /docker-entrypoint.d/replace-branding.sh
RUN chmod +x /docker-entrypoint.d/replace-branding.sh

# 安装 envsubst 工具（用于配置模板变量替换）
RUN apt-get update && apt-get install -y --no-install-recommends gettext-base && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 复制配置模板和入口脚本
COPY config/local.json.template ${EDITOR_CONFIG}/local.json.template
COPY config/local.json ${EDITOR_CONFIG}/local.json
COPY scripts/entrypoint.sh /docker-entrypoint.d/99-entrypoint.sh
RUN chmod +x /docker-entrypoint.d/99-entrypoint.sh

# 复制自定义插件（签署按钮等）
COPY plugins/ ${EDITOR_HOME}/sdkjs-plugins/

# 修复编辑器内部 nginx 的 Connection 头配置（socket.io 长轮询需要 keep-alive）
RUN sed -i 's/default close;/default "";/' ${EDITOR_CONFIG}/nginx/includes/http-common.conf 2>/dev/null || \
    sed -i 's/default close;/default "";/' /etc/nginx/includes/http-common.conf 2>/dev/null || true

# 增大内部 nginx 代理超时（防止 WebSocket/长连接因空闲被断开）
RUN sed -i 's/proxy_read_timeout[[:space:]]*300s/proxy_read_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/proxy_send_timeout[[:space:]]*300s/proxy_send_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/send_timeout[[:space:]]*300s/send_timeout          900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null || true

# 添加访问日志以便调试 socket.io 请求
RUN echo 'access_log /var/log/onlyoffice/documentserver/nginx.access.log;' >> /etc/nginx/includes/http-common.conf

# 替换 Service Worker 为自注销版本（防止 SW 拦截 socket.io 请求导致文档加载失败）
COPY service-worker-override.js ${EDITOR_HOME}/documentserver/sdkjs/common/serviceworker/document_editor_service_worker.js

# 替换品牌标识（仅 HTML/JSON，不替换 JS 以免破坏代码语法）
RUN find ${EDITOR_HOME} -name "*.html" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' \
    -e 's/onlyoffice/miaotongdoc/g' {} \; 2>/dev/null || true && \
    find ${EDITOR_HOME} -name "*.json" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' {} \; 2>/dev/null || true

# 清理
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost/healthcheck || exit 1

EXPOSE 80

CMD ["/app/ds/run-document-server.sh"]
