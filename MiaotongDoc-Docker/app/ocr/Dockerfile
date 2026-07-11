FROM python:3.11-slim

WORKDIR /app

# 安装 Tesseract OCR 和依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-chi-sim \
    tesseract-ocr-eng \
    libtesseract-dev \
    libleptonica-dev \
    pkg-config \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
RUN pip install --no-cache-dir \
    flask \
    pytesseract \
    pdf2image \
    Pillow

# 安装 poppler-utils (pdf2image 依赖)
RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

# 复制服务代码
COPY app.py /app/app.py

# 暴露端口
EXPOSE 5002

# 启动服务
CMD ["python", "app.py"]
