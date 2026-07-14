# 妙同文档 — PDF 编辑器重塑方案

> **状态**: 重塑版 | **创建日期**: 2026-07-12 | **维护者**: Claude Code
> **本文档**: 简洁版，聚焦"做什么"和"怎么做"，不堆术语

---

## 一、我们要做什么

**一句话**：做一个让用户能"打开 PDF → 直接编辑 → AI 帮忙 → 在线签署"的编辑器。

**取代现状**：
- 现有的 PdfEditor.vue（1905 行）功能基础，但不支持 OCR、AI、协同
- 用户的痛点：扫描件改不了、AI 不会用、多人协作难

**我们要做的（按优先级）**：

| 阶段 | 核心功能 | 周期 |
|------|----------|------|
| **阶段 1** | 基础重构 + OCR 识别（杀手锏） | 1-2 月 |
| **阶段 2** | 文字编辑 + 批注 + 协同 | 3-4 月 |
| **阶段 3** | AI 助手 + 格式转换 | 5-6 月 |
| **阶段 4** | 签署 + 安全 + 移动端 | 7-8 月 |

---

## 二、用户与场景

### 2.1 谁会用？（3 类核心用户）

| 用户 | 占比 | 用 PDF 做什么 |
|------|------|---------------|
| **行政人员**（李婷，28 岁） | 50% | 改扫描件、多人审阅、归档 |
| **业务经理**（王强，35 岁） | 30% | 处理合同、现场签字、查条款 |
| **法务专员**（张律师，40 岁） | 15% | 审阅合同、标条款、写批注 |

### 2.2 5 大核心场景

1. **📄 打开阅读**（每天）：秒开、搜索、目录、缩略图
2. **✏️ 编辑修改**（每周）：扫描件 OCR 改字、AI 改写段落
3. **📋 审阅批注**（每周）：高亮评论、形状批注、协同讨论
4. **🔒 安全签署**（每月）：手写签名、电子签章、审批流
5. **📤 分享协作**（每周）：链接分享、权限控制、访问记录

---

## 三、核心功能清单

### 3.1 阶段 1 必做（基础 + OCR 杀手锏）

| 功能 | 用户视角 | 优先级 |
|------|----------|--------|
| **PDF 秒开** | 打开 100 页 ≤ 3 秒 | P0 |
| **缩略图侧栏** | 左侧显示所有页面缩略图 | P0 |
| **全文搜索** | Ctrl+F 搜任意关键词 | P0 |
| **OCR 智能识别** | 扫描件 → 可编辑文字（杀手锏） | P0 |
| **识别结果校对** | 双栏对照：左原 PDF / 右可编辑 | P0 |
| **表格识别** | 表格保留为结构化数据 | P0 |
| **图片导出** | 选中页面 → 导出 PNG/JPG | P0 |
| **基础批注** | 高亮、评论、画笔 | P0 |

### 3.2 阶段 2 必做（编辑 + 协同）

| 功能 | 用户视角 | 优先级 |
|------|----------|--------|
| **文字直接编辑** | 点击文字直接改 | P0 |
| **协同编辑** | 多人同时编辑，看到他人光标 | P0 |
| **协同批注** | 不同人不同颜色 | P0 |
| **形状批注** | 矩形、椭圆、箭头 | P0 |
| **图章批注** | 8 种预置 + 自定义 | P1 |
| **手写签名** | 鼠标/触屏手写签名 | P1 |
| **撤销重做** | Ctrl+Z 100 步历史 | P0 |
| **自动保存** | 5 秒空闲自动保存 | P0 |

### 3.3 阶段 3 必做（AI + 转换）

| 功能 | 用户视角 | 优先级 |
|------|----------|--------|
| **AI 摘要** | 100 页 → 一段话摘要 | P0 |
| **AI 问答** | "这份合同的总金额？" | P0 |
| **AI 翻译** | 选中英文 → 翻译中文 | P0 |
| **视觉问答（VLM）** | 选中扫描件某区域 → "这里写什么？" | P1 |
| **关键条款抽取** | 合同 → 标出金额/日期/违约责任 | P1 |
| **OCR 结果优化** | OCR 后 → AI 去噪声 | P1 |
| **PDF ↔ Word 转换** | 格式互转 | P0 |
| **PDF → Markdown** | 提取结构化文本 | P0 |
| **PDF → Excel** | 表格数据提取 | P1 |
| **PDF 合并/拆分** | 多文件合并、按页拆分 | P0 |

### 3.4 阶段 4 必做（签署 + 安全 + 移动）

| 功能 | 用户视角 | 优先级 |
|------|----------|--------|
| **电子签章** | 管理员预配置，员工调用 | P1 |
| **数字签名** | 符合电子签名法 | P1 |
| **加密/权限** | 设置密码、禁止打印 | P1 |
| **动态水印** | 用户名/IP/时间戳 | P1 |
| **完整性校验** | 检测文件被篡改 | P1 |
| **移动端响应式** | iPad 可用 | P1 |

---

## 四、技术架构（极简版）

### 4.1 核心原则

```
✅ 不动现有 13 个容器
✅ OCR 容器按需启动（空闲 0 占用）
✅ 视觉与 MD 编辑器一致（复用设计语言）
✅ 不搞双模式并存（一个就够）
✅ 模块可复用，代码和接口独立解耦（最重要）
```

### 4.2 现有 OCR/解析容器的定位（关键！）

> **v2 不是废弃现有容器，而是明确每个容器的职责，加一个新容器补强 OCR 能力。**

#### 现有容器 + 新增容器 的完整角色

| 容器 | 端口 | 状态 | v2 角色 | 何时启用 |
|------|------|------|---------|----------|
| **miaotongdoc-docling** | 5001 | ✅ **保留** | **结构化理解主力** | 文本型 PDF（T1-T3） |
| **miaotongdoc-ocr** (Tesseract) | 5002 | ⚠️ **保留但默认禁用** | **多语言兜底** | PaddleOCR 失败时 |
| **miaotongdoc-ocr-paddle-light**（新增） | 5003 | ✅ **新增** | **OCR 主力** | 扫描件 PDF（T4-T7） |

#### 三个 OCR/解析容器的分工

```
miaotongdoc-docling（已有）
├─ 强项：版面分析、阅读顺序、结构化 Markdown、章节关系
├─ 弱项：内置 OCR 是 RapidOCR，中文识别一般
├─ 适用：文本型 PDF（有内嵌文字层）
└─ v2 定位：✅ **必须保留**，是文本型 PDF 最佳工具

miaotongdoc-ocr（Tesseract，已有）
├─ 强项：多语言（100+）、内存小（25 MB）
├─ 弱项：中文 80%、无版面分析、无表格识别
├─ 适用：多语言兜底、PaddleOCR 故障时启用
└─ v2 定位：⚠️ **保留容器但默认禁用**（`OCR_ENABLED=false`）

miaotongdoc-ocr-paddle-light（新增）
├─ 强项：中文 90%+、表格 80%+、版面分析
├─ 弱项：内存占用（800 MB）、多语言一般
├─ 适用：扫描件 PDF（中文场景）
└─ v2 定位：✅ **新增**，是中文 OCR 最佳工具
```

#### 后端调用关系（PDF 五维画像自动路由）

```
PDF 上传
    ↓
[PdfProfilerService 五维画像] 判定 8 种类型
    ↓
┌─────────┬───────────────────────────────────┐
│ 类型     │ 路由                              │
├─────────┼───────────────────────────────────┤
│ T1 纯文本│ PDFBox 直接读（0 成本）            │
│ T2 文本+表格│ miaotongdoc-docling（已有）     │
│ T3 文本+复杂│ miaotongdoc-docling + VLM       │
│ T4 扫描件简单│ miaotongdoc-ocr-paddle-light（新增）│
│ T5 扫描件+表格│ miaotongdoc-ocr-paddle-light（新增）│
│ T6 扫描件+复杂│ PaddleOCR-VL（后期，16G+ 服务器）│
│ T7 混合型 │ 分页路由，每页独立判定           │
│ T8 加密  │ 先解密再判定                       │
└─────────┴───────────────────────────────────┘
    ↓
失败兜底：miaotongdoc-ocr（Tesseract，默认禁用）
```

#### 容器启用配置（.env）

```bash
# 现有容器（保留）
DOCLING_ENABLED=true           # Docling 一直启用，结构化主力
OCR_ENABLED=false              # Tesseract 默认禁用，仅作兜底

# 新增容器
PADDLE_OCR_ENABLED=true        # PaddleOCR 主力，按需启动
```

#### 资源账本更新（明确两个现有容器的影响）

| 容器 | 内存 | v2 状态 |
|------|------|---------|
| miaotongdoc-docling | 738 MB | ✅ **保留**（不动） |
| miaotongdoc-ocr (Tesseract) | 25 MB | ⚠️ **保留但禁用**（占用极小） |
| miaotongdoc-ocr-paddle-light | 800 MB | ✅ **新增，按需启动** |
| **合计** | **1.6 GB** | 占用可控 |

#### 结论

- ✅ **docling 必须保留**——是文本型 PDF 结构化最优解，不能替代
- ✅ **Tesseract 容器保留**——多语言兜底，禁用时几乎 0 成本
- ✅ **PaddleOCR 新增作主力**——补强中文/表格识别
- ✅ **三者各司其职**，按 PDF 类型自动路由
- ✅ **零容器废弃**，完全兼容现有架构

### 4.1.1 「可复用 vs 解耦」原则（核心架构原则）

**模块可复用 ≠ 代码耦合**。这是软件设计的核心区分。

#### ✅ 可复用（Reusable）

- **通用能力**放在通用模块：`documentAiApi` / `useAiChat` / `useRouter` / `useStorage`
- PDF 和 MD 都**调用**这些通用模块
- 通用模块升级，PDF 和 MD 同时受益

#### ❌ 不耦合（Decoupled）

- PDF 和 MD **互不依赖**，可以独立修改、独立发布
- PDF 不能 `import` MD 的私有组件，反之亦然
- 通用模块不能反向依赖 PDF 或 MD（避免成为"上帝模块"）

#### 依赖方向（单向、不可逆）

```
                ┌──────────────────┐
                │   通用模块       │ ← 最底层
                │  (api/ stores/  │    无业务依赖
                │   composables/) │
                └────────┬─────────┘
                         │ 依赖
            ┌────────────┼────────────┐
            │                         │
            ▼                         ▼
    ┌──────────────┐          ┌──────────────┐
    │  PDF 模块     │          │  MD 模块      │ ← 上层
    │ (PdfXxx.vue/ │          │ (MarkdownXxx) │    互不依赖
    │  usePdfXxx)  │          │               │
    └──────────────┘          └──────────────┘
```

**规则**：
- 通用模块**只能被上层调用**，不能反过来依赖上层
- PDF 和 MD **平级**，互不依赖
- 上层模块之间通过**通用模块**通信，不直接耦合

#### 实际例子

| 场景 | 可复用 | 解耦方式 |
|------|--------|----------|
| AI 流式聊天 | PDF 和 MD 都用 `useAiChat` | PDF 加 `usePdfAiFloat` 包装，**不直接 import MD 组件** |
| AI API | PDF 和 MD 都用 `documentAiApi` | 不新建 `ai.ts`，避免重复 |
| Yjs 协同 | PDF 和 MD 都用 Yjs | 各有自己的 `usePdfCollaborate` / MD 的协同逻辑，**不互相 import** |
| 主题/颜色 | PDF 和 MD 都用同一套 CSS 变量 | 改一处 CSS，两边都生效，**不共享 Vue 组件** |
| 文件下载 | PDF 和 MD 都用同一工具 | 各自调用，**不共享组件** |

#### 错误示例（不要这样做）

```ts
// ❌ 错误：PDF 直接 import MD 私有组件
import { MarkdownEditor } from '@/components/MarkdownEditor.vue'

// ❌ 错误：通用模块反向依赖业务模块
// composables/useAiChat.ts
import { PdfEditor } from '@/components/PdfEditor.vue'  // 错！

// ❌ 错误：PDF 和 MD 互相 import
// components/PdfAiFloatPanel.vue
import { MdAiLogic } from '@/components/MarkdownEditor.vue'  // 错！
```

#### 正确示例（应该这样做）

```ts
// ✅ 正确：PDF 调用通用模块
import { useAiChat } from '@/composables/useAiChat'  // 通用
import { documentAiApi } from '@/api/documentAi'      // 通用
import { usePdfAiFloat } from '@/composables/usePdfAiFloat'  // PDF 特有包装

// ✅ 正确：MD 也调用通用模块
import { useAiChat } from '@/composables/useAiChat'  // 通用
// MD 不 import 任何 PDF 相关的代码
```

**结论**：模块可复用是**对外能力**，代码独立解耦是**对内结构**。两者不矛盾，反而相辅相成。

### 4.2 整体架构（一句话）

```
浏览器 Vue 3 + Element Plus + Yjs
    ↓
Spring Boot 3.2.5（9004）
    ↓
现有 13 容器（不动）+ 新增 2 个容器
    ├─ miaotongdoc-ocr-paddle-light（OCR 引擎，按需启动）
    └─ miaotongdoc-pdf-worker（异步任务）
```

### 4.3 OCR 引擎路由（8 类型 → 8 引擎）

| PDF 类型 | 推荐引擎 |
|----------|----------|
| T1 纯文本 | PDFBox 直接读（0 成本） |
| T2 文本+表格 | Docling |
| T4 扫描件简单 | PaddleOCR 轻量（新增） |
| T5 扫描件+表格 | PaddleOCR-VL（后期） |
| T7 混合型 | 分页路由 |

### 4.4 资源账本（8G 内存 · 不精简）

| 容器 | 内存 | 状态 |
|------|------|------|
| 系统 + Docker | 2.5 GB | 必用 |
| editor × 3 | 3.0 GB | 保留 |
| elasticsearch | 969 MB | 保留 |
| docling | 738 MB | 保留 |
| 其他 8 个 | ~500 MB | 保留 |
| **当前合计** | **~7.7 GB** | - |
| + PaddleOCR（按需启动） | +800 MB | 仅识别时 |

---

## 五、实施任务（4 阶段）

### 5.1 阶段 1：基础 + OCR（Week 1-8 · 8 周）

| # | 任务 | 工作量 | 依赖 |
|---|------|--------|------|
| 1 | 重构 PdfEditor.vue 拆 composable | 5 人天 | - |
| 2 | PDF 阅读器（秒开/搜索/目录/缩略图） | 5 人天 | 1 |
| 3 | PDF 工具栏（与 MD 编辑器视觉一致） | 3 人天 | 1 |
| 4 | 抽取共享 AI 浮窗组件（从 MD 编辑器） | 2 人天 | - |
| 5 | 数据库迁移 V25（mt_pdf_block / mt_pdf_profile / mt_sys_config，沿用 Flyway 版本号） | 1 人天 | - |
| 6 | PdfProfilerService（PDF 五维画像） | 3 人天 | 5 |
| 7 | PdfOcrRouterService（OCR 路由） | 2 人天 | 6 |
| 8 | PaddleOcrClient（Java HTTP 客户端） | 1 人天 | 9 |
| 9 | 构建 ocr-paddle-light Docker 镜像 | 1.5 人天 | - |
| 10 | PdfProfileCard 前端组件 | 1 人天 | 6 |
| 11 | usePdfOcr composable + SSE | 2 人天 | 7 |
| 12 | PdfOcrPanel.vue（OCR 面板） | 1 人天 | 11 |
| 13 | PdfOcrEditor.vue（双栏对照编辑器） | 3 人天 | 11 |
| 14 | 基础批注（高亮/评论/画笔） | 3 人天 | 1 |
| 15 | 集成测试 + 灰度上线 | 2 人天 | - |

**总工作量**：约 35 人天，**8 周**

### 5.2 阶段 2：编辑 + 协同（Week 9-16 · 8 周）

| # | 任务 | 工作量 |
|---|------|--------|
| 1 | 文字直接编辑（PDFBox 文字流） | 5 人天 |
| 2 | 协同编辑（Yjs 复用） | 4 人天 |
| 3 | 形状批注（矩形/椭圆/箭头） | 3 人天 |
| 4 | 图章批注（8 种预置） | 2 人天 |
| 5 | 手写签名板 | 3 人天 |
| 6 | 撤销重做 + 自动保存 | 2 人天 |

**总工作量**：约 19 人天

### 5.3 阶段 3：AI + 转换（Week 17-24 · 8 周）

| # | 任务 | 工作量 |
|---|------|--------|
| 1 | AI 助手（摘要/问答/翻译） | 4 人天 |
| 2 | 视觉问答（VLM） | 4 人天 |
| 3 | OCR 结果 AI 优化 | 2 人天 |
| 4 | 关键条款抽取 | 2 人天 |
| 5 | PDF ↔ Word/Markdown 转换 | 3 人天 |
| 6 | PDF 合并/拆分 | 2 人天 |

**总工作量**：约 17 人天

### 5.4 阶段 4：签署 + 安全 + 移动（Week 25-32 · 8 周）

| # | 任务 | 工作量 |
|---|------|--------|
| 1 | 电子签章 + 数字签名 | 5 人天 |
| 2 | 加密/权限/水印 | 3 人天 |
| 3 | 完整性校验 | 1 人天 |
| 4 | 移动端响应式 | 3 人天 |

**总工作量**：约 12 人天

---

## 六、立即可执行的第一步

### 6.1 第 1 周任务清单（按项目平铺命名规范 · 标识 PDF 和 AI）

```
Day 1：
  - 创建 feature/pdf-editor-reshape 分支
  - 扩展现有 pdf.ts API（添加新接口）
  - ※ 不新建 ai.ts（项目已有 documentAi.ts，PDF 直接复用 documentAiApi）

Day 2-3：
  - 实现 usePdfRenderer（pdfjs-dist 封装）
  - 实现 usePdfCollaborate（Yjs 注册表，复用 useAiChat 模式）

Day 4-5：
  - 实现 usePdfAnnotation（标注管理）
  - 实现 usePdfAiFloat（PDF AI 浮窗逻辑，复用 useAiChat 流式聊天）
  - 抽取 PdfAiFloatPanel.vue（PDF 专用 AI 浮窗，不复用 MD 的 AiPanel.vue）
```

### 6.2 第 1 周需要创建的文件（按项目平铺命名规范 · 标识 PDF 和 AI）

```
miaotongdoc-web/src/
├── api/
│   └── pdf.ts                                   ⚠️ 扩展现有 pdf.ts（添加 PDF v2 接口）
│                                                   ※ 已有 documentAi.ts（通用 AI API），
│                                                     PDF 直接复用 documentAiApi 即可，
│                                                     不新建 ai.ts
│
├── components/
│   ├── PdfToolbar.vue                          ★ 新增：PDF 工具栏（与 MD 视觉一致）
│   ├── PdfCanvas.vue                           ★ 新增：PDF 中央渲染
│   ├── PdfAnnotationLayer.vue                  ★ 新增：PDF 标注层
│   ├── PdfTextLayer.vue                        ★ 新增：PDF 可编辑文字层
│   ├── PdfRightPanel.vue                       ★ 新增：PDF 右栏（识别/AI）
│   ├── PdfSidePanel.vue                        ★ 新增：PDF 左侧栏（缩略图/目录）
│   ├── PdfBottomBar.vue                        ★ 新增：PDF 底部进度条
│   ├── PdfTierBadge.vue                        ★ 新增：PDF 档位标识
│   ├── PdfProfileCard.vue                      ★ 新增：PDF 画像卡片
│   ├── PdfOcrPanel.vue                         ★ 新增：PDF OCR 面板
│   ├── PdfOcrEditor.vue                        ★ 新增：PDF 双栏编辑器
│   ├── PdfSignaturePad.vue                     ★ 新增：PDF 手写签名板
│   ├── PdfConvertDialog.vue                    ★ 新增：PDF 格式转换对话框
│   ├── PdfAiFloatPanel.vue                     ★ 新增：PDF AI 浮窗（与 MD 的 AiPanel 区分）
│   ├── AiPanel.vue                             ✓ 已有 MD 编辑器 AI 面板，PDF 不直接复用
│   └── PdfEditor.vue                           ⚠️ 重构现有 1905 行 PdfEditor.vue
│
├── composables/
│   ├── usePdfRenderer.ts                       ★ 新增：PDF 渲染（pdfjs-dist 封装）
│   ├── usePdfCollaborate.ts                    ★ 新增：PDF 协同（Yjs 注册表）
│   ├── usePdfAnnotation.ts                     ★ 新增：PDF 标注管理
│   ├── usePdfOcr.ts                            ★ 新增：PDF OCR 调用
│   ├── usePdfConvert.ts                        ★ 新增：PDF 格式转换
│   ├── usePdfSearch.ts                         ★ 新增：PDF 搜索
│   ├── usePdfTier.ts                           ★ 新增：PDF 档位检测
│   ├── usePdfAiFloat.ts                        ★ 新增：PDF AI 浮窗逻辑
│   └── useAiChat.ts                            ✓ 已有通用 AI 聊天，PDF 可复用流式
│
└── stores/
    └── pdf.ts                                  ⚠️ 扩展现有 pdf.ts
```

**项目已有 AI 相关文件（不新建）**：

| 文件 | 用途 | PDF 使用方式 |
|------|------|-------------|
| `api/documentAi.ts` | 通用 AI API（generate/chat/translate/compare/vision） | **直接复用 `documentAiApi`** |
| `composables/useAiChat.ts` | 通用流式 AI 聊天 | **直接复用**（PDF 流式用） |
| `components/AiPanel.vue` | MD 编辑器 AI 面板 | **不复用**，PDF 用 `PdfAiFloatPanel.vue` |

**命名规范说明（按项目现有约定 + 标识 PDF 和 AI）**：

| 类型 | 命名规则 | 示例 | 备注 |
|------|----------|------|------|
| **PDF 特有组件** | `PdfXxx.vue` | PdfEditor / PdfToolbar | 与 MD 完全区分 |
| **PDF AI 组件** | `PdfAiXxx.vue` | PdfAiFloatPanel | 即使是 AI 也标识 PDF |
| **PDF composable** | `usePdfXxx.ts` | usePdfRenderer / usePdfOcr | - |
| **PDF AI composable** | `usePdfAiXxx.ts` | usePdfAiFloat | 即使是 AI 也标识 PDF |
| **MD AI 组件**（已有） | `AiXxx.vue` | AiPanel.vue | MD 编辑器已用，PDF 不直接复用 |
| **通用 AI**（已有） | `useAiXxx.ts` / `documentAi.ts` | useAiChat / documentAi | PDF 复用，不新建 |

**关键原则**：
- ✅ **PDF 特有 → 加 `Pdf` 前缀**（组件 + composable + 涉及 AI 的也加）
- ✅ **MD 已有 AI 组件不动**（AiPanel.vue），PDF 自己新建 `PdfAiFloatPanel.vue`
- ✅ **AI 通用逻辑直接复用**（`documentAiApi` + `useAiChat`），不新建 `ai.ts`
- ✅ **模块可复用，代码独立解耦**（核心原则）：
  - 通用模块（`useAiChat` / `documentAiApi`）可被 PDF 和 MD 共用
  - PDF 和 MD 互不 import，互不依赖
  - 通用模块不反向依赖 PDF 或 MD
- ✅ 所有文件平铺在根目录，**不分子目录**
- ✅ 已有 `pdf.ts` / `PdfEditor.vue` / `AiPanel.vue` 直接复用或扩展，**不新建同名文件**

**与 MD 的 AI 区分**：

| 能力 | MD 编辑器（已有） | PDF 编辑器（新增） |
|------|-------------------|-------------------|
| AI API | `documentAiApi`（通用，PDF 复用） | `documentAiApi`（直接复用） |
| 流式聊天 | `useAiChat.ts`（通用，PDF 复用） | `useAiChat.ts`（直接复用） |
| AI 面板 UI | `AiPanel.vue`（MD 独立面板） | `PdfAiFloatPanel.vue`（PDF 浮窗） |
| AI 浮窗逻辑 | 在 MarkdownEditor.vue 内联 | `usePdfAiFloat.ts` composable |

### 6.3 阶段 1 完成标志

- [ ] PdfEditor.vue 重构完成，代码量减少 30%+
- [ ] 秒开 100 页 PDF ≤ 3 秒
- [ ] OCR 识别 10 页扫描件 ≤ 60 秒
- [ ] OCR 中文准确率 ≥ 90%
- [ ] 工具栏视觉与 MD 编辑器一致
- [ ] AI 浮窗组件（从 MD 抽取）可共用
- [ ] 至少 3 个内部用户试用 1 周无问题

---

## 七、风险与控制

### 7.1 主要风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 8G 内存部署后 OOM | 🔴 高 | OCR 按需启动，空闲 0 占用 |
| 重构破坏现有 PDF | 🟡 中 | 灰度发布，先小流量 |
| OCR 识别准确率不达 90% | 🟢 低 | 已实测 PP-OCRv4 中文 90%+ |
| 用户接受度低 | 🟡 中 | 灰度发布收集反馈 |

### 7.2 Feature Flag 控制

```sql
INSERT INTO mt_sys_config (config_key, config_value, description) VALUES
    ('pdf.feature.ocr_enabled', 'true', 'OCR 识别启用'),
    ('pdf.feature.profile_enabled', 'true', 'PDF 画像启用'),
    ('pdf.feature.vlm_enabled', 'false', 'VLM 视觉问答启用');
```

### 7.3 5 分钟回滚

```bash
# 数据库回滚
psql -U miaotong -d miaotongdocdb < backup.sql

# 关闭 OCR 容器
docker compose --profile ocr down

# 关闭新功能 flag
UPDATE mt_sys_config SET config_value = 'false' 
WHERE config_key LIKE 'pdf.feature.%';
```

---

## 八、成功指标

| 指标 | 目标 |
|------|------|
| **DAU** | 500+ |
| **NPS** | ≥ 40 |
| **PDF 处理效率** | 提升 ≥ 50% |
| **合同签署时间** | 从 2 天 → 30 分钟 |
| **OCR 准确率** | ≥ 90%（中文） |
| **页面加载** | < 3 秒 |

---

## 九、产品宣言

> **我们不是要做一个"功能最全"的 PDF 编辑器，**
> **而是要做一个"用户最愿用"的 PDF 编辑器。**
>
> **用户每天愿意打开它，**
> **用它 5 分钟完成过去 1 小时的工作，**
> **不再需要装 5 个不同的 PDF 工具，**
> **不再为"扫描件改不了"而烦恼。**

---

## 附录：与其他文档关系

| 文档 | 状态 |
|------|------|
| **本文档（pdf-editor-reshape.md）** | **当前唯一权威** ✅ |
| [pdf-product-plan.md](pdf-product-plan.md) | DEPRECATED（已合并） |
| [pdf-editor-v2-redesign.md](pdf-editor-v2-redesign.md) | DEPRECATED |
| [pdf-editor-tech-stack-audit.md](pdf-editor-tech-stack-audit.md) | DEPRECATED |
| [pdf-editor-v2-implementation.md](pdf-editor-v2-implementation.md) | DEPRECATED |
| [pdf-feature-redesign.md](pdf-feature-redesign.md) | DEPRECATED |

---

**版本**: 重塑版（无版本号递增）  
**生效日期**: 2026-07-12  
**后续**: 本文档是唯一权威方案，后续修改直接在本文档更新

**Sources**:
- [2026 PDF 编辑器排行](https://blog.csdn.net/xibuguanca/article/details/161452768)
- [PaddleOCR-VL 文档解析](https://blog.csdn.net/wangmengmeng99/article/details/155320172)