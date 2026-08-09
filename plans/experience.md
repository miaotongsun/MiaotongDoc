# 经验汇总 (experience.md)

> 跨任务复用的踩坑/经验沉淀。每条按"问题 → 解决 → 适用场景"格式记录。

---

## 🧪 Playwright E2E 踩坑

### 1. Element Plus el-select 隐藏测量副本 + 远程搜索时序
**问题**: `.el-select-dropdown__item` 选择器会同时命中"隐藏的测量副本"和"可见的可见项",且远程搜索时 800ms 等待不足以覆盖 debounce(300ms) + fetch 渲染。

**解决**:
```js
// ✅ 正确写法
const items = page.locator('.el-select-dropdown__item:visible')   // 加 :visible 过滤
const target = page.locator('.el-select-dropdown__item:visible').filter({ hasText }).first()
await input.pressSequentially(keyword, { delay: 30 })
await page.waitForTimeout(1500)   // debounce + fetch 渲染
await target.click()
await page.waitForTimeout(500)
```

**适用场景**: 所有 Element Plus `el-select` (filterable + remote) 的 Playwright 自动化。
**首次记录**: 2026-08-08 合同 E2E (tests/contract-e2e.mjs)

---

## ⚠️ 提交/部署纪律

### 1. `docker compose restart` 不会加载新 jar
**问题**: 仅 `restart` 容器不会自动重新挂载 `app/server/*.jar`,必须 `cp` 新 jar **之后**再 restart。
**顺序**:
```bash
cp target/miaotongdoc.jar MiaotongDoc-Docker/app/server/
cp -r dist/* MiaotongDoc-Docker/app/web/dist/    # 注意先 rm -rf dist/*
docker compose restart web-server nginx
```
**首次记录**: 2026-08-09 合同管理内容识别重塑