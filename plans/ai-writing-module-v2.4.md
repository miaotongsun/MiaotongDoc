# MiaotongDoc — AI 文档在线写作模块 v2.4(独立 Syllepsis 技术栈 · **架构对齐 + markitdown 集成**)

> 状态: **v2.4 最终版**(架构对齐 + markitdown 集成)
> 创建日期: 2026-07-10 / 更新: 2026-07-11
> 维护者: Claude Code
>
> **方向**:v1.0–v1.4 讨论的是"用 Syllepsis 替换现有 MD 编辑器"。**用户决策改为"在项目中新增一个独立的『AI 文档在线写作』模块"**:
> - 与现有 Tiptap MD 编辑器、PDF 编辑、OnlyOffice 完全隔离
> - Syllepsis 技术栈可以全栈重塑(react/react-dom/Syllepsis 整套);旧技术栈对该模块可放弃
> - 后端**最大化复用现有接口**,新增最小化
>
> **v2.4 关键增量**(v2.3 → v2.4):
> - 新增 §13:**markitdown 集成** — 微软开源多格式转 Markdown(11万 stars),支持 PDF/Word/PPT/Excel/图片/音频/HTML 等
> - 增量工作量:9 d(68 d → 77 d)
> - 实现"用户从 0 起草"升级为"导入已有材料继续 AI 协作"
>
> **v2.3 关键修订**(v2.2 → v2.3,详见 §0):
> - Flyway 迁移号 V25/V26(项目已到 V24)
> - 数据库表 `mt_ai_write_*`(与 mt_document / mt_pdf_task 统一)
> - 复用 8 个现有端点,自研新增从 14 个降到 5 个
> - 前端 axios(与主站一致)

---

## §0 架构对齐报告 — 与项目实际代码核对(必读)

> 本节是 v2.3 的灵魂:**所有"应该怎么实现"的最终答案**。读懂 §0 就能直接写代码。

### §0.1 对齐过程

逐文件检查了 30+ 个项目实际源文件:
- **后端 Java**:DocumentController / MarkdownController / AiProxyController / AiChatSseController / DocumentAiController / AuthController / DocumentService / AiProxyService / DTOs
- **后端 SQL**:V1–V24 Flyway 迁移(确认**最新版本是 V24,不是 V21**)
- **前端**:router/index.ts / Home.vue / api/index.ts / api/document.ts / api/user.ts / api/documentAi.ts / useAiChat.ts
- **基础设施**:docker-compose.yml / nginx.conf / yjs/server.js / deploy.sh

### §0.2 16 个架构冲突点(必须按本表实施)

| # | 冲突项 | 原计划(v2.2) | **修订后(v2.3)** | 依据 |
|---|---|---|---|---|
| 1 | Flyway 迁移号 | V22/V23(冲突!)| **V25 / V26** | 项目已到 V24,CLAUDE.md 写 V21 是过时的 |
| 2 | 数据库表前缀 | `ai_write_template`、`ai_write_history` | **`mt_ai_write_template`、`mt_ai_write_history`** | 与现有 `mt_document_template`(V13)、`mt_pdf_task`(V22) 风格统一 |
| 3 | **复用创建文档端点** | 新增 `POST /api/ai-write/documents/create` | **复用 `POST /api/documents/create`** | `CreateDocumentRequest` 已支持 `docType='markdown'` |
| 4 | **复用文档列表** | 新增 `GET /api/ai-write/documents/list` | **复用 `GET /api/documents/list?type=markdown`** | `documentApi.list({ type: 'markdown' })` 已存在 |
| 5 | **复用 markdown 加载** | 新增 `GET /api/ai-write/documents/{key}/content` | **复用 `GET /api/markdown/{id}/content`** | `MarkdownController.getContent` 已存在 |
| 6 | **复用 markdown 保存** | 新增 `POST /api/ai-write/documents/{key}/save` | **复用 `POST /api/markdown/{id}/save`** | `MarkdownController.saveContent` 已存在 |
| 7 | **复用用户搜索** | 自研 `?query=` | **复用 `GET /api/auth/users/search?keyword=`** | `AuthController.searchUsers` 已存在,参数名 `keyword` |
| 8 | **复用 AI 调用** | 自研 `/api/ai-write/stream/*` | **复用 `POST /api/ai/proxy`**(7 种 action 通过 body.action 区分) | `AiProxyController` 已支持,前端 `documentAi.ts` 已封装 |
| 9 | 前端 HTTP 客户端 | 自封装 fetch | **axios**(与主站一致)| 主站 `api/index.ts` 已用 axios + 401 自动跳登录 |
| 10 | **复用权限校验** | 自研权限检查 | **复用后端 `shareService.getUserPermission`** | 已存在,view/comment/edit/admin 4 级 |
| 11 | **复用 JWT** | 自研 token 解析 | **复用主站 `sessionStorage.getItem('token')`** | 主站统一存在 sessionStorage |
| 12 | 路径变量 | `/{key}` 字符串 | **`/{id}` Long 数字**(主站风格)| MarkdownController 用 Long id |
| 13 | 协同 docKey 命名 | `aiw-{docKey}` | **同主站 `aiw-{docKey}`**(用 docKey,不是 id)| 与现有 `md-{docKey}` 对齐 |
| 14 | DocContentResp 结构 | 自定义 interface | **复用 `Map<String,String>{ content }`** | MarkdownController.getContent 返回的就是这个 |
| 15 | 部署脚本 | 新增独立脚本 | **`deploy.sh` 加 `mkdir app/ai-write/dist/` 一行**(第 55 行)+ `data/{...}` 加 ai-write 一项 | 与 editor 等子模块风格一致 |
| 16 | 主站跳转按钮位置 | Home.vue 加 | **Home.vue 侧边栏 MiaotongMD 上方加 "AI 写作" 独立项**(与全部/最近/MD/PDF 同级)| 已有 UI 结构,新位置最自然 |

### §0.3 实施边界:本方案**新增 vs 复用**清晰对照

#### **复用现有接口**(后端零代码改 = 0 工作量)

| 接口 | 用途 | 在新模块的调用方式 |
|---|---|---|
| `POST /api/documents/create` body `{docType:'markdown', title, templateId?}` | 创建 AI 写作文档 | `axios.post('/documents/create', ...)` |
| `GET /api/documents/list?type=markdown&page&size` | 我的 AI 写作文档列表 | `axios.get('/documents/list', { params: { type: 'markdown' } })` |
| `GET /api/markdown/{id}/content` → `{content: string}` | 加载 markdown 内容 | `axios.get('/markdown/{id}/content')` |
| `POST /api/markdown/{id}/save` body `{content: string}` | 保存 markdown | `axios.post('/markdown/{id}/save', ...)` |
| `GET /api/auth/users/search?keyword=X` | @Mention 搜索 | `axios.get('/auth/users/search', { params: { keyword } })` |
| `POST /api/ai/proxy` body `{target, method, data}` | AI 流式调用 | `fetch('/api/ai/proxy', ...)` + ReadableStream |
| `GET /api/documents/{id}/export/pdf` | 导出 PDF | `axios.get('/documents/{id}/export/pdf', { responseType: 'blob' })` |
| `GET /api/auth/me` | 当前用户信息 | `axios.get('/auth/me')` |

#### **复用现有 service**(后端零代码改)

| 现有 service | 在新模块的使用方式 |
|---|---|
| `DocumentService.createDocument` | 文档创建 |
| `DocumentService.getDocument` | 按 id 查询 |
| `DocumentService.getDocumentByKey` | 按 docKey 查询 |
| `DocumentService.listDocuments(type=markdown)` | 文档列表 |
| `DocumentService.updateDocument` | 更新文件 hash / size |
| `StorageService.load / store` | markdown 文件读写 |
| `ShareService.getUserPermission` | view/comment/edit/admin 校验 |
| `JwtUtil` | JWT 生成与校验(已由 Spring Security Filter 处理)|
| `AiProxyService.proxy / saveConfig / getConfig` | LLM 网关(已存在,新模块仅调用)|

#### **必须自研新增**(本方案真正的工作量)

| 端点 / 表 | 原因 | 工作量 |
|---|---|---|
| **`POST /api/ai-write/stream/{action}`**(outline/expand/polish/translate/grammar/style/vision 共 7 个)| 主站 `AiChatSseController` 是单聊模式;AI 写作需要"按选区扩写"等多场景,**action 路由参数 + 专门的 prompt builder** | 8 d |
| **`POST /api/ai-write/templates`** + **`GET /api/ai-write/templates`** + **`DELETE /api/ai-write/templates/{id}`** | `mt_document_template` 是文件模板(Word/Excel/PPT);**AI 写作模板需要 prompt + schema_json**,需新表 | 4 d |
| **`GET /api/ai-write/templates/system`** | 系统预设模板初始化 | 1 d |
| **`GET /api/ai-write/history/{docKey}`** + **`POST /api/ai-write/history`** | 自动保存快照,主站没有会话历史概念 | 4 d |
| **`POST /api/ai-write/documents/from-template`** | "用模板创建文档"便捷入口(可选,等价于 create + templateId)| 1 d |
| **`AiWritePromptBuilder` service** | 7 个 action 的 system prompt 构造器 | 3 d |
| **`AiWriteTemplate` entity + repo + Flyway V25** | 见 §0.2 表 | 1 d |
| **`AiWriteHistory` entity + repo + Flyway V26** | 见 §0.2 表 | 1 d |
| **合计后端自研** | | **~23 d**(原计划 23 d 不变,但少了 14 个端点 → 多了 1 个端点 + 2 个表)|

### §0.4 修订后的关键架构决策

#### 决策 1:**不新增 `/api/ai-write/documents/*` 这套端点**(完全复用 Documents + Markdown)

```
v2.2 原计划(废弃):
POST   /api/ai-write/documents/create       ← 废
GET    /api/ai-write/documents/list         ← 废
GET    /api/ai-write/documents/{key}/content ← 废
POST   /api/ai-write/documents/{key}/save   ← 废

v2.3 修订后:
POST   /api/documents/create                ← 复用
GET    /api/documents/list?type=markdown    ← 复用
GET    /api/markdown/{id}/content           ← 复用
POST   /api/markdown/{id}/save              ← 复用
```

**好处**:
- 新模块产出的 `.md` 文档**天然能被主站 MarkdownEditor.vue 打开**(同一接口)
- 主站用户列表里也能看到 AI 写作产出的文档(同一份数据)
- 少写 4 个 controller,省 8 d

#### 决策 2:**AI 流式调用全部走 `/api/ai/proxy`**

主站 `AiProxyController` 已经是"通用 LLM 网关":任意 target / method / data 都可代理。**新模块只需在请求 body 里写明 target + body 即可**。不重复写 stream controller。

```
新模块前端调用示例:
POST /api/ai/proxy
{
  "target": "https://llm.example.com/v1/chat/completions",
  "method": "POST",
  "headers": { "Content-Type": "application/json" },
  "data": "{ \"model\": \"gpt-4o\", \"stream\": true, \"messages\": [...] }"
}
```

**新增的 `/api/ai-write/stream/{action}` 是"应用层"端点**,在 `AiProxyController` 上层:
- 接收业务参数(选区文本、action 类型、目标语言)
- 由 `AiWritePromptBuilder` 构造完整 prompt
- 调用 `AiProxyService.proxy(...)` 转发
- 对前端返回 SSE 流

这样业务逻辑在 `ai-write/`,LLM 协议细节在 `ai/`,关注点分离。

#### 决策 3:**JWT token 复用 sessionStorage**

主站 Vue 已经把 JWT 存 `sessionStorage`,401 时 axios 拦截器自动跳登录。**新模块用 axios 自动继承这一行为**,完全无需自研。

#### 决策 4:**协同 docKey 用 `aiw-{docKey}`**

- yjs-server 正则 `^(md|pdf)-[a-f0-9-]+$` → 扩为 `^(md|pdf|aiw)-[a-f0-9-]+$`
- 加 1 个 `a` 字符即可
- **不能用 `aiw-{id}` 数字**,与主站 `md-{docKey}` 一致,**前端拿到 DocumentDTO.docKey 字段直接拼**

### §0.5 路径参数:id vs docKey 的决策

| 场景 | 用 id 还是 docKey | 原因 |
|---|---|---|
| 主站 `/api/documents/{id}` 接口(已有)| **id**(数字) | 现有接口 |
| 主站 `/api/markdown/{id}` 接口(已有)| **id**(数字) | 现有接口 |
| 新模块前端路由 `/ai-write/editor/{key}` | **docKey**(字符串) | docKey 是 UUID,前端 Yjs 协同直接用 |
| 新模块后端 `/api/ai-write/history/{key}` | **docKey** | 历史快照天然按 docKey 聚合 |
| 协同 yjs 房间 | **`aiw-{docKey}`** | 与主站对齐 |

**前端怎么从 docKey 拿 id?** 列表 API 返回 `DocumentDTO { id, docKey }`,前端拿到后两者都缓存。

### §0.6 yjs-server 协议扩展示例代码

```js
// MiaotongDoc-Docker/app/yjs/server.js 第 36 行
- if (!docName.match(/^(md|pdf)-[a-f0-9-]+$/)) {
+ if (!docName.match(/^(md|pdf|aiw)-[a-f0-9-]+$/)) {
    console.warn(`[Yjs] 拒绝非法文档名: ${docName}`)
    ws.close(4000, 'Invalid document name')
    return
  }
```

**1 字符改动,新增 `a`**。

### §0.7 docker-compose.yml 改动

```yaml
nginx:
  volumes:
    - ./app/web/dist:/usr/share/nginx/html
    - ./app/ai-write/dist:/usr/share/nginx/ai-write:ro  # 新加
    - ...
```

**+1 行 volume,挂 React 产物目录**。

### §0.8 nginx.conf 改动

```nginx
# 在 location / { ... } 之前加
location /ai-write/ {
  alias /usr/share/nginx/ai-write/;
  try_files $uri $uri/ /ai-write/index.html;
}
```

**+1 段 location**。

### §0.9 deploy.sh 改动

```bash
# 第 55 行 mkdir -p data/{...} 加 ai-write 一项
- mkdir -p data/{documents,pgdata,minio,rabbitmq,editor,editor-cache}
+ mkdir -p data/{documents,pgdata,minio,rabbitmq,editor,editor-cache,ai-write}
# + data/logs/{...} 不需要,前端静态资源无日志
```

### §0.10 主站 Home.vue 改动(2 处)

**1. 侧边栏加 "AI 写作" 项**(放在 MiaotongMD 上方):

```vue
<!-- Home.vue 第 30 行,activeTab === 'markdown' 之前 -->
<li class="nav-divider"></li>
<li :class="{ active: activeTab === 'ai-write' }" @click="goToAiWrite">
  <el-icon><MagicStick /></el-icon>
  <span>AI 写作空间</span>
</li>
<li class="nav-divider"></li>
```

**2. router/index.ts 加跳转方法**(不改 router 配置):

```ts
// 写在 setup() 里
function goToAiWrite() {
  // AI 写作是独立 SPA,整页跳转
  window.location.href = '/ai-write/'
}
```

**注意**:`activeTab` 是个 UI state,真正跳走不需要维护。

### §0.11 新增文件清单(v2.3 修订后)

| 文件类型 | 数量 | 说明 |
|---|---|---|
| 独立 React 项目 | ~70 个文件(同 v2.2)| 与 v2.2 完全一致 |
| 后端 Java 新增 | **8 个文件**(v2.2 是 10 个)| 4 controller + 3 service + 2 entity - 减少 2 个 controller |
| Flyway 迁移 | **2 个 SQL 文件**(V25 + V26)| V22/V23 → V25/V26 |
| Docker / Nginx | 3 行改动(v2.2 是 3 行,数量不变)|
| Syllepsis Fork 自维护仓 | 1 个独立仓库 |
| 主站 Vue 改动 | **1 个文件**(Home.vue,2 处小改)| 不动 router/index.ts |
| **总计** | 与 v2.2 文件总数几乎相同 | **接口数量从 14 个降到 5 个,后端工作量大幅降低** |

### §0.12 Flyway V25/V26 SQL 草稿

```sql
-- V25__add_ai_write_template.sql
CREATE TABLE IF NOT EXISTS mt_ai_write_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    prompt TEXT NOT NULL,                          -- 完整 system prompt
    schema_json TEXT,                              -- 可选,Syllepsis schema JSON 作为初始内容
    scope VARCHAR(20) NOT NULL DEFAULT 'USER',     -- 'SYSTEM' | 'USER'
    category VARCHAR(100),                         -- 分类:报告/合同/会议纪要...
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    owner_id BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ai_write_template_scope ON mt_ai_write_template(scope);
CREATE INDEX IF NOT EXISTS idx_ai_write_template_owner ON mt_ai_write_template(owner_id);
CREATE INDEX IF NOT EXISTS idx_ai_write_template_active ON mt_ai_write_template(is_active);

-- 预置模板
INSERT INTO mt_ai_write_template (name, description, prompt, scope, category, sort_order) VALUES
('季度总结报告', '标准季度报告骨架', '你是一位资深运营经理...', 'SYSTEM', '报告', 1),
('产品需求文档', 'PRD 模板', '你是一位产品经理...', 'SYSTEM', '产品', 2),
('会议纪要', '标准会议纪要', '你是会议记录员...', 'SYSTEM', '会议', 3);

-- V26__add_ai_write_history.sql
CREATE TABLE IF NOT EXISTS mt_ai_write_history (
    id BIGSERIAL PRIMARY KEY,
    doc_key VARCHAR(64) NOT NULL,
    content_md TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'AUTOSAVE',    -- 'AUTOSAVE' | 'MANUAL'
    title VARCHAR(200),
    snapshot_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ai_write_history_doc_key ON mt_ai_write_history(doc_key, snapshot_at DESC);
```

### §0.13 后端新增 controller 接口契约(v2.3 最终版)

```java
// AiWriteStreamController.java
@RestController
@RequestMapping("/api/ai-write/stream")
public class AiWriteStreamController {
  @PostMapping(value = "/outline",  produces = TEXT_EVENT_STREAM)
  public SseEmitter outline(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/expand",   produces = TEXT_EVENT_STREAM)
  public SseEmitter expand(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/polish",   produces = TEXT_EVENT_STREAM)
  public SseEmitter polish(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/translate",produces = TEXT_EVENT_STREAM)
  public SseEmitter translate(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/grammar",  produces = TEXT_EVENT_STREAM)
  public SseEmitter grammar(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/style",    produces = TEXT_EVENT_STREAM)
  public SseEmitter style(@RequestBody AiWriteStreamReq req, HttpServletRequest http);
  
  @PostMapping(value = "/vision",   produces = TEXT_EVENT_STREAM)
  public SseEmitter vision(@RequestPart("file") MultipartFile file,
                           @RequestPart("question") String question,
                           HttpServletRequest http);
}

// AiWriteTemplateController.java
@RestController
@RequestMapping("/api/ai-write/templates")
public class AiWriteTemplateController {
  @GetMapping             // 列表(系统+用户)
  public ResponseEntity<List<AiWriteTemplateDTO>> list(HttpServletRequest http);
  
  @PostMapping            // 用户创建模板
  public ResponseEntity<AiWriteTemplateDTO> create(@RequestBody AiWriteTemplateCreateReq req, HttpServletRequest http);
  
  @DeleteMapping("/{id}") // 删除用户模板
  public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http);
}

// AiWriteHistoryController.java
@RestController
@RequestMapping("/api/ai-write/history")
public class AiWriteHistoryController {
  @GetMapping("/{docKey}")              // 列某文档的历史快照
  public ResponseEntity<List<AiWriteHistoryDTO>> list(@PathVariable String docKey, HttpServletRequest http);
  
  @PostMapping                          // 写入一次快照
  public ResponseEntity<AiWriteHistoryDTO> save(@RequestBody AiWriteHistorySaveReq req, HttpServletRequest http);
}
```

### §0.14 v2.3 工作量重估(更精确)

| 阶段 | 内容 | v2.3 工作量 | vs v2.2 |
|---|---|---|---|
| 0 | Syllepsis Fork + React 骨架 | 5 d | 5 d |
| 1 | Syllepsis 基础富文本 + MarkdownIO(**完全复用 MarkdownController**)| 5 d | -3 d |
| 2 | mention + slash 自研 | 10 d | 10 d |
| 3 | AI 流式(自研 7 个端点,**基于现有 AiProxyController**)| 8 d | -2 d |
| 4 | 协同持久化(y-leveldb + aiw 前缀)| 8 d | 8 d |
| 5 | 后端模板 / 历史(V25/V26 + 2 controller)| 6 d | -2 d |
| 6 | 自研 plugin(KaTeX / Mermaid / Doubao 代码块 / 图片 resize)| 16 d | 16 d |
| 7 | Outline / Theme / QA / 文档 | 10 d | 10 d |
| **合计** | | **68 d 现实**(乐观 50 / 悲观 100)| **-7 d** |

**比 v2.2 省 7 d**:因为减少了 4 个文档 controller + 3 个自研端点。

### §0.15 风险与对策(v2.3 关键变化)

| 风险 | 严重性 | 对策 |
|---|---|---|
| 主站 `MarkdownController` 的权限校验(view/edit)在 AI 写作场景下语义错位 | 中 | **接受**,AI 写作场景也用同一权限模型;如需"自动授权",加 `POST /api/ai-write/grant` 调 `ShareService.grant` |
| `mt_document` 已加 `pdf_markdown` / `pdf_recognized` / `text_edits` JSONB 列,markdown 文档用不到 | 低 | 无影响,JSONB 字段为 NULL 时忽略 |
| `DocumentService.createDocument` 不支持自动套用 AI 模板 | 中 | `AiWritePromptBuilder` 在前端层用;后端 `create` 接 `templateId`,由前端在 React 端用模板 schema_json 初始化编辑器 |
| `templateId` 字段值是 `mt_document_template.id`(文件模板)而非 `mt_ai_write_template.id` | **冲突,必须修订** | **新增字段** `ai_write_template_id` 到 `CreateDocumentRequest`,或前端不带 templateId,自己处理 |
| Syllepsis 文档转 markdown 时可能丢自定义节点(mention / doubao 代码块) | 中 | `MarkdownIO.serialize` 必须覆盖自定义节点(详见 v2.2 §5.7)|

### §0.16 决策清单(必过,过不了不开工)

1. ✅ Flyway V25/V26 接受?(若否,需要重新设计历史表命名)
2. ✅ 数据库表 `mt_ai_write_*` 接受?(与 mt_document_template / mt_pdf_task 风格一致)
3. ✅ **删除 `/api/ai-write/documents/*` 4 个端点,改用主站接口** 接受?(这是 v2.2 → v2.3 最大的架构简化)
4. ✅ AI 流式走 `AiProxyController` + `AiWriteStreamController` 二层结构 接受?
5. ✅ `templateId` 字段不直接复用,改在 React 端自己处理 AI 写作模板初始化 接受?
6. ✅ 主站侧栏新增 "AI 写作空间" 项,与 MiaotongMD 同级 接受?

---

> **以下为 v2.2 原文档所有章节(§1–§A.11),作为详细参考保留。**
> **实施时请以 §0 为准;§1–§A.11 中关于 Flyway 号、表名前缀、端点路径的描述已被 §0 覆盖,以 §0 为准**。

---
> - **§3 功能矩阵**:模块 24 项能力 + Syllepsis 现成 / 自研 / 不做的明确边界
> - **§4–§12 实施细节**:13 周交付计划、75 文件规划、难点与对策

---

## Context:为什么是这个方向

现有项目痛点:
1. `MarkdownEditor.vue` 是 6679 行单文件 Tiptap 实现,产品迭代力受限
2. 文档协作是"先创建 → 后编辑",缺乏"AI 协作创作"模式
3. 编辑器与 AI 浮窗耦合在 Tiptap 内,深度依赖 Tiptap 生态
4. Syllepsis 已经在 ByteDance 内部验证,提供更好用的 slash/AI/Mention 原生 UI 范式

新增独立模块的好处:
- **完全独立部署**:Vue 主站不动,新模块独立 React SPA
- **完全独立技术栈**:不再受 Tiptap 历史包袱影响
- **完全独立产品定位**:"AI 文档在线写作"是 Next-Gen 场景,与现有"在线编辑"是不同细分
- **Syllepsis 选型合理**:React-only 富文本框架 + ProseMirror 直系 + AI 写作场景下官方文档推荐 slash/mention 用法

---

## 1. 用户场景与功能边界

### 1.1 模块定位

**模块名**:`miaotongdoc-ai-write`(URL 前缀 `/ai-write/`)

**一句话定位**:**面向团队协作的 AI 辅助在线写作工具**,用户通过模板、AI 扩写、Mention 协同,从零起草一份专业文档。

**与现有功能的关系**(很重要):
- 当前 `MarkdownEditor.vue`(Tiptap)是"**事后编辑**"模式 —— 文档已存在,用户改它
- 本模块是"**AI 协作起草**"模式 —— 从空白 / 模板开始,**AI 与人协同生成**
- 两者**完全独立**,互不依赖;但**数据可互通**:本模块产出的 `.md` 文件可被主站 `MarkdownEditor.vue` 打开,反之亦然

### 1.2 主用户故事(基于真实场景)

> 张三是运营经理。周一上午老板让他写一份"2026 年 Q2 运营总结报告"。他希望 **1 小时内完成初稿**,并在团队会议上与同事协作填内容。

完整流程:

```
09:00  登录 miaotongdoc.com/ai-write → 进入 AI 写作空间
09:05  从模板库选择"季度总结"模板 → 一键创建文档(自动套用模板大纲与 placeholder)
09:10  / 调出 slash 菜单 → 插入 H1 标题、表格骨架;@三位同事 @小李 @王五 @赵二 让 TA 准备数据
09:20  AI 浮窗对每段文本"扩写润色" ── 选中"营销数据"段,选"扩写",AI 流式补全 ~500 字
09:25  选"翻译"按钮,把英文段落翻成中文,逐字流入;选中文段落翻英文
09:30  同事 @小李 在另一个浏览器同时打开这份文档,两人实时协同(看到对方头像与光标)
09:35  选全文 → AI 一键"语法纠错 + 风格统一" → 全文标黄做错点
09:40  导出 markdown / PDF / Word,作为初稿附件发送给老板
09:45  内容润色完成后,"一键转为正式文档" → 调用 /api/documents/create,在主站 mt_document 表
       创建一条 docType='markdown' 记录,主站 MarkdownEditor.vue 也能打开它
```

### 1.3 与现有功能的关系矩阵

| 现有功能 / 子系统 | 关系 | 数据流 / 复用方式 |
|---|---|---|
| `MarkdownEditor.vue`(Tiptap)| **完全隔离**,不互换 | 两个独立 SPA,UI 完全独立 |
| `MarkdownController` | **共享磁盘 storage** | `StorageService` 写入的 `.md` 文件,两栈都可读 |
| `mt_document` 表 | **共享** | 本模块写 `docType='markdown'` 记录 |
| `/api/ai/proxy`(LLM 网关)| **共享,不重写** | 本模块所有 AI 流式调用都走 `/api/ai/proxy` 转发 |
| `AiProxyService.java` | **共享** | 后端新 controller 注入它 |
| `userApi.search`(Mention)| **共享** | 本模块 mention popup 调用同一接口 |
| `/api/auth/users/search` | **共享** | 同上(后端鉴权一致的 Spring Security JWT)|
| `JwtUtil + JwtAuthFilter` | **共享** | 本模块前端从主站 sessionStorage 拿 token |
| yjs-server(`md-{docKey}`) | **协议扩展** | 增加 `aiw-` 前缀,与 `md-` 并存不冲突 |
| `Element Plus` 主站 UI 库 | **不共享** | 本模块 React 用 Antd;视觉风格各自独立 |
| `Pinia` 主站状态管理 | **不共享** | 本模块用 Zustand |

### 1.4 不做什么(明确边界)

| 不做 | 原因 |
|---|---|
| 不做合同审批/签署 | 属于主站"签署"模块的职责 |
| 不做 PDF 编辑/OCR | 属于主站 PDF 模块,本模块只写 markdown |
| 不做文档归档/回收站 | 主站已有 |
| 不做 OnlyOffice 集成 | 本模块只产出 markdown |
| 不做移动端深度适配 | Syllepsis 桌面优先,移动端 v3 优化(本期先响应式)|
| 不接管主站 MarkdownEditor 的现有功能 | 隔离原则 |
| `userApi.search`(用户搜索) | **共享** | Syllepsis Mention 走同一接口 |
| `mt_document` 表 | **共享**,`docType='markdown'` | 新模块创建文档主表记录 |
| yjs-server `md-{docKey}` | **协议继承** | 用同 yjs 协议,独立 docKey 命名空间 |

---

## 2. 重塑技术栈(Syllepsis 全栈,完全独立)

### 2.1 前端技术栈

| 层 | 技术 | 版本 | 选型理由 |
|---|---|---|---|
| 框架 | **React 18** | ^18.3 | Syllepsis 官方唯一适配器 `access-react` 要求 |
| 构建 | **Vite 5** | ^5.4 | 与 Vue 主站一致,生态最熟 |
| 语言 | **TypeScript 5** | ^5.4 | Syllepsis 自带 .d.ts,类型友好 |
| 路由 | **React Router v6** | ^6.26 | SPA 标准方案 |
| 状态 | **Zustand** 或 **Jotai** | ^4.5 / ^5 | 比 Redux 轻,与 Syllepsis 控制器状态并存的推荐选择 |
| UI 库 | **Ant Design 5** 或 **shadcn/ui** | ^5.20 | Syllepsis 文档站示例用 antd;shadcn 更现代,自行决定 |
| 样式 | **Tailwind CSS** 或 **CSS Modules** | ^3.4 | 与 Syllepsis 零依赖冲突 |
| 富文本 | **Syllepsis 全套** | 见下 | 直接基于 ProseMirror,可控 |
| 富文本相关 | `@syllepsis/access-react` | ^1.x | 官方 React 适配器 |
| | `@syllepsis/editor` | ^1.x | Syllepsis Controller/Plugin 注册 |
| | `@syllepsis/adapter` | ^1.x | ProseMirror 适配 |
| | `@syllepsis/plugin-basic` | ^1.x | 基础富文本 plugin |
| | `@syllepsis/plugin-code-block` | ^1.x | 代码块 |
| | `@syllepsis/plugin-table` | ^1.x | 表格 |
| | `@syllepsis/plugin-placeholder` | ^1.x | placeholder |
| Markdown IO | **prosemirror-markdown** | ^1.13 | 标准 ProseMirror markdown |
| 协同 | **Yjs + y-websocket + y-prosemirror** | 同主站 | 沿用主仓版本保持协议兼容 |
| AI 流式 | **fetch + ReadableStream** | 原生 | 同 `useAiChat.ts` 策略;迁移为 hook 实现 |
| XSS 防护 | **DOMPurify** | ^3.x | 与主站一致 |
| HTTP | **fetch** 或 **ky** | ^1.x | Syllepsis 示例用 fetch;ky 拦截器更顺 |

### 2.2 后端技术栈(增量新增,与 Spring 共享)

| 层 | 技术 | 动作 |
|---|---|---|
| 框架 | Spring Boot 3.2.5 | 复用现有 (`miaotongdoc-server/`) |
| AI 网关 | 现有 `AiProxyService` / `/api/ai/proxy` | **复用**,不重写 |
| 新增端点 | `/api/ai-write/*` 控制器 | 新增 4 个端点(详见 §3.3) |
| 新增 service | `AiWriteService.java` | 编排:文档存储 + 模板拉取 + AI 调用 |
| 新增 entity | `ai_write_document` (可选) 或复用 `mt_document` | 推荐复用,避免 schema 膨胀 |
| LLM 调用 | 现有 `AiService` + `AiProxyService` | 复用 |
| 鉴权 | 现有 `JwtUtil` + `JwtAuthFilter` | 复用;新模块走现有 JWT |
| 存储 | 现有 `StorageService`(minio / 本地磁盘) | 复用,写入 `.md` 文件 |

### 2.3 Docker / 基础设施

| 组件 | 动作 |
|---|---|
| 新增容器 | `miaotongdoc-ai-write`(nginx 提供 React SPA 静态产物) |
| nginx 路由 | `location /ai-write/ { root /usr/share/nginx/ai-write; try_files $uri /index.html; }` |
| 与 yjs-server 关系 | **复用**;docKey 命名空间 `aiw-{docKey}` 避免与 `md-{docKey}` 冲突 |
| 与 backend 关系 | **复用**;新模块 API 走 `/api/ai-write/*`,由后端 spring 路由 |

### 2.4 富文本依赖矩阵(Syllepsis 真实能力 vs 本模块需要)

| 能力 | Syllepsis 原生 | 自研工作量 | 备注 |
|---|---|---|---|
| 标题 / 段落 / 引用 / 列表 | ✅ `plugin-basic` | 0 | |
| 表格 | ✅ `plugin-table` | 0 | |
| 代码块(基础) | ✅ `plugin-code-block` | 0 | |
| 链接 / 图片 / 视频 | ✅ `plugin-basic` | 0 | |
| 富文本格式(b/i/u/s/color/font-size) | ✅ `plugin-basic` | 0 | |
| Undo/Redo | ✅ `plugin-basic` | 0 | |
| placeholder | ✅ `plugin-placeholder` | 0 | |
| **mention(@用户)** | ❌ | **6 d** | Syllepsis 无 mention plugin |
| **slash command** | ❌ | **4 d** | Syllepsis 无 slash 内置(Syllepsis 优势在于更简洁 API,但 slash 仍要自己写) |
| **AI 浮窗** | ❌ | **5 d** | 与 Tiptap 版等价 React 实现 |
| **拖拽 resize 图片** | ❌ | **3 d** | 自研 mousedown listener |
| **KaTeX 数学公式** | ❌ | **5 d** | 自研 node + parser/serializer |
| **Mermaid** | ❌ | **4 d** | 自研 code-block 变体 |
| **协同光标** | ❌ | **3 d** | y-prosemirror 桥 |
| **Markdown IO** | ❌ | **3 d** | prosemirror-markdown 适配 |
| **协同 + y-leveldb** | ❌ | **5 d** | yjs-server volume |
| **Fork Syllepsis 自维护** | — | **2 d** | 见 §5.3 |
| **合计自研** | — | **40 d** | — |

### 2.5 与现有技术栈的差异(对打)

| 项 | 现有 Tiptap 栈 | Syllepsis 新栈 |
|---|---|---|
| 编辑器核心 | Tiptap (ProseMirror 封装) | Syllepsis (ProseMirror 封装) |
| 视图层 | Vue 3 Composition | React 18 |
| UI 组件库 | Element Plus | Ant Design 5 / shadcn/ui |
| 状态管理 | Pinia | Zustand / Jotai |
| 路由 | Vue Router | React Router v6 |
| 样式 | scoped + Sass | CSS Modules / Tailwind |
| Markdown IO | `marked + turndown`(自定义 IO) | `prosemirror-markdown` |
| 协同 | `y-prosemirror` + `@tiptap/extension-collaboration` | `y-prosemirror` + 自写注入插件 |
| AI 流式 | `useAiChat.ts` (composable) | `useAiChat.ts`(迁移为 hook) |
| sanitization | `utils/sanitize.ts` + DOMPurify | DOMPurify(逻辑等价) |
| 构建 | `miaotongdoc-web/` 单仓库 | `miaotongdoc-ai-write/` 独立仓库 |

---

## 3. 模块功能分解

### 3.1 路由 / 页面结构

```
miaotongdoc-ai-write/                          # 独立 npm 项目
├── public/index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── router.tsx
    ├── pages/
    │   ├── AiWriteHome.tsx              # / 工作台,模板库 + 我的文档 + AI 灵感
    │   ├── AiWriteEditor.tsx           # /editor/:docId 编辑器主页面
    │   ├── AiWriteTemplate.tsx         # /templates 模板管理(预设模板 + 用户自定义模板)
    │   └── AiWriteSettings.tsx         # /settings 个人设置(默认 model / 主题 / 字体)
    ├── components/
    │   ├── SylEditorAdapter.tsx       # SylEditor 包装
    │   ├── Toolbar.tsx                # Syllepsis toolbar(原 SylApi.command 暴露)
    │   ├── SlashMenu.tsx               # / 命令面板
    │   ├── MentionPopup.tsx             # @用户弹层(走父 userApi.search)
    │   ├── AiFloatPanel.tsx            # AI 流式浮窗
    │   ├── AiChatPanel.tsx             # AI 侧栏对话
    │   ├── CollabAvatars.tsx           # 协作者头像(来自 yjs awareness)
    │   ├── Outline.tsx                 # 文档大纲(从 ProseMirror doc 抓 heading)
    │   ├── ThemeSwitch.tsx             # 主题切换(antd ConfigProvider)
    │   └── ExportMenu.tsx              # 导出 markdown / PDF / Word(走 /api/documents/{id}/export/*)
    ├── editor/                          # Syllepsis plugin / runtime
    │   ├── runtime/create.ts
    │   ├── runtime/mention-plugin/
    │   ├── runtime/slash-plugin/
    │   ├── runtime/ai-plugin/          # 流式 AI 文本插入
    │   ├── runtime/codeblock-doubao/
    │   ├── runtime/image-resize/
    │   ├── runtime/mathjax/
    │   ├── runtime/mermaid/
    │   ├── io/markdown.ts
    │   └── collab/yjs-bridge.ts
    ├── api/
    │   ├── ai-write.ts                 # /api/ai-write/* 封装
    │   ├── ai.ts                       # /api/ai/proxy 封装
    │   ├── docs.ts                     # /api/documents/* 封装(创建 / 列表)
    │   └── user.ts                     # /api/auth/users/search 复用
    ├── hooks/
    │   ├── useAiStream.ts             # 改自 useAiChat.ts 的 React 版
    │   ├── useYjsRoom.ts               # y-websocket + y-prosemirror 桥
    │   ├── useMarkdownIO.ts            # md ⇄ ProseMirror doc
    │   └── useAuth.ts                  # 复用主站 JWT,sessionStorage.getItem('token')
    ├── store/                          # Zustand 状态
    │   ├── user.ts                    # 当前用户 / token
    │   ├── doc.ts                     # 当前文档 meta + awareness
    │   └── ai.ts                      # AI 配置 + 默认 model
    └── styles/
        └── theme.ts
```

### 3.2 关键功能点(对应现有 AI 调用列表)

| AI 能力 | 来源 | Syllepsis 模块实现要点 |
|---|---|---|
| AI 文档问答 | 现有 `/api/documents/{id}/ai/chat-stream` | Syllepsis `ai-plugin` + 侧栏 `AiChatPanel`,支持多轮 |
| AI 文档生成(流式) | 现有 `generate-stream` | `AiFloatPanel` 选区→生成,流式插入到 ProseMirror 文档 |
| AI 改写 | 现有 `rewrite` | 选区右键 → 改写指令,流式替换选区 |
| AI 翻译 | 现有 `translate` | 选区右键 → 翻译,流式替换 |
| AI 摘要 | 现有 `summarize` | 顶部工具栏 → 摘要,显示在侧栏 |
| AI 表格提取 | 现有 `extract-tables` | 选区 → 表格提取,转换为表格节点 |
| AI 对比 | 现有 `compare` | 跨文档对比侧栏 |
| **AI 协作大纲生成(新增)** | 新增 `POST /api/ai-write/outline` | `/outline` slash 命令触发 |
| **AI 一键扩写(新增)** | 新增 `POST /api/ai-write/expand` | 选区 → AI 扩写 |
| **AI 多模态(图片问答,新增)** | 新增 `POST /api/ai-write/vision` | 上传图片后流式回答 |
| **模板库(新增)** | 新增 `GET /api/ai-write/templates` | 模板 ID + prompt + schema |
| **历史版本/会话(新增)** | 新增 `GET /api/ai-write/history` | 写作会话快照(每 30s 一次) |

### 3.3 后端新增端点

```java
// 全部后端接口,沿用现有 springboot 端口 9004
// 复用 JwtUtil + JwtAuthFilter,不引入新鉴权

POST   /api/ai-write/documents/create           // 创建 AI 写作文档
GET    /api/ai-write/documents/list             // 我的 AI 写作文档列表
GET    /api/ai-write/documents/{id}/content     // 读取 markdown 内容(同主站接口)
POST   /api/ai-write/documents/{id}/save        // 保存 markdown(同主站接口)
GET    /api/ai-write/templates                  // 模板列表
POST   /api/ai-write/templates                  // 创建模板(用户自定义)
DELETE /api/ai-write/templates/{id}             // 删除模板

POST   /api/ai-write/stream/outline             // AI 大纲生成(流式)
POST   /api/ai-write/stream/expand              // AI 扩写(流式)
POST   /api/ai-write/stream/vision              // AI 多模态(流式,multipart)
POST   /api/ai-write/stream/grammar             // AI 语法纠错
POST   /api/ai-write/stream/style               // AI 风格转换

GET    /api/ai-write/history/{docId}            // 会话快照
POST   /api/ai-write/history                    // 写入会话快照
```

所有 `stream/*` 端点**复用现有 SSE 模式**(`SseEmitter` + `AiProxyService.proxy`),数据流经现有 LLM 网关,只新增 application 层路由。

---

## §A v2.2 重塑规划总览 — 功能 + 技术栈(给需求方一眼看清)

> 本节是给需求方看的"一页纸总览";§1–§13 是实施细节。本节**只回答四个问题**:1)做什么(功能);2)用什么做(技术栈);3)稳定吗(运维);4)有什么坑(难点)。

### A.1 一句话定位

**MiaotongDoc AI 文档在线写作模块**:在 `miaotongdoc.com/ai-write/` 路径下,提供"AI 辅助 + 多人协同"的在线写作工具,辅助用户从零起草文档,产出 `.md` 文件,与主站 MarkdownEditor.vue 互通。

### A.2 功能矩阵(24 项能力,分 6 大类)

#### A.2.1 富文本编辑基础(8 项,**纯 Syllepsis 官方能力,0 自研**)

| # | 能力 | Syllepsis 来源 | 说明 |
|---|---|---|---|
| 1 | 标题 H1–H3 + 段落 | `plugin-basic` | 含大纲抓取跳转 |
| 2 | 列表(无序 / 有序 / 任务列表) | `plugin-basic` | 含任务勾选态 |
| 3 | 引用 blockquote | `plugin-basic` | |
| 4 | 表格(可调整列宽) | `plugin-table` | |
| 5 | 基础代码块 | `plugin-code-block` | 单语言、基础高亮 |
| 6 | 链接 / 图片 / 视频插入 | `plugin-basic` | 图片支持拖拽 resize(自研)|
| 7 | 富文本格式(b / i / u / s / color / font-size / sub / sup) | `plugin-basic` | |
| 8 | Undo / Redo | `plugin-basic` | |

#### A.2.2 AI 写作能力(7 项,核心卖点,**全自研 LLM 调用**)

| # | 能力 | 后端端点 | 流式 | 说明 |
|---|---|---|---|---|
| 9 | **AI 大纲生成** | `POST /api/ai-write/stream/outline` | ✅ SSE | 用户给主题 → AI 输出多级大纲 |
| 10 | **AI 扩写**(选区) | `POST /api/ai-write/stream/expand` | ✅ SSE | 选中段落 → AI 扩写 → 流式替换 |
| 11 | **AI 改写**(指令) | `POST /api/ai-write/stream/polish` | ✅ SSE | "改成更专业"/"更口语" 等指令 |
| 12 | **AI 翻译**(多语) | `POST /api/ai-write/stream/translate` | ✅ SSE | 中英日韩等 |
| 13 | **AI 全文纠错** | `POST /api/ai-write/stream/grammar` | ✅ SSE | 把全文给 AI,标黄返回 |
| 14 | **AI 风格转换** | `POST /api/ai-write/stream/style` | ✅ SSE | "鲁迅风"/"小红书风" |
| 15 | **AI 多模态问答** | `POST /api/ai-write/stream/vision` | ✅ SSE(multipart) | 上传图,提问,流式回答 |

#### A.2.3 协作与版本能力(3 项)

| # | 能力 | 协议 / 实现 | 说明 |
|---|---|---|---|
| 16 | **多端实时协同** | `Yjs + y-websocket + y-prosemirror`;docKey 前缀 `aiw-` | 与主站 `md-` 互不影响 |
| 17 | **协作者头像与光标** | `y-prosemirror` awareness + 自研 `CollabAvatars` | |
| 18 | **自动保存 + 历史快照** | `Y.Doc.update` 2s 防抖 + `ai_write_history` 表 | 每 30s / 显式保存时落库 |

#### A.2.4 高价值 UI/UX 能力(3 项,**全自研**)

| # | 能力 | 实现位置 | 说明 |
|---|---|---|---|
| 19 | **Slash 命令面板**(`/outline`) | 自研 `editor/runtime/slash-plugin/` | 仿 Notion,12 项命令 |
| 20 | **@Mention 用户** | 自研 `editor/runtime/mention-plugin/` + 复用主站 `userApi.search` | |
| 21 | **AI 浮窗选区操作** | 自研 `components/AiFloatPanel.tsx` + `editor/runtime/ai-plugin/index.ts` | 选区浮窗,流式回填 |

#### A.2.5 富文本增强(5 项,后期版本)

| # | 能力 | 实现 | 等级 |
|---|---|---|---|
| 22 | 拖拽 resize 图片 | 自研 mousedown listener | P1 |
| 23 | 豆包风格代码块(主题 / 行号 / wrap) | 自研 NodeView | P1 |
| 24 | KaTeX 数学公式 | 自研 inline + block node | P2 |
| 25 | Mermaid 图表 | 自研 code-block 变体 | P2 |
| 26 | 表格列宽调整 + 单元格合并 | 自研 | P2 |

**说明**:20 项 P0/P1 + 4 项 P2 后续版本。

#### A.2.6 模板与导出(2 项)

| # | 能力 | 实现 |
|---|---|---|
| 27 | 模板库(系统 + 用户自建) | `ai_write_template` 表 + Flyway V22 |
| 28 | 导出 markdown / PDF / Word | 复用主站 `/api/documents/{id}/export/*` |

### A.3 技术栈(逐层选型 + 与主栈对打)

#### A.3.1 前端核心栈(React + Vite)

| 层 | 选型 | 版本 | 为什么 | 与主站差异 |
|---|---|---|---|---|
| 视图框架 | **React 18** | ^18.3 | Syllepsis 官方仅支持 React | Vue 3 → React 18 |
| 构建 | **Vite 5** | ^5.4 | 同主站,生态熟 | 同 |
| 语言 | **TypeScript 5** | ^5.4 | Syllepsis 自带完整 `.d.ts` | 同 |
| 路由 | **React Router v6** | ^6.26 | SPA 标准 | Vue Router → RR6 |
| 状态 | **Zustand** | ^4.5 | 轻,本模块仅 3 个 store | Pinia → Zustand |
| UI | **Ant Design 5** | ^5.20 | Syllepsis 文档官方示例 | Element Plus → Antd |
| 样式 | CSS Modules + 全局 CSS 变量 | — | 避免 Tailwind preflight 干扰 Syllepsis | scoped + Sass → CM |
| HTTP | 自封装 fetch | 原生 | 与主站 `request.ts` 一致 | axios → fetch |
| XSS 防护 | DOMPurify | ^3.1 | 与主站统一 | 同 |
| 工具 | dayjs | ^1.11 | 同主站 | 同 |

#### A.3.2 富文本栈(Syllepsis 全套)

| 包 | 来源 | 自研 / 复用 |
|---|---|---|
| `@syllepsis/access-react` 适配器 | Syllepsis 官方 | 复用 |
| `@syllepsis/editor` Controller | Syllepsis 官方 | 复用 |
| `@syllepsis/adapter` ProseMirror 适配 | Syllepsis 官方 | 复用 |
| `@syllepsis/plugin-basic` 基础 | Syllepsis 官方 | 复用 |
| `@syllepsis/plugin-code-block` 代码块 | Syllepsis 官方 | 复用 |
| `@syllepsis/plugin-table` 表格 | Syllepsis 官方 | 复用 |
| `@syllepsis/plugin-placeholder` placeholder | Syllepsis 官方 | 复用 |
| `prosemirror-markdown` Markdown IO | ProseMirror 官方 | 复用 |
| `yjs` `y-websocket` `y-prosemirror` 协同 | 同主站版本 | 复用 |
| Mention / Slash / AI plugin / Doubao 代码块 NodeView / 图片 resize / KaTeX / Mermaid | **本模块自研** | 自研 |
| **Syllepsis Fork 自维护** | 内部 `miaotong/ai-write-syllepsis-fork` | 自维护 |

#### A.3.3 后端栈(Spring 共享 + 增量新增)

| 层 | 选型 | 动作 |
|---|---|---|
| 框架 | **Spring Boot 3.2.5** | 复用 |
| 鉴权 | **Spring Security + JwtUtil + JwtAuthFilter** | 复用(JWT 与主站统一)|
| AI 网关 | **AiProxyService + `/api/ai/proxy`** | **复用,不重写** |
| LLM 客户端 | **AiService**(OpenAI 兼容协议 + HttpURLConnection) | 复用 |
| 数据访问 | **Spring Data JPA + PostgreSQL 12** | 复用,新增 2 张表 |
| DB 迁移 | **Flyway V22 / V23** | 新增 |
| 存储 | **StorageService**(minio / 本地磁盘) | 复用 |
| 流式响应 | **SseEmitter + Jackson** | 复用 |

#### A.3.4 基础设施栈

| 组件 | 选型 |
|---|---|
| 新增静态目录 | `MiaotongDoc-Docker/app/ai-write/dist/` |
| nginx 路由 | `location /ai-write/ { root /usr/share/nginx/ai-write; try_files $uri /index.html; }` |
| Docker volume | 给 nginx 容器挂新目录 |
| yjs-server | docKey 正则扩 `aiw` 前缀(+1 字符);可选加 y-leveldb 持久化 |
| 部署 | **完全独立 SPA,与 Vue 主站 0 共享运行时** |

#### A.3.5 安全栈(与主站一致)

| 项 | 实现 |
|---|---|
| 鉴权 | 复用主站 JWT(token 从 sessionStorage 读取) |
| XSS | DOMPurify 白名单 |
| SSRF | 复用车站外网禁止 |
| CSRF | SameSite Cookie + 自定义 Header |
| LLM 限流 | 复用 `AiProxyService` 现有 429 处理 |

### A.4 与现有主站栈对打(决策用)

| 项 | 现有主站栈 | 本模块栈 | 决策 |
|---|---|---|---|
| 富文本核心 | Tiptap 3.26 | Syllepsis 1.x | Syllepsis ProseMirror 直系, mention/slash 自研 |
| 视图层 | Vue 3 | React 18 | Syllepsis 适配器仅 React |
| UI 库 | Element Plus 2.5 | Ant Design 5 | 企业级,与 Syllepsis 文档一致 |
| 状态管理 | Pinia | Zustand | 单 store 即可,无 boilerplate |
| 路由 | Vue Router 4 | React Router 6 | 标准 SPA |
| 协同 | Tiptap ext-collab | 自写 Syllepsis 注入 | 协议同 yjs,docKey 前缀区分 |
| Markdown IO | marked + turndown | prosemirror-markdown | 同 ProseMirror 体系 |
| AI 流式 | Vue composable | React hook | 算法等价,移植 |
| 部署 | miaotongdoc-web/ 单 SPA | miaotongdoc-ai-write/ 独立 SPA | 完全隔离 |
| token 源 | 主站 sessionStorage | 同主站 | 复用 |

### A.5 运维与稳定性

| 维度 | 实现 |
|---|---|
| 部署形态 | docker compose 新增 nginx volume,**新模块独立升级不影响主站** |
| 灰度能力 | 无需,因模块独立 URL,上线即全部生效 |
| 回滚 | 删 volume / 删 nginx location,30 秒内回滚到"上线前" |
| 监控指标 | API 端点 latency、AI 流式 TTFT、协同 sync 时间、SSE 连接数 |
| 数据备份 | `.md` 文件随主站一并备份;模板 / 历史存 PostgreSQL 跟随主站备份 |
| 高可用 | yjs-server 单点,主站相同模式不需额外集群 |

### A.6 关键技术难点(5 项核心,11 项次要)

#### A.6.1 5 项核心难点(必须解决)

| # | 难点 | 难点原因 | 解决策略 |
|---|---|---|---|
| ① | **Syllepsis 16 个月无 commit** | 上游维护停滞,CVE / bugfix 无人接 | Fork 自维护 + 内部 npm 别名 + 每月 rebase |
| ② | **Mention 必须自研** | Syllepsis 无 mention plugin | 监 transaction + popup + nodeView |
| ③ | **Slash 必须自研** | Syllepsis 无 slash 内置 | 监听 `/` + 居中 popup + ProseMirror command |
| ④ | **AI 流式 transaction 抖动** | 高频 dispatch 卡编辑器 | rAF 节流 + `setMeta('addToHistory', false)` |
| ⑤ | **y-prosemirror 注入 Syllepsis plugin 链** | Syllepsis 已装 plugin 必须保留 | `view.state.reconfigure({plugins: [...current, ySync, yCursor]})` |

#### A.6.2 6 项次要难点(可排队解决)

| # | 难点 | 解决策略 |
|---|---|---|
| ⑥ | MarkdownIO 与 Syllepsis schema 对齐 | 自定义 node 实现 toMarkdown/parseMarkdown |
| ⑦ | Antd 样式与 Syllepsis 冲突 | 局部禁用 preflight 或 CSS Modules |
| ⑧ | 协同冲突时 AI 插入位置 | Yjs CRDT 自动,UI placeholder |
| ⑨ | React 18 strict mode 双 mount | Syllepsis mount idempotent 测试 |
| ⑩ | AI 流式中断与重试 | AbortController + 5s 重试 + 限流识别 |
| ⑪ | Syllepsis 与中文输入法 IME | ProseMirror 默认行为已支持,需烟测 |

### A.7 工作量与里程碑(75 人日 / 13 周)

| 阶段 | 内容 | 工作量 |
|---|---|---|
| 0 | Syllepsis Fork + React 项目骨架 + Router + 4 空页 | 5 d |
| 1 | Syllepsis 基础富文本 + MarkdownIO + 后端 CRUD | 8 d |
| 2 | Mention + Slash 自研 plugin | 10 d |
| 3 | AI 流式 + Syllepsis transaction + 后端 7 个 stream 端点 | 10 d |
| 4 | y-prosemirror 协同 + y-leveldb 持久化 | 8 d |
| 5 | 后端模板 / 历史 Controller + Flyway V22/V23 | 8 d |
| 6 | 自研 plugin(图片 resize / Doubao 代码块 / KaTeX / Mermaid)| 16 d |
| 7 | Outline / Theme / QA / 文档 / Docker 脚本 | 10 d |
| **合计** | | **75 d 现实(乐观 55 / 悲观 110)** |

**里程碑交付**:
- **W3 末**:基础编辑器 + markdown save/load,**MVP 可演示**
- **W5 末**:Mention + Slash + 简单 AI 浮窗
- **W8 末**:完整 AI 7 能力 + 协同,**可推广**
- **W13 末**:全功能,**生产可用**

### A.8 不做什么(明确边界)

- ❌ 不做合同审批 / 签署(主站签署模块)
- ❌ 不做 PDF 编辑 / OCR(主站 PDF 模块)
- ❌ 不做文档归档 / 回收站(主站已有)
- ❌ 不做 OnlyOffice 集成(本模块只产出 markdown)
- ❌ 不做移动端深度适配(本期响应式,v3 再深度适配)
- ❌ 不接管主站 MarkdownEditor 任何代码

### A.9 立即可执行(本周)

1. **Fork Syllepsis**:`git clone https://github.com/bytedance/syllepsis.git miaotong-internal/syllepsis-fork`,锁版本发布 internal npm
2. **建 React 项目骨架**:`mkdir miaotongdoc-ai-write && cd miaotongdoc-ai-write && npm init -y`
3. **装依赖**:React 18 + Syllepsis 全套 + Vite + Antd + Zustand + RR6 + prosemirror-markdown + yjs 系列
4. **建空页**:Home / Editor / Templates / Settings 四个 router
5. **跑通 SylEditor hello world**

**5 天验收**:Syllepsis 在 React 中渲染 + `/ai-write/editor/test` 可访问 + `vite build` < 1MB。

### A.10 与已有 Vue 主站的协作机制(关键设计)

> 很多人以为"两套前端怎么接"会有胶水成本,实际上答案是:**不需要任何胶水**,因为它们是同一域名的两个独立 SPA。下面把协作机制讲透。

#### A.10.1 协作的 5 个层面(实际只有"路由" + "数据"两层需要考虑)

| 层面 | 怎么配合 | 成本 |
|---|---|---|
| **1. 部署域名** | 两个 SPA 都由 `miaotongdoc-nginx` 单容器托管,各自路径独立,不需要 CORS | 0 |
| **2. 鉴权 token** | 新模块从 `sessionStorage.getItem('token')` 直接读主站 token,后端共用 `JwtUtil` | 0 |
| **3. 用户身份** | JWT payload 里有 `userId` / `username`,无需额外接口 | 0 |
| **4. 数据互通** | 文档 / 模板 / 历史通过现有 `/api/...` 接口,两栈都调 | 0 |
| **5. 导航跳转** | 主站 Home.vue 加"AI 写作"按钮,`<a href="/ai-write/">` 整页跳转(SPA→SPA)| 0 |

#### A.10.2 nginx 单容器同时托管两个 SPA 的具体配置

**目录结构**(部署后):
```
MiaotongDoc-Docker/app/
├── web/dist/                  ← 现有 Vue 主站产物(已存在)
│   ├── index.html
│   └── assets/
└── ai-write/dist/             ← 新模块 React 产物(本次新增)
    ├── index.html
    └── assets/
```

**nginx.conf 改动**(只加 1 段,放在原 `location / { ... }` 之前):
```nginx
# 主站 location(原有)
location / {
  root /usr/share/nginx/html;
  try_files $uri $uri/ /index.html;
}

# AI 写作模块(新增)
location /ai-write/ {
  alias /usr/share/nginx/ai-write/;
  try_files $uri $uri/ /ai-write/index.html;
}
```

**docker-compose.yml 改动**(只加 1 行):
```yaml
nginx:
  volumes:
    - ./app/web/dist:/usr/share/nginx/html         # 已有
    - ./app/ai-write/dist:/usr/share/nginx/ai-write:ro  # 新增
```

#### A.10.3 路径分发流程(用户视角)

```
用户从主站 Home.vue 点击"AI 写作空间"按钮
  └─ 浏览器地址栏跳转到 https://miaotongdoc.com/ai-write/
     └─ nginx 看到 /ai-write/ 前缀
        └─ try_files 命中 /ai-write/index.html
           └─ 浏览器加载 React 产物
              └─ React Router 在 /ai-write/ 路径下管理路由
                 - /ai-write/         → 模板库首页
                 - /ai-write/editor/X → 编辑器
                 - /ai-write/templates → 模板管理
                 - /ai-write/settings  → 个人设置
```

**关键**:**两个 SPA 路径无重叠**(`/` 主站、`/ai-write/` 新模块),浏览器地址栏自然切换,Vue 与 React **互不知道对方存在**。

#### A.10.4 主站"如何引导用户去 AI 写作"(从 Vue 侧改 2 处)

**改 1:Home.vue 加导航按钮**(用户最显眼的入口)
```vue
<!-- src/views/Home.vue 顶部工具栏 -->
<el-button type="primary" icon="MagicStick" @click="$router.push('/ai-write/')">
  AI 写作空间
</el-button>
```

**改 2:router/index.ts 不需要改**(因为主站 Vue Router 不管理 `/ai-write/*`,那些路径全部交给 nginx → React SPA 处理)

如果担心用户在 Vue 主站 SPA 内"内部跳转"触发 Vue Router 拦截,可改用 `window.location.href` 整页跳转:
```js
window.location.href = '/ai-write/'   // 整页跳到 React SPA
```

#### A.10.5 新模块"如何回到主站"(从 React 侧改 1 处)

新模块任何要回到主站的链接都用 `<a href="/home">` 整页跳转,不要用 React Router(避免 SPA 内失败):
```tsx
<a href="/home">← 返回主站</a>
<a href={`/editor/${docKey}`}>用主站编辑器打开</a>
```

#### A.10.6 鉴权配合(关键的 token 复用)

```
用户登录主站(走主站 Vue Login.vue):
  POST /api/auth/login
    └─ 后端 JwtUtil 生成 JWT
       └─ 前端 sessionStorage.setItem('token', jwt)
          └─ sessionStorage.setItem('userId', 'xxx')
             └─ sessionStorage.setItem('role', 'admin|user')

用户点 "AI 写作空间" 跳到 /ai-write/:
  └─ React SPA 加载
     └─ useAuth() hook:sessionStorage.getItem('token')
        └─ 直接使用同一 token,带在 fetch 的 Authorization header
           └─ 后端 JwtAuthFilter 校验通过,无需新登录
```

**结论**:**用户从主站登录一次,在新模块内完全不用登录,token 复用 sessionStorage**。

#### A.10.7 数据互通的具体场景

| 场景 | 调用 | 复用情况 |
|---|---|---|
| 主站"创建文档"按钮 | `POST /api/documents` | 主站已有接口,新模块**不调** |
| 主站"打开此 markdown" | `GET /api/markdown/{id}/content` | 主站已有 |
| 新模块"创建 AI 写作文档" | `POST /api/ai-write/documents/create` | **新增**,在 `mt_document` 表写 `docType='markdown'` |
| 新模块"保存内容" | `POST /api/ai-write/documents/{key}/save` | **新增**,复用 `StorageService` 写磁盘 |
| 新模块"@同事" | `GET /api/auth/users/search?q=X` | 主站已有,新模块**直接调** |
| 新模块"AI 调用" | `POST /api/ai/proxy` | 主站已有,新模块**直接调** |
| 新模块"打开主站已有 markdown" | 跳 `https://miaotongdoc.com/editor/{docId}` | 主站 `DocEditor.vue` 加载该 markdown |
| 主站"AI 辅助编辑" 跳 AI 写作空间 | 主站"AI 写作"按钮 → `/ai-write/?sourceDoc={docId}` | 新模块读 URL query,初始化时预填内容 |

**数据互通零成本**:两栈调的是同一组后端接口,只是路径前缀不同(`/api/documents` 主站、`/api/ai-write/*` 新模块)。

#### A.10.8 协同跨栈的边界

| 协同场景 | 是否支持 |
|---|---|
| 主站 MarkdownEditor.vue 编辑 + 新模块 Syllepsis 同时编辑同一文档 | **不支持**(同 docKey 不同栈 schema 不同,CRDT 必冲突)|
| 新模块内多人同时编辑同一 AI 写作文档 | **支持**(同栈,`yjs-server aiw-{key}` 房间)|
| 主站内多人同时编辑同一 markdown 文档 | **支持**(同栈,`yjs-server md-{key}` 房间,沿用现有)|
| 新模块"导出" → 主站打开 | **支持**:`window.location.href = '/editor/' + docKey`,主站能加载 |

#### A.10.9 总结:协作机制的核心就 3 件事

1. **nginx 加 1 段 location 路由**(零代码侵入)
2. **sessionStorage 复用 token**(零代码侵入)
3. **Home.vue 加 1 个跳转按钮**(5 行 Vue 代码,主站侧改 1 个文件)

其他所有"跨栈"的事都是 HTTP / WebSocket 协议层的对接,**不涉及 JS 代码层面的胶水**。这就是"完全独立部署、独立技术栈"换来的清晰边界。

### A.11 模块命名(`ai-write` 是否合适 + 备选方案)

> 命名看似小事,但**影响 URL、菜单、图标、API 路径前缀、数据库表前缀、协同 docKey 前缀、用户心智**。选错代价很大。本节给硬数据。

#### A.11.1 行业主流命名样本(实测)

| 产品 | AI 写作模块命名 | URL 形式 | 备注 |
|---|---|---|---|
| **Notion** | **Notion AI** | 嵌入主编辑器,无独立 URL | 功能名(命令名)叫 `Ask AI` / `AI Write` / `Improve writing` / `Continue writing` |
| **飞书** | **智能伙伴 / AI 写作助手** | `/ai/template/xxx` | 模板中心是 `/template/ai-*` |
| **腾讯文档** | **AI 助手 / 人机双写** | 嵌入主编辑器 | "人机双写"是 feature 名 |
| **Grammarly** | **GrammGO / Compose** | 无独立 URL | `Compose` 是核心动作 |
| **ClickUp** | **ClickUp AI / AI Writer** | 嵌入任务编辑器 | 命名走 `AI Writer` |
| **Writer.com** | **Writer** | `/app/...` | 直接用产品名作 AI 模块名 |
| **GitHub Copilot** | **Copilot** | 嵌入 IDE | 用航航空概念隐喻 AI 助手 |
| **阿里 QoderWork** | **QoderWork / AI Native 设计工作台** | `qoder.work` | "Work" + AI 概念 |
| **科大讯飞** | **讯飞文书** | `/doc/...` | 直接是产品名 |

#### A.11.2 命名的三大流派

| 流派 | 命名特征 | 代表 | 适合场景 |
|---|---|---|---|
| **A. 功能直白派** | `AI Write` / `AI 写作` / `AI Writer` / `AI Compose` | Notion, ClickUp, Grammarly Compose | 用户一看就懂"是干啥的" |
| **B. 隐喻 / 品牌派** | `Copilot` / `Studio` / `Magic` / `Effidit` | GitHub Copilot, Notion AI | 强调 AI 助手属性,不直白 |
| **C. 场景派** | `Draft` / `Compose` / `Assistant` / `人机双写` | Grammarly, 腾讯 | 用动作或场景命名,不是工具名 |

#### A.11.3 备选命名评分表

| 候选名 | 含义 | 直白 | 中文化 | URL 友好 | 易于记忆 | 与主站风格一致 | 综合 |
|---|---|---|---|---|---|---|---|
| **`ai-write`**(当前) | AI 写作 | ⭐⭐⭐⭐ | ⭐⭐(英文) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | 17 / 24 |
| **`ai-write` + 中文站 `写作空间`** | 双语 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 21 / 24 |
| **`compose`** | 写作动词,Notion / Gmail 风 | ⭐⭐⭐ | ⭐⭐(英文) | ⭐⭐⭐⭐⭐(短)| ⭐⭐⭐ | ⭐⭐ | 15 / 24 |
| **`studio`** | AI 工作室 | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐(短)| ⭐⭐⭐ | ⭐⭐ | 13 / 24 |
| **`draft`** | 草稿 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | 18 / 24 |
| **`copilot`** | 副驾驶(隐喻) | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐(品牌) | ⭐⭐ | 15 / 24 |
| **`magic-write`** | 神奇写作 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | 14 / 24 |
| **`aide`** | AI 文档引擎 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | 15 / 24 |
| **`writer`** | 直接产品名 | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | 16 / 24 |
| **`author`** | 作者 | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | 16 / 24 |

#### A.11.4 推荐方案:**保留 `ai-write`,但配套做三件事**

经过评估,**`ai-write` 作为 URL 路径前缀和项目目录名是合适的**(直白、URL 短、易记忆、与项目名 `miaotongdoc-ai-write` 一致),但**用户面向命名建议如下**:

```
URL 路径前缀   /ai-write/              ← 不变(技术前缀)
项目目录名    miaotongdoc-ai-write/   ← 不变(技术目录)
路由名        AiWriteHome/AiWriteEditor/...
数据库表前缀  ai_write_*              ← 不变
协同 docKey 前缀 aiw-                  ← 不变
API 路径前缀   /api/ai-write/         ← 不变

面向用户的产品名:
  内部代号  → "AI 写作空间"           ← 中文用户用
  海外版本  → "MiaotongDoc AI Write"  ← 如有海外计划
  菜单按钮文字 → "AI 写作空间"(主) / "AI 写作"(折叠)
  图标       → MagicStick / Sparkles / Quill(羽毛笔)
```

**理由**:
1. **路径名稳**:URL 一旦上线不轻易改,`/ai-write/` 业内最常见,适合
2. **目录名跟随路径**:`miaotongdoc-ai-write/` 简单直接
3. **用户界面用中文**:"AI 写作空间"5 个字,直观,符合飞书 / 腾讯命名习惯
4. **数据前缀**:`aiw-` / `ai_write_*` 都与命名一致,搜索 grep 方便

#### A.11.5 行业里"AI Write" 的具体 URL 案例

- `notion.so/ai-write` 概念上嵌入主编辑器
- 飞书智能写作助手:`/ai/...` 路径(用 `ai` 不用 `ai-write`)
- ClickUp AI Writer:嵌入主任务编辑器
- 国内一些产品:`/write/` `/compose/` `/draft/`

**结论**:`/ai-write/` 在国内产品中**不算奇怪但也不算最常见**;**飞书的 `/ai/` 更简短**(可惜被 Notion AI 占用)。最终推荐保留 `ai-write`,因为:
- 含义清晰,与项目目录名一致
- 与主站 `MiaotongDoc` 品牌前缀不冲突
- 后续如果想升级品牌,只需改 UI 文案,不改 URL

#### A.11.6 备选:统一品牌命名(更彻底)

如果想给 MiaotongDoc 一个更强的 AI 品牌感,可选一个统一品牌词:

| 品牌候选 | 含义 | 适用 |
|---|---|---|
| **Miaotong AI** | 主品牌 + AI 后缀 | 全产品 AI 能力的总称 |
| **Miaotong Copilot** | "副驾驶" | 类似 GitHub Copilot |
| **Miaotong Writer** | AI 写作子产品名 | 单独一个 AI 写作产品 |
| **妙笔**(中文品牌) | 写作意象 | 中文用户友好 |

#### A.11.7 最终建议

**短期(本次发布)**:**保留 `ai-write`**,内部代号"**AI 写作空间**",中文界面显示"**AI 写作空间**"。

**中长期(品牌升级)**:产品成熟后可考虑统一命名为 "**妙笔**" 或 "**Miaotong Writer**",给整个 AI 写作能力一个品牌锚点。

#### A.11.8 命名相关的副作用清单(改了命名都要跟着改)

| 改的层级 | 涉及文件 | 数量 |
|---|---|---|
| URL 路径 | `nginx.conf` 1 段;React 路由配置;主站跳转按钮 | 3 处 |
| 项目目录 | `miaotongdoc-ai-write/` | 1 处 |
| 包名 | `package.json` 中 `name`;`vite.config.ts` 的 `base` | 2 处 |
| 数据库表 | 2 张表 `ai_write_template`、`ai_write_history` + Flyway V22/V23 | 4 处 |
| 后端 API | `/api/ai-write/*` 全部 controller 路径 | 4 处 controller |
| 协同前缀 | `aiw-{key}` 正则 1 字符;前端 awareness key | 2 处 |
| 用户界面 | Home.vue / DocEditor.vue / AiWriteHome 标题 | 3 处 |
| 日志 / 监控标签 | audit log、APM 标签 | 多处 |
| **总计** | | **~20 处** |

> **如果想改命名,集中在这个 PR 做;一旦上线,改动成本高**。

---

---

## 4. 数据流与存储

### 4.1 数据流图

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 浏览器(独立部署的 React SPA: miaotong.com/ai-write)                       │
│                                                                           │
│  1. 用户输入                                                              │
│     └─ Syllepsis Doc -> ySyncPlugin -> Y.Doc (Y.XmlFragment 'content')   │
│                                                                           │
│  2. 协同同步                                                              │
│     └─ Y.Doc -> y-websocket -> ws://miaotong.com/ws/yjs/aiw-{docKey}     │
│                                                                           │
│  3. AI 流式                                                              │
│     └─ fetch POST /api/ai-write/stream/expand?docId=X                    │
│        Server-Sent Events -> useAiStream hook                            │
│        -> 流式插入 Syllepsis Doc(实时渲染)                                │
│                                                                           │
│  4. 自动保存                                                              │
│     └─ Y.Doc.on('update') 2s 防抖 -> MarkdownIO.serialize -> markdown    │
│        -> POST /api/ai-write/documents/{id}/save                          │
└───────────────────────────────────────────────────────────────---──────────┘
                                  │            │              │
                                  │            │              │
                                  ▼            ▼              ▼
                       ┌─────────────┐  ┌────────────────────┐
                       │ yjs-server  │  │ miaotongdoc-server │  LLM 网关
                       │ aiw-{key}   │  │ /api/ai-write/*    │  (现有)
                       │ 持久化可选  │  │ Spring Boot 9004   │
                       └─────────────┘  └─────────┬──────────┘
                                                    │
                                                    ▼
                                          StorageService(MinIO/磁盘)
                                          mt_document 表(DocType='markdown')
```

### 4.2 数据存储

- **主存储**:`mt_document` 表(沿用)`docType='markdown'`,`filePath` 指向 `.md` 文件
- **模板存储**:新增 `ai_write_template` 表(id, name, prompt, schema_json, owner_id)
- **会话快照存储**:新增 `ai_write_history` 表(id, doc_id, content_md, snapshot_at)

### 4.3 协同 docName 协议

- 沿用 `md-{docKey}` 协议上增加新前缀,**避免与主站 markdown 协同冲突**:
  - 主站现有:`md-{docKey}`,存根于 `mt_document.file_path`
  - AI 写作新栈:`aiw-{docKey}`,同样指向同一份 `mt_document.file_path`(共享磁盘)
- yjs-server 需要更新正则:`^(md|aiw)-[a-f0-9-]+$`

---

## 5. 技术难点 / 壁垒清单(必看)

### 5.1 难点分级

| 等级 | 难点 | 关键影响 |
|---|---|---|
| **P0-阻塞** | Syllepsis Vue 不支持(本模块用 React 完美避过) | — |
| **P0-阻塞** | Syllepsis 16 个月无 commit,Fork 自维护成本 | §5.3 |
| **P1-硬骨头** | Syllepsis 无 mention plugin,Syllepsis 无 slash 内置 | §5.4 |
| **P1-硬骨头** | 流式 AI 文本如何映射 ProseMirror transactions | §5.5 |
| **P1-硬骨头** | y-prosemirror 如何注入 Syllepsis 自带 plugin 链 | §5.6 |
| **P2-次要** | MarkdownIO 与 Syllepsis schema 不完全对齐 | §5.7 |
| **P2-次要** | Tailwind 与 Syllepsis 自带样式冲突 | §5.8 |
| **P2-次要** | 协作冲突时 AI 文本插入位置 | §5.9 |
| **P3-杂项** | AI 流式中断与重试 | §5.10 |

### 5.2 Syllepsis 原生无 Vue,本模块 React 完全避过

✅ **本模块已规避 Syllepsis 的最大问题** —— 既然是新增独立模块,直接用 React 跑 Syllepsis,完全没有 Vue ↔ React 桥的代价。这是 v1.4 iframe 方案升级后最大的优势:**越独立越便宜**。

### 5.3 Syllepsis 16 个月无 commit,Fork 自维护路径

| 时点 | 动作 |
|---|---|
| 第 1 天 | fork `bytedance/syllepsis` → `miaotong/ai-write-syllepsis-fork`,仅作内部 npm 包 |
| 第 1 周 | fork 仓修复若干 patch:ProseMirror 版本锁、TypeScript 类型修复、CVE |
| 第 2 周 | 提交下游补丁:`@miaotong/syllepsis-editor`,发到内部 npm registry |
| 每月 | pull 上游比对,手动 rebase |
| 每季度 | 6 个月无上游新 commit,把 fork 转正 |

### 5.4 mention 与 slash 必须自写

Syllepsis 没有 mention / slash 的内置 plugin。原因可能是 Syllepsis 假设业务方自己写(因为这俩都是 UX 强相关的)。

**mention plugin(6 d)**:
- 监听 `transaction.docChanged`,识别 `@` 字符插入事件
- 在 caret 位置显示浮层(popup),定位用 `editor.view.posAtCoords`
- 用户选择用户后,通过 ProseMirror command 把 mention node 插入到 caret 位置
- mention node 在 MarkdownIO 中实现 `serialize` / `parseMarkdown`

**slash command(4 d)**:
- 监听 `/` keydown 事件
- 显示命令面板(viewport 居中浮层);选择命令后用 ProseMirror command 插入对应节点
- 命令:标题级别 / 引用 / 代码块 / 列表 / 表格 / 分隔线 / 模板插入 / AI 大纲

### 5.5 流式 AI 文本如何映射 ProseMirror transactions

**问题**: Syllepsis 是 `EditorView` based,AI 给的字符串流(逐字)不能直接走 `view.dispatch` —— 因为频繁 dispatch 大量 transaction 会卡。

**解法**:
1. **节流**:用 `requestAnimationFrame` 把流式 chunk 累积,每 16ms 一次性 dispatch
2. **范围定位**:AI 文本插入需要找到目标位置(选区 / 当前 cursor / 末尾),通过 `view.state.selection`
3. **替换 vs 插入**:
   - 选区 AI 改写:`tr.replaceWith(start, end, fragment)`
   - 末尾插入续写:`tr.insert(end, fragment)`
4. **可撤销**:`tr.setMeta('addToHistory', false)` 避免 AI 插入污染 undo stack(用户撤销时一次回到 AI 修改前)

### 5.6 y-prosemirror 注入 Syllepsis 自带 plugin 链

**问题**: Syllepsis 内部已经装了一堆 plugin(table/keymap/history/...),直接重置 plugins 会丢 toolbar 行为。

**解法**(基于 `api.view` 公开 `EditorView`):
```ts
// editor/collab/yjs-bridge.ts
export function mountYjs(api: SylApi, ydoc: Y.Doc, awareness: Awareness) {
  const view = api.view
  const yfragment = ydoc.getXmlFragment('content')
  const ySyncPlugin = ySyncPlugin(yfragment)
  const yCursorPlugin = yCursorPlugin(awareness)

  const newState = view.state.reconfigure({
    plugins: [
      ...view.state.plugins,   // 保留 Syllepsis 自带
      ySyncPlugin,
      yCursorPlugin,
    ],
  })
  view.updateState(newState)

  awareness.setLocalStateField('user', {
    name, color, userId
  })
}
```
**回滚**:卸载时 `view.updateState(originalState)`,恢复 Syllepsis 默认 plugins。

### 5.7 MarkdownIO 与 Syllepsis schema 不完全对齐

| 不对齐点 | 解决方案 |
|---|---|
| Syllepsis 自定义 node(mention/codeblock-doubao/mermaid)无法被默认 MarkdownSerializer 识别 | 这些 node 实现 `toMarkdown()` 方法,被 `MarkdownSerializer` 自动调用 |
| Markdown 表格语法对不齐 Syllepsis 表格 node | `MarkdownSerializer.fromSchema` 时把 `plugin-table` node 注册 |
| Markdown 代码块 fence(```lang)对应 Syllepsis code-block node | 默认 fence parser 已支持,只需确认 `lang` attr 映射正确 |

### 5.8 Tailwind 与 Syllepsis 自带样式冲突

Syllepsis 内置样式可能与 Tailwind reset / preflight 冲突(因为 Syllepsis 默认 `pre { ... }` 会被 Tailwind preflight `*{...}` 干掉)。

**解决方案**:
- Syllepsis 容器外层包一层 `.syllepsis-scope` class,在该 class 下禁用 Tailwind preflight
- 或者:**改用 CSS Modules + 全局 Syllepsis 样式**(更简单)

### 5.9 协作冲突时 AI 文本插入位置

**场景**:用户 A 触发 AI 续写,在第 5 段;A 的 AI 文本还在流;同时 B 在第 3 段插入新段落 → 第 5 段位置变化。

**Syllepsis + Yjs 自带方案**:
- Yjs 是 CRDT,文本位置是 logical index,不会被"行号变化"影响
- AI 流式插入的 transaction 走 ySyncPlugin 自动 merge,无冲突
- 但 AI 文本 mark(如"由 AI 生成"的标记)需要单独设计 schema

**风险**:AI 续写与用户输入同时发生,yjs merge 后用户可能觉得"AI 写在我的输入后面"是合理的。要在 UI 上用 placeholder 提示"AI 正在此区域生成"。

### 5.10 AI 流式中断与重试

| 中断场景 | 处理 |
|---|---|
| 用户手动 stop(AbortController) | 保留已有文本,标记 `state: 'aborted'` |
| 网络断开 | 5 秒后重试;重试失败 → 标记 `state: 'error'`,保留已生成内容 |
| LLM 429 限流 | ElMessage 提示,等 30s 重试 |
| LLM 超时(60s 无 token) | 关流,提示用户"AI 服务超时,请稍后重试" |
| Syllepsis 卸载 | 立刻 AbortController,清空所有 state |

### 5.11 其他隐性壁垒(必查)

| 壁垒 | 影响 |
|---|---|
| Syllepsis 自带 toolbar 不能完全自定义 | Syllepsis 是 Controller 模式,toolbar 配置写在 `Controller.toolbar` 字段;不在的位置需要自实现原生 toolbar |
| Syllepsis 移动端体验 | Syllepsis 0.5.x 引入 mobile keyboard 适配,但 Syllepsis 在 1.x 主要面向桌面端,移动端需自适配 |
| Syllepsis React 18 strict mode 兼容性 | Syllepsis `componentDidMount` 中 `new SylEditor()`,React 18 strict mode 下 mount/unmount 触发两次需保证 idempotent |
| Syllepsis 中文输入 IME 兼容 | 中文输入法 composition 事件,Syllepsis 走 ProseMirror 默认行为,拼音过程不触发 transaction(已验证) |
| Syllepsis 浏览器兼容 | Syllepsis 0.7.x 起要求 Chrome 80+/Safari 13+;老 Edge / IE11 不支持,与主站策略一致 |

---

## 6. 工程文件与工作量

### 6.1 新增独立 React 项目

```
miaotongdoc-ai-write/                        # 新增,独立 npm 项目
├── package.json                              # react 18 + react-dom 18 + Syllepsis 全套 + yjs
├── tsconfig.json
├── vite.config.ts                            # base: '/ai-write/'
├── Dockerfile                                # 多阶段 build -> nginx
├── index.html
├── public/
└── src/                                      # 见 §3.1
```

### 6.2 后端新增 Java 文件

```
miaotongdoc-server/src/main/java/com/miaotong/doc/
├── controller/
│   ├── AiWriteDocumentController.java        # CRUD + 保存 / 加载 markdown
│   ├── AiWriteStreamController.java          # 流式端点(outline/expand/vision/...)
│   ├── AiWriteTemplateController.java        # 模板
│   └── AiWriteHistoryController.java         # 历史快照
├── service/
│   ├── AiWriteDocumentService.java
│   ├── AiWriteTemplateService.java
│   ├── AiWriteHistoryService.java
│   └── AiWritePromptBuilder.java             # system prompt 构造(类似 DocumentAiController 现有)
├── entity/                                   # Flyway 迁移见 §6.3
│   ├── AiWriteTemplate.java
│   └── AiWriteHistory.java
└── repository/
    ├── AiWriteTemplateRepository.java
    └── AiWriteHistoryRepository.java
```

### 6.3 Flyway 迁移

```
miaotongdoc-server/src/main/resources/db/migration/
├── V22__add_ai_write_template.sql
└── V23__add_ai_write_history.sql
```

### 6.4 Docker / Nginx

```
MiaotongDoc-Docker/
├── app/
│   └── ai-write/                            # 新增,React 产物
│       └── dist/                            # vite build 产物
├── config/nginx/nginx.conf                  # 加 location /ai-write/
└── docker-compose.yml                       # nginx 容器加 volume
```

### 6.5 yjs-server 改动

```
MiaotongDoc-Docker/app/yjs/
├── server.js                                # 正则 ^aiw|md|pdf 接受新前缀
└── Dockerfile                               # 不动
```

### 6.6 关键文件清单(汇总)

#### 新增
- `miaotongdoc-ai-write/` 整个独立 React SPA
- `internal-frontend/syllepsis-fork/` Syllepsis Fork 自维护仓
- 后端 `controller/AiWrite*.java` × 4
- 后端 `service/AiWrite*.java` × 4
- 后端 `entity/AiWrite*.java` × 2
- Flyway 迁移 V22、V23
- `app/ai-write/dist/` React 产物

#### 修改(零侵入 Vue 主站)
- `MiaotongDoc-Docker/config/nginx/nginx.conf` 加 1 段 location
- `MiaotongDoc-Docker/docker-compose.yml` 加 1 行 volume
- `MiaotongDoc-Docker/app/yjs/server.js` 改 1 行正则

#### 完全不动
- Vue 主站 `miaotongdoc-web/` 整个仓库
- 现有 `MarkdownEditor.vue` / `MarkdownController` / `DocumentAiController`
- 数据库 `mt_document` / `mt_*` 表
- 现有 AI 能力端点(`/api/ai/proxy`、`/api/ai/chat/stream` 等)

### 6.7 工作量估算

| 阶段 | 工作量(人日) | 内容 |
|---|---|---|
| **阶段 0** Syllepsis Fork + React 项目骨架 + 路由 | 5 d | 关键基础设施 |
| **阶段 1** Syllepsis 基础富文本 + MarkdownIO | 8 d | 替代 MarkdownController 的加载/保存 |
| **阶段 2** mention + slash 自研 plugin | 10 d | 8.2 节的 40 d - 已含此 |
| **阶段 3** AI 浮窗 + 流式集成 + Syllepsis transaction | 10 d | 5.5 + 5.6 + 5.10 |
| **阶段 4** y-prosemirror 协同 + yjs leveldb 持久化 | 8 d | yjs-server 改动 |
| **阶段 5** 后端 Spring 新增端点 + Flyway + 模板/历史 | 8 d | 4 个 controller + 2 表 |
| **阶段 6** 自研 plugin(图片 resize/KaTeX/Mermaid/doubao 代码块) | 16 d | 全部 P1/P2 |
| **阶段 7** 主题/UI/QA | 10 d | 蚂蚁 + Tailwind + 测试 |
| **合计** | **75 d 现实(乐观 55 / 悲观 110)** | |

---

## 7. 验证(端到端)

### 阶段 0 验证
```bash
# 1. 装包
cd miaotongdoc-ai-write
npm install
npm run dev
# 浏览器打开 http://localhost:5173,看到 /ai-write/home 模板库
```

### 阶段 1-2 验证
```bash
# 路由测试
# /ai-write/editor/test-doc-key 输入文字、保存、刷新页面

# Markdown round-trip
# 输入 markdown 文本(包括表格 / 任务列表 / 引用),看 Syllepsis 渲染是否正确
# 保存后 GET /api/ai-write/documents/{id}/content 应返回原 markdown
```

### 阶段 3 验证(AI 流式)
```bash
# 选区触发 AI 改写
# 流式 30 秒内拿到完整结果
# Typewriter 效果不卡顿(25ms/字)
```

### 阶段 4 验证(协同)
```bash
# 两个浏览器同时打开 /ai-write/editor/test-doc-key
# 任一输入,另一 500ms 内看到
# 协同光标正确显示
```

### 阶段 7 验证(整体)
```bash
# 主题切换正常
# 打开编辑器 → 选 slash → /heading 一级标题 → 输入"季度总结"
# Ctrl+S 保存 → /api/ai-write/documents/{id}/save 200 OK
# 关闭页面后再打开,内容完整恢复
```

---

## 8. 风险与对策汇总

| 风险 | 严重性 | 对策 |
|---|---|---|
| Syllepsis 16 个月无维护 | 高 | Fork 自维护 + 锁版本 |
| Syllepsis 无 mention / slash plugin | 高 | 40 d 自研工作量 |
| AI 流式 transaction 抖动 | 中 | rAF 节流 + ProseMirror setMeta |
| 协同冲突与 AI 插入位置 | 中 | Yjs CRDT 自带,但 UI 提示要自实现 |
| Syllepsis 与 Tailwind 样式冲突 | 中 | 局部禁用 preflight 或用 CSS Modules |
| 移动端体验 | 中 | Syllepsis 主桌面,移动端后续优化 |
| React 18 strict mode 双 mount | 低 | Syllepsis mount idempotent 测试 |

---

## 9. 与 v1.4 iframe 方案的关键差异

| 维度 | v1.4 iframe 替换 MD 编辑器 | v2.0 新增 AI 写作模块 |
|---|---|---|
| 目标 | 替换 | 新增 |
| 旧 MD 编辑器影响 | 不兼容,iframe 替代 | 完全不动 |
| Syllepsis 部署 | 与 Vue 同域 iframe | 完全独立域名/路径 |
| 协同冲突风险 | 与 md-{docKey} 同栈可能冲突 | 用 aiw-{docKey} 完全隔离 |
| 工作量 | 66 d | **75 d** |
| Syllepsis 维护负担 | vue/react 双 runtime | **仅 React**(本模块完全 Syllepsis 适配) |
| Vue ↔ React 桥 | 需要 | **不需要** |
| 商业模式 | 维护 Tiptap 同时维护 Syllepsis | Syllepsis 独立,主站 Tiptap 不受影响 |

---

## 10. 立即可执行的下一步

按 §6.7 工作量,**前 5 天骨架阶段**:

1. **Fork Syllepsis**:`git clone https://github.com/bytedance/syllepsis.git miaotong-internal/syllepsis-fork`,内部 npm 别名
2. **建独立 React 项目骨架**:
   ```bash
   mkdir miaotongdoc-ai-write && cd miaotongdoc-ai-write
   npm init -y
   npm install react@^18 react-dom@^18 react-router-dom@^6 zustand@^4 antd@^5 dompurify@^3
   npm install -D typescript@^5 vite@^5 @vitejs/plugin-react @types/react
   # Syllepsis 在 fork 仓发布后装
   ```
3. **建最小路由**:`/ai-write/home`、`/ai-write/editor/:docId`,React Router v6
4. **SylEditor 装包跑通 demo 页**:`npm run dev`,看到 SylEditor 渲染
5. **决定 UI 库**:antd 5(文档案例)vs shadcn/ui(更现代)

**5 天验收标准**:
- Syllepsis 在 React 中渲染 hello world
- 路由 `/ai-write/editor/test` 可访问,容器内见 SylEditor
- vite build 产物 < 1MB(不计 Syllepsis)

**PoC 通过后再进入阶段 1 实质编码**;任一不过则暂停(回退到方案 A/B 或更保守方案)。

---

## 11. 待决清单(优先级降序)

1. **UI 库选 antd 5 还是 shadcn/ui?**(antd 跟 Syllepsis 文档示例一致,shadcn 更现代)
2. **数据存储是否复用 `mt_document` 表,还是新增 `ai_write_document` 表?**(复用更顺,新增更专)
3. **模板是用户级还是租户级?**(主站没 multi-tenant,默认用户级)
4. **协同 docKey 用 `aiw-{key}` 是否会被拒绝?**(需改 yjs-server 正则)
5. **是否做移动端 / 响应式?**(Syllepsis 桌面优先,移动端后续 v3)
6. **Fork Syllepsis 后,内部 npm 注册用 Verdaccio 还是直接通过 file: 别名?**
7. **后端 AI 流式改用 WebFlux 响应式提升并发?**(当前 SseEmitter 已经够,本期不做)
8. **是否做"AI 一键插入整篇预填文档"作为杀手特性?**(v1 必备,v2 可选)

---

## §12 v2.1 实施级代码文件规划(每个文件的 exports / 依赖 / 契约)

本节是 §3.1 目录结构的**逐文件深化**。每个文件列出:
- **路径**(相对 `miaotongdoc-ai-write/` 或后端 src)
- **作用**
- **导出 / 公开 API**(TypeScript export / Java public method)
- **依赖**(被谁 import、依赖哪些内部模块)
- **类型契约**(`interface` / `type` / `Entity` 关键字段)

### 12.1 框架入口层

#### `miaotongdoc-ai-write/src/main.tsx`
```ts
export function bootstrap()
```
- React 18 `createRoot` mount `<App />` 到 `#root`
- 注册 Ant Design ConfigProvider(中文 locale)+ 全局 error boundary

依赖:`./App`、`antd`

#### `miaotongdoc-ai-write/src/App.tsx`
```ts
export default function App(): JSX.Element
```
- `<RouterProvider router={router}>`
- `<ConfigProvider locale={zhCN}>`
- 全局 `<SessionBootstrap>` 组件:启动时拿 token,失败跳 `/login`

依赖:`./router`、`./store/user`、`antd`

#### `miaotongdoc-ai-write/src/router.tsx`
```ts
export const router: ReturnType<typeof createBrowserRouter>
export const ROUTES = {
  HOME: '/ai-write',
  EDITOR: '/ai-write/editor/:docKey',
  TEMPLATES: '/ai-write/templates',
  SETTINGS: '/ai-write/settings',
} as const
```
- React Router v6 `createBrowserRouter` 配置 + 懒加载每个页面
- 守卫:token 缺失跳主站 `/login`

依赖:`./pages/*`

### 12.2 类型契约层(`src/types/`)

#### `src/types/document.ts`
```ts
export interface AiWriteDoc {
  docId: string                  // 主键(也是 docKey)
  title: string
  docType: 'markdown'            // 复用 mt_document
  ownerId: number
  ownerName: string
  filePath: string               // mt_document.filePath
  currentVersion: number
  status: 'draft' | 'signed' | 'archived'
  createdAt: string
  updatedAt: string
}

export interface AiWriteDocListResp {
  content: AiWriteDoc[]
  totalElements: number
  totalPages: number
  number: number
}

export interface DocContentResp {
  docKey: string
  content: string                // markdown 字符串
  version: number
}
```

#### `src/types/template.ts`
```ts
export interface AiWriteTemplate {
  id: number
  name: string
  description: string
  prompt: string                 // 给 LLM 的 system prompt
  schemaJson?: string            // 可选,Syllepsis schema JSON 作为初始内容
  scope: 'system' | 'user'       // 系统预置 vs 用户自建
  ownerId: number
  createdAt: string
}

export interface TemplateCreateReq {
  name: string
  description: string
  prompt: string
  schemaJson?: string
}
```

#### `src/types/history.ts`
```ts
export interface AiWriteHistorySnapshot {
  id: number
  docId: string
  contentMd: string              // 完整 markdown 快照
  source: 'autosave' | 'manual'  // 自动 vs 手动
  snapshotAt: string
}

export interface HistoryListResp {
  content: AiWriteHistorySnapshot[]
  totalElements: number
}
```

#### `src/types/ai.ts`
```ts
export type AiAction =
  | 'expand' | 'rewrite' | 'translate' | 'summarize' | 'polish'
  | 'outline' | 'grammar' | 'style' | 'vision' | 'continue'

export interface AiStreamReq {
  action: AiAction
  docKey?: string                // 协同文档时挂当前 Y.Doc
  selection?: { from: number, to: number, text: string }
  question?: string              // 用户 prompt
  instruction?: string           // 改写指令
  targetLang?: string            // 'zh-CN' | 'en' | ...
  fileId?: string                // 多模态图片
  history?: ChatMessage[]
  systemPrompt?: string
  temperature?: number
  stream: true                   // 强制 true(本模块只走流式)
}

export interface AiStreamChunk {
  type: 'thinking' | 'content' | 'error' | 'done'
  content: string
  done?: boolean
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}
```

#### `src/types/collab.ts`
```ts
export interface CollabUser {
  userId: number
  name: string
  color: string                  // '#RRGGBB'
  cursor?: { from: number, to: number }
}

export interface YjsRoomConfig {
  docKey: string
  wsUrl: string                  // 'wss://host/ws/yjs/'
  userName: string
  userId: number
}
```

#### `src/types/postmessage.ts`(预留,供未来 npm 复用)
```ts
// 当前模块是独立 SPA,不需要 postMessage 桥
// 但预留类型给"嵌入主站"场景作 v3 扩展
export type AiWriteEvent =
  | { type: 'doc-saved', docKey: string, version: number }
  | { type: 'doc-opened', docKey: string }
  | { type: 'ai-stream-error', docKey: string, error: string }
```

### 12.3 API 层(`src/api/`)

#### `src/api/client.ts`
```ts
export interface ApiOptions {
  baseUrl?: string               // 默认 '/api'
  tokenKey?: string              // 默认 'token'
  onUnauthorized?: () => void    // 主站跳登录
}

export function createClient(opts?: ApiOptions): {
  get<T>(path: string, params?: Record<string, unknown>): Promise<T>
  post<T>(path: string, body?: unknown): Promise<T>
  put<T>(path: string, body?: unknown): Promise<T>
  delete<T>(path: string): Promise<T>
}
```
- fetch 封装:自动加 `Authorization: Bearer ${token}`,401 触发 onUnauthorized
- 全局错误 toast(antd `message.error`)
- 请求 / 响应 TypeScript 泛型

#### `src/api/ai-write.ts`
```ts
export interface AiWriteApi {
  createDoc(req: { title: string, templateId?: number }): Promise<AiWriteDoc>
  listDocs(params?: { page?: number, size?: number, sort?: string }): Promise<AiWriteDocListResp>
  getContent(docKey: string): Promise<DocContentResp>
  saveContent(docKey: string, content: string): Promise<{ version: number }>
  listTemplates(): Promise<AiWriteTemplate[]>
  createTemplate(req: TemplateCreateReq): Promise<AiWriteTemplate>
  deleteTemplate(id: number): Promise<void>
  listHistory(docKey: string): Promise<HistoryListResp>
}
export const aiWriteApi: AiWriteApi
```
- 端点(详见 §3.3 + §4.3)

#### `src/api/ai.ts`
```ts
export interface AiApi {
  stream(docKey: string, req: AiStreamReq): Promise<ReadableStream<AiStreamChunk>>
  listModels(): Promise<{ id: string, name: string }[]>
}
export const aiApi: AiApi
```
- 走 `fetch` + `ReadableStream`,解析 SSE 块成 `AiStreamChunk`

#### `src/api/user.ts`
```ts
export interface UserSearchResp {
  id: number
  username: string
  employeeId: string
  realName: string
}
export interface UserApi {
  search(query: string, limit?: number): Promise<UserSearchResp[]>
}
export const userApi: UserApi
```

### 12.4 Hooks 层(`src/hooks/`)

#### `src/hooks/useAuth.ts`
```ts
export interface AuthState {
  token: string | null
  userId: number | null
  userName: string | null
  realName: string | null
}
export function useAuth(): {
  auth: AuthState
  isLoggedIn: boolean
  logout(): void
}
```
- 从 `sessionStorage.getItem('token')` 读 token
- 解析 JWT payload 拿 userId / userName
- `logout()` 清除 sessionStorage + 跳主站 `/login`

#### `src/hooks/useAiStream.ts`(从主站 `useAiChat.ts` 迁移)
```ts
export type StreamStatus = 'ready' | 'submitted' | 'streaming' | 'aborted' | 'error'
export interface StreamHandle {
  status: Ref<StreamStatus>
  content: Ref<string>             // 当前累积的正文
  reasoning: Ref<string>           // 思考链路
  error: Ref<Error | undefined>
  start(req: AiStreamReq): Promise<void>
  stop(): void
}
export function useAiStream(opts: {
  onChunk?: (chunk: AiStreamChunk) => void
  onDone?: (final: { content: string, reasoning: string }) => void
}): StreamHandle
```
- 核心算法:**与 `useAiChat.ts` 同款** —— AbortController + 25ms 打字机 + DOMPurify + 429 识别
- rAF 节流 + Typewriter 双节流都保留
- 把 Vue 的 `triggerRef` 改成 React 的 `useState` + 手动 `forceUpdate`

#### `src/hooks/useMarkdownIO.ts`
```ts
export interface MarkdownIO {
  loadFromMarkdown(md: string, api: SylApi): void
  saveToMarkdown(api: SylApi): string
  watchAutoSave(api: SylApi, onDebouncedSave: (md: string) => void): () => void
}
export function useMarkdownIO(): MarkdownIO
```
- 输入:用 `MarkdownParser.fromSchema(schema).parse(md)`(扩展 token:GFM 表格/任务列表/删除线)
- 输出:`MarkdownSerializer.fromSchema(schema).serialize(api.view.state.doc)`
- `watchAutoSave`:监听 `api.update` 事件 2s 防抖,触发外部 save

#### `src/hooks/useYjsRoom.ts`
```ts
export interface YjsRoomHandle {
  ydoc: Y.Doc | null
  provider: WebsocketProvider | null
  collabUsers: CollabUser[]
  isSynced: boolean
  destroy(): void
}
export function useYjsRoom(opts: {
  docKey: string | null
  wsUrl: string
  user: { id: number, name: string, color: string }
}): YjsRoomHandle
```
- `new Y.Doc()` + `new WebsocketProvider(wsUrl, 'md-' + docKey, ydoc)`
- awareness:`provider.awareness.setLocalStateField('user', {id, name, color})`
- 监听 `awareness.on('change')` → 同步 `collabUsers`
- refCount 管理多实例复用,与主仓 `getYjsEntry` 同模式

#### `src/hooks/useSyllepsisCollab.ts`(Syllepsis ↔ Yjs 桥)
```ts
export function useSyllepsisCollab(api: SylApi | null, room: YjsRoomHandle): void
```
- `api` 准备好后,挂载 y-prosemirror 桥:
  ```ts
  const fragment = room.ydoc.getXmlFragment('content')
  const newState = api.view.state.reconfigure({
    plugins: [...api.view.state.plugins, ySyncPlugin(fragment), yCursorPlugin(room.provider.awareness)]
  })
  api.view.updateState(newState)
  ```
- 返回时(组件卸载)恢复 plugins

### 12.5 Syllepsis 编辑器运行时(`src/editor/runtime/`)

#### `src/editor/runtime/create.ts`
```ts
export interface CreateEditorOptions {
  mount: HTMLElement
  content?: string                // 初始 markdown
  canEdit: boolean
  onChange?: (md: string) => void
  onSelectionChange?: (selection: { from: number, to: number, text: string }) => void
  onReady?: (api: SylApi) => void
}
export function createAiWriteEditor(opts: CreateEditorOptions): SylApi
```
- 加载自定义 plugin(mention/slash/codeblock-doubao/...)
- 加载官方 plugin(basic/code-block/table/placeholder)
- 把生成的 ProseMirror plugin 数组传 `SylEditorService.init(mount, props)`
- 返回 `SylApi` 实例

#### `src/editor/runtime/mention-plugin/index.ts`
```ts
export function createMentionPlugin(opts: {
  fetchUsers: (query: string) => Promise<UserSearchResp[]>
}): SylApi.Plugin
```
- 基于 ProseMirror 装饰器 + Suggestion 风格
- `@` 触发:展示 popup(`document.body.appendChild`),定位用 `view.coordsAtPos`
- 键盘 ↑↓Enter+Esc 处理
- 选用户后 `tr.replaceWith(from, to, mentionNode)`

数据结构 ProseMirror node:
```ts
{
  type: 'mention',
  attrs: { userId: number, realName: string, employeeId: string }
}
```

#### `src/editor/runtime/slash-plugin/index.ts`
```ts
export interface SlashItem {
  id: string                      // 'h1' | 'quote' | 'codeblock' | 'table' | ...
  title: string
  icon?: string
  description?: string
  keywords: string[]
  command: (api: SylApi) => void
}
export function createSlashPlugin(opts: {
  items: SlashItem[]
}): SylApi.Plugin
```
- 默认 items:h1/h2/h3/quote/codeblock/codeblock-doubao/bulletList/orderedList/taskList/table/hr/divider/insertTemplate/aiOutline
- `/` 触发:在 caret 位置显示 popup
- 用户选择 → 执行 `command(api)`,通常是 `api.command.insertXXX(...)` 或 view.dispatch 插入节点

#### `src/editor/runtime/ai-plugin/index.ts`
```ts
export interface AiInsertOptions {
  selection: { from: number, to: number }
  mode: 'replace' | 'insert-after' | 'insert-at-cursor'
  generator: (onChunk: (text: string) => void, signal: AbortSignal) => Promise<void>
}
export function createAiInsertPlugin(): SylApi.Plugin
export function aiInsert(api: SylApi, opts: AiInsertOptions): { abort(): void }
```
- 关键算法:
  ```ts
  const tr = api.view.state.tr
  // 1. 占位 span:<span data-ai-stream-id="uuid">▍</span>
  // 2. dispatch(tr)
  // 3. 流式 chunk 到:用最新的 tr 替换占位 span 内容
  // 4. rAF 节流:每 16ms 一次 view.dispatch
  // 5. 结束:把 span 替换为正式文本节点
  // 6. 全程 setMeta('addToHistory', false) 防止污染 undo
  ```
- 跨组件通讯:`SylApi` 全局实例,通过 ref 或 context 共享

#### `src/editor/runtime/codeblock-doubao/index.ts`
```ts
export function createCodeBlockDouBaoPlugin(opts: {
  defaultLanguage?: string        // 默认 'plain'
  defaultTheme?: 'light' | 'dark'
}): SylApi.Plugin
```
- 自写 NodeView(给 `editor.runtime.create` 装载)
- 节点结构:
  ```ts
  {
    type: 'codeblockDouBao',
    attrs: { language: string, caption?: string, theme: 'light' | 'dark', wrap: boolean }
  }
  ```
- 渲染:language dropdown / theme toggle / 行号 / wrap toggle / 自定义 header
- 序列化:markdown ` ```lang {caption}\n code \n``` `

#### `src/editor/runtime/image-resize/index.ts`
```ts
export function createImageResizePlugin(): SylApi.Plugin
```
- 给 `image` node 加 `width` / `height` attrs(50–800 像素范围)
- NodeView 内 mousedown → 计算 dx → updateAttributes({width: newWidth})
- 在 4 角 / 4 边显示拖拽 handle(React 渲染时附加 onMouseDown listener)

#### `src/editor/runtime/mathjax/index.ts`
```ts
export function createMathJaxPlugin(): SylApi.Plugin
```
- 节点结构:
  ```ts
  { type: 'inlineMath', attrs: { latex: string } }   // $...$
  { type: 'blockMath',  attrs: { latex: string } }   // $$...$$
  ```
- NodeView:render 时调用 KaTeX renderToString
- 序列化:`parseMarkdown` / `serializeMarkdown` 双向转换

#### `src/editor/runtime/mermaid/index.ts`
```ts
export function createMermaidPlugin(): SylApi.Plugin
```
- 节点结构:
  ```ts
  { type: 'mermaid', attrs: { source: string } }
  ```
- NodeView:async import mermaid 后 `mermaid.render(id, source)`
- 序列化:```markdown\n```mermaid  与 node 互转

### 12.6 Markdown IO(`src/editor/io/markdown.ts`)

#### `src/editor/io/markdown.ts`
```ts
export interface MarkdownIO {
  parse(md: string, schema: Schema): ProsemirrorNode
  serialize(doc: ProsemirrorNode, schema: Schema): string
  registerToken(name: string, parser: TokenParser, serializer: TokenSerializer): void
  reset(): void
}
export function createMarkdownIO(opts?: { gfm?: boolean, math?: boolean }): MarkdownIO
```
- 封装 `prosemirror-markdown` 的 MarkdownParser/MarkdownSerializer
- `registerToken` 用于自定义节点(mention/codeblock-doubao/mermaid/math)

### 12.7 协同桥(`src/editor/collab/yjs-bridge.ts`)

#### `src/editor/collab/yjs-bridge.ts`
```ts
export interface MountCollabOpts {
  api: SylApi
  ydoc: Y.Doc
  awareness: Awareness
  fieldName?: string              // 默认 'content'
}
export function mountCollab(opts: MountCollabOpts): () => void
```
- 返回卸载函数:`api.view.updateState(originalState)` 恢复 plugins
- 这是 §5.6 难点的实现

### 12.8 页面层(`src/pages/`)

#### `src/pages/AiWriteHome.tsx`
```ts
export default function AiWriteHome(): JSX.Element
```
- 三个区:模板库(左)/ 我的文档(中)/ AI 灵感(右)
- 模板库:卡片网格 → 点击创建文档或选用模板
- 我的文档:`useQuery(listDocs)`
- AI 灵感:快捷指令按钮(扩写/续写/翻译/...)

依赖:`./components/TemplateCard`、`./components/DocList`、`api/ai-write`

#### `src/pages/AiWriteEditor.tsx`
```ts
export interface AiWriteEditorRoute {
  docKey: string                  // URL param
}
export default function AiWriteEditor({ params }: { params: AiWriteEditorRoute }): JSX.Element
```
- 编排各模块:Toolbar + Outline + AiChatPanel + Toolbar + Editor + CollabAvatars
- `useCreateEditor` + `useMarkdownIO` + `useAiStream` + `useYjsRoom` + `useSyllepsisCollab`
- 生命周期:`onMount` 创建 Syllepsis + 挂 yjs 桥;`onUnmount` 销毁 + 触发最后一次 save

依赖:全部 hooks + components

#### `src/pages/AiWriteTemplate.tsx`
```ts
export default function AiWriteTemplate(): JSX.Element
```
- 两栏:左侧 system 模板(只读)/ 右侧 user 模板(可创建/编辑/删除)
- 创建模板:名称 + 描述 + prompt(system prompt,multiline) + 可选 schemaJson
- 删除二次确认(antd `Modal.confirm`)

依赖:`./components/TemplateForm`

#### `src/pages/AiWriteSettings.tsx`
```ts
export default function AiWriteSettings(): JSX.Element
```
- 默认 model(`api.listModels`)
- 主题切换(antd theme algorithm)
- 字体偏好
- 退出登录

依赖:`./components/ThemeSwitch`、`./components/ModelSelect`

### 12.9 组件层(`src/components/`)

#### `src/components/SylEditorAdapter.tsx`
```ts
export interface SylEditorAdapterProps {
  docKey: string
  initialContent?: string
  canEdit: boolean
  onReady?: (api: SylApi) => void
  onChange?: (md: string) => void
  onSelectionChange?: (sel: { from: number, to: number, text: string }) => void
}
export function SylEditorAdapter(props: SylEditorAdapterProps): JSX.Element
```
- `useEffect` mount 时:`createAiWriteEditor({ mount: ref.current, ... })` 返回 api,保存到 ref
- `useEffect` cleanup 销毁 SylApi(`api.uninstall()`)
- 用 `forwardRef` 把 api 暴露给父组件

#### `src/components/Toolbar.tsx`
```ts
export interface ToolbarProps {
  api: SylApi | null
  canEdit: boolean
  onTriggerAi: (action: AiAction, selection: { from: number, to: number, text: string }) => void
}
export function Toolbar(props: ToolbarProps): JSX.Element
```
- 基于 antd `Button` + `Dropdown` / `Tooltip`
- Syllepsis 工具按钮:`api.command.bold()` / `api.command.italic()` / ...
- 自定义按钮:AI 相关(扩写/翻译/改写/摘要)

#### `src/components/AiFloatPanel.tsx`
```ts
export interface AiFloatPanelProps {
  api: SylApi
  selection: { from: number, to: number, text: string }
  onClose: () => void
}
export function AiFloatPanel(props: AiFloatPanelProps): JSX.Element
```
- 浮层定位:`coords = api.view.coordsAtPos(selection.from); style.top = coords.top - 40; style.left = coords.left;`
- 输入栏 + 操作按钮(扩写/翻译/总结/改写)
- 流式输出:`useAiStream` → `apiInsert(api, {...})` 实时替换选区

#### `src/components/AiChatPanel.tsx`
```ts
export interface AiChatPanelProps {
  docKey: string
}
export function AiChatPanel(props: AiChatPanelProps): JSX.Element
```
- 右侧抽屉 + 多轮对话
- 用户消息历史 + AI 流式回复
- 支持加入"当前选区文本"作为附加上下文

#### `src/components/SlashMenu.tsx`
```ts
export interface SlashMenuProps {
  items: SlashItem[]
  onSelect: (item: SlashItem) => void
  onDismiss: () => void
}
export function SlashMenu(props: SlashMenuProps): JSX.Element
```
- 全屏居中浮层 + 列表 + 搜索框
- 键盘 ↑↓Enter+Esc

#### `src/components/MentionPopup.tsx`
```ts
export interface MentionPopupProps {
  query: string
  fetchUsers: (query: string) => Promise<UserSearchResp[]>
  onSelect: (user: UserSearchResp) => void
  onDismiss: () => void
}
export function MentionPopup(props: MentionPopupProps): JSX.Element
```
- 在 caret 位置显示下拉列表
- 异步加载 users(走父 `userApi.search`)

#### `src/components/CollabAvatars.tsx`
```ts
export interface CollabAvatarsProps {
  users: CollabUser[]
}
export function CollabAvatars(props: CollabAvatarsProps): JSX.Element
```
- 头部右侧头像堆叠 + tooltip 显示用户名

#### `src/components/Outline.tsx`
```ts
export interface OutlineProps {
  api: SylApi
}
export function Outline(props: OutlineProps): JSX.Element
```
- 左侧侧栏,从 ProseMirror doc 抓 heading(每次 transaction 后重抓)
- 锚点滚动:点击 N 级标题 → `api.view.focus()` + `api.command.scrollIntoView(pos)`

#### `src/components/ExportMenu.tsx`
```ts
export interface ExportMenuProps {
  docKey: string
}
export function ExportMenu(props: ExportMenuProps): JSX.Element
```
- 导出 markdown / PDF / Word
- 走 `/api/documents/{id}/export/pdf`(主站原接口,复用)

#### `src/components/ThemeSwitch.tsx`
```ts
export interface ThemeSwitchProps {
  theme: 'light' | 'dark'
  onChange: (t: 'light' | 'dark') => void
}
export function ThemeSwitch(props: ThemeSwitchProps): JSX.Element
```
- antd `Switch`,改 ConfigProvider 的 algorithm

#### `src/components/ModelSelect.tsx`
```ts
export interface ModelSelectProps {
  models: { id: string, name: string }[]
  value: string
  onChange: (id: string) => void
}
export function ModelSelect(props: ModelSelectProps): JSX.Element
```
- antd `Select`,从 `api.listModels()` 填充选项

### 12.10 Store 层(`src/store/`,Zustand)

#### `src/store/user.ts`
```ts
export interface UserState {
  userId: number | null
  userName: string | null
  token: string | null
  setUser: (u: { userId: number, userName: string, token: string }) => void
  logout: () => void
}
export const useUserStore: UseBoundStore<StoreApi<UserState>>
```

#### `src/store/doc.ts`
```ts
export interface DocState {
  current: AiWriteDoc | null
  isDirty: boolean
  lastSaveStatus: 'idle' | 'saving' | 'saved' | 'error'
  setCurrent: (d: AiWriteDoc | null) => void
  setDirty: (b: boolean) => void
  setSaveStatus: (s: DocState['lastSaveStatus']) => void
}
export const useDocStore: UseBoundStore<StoreApi<DocState>>
```

#### `src/store/ai.ts`
```ts
export interface AiState {
  defaultModel: string | null
  models: { id: string, name: string }[]
  setModels: (m: { id: string, name: string }[]) => void
  setDefaultModel: (id: string) => void
}
export const useAiStore: UseBoundStore<StoreApi<AiState>>
```

### 12.11 工具层(`src/utils/`)

#### `src/utils/sanitize.ts`
```ts
export function sanitizeAiMarkdown(s: string): string         // DOMPurify 白名单
export function escapeHtml(s: string): string                 // textContent 自动转义
export function sanitizePlainText(s: string): string          // 去所有 HTML
```
- 与 `src/utils/sanitize.ts` 主站版逻辑等价

#### `src/utils/jwt.ts`
```ts
export interface JwtPayload {
  userId: number
  employeeId: string
  username: string
  role: string
  exp: number                    // 过期时间
}
export function parseJwt(token: string): JwtPayload | null
```

#### `src/utils/retry.ts`
```ts
export interface RetryOptions {
  attempts: number               // 默认 3
  delayMs: number                // 默认 1000
  backoff?: 'exponential'
  retryOn?: (err: unknown) => boolean  // 默认识别 429
}
export function withRetry<T>(fn: () => Promise<T>, opts?: RetryOptions): Promise<T>
```

#### `src/utils/color.ts`
```ts
export function generateUserColor(seed: number): string      // '#RRGGBB'
```
- 通过 HSL 哈希 userId 保证稳定色彩

### 12.12 样式层(`src/styles/`)

#### `src/styles/theme.ts`
```ts
export const lightTheme: ThemeConfig
export const darkTheme: ThemeConfig
export const syllepsisStyles: { [className: string]: CSSProperties }   // Syllepsis 内置样式 override
```

#### `src/styles/global.css`
- Tailwind preflight 关闭:`@tailwind base` 时用 `corePlugins: { preflight: false }`
- 全局 CSS 变量:色板 / 字号 / 行高

### 12.13 后端 Java 代码(增量新增)

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/AiWriteDocumentController.java`
```java
@RestController
@RequestMapping("/api/ai-write/documents")
public class AiWriteDocumentController {
  public ResponseEntity<AiWriteDoc> create(@RequestBody CreateDocReq req, HttpServletRequest http)
  public ResponseEntity<DocContentResp> getContent(@PathVariable String docKey, HttpServletRequest http)
  public ResponseEntity<SaveResp> save(@PathVariable String docKey, @RequestBody SaveContentReq req, HttpServletRequest http)
  public ResponseEntity<AiWriteDocListResp> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, HttpServletRequest http)
}
```

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/AiWriteStreamController.java`
```java
@RestController
@RequestMapping("/api/ai-write/stream")
public class AiWriteStreamController {
  public SseEmitter outline(@RequestBody AiStreamReq req, HttpServletRequest http)
  public SseEmitter expand(@RequestBody AiStreamReq req, HttpServletRequest http)
  public SseEmitter polish(@RequestBody AiStreamReq req, HttpServletRequest http)
  public SseEmitter grammar(@RequestBody AiStreamReq req, HttpServletRequest http)
  public SseEmitter style(@RequestBody AiStreamReq req, HttpServletRequest http)
  public SseEmitter vision(@RequestPart("file") MultipartFile file, @RequestPart("question") String question, HttpServletRequest http)
}
```
- 每个端点:复用 `AiProxyService.proxy` + SseEmitter,与 `AiChatSseController` 同模式

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/AiWriteTemplateController.java`
```java
@RestController
@RequestMapping("/api/ai-write/templates")
public class AiWriteTemplateController {
  public ResponseEntity<List<AiWriteTemplate>> list(HttpServletRequest http)
  public ResponseEntity<AiWriteTemplate> create(@RequestBody TemplateCreateReq req, HttpServletRequest http)
  public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http)
}
```

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/controller/AiWriteHistoryController.java`
```java
@RestController
@RequestMapping("/api/ai-write/history")
public class AiWriteHistoryController {
  public ResponseEntity<HistoryListResp> listByDoc(@PathVariable String docKey, HttpServletRequest http)
  public ResponseEntity<AiWriteHistorySnapshot> save(@RequestBody HistorySaveReq req, HttpServletRequest http)
}
```

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/service/AiWriteDocumentService.java`
```java
@Service
public class AiWriteDocumentService {
  public AiWriteDoc createDoc(Long ownerId, String title, Long templateId)
  public String loadContent(Long docId)                          // 走 storageService.load
  public int saveContent(Long docId, String md, Long userId)
  public Page<AiWriteDoc> listDocs(Long ownerId, Pageable page)
}
```
- **复用现有 `DocumentService` 与 `StorageService`**;只编排,不改核心

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/service/AiWritePromptBuilder.java`
```java
@Component
public class AiWritePromptBuilder {
  public String buildExpandPrompt(String selection, String surrounding)   // 扩写
  public String buildPolishPrompt(String selection, String style)         // 润色
  public String buildOutlinePrompt(String topic, Integer levels)           // 大纲
  public String buildGrammarPrompt(String text)                           // 语法纠错
  public String buildVisionPrompt(String question)                         // 视觉问答
}
```
- 类比主站 `DocumentAiController.buildDefaultGeneratePrompt`

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/entity/AiWriteTemplate.java`
```java
@Entity @Table(name = "ai_write_template")
public class AiWriteTemplate {
  @Id @GeneratedValue private Long id;
  private String name;
  private String description;
  @Column(length = 4000) private String prompt;
  @Column(columnDefinition = "TEXT") private String schemaJson;
  @Enumerated(EnumType.STRING) private Scope scope;       // 'SYSTEM' | 'USER'
  private Long ownerId;
  private LocalDateTime createdAt;
}
```

#### `miaotongdoc-server/src/main/java/com/miaotong/doc/entity/AiWriteHistory.java`
```java
@Entity @Table(name = "ai_write_history")
public class AiWriteHistory {
  @Id @GeneratedValue private Long id;
  private String docKey;
  @Column(columnDefinition = "TEXT") private String contentMd;
  @Enumerated(EnumType.STRING) private Source source;
  private LocalDateTime snapshotAt;
}
```

#### Flyway 迁移
```sql
-- V22__add_ai_write_template.sql
CREATE TABLE ai_write_template (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  description VARCHAR(1000),
  prompt VARCHAR(4000) NOT NULL,
  schema_json TEXT,
  scope VARCHAR(20) NOT NULL DEFAULT 'USER',
  owner_id BIGINT REFERENCES sys_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V23__add_ai_write_history.sql
CREATE TABLE ai_write_history (
  id BIGSERIAL PRIMARY KEY,
  doc_key VARCHAR(64) NOT NULL,
  content_md TEXT,
  source VARCHAR(20) NOT NULL DEFAULT 'AUTOSAVE',
  snapshot_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_write_history_doc_key ON ai_write_history (doc_key, snapshot_at DESC);
```

#### `MiaotongDoc-Docker/app/yjs/server.js` 改动
```js
// 1 行改动
- const docName = decoded[0].match(/^(md|pdf)-[a-z0-9-]+/);
+ const docName = decoded[0].match(/^(md|pdf|aiw)-[a-z0-9-]+/);
```
- 不动其他代码

#### `MiaotongDoc-Docker/config/nginx/nginx.conf` 改动
```nginx
# 在 location / { ... } 之前加一段
location /ai-write/ {
  root /usr/share/nginx/ai-write;
  try_files $uri $uri/ /ai-write/index.html;
}

# WebSocket /ws/yjs/ 不动
```

---

### 12.14 文件清单分类汇总

#### 前端新增(独立 React 项目内,~70 个文件)
- 入口:`main.tsx`、`App.tsx`、`router.tsx`、`index.html`(4)
- 类型:`types/` 5 文件
- API:`api/` 4 文件
- Hooks:`hooks/` 5 文件
- 编辑器运行时:`editor/runtime/` 7 个子目录 × 2 文件 = 14
- 协同:`editor/collab/yjs-bridge.ts` + `editor/io/markdown.ts`(2)
- 页面:`pages/` 4 文件
- 组件:`components/` ~12 文件
- Store:`store/` 3 文件
- 工具:`utils/` 4 文件
- 样式:`styles/` 2 文件
- 配置:`package.json`、`tsconfig.json`、`vite.config.ts`、`Dockerfile`(4)

#### 后端新增(~10 个 Java 文件)
- Controller × 4
- Service × 3(含 PromptBuilder)
- Entity × 2
- Repository × 2

#### DB 迁移(2 个 SQL)

#### Docker / Nginx 改动(2 行级)
- `nginx.conf` +1 段
- `docker-compose.yml` +1 行 volume
- `yjs/server.js` +1 字符正则

#### Syllepsis Fork 自维护仓(独立)
- `miaotong-internal/syllepsis-fork/`

#### **零侵入**(Vue 主站与现有 spring 模块)
- `miaotongdoc-web/` 不动
- `MarkdownController` / `MarkdownEditor.vue` 不动
- 现有所有后端业务模块不动

---

### 12.15 实施顺序(按依赖反向)

```
第 1 周(0 阶段)
├─ 1. Fork Syllepsis(读仓库 README + packages 全列)
├─ 2. npm init miaotongdoc-ai-write
├─ 3. 装 React 18 + Syllepsis 依赖 + Vite + Antd
├─ 4. 写 router + App + main + 4 个空页面
└─ 5. /ai-write/editor/test 看到 SylEditor hello world

第 2-3 周(1 阶段)
├─ 6. editor/io/markdown.ts
├─ 7. editor/runtime/create.ts(默认 plugin,不含 mention/slash)
├─ 8. components/Toolbar.tsx(基础富文本按钮)
├─ 9. api/ai-write.ts + api/client.ts
├─ 10. 后端 AiWriteDocumentController.java + AiWriteDocumentService.java
├─ 11. 集成测试:加载/保存/显示 markdown
└─ 12. Theme + Antd ConfigProvider + 全局样式

第 4-5 周(2 阶段)
├─ 13. mention plugin + Slash menu
├─ 14. MentionPopup + SlashMenu 组件
└─ 15. 与 Toolbar 集成,@触发测试

第 6-7 周(3 阶段)
├─ 16. editor/runtime/ai-plugin/index.ts
├─ 17. hooks/useAiStream.ts(从主站 useAiChat.ts 移植)
├─ 18. api/ai.ts
├─ 19. 后端 AiWriteStreamController.java(7 个端点)
└─ 20. components/AiFloatPanel + AiChatPanel

第 8 周(4 阶段)
├─ 21. editor/collab/yjs-bridge.ts
├─ 22. hooks/useYjsRoom + useSyllepsisCollab
├─ 23. yjs-server 正则扩 aiw-
└─ 24. yjs-server volume + leveldb 持久化

第 9-10 周(5 阶段)
├─ 25. 后端 AiWriteTemplate + AiWriteHistory Controller + Service
├─ 26. Flyway V22 + V23
├─ 27. 后端 AiWritePromptBuilder.java
└─ 28. 模板库 + 历史快照前端页面

第 11-12 周(6 阶段)
├─ 29. editor/runtime/codeblock-doubao/
├─ 30. editor/runtime/image-resize/
├─ 31. editor/runtime/mathjax/
└─ 32. editor/runtime/mermaid/

第 13 周(7 阶段)
├─ 33. Outline / ExportMenu / ThemeSwitch
├─ 34. AI 灵感快捷指令按钮
├─ 35. 性能/协同/AI 流式综合测试
└─ 36. 文档 + 用户指南 + docker 一键脚本
```

---

### 12.16 与 §6.7 工作量对照

§6.7 给的是"分阶段总人日"(75 d);本节给出**每个文件的实施顺序**(13 周)。两份对得上,§12.15 是 §6.7 的施工图纸。

---

*v2.1 在 v2.0 基础上追加逐文件实施规划,每个文件列出 exports / 依赖 / 契约,共追加 ~700 行。本次没改项目代码,仅计划文档细化。*


---

## §13 markitdown 集成 — 导入文件 → Markdown → Syllepsis 编辑器

> 本节是 **v2.4 的新增内容**。markitdown 是微软开源的多格式转 Markdown 工具(GitHub 11万 stars),与本模块"AI 协作写作"场景天然互补——用户**从 0 起草**升级为**"导入已有材料继续 AI 协作"**。

### §13.1 选型理由

| 维度 | markitdown | Docling(主站现有)| 结论 |
|---|---|---|---|
| 范围 | PDF / Word / PPT / Excel / 图片 OCR / 音频转录 / HTML | 主要 PDF(部分表格 / 图片位置) | **markitdown 通用更强** |
| 协议 | MIT | Apache 2.0 | 同 |
| 形态 | Python 库 + HTTP 服务 | Python 服务,目前仅 Java 内嵌 | markitdown 起独立 HTTP 服务更解耦 |
| 与 AI 集成 | ✅ MCP 协议(2025 新增)| 通过 prompt 集成 | markitdown 趋势更现代 |
| 在 AI 写作场景的角色 | **前端输入转换器**(文件 → MD → 编辑器) | 主站 PDF 文档识别(事后处理)| **互补不冲突** |

**最终架构**:**markitdown 与 Docling 并存**:
- Docling 走主站 PDF 文档的事后识别(V22–V24 已落地)
- markitdown 走 AI 写作模块的"导入 → 继续协作"流程

### §13.2 集成架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│ miaotongdoc-ai-write(浏览器内 React SPA)                                  │
│                                                                            │
│  <ImportFileButton> 用户点击上传                                            │
│    └─ POST /api/ai-write/import/file  multipart/form-data                │
│       (走 axios + sessionStorage token,自动 401 跳登录)                    │
└──────────────────────────────────────┬──────────────────────────────────┘
                                       │
                                       ▼ (Spring 路由)
┌─────────────────────────────────────────────────────────────────────────┐
│ miaotongdoc-server(Java Spring Boot 容器)                                │
│                                                                            │
│  AiWriteImportController                                                   │
│    └─ MarkitdownClient(Java WebClient) ──┐                                │
│    └─ DocumentService.createDocument(docType='markdown')                  │
│    └─ StorageService.store(filePath, markdown.getBytes(UTF_8))            │
│                                                                            │
│  返回 {docId, docKey, title, contentPreview}                              │
└──────────────────────────────────────┬────────────────┬────────────────────┘
                                       │                │
                          HTTP 5003   │                │ PostgreSQL
                                       ▼                ▼
                ┌────────────────────────────┐   ┌──────────────────┐
                │ miaotongdoc-markitdown     │   │ mt_document 表   │
                │ (Python 3.11 + FastAPI)    │   │ + 磁盘 .md 文件  │
                │                            │   │                  │
                │ POST /convert              │   │ docType='markdown'
                │   上传 multipart 文件      │   │ file_path 指向  │
                │   返回 { markdown: ... }   │   │ 转换结果         │
                │                            │   │                  │
                │ 内部:from markitdown       │   │                  │
                │       import MarkItDown    │   │                  │
                │       md = MarkItDown()    │   │                  │
                │       result = md.convert( │   │                  │
                │           file_bytes,      │   │                  │
                │           filename)        │   │                  │
                │       return result.text_  │   │                  │
                │             content        │   │                  │
                └────────────────────────────┘   └──────────────────┘
```

### §13.3 新增文件清单

#### MiaotongDoc-Docker(新增容器)

```
MiaotongDoc-Docker/app/markitdown/
├── Dockerfile                    # python:3.11-slim + pip install
├── requirements.txt              # fastapi uvicorn markitdown[pdf,docx,pptx,xlsx] python-multipart
├── server.py                     # FastAPI wrapper
└── README.md
```

**Dockerfile** 草稿:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .
EXPOSE 5003
HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:5003/health || exit 1
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "5003", "--workers", "2"]
```

**requirements.txt**:
```
fastapi==0.115.0
uvicorn[standard]==0.32.0
python-multipart==0.0.12
markitdown[pdf,docx,pptx,xlsx]==0.0.1
```

**server.py**:
```python
from fastapi import FastAPI, File, UploadFile, HTTPException
from markitdown import MarkItDown
from io import BytesIO

app = FastAPI(title="MiaotongDoc Markitdown Service", version="1.0.0")
md = MarkItDown()  # 全局实例

@app.get("/health")
async def health():
    return {"status": "ok", "service": "miaotongdoc-markitdown"}

@app.post("/convert")
async def convert(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        # markitdown 需要文件路径或 stream
        result = md.convert_stream(BytesIO(contents), file.filename)
        return {"markdown": result.text_content, "filename": file.filename}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"convert failed: {str(e)}")
```

#### miaotongdoc-server(新增 controller + service + client)

```
miaotongdoc-server/src/main/java/com/miaotong/doc/
├── controller/
│   └── AiWriteImportController.java      # 新增
├── service/
│   ├── MarkitdownService.java            # 新增
│   └── aiwrite/
│       └── AiWritePromptBuilder.java     # 已有规划
└── client/
    └── MarkitdownClient.java             # 新增(用 Spring WebClient 调用 Python 服务)
```

**MarkitdownClient.java** 草稿:
```java
@Component
public class MarkitdownClient {
    private final WebClient webClient;

    @Value("${markitdown.url:http://markitdown:5003}")
    private String markitdownUrl;

    public MarkitdownClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(markitdownUrl).build();
    }

    /**
     * 调用 markitdown 服务把文件转 markdown
     */
    public String convert(byte[] fileBytes, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override public String getFilename() { return filename; }
        });
        return webClient.post()
            .uri("/convert")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(builder.build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(node -> node.get("markdown").asText())
            .block(Duration.ofSeconds(120));  // markitdown 可能慢(尤其 PDF/OCR)
    }
}
```

**AiWriteImportController.java** 草稿:
```java
@Slf4j
@RestController
@RequestMapping("/api/ai-write/import")
@RequiredArgsConstructor
public class AiWriteImportController {

    private final MarkitdownClient markitdownClient;
    private final DocumentService documentService;
    private final StorageService storageService;

    @PostMapping("/file")
    public ResponseEntity<Map<String, Object>> importFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");

        try {
            // 1. 调用 markitdown
            String markdown = markitdownClient.convert(file.getBytes(), file.getOriginalFilename());

            // 2. 从 markdown 提取标题(取第一行 H1 或文件名)
            String title = extractTitle(markdown, file.getOriginalFilename());

            // 3. 创建文档记录
            Document doc = documentService.createDocument(
                CreateDocumentRequest.builder()
                    .docType("markdown")
                    .title(title)
                    .build(),
                userId);

            // 4. 写入磁盘
            storageService.store(doc.getFilePath(), markdown.getBytes(StandardCharsets.UTF_8));

            // 5. 更新 hash / size
            doc.setFileHash(FileHashUtil.calculateSHA256(markdown.getBytes(StandardCharsets.UTF_8)));
            doc.setFileSize((long) markdown.getBytes(StandardCharsets.UTF_8).length);
            documentService.updateDocument(doc);

            // 6. 返回
            return ResponseEntity.ok(Map.of(
                "docId", doc.getId(),
                "docKey", doc.getDocKey(),
                "title", doc.getTitle(),
                "size", markdown.length(),
                "preview", markdown.substring(0, Math.min(500, markdown.length()))
            ));
        } catch (Exception e) {
            log.error("markitdown 转换失败", e);
            throw new BusinessException("文件转换失败: " + e.getMessage());
        }
    }

    private String extractTitle(String markdown, String filename) {
        // 优先取第一行 H1
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^#\\s+(.+)$", java.util.regex.MutilINE);
        java.util.regex.Matcher m = p.matcher(markdown);
        if (m.find()) return m.group(1).trim();
        // 否则去后缀当标题
        return filename.replaceAll("\\.[^.]+$", "");
    }
}
```

**application.yml 新增配置**:
```yaml
markitdown:
  url: http://markitdown:5003
```

#### miaotongdoc-ai-write(新增前端组件)

```
miaotongdoc-ai-write/src/components/
├── ImportFileButton.tsx      # 新增
└── ImportDropZone.tsx        # 可选,拖拽上传

miaotongdoc-ai-write/src/api/
└── ai-write.ts               # 在已有基础上加 importFile 方法
```

**ImportFileButton.tsx** 草稿:
```tsx
import { useState } from 'react'
import { Upload, message } from 'antd'
import { aiWriteApi } from '@/api/ai-write'

export function ImportFileButton() {
  const [loading, setLoading] = useState(false)

  const handleUpload = async (file: File) => {
    setLoading(true)
    try {
      const result = await aiWriteApi.importFile(file)
      message.success(`已导入: ${result.title}`)
      // 整页跳到 Syllepsis 编辑器
      window.location.href = `/ai-write/editor/${result.docKey}`
    } catch (e: any) {
      message.error(`导入失败: ${e.message}`)
    } finally {
      setLoading(false)
    }
    return false  // 阻止 antd 默认上传
  }

  return (
    <Upload accept=".pdf,.docx,.pptx,.xlsx,.jpg,.jpeg,.png,.mp3,.wav,.html,.csv,.json,.xml"
            beforeUpload={handleUpload}
            showUploadList={false}>
      <button disabled={loading}>
        {loading ? '转换中...' : '导入文件转 Markdown'}
      </button>
    </Upload>
  )
}
```

**api/ai-write.ts 增补方法**:
```ts
export const aiWriteApi = {
  // ... 已有方法

  /** 通过 markitdown 导入文件,返回新建的 markdown 文档 */
  importFile(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<any, { docId: number; docKey: string; title: string; size: number; preview: string }>(
      '/ai-write/import/file', formData,
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000 }
    )
  }
}
```

#### docker-compose.yml 改动

```yaml
markitdown:
  build: ./app/markitdown
  container_name: miaotongdoc-markitdown
  ports:
    - "5003:5003"
  environment:
    - TZ=Asia/Shanghai
  volumes:
    - ./data/markitdown-cache:/tmp/markitdown
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:5003/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
  networks:
    mtd-net:
      ipv4_address: 172.20.0.100
  restart: unless-stopped
```

#### deploy.sh 改动

```bash
# 第 55 行 mkdir -p data/{...} 加 markitdown-cache
- mkdir -p data/{documents,pgdata,minio,rabbitmq,editor,editor-cache}
+ mkdir -p data/{documents,pgdata,minio,rabbitmq,editor,editor-cache,markitdown-cache}
```

### §13.4 markitdown 支持的文件格式(实测)

| 格式 | 后缀 | markitdown 支持 | 备注 |
|---|---|---|---|
| PDF | .pdf | ✅ | 含图片 OCR,需 `[pdf]` 依赖 |
| Word | .docx | ✅ | 含表格 |
| PowerPoint | .pptx | ✅ | 每页转换 |
| Excel | .xlsx | ✅ | 每个 sheet 一个表格 |
| 图片 | .jpg .png | ✅ | 含 EXIF + OCR(需 Tesseract)|
| 音频 | .mp3 .wav | ✅ | 含语音转录(需 Whisper)|
| HTML | .html | ✅ | 含 Wikipedia 特殊处理 |
| CSV | .csv | ✅ | 表格化 |
| JSON | .json | ✅ | |
| XML | .xml | ✅ | |
| ZIP | .zip | ✅ | 遍历 |
| EPub | .epub | ✅ | |

**安装大小**:`pip install 'markitdown[pdf,docx,pptx,xlsx]'` ≈ 200MB(基础 + PDF 解析)
**全装**:`pip install 'markitdown[all]'` ≈ 1GB(包含 OCR/Whisper)
**建议**:生产装 `[pdf,docx,pptx,xlsx]` 子集,OCR/Whisper 视情况单独装。

### §13.5 工作量与里程碑

| 子任务 | 估时 |
|---|---|
| markitdown 服务容器(Dockerfile + compose + deploy.sh)| 1.5 d |
| FastAPI wrapper(server.py + health + 错误处理) | 1 d |
| 后端 `MarkitdownClient` + `AiWriteImportController` | 2 d |
| application.yml + 测试覆盖(PDF / Word / PPT / Excel / 图片) | 2 d |
| 前端 `ImportFileButton` + antd Upload + 跳转 | 1 d |
| 与 Syllepsis 编辑器集成测试(导入 → 编辑 → 保存) | 1.5 d |
| **合计** | **9 d** |

**v2.3 → v2.4 总工作量:68 d → 77 d**

### §13.6 风险与对策

| 风险 | 严重性 | 对策 |
|---|---|---|
| **Python 异构,需要 HTTP 桥接** | 中 | FastAPI wrapper 起标准 HTTP,Java WebClient 调用;**隔离性 > 直接调用 Py4J** |
| **markitdown 转换慢**(PDF 几十秒,音频分钟级)| 高 | 前端 180s timeout + 后端连接池 + 进度条(后续可改异步 job)|
| **OCR / Whisper 装包大且慢** | 中 | **本期不装** `[all]`,只装 `[pdf,docx,pptx,xlsx]`;OCR 留给二期 |
| **PDF 表格识别质量一般** | 中 | 表格复杂场景仍走 Docling;markitdown 仅做"够用"转换 |
| **markdown 中 base64 图片**(很大)| 中 | Syllepsis MarkdownIO 提取 base64 图片转 MinIO 上传 → URL(后续 P2)|
| **markitdown 镜像更新慢** | 低 | 锁版本 `markitdown==0.0.1`;季度升级 |
| **markitdown 不支持复杂 docx 公式** | 低 | OMath 公式 markitdown 输出 LaTeX 文本块;Syllepsis KaTeX 可渲染(若启用)|

### §13.7 验收清单

阶段 9 完成后,逐项验证:

- [ ] `docker compose up markitdown` 健康检查通过
- [ ] `curl -X POST http://localhost:5003/convert -F "file=@test.pdf"` 返回 markdown
- [ ] AI 写作模块 / 首页点击"导入文件转 Markdown",上传 1MB PDF
- [ ] 30 秒内跳转到 `/ai-write/editor/{docKey}`
- [ ] Syllepsis 编辑器加载该 markdown,内容含标题、段落、表格
- [ ] 用户继续修改并保存,主站 MarkdownEditor.vue 也能打开同一文档
- [ ] AI 浮窗对导入的段落做扩写,流式正常
- [ ] audit_log 中 `import_file` 事件记录成功

### §13.8 不在本期

| 不做 | 原因 |
|---|---|
| OCR 图片转文字 | 装包大、慢,本期不装 `[all]`;用户手动描述图片或上 OCR 单独服务 |
| 音频转录 | 同上,Whisper 单独服务 |
| **PDF 公式 → KaTeX** | markitdown 默认 OMath → LaTeX 文本;Syllepsis KaTeX 渲染是 P2 功能,本期内 OMath 内容存为文本占位 |
| **base64 图片 → MinIO** | 本期 markdown 含 base64 内联图片可显示但体积大;转 MinIO URL 是 P2 |
| 异步 job 表 | markitdown 同步 HTTP 调用已够用;失败可重试 |

---

*v2.4 在 v2.3 基础上新增 §13 markitdown 集成,工作量从 68 d → 77 d。本节与 §0–§12 一致;实施时优先阶段 0–8(v2.3 范围),markitdown 作为阶段 9 P1 增量。*
