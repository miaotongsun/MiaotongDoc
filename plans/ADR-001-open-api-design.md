# ADR-001: 对外服务 API 规范设计

> **状态**: 已接受
> **日期**: 2026-07-27
> **决策者**: 项目架构组

## 背景

MiaotongDoc 系统需要向外部系统（HR、CRM、OA 等）提供接口，用于：
- 外部系统同步用户/部门数据到 MiaotongDoc
- 外部系统批量导入用户/部门

当前所有 `/api/**` 接口都基于应用 JWT 鉴权，无法直接供外部系统调用。需要一套独立的对外 API 体系。

## 决策

### 鉴权方案：多 Key API Key 管理

| 方案 | OAuth2 + Client Credentials | **API Key（多 Key 管理）** |
|------|------------------------------|-----------------------------|
| 复杂度 | 高（需 token 刷新、密钥管理） | **低** |
| 外部系统集成成本 | 高 | **低** |
| 适合场景 | 面向用户的开放平台 | **后端系统集成** ✅ |
| 吊销粒度 | token 级别 | **Key 级别** ✅ |
| 实施成本 | 1-2 周 | **1 天** ✅ |

**选择 API Key 的理由**：
1. 主要场景是后端系统集成，非用户开放平台
2. 每个外部系统颁发独立 Key，便于审计和吊销
3. 简单可靠，无需复杂的 token 刷新机制
4. 数据库存明文是合理的（API Key 不同于密码，需要每次明文比对）

### Key 存储：数据库表 `sys_openapi_key`

| 字段 | 用途 |
|------|------|
| `access_key` | 明文，每次请求匹配 |
| `secret_prefix` | 前 8 位，列表展示用 |
| `enabled` / `revoked_at` / `expires_at` | 三重生效状态判断 |
| `rate_limit_per_minute` | 单 Key 限流 |
| `allowed_ips` | IP 白名单兜底 |
| `last_used_at` | 审计/排查 |

**颁发流程**：
1. 管理员在 Admin → 系统集成 tab 点击"颁发新 Key"
2. 填写用途、外部系统、过期时间、IP 白名单、限流
3. 后端生成 `ak_<32位随机字符>` 明文密钥
4. **明文仅返回一次**（前端弹窗强制用户复制保存）
5. 数据库存明文 + 前缀
6. 列表只显示前缀，不再返回明文

**吊销流程**：软删除（设置 `enabled=false` 和 `revoked_at`），立即生效。

### 接口路径：`/api/open/v1/`

- 当前版本：`v1`
- URL 包含版本号，未来不兼容变更走 `/v2`
- 老版本至少维护 6 个月

### 鉴权 Header

| Header | 必填 | 用途 |
|--------|------|------|
| `X-API-Key` | ✅ | API Key 明文 |
| `Idempotency-Key` | 可选 | 幂等性（POST/PUT），同 Key 24h 内返回首次结果 |
| `X-Request-Id` | 自动 | 服务端生成，返回给客户端 |

### 错误码体系

```
200    成功
4xxxx  客户端错误（参数/业务）
5xxxx  服务端错误
40101  API Key 缺失
40102  API Key 无效/已吊销/已过期
42901  触发限流
```

### 限流策略

- Redis 计数器：`openapi:rate:{keyId}`，TTL 60s
- 超限返回 `42901`
- Redis 不可用时降级放行（不阻塞正常请求）

### 幂等性

- POST/PUT 请求可传 `Idempotency-Key` 头
- Redis 缓存 key：`openapi:idem:{keyId}:{idemKey}`
- TTL：24h
- 命中缓存时直接返回缓存响应，不进入业务逻辑

### 安全设计

1. **HTTPS 强制**（生产环境）
2. **IP 白名单**（兜底，密钥泄漏时仍能阻断）
3. **异常使用模式告警**：同 Key 1 分钟内 100 次失败 → 审计日志告警
4. **启动自检**：无可用 Key 时记录 WARN；即将过期（7 天内）记录 WARN
5. **requestId 全链路追踪**：所有调用记录审计日志

## 不选择的方案

### OAuth2 + Client Credentials

- 优点：标准化、token 短期有效更安全
- 缺点：复杂度高、外部系统集成需要先获取 token、外部系统管理 token 生命周期
- 不选原因：后端集成场景复杂度与价值不匹配

### 全局单一 API Key

- 优点：实现简单
- 缺点：无法区分外部系统、无法独立吊销某个系统、审计困难
- 不选原因：多 Key 管理的成本远低于其收益

## 实施范围

本次实施：
- ✅ `/api/open/v1/users` 创建用户
- ✅ `/api/open/v1/departments` 创建部门
- ✅ `/api/open/v1/health` 健康检查
- ✅ 管理员 Key 管理 UI

后续扩展（按需）：
- `PUT /api/open/v1/users/{id}` 更新用户
- `POST /api/open/v1/users/batch` 批量创建
- `DELETE /api/open/v1/users/{id}` 停用用户

## 兼容性

- 现有 `/api/admin/users` 和 `/api/auth/*` 接口不变
- 新增 `/api/open/**` 是独立命名空间

## 验证

详见 [Plan](../plans/2026-07-27-open-api-design.md) 的"验证方案"章节。

## 参考

- [Stripe API Authentication](https://stripe.com/docs/api/authentication)
- [GitHub API Authentication](https://docs.github.com/en/rest/authentication)
- [AWS Access Key Best Practices](https://docs.aws.amazon.com/general/latest/gr/aws-access-keys-best-practices.html)