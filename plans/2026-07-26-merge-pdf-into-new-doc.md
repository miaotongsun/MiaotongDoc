# 计划:首页新建文档合并 PDF 创建 + 模板按 docType 过滤 + 模板区可折叠

> 创建时间: 2026-07-26
> 状态: 已完成

## 📊 状态摘要

- **进度**: 100% (实现 + 验证全过)
- **验证**: ✅ Phase 15 E2E 27/27 通过
- **最近变更**: 2026-07-26 — 创建对话框合并 PDF 创建,模板区可折叠,首页按钮合并

## Context

当前 MiaotongDoc 首页顶栏同时存在两个并列按钮"新建文档"和"创建 PDF"(`Home.vue:131-138`),分别打开两个独立对话框 (`CreateDocDialog.vue` + `PdfCreateDialog.vue`)。这造成两个问题:

1. **入口分裂**:用户创建一个 PDF 文档需要走"创建 PDF"按钮而不是"新建文档",体验不统一,后续 Markdown/PPT 等类型没有对应入口
2. **模板不可折叠**:当前模板区域固定 280px 高(`CreateDocDialog.vue:168`),必须选模板(选"空白文档"是隐形选项),不能跳过
3. **模板筛选时机已对**:后端 `GET /api/templates?docType=pdf` 已支持按 docType 过滤(`TemplateController.java:24-35`),但前端 `loadData()` 调用 `templateApi.getAll()`(无参数,前端 JS 过滤) `CreateDocDialog.vue:96` —— 应改成传 `docType` 让后端过滤,减少不必要的数据传输

改造后:只剩一个"新建文档"按钮,打开后:
- 选文档类型(5 种)
- 选模板(根据所选 docType 过滤,可折叠,默认展开,可选"空白文档")
- 选标题
- PDF 类型额外支持"空白 PDF / 图片转 PDF"两种模式(原本在 PdfCreateDialog)
- 创建成功后跳转编辑器

## 改动范围

### 前端核心改动 (4 个文件)

#### 1. [miaotongdoc-web/src/components/CreateDocDialog.vue](miaotongdoc-web/src/components/CreateDocDialog.vue) — 主重写

- **集成 PdfCreateDialog 内容**:PDF 模式下渲染 Tab(空白 PDF / 图片转 PDF),其他模式隐藏 Tab
- **模板区可折叠**:`templateSectionExpanded` ref,默认 `true`,带"展开/折叠"按钮
- **模板加载按 docType**:`templateApi.getAll({ docType })`,切类型时重新调用
- **handleCreate 分流**:
  - 非 PDF:走原 `documentApi.create(...)`(已存在,不动)
  - PDF blank:走 `pdfApi.createBlank(pages, width, height, title)`
  - PDF images:走 `pdfApi.createFromImages(files, title)`
  - 创建成功后 `router.push('/editor/' + docId)`(从 PdfCreateDialog 迁移过来)
- **新增 props**:`useRouter`(集成进来,不再依赖 Home 跳)
- **新增 ref**: `pdfMode` (`'blank' | 'images'`), `pdfPages`, `pdfPreset`, `pdfWidth`, `pdfHeight`, `pdfImages` (File 列表), `templateSectionExpanded`
- **保留**:`form.docType/title/templateId`, `selectedCategory`, `categories` 加载逻辑

#### 2. [miaotongdoc-web/src/views/Home.vue](miaotongdoc-web/src/views/Home.vue) — 入口简化

- **删除** lines 135-138 的"创建 PDF"按钮
- **删除** lines 472-473 的 `<PdfCreateDialog>` 挂载
- **删除** lines 586 `const showCreatePdf = ref(false)`,以及 line 1165-1167 `handlePdfCreated`
- **删除** line 571 `import PdfCreateDialog from '@/components/PdfCreateDialog.vue'`
- **新增事件处理**:`handleCreated(docId: number)` —— 接收新文档 id,跳编辑器(`router.push('/editor/' + docId)`),保留原列表刷新

#### 3. [miaotongdoc-web/src/api/template.ts](miaotongdoc-web/src/api/template.ts) — 无需改动

后端已支持 `?docType=` 参数,前端 `getAll({ docType })` 已可传入。无需修改。

#### 4. [miaotongdoc-web/src/components/PdfCreateDialog.vue](miaotongdoc-web/src/components/PdfCreateDialog.vue) — **删除**

迁移后无引用方。

### 后端 — 不改

- 后端 `TemplateController.java` 已支持 `?docType=` 过滤(已确认)
- 后端 `PdfController.java` 已支持 `createBlank` / `createFromImages`(已确认)
- 数据库无迁移

## 设计要点

### 模板区可折叠 UX

```
┌─ 选择模板 ▼ ─────────────────────────┐  ← 默认展开,显示计数 (N)
├──────────────────────────────────────┤
│ [空白文档]   [模板1]   [模板2]  ...   │  ← 折叠时整块收起
└──────────────────────────────────────┘
```

- 标题行可点击切换 `templateSectionExpanded`
- 折叠后整块隐藏,只留标题行 + 计数
- "空白文档"始终作为第一项可选(不强制选模板)

### PDF 模式 Tab 集成

PDF docType 被选中时,在"文档类型"区下方显示两个子模式 tab:
- **空白 PDF**:页数 + 纸张尺寸 + 标题(沿用 PdfCreateDialog 的 `blankPages/blankPreset/blankWidth/blankHeight`)
- **图片转 PDF**:拖拽上传 + 图片列表 + 标题

非 PDF docType 时不显示 PDF 子模式。

### 模板按 docType 加载

- `loadData()` 改为根据当前 `form.docType` 调用 `templateApi.getAll({ docType: form.docType })`
- `@watch(form.docType)` 触发 `loadData()`
- 切换 docType 重置 `templateId = 0` 和 `selectedCategory = ''`

## E2E 测试计划

### 新增测试文件: [miaotongdoc-web/tests/phase15-e2e.mjs](miaotongdoc-web/tests/phase15-e2e.mjs)

参考 [phase14-e2e.mjs](miaotongdoc-web/tests/phase14-e2e.mjs) 的 `step()` + `login()` 模式。

#### 测试场景

1. **登录** (复用 phase14 模式)
2. **打开新建文档对话框** —— 点"新建文档"按钮,确认 dialog 出现
3. **5 种 docType 切换** —— 循环点击 5 个类型卡片,确认选中态正确切换
4. **模板区可折叠** —— 默认展开 → 点折叠标题 → 模板列表不可见 → 再点展开 → 可见
5. **模板按 docType 过滤** —— 切到 PDF → 调用 `GET /api/templates?docType=pdf` 验证返回为空(后端空数据)或命中数据;切到 word 同理
6. **空白文档选项始终存在** —— 任意 docType,确认 "空白文档" 是模板列表第一项
7. **PDF 模式 tab 显示** —— 切到 PDF docType,确认"空白 PDF"/"图片转 PDF" 两个 tab 出现
8. **非 PDF 模式 tab 隐藏** —— 切到 word,确认 PDF tab 不出现
9. **空白 PDF 创建流程** —— 选 PDF + blank tab + 页数 2 + 标题 → 点创建 → 验证路由跳转 `/editor/{id}` + URL 含 `/editor/`
10. **Word 普通创建流程** —— 选 word + 不选模板 + 标题 → 点创建 → 验证路由跳转 `/editor/{id}`
11. **首页按钮只有一个"新建文档"** —— 验证 "创建 PDF" 按钮已不存在

#### 报告输出

写入 `miaotongdoc-web/tests/phase15-e2e-report.md`,截图入 `miaotongdoc-web/tests/screenshots/phase15-*.png`(gitignored)

## 验证步骤

1. **构建**:`cd miaotongdoc-web && npx vite build` (无 TS 错误)
2. **后端 curl 验证模板过滤**:
   ```bash
   curl -s "http://localhost:9004/api/templates?docType=pdf" -H "Authorization: Bearer $TOKEN"
   ```
3. **E2E**:`cd miaotongdoc-web && npm run e2e` —— 自动跑 phase14 + 手动跑 phase15(在 phase14 之后)
   - 实际方案:新写 `tests/phase15-e2e.mjs`,在 `run.mjs` 末尾追加 spawn 一次(或独立手动跑)
4. **视觉验证**:浏览器打开 `http://localhost:3000`,点"新建文档",逐项手测 5 种 docType + PDF tab + 折叠展开
5. **Home.vue 入口**:确认顶栏只剩一个"新建文档"按钮

## 不触发

- 数据库迁移
- CLAUDE.md 项目结构章节同步(CreateDocDialog 内容变更触发 §9 清单的"前端组件修改"项,需要在提交前同步)
- plans/README.md 看板更新(实现完成后做)

## 风险与回退

| 风险 | 应对 |
|---|---|
| 模板表为空导致 E2E 看不出"过滤效果" | 测空集合也通过 + 截图展示 |
| PDF 模式集成后,后端没返回 docId | 已确认后端 `PdfController.createBlank` 返回 `{ docId, title, pages }`(见 pdf.ts:431) |
| PdfCreateDialog 删除后有遗漏引用 | 全文搜索 `PdfCreateDialog` 引用,删除 |
| 折叠展开动画与 Element Plus 不兼容 | 用 `v-show` 控制可见性,不用动画 |