# 智能目录双模式(fast / precise) — 2026-08-09

> **状态**: ✅ 已完成 | **作者**: Claude Code | **优先级**: 🟡 中(用户主动提出需求)

## 📊 状态摘要

- **复杂度**: 🟡 中(2 后端 + 3 前端文件改动)
- **改动文件**: 5 个
- **验证**: ✅ E2E 双模式都跑通(后端日志确认 Docling 调用)

## 🎯 目标

让智能目录功能支持两种模式,各取所长:
- **fast**:沿用原 PDFTextStripper + LLM,纯文本 PDF 秒级
- **precise**:用 Docling + LLM,支持扫描件/复杂版面,章节识别更准

## 🔍 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `PdfController.java` | `/ai/auto-outline` 加 `mode` body 参数(`fast`/`precise`,默认 fast) |
| 2 | `PdfToolService.java` | 拆 `autoOutline()` 为分发 + `autoOutlineFast()` + `autoOutlinePrecise()` + `autoOutlineCore()`;注入 `DoclingProperties` |
| 3 | `pdf.ts` | `autoOutline(docId, mode='fast')` 接口加 mode 参数 |
| 4 | `PdfEditor.vue` | `onAiAutoOutline(mode)` 接收 mode 参数;加 `onAiAutoOutlineWithMode` 包装给模板 |
| 5 | `PdfRibbon.vue` | 智能目录按钮改成「分体按钮」(主按钮 = 快速生成 + 下拉箭头选精准) |

## 🔧 关键技术决策

- **保持 API 兼容**:不传 `mode` 时默认 `fast`(向后兼容旧调用方)
- **Docling 字符上限 60000 vs PDFStripper 12000**:Docling 输出更精炼,可放大 5 倍
- **分体按钮 UI 模式**:主按钮承担常用操作(快速),下拉箭头承担备选操作(精准) — 学习 Office Ribbon 设计

## ✅ 验证

- 后端编译通过(`mvn package -DskipTests`,无错误)
- 前端构建成功(`npm run build`,15.28s)
- E2E fast 模式:`POST /api/pdf/332/ai/auto-outline {"mode":"fast"}` → 2 章节,秒级返回
- E2E precise 模式:`POST /api/pdf/332/ai/auto-outline {"mode":"precise"}` → 2 章节,Docling 处理 ~100s,LLM 抽取
- 后端日志确认:`智能目录(precise):Docling 输出 1854 字符, 1 页` + `PDF 替换完成`

## 📝 后续建议

1. **Docling 慢的优化**:当前 ~100s 是因为这个 PDF 有大量图片。可考虑:
   - Docling 加并发(`DOCLING_SERVE_ENG_LOC_NUM_WORKERS` 已设 1,可提到 2)
   - 大 PDF 分页并发处理
2. **前端按钮文案**:精准生成描述"5-10 秒"对小文档准确,大文档可能 1-2 分钟,考虑加文档大小判断动态调整提示

## 🔗 关联

- **CLAUDE.md**:`PDF 工具模块 (/api/pdf)` 表已同步更新双模式说明
- **plans/README.md**:看板已同步