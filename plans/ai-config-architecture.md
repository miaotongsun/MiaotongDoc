# AI 配置架构说明（v2.7）

> **创建日期**: 2026-07-13 · **最后更新**: v2.7 修复 Spring AI ChatClient 动态刷新
> **目的**: 厘清项目中所有 AI 服务的配置来源、调用方、互相影响

---

## ✅ v2.7 核心结论：管理后台 AI 配置 = 真正统一配置

**所有 14 个 AI 调用端点现在都支持热刷新**（管理后台改 LLM URL/Key/Model/Timeout 后立即生效，无需重启）。

**修复内容**（本轮）：
- `AiConfigService.refresh()` 末尾调 `AiService.rebuildClient()`
- 让 Spring AI `ChatClient` 也用最新 baseUrl/apiKey
- 解决 v2.7 之前的"同步方法不动态刷新"问题

## 1. 三个 AI 服务方

项目里有 **三个** AI 服务调用方，**不互相干扰**：

| 调用方 | 端点 | 用户场景 |
|--------|------|----------|
| **OnlyOffice AI 插件** | `/api/ai/proxy` / `/api/ai/config` / `/api/ai/refresh-models` | Word/Excel/Markdown 编辑器工具栏的 AI 按钮（由 OnlyOffice 提供） |
| **MD 编辑器 AI 助手** | `/api/documents/{id}/ai/chat-stream` | MarkdownEditor.vue 工具栏的 AI 助手 / 侧边栏 AI 对话 |
| **PDF AI** | `/api/pdf/{id}/recognize-stream` / `/api/pdf/{id}/ai/vision/stream` / `/extract-terms/stream` / `/optimize-ocr/stream` | PdfEditor.vue 工具栏的 OCR / VLM / 条款抽取 / OCR 优化按钮 |

---

## 2. 配置来源优先级

**所有 5 个 SSE 端点**（MD 编辑器 + 4 个 PDF AI）**都共用一个 LLM 配置**：

```
AiConfigService.getActive("LLM")
  ↓ 优先
  1. mt_ai_provider 表（数据库，动态配置，可热刷新）
  ↓ 没找到
  2. /data/config/ai-config.json（文件，兼容旧版）
  ↓ 没找到
  3. 环境变量 ai-proxy.target-url / ai-proxy.api-key（启动时配置）
  ↓
  4. 空字符串
```

**OnlyOffice AI 插件** 也走同一套优先级（`AiProxyService.getTargetUrl()` 内部集成 `AiConfigService`）。

### ⚠️ 关键补充：Spring AI ChatClient 动态刷新（v2.7 修复）

`AiService` 内部有一个 Spring AI `ChatClient` 实例，**构造时烤入 baseUrl/apiKey**。原实现里这个 ChatClient 不会动态刷新，导致同步端点（chat/translate/rewrite/summarize/generate/vision-chat）配置不会更新。

**v2.7 修复**：

```
AiProviderAdminController.create/update/delete
  ↓ 写 DB
AiConfigService.refresh()  ← 新增：刷新后调 AiService.rebuildClient()
  ↓
AiService.rebuildClient()  ← 重建 Spring AI ChatClient（用新 baseUrl/apiKey）
  ↓
下次 chat() / visionChat() 自动用新配置
```

**实现细节**：
- `AiConfigService` 注入 `Optional<AiService>`（避免循环依赖）
- `refresh()` 末尾调 `aiService.ifPresent(svc -> svc.rebuildClient())`
- 整个链路**热生效**，无需重启

---

## 3. AiProxyService 是什么？

**AiProxyService 是 OnlyOffice AI 插件的代理**。它的核心作用：

```java
// /api/ai/proxy 端点
// OnlyOffice 插件调用："帮我用 LLM 总结这段文字"
// 后端收到请求 → 转发到配置的 LLM 服务 → 返回结果给插件
```

**它不是一个独立的 AI 服务**，而是 OnlyOffice 插件的"中间人"。

### AiProxyService 的所有方法

| 方法 | 用途 | 给谁用 |
|------|------|--------|
| `getTargetUrl()` | 拿 LLM base URL | OnlyOffice 插件 + MD/PDF AI（间接） |
| `getApiKey()` | 拿 LLM API key | OnlyOffice 插件 + MD/PDF AI（间接） |
| `getDefaultModel()` | 拿默认模型名 | OnlyOffice 插件 + MD/PDF AI（间接） |
| `getConfig()` | 拼装 OnlyOffice AI 插件协议 | **只**给 OnlyOffice 插件初始化用 |
| `proxy()` | 转发插件请求到 LLM | **只**给 OnlyOffice 插件 |
| `refreshModels()` | 拉 LLM 模型列表 | **只**给 OnlyOffice 插件 |
| `saveConfig()` | 写文件配置 | 旧版兼容，新版用 `/admin/ai/providers` |
| `getCurrentConfig()` | 读当前配置 | 旧版兼容 |

**关键点**：`getTargetUrl / getApiKey / getDefaultModel` 这 3 个方法是**公共配置访问层**，被 5 个 SSE 端点复用。

---

## 4. v2.7 改造：动态配置（AiConfigService）

### 为什么需要？

**改造前**（v2.6 及之前）：
- LLM 配置只能改 `.env` 或文件 `ai-config.json`，**必须重启后端**
- 管理后台的 "AI 配置" 页面**看起来能改，但实际写不到数据库**（PUT /ai/settings 只写文件）

**改造后**（v2.7）：
- 在管理后台改 AI 配置 → 立即写入 `mt_ai_provider` 表 → 立即生效
- 无需重启后端
- 支持多个 LLM Provider（OpenAI / DeepSeek / 阿里云 / 自建）切换

### 架构

```
管理后台 Admin.vue
  ↓ 点"保存"
PUT /api/admin/ai/providers/{id}
  ↓
AiProviderAdminController
  ↓ 加密 API Key
mt_ai_provider (DB)
  ↓
AiConfigService.refresh()   ← 清内存缓存
  ↓
下次 AiProxyService.getTargetUrl() 调用时
  → AiConfigService.getActive("LLM")  ← 读 DB 最新值
  → 返回给调用方（OnlyOffice 插件 / MD/PDF AI）
```

### 哪些文件改了

| 文件 | 改动 | 影响 |
|------|------|------|
| 🆕 `AiConfigService.java` | 内存缓存 + 热刷新 | 新增 |
| 🆕 `AiProvider.java` (entity) | JPA 实体 | 新增 |
| 🆕 `AiProviderRepository.java` | JPA Repository | 新增 |
| 🆕 `AiProviderAdminController.java` | `/api/admin/ai/providers` CRUD | 新增 |
| 🆕 `V25__add_ai_provider_table.sql` | 数据库迁移 | 新增 |
| ✏️ `AiProxyService.java` | 构造函数加 `Optional<AiConfigService>` 注入，3 个 getter 加 DB 优先逻辑 | **OnlyOffice 插件 API 100% 兼容** |
| ✏️ `Admin.vue` | `saveAiConfig` 优先调新接口，失败回退旧接口 | 前端兼容 |

---

## 5. AiProxyService 是不是会污染 OnlyOffice 插件？

**答：不会**。原因：

1. **AiConfigService 是 `Optional` 依赖**（`@Autowired(required = false)`）
   - 如果数据库没 LLM 配置 → Optional 为空 → AiProxyService 走文件/env
   - OnlyOffice 插件在 DB 空时**行为完全不变**

2. **AiProxyService 的公共方法签名 100% 保留**
   - `getTargetUrl() / getApiKey() / getDefaultModel()` 接口未变
   - `getConfig() / proxy() / refreshModels()` 实现未改
   - `/api/ai/proxy / config / refresh-models / settings` 端点 URL + 协议未变

3. **OnlyOffice 插件读的 key 永远是 `targetUrl` 字段值**（`getConfig()` 方法内调用 `targetUrl != null ? targetUrl : ""`），不会变

4. **如果想恢复原行为**（不使用 DB）：删掉 `mt_ai_provider` 表所有 LLM 记录即可，AiProxyService 自动回退到文件/env

---

## 6. 各端点配置一览（v2.7 后**全部**可热刷新）

### OnlyOffice AI 插件（`AiProxyService`）✅ 热生效
```
配置来源优先级:
  1. DB mt_ai_provider (type='LLM', is_default=true)
  2. /data/config/ai-config.json
  3. 环境变量 ai-proxy.target-url / api-key

端点:
  POST /api/ai/proxy           ← 插件代理
  GET  /api/ai/config          ← 插件初始化
  POST /api/ai/refresh-models  ← 插件刷新模型
  GET  /api/ai/settings        ← 兼容旧版管理后台
  PUT  /api/ai/settings        ← 兼容旧版（写文件）

新端点:
  GET    /api/admin/ai/providers       ← 列出所有 Provider
  POST   /api/admin/ai/providers       ← 创建
  PUT    /api/admin/ai/providers/{id}  ← 更新
  DELETE /api/admin/ai/providers/{id}  ← 删除
  POST   /api/admin/ai/providers/refresh ← 手动刷新缓存（自动触发 rebuildClient）
```

### MD 编辑器 AI（`AiChatSseController` + `DocumentAiController` + `AiService`）✅ 热生效

`AiService` 通过注入 `AiProxyService` 拿配置，**所有端点**现在都支持热刷新。

| 端点 | 实现 | 动态 |
|------|------|------|
| `POST /api/documents/{id}/ai/chat-stream` | `AiService.chatStreamSse()` (HttpURLConnection) | ✅ |
| `POST /api/documents/{id}/ai/chat` | `AiService.chat()` (Spring AI ChatClient) | ✅（v2.7 修复） |
| `POST /api/documents/{id}/ai/summarize` | `AiService.summarize()` → `chat()` | ✅ |
| `POST /api/documents/{id}/ai/translate` | `AiService.translate()` → `chat()` | ✅ |
| `POST /api/documents/{id}/ai/rewrite` | `AiService.rewrite()` → `chat()` | ✅ |
| `POST /api/documents/{id}/ai/generate` | `AiService.generate()` → `chat()` | ✅ |
| `POST /api/documents/{id}/ai/generate-stream` | `AiService.chatStreamSse()` | ✅ |
| `POST /api/documents/{id}/ai/vision-chat` | `AiService.visionChat()` (Spring AI ChatClient) | ✅（v2.7 修复） |

### PDF AI（4 个独立控制器）✅ 热生效

| 端点 | 说明 |
|------|------|
| `POST /api/pdf/{id}/recognize-stream` | OCR 识别（Docling/PaddleOCR/Tesseract）|
| `POST /api/pdf/{id}/ai/vision/stream` | VLM 视觉问答（多模态 LLM） |
| `POST /api/pdf/{id}/ai/extract-terms/stream` | 关键条款抽取（结构化 LLM） |
| `POST /api/pdf/{id}/ai/optimize-ocr/stream` | OCR 结果去噪（LLM） |

### Spring AI 配置（`spring.ai.openai.*`）
```
用途: Spring AI ChatClient 的初始 baseUrl/apiKey（启动时烤入）
位置: application.yml
优先级: 最低（AiConfigService 接管后这个几乎不用，但作为兜底保留）

注意: 即使没有 DB 配置文件，AiProxyService 仍能通过环境变量返回配置
      AiService.rebuildClient() 会用 AiProxyService.getTargetUrl() 重建 ChatClient
```

---

## 7. 管理后台使用流程

### 修改 LLM 配置
1. 进入 **Admin → AI 配置**
2. 填入 LLM URL / Key / Model / Timeout
3. 点 **保存**
4. 系统调用 `PUT /api/admin/ai/providers/{id}`
5. 写入数据库 + 触发 `AiConfigService.refresh()`
6. 提示"AI 配置已保存（数据库）"
7. 立即生效，**无需重启**

### 多 LLM Provider 切换（API 方式）
```bash
# 1. 添加新 Provider（OpenAI）
curl -X POST http://localhost:9004/api/admin/ai/providers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "LLM",
    "name": "OpenAI",
    "baseUrl": "https://api.openai.com/v1",
    "apiKey": "sk-...",
    "defaultModel": "gpt-4o-mini",
    "isDefault": true
  }'

# 2. 添加 DeepSeek
curl -X POST http://localhost:9004/api/admin/ai/providers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "LLM",
    "name": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "apiKey": "sk-...",
    "defaultModel": "deepseek-chat",
    "isDefault": false
  }'

# 3. 切换默认 Provider
curl -X PUT http://localhost:9004/api/admin/ai/providers/2 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...,"isDefault": true}'

# 4. 立即生效（无需重启）
```

---

## 8. 常见误解澄清

### ❌ "AiProxyService 是给 OnlyOffice 用的，不能碰"

✅ **正确**：AiProxyService 公共 API（`getTargetUrl / getApiKey / getDefaultModel / getConfig / proxy / refreshModels`）**不能改**。但**内部实现可以加 Optional 依赖**（这就是 v2.7 做的）。

✅ **正确**：AiProxyService 已经是 MD/PDF AI 的**间接配置来源**（通过 `getTargetUrl()` 共享）。改造前是这样，改造后还是这样。

### ❌ "MD 编辑器 AI 用的是 Spring AI"

✅ **半对**：application.yml 里有 `spring.ai.openai.*` 配置（备选），但**实际调用走 `AiProxyService`**。Spring AI 是历史遗留配置，目前未被任何控制器使用。

### ❌ "改 AiProxyService 会污染 OnlyOffice 插件"

✅ **不正确**：AiProxyService 的公共方法签名 100% 保留。OnlyOffice 插件协议（`/api/ai/config` 返回的 JSON 结构）未改。**当 DB 无 LLM 配置时，行为完全等价于改造前**。

---

## 9. 维护 checklist

当修改 AI 配置相关代码时：

- [ ] **不要**修改 `AiProxyService.getConfig()` 返回的 JSON 结构（OnlyOffice 插件协议）
- [ ] **不要**修改 `/api/ai/proxy` 的请求/响应格式
- [ ] **可以**修改 `AiProxyService.getTargetUrl/getApiKey/getDefaultModel` 内部实现（已支持 DB 优先）
- [ ] **可以**修改 `AiConfigService.refresh()` 内部实现
- [ ] **新增** LLM 调用方时，**注入 AiProxyService** 拿配置（不要直接读环境变量）
- [ ] **新增** AI Provider 类型时（如 OCR/VISION/DOCLING），在 `mt_ai_provider` 表用不同 `type` 区分

---

*文档维护：Claude Code · 2026-07-13*