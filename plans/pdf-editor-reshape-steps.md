# PDF 编辑器重塑 — 落地步骤（科学合理版）

> **创建日期**: 2026-07-12 | **基于**: pdf-editor-reshape.md
> **原则**: 先易后难、先独立后组合、有验证有回滚

---

## 总览：6 个 Sprint，每个 Sprint 独立可验证

```
Sprint 0（准备，0.5 天）   → 建分支、定基线、确认环境
Sprint 1（骨架，2 天）     → 拆出 4 个 composable 骨架（不接业务）
Sprint 2（验证，2 天）     → 一个一个 composable 接业务并验证
Sprint 3（清理，1 天）     → 主组件精简，所有功能走 composable
Sprint 4（兜底，0.5 天）   → 灰度开关、回滚预案
Sprint 5（扩展，2-3 天）   → PdfAiFloatPanel + 新功能接入
```

**总时长**：8-9 天（1.5-2 周）完成 Phase 1

---

## Sprint 0：准备（0.5 天）✅ 今天立即做

### 任务清单

```
[ ] 1. 备份 PdfEditor.vue 到 PdfEditor.vue.original（防回滚用）
[ ] 2. 确认 npm run build 通过
[ ] 3. 启动 npm run dev，打开一个 PDF，确认能加载

⚠️ 不建分支：直接在 main 上做
   - 原因：新建 composable 不修改 PdfEditor.vue，不影响其他模块
   - 兜底：PdfEditor.vue.original 备份保证可回滚
```

### 命令

```bash
cd d:/tiany/stycode/MiaotongDoc/miaotongdoc-web

# 1. 备份原文件
cp src/components/PdfEditor.vue \
   src/components/PdfEditor.vue.original

# 2. 验证基线
npm run build
```

### 验收

- [ ] PdfEditor.vue.original 存在
- [ ] npm run build 通过

### 回滚方式

```bash
# 完全回滚到重构前
cp src/components/PdfEditor.vue.original \
   src/components/PdfEditor.vue
rm src/components/PdfEditor.vue.original
# 删除新建的 composable 即可
```

---

## Sprint 1：3 个 composable 骨架（2 天）

> **关键原则**：先建空壳 composable，**不接业务**，只定义接口。**保证编译通过 + 不破坏现有功能**。

### 1.1 拆分原则（先想清楚再动手）

PdfEditor.vue 有 76 个变量/函数，按职责归类：

| 职责分类 | 涉及内容 | 归属 composable |
|----------|----------|-----------------|
| **渲染** | pdfjsLib/pdfDoc/totalPages/scale/pageWidth/pageHeight/renderPage/renderAllThumbs/reRenderAll/fitWidth/loadPdf | **usePdfRenderer** |
| **协同** | ydoc/provider/yAnnotations/annotations/connectYjs | **usePdfCollaborate** |
| **标注** | annotations/showCommentDialog/pendingRect/isDrawing/drawPoints/getHighlightStyle/onMouseDown/Move/Up | **usePdfAnnotation** |
| **状态管理** | activeTool/activeColor/predefineColors/showThumbnails/activeTool 切换 | 留在主组件（轻度） |
| **导航** | currentPage/pageInput/prevPage/nextPage/goToPage/onScroll | 留在主组件（轻度） |
| **识别/MD/转换** | pdfMarkdown/currentMarkdown/saveMarkdown/handleConvert/openAiChat/sendChat | 暂留主组件（后续再拆） |

### 1.2 拆分比例

```
usePdfRenderer       约 30% 代码  → 拆 350 行
usePdfCollaborate    约 15% 代码  → 拆 180 行
usePdfAnnotation     约 35% 代码  → 拆 440 行
留在主组件           约 20% 代码  → 保留 280 行
```

### 1.3 任务清单

#### Day 1 上午：usePdfRenderer（4 小时）

```typescript
// miaotongdoc-web/src/composables/usePdfRenderer.ts

// 只定义接口和最小实现，不接业务
import { ref, shallowRef, onUnmounted } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

export interface PdfPageRenderResult {
  pageNumber: number
  viewport: any
  canvas: HTMLCanvasElement
}

export function usePdfRenderer(options: {
  fileUrl: string
  token?: string | null
}) {
  // 状态（先全部用 ref 占位）
  const pdfDoc = shallowRef<any>(null)
  const totalPages = ref(0)
  const scale = ref(1.2)
  const pageWidth = ref(0)
  const pageHeight = ref(0)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  // 方法（先空实现）
  async function load() { /* TODO */ }
  async function renderPage(pageNum: number) { /* TODO */ }
  async function renderAllThumbs() { /* TODO */ }
  async function fitWidth() { /* TODO */ }
  function zoomIn() { /* TODO */ }
  function zoomOut() { /* TODO */ }
  function destroy() { pdfDoc.value?.destroy() }

  onUnmounted(destroy)

  return {
    pdfDoc, totalPages, scale, pageWidth, pageHeight, loading, error,
    load, renderPage, renderAllThumbs, fitWidth, zoomIn, zoomOut, destroy,
  }
}
```

#### Day 1 下午：usePdfCollaborate（3 小时）

```typescript
// miaotongdoc-web/src/composables/usePdfCollaborate.ts

// 参考 MarkdownEditor.vue 的 YjsEntry 模式
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

export interface PdfAnnotation {
  id: string
  type: 'highlight' | 'comment' | 'draw'
  pageNumber: number
  rect?: { x: number; y: number; width: number; height: number }
  color: string
  content?: string
  points?: number[]
  userId: number
  userName: string
  createdAt: string
}

export interface UsePdfCollaborateOptions {
  docKey: string
  userId: number
  userName: string
}

export function usePdfCollaborate(options: UsePdfCollaborateOptions) {
  const ydoc = new Y.Doc()
  let provider: WebsocketProvider | null = null
  const yAnnotations = ydoc.getArray<PdfAnnotation>('annotations')

  function connect() { /* TODO */ }
  function disconnect() { /* TODO */ }

  return { ydoc, provider, yAnnotations, connect, disconnect }
}
```

#### Day 2 上午：usePdfAnnotation（4 小时）

```typescript
// miaotongdoc-web/src/composables/usePdfAnnotation.ts

import { ref } from 'vue'
import type { PdfAnnotation } from './usePdfCollaborate'

export type AnnotationTool = 'select' | 'highlight' | 'comment' | 'draw' | 'eraser'

export interface UsePdfAnnotationOptions {
  getYAnnotations: () => any
  userId: number
  userName: string
}

export function usePdfAnnotation(options: UsePdfAnnotationOptions) {
  const annotations = ref<PdfAnnotation[]>([])
  const activeTool = ref<AnnotationTool>('select')
  const activeColor = ref('#FFEB3B')

  function add(ann: Partial<PdfAnnotation>) { /* TODO */ }
  function update(id: string, ann: Partial<PdfAnnotation>) { /* TODO */ }
  function remove(id: string) { /* TODO */ }
  function getByPage(page: number) { /* TODO */ }

  return { annotations, activeTool, activeColor, add, update, remove, getByPage }
}
```

#### Day 2 下午：编译验证（2 小时）

```bash
# 1. 编译检查（必须通过）
npm run build

# 2. 类型检查
npm run type-check

# 3. 手动测试（重要！）
# 启动 npm run dev，打开 PDF 文件，确认：
# - 仍然能打开 PDF（旧功能不破坏）
# - 控制台无报错
# - 网络面板无 404
```

### 验收

- [ ] 3 个 composable 文件创建成功
- [ ] npm run build 通过
- [ ] 手动打开 PDF 仍然正常
- [ ] PdfEditor.vue 未修改（1254 行原样）

### 风险点

| 风险 | 缓解 |
|------|------|
| 命名冲突 | 用 usePdfRenderer / usePdfCollaborate / usePdfAnnotation 等具体名 |
| composable 被多个组件复用导致状态污染 | 每个组件调用 composable 时是独立实例（Vue 默认行为） |
| 类型不匹配 | 先看 PdfEditor.vue 的接口定义，保持类型一致 |

---

## Sprint 2：一个一个接业务并验证（2 天）

> **关键原则**：**每个 composable 单独接业务**，接一个验证一个，**不要一次全接**。

### 2.1 接业务顺序（按依赖关系）

```
usePdfRenderer        ← 独立，无依赖，先接
        ↓
usePdfCollaborate     ← 独立，无依赖，接
        ↓
usePdfAnnotation      ← 依赖 usePdfCollaborate 的 yAnnotations，最后接
```

### 2.2 任务清单

#### Day 3：接 usePdfRenderer（6 小时）

```
[ ] 1. 从 PdfEditor.vue 复制渲染相关代码
[ ] 2. 把 PdfEditor.vue 的渲染相关 ref 改为引用 composable
[ ] 3. 把 PdfEditor.vue 的渲染相关函数改为调用 composable
[ ] 4. 验证：手动打开 PDF，确认渲染正常
```

**具体改动点**（PdfEditor.vue）：

```typescript
// 改之前（1254 行）
const pdfDoc = shallowRef<any>(null)
const totalPages = ref(0)
const scale = ref(1.2)
// ... 30+ 个变量和函数

// 改之后
const renderer = usePdfRenderer({
  fileUrl: props.fileUrl,
  token: sessionStorage.getItem('token'),
})

const pdfDoc = renderer.pdfDoc
const totalPages = renderer.totalPages
// ... 等等
```

#### Day 4：接 usePdfCollaborate + usePdfAnnotation（6 小时）

```
[ ] 1. 协同部分移到 usePdfCollaborate
[ ] 2. 标注部分移到 usePdfAnnotation
[ ] 3. 验证：手动测试批注、画笔、橡皮擦
[ ] 4. 验证：双账号登录看协同
```

### 验收

- [ ] PdfEditor.vue 代码量降到 ~700 行
- [ ] 渲染、协同、标注三大功能全部正常
- [ ] 控制台无新增报错

### 风险点

| 风险 | 缓解 |
|------|------|
| 状态共享出错 | 用 `const { ... } = useXxx()` 解构时确保每个 ref 独立 |
| Yjs 协同被破坏 | 接完后**双账号测试** |
| 事件监听未清理 | composable 用 `onUnmounted` 自动清理 |

---

## Sprint 3：主组件清理（1 天）

### 任务清单

```
[ ] 1. 删除 PdfEditor.vue 中已经移到 composable 的代码
[ ] 2. 简化 template（用更清晰的命名）
[ ] 3. 添加注释说明每个 composable 的职责
[ ] 4. 最终验证
```

### 验收

- [ ] PdfEditor.vue 代码量降到 **400 行内**
- [ ] 代码可读性提升（template + script 各占一半）
- [ ] 所有功能正常
- [ ] npm run build 通过
- [ ] npm run type-check 通过

---

## Sprint 4：灰度开关 + 回滚（0.5 天）

> **关键**：即使只是 refactor，也要有 feature flag，万一出问题能立即回滚。

### 任务清单

```
[ ] 1. 在 mt_sys_config 加 pdf.feature.composable_refactor_enabled（默认 true）
[ ] 2. DocEditor.vue 根据 flag 决定用哪个 PdfEditor.vue
       flag=true  → 当前重构版
       flag=false → PdfEditor.vue.original（兜底）
[ ] 3. 测试切换：关闭 flag，确认旧版仍能工作
[ ] 4. 测试切换：开启 flag，确认新版正常工作
```

### 回滚 SOP

```bash
# 紧急回滚（30 秒）
mysql -u miaotong -p miaotongdocdb -e "
UPDATE mt_sys_config
SET config_value = 'false'
WHERE config_key = 'pdf.feature.composable_refactor_enabled';
"
# 用户下次刷新页面即看到旧版
```

---

## Sprint 5：PdfAiFloatPanel 接入（2-3 天）

> **这是新增功能**，composable 验证完后再做。

### 任务清单

```
[ ] 1. 创建 PdfAiFloatPanel.vue（豆包风格 UI 骨架）
[ ] 2. 实现 usePdfAiFloat.ts（包装 useAiChat，加 PDF 特有逻辑）
[ ] 3. 在 PdfEditor.vue 加 "✨ AI" 按钮
[ ] 4. 点击按钮显示 PdfAiFloatPanel
[ ] 5. 测试：基础问答功能
```

### 验收

- [ ] AI 浮窗可弹出
- [ ] 输入问题能收到回答
- [ ] UI 与 MD 编辑器视觉一致

---

## 最终交付物（Phase 1 完成后）

```
miaotongdoc-web/src/
├── components/
│   ├── PdfEditor.vue              ← 从 1254 行降到 400 行
│   ├── PdfEditor.vue.original     ← 备份，可随时回滚
│   └── PdfAiFloatPanel.vue        ← 新增（豆包风格 AI 浮窗）
│
└── composables/
    ├── useAiChat.ts                ← 已有，不动
    ├── usePdfRenderer.ts           ← 新增（pdfjs-dist 渲染）
    ├── usePdfCollaborate.ts        ← 新增（Yjs 协同）
    └── usePdfAnnotation.ts         ← 新增（标注管理）
```

**代码变化统计**：
- PdfEditor.vue：1254 → 400 行（-68%）
- 新增 composable：3 个，约 600 行
- 新增组件：1 个 PdfAiFloatPanel.vue，约 200 行
- **净增代码**：约 350 行（但模块化、可维护）

---

## 风险控制清单

| 风险 | 等级 | 缓解 |
|------|------|------|
| refactor 破坏现有功能 | 🟡 中 | Sprint 4 加灰度开关 + 备份原文件 |
| 类型推导失败 | 🟡 中 | Sprint 1 先建空壳，让 TS 编译通过 |
| Yjs 协同失效 | 🟡 中 | Sprint 2 双账号测试 |
| 事件监听泄漏 | 🟢 低 | composable 用 onUnmounted 自动清理 |
| 命名与未来冲突 | 🟢 低 | 用 usePdfRenderer 等具体名 |
| 工期延误 | 🟡 中 | Sprint 5 是新增功能，可推迟；Sprint 1-4 是 refactor 必须做完 |

---

## 与原方案的对比

| 项 | 原方案 | 修订方案 |
|----|--------|----------|
| 拆分粒度 | 一次建 5 个 composable | **先建 3 个核心骨架，验证后再补** |
| 接业务时机 | 一次接完 | **一个一个接，每个独立验证** |
| PdfAiFloatPanel | 与 composable 并行 | **composable 验证完后再做** |
| 回滚机制 | 未明确 | **Sprint 4 专门加灰度开关** |
| 时间 | 含糊 | **Sprint 0-4 = 6 天，Phase 1 完成** |

---

## 立即可执行的第一步（今天）

```
Sprint 0（0.5 天）
[ ] 1. cp PdfEditor.vue PdfEditor.vue.original
[ ] 2. npm run build 验证基线
[ ] 3. 启动 npm run dev，打开一个 PDF，确认能加载

⚠️ 不建分支，直接在 main 上做
```

**今天半小时搞定 Sprint 0**。明天开始 Sprint 1 建 composable 骨架。

---

## 进度跟踪

| Sprint | 状态 | 完成日期 |
|--------|------|----------|
| Sprint 0（准备） | ⏳ 待开始 | - |
| Sprint 1（骨架） | ⏳ 待开始 | - |
| Sprint 2（接业务） | ⏳ 待开始 | - |
| Sprint 3（清理） | ⏳ 待开始 | - |
| Sprint 4（灰度） | ⏳ 待开始 | - |
| Sprint 5（AI 浮窗） | ⏳ 待开始 | - |

如果你说"**开始 Sprint 0**"，我立刻执行：
1. 备份 PdfEditor.vue
2. 跑 build 验证基线