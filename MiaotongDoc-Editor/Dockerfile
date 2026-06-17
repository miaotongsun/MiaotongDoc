# MiaotongDoc Editor - 企业级在线文档编辑器
# 自定义品牌、解除并发限制、集成签署功能、AI 插件

ARG BASE_IMAGE
FROM ${BASE_IMAGE}

LABEL maintainer="MiaotongDoc Team"
LABEL description="MiaotongDoc Editor - 企业级在线文档编辑器"
LABEL version="1.0"

ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production

# 编辑器安装目录
ENV EDITOR_HOME=/var/www/onlyoffice
ENV EDITOR_CONFIG=/etc/onlyoffice/documentserver

# 替换 docservice 二进制（解除并发限制：20 -> 127）
COPY docservice_patched /var/www/onlyoffice/documentserver/server/DocService/docservice
RUN chmod +x /var/www/onlyoffice/documentserver/server/DocService/docservice && \
    echo "docservice_patched loaded, size=$(wc -c < /var/www/onlyoffice/documentserver/server/DocService/docservice) bytes"

# 备份原始资源
RUN mkdir -p ${EDITOR_HOME}/branding/backup && \
    cp -r ${EDITOR_HOME}/web-apps/apps/*/main/resources/img/* ${EDITOR_HOME}/branding/backup/ 2>/dev/null || true && \
    cp -r ${EDITOR_HOME}/web-apps/apps/*/main/index.html ${EDITOR_HOME}/branding/backup/ 2>/dev/null || true

# 复制自定义品牌资源
COPY branding/ ${EDITOR_HOME}/branding/

# 替换 header logo
RUN cp ${EDITOR_HOME}/branding/header-logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header/header-logo_s.svg && \
    cp ${EDITOR_HOME}/branding/dark-logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header/dark-logo_s.svg && \
    cp ${EDITOR_HOME}/branding/logo_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about/logo_s.svg && \
    cp ${EDITOR_HOME}/branding/logo-white_s.svg ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about/logo-white_s.svg && \
    cp ${EDITOR_HOME}/branding/embed-logo.svg ${EDITOR_HOME}/web-apps/apps/common/embed/resources/img/logo.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/header && gzip -kf header-logo_s.svg && gzip -kf dark-logo_s.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/main/resources/img/about && gzip -kf logo_s.svg && gzip -kf logo-white_s.svg && \
    cd ${EDITOR_HOME}/web-apps/apps/common/embed/resources/img && gzip -kf logo.svg 2>/dev/null || true

# 安装工具（envsubst 用于配置模板变量替换，jq 用于 JSON 处理，python3 用于模板修复）
RUN apt-get update && apt-get install -y --no-install-recommends gettext-base jq python3 && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 复制定制脚本并执行
COPY scripts/remove-license-limit.sh /tmp/remove-license-limit.sh
COPY scripts/replace-branding.sh /tmp/replace-branding.sh
COPY scripts/replace-onlyoffice.sh /tmp/replace-onlyoffice.sh
COPY scripts/remove-ui-elements.sh /tmp/remove-ui-elements.sh
COPY scripts/fix-templates.py /tmp/fix-templates.py

RUN chmod +x /tmp/*.sh && \
    /tmp/replace-branding.sh && \
    /tmp/replace-onlyoffice.sh && \
    /tmp/remove-ui-elements.sh && \
    python3 /tmp/fix-templates.py && \
    rm -f /tmp/*.sh /tmp/*.py

# 保留到 entrypoint 目录（备用）
COPY scripts/replace-branding.sh /docker-entrypoint.d/replace-branding.sh
RUN chmod +x /docker-entrypoint.d/replace-branding.sh

# 安装工具和中文字体
RUN apt-get update && apt-get install -y --no-install-recommends \
    gettext-base \
    jq \
    python3 \
    fonts-noto-cjk \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && fc-cache -fv

# 复制配置模板和入口脚本
COPY config/local.json.template ${EDITOR_CONFIG}/local.json.template
COPY scripts/entrypoint.sh /app/ds/miaotongdoc-entrypoint.sh
RUN chmod +x /app/ds/miaotongdoc-entrypoint.sh

# 复制自定义插件（仅 AI + signing）
COPY plugins/signing/ ${EDITOR_HOME}/documentserver/sdkjs-plugins/signing/
COPY plugins/ai-plugin/ ${EDITOR_HOME}/documentserver/sdkjs-plugins/ai-plugin/

# 删除不需要的插件（保留 AI、signing、v1）
RUN PLUGIN_DIR="${EDITOR_HOME}/documentserver/sdkjs-plugins" && \
    cd "$PLUGIN_DIR" && \
    for dir in \
        {07FD8DFA-DFE0-4089-AL24-0730933CC80A} \
        {38E022EA-AD92-45FC-B22B-49DF39746DB4} \
        {440EBF13-9B19-4BD8-8621-05200E58140B} \
        {7327FC95-16DA-41D9-9AF2-0E7F449F6800} \
        {9DC93CDB-B576-4F0C-B55E-FCC9C48DD007} \
        {BE5CBF95-C0AD-4842-B157-AC40FEDD9441} \
        {BE5CBF95-C0AD-4842-B157-AC40FEDD9840} \
        {BE5CBF95-C0AD-4842-B157-AC40FEDD9841} \
        {BFC5D5C6-89DE-4168-9565-ABD8D1E48711} \
        {D71C2EF0-F15B-47C7-80E9-86D671F9C595} \
        {F30BDD79-23A0-4B05-8DE3-2AB77D03A1B4}; do \
        rm -rf "$dir" 2>/dev/null; \
    done && \
    rm -rf marketplace && \
    echo "Plugins cleaned: only AI + signing remain"

# 更新插件列表，仅保留 AI 插件
RUN PLUGIN_LIST="${EDITOR_HOME}/documentserver/sdkjs-plugins/plugin-list-default.json" && \
    echo '["ai-plugin"]' > "$PLUGIN_LIST" && \
    gzip -kf "$PLUGIN_LIST" && \
    echo "plugin-list-default.json updated to AI only"

# 修复编辑器内部 nginx 配置
RUN sed -i 's/default close;/default "";/' ${EDITOR_CONFIG}/nginx/includes/http-common.conf 2>/dev/null || \
    sed -i 's/default close;/default "";/' /etc/nginx/includes/http-common.conf 2>/dev/null || true

# 增大内部 nginx 代理超时（防止 WebSocket/长连接因空闲被断开）
RUN sed -i 's/proxy_read_timeout[[:space:]]*300s/proxy_read_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/proxy_send_timeout[[:space:]]*300s/proxy_send_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/send_timeout[[:space:]]*300s/send_timeout          900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null || true

# 添加访问日志以便调试
RUN echo 'access_log /var/log/onlyoffice/documentserver/nginx.access.log;' >> /etc/nginx/includes/http-common.conf

# 替换 Service Worker 为自注销版本（防止 SW 拦截 socket.io 请求）
COPY service-worker-override.js ${EDITOR_HOME}/documentserver/sdkjs/common/serviceworker/document_editor_service_worker.js

# 替换品牌标识（HTML/JSON）
RUN find ${EDITOR_HOME} -name "*.html" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' \
    -e 's/onlyoffice/miaotongdoc/g' {} \; 2>/dev/null || true && \
    find ${EDITOR_HOME} -name "*.json" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' {} \; 2>/dev/null || true

# 替换 About 对话框中的品牌前缀
RUN find ${EDITOR_HOME}/documentserver/web-apps -name "app.js" -exec sed -i \
    -e 's|txtEdition:""|txtEdition:"MiaotongDoc "|g' {} \; 2>/dev/null || true

# 清理
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost/healthcheck || exit 1

EXPOSE 80

ENTRYPOINT ["/app/ds/miaotongdoc-entrypoint.sh"]
CMD ["/app/ds/run-document-server.sh"]
