# OCR 识别使用说明

> **创建日期**: 2026-07-12 | **更新**: v2.5 PaddleOCR 接入
> **适用版本**: MiaotongDoc v1.0.2+ / miaotongdoc-server Spring Boot 3.2.5 / miaotongdoc-web Vue 3.4+

---

## 1. 能力概览

MiaotongDoc PDF 编辑器支持 **四层 OCR 引擎自动路由**，根据文档类型智能选择最优引擎：

| 引擎 | 适用场景 | 中文精度 | 表格保留 | 启用配置 | 内存 |
|------|----------|----------|----------|----------|------|
| **Docling** | 文本型 PDF（有内嵌文字层） | ✅ 中等 | ✅ 优秀 | `DOCLING_ENABLED=true` | 738 MB |
| **PaddleOCR** 🆕 | 中文扫描件主力（推荐） | ✅ 90%+ | ✅ PP-Structure | `PADDLE_OCR_ENABLED=true` | 800 MB |
| **Tesseract** | 多语言兜底（扫描件） | ⚠️ 一般 | ❌ 无 | `OCR_ENABLED=true` | 25 MB |
| **PDFBox** | 文本型 PDF 提取（最后兜底） | ✅ 中等 | ❌ 无 | **始终启用** | 内置 |

**前端**自动调用 `/api/pdf/{id}/recognize`，无需关心后端引擎选择。

---

## 2. 启用方式

### 2.1 编辑 `.env` 配置

```bash
cd MiaotongDoc-Docker
cp .env.example .env
vi .env
```

找到 OCR 相关配置：

```bash
# ===== Docling 文档解析配置（文本型 PDF） =====
DOCLING_SERVER_URL=http://docling:5001   # 默认即可
DOCLING_ENABLED=true                     # ← 改 true（默认 false）
DOCLING_TIMEOUT=300                      # 超时秒数
DOCLING_OCR_LANGUAGES=chi_sim+eng        # 语言包

# ===== PaddleOCR 配置（中文扫描件主力，推荐启用） =====
PADDLE_OCR_SERVER_URL=http://ocr-paddle:5003
PADDLE_OCR_ENABLED=true                  # ← 改 true（默认 true，中文场景必备）
PADDLE_OCR_TIMEOUT=600
PADDLE_OCR_LANGUAGE=ch
PADDLE_OCR_USE_TABLE=true                # 表格识别
PADDLE_OCR_USE_LAYOUT=true               # 版面分析
PADDLE_OCR_RETURN_COORDS=true            # 文字坐标（前端画布高亮）

# ===== Tesseract OCR 配置（多语言兜底，可选） =====
OCR_ENABLED=false                        # PaddleOCR 启用时可关闭
OCR_SERVER_URL=http://ocr:5002
```

### 2.2 启动 OCR 容器

```bash
# 启动所有服务（包括 Docling + PaddleOCR + Tesseract 三个 OCR 容器）
docker compose up -d

# 启动 PaddleOCR（中文场景首选）
docker compose up -d ocr-paddle

# 验证容器状态
docker ps | grep -E "(docling|ocr)"
# 应输出：
# miaotongdoc-docling        Up (healthy)
# miaotongdoc-ocr-paddle     Up (healthy)    ← 中文主力
# miaotongdoc-ocr            Up (healthy)    ← 多语言兜底（可选）
```

> **首次启动 PaddleOCR 注意**：
> - 镜像构建需要 5-10 分钟（下载 paddlepaddle 700MB + 模型 1.5GB）
> - 容器启动后还需 1-2 分钟加载模型到内存
> - 模型缓存到 `./data/paddleocr-cache/` 持久化目录，容器重建无需重新下载

### 2.3 验证 OCR 接口可用

```bash
# Docling 健康检查
curl http://localhost:5001/health
# 应返回：{"status":"ok",...}

# PaddleOCR 健康检查（中文主力）
curl http://localhost:5003/health
# 应返回：{"status":"ok","service":"ocr-paddle","engine":"PaddleOCR"}

# Tesseract 健康检查（可选）
curl http://localhost:5002/health
# 应返回：{"status":"ok","service":"ocr"}
```

如果某个引擎不可用，**后端自动跳过并尝试下一个引擎**（不会报错）。

---

## 3. 用户视角操作流程

### 3.1 自动识别（首次打开 PDF）

1. 用户在 Home 上传或点击一个 PDF
2. 进入 DocEditor → 加载 PdfEditor
3. **首次加载若未识别**：自动触发 OCR（无需用户操作）
4. PDF 顶部出现蓝色进度条：
   ```
   ┌──────────────────────────────────────────┐
   │ Docling 解析中... 45%                    │  ← 蓝色 sticky 进度条
   └──────────────────────────────────────────┘
   ```
5. 识别完成（约 10-60 秒）→ 进度条消失
6. Markdown 面板标题旁出现绿色徽标：
   ```
   ┌────────────────────────────────────┐
   │ 第 1 页内容  ● 已识别   已保存     │
   └────────────────────────────────────┘
   ```

### 3.2 手动重新识别

**场景**：上传错了引擎 / 识别结果差 / 修改了 PDF

**三种入口**：

1. **工具栏**：`🔍 识别` 按钮（OCR 引擎颜色根据状态变化）
   - 灰色：未识别
   - 绿色：已识别（点击重新识别）
   - 红色：识别失败（点击重试）
   - 蓝色 loading：识别中

2. **Markdown 面板底部**：`↻ 重新识别` 按钮

3. **快捷键**：`Ctrl + R`

**识别完成后**：自动 toast 提示，例：

```
┌─────────────────────────────────────────┐
│ ✅ OCR 识别完成（引擎：docling）        │
└─────────────────────────────────────────┘
```

### 3.3 状态徽标说明

| 徽标 | 含义 | 触发条件 |
|------|------|----------|
| `● 未识别`（灰） | 文档从未识别过 | 首次打开 / OCR 失败后未重试 |
| `● 识别中...`（蓝，脉动） | OCR 正在执行 | 调用 `manualRecognize()` 期间 |
| `● 已识别`（绿） | 识别成功且有结果 | `recognizeStatus === 'recognized'` |
| `● 部分识别`（黄） | 仅有部分页面识别成功 | 引擎返回不完整结果 |
| `● 识别失败`（红） | 三层引擎全部失败 | 网络断 / 引擎不可用 |

**hover 徽标** 显示上次识别时间（自动相对时间）：

```
● 已识别  5 分钟前
│
└── 完整：2026-07-12 14:30:25
```

---

## 4. SSE 进度推送（开发调试用）

### 4.1 API 端点

```http
POST /api/pdf/{id}/recognize
GET  /api/pdf/{id}/recognize-stream   ← SSE 流
```

### 4.2 事件格式

服务端推送 4 种事件：

```
event: connected
data: {"docId":123,"ts":1720780200000}

event: progress
data: {"percent":45,"message":"Docling 解析中...","ts":1720780203000}

event: done
data: {"engine":"docling","ts":1720780210000}

event: error
data: {"error":"OCR 服务不可用","ts":1720780215000}
```

### 4.3 浏览器调试

```javascript
// 在浏览器控制台手动订阅
const es = new EventSource('/api/pdf/123/recognize-stream', { withCredentials: true })

es.addEventListener('connected', e => console.log('已连接:', e.data))
es.addEventListener('progress', e => console.log('进度:', JSON.parse(e.data)))
es.addEventListener('done', e => {
  console.log('完成:', JSON.parse(e.data))
  es.close()
})
es.addEventListener('error', e => {
  console.error('错误:', e.data || e)
  es.close()
})
```

### 4.4 cURL 测试

```bash
# 启动识别
curl -X POST http://localhost:9004/api/pdf/123/recognize \
  -H "Authorization: Bearer <token>"

# 订阅进度（注意 -N 禁用缓冲）
curl -N http://localhost:9004/api/pdf/123/recognize-stream \
  -H "Authorization: Bearer <token>"

# 输出示例：
# event:connected
# data:{"docId":123,"ts":1720780200000}
#
# event:progress
# data:{"percent":10,"message":"使用 Docling 引擎解析...","ts":1720780201000}
#
# event:progress
# data:{"percent":90,"message":"Docling 解析完成...","ts":1720780208000}
#
# event:done
# data:{"engine":"docling","ts":1720780210000}
```

---

## 5. 引擎路由决策表

后端 `PdfRecognizeService.recognize()` 的选择逻辑（v2.5 更新）：

```
PDF 文档
  │
  ├─ Docling 可用？ ── yes ─→ 尝试 Docling（最多 2 次）
  │   └─ 失败 → 休息 3s 重试
  │
  ├─ PaddleOCR 可用？ ── yes ─→ 尝试 PaddleOCR（中文扫描件，PP-OCRv4 + 表格）
  │   └─ 失败 → 继续
  │
  ├─ Tesseract OCR 可用？ ── yes ─→ 尝试 OCR（多语言兜底）
  │   └─ 失败 → 继续
  │
  └─ PDFBox（最后兜底）
      └─ 始终可用
```

**v2.4 → v2.5 路由升级**：

| 优先级 | v2.4 | v2.5（当前） |
|--------|------|--------------|
| 1 | Docling | Docling |
| 2 | Tesseract | **PaddleOCR 🆕** |
| 3 | PDFBox | Tesseract |
| 4 | — | PDFBox |

**关键能力对比**：

| 能力 | Docling | PaddleOCR | Tesseract | PDFBox |
|------|---------|-----------|-----------|--------|
| 中文印刷体 | 中 | **优（90%+）** | 良（80%） | 中 |
| 中文手写体 | 差 | 中 | 差 | 差 |
| 表格识别 | ✅ 优 | ✅ 优（PP-Structure） | ❌ | ❌ |
| 版面分析 | ✅ | ✅（PP-Layout） | ❌ | ❌ |
| 文字坐标 | ✅ | ✅（含置信度） | ❌ | ❌ |
| 内存 | 738 MB | 800 MB | 25 MB | 内置 |

**前端 UI 会显示最终使用的引擎**：

```
┌────────────────────────────────────────┐
│ ✅ OCR 识别完成（引擎：paddleocr）     │  ← toast 提示
└────────────────────────────────────────┘
```

---

## 6. 性能基线

| 文档类型 | 页数 | Docling | PaddleOCR | Tesseract | PDFBox |
|----------|------|---------|-----------|-----------|--------|
| 文本型 PDF | 10 | ~5s | ~15s | ~30s | ~1s |
| 文本型 PDF | 100 | ~30s | ~90s | ~5min | ~5s |
| **扫描件（中文）** | 10 | ~10s | **~20s** | ~30s | ❌ 空 |
| **扫描件（中文）** | 100 | ~60s | **~3min** | ~5min | ❌ 空 |
| 扫描件（英文） | 10 | ~10s | ~15s | ~20s | ❌ 空 |

> **建议**：
> - 文本型 PDF → Docling（精度高 + 结构化）
> - 中文扫描件 → **PaddleOCR**（精度 + 表格）
> - 多语言扫描件 → Tesseract（轻量，多语言）

---

## 7. 故障排查

### 7.1 徽标一直是"未识别"

```bash
# 1. 检查后端日志（看实际走到哪个引擎）
docker logs miaotongdoc-server 2>&1 | grep -E "(recognize|engine=|PaddleOCR|OCR)" | tail -30

# 2. 手动触发识别（看错误）
curl -X POST http://localhost:9004/api/pdf/123/recognize \
  -H "Authorization: Bearer <token>"

# 3. 订阅 SSE 流
curl -N http://localhost:9004/api/pdf/123/recognize-stream \
  -H "Authorization: Bearer <token>"
```

可能原因：
- 所有 OCR 引擎都未启用（只走 PDFBox）→ 设 `DOCLING_ENABLED=true` + `PADDLE_OCR_ENABLED=true`
- PaddleOCR 容器首次启动未完成（加载模型需要 1-2 分钟）→ 等待或查看 `docker logs miaotongdoc-ocr-paddle`
- 网络隔离（容器之间无法通信）→ `docker exec miaotongdoc-server curl http://ocr-paddle:5003/health`

### 7.2 进度条卡在某个百分比

```bash
# 1. 查看 SSE 流是否还活着
curl -N http://localhost:9004/api/pdf/123/recognize-stream \
  -H "Authorization: Bearer <token>"

# 2. 查看 PaddleOCR 容器日志
docker logs miaotongdoc-ocr-paddle --tail 50
```

可能原因：
- PaddleOCR 处理 100+ 页大文档较慢（~3min）→ 调大 `PADDLE_OCR_TIMEOUT=1800`
- 文档太大（>100MB）→ 压缩后再上传
- PaddleOCR 引擎崩溃（OOM）→ 重启容器或减少 `gunicorn -w` worker 数

### 7.3 PaddleOCR 识别中文乱码

```bash
# 1. 检查容器语言环境
docker exec miaotongdoc-ocr-paddle locale
# 必须包含 C.UTF-8 或 zh_CN.UTF-8

# 2. 检查中文字体
docker exec miaotongdoc-ocr-paddle fc-list :lang=zh | head -5
# 必须包含 Noto Sans CJK

# 3. 验证模型加载
docker exec miaotongdoc-ocr-paddle python -c "from paddleocr import PaddleOCR; o = PaddleOCR(lang='ch'); print('OK')"
```

### 7.4 容器启动慢（>3 分钟）

PaddleOCR 首次启动需要：
1. 解压 paddlepaddle 包（~30s）
2. 下载 PP-OCRv4 模型（~1-2min）
3. 下载 PP-Structure 模型（~1-2min）
4. 加载到内存（~30s）

模型已下载后会缓存到 `./data/paddleocr-cache/`，**第二次启动只需 30 秒**。

---

## 8. 相关文件

| 文件 | 用途 |
|------|------|
| `miaotongdoc-server/.../controller/PdfController.java` | `/recognize` + `/recognize-stream` 端点 |
| `miaotongdoc-server/.../service/PdfRecognizeService.java` | 四层引擎路由（Docling→PaddleOCR→Tesseract→PDFBox） |
| `miaotongdoc-server/.../service/OcrProgressService.java` | SSE emitter 管理 |
| `miaotongdoc-server/.../service/OcrService.java` | Tesseract 调用 |
| `miaotongdoc-server/.../service/PaddleOcrClient.java` 🆕 | PaddleOCR 调用 + 进度回调 |
| `miaotongdoc-server/.../config/DoclingProperties.java` | Docling 配置 |
| `miaotongdoc-server/.../config/PaddleOcrProperties.java` 🆕 | PaddleOCR 配置 |
| `miaotongdoc-web/.../composables/pdf/usePdfOcrProgress.ts` | 前端 SSE 订阅 |
| `miaotongdoc-web/.../components/PdfEditor.vue` | OCR UI（工具栏按钮 / 进度条 / 徽标） |
| `MiaotongDoc-Docker/app/ocr-paddle/Dockerfile` 🆕 | PaddleOCR 镜像构建 |
| `MiaotongDoc-Docker/app/ocr-paddle/app.py` 🆕 | PaddleOCR Flask 服务 |

---

## 9. 路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| ✅ v2.4 | Docling + Tesseract + PDFBox 三层路由 + SSE 进度推送 | 已完成 |
| ✅ **v2.5** | **PaddleOCR 中文优化（中文扫描件 90%+）** | **已完成** |
| ⏳ v2.6 | PDF 五维画像自动路由（T1-T8）按页类型分流 | 规划中 |
| ⏳ v2.7 | VLM 视觉问答（前端选区截图，后端 GPT-4V/Claude） | 规划中 |
| ⏳ v2.8 | 关键条款抽取（合同金额/日期/违约责任自动识别） | 规划中 |

---

## 10. 部署清单（运维）

部署 PaddleOCR 需要：

| 项 | 要求 |
|----|------|
| 内存 | 容器预留 **800MB**（含系统 + Python + paddlepaddle） |
| CPU | 推荐 **2 核**（PaddleOCR 推理较慢，多核有助加速） |
| 磁盘 | 模型缓存 **1.5GB**（持久化到 `./data/paddleocr-cache/`） |
| 启动时间 | 首次 **3-5 分钟**（含模型下载），后续 **30-60 秒** |

如果内存紧张，可以：
1. **关闭 Docling**（节省 738MB）— 但失去结构化解析
2. **关闭 Tesseract**（节省 25MB）— 由 PaddleOCR 全权处理
3. **使用更小的 PaddleOCR 模型**（如 PP-OCRv4-mobile，约 200MB）

---

*文档维护：Claude Code · 更新日期：2026-07-12 · v2.5 PaddleOCR 接入*