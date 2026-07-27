FROM python:3.10-slim-bookworm

WORKDIR /app

# 系统依赖：libgl + libglib（OpenCV 必需）+ poppler（PDF 转图片）
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        libgomp1 \
        poppler-utils \
        curl \
        fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

# Python 依赖（清华 pip 镜像加速）
# paddlepaddle 3.0+ 才能在容器内正常运行（2.6.2 会触发 free(): invalid pointer）
# paddleocr 3.0+ 新 API（predict()），不再支持 ocr() 和 show_log 参数
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple \
        --trusted-host pypi.tuna.tsinghua.edu.cn \
        paddlepaddle==3.0.0 \
        paddleocr==3.2.0 \
        pdf2image==1.17.0 \
        Pillow==10.4.0 \
        flask==3.0.3 \
        flask-cors==4.0.1 \
        gunicorn==22.0.0 \
        beautifulsoup4==4.12.3

# 复制应用代码
COPY app.py /app/app.py

# ============================================================
# 纯内网离线改造：预下载模型缓存烧入镜像
# 模型文件约 232MB（含 PP-OCRv5_mobile/server 检测+识别）
# 运行时零外网请求，PaddleOCR 优先使用本地缓存
# ============================================================

# 复制预下载的 PaddleOCR 模型缓存（来自 paddlex-cache/.paddlex）
# 确保构建期下载完整，运行时零联网
COPY paddlex-cache/.paddlex /root/.paddlex

# 验证模型缓存完整性（构建期运行，失败则中断构建）
RUN python -c "from paddleocr import PaddleOCR; ocr = PaddleOCR(lang='ch', use_textline_orientation=True); print('PaddleOCR 模型加载成功')"

EXPOSE 5003

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:5003/health || exit 1

# 启动（gunicorn 生产模式，2 worker + 4 threads 适配 CPU 容器）
CMD ["gunicorn", "-w", "2", "-k", "gthread", "--threads", "4", "-b", "0.0.0.0:5003", "--timeout", "600", "app:app"]