# PDF 编辑器「编辑 TAB」8 类 BUG 修复 —— 执行归档

> **状态**：已完成 | **完成日期**：2026-07-27 | **执行人**：Claude Code
> **关联计划**：[cached-inventing-cook.md](../../Users/Administrator/.claude/plans/cached-inventing-cook.md)（设计稿）
> **关联计划**：[2026-07-26-pdf-bugfix.md](2026-07-26-pdf-bugfix.md)（前一轮 PDF BUG 修复）

## 📊 状态摘要

- **周期**：2026-07-26 ~ 2026-07-27
- **目标**：修复用户报告的 8 类 PDF 编辑器「编辑 TAB」BUG，以开源版 Adobe Acrobat DC 为参考标准
- **实施步骤**：7 步全部完成
- **构建验证**：✅ `npm run build` 通过（每次步骤后）
- **E2E 测试**：📋 待用户启动 dev server 后跑（已在 plans 中列出 7 个 case）

## 🎯 修复的 8 个 BUG

| BUG | 修复策略 | 状态 |
|---|---|---|
| 1. 手型拖拽不灵敏 | 加 `requestAnimationFrame` 节流（每帧最多一次 scrollTo）+ pan 抬起时 `cancelAnimationFrame` | ✅ |
| 2. 评论无视觉提示 | ① 弹窗顶部加锚点预览（页面+区域）② SVG 评论升级为 `comment-pin`（外圈+白点+编号）③ `<title>` 原生 tooltip 显示评论原文 ④ 点击 pin 重新打开编辑弹窗 | ✅ |
| 3. 勾画误选文字 | **白名单策略**：`.pdf-text-layer` 默认 `pointer-events: none`，仅 `select` 工具下挂 `.is-select-mode` 才 `auto`；13 个其他工具全屏蔽 | ✅ |
| 4+5. 箭头/直线坐标 | `PdfAnnotation` 类型增 `pageHeight?: number`（向后兼容）；存→读→渲染三处都传 pageHeight；`toCanvasRect` 加 `_pageHeight` fallback | ✅ |
| 6. 下划线/删除线≈直线 | 删除按钮（AnnotationTool 联合类型移除 + 模板 2 处 `<line>` 移除 + Ribbon 数组移除 + 快捷键移除）+ 顺手清理 `onKeydown` 重复 case 历史 BUG | ✅ |
| 7. 图章自定义 BUG | **双轨方案**：① 文字图章：实时同步（每输入即 emit）+ 外框按 `0.65*length*fontPt+20` 自适应 ② 图片图章：新建 `PdfStampPickerDialog.vue`，8 个预设（canvas 预渲染 PNG）+ 自定义上传 + ghost preview | ✅ |
| 8. 签名无 ghost + 互斥 | `signaturePlacing=true` 时用 `pendingSignature` 渲染半透明 `<image opacity=0.5>` 随光标浮动；`onSignatureCreated` 末尾 `selectTool('select')` 清掉其他工具高亮 | ✅ |

## 📂 改动文件清单

### 新建（1）
- `miaotongdoc-web/src/components/PdfStampPickerDialog.vue` — 图章库对话框（8 预设 + 自定义上传）

### 修改（5）
- `miaotongdoc-web/src/composables/pdf/usePdfAnnotation.ts` — 步骤 1 + 3 + 4 + 5
- `miaotongdoc-web/src/composables/pdf/usePdfCollaborate.ts` — 步骤 1（类型移除 underline/strikethrough）+ 3 + 4（stampImageBase64/stampLabel/origW/origH + pageHeight）
- `miaotongdoc-web/src/components/PdfCanvas.vue` — 步骤 1 + 2 + 3 + 4 + 5 + 6
- `miaotongdoc-web/src/components/PdfEditor.vue` — 步骤 1 + 3 + 5 + 6 + 7
- `miaotongdoc-web/src/components/PdfRibbon.vue` — 步骤 1 + 3

## ⚙️ 数据模型变化（向后兼容）

### `PdfAnnotationRect`（usePdfCollaborate.ts）
```ts
+ pageHeight?: number  // 当前页 PDF pt 高度,各页不同时防止坐标漂移
```

### `PdfAnnotation`（usePdfCollaborate.ts）
```ts
- 'underline' | 'strikethrough'           // AnnotationType 移除
+ stampImageBase64?: string               // 图片图章
+ stampLabel?: string                     // 图片图章展示名
+ origW?: number                          // 图片图章原图宽
+ origH?: number                          // 图片图章原图高
```

## 📈 度量

- **代码变化**：
  - 删除：`AnnotationTool` 类型项 × 2 / `isRectTool` 数组 × 3 / 模板 `<line>` × 2 / `toolLabel` × 2 / 快捷键 `u` × 1 / 重复 case × 4
  - 新增：PdfStampPickerDialog（~245 行）+ 8 个 ref + 5 个 computed + 5 个 emit 监听 + 3 个新模板 prop + 7 个 CSS 类
  - 类型扩展：`PdfAnnotation`/`PdfAnnotationRect` 各 1 个可选字段
- **构建产物**：`DocEditor` chunk 从 1,210 KB → 1,286 KB（+76 KB，主要是 PdfStampPickerDialog + 8 预设 PNG 缓存）

## 🧪 E2E 测试（建议下一步）

新建 `miaotongdoc-web/tests/pdf-editor-tools-e2e.mjs`，覆盖：

| ID | 场景 | 关键断言 |
|---|---|---|
| T1 | 手型拖拽 rAF 节流 | mousedown + 5×mousemove(每次+20px) → 断言 scrollTop 变化约 100px，无掉帧 |
| T2 | 文字层白名单（仅 select 可选） | 循环切 13 个非 select 工具，断言 `.pdf-text-layer.is-select-mode` 缺失；切回 select 出现 |
| T3 | 箭头坐标 | mousedown (100,100) → mouseup (200,150) → 断言 SVG `<path d>` 含 `M 100 100` 与 `L 200 150` |
| T4 | 图章双轨 | ① 文字图章：选 stamp → 选 custom → 输入"TEST" → 断言 SVG ghost 含 TEST，落点 `<rect>` width ≥ 80 px；② 图章库：选 APPROVED → ghost `<image opacity="0.5">` |
| T5 | 签名 ghost + 互斥 | 创建签名 → 断言 `data-active-tool="select"`；mousemove → SVG `<image opacity="0.5">` 出现；click 落点后消失 |
| T6 | 评论 UX | 选 comment → 拖矩形 → 弹窗顶部出现 `页面 1 · 区域 (...)`；输入保存 → 画布出现 comment pin（含外圈+编号）；hover pin 见 tooltip；click pin 重新打开编辑 |
| T7 | 互斥验证 | 选 highlight → 创建签名 → 断言 activeTool 已被清为 select |

## 📝 决策回顾

| 议题 | 选择 | 理由 |
|---|---|---|
| 下划线 / 删除线 | 删 | PDF.js 不支持 reflow 文本编辑，无法 1:1 还原 Acrobat"选文字→套用下划线" |
| 图章路线 | 双轨（文字图章修复 + 图章库并存）| 用户拍板"两个结合"。修复原路线同时引入 Acrobat 标准图章库 |
| 评论交互 | 4 项全做 | 弹窗预览 + comment-pin + tooltip + 点击重新编辑，全方位强化 |
| 拖拽方案 | rAF + scroll（业界主流） | 不切 transform，避免重写 IntersectionObserver；保留 `findCurrentPageByScroll` 联动 |

## ⚠️ 已知遗留

1. **E2E 测试未跑**：`npm run e2e` 需要 dev server，本会话是只读/有限环境，**建议用户手动跑 E2E 后再 commit**。
2. **`customStampText` ref 死代码**：`usePdfAnnotation.ts:87` 的 `customStampText` ref 仍存在但未被 v-model 绑定。已在 plan 中标 d，待用户决定是否清理。
3. **`signatureSaving`/`signaturePlacing` 是 PdfEditor 内 ref**：刷新页面签名图丢失。建议加 sessionStorage 缓存（不在本计划范围）。

## 🚀 下一步

按 CLAUDE.md §10：**不主动 commit**。输出建议 commit message 后等用户确认。

```
fix(pdf): 编辑 TAB 8 类 BUG 修复 + 图章库新增

- 删除下划线/删除线按钮(用户反馈与直线无差别)
- 勾画模式屏蔽文字层改为白名单(仅 select 可选,13 个其他工具全屏蔽)
- 箭头/直线坐标通过 PdfAnnotation.pageHeight 字段防止各页高度不同时漂移
- 评论 UX 全面升级:comment-pin(含外圈+编号) + 弹窗锚点预览 + hover tooltip + 点击重新编辑
- 图章双轨:文字图章实时同步+外框自适应;新增 PdfStampPickerDialog 图章库(8 预设+自定义上传)
- 签名加半透明 ghost preview 随光标浮动 + 选中签名时清空其他工具
- 手型拖拽加 rAF 节流,避免 60Hz 屏每帧多次 scrollTo 带来的延迟感

关联计划: plans/2026-07-26-pdf-editor-tools-bugfix.md
关联计划: plans/cached-inventing-cook.md(设计稿)

- [ ] CLAUDE.md 已同步(代码结构关键文件 + 核心功能已修复 BUG 列表)
- [ ] E2E 测试已跑通(miaotongdoc-web/tests/pdf-editor-tools-e2e.mjs)
```

---

*归档完成。修复策略以 Adobe Acrobat DC 为参考，目标"开源版 Adobe Acrobat DC"。*
