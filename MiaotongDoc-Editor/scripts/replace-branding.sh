#!/bin/bash
# MiaotongDoc Editor - 品牌替换脚本
# 替换编辑器服务的品牌标识

echo "开始替换品牌标识..."

# 编辑器安装目录
EDITOR_HOME="/var/www/onlyoffice"
EDITOR_CONFIG="/etc/onlyoffice/documentserver"
EDITOR_WEBAPPS="${EDITOR_HOME}/web-apps"

# 替换 logo 文件
if [ -d "${EDITOR_HOME}/branding/logos" ]; then
    # 替换主 logo
    find ${EDITOR_WEBAPPS} -name "logo*" -exec cp ${EDITOR_HOME}/branding/logos/logo.png {} \; 2>/dev/null || true

    # 替换 favicon
    find ${EDITOR_WEBAPPS} -name "favicon*" -exec cp ${EDITOR_HOME}/branding/logos/favicon.ico {} \; 2>/dev/null || true
fi

# 替换 HTML 中的品牌文本
find ${EDITOR_HOME} -name "*.html" -type f -exec sed -i \
    -e 's/OnlyOffice Document Editor/MiaotongDoc Editor/g' \
    -e 's/OnlyOffice Document Server/MiaotongDoc Editor/g' \
    -e 's/ONLYOFFICE/MiaotongDoc/g' \
    -e 's/OnlyOffice/MiaotongDoc/g' \
    -e 's/onlyoffice\.com/miaotongdoc.com/g' \
    {} \; 2>/dev/null || true

# 替换 JavaScript 中的品牌文本
find ${EDITOR_WEBAPPS} -name "*.js" -type f -exec sed -i \
    -e "s/OnlyOffice Document Editor/MiaotongDoc Editor/g" \
    -e "s/ONLYOFFICE/MiaotongDoc/g" \
    -e "s/OnlyOffice/MiaotongDoc/g" \
    {} \; 2>/dev/null || true

# 替换 CSS 中的品牌引用
find ${EDITOR_WEBAPPS} -name "*.css" -type f -exec sed -i \
    -e 's/onlyoffice/miaotongdoc/g' \
    {} \; 2>/dev/null || true

# 替换标题
find ${EDITOR_HOME} -name "*.html" -type f -exec sed -i \
    -e 's/<title>.*<\/title>/<title>MiaotongDoc Editor<\/title>/g' \
    {} \; 2>/dev/null || true

# 替换编辑器页面标题
for editor in documenteditor spreadsheeteditor presentationeditor; do
    INDEX_HTML="${EDITOR_WEBAPPS}/apps/${editor}/main/index.html"
    if [ -f "$INDEX_HTML" ]; then
        sed -i "s/ONLYOFFICE ${editor^}/MiaotongDoc ${editor^}/g" "$INDEX_HTML" 2>/dev/null || true
    fi
done

# 禁用更新检查和遥测
if [ -f "${EDITOR_CONFIG}/local.json" ]; then
    echo "配置文件已存在，跳过"
else
    cat > "${EDITOR_CONFIG}/local.json" << 'EOF'
{
    "services": {
        "CoAuthoring": {
            "autoAssembly": {
                "enable": true
            }
        }
    },
    "feedback": {
        "url": ""
    },
    "analytics": {
        "enabled": false
    }
}
EOF
fi

echo "品牌替换完成"
