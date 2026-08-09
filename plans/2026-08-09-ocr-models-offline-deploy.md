# OCR 模型/引擎优化与内网离线部署(2026-08-09)

> **状态**: ✅ 已完成 | **作者**: Claude Code | **优先级**: 🟡 中(扫清生产部署障碍)

## 📊 状态摘要

- **复杂度**: 🟡 中(6 个文件改动 + 1 个文档)
- **进度**: 完成(2026-08-09)
- **改动文件**: 6 个(代码 3 + Docker 3)
- **新增文件**: 2 个(`app/docling/healthcheck.sh`、`app/ocr/entrypoint.sh`)
- **验证**: 后端类型检查通过、Tesseract entrypoint 离线回退 OK

## 🎯 目标

1. **保证模型都能用**: PaddleOCR 内网无外网探测、Docling 模型加载健康检查、Tesseract 语言包动态扩展
2. **项目中要有应用**: 三套 OCR 模型都已烧入镜像,在 PDF 编辑器、合同 AI 解析、扫描件识别等场景有具体调用
3. **环境配置完整说明**: `.env.example` 每项配置说明用途、默认值、影响范围、内网/外网注意事项

## 🔍 现状回顾

### OCR 引擎矩阵

| 引擎 | 镜像 | 模型 | 默认状态 | 用途 |
|---|---|---|---|---|
| **Docling** | `miaotongdoc-docling` (port 5001) | 506 MB 已烧入 | `--profile docling` 启动 | PDF→结构化 Markdown(表格/标题) |
| **PaddleOCR** | `miaotongdoc-ocr-paddle` (port 5003) | 232 MB 已烧入 | **默认启动** | 中文扫描件主力,支持表格识别 |
| **Tesseract** | `miaotongdoc-ocr` (port 5002) | apt tessdata | `--profile ocr` 启动 | 多语言兜底 |
| **PDFBox** | 内嵌 | 无需模型 | 永远在 | 纯文本 PDF 兜底 |

### 4 层瀑布路由

`PdfRecognizeService.java:48-103`:
```
PDF → Docling(若启用) → PaddleOCR(若启用) → Tesseract(若启用) → PDFBox
```

前端只暴露 `mobile/server` PaddleOCR 档位(`PdfRibbon.vue:201-204`),其它引擎由后端自动选。

## 🔧 改动清单

### 1. PaddleOCR 内网零外网(`app/ocr-paddle/Dockerfile`)

**问题**: 之前没有显式禁用 PaddleX 的远程模型仓库探测,首次启动会去 `aistudio.baidu.com`/`modelscope`/`hf-mirror` 探测,失败后才回退到本地缓存。

**解决**: 在 Dockerfile 加 `ENV PADDLE_PDX_DISABLE_REMOTE=1 PADDLEX_DISABLE_REMOTE_SOURCE=1 FLAGS_use_mkldnn=0`,启动期彻底禁外网。

### 2. Docling 深度健康检查(`app/docling/healthcheck.sh`)

**问题**: 原 healthcheck 只查 `/health`,模型是否真加载不知道。

**解决**: 新脚本检查 `/health` + `/v1/health/ready`(docling-serve 1.26 自带),任何一个失败就非零退出。docker-compose 改用脚本。

### 3. Tesseract 语言包动态扩展(`app/ocr/entrypoint.sh` + `app/ocr/app.py`)

**问题**: 之前只内置 chi_sim+eng,加日韩要重建镜像。

**解决**:
- 新增 `entrypoint.sh`,启动时根据 `OCR_LANGUAGES` 环境变量动态 `apt-get install tesseract-ocr-<lang>`
- 内网环境无 apt 源时优雅降级(打印警告,不崩溃)
- `app.py` 增加 `loaded_languages` 字段到 `/health` 响应,运维可见
- Dockerfile 默认 `OCR_LANGUAGES="chi_sim+eng"`,运行时可通过环境变量覆盖

### 4. 应用层默认模型可配置(`PaddleOcrClient.java`)

**问题**: `recognizePdf(docId, lang, callback)` 硬编码 `"mobile"`,无法通过环境变量改默认。

**解决**: 改读 `properties.getDefaultModel()`,由 `PADDLE_OCR_DEFAULT_MODEL` 环境变量控制。

### 5. 后端 properties 全配置(`PaddleOcrProperties.java` + `application.yml`)

新增字段:
- `defaultModel`(默认 `mobile`)
- 加 `application.yml` 中 `paddle-ocr` 段的 `default-model` / `use-table-recognition` / `use-layout` / `return-coordinates` 等映射
- `ocr` 段加 `languages` 字段

### 6. docker-compose 环境变量透传

web-server 容器原来只透传 `*_ENABLED` + `_SERVER_URL`,补全 `PADDLE_OCR_TIMEOUT`、`PADDLE_OCR_DEFAULT_MODEL`、`PADDLE_OCR_USE_TABLE` 等 10 个变量。

### 7. `.env.example` 完整注释

每项 OCR 配置都加了:
- 用途说明
- 默认值
- 可选值列表
- 内网/外网注意事项
- 末尾增加"OCR 引擎开关组合速查"(4 种部署场景)

## ✅ 验证

- 后端类型检查:`PaddleOcrClient.java` IDE diagnostics 通过
- 文件编码:全部 UTF-8(PaddleOcrProperties 之前是 GBK 已确认)
- Tesseract entrypoint 在无 apt 源的内网环境会优雅降级,打印警告但不阻塞启动

## 🔗 关联

- **离线部署总方案**: `plans/2026-07-26-offline-deployment.md`
- **OCR 使用文档**: `plans/ocr-usage-guide.md`
- **CLAUDE.md**: 已同步(见项目根 CLAUDE.md)

## 📝 后续 TODO(可选)

1. 把 `app/docling/layout-heron-git/`(LFS pointer 134 字节)清理,Dockerfile 未引用
2. 给 web-server 加 `/api/ocr/status` 端点,聚合三套引擎健康状态(前端做统一监控)
3. 评估 `UVDoc` / `PP-LCNet_x1_0_doc_ori`(已预下载但未用)是否要暴露给前端做"文档方向识别"