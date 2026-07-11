#!/bin/bash
# 移除不需要的编辑器 UI 元素
# 1. 支持按钮 (#left-btn-support)
# 2. 关于按钮 (#left-btn-about)
# 3. 头部 logo (#header-logo)
# 4. 帮助按钮 (#fm-btn-help)
# 5. 提出功能建议 (#fm-btn-suggest)

echo "移除不需要的 UI 元素..."

EDITOR_HOME="/var/www/onlyoffice/documentserver"
EDITOR_WEBAPPS="${EDITOR_HOME}/web-apps"

# === 1. 清理旧版 CSS 规则（防止升级后残留） ===
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    for subdir in main forms embed; do
        CSS_FILE="${EDITOR_WEBAPPS}/apps/${editor}/${subdir}/resources/css/app.css"
        if [ -f "$CSS_FILE" ]; then
            # 移除所有旧版 MiaotongDoc 规则
            sed -i '/MiaotongDoc: 隐藏/,/^}/d' "$CSS_FILE" 2>/dev/null
            sed -i '/MiaotongDoc: 修复/,/^}/d' "$CSS_FILE" 2>/dev/null
            # 移除残留的插件相关规则
            sed -i '/\.ribtab > a\[data-tab.*plugin/,/^}/d' "$CSS_FILE" 2>/dev/null
            sed -i '/\.ribtab > a {/,/^}/d' "$CSS_FILE" 2>/dev/null
            sed -i '/\.box-toolbar {/,/^}/d' "$CSS_FILE" 2>/dev/null
            sed -i '/\[class\*="plugin-manager"\]/d' "$CSS_FILE" 2>/dev/null
            sed -i '/\[class\*="pluginmanager"\]/d' "$CSS_FILE" 2>/dev/null
            sed -i '/li\.ribtab:has.*plugin/d' "$CSS_FILE" 2>/dev/null
            sed -i '/section\.panel\[data-tab.*plugin\]/d' "$CSS_FILE" 2>/dev/null
            # 清理末尾多余空行
            sed -i -e :a -e '/^\n*$/{$d;N;ba' -e '}' "$CSS_FILE" 2>/dev/null
            echo "  已清理旧规则: $CSS_FILE"
        fi
    done
done

# === 2. 添加 CSS 隐藏元素 ===
# 在所有编辑器的 app.css 中添加隐藏规则
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    for subdir in main forms embed; do
        CSS_FILE="${EDITOR_WEBAPPS}/apps/${editor}/${subdir}/resources/css/app.css"
        if [ -f "$CSS_FILE" ]; then
            # 检查是否已经添加了规则
            if ! grep -q 'MiaotongDoc: 隐藏' "$CSS_FILE"; then
                cat >> "$CSS_FILE" << 'EOF'

/* MiaotongDoc: 隐藏支持、关于按钮、头部 logo、帮助、提出功能建议 */
#left-btn-support,
#left-btn-about,
#header-logo,
#fm-btn-help,
#fm-btn-suggest {
    display: none !important;
    visibility: hidden !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    overflow: hidden !important;
}
/* MiaotongDoc: 隐藏插件页签 */
li.ribtab:has(> a[data-tab="plugins"]) { display: none !important; }
section.panel[data-tab="plugins"] { display: none !important; }
EOF
                echo "  已修改 CSS: $CSS_FILE"
            fi
        fi
    done
done

# === 2. 从 app.js 的 HTML 模板中移除按钮 ===
# 已禁用: 在压缩的 JS 文件中用 sed 删除 HTML 片段会破坏语法
# CSS 规则（上方步骤 2）已足够隐藏这些元素
echo "  [跳过] app.js HTML 模板修改（使用 CSS 隐藏代替）"

echo "UI 元素移除完成"
