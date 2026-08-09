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

# 复制服务代码 + 入口脚本(支持 OCR_LANGUAGES 动态扩展语言包)
COPY app.py /app/app.py
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 默认暴露的语言包配置(运行时可通过 OCR_LANGUAGES 环境变量覆盖)
# 格式:tesseract 语言代码,用 + 连接(chi_sim=简体,chi_tra=繁体,jpn=日文,kor=韩文,fra=法文,deu=德文)
ENV OCR_LANGUAGES="chi_sim+eng"

# 暴露端口
EXPOSE 5002

# 通过 entrypoint.sh 启动(动态加载 OCR_LANGUAGES 指定的语言包,然后启动应用)
CMD ["/app/entrypoint.sh"]
