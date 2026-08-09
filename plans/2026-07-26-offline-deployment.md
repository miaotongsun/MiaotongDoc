---
name: "纯内网部署方案"
status: "planning"
priority: "high"
---

# 纯内网部署方案

> **目标**: 所有服务在**完全断网**环境下可正常启动、运行、识别、推理
> **原则**: 构建期可在外网完成（一次构建，到处部署）；运行期零外网请求
> **范围**: 全栈 16 个 Docker 容器 + 前端 + 后端 + AI 推理

---

## 📊 状态摘要

- **状态**: 规划中（方案设计完成，待实施）
- **优先级**: P0 — 纯内网部署硬要求
- **前提条件**: ✅ 内网已有私有化部署的大模型推理服务（LLM 无需额外部署）
- **关键结论**: 当前设计 **不能直接部署到纯内网**，需完成以下改造后才能部署

---

## 📊 互联网依赖全景图

### 运行期依赖（必须零外网请求）

| 服务 | 容器 | 外网请求 | 频率 | 影响 | 方案 |
|------|------|----------|------|------|------|
| Docling | `miaotongdoc-docling:5001` | `huggingface_hub` → `hf-mirror.com` / `xethub.hf.co` | **每次推理启动** | ❌ 挂起、报错 | 离线环境变量 + 模型烧入镜像 |
| PaddleOCR | `miaotongdoc-ocr-paddle:5003` | `aicloud.baidu.com` / `modelscope` 下载模型 | 首次启动 | ❌ 预热失败 | 预下载 + 模型缓存烧入镜像 |
| LLM/AI | web-server 9004 | `LLM_BASE_URL` 指向外网 API | 每次 AI 调用 | ❌ 功能不可用 | ✅ **已有内网 LLM 服务**，配置 `.env` 指向即可 |
| 前端 | 浏览器 | 无（所有资源自建） | — | ✅ 无依赖 | 不涉及 |
| 后端 | web-server | 无（自建 API） | — | ✅ 无依赖 | 不涉及 |

### 构建期依赖（一次构建，可离线完成）

| 依赖类型 | 来源 | 内网方案 |
|----------|------|----------|
| Docker 基础镜像 | `python:3.11` `python:3.10-slim` | 拉取 → 推送到内网 Harbor / Nexus docker registry |
| pip 包 | `pypi.tuna.tsinghua.edu.cn` | 内网 Nexus PyPI proxy / 离线 whl 包 |
| apt 包 | `deb.debian.org` | 内网 Nexus APT proxy / 离线 .deb |
| npm 包 | `registry.npmjs.org` | 内网 Nexus npm proxy / 离线 tgz |
| Maven 包 | `maven.aliyun.com` | 内网 Nexus maven proxy / 离线 .jar |
| HuggingFace 模型 | `huggingface.co` 17 个文件 | 外网拉取 → 内网共享存储 / 烧入镜像 |

---

## 一、LLM / AI 大模型服务（纯内网方案）

### 当前架构

```
AI 调用请求 → AiProxyService.getTargetUrl() → 外网 LLM API
```

### 纯内网配置

**前提**：内网已有私有化部署的大模型推理服务（如 Ollama / vLLM / xinference 等），提供 OpenAI 兼容接口。

**配置方式**（修改 `.env`）：

```bash
# LLM 通用问答
LLM_BASE_URL=http://192.168.1.100:11434/v1    # 内网 LLM 地址
LLM_API_KEY=not-needed                         # 内网服务通常不需要密钥
LLM_DEFAULT_MODEL=qwen2.5:14b                  # 根据内网部署的模型填写

# 视觉 AI（VISION 类型，用于 PDF 图片分析）
VISION_BASE_URL=http://192.168.1.100:11434/v1
VISION_API_KEY=not-needed
VISION_DEFAULT_MODEL=qwen2.5-vl:7b
```

### 配置变更（已实现，无需改代码）

| 配置项 | 文件 | 说明 |
|--------|------|------|
| `LLM_BASE_URL` | `.env` | 改指向内网地址即可 |
| `VISION_BASE_URL` | `.env` | 改指向内网地址即可 |
| `AiProxyService` | 代码 | 已通过 `getTargetUrl()` 读取配置，无需改代码 |

---

## 二、Docling 内网改造（最关键）

### 当前问题

```
docling-serve 启动 → warm_up_caches() → huggingface_hub → 
  → hf-mirror.com → xethub.hf.co CAS 401 ❌
```

**即使模型已下载到本地缓存**，docling 1.26.0 的 huggingface_hub 仍会**在推理时尝试 CAS 重建**，失败后有时 fallback 成功，有时直接报错。

### 改造方案

**核心：设置离线环境变量 + 模型烧入镜像层**

#### 修改 docker-compose.yml

```yaml
docling:
  environment:
    - HF_HUB_OFFLINE=1           # ⭐ 关键：禁止 huggingface_hub 发任何外网请求
    - TRANSFORMERS_OFFLINE=1     # ⭐ transformers 框架离线
    - HF_DATASETS_OFFLINE=1
    - HF_HOME=/root/.cache/huggingface  # 指定缓存路径
```

#### 修改 Dockerfile（模型烧入镜像）

```dockerfile
# 构建期预下载模型（外网环境执行一次）
RUN mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/blobs && \
    mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-layout-heron/snapshots/8f39ad3c0b4c58e9c2d2c84a38465abf757272d8 && \
    mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-models/blobs && \
    mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-models/snapshots/fc0f2d45e2218ea24bce5045f58a389aed16dc23/model_artifacts/tableformer/accurate && \
    mkdir -p /root/.cache/huggingface/hub/models--docling-project--docling-models/snapshots/fc0f2d45e2218ea24bce5045f58a389aed16dc23/model_artifacts/tableformer/fast

# 复制预下载的模型文件（来自外网构建环境）
COPY models/ /root/.cache/huggingface/hub/
```

#### 模型文件清单

| 文件 | 大小 | 存放路径 | 用途 |
|------|------|----------|------|
| `model.safetensors` | 171 MB | `models--docling-project--docling-layout-heron/blobs/00333a4...` | 版面分析 |
| `preprocessor_config.json` | 444 B | 同上 snapshot symlink | 图像预处理 |
| `config.json` | 3.3 KB | 同上 | 模型配置 |
| `tableformer_accurate.safetensors` | 203 MB | `models--docling-project--docling-models/blobs/2a7d6c9...` | 表格高精度 |
| `tableformer_fast.safetensors` | 139 MB | 同上 `blobs/3119563...` | 表格快速 |
| blobs 小文件 6 个 | 各 1-7 KB | 同上 | 配置 |
| 全部 symlink | — | 同上 snapshot 目录 | HF 缓存结构 |

**合计约 520 MB**（烧入镜像层，容器内零联网）

---

## 三、PaddleOCR 内网改造

### 当前问题

```
PaddleOCR 构造时 → PaddleOCR() 初始化 → 下载 PP-OCRv5 模型
  → aistudio.baidu.com / modelscope / hf-mirror
```

首次启动约 30-60s 下载模型，如果断网则构造失败。

### 改造方案

**方案 A：模型缓存烧入镜像（推荐）**

```dockerfile
# Dockerfile.ocr-paddle 中已预下载模型（构建期执行）
RUN python -c "from paddleocr import PaddleOCR; PaddleOCR(lang='ch', use_textline_orientation=True)" || true
```

实际上 paddleocr 的 `PaddleOCR()` 构造时下载的模型默认会存到 `~/.paddleocr/` 目录。这个目录本身就在镜像层内。

**但问题**：`paddleocr==3.2.0` 内部多次尝试从 `aiserver` / `modelscope` / `huggingface` 下载，**失败后不会报错**（静默 fallback），但可能加载默认模型性能下降。

**方案 B：离线环境变量**

```yaml
ocr-paddle:
  environment:
    - PADDLE_OCR_OFFLINE=1       # 自定义环境变量，app.py 识别后跳过下载
    - PADDLE_MODEL_DIR=/app/models  # 指定本地模型路径
```

**方案 C：修改 app.py 代码**（最可靠）

当前 `app.py:34` 的 `get_ocr_engine()` 创建 PaddleOCR 时传入模型名称。如果 `PADDLE_MODEL_DIR` 指向本地路径，PaddleOCR 不会尝试下载。

```python
# 内网模式下，将模型目录挂在到容器内
PaddleOCR(
    lang=lang,
    det_model_dir="/app/models/det",
    rec_model_dir="/app/models/rec",
    cls_model_dir="/app/models/cls",
    use_gpu=False,
)
```

**优先推荐方案 A + C 组合**：模型烧入镜像 + 代码指定本地路径。

#### 模型文件清单

| 模型 | 文件 | 大小 | 来源 |
|------|------|------|------|
| PP-OCRv5 检测 | `ch_PP-OCRv5_det_mobile.nb` / `server.nb` | 约 30MB | 外网一次下载 |
| PP-OCRv5 识别 | `ch_PP-OCRv5_rec_mobile.nb` / `server.nb` | 约 50MB | 同上 |
| 文本方向分类 | `ch_ptocr_mobile_v2.0_cls.nb` | 约 1MB | 同上 |
| PP-LCNet 方向 | `PP-LCNet_x0_25_textline_ori` | 约 10MB | 同上 |

---

## 四、Docker 镜像内网分发

### 当前流程

```
docker build → 拉基础镜像(python:3.11) → pip 安装 → 打包
          ↓
      docker compose up -d
```

### 内网流程

```
外网环境（一次构建，所有镜像打包）:
  1. docker build （在外网机器上构建所有镜像）
  2. docker save -o miaotongdoc-all.tar miaotongdoc-docker-xxx:latest ...
  3. 拷贝到内网

内网环境（部署）:
  1. docker load -i miaotongdoc-all.tar     # 加载所有镜像
  2. docker compose up -d                   # 零外网启动
```

### 镜像清单（需在外网构建后打包传输）

| 镜像名 | 构建目录 | 大小 | 优先级 |
|--------|----------|------|--------|
| `miaotongdoc-docker-server` | `app/server/` | ~200MB | ✅ 必须 |
| `miaotongdoc-docker-web` | `app/web/` | ~50MB（静态文件） | ✅ 必须 |
| `miaotongdoc-docker-editor` / editor2 / editor3 | `app/editor/` | 各 ~2GB | ✅ 必须 |
| `miaotongdoc-docker-ocr-paddle` | `app/ocr-paddle/` | ~3GB | ✅ 必须（中文 OCR） |
| `miaotongdoc-docker-ocr` | `app/ocr/` | ~1GB | ⚠️ 可选（Tesseract 兜底） |
| `miaotongdoc-docker-docling` | `app/docling/` | ~12GB | ⚠️ 可选（AI 文档解析） |
| 第三方基础镜像 | `postgres`, `redis`, `rabbitmq`, `elasticsearch`, `minio`, `nginx:alpine` | 各 100MB-1GB | ✅ 必须 |

**合计约 12-15GB**（一次 tar 打包，USB 或内网传输）

### 内网不需要 harbor

首次部署只需 `docker save` → `docker load` 即可，日常迭代也用同方式。仅当多台机器频繁部署时才需要搭建 harbor。**内网无 harbor 也不影响部署**。

---

## 五、当前已做的改造进度

### ✅ 已完成

| 改造 | 文件 | 说明 |
|------|------|------|
| `HF_ENDPOINT=https://hf-mirror.com` | `docker-compose.yml` | 已改为国内镜像（但内网仍需完全离线） |
| `docling-serve==1.26.0` | `app/docling/Dockerfile` | 版本 pin |
| pip 清华镜像 | 所有 Dockerfile | 已用 `tuna.tsinghua.edu.cn` |
| PaddleOCR 模型预下载 | `app/ocr-paddle/Dockerfile` | 构建期下载 |
| `PADDLE_OCR_ENABLED=true` | `.env` | 已启用 |
| 所有外网 AI URL 可配置 | 代码 | 通过 `.env` 配置，不改代码 |
| `ocr-paddle` 默认启动 | `docker-compose.yml` | 无 profile |
| docling 跳过 warm_up | `docker-compose.yml` | 让启动更快 |

### ❌ 待改造（纯内网必须）

| 改造 | 优先级 | 工作量 | 说明 |
|------|--------|--------|------|
| **`HF_HUB_OFFLINE=1`** | 🔴 高 | 1 行 | docling 禁止外网请求 |
| **`TRANSFORMERS_OFFLINE=1`** | 🔴 高 | 1 行 | transformers 禁止外网 |
| **模型烧入 docling 镜像** | 🔴 高 | 4 小时 | 构建期 COPY 模型文件 |
| **模型烧入 PaddleOCR 镜像** | 🔴 高 | 2 小时 | 构建期 COPY 模型文件 |
| **内网 harbor 推送脚本** | 🟡 中 | 1 小时 | `build.sh` |
| **内网 pip 私服配置** | 🟡 中 | 30 分钟 | `--index-url` 参数 |
| **内网 nexus 搭建文档** | 🟡 中 | 2 小时 | 第一次搭建 |
| **Ollama 内网部署文档** | 🟡 中 | 1 小时 | 离线安装包 + 模型 |
| **离线构建脚本** | 🟡 中 | 2 小时 | 全自动一次构建 |
| **验证清单** | 🟢 低 | 30 分钟 | 接口测试 |

---

## 六、离线构建工作流

### 外网环境（一次构建，一劳永逸）

```bash
# 1. 拉取所有基础镜像
docker pull python:3.11
docker pull python:3.10-slim-bookworm
docker pull postgres:12
docker pull redis:7
docker pull rabbitmq:3
docker pull elasticsearch:8.11
docker pull minio/minio
docker pull nginx:alpine
docker pull node:18-alpine

# 2. 标记并推送到内网 harbor
docker tag python:3.11 harbor.internal/library/python:3.11
docker push harbor.internal/library/python:3.11
# ... 重复所有基础镜像

# 3. 构建项目镜像
cd MiaotongDoc-Docker
./build.sh offline   # 离线构建脚本（指定内网源）

# 4. 推送项目镜像
docker tag miaotongdoc-docker-server harbor.internal/miaotongdoc/server:latest
docker push harbor.internal/miaotongdoc/server:latest
# ... 重复所有项目镜像

# 5. 打包模型文件
tar -czf offline-models.tar.gz \
  models/docling-layout-heron/ \
  models/docling-models/ \
  models/paddleocr-models/
# 拷贝到内网环境
```

### 内网环境（部署）

```bash
# 1. 从 harbor 拉取所有镜像
docker pull harbor.internal/miaotongdoc/server:latest
# ... 重复

# 2. 修改 .env 指向内网地址
vim .env
# LLM_BASE_URL=http://192.168.1.100:11434/v1
# DOCKER_REGISTRY=harbor.internal

# 3. 修改 docker-compose.yml 统一镜像前缀
# 将 image: postgres:12 改为 harbor.internal/library/postgres:12

# 4. 启动
./deploy.sh start
```

---

## 七、风险与边界

### 已知风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 模型文件合计 6GB 以上 | 中 | 传输、存储成本 | 分文件打包，按需部署 |
| Dokcer 镜像 12GB+ | 中 | 内网传输慢 | 分阶段推送，USB 离线传输 |
| 上游依赖 API 不兼容 | 低 | AI 功能异常 | 测试 OpenAI 兼容接口 |

### 不可用功能（无内网替代时）

| 功能 | 原因 | 替代 |
|------|------|------|
| 公式识别 | 需要 Mathpix API | 无替代（未使用） |

---

## 八、下一步行动

| 优先级 | 任务 | 预计工时 | 产出 |
|--------|------|----------|------|
| 🔴 P0 | 修改 `app/docling/Dockerfile` 烧入模型 + `HF_HUB_OFFLINE=1` | 4h | 离线版 docling 镜像 |
| 🔴 P0 | 修改 `app/ocr-paddle/Dockerfile` 烧入模型 + 本地模型路径 | 2h | 离线版 PaddleOCR 镜像 |
| 🔴 P0 | 外网一次构建所有镜像 + `docker save` 打包 | 2h | 离线 tar 包 |
| 🟡 P1 | 更新 `.env.example` 内网配置 | 30min | 环境变量模板 |
| 🟢 P2 | 写验证清单 | 1h | 测试用例 |

---

*计划版本: v1.0 | 最后更新: 2026-07-26 | 计划人: Claude*