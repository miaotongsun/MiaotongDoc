FROM python:3.11

WORKDIR /app

# 安装 docling 依赖的系统库(OpenCV、Poppler 等)
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
# Pin 到 1.26.0：1.27.0 在 Python 3.11 下 policy.py:44 的 _source_kinds 函数
# 对 Annotated[Union[...]] 的 UnionType 调 .model_fields 报错
# (UnionType 不是 Pydantic BaseModel),需等上游修复合并后升级
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn docling-serve==1.26.0

# ============================================================
# 纯内网离线改造：预下载模型文件 + HF 缓存结构
# 运行时通过 HF_HUB_OFFLINE=1 禁止任何外网请求
# 模型文件约 506MB，烧入镜像层
# 构建期在外网环境执行一次，内网环境零外网请求
# ============================================================

# 复制预下载的模型缓存文件（以 HF hub 标准缓存结构组织）
# COPY 不支持保留 symlink，所以只复制 blobs 目录
COPY models/layout-heron/blobs/ /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/blobs/
COPY models/docling-models/blobs/ /root/.cache/huggingface/hub/models--docling-project--docling-models/blobs/

# 在容器内创建 snapshot symlink（容器内文件系统支持真 symlink）
# layout-heron: 版面分析模型
RUN mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/snapshots/8f39ad3c0b4c58e9c2d2c84a38465abf757272d8 && \
    cd /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/snapshots/8f39ad3c0b4c58e9c2d2c84a38465abf757272d8 && \
    ln -sf ../../blobs/fcdff16b42e5ebc51d28f59184490c6eff91a88b preprocessor_config.json && \
    ln -sf ../../blobs/98434d37ace2399b824d685b0795ab7a68e53dd2 config.json && \
    ln -sf ../../blobs/00333a43451945aaf89db8ca9c0a17e75d1537c17db60fdb91aa95f4c7929e0c model.safetensors && \
    ln -sf ../../blobs/2c5b32c47fb9789ed49606988f58bc4741a731ab .gitattributes && \
    ln -sf ../../blobs/a6344aac8c09253b3b630fb776ae94478aa0275b README.md && \
    ln -sf ../../blobs/d7a3a823e847c4b091ef0094bf9840129f167a62 docling_heron_400.png && \
    echo "8f39ad3c0b4c58e9c2d2c84a38465abf757272d8" > /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/refs/main

# docling-models: 表格识别模型 (tableformer)
RUN mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-models/snapshots/fc0f2d45e2218ea24bce5045f58a389aed16dc23/model_artifacts/tableformer/accurate && \
    mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-models/snapshots/fc0f2d45e2218ea24bce5045f58a389aed16dc23/model_artifacts/tableformer/fast && \
    cd /root/.cache/huggingface/hub/models--docling-project--docling-models/snapshots/fc0f2d45e2218ea24bce5045f58a389aed16dc23 && \
    ln -sf ../../blobs/4dc84c9101238bb955158144660155da99438490 config.json && \
    ln -sf ../../blobs/575b058b45230d48a06df9b1944f4bd40949db1d .gitattributes && \
    ln -sf ../../blobs/9430eb3c89847aa8f80af5c96df3c348fca6da63 .gitignore && \
    ln -sf ../../blobs/598f7fa8e3023852ab74e8e851dc0d9b195cbc20 README.md && \
    cd model_artifacts/tableformer/accurate && \
    ln -sf ../../../../blobs/2a7d6c924b3cd12fb99a09280ca9c33a89c5d60b93253617d2e088c1a40374d9 tableformer_accurate.safetensors && \
    ln -sf ../../../../blobs/5036aa114b9393f607440cb0f6cc54229b5b30e1 tm_config.json && \
    cd ../../fast && \
    ln -sf ../../../../blobs/3119563aab5a7c96fda4d621119b63fd8806272b86c30936d15507616422f718 tableformer_fast.safetensors && \
    ln -sf ../../../../blobs/d02c5889d288b8baefd42e9e4da094f0cea07bbe tm_config.json && \
    echo "fc0f2d45e2218ea24bce5045f58a389aed16dc23" > /root/.cache/huggingface/hub/models--docling-project--docling-models/refs/main

# 验证缓存完整性
RUN echo "=== HF Hub Cache 结构 ===" && \
    find /root/.cache/huggingface/hub -type f -o -type l | head -30 && \
    echo "=== 文件大小 ===" && \
    du -sh /root/.cache/huggingface/hub

# 暴露端口
EXPOSE 5001

# 启动服务
CMD ["docling-serve", "run", "--port", "5001"]