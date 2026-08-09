# Docling 服务镜像

PDF → 结构化 Markdown（保留表格/标题层级）。供 PDF 编辑器结构化导出、智能目录 precise 模式、合同 LLM 抽取使用。

- 容器：`miaotongdoc-docling` @ `172.20.0.90:5001`
- 启动：**可选 profile**，`./deploy.sh start --with-docling` 或 `docker compose --profile docling up -d docling`
- 上游：`docling-serve==1.26.0`（pin 版本，原因见 Dockerfile 注释）

---

## ⚠️ 模型文件不在 Git 里

`models/` 目录约 **506MB**，已被 `.gitignore` 排除（`MiaotongDoc-Docker/.gitignore:12`）。

**后果：干净 clone 的仓库无法构建本镜像**，`COPY models/...` 会失败。首次构建或换机器时必须先按下文重建 `models/`。

内网离线部署不受影响 —— 按 [plans/offline-env-requirements.md](../../../plans/offline-env-requirements.md) 方案，内网只 `docker load` 不构建。

---

## 离线设计：为什么要手工拼 HF 缓存

Docling 运行时会向 HuggingFace 拉模型。内网无外网，所以：

1. **构建期**（外网）把模型文件 `COPY` 进镜像
2. 在镜像内**手工重建 HF hub 标准缓存结构**，让 huggingface_hub 以为模型已缓存
3. **运行期** `HF_HUB_OFFLINE=1` 禁止任何外网请求

HF 缓存结构三件套，缺一不可：

```
/root/.cache/huggingface/hub/models--docling-project--<name>/
├── blobs/          真实文件，以内容 hash 命名
├── snapshots/<commit>/   symlink → ../../blobs/<hash>，用真实文件名
└── refs/main       内容为 <commit>，指明哪个 snapshot 是当前版本
```

> **为什么 blobs 用 COPY、symlink 用 RUN 分两步？**
> Docker `COPY` 不保留 symlink（Windows 宿主更是完全不支持）。所以只 COPY `blobs/`，再在容器内用 `RUN ln -sf` 建真 symlink。改动这段时务必保持这个拆分。

---

## 两个模型

| 模型 | 用途 | 大小 | commit |
|---|---|---|---|
| `docling-project/docling-layout-heron` | 版面分析（识别标题/段落/表格区域） | 164 MB | `8f39ad3c0b4c58e9c2d2c84a38465abf757272d8` |
| `docling-project/docling-models` | 表格识别 TableFormer（accurate 203MB + fast 139MB） | 342 MB | `fc0f2d45e2218ea24bce5045f58a389aed16dc23` |

Dockerfile 中 14 个 blob hash 全部硬编码。**换模型版本时 commit 与全部 hash 都要同步更新**，否则 symlink 指向空文件，容器能起但推理失败。

---

## 重建 models/ 目录（外网执行）

镜像里的缓存结构就是从 HF 缓存直接搬的，所以最省事的方式是让 huggingface_hub 自己下载，再把 `blobs/` 拷出来。

```bash
cd MiaotongDoc-Docker/app/docling

pip install -U "huggingface_hub[cli]"
export HF_ENDPOINT=https://hf-mirror.com     # 国内镜像，直连 HF 慢

# 下到本地 HF 缓存
huggingface-cli download docling-project/docling-layout-heron --cache-dir ./_hf
huggingface-cli download docling-project/docling-models       --cache-dir ./_hf

# 按 Dockerfile 期望的布局摆好
mkdir -p models/layout-heron models/docling-models
cp -r ./_hf/models--docling-project--docling-layout-heron/blobs  models/layout-heron/
cp -r ./_hf/models--docling-project--docling-models/blobs        models/docling-models/
rm -rf ./_hf

du -sh models    # 期望约 506M
```

> ❌ **不要用 `git clone` HF 仓库**。仓库里的 `model.safetensors` 是 Git LFS 指针（几百字节的文本），不是真模型。
> （2026-08-10 已清理一个这样的残留目录 `layout-heron-git/`——118KB，从未被 Dockerfile 引用。）

### 校验 blob 完整性

Dockerfile 引用的 14 个 blob 必须全部存在，否则构建出的镜像会静默损坏：

```bash
cd MiaotongDoc-Docker/app/docling
grep -oE 'blobs/[0-9a-f]{40,64}' Dockerfile | sed 's|blobs/||' | sort -u | while read h; do
  if [ -f "models/layout-heron/blobs/$h" ] || [ -f "models/docling-models/blobs/$h" ]; then
    echo "OK   $h"
  else
    echo "MISS $h"
  fi
done
```

全部 `OK` 才能构建。若有 `MISS`，通常是上游更新了 commit —— 需同步更新 Dockerfile 里的 commit 与 hash。

---

## 构建与验证

```bash
cd MiaotongDoc-Docker
docker compose --profile docling build docling
```

构建末尾会打印 HF 缓存结构与总大小（Dockerfile "验证缓存完整性" 步骤），确认约 506MB。

```bash
# 启动
docker compose --profile docling up -d docling

# 健康检查（healthcheck.sh 查 /health + /v1/health/ready 模型就绪）
docker compose ps docling
curl http://localhost:5001/health
```

---

## 资源占用

| 项 | 值 |
|---|---|
| 镜像大小 | ~12 GB（磁盘占用）/ ~3.8 GB（`docker save` tar） |
| 运行内存 | 3–4 GB |
| 启动时间 | 首次加载模型约 60–90s（healthcheck `start_period` 已放宽） |

因为重，默认不启动。四层 OCR 瀑布路由 `Docling → PaddleOCR → Tesseract → PDFBox` 中 Docling 是第一层，未启用时自动降级到 PaddleOCR。

---

## 相关文档

- [plans/2026-08-09-ocr-models-offline-deploy.md](../../../plans/2026-08-09-ocr-models-offline-deploy.md) — OCR 模型离线化方案
- [plans/offline-env-requirements.md](../../../plans/offline-env-requirements.md) — 内网部署环境依赖
- [plans/ocr-usage-guide.md](../../../plans/ocr-usage-guide.md) — OCR 引擎选型
- 路由实现：`PdfRecognizeService.java:48-103`
