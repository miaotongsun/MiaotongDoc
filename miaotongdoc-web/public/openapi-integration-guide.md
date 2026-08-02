# MiaotongDoc 对外服务 API 集成指南

> **版本**: v1 | **鉴权**: API Key | **速率限制**: 默认 60 次/分钟/Key
> **适用对象**: 需要对接 MiaotongDoc 的外部系统（HR、ERP、OA 等）

---

## 目录

- [快速开始](#快速开始)
- [鉴权方式](#鉴权方式)
- [错误码说明](#错误码说明)
- [接口清单](#接口清单)
- [API 详情](#api-详情)
  - [1. 健康检查](#1-健康检查)
  - [2. 创建用户](#2-创建用户)
  - [3. 创建部门](#3-创建部门)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## 快速开始

### 第 1 步：获取 API Key

管理员登录系统后台 →「系统集成」→「颁发新 Key」，填写用途后获得密钥：

```
密钥: ak_FwZcTMGiGCHyUD9suG1mY4yBElb8fbuF
```

> ⚠️ **密钥明文仅展示一次**，请立即复制保存到外部系统的配置中。

### 第 2 步：测试连通性

```bash
curl -s https://your-domain.com/api/open/v1/health \
  -H "X-API-Key: ak_FwZcTMGiGCHyUD9suG1mY4yBElb8fbuF"
```

成功返回：
```json
{"status":"ok","version":"v1","timestamp":1785642199000}
```

### 第 3 步：调用业务接口

```bash
# 创建用户示例
curl -s -X POST https://your-domain.com/api/open/v1/users \
  -H "X-API-Key: ak_FwZcTMGiGCHyUD9suG1mY4yBElb8fbuF" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "20001",
    "username": "zhangsan",
    "realName": "张三",
    "email": "zhangsan@company.com",
    "phone": "13800138000",
    "departmentCode": "HR"
  }'
```

---

## 鉴权方式

### 请求头

| 头 | 必填 | 说明 |
|---|---|---|
| `X-API-Key` | ✅ | 颁发时获得的密钥，格式 `ak_xxxxxxxx...` |
| `Content-Type` | ✅ | 固定为 `application/json` |
| `Idempotency-Key` | ❌ | 幂等键（可选），用于防止重复创建 |

### 幂等性（可选）

POST 请求支持幂等性。传入 `Idempotency-Key` 头：

```bash
curl -s -X POST https://your-domain.com/api/open/v1/users \
  -H "X-API-Key: ak_xxx" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-req-id-001" \
  -d '{"employeeId":"20001","username":"zhangsan","realName":"张三"}'
```

- 同一 Key + 同一 `Idempotency-Key` 在 24 小时内重复请求**返回首次成功响应**
- 幂等键由调用方自主生成（建议 UUID）

### 响应头

每次响应都会包含 `X-Request-Id`，用于问题排查：

```bash
X-Request-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

---

## 错误码说明

### HTTP 状态码与业务码对照

| HTTP 状态码 | 业务码 | 含义 |
|---|---|---|
| 401 | 40101 | 缺少 `X-API-Key` 请求头 |
| 401 | 40102 | API Key 无效、已吊销或已过期 |
| 403 | 40301 | 客户端 IP 不在白名单内 |
| 429 | 42901 | 请求过于频繁，超过限流阈值 |
| 400 | 400xx | 参数校验失败（如必填字段缺失） |
| 500 | 50001 | 服务内部错误 |

### 错误响应格式

```json
{
  "code": 40102,
  "message": "API Key 无效、已吊销或已过期",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### 常见错误排查

| 错误 | 原因 | 解决 |
|---|---|---|
| 40101 | 未传 `X-API-Key` | 检查请求头是否携带 |
| 40102 | Key 无效/已吊销/已过期 | 确认 Key 状态，重新颁发 |
| 40301 | IP 不在白名单 | 管理员在颁发 Key 时添加调用方 IP |
| 42901 | 超过限流阈值 | 降低调用频率，或申请提高限流 |
| 400 | 参数错误 | 查看 message 字段提示具体缺失字段 |

---

## 接口清单

| 方法 | 路径 | 用途 | 幂等 |
|---|---|---|---|
| `GET` | `/api/open/v1/health` | 健康检查 | — |
| `POST` | `/api/open/v1/users` | 创建用户 | ✅ |
| `POST` | `/api/open/v1/departments` | 创建部门 | ✅ |
| `GET` | `/api/open/v1/documents` | 文档列表（分页） | — |
| `GET` | `/api/open/v1/documents/{id}` | 文档详情 | — |
| `GET` | `/api/open/v1/documents/{id}/file` | 下载文档文件 | — |
| `GET` | `/api/open/v1/documents/{id}/sheet-data` | 读取 xlsx 结构化数据（按行列） | — |
| `POST` | `/api/open/v1/documents/upload` | 上传文档 | — |

> 基础路径: `https://your-domain.com`（由部署方提供）

---

## API 详情

### 1. 健康检查

对外系统探活，确认服务可达。

**请求**

```bash
GET /api/open/v1/health
Host: your-domain.com
X-API-Key: ak_xxx
```

**响应 200**

```json
{
  "status": "ok",
  "version": "v1",
  "timestamp": 1785642199000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | `"ok"` 表示服务正常 |
| `version` | string | API 版本号 `"v1"` |
| `timestamp` | long | 服务端时间戳（毫秒） |

---

### 2. 创建用户

在 MiaotongDoc 中创建新用户账号。

**请求**

```bash
POST /api/open/v1/users
Host: your-domain.com
X-API-Key: ak_xxx
Content-Type: application/json
```

**请求体**

```json
{
  "employeeId": "20001",
  "username": "zhangsan",
  "realName": "张三",
  "password": "自定义密码",
  "email": "zhangsan@company.com",
  "phone": "13800138000",
  "position": "软件工程师",
  "role": "user",
  "departmentCode": "HR"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `employeeId` | string | ✅ | — | 工号，最长 8 位 |
| `username` | string | ✅ | — | 登录用户名，最长 50 位 |
| `realName` | string | ✅ | — | 真实姓名 |
| `password` | string | ❌ | `"123456"` | 登录密码 |
| `email` | string | ❌ | null | 邮箱 |
| `phone` | string | ❌ | null | 手机号 |
| `position` | string | ❌ | null | 职位 |
| `role` | string | ❌ | `"user"` | 角色：`"user"` 或 `"admin"` |
| `departmentCode` | string | ❌ | null | 部门编码，必须已存在 |

**响应 200**

```json
{
  "id": 42,
  "employeeId": "20001",
  "username": "zhangsan",
  "realName": "张三",
  "email": "zhangsan@company.com",
  "phone": "13800138000",
  "departmentId": 5,
  "position": "软件工程师",
  "role": "user",
  "isActive": true
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 用户 ID（系统内部） |
| `employeeId` | string | 工号 |
| `username` | string | 用户名 |
| `realName` | string | 姓名 |
| `email` | string | 邮箱 |
| `phone` | string | 手机号 |
| `departmentId` | number | 部门 ID |
| `position` | string | 职位 |
| `role` | string | 角色 |
| `isActive` | boolean | 是否启用 |

**响应 400（参数错误）**

```json
{
  "code": 400,
  "message": "工号不能为空",
  "requestId": "a1b2c3d4-..."
}
```

**注意**:
- `employeeId` 和 `username` 在系统中**唯一**，重复会返回 400
- `departmentCode` 必须对应系统中已存在的部门编码，否则返回 400
- 创建成功后密码自动加密，无法通过接口获取明文密码

---

### 3. 创建部门

在 MiaotongDoc 中创建新部门。

**请求**

```bash
POST /api/open/v1/departments
Host: your-domain.com
X-API-Key: ak_xxx
Content-Type: application/json
```

**请求体**

```json
{
  "code": "HR",
  "name": "人力资源部",
  "parentCode": null,
  "sortOrder": 0
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `code` | string | ✅ | — | 部门编码，最长 20 位，**唯一** |
| `name` | string | ✅ | — | 部门名称，最长 200 位 |
| `parentCode` | string | ❌ | null | 上级部门编码，必须已存在 |
| `sortOrder` | number | ❌ | 0 | 排序序号，同级内排序 |

**响应 200**

```json
{
  "id": 10,
  "code": "HR",
  "name": "人力资源部",
  "parentId": null,
  "level": 1,
  "path": "/HR",
  "sortOrder": 0,
  "isActive": true
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 部门 ID（系统内部） |
| `code` | string | 部门编码 |
| `name` | string | 部门名称 |
| `parentId` | number | 上级部门 ID |
| `level` | number | 层级（根部门为 1） |
| `path` | string | 路径 |
| `sortOrder` | number | 排序序号 |
| `isActive` | boolean | 是否启用 |

**响应 400（参数错误）**

```json
{
  "code": 400,
  "message": "部门编码不能为空",
  "requestId": "a1b2c3d4-..."
}
```

**注意**:
- `code` 在系统中**唯一**，重复会返回 400
- `parentCode` 传值时，对应的上级部门必须已存在
- 部门支持无限层级，通过 `parentCode` 指定上下级关系

### 4. 文档列表

查询文档列表，支持分页和按类型筛选。

**请求**

```bash
GET /api/open/v1/documents?type=pdf&keyword=报告&page=0&size=20
Host: your-domain.com
X-API-Key: ak_xxx
```

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | string | ❌ | 全部 | 文档类型：`pdf`/`word`/`cell`/`slide`/`markdown` |
| `keyword` | string | ❌ | 无 | 搜索关键词（标题/内容） |
| `page` | number | ❌ | `0` | 页码（从 0 开始） |
| `size` | number | ❌ | `20` | 每页数量 |

**响应 200**

```json
{
  "content": [
    {
      "id": 42,
      "title": "2024年度报告",
      "docType": "pdf",
      "fileType": "pdf",
      "fileSize": 2048576,
      "status": "draft",
      "isStarred": false,
      "createdAt": "2024-01-01T00:00:00",
      "updatedAt": "2024-06-01T00:00:00",
      "ownerUserId": 1
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

---

### 5. 文档详情

获取单个文档的详细信息。

**请求**

```bash
GET /api/open/v1/documents/{id}
Host: your-domain.com
X-API-Key: ak_xxx
```

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | ✅ | 文档 ID（从文档列表接口获取） |

**响应 200**

```json
{
  "id": 42,
  "title": "2024年度报告",
  "docType": "pdf",
  "fileType": "pdf",
  "fileSize": 2048576,
  "status": "draft",
  "isStarred": false,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-06-01T00:00:00",
  "ownerUserId": 1,
  "docKey": "uuid-string",
  "fileHash": "sha256-hash",
  "currentVersion": 3,
  "folderId": null,
  "departmentId": 5,
  "signingLocked": false
}
```

---

### 6. 下载文档文件

下载文档的原始文件。

**请求**

```bash
GET /api/open/v1/documents/{id}/file
Host: your-domain.com
X-API-Key: ak_xxx
```

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | ✅ | 文档 ID |

**响应 200**: 返回文件二进制流，`Content-Type: application/octet-stream`，`Content-Disposition: attachment; filename="文档标题.扩展名"`

```bash
# 保存为文件
curl -s -o report.pdf https://your-domain.com/api/open/v1/documents/42/file \
  -H "X-API-Key: ak_xxx"
```

---

### 7. 上传文档

上传文件到系统，创建新文档。

**请求**

```bash
POST /api/open/v1/documents/upload
Host: your-domain.com
X-API-Key: ak_xxx
Content-Type: multipart/form-data
```

**表单参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | ✅ | 上传的文件（支持 docx/xlsx/pptx/md/pdf） |

**支持的文件格式**: `.docx`(Word)、`.xlsx`(Excel)、`.pptx`(PPT)、`.md`(Markdown)、`.pdf`(PDF)

**响应 200**

```json
{
  "id": 43,
  "title": "报告文档",
  "docType": "word",
  "fileType": "docx",
  "fileSize": 51200,
  "status": "draft",
  "isStarred": false,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00",
  "ownerUserId": 1,
  "docKey": "uuid-string",
  "fileHash": "sha256-hash",
  "currentVersion": 1,
  "folderId": null,
  "departmentId": null,
  "signingLocked": false
}
```

### 8. 读取 xlsx 结构化数据

将 Excel 文档的内容按行列解析为 JSON 结构化数据，外部系统无需安装任何 Excel 解析库即可直接读取单元格内容。

**请求**

```bash
GET /api/open/v1/documents/{id}/sheet-data
Host: your-domain.com
X-API-Key: ak_xxx
```

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | ✅ | 文档 ID（必须是 xlsx 类型文档） |

**响应 200**

```json
{
  "documentId": 42,
  "title": "员工信息表.xlsx",
  "fileType": "xlsx",
  "totalSheets": 2,
  "sheets": [
    {
      "name": "在职员工",
      "rowCount": 101,
      "columnCount": 5,
      "headers": ["工号", "姓名", "部门", "职位", "入职日期"],
      "rows": [
        {
          "rowNum": 2,
          "cells": ["10001", "张三", "技术部", "高级工程师", "2020-03-15"]
        },
        {
          "rowNum": 3,
          "cells": ["10002", "李四", "市场部", "市场经理", "2021-06-01"]
        }
      ]
    },
    {
      "name": "离职员工",
      "rowCount": 10,
      "columnCount": 5,
      "headers": ["工号", "姓名", "部门", "职位", "离职日期"],
      "rows": [
        {
          "rowNum": 2,
          "cells": ["20001", "王五", "财务部", "会计", "2023-12-31"]
        }
      ]
    }
  ]
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|---|---|---|
| `documentId` | number | 文档 ID |
| `title` | string | 文档标题 |
| `fileType` | string | 文件类型（xlsx） |
| `totalSheets` | number | 工作表数量 |
| `sheets[].name` | string | 工作表名称 |
| `sheets[].rowCount` | number | 数据行数（含表头） |
| `sheets[].columnCount` | number | 列数 |
| `sheets[].headers` | array | 列标题（第一行） |
| `sheets[].rows[].rowNum` | number | 行号（Excel 中的行号） |
| `sheets[].rows[].cells` | array | 单元格值列表（按列顺序，与 headers 对应） |

**注意**:
- 仅支持 `.xlsx` 格式文档（`docType=cell`）
- 每个工作表最多返回 1000 行数据（从第二行开始）
- 单元格值已格式化为字符串（日期、数字均为文本格式）
- 空单元格返回空字符串

---

## 最佳实践

### 1. 连接池管理

```java
// Java HttpClient 示例 - 复用连接池
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// 构建请求
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://your-domain.com/api/open/v1/users"))
    .header("X-API-Key", "ak_xxx")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    .build();

// 发送
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

### 2. 错误重试策略

```python
# Python 示例 - 带退避的重试
import time, requests

def call_openapi(method, path, api_key, body=None, max_retries=3):
    url = f"https://your-domain.com{path}"
    headers = {"X-API-Key": api_key, "Content-Type": "application/json"}

    for attempt in range(max_retries):
        resp = requests.request(method, url, headers=headers, json=body)

        if resp.status_code == 429:  # 限流
            wait = 2 ** attempt  # 指数退避
            time.sleep(wait)
            continue

        if resp.status_code >= 500:  # 服务端错误
            wait = 2 ** attempt
            time.sleep(wait)
            continue

        return resp.json()

    raise Exception("重试耗尽")
```

### 3. 幂等键使用

```python
import uuid

def create_user_safe(api_key, user_data):
    idempotency_key = str(uuid.uuid4())
    headers = {
        "X-API-Key": api_key,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotency_key
    }
    resp = requests.post(
        "https://your-domain.com/api/open/v1/users",
        headers=headers,
        json=user_data
    )
    return resp.json()
```

### 4. 批量同步方案

建议先同步部门，再同步用户：

1. **批量创建部门** → 获得部门 ID 映射
2. **批量创建用户** → 关联 `departmentCode`

```python
# 先同步部门
for dept in departments:
    call_openapi("POST", "/api/open/v1/departments", api_key, body={
        "code": dept["code"],
        "name": dept["name"],
        "parentCode": dept.get("parentCode")
    })

# 再同步用户
for user in users:
    call_openapi("POST", "/api/open/v1/users", api_key, body={
        "employeeId": user["id"],
        "username": user["username"],
        "realName": user["realName"],
        "departmentCode": user.get("deptCode")
    })
```

---

## 常见问题

### Q: 如何获取 API Key？

A: 管理员登录系统 → 后台管理 →「系统集成」→「颁发新 Key」。Key 明文仅展示一次，务必立即保存。

### Q: Key 泄露了怎么办？

A: 管理员在后台吊销泄露的 Key 并颁发新 Key。吊销后原 Key 立即失效。建议将 Key 存储在环境变量或密钥管理服务中，**不要硬编码在代码里**。

### Q: 调用频率限制是多少？

A: 默认每个 Key 每分钟 60 次。如需提高，管理员在颁发 Key 时可设置 `rateLimit` 参数。

### Q: 响应中的 `X-Request-Id` 有什么用？

A: 每个请求唯一标识，联系技术支持时提供此 ID 可快速定位问题。

### Q: 如何查询已创建的用户或部门？

A: 当前版本暂未提供对外查询接口。如需查询，请使用管理员账号登录系统 Web 界面。

### Q: 接口 API 版本如何演进？

A: 路径使用 `/api/open/v1/` 版本前缀。主版本升级时路径变为 `/api/open/v2/`，v1 会保持兼容至少 6 个月。

---

## 附录：管理员接口

以下接口仅供管理员在系统后台操作，**不对外部系统开放**。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/admin/openapi/keys` | 查看所有 Key 列表（仅前缀） |
| POST | `/api/admin/openapi/keys` | 颁发新 Key |
| DELETE | `/api/admin/openapi/keys/{id}` | 吊销 Key |
| GET | `/api/admin/openapi/keys/{id}/reveal` | 查看 Key 明文（审计记录） |
| PUT | `/api/admin/openapi/keys/{id}/enable` | 启用 Key |
| PUT | `/api/admin/openapi/keys/{id}/disable` | 禁用 Key |
| DELETE | `/api/admin/openapi/keys/{id}/hard` | 硬删除 Key |