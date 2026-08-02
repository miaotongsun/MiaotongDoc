# PDF 编辑器 V3 — 6 项功能 UX 优化（5 PR 拆分方案）

> **日期**: 2026-08-02
> **范围**: 前端 + 后端 + E2E
> **复杂度**: 🔴 复杂（6 项用户反馈，~25 个问题点）
> **状态**: 🟡 规划中（待批准）
> **拆分策略**: 用户选择"5 个独立 PR"——按改动独立性和验证粒度切分

## Context

PDF 编辑器 V3 收到 6 项用户反馈，经 Phase 1 探索（2 个 Explore agent 并行报告）发现实际有 25+ 问题点，其中 2 个是**咬合的根因 bug**：

```
PositionStripper.writeString 签名错误       ─┐
                                           ├─→ textEdit 完全无字可选(功能 3)
PdfOcrTaskConsumer.saveOcrResult() 空实现  ┘
        └─→ OCR "成功"但数据从不落库(功能 2)
```

这两个 bug 是用户反馈"编辑模式无法选文字"和"AI OCR 经常失败"的真正根因。其他 ~23 个问题点是体验/UX 层面。

## 用户决策（已确认）

| 决策 | 选择 |
|---|---|
| 拆分粒度 | **5 个独立 PR**（每轮可独立验证/回滚） |
| 优先级 | P0 阻断 bug 必修 + P1 用户明显感知的功能 + 必要 P2 |
| 文档归属 | `plans/` 目录（CLAUDE.md §3 文档体系规范） |

## PR 拆分总览

| PR | 标题 | 文件数 | 改动行数 | 包含功能 | 验证 |
|---|---|---|---|---|---|
| **PR1** | `feat(pdf): 导出压缩级别下拉 + AI 按钮清理` | ~5 | ~150 | 功能 1 + 功能 4 | E2E: 压缩下拉 3 档 + AI tab 按钮数 |
| **PR2** | `fix(pdf): 编辑模式可选文字 + 文本保存入口` | ~4 | ~200 | **功能 3 根因** + 编辑 UX | E2E: textEdit 选字可编辑 + 保存可见 |
| **PR3** | `fix(pdf): OCR 落库 + 进度可见 + 单页识别` | ~6 | ~300 | **功能 2 根因** + OCR UX | E2E: OCR 完成后 markdown 有内容 + 右键单页 |
| **PR4** | `feat(pdf): AI 助手深度优化（右键/摘要/翻译/合同/目录）` | ~8 | ~500 | 功能 5（5 个失效项） | E2E: 各 AI 功能有响应 + 目录页码对齐 |
| **PR5** | `feat(pdf): 表单完整化（中文字形 + signature + 搜索 + 说明）` | ~5 | ~250 | 功能 6 | E2E: sample-form.pdf UI 填值 + 中文姓名 |

> **顺序原因**：PR2 + PR3 是同一根因链（writeString 错 + saveOcrResult 空），可并行开发但需**先 PR2** 让用户看到 textEdit 立刻生效，PR3 让 OCR 真产出。PR1 独立（UI 文案）。PR4 依赖 PR3（需 OCR 数据喂 LLM）。PR5 独立。

---

## PR1: 导出压缩级别下拉 + AI 按钮清理

### Context
用户原话："导出中的优化-压缩 PDF，名称改为压缩及导出，压缩级别要有下拉选择，按级别处理" / "AI 页签中的智能重写和纠错按钮去掉吧"。

### 改动文件
| 文件 | 改动 |
|---|---|
| `miaotongdoc-web/src/components/PdfExportMenu.vue` | "压缩 PDF" 按钮 → "压缩及导出"；`ElMessageBox.showInput` → `ElMessageBox` + `ElSelect`（低/中/高 三档，标签"低 72dpi / 中 150dpi / 高 200dpi"），统一 op = "压缩" |
| `miaotongdoc-web/src/components/PdfRibbon.vue` | Page Tab "压缩"按钮**删掉**（避免双入口行为分裂），让导出菜单统一收口；AI Tab 删 `<RibbonBtn label="智能重写">` 和 `<RibbonBtn label="纠错">` 两行；删 emit 声明里的 `'ai-rewrite'` / `'ai-proofread'` |
| `miaotongdoc-web/src/components/PdfEditor.vue` | 删 `@ai-rewrite="onAiRewrite"` `@ai-proofread="onAiProofread"`；删 `onAiRewrite()` `onAiProofread()` 两个函数（~20 行） |

### 不改
- 后端 `compress` 端点（已支持 level 参数）
- 后端 `/rewrite` 端点（保留，能力在但 UI 不暴露）
- PdfAiMenu / PdfCanvasContextMenu（已干净）

### 验证（G5）
- E2E `tests/pdf-export-compress-e2e.mjs`（新建）:
  - 导出菜单"压缩及导出"按钮渲染
  - 点按钮 → ElSelect 下拉显示三档
  - 选"中" → 调 `/compress?level=medium` → 下载 `compress_*_medium.pdf`
- 回归 `tests/phase14-e2e.mjs` 39/39
- E2E `tests/pdf-redact-e2e.mjs` 17/17（其他功能不被影响）

---

## PR2: 编辑模式可选文字 + 文本保存入口

### Context
用户原话："现在的编辑模式是什么逻辑，- 点击文字修改，现在根本没有文字可选，如何编辑"。

**根因（已确认）**：
1. `PdfToolService.PositionStripper.writeString(String, TextPosition)` 签名错误——PDFBox 3.0.3 只接受 `(String, List<TextPosition>)` 或 `(String)`，本类的覆写永远不被调用 → `positions` 永远为空 → textEdit 画布上没 token 元素可点
2. `PdfTextEditorLayer` 没空态 UI，加载失败和"无文字"表现一致
3. `onSave()` 当前是 `ElMessage.success('已保存(占位)')` 占位实现，无真正的保存入口

### 改动文件
| 文件 | 改动 |
|---|---|
| `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java` PositionStripper 内部类 | `writeString(String, TextPosition)` → `writeString(String text, List<TextPosition> positions)` 遍历 list 每个 TextPosition 产出；加 `@Override` 强制编译期校验 |
| `miaotongdoc-web/src/components/PdfTextEditorLayer.vue` | positions 为空时显示"未提取到可编辑文字,请先 OCR 识别 [OCR 全文识别]"一键按钮；区分 loading / empty / loaded 三态 |
| `miaotongdoc-web/src/components/PdfEditor.vue` | `onSave()` 改为：若 `textEditor.dirty.value` 则 `openSaveModeDialog()`（复用现有 PdfSaveModeDialog）；状态条显示 "N 处未保存" |
| `miaotongdoc-web/src/components/PdfRibbon.vue` | Edit Tab "保存"按钮在 `textEditor.dirty` 时显示小红点徽标 |

### 不改
- `applySingleEdit` / `applyTextEdits` 业务逻辑（已正确）
- `selectFont` 字体选择（PR3 跟 OCR fallback 一起改）

### 验证（G5）
- E2E `tests/pdf-edit-mode-e2e.mjs`（新建）:
  - 打开 `sample-single-page.pdf` → 切 textEdit → 断言至少 1 个 `.pdf-edit-token` DOM 元素
  - 点击 token → 出现 contenteditable → 输入新文字 → 断言 blur 后触发 `POST /text-edits`
  - 状态条出现 "1 处未保存"
  - 点保存按钮 → 弹 SaveModeDialog → 选覆盖模式 → 落盘
  - 重新打开 → 断言新文字显示
- 回归 phase14 39/39 + redact 17/17 + pdf-api-e2e 65/77（API 失败与本次无关）

---

## PR3: OCR 落库 + 进度可见 + 单页识别

### Context
用户原话："右键中的编辑中，OCR 快速识别和高精度识别默认只识别当前页面。AI 页签中的 OCR 可以识别全文，但是现在经常会失败，优化一下用户体验"。

**根因（已确认）**：
1. `PdfOcrTaskConsumer.saveOcrResult()` 是空函数（只打日志）—— PaddleOCR 完成后**结果从不落库** → 任务标 completed 但无数据（功能 2 失败的核心）
2. `estimateTotalPages()` 硬编码 `return 1` —— 进度条百分比失真
3. `@Transactional` + MQ 长任务 → SSE 读不到未提交的进度
4. 右键 OCR 不传 pageNum（menu emit 缺参数）→ 全文/单页无区分

### 改动文件
| 文件 | 改动 |
|---|---|
| `miaotongdoc-server/src/main/java/com/miaotong/doc/mq/PdfOcrTaskConsumer.java` | 实现 `saveOcrResult()`（参考 `doRecognizePdfbox` 调 `savePdfOcrData` + `markPdfRecognized`）；`estimateTotalPages()` 改用 PDF `getNumberOfPages()`；`@Transactional` 摘除或缩小到 `updateTaskStatus` 各方法 |
| `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/PdfController.java` | `recognizePaddle` 加可选 `@RequestParam Integer pageNum`（默认 null = 全文；非 null = 单页） |
| `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PaddleOcrClient.java` | 加 `pages: int[]` 可选参数，转发到 Python `/ocr/pdf?pages=N` |
| `miaotongdoc-web/src/components/PdfCanvasContextMenu.vue` | 右键菜单 OCR 按钮文案 "OCR 识别本页"；emit 补 `props.pageNum`；两个菜单（快速/高精度）都带 pageNum |
| `miaotongdoc-web/src/components/PdfEditor.vue` | `onCanvasMenuOcr(model, pageNum)` 转发给 `onOcrRecognize(model, pageNum)`；AI tab 的 OCR 按钮不传 pageNum（全文） |
| `miaotongdoc-web/src/composables/pdf/usePdfOcrProgress.ts` | 删除（死代码，全仓零引用），合并到 PdfEditor 内联 SSE 中 |
| `miaotongdoc-web/src/components/PdfEditor.vue` | 进度改单条常驻 `ElNotification`（非 toast 刷屏）；SSE 断流时显示"识别中断 [重试]"按钮；前端侧 60s 超时；失败 toast 加重试按钮 |

### 不改
- `recognize-paddle` 端点 URL 和参数风格（仅新增可选 pageNum）
- `usePdfOcrProgress` 之外的 OCR UI 组件

### 验证（G5）
- E2E `tests/pdf-ocr-e2e.mjs`（新建）:
  - 上传 `sample-scanned.pdf` → 点 AI tab "OCR 高精度识别" → 断言 SSE done 事件 `engine=server`
  - 完成后断言 `GET /markdown` 返回非空内容（**验证 saveOcrResult 修复**）
  - 状态条 OCR 标识变成 "已识别 · 100%"
  - 重开 PDF → 切 textEdit → 断言有 bbox 元素可点（**联动 PR2 修复**）
  - 右键菜单 "OCR 识别本页" → 断言请求带 `pageNum` 参数
- 回归 phase14 39/39 + redact 17/17

---

## PR4: AI 助手深度优化（5 个失效功能）

### Context
用户原话："AI 助手的功能要优化一下，现在很多功能都不能用，比如右键快速菜单，摘要当前页，翻译选区，全文摘要，合同条款、这些点了都没反应。智能目录输出目录页对应不起来"。

**根因**：所有 AI 按钮都把任务文本塞进 `aiFloat.chat.sendUserMessage()` 走通用 chat 流，**不走专用端点**，LLM 看不到 PDF 内容：
- 摘要 / 当前页摘要 → 没传 `docContent`，返回通泛答
- 翻译 → 走 chat 流，没用 `documentAiApi.translate(docId, {text, targetLang})`
- 合同条款 → 走 chat 流，没用 `usePdfExtractTerms.extract()`
- 智能目录 → `toggleRightPanel('outline')` 会关闭已开面板 + `setTimeout(300)` 跳页不稳 + 缺 DB 回退（PDFBox 序列化丢失 page 引用）

### 改动文件
| 文件 | 改动 |
|---|---|
| `miaotongdoc-web/src/components/PdfEditor.vue` | `onAiFullSummary` → 调 `documentAiApi.summarize(docId)`，结果弹 AI 浮窗；`onCanvasMenuAiSummarize` → 先 `await pdfApi.getPageText(docId, pageNum)`，把 `pageText.text` 作 `docContent` 传 chat.sendUserMessage；`onCanvasMenuAiTranslate` → 调 `documentAiApi.translate(docId, {text: sel, targetLang})` |
| `miaotongdoc-web/src/components/PdfEditor.vue` | `onAiExtractTerms` → 改 `termsPanelOpen.value = true` 打开抽屉（复用 PdfTermsPanel 自动抽）；同时调 `aiFloat.extractTerms.extract()` 触发 SSE |
| `miaotongdoc-web/src/components/PdfEditor.vue` | `onAiAutoOutline` 改造：`if (rightPanelOpen.value !== 'outline') toggleRightPanel('outline')`（强制开）；删多余 `pdfApi.getOutline`；`setTimeout(300)` → `await nextTick() + goToPage`；移除"自动跳首章节"（避免用户失去上下文） |
| `miaotongdoc-web/src/composables/pdf/usePdfAiFloat.ts` | 右键"AI 问答"进入空浮窗时，自动发一个引导 prompt（"我看到你打开了第 N 页 PDF，请问想了解什么？"） |
| `miaotongdoc-web/src/components/PdfAiFloatPanel.vue` | QUICK_QUESTIONS 扩充 4-6 个文档级快捷问 |
| `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java` | `extractOutline(id)` 加 DB `pageByTitle` 表回退（解决 PDFBox PDPageXYZDestination 序列化丢失 page 引用） |
| `miaotongdoc-web/src/composables/pdf/usePdfDocumentContext.ts` | **新建**：统一暴露 `getFullText()` / `getPageText(n)` / `getOutline()` / `getRecognizedMarkdown()`，所有 AI 入口先调此 hook |

### 不改
- AI 浮窗 chat 通用流（保留 chat.sendUserMessage 给"自由问答"用）
- 后端 AI 端点（`/summarize` `/translate` `/extract-terms/stream` `/auto-outline` 全部已存在）

### 验证（G5）
- E2E `tests/pdf-ai-e2e.mjs`（新建）:
  - AI tab "全文摘要" → 断言调 `/summarize` + AI 浮窗显示结果
  - 右键菜单 "AI 问答" → 浮窗出现 + 自动引导消息渲染
  - 右键 "摘要当前页" → 调 `/text?pageNum=N` 把内容塞 docContent
  - AI tab "合同条款" → 抽屉自动打开 + 触发 SSE + 字段表格渲染
  - AI tab "智能目录" → 右面板 outline tab 自动展开 + 列表项 pageNum 与内容匹配
- 回归 phase14 39/39 + redact 17/17

---

## PR5: PDF 表单完整化

### Context
用户原话："表单的功能，PDF 表单字段识别与填充。仅 AcroForm 交互式表单可识别。都不知道怎么使用，也不知道如何测试"。

**根因（已确认）**：
1. **真 bug**：中文字段填充报 400 `No glyph for U+674E (李) in font WenQuanYiMicroHei`（E2E L82 已实证）—— 用户试一次失败就放弃
2. 入口隐蔽（埋 Ribbon「编辑」tab），操作流没引导
3. `signature` 字段死路：后端 `setFieldValue` 走 else 抛异常
4. 缺 UI E2E 覆盖

### 改动文件
| 文件 | 改动 |
|---|---|
| `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java` | `setFieldValue()` 加 `PDSignatureField` 分支：调 `embedSignature(imageBase64, page, x, y, w, h)` 嵌入 widget 中心；AcroForm 中文字体回退用 `PdfFontUtil.getFontForText(pdf, text, false)`（替换 WenQuanYiMicroHei） |
| `miaotongdoc-web/src/components/PdfRightPanel.vue` | signature 字段 → "选择签名" 按钮 → 弹 `PdfSignatureDialog` → 嵌入；增加 3 步操作说明栏（输入 → 应用填充 → 已保存）；字段卡片加 "📍 点击在画布定位" hint；空态区分"非 AcroForm / 字段未加载 / 加载失败" 三种文案 |
| `miaotongdoc-web/src/components/PdfCanvas.vue` | 字段高亮加 CSS transition 400ms 渐隐 |
| `miaotongdoc-web/src/components/PdfRightPanel.vue` | 字段列表顶部加搜索框（按 name/partialName 关键字）；新增筛选 chip：必填/已填/未填 |
| `miaotongdoc-web/src/components/PdfEditor.vue` | ToolsRail 增加表单图标入口（与"页面"/"AI"/"工具"并列），提升发现性 |

### 不改
- 后端 `fillFormFields` 主流程
- `resolveFieldLocation`（多 widget 遍历作为后续 PR）

### 验证（G5）
- E2E `tests/pdf-form-ui-e2e.mjs`（新建）:
  - 上传 `sample-form.pdf` → 切到 Ribbon「表单」tab → 断言 5 个字段渲染
  - 填 "name" = "李四"（中文） → 点应用填充 → 断言 success（**验证中文字形修复**）
  - signature 字段 → 点"选择签名" → 弹签名对话框
  - 字段搜索框输入 "性别" → 断言只显示 1 个字段
- 回归 phase14 39/39 + redact 17/17 + edit-mode E2E

---

## 全局验证（每个 PR 完成后）

| 测试 | 命令 | 期望 |
|---|---|---|
| 单元 | `mvn test` | 全绿 |
| 前端构建 | `npm run build` | 0 错误 |
| E2E 总套 | `node tests/run.mjs`（含 phase14 + 各新增） | 全绿 |
| 手动 | 浏览器登录 → 编辑器 → 各 PR 涉及场景 | UX 通过 |

## CLAUDE.md 同步纪律

每个 PR 完成后按 §9.0 检查：
- ✅ 新增/修改 API 端点 → 同步「API 接口」表（PR3 加 pageNum 可选参数）
- ✅ 新增/修改 Vue 组件/页面 → 同步「代码结构」+「前端关键文件」表（PR2 加 PdfTextEditorLayer 空态，PR4 加 usePdfDocumentContext composable，PR5 加 PdfSignatureDialog 联动）
- ✅ 新增/修改 Flyway 迁移 → 无（本次纯代码层）
- ✅ plans/README.md 总看板 → 每个 PR commit 时追加状态变更

## 涉及文件清单（去重）

### 后端（仅 PR3 + PR5）
- `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java`（PR3 OCR 落库 + PR5 字体修复 + signature 分支）
- `miaotongdoc-server/src/main/java/com/miaotong/doc/mq/PdfOcrTaskConsumer.java`（PR3）
- `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/PdfController.java`（PR3 recognizePaddle pageNum）
- `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PaddleOcrClient.java`（PR3 pages 参数）

### 前端
- `miaotongdoc-web/src/components/PdfExportMenu.vue`（PR1）
- `miaotongdoc-web/src/components/PdfRibbon.vue`（PR1 + PR2）
- `miaotongdoc-web/src/components/PdfEditor.vue`（PR1 + PR2 + PR3 + PR4）
- `miaotongdoc-web/src/components/PdfCanvasContextMenu.vue`（PR3）
- `miaotongdoc-web/src/composables/pdf/usePdfOcrProgress.ts`（PR3 删）
- `miaotongdoc-web/src/components/PdfTextEditorLayer.vue`（PR2）
- `miaotongdoc-web/src/composables/pdf/usePdfAiFloat.ts`（PR4）
- `miaotongdoc-web/src/components/PdfAiFloatPanel.vue`（PR4）
- `miaotongdoc-web/src/components/PdfRightPanel.vue`（PR5）
- `miaotongdoc-web/src/components/PdfCanvas.vue`（PR5）
- `miaotongdoc-web/src/composables/pdf/usePdfDocumentContext.ts`（PR4 新建）

## 进度跟踪（PR 完成时填）

| PR | 状态 | 完成时间 | 关联 commit |
|---|---|---|---|
| PR1: 压缩下拉 + AI 清理 | ⏳ 规划中 | - | - |
| PR2: textEdit 根因修复 | ⏳ 规划中 | - | - |
| PR3: OCR 落库 + 单页 | ⏳ 规划中 | - | - |
| PR4: AI 助手深度优化 | ⏳ 规划中 | - | - |
| PR5: 表单完整化 | ⏳ 规划中 | - | - |

---

*计划文档完成。等待 ExitPlanMode 批准后，按 PR1 → PR5 顺序实施、验证、跟进进度。*