# 功能配置（Feature Flags + 业务角色 + 三轴授权）

> **状态**: 规划中
> **创建日期**: 2026-08-12
> **范围**: 后端(新表 V27 + 拦截器 + 注解) + 前端(管理后台新 Tab + 路由守卫 + 菜单 store)
> **复杂度**: 🔴 复杂(数据库迁移 + 多模块改动 + 三重防护 + 缓存策略)
> **关联代码**: mt_feature_flag / mt_business_role / mt_feature_grant(NEW) + Admin.vue + Vue Router + 各业务 Controller

---

## 📊 状态摘要

**进度**: ░░░░░░░░░░ 0% (0/8 步完成)
**验证**: ⏳ 未开始
**最近变更**: 2026-08-12 — 创建计划

| 维度 | 状态 |
|---|---|
| 实现步骤 | 0/8 |
| 验证项 | 0/Y ✅ |
| 临时需求 | 0（已合并 0 / 待评估 0）|
| 经验沉淀 | 0 条 |

---

## 一、Context — 现状

### 现状摘要

- 管理后台 `Admin.vue` 已有 8 个一级 Tab（用户管理 / 部门管理 / 操作日志 / 水印配置 / 文档模板 / AI 配置 / 系统集成 / 文件夹模板），都是平级展示，没有"功能开关"维度
- 现有权限模型有 3 层：admin role → 文档所有者 → `mt_document_share` 4级权限（view/comment/edit/admin）。**没有模块级开关**——只能整库开或整库关某个 Controller
- `mt_watermark_config` 是单条配置的范例（单行表 + 所有人可见），但与"多开关 + 多例外授权"模型不一致
- `JwtAuthFilter` 已从 token 解析 userId/employeeId/role；`SecurityConfig` 配置了 CORS、JWT 校验、Admin 路径保护
- 现有注解 `@PreAuthorize` 已在使用（部分 Controller 上），但仅做角色判断，不能做功能判断

### 用户原始反馈

| # | 反馈 | 状态 |
|---|------|------|
| P1 | 管理后台加"首页功能开启/关闭"页签，可开放给特定人员（如合同管理） | 📋 待实施 |
| P2 | 授权粒度：可创建不同角色 + 按部门选用户 | 📋 待实施 |
| P3 | 拦截策略：三重防护（菜单 / 路由 / API） | 📋 待实施 |
| P4 | 功能范围：能加的都加上（合同 / 签署 / 模板 / 共享 / 评论 / PDF / AI / 审计 / 水印 / SSO / 管理后台） | 📋 待实施 |

---

## 二、整体策略

> 单阶段实施，但分 4 个交付批次落地（每批独立可测）。

```
批次 A（DB + 后端基础）→ 批次 B（后端拦截器 + 注解）→ 批次 C（前端基础 + 菜单 + 路由）→ 批次 D（管理后台 UI + E2E）
       ↓                       ↓                          ↓                              ↓
  V27 迁移 + 4 张表          FeatureGuard 拦截器           Pinia store + Router          FeatureConfig.vue
  + 基础 CRUD API           + @RequiresFeature            beforeEach 守卫              + 业务角色管理 UI
```

**当前进度**：批次 A 准备中

---

## 三、需求

管理后台新增"功能配置"Tab，让管理员通过 Web 界面控制 12 个核心功能的可见性与访问权，支持三轴授权（业务角色 / 部门 / 用户）+ 三重防护（菜单 / 路由 / API）。

## 四、目标

- 12 个功能开关可在管理后台可视化配置（启用/禁用 + 例外授权）
- 三轴授权查询在 50ms 内返回（缓存命中）
- 未授权用户：菜单不显示 / 路由跳 403 / API 返 403
- 配置变更后 ≤30 秒全端生效（前端轮询或事件总线）
- 现有 V1-V26 Flyway 迁移不受影响，新表独立

## 五、方案

### 5.1 功能清单（12 项）

| # | 编码 | 功能名 | 默认 | 主要 API 前缀 |
|---|---|---|---|---|
| 1 | `contract` | 合同管理 | 启用 | `/api/contracts` |
| 2 | `signing` | 签署任务 | 启用 | `/api/signing` |
| 3 | `doc_template` | 文档模板 | 启用 | `/api/templates` |
| 4 | `folder_template` | 文件夹模板 | 启用 | `/api/folder-templates` |
| 5 | `comment` | 评论协作 | 启用 | `/api/comments` |
| 6 | `share` | 文档共享 | 启用 | `/api/shares` |
| 7 | `pdf_editor` | PDF 编辑器 | 启用 | `/api/pdf` |
| 8 | `ai_assist` | AI 助手 | 启用 | `/api/ai/*`, `/api/documents/{id}/ai/*` |
| 9 | `audit_log` | 操作日志查看 | 启用 | `/api/audit` |
| 10 | `watermark` | 水印配置 | 启用 | `/api/watermark` |
| 11 | `sso` | SSO 单点登录 | 启用 | `/api/sso` |
| 12 | `admin_panel` | 管理后台 | 启用 | `/api/admin/**`（除基础项） |

### 5.2 数据库迁移（V27）

```sql
-- 功能开关主表（一行一个功能，12 行种子数据）
CREATE TABLE mt_feature_flag (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(50) UNIQUE NOT NULL,           -- contract / signing / ...
  name VARCHAR(100) NOT NULL,                  -- 合同管理
  description TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,      -- 总开关
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_by BIGINT,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_flag_code ON mt_feature_flag(code);

-- 业务角色（与 admin/user 平行，不冲突）
CREATE TABLE mt_business_role (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(50) UNIQUE NOT NULL,           -- contract_admin / ai_tester / ...
  name VARCHAR(100) NOT NULL,                  -- 合同管理员
  description TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 业务角色成员（N 个用户 = 1 个角色）
CREATE TABLE mt_business_role_member (
  id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_role_user UNIQUE (role_id, user_id),
  CONSTRAINT fk_brm_role FOREIGN KEY (role_id) REFERENCES mt_business_role(id) ON DELETE CASCADE,
  CONSTRAINT fk_brm_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_brm_role ON mt_business_role_member(role_id);
CREATE INDEX idx_brm_user ON mt_business_role_member(user_id);

-- 功能例外授权（三轴并集：role_id / dept_id / user_id 至少一个非空）
CREATE TABLE mt_feature_grant (
  id BIGSERIAL PRIMARY KEY,
  feature_code VARCHAR(50) NOT NULL,          -- 关联 mt_feature_flag.code，无 FK（解耦）
  grant_type VARCHAR(20) NOT NULL,            -- ROLE / DEPT / USER
  role_id BIGINT,                             -- grant_type=ROLE 时非空
  dept_id BIGINT,                             -- grant_type=DEPT 时非空（含子部门）
  user_id BIGINT,                             -- grant_type=USER 时非空
  expires_at TIMESTAMP,                       -- 可选到期时间
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_grant_type CHECK (grant_type IN ('ROLE','DEPT','USER')),
  CONSTRAINT chk_grant_axis CHECK (
    (grant_type='ROLE' AND role_id IS NOT NULL) OR
    (grant_type='DEPT' AND dept_id IS NOT NULL) OR
    (grant_type='USER' AND user_id IS NOT NULL)
  )
);
CREATE INDEX idx_grant_feature ON mt_feature_grant(feature_code);
CREATE INDEX idx_grant_role ON mt_feature_grant(role_id) WHERE role_id IS NOT NULL;
CREATE INDEX idx_grant_dept ON mt_feature_grant(dept_id) WHERE dept_id IS NOT NULL;
CREATE INDEX idx_grant_user ON mt_feature_grant(user_id) WHERE user_id IS NOT NULL;

-- V27 内附 12 行功能种子数据（INSERT INTO mt_feature_flag ...）
```

### 5.3 三轴授权判定（核心查询）

```java
// FeatureGuard.check(userId, featureCode) → boolean
// 1) feature_flag.enabled = true → 直接放行
// 2) 查询 mt_feature_grant：role 命中 / dept 命中（含子部门递归）/ user 命中 任一
// 3) 缓存 30s（Redis key: feat:{featureCode}:{userId}）
```

**部门含子部门** 用 `WITH RECURSIVE` 或一次性查出所有祖先部门 ID 集合。

### 5.4 三重防护实现路径

| 层级 | 位置 | 拦截点 | 实现 |
|---|---|---|---|
| **API** | 后端 | `FeatureGuardInterceptor` + `@RequiresFeature("code")` 注解 | Spring `WebMvcConfigurer.addInterceptors` 注册；Controller 方法上加注解；拦截器读注解 → 调 `FeatureGuard.check` |
| **路由** | 前端 | `Vue Router beforeEach` | 路由 meta 携带 `meta.requiresFeature: 'code'`；守卫内调 `useFeatureFlagStore().hasFeature(code)`；未通过 → `next('/403')` |
| **菜单** | 前端 | Pinia store + 侧栏渲染 | `featureFlagStore` 启动时 `GET /api/admin/features/me`；侧栏 v-for 时 `v-if="store.has(code)"` |

### 5.5 缓存策略

- **Redis 缓存 key**：`feat:{code}:{userId}`，value `1/0`，TTL **30s**
- **失效广播**：管理员修改授权后，`POST /api/admin/features/{code}/invalidate-cache` 主动 `DEL feat:{code}:*`
- **TTL 兜底**：即使失效广播失败，30s 后也会自动重建
- **管理后台列表本身**：不走用户缓存，每次实时查 DB（管理操作要看到当前真实状态）

### 5.6 方案对比

| 方案 | 优点 | 缺点 | 推荐 |
|---|---|---|---|
| A. 单一开关表 + JSON 字段存授权 | 一张表搞定 | JSON 查询慢，难建索引 | ❌ |
| B. 4 张表（flag + role + member + grant）| 规范化、索引清晰、易扩展 | 表数多 | ✅ |
| C. 用现有 `mt_audit_log` 做 ACL | 零迁移 | 语义错乱 | ❌ |

### 5.7 关键技术决策

- **决策 1**：业务角色独立于 admin/user 体系。理由：现有角色只有 admin/user 二元，不足以表达"合同管理员""AI 体验员"等业务身份；放平行新表不冲突。
- **决策 2**：`mt_feature_grant.feature_code` 不做 FK 关联 `mt_feature_flag`。理由：删功能开关不应级联删授权记录（审计需求），且方便灰度上线未注册到 flag 表的代码。
- **决策 3**：缓存粒度 `(featureCode, userId)`。理由：管理员修改只影响特定用户集合，全量失效代价大；30s TTL 兜底。
- **决策 4**：用拦截器 + 注解而非 AOP 切面。理由：注解语义更直白，与现有 `@PreAuthorize` 同模式；拦截器覆盖面更可控。
- **决策 5**：前端用 store 缓存而非每次拉接口。理由：路由跳转频繁，每次调 `/api/admin/features/me` 太重；30s 轮询 + 失效广播平衡体验与一致性。

---

## 六、涉及文件

### Critical Files（⭐）

- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/config/FeatureGuardConfig.java` - 新增（拦截器注册）
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/security/FeatureGuard.java` - 新增（核心判定）
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/security/RequiresFeature.java` - 新增（注解）
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/security/FeatureGuardInterceptor.java` - 新增（拦截器）
- ⭐ `miaotongdoc-web/src/stores/featureFlag.ts` - 新增（Pinia store）
- ⭐ `miaotongdoc-web/src/router/guards.ts` - 新增（路由守卫）
- ⭐ `miaotongdoc-web/src/views/admin/FeatureConfig.vue` - 新增（管理后台新 Tab）

### 后端

- `entity/FeatureFlag.java` - 新增
- `entity/BusinessRole.java` - 新增
- `entity/BusinessRoleMember.java` - 新增
- `entity/FeatureGrant.java` - 新增
- `repository/FeatureFlagRepository.java` - 新增
- `repository/BusinessRoleRepository.java` - 新增
- `repository/BusinessRoleMemberRepository.java` - 新增
- `repository/FeatureGrantRepository.java` - 新增
- `service/FeatureFlagService.java` - 新增
- `service/FeatureGuardService.java` - 新增（缓存 + 判定）
- `service/BusinessRoleService.java` - 新增
- `controller/FeatureFlagAdminController.java` - 新增（`/api/admin/features`）
- `controller/BusinessRoleAdminController.java` - 新增（`/api/admin/business-roles`）
- `controller/FeatureFlagMeController.java` - 新增（`/api/features/me`，前端轮询用）
- `dto/FeatureFlagDto.java` 等 DTO - 新增
- 现有 12 个功能对应 Controller（共 ~12 个文件） - 加 `@RequiresFeature` 注解

### 前端

- `api/featureFlag.ts` - 新增
- `api/businessRole.ts` - 新增
- `stores/featureFlag.ts` - 新增
- `router/index.ts` - 修改（加守卫导入 + 路由 meta 标注）
- `router/guards.ts` - 新增
- `views/Admin.vue` - 修改（加新 Tab）
- `views/admin/FeatureConfig.vue` - 新增
- `views/admin/BusinessRoleManager.vue` - 新增（业务角色管理）
- `components/Sidebar.vue` 等侧栏组件 - 修改（v-if 检查）
- `views/Home.vue` 首页卡片 - 修改（v-if 检查）

### 数据库

- `V27__feature_flags.sql` - 新增（4 张表 + 12 行种子）

---

## 七、实现步骤

### 阶段 1：DB + 后端基础（CRUD）

- [ ] 1.1 写 `V27__feature_flags.sql`（4 张表 + 12 行种子数据 + 必要索引）
- [ ] 1.2 创建 4 个 Entity 类 + Repository
- [ ] 1.3 创建 DTO（FeatureFlagDto、BusinessRoleDto、FeatureGrantDto）
- [ ] 1.4 创建 FeatureFlagService + BusinessRoleService + FeatureGrantService
- [ ] 1.5 创建 FeatureFlagAdminController + BusinessRoleAdminController
- [ ] 1.6 编译 + API 自测：`curl /api/admin/features` 返 12 行

### 阶段 2：后端拦截器 + 注解（三重防护之 API 层）

- [ ] 2.1 创建 `RequiresFeature` 注解 + `FeatureGuard` 核心类 + `FeatureGuardInterceptor`
- [ ] 2.2 创建 `FeatureGuardConfig`（WebMvcConfigurer 注册）
- [ ] 2.3 创建 `FeatureGuardService`（封装 Redis 缓存 + DB 查询）
- [ ] 2.4 给 12 个 Controller 的关键方法加 `@RequiresFeature("code")`
- [ ] 2.5 关闭 `contract` 功能，未授权用户访问 `/api/contracts` → 返 403

### 阶段 3：前端 store + 路由守卫（三重防护之菜单 + 路由层）

- [ ] 3.1 创建 `api/featureFlag.ts` + `api/businessRole.ts`
- [ ] 3.2 创建 Pinia store `featureFlag.ts`，含 `hasFeature(code)` 方法
- [ ] 3.3 创建 `/api/features/me` 后端接口（返当前用户所有授权功能列表）
- [ ] 3.4 创建 `router/guards.ts`（`beforeEach` 检查 meta.requiresFeature）
- [ ] 3.5 在 `router/index.ts` 给 ~20 个路由加 `meta.requiresFeature`
- [ ] 3.6 Sidebar.vue / Home.vue 加 v-if 检查
- [ ] 3.7 应用启动时调 `featureFlagStore.refresh()`

### 阶段 4：管理后台 UI

- [ ] 4.1 创建 `FeatureConfig.vue`（功能列表 + 开关 + 授权管理）
- [ ] 4.2 创建 `BusinessRoleManager.vue`（角色 CRUD + 成员管理）
- [ ] 4.3 Admin.vue 加 Tab
- [ ] 4.4 失效广播：`POST /api/admin/features/{code}/invalidate-cache`
- [ ] 4.5 操作审计：每次开关切换写 `mt_audit_log`

---

## 八、测试策略

### 单元测试

- `FeatureGuardTest`：管理员 / 普通用户 / 已授权用户 / 子部门成员 / 角色成员 — 5 种 case
- `RequiresFeatureAspectTest`：注解被命中 / 未命中
- `FeatureFlagServiceTest`：CRUD + 缓存失效

### 集成测试（curl + psql）

- `GET /api/admin/features` 返 12 行种子数据
- `POST /api/admin/features/contract/grant` 给用户 X 加授权 → 用户 X 调 `/api/contracts` 200
- `DELETE` 授权 → 用户 X 调 `/api/contracts` 403
- 关闭 `contract` 总开关 → 所有非授权用户 403

### 手动测试（UI）

- 管理后台 → 功能配置 → 关闭合同管理 → 普通用户菜单看不到"合同管理"
- 直接访问 `/contracts` 路由 → 跳 `/403`
- 调 `/api/contracts/list` → 403
- 给某用户授权 → 三层都恢复

### E2E（按 CLAUDE.md 铁律 6）

- 在 `tests/` 下新建 `tests/feature-flag-e2e.mjs`
- 场景：管理员开启/关闭 `contract` → 普通用户登录 → 检查菜单 / 路由跳转 / API 403

---

## 九、风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|---|---|---|---|---|
| 拦截器误伤现有接口 | 中 | 高 | 阶段 2 全量灰度，给 12 个 Controller 加注解时**逐步推进** + 每步 curl 验证 | 拦截器加白名单 `@RequiresFeature(skip=true)`；最坏情况 `application.yml` 关闭 `feature.guard.enabled` |
| Redis 缓存不一致 | 低 | 中 | TTL 30s 兜底 + 失效广播 | 关闭缓存走 DB |
| 部门含子部门查询慢 | 中 | 中 | `WITH RECURSIVE` + 索引；限制最大递归深度 10 | 改用"部门路径冗余字段"预计算 |
| 12 个 Controller 加注解改太多文件 | 中 | 低 | 用 `grep` 列出每个 Controller 的关键 endpoint，分批提交 | 关键 Controller（contract/ai/pdf）必加，其他可后续补 |
| 管理后台新 Tab 路由权限 | 低 | 低 | Tab 仅 admin 可见，由现有 `meta.requiresAdmin` 保证 | 关闭 `admin_panel` 总开关后，admin 自己看不到——属预期，需开白名单 |

---

## 十、验证标准

> 与 G5 对应，验证通过后在此勾选

- [ ] V27 迁移在干净库上执行成功，12 行种子数据落库
- [ ] `mvn package` 编译通过，无 P0/P1 警告
- [ ] `npm run build` 类型检查通过
- [ ] 三重防护手动测试 5 项 case 全通
- [ ] E2E 测试通过率 100%
- [ ] 缓存命中时 `FeatureGuard.check` 延迟 < 5ms
- [ ] 修改授权后 ≤30s 全端生效
- [ ] 现有功能无回归（合同 / 签署 / PDF 主流程各跑一次）

---

## 十一、待评估临时需求

| 时间 | 来源 | 内容 | 状态 |
|---|---|---|---|
| — | — | — | — |

---

## 十二、验证结果（待实施后填）

### API 验证（集成）

| 验证项 | 命令 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| — | — | — | — | — |

### UI 验证（手动）

| 验证项 | 操作 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| — | — | — | — | — |

### E2E 验证

| 验证项 | 命令 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| — | — | — | — | — |