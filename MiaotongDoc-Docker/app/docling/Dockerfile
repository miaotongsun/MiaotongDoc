FROM python:3.11

WORKDIR /app

# 安装 OpenCV + docling 依赖的系统库
RUN apt-get update && apt-get install -y --no-install-recommends \
    libxcb-xinerama0 \
    libxcb1 \
    libxcb-render0 \
    libxcb-shape0 \
    libxcb-xfixes0 \
    libxkbcommon0 \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libgomp1 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 使用清华 pip 镜像安装 docling-serve
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn docling-serve

# 暴露端口
EXPOSE 5001

# 启动服务
CMD ["docling-serve", "run", "--port", "5001"]
