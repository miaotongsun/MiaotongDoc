# 合同付款计划

> **状态**: 已完成
> **创建日期**: 2026-08-09
> **范围**: 后端(新表 + scheduler + AI 抽取) + 前端(详情 Tab + 通知跳转)
> **复杂度**: 🔴 复杂(数据库迁移 + 多模块改动 + AI 集成)
> **关联代码**: mt_contract_payment (NEW) / ContractPaymentService / ContractPaymentController / ContractPaymentScheduler / ContractDetail.vue / NotificationBell.vue

---

## Context

用户需求:合同新增付款计划功能,支持 AI 自动识别合同中的付款条款,生成候选付款计划,用户确认后入库;到期日临近时通过通知系统提醒录入人。

### 现状

- 合同模块已有完整生命周期(草稿/审批/签署/到期)
- 通知系统已成熟(`NotificationService.notify(toUserId, type, content)`,带 `documentId` 关联)
- 已存在 `ContractExpirationScheduler` 每天9点检查合同到期

### 设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 存储 | 新表 `mt_contract_payment`(独立表,不嵌入 mt_contract) | 1对多,便于查询"未来7天到期的所有付款" |
| 状态字段 | `pending`(待付)/ `paid`(已付)/ `overdue`(逾期) | 三态清晰,提醒逻辑只查 pending+dueDate<=today+7 |
| AI 抽取 | 复用 `DocumentContentService.extractText` + 专用 Prompt + `response_format: json_object` | 与合同 PDF 抽取同模式 |
| 提醒时机 | scheduler 每日09:00 + 到达付款日当天 + 7天前各提醒 1 次(用 `reminder_sent` 防重复) | 与现有 ContractExpirationScheduler 一致 |
| 通知关联 | 新加 `contract_id` 字段到 `mt_notification`(可选,nullable) | 不破坏现有 documentId 关联 |
| 跳转逻辑 | 通知点击 → `sessionStorage` 标记 + 跳 `/home` + ContractList 监听器打开合同详情弹窗 | 与 `/contracts/:id` 深链复用同一机制 |

---

## 一、方案

### 1.1 数据库迁移 (V29)

```sql
CREATE TABLE mt_contract_payment (
  id BIGSERIAL PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  sequence INT NOT NULL,                    -- 第几期
  title VARCHAR(100),                        -- "首付款" / "尾款"
  amount NUMERIC(18,2),                      -- 本期金额
  currency VARCHAR(10) DEFAULT 'CNY',
  due_date DATE NOT NULL,                    -- 应付款日
  paid_date DATE,                             -- 实际付款日(null=未付)
  status VARCHAR(20) DEFAULT 'pending',       -- pending / paid / overdue
  reminder_sent BOOLEAN DEFAULT FALSE,        -- 防重复提醒
  remarks TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT fk_payment_contract FOREIGN KEY (contract_id) REFERENCES mt_document(id) ON DELETE CASCADE
);
CREATE INDEX idx_payment_contract ON mt_contract_payment(contract_id);
CREATE INDEX idx_payment_due_status ON mt_contract_payment(due_date, status);
```

同时 `mt_notification` 加 `contract_id BIGINT`(nullable,无 FK)。

### 1.2 后端

| 类 | 职责 |
|---|---|
| `entity/ContractPayment.java` | JPA 实体,映射 mt_contract_payment |
| `repository/ContractPaymentRepository.java` | `findByContractIdOrderBySequence`, `findByDueDateLessThanEqualAndStatusAndReminderSentFalse` |
| `service/ContractPaymentService.java` | CRUD + AI 抽取(`extractFromContract`) + 标记已付 |
| `controller/ContractPaymentController.java` | `GET/POST/PUT/DELETE /api/contracts/{id}/payments` + `POST /api/contracts/{id}/payments/extract` (AI) |
| `service/ai/PromptTemplates.java` | 新增 `CONTRACT_PAYMENT_EXTRACT` JSON schema Prompt |
| `service/ai/AiService.java` | 新增 `extractPaymentPlan(String content)` 返回 `List<PaymentPlanDTO>` |
| `scheduler/ContractPaymentScheduler.java` | 每日09:05 检查到期 → 调 `NotificationService.notify` |

### 1.3 通知扩展

`NotificationService.notify(...)` 加 `contractId` 形参(可选),`Notification` 实体加 `contract_id` 字段(V29)。

### 1.4 前端

- `contract.ts`:`payments(id)`, `addPayment(id, data)`, `updatePayment(id, pid, data)`, `deletePayment(id, pid)`, `extractPaymentPlan(id)`
- `ContractDetail.vue`:新增"付款计划"Tab,显示列表 + 添加/编辑/删除按钮 + "AI 提取付款计划"按钮(若为空时高亮引导)
- `NotificationBell.vue`:`handleClick` 增加 `contractId` 分支 → `sessionStorage` 标记 + `router.push('/home')`(复用深链机制)

---

## 二、涉及文件

### 后端 (新建 5 + 修改 3)

| 文件 | 改动 |
|---|---|
| `db/migration/V29__add_contract_payment.sql` | **新建**:mt_contract_payment 表 + mt_notification 加 contract_id |
| `entity/ContractPayment.java` | **新建** |
| `repository/ContractPaymentRepository.java` | **新建** |
| `service/ContractPaymentService.java` | **新建** |
| `controller/ContractPaymentController.java` | **新建** |
| `scheduler/ContractPaymentScheduler.java` | **新建** |
| `service/ai/PromptTemplates.java` | 修改:新增 CONTRACT_PAYMENT_EXTRACT |
| `service/ai/AiService.java` | 修改:新增 extractPaymentPlan |
| `entity/Notification.java` | 修改:加 contractId 字段 |

### 前端 (修改 3)

| 文件 | 改动 |
|---|---|
| `api/contract.ts` | 修改:加 payments CRUD + extractPaymentPlan |
| `views/ContractDetail.vue` | 修改:加"付款计划"Tab + 表单 + AI 按钮 |
| `components/NotificationBell.vue` | 修改:handleClick 支持 contractId 跳转 |

---

## 三、实现步骤

- [x] 1. V29 SQL 迁移(本地 psql 验证表结构) ✅ 部署后自动执行
- [x] 2. ContractPayment 实体 + Repository + Service + Controller(无 AI 部分)
- [x] 3. PromptTemplates + AiService 加支付计划抽取
- [x] 4. ContractPaymentController 加 extract 端点
- [x] 5. Notification 实体加 contractId + V29 改 mt_notification
- [x] 6. NotificationService.notify 加 contractId 形参
- [x] 7. ContractPaymentScheduler 每日09:05 检查 + 写入通知
- [x] 8. contract.ts 加前端 API
- [x] 9. ContractDetail.vue 加付款计划 Tab
- [x] 10. NotificationBell.vue handleClick 支持 contractId 跳转
- [x] 11. 后端编译 + 前端构建 + 部署 ✅
- [x] 12. E2E 回归 + 新功能验证 ✅ (32/32 + payments API 200 + AI extract 400 proper error)

---

## ✅ 验证结果(2026-08-09)

| 验证项 | 命令 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| V29 迁移 | docker logs web-server grep Flyway | 表创建 | mt_contract_payment + mt_notification.contract_id | ✅ |
| 付款列表 | `GET /api/contracts/14/payments` | 200 + [] | 200 + [] | ✅ |
| 付款创建 | `POST /api/contracts/14/payments` | 200 + id | 200 + id=1 | ✅ |
| AI 抽取(空文档) | `POST /api/contracts/14/payments/extract` | 400 + 友好提示 | 400 + "合同文档为空" | ✅ |
| 文档文本 | `GET /api/documents/325/text` | 200 + text | 200 + text="" | ✅ |
| E2E 回归 | `node tests/contract-e2e.mjs` | 32/32 | 32/32 | ✅ |
| 后端编译 | `mvn clean package -DskipTests` | BUILD SUCCESS | BUILD SUCCESS | ✅ |
| 前端构建 | `npm run build` | built ok | ✓ built in 14.83s | ✅ |

---

## 四、风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退 |
|---|---|---|---|---|
| V29 迁移失败 | 低 | 中 | 本地先 psql 验证 | 删除 V29 文件 |
| AI 抽取质量差 | 中 | 中 | 字段全部 nullable,用户必须确认后才入库 | 隐藏 AI 按钮 |
| scheduler 重复发通知 | 中 | 中 | reminder_sent 标志位 | 已设计 |
| 通知跳转错合同 | 低 | 中 | contractId null check + 深链标记模式 | 不跳转 |

---

## 五、验证标准

- [ ] 合同详情 → 付款计划 Tab → 添加/编辑/删除正常
- [ ] AI 抽取:新建合同 → 点"AI 提取" → 弹出候选表单 → 用户确认 → 入库
- [ ] 到期提醒:构造到期 < 7 天的付款 → 09:05 后通知列表出现
- [ ] 通知点击 → 自动跳转合同详情弹窗
- [ ] 已有合同 E2E 32/32 仍通过(无回归)