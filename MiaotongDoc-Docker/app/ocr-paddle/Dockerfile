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
        paddleocr==3.0.0 \
        pdf2image==1.17.0 \
        Pillow==10.4.0 \
        flask==3.0.3 \
        flask-cors==4.0.1 \
        gunicorn==22.0.0 \
        beautifulsoup4==4.12.3

# 复制应用代码
COPY app.py /app/app.py

# 预下载 PaddleOCR 3.0 默认模型（构建期缓存，避免运行时首次慢）
# PP-OCRv5_mobile（检测+识别）约 30MB
RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(lang='ch', use_textline_orientation=True)" || true

EXPOSE 5003

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:5003/health || exit 1

# 启动（gunicorn 生产模式，2 worker + 4 threads 适配 CPU 容器）
CMD ["gunicorn", "-w", "2", "-k", "gthread", "--threads", "4", "-b", "0.0.0.0:5003", "--timeout", "600", "app:app"]