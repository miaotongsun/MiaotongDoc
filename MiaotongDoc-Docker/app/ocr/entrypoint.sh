#!/bin/sh
# Tesseract 启动脚本 - 根据 OCR_LANGUAGES 环境变量动态安装额外语言包
# 默认内置中文简体+英文,启动时可挂载更多(支持 jpn/kor/chi_tra/fra/deu 等)
#
# 用法:docker run -e OCR_LANGUAGES="chi_sim+eng+jpn+kor+chi_tra" ...
# 不传或空值:使用内置的中文+英文(无需联网,纯内网环境安全)

set -e

INNER_LANGS="chi_sim eng"   # 已通过 Dockerfile 内置

if [ -n "$OCR_LANGUAGES" ]; then
    # 解析 OCR_LANGUAGES(格式:chi_sim+eng+jpn+kor),只安装不在内置里的
    REQUESTED=$(echo "$OCR_LANGUAGES" | tr '+' ' ')
    for lang in $REQUESTED; do
        case " $INNER_LANGS " in
            *" $lang "*)
                # 已内置,跳过
                ;;
            *)
                PACKAGE="tesseract-ocr-$lang"
                echo "[tesseract-entrypoint] 动态安装语言包: $PACKAGE"
                # 内网场景可能没 apt 源,安装失败时优雅降级到内置语言
                if apt-get install -y --no-install-recommends "$PACKAGE" > /dev/null 2>&1; then
                    INNER_LANGS="$INNER_LANGS $lang"
                    echo "[tesseract-entrypoint] ✓ $PACKAGE 安装成功"
                else
                    echo "[tesseract-entrypoint] ⚠ $PACKAGE 安装失败(离线环境或语言代码不存在),跳过"
                fi
                ;;
        esac
    done
    rm -rf /var/lib/apt/lists/*
fi

echo "[tesseract-entrypoint] 已加载语言: $INNER_LANGS"
echo "[tesseract-entrypoint] tessdata 文件清单:"
ls /usr/share/tesseract-ocr/*/tessdata/*.traineddata 2>/dev/null | xargs -n1 basename || true

# 启动应用
exec python app.py