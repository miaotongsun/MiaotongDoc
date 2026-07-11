# MarkdownEditor.vue 4000+ 行修复计划与派发任务

> 目标：根据 `me_record.md` 和 `me_changes.md` 中 **7 月 6 日及以前** 的所有修改记录，
> 把当前 1905 行的 `MarkdownEditor.vue` 恢复到 **4000+ 行** 的工作代码版本。
> 冲突解决原则：**每个功能以最后一次（最新）修改为准**（按 `me_changes.md` 中时间排序）。
>
> 当前文件路径：`d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue`
> 当前行数：1905 行
> 目标行数：~4000+ 行（实际约 3800-4200 行）
>
> **重要约束（用户已明确要求）**：
> - 不要 `git add` 和 `git commit`（用户："这个版本太垃圾了，等你开发完再基线吧"）
> - 不要 `git checkout HEAD`
> - 不要 `git reset` / `restore`
> - 直接修改文件即可，部署走 `npm run build` + `docker compose restart nginx`

---

## 1. 修复总策略

### 1.1 维度划分

`me_changes.md` 共 18 个分类、6125 条事件，覆盖：

| # | 分类 | 事件数 | 7/6 之前最后一次更新 | 关键产物 |
|---|------|--------|---------------------|----------|
| 1 | 创建与基础脚手架 | 163 | 2026-07-06 | 编辑器+Tiptap+Yjs 基础 |
| 2 | 内容重复/标题注入修复 | 10 | 2026-07-08 | 修复 props.initialContent 重复 |
| 3 | Yjs 协同与保存 | 127 | 2026-07-06 | WebsocketProvider + 自动保存 |
| 4 | 斜杠输入 (Slash) | 198 | 2026-07-06 | `/` 触发菜单 + detectSlashTrigger |
| 5 | 大纲导航 (Outline) | 109 | 2026-07-06 | flashOutlineHeading + 淡蓝闪动画 |
| 6 | @提及 (Mention) | 49 | 2026-07-06 | 提及下拉 + 用户列表 |
| 7 | 图片上传/MinIO | 39 | 2026-07-04 | addImage + 上传逻辑 |
| 8 | AI 助手侧栏 (Q&A) | 558 | 2026-07-06 | 豆包风格侧栏 + 8 快捷卡 |
| 9 | AI 流式输出 (SSE) | 525 | 2026-07-06 | fetch+ReadableStream |
| 10 | AI 改写弹窗 (采用/拒绝) | 333 | **2026-07-06** | **Diff 模式 + 接受/拒绝** |
| 11 | 快捷命令 / (Slash) | 362 | 2026-07-06 | 完整 slash 菜单 |
| 12 | 标题预览 | 13 | 2026-07-08 | previewModal 弹窗 |
| 13 | Word 文档导入 (Mammoth) | 79 | 2026-07-06 | 导入保留表格 |
| 14 | 表格 UI 豆包风格 | 97 | **2026-07-06** | **injectTableIcons + 列拖拽** |
| 15 | Selection 浅紫 | 253 | 2026-07-06 | `::selection` 浅紫 |
| 16 | 权限模式 (只读/评论) | 88 | 2026-07-06 | canEdit 短路 |
| 17 | 其他工具栏/样式 | 86 | 2026-07-06 | 字体颜色 + 清除 |
| 18 | 布局/全屏 | 71 | 2026-07-07 | 全屏 + 侧边栏 |

### 1.2 关键时间窗口

- **基础期（6/15-6/28）**：建立骨架（编辑器+Yjs+工具栏+AI 侧栏+斜杠）
- **增强期（6/29-7/5）**：豆包风格改造（流式+思考链+Diff 预览+表格 UI）
- **收尾期（7/6 截止）**：所有功能最终定型
  - 7/6 12:21 ~ 16:49：**表格 UI 列拖拽 + 操作栏**（这是 7/6 最后一个大改）
  - 7/6 10:55：5 个核心问题一次性收尾（淡黄渐隐、消息边距、大纲淡蓝闪、Word 表格、选区浅紫）
  - 7/6 01:07：Diff 预览模式（采用 → 接受/拒绝）实现
- **废弃期（7/7+）**：`git checkout HEAD` 之后的工作 → **不要看这些**

### 1.3 冲突解决规则

**对每个功能，取 `me_changes.md` 中该分类下时间戳最大（最晚）但 `<= 2026-07-06 23:59:59` 的 `tool_use Edit/Write` 参数。**

按 `me_changes.md` 文件的章节定位：

```
- 表格 UI 豆包风格 → 跳到 36521 行前的 7/6 12:48:24（injectTableIcons 的 SVG 版本）
- AI 改写弹窗     → 跳到 36340 行前的 7/6 01:07:57（Diff 模式 ProseMirror tr 实现）
- AI 助手侧栏     → 跳到 25645 行前的 6/28 12:18:21（豆包风格侧栏重写）
- AI 流式         → 跳到 6/28 06:39:25（WebSocket → SSE 切换）
- 大纲导航        → 跳到 7/6 10:55:23 报告（用 doc.forEach 收集顶层 block）
- 浅紫选区        → 跳到 7/6 12:21:46（CSS `::selection` 浅紫）
- Word 导入       → 跳到 7/6 10:55:23 报告（mammoth 保留表格）
```

---

## 2. 现状对照（当前 1905 行 vs 目标 4000+ 行）

### 2.1 已有（保留不动）

- ✅ Tiptap 编辑器基础（StarterKit + Link + Underline + Highlight + TaskList + Table + Image + Subscript + Superscript）
- ✅ 工具栏（块选择 + B/I/U/S/code + 列表 + 任务 + 对齐 + 引用/代码块/表格/图片/链接/分割线/清除 + 撤销/重做）
- ✅ 协同头像（collabUsers + Yjs 头像）
- ✅ 大纲按钮（☰ 触发 showOutline）
- ✅ 斜杠命令按钮（点 / 弹菜单 + detectSlashTrigger 检测输入 /）
- ✅ 标题预览（👁 按钮 + previewModal）
- ✅ Word 导入（📥 按钮 + wordInputRef + mammoth）
- ✅ AI 助手按钮（✨ AI 切换 showAi）
- ✅ `editor = shallowRef<any>(undefined)` 类型修复
- ✅ `useAiChat, sanitizeAiMarkdown` 导入
- ✅ `RewriteModalState` interface + `rewriteModal` ref + `makeEmptyRewriteModal()`
- ✅ 自定义 AI 改写弹窗模板（三区 + 拖拽 + 状态标签 + 拒绝/重新生成/采用）
- ✅ `detectSlashTrigger` + `slashTriggerPos` + `startDragRewrite`（部分）

### 2.2 缺失（必须补回，参考 7/6 之前的最后实现）

| 缺失项 | 重要性 | 参考位置 |
|--------|--------|----------|
| **`acceptDiff` / `rejectDiff` / `setAiInsertHighlight` / `clearAiInsertHighlights`** | 关键 | 7/6 01:07:57 ProseMirror tr 实现 + `me_record.md` 第 28300+ 行 |
| **`injectTableIcons` (完整版含 SVG + col-plus + row-plus + openTableMenu/hideTableMenu)** | 关键 | 7/6 12:48:24 + 7/7 13:51:36（注意去重 7/7 修正） |
| **`@tiptap/extension-table` resizable 启动 + `.column-resize-handle` CSS** | 关键 | 7/6 10:47:37 |
| **Yjs 自动保存逻辑（`scheduleSave` + 防抖）** | 关键 | 6/16 ~ 7/6 多次迭代 |
| **`flashOutlineHeading` 完整版（`doc.forEach` + 顶层 block 收集）** | 关键 | 7/6 10:55:23 报告 |
| **大纲数据结构 + 大纲弹窗模板** | 关键 | 7/6 之前 |
| **`@提及` Mention 扩展 + 用户搜索下拉** | 中 | 6/19 ~ 7/6 |
| **`/快捷命令面板` 完整版（含 `runSlashCommand` 所有命令 + 上下箭头导航）** | 中 | 6/17 ~ 7/6 |
| **Word 导入完整版（`triggerImportWord` + mammoth 转 HTML + 保留表格）** | 中 | 7/6 10:55:23 报告 |
| **`onAiAction` 入口 + handleAiAction 重构** | 关键 | 7/6 之前最后一次 |
| **`getPlainTextLength` + 复杂 AI 操作** | 中 | 7/5 09:15:32 |
| **布局（全屏 toggle + 大纲侧边栏）** | 中 | 7/4 ~ 7/7 |
| **`fadeOutAiInsertHighlights` 1.2s 淡黄渐隐动画** | 关键 | 7/6 10:55:23 |
| **`.outline-flash` 1.5s 淡蓝闪烁动画** | 关键 | 7/6 10:55:23 |
| **AI 消息 `<p>` 段落化（双换行 → `</p><p>`）** | 中 | 7/6 10:55:23 |
| **CSS `::selection` 浅紫 + `mark[data-color="#fef3c7"]` 精准匹配** | 关键 | 7/6 12:21:46 |
| **`.msg-bubble { padding: 10px 14px; line-height: 1.6 }`** | 中 | 7/6 10:55:23 |
| **`canEdit` props + 权限短路** | 关键 | 7/6 之前 |
| **`<style global>` 块（ProseMirror 注入 DOM 的样式）** | 关键 | 7/6 12:21:46 文档说明 |

### 2.3 估算行数缺口

| 模块 | 当前 | 目标 | 缺口 |
|------|------|------|------|
| 模板（`<template>`） | ~400 | ~700 | +300 |
| `<script setup>` | ~1000 | ~2200 | +1200 |
| `<style scoped>` | ~250 | ~400 | +150 |
| `<style global>` | 0 | ~250 | +250 |
| 工具函数（startDrag, sanitize, etc.） | ~100 | ~250 | +150 |
| **合计** | **1905** | **~4000** | **+2100** |

---

## 3. 分阶段执行步骤

### 阶段 0：准备

```bash
cd d:\tiany\stycode\MiaotongDoc
# 备份当前文件
cp miaotongdoc-web/src/components/MarkdownEditor.vue \
   miaotongdoc-web/src/components/MarkdownEditor.vue.current-bak
```

### 阶段 1：阅读参考资料（按优先级）

**派发前先让 Agent 阅读的关键参考**：

1. `me_record.md` 28300-28400 行 — `acceptDiff` / `rejectDiff` / Diff State 完整实现
2. `me_record.md` 36500-36700 行 — `injectTableIcons` 完整版
3. `me_record.md` 2400-2600 行 — 工具栏 + 斜杠命令入口
4. `me_record.md` 18000-19000 行 — AI 改写弹窗（豆包风格）HTML
5. `me_record.md` 25000-26000 行 — 浅紫选区 + Diff 样式
6. `me_record.md` 19000-20000 行 — 提纲 + 闪动画
7. `me_record.md` 22000-23000 行 — Word 导入完整版
8. `me_record.md` 27000-27500 行 — AI 编辑模式多方案

### 阶段 2：按功能补全

**派发 6 个并行 Agent**，每个负责一个功能模块（互不重叠）：

| Agent # | 模块 | 行数预估 | 优先级 |
|---------|------|----------|--------|
| A | 表格 UI 豆包风格（`injectTableIcons` + 列拖拽 + 操作栏） | +500 | P0 |
| B | AI 改写/接受/拒绝（`acceptDiff` + `rejectDiff` + Diff State + 淡黄渐隐） | +450 | P0 |
| C | 大纲导航（`flashOutlineHeading` + outline panel + `.outline-flash` CSS） | +250 | P0 |
| D | AI 助手侧栏（豆包风格 + 8 快捷卡 + 流式 thinking/content） | +400 | P1 |
| E | Word 导入 + 斜杠命令 + @提及 | +300 | P1 |
| F | Yjs 自动保存 + 布局/全屏 + Selection 浅紫 + 各种样式 | +200 | P1 |

### 阶段 3：合并 + TS check

```bash
# 合并 6 个 Agent 的 patch（按文件追加 Edit）
# 用 sed 注入到当前 MarkdownEditor.vue 合适位置

# 类型检查
cd miaotongdoc-web
npx vue-tsc --noEmit 2>&1 | head -50
```

### 阶段 4：构建 + 部署

```bash
cd miaotongdoc-web
npm run build
cd ../MiaotongDoc-Docker
docker compose restart nginx
```

### 阶段 5：验证

1. 浏览器硬刷新（Ctrl+Shift+R）拿最新 JS
2. 测试 7/6 报告中的 5 个核心问题：
   - 选中文本浅紫（不是深蓝）
   - AI 改写 → 采用 → 接受 → 1.2s 淡黄渐隐
   - AI 消息边距舒适
   - 大纲点击 → 标题淡蓝闪
   - Word 导入保留表格
3. 测试表格 UI 豆包风格：hover 行/列出现操作栏 + 列宽拖拽

---

## 4. 派发给 Agent 的提示词模板

> **使用方式**：把下面 6 个提示词分别派发给 6 个 Agent（用 Task tool），
> 每个 Agent 只读 `me_record.md` + `me_changes.md` 对应章节，独立编辑 `MarkdownEditor.vue` 的相应位置。
> 最后手动合并（用 Edit 工具拼接各 Agent 输出）。

### Agent A：表格 UI 豆包风格

```text
你是一个 Vue 3 + Tiptap 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"表格 UI 豆包风格"完整实现，让最终文件达到 4000+ 行。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索关键字 `injectTableIcons`、`table-float-layer`、
  `column-resize-handle`、`injectTableActionBars`、`openTableMenu`、`tableMenuEl`、
  `列宽拖拽`、`.column-resize-handle`、`.table-action-bar`、`.table-icon-handle`、
  `.table-row-plus`、`.table-col-plus`
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：搜索同上

【关键时间点（取最后一次）】
- 2026-07-06 10:47:37：CSS `.column-resize-handle` 4px 拖拽手柄（me_changes.md 36390 行）
- 2026-07-06 10:55:23：列宽拖拽 + 8 个表格操作按钮报告（me_changes.md 36417 行）
- 2026-07-06 12:45:46：global 块补充样式（me_changes.md 36441 行）
- 2026-07-06 12:46:21：injectTableActionBars 完整版（me_changes.md 36452 行）
- 2026-07-06 12:48:24：injectTableIcons SVG 版本（me_changes.md 36486 行）
- 2026-07-07 13:51:36：openTableMenuByWrap 修复重复声明（me_changes.md 36550 行）
  — 注意 7/7 改的仅是去重，逻辑 7/6 已完整

【要补全的具体内容】

1. **Tiptap Table 扩展启动 resizable**：
   ```ts
   import { Table } from '@tiptap/extension-table'
   import { TableRow } from '@tiptap/extension-table-row'
   import { TableHeader } from '@tiptap/extension-table-header'
   import { TableCell } from '@tiptap/extension-table-cell'
   // 在 extensions 数组中加：
   Table.configure({ resizable: true, allowTableNodeSelection: true }),
   TableRow, TableHeader, TableCell,
   ```

2. **`injectTableIcons(rootEl: HTMLElement)` 完整版**（参考 me_record.md 第 36500-36700 行）：
   - 注入 SVG icon-handle（绝对定位 top:-32px）
   - 注入 row-plus（tr 末尾，hover tr 显示）
   - 注入 col-plus（th 右侧，hover th 显示）
   - 用 `__table_bar_idx` 标记防重复
   - 监听 hover 显示

3. **`openTableMenu(anchor)` / `hideTableMenu()`**（参考 me_changes.md 36550 行）：
   - `let tableMenuEl: HTMLElement | null = null`（只一处，不要重复）
   - 菜单项：addRowBefore/addRowAfter/addColumnBefore/addColumnAfter/mergeCells/splitCell/deleteRow/deleteColumn/deleteTable
   - 关闭时 `tableMenuEl.parentNode.removeChild`

4. **`updateTableActionBarVisibility(rootEl)`**（参考 me_changes.md 36497 行）：
   - 简化策略：hover 表格 OR selection 在表格内 → 显示
   - 同时只能一个 bar 显示

5. **CSS `<style global>` 块**（参考 me_changes.md 36441 行）：
   - `.ProseMirror .tableWrapper { position: relative; overflow-x: auto; }`
   - `.ProseMirror .column-resize-handle`：4-6px 宽、rgba(99,102,241,0.2-0.55)、hover 渐入
   - `.ProseMirror .tableWrapper:hover > .table-action-bar { opacity: 1 }`
   - `.table-action-btn`：颜色、hover 背景、danger 红色
   - `.table-icon-handle`、`.table-row-plus`、`.table-col-plus`：绝对定位 + hover 显示

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 编辑完成后报告新增了多少行、是否还有重复声明（用 grep 检查 `tableMenuEl` `injectTableActionBars` `openTableMenuByWrap` 等只能各出现一次）
- 报告要具体到行号（用 `grep -n` 输出）
```

### Agent B：AI 改写/接受/拒绝

```text
你是一个 Vue 3 + Tiptap + ProseMirror 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"AI 改写 → Diff 模式 → 接受/拒绝"完整实现。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索 `acceptDiff`、`rejectDiff`、`diffState`、
  `pendingDiff`、`setAiInsertHighlight`、`clearAiInsertHighlights`、`fadeOutAiInsertHighlights`、
  `淡黄`、`hardBreak`、`setMark('strike')`、`<result>`、`handleEditModeResult`、
  `handleAiAction`、`pickAiFloatOption`
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：同上 + 7/6 01:07:57（me_changes.md 36325 行）

【关键时间点（取最后一次）】

- 2026-07-05 11:36:42：aiFloatPanel 添加 `diffMode + pendingDiff + diffState`（me_changes.md 35909 行）
- 2026-07-06 01:05:58：Diff 预览模式（豆包风格 + 用户特殊需求）说明（me_changes.md 36302 行）
- 2026-07-06 01:07:57：**关键** — 用 ProseMirror tr 直接操作的最终实现（me_changes.md 36325 行）
- 2026-07-06 10:55:23：5 个核心问题收尾报告
  - "3 处 `setAiInsertHighlight(from, finalDocSize)` 改为 `from + 纯文本字符数`"
  - "选择器修复：`mark[data-color="#fef3c7"]` 精准匹配"
  - "`.think-body` 去掉 `max-height`，让外层 `.ai-messages` 自然滚动"
  - "AI 消息双换行 → `</p><p>`，单换行 → `<br>`"
  - "fadeOutAiInsertHighlights 1.2s 淡黄渐隐动画"

【要补全的具体内容】

1. **`acceptDiff()` 完整版**（参考 me_changes.md 36376 行 + me_record.md 第 28300+ 行）：
   - 用 ProseMirror descendants 遍历删除原文 + hardBreak
   - 接受后给新文加 highlight mark（淡黄 #fef3c7）→ 1.2s 后淡黄渐隐
   - 写入 `aiInsertHighlights` 数组 + 启动 `fadeOutAiInsertHighlights`
   - `aiFloatPanel.value.pendingDiff = false`
   - `aiFloatPanel.value.diffState = null`
   - `aiFloatPanel.value.visible = false`
   - `ElMessage.success('已采用新内容')`

2. **`rejectDiff()`**：
   - 删 hardBreak + 新文（用 ProseMirror tr）
   - 取消原文的 strike mark（tr.removeMark）
   - `aiFloatPanel.value.pendingDiff = false`
   - `aiFloatPanel.value.diffState = null`
   - `aiFloatPanel.value.visible = false`
   - `ElMessage.info('已恢复原文')`

3. **`setAiInsertHighlight(from, to)` / `clearAiInsertHighlights()` / `fadeOutAiInsertHighlights()`**：
   - 内部用 ProseMirror tr.addMark(from, to, highlightType.create({color: '#fef3c7'}))
   - 1.2s 后再 dispatch 一次 tr.removeMark(from, to, highlightType)
   - 关键是 `from + 纯文本字符数`（不是 `finalDocSize`）

4. **`handleEditModeResult(fullText, messageId)` 完整版**（参考 me_changes.md 35841 行）：
   - 清洗 LLM 输出：去 `<result>` 标签、去 `以下是` / `好的` 前缀、去 `**`、去 `##`、去 `> `、合并多换行
   - 检测多方案（`方案一：... 方案二：...`）→ `aiExtras.set(messageId, { hasOptions: true, options, originalRange })`
   - 单方案：直接插入到目标 range + 高亮

5. **`handleAiAction(command, selection)` 重构**（**最关键**）：
   - 改用 `useAiChat` 流式获取思考 + 文本
   - 调用 `rewriteModal.value = { ...makeEmptyRewriteModal(), visible: true, command, originalText }`
   - 不要再用 `ElMessageBox.confirm`
   - 把 `aiQuestion` 内容接到 `rewriteModal`
   - 流式期间 update `rewriteModal.thinking` 和 `rewriteModal.text`
   - 流结束 → `rewriteModal.status = 'done'`

6. **AI 消息段落化**：
   ```ts
   function aiTextToHtml(t: string) {
     return '<p>' + t.replace(/\n\n/g, '</p><p>').replace(/\n/g, '<br>') + '</p>'
   }
   ```

7. **CSS 渐隐动画**：
   ```css
   .ai-fade-out {
     animation: ai-fade-out 1.2s ease-out forwards !important;
   }
   @keyframes ai-fade-out {
     0% { background-color: rgba(254, 243, 199, 1); }
     100% { background-color: rgba(254, 243, 199, 0); }
   }
   ```

8. **CSS 精准选择器**：
   ```css
   mark[data-color="#fef3c7"] {
     background: #fef3c7 !important;
     color: #78350f !important;
     padding: 1px 2px;
     border-radius: 2px;
   }
   ```

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 用 `grep -n` 验证 `acceptDiff` / `rejectDiff` / `fadeOutAiInsertHighlights` 各只出现一次
- 报告要具体到行号
```

### Agent C：大纲导航 + 淡蓝闪动画

```text
你是一个 Vue 3 + Tiptap 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"大纲导航 + 淡蓝闪动画"完整实现。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索 `大纲`、`outline`、`flashOutlineHeading`、
  `scrollToHeading`、`outline-flash`、`淡蓝闪`、`outlineItems`、`showOutline`、
  `buildOutline`、`<style global>`、`@keyframes outline-flash-anim`
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：搜索同上 + 7/6 10:55:23 报告（me_changes.md 36415 行）

【关键时间点（取最后一次）】

- 2026-06-16 ~ 2026-07-04：基础大纲面板（浮动弹窗或侧栏）
- 2026-07-04 之后：基础功能定型
- 2026-07-06 10:55:23：**关键** — "用 `doc.forEach` 收集所有顶层 block DOM，通过 `domAtPos` + parent walk 找到 `.ProseMirror` 直接子级，给它们统一加 `outline-flash` class，1.5s 后清除"

【要补全的具体内容】

1. **`buildOutline()` 函数**：
   - 遍历 `editor.value.state.doc` 收集所有 `heading` 节点
   - 记录 `{ level, text, pos, id }` 列表
   - 用 `editor.value.state.doc.descendants((node, pos) => {...})`
   - 返回 `outlineItems.value = [...]`

2. **`scrollToHeading(item)` 函数**：
   - `editor.value.chain().focus().setTextSelection(item.pos + 1).scrollIntoView().run()`
   - 滚动到 heading 位置

3. **`flashOutlineHeading(item)` 函数**（**关键** — 7/6 最终版）：
   - 用 `editor.value.state.doc.descendants((node, pos) => { ... })` 收集**所有顶层 block DOM**
   - 找到当前 heading 所在段落（topLevel block）
   - 给该 block DOM 加 `.outline-flash` class
   - `setTimeout(() => dom.classList.remove('outline-flash'), 1500)`
   - **不要**给 heading 自己加 class —— 要给包含该 heading 的整段 block 加

4. **大纲侧栏模板**（替换/补全当前 `#if showOutline` 区块）：
   ```html
   <transition name="slide">
     <aside v-if="showOutline" class="outline-panel">
       <div class="outline-hdr">📑 大纲 <button @click="showOutline=false">×</button></div>
       <div class="outline-list">
         <div v-for="(item, i) in outlineItems" :key="i"
              :class="['outline-item', 'lvl-' + item.level]"
              @click="flashOutlineHeading(item)">
           {{ item.text }}
         </div>
       </div>
     </aside>
   </transition>
   ```

5. **CSS `<style global>` 块**（参考 me_changes.md 36430 行）：
   ```css
   .outline-flash {
     animation: outline-flash-anim 1.5s ease-out forwards !important;
   }
   @keyframes outline-flash-anim {
     0% { background-color: rgba(59, 130, 246, 0); }
     20% { background-color: rgba(59, 130, 246, 0.25); }
     100% { background-color: rgba(59, 130, 246, 0); }
   }
   ```

6. **`onUpdate` hook 中调用 `buildOutline()`**：
   - 在 `useEditor` 的 `onUpdate` 回调里调一次

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 报告要具体到行号
```

### Agent D：AI 助手侧栏（豆包风格 + 8 快捷卡 + 流式）

```text
你是一个 Vue 3 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"AI 助手侧栏（豆包风格）"完整实现。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索 `aiPanel`、`aiMessages`、`sendAiChat`、
  `ai-quick-cards`、`思考气泡`、`aiThinking`、`quickAiAsk`、`showAi`、`aiPanelWidth`、
  `startResize`、`aiQuestion`、`checkSlash`、`aiSlashCommands`、`豆包`、`userToggledThinking`
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：搜索同上 + 6/28 11:34:14 / 12:16:07 / 14:07:13

【关键时间点（取最后一次）】

- 2026-06-28 11:34:14：豆包风格重写（me_changes.md 25641 行）
- 2026-06-28 12:16:07：aiPanel 加 `aiPanelWidth` + 调整大小拖拽条（me_changes.md 35706 行）
- 2026-06-28 14:07:13：完整 aiPanel 模板 + sendChat 实现
- 2026-07-01 15:25:34：思考链 UI 重做报告（me_changes.md 35750 行）
  - "思考中：浅紫边框 + 蓝色阴影 + 文字呼吸"
  - "思考完成：💡灯泡 + '已思考 N 字' + 默认折叠"
  - "`userToggledThinking` 字段"
  - "正文第一个 chunk 来时立即折叠思考"

【要补全的具体内容】

1. **完整 aiPanel 模板**（参考 6/28 14:07:13 + 7/1 15:25:34）：
   - 头部：品牌图标 + "AI 助手" + 关闭按钮
   - 功能 tab：问答 / 摘要 / 翻译（3 个）
   - 问答区：
     - 空状态：欢迎语 + 8 个 ai-quick-cards（总结、续写、润色、解释、缩写、扩写、正式、轻松）
     - 消息列表：user 右对齐蓝底，assistant 左对齐白底
     - 每个 assistant 消息：thinking（可折叠）+ content + 操作按钮（复制/插入文档）
   - 输入框：底部固定 + placeholder + send 按钮（流式中变 stop）
   - 调整大小拖拽条（左侧边缘）

2. **`sendAiChat()` 完整版**（参考 7/5 09:15:06 + useAiChat 集成）：
   - 用 `useAiChat({ docId, endpoint: 'chat-stream' })`
   - 把当前消息 push 到 `aiMessages`
   - 把 editor.value?.getText() 传给后端作为 content
   - 流式接收 → `messages` ref 自动更新
   - 流结束 → scroll to bottom

3. **`thinking` 处理**（参考 7/1 15:25:34）：
   - 状态机：thinking 期间 → "正在思考..." + 旋转时钟图标
   - 正文第一个 chunk 来时（content 长度 > 0）→ 立即折叠 + 标题变 "已思考 N 字"
   - 用户点开后再不自动折叠（用 `userToggledThinking` 标记）
   - 1.2s 旋转动画 + `thinking-cursor` 打字光标

4. **aiSlashCommands 完整版**（参考 6/28 11:30:04）：
   ```ts
   const aiSlashCommands = [
     { id: 'summarize', icon: '📋', name: '总结摘要', desc: '提取文档核心内容' },
     { id: 'translate', icon: '🌐', name: '翻译', desc: '翻译成其他语言' },
     { id: 'rewrite', icon: '✏️', name: '改写', desc: '润色改进文字' },
     { id: 'expand', icon: '📝', name: '扩写', desc: '扩展补充内容' },
     { id: 'shorten', icon: '✂️', name: '缩写', desc: '精简压缩内容' },
     { id: 'formal', icon: '👔', name: '正式化', desc: '改为正式语气' },
   ]
   function checkSlash() { ... }
   function handleAiKeydown(e) { ... }  // 处理上下箭头 + Enter
   function execAiSlash(cmd) { ... }
   function quickAiAsk(q: string) { ... }
   ```

5. **CSS 样式**：
   ```css
   .ai-panel { width: 360px; background: #fafbfc; ... }
   .ai-quick-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
   .ai-quick-card { padding: 12px; background: #fff; border-radius: 8px; ... }
   .msg-bubble { padding: 10px 14px; line-height: 1.6; max-width: 90%; }
   .think-area { border: 1px solid rgba(167, 139, 250, 0.3); background: #faf5ff; ... }
   .think-area.thinking { animation: breathe 1.5s ease-in-out infinite; }
   @keyframes breathe { 0%,100% { box-shadow: 0 0 0 rgba(99,102,241,0); } 50% { box-shadow: 0 0 8px rgba(99,102,241,0.3); } }
   .thinking-cursor::after { content: '▍'; animation: blink 0.8s infinite; }
   @keyframes blink { 0%,50% { opacity: 1; } 50.01%,100% { opacity: 0; } }
   ```

6. **aiPanelWidth 拖拽**：
   ```ts
   const aiPanelWidth = ref(360)
   function startResize(e: MouseEvent) { ... }
   ```

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 报告要具体到行号
```

### Agent E：Word 导入 + 斜杠命令 + @提及

```text
你是一个 Vue 3 + Tiptap 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"Word 文档导入 + 完整斜杠命令 + @提及"实现。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索 `triggerImportWord`、`onWordFileSelected`、
  `wordInputRef`、`mammoth`、`importWord`、`docx`、`@tiptap/extension-mention`、
  `runSlashCommand`、`showSlashMenu`、`detectSlashTrigger`、
  `mention`、`@用户`、`用户列表`、`退格`
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：搜索同上

【关键时间点（取最后一次）】

- Word 导入：7/6 10:55:23 报告（me_changes.md 36418 行）"mammoth.js 转换 .docx 为 HTML，保留表格"
- 斜杠命令：7/6 之前完整（`runSlashCommand` 处理 h1/h2/h3/code/quote/ul/ol/task/table/hr/...）
- @提及：6/19 ~ 7/6 多次迭代

【要补全的具体内容】

1. **Word 导入完整版**（**最关键** — 7/6 报告提到"导入后文档格式丢失，表格没了"）：
   ```ts
   import mammoth from 'mammoth'
   async function onWordFileSelected(e: Event) {
     const file = (e.target as HTMLInputElement).files?.[0]
     if (!file) return
     try {
       const arrayBuffer = await file.arrayBuffer()
       const result = await mammoth.convertToHtml({ arrayBuffer })
       // 用 DOMPurify 消毒
       const clean = DOMPurify.sanitize(result.value, { ADD_TAGS: ['table','tr','td','th','tbody','thead'] })
       // 插入到当前光标位置
       editor.value?.chain().focus().insertContent(clean).run()
       ElMessage.success('Word 文档导入成功')
     } catch (err) {
       ElMessage.error('Word 导入失败：' + (err as Error).message)
     } finally {
       ;(e.target as HTMLInputElement).value = ''
     }
   }
   ```

2. **`runSlashCommand(cmd)` 完整版**（参考 me_changes.md 29507 行附近）：
   ```ts
   function runSlashCommand(cmd: string) {
     if (!editor.value) return
     const ch = editor.value.chain().focus()
     switch (cmd) {
       case 'h1': ch.toggleHeading({ level: 1 }).run(); break
       case 'h2': ch.toggleHeading({ level: 2 }).run(); break
       case 'h3': ch.toggleHeading({ level: 3 }).run(); break
       case 'code': ch.toggleCodeBlock().run(); break
       case 'quote': ch.toggleBlockquote().run(); break
       case 'ul': ch.toggleBulletList().run(); break
       case 'ol': ch.toggleOrderedList().run(); break
       case 'task': ch.toggleTaskList().run(); break
       case 'table': ch.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(); break
       case 'hr': ch.setHorizontalRule().run(); break
       case 'image': addImage(); break
       case 'link': setLink(); break
     }
     showSlashMenu.value = false
   }
   ```

3. **`detectSlashTrigger()` 完整版**（**关键** — 用户明确要求"在编辑页面输入时弹出"）：
   - 监听 `editor.on('update')` 或 `onSelectionUpdate`
   - 检查当前光标前一个字符是否是 `/`
   - 如果是 → 弹出 `showSlashMenu.value = true`
   - 退格删除 `/` → 关闭菜单
   - 同时按 / 触发命令

4. **@提及 Mention 扩展**（可选，参考 me_changes.md 8749 行）：
   - 用 `@tiptap/extension-mention` + `@tiptap/suggestion`
   - 监听 `/api/admin/users/search?q=` 获取用户列表
   - 显示姓名 + 工号
   - 选中 → 插入到文档

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 报告要具体到行号
```

### Agent F：Yjs 自动保存 + 布局/全屏 + Selection 浅紫 + 其他样式

```text
你是一个 Vue 3 + Tiptap 前端工程师。

任务：在 `d:\tiany\stycode\MiaotongDoc\miaotongdoc-web\src\components\MarkdownEditor.vue` 中
补全 7/6 之前的"Yjs 自动保存 + 布局/全屏 + Selection 浅紫 + 其他样式"实现。

【必读参考资料】
- `d:\tiany\stycode\MiaotongDoc\me_record.md`：搜索 `scheduleSave`、`自动保存`、
  `WebsocketProvider`、`Y.Doc`、`getXmlFragment`、`ydoc`、`onChange`、
  `全屏`、`fullscreen`、`侧边栏`、`.tiptap ::selection`、`A78BFA`、浅紫
- `d:\tiany\stycode\MiaotongDoc\me_changes.md`：搜索同上

【关键时间点（取最后一次）】

- Yjs：6/16 ~ 7/6 多次迭代
- 全屏：7/7 最后定型（me_changes.md 44667 行）— 用 `requestFullscreen()`
- 浅紫选区：7/6 12:21:46（me_changes.md 36430 行）— `.tiptap ::selection` 浅紫
- 全局样式：7/6 12:21:46 报告说"动态生成 DOM 的样式都在 <style> global 块单独声明"

【要补全的具体内容】

1. **Yjs 自动保存完整版**（参考 me_record.md 2421+ 行）：
   ```ts
   const yjsEntry = getYjsInstance(props.docKey)
   const { ydoc, provider } = yjsEntry
   const fragment = ydoc.getXmlFragment('content')

   // 初始内容（仅 Yjs 为空时写入）
   if (props.initialContent && fragment.length === 0) {
     fragment.insert(0, [ydoc.createText(props.initialContent)])
   }

   // 自动保存：editor.on('update') → 防抖 → POST /api/markdown/{id}/content
   let saveTimer: any = null
   function scheduleSave() {
     clearTimeout(saveTimer)
     saveTimer = setTimeout(async () => {
       const text = editor.value?.getText() || ''
       await fetch(`/api/markdown/${props.docId}/content`, {
         method: 'PUT',
         headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sessionStorage.getItem('token')}` },
         body: JSON.stringify({ content: text })
       })
     }, 2000)
   }
   ```

2. **`getYjsInstance(docKey)` 全局单例**（参考 me_changes.md 297 行）：
   ```ts
   const yjsMap = new Map<string, { ydoc: Y.Doc, provider: WebsocketProvider }>()
   function getYjsInstance(docKey: string) {
     if (yjsMap.has(docKey)) return yjsMap.get(docKey)!
     const ydoc = new Y.Doc()
     const provider = new WebsocketProvider(getWsUrl(), `md-${docKey}`, ydoc)
     const entry = { ydoc, provider }
     yjsMap.set(docKey, entry)
     return entry
   }
   ```

3. **全屏 toggle**（参考 me_changes.md 44667 行）：
   ```ts
   const isFullscreen = ref(false)
   async function toggleFullscreen() {
     if (!document.fullscreenElement) {
       await document.documentElement.requestFullscreen()
       isFullscreen.value = true
     } else {
       await document.exitFullscreen()
       isFullscreen.value = false
     }
   }
   ```

4. **Selection 浅紫**（**关键** — 7/6 12:21:46 + 7/6 10:55:23 报告）：
   ```css
   /* 放在 <style global> 块内 */
   .tiptap ::selection,
   .ProseMirror ::selection,
   .tiptap *::selection,
   .ProseMirror *::selection {
     background: rgba(167, 139, 250, 0.32) !important;
     color: inherit !important;
   }
   ```

5. **`<style global>` 块**（7/6 报告要求 — 7/6 12:21:46 之前就开始用）：
   ```html
   <style global>
   /* ProseMirror 注入的 <mark> <p> <table> 等节点样式 */
   .ProseMirror ::selection { background: rgba(167, 139, 250, 0.32) !important; }
   .ProseMirror mark[data-color="#fef3c7"] { background: #fef3c7 !important; color: #78350f !important; }
   /* 列拖拽手柄 */
   .ProseMirror .column-resize-handle { ... }
   /* 表格操作栏 */
   .ProseMirror .tableWrapper:hover > .table-action-bar { ... }
   /* outline-flash 动画 */
   .outline-flash { animation: outline-flash-anim 1.5s ease-out forwards !important; }
   @keyframes outline-flash-anim { ... }
   /* AI 渐隐 */
   .ai-fade-out { animation: ai-fade-out 1.2s ease-out forwards !important; }
   @keyframes ai-fade-out { ... }
   </style>
   ```

6. **`addImage()` + MinIO 上传**（参考 me_changes.md 9392+ 行）：
   ```ts
   function addImage() {
     const input = document.createElement('input')
     input.type = 'file'
     input.accept = 'image/*'
     input.onchange = async (e) => {
       const file = (e.target as HTMLInputElement).files?.[0]
       if (!file) return
       const fd = new FormData()
       fd.append('file', file)
       const resp = await fetch('/api/files/upload', {
         method: 'POST',
         headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` },
         body: fd
       })
       const json = await resp.json()
       editor.value?.chain().focus().setImage({ src: json.data.url }).run()
     }
     input.click()
   }
   ```

7. **`canEdit` props + 权限短路**（参考 me_changes.md 40710+ 行）：
   ```ts
   const props = defineProps<{
     docId: number | string
     docKey: string
     initialContent?: string
     canEdit?: boolean
   }>()
   // 工具栏按钮 click handler 包一层：
   function runEditorCmd(fn: () => boolean) {
     if (props.canEdit === false) {
       ElMessage.warning('只读模式，无法编辑')
       return
     }
     fn()
   }
   ```

【约束】
- 不要 `git commit` / `git add`
- 不要用 `git checkout` / `git reset` / `git restore`
- 不要删除任何已有功能，只追加
- 报告要具体到行号
```

---

## 5. 合并与验证

### 5.1 合并 6 个 Agent 的输出

```bash
# 1. 每个 Agent 编辑完，先备份
cp miaotongdoc-web/src/components/MarkdownEditor.vue \
   miaotongdoc-web/src/components/MarkdownEditor.vue.partial-bak

# 2. 合并方式（建议用 Edit 工具手工合并）
# 6 个 Agent 的修改互不重叠：
#   - A：表格 UI（CSS + 函数）
#   - B：AI 改写/接受/拒绝（CSS + 函数 + handleAiAction 重构）
#   - C：大纲（CSS + 函数 + 模板）
#   - D：AI 侧栏（CSS + 函数 + 模板）
#   - E：Word 导入 + 斜杠 + @提及
#   - F：Yjs + 全屏 + 浅紫 + 全局样式 + 权限
# 按 A → F 顺序追加（每个 Agent 输出已经定位到具体行号）

# 3. 验证行数
wc -l miaotongdoc-web/src/components/MarkdownEditor.vue
# 期望：3800-4200 行
```

### 5.2 类型检查

```bash
cd miaotongdoc-web
npx vue-tsc --noEmit 2>&1 | head -50
# 期望：无 error
```

### 5.3 构建

```bash
cd miaotongdoc-web
npm run build
# 期望：success，DocEditor-xxx.js 体积在 700-900KB 之间
```

### 5.4 部署

```bash
cd ../MiaotongDoc-Docker
docker compose restart nginx
```

### 5.5 浏览器验证清单

1. 硬刷新（Ctrl+Shift+R）拿最新 JS
2. 打开任意 Markdown 文档
3. **测试 1 — 选中文本浅紫**：选中一段文字 → 背景应为浅紫（不是深蓝）
4. **测试 2 — AI 改写 → Diff → 接受**：
   - 选中文字 → 点 ✨ AI → 改写
   - 浮窗出现"原文 / 思考 / 新文"三区
   - 点"采用" → 原文加删除线 + 新文插入
   - 浮窗切换"接受/拒绝"
   - 点"接受" → 原文删除 + 新文淡黄高亮 → 1.2s 后淡黄渐隐
5. **测试 3 — AI 改写 → 拒绝**：
   - 同样操作 → 点"拒绝" → 新文删除 + 原文 strike 取消
6. **测试 4 — 大纲淡蓝闪**：
   - 打开大纲侧栏（☰）
   - 点击任一 H2 标题
   - 编辑区对应位置**整个段落**淡蓝闪 1.5s
7. **测试 5 — Word 导入保留表格**：
   - 点 📥 按钮 → 选 .docx 文件（含表格）
   - 文档插入 → 表格保留
8. **测试 6 — 表格 UI 豆包风格**：
   - 插入表格（点 ⊞）
   - hover 表格左上角 → 出现"+"图标 → 点击 → 出现 8 个操作按钮
   - hover 列右侧边缘 → 列宽拖拽手柄渐入
9. **测试 7 — 斜杠命令**：
   - 在编辑区输入 `/` → 弹出命令菜单
   - 上下箭头选择 → Enter 触发
10. **测试 8 — AI 侧栏豆包风格**：
    - 点 ✨ AI 打开侧栏
    - 空状态显示 8 个 ai-quick-cards
    - 点任意 card → 自动填入问题 + 发送
    - 看到 thinking（浅紫边框）→ 折叠 → content 流式输出
    - 流结束自动滚到底
11. **测试 9 — 浅紫选区**：再测一次（与测试 1 一致）
12. **测试 10 — 消息边距**：AI 侧栏的消息气泡应距左右边至少 14px

### 5.6 失败回滚

```bash
# 任何步骤失败，回滚
cp miaotongdoc-web/src/components/MarkdownEditor.vue.current-bak \
   miaotongdoc-web/src/components/MarkdownEditor.vue
docker compose restart nginx
```

---

## 6. 关键代码片段速查（避免重新翻 `me_record.md`）

### 6.1 Diff State 数据结构

```ts
interface AiFloatPanel {
  visible: boolean
  command: AiFloatCommand
  title: string
  status: 'streaming' | 'done' | 'error' | 'aborted'
  text: string
  thinking: string
  thinkingDone: boolean
  thinkingStartedAt?: number
  thinkingEndedAt?: number
  thinkingDurationSec: number
  options: Array<{ title: string; content: string }>
  originalText: string
  originalRange: { from: number; to: number }
  insertAtCursor: boolean
  position: { top: number; left: number }
  placeAbove: boolean
  errorMsg?: string
  diffMode: boolean
  pendingDiff: boolean
  diffState: null | {
    originalText: string
    newText: string
    removedFrom: number
    removedTo: number
    addedStart: number
    addedEnd: number
    originalDocSize: number
  }
}
```

### 6.2 acceptDiff 关键代码

```ts
function acceptDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo } = aiFloatPanel.value.diffState

  // 1. 删原文（用 ProseMirror tr 直接操作）
  try {
    const tr = editor.value.state.tr.delete(removedFrom, removedTo)
    editor.value.view.dispatch(tr)
  } catch (e) { console.warn('[acceptDiff] delete failed', e) }

  // 2. 给新文加 highlight mark（淡黄）
  const newFrom = removedFrom
  const newTo = removedFrom + aiFloatPanel.value.diffState.newText.length
  setAiInsertHighlight(newFrom, newTo)

  // 3. 关闭浮窗
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.success('已采用新内容')
}
```

### 6.3 setAiInsertHighlight 关键代码

```ts
function setAiInsertHighlight(from: number, to: number) {
  if (!editor.value) return
  const { state } = editor.value
  const highlightType = state.schema.marks?.highlight
  if (!highlightType) return

  // 关键：精确范围 from + 纯文本字符数
  const docSize = state.doc.content.size
  const finalTo = Math.min(to, docSize)
  if (finalTo <= from) return

  // 加 mark
  const tr = state.tr.addMark(from, finalTo, highlightType.create({ color: '#fef3c7' }))
  editor.value.view.dispatch(tr)

  // 1.2s 后移除 mark
  setTimeout(() => {
    if (!editor.value) return
    const tr2 = editor.value.state.tr.removeMark(from, finalTo, highlightType)
    editor.value.view.dispatch(tr2)
  }, 1200)
}
```

### 6.4 flashOutlineHeading 关键代码

```ts
function flashOutlineHeading(item: { pos: number }) {
  if (!editor.value) return
  const ed = editor.value
  const pm = ed.view.dom as HTMLElement

  // 用 doc.forEach 找 item.pos 所在 topLevel block
  let topLevelDom: HTMLElement | null = null
  ed.state.doc.forEach((node, pos) => {
    if (topLevelDom) return false
    const nodeEnd = pos + node.nodeSize
    if (item.pos >= pos && item.pos <= nodeEnd) {
      const dom = ed.view.nodeDOM(pos) as HTMLElement
      if (dom) {
        // 找到 .ProseMirror 的直接子级
        let p: HTMLElement | null = dom
        while (p && p.parentElement !== pm) p = p.parentElement
        topLevelDom = p
      }
      return false
    }
    return true
  })

  if (topLevelDom) {
    topLevelDom.classList.add('outline-flash')
    setTimeout(() => topLevelDom!.classList.remove('outline-flash'), 1500)
  }

  // 同时滚动
  ed.chain().focus().setTextSelection(item.pos + 1).scrollIntoView().run()
}
```

### 6.5 injectTableIcons 关键代码（节选）

```ts
function injectTableIcons(rootEl: HTMLElement) {
  if (!editor.value || props.canEdit === false) return
  const wrappers = rootEl.querySelectorAll<HTMLElement>('.tableWrapper')

  wrappers.forEach((wrap, idx) => {
    if ((wrap as any).__tableIconsInstalled === idx) return
    ;(wrap as any).__tableIconsInstalled = idx
    ;(wrap as any).style.position = 'relative'

    // icon handle（左上角浮动按钮）
    const handle = document.createElement('button')
    handle.className = 'table-icon-handle'
    handle.innerHTML = '<svg viewBox="0 0 24 24" ...>...</svg>'
    handle.onmousedown = (e) => {
      e.preventDefault()
      e.stopPropagation()
      openTableMenu(handle)
    }
    wrap.appendChild(handle)

    // row-plus（tr 末尾）
    wrap.querySelectorAll('tr').forEach((tr) => {
      const rp = document.createElement('button')
      rp.className = 'table-row-plus'
      rp.onmousedown = (e) => {
        e.preventDefault()
        editor.value!.chain().focus().addRowAfter().run()
      }
      tr.appendChild(rp)
    })

    // col-plus（th 右侧）
    wrap.querySelectorAll('th').forEach((th) => {
      const cp = document.createElement('button')
      cp.className = 'table-col-plus'
      cp.onmousedown = (e) => {
        e.preventDefault()
        editor.value!.chain().focus().addColumnAfter().run()
      }
      th.appendChild(cp)
    })
  })
}
```

### 6.6 Selection 浅紫 CSS

```css
/* 放在 <style global> 块内 */
.tiptap ::selection,
.ProseMirror ::selection,
.tiptap *::selection,
.ProseMirror *::selection {
  background: rgba(167, 139, 250, 0.32) !important;
  color: inherit !important;
}
```

### 6.7 Word 导入（保留表格）

```ts
async function onWordFileSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const buf = await file.arrayBuffer()
    const result = await mammoth.convertToHtml({ arrayBuffer: buf })
    // 关键：mammoth 默认就把 <table> 转出来
    const clean = DOMPurify.sanitize(result.value, {
      ADD_TAGS: ['table', 'thead', 'tbody', 'tr', 'td', 'th'],
      ADD_ATTR: ['colspan', 'rowspan']
    })
    editor.value?.chain().focus().insertContent(clean).run()
    ElMessage.success('Word 文档导入成功')
  } catch (err) {
    ElMessage.error('Word 导入失败：' + (err as Error).message)
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}
```

---

## 7. 风险与回滚

| 风险 | 触发条件 | 回滚方案 |
|------|----------|----------|
| TypeScript 编译失败 | Agent 之间函数名/类型冲突 | 用 `npx vue-tsc --noEmit` 定位，对照 `me_record.md` 修正 |
| `tableMenuEl` 重复声明 | 7/6 + 7/7 都有声明 | 用 `grep -n "tableMenuEl"` 只保留一处 |
| 模板/JS 状态不匹配 | 模板引用了未声明的 ref | 浏览器 Console 报错 → grep ref 名补上 |
| AI 改写流程跑不通 | `useAiChat` 与 `aiFloatPanel` 状态错乱 | 重新阅读 7/5 09:15:06 + 7/6 01:07:57 |
| Yjs 协同冲突 | `getYjsInstance` 单例 Map 重复创建 | 用 `console.log` 检查 `yjsMap.size` |
| 部署后白屏 | 模板/JS 严重不匹配 | `docker compose restart nginx` + Ctrl+Shift+R；失败则 `cp .current-bak MarkdownEditor.vue` |

---

## 8. 验收标准

- [ ] `MarkdownEditor.vue` 行数 ≥ 3800
- [ ] `npx vue-tsc --noEmit` 无 error
- [ ] `npm run build` success
- [ ] 浏览器硬刷新后所有 7/6 之前的功能可见：
  - [ ] 工具栏（30+ 按钮）
  - [ ] 斜杠命令（输入 / 弹出）
  - [ ] 标题预览（👁 弹窗）
  - [ ] Word 导入（保留表格）
  - [ ] 表格 UI（hover 操作栏 + 列拖拽）
  - [ ] AI 助手侧栏（豆包风格 + 8 快捷卡 + 流式 thinking）
  - [ ] AI 改写 → Diff → 接受/拒绝
  - [ ] 淡黄高亮 + 1.2s 渐隐
  - [ ] 大纲淡蓝闪
  - [ ] 浅紫选区
  - [ ] Yjs 协同（多 tab 同步）
  - [ ] @提及
  - [ ] 全屏 toggle

---

# 🔴 重要补充：AI 浮窗 + 智能编辑 完整实现清单

> **重新深度搜索 `me_record.md` + `me_changes.md` 后发现，初版计划遗漏了多个关键 AI 功能。**
> 这些是 7/6 之前最后定型的 AI 浮窗 + 智能编辑代码，必须全部补回。

## 9. 关键发现：AI 系统分 4 大块

| 模块 | 关键对象 | 用途 | 7/6 之前最后一次更新 |
|------|----------|------|---------------------|
| **AI 浮窗（独立）** | `aiFloatPanel` + `openAiFloatPanel` + `acceptAiFloatResult` + `regenerateAiFloatResult` + `stopAiFloatResult` + `pickAiFloatOption` + `closeAiFloatPanel` | 选区上方显示 AI 输出，多方案选择，独立于侧栏 | 7/6 01:07:57 (Diff 模式) + 7/5 11:37:26 (接受/拒绝按钮) |
| **智能编辑（侧栏）** | `aiEditMode` + `aiEditTargetRange` + `smartEditOptions` + `selectAiOption` + `handleAiMessagesClick` + `applySmartEditResult` | 侧栏选区后输入指令 → 替换/插入 | 6/28 15:08:13 → 7/5 09:15:06 (system prompt 重构) |
| **AI 侧栏** | `aiMessages` + `sendAiChat` + `thinking` + `thinkingDone` + `userToggledThinking` + `inserted` + `aiPanelWidth` + `startResize` + `ai-quick-cards` + `handleAiKeydown` + `checkSlash` + `aiSlashCommands` | Q&A 8 个快捷卡 + 流式 thinking/content + 调整宽度 | 7/5 09:15:06 (system prompt 学习豆包) + 7/1 15:22:37 (思考自动折叠) |
| **AI 增强（高亮）** | `setAiInsertHighlight` + `clearAiInsertHighlights` + `fadeOutAiInsertHighlights` + `aiInsertHighlights` | AI 插入后淡黄高亮 1.2s | 7/6 10:55:23 (5 个核心问题收尾) + 7/5 13:24:41 (Decoration → mark fallback) |

## 10. 必须补回的具体函数（按重要性）

### 10.1 关键：AI 浮窗完整代码（参考 `me_record.md:109162-110400`）

```ts
// ============ AI 浮窗结果区（独立于侧栏 chat） ============
// 气泡菜单操作 / 智能编辑都通过这个浮窗展示 AI 输出，不污染侧栏聊天历史
// 参考 Cursor/Notion AI：选区上方实时 streaming 结果 + 接受/拒绝按钮
type AiFloatCommand = 'smart-edit' | 'rewrite' | 'expand' | 'shorten' |
  'translate-en' | 'translate-zh' | 'fix-grammar' | 'formal' | 'casual' | 'continue'

interface AiFloatPanel {
  visible: boolean
  command: AiFloatCommand
  title: string
  status: 'streaming' | 'done' | 'error' | 'aborted'
  text: string
  options: Array<{ title: string; content: string }>  // 多方案
  originalText: string
  originalRange: { from: number; to: number }
  insertAtCursor: boolean
  position: { top: number; left: number }
  errorMsg?: string
  // 7/5 11:36 之后新增字段
  thinking: string
  thinkingDone: boolean
  thinkingStartedAt?: number
  thinkingEndedAt?: number
  thinkingDurationSec: number
  placeAbove: boolean
  // 7/5 11:36:42 新增 Diff 模式
  diffMode: boolean
  pendingDiff: boolean
  diffState: null | {
    originalText: string
    newText: string
    removedFrom: number
    removedTo: number
    addedStart: number
    addedEnd: number
    originalDocSize: number
  }
}

const aiFloatPanel = ref<AiFloatPanel>({
  visible: false,
  command: 'rewrite',
  title: '',
  status: 'streaming',
  text: '',
  options: [],
  originalText: '',
  originalRange: { from: 0, to: 0 },
  insertAtCursor: false,
  position: { top: 0, left: 0 },
  thinking: '',
  thinkingDone: false,
  thinkingStartedAt: undefined,
  thinkingEndedAt: undefined,
  thinkingDurationSec: 0,
  placeAbove: true,
  diffMode: true,
  pendingDiff: false,
  diffState: null,
})

const AI_COMMAND_TITLES: Record<string, string> = {
  'smart-edit': '智能编辑',
  'rewrite': '改写',
  'expand': '扩写',
  'shorten': '缩写',
  'translate-en': '翻译为英文',
  'translate-zh': '翻译为中文',
  'fix-grammar': '修正语法',
  'formal': '改写为正式语气',
  'casual': '改写为随意语气',
  'continue': '续写',
}

function openAiFloatPanel(
  command: AiFloatCommand,
  range: { from: number; to: number },
  originalText: string,
  insertAtCursor = false
) {
  if (!editor.value) return
  const start = editor.value.view.coordsAtPos(range.from)
  const end = editor.value.view.coordsAtPos(range.to)
  aiFloatPanel.value = {
    visible: true,
    command,
    title: AI_COMMAND_TITLES[command] || 'AI 操作',
    status: 'streaming',
    text: '',
    options: [],
    originalText,
    originalRange: range,
    insertAtCursor,
    position: { top: Math.min(start.top, end.top), left: (start.left + end.left) / 2 },
    thinking: '',
    thinkingDone: false,
    thinkingStartedAt: Date.now(),
    thinkingEndedAt: undefined,
    thinkingDurationSec: 0,
    placeAbove: true,
    diffMode: true,  // 默认开启 diff 预览
    pendingDiff: false,
    diffState: null,
  }
}

function closeAiFloatPanel() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  // 关闭时：如果有 pending diff，默认拒绝
  if (aiFloatPanel.value.pendingDiff) {
    rejectDiff()
    return
  }
  aiFloatPanel.value.visible = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.pendingDiff = false
}

// 实时同步 editAi 流式文本到浮窗
watch(
  () => [
    editAi.status.value,
    editAi.messages.value[editAi.messages.value.length - 1]?.parts?.length,
  ],
  () => {
    const last = editAi.messages.value[editAi.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    const thinkingPart = (last.parts || []).find((p: any) => p.type === 'reasoning')
    if (thinkingPart) aiFloatPanel.value.thinking = thinkingPart.text || ''
    if (textPart) {
      aiFloatPanel.value.text = textPart.text || ''
      if (textPart.state === 'done' && !aiFloatPanel.value.thinkingDone) {
        aiFloatPanel.value.thinkingDone = true
        aiFloatPanel.value.thinkingEndedAt = Date.now()
        aiFloatPanel.value.thinkingDurationSec = Math.floor(
          (aiFloatPanel.value.thinkingEndedAt! - (aiFloatPanel.value.thinkingStartedAt || 0)) / 1000
        )
      }
    }
    if (textPart?.state === 'done') {
      aiFloatPanel.value.status = aiFloatPanel.value.status === 'aborted' ? 'aborted' : 'done'
    }
  },
  { flush: 'post' }
)

// "采用"按钮：替换选区文本或插入到光标
function acceptAiFloatResult() {
  if (!editor.value) return
  let cleanText = aiFloatPanel.value.text
  // 防御性：清除孤儿 </think>
  cleanText = cleanText
    .replace(/<\/think>/gi, '')
    .replace(/<\|thinking\|>/gi, '')
    .replace(/<\|reasoning\|>/gi, '')
    .replace(/<\|thought\|>/gi, '')
    .replace(/<think>/gi, '')
  // 提取 <result> 标签
  const resultMatch = cleanText.match(/<result>([\s\S]*?)<\/result>/i)
  if (resultMatch && resultMatch[1]) {
    cleanText = resultMatch[1].trim()
  } else {
    cleanText = cleanText
      .replace(/<\/?result>/gi, '')
      .replace(/^[\s\n]+/, '')
      .trim()
  }
  cleanText = cleanText
    .replace(/^(好的|以下是|根据|按照|修改后|编辑后|说明|解释|建议|方案|推荐)[：:,，\s]*/i, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/^>\s*/gm, '')
    .trim()

  if (!cleanText) {
    ElMessage.warning('AI 未生成可用内容')
    return
  }

  // 检测多方案（"方案一/方案二"）
  const optionMatches = cleanText.match(
    /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]\s*[\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]|$))/gi
  )
  if (optionMatches && optionMatches.length > 1) {
    const options = optionMatches.map((opt: string) => {
      const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]/)
      const title = titleMatch ? titleMatch[1] : '方案'
      const content = opt
        .replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]\s*/, '')
        .trim()
      return { title, content }
    })
    aiFloatPanel.value.options = options
    aiFloatPanel.value.text = ''  // 隐藏正文，让用户从 options 中选
    return
  }

  // ====== Diff 预览模式（豆包风格）======
  // 7/5 11:36 → 7/6 01:07 ProseMirror tr 直接操作（最终版）
  const { from, to } = aiFloatPanel.value.originalRange
  if (aiFloatPanel.value.diffMode && !aiFloatPanel.value.insertAtCursor && to > from) {
    const safe = sanitizeAiMarkdown(cleanText)
    const strikeType = editor.value.state.schema.marks?.strike
    const hardBreakType = editor.value.state.schema.nodes?.hardBreak
    if (!strikeType || !hardBreakType) {
      ElMessage.warning('diff 模式不支持（schema 缺失）')
      return
    }
    const docSizeBefore = editor.value.state.doc.content.size
    // 用 ProseMirror tr 直接操作，避免 chain() setTextSelection 副作用
    let tr = editor.value.state.tr
    tr = tr.addMark(from, to, strikeType.create())
    // 插入 hardBreak + 新内容
    tr = tr.insert(to, hardBreakType.create())
    const textNode = editor.value.state.schema.text(safe)
    tr = tr.insert(to + 1, textNode)
    editor.value.view.dispatch(tr)
    // 记录 diff 状态
    const docSizeAfter = editor.value.state.doc.content.size
    aiFloatPanel.value.diffState = {
      originalText: aiFloatPanel.value.originalText,
      newText: safe,
      removedFrom: from,
      removedTo: to,
      addedStart: to,
      addedEnd: docSizeAfter,
      originalDocSize: docSizeBefore,
    }
    aiFloatPanel.value.status = 'done'
    aiFloatPanel.value.pendingDiff = true
    ElMessage.info('Diff 预览已生成：原文已加删除线，新内容已插入')
    return
  }

  // 非 diff 模式：直接替换/插入
  const safe = sanitizeAiMarkdown(cleanText)
  if (aiFloatPanel.value.insertAtCursor) {
    const pos = editor.value.state.selection.from
    editor.value.chain().focus().insertContentAt(pos, safe).run()
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(pos, finalDocSize)
  } else {
    const { from, to } = aiFloatPanel.value.originalRange
    if (to > from) {
      editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
    } else {
      editor.value.chain().focus().insertContentAt(from, safe).run()
    }
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(from, finalDocSize)
  }
  ElMessage.success('已采用')
  aiFloatPanel.value.visible = false
}

// "重新生成"按钮：复用浮窗当前位置，避免浮窗跳回
function regenerateAiFloatResult() {
  if (!editor.value || aiFloatPanel.value.status === 'streaming') return
  // 重置浮窗状态
  aiFloatPanel.value.text = ''
  aiFloatPanel.value.thinking = ''
  aiFloatPanel.value.thinkingDone = false
  aiFloatPanel.value.thinkingStartedAt = Date.now()
  aiFloatPanel.value.options = []
  aiFloatPanel.value.status = 'streaming'
  // 重新调 runEditAi
  runEditAi(
    aiFloatPanel.value.command,
    aiFloatPanel.value.originalRange,
    aiFloatPanel.value.originalText,
    aiFloatPanel.value.insertAtCursor
  )
}

function stopAiFloatResult() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  aiFloatPanel.value.status = 'aborted'
}

function pickAiFloatOption(idx: number) {
  const opt = aiFloatPanel.value.options[idx]
  if (!opt || !editor.value) return
  const safe = sanitizeAiMarkdown(opt.content)
  const { from, to } = aiFloatPanel.value.originalRange
  if (to > from) {
    editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
  } else {
    editor.value.chain().focus().insertContentAt(from, safe).run()
  }
  const finalDocSize = editor.value.state.doc.content.size
  setAiInsertHighlight(from, finalDocSize)
  ElMessage.success('已采用方案')
  aiFloatPanel.value.visible = false
}

// Diff 模式：接受（删原文 + 保留新文 + 淡黄高亮 1.2s）
function acceptDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo } = aiFloatPanel.value.diffState
  // 1. 删原文（带 strike mark）
  try {
    const tr = editor.value.state.tr.delete(removedFrom, removedTo)
    editor.value.view.dispatch(tr)
  } catch (e) {
    console.warn('[acceptDiff] delete original failed', e)
  }
  // 2. 给新文加 highlight mark（淡黄）→ 1.2s 后移除
  const newFrom = removedFrom
  const newTo = removedFrom + aiFloatPanel.value.diffState.newText.length
  setAiInsertHighlight(newFrom, newTo)
  // 3. 关闭浮窗
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.success('已采用新内容')
}

// Diff 模式：拒绝（删新文 + 取消 strike）
function rejectDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo, addedStart, addedEnd } = aiFloatPanel.value.diffState
  // 1. 取消原文的 strike mark
  try {
    const strikeType = editor.value.state.schema.marks?.strike
    if (strikeType) {
      const tr = editor.value.state.tr.removeMark(removedFrom, removedTo, strikeType)
      editor.value.view.dispatch(tr)
    }
  } catch (e) {
    console.warn('[rejectDiff] remove strike failed', e)
  }
  // 2. 删新文（含 hardBreak + newText）
  try {
    const tr = editor.value.state.tr.delete(addedStart, addedEnd)
    editor.value.view.dispatch(tr)
  } catch (e) {
    console.warn('[rejectDiff] delete new failed', e)
  }
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.info('已恢复原文')
}
```

### 10.2 关键：`runEditAi` 统一智能编辑（参考 `me_record.md:271000+` + `me_changes.md:27018`）

```ts
/**
 * 统一的 editAi 触发函数（参考 Cursor 浮窗思路）
 * - 打开浮窗
 * - 调 editAi 流式生成
 * - 浮窗 watch 自动同步流式文本
 * - 用户在浮窗内"接受"才真正写入编辑器
 */
async function runEditAi(
  command: AiFloatCommand,
  range: { from: number; to: number },
  sourceText: string,
  insertAtCursor: boolean,
  instruction?: string,
) {
  if (!editor.value) return
  // 先停掉可能还在跑的流
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
    await nextTick()
  }
  // 打开浮窗
  openAiFloatPanel(command, range, sourceText, insertAtCursor)

  // 构造 systemPrompt
  let systemPrompt = ''
  let question = sourceText
  switch (command) {
    case 'smart-edit':
      systemPrompt = `你是文档编辑助手，严格按照用户指令修改文本。

【任务】根据用户指令，修改【原文】中的内容。

【原文】（只修改这部分，不要管其他内容）
${sourceText}

【指令】
${instruction || ''}

【输出规则 - 严格遵守】
1. 只输出修改后的内容，不要输出任何解释
2. 不要输出"好的"、"以下是"、"修改后"等前缀
3. 不要输出任何说明或备注
4. 不要输出多个版本

【输出格式】
<result>修改后的纯文本内容</result>

【示例】
原文：这是一个非常大的项目
指令：让它更简洁
输出：<result>这是一个大项目</result>

【错误输出示例】
<result>以下是简化版：这是一个大项目</result>
<result>修改后：这是一个大项目</result>
<result>**这是一个大项目**</result>`
      question = instruction || sourceText
      break
    case 'rewrite':
      systemPrompt = '请改写以下文本，保持原意但使用不同的表达方式。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'expand':
      systemPrompt = '请扩写以下文本，增加更多细节和描述。只输出扩写后的内容，不要添加任何解释或前缀。'
      break
    case 'shorten':
      systemPrompt = '请缩写以下文本，保留核心信息，去除冗余。只输出缩写后的内容，不要添加任何解释或前缀。'
      break
    case 'translate-en':
      systemPrompt = '请将以下文本翻译为 English。只输出翻译结果，不要添加任何解释或前缀。'
      break
    case 'translate-zh':
      systemPrompt = '请将以下文本翻译为中文。只输出翻译结果，不要添加任何解释或前缀。'
      break
    case 'fix-grammar':
      systemPrompt = '请修正以下文本的语法错误，保持原意。只输出修正后的内容，不要添加任何解释或前缀。'
      break
    case 'formal':
      systemPrompt = '请将以下文本改写为更正式的语气。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'casual':
      systemPrompt = '请将以下文本改写为更随意的语气。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'continue':
      systemPrompt = '请基于以下文本继续续写。只输出续写内容，不要添加任何解释或前缀。'
      question = '请续写：' + sourceText
      break
  }

  // 调用 editAi 流式生成
  await editAi.sendUserMessage(question, systemPrompt, editor.value.getText())
}

/** 智能编辑入口（侧栏） */
async function handleSmartEdit() {
  if (!editor.value) return
  // 用 aiEditTargetRange 不用当前 selection（用户在 AI 面板输入时 selection 已丢失）
  const target = aiEditTargetRange.value
  const from = target?.from ?? editor.value.state.selection.from
  const to = target?.to ?? editor.value.state.selection.to
  const sourceText = target?.text || editor.value.state.doc.textBetween(from, to)
  if (!sourceText) {
    ElMessage.warning('请先在文档中选中文本')
    return
  }
  const result = await ElMessageBox.prompt(
    '告诉 AI 如何修改这段文字',
    '✨ 智能编辑',
    {
      inputPlaceholder: '如：让这段更简洁、添加更多细节、使语言更生动...',
      confirmButtonText: '开始编辑',
      cancelButtonText: '取消',
    }
  ).catch(() => null)
  if (!result || !result.value) return
  // 用 runEditAi 统一处理（弹浮窗 + 流式）
  await runEditAi('smart-edit', { from, to }, sourceText, false, result.value)
}
```

### 10.3 关键：AI 侧栏多方案选择（参考 `me_record.md:14030-14100`）

```ts
const smartEditOptions = ref<Array<{ title: string; content: string }>>([])
const smartEditOriginalPos = ref<{ from: number; to: number }>({ from: 0, to: 0 })

/** AI 编辑模式：检测多方案 + 显示方案卡片 */
function applySmartEditResult(content: string, insertPos: number, originalText: string) {
  const hasContent = content.length >= 5
  const isNotJustIntro = !/^(好的|以下是|根据|按照|修改后|编辑后)[：:。\s]/i.test(content)
  if (content && hasContent && isNotJustIntro) {
    // 检测多方案
    const optionMatches = content.match(
      /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+)\s*[:：][\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+)\s*[:：]|$))/gi
    )
    if (optionMatches && optionMatches.length > 1) {
      const options = optionMatches.map((opt: string) => {
        const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]/)
        const title = titleMatch ? titleMatch[1] : '方案'
        const text = opt.replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]\s*/, '').trim()
        return { title, content: text }
      })
      smartEditOptions.value = options
      smartEditOriginalPos.value = { from: insertPos, to: insertPos + originalText.length }
      ElMessage.info('AI 生成了多个方案，点击选择')
      return
    }
    // 单方案：直接替换
    editor.value?.chain().focus()
      .deleteRange({ from: insertPos, to: insertPos + originalText.length })
      .insertContentAt(insertPos, content).run()
    setAiInsertHighlight(insertPos, editor.value.state.doc.content.size)
    ElMessage.success('智能编辑完成')
  } else if (content && hasContent) {
    // 移除"好的"/"以下是"等前缀
    let clean = content
      .replace(/^(好的|以下是|根据|按照|修改后|编辑后)[：:。\s]*/i, '')
      .trim()
    if (clean) {
      editor.value?.chain().focus()
        .deleteRange({ from: insertPos, to: insertPos + originalText.length })
        .insertContentAt(insertPos, clean).run()
      setAiInsertHighlight(insertPos, editor.value.state.doc.content.size)
    }
  }
}

function selectSmartOption(index: number) {
  const option = smartEditOptions.value[index]
  if (!option || !editor.value) return
  const { from, to } = smartEditOriginalPos.value
  editor.value.chain().focus()
    .deleteRange({ from, to })
    .insertContentAt(from, option.content).run()
  setAiInsertHighlight(from, editor.value.state.doc.content.size)
  smartEditOptions.value = []
  ElMessage.success('已采用方案')
}

function cancelSmartOption() {
  smartEditOptions.value = []
}
```

### 10.4 关键：AI 侧栏消息 + 思考链 UI（参考 `me_record.md:13100-14150`）

```ts
const aiEditMode = ref(false)
const aiEditTargetRange = ref<{ from: number; to: number; text: string } | null>(null)

const aiMessages = ref<Array<{
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  thinkingExpanded?: boolean
  thinkingDone?: boolean
  /** 用户是否手动切换过 thinking 面板（避免自动折叠覆盖用户意图） */
  userToggledThinking?: boolean
  inserted?: boolean
  hasOptions?: boolean
  options?: Array<{ title: string; content: string }>
  originalRange?: { from: number; to: number }
  status?: 'streaming' | 'done' | 'aborted' | 'error'
}>>([])

const aiInputFocused = ref(false)
const aiPanelWidth = ref(400)
const isResizing = ref(false)

// AI 面板拖拽调整宽度
function startResize(e: MouseEvent) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = aiPanelWidth.value

  const onMouseMove = (e: MouseEvent) => {
    const diff = startX - e.clientX
    const newWidth = Math.max(280, Math.min(600, startWidth + diff))
    aiPanelWidth.value = newWidth
  }
  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function handleAiMessagesClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const optionItem = target.closest('.ai-option-item')
  if (optionItem) {
    const msgElement = optionItem.closest('.ai-msg')
    if (msgElement) {
      const msgIndex = Array.from(msgElement.parentElement?.children || []).indexOf(msgElement)
      const optionIndex = Array.from(optionItem.parentElement?.children || []).indexOf(optionItem)
      if (msgIndex >= 0 && optionIndex >= 0) {
        selectAiOption(msgIndex, optionIndex)
      }
    }
  }
}

function selectAiOption(msgIndex: number, optionIndex: number) {
  const msg = aiMessages.value[msgIndex]
  if (msg && msg.options && msg.options[optionIndex] && editor.value) {
    const option = msg.options[optionIndex]
    if (msg.originalRange && msg.originalRange.to > msg.originalRange.from) {
      editor.value.chain().focus()
        .deleteRange(msg.originalRange)
        .insertContentAt(msg.originalRange.from, option.content)
        .run()
    } else {
      editor.value.chain().focus().insertContent(option.content).run()
    }
    msg.inserted = true
    msg.hasOptions = false
    ElMessage.success('已采用方案')
  }
}

function insertToEditor(content: string, msgIndex?: number) {
  if (editor.value) {
    editor.value.chain().focus().insertContent(content).run()
    if (msgIndex !== undefined) {
      aiMessages.value[msgIndex].inserted = true
    }
    ElMessage.success('已插入到文档')
  }
}

function copyAiMsg(content: string) {
  navigator.clipboard.writeText(content)
  ElMessage.success('已复制')
}

function toggleThinking(m: any) {
  m.thinkingExpanded = !m.thinkingExpanded
  m.userToggledThinking = true  // 标记用户手动切换，自动折叠不再生效
}

// AI 输入框自动调整高度
function autoResizeAiInput() {
  if (aiInputRef.value) {
    aiInputRef.value.style.height = 'auto'
    aiInputRef.value.style.height = Math.min(aiInputRef.value.scrollHeight, 120) + 'px'
  }
}

function handleAiKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendAiChat()
  }
}

function quickAiAsk(q: string) {
  aiQuestion.value = q
  sendAiChat()
}

function renderAiMarkdown(text: string): string {
  if (!text) return ''
  // 段落分隔：双换行 → </p><p>，单换行 → <br>
  const safe = sanitizeHtml(text)
  return '<p>' + safe
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>') + '</p>'
}
```

### 10.5 关键：`setAiInsertHighlight` + `clearAiInsertHighlights` + `fadeOutAiInsertHighlights`（参考 `me_record.md:144290-144320` + `me_changes.md:28077`）

```ts
const aiInsertHighlights = ref<Array<{ from: number; to: number }>>([])
let aiInsertClickHandlerInstalled = false
let aiInsertClickHandler: ((e: MouseEvent) => void) | null = null

/**
 * AI 插入内容高亮（用 highlight mark 实现，淡黄背景）
 * - 插入后给新内容加 highlight mark（淡黄背景 #fef3c7）
 * - 1.2s 后自动移除 mark（淡黄渐隐）
 * - 多次采用可以各自独立高亮（数组保存）
 */
function setAiInsertHighlight(from: number, to: number) {
  if (!editor.value) return
  const { state } = editor.value
  const highlightType = state.schema.marks?.highlight
  if (!highlightType) return

  // 关键：精确范围 from + 纯文本字符数（不是 finalDocSize！）
  const docSize = state.doc.content.size
  const finalTo = Math.min(to, docSize)
  if (finalTo <= from) return

  // 加 mark
  const tr = state.tr.addMark(from, finalTo, highlightType.create({ color: '#fef3c7' }))
  editor.value.view.dispatch(tr)

  // 1.2s 后移除 mark
  setTimeout(() => {
    if (!editor.value) return
    const tr2 = editor.value.state.tr.removeMark(from, finalTo, highlightType)
    editor.value.view.dispatch(tr2)
  }, 1200)

  // 安装全局 mousedown 监听器（启动一次）
  if (!aiInsertClickHandlerInstalled) {
    aiInsertClickHandlerInstalled = true
    aiInsertClickHandler = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      const inEditor = target.closest('.tiptap')
                    || target.closest('.ProseMirror')
                    || target.closest('.md-editor-content')
      if (inEditor) {
        // 点击编辑器内 → 立即清除所有高亮
        clearAiInsertHighlights()
      }
    }
    // 1 秒后挂载（避免当前 click 立即触发）
    setTimeout(() => {
      if (aiInsertClickHandler) {
        document.addEventListener('mousedown', aiInsertClickHandler, true)
      }
    }, 100)
  }
}

function clearAiInsertHighlights() {
  if (!editor.value) return
  const { state } = editor.value
  const highlightType = state.schema.marks?.highlight
  if (!highlightType) return
  // 清除所有 ai-insert 高亮（精确匹配 color: #fef3c7）
  const tr = state.tr
  state.doc.descendants((node: any, pos: number) => {
    if (node.isText) {
      const mark = node.marks.find((m: any) => m.type.name === 'highlight' && m.attrs.color === '#fef3c7')
      if (mark) {
        tr.removeMark(pos, pos + node.nodeSize, mark.type)
      }
    }
    return true
  })
  if (tr.docChanged) {
    editor.value.view.dispatch(tr)
  }
}

function highlightAiChanges(from: number, to: number) {
  setAiInsertHighlight(from, to)
}
```

### 10.6 关键：3 个独立 useAiChat 实例（参考 `me_record.md:142470, 142549, 156491`）

```ts
// ============ 3 个独立的 AI 流式会话 ============
// 1. ai：侧栏聊天（chat）
const ai = useAiChat({ docId: computed(() => props.docId) })
const aiLoading = computed(() => ai.status.value === 'submitted' || ai.status.value === 'streaming')

// 2. editAi：智能编辑 / 改写 / 扩写等浮窗操作（不污染侧栏历史）
const editAi = useAiChat({ docId: computed(() => props.docId) })
const editAiLoading = computed(() => editAi.status.value === 'submitted' || editAi.status.value === 'streaming')

// 3. slashAi：/ 斜杠命令生成
const slashAi = useAiChat({ docId: computed(() => props.docId) })
const slashAiLoading = ref(false)

function stopAiGeneration() {
  ai.stop()
}

/**
 * AI 全局遮盖层：所有 AI 操作期间显示
 * - 防止用户误输入到 AI 正在生成的位置
 * - 点击遮盖层可停止所有 AI
 */
const aiCoverVisible = computed(() => aiLoading.value || editAiLoading.value || slashAiLoading.value)

function stopAllAi() {
  ai.stop()
  editAi.stop()
  if (slashAi) slashAi.stop()
  ElMessage.info('已停止 AI 生成')
}

// AI 处理时把编辑器设为只读
watch(aiCoverVisible, (v) => {
  if (editor.value) {
    try { editor.value.setEditable(!v && props.canEdit) } catch {}
  }
})
```

### 10.7 关键：`sendAiChat` 完整版（参考 `me_changes.md:14030+` + `me_record.md:14050-14250`）

```ts
async function sendAiChat() {
  const q = aiQuestion.value.trim()
  if (!q || aiLoading.value) return

  const isEditMode = aiEditMode.value
  const targetRange = aiEditTargetRange.value

  // 编辑模式 prompt（学习豆包 — 不要求 <result> 标签，不塞全文）
  const systemPrompt = isEditMode
    ? (() => {
        if (targetRange && targetRange.text) {
          // 有选区：只给目标文本 + 上下文
          const ctxBefore = editor.value?.state.doc.textBetween(
            Math.max(0, targetRange.from - 200), targetRange.from, '\n', '\n'
          ) || ''
          const ctxAfter = editor.value?.state.doc.textBetween(
            targetRange.to, Math.min(editor.value.state.doc.content.size, targetRange.to + 200), '\n', '\n'
          ) || ''
          return `你是文档编辑助手。

【任务】根据用户指令，修改【原文】中的内容。

【上文】
${ctxBefore}

【原文】（只修改这部分）
${targetRange.text}

【下文】
${ctxAfter}

【指令】
${q}

【输出规则】
1. 只输出修改后的"原文"内容
2. 不要输出任何解释、说明
3. 不要输出"好的"、"以下是"、"修改后"等前缀
4. 直接输出结果`
        } else {
          // 无选区：续写
          return `你是文档编辑助手。根据用户的指令续写文档。

【当前光标前文】
${editor.value?.state.doc.textBetween(
  Math.max(0, editor.value.state.selection.from - 500),
  editor.value.state.selection.from, '\n', '\n'
) || ''}

【指令】
${q}

【输出规则】
1. 只输出续写的内容
2. 不要输出任何解释、说明
3. 直接输出结果`
        }
      })()
    : ''  // 问答模式：传空 systemPrompt，由后端自动注入"文档内容"上下文

  aiQuestion.value = ''
  await nextTick()

  await ai.sendUserMessage(q, systemPrompt, editor.value?.getText() || '')
}
```

## 11. AI 浮窗完整模板（参考 `me_record.md:110400`）

替换当前文件中的 rewriteModal 模板：

```html
<!-- AI 浮窗结果区（独立于侧栏聊天，选区上方显示 AI 输出 + 接受/拒绝） -->
<transition name="fade">
  <div
    v-if="aiFloatPanel.visible"
    class="ai-float-panel"
    :class="{ 'ai-float-panel--options': aiFloatPanel.options.length > 0 }"
    :style="{ top: aiFloatPanel.position.top + 'px', left: aiFloatPanel.position.left + 'px' }"
    @mousedown.stop
  >
    <div class="ai-float-header">
      <span class="ai-float-title">✨ {{ aiFloatPanel.title }}</span>
      <div class="ai-float-status">
        <span v-if="aiFloatPanel.status === 'streaming'" class="ai-float-streaming">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </span>
        <span v-else-if="aiFloatPanel.status === 'done'" class="ai-float-done">已完成</span>
        <span v-else-if="aiFloatPanel.status === 'aborted'" class="ai-float-aborted">已停止</span>
        <span v-else-if="aiFloatPanel.status === 'error'" class="ai-float-error">错误</span>
        <button class="ai-float-close" @click="closeAiFloatPanel">×</button>
      </div>
    </div>

    <!-- 思考链（豆包风格 — 浅紫边框 + 蓝色阴影 + 呼吸动画） -->
    <div v-if="aiFloatPanel.thinking || aiFloatPanel.thinkingDone"
         class="ai-float-thinking"
         :class="{ thinking: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone, done: aiFloatPanel.thinkingDone }">
      <div class="thinking-head" @click="aiFloatPanel.thinkingDone && (aiFloatPanel.thinkingExpanded = !aiFloatPanel.thinkingExpanded)">
        <svg class="thinking-icon" :class="{ spinning: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 6v6l4 2"/>
        </svg>
        <span class="thinking-title">
          {{ aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone
             ? '正在思考...'
             : `已思考 ${aiFloatPanel.thinkingDurationSec} 秒` }}
        </span>
        <span v-if="aiFloatPanel.thinkingDone" class="thinking-toggle">▾</span>
      </div>
      <div v-show="aiFloatPanel.thinkingExpanded || (aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone)" class="thinking-body">
        <div class="thinking-text">{{ aiFloatPanel.thinking || '...' }}<span v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone" class="thinking-cursor">▍</span></div>
      </div>
    </div>

    <!-- 单方案流式结果 -->
    <div v-if="aiFloatPanel.options.length === 0" class="ai-float-body">
      <div v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.text" class="ai-float-placeholder">
        AI 撰写中…
      </div>
      <div v-else class="ai-float-content">{{ aiFloatPanel.text }}<span v-if="aiFloatPanel.status === 'streaming'" class="ai-float-cursor">▍</span></div>
    </div>

    <!-- 多方案选择 -->
    <div v-else class="ai-float-options">
      <div class="ai-float-options-hint">AI 提供了 {{ aiFloatPanel.options.length }} 个方案：</div>
      <div
        v-for="(opt, idx) in aiFloatPanel.options"
        :key="idx"
        class="ai-float-option"
        @click="pickAiFloatOption(idx)"
      >
        <div class="ai-float-option-title">{{ opt.title }}</div>
        <div class="ai-float-option-content">{{ opt.content.substring(0, 120) }}{{ opt.content.length > 120 ? '...' : '' }}</div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div v-if="aiFloatPanel.options.length === 0" class="ai-float-footer">
      <!-- Diff 模式：显示接受/拒绝 -->
      <template v-if="aiFloatPanel.pendingDiff">
        <div class="ai-float-diff-hint">
          <span class="diff-icon-removed">⊘</span> 原文
          <span class="diff-icon-added">+</span> AI 新内容
        </div>
        <button class="ai-float-btn ai-float-btn--ghost" @click="rejectDiff">✗ 拒绝</button>
        <button class="ai-float-btn ai-float-btn--primary" @click="acceptDiff">✓ 采用 ↵</button>
      </template>
      <!-- 流式中 -->
      <button
        v-else-if="aiFloatPanel.status === 'streaming'"
        class="ai-float-btn ai-float-btn--ghost"
        @click="stopAiFloatResult"
      >停止</button>
      <!-- 完成 -->
      <template v-else>
        <el-tooltip content="重新生成" placement="top">
          <button
            class="ai-float-btn ai-float-btn--icon"
            :disabled="!aiFloatPanel.text"
            @click="regenerateAiFloatResult"
          >↻</button>
        </el-tooltip>
        <button
          class="ai-float-btn ai-float-btn--primary"
          :disabled="!aiFloatPanel.text"
          @click="acceptAiFloatResult"
        >采用 ↵</button>
      </template>
    </div>
  </div>
</transition>
```

## 12. AI 侧栏完整模板（豆包风格 + 8 快捷卡）

```html
<!-- AI 侧边面板 - 豆包风格 -->
<transition name="slide">
  <div v-if="showAi && canEdit" class="ai-panel" :style="{ width: aiPanelWidth + 'px' }">
    <!-- 调整大小拖拽条 -->
    <div class="ai-resize-handle" @mousedown="startResize"></div>
    <!-- 头部 -->
    <div class="ai-header">
      <div class="ai-brand">
        <div class="ai-avatar-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
          </svg>
        </div>
        <span class="ai-title">AI 助手</span>
      </div>
      <div class="ai-mode-toggle">
        <button class="mode-btn" :class="{ active: !aiEditMode }" @click="aiEditMode = false">问答</button>
        <button class="mode-btn" :class="{ active: aiEditMode }" @click="aiEditMode = true">编辑</button>
      </div>
      <button class="ai-close-btn" @click="showAi = false">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </button>
    </div>

    <!-- 消息列表 -->
    <div class="ai-messages" ref="aiMsgRef" @click="handleAiMessagesClick">
      <!-- 空状态：8 个快捷卡 -->
      <div v-if="aiMessages.length === 0" class="ai-quick-cards">
        <div class="ai-welcome">👋 你好，我是 AI 助手。可以帮你总结、翻译、润色、解释、扩写...</div>
        <div class="quick-card" @click="quickAiAsk('请总结这篇文档的核心内容')">
          <span class="qc-icon">📋</span>
          <span class="qc-name">总结摘要</span>
          <span class="qc-desc">提取文档核心</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请续写文档')">
          <span class="qc-icon">✍️</span>
          <span class="qc-name">续写内容</span>
          <span class="qc-desc">基于上下文</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请润色这段文字')">
          <span class="qc-icon">✨</span>
          <span class="qc-name">润色文字</span>
          <span class="qc-desc">改进表达</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请解释这段内容')">
          <span class="qc-icon">💡</span>
          <span class="qc-name">解释说明</span>
          <span class="qc-desc">通俗易懂</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请检查语法错误')">
          <span class="qc-icon">🔍</span>
          <span class="qc-name">检查语法</span>
          <span class="qc-desc">修正错误</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请扩写这段内容')">
          <span class="qc-icon">📝</span>
          <span class="qc-name">内容扩写</span>
          <span class="qc-desc">补充细节</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请缩写这段内容')">
          <span class="qc-icon">✂️</span>
          <span class="qc-name">内容缩写</span>
          <span class="qc-desc">精简压缩</span>
        </div>
        <div class="quick-card" @click="quickAiAsk('请翻译成英文')">
          <span class="qc-icon">🌐</span>
          <span class="qc-name">翻译英文</span>
          <span class="qc-desc">中→英</span>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(m, i) in aiMessages" :key="i" :class="['ai-msg', `ai-msg-${m.role}`]">
        <div class="msg-avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
        <div class="msg-body">
          <!-- 思考区域 -->
          <div v-if="m.role === 'assistant' && (m.thinking || m.thinkingDone)" class="thinking-section" :class="{ ongoing: aiLoading && !m.thinkingDone }">
            <button class="thinking-toggle" @click="toggleThinking(m)">
              <svg class="thinking-icon" :class="{ spinning: aiLoading && !m.thinkingDone }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 6v6l4 2"/>
              </svg>
              <span>{{ aiLoading && !m.thinkingDone ? '正在思考...' : '思考过程' }}</span>
              <svg class="chevron" :class="{ expanded: m.thinkingExpanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
            </button>
            <div v-show="m.thinkingExpanded || (aiLoading && !m.thinkingDone)" class="thinking-content-wrapper">
              <div class="thinking-text">{{ m.thinking }}<span v-if="aiLoading && !m.thinkingDone" class="thinking-cursor">▍</span></div>
            </div>
          </div>

          <!-- 用户消息内容 -->
          <div v-if="m.role === 'user'" class="msg-content user-content">{{ m.content }}</div>

          <!-- AI 消息内容 -->
          <div v-else-if="m.thinkingDone" class="msg-content">
            <div v-html="renderAiMarkdown(m.content)"></div>
            <!-- 智能编辑多方案 -->
            <div v-if="m.hasOptions && m.options" class="ai-options">
              <div class="ai-options-hint">选择方案：</div>
              <div v-for="(opt, idx) in m.options" :key="idx" class="ai-option-item" @click="selectAiOption(i, idx)">
                <div class="ai-option-title">{{ opt.title }}</div>
                <div class="ai-option-content">{{ opt.content.substring(0, 100) }}...</div>
              </div>
            </div>
            <!-- 操作按钮 -->
            <div v-if="m.content && !(aiLoading && i === aiMessages.length - 1)" class="msg-actions">
              <span v-if="m.inserted" class="inserted-badge">✓ 已插入</span>
              <template v-else>
                <button class="action-btn" @click="copyAiMsg(m.content)" title="复制">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                </button>
                <button class="action-btn insert-btn" @click="insertToEditor(m.content, i)" title="插入文档">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 5v14M5 12l7 7 7-7"/></svg>
                </button>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 智能编辑模式：显示已选区 -->
    <div v-if="aiEditMode && aiEditTargetRange" class="ai-edit-target-info">
      <span class="target-icon">✏️</span>
      <span class="target-text">
        已选 <b>{{ aiEditTargetRange.text.length }}</b> 字
        <span v-if="aiEditTargetRange.text.length > 30">（{{ aiEditTargetRange.text.slice(0, 30) }}...）</span>
        <span v-else>{{ aiEditTargetRange.text }}</span>
      </span>
      <button class="target-clear" @click="aiEditTargetRange = null" title="取消选区">×</button>
    </div>

    <!-- 输入框 -->
    <div class="ai-input-area">
      <textarea
        v-model="aiQuestion"
        :placeholder="aiEditMode ? (aiEditTargetRange ? '输入指令：让这段更简洁 / 翻译成英文 / 扩写...' : '在文档中先选中文本，再输入指令；或留空：续写光标处') : '输入问题或指令...'"
        @focus="onAiInputFocus"
        @blur="handleInputBlur"
        @keydown="handleAiKeydown"
        @input="autoResizeAiInput"
        ref="aiInputRef"
        class="ai-textarea"
        rows="1"
      ></textarea>
      <button v-if="!aiLoading" class="send-btn" :disabled="!aiQuestion.trim()" @click="sendAiChat">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></svg>
      </button>
      <button v-else class="send-btn stop-btn" title="停止生成" @click="stopAiGeneration">
        <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
      </button>
    </div>
  </div>
</transition>
```

## 13. AI 相关 CSS（必须新增）

```css
/* AI 浮窗 */
.ai-float-panel {
  position: absolute;
  z-index: 100;
  width: 380px;
  max-width: 90vw;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.15);
  font-size: 13px;
  transform: translateX(-50%);
  margin-top: -8px;
}
.ai-float-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #e5e7eb;
}
.ai-float-title { font-weight: 600; color: #4f46e5; }
.ai-float-status { display: flex; align-items: center; gap: 6px; }
.ai-float-streaming .dot {
  display: inline-block;
  width: 4px; height: 4px;
  background: #6366f1;
  border-radius: 50%;
  margin: 0 1px;
  animation: blink 1.4s infinite both;
}
.ai-float-streaming .dot:nth-child(2) { animation-delay: 0.2s; }
.ai-float-streaming .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.3; } 40% { opacity: 1; } }
.ai-float-close { background: none; border: none; cursor: pointer; color: #6b7280; font-size: 20px; }
.ai-float-body { padding: 12px 14px; min-height: 60px; max-height: 300px; overflow-y: auto; }
.ai-float-content { white-space: pre-wrap; line-height: 1.6; color: #1f2937; }
.ai-float-cursor::after { content: '▍'; animation: cursor-blink 0.8s infinite; color: #6366f1; }
@keyframes cursor-blink { 0%, 50% { opacity: 1; } 50.01%, 100% { opacity: 0; } }
.ai-float-placeholder { color: #9ca3af; font-style: italic; }
.ai-float-options { padding: 12px 14px; }
.ai-float-options-hint { font-size: 11px; color: #6b7280; margin-bottom: 8px; }
.ai-float-option {
  padding: 10px 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.ai-float-option:hover { background: #f3f4f6; border-color: #6366f1; }
.ai-float-option-title { font-weight: 600; color: #4f46e5; margin-bottom: 4px; }
.ai-float-option-content { font-size: 12px; color: #4b5563; line-height: 1.5; }
.ai-float-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-top: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 0 0 12px 12px;
}
.ai-float-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.ai-float-btn--primary { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.ai-float-btn--primary:hover:not(:disabled) { background: #4338ca; }
.ai-float-btn--ghost:hover:not(:disabled) { background: #f3f4f6; }
.ai-float-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ai-float-btn--icon { padding: 6px 10px; }

/* AI 浮窗思考链（豆包风格 — 浅紫边框 + 蓝色阴影） */
.ai-float-thinking {
  margin: 8px 14px 0;
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 8px;
  background: #faf5ff;
  overflow: hidden;
}
.ai-float-thinking.thinking { animation: thinking-breathe 1.5s ease-in-out infinite; }
@keyframes thinking-breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(99, 102, 241, 0); }
  50% { box-shadow: 0 0 12px rgba(99, 102, 241, 0.25); }
}
.thinking-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: #6d28d9;
}
.thinking-icon {
  width: 14px; height: 14px;
}
.thinking-icon.spinning { animation: spin 1.2s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.thinking-title { flex: 1; font-weight: 500; }
.thinking-toggle { font-size: 10px; }
.thinking-body {
  padding: 8px 12px;
  border-top: 1px solid rgba(167, 139, 250, 0.2);
  max-height: 200px;
  overflow-y: auto;
}
.thinking-text {
  font-size: 12px;
  line-height: 1.5;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}
.thinking-cursor::after { content: '▍'; animation: cursor-blink 0.8s infinite; color: #8b5cf6; }

/* Diff 提示 + 接受/拒绝 */
.ai-float-diff-hint { display: flex; align-items: center; gap: 6px; margin-right: auto; font-size: 11px; color: #6b7280; }
.diff-icon-removed { color: #ef4444; font-weight: 600; font-size: 13px; }
.diff-icon-added { color: #10b981; font-weight: 600; font-size: 13px; }

/* 编辑器内 diff 样式（豆包风格） */
.diff-removed {
  background: linear-gradient(transparent 60%, rgba(239, 68, 68, 0.25) 60%);
  text-decoration: line-through;
  text-decoration-color: rgba(239, 68, 68, 0.7);
  text-decoration-thickness: 2px;
  color: #6b7280;
  padding: 0 2px;
  border-radius: 2px;
}
.diff-added {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
  padding: 0 2px;
  border-radius: 2px;
  border-bottom: 2px solid rgba(16, 185, 129, 0.4);
}

/* AI 侧栏（豆包风格） */
.ai-panel {
  width: 360px;
  background: #fafbfc;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  transition: width 0.05s linear;
}
.ai-resize-handle {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 4px;
  cursor: ew-resize;
  z-index: 10;
  background: transparent;
  transition: background 0.15s;
}
.ai-resize-handle:hover { background: rgba(99, 102, 241, 0.3); }

.ai-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.ai-brand { display: flex; align-items: center; gap: 8px; flex: 1; }
.ai-avatar-icon {
  width: 28px; height: 28px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.ai-avatar-icon svg { width: 18px; height: 18px; }
.ai-title { font-weight: 600; font-size: 14px; color: #1f2937; }

.ai-mode-toggle { display: flex; gap: 4px; margin-right: 8px; }
.mode-btn {
  padding: 4px 10px;
  font-size: 12px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
}
.mode-btn.active { background: #6366f1; color: #fff; border-color: #6366f1; }

.ai-close-btn {
  background: none; border: none;
  color: #9ca3af; cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.ai-close-btn:hover { background: #f3f4f6; color: #6b7280; }
.ai-close-btn svg { width: 16px; height: 16px; }

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbfc;
}
.ai-welcome { text-align: center; color: #6b7280; font-size: 13px; margin-bottom: 16px; line-height: 1.6; }
.ai-quick-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.quick-card {
  display: flex; flex-direction: column;
  align-items: center;
  padding: 14px 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: center;
}
.quick-card:hover { border-color: #6366f1; background: #f5f3ff; transform: translateY(-1px); }
.qc-icon { font-size: 24px; margin-bottom: 4px; }
.qc-name { font-size: 13px; font-weight: 600; color: #1f2937; margin-bottom: 2px; }
.qc-desc { font-size: 11px; color: #9ca3af; }

.ai-msg { display: flex; gap: 8px; margin-bottom: 16px; }
.ai-msg-user { flex-direction: row-reverse; }
.msg-avatar {
  flex: 0 0 28px; height: 28px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  color: #fff;
}
.ai-msg-user .msg-avatar { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.ai-msg-assistant .msg-avatar { background: linear-gradient(135deg, #10b981, #059669); }

.msg-body { flex: 1; max-width: calc(100% - 36px); }
.msg-content {
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 13px;
  word-break: break-word;
  color: #1f2937;
}
.msg-content.user-content { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; border: none; }
.msg-content p { margin: 0 0 6px; }
.msg-content p:last-child { margin: 0; }

/* 思考区域（豆包风格 — 浅紫边框 + 旋转时钟） */
.thinking-section {
  margin-bottom: 6px;
  border: 1px solid rgba(167, 139, 250, 0.3);
  background: #faf5ff;
  border-radius: 10px;
  overflow: hidden;
}
.thinking-section.ongoing { animation: thinking-breathe 1.5s ease-in-out infinite; }
.thinking-toggle {
  display: flex; align-items: center; gap: 6px;
  width: 100%;
  padding: 6px 10px;
  background: transparent; border: none;
  font-size: 12px;
  color: #6d28d9;
  cursor: pointer;
  text-align: left;
}
.thinking-icon { width: 14px; height: 14px; flex-shrink: 0; }
.thinking-icon.spinning { animation: spin 1.2s linear infinite; }
.chevron {
  width: 12px; height: 12px;
  margin-left: auto;
  transition: transform 0.2s;
}
.chevron.expanded { transform: rotate(180deg); }
.thinking-content-wrapper {
  padding: 8px 12px;
  border-top: 1px solid rgba(167, 139, 250, 0.2);
  max-height: 200px; overflow-y: auto;
}
.thinking-text { font-size: 12px; line-height: 1.5; color: #4b5563; white-space: pre-wrap; }

/* 操作按钮 */
.msg-actions { display: flex; gap: 4px; margin-top: 4px; }
.action-btn {
  background: none; border: none;
  padding: 4px;
  cursor: pointer;
  color: #9ca3af;
  border-radius: 4px;
  display: flex; align-items: center;
}
.action-btn:hover { color: #6366f1; background: #f3f4f6; }
.action-btn svg { width: 14px; height: 14px; }
.inserted-badge {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 2px 6px;
  font-size: 11px;
  color: #059669;
  background: #d1fae5;
  border-radius: 4px;
}

/* 多方案选择 */
.ai-options { margin-top: 8px; }
.ai-options-hint { font-size: 11px; color: #6b7280; margin-bottom: 4px; }
.ai-option-item {
  padding: 8px 10px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-bottom: 4px;
  cursor: pointer;
}
.ai-option-item:hover { background: #f3f4f6; border-color: #6366f1; }
.ai-option-title { font-weight: 600; color: #4f46e5; font-size: 12px; }
.ai-option-content { font-size: 11px; color: #4b5563; line-height: 1.5; }

/* 智能编辑目标信息 */
.ai-edit-target-info {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  background: rgba(99, 102, 241, 0.08);
  border-top: 1px solid rgba(99, 102, 241, 0.2);
  font-size: 12px;
  color: #4f46e5;
}
.target-icon { font-size: 14px; }
.target-text { flex: 1; line-height: 1.4; }
.target-clear {
  background: none; border: none;
  color: #6b7280;
  cursor: pointer;
  font-size: 18px;
  padding: 0 4px;
}

/* 输入框 */
.ai-input-area {
  display: flex; gap: 6px; align-items: flex-end;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
.ai-textarea {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.5;
  max-height: 120px;
  background: #fafbfc;
  transition: border-color 0.15s;
}
.ai-textarea:focus { border-color: #6366f1; background: #fff; }
.send-btn {
  width: 32px; height: 32px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn svg { width: 16px; height: 16px; }
.send-btn.stop-btn { background: #ef4444; }

/* AI 全局遮盖层（防止误输入到 AI 正在生成的位置） */
.ai-cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex; align-items: center; justify-content: center;
  z-index: 50;
  cursor: pointer;
}
.ai-cover-overlay::after {
  content: 'AI 正在生成中...（点击停止）';
  padding: 8px 16px;
  background: rgba(99, 102, 241, 0.9);
  color: #fff;
  border-radius: 20px;
  font-size: 13px;
}
```

## 14. 必须在 handleAiAction 中保留 smart-edit 分支

**当前文件用错了：`handleAiAction` 直接调 `rewriteModal`（新加的弹窗）**。
**正确做法（参考 7/3 06:06:48 + 7/2 01:29:16）**：
- `handleAiAction` 接收 `command` 参数
- 如果 `command === 'smart-edit'` → 调 `handleSmartEdit`（弹 `ElMessageBox.prompt` 收指令 → 调 `runEditAi`）
- 其他命令（rewrite/expand/shorten/translate/fix-grammar/formal/casual/continue）→ 直接调 `runEditAi`

```ts
async function handleAiAction(command: string) {
  if (!editor.value || !canEdit.value) return
  if (command === 'smart-edit') {
    await handleSmartEdit()
    return
  }
  // 改写/扩写/缩写/翻译/语法/正式/随意/续写
  const { from, to } = editor.value.state.selection
  const selectedText = editor.value.state.doc.textBetween(from, to)
  const insertAtCursor = !selectedText || to <= from
  await runEditAi(
    command as AiFloatCommand,
    { from, to },
    selectedText,
    insertAtCursor
  )
}
```

---

**完成时间估算**：
- 阶段 0-1（准备 + 阅读）：30 min
- 阶段 2（6 个 Agent 并行）：45-60 min
- 阶段 3-4（合并 + TS check + build）：15 min
- 阶段 5（部署 + 验证）：30 min
- **总计**：2-2.5 小时

---

# 🔴 重要补充 #2：AI 侧栏"问答模式 / 编辑模式"完整设计

> **重新搜索 `me_record.md` + `me_changes.md` 后发现，初版补充 #1 的"AI 侧栏"部分遗漏了核心的"问答/编辑模式切换"设计。**
> 7/6 之前最后定型的设计有 **8 个关键改进点**，必须全部补回。

## 15. 核心问题（用户原始诉求）

> "AI 侧栏的聊天窗口之前有编辑模式和问答模式啊"
> "编辑模式下的功能和模式输出优化一下，能够真正修改md文档。"
> "编辑模式应该学习一下豆包的md编辑，他可以对每行，或选中片段就行编辑"
> "聊天侧栏的编辑模式有问题，逻辑不对，你重新设计一下，应该是只有全文确认后的内容才能插入或替换原文，现在是大模型生成的无关内容也进入了编辑区域内。"
> "Mode toggle button should use home page theme color via CSS variable"
> "AI 面板应该可以拖拽调整宽度（resizable without hover highlight effect）"
> "AI 编辑 local loading should be transparent (0.3 opacity) so users can see streaming content"
> "Smart edit should not clear original content until AI outputs usable content"
> "If AI outputs multiple versions/suggestions, show a UI for user to select"

## 16. 8 个关键改进点（按时间线）

| # | 改进点 | 引入时间 | 7/6 之前最后一次定型 |
|---|--------|----------|---------------------|
| 1 | `mode-toggle-btn`（问答/编辑切换） | 6/28 13:36:16 | 7/5 08:52:59 + `--primary-gradient` |
| 2 | `aiEditTargetRange` 锁定选区 | 7/5 08:53:29 | 7/5 08:53:29 (核心创新) |
| 3 | `onAiInputFocus` 焦点时捕获选区 | 7/5 08:53:29 | 7/5 08:53:29 |
| 4 | 豆包风格 system prompt（不要求 result 标签） | 7/5 09:15:06 | 7/5 09:15:06 (最终版) |
| 5 | 上下文只有 200 字 + 目标文本 | 7/5 09:15:06 | 7/5 09:15:06 |
| 6 | `ai-edit-target-info` 选区信息条 | 7/5 08:52:59 | 7/5 08:52:59 |
| 7 | 4 段独立 system prompt（rewrite/expand/shorten/translate） | 7/3 06:06:48 | 7/3 06:06:48 |
| 8 | `aiExtras.set` 保存多方案 + originalRange | 7/5 08:53:56 | 7/5 08:53:56 |

## 17. mode-toggle-btn 完整设计（参考 `me_record.md:83552`）

```html
<!-- AI 侧栏头部 - 含 mode 切换按钮 -->
<div class="ai-header">
  <div class="ai-brand">
    <div class="ai-avatar-icon">
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
      </svg>
    </div>
    <span class="ai-title">AI 写作助手</span>
  </div>
  <div class="ai-header-actions">
    <el-tooltip :content="aiEditMode ? '切换到问答模式' : '切换到编辑模式'" placement="bottom">
      <!-- 关键：点击时清空 target range（防止旧选区残留） -->
      <button
        class="mode-toggle-btn"
        :class="{ active: aiEditMode }"
        @click="aiEditMode = !aiEditMode; aiEditTargetRange = null"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
          <path v-if="aiEditMode" d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
          <path v-else d="M12 20h9M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/>
        </svg>
        {{ aiEditMode ? '编辑模式' : '问答模式' }}
      </button>
    </el-tooltip>
  </div>
  <button class="ai-close-btn" @click="showAi=false">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M18 6L6 18M6 6l12 12"/>
    </svg>
  </button>
</div>
```

**关键细节**：
- 编辑模式图标：📄 文档图标（"document"）
- 问答模式图标：✏️ 笔图标（"pencil"）
- 切换时同时清空 `aiEditTargetRange`（防止旧选区残留到新模式）
- `ai-header-actions` 用 `flex: 1; justify-content: center` 居中

## 18. mode-toggle-btn 完整 CSS（参考 `me_record.md:83836`）

```css
.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

/* 关键：使用主页 --primary-gradient 主题色变量（不是 Element Plus 默认蓝） */
.mode-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-toggle-btn:hover {
  border-color: var(--primary-color, #409eff);  /* 主页主题色 */
  color: var(--primary-color, #409eff);
}

.mode-toggle-btn.active {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border-color: #409eff;
  color: #fff;
}
```

## 19. 关键创新：`aiEditTargetRange` 选区锁定（参考 `me_record.md:137887`）

**问题背景**：用户在 AI 面板输入时，焦点在 textarea，编辑器 selection 已丢失。
**解法**：focus 时**立即捕获**当前 selection，存到 `aiEditTargetRange`。AI 输出后用这个 range 替换。

```ts
const aiEditTargetRange = ref<{ from: number; to: number; text: string } | null>(null)

/**
 * AI 输入框 focus 时：捕获当前编辑器选区
 * 关键：用户切换到 AI 面板时 selection 已丢失，
 * 所以必须在 focus 的瞬间记录 from/to/text
 */
function onAiInputFocus() {
  aiInputFocused.value = true
  if (!aiEditMode.value || !editor.value) return
  // 已经有 target 时不覆盖（用户可能想复用旧的选区）
  if (aiEditTargetRange.value) return
  const { from, to } = editor.value.state.selection
  if (from === to) return  // 无选区（纯光标）不记录
  const text = editor.value.state.doc.textBetween(from, to, '\n', '\n')
  if (!text) return
  aiEditTargetRange.value = { from, to, text }
}

function handleInputBlur() {
  // blur 时不立即清空（让用户继续编辑 AI 输入）
  // 仅在切回编辑器或点击插入按钮时才清空
}
```

## 20. `ai-edit-target-info` 选区信息条（参考 `me_record.md:137811`）

```html
<!-- 编辑模式下显示当前选区，让用户清楚知道 AI 会修改哪里 -->
<div v-if="aiEditMode && aiEditTargetRange" class="ai-edit-target-info">
  <span class="target-icon">✏️</span>
  <span class="target-text">
    已选 <b>{{ aiEditTargetRange.text.length }}</b> 字
    <span v-if="aiEditTargetRange.text.length > 30">（前 30 字：{{ aiEditTargetRange.text.slice(0, 30) }}...）</span>
    <span v-else>{{ aiEditTargetRange.text }}</span>
  </span>
  <button
    class="target-clear"
    @click="aiEditTargetRange = null"
    title="取消选区（改为在光标处插入）"
  >×</button>
</div>

<!-- textarea placeholder 动态切换 -->
<textarea
  v-model="aiQuestion"
  :placeholder="aiEditMode
    ? (aiEditTargetRange
       ? '输入指令：让这段更简洁 / 翻译成英文 / 扩写...'
       : '在文档中先选中文本，再输入指令；或留空：续写光标处')
    : '输入问题或指令...'"
  @focus="onAiInputFocus"
  @blur="handleInputBlur"
  @keydown="handleAiKeydown"
  ref="aiInputRef"
  class="ai-textarea"
></textarea>
```

```css
/* 选区信息条 - 蓝色调（与 mode-toggle-btn.active 主题色一致） */
.ai-edit-target-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(51, 126, 204, 0.08));
  border-top: 1px solid rgba(64, 158, 255, 0.2);
  font-size: 12px;
  color: #2563eb;
}
.target-icon { font-size: 14px; }
.target-text { flex: 1; line-height: 1.4; word-break: break-all; }
.target-text b { color: #1d4ed8; }
.target-clear {
  background: none;
  border: none;
  color: #6b7280;
  cursor: pointer;
  font-size: 18px;
  padding: 0 4px;
  border-radius: 4px;
}
.target-clear:hover { background: rgba(0, 0, 0, 0.05); }
```

## 21. 关键改进：豆包风格 system prompt（参考 `me_changes.md:27298` + `me_record.md:27298`）

**7/5 09:15:06 重构**：从 `<result>` 标签严格格式 → 豆包风格的"自然 prompt + 上下文 200 字"。

```ts
async function sendAiChat() {
  const q = aiQuestion.value.trim()
  if (!q || aiLoading.value) return

  const isEditMode = aiEditMode.value
  const targetRange = aiEditTargetRange.value

  // 关键：编辑模式 prompt 设计原则（学习豆包）
  // 1. 不要求 <result> 标签 —— LLM 直接输出纯文本，前端不用 regex 提取
  // 2. 不塞全文 —— 只给目标文本 + 上下文，避免 LLM 混淆
  // 3. 不列"不要做什么" —— 负面清单会让 LLM 反而出错；只告诉它要做什么
  // 4. 让 LLM 自行决定续写/改写语义（用户指令足够明确时）
  const systemPrompt = isEditMode
    ? (() => {
        if (targetRange && targetRange.text) {
          // 有选区：明确告诉 LLM 要修改这段
          const ctxBefore = editor.value?.state.doc.textBetween(
            Math.max(0, targetRange.from - 200), targetRange.from, '\n', '\n'
          ) || ''
          const ctxAfter = editor.value?.state.doc.textBetween(
            targetRange.to, Math.min(editor.value.state.doc.content.size, targetRange.to + 200), '\n', '\n'
          ) || ''
          return `你是文档编辑助手。

【任务】根据用户指令，修改【原文】中的内容。

【上文】
${ctxBefore}

【原文】（只修改这部分）
${targetRange.text}

【下文】
${ctxAfter}

【指令】
${q}

【输出规则】
1. 只输出修改后的"原文"内容
2. 不要输出任何解释、说明
3. 不要输出"好的"、"以下是"、"修改后"等前缀
4. 直接输出结果`
        } else {
          // 无选区：续写
          const ctxBefore = editor.value?.state.doc.textBetween(
            Math.max(0, editor.value.state.selection.from - 500),
            editor.value.state.selection.from, '\n', '\n'
          ) || ''
          return `你是文档编辑助手。根据用户的指令续写文档。

【当前光标前文】
${ctxBefore}

【指令】
${q}

【输出规则】
1. 只输出续写的内容
2. 不要输出任何解释、说明
3. 直接输出结果`
        }
      })()
    : ''  // 问答模式：传空 systemPrompt，由后端自动注入"文档内容"上下文

  aiQuestion.value = ''
  await nextTick()

  // useAiChat 内部用 25ms/字 打字机 + 段落化（双换行 → </p><p>）
  await ai.sendUserMessage(q, systemPrompt, editor.value?.getText() || '')
}
```

## 22. 关键创新：`handleEditModeResult` 完整版（参考 `me_record.md:137925`）

**用户原始问题**："聊天侧栏的编辑模式有问题，逻辑不对...大模型生成的无关内容也进入了编辑区域内"
**7/5 08:53:56 修复**：
- 多方案时只显示选项卡，**不立即插入**（让用户选择）
- 单方案时**清洗无用前缀**（"以下是"、"修改后"等）
- 用 `aiEditTargetRange` 不用当前 selection
- 用完后清空 target，下次重新捕获

```ts
/**
 * 编辑模式：清洗 AI 文本 + 写入编辑器 + 多方案选择
 * 7/5 08:53:56 关键修复：用 aiEditTargetRange 不用当前 selection（焦点已丢失）
 */
function handleEditModeResult(fullText: string, messageId: string) {
  if (!editor.value) return

  // ===== 步骤 1: 清洗无用内容 =====
  let cleanContent = fullText
    .replace(/<result>([\s\S]*?)<\/result>/gi, '$1')  // 提取 <result> 标签
    .replace(/<\/result>/gi, '')
    .replace(/^[\s\n]*/, '')
    .trim()
  cleanContent = cleanContent
    .replace(/^(根据|按照|以下是|修改后|编辑后|说明|建议|好的)[：:,，\s]*/i, '')  // 关键：去前缀
    .replace(/^>\s*/gm, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/\n*[-—*]*(解释|说明|备注|建议|成语)[：:].*$/gi, '')
    .replace(/\n*[-—*]*(如需|如果|或者|另外)[：:\s\S]*/gi, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  // ===== 步骤 2: 检测多方案 → 显示选项卡，不立即插入 =====
  const optionMatches = cleanContent.match(
    /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]\s*[\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]|$))/gi
  )
  if (optionMatches && optionMatches.length > 1) {
    const options = optionMatches.map((opt: string) => {
      const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]/)
      const title = titleMatch ? titleMatch[1] : '方案'
      const content = opt
        .replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]\s*/, '')
        .trim()
      return { title, content }
    })
    // 关键：用 aiEditTargetRange 不用当前 selection
    const target = aiEditTargetRange.value
    const from = target?.from ?? editor.value.state.selection.from
    const to = target?.to ?? editor.value.state.selection.to
    aiExtras.set(messageId, {
      hasOptions: true,
      options,
      originalRange: { from, to },
    })
    ElMessage.info('AI 生成了多个方案，点击选择')
    return
  }

  if (!cleanContent) {
    ElMessage.warning('AI 未生成可用内容')
    return
  }

  // ===== 步骤 3: 关键修复 - 用 aiEditTargetRange（不是当前 selection） =====
  // 用户在 AI 面板输入时，焦点不在编辑器，selection 已丢失
  const target = aiEditTargetRange.value
  const from = target?.from ?? editor.value.state.selection.from
  const to = target?.to ?? editor.value.state.selection.to

  if (target && to > from) {
    // 有选区 → 替换选区
    editor.value.chain().focus().deleteRange({ from, to }).insertContent(cleanContent).run()
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(from, finalDocSize)  // 1.2s 淡黄渐隐
    ElMessage.success(`已替换选区（${cleanContent.length} 字）`)
  } else {
    // 无选区 → 在光标处插入（续写场景）
    const pos = from
    editor.value.chain().focus().insertContentAt(pos, cleanContent).run()
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(pos, finalDocSize)
    ElMessage.success(`已插入到光标处（${cleanContent.length} 字）`)
  }
  aiExtras.set(messageId, { inserted: true })
  // 用完后清空 target，下次重新捕获
  aiEditTargetRange.value = null
}

/**
 * 用户点击多方案中的某个方案
 */
function selectAiOption(msgIndex: number, optionIndex: number) {
  const last = ai.messages.value[msgIndex] as any
  if (!last) return
  const extra = aiExtras.get(last.id)
  if (!extra || !extra.options || !extra.options[optionIndex] || !editor.value) return
  const option = extra.options[optionIndex]
  const range = extra.originalRange
  if (range && range.to > range.from) {
    editor.value.chain().focus()
      .deleteRange(range)
      .insertContentAt(range.from, option.content)
      .run()
    setAiInsertHighlight(range.from, editor.value.state.doc.content.size)
  } else {
    editor.value.chain().focus().insertContent(option.content).run()
    setAiInsertHighlight(editor.value.state.selection.from, editor.value.state.doc.content.size)
  }
  ElMessage.success('已采用方案')
  // 用完后清空 target
  aiEditTargetRange.value = null
}
```

## 23. 完整 AI 侧栏模板（替换当前 ai-panel）

```html
<!-- AI 侧边面板 - 豆包风格（带问答/编辑模式切换） -->
<transition name="slide">
  <div
    v-if="showAi && canEdit"
    class="ai-panel"
    :style="{ width: aiPanelWidth + 'px' }"
  >
    <!-- 调整大小拖拽条（无 hover 高亮） -->
    <div class="ai-resize-handle" @mousedown="startResize"></div>

    <!-- 头部（含 mode-toggle-btn） -->
    <div class="ai-header">
      <div class="ai-brand">
        <div class="ai-avatar-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
          </svg>
        </div>
        <span class="ai-title">AI 写作助手</span>
      </div>
      <!-- 关键：mode-toggle-btn 在头部居中 -->
      <div class="ai-header-actions">
        <el-tooltip :content="aiEditMode ? '切换到问答模式' : '切换到编辑模式'" placement="bottom">
          <button
            class="mode-toggle-btn"
            :class="{ active: aiEditMode }"
            @click="aiEditMode = !aiEditMode; aiEditTargetRange = null"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
              <path v-if="aiEditMode" d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
              <path v-else d="M12 20h9M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/>
            </svg>
            {{ aiEditMode ? '编辑模式' : '问答模式' }}
          </button>
        </el-tooltip>
      </div>
      <button class="ai-close-btn" @click="showAi=false">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 6L6 18M6 6l12 12"/>
        </svg>
      </button>
    </div>

    <!-- 消息列表 -->
    <div class="ai-messages" ref="aiMsgRef" @click="handleAiMessagesClick">
      <!-- 空状态：8 个快捷卡 + 豆包风格欢迎 -->
      <div v-if="ai.messages.value.length === 0" class="ai-welcome">
        <div class="welcome-icon">
          <div class="icon-ring"></div>
          <div class="icon-core">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
          </div>
        </div>
        <h3>有什么可以帮你的？</h3>
        <p>基于当前文档内容，AI 可以帮你完成以下任务</p>

        <!-- 关键：8 个 quick-prompts 大卡 + 6 个 quick-links 小链接 -->
        <div class="quick-prompts">
          <button class="prompt-btn" @click="quickAiAsk('总结这篇文档的核心内容，提取关键信息')">
            <span class="prompt-icon">📋</span>总结摘要
          </button>
          <button class="prompt-btn" @click="quickAiAsk('润色并改进这段文字，使其更专业流畅')">
            <span class="prompt-icon">✏️</span>润色文字
          </button>
          <button class="prompt-btn" @click="quickAiAsk('解释这段内容的含义，用简洁的语言说明')">
            <span class="prompt-icon">💡</span>解释说明
          </button>
          <button class="prompt-btn" @click="quickAiAsk('翻译成英文，保持专业准确的表达')">
            <span class="prompt-icon">🌐</span>翻译英文
          </button>
        </div>
        <div class="quick-links">
          <a class="quick-link" @click.prevent="quickAiAsk('检查文档中的语法错误和不通顺的句子，给出修改建议')">检查语法 ✕</a>
          <a class="quick-link" @click.prevent="quickAiAsk('扩写以下内容，增加更多细节和深度')">内容扩写 ✕</a>
          <a class="quick-link" @click.prevent="quickAiAsk('缩写以下内容，保留核心信息，精简表达')">内容缩写 ✕</a>
          <a class="quick-link" @click.prevent="quickAiAsk('续写当前文档内容，保持风格一致')">续写内容 ✕</a>
          <a class="quick-link" @click.prevent="quickAiAsk('用更正式专业的语气重写这段话')">正式语气 ✕</a>
          <a class="quick-link" @click.prevent="quickAiAsk('用更轻松随意的语气重写这段话')">轻松语气 ✕</a>
        </div>
      </div>

      <!-- 消息列表 - 遍历 ai.messages（useChat 管理） -->
      <div
        v-for="(m, i) in ai.messages.value"
        :key="m.id"
        :class="['ai-msg', `ai-msg-${m.role}`]"
      >
        <div class="msg-avatar">
          <span v-if="m.role === 'user'">👤</span>
          <span v-else>🤖</span>
        </div>
        <div class="msg-body">
          <!-- 思考区域（豆包风格 - 浅紫边框 + 旋转时钟） -->
          <div
            v-if="m.role === 'assistant' && (hasReasoning(m) || m.id && aiExtras.get(m.id)?.thinkingDone)"
            class="thinking-section"
            :class="{ ongoing: aiLoading && hasReasoning(m) && !isReasoningDone(m) }"
          >
            <button class="thinking-toggle" @click="toggleThinkingFor(m.id)">
              <svg class="thinking-icon" :class="{ spinning: aiLoading && hasReasoning(m) && !isReasoningDone(m) }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 6v6l4 2"/>
              </svg>
              <span>{{ aiLoading && hasReasoning(m) && !isReasoningDone(m) ? '正在思考...' : '思考过程' }}</span>
              <svg class="chevron" :class="{ expanded: aiExtras.get(m.id)?.thinkingExpanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 9l6 6 6-6"/>
              </svg>
            </button>
            <div v-show="aiExtras.get(m.id)?.thinkingExpanded || (aiLoading && hasReasoning(m) && !isReasoningDone(m))" class="thinking-content-wrapper">
              <div class="thinking-text">{{ getReasoningText(m) }}<span v-if="aiLoading && hasReasoning(m) && !isReasoningDone(m)" class="thinking-cursor">▍</span></div>
            </div>
          </div>

          <!-- 用户消息 -->
          <div v-if="m.role === 'user'" class="msg-content user-content">
            {{ getTextPart(m) }}
          </div>

          <!-- AI 消息（流式中：透明 0.3 opacity，让用户看到流式过程） -->
          <div
            v-else
            class="msg-content"
            :class="{
              'msg-loading-transparent': aiLoading && i === ai.messages.value.length - 1 && !getTextPart(m),
              'msg-ai-fade-in': !aiLoading && i === ai.messages.value.length - 1
            }"
          >
            <span v-if="aiLoading && i === ai.messages.value.length - 1 && !getTextPart(m)" class="typing-dots">
              <span></span><span></span><span></span>
            </span>
            <span v-html="renderAiMarkdown(getTextPart(m))"></span>
            <span v-if="aiLoading && i === ai.messages.value.length - 1 && getTextPart(m)" class="ai-streaming-cursor">▍</span>

            <!-- 多方案选择卡（7/5 08:53:56 新增） -->
            <div v-if="m.role === 'assistant' && aiExtras.get(m.id)?.hasOptions" class="ai-options">
              <div class="ai-options-hint">AI 提供了 {{ aiExtras.get(m.id)!.options!.length }} 个方案，点击选择：</div>
              <div
                v-for="(opt, idx) in aiExtras.get(m.id)!.options"
                :key="idx"
                class="ai-option-item"
                @click="selectAiOption(i, idx)"
              >
                <div class="ai-option-title">{{ opt.title }}</div>
                <div class="ai-option-content">{{ opt.content.substring(0, 120) }}{{ opt.content.length > 120 ? '...' : '' }}</div>
                <div class="ai-option-hint">点击采用此方案</div>
              </div>
            </div>

            <!-- 操作按钮（复制 + 插入） -->
            <div v-if="getTextPart(m) && !(aiLoading && i === ai.messages.value.length - 1)" class="msg-actions">
              <span v-if="aiExtras.get(m.id)?.inserted" class="inserted-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                已插入
              </span>
              <template v-else>
                <button class="action-btn" @click="copyAiMsg(getTextPart(m))" title="复制">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="9" y="9" width="13" height="13" rx="2"/>
                    <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
                  </svg>
                </button>
                <button
                  class="action-btn insert-btn"
                  @click="insertToEditor(getTextPart(m), m.id)"
                  title="插入文档"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M12 5v14M5 12l7 7 7-7"/>
                  </svg>
                </button>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 关键：编辑模式选区信息条（7/5 08:52:59） -->
    <div v-if="aiEditMode && aiEditTargetRange" class="ai-edit-target-info">
      <span class="target-icon">✏️</span>
      <span class="target-text">
        已选 <b>{{ aiEditTargetRange.text.length }}</b> 字
        <span v-if="aiEditTargetRange.text.length > 30">（前 30 字：{{ aiEditTargetRange.text.slice(0, 30) }}...）</span>
        <span v-else>{{ aiEditTargetRange.text }}</span>
      </span>
      <button class="target-clear" @click="aiEditTargetRange = null" title="取消选区（改为在光标处插入）">×</button>
    </div>

    <!-- 输入区 -->
    <div class="ai-input-area">
      <div class="input-box" :class="{ focused: aiInputFocused }">
        <textarea
          v-model="aiQuestion"
          :placeholder="aiEditMode
            ? (aiEditTargetRange
               ? '输入指令：让这段更简洁 / 翻译成英文 / 扩写...'
               : '在文档中先选中文本，再输入指令；或留空：续写光标处')
            : '输入问题或指令...'"
          @focus="onAiInputFocus"
          @blur="aiInputFocused = false"
          @keydown="handleAiKeydown"
          ref="aiInputRef"
          class="ai-textarea"
          rows="1"
        ></textarea>
        <button
          v-if="!aiLoading"
          class="send-btn"
          :disabled="!aiQuestion.trim()"
          @click="sendAiChat"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
        </button>
        <button
          v-else
          class="send-btn stop-btn"
          title="停止生成"
          @click="stopAiGeneration"
        >
          <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
        </button>
      </div>
      <!-- 关键：input-hint 动态切换 -->
      <div class="input-hint">{{ aiEditMode ? '编辑模式：AI 结果可一键插入文档' : '基于当前文档 · Enter 发送' }}</div>
    </div>
  </div>
</transition>
```

## 24. 关键辅助函数（参考 `me_record.md:137887` + 7/5 useChat 迁移）

```ts
// ===== 消息辅助函数（useChat messages 结构） =====
function getTextPart(m: any): string {
  const part = (m.parts || []).find((p: any) => p.type === 'text')
  return part?.text || ''
}
function getReasoningText(m: any): string {
  const part = (m.parts || []).find((p: any) => p.type === 'reasoning')
  return part?.text || ''
}
function hasReasoning(m: any): boolean {
  return !!(m.parts || []).find((p: any) => p.type === 'reasoning')
}
function isReasoningDone(m: any): boolean {
  const part = (m.parts || []).find((p: any) => p.type === 'reasoning')
  return part ? part.state === 'done' : true
}

// ===== 思考面板展开/折叠 =====
function toggleThinkingFor(msgId: string) {
  const extra = aiExtras.get(msgId)
  if (extra) {
    extra.thinkingExpanded = !extra.thinkingExpanded
    extra.userToggledThinking = true  // 标记用户手动切换
  }
  // 触发响应式（用 shallowRef 时手动 trigger）
  // aiExtras 是 Map，不是 reactive；用 forceUpdate 替代
  forceUpdate.value++
}

// 强制重渲染（aiExtras Map 变化不触发响应式）
const forceUpdate = ref(0)

// ===== 思考面板自动折叠 =====
watch(
  () => ai.messages.value,
  (newMsgs, oldMsgs) => {
    if (!aiLoading.value) return
    const lastIdx = newMsgs.length - 1
    if (lastIdx < 0) return
    const last = newMsgs[lastIdx]
    if (!last || last.role !== 'assistant') return
    // 正文第一个 chunk 来时，折叠思考（除非用户已手动展开）
    const hasText = (last.parts || []).some((p: any) => p.type === 'text' && p.text)
    if (hasText) {
      const extra = aiExtras.get(last.id) || { thinkingExpanded: false, userToggledThinking: false }
      if (!extra.userToggledThinking) {
        extra.thinkingExpanded = false
        aiExtras.set(last.id, extra)
        forceUpdate.value++
      }
    }
  },
  { deep: true, flush: 'post' }
)
```

## 25. 完整 CSS（豆包风格 + 透明流式 + 8 快捷卡 + 编辑模式）

```css
/* ===== AI 侧栏 ===== */
.ai-panel {
  width: 360px;
  background: #fafbfc;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

/* 调整大小拖拽条（无 hover 高亮） */
.ai-resize-handle {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 4px;
  cursor: ew-resize;
  z-index: 10;
  background: transparent;
}

/* 头部 */
.ai-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.ai-brand { display: flex; align-items: center; gap: 8px; }
.ai-avatar-icon {
  width: 28px; height: 28px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.ai-avatar-icon svg { width: 18px; height: 18px; }
.ai-title { font-weight: 600; font-size: 14px; color: #1f2937; }

/* 头部 actions（mode toggle 居中） */
.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

/* mode toggle button - 关键：使用主页主题色 */
.mode-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}
.mode-toggle-btn:hover {
  border-color: var(--primary-color, #409eff);
  color: var(--primary-color, #409eff);
}
.mode-toggle-btn.active {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border-color: #409eff;
  color: #fff;
}

.ai-close-btn {
  background: none; border: none;
  color: #9ca3af; cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex; align-items: center;
}
.ai-close-btn:hover { background: #f3f4f6; color: #6b7280; }
.ai-close-btn svg { width: 16px; height: 16px; }

/* 消息列表 */
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbfc;
}

/* 欢迎界面（豆包风格） */
.ai-welcome { text-align: center; padding: 20px 0; }
.welcome-icon {
  position: relative;
  width: 60px; height: 60px;
  margin: 0 auto 16px;
}
.icon-ring {
  position: absolute; inset: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  opacity: 0.2;
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.2; }
  50% { transform: scale(1.2); opacity: 0.1; }
}
.icon-core {
  position: relative;
  width: 60px; height: 60px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
}
.icon-core svg { width: 30px; height: 30px; }
.ai-welcome h3 { font-size: 16px; font-weight: 600; color: #1f2937; margin: 0 0 4px; }
.ai-welcome p { font-size: 12px; color: #6b7280; margin: 0 0 20px; }

/* 关键：4 个大卡 + 6 个小链接（豆包风格） */
.quick-prompts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 16px;
}
.prompt-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 13px;
  color: #1f2937;
}
.prompt-btn:hover { border-color: #6366f1; background: #f5f3ff; transform: translateY(-1px); }
.prompt-icon { font-size: 22px; }

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.quick-link {
  font-size: 11px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.quick-link:hover { background: #e5e7eb; color: #4f46e5; }

/* 消息气泡 */
.ai-msg { display: flex; gap: 8px; margin-bottom: 16px; }
.ai-msg-user { flex-direction: row-reverse; }
.msg-avatar {
  flex: 0 0 28px; height: 28px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.ai-msg-user .msg-avatar { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.ai-msg-assistant .msg-avatar { background: linear-gradient(135deg, #10b981, #059669); }

.msg-body { flex: 1; max-width: calc(100% - 36px); }
.msg-content {
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 13px;
  word-break: break-word;
  color: #1f2937;
  transition: opacity 0.3s;
}
.msg-content.user-content {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
}
.msg-content p { margin: 0 0 6px; }
.msg-content p:last-child { margin: 0; }

/* 关键：AI 加载中透明（让用户看到流式过程） */
.msg-loading-transparent { opacity: 0.3; }

/* 关键：AI 流式光标 */
.ai-streaming-cursor {
  display: inline-block;
  color: #6366f1;
  animation: blink 0.8s infinite;
  margin-left: 2px;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

/* 思考区域（豆包风格 - 浅紫边框） */
.thinking-section {
  margin-bottom: 6px;
  border: 1px solid rgba(167, 139, 250, 0.3);
  background: #faf5ff;
  border-radius: 10px;
  overflow: hidden;
}
.thinking-section.ongoing { animation: thinking-breathe 1.5s ease-in-out infinite; }
@keyframes thinking-breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(99, 102, 241, 0); }
  50% { box-shadow: 0 0 12px rgba(99, 102, 241, 0.25); }
}
.thinking-toggle {
  display: flex; align-items: center; gap: 6px;
  width: 100%;
  padding: 6px 10px;
  background: transparent; border: none;
  font-size: 12px;
  color: #6d28d9;
  cursor: pointer;
  text-align: left;
}
.thinking-icon { width: 14px; height: 14px; flex-shrink: 0; }
.thinking-icon.spinning { animation: spin 1.2s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.chevron { width: 12px; height: 12px; margin-left: auto; transition: transform 0.2s; }
.chevron.expanded { transform: rotate(180deg); }
.thinking-content-wrapper {
  padding: 8px 12px;
  border-top: 1px solid rgba(167, 139, 250, 0.2);
  max-height: 200px;
  overflow-y: auto;
}
.thinking-text { font-size: 12px; line-height: 1.5; color: #4b5563; white-space: pre-wrap; }
.thinking-cursor::after { content: '▍'; animation: cursor-blink 0.8s infinite; color: #8b5cf6; }
@keyframes cursor-blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

/* 操作按钮 */
.msg-actions { display: flex; gap: 4px; margin-top: 4px; align-items: center; }
.action-btn {
  background: none; border: none;
  padding: 4px;
  cursor: pointer;
  color: #9ca3af;
  border-radius: 4px;
  display: flex; align-items: center;
}
.action-btn:hover { color: #6366f1; background: #f3f4f6; }
.action-btn svg { width: 14px; height: 14px; }
.inserted-badge {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 2px 6px;
  font-size: 11px;
  color: #059669;
  background: #d1fae5;
  border-radius: 4px;
}

/* 多方案选择 */
.ai-options { margin-top: 8px; }
.ai-options-hint { font-size: 11px; color: #6b7280; margin-bottom: 4px; }
.ai-option-item {
  padding: 8px 10px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.ai-option-item:hover { background: #f3f4f6; border-color: #6366f1; }
.ai-option-title { font-weight: 600; color: #4f46e5; font-size: 12px; margin-bottom: 2px; }
.ai-option-content { font-size: 11px; color: #4b5563; line-height: 1.5; }
.ai-option-hint { font-size: 10px; color: #9ca3af; margin-top: 4px; }

/* 编辑模式选区信息条 */
.ai-edit-target-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(51, 126, 204, 0.08));
  border-top: 1px solid rgba(64, 158, 255, 0.2);
  font-size: 12px;
  color: #2563eb;
}
.target-icon { font-size: 14px; }
.target-text { flex: 1; line-height: 1.4; word-break: break-all; }
.target-text b { color: #1d4ed8; }
.target-clear {
  background: none; border: none;
  color: #6b7280;
  cursor: pointer;
  font-size: 18px;
  padding: 0 4px;
  border-radius: 4px;
  line-height: 1;
}
.target-clear:hover { background: rgba(0, 0, 0, 0.05); }

/* 输入区 */
.ai-input-area {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
.input-box {
  display: flex; gap: 6px; align-items: flex-end;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 6px 8px;
  background: #fafbfc;
  transition: border-color 0.15s;
}
.input-box.focused { border-color: #6366f1; background: #fff; }
.ai-textarea {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 13px;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.5;
  max-height: 100px;
  padding: 4px 6px;
}
.send-btn {
  width: 30px; height: 30px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
}
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn.stop-btn { background: #ef4444; }
.send-btn svg { width: 14px; height: 14px; }

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #9ca3af;
  margin-top: 6px;
}
```

## 26. 验证清单

| 验证项 | 期望行为 |
|--------|----------|
| AI 侧栏头部出现 mode-toggle-btn | 白色边框 + 问答模式文字 |
| 点击 mode-toggle-btn | 切到"编辑模式"，按钮变蓝（`--primary-gradient`） |
| 切换时选区信息条 | 编辑模式下出现"已选 N 字"，× 按钮可清空 |
| 编辑模式 placeholder | "在文档中先选中文本，再输入指令；或留空：续写光标处" |
| 选中文字 → focus AI 输入框 | aiEditTargetRange 自动记录选区 |
| 选区信息条出现 | 显示"已选 N 字" + 前 30 字预览 |
| 发送"让这段更简洁" | AI 返回内容，**精确替换原选区**（不是当前 selection） |
| AI 返回多方案（"方案一/方案二"） | 显示 4 个方案卡，点击采用替换 |
| AI 加载中消息气泡 | 透明度 0.3，显示流式光标 ▍ |
| AI 思考区 | 浅紫边框 + 蓝色阴影呼吸 + 旋转时钟 |
| AI 思考完成 | 自动折叠为"已思考 N 秒"，灯泡图标 |
| 流式字符 | 段落化（双换行 → `</p><p>`） |
| 消息操作按钮 | 复制 + 插入 + 已插入 badge |
| 拖拽条 | 调整宽度 280-600px，无 hover 高亮 |
| 8 快捷卡 | 大卡（4 个）+ 小链接（6 个） |
| input-hint | 动态切换"编辑模式：AI 结果可一键插入文档" / "基于当前文档 · Enter 发送" |

## 27. 用户关键诉求映射

| 用户原话 | 7/6 之前实现 |
|----------|--------------|
| "编辑模式下的功能和模式输出优化一下，能够真正修改md文档" | `aiEditTargetRange` + `handleEditModeResult` 7/5 重构 |
| "编辑模式应该学习一下豆包的md编辑" | 7/5 09:15 system prompt 学习豆包（不要求 result 标签） |
| "现在编辑模式下会输出result的标签" | 7/5 09:15 移除 result 标签要求 |
| "5、聊天侧栏的编辑模式有问题，逻辑不对" | 7/5 08:53 多方案不立即插入 + 清洗无用前缀 |
| "大模型生成的无关内容也进入了编辑区域内" | 7/5 handleEditModeResult 清洗 + multi-option 检测 |
| "Mode toggle button should use home page theme color" | `--primary-gradient` / `--primary-color` CSS 变量 |
| "AI panel should be resizable without hover highlight effect" | `.ai-resize-handle` 无 hover 高亮 |
| "AI editing local loading should be transparent (0.3 opacity)" | `.msg-loading-transparent { opacity: 0.3 }` |
| "Smart edit should not clear original content until AI outputs usable content" | `if (!cleanContent) return` 早返回 |
| "If AI outputs multiple versions/suggestions, show a UI for user to select" | 7/5 08:53 `optionMatches` + `.ai-option-item` |

---

# 🔴 重要补充 #3：AI 浮窗（编辑页改写/智能编辑）完整设计

> **用户原话**："编辑页改写或智能编辑后的浮窗效果呢？"
> 之前补充 #1 给了 `aiFloatPanel` 的核心代码，但**遗漏了大量交互细节**：
> 1. 智能位置计算（placeAbove + clamp 视口边界）
> 2. 三态样式（streaming / done / aborted / error + Diff 模式）
> 3. 思考链完整 UI（折叠 + 旋转时钟 + 浅紫边框 + 蓝色阴影）
> 4. 多方案卡（pickAiFloatOption + optionMatches 检测）
> 5. 淡黄高亮的"立即折叠" + 1.2s 渐隐动画
> 6. "重新生成" 复用位置（preservePosition）
> 7. 蓝色 selection 重复问题修复

## 28. ai-float-panel 核心设计要点

### 28.1 位置算法（关键：选区上方优先）

```ts
function openAiFloatPanel(
  command: AiFloatCommand,
  range: { from: number; to: number },
  originalText: string,
  insertAtCursor = false,
  preservePosition = false,  // 重新生成时复用上次位置
) {
  if (!editor.value) return
  const view = editor.value.view
  const start = view.coordsAtPos(range.from)
  const end = view.coordsAtPos(range.to)
  const selTop = Math.min(start.top, end.top)
  const selBottom = Math.max(start.bottom, end.bottom)
  const selLeft = Math.min(start.left, end.left)
  const selRight = Math.max(start.right, end.right)
  const selCenterX = (selLeft + selRight) / 2

  // 浮窗尺寸（与 CSS 对应）
  const PANEL_W = 380
  const PANEL_MAX_H = Math.min(560, window.innerHeight * 0.6)
  const GAP = 12

  // 关键：优先放选区上方；若上方空间不足则放下方
  const spaceAbove = selTop
  const placeAbove = spaceAbove >= PANEL_MAX_H + GAP || spaceAbove >= window.innerHeight / 2

  let top: number
  if (placeAbove) {
    top = selTop - GAP  // CSS 用 transform: translateY(-100%) 偏移
  } else {
    top = selBottom + GAP
  }

  // 关键：水平 clamp 到视口（保留 24px 安全边距）
  const halfW = PANEL_W / 2
  let left = selCenterX
  const minLeft = halfW + 24
  const maxLeft = window.innerWidth - halfW - 24
  if (left < minLeft) left = minLeft
  if (left > maxLeft) left = maxLeft

  // 关键：重新生成复用上次的 position（不跳回初始位置）
  const reusePosition =
    preservePosition && aiFloatPanel.value.visible && aiFloatPanel.value.position
  const finalTop = reusePosition ? aiFloatPanel.value.position.top : top
  const finalLeft = reusePosition ? aiFloatPanel.value.position.left : left
  const finalPlaceAbove = reusePosition ? aiFloatPanel.value.placeAbove : placeAbove

  aiFloatPanel.value = {
    visible: true,
    command,
    title: AI_COMMAND_TITLES[command] || 'AI 操作',
    status: 'streaming',
    text: '',
    thinking: '',
    thinkingDone: false,
    thinkingStartedAt: Date.now(),
    thinkingEndedAt: undefined,
    thinkingDurationSec: 0,
    options: [],
    originalText,
    originalRange: range,
    insertAtCursor,
    position: { top: finalTop, left: finalLeft },
    placeAbove: finalPlaceAbove,
    diffMode: true,  // 默认开启 diff 预览
    pendingDiff: false,
    diffState: null,
  }
}
```

### 28.2 完整 CSS（位置 + 玻璃拟态 + 三态动画）

```css
/* === AI 浮窗 === */
.ai-float-panel {
  position: absolute;  /* 关键：absolute 不是 fixed，position.top/left 是相对 .md-main */
  z-index: 50;
  min-width: 360px;
  max-width: 480px;
  background: rgba(255, 255, 255, 0.98);  /* 玻璃拟态 */
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 12px;
  box-shadow:
    0 8px 32px rgba(99, 102, 241, 0.15),
    0 2px 8px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(8px);  /* 玻璃效果 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-size: 13px;
  animation: floatPanelEnter 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes floatPanelEnter {
  from { opacity: 0; transform: translate(-50%, calc(-100% - 8px)) scale(0.95); }
  to   { opacity: 1; transform: translate(-50%, calc(-100% - 14px)) scale(1); }
}

/* 关键：浮窗默认显示在选区上方（用 transform 偏移） */
.ai-float-panel { transform: translate(-50%, calc(-100% - 14px)); }
.ai-float-panel--below { transform: translate(-50%, 0); }  /* 下方变体 */

.ai-float-panel--options { min-width: 320px; }

/* 头部 */
.ai-float-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.04), rgba(139, 92, 246, 0.04));
  font-weight: 600;
  font-size: 12px;
  color: #4f46e5;
}
.ai-float-title { display: inline-flex; align-items: center; gap: 4px; }
.ai-float-status { display: inline-flex; align-items: center; gap: 8px; }

/* 状态点（流式中三个跳动的点） */
.ai-float-streaming { display: inline-flex; gap: 3px; }
.ai-float-streaming .dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #6366f1;
  animation: pulse 1.2s infinite ease-in-out;
}
.ai-float-streaming .dot:nth-child(2) { animation-delay: 0.2s; }
.ai-float-streaming .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes pulse {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* 状态文字 */
.ai-float-done { color: #10b981; font-size: 11px; }
.ai-float-aborted { color: #ef4444; font-size: 11px; }
.ai-float-error { color: #ef4444; font-size: 11px; }

.ai-float-close {
  background: none; border: none; cursor: pointer;
  color: #9ca3af; font-size: 18px; line-height: 1;
  padding: 0 6px; border-radius: 4px;
  transition: all 0.15s;
}
.ai-float-close:hover { background: rgba(0, 0, 0, 0.05); color: #6b7280; }

/* 浮窗思考链（豆包风格 - 浅紫边框 + 蓝色阴影呼吸） */
.ai-float-thinking {
  margin: 8px 14px 0;
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 8px;
  background: #faf5ff;
  overflow: hidden;
}
.ai-float-thinking.thinking {
  animation: thinking-breathe 1.5s ease-in-out infinite;
}
@keyframes thinking-breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(99, 102, 241, 0); }
  50% { box-shadow: 0 0 12px rgba(99, 102, 241, 0.25); }
}
.thinking-head {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: #6d28d9;
  user-select: none;
}
.thinking-head:hover { background: rgba(167, 139, 250, 0.1); }
.thinking-icon { width: 14px; height: 14px; }
.thinking-icon.spinning { animation: spin 1.2s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.thinking-title { flex: 1; font-weight: 500; }
.thinking-toggle { font-size: 10px; opacity: 0.6; }

.thinking-body {
  padding: 8px 12px;
  border-top: 1px solid rgba(167, 139, 250, 0.2);
  max-height: 200px;
  overflow-y: auto;
}
.thinking-text {
  font-size: 12px;
  line-height: 1.5;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}
.thinking-cursor::after {
  content: '▍';
  animation: cursor-blink 0.8s infinite;
  color: #8b5cf6;
}
@keyframes cursor-blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}

/* 主体（流式文本区） */
.ai-float-body {
  padding: 10px 14px;
  min-height: 56px;
  max-height: 280px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}
.ai-float-placeholder { color: #9ca3af; font-style: italic; }
.ai-float-content { color: #1f2937; }
.ai-float-cursor::after {
  content: '▍';
  animation: cursor-blink 0.8s infinite;
  color: #6366f1;
}

/* 多方案选择 */
.ai-float-options { padding: 8px 12px; max-height: 320px; overflow-y: auto; }
.ai-float-options-hint {
  color: #4f46e5; font-size: 11px;
  font-weight: 600; margin-bottom: 6px;
}
.ai-float-option {
  padding: 10px 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.ai-float-option:hover { background: #f3f4f6; border-color: #6366f1; transform: translateX(2px); }
.ai-float-option-title { font-weight: 600; color: #4f46e5; font-size: 12px; margin-bottom: 4px; }
.ai-float-option-content { font-size: 12px; color: #4b5563; line-height: 1.5; }

/* 底部操作按钮 */
.ai-float-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-top: 1px solid #f0f0f0;
  background: #fafbfc;
}
.ai-float-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ai-float-btn--primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-color: transparent;
}
.ai-float-btn--primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.ai-float-btn--ghost:hover:not(:disabled) { background: #f3f4f6; border-color: #6366f1; color: #4f46e5; }
.ai-float-btn--icon { padding: 6px 8px; }
.ai-float-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Diff 模式：接受/拒绝按钮 */
.ai-float-diff-hint {
  display: flex; align-items: center; gap: 6px;
  margin-right: auto;  /* 推到左侧，让接受/拒绝按钮在右侧 */
  font-size: 11px; color: #6b7280;
}
.diff-icon-removed { color: #ef4444; font-weight: 600; font-size: 13px; }
.diff-icon-added { color: #10b981; font-weight: 600; font-size: 13px; }
```

## 29. 完整 ai-float-panel 模板（替换当前 rewriteModal）

```html
<!-- AI 浮窗结果区（独立于侧栏聊天，选区上方显示 AI 输出 + 接受/拒绝） -->
<transition name="floatPanel">
  <div
    v-if="aiFloatPanel.visible"
    :class="['ai-float-panel', {
      'ai-float-panel--options': aiFloatPanel.options.length > 0,
      'ai-float-panel--below': !aiFloatPanel.placeAbove,
    }]"
    :style="{ top: aiFloatPanel.position.top + 'px', left: aiFloatPanel.position.left + 'px' }"
    @mousedown.stop
  >
    <!-- 头部 -->
    <div class="ai-float-header">
      <span class="ai-float-title">✨ {{ aiFloatPanel.title }}</span>
      <div class="ai-float-status">
        <span v-if="aiFloatPanel.status === 'streaming'" class="ai-float-streaming">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </span>
        <span v-else-if="aiFloatPanel.status === 'done'" class="ai-float-done">✓ 已完成</span>
        <span v-else-if="aiFloatPanel.status === 'aborted'" class="ai-float-aborted">已停止</span>
        <span v-else-if="aiFloatPanel.status === 'error'" class="ai-float-error">错误</span>
        <button class="ai-float-close" @click="closeAiFloatPanel" title="关闭 (Esc)">×</button>
      </div>
    </div>

    <!-- 思考链（豆包风格 - 浅紫边框 + 蓝色阴影呼吸） -->
    <div
      v-if="aiFloatPanel.thinking || aiFloatPanel.thinkingDone"
      class="ai-float-thinking"
      :class="{
        thinking: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone,
        done: aiFloatPanel.thinkingDone
      }"
    >
      <div class="thinking-head" @click="aiFloatPanel.thinkingDone && (aiFloatPanel.thinkingExpanded = !aiFloatPanel.thinkingExpanded)">
        <svg
          class="thinking-icon"
          :class="{ spinning: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone }"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 6v6l4 2"/>
        </svg>
        <span class="thinking-title">
          {{
            aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone
              ? '正在思考...'
              : `已思考 ${aiFloatPanel.thinkingDurationSec} 秒`
          }}
        </span>
        <span v-if="aiFloatPanel.thinkingDone" class="thinking-toggle">▾</span>
      </div>
      <div
        v-show="aiFloatPanel.thinkingExpanded || (aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone)"
        class="thinking-body"
      >
        <div class="thinking-text">
          {{ aiFloatPanel.thinking || '...' }}
          <span
            v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone"
            class="thinking-cursor"
          >▍</span>
        </div>
      </div>
    </div>

    <!-- 单方案流式结果 -->
    <div v-if="aiFloatPanel.options.length === 0" class="ai-float-body">
      <div
        v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.text"
        class="ai-float-placeholder"
      >
        AI 撰写中…
      </div>
      <div v-else class="ai-float-content">
        {{ aiFloatPanel.text }}
        <span
          v-if="aiFloatPanel.status === 'streaming'"
          class="ai-float-cursor"
        >▍</span>
      </div>
    </div>

    <!-- 多方案选择 -->
    <div v-else class="ai-float-options">
      <div class="ai-float-options-hint">
        AI 提供了 {{ aiFloatPanel.options.length }} 个方案，点击选择：
      </div>
      <div
        v-for="(opt, idx) in aiFloatPanel.options"
        :key="idx"
        class="ai-float-option"
        @click="pickAiFloatOption(idx)"
      >
        <div class="ai-float-option-title">{{ opt.title }}</div>
        <div class="ai-float-option-content">
          {{ opt.content.substring(0, 120) }}{{ opt.content.length > 120 ? '...' : '' }}
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div v-if="aiFloatPanel.options.length === 0" class="ai-float-footer">
      <!-- Diff 模式：显示接受/拒绝 -->
      <template v-if="aiFloatPanel.pendingDiff">
        <div class="ai-float-diff-hint">
          <span class="diff-icon-removed">⊘</span> 原文
          <span class="diff-icon-added">+</span> AI 新内容
        </div>
        <button class="ai-float-btn ai-float-btn--ghost" @click="rejectDiff">✗ 拒绝</button>
        <button class="ai-float-btn ai-float-btn--primary" @click="acceptDiff">✓ 采用 ↵</button>
      </template>
      <!-- 流式中 -->
      <button
        v-else-if="aiFloatPanel.status === 'streaming'"
        class="ai-float-btn ai-float-btn--ghost"
        @click="stopAiFloatResult"
      >停止</button>
      <!-- 完成 -->
      <template v-else>
        <!-- 关键：重新生成是图标按钮（更不显眼），用 preservePosition 复用位置 -->
        <el-tooltip content="重新生成" placement="top">
          <button
            class="ai-float-btn ai-float-btn--icon"
            :disabled="!aiFloatPanel.text"
            @click="regenerateAiFloatResult"
          >↻</button>
        </el-tooltip>
        <button
          class="ai-float-btn ai-float-btn--primary"
          :disabled="!aiFloatPanel.text"
          @click="acceptAiFloatResult"
        >采用 ↵</button>
      </template>
    </div>
  </div>
</transition>
```

## 30. 完整 acceptAiFloatResult（ProseMirror tr 精确操作）

参考 `me_record.md:143821` 7/5 最终版：

```ts
function acceptAiFloatResult() {
  if (!editor.value) return

  // 防御性：清除孤儿 </think>
  let cleanText = aiFloatPanel.value.text
    .replace(/<\/think>/gi, '')
    .replace(/<\|thinking\|>/gi, '')
    .replace(/<\|reasoning\|>/gi, '')
    .replace(/<\|thought\|>/gi, '')
    .replace(/<think>/gi, '')

  // 提取 <result> 标签
  const resultMatch = cleanText.match(/<result>([\s\S]*?)<\/result>/i)
  if (resultMatch && resultMatch[1]) {
    cleanText = resultMatch[1].trim()
  } else {
    cleanText = cleanText
      .replace(/<\/?result>/gi, '')
      .replace(/^[\s\n]+/, '')
      .trim()
  }
  // 去无用前缀
  cleanText = cleanText
    .replace(/^(好的|以下是|根据|按照|修改后|编辑后|说明|解释|建议|方案|推荐)[：:,，\s]*/i, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/^>\s*/gm, '')
    .trim()

  if (!cleanText) {
    ElMessage.warning('AI 未生成可用内容')
    return
  }

  // ====== 检测多方案 ======
  const optionMatches = cleanText.match(
    /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]\s*[\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]|$))/gi
  )
  if (optionMatches && optionMatches.length > 1) {
    const options = optionMatches.map((opt: string) => {
      const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]/)
      const title = titleMatch ? titleMatch[1] : '方案'
      const content = opt
        .replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]\s*/, '')
        .trim()
      return { title, content }
    })
    aiFloatPanel.value.options = options
    aiFloatPanel.value.text = ''  // 隐藏正文，让用户从 options 中选
    return
  }

  // ====== Diff 预览模式（豆包风格）======
  // 7/5 11:36 → 7/6 01:07 ProseMirror tr 直接操作（避免 chain() 副作用）
  const { from, to } = aiFloatPanel.value.originalRange
  if (aiFloatPanel.value.diffMode && !aiFloatPanel.value.insertAtCursor && to > from) {
    const safe = sanitizeAiMarkdown(cleanText)
    const strikeType = editor.value.state.schema.marks?.strike
    const hardBreakType = editor.value.state.schema.nodes?.hardBreak
    if (!strikeType || !hardBreakType) {
      ElMessage.warning('diff 模式不支持（schema 缺失）')
      return
    }
    const docSizeBefore = editor.value.state.doc.content.size
    // 用 ProseMirror tr 直接操作（不调 setSelection，selection 保持原状）
    let tr = editor.value.state.tr
    tr = tr.addMark(from, to, strikeType.create())
    tr = tr.insert(to, hardBreakType.create())
    const textNode = editor.value.state.schema.text(safe)
    tr = tr.insert(to + 1, textNode)
    editor.value.view.dispatch(tr)
    // 记录 diff 状态
    const docSizeAfter = editor.value.state.doc.content.size
    aiFloatPanel.value.diffState = {
      originalText: aiFloatPanel.value.originalText,
      newText: safe,
      removedFrom: from,
      removedTo: to,
      addedStart: to,
      addedEnd: docSizeAfter,
      originalDocSize: docSizeBefore,
    }
    aiFloatPanel.value.status = 'done'
    aiFloatPanel.value.pendingDiff = true
    ElMessage.info('Diff 预览已生成：原文已加删除线，新内容已插入')
    return
  }

  // ====== 非 diff 模式：直接替换/插入 ======
  // 关键：用 originalRange 记录的 from，不用当前 selection
  // —— 用户点"采用"时焦点在浮窗按钮，editor.selection 位置不准确
  // 高亮位置：插入后，ProseMirror 节点 position 不可用 safe.length 估算
  // 用 updateState 后查询新内容位置：from 是新内容起点，docSize 是终点
  const safe = sanitizeAiMarkdown(cleanText)
  if (aiFloatPanel.value.insertAtCursor) {
    // 斜杠生成 / 续写：在 originalRange.from 位置插入
    const pos = from
    editor.value.chain().focus().insertContentAt(pos, safe).run()
    // 获取插入后的真实位置（用 ProseMirror docSize）
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(pos, finalDocSize)
  } else if (to <= from) {
    // 无选区：当前位置插入（fallback）
    const pos = editor.value.state.selection.from
    editor.value.chain().focus().insertContentAt(pos, safe).run()
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(pos, finalDocSize)
  } else {
    // 替换原文
    editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
    // 替换后新内容范围 [from, from + 新插入内容长度]
    // 用 docSize - from 是新内容长度（如果有删旧+加新）
    const finalDocSize = editor.value.state.doc.content.size
    setAiInsertHighlight(from, finalDocSize)
  }
  ElMessage.success('已采用')
  aiFloatPanel.value.visible = false
}
```

## 31. 关键修复：acceptDiff / rejectDiff（ProseMirror tr 操作）

参考 `me_record.md:28306` 7/6 01:07 最终版：

```ts
// Diff 模式：接受（删原文 + 保留新文 + 1.2s 淡黄高亮）
function acceptDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo, addedStart } = aiFloatPanel.value.diffState

  // 1. 删原文（带 strike mark）
  try {
    const tr = editor.value.state.tr.delete(removedFrom, removedTo)
    editor.value.view.dispatch(tr)
  } catch (e) { console.warn('[acceptDiff] delete original failed', e) }

  // 2. 删 hardBreak（紧跟原文后）
  try {
    const docSize = editor.value.state.doc.content.size
    let pos = removedFrom
    while (pos < docSize) {
      const nodeAt = editor.value.state.doc.nodeAt(pos)
      if (!nodeAt) break
      if (nodeAt.type.name === 'hardBreak') {
        const tr = editor.value.state.tr.delete(pos, pos + nodeAt.nodeSize)
        editor.value.view.dispatch(tr)
        break
      } else {
        break
      }
    }
  } catch (e) { console.warn('[acceptDiff] cleanup hardBreak failed', e) }

  // 3. 给新文加 highlight mark（淡黄）→ 1.2s 后移除
  const newFrom = removedFrom
  const newTo = removedFrom + aiFloatPanel.value.diffState.newText.length
  setAiInsertHighlight(newFrom, newTo)

  // 4. 关闭浮窗
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.success('已采用新内容')
}

// Diff 模式：拒绝（删新文 + 取消 strike）
function rejectDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo, addedStart, addedEnd } = aiFloatPanel.value.diffState

  // 1. 取消原文的 strike mark
  try {
    const strikeType = editor.value.state.schema.marks?.strike
    if (strikeType) {
      const tr = editor.value.state.tr.removeMark(removedFrom, removedTo, strikeType)
      editor.value.view.dispatch(tr)
    }
  } catch (e) { console.warn('[rejectDiff] remove strike failed', e) }

  // 2. 删新文（含 hardBreak + newText）
  try {
    const tr = editor.value.state.tr.delete(addedStart, addedEnd)
    editor.value.view.dispatch(tr)
  } catch (e) { console.warn('[rejectDiff] delete new failed', e) }

  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.info('已恢复原文')
}
```

## 32. 关键修复：淡黄高亮 mark（不用 setTextSelection）

参考 `me_record.md:146289` 7/5 14:48 关键修复：

**问题根因**：`applyAiInsertHighlightsViaMark` 用 `setTextSelection` 改变浏览器 selection → 出现蓝色 selection
**修复**：用 `tr.addMark` 一次性设置所有 mark（不触发 selection change）

```ts
/** Fallback: 用 mark 实现（关键：不调 setTextSelection，避免蓝色高亮） */
function applyAiInsertHighlightsViaMark() {
  if (!editor.value) return
  try {
    const { state } = editor.value
    const highlightType = state.schema.marks?.highlight
    if (!highlightType) return
    // 用 transaction 一次性设置所有 mark（不触发 selection change）
    let tr = state.tr
    aiInsertHighlights.value.forEach(r => {
      if (r.to > r.from) {
        tr = tr.addMark(r.from, r.to, highlightType.create({ color: '#fef3c7' }))
      }
    })
    editor.value.view.dispatch(tr)
  } catch (e) {
    console.warn('[applyAiInsertHighlightsViaMark] 失败', e)
  }
}
```

## 33. 关键修复：去掉蓝色 selection 重复

参考 `me_record.md:145816-145874` 7/5 14:39 关键修复：

**用户原话**："你说的对——淡黄高亮已经是'已插入'的视觉表达，再加 setTextSelection 让它变蓝色是**重复表达**"

```ts
// ❌ 之前：重复表达
editor.value.chain().focus().insertContentAt(pos, safe).run()
editor.value.chain().focus().setTextSelection({ from: pos, to: finalDocSize }).run()  // 删除
setAiInsertHighlight(pos, finalDocSize)

// ✅ 现在：只用淡黄高亮
editor.value.chain().focus().insertContentAt(pos, safe).run()
setAiInsertHighlight(pos, finalDocSize)  // 单一明确的视觉反馈
```

**修复后**："采用"行为：
1. AI 输出完成 → 浮窗显示
2. 点"✓ 采用"
3. 新内容插入到对应位置
4. **淡黄高亮**新内容（单一明确视觉反馈）
5. 浮窗关闭
6. 用户点击编辑器任意位置 → 淡黄高亮消失

## 34. 关键 UX：浮窗按钮简化（"采用"为主 + "重新生成"为图标）

参考 `me_record.md:146082` 7/5 14:48 关键设计：

```html
<!-- ❌ 之前：两个等权重按钮（"重新生成" + "采用"） -->
<button @click="regenerateAiFloatResult">↻ 重新生成</button>
<button @click="acceptAiFloatResult">采用 ↵</button>

<!-- ✅ 现在：默认只显示"采用"，"重新生成"是图标按钮（更不显眼） -->
<el-tooltip content="重新生成" placement="top">
  <button class="ai-float-btn ai-float-btn--icon" @click="regenerateAiFloatResult">↻</button>
</el-tooltip>
<button class="ai-float-btn ai-float-btn--primary" @click="acceptAiFloatResult">采用 ↵</button>
```

**关键**：`regenerateAiFloatResult` 传 `preservePosition: true`，**复用上次浮窗位置**（不跳回初始位置）

## 35. pickAiFloatOption（多方案点击采用）

```ts
function pickAiFloatOption(idx: number) {
  const opt = aiFloatPanel.value.options[idx]
  if (!opt || !editor.value) return
  const safe = sanitizeAiMarkdown(opt.content)
  const { from, to } = aiFloatPanel.value.originalRange
  if (to > from) {
    editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
  } else {
    editor.value.chain().focus().insertContentAt(from, safe).run()
  }
  // 用 ProseMirror docSize 计算新内容位置
  const finalDocSize = editor.value.state.doc.content.size
  setAiInsertHighlight(from, finalDocSize)
  ElMessage.success('已采用方案')
  aiFloatPanel.value.visible = false
}
```

## 36. regenerateAiFloatResult / stopAiFloatResult / closeAiFloatPanel

```ts
/** 重新生成（复用浮窗位置） */
function regenerateAiFloatResult() {
  if (!editor.value || aiFloatPanel.value.status === 'streaming') return
  // 重置浮窗状态
  aiFloatPanel.value.text = ''
  aiFloatPanel.value.thinking = ''
  aiFloatPanel.value.thinkingDone = false
  aiFloatPanel.value.thinkingStartedAt = Date.now()
  aiFloatPanel.value.options = []
  aiFloatPanel.value.status = 'streaming'
  // 重新调 runEditAi，preservePosition=true
  runEditAi(
    aiFloatPanel.value.command,
    aiFloatPanel.value.originalRange,
    aiFloatPanel.value.originalText,
    aiFloatPanel.value.insertAtCursor,
    undefined,
    undefined,
    { preservePosition: true }
  )
}

function stopAiFloatResult() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  aiFloatPanel.value.status = 'aborted'
}

function closeAiFloatPanel() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  // 关闭时：如果有 pending diff，默认拒绝（回滚原文）
  if (aiFloatPanel.value.pendingDiff) {
    rejectDiff()
    return
  }
  aiFloatPanel.value.visible = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.pendingDiff = false
}

/** Esc 键关闭浮窗 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && aiFloatPanel.value.visible) {
    closeAiFloatPanel()
  }
  // ... 其他键盘处理
}
```

## 37. watch 同步 editAi 流式文本到浮窗

```ts
// 实时同步 editAi 流式文本到浮窗（核心 watch）
watch(
  () => [
    editAi.status.value,
    editAi.messages.value[editAi.messages.value.length - 1]?.parts?.length,
  ],
  () => {
    const last = editAi.messages.value[editAi.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    const thinkingPart = (last.parts || []).find((p: any) => p.type === 'reasoning')
    if (thinkingPart) aiFloatPanel.value.thinking = thinkingPart.text || ''
    if (textPart) {
      aiFloatPanel.value.text = textPart.text || ''
      if (textPart.state === 'done' && !aiFloatPanel.value.thinkingDone) {
        aiFloatPanel.value.thinkingDone = true
        aiFloatPanel.value.thinkingEndedAt = Date.now()
        aiFloatPanel.value.thinkingDurationSec = Math.floor(
          (aiFloatPanel.value.thinkingEndedAt! - (aiFloatPanel.value.thinkingStartedAt || 0)) / 1000
        )
      }
    }
    if (textPart?.state === 'done') {
      aiFloatPanel.value.status = aiFloatPanel.value.status === 'aborted' ? 'aborted' : 'done'
    }
  },
  { flush: 'post' }
)
```

## 38. 完整 AI 浮窗交互流（按时间线）

### 38.1 完整流程

```
[用户在编辑器选中文本]
  ↓
[点气泡菜单的 "✨ AI 改写" / "智能编辑"]
  ↓
[handleAiAction] → runEditAi('rewrite', range, sourceText, false)
  ↓
[runEditAi 内部]：
  1. 检查 editAi 状态，stop 旧流
  2. editAi.clear() 清空历史（避免污染）
  3. openAiFloatPanel('rewrite', range, sourceText, false)
     - 计算 placeAbove（上 / 下方）
     - 水平 clamp 到视口
     - 重置浮窗状态（visible, status, text, thinking, options...）
  4. 构造 systemPrompt
  5. editAi.sendUserMessage(question, systemPrompt, editor.getText())
  ↓
[浮窗显示在选区上方，状态 streaming]
  ↓
[SSE 流式接收，watch 同步 text + thinking 到浮窗]
  ↓
[流结束 → status = 'done']
  ↓
[用户点 "采用" / "拒绝" / "重新生成" / "×" 关闭]
```

### 38.2 三种结束状态

| 状态 | 触发 | 视觉 | 后续 |
|------|------|------|------|
| **`done`** | AI 流正常结束 | "✓ 已完成" | 可点"采用"/"重新生成" |
| **`aborted`** | 用户点"停止" | "已停止" | 已生成内容仍可采用 |
| **`error`** | 网络错误/401 | "错误" | 灰色 + 不可采用 |

### 38.3 三种交互结果

| 操作 | 行为 |
|------|------|
| **采用**（默认非 Diff 模式） | 直接替换原选区 / 插入光标处 + 1.2s 淡黄高亮 |
| **采用**（Diff 模式） | 原文删除 + 保留新文 + 1.2s 淡黄高亮 |
| **拒绝**（Diff 模式） | 删新文 + 取消原文 strike + 完全恢复 |
| **重新生成** | 复用浮窗位置 + 重新跑 AI（**不跳回**） |
| **关闭**（×/Esc） | 流式时 abort + 有 pending diff 时自动 reject |

## 39. 浮窗位置算法详解（3 大关键点）

### 39.1 选区上方优先（豆包风格）

```ts
const PANEL_W = 380
const PANEL_MAX_H = Math.min(560, window.innerHeight * 0.6)
const GAP = 12
const spaceAbove = selTop
const placeAbove = spaceAbove >= PANEL_MAX_H + GAP || spaceAbove >= window.innerHeight / 2
```

**为什么？**：用户视线先看到原文，再看上方浮窗（类似 Figma/Notion 浮窗）。

### 39.2 水平 clamp 到视口

```ts
const halfW = PANEL_W / 2
let left = selCenterX
const minLeft = halfW + 24    // 24px 安全边距
const maxLeft = window.innerWidth - halfW - 24
if (left < minLeft) left = minLeft
if (left > maxLeft) left = maxLeft
```

**为什么？**：选区在屏幕最右侧时，浮窗不能溢出视口。

### 39.3 重新生成复用位置

```ts
const reusePosition = preservePosition && aiFloatPanel.value.visible && aiFloatPanel.value.position
const finalTop = reusePosition ? aiFloatPanel.value.position.top : top
const finalLeft = reusePosition ? aiFloatPanel.value.position.left : left
const finalPlaceAbove = reusePosition ? aiFloatPanel.value.placeAbove : placeAbove
```

**为什么？**：用户已调整好浮窗位置，重新生成不应跳回初始位置。

## 40. 浮窗 CSS 关键样式说明

### 40.1 absolute 定位（非 fixed）

```css
.ai-float-panel {
  position: absolute;  /* 不是 fixed */
  /* position.top/left 是相对最近的 positioned 祖先 (.md-main) */
}
```

**为什么用 absolute**：
- 浮窗跟随 `.md-main` 容器滚动（而不是视口）
- 切换全屏时浮窗正确重定位
- 多文档 tab 切换时浮窗自动隐藏

### 40.2 玻璃拟态

```css
.ai-float-panel {
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.15), 0 2px 8px rgba(0, 0, 0, 0.08);
}
```

**为什么**：现代 AI 工具风格（豆包/Notion AI/ChatGPT），质感高级。

### 40.3 思考链浅紫呼吸

```css
.ai-float-thinking.thinking {
  animation: thinking-breathe 1.5s ease-in-out infinite;
}
@keyframes thinking-breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(99, 102, 241, 0); }
  50% { box-shadow: 0 0 12px rgba(99, 102, 241, 0.25); }
}
```

**为什么**：用户能感知"AI 还在思考"，不会以为卡死。

### 40.4 流式光标

```css
.ai-float-cursor::after {
  content: '▍';
  animation: cursor-blink 0.8s infinite;
  color: #6366f1;
}
@keyframes cursor-blink {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}
```

**为什么**：实时流式感，类似 ChatGPT 打字效果。

## 41. 验证清单

| 验证项 | 期望行为 |
|--------|----------|
| 选中文本 → 浮窗位置 | 选区**正上方**（视口足够时） |
| 选区在屏幕底部 | 浮窗自动转**下方**显示 |
| 选区在屏幕最右 | 浮窗 clamp 到视口（不溢出） |
| 流式输出 | 浮窗内文字逐字 + 蓝色光标闪烁 |
| 思考链 | 浅紫边框 + 旋转时钟 + 蓝色阴影呼吸 |
| 思考完成 | 自动折叠为"已思考 N 秒" |
| 完成后操作按钮 | 左侧"↻"图标 + 右侧"采用 ↵"主按钮 |
| 点击"采用" | 新内容插入 + 1.2s 淡黄高亮（无蓝色 selection） |
| 点击"↻"重新生成 | 浮窗**不跳位置**，新流开始 |
| AI 返回"方案一/方案二" | 显示 4 个方案卡，点击采用对应方案 |
| Diff 模式 | 原文加删除线 + 新文插入 + "接受/拒绝"按钮 |
| 点击"接受" | 原文删除 + 1.2s 淡黄高亮 |
| 点击"拒绝" | 删新文 + 取消 strike |
| 点击"×"或按 Esc | 浮窗关闭（如有 pending diff 自动拒绝） |
| 点击编辑器其他位置 | 1.2s 后淡黄高亮消失 |

## 42. 用户原始诉求映射

| 用户原话 | 7/6 之前实现 |
|----------|--------------|
| "编辑页改写或智能编辑后的浮窗效果" | `ai-float-panel` 完整实现（豆包风格） |
| "AI 改写 → 采用 → 接受 → 点击新文 → 淡黄渐隐消失（1.2秒）" | `setAiInsertHighlight` + mark fallback + 1.2s 移除 |
| "AI 消息换行过多 / 离边框太近" | 浮窗 padding 10px 14px + line-height 1.6 |
| "AI 改写 → 采用 → 精准替换那段文字" | 用 `aiEditTargetRange` 不用当前 selection |
| "撰写中加个遮盖层" | `aiCoverVisible` 编辑器只读 |
| "AI 改写或相同类型的调用AI生成内容时，要确保在AI生成内容后，点采用按钮，替换的是原文的内容" | `acceptAiFloatResult` 用 `originalRange.from` |
| "智能编辑改写缩写这些按钮，点击后生成后的内容只保留一个采用就可以" | "↻"图标按钮（不显眼）+ "采用"主按钮（突出） |
| "淡黄色高亮的取消效果依然没有修改" | 1.2s setTimeout removeMark + 全局 mousedown 监听 |

---

**更新完成**。第 28-42 节共 14 大块，覆盖了所有"AI 浮窗"的设计细节，包括 7/5 关键修复（去蓝色 selection / 重新生成复用位置）和 7/6 最终版（ProseMirror tr 操作）。
