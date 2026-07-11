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
# 如果此步报错 "not found"，说明 docservice_patched 文件缺失，需从 MiaotongDoc-Editor/ 复制
COPY docservice_patched /var/www/onlyoffice/documentserver/server/DocService/docservice
RUN chmod +x /var/www/onlyoffice/documentserver/server/DocService/docservice && \
    echo "docservice_patched loaded, size=$(wc -c < /var/www/onlyoffice/documentserver/server/DocService/docservice) bytes"

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

# 安装工具和基础中文字体
RUN apt-get update && apt-get install -y --no-install-recommends \
    gettext-base \
    jq \
    fonts-noto-cjk \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 复制自定义中文字体（仿宋、楷体、黑体、宋体、微软雅黑等）
COPY fonts/ /usr/share/fonts/truetype/custom/
RUN fc-cache -fv

# 复制配置模板和入口脚本（local.json 由 entrypoint.sh 从模板生成，不复制硬编码版本）
COPY scripts/entrypoint.sh /app/ds/miaotongdoc-entrypoint.sh
RUN chmod +x /app/ds/miaotongdoc-entrypoint.sh

# 复制自定义插件（签署按钮等）
COPY plugins/signing/ ${EDITOR_HOME}/documentserver/sdkjs-plugins/signing/
COPY plugins/ai-plugin/ ${EDITOR_HOME}/documentserver/sdkjs-plugins/ai-plugin/

# 修复编辑器内部 nginx 的 Connection 头配置（socket.io 长轮询需要 keep-alive）
RUN sed -i 's/default close;/default "";/' ${EDITOR_CONFIG}/nginx/includes/http-common.conf 2>/dev/null || \
    sed -i 's/default close;/default "";/' /etc/nginx/includes/http-common.conf 2>/dev/null || true

# 增大内部 nginx 代理超时（防止 WebSocket/长连接因空闲被断开）
RUN sed -i 's/proxy_read_timeout[[:space:]]*300s/proxy_read_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/proxy_send_timeout[[:space:]]*300s/proxy_send_timeout 900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null && \
    sed -i 's/send_timeout[[:space:]]*300s/send_timeout          900s/g' ${EDITOR_CONFIG}/nginx/includes/ds-docservice.conf 2>/dev/null || true

# 添加访问日志以便调试 socket.io 请求
RUN echo 'access_log /var/log/onlyoffice/documentserver/nginx.access.log;' >> /etc/nginx/includes/http-common.conf

# 添加 AI Proxy 反向代理 - 将 ai-proxy 请求转发到后端 Spring Boot
COPY config/nginx/ds-aiproxy.conf /etc/nginx/includes/ds-aiproxy.conf
RUN sed -i '/^}/i\    include /etc/nginx/includes/ds-aiproxy.conf;' /etc/onlyoffice/documentserver/nginx/ds.conf || \
    echo 'include /etc/nginx/includes/ds-aiproxy.conf;' >> /etc/onlyoffice/documentserver/nginx/ds.conf

# 替换 Service Worker 为自注销版本（防止 SW 拦截 socket.io 请求导致文档加载失败）
COPY service-worker-override.js ${EDITOR_HOME}/documentserver/sdkjs/common/serviceworker/document_editor_service_worker.js

# 替换品牌标识（HTML/JSON）
RUN find ${EDITOR_HOME} -name "*.html" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' \
    -e 's/onlyoffice/miaotongdoc/g' {} \; 2>/dev/null || true && \
    find ${EDITOR_HOME} -name "*.json" -exec sed -i \
    -e 's/OnlyOffice/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' {} \; 2>/dev/null || true

# 替换 About 对话框中的品牌前缀（不改版本号，避免触发 OnlyOffice 版本校验导致无限重载）
RUN find ${EDITOR_HOME}/documentserver/web-apps -name "app.js" -exec sed -i \
    -e 's|txtEdition:""|txtEdition:"MiaotongDoc "|g' {} \; 2>/dev/null || true

# 修改 OnlyOffice 新建文档模板的 app.xml 元数据
# 将 Application 设为 MiaotongDoc，AppVersion 设为 1.0
COPY scripts/fix-templates.py /tmp/fix-templates.py
RUN apt-get update && apt-get install -y --no-install-recommends python3 && \
    python3 /tmp/fix-templates.py && \
    rm -f /tmp/fix-templates.py && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 删除不需要的 GUID 插件目录和插件管理器（保留 AI、signing、v1）
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
    echo '["ai"]' > "$PLUGIN_LIST" && \
    gzip -kf "$PLUGIN_LIST" && \
    echo "plugin-list-default.json updated to AI only"

# 清理
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost/healthcheck || exit 1

EXPOSE 80

ENTRYPOINT ["/app/ds/miaotongdoc-entrypoint.sh"]
CMD ["/app/ds/run-document-server.sh"]
