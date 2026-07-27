# PDF 编辑器全面功能测试

> **状态**: 已完成
> **创建日期**: 2026-07-25
> **完成日期**: 2026-07-25
> **维护者**: Claude Code
> **关联代码**: `miaotongdoc-web/src/components/Pdf*.vue`, `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/Pdf*.java`, `miaotongdoc-web/src/api/pdf.ts`, `miaotongdoc-web/src/composables/pdf/*.ts`
> **关联 ADR**: 无
> **最终报告**: [miaotongdoc-web/tests/pdf-full-test-final-report.md](../miaotongdoc-web/tests/pdf-full-test-final-report.md)

---

## 📊 状态摘要

**进度**: ██████████ 100% (所有阶段已完成)
**验证**: ✅ 已通过 166/175 用例 (94.9% 去阻塞通过率)
**最近变更**: 2026-07-25 12:35 — 完成两轮 E2E + 最终报告

| 维度 | 状态 |
|---|---|
| 环境 | ✅ docker healthy, jar/dist 已重建并部署最新代码 |
| 测试样本 | ✅ 5 个 (单页/多页/表单/扫描件/大文档) |
| phase14 基线 | ✅ 23/23 通过 |
| API 全覆盖 | ✅ 69 通过 / 8 失败 (含 6 个 P0/P1 真实 Bug) |
| UI 全覆盖 | ✅ 74 通过 / 1 失败 (P0-1 搜索 bug,UI 验证到) |
| 最终报告 | ✅ miaotongdoc-web/tests/pdf-full-test-final-report.md |

### 关键 Bug 摘要

- **P0-1** 全文搜索完全失效(`/search` 任何关键词返回 0)
- **P0-2** PDF 文本提取中文 mojibake(`/text` 中中文乱码)
- **P0-3** 水印/页眉页脚/表单填充中文 500
- **P1-1** 后端 PDF API 响应格式严重不统一
- **P1-2** `pages/extract` 语义与用户预期不符
- **P1-3** `extract-images` 空 zip(22 字节)
- **P1-4** `/compare` 错误信息误导

详细见最终报告。

---

## 🔄 临时需求与变更

| 时间 | 来源 | 内容 | 状态 |
|---|---|---|---|
| 2026-07-25 | 用户授权 | 不改业务代码,只测试 + 提建议 | ✅ 遵守 |

---

## 一、Context — 现状

- 已部署最新代码(miaotongdoc-server jar 时间 2026-07-25,前端 dist index hash `index-Dqqf20wO.js`)
- docker 全套 healthy: web-server / nginx / postgres / redis / minio / elasticsearch / rabbitmq / 3 个 editor
- 已有 phase14-e2e.mjs(11 项 UI 烟测)
- PDF 编辑器是 V3 主壳,前端 26 个 Pdf* 组件,后端 53 个 API,前端 api 模块 47 个方法

## 二、整体策略

```
阶段 1: 环境 + 样本(已完成)
   ↓
阶段 2: phase14 基线(已运行,作为基线参考)
   ↓
阶段 3: PDF API 集成测试(覆盖 /api/pdf/* 53 个端点)
   ↓
阶段 4: PDF UI 交互测试(覆盖编辑器主要交互流程)
   ↓
阶段 5: 复测失败项 + 输出报告
```

## 📋 需求

系统性验证 PDF 编辑器全部功能无明显 Bug,从用户视角识别优化点。

## 🎯 目标

- ✅ 所有 53 个后端 API 至少 1 次端到端覆盖(成功路径)
- ✅ 主要 UI 交互流程覆盖(打开/渲染/编辑/导出/页面操作/OCR/表单/签名/AI)
- ✅ 输出缺陷清单 + 优化建议(用户视角)

## 🔧 方案

### 方案对比

| 方案 | 优点 | 缺点 | 推荐 |
|------|------|------|------|
| A. 仅跑现有 phase14 | 成本低 | 覆盖率 < 20% | ❌ |
| B. 新增 PDF 全量 API + UI E2E | 覆盖率高,可重复 | 需写脚本 | ✅ |
| C. 手动点点点 | 灵活 | 不可重复,易漏 | ❌ |

### 关键技术决策

- **决策 1**: 不修改业务代码;只新增 `tests/*.mjs` + 测试样本 fixtures
- **决策 2**: API 测试用 Node 原生 fetch + token;不依赖额外 npm 包
- **决策 3**: UI 测试在已有 phase14-e2e.mjs 基础上扩展,避免重复登录流程

## 📁 涉及文件

### 测试脚本(新增,不进生产)

- `miaotongdoc-web/tests/pdf-api-e2e.mjs` - PDF API 集成测试
- `miaotongdoc-web/tests/pdf-ui-e2e.mjs` - PDF UI 交互测试
- `miaotongdoc-web/tests/fixtures/*.pdf` - 测试样本
- `miaotongdoc-web/tests/fixtures/make-samples.py` - 样本生成器
- `miaotongdoc-web/tests/pdf-*-e2e-report.md` - 测试报告(本地,gitignore)

## 📝 实现步骤

### 阶段 1: 环境 + 样本

- [x] 重建后端 jar (mvn clean package)
- [x] 构建前端 (npm run build)
- [x] 拷贝到部署目录,重启 web-server + nginx
- [x] 生成 5 个 PDF 测试样本

### 阶段 2: phase14 基线

- [x] 运行 phase14-e2e.mjs,记录基线

### 阶段 3: PDF API 集成测试

- [ ] 登录拿 token
- [ ] 上传样本 PDF,记录 docId
- [ ] 跑全部只读 API (text/info/outline/metadata/search/...)
- [ ] 跑全部写入 API (rotate/merge/split/watermark/encrypt/decrypt/...)
- [ ] 跑全部 AI/OCR API (recognize/recognize-paddle/auto-outline/...)
- [ ] 跑全部高级 API (form-fields/signature/redact/...)

### 阶段 4: PDF UI 交互测试

- [ ] 登录
- [ ] 打开 PDF 编辑器
- [ ] 验证 Ribbon 5 个 tab
- [ ] 验证 ToolsRail 工具按钮
- [ ] 验证右面板 5 个 tab
- [ ] 验证缩略图/搜索/批注
- [ ] 验证导出菜单/打印
- [ ] 验证页面操作弹窗
- [ ] 验证 OCR 触发

### 阶段 5: 报告

- [ ] 汇总通过/失败/未验证
- [ ] 列出 P0/P1/P2 缺陷
- [ ] 用户视角优化建议
- [ ] 出最终报告

## 🧪 测试策略

- **API 集成**: 每个端点至少 1 个成功用例;失败用例只在已知有 Bug 时补
- **UI E2E**: 复用 phase14 登录 + 打开 PDF,扩展交互用例
- **样本**: 5 种类型(单页/多页/表单/扫描件/大文档)
- **环境隔离**: 测试中创建的文档用前缀 `pdf-test-` 命名,便于清理

## ⚠️ 风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|------|------|------|------|---------|
| AI/OCR 服务超时 | 高 | 中 | 设置短超时,标记阻塞 | 跳过该用例 |
| 大文档渲染慢 | 中 | 低 | 单独跑,允许更长超时 | 标记性能观察项 |
| Phase14 基线本身有 flaky | 中 | 中 | 跑 2 次确认 | 记录但不阻塞 |

## ✅ 验证标准

- [ ] 53 个 API 全部有测试结果(成功/失败/阻塞)
- [ ] UI 主要交互流程有截图证据
- [ ] 缺陷清单含复现步骤
- [ ] 优化建议从用户视角出发
- [ ] 最终报告覆盖完整

---

## 📦 交付清单

### 已完成文件

- ⭐ `miaotongdoc-web/tests/fixtures/make-samples.py` - 样本生成器
- ⭐ `miaotongdoc-web/tests/fixtures/*.pdf` - 5 个测试样本
- ⭐ `miaotongdoc-web/tests/pdf-api-e2e.mjs` - API 集成测试(规划)
- ⭐ `miaotongdoc-web/tests/pdf-ui-e2e.mjs` - UI 交互测试(规划)

### 测试报告(本地生成)

- `miaotongdoc-web/tests/pdf-api-e2e-report.md`
- `miaotongdoc-web/tests/pdf-ui-e2e-report.md`
- `miaotongdoc-web/tests/pdf-full-test-final-report.md`

---

## 变更日志

- 2026-07-25 创建
- 2026-07-25 部署最新代码 + 准备样本 + 写文档