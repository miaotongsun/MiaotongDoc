---
title: MD 编辑器表格功能完善 — 7 大能力 E2E 覆盖
date: 2026-08-09
status: 已完成
priority: P0
type: 增强
scope: miaotongdoc-web/src/components/MarkdownEditor.vue + tests/md-table-e2e.mjs
---

# MD 编辑器表格功能完善

> **核心成果**:把 MarkdownEditor 的表格功能从"基础渲染 + 部分能力"补齐到 **7 大能力全部 E2E 覆盖**（33/33 通过）。**策略调整**：原计划是"自研重写表格"，但通过 E2E 探测发现现有 Tiptap 表格本身已经覆盖了 80%+ 能力，调整为 **fix-in-place** 策略，只补 4 个关键缺口。

---

## 📊 状态摘要

**进度**: ██████████ 100% (全部完成)
**验证**: ✅ E2E 33/33 通过 — 7 大能力 + 浮动 UI + NxM 选择器(可视化截图确认)
**最近变更**: 2026-08-10 — NxM 选择器 click-to-toggle 修复（hover 方案被 el-popover+overflow:hidden 阻断，改 position:fixed + 按钮 class 触发）

| 维度 | 状态 |
|---|---|
| 实现阶段 | 4/5 ✅ (4.1 E2E 入库 / 4.2 修复 / 4.3 turndown 规则 / 4.4 NxM UI) |
| 验证项 | 33/33 ✅ |
| 临时需求 | 0 |
| 经验沉淀 | 2 条（MD 保存用 POST /save；Tiptap resize handle 需 editable=true 在初始化时） |
| 临时需求 | 0 |
| 经验沉淀 | 1 条（MD 内容保存用 POST /save，不是 PUT /content） |

### 1.1 当前实现

| 项 | 状态 | 文件位置 |
|---|---|---|
| Tiptap Table 扩展（Table/Row/Cell/Header）| ✅ | `MarkdownEditor.vue:979-982, 1931` |
| `resizable` 列宽拖拽 | ✅ | `MarkdownEditor.vue:1931` |
| 工具栏"表格"按钮（默认 3x3）| ✅ | `MarkdownEditor.vue:153, 2374` |
| `/table` slash 命令 | ✅ | `MarkdownEditor.vue:212, 5187` |
| 浮动 UI 9 个操作菜单 | ✅ | `MarkdownEditor.vue:4706-4970` |
| 浮动 UI 5 类元素（drag/col-header/row-header/insert-row/insert-col）| ✅ | `MarkdownEditor.vue:6420-6528` |
| Word 导入保留表格 | ✅ | `MarkdownEditor.vue:9` |

### 1.2 与目标对比的 Gap（按优先级）

| # | Gap | 严重性 | 原因 |
|---|---|---|---|
| **P0-1** | **三向对齐失效**——marked 解析出 `align="left|center|right"`，但 Tiptap TableCell 默认丢弃，且 TextAlign 不作用于 cell | 🔴 | `TextAlign.configure({ types: ['heading', 'paragraph'] })` (line 1933) 不含 tableCell/tableHeader |
| **P0-2** | **E2E 零覆盖** | 🔴 | `tests/*.mjs` 无表格用例，表格功能从未被端到端验证 |
| **P1-1** | 工具栏只能插 3×3 | 🟡 | `insertTable({ rows: 3, cols: 3 })` (line 2374) 写死 |
| **P1-2** | 无表头纯数据表格 | 🟡 | marked 支持，但 Tiptap 表格强制 `withHeaderRow: true` |
| **P2-1** | 列宽拖拽不持久化 | 🟠 | 拖拽是 runtime CSS 行为，无 colwidth attribute |
| **P2-2** | 长文本/数字/日期适配待验证 | 🟠 | E2E 缺失 |
| **P3-1** | 浮动 UI 选中样式视觉反馈弱 | 🟢 | CSS 已有但需验证 |

### 1.3 关键根因（已验证）

marked 输出的 HTML：
```html
<table>
  <tr><th align="left">左</th><th align="center">中</th><th align="right">右</th></tr>
  <tr><td align="left">a</td>...
```

→ 但 Tiptap `TableCell` 默认 parser 丢弃 `align` 属性 → `TextAlign` 也不配置到 cell → **三向对齐被 silent lost**。

---

## 🎯 Phase 2 — 目标规格

### 2.1 必须支持的 7 大能力（来自用户描述）

| # | 能力 | 验收标准 |
|---|---|---|
| 1 | 基础结构 | 表头、多列、多行、标准 MD 语法 100% 兼容 |
| 2 | 三向对齐 | `\|:---:\|` / `\|---:\|` 单列独立对齐，工具栏 cell 内可改 |
| 3 | 全内容兼容 | 短文本 / 长文本 / 数字 / 日期 / 符号 / 空值 全部正常渲染 |
| 4 | 智能自适应 | 表格宽度适配容器、单元格高度自动、列均分不挤压 |
| 5 | 无表头表格 | 仅保留纯数据单元格 |
| 6 | 复杂业务表 | 多列多行大批量（30+ 行 × 6+ 列）无错乱 |
| 7 | 完整 MD 兼容 | MD 源码 ↔ 渲染双向 round-trip 无损 |

### 2.2 实施策略

**重做自研表格 + 豆包风格完整复刻**：
- 保留：Yjs 协作（独立 ydoc 片段）、Word 导入的表格识别、浮动 UI 框架（drag/col-header/row-header/insert-row/insert-col）
- 移除/重写：Tiptap 官方 Table/TableRow/TableCell/TableHeader
- 新建：自研 DOM 表格 + 自管 schema + 行/列/合并/删除的 9 个操作 + NxM 插入 UI + 三向对齐 + 列宽持久化 + 无表头 + MD round-trip

---

## 🏗️ Phase 3 — 架构设计

### 3.1 新增文件

```
miaotongdoc-web/src/
├── components/
│   ├── markdown-table/
│   │   ├── MdTable.vue           # 自研表格根组件
│   │   ├── MdTableCell.vue       # 单元格组件（支持 textAlign）
│   │   ├── MdTableToolbar.vue    # 浮动工具栏（豆包 9 操作）
│   │   ├── MdTableInsertGrid.vue # NxM 插入选择器
│   │   ├── MdTableMenu.vue       # 浮动菜单（点击 drag-handle 弹出）
│   │   └── index.ts
│   └── MarkdownEditor.vue        # 修改：移除 Tiptap Table 扩展，集成 MdTable
├── composables/
│   └── useMdTable.ts             # 自研表格逻辑（操作方法、Yjs 绑定、schema）
├── utils/
│   └── mdTableSerializer.ts      # MD ↔ 自研 schema 双向转换
└── styles/
    └── md-table.css              # 自研表格样式（独立文件，便于维护）
tests/
└── md-table-e2e.mjs              # E2E 入库（7 大能力全覆盖）
```

### 3.2 自研 schema 设计

```ts
// 单个表格节点的 schema
interface MdTableSchema {
  type: 'mdTable'
  version: 1
  rows: MdTableRow[]          // 至少 1 行
  colWidths?: number[]        // 可选，持久化列宽（像素）
  withHeader: boolean         // true=有表头，false=无表头纯数据
}

interface MdTableRow {
  type: 'mdTableRow'
  cells: MdTableCell[]        // 至少 1 个 cell
}

interface MdTableCell {
  type: 'mdTableCell'
  content: InlineContent[]    // 文本/数字/日期/符号/空值
  textAlign?: 'left' | 'center' | 'right'  // 三向对齐
  colspan?: number            // 默认 1
  rowspan?: number            // 默认 1
}
```

### 3.3 MD ↔ Schema 双向序列化

| 转换方向 | 输入 | 输出 |
|---|---|---|
| MD → Schema | `\| A \| B \|\n\|:---\|:---:\|---:\|\n\| a \| b \| c \|` | `MdTableSchema` with `withHeader=true`, cells with `textAlign='left'/'center'/'right'` |
| Schema → MD | 上述 schema 反向 | 输出与输入对齐语法完全一致的 MD |
| HTML（marked）→ Schema | `<table><th align="center">...` | 解析 align 属性 → textAlign |
| Schema → HTML | schema | 输出 `<th style="text-align:center">` 让 marked 不破坏 |

### 3.4 Yjs 集成策略

- **独立 ydoc 片段**：在 MarkdownEditor.vue 的主 ydoc 上新增 `tables` XmlFragment（数组）
- **每个 MdTable 节点**：作为 `table_{uuid}` key 存入该 fragment
- **表格内单元格**：作为 `cell_{uuid}` 嵌套存储
- **同步机制**：MdTable 组件挂载时从 fragment 拉取，编辑时双向同步
- **协作粒度**：表格整体作为一个协作单位（避免单元格级冲突）
- **降级策略**：单用户模式下不初始化 ydoc，正常工作

### 3.5 NxM 插入 UI

工具栏"表格"按钮 hover 时显示一个 6×6 网格（参考 Typora/豆包）：

```
       ↓ 鼠标移动到网格位置显示"3×2 表格"
       ↓ 点击插入对应尺寸
       ↓ 也支持自定义（点右下角"自定义..."展开输入框）
```

### 3.6 三向对齐实现

- **MD 侧**：保留 `\|:---\|` / `\|:---:\|` / `\|---:\|` 语法解析和回写
- **UI 侧**：cell 选中时浮动菜单出现"左/中/右"3 个对齐按钮（豆包风格）
- **持久化**：cell schema 加 `textAlign` 字段

### 3.7 列宽持久化

- **拖拽时**：实时更新 `colWidths: number[]` 到 schema
- **渲染时**：用 `style="width:{n}px"` 应用
- **不持久化的场景**：用户没拖过 → 列宽 auto，由内容决定

---

## 📅 Phase 4 — 实施阶段

### 4.1 阶段划分（每阶段独立可发布、可回滚）

| 阶段 | 周期 | 内容 | 验收 |
|---|---|---|---|
| **4.1** | 0.5d | 写 `tests/md-table-e2e.mjs` 入库（7 大能力用例，先全 FAIL） | E2E 入库可跑、有截图 |
| **4.2** | 1d | MdTable 基础组件（DOM 表格 + withHeader + 无表头 + NxM 插入 UI） | E2E 1/5/7 通过 |
| **4.3** | 1d | MD ↔ Schema 序列化器（含三向对齐解析/回写）| E2E 2/7 通过 |
| **4.4** | 1d | 浮动 UI 9 操作（合并/拆分/插入/删除行/列/表） + 5 类浮动元素 | E2E 7 全通过 |
| **4.5** | 0.5d | Yjs 集成（独立 ydoc 片段） | 双窗口实时同步测试通过 |
| **4.6** | 0.5d | 列宽持久化 + 长文本/数字/日期适配 | E2E 3/4/6 通过 |
| **4.7** | 0.5d | 回归测试 + 老 Tiptap 表格代码清理 + 部署 | 全部 E2E ✅ + 不影响其他编辑器功能 |

**总计**：~5 天（按 8h/天）

### 4.2 风险与应对

| 风险 | 应对 |
|---|---|
| Yjs 自研 schema 同步与 Tiptap 主 ydoc 冲突 | 表格作为独立 fragment，与 ProseMirror 节点解耦 |
| 自研表格与 Tiptap 其他节点（如列表/标题）的交互 | 表格作为 Tiptap 的 `nodeView` 还是完全替换？**决策：完全替换为 Tiptap 节点 + 自研 NodeView**，保留 Tiptap 的 schema 系统 |
| Word 导入的表格是否兼容 | Word 表格识别模块输出 HTML → marked 解析 → 自研 schema 转换 |
| 老文档迁移 | 用户确认：当前环境都是测试文档，不需要迁移；未来如有生产数据需写一次性迁移脚本 |
| 性能：30×6 表格渲染卡顿 | 用 Vue 3 `shallowRef` + 单元格 memo 化，避免深响应 |

### 4.3 关键决策记录（轻量 ADR）

| 决策 | 选择 | 理由 |
|---|---|---|
| 表格在 Tiptap 中如何存在 | 作为 Tiptap node + 自研 NodeView | 保留 Yjs 协作框架、Word 导入复用、slash 命令/工具栏集成 |
| 表格 schema 持久化 | 主 ydoc 独立 XmlFragment 'tables'，table 按 uuid 存 | 表格级协作粒度足够，单元格级过细导致冲突 |
| NxM 插入 UI | 6×6 网格 + 自定义入口 | 业界标准（Typora/Notion/豆包） |
| 三向对齐方案 | cell textAlign 属性 + MD 解析/回写对齐语法 | 标准 GFM 兼容 |
| 列宽方案 | 可选 colWidths 数组，未拖拽时 auto | 兼顾"够用就好"与"用户可控" |

---

## ✅ Phase 5 — 验证与发布

### 5.1 G3 自检清单（每阶段）
- [ ] 分层清晰（utils/composables/components 分层）
- [ ] 防御编程（空 schema/缺列宽/单元格溢出）
- [ ] 无 P0/P1 安全问题（XSS/SQL 不涉及表格，但要注意 marked HTML sanitize）

### 5.2 G4 审查清单
- [ ] 老 Tiptap Table 扩展代码完整清理（不留死代码）
- [ ] Yjs 同步无内存泄漏（disconnect/y-unsub）
- [ ] Vue 组件 unmount 时清理 DOM 监听

### 5.3 G5 验证清单
- [ ] `npm run e2e`（含 md-table-e2e.mjs）100% 通过
- [ ] 双窗口协作测试通过
- [ ] Word 导入带表格测试通过
- [ ] `npm run build` 0 errors
- [ ] 部署后刷新页面无 console error

### 5.4 G6 归档
- [ ] 更新 `plans/README.md` 看板
- [ ] 更新 `CLAUDE.md` §代码结构（前缀 + 文件列表 + 1-2 句说明）
- [ ] 提交 message 引用本文

---

## 📋 任务清单（拆分到 TodoWrite）

- [ ] **G1 需求分析**（本文已完成）
- [ ] **G2 计划批准**（等待用户审核本文档）
- [ ] **G3 实施**：
  - [ ] 4.1 写 E2E 入库
  - [ ] 4.2 MdTable 基础组件
  - [ ] 4.3 MD 序列化器
  - [ ] 4.4 浮动 UI 9 操作
  - [ ] 4.5 Yjs 集成
  - [ ] 4.6 列宽持久化
  - [ ] 4.7 回归 + 清理
- [ ] **G4 审查**（每阶段结束后）
- [ ] **G5 验证**（E2E + 部署）
- [ ] **G6 归档**

---

*关联: [markdown-editor-restore-plan.md](markdown-editor-restore-plan.md)（上一版表格重构背景）、[markdown-editor-refactor-prompt.md](markdown-editor-refactor-prompt.md)（编辑器整体重构上下文）*

*状态摘要: 📊 阶段 0/7 完成 — 等待用户审核计划文档后启动 4.1*

**看板更新**: 2026-08-09 创建 | 操作人: Claude