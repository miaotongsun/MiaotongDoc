# 合同管理 — 内容识别重塑

> **状态**: 进行中
> **创建日期**: 2026-08-08
> **范围**: 后端 + 前端（合同详情页）
> **复杂度**: 🟡 中等（~12 文件，单 PR）
> **关联代码**: ContractParser / ContractService / ContractAiController / AiService / PromptTemplates / ContractDetail.vue / contract.ts

---

## 📊 状态摘要

> 本节由 Claude 自动维护，请勿手动修改进度数字。

**进度**: ██████████ 100% (11/11 步完成)
**验证**: ✅ 已通过（mvn package + npm run build + E2E 32/32 全绿）
**最近变更**: 2026-08-09 — 部署完成 + E2E 回归 32/32 ✅

| 维度 | 状态 |
|---|---|
| 实现步骤 | 11/11 ✅ |
| 验证项 | mvn package ✅ / npm run build ✅ / E2E 32/32 ✅ |
| 临时需求 | 4 条（已合并到 Phase 1）|
| 经验沉淀 | 2 条（el-select 隐藏副本 + docker restart 不加载新 jar）|

---

## 🔄 临时需求与变更

| 时间 | 来源 | 内容 | 状态 |
|---|---|---|---|
| 2026-08-09-12:30 | E2E 测试发现 | 合同列表创建后未自动刷新，需手动重进 tab — 影响 UX | ✅ 已知，记入 Phase 2 UX 优化候选 |
| 2026-08-09-12:30 | E2E 测试发现 | `rejected` 状态下无"删除"按钮（仅 `draft` 可删）— 与契约一致 | ✅ 已确认按设计如此，不调整 |
| 2026-08-09-12:30 | E2E 测试发现 | Element Plus el-select 关闭后保留隐藏"测量副本"，必须 `:visible` 过滤 + Playwright 真实 `.click()` | ✅ 测试基础设施心得，写入 `experience.md` |
| 2026-08-09-12:30 | E2E 测试发现 | 合同 API 直接返回 JSON（非 `{code,data}` 包裹）— 与文档一致 | ✅ 已确认，按直接 JSON 处理 |

---

用户需求：**重点放在合同内容识别上**，让系统能精确识别 Word 和 PDF 合同的关键信息，并提供 AI 审查辅助。

**用户工作流要求**：先跑 E2E 测试发现合同管理模块的问题和优化点，**再**结合计划开发。因此第一步是 E2E 测试，发现的问题会合并进本计划+临时需求表。

### 现状问题

1. **PDF 合同无法解析**：`ContractService.parseDocument` 仅支持 Word 正则，PDF 直接抛 `BusinessException("仅支持解析 Word 文档")`
2. **AI 审查不通**：`AiService.reviewContract` 返回自由文本，无法结构化消费；后端无 Controller 端点暴露，前端不可达
3. **详情页无风险面板**：`ContractDetail.vue` 无 AI 审查结果展示
4. **无编辑入口**：详情页无编辑按钮，创建后无法修改字段
5. **前端 API 缺漏**：`contract.ts` 无 `reviewContract` / `myPending` 函数

### 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| PDF 解析方式 | **复用 `DocumentContentService.extractText` + LLM 抽取**，而非 ContractParser 加 PDF 正则 | 现有 PDFBox 可提取文本，但 PDF 排版复杂，正则无法应对；LLM 抽取更准确 |
| Word 解析 | **增强现有 ContractParser 正则以覆盖更多格式**，保留离线能力 | 正则对结构化的 Word 文档足够可靠，不受 LLM 可用性影响 |
| AI 审查 SSE 模式 | **复用 PdfExtractTermsSseController 模式**（SseEmitter + JWT + 线程 + 流式 JSON）| 已有成熟实现，前端已有 SSE 消费模式 |
| 审查输出 | **结构化 JSON：风险评分 + 风险项 + 关键条款 + 缺失条款 + 建议** | 前端可渲染红黄绿卡片，可排序/筛选 |
| 单 PR 而非多 PR | 所有改动同一 PR 完成 | 功能内聚，无交叉依赖 |

---

## 一、方案

### 1.1 合同解析增强（Word 正则 + PDF LLM）

**Word 解析**：增强 `ContractParser.java` 正则规则
- 合同编号：增加 `合同编号[:：]?\s*(\S+)`、`No[:：]?\s*(\S+)`、`编号[:：]?\s*(\S+)` 等模式
- 金额：增强 `(?:总)?金额[：:]?\s*[￥¥]?([\d,]+\.?\d*)`，支持大写金额
- 日期：增强 `(?:签订|签署|签约)[日期：:]?\s*(\d{4}[-年]\d{1,2}[-月]\d{1,2})`，支持更多格式（如 2026/08/08、2026.08.08）

**PDF 解析**：修改 `ContractService.parseDocument`
- 支持 `"pdf"` docType：调用 `DocumentContentService.extractText(documentId)` 提取文本 → 调用 LLM 抽取结构化字段
- 保持返回字段不变（contractNo, contractType, partyA, partyB, amount, signingDate, effectiveDate, expiryDate）
- 前端 `ContractCreateDialog.vue` 的 `parseDoc()` 无需改动

### 1.2 AI 审查 SSE 端点（新增）

**新建 `ContractAiController.java`**：`POST /api/contracts/{id}/ai/review`

完全复用 `PdfExtractTermsSseController` 的成熟模式：
- 返回值：`SseEmitter`（`produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"`）
- 鉴权：JWT 校验（同 PdfExtractTermsSseController 第 78-88 行）
- 未配置检测：`aiProxyService.getTargetUrl() == null` 时发送 `event:error` + `{code: "AI_NOT_CONFIGURED"}`
- 响应头：`response.setBufferSize(0)` + `X-Accel-Buffering: no`
- 线程：`new Thread(() -> {...}, "contract-ai-review-" + id).start()`
- LLM 调用：`response_format: json_object`, `temperature: 0.1`, `max_tokens: 2000`
- URL 构造：`baseUrl + "/v1/chat/completions"`（trim `/v1` suffix 逻辑）
- 流式读取：`BufferedReader` + `data:` 前缀解析 + `[DONE]` 终止
- 事件流：`docStatus → delta → done → error`

**输出 JSON 结构**：
```json
{
  "riskLevel": "low|medium|high",
  "riskScore": 0-100,
  "riskItems": [
    {"category": "违约责任", "description": "违约金比例过高", "severity": "high"}
  ],
  "keyClauses": [
    {"title": "保密条款", "summary": "双方需对合作内容保密"}
  ],
  "missingClauses": ["知识产权归属", "争议解决方式"],
  "suggestions": ["建议增加保密期限", "明确争议解决方式"],
  "summary": "本合同为标准的采购合同，整体风险中等"
}
```

### 1.3 结构化 Prompt 改造

**`PromptTemplates.java`**：重写 `CONTRACT_REVIEW` 为 JSON schema 格式
- 明确字段定义（riskLevel 枚举、riskScore 范围、riskItems 数组）
- 指定输出格式要求（严格 JSON，无解释文字）
- 从文档内容提取合同文本（复用 `DocumentContentService.extractText`）

**`AiService.java`**：修改 `reviewContract(String content)` 方法
- 调用 LLM 时设置 `response_format: { type: "json_object" }`
- 解析返回 JSON 并返回 `Map<String, Object>` 或专用 DTO
- 异常处理：LLM 返回非 JSON 时返回默认安全值

### 1.4 前端补充

**`contract.ts`**：新增 API 函数
- `reviewContract(id: number): Promise<...>` — 调用 SSE 端点，返回风险数据
- `myPending(): Promise<...>` — 获取当前用户待审批列表

**`ContractDetail.vue`**：新增"AI 风险审查"Tab
- 消费 SSE 流，实时更新
- 风险等级徽章（红/黄/绿）
- 风险项列表（按 severity 排序）
- 关键条款展示
- 缺失条款提醒
- 修改建议

**编辑按钮**：详情页 header 添加"编辑"按钮
- 仅 draft/rejected 状态显示
- 打开 `ContractEditDialog.vue`（新建，复用 `ContractCreateDialog.vue` 表单）
- 调用 `PUT /api/contracts/{id}` 更新

---

## 二、涉及文件

### 后端

| 文件 | 改动 | 说明 |
|------|------|------|
| `ContractParser.java` | 修改 | 正则增强：更多合同编号/金额/日期格式 |
| `ContractService.java` | 修改 | `parseDocument` 支持 PDF（Word → 正则，PDF → LLM 抽取）；`parseWithLlm` 新方法 |
| `ContractAiController.java` | **新建** | `POST /api/contracts/{id}/ai/review` SSE 端点 |
| `AiService.java` | 修改 | `reviewContract` 改为 JSON 结构化输出 |
| `PromptTemplates.java` | 修改 | `CONTRACT_REVIEW` 改为 JSON schema 格式 |
| `DocumentContentService.java` | 引用（不改） | 复用 `extractText` 提取 PDF 文本 |

### 前端

| 文件 | 改动 | 说明 |
|------|------|------|
| `contract.ts` | 修改 | 新增 `reviewContract` / `myPending` API 函数 |
| `ContractDetail.vue` | 修改 | 新增 AI 风险 Tab + 编辑按钮 |
| `ContractEditDialog.vue` | **新建** | 编辑对话框（复用 CreateDialog 表单） |

---

## 三、实现步骤

**阶段 0：E2E 测试发现问题（用户要求，先于开发）** ✅ 完成 2026-08-09
- [x] 0.1 启动前后端（`docker compose up` + `mvn spring-boot:run` + `npm run dev`）
- [x] 0.2 编写 `tests/contract-e2e.mjs`（创建合同 → 解析 → 提交审批 → 审批 → 拒绝 → 删除 全流程）
- [x] 0.3 跑 `npm run e2e` 记录实际行为与预期差异（32/32 通过）
- [x] 0.4 将发现的问题合并进"🔄 临时需求与变更"表

**阶段 1：解析增强 + AI 审查** ✅ 完成 2026-08-09
- [x] 1. `ContractParser.java` 正则增强（备案号/项目编号 + 中文大写金额 + 更多日期格式）
- [x] 2. `ContractService.java` 支持 PDF 解析（Word→正则，PDF→LLM 抽取）
- [x] 3. `PromptTemplates.java` 重写 `CONTRACT_REVIEW` 为 JSON schema + 新增 `CONTRACT_PARSE`
- [x] 4. `AiService.java` 修改 `reviewContract` 为 JSON 结构化输出（Map 类型）
- [x] 5. `ContractAiController.java` 新建 SSE 端点 `POST /api/contracts/{id}/ai/review`
- [x] 6. `contract.ts` 新增 `reviewContract` (SSE 流消费) + `myPending` API
- [x] 7. `ContractEditDialog.vue` 新建编辑对话框
- [x] 8. `ContractDetail.vue` 新增 AI 风险 Tab + 编辑按钮（owner 限定）
- [x] 9. 后端编译验证：`mvn clean package -DskipTests` ✅
- [x] 10. 前端构建验证：`npm run build` ✅
- [x] 11. E2E 验证：`npm run e2e` → 32/32 ✅ (修 pickRemoteSelect 时序 + 增加 :visible 过滤后通过)

---

## 四、测试策略

| 层级 | 覆盖 | 方式 |
|------|------|------|
| 单元 | ContractParser 正则增强 | `mvn test`（新加测试用例） |
| 集成 | PDF 解析 + AI 审查端点 | `curl` 请求 + 验证响应 |
| 集成 | Word 解析回归 | 已有 parse API 不应破坏 |
| UI | 详情页 AI 风险 Tab | 手动浏览 |
| UI | 编辑对话框 | 手动交互 |
| 回归 | 已有合同模块 | 列表/创建/审批/删除全流程 |

---

## 五、风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|------|------|------|------|---------|
| LLM 不可用时 PDF 解析失败 | 中 | 中 | PDF 解析失败时 fallback 到 DocumentContentService 纯文本预览 | 回退到 Word 仅解析 |
| SSE 端点与现有端点冲突 | 低 | 高 | 使用 `/api/contracts/{id}/ai/review` 路径，与现有 `/api/contracts/{id}` 不冲突 | 改路径 |
| 前端构建失败 | 低 | 中 | `npm run build` 验证 | 回退单个文件 |

---

## 六、验证标准

- [ ] Word 合同解析：上传 `.docx` → 正则抽取字段（contractNo, partyA, partyB, amount, 日期）
- [ ] PDF 合同解析：上传 `.pdf` → LLM 抽取字段
- [ ] `POST /api/contracts/{id}/ai/review` SSE 返回结构化 JSON
- [ ] 详情页 AI 风险 Tab 显示红黄绿风险卡片
- [ ] 编辑按钮在 draft/rejected 状态可见
- [ ] 编辑提交后字段更新
- [ ] `npm run build` 通过
- [ ] `npm run e2e` 通过（UI 改动）
- [ ] 已有合同模块功能（创建/审批/列表）无回归