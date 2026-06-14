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
# 已禁用: replace-onlyoffice.sh 已负责 JS 品牌替换，此处重复执行会破坏压缩的 JS 文件
echo "[跳过] JS 品牌替换（由 replace-onlyoffice.sh 处理）"

# 替换 CSS 中的品牌引用（仅替换 URL 和文本，不替换类名）
# 注意：不能全局替换 onlyoffice，因为 CSS 类名中可能包含这个词
echo "[跳过] CSS 品牌替换（避免破坏编辑器布局）"

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
