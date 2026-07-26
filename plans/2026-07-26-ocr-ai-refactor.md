# PDF OCR/AI 流程优化与异步化改造方案 (v2)

> **状态**: 已完成(5 个阶段全部完成)
> **创建日期**: 2026-07-26
> **完成日期**: 2026-07-26
> **维护者**: Claude Code
> **关联报告**: `tests/pdf-ocr-ai-e2e-report.md`
> **关联代码**: `AiProxyService.java` / `AiConfigService.java` / `PdfVisionSseController.java` 等

---

## 📊 状态摘要

**进度**: ░░░░░░░░░░ 0% (方案 v2,基于用户指示:AI 配置统一从管理后台取)

---

## 一、Context — 现状

### MD 编辑器的 AI 配置调用模式(参考)

| 层 | 实现 | 路径 |
|---|---|---|
| 数据库表 | `mt_ai_provider`(type: LLM/VISION/OCR_PADDLE/DOCLING/OCR_TESSERACT) | V25 迁移 |
| 配置管理 | `AiProviderAdminController` CRUD + set-default + refresh + test-connection | `/api/admin/ai/providers/*` |
| 配置中心 | `AiConfigService`(内存缓存,DB 优先,文件 fallback) | 单例服务 |
| 代理层 | `AiProxyService.getTargetUrl() / getApiKey() / getDefaultModel()` | 所有 LLM/Vision 调用入口 |
| MD 调用 SSE | `POST /api/documents/{id}/ai/chat-stream` | 走 `AiProxyService` 代理 |
| 前端 AI 配置 UI | `Admin.vue` AI 配置页 | `apiProvidersApi` |

**关键事实**: `AiProxyService.getTargetUrl()` 内部已经走 `AiConfigService.getActive("LLM")`,**MD 编辑器所有 LLM 调用都从管理后台取配置**。

### PDF 当前问题

| 问题 | 现状 |
|---|---|
| OCR 同步阻塞 | 大文件识别 15 分钟,前端按钮一直"转圈" |
| Vision 配置硬编码 | `PdfVisionSseController.pickVisionModel()` 用环境变量 `LLM_VISION_MODEL`,不走 DB |
| Vision baseUrl/key 硬编码 | `aiProxyService.getTargetUrl()/getApiKey()` 已走 DB(✓ 部分正确) |
| OCR PaddleOCR 走 PaddleOcrProperties | 从 application.yml 读,**不走 DB 配置中心** |
| SSE 错误格式 | 4 个 SSE 端点行为不一致(500 / 200 event:error) |
| OCR 响应格式 | 4 个 API 字段不统一 |
| 前端 AI 状态 | 前端不知道 AI 是否配置好,点击失败才看到 |

---

## 二、整体策略

```
阶段 1: 后端 — AI 配置统一接入 AiConfigService
   ├─ Vision SSE: 改用 AiProxyService(替代 pickVisionModel)
   ├─ PaddleOCR SSE: 新增 AiConfigService.getActive("OCR_PADDLE") 读取
   └─ 新增 /api/ai/status 端点

阶段 2: 后端 — SSE 错误统一(LLM 未配置 → event:error)
   └─ 4 个 SSE 控制器统一加 LLM 配置检测

阶段 3: 后端 — OCR 异步化(MQ)
   ├─ RabbitMqConfig + V27 数据库迁移
   ├─ Producer + Consumer + 任务实体
   └─ 进度 SSE 推送

阶段 4: 前端 — AI 状态感知 + OCR 进度条 + 轻量错误

阶段 5: 文档 + 验证
```

---

## 三、关键技术决策

### 决策 1 · AI 配置接入对齐 MD 编辑器
- **PDF Vision**: 改用 `AiProxyService`(已走 `AiConfigService.getActive("LLM")`),删除 `pickVisionModel()` 硬编码
- **PDF PaddleOCR**: 新增 `AiConfigService.getActive("OCR_PADDLE")` 读取;若 DB 有配置,优先 DB,否则用 `PaddleOcrProperties` fallback
- **PDF Extract Terms / Optimize OCR**: 同样走 `AiProxyService`(已走 DB)

### 决策 2 · OCR 异步化(MQ + SSE)
- **大文件场景**: 同步 HTTP 阻塞 15 分钟,易超时
- **方案**: RabbitMQ 队列 `pdf.ocr.task` + SSE 进度推送
- **状态机**: pending → processing → completed/failed
- **持久化**: 复用 `mt_pdf_task` 表,新增 `progress` / `current_page` 字段

### 决策 3 · SSE 错误统一
- LLM 未配置时:HTTP 200 + `event:error data:{"code":"AI_NOT_CONFIGURED","message":"..."}`
- 不抛异常,不返回 500
- 前端统一处理 `event:error`,显示引导提示

### 决策 4 · API 响应格式统一
- 4 个 OCR API 统一返回:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": { ... 业务字段 ... }
  }
  ```
- 同步 API 保持兼容(增加 `code`/`message`/`data` 包装层,不破坏现有字段)

### 决策 5 · 前端 AI 状态感知
- 编辑器加载时调 `/api/ai/status` 一次,缓存到 store
- 各 AI 按钮点击前检查状态
- 未配置时弹引导提示(打开管理后台 AI 配置页)

---

## 四、详细修改方案

### 4.1 后端阶段 1: AI 配置统一接入

#### 4.1.1 `AiProxyService` 改造(新增 VISION/OCR 支持)
- **文件**: `AiProxyService.java`
- **改动**:
  - 增加 `getVisionUrl()` / `getVisionKey()` / `getVisionModel()` 方法
  - 内部调用 `AiConfigService.getActive("VISION")`
  - 与 MD 编辑器共享同一配置源(LLM 用 getActive("LLM"),Vision 用 getActive("VISION"))

#### 4.1.2 `PdfVisionSseController` 改造
- **文件**: `PdfVisionSseController.java`
- **改动**:
  - 删除 `pickVisionModel()` 硬编码
  - 改用 `AiProxyService.getVisionUrl()` / `getVisionKey()` / `getVisionModel()`
  - 调用 LLM 前检查:`AiProxyService.getActive("VISION") == null` → 直接 `event:error AI_NOT_CONFIGURED`

#### 4.1.3 `PaddleOcrClient` 改造
- **文件**: `PaddleOcrClient.java`
- **改动**:
  - 新增读 `AiConfigService.getActive("OCR_PADDLE")`,如果 DB 配置优先
  - 保留 `PaddleOcrProperties` 作为 fallback
  - health check 也用 DB 配置的 URL

#### 4.1.4 `AiStatusController` 新增
- **新文件**: `AiStatusController.java`
- **接口**: `GET /api/ai/status`
- **响应**:
  ```json
  {
    "llm": true,
    "vision": false,
    "ocrPaddle": true,
    "docling": false,
    "details": {
      "llm": { "name": "GPT-4", "defaultModel": "gpt-4o-mini" },
      "ocrPaddle": { "name": "PaddleOCR-中文", "baseUrl": "http://ocr-paddle:5003" }
    }
  }
  ```

### 4.2 后端阶段 2: SSE 错误统一

#### 4.2.1 4 个 SSE 控制器统一检测
- **文件**: `PdfVisionSseController.java` / `PdfExtractTermsSseController.java` / `PdfOptimizeOcrSseController.java` / `PdfRecognizeService.recognize` 间接调用
- **改动**:
  - 工具方法:`AiConfigService.requireActive("LLM")` → 返回 null 时抛业务异常,统一异常处理返回 `event:error`
  - 或每个控制器头部统一加检测:`if (AiProxyService.getActive(...) == null) { sendError("AI_NOT_CONFIGURED"); return; }`

### 4.3 后端阶段 3: OCR 异步化(MQ)

#### 4.3.1 数据库迁移 V27
- **新文件**: `miaotongdoc-server/src/main/resources/db/migration/V27__add_pdf_ocr_task_progress.sql`
- **改动**:
  - `mt_pdf_task` 增加 `progress INT DEFAULT 0`, `current_page INT DEFAULT 0`, `started_at TIMESTAMP`, `completed_at TIMESTAMP`, `error TEXT`

#### 4.3.2 RabbitMQ 配置
- **新文件**: `RabbitMqConfig.java`
- **改动**:
  - 声明队列 `pdf.ocr.task`(持久化)
  - 配置 `RabbitTemplate` + Jackson2JsonMessageConverter
  - docker 已运行 RabbitMQ 容器,无需新建

#### 4.3.3 OCR 任务消息
- **新文件**: `PdfOcrTaskMessage.java`
- **字段**: `taskId`、`documentId`、`model`、`userId`、`submittedAt`

#### 4.3.4 Producer + Consumer
- **新文件**: `PdfOcrTaskProducer.java`(注入 RabbitTemplate,发送消息)
- **新文件**: `PdfOcrTaskConsumer.java`(`@RabbitListener` 接收,调用 `PdfRecognizeService.recognizeWithPaddle`)
- **逻辑**: Consumer 处理时实时更新 `mt_pdf_task` 的 progress / current_page

#### 4.3.5 OCR 进度 SSE
- **新文件**: `PdfOcrProgressSseController.java`
- **接口**: `GET /api/pdf/{id}/ocr-progress?taskId=xxx`(SSE)
- **逻辑**: 订阅 `mt_pdf_task` 进度变化,推送 SSE 事件 `progress / done / error`

#### 4.3.6 `PdfController.recognizePaddle` 改造
- 改为创建 `mt_pdf_task` 记录 + 发 MQ 消息,立即返回 `taskId`
- 兼容:保留同步方法(标 deprecated)供小文件快速识别

### 4.4 前端阶段 4: 改造

#### 4.4.1 AI 状态 store
- **新文件**: `useAiStatus.ts`
- **逻辑**: 调 `/api/ai/status`,缓存到 ref,提供 `isAvailable(type)` 方法

#### 4.4.2 OCR 异步任务 UI
- **文件**: `PdfEditor.vue` + `usePdfOcrProgress.ts`
- **改动**:
  - `usePdfOcrProgress` 改为订阅 `/ocr-progress` SSE
  - 点击 OCR 按钮 → 调 `/recognize-paddle` 拿 `taskId` → 连接 SSE
  - 显示进度条 + `当前页/总页数`

#### 4.4.3 AI 按钮引导
- **文件**: `PdfEditor.vue`
- **改动**:
  - 点击 AI 按钮前检查 `useAiStatus.isAvailable('LLM')`
  - 未配置时:`ElMessageBox.confirm("AI 服务未配置,前往管理后台?", "前往", "取消")`
  - 点击"前往" → `window.open('/admin?tab=ai')`

#### 4.4.4 轻量错误提示
- **文件**: `PdfEditor.vue` 的 `onOcrRecognize`
- **改动**:
  - 删除阻塞式 `ElMessageBox`
  - 失败时 `ElNotification` + 切换按钮文本"重试"

---

## 五、涉及文件

### 后端 Critical Files
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/service/AiProxyService.java` - 加 VISION 方法
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/PdfVisionSseController.java` - 统一配置 + 错误处理
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/PdfExtractTermsSseController.java` - 统一错误
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong\doc/controller/PdfOptimizeOcrSseController.java` - 统一错误
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PaddleOcrClient.java` - 走 AiConfigService

### 后端新增
- `miaotongdoc-server/src/main/java/com/miaotong/doc/config/RabbitMqConfig.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/AiStatusController.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/mq/PdfOcrTaskMessage.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/mq/PdfOcrTaskProducer.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/mq/PdfOcrTaskConsumer.java`
- `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/PdfOcrProgressSseController.java`

### 数据库
- `miaotongdoc-server/src/main/resources/db/migration/V27__add_pdf_ocr_task_progress.sql`

### 前端
- ⭐ `miaotongdoc-web/src/components/PdfEditor.vue` - OCR 异步 UI + AI 引导
- ⭐ `miaotongdoc-web/src/composables/pdf/usePdfOcrProgress.ts` - 改为 SSE 进度订阅
- `miaotongdoc-web/src/composables/useAiStatus.ts` - AI 状态 store
- `miaotongdoc-web/src/api/pdf.ts` - 异步 OCR + AI status API

### 测试
- `miaotongdoc-web/tests/pdf-ocr-ai-e2e.mjs` - 加异步任务 + 大文档用例

---

## 六、实现步骤

### 阶段 1: AI 配置接入(关键,先做)
- [ ] 1.1 `AiProxyService` 加 VISION 方法
- [ ] 1.2 `PdfVisionSseController` 改用 AiProxyService
- [ ] 1.3 `PaddleOcrClient` 走 AiConfigService(读取 OCR_PADDLE)
- [ ] 1.4 新增 `AiStatusController`

### 阶段 2: SSE 错误统一
- [ ] 2.1 4 个 SSE 控制器加 LLM 配置检测
- [ ] 2.2 统一返回 `event:error data:{code,message}`

### 阶段 3: OCR 异步化(MQ)
- [ ] 3.1 数据库 V27 迁移
- [ ] 3.2 RabbitMqConfig
- [ ] 3.3 Entity + Repository + Message + Producer + Consumer
- [ ] 3.4 进度 SSE 控制器
- [ ] 3.5 `PdfController.recognizePaddle` 改异步

### 阶段 4: 前端改造
- [ ] 4.1 `useAiStatus` store
- [ ] 4.2 OCR 进度条 UI
- [ ] 4.3 AI 按钮引导
- [ ] 4.4 轻量错误提示

### 阶段 5: 验证
- [ ] 5.1 重建 jar + 重启 web-server
- [ ] 5.2 跑完整 E2E
- [ ] 5.3 大文档 OCR 测试(50 页+)
- [ ] 5.4 AI 未配置引导测试
- [ ] 5.5 最终报告 + 提议 commit

---

## 七、测试策略

### 单元覆盖
- `AiProxyService.getVisionUrl()` 等方法
- `AiConfigService.getActive("OCR_PADDLE")` vs PaddleOcrProperties fallback

### 集成验证
- 4 个 SSE 端点 LLM 未配置时统一 `event:error`
- 大文件 OCR 异步任务进度推送
- 多文档并发 OCR 不阻塞

### UI E2E
- OCR 进度条实时更新
- AI 按钮未配置时引导提示
- 大文档识别时编辑器可交互

---

## 八、风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退 |
|---|---|---|---|---|
| RabbitMQ 容器异常 | 低 | 高 | 已运行 rabbitmq + healthcheck | 同步方法保留 |
| 异步任务堆积 | 中 | 中 | Consumer 单线程消费,任务超 5min 报警 | 后台脚本清理 stuck 任务 |
| SSE 长连接占用 | 低 | 中 | 单文档 1 个连接,识别完自动断开 | 连接数监控 |
| V27 迁移失败 | 低 | 中 | 字段都有 DEFAULT,兼容旧数据 | 回滚 SQL |
| AiProxyService 改 VISION 影响 MD | 中 | 中 | 加新方法,不改 LLM 方法 | MD 用 LLM,PDF 用 VISION,互不影响 |

---

## 九、验收标准

- [ ] PDF Vision 调用走 `AiConfigService.getActive("VISION")`,与 MD 编辑器同源
- [ ] 4 个 SSE 端点 LLM 未配置时统一 `event:error`(非 500)
- [ ] `/api/ai/status` 返回 4 个 type 的配置状态
- [ ] OCR 提交后立即返回 taskId
- [ ] 50 页大文档 OCR 完整跑通,SSE 进度 0% → 100%
- [ ] 多文档并发 OCR 不互相阻塞
- [ ] AI 按钮未配置时前端有引导提示
- [ ] PDF OCR/AI 完整 E2E 通过率 ≥ 95%

---

## 十、不在本次范围

- OCR 人工校对功能
- AI 对话历史持久化(已有 useAiChat)
- 智能目录结果在编辑器内可视化
- OCR 历史记录查询页面

---

## 变更日志

- 2026-07-26 v1 创建方案
- 2026-07-26 v2 根据用户指示修订:AI 配置统一从管理后台取,与 MD 编辑器同源