# 规划与方案文档

> 本目录是项目**非源码文档的统一落点**：开发计划、执行记录、架构决策、经验总结、历史设计。
>
> ⚠️ **Claude 注意**：每次新建/修改计划后，必须同步本文件"📊 项目总看板" + 该计划"📊 状态摘要"。

---

## 📊 项目总看板

> 上次更新：2026-08-09
> 上次更新：2026-08-02

### 快速索引

- **正在开发** → [进行中](#进行中)
- **已完成** → [已完成](#已完成)
- **已废弃/取消** → [已废弃](#已废弃)
- **架构选型理由** → [架构决策](#架构决策)
- **跨任务复用的踩坑** → `experience.md`
- **历史设计稿** → [历史设计](#历史设计)

---

## 进行中

<!-- 状态：规划中 / 进行中。每条加一行：- [YYYY-MM-DD-xxx.md](xxx) — 一句话目标 -->

- [2026-08-09-md-table-rewrite.md](2026-08-09-md-table-rewrite.md) — MD 编辑器表格 7 大能力(策略改为 fix-in-place,E2E 33/33 ✅)— **已完成** 2026-08-09
- [2026-08-09-contract-payment-plan.md](2026-08-09-contract-payment-plan.md) — 合同付款计划(新表 + AI 抽取 + scheduler + 通知跳转)— **已完成** (V29 + 32/32) 2026-08-09
- [2026-07-26-offline-deployment.md](2026-07-26-offline-deployment.md) — 纯内网部署方案设计（LLM/Docling/PaddleOCR/镜像 全链路离线改造）— 规划中（文件存在，此前被 .gitignore 误排除，2026-08-10 已修正）
- [offline-export-command.md](offline-export-command.md) — 镜像导出速查（已重写：一键脚本 + 手工命令 + 实测尺寸 + 三大坑）
- [offline-env-requirements.md](offline-env-requirements.md) — **内网离线部署环境依赖清单**（硬件/Docker 版本/内核/网段/镜像/自检脚本/交付确认表，26 项依赖；7 项待修已修 5 项）— 生效中 2026-08-10

## 已完成

<!-- 状态：已完成。每条加一行 + 完成日期 -->
- [2026-08-09-auto-outline-dual-mode.md](2026-08-09-auto-outline-dual-mode.md) - 智能目录双模式(fast=PDFStripper+LLM / precise=Docling+LLM,前端分体按钮 UI)- 完成 2026-08-09
- [2026-08-09-docling-frontend-test.md](2026-08-09-docling-frontend-test.md) - Docling 启用 + AiStatusController bug 修复(硬编码 false -> 真实检查) + 前端测试手册 - 完成 2026-08-09
- [2026-08-09-ocr-models-offline-deploy.md](2026-08-09-ocr-models-offline-deploy.md) — OCR 模型/引擎优化与内网离线部署(PaddleOCR 零外网探测 + Docling 深度健康检查 + Tesseract 语言包动态扩展 + 环境变量完整注释) — 完成 2026-08-09
- [2026-08-08-contract-ai-review.md](2026-08-08-contract-ai-review.md) — 合同管理内容识别重塑（Word 正则增强 + PDF LLM 抽取 + AI 审查 SSE + 编辑对话框 + 部署）— 完成 2026-08-09
- [2026-08-02-pre-prod.md](2026-08-02-pre-prod.md) — 投产前全维度验证+深度测试+补充测试+Bug修复(13个P0/P1修复,最终回归100% (135/135) ✅) (2026-08-02)
- [2026-08-02-pdf-redaction-refactor.md](2026-08-02-pdf-redaction-refactor.md) — PDF 编辑器「密文遮盖」功能重构 (RedactionEngine + 红色 banner + OCR 路径 + E2E 15/15 + text API 强验证 166→1 char) — 完成 2026-08-02
- [2026-07-27-admin-excel-import-tree-openapi.md](2026-07-27-admin-excel-import-tree-openapi.md) — 管理后台增强：Excel 导入用户/部门 + 树形选择器 + 对外 API 规范 (2026-07-27)
<!-- 状态：已完成。每条加一行：- [YYYY-MM-DD-xxx.md](xxx) — 一句话成果 + 完成日期 -->

- [2026-07-25-pdf-full-test.md](2026-07-25-pdf-full-test.md) — PDF 编辑器全面测试(API+UI 175 用例,166 通过) — 完成日期 2026-07-25
- [2026-07-26-pdf-bugfix.md](2026-07-26-pdf-bugfix.md) — PDF 编辑器 Bug 修复(7 个 P0/P1 修复,含中文搜索/水印/文本清理等) — 完成日期 2026-07-26
- [2026-07-26-ocr-ai-refactor.md](2026-07-26-ocr-ai-refactor.md) — PDF OCR/AI 完整改造(5 阶段全完成:AI配置接入 + SSE统一 + MQ异步 + 前端引导 + E2E) — 完成 2026-07-26
- [2026-07-26-pdf-editor-tools-bugfix.md](2026-07-26-pdf-editor-tools-bugfix.md) — PDF 编辑器「编辑 TAB」8 类 BUG 修复(手型拖拽/评论/勾画/箭头/图章/签名 + Acrobat 对齐) — 完成 2026-07-27
- [2026-08-01-pre-prod-audit.md](2026-08-01-pre-prod-audit.md) — 投产前全维度验证(10 维度) + GO WITH FIXES 决策 + 3 P0 + 2 P1 — 完成 2026-08-01
<!-- 状态：已完成。每条加一行 + 完成日期 -->
- [2026-07-27-admin-excel-import-tree-openapi.md](2026-07-27-admin-excel-import-tree-openapi.md) — 管理后台增强：Excel 导入用户/部门 + 树形选择器 + 对外 API 规范 (2026-07-27)

## 看板更新记录

- 2026-08-10 -- 清理 docling 残留 layout-heron-git/（HF 仓库 clone，模型仅 LFS 指针，Dockerfile 从未引用）· 新增 app/docling/README.md（HF 缓存三件套原理 + models/ 重建步骤 + 14 blob 校验脚本）· 修 .gitignore（混入的 XML 标签 + 重复行 + 误排除 plans/ 两个文档）
- 2026-08-10 -- 离线部署三件套落地：修 setup-linux-host.sh 4 项（max_map_count 262144 / Docker 缺失改 exit 1 + 版本校验 / 颜色转义 / --ntp-server）· 新增 export-images.sh（实测导出通过，核心 tar 4.0GB）· 重写 offline-export-command.md（删 httpd:alpine，尺寸改实测）
- 2026-08-10 -- 新增 offline-env-requirements.md（内网离线部署环境依赖清单：26 项依赖/9 项阻断级，含镜像清单核对、Compose V2 红线、172.20.0.0/16 网段冲突、目录名依赖陷阱、自检脚本、交付确认表；同时记录 7 项已知待修）
- 2026-08-10 -- 2026-08-09-md-table-rewrite.md **真正完成**(NxM picker click-to-toggle + position:fixed 解决 toolbar overflow 裁剪,可视化截图确认)
- 2026-08-09 -- 2026-08-09-md-table-rewrite.md **完成**(策略 fix-in-place,E2E 33/33 ✅:三向对齐回写+NxM 选择器+无表头支持+resize 基础设施,2 文件改动)
- 2026-08-09 -- 4.1 E2E 入库完成(初始 31/32 FAIL,真实仅 7 fail,补后 33/33 ✅)
- 2026-08-09 -- 新增 2026-08-09-auto-outline-dual-mode.md(智能目录支持 fast/precise 双模式,前端分体按钮)


- 2026-08-09 -- 新增 2026-08-09-md-table-rewrite.md(MD 表格自研重做计划:5 天/7 阶段,等待用户审核)
- 2026-08-09 -- 新增 2026-08-09-docling-frontend-test.md(Docling 启用 + AiStatusController bug 修复 + 前端测试手册)

- 2026-08-09 —— 新增 2026-08-09-ocr-models-offline-deploy.md 完成记录（OCR 三套引擎内网离线优化 + .env 完整注释 + 6 文件改动）
- 2026-08-09 —— 2026-08-08-contract-ai-review.md **完成**（部署 jar + dist + 重启 web-server/nginx + E2E 32/32 ✅；修 pickRemoteSelect 时序 + :visible 过滤）；新增 experience.md 沉淀 el-select 与 docker restart 经验
- 2026-08-02 —— 2026-08-02-pdf-redaction-refactor.md 完成（RedactionEngine + 红色 banner + E2E 15/15 + text API 强验证 166→1 char）
- 2026-08-02 —— 新增 2026-08-02-pdf-redaction-refactor.md 进行中（用户提出 PDF 密文遮盖改造：交互闭环 + 后端真脱敏 + OCR fallback）
- 2026-07-27 —— 新增 2026-07-26-pdf-editor-tools-bugfix.md 完成记录（用户提出 8 类 BUG，以 Acrobat DC 为标准重审，7 步实施完成）

## 已废弃

<!-- 状态：已废弃。明确告知"这个需求不要做了"，避免重复提出 -->

暂无。

## 架构决策

<!-- ADR-NNN 索引 -->

- [ADR-001-open-api-design.md](ADR-001-open-api-design.md) — 对外服务 API 规范设计（API Key 多 Key 管理 vs OAuth2 选择）
- [openapi-integration-guide.md](openapi-integration-guide.md) — 对外服务 API 集成指南（接口清单/入参出参/错误码/示例代码/最佳实践）

---

## 约定

### 命名

| 类型 | 格式 |
|---|---|
| 开发计划 | `YYYY-MM-DD-<kebab>.md` |
| 架构决策 | `ADR-NNN-<kebab>.md` |
| 经验汇总 | `experience.md`（单文件） |

### 状态机

```
规划中 → 进行中 → 已完成
   ↓        ↓
   └──── 已废弃
```

状态变更时**必须同步**：
1. 文件 frontmatter 的"状态"字段
2. 文件内"📊 状态摘要"小节
3. 本 README 对应索引区域

### 文档必含章节

完整模板见本页：[§计划文档模板](#计划文档模板) / [§ADR 模板](#adr-模板) / [§提交规范](#提交规范)。

---

## 历史设计（保留）

| 文档 | 状态 | 说明 |
|------|------|------|
| [ai-writing-module-v2.4.md](ai-writing-module-v2.4.md) | 规划中 | AI 文档在线写作模块 v2.4 架构方案 |
| [pdf-feature-redesign.md](pdf-feature-redesign.md) | 规划中 | PDF 功能模块重新设计方案 |
| [pdf-editor-reshape.md](pdf-editor-reshape.md) | 已完成 | PDF 编辑器 V2 → V3 重构方案 |
| [pdf-editor-v3.md](pdf-editor-v3.md) | 已完成 | PDF 编辑器 V3 详细设计 |
| [pdf-editor-reshape-steps.md](pdf-editor-reshape-steps.md) | 已完成 | V3 重构实施步骤 |
| [ai-config-architecture.md](ai-config-architecture.md) | 已完成 | AI 配置模块架构 |
| [markdown-editor-restore-plan.md](markdown-editor-restore-plan.md) | 已完成 | MarkdownEditor.vue 重构修复计划 |
| [markdown-editor-refactor-prompt.md](markdown-editor-refactor-prompt.md) | 已完成 | MarkdownEditor.vue 功能重构提示词 |
| [ocr-usage-guide.md](ocr-usage-guide.md) | 已完成 | OCR 服务使用指南 |
| [FEATURE_DESIGN.md](FEATURE_DESIGN.md) | 已废弃 | 早期功能设计稿 |
| [MiaotongDoc-Architecture.md](MiaotongDoc-Architecture.md) | 已废弃 | 早期架构稿 |
| [SYSTEM_AUDIT.md](SYSTEM_AUDIT.md) | 已完成 | 系统审查报告 |

---

## 看板更新记录

| 时间 | 更新内容 | 操作人 |
|---|---|---|
| 2026-08-02 | 合并归档: 2026-08-01-pre-prod-audit + 2026-08-02-pre-prod-full-audit + 2026-08-02-pre-prod-supplement + 2026-08-02-bugfixes → 2026-08-02-pre-prod.md | Claude |
| 2026-08-02 | 最终回归验证 100% (135/135), reindex 异步化修复完成, 计划文档更新至 100% | Claude |
| 2026-08-02 | 补充入档: FolderController 尾斜杠 500 修复 (@GetMapping({"", "/"})) 作为 Bug #15, 13 Bug 全部入档 | Claude |
| 2026-07-26 | 新建文档对话框合并 PDF 创建 + 模板可折叠 + 按 docType 过滤（phase15-e2e 27/27 通过） | Claude |
| 2026-07-25 | 迁入计划文档模板/ADR 模板/提交规范（从 CLAUDE.md §3 瘦身） | Claude |
| 2026-07-23 | 重构看板：精简 + 明确"进行中/已完成/已废弃"三类索引 | Claude |
| 2026-07-22 | 初始化看板 | Claude |

---

## 计划文档模板

> 借鉴自 `pdf-editor-v3.md`（1951 行优秀实践）+ 本项目经验。
> 章节裁剪指南见文末"章节裁剪"小节。

```markdown
# <计划标题>

> **状态**: 规划中 / 进行中 / 已完成 / 已废弃
> **创建日期**: YYYY-MM-DD
> **维护者**: Claude Code / <人名>
> **关联代码**: <涉及文件列表>
> **关联 ADR**: ADR-NNN（如有）

---

## 📊 状态摘要

> 本节由 Claude 自动维护，请勿手动修改进度数字。

**进度**: ████████░░ 80% (8/10 步完成)
**验证**: ✅ 已通过（6/7 项）
**最近变更**: YYYY-MM-DD-HH:MM — <变更概述>

| 维度 | 状态 |
|---|---|
| 实现步骤 | N/M |
| 验证项 | X/Y ✅ |
| 临时需求 | N（已合并 X / 待评估 Y）|
| 经验沉淀 | N 条 |

---

## 🔄 临时需求与变更

| 时间 | 来源 | 内容 | 状态 |
|---|---|---|---|
| YYYY-MM-DD-HH:MM | 用户/测试发现 | <需求描述> | ✅ 已合并 / 📋 待评估 |

---

## 一、Context — 现状

> 借鉴 pdf-editor-v3：让接手的人 30 秒看懂背景。

### 已完成（如有前置工作）

| 阶段 | 内容 | 状态 |
|------|------|------|
| <前置> | <简述> | ✅ |

### 用户原始反馈（如有）

| # | 反馈 | 状态 |
|---|------|------|
| P1 | <原始需求> | ✅ 已修复 / ⏳ 待办 |

---

## 二、整体策略（大型任务必填）

> 多阶段任务的路线图，每阶段独立可交付。

\`\`\`
阶段 1（X 周）→ <产出>
阶段 2（X 周）→ <产出>
阶段 3（X 周）→ <产出>
\`\`\`

**当前进度**：阶段 N / M

---

## 📋 需求

[一句话描述需求]

## 🎯 目标

[可衡量的完成标准]

## 🔧 方案

[技术方案描述，包括架构设计、关键决策理由]

### 方案对比

| 方案 | 优点 | 缺点 | 推荐 |
|------|------|------|------|
| A | ... | ... | ✅ |
| B | ... | ... | ❌ |

### 关键技术决策（架构选择时填写）

- **决策 1**：选 X 而非 Y，理由是...
- **决策 2**：用模式 P 而非 Q，理由是...

## 📁 涉及文件

### Critical Files（关键文件，⭐ 标注）

- ⭐ `xxx/yyy/Xxx.java` - 新增/修改（核心逻辑）

### 后端

- `src/main/java/...` - 新增/修改/删除（理由）

### 前端

- `src/api/...` - 新增/修改/删除（理由）

### 数据库

- `V27__add_new_table.sql` - 新增/修改（理由）

## 📝 实现步骤

> 按执行顺序排列，每步完成后勾选（进度自动算）

### 阶段 N（如有）：<阶段名>

- [ ] 子步骤 1
- [ ] 子步骤 2

### 阶段 N+1：<阶段名>

- [ ] 子步骤 3

## 🧪 测试策略

- 单元测试覆盖：*核心逻辑方法*
- 集成测试覆盖：*API 端点（curl + psql）*
- 手动测试覆盖：*UI 交互流程*
- E2E 覆盖：*改 UI 组件时必须 `npm run e2e` 跑通（详见 CLAUDE.md 铁律 6 + G5 门禁）*

## ⚠️ 风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|------|------|------|------|---------|
| ... | 低/中/高 | 低/中/高 | ... | ... |

## ✅ 验证标准

> 与 G5 对应，验证通过后在此勾选

- [ ] 标准 1
- [ ] 标准 2

---

## ✅ 验证结果

> 验证日期：YYYY-MM-DD | 验证人：Claude Code

### API 验证（集成）

| 验证项 | 命令 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 接口 200 | `curl ...` | 200 | 200 | ✅ |
| 权限拒绝 | `curl ...` | 403 | 403 | ✅ |
| 数据落库 | `psql ...` | 1 行 | 1 行 | ✅ |

### UI 验证（手动）

| 验证项 | 操作 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 页面渲染 | 浏览器访问 | UI 正常 | UI 正常 | ✅ |
| 交互流程 | 点击按钮 | 流程通顺 | 流程通顺 | ✅ |

### E2E 验证（UI 改动必填,见 CLAUDE.md 铁律 6）

| 验证项 | 命令 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 关键场景 1 | `npm run e2e` | 通过 | 通过 | ✅ |
| 报告路径 | `tests/phase14-e2e-report.md` | 存在 | 存在 | ✅ |

### 回归验证

- [ ] 旧功能 1 未受影响
- [ ] 旧功能 2 未受影响

## 📝 经验总结

### 踩坑
- ...

### 教训（如借鉴自大型项目可加此节）
- ...

### 复用
- ...

### 流程改进
- ...

---

## 📦 交付清单

### 已完成文件

- ⭐ `xxx/Xxx.java` - <简述>
- `xxx/Yyy.vue` - <简述>

### 待办（如未全部完成）

- [ ] <待办项>

### 上线检查项

- [ ] 配置文件已更新（.env / application.yml）
- [ ] 数据库迁移已执行（Flyway 自动）
- [ ] Docker 镜像已重建（如涉及）
- [ ] CLAUDE.md 已同步（API / 表 / 配置变更）
- [ ] 用户验收通过

---

## 变更日志

- YYYY-MM-DD-HH:MM 创建
- YYYY-MM-DD-HH:MM 进入进行中
- YYYY-MM-DD 完成（commit: <hash>）
```

### 章节裁剪

- 小型任务（单文件 < 10 行）：可省略"一、Context"、"二、整体策略"、"📦 交付清单"
- 无架构选择：可省略"关键技术决策"
- 无前期工作：可省略"已完成"

---

## ADR 模板

架构决策记录用于记录每次架构级选择的背景、方案和后果。参考 Michael Nygard 的 ADR 模式：

```markdown
# ADR-{NNN}: {决策标题}

> 日期：YYYY-MM-DD | 状态：已接受 / 已废弃 / 已替代

## 上下文

[描述需要做决策的背景和动机]

## 决策

[我们决定采用什么方案]

## 理由

[为什么选这个方案，不选其他方案]

## 后果

[这个决策带来的正面和负面影响]

## 替代方案

[考虑过的其他方案及未选理由]
```

---

## 提交规范

```
格式：类型(模块): 一句话描述

类型: feat / fix / refactor / test / docs / style / chore / perf / security

内容要求：
  1. 第一行：类型 + 范围 + 一句话
  2. 空一行
  3. 正文：列出关键变更点（每行一个）
  4. 空一行
  5. 引用：Plan / ADR / Issue 引用

示例：
feat(signing): 添加电子签署功能

- 实现签署任务的创建/确认/拒绝/取消全流程
- 新增 mt_signing_task 和 mt_signing_record 表
- 签署完成后文档状态自动变更为 signed
- 签署记录包含签署人 IP、UA、文档哈希校验

关联计划：plans/2026-07-22-signing-feature.md
关联 ADR：plans/ADR-001-signing-state-machine.md
```