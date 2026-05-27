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

# === 1. 添加 CSS 隐藏元素 ===
# 在所有编辑器的 app.css 中添加隐藏规则
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    for subdir in main forms embed; do
        CSS_FILE="${EDITOR_WEBAPPS}/apps/${editor}/${subdir}/resources/css/app.css"
        if [ -f "$CSS_FILE" ]; then
            # 检查是否已经添加了规则
            if ! grep -q 'MiaotongDoc: 隐藏' "$CSS_FILE"; then
                cat >> "$CSS_FILE" << 'EOF'

/* MiaotongDoc: 隐藏支持、关于按钮、头部 logo、帮助和提出功能建议 */
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
EOF
                echo "  已修改 CSS: $CSS_FILE"
            fi
        fi
    done
done

# === 2. 从 app.js 的 HTML 模板中移除按钮 ===
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    APP_JS="${EDITOR_WEBAPPS}/apps/${editor}/main/app.js"
    if [ -f "$APP_JS" ]; then
        # 移除支持按钮的 HTML 模板
        sed -i 's/<button id="left-btn-support"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$APP_JS" 2>/dev/null
        # 移除关于按钮的 HTML 模板
        sed -i 's/<button id="left-btn-about"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$APP_JS" 2>/dev/null
        # 移除 header-logo 的 HTML 模板
        sed -i 's/<div id="header-logo"[^>]*><i><\/i><\/div>//g' "$APP_JS" 2>/dev/null
        # 移除帮助按钮 HTML
        sed -i 's/<li id="fm-btn-help" class="fm-btn"><\/li>//g' "$APP_JS" 2>/dev/null
        # 移除建议按钮 HTML
        sed -i 's/<li id="fm-btn-suggest" class="fm-btn"><\/li>//g' "$APP_JS" 2>/dev/null
        echo "  已修改模板: $APP_JS"
    fi
done

# === 3. 处理 IE 兼容版本 ===
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    IE_JS="${EDITOR_WEBAPPS}/apps/${editor}/main/ie/app.js"
    if [ -f "$IE_JS" ]; then
        sed -i 's/<button id="left-btn-support"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$IE_JS" 2>/dev/null
        sed -i 's/<button id="left-btn-about"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$IE_JS" 2>/dev/null
        sed -i 's/<div id="header-logo"[^>]*><i><\/i><\/div>//g' "$IE_JS" 2>/dev/null
        sed -i 's/<li id="fm-btn-help" class="fm-btn"><\/li>//g' "$IE_JS" 2>/dev/null
        sed -i 's/<li id="fm-btn-suggest" class="fm-btn"><\/li>//g' "$IE_JS" 2>/dev/null
        echo "  已修改 IE 版本: $IE_JS"
    fi
done

# === 4. 处理 embed 版本 ===
for editor in documenteditor spreadsheeteditor presentationeditor pdfeditor; do
    EMBED_HTML="${EDITOR_WEBAPPS}/apps/${editor}/embed/index.html"
    if [ -f "$EMBED_HTML" ]; then
        sed -i 's/<button id="left-btn-support"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$EMBED_HTML" 2>/dev/null
        sed -i 's/<button id="left-btn-about"[^>]*>[^<]*<i[^>]*>[^<]*<\/i>[^<]*<\/button>//g' "$EMBED_HTML" 2>/dev/null
        sed -i 's/<li id="fm-btn-help" class="fm-btn"><\/li>//g' "$EMBED_HTML" 2>/dev/null
        sed -i 's/<li id="fm-btn-suggest" class="fm-btn"><\/li>//g' "$EMBED_HTML" 2>/dev/null
        echo "  已修改 embed: $EMBED_HTML"
    fi
done

echo "UI 元素移除完成"
