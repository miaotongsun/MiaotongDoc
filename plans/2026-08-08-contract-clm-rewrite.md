# 合同管理模块重塑 - 精准识别 + 友好管理

> **日期**: 2026-08-08
> **范围**: 后端 + 前端（首页合同 Tab）
> **复杂度**: 🟡 中等（~12 文件，3 轮迭代）
> **状态**: 🟡 规划中

## Context

用户需求：**首页合同管理功能**，上传 Word 为主、PDF 为辅，精确识别合同重要信息，对员工负责的和外部合作的合同进行管理。

当前问题（聚焦首页体验）：
1. **AI 审查全链路断裂**：后端有 `AiService.reviewContract` 但无 Controller 端点，前端不可达
2. **PDF 合同无法创建**：`ContractParser` 仅支持 Word 正则，PDF 直接报错
3. **无"我的待审批"**：审批人找不到待自己审批的合同
4. **创建后无法编辑**：详情页无编辑按钮
5. **deadline 形同虚设**：前端收集后端丢弃

## 用户决策（已确认）

| 决策 | 选择 |
|---|---|
| 范围 | **首页合同管理**（不涉及 PDF 编辑器） |
| 上传格式 | **Word 为主，PDF 为辅** |
| AI 审查 | **结构化 JSON + 风险评分** |
| PDF 创建 | **复用 LLM 抽取回填** |

## 3 个 PR 拆分

| PR | 标题 | 文件数 | 核心改动 |
|---|---|---|---|
| **PR1** | `feat(contract): AI 精准识别 + 审查打通` | ~5 | Word 正则增强 + PDF LLM 抽取 + AI 审查端点 |
| **PR2** | `feat(contract): 我的待审批 + 编辑 + deadline` | ~4 | 待审批列表 + 编辑对话框 + deadline 生效 |
| **PR3** | `feat(contract): 详情页增强 + 风险面板` | ~3 | AI 风险红黄绿 + 审批 Stepper + 到期提醒优化 |

---

## PR1: AI 精准识别 + 审查打通

### 改动
| 文件 | 改动 |
|---|---|
| `ContractParser.java` | 正则增强（更多合同编号/金额/日期格式）；加 `parsePdf` 方法调 LLM 抽取 |
| `ContractService.java` | `parseDocument` 支持 PDF（分流 Word 正则 / PDF LLM）；`parseWithLlm` 方法 |
| `ContractAiController.java`（**新建**） | `POST /api/contracts/{id}/ai/review` -> 结构化 JSON 风险评分 |
| `AiService.java` | `reviewContract` 改为 JSON 输出（`response_format: json_object`） |
| `PromptTemplates.java` | `CONTRACT_REVIEW` 改为 JSON schema |

### 验证
- Word 合同解析：上传 .docx -> 正则抽取字段
- PDF 合同解析：上传 .pdf -> LLM 抽取字段
- `POST /api/contracts/{id}/ai/review` 返回 `{riskLevel, riskItems, missingClauses, summary}`

---

## PR2: 我的待审批 + 编辑 + deadline

### 改动
| 文件 | 改动 |
|---|---|
| `ContractController.java` | 加 `GET /my-pending` 端点；`submitForApproval` 存储 deadline |
| `ContractService.java` | deadline 存储 + 超时催办；`approve` 通知下一审批人 |
| `ContractList.vue` | 加"我的待审批"Tab + 角标 |
| `ContractEditDialog.vue`（**新建**） | 编辑合同（draft/rejected）复用 CreateDialog 表单 |

### 验证
- `/my-pending` 返回当前用户待审批列表
- deadline 存储 + 到期催办
- 编辑对话框可改字段

---

## PR3: 详情页增强 + 风险面板

### 改动
| 文件 | 改动 |
|---|---|
| `ContractDetail.vue` | 加"AI 风险审查"Tab + 红黄绿风险卡片；审批流横向 Stepper |
| `ContractRiskPanel.vue`（**新建**） | AI 风险评分仪表盘 |
| `contract.ts` | 加 `reviewContract` / `myPending` API |

### 验证
- 详情页 AI 风险 Tab 显示红黄绿
- 审批 Stepper 可视化
- 到期提醒 banner

---

## 全局验证

| 测试 | 命令 |
|---|---|
| 后端 | `mvn clean package -DskipTests` |
| 前端 | `npm run build` |
| E2E | `tests/contract-*.mjs` |
| 回归 | phase14 + redact + pdf 相关 |

## 进度跟踪

| PR | 状态 |
|---|---|
| PR1: AI 精准识别 + 审查 | ⏳ 规划中 |
| PR2: 待审批 + 编辑 + deadline | ⏳ 规划中 |
| PR3: 详情页 + 风险面板 | ⏳ 规划中 |