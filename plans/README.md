# 规划与方案文档

> 本目录是项目**非源码文档的统一落点**：开发计划、执行记录、架构决策、经验总结、历史设计。
>
> ⚠️ **Claude 注意**：每次新建/修改计划后，必须同步本文件"📊 项目总看板" + 该计划"📊 状态摘要"。

---

## 📊 项目总看板

> 上次更新：2026-07-23

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

暂无。

## 已完成

<!-- 状态：已完成。每条加一行：- [YYYY-MM-DD-xxx.md](xxx) — 一句话成果 + 完成日期 -->

暂无。

## 已废弃

<!-- 状态：已废弃。明确告知"这个需求不要做了"，避免重复提出 -->

暂无。

## 架构决策

<!-- ADR-NNN 索引 -->

暂无。

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

完整模板见 [CLAUDE.md §计划文档模板](../CLAUDE.md#计划文档模板) / [§ADR 模板](../CLAUDE.md#架构决策记录-adr)。

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
| 2026-07-23 | 重构看板：精简 + 明确"进行中/已完成/已废弃"三类索引 | Claude |
| 2026-07-22 | 初始化看板 | Claude |