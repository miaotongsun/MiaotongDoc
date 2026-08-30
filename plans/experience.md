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

### 2. MTOffice 编辑器 nginx 静默挂掉（无 healthcheck + 无守护）
**现象**: 浏览器访问 `/ds-vpath/` 返 502 Bad Gateway；editor 容器 `docker compose ps` 显示 unhealthy。
**根因**: OnlyOffice 容器内 nginx **不归 supervisord 管**（`/etc/supervisor/conf.d/` 下没有 `ds-nginx.conf`，只有 docs/converter/example/metrics），由 `run-document-server.sh` 一次性 `service nginx start` 拉起，**挂了没人拉**。
**诊断**:
```bash
podman exec miaotongdoc-editor ss -tlnp | grep :80   # 没人监听 = nginx 死了
podman exec miaotongdoc-editor service nginx start    # 临时恢复
```
**修复**: 编辑器 + web-server 都配 docker healthcheck（项目 8 个其他服务都配了，唯独这俩没配——架构缺口）：
```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q --spider http://localhost/healthcheck || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
```
**首次记录**: 2026-08-18 表格编辑器连接不上排查

### 3. nginx 路由策略不一致导致多人协同失败
**现象**: 多人协同编辑同一文档时，光标看不到其他人（HTTP 和 WebSocket 路由到不同 editor 实例）。
**根因**: nginx.conf 中两个 upstream 用**不同 hash 算法**：
```nginx
upstream editors {
    hash $doc_key$remote_addr consistent;  # HTTP 用 doc_key + IP
}
upstream editors_socketio {
    hash $remote_addr consistent;          # WebSocket 只用 IP
}
```
同一用户同一文档：HTTP 路由到 editor1，WebSocket 路由到 editor2/3 → **协同失败**。
**为什么之前没暴露**: editor ×3 经常 unhealthy（nginx 静默挂掉），实际工作的实例少（1-2 个），多人协同"凑巧"命中同一实例。修 healthcheck + 颠倒启动顺序后，3 个 editor 都健康，路由分散问题暴露。
**修复**: 两个 upstream 用相同的 hash 算法：
```nginx
upstream editors_socketio {
    hash $doc_key$remote_addr consistent;  # 跟 editors 一致
```
**修复**: 改完 `nginx -s reload`（不需要重建容器，volume mount 立即生效）
**首次记录**: 2026-08-18 nginx 路由逻辑排查

---

## 🧠 MindElixir 5.15 集成踩坑

### 1. `overflowHidden: true` 会**完全禁用**内置 panHelper（关键陷阱）
**问题**: 自定义工具栏 + MindElixir 自带 pan，按 Space + 拖动鼠标不能平移画布。
**根本原因**（追到 `node_modules/mind-elixir/dist/MindElixir.js:2869`）：
```js
this.overflowHidden ? this.container.style.overflow = "hidden" : this.disposable.push(jn(this))
```
**三元表达式短路求值**：当 `overflowHidden: true` 时，**不会调用 `jn(this)`**，即**不注册 panHelper 的所有 listener**（pointerdown/pointermove/pointerup/keydown/keyup），导致：
- `spacePressed` 永远不会被设为 `true`（虽然 keydown 也注册在 container 上，但同时也没注册）
- `panHelper.handlePointerDown` 永远没机会被调用
- 即使你按了 Space + 鼠标左键拖动，也走节点拖动而不是画布平移

**症状**: 自定义实现一套 Space+drag pan 一直失败 → 检查 `overflowHidden` 选项 → 改成 `false` → 官方 panHelper 接管，立刻工作。

**适用场景**: 任何 MindElixir 5.x 项目想要 Space+drag 平移画布。
**Why**: 默认 `overflowHidden: false`，但很多人习惯开 `overflow: hidden` 来防止内容溢出，会无意中关掉 panHelper。
**首次记录**: 2026-08-22 MindmapEditor pan 修复

### 2. MindElixir 的 keydown listener 注册在 `mind.container` 上，不是 window
**关键代码**（`MindElixir.js:1219-1220`）：
```js
return Ge([
  { dom: n, evt: "pointerdown", func: u },  // n = mind.container
  ...
  { dom: n, evt: "keydown", func: C },     // Space → spacePressed=true
  { dom: n, evt: "keyup", func: T },       // Space → spacePressed=false
]);
```
**Why this matters**:
- container 没焦点时，浏览器不把键盘事件路由到这里 → spacePressed 永远 false
- MindElixir init 时自动设 `container.setAttribute('tabindex', '0')`，但需要用户**主动点击画布**才能聚焦
- **不要**在 window 上加自己的 Space 处理 + `stopPropagation`，否则 MindElixir 收不到
- **可以**在 window 上 preventDefault Space（阻止页面滚动），MindElixir 仍能收到（preventDefault 不阻止冒泡）

**正确做法**:
```js
// mousedown 时让 container 获得焦点
mind.container.addEventListener('mousedown', (e) => {
  mind.container.focus({ preventScroll: true })
})

// window 上只 preventDefault Space，不拦截
window.addEventListener('keydown', (e) => {
  if (e.code === 'Space' && !editing) e.preventDefault()  // 防止页面滚动
})
```

**适用场景**: MindElixir 5.x 任何需要自定义快捷键的项目。
**首次记录**: 2026-08-22 MindmapEditor pan 修复

### 3. MindElixir panHelper 只支持**鼠标左键 + Space**（非右键 + Space）
**关键代码**（`MindElixir.js:2768-2780`）：
```js
handlePointerDown(t) {
  const s = e.spacePressed && t.button === 0 && t.pointerType === "mouse";  // 左键
  const i = !e.editable || t.button === o && t.pointerType === "mouse" || t.pointerType === "touch";  // 右键/touch
  !s && !i || (...this.mousedown = true);
}
```
**Why**: MindElixir 默认 `mouseSelectionButton = 0`（左键 = 选区），所以右键用于拖拽节点，左键配合 Space 用于平移。这跟常见编辑器（mermaid/draw.io）相反。
**适用场景**: 给 MindElixir 加自定义快捷键或手势时，先看清 `button` 限制。
**首次记录**: 2026-08-22 MindmapEditor pan 修复
### 4. Vue `<style scoped>` 内的 `@import` 会给 `:root` 加 data-v hash（致命陷阱）
**问题**: `var(--my-var)` 在生产 build 后值是空字符串。MindElixir 主题色/CSS 变量在编辑器面板里完全失效。
**根因**（Vite 生产 build 产物）：
```css
/* 源码 */
:root { --mindmap-primary: #F59E0B; }

/* build 后 */
[data-v-9e3226d3]:root { --mindmap-primary: #F59E0B; }
/* ↑ 但 <html> 没有 data-v-xxx 属性，规则永远不命中 */
```
**Why**: Vue SFC 的 `<style scoped>` 内部 `@import '@/styles/xxx.css'` 时，Vite 把 @import 内容**当 scoped 规则处理**，给所有选择器（包括 `:root`）加 `[data-v-xxx]` 前缀。
**修复**: 把全局 CSS 变量文件从 `<style scoped>` 内的 `@import` 移到 `main.ts` 顶层 `import '@/styles/xxx.css'`（主入口加载，无 scoped 哈希）。
**验证方法**:
```js
const v = getComputedStyle(document.documentElement).getPropertyValue('--my-var')
// 如果返回 '' 即失效
```
**适用场景**: 所有需要在 `:root` 上声明的全局 CSS 变量（主题色、间距 token、设计令牌）。
**首次记录**: 2026-08-22 MindmapEditor 大纲选中不可见修复

### 5. MindElixir 5.15 容器类名是 `.map-container`，**没有 `.mind-elixir`**
**关键代码**（`MindElixir.js:2865`）：
```js
this.container.className = "map-container";  // 不是 mind-elixir
```
**Why this matters**: 旧版 MindElixir (v3/v4) 容器是 `.mind-elixir`，官方文档示例也是这个 class。5.15 重构后改名为 `.map-container` ——所有 CSS 选择器 `:deep(.mind-elixir me-tpc)` 等都失效。
**修复**: 所有覆盖 MindElixir 元素的 CSS 选择器改用 `.map-container`：
```css
:deep(.map-container me-tpc) { ... }          /* 不是 .mind-elixir */
:deep(.map-container me-epd) { ... }
:deep(.map-container me-tpc.search-hit) { ... }
```
**验证方法**: Playwright `getComputedStyle(epd).width === 'auto'` 而不是 CSS 设置的 '16px' → 选择器不命中。
**适用场景**: 任何 MindElixir 5.x 项目覆盖默认样式时。
**首次记录**: 2026-08-22 MindmapEditor 折叠图标不显示修复

### 6. MindElixir 5.15 `addChild` 不会自动给父节点补 `me-epd` 折叠按钮（隐藏 bug）
**现象**: 工具栏点「添加子节点」后，父节点（root）没有 +/- 折叠按钮。
**根因**（`MindElixir.js:518-528`）：`addChild` → `Te(...)` → `o.children.push(s)`（更新 nodeData） → `createWrapper(s)`（**只创建子节点的 wrapper**，没补父节点 epd）。`createWrapper` 内部 line 441 会创建 epd，**但只在初始 init 时被父节点调用**。
**修复**: 自定义 `repairEpds()` 函数，在 `refreshAndReselect` 后 setTimeout 80ms 扫所有 wrapper，给有 children 但缺 me-epd 的节点手动补元素：
```ts
function repairEpds() {
  wrappers.forEach((w) => {
    const tpc = w.querySelector('me-parent me-tpc')
    if (tpc.nodeObj.children?.length && !w.querySelector('me-epd')) {
      const epd = document.createElement('me-epd')
      epd.expanded = tpc.nodeObj.expanded !== false
      epd.className = epd.expanded ? 'minus' : ''
      epd.addEventListener('click', (e) => { e.stopPropagation(); mind.expandNode(tpc) })
      parent.insertBefore(epd, tpc)
    }
  })
}
```
**为什么 setTimeout**: 必须等 MindElixir 内部 linkDiv + DOM 重建完成后再操作 DOM。
**不要**调 `mind.refresh()` 或 `mind.layout()` 来重建 epd：前者会触发 linkDiv null（DOM 重建时旧 me-tpc 引用失效），后者会清空所有 me-epd 但不重新生成。
**适用场景**: MindElixir 5.x 项目用 addChild/insertSibling 等 API 后。
**首次记录**: 2026-08-22 MindmapEditor 折叠图标不显示修复

### 7. MindElixir `scrollIntoView` 是 API 不是浏览器原生（关键陷阱）
**问题**: 大纲点击节点希望滚动画布到节点处，用了 `element.scrollIntoView()` 没效果（节点在 me-tpc 容器内，浏览器原生 scrollIntoView 只滚 document 不会滚 mind 内部画布）。
**根因**: MindElixir 5.15 的画布是 `transform: translate3d(...) scale(...)`，浏览器原生 scrollIntoView 不知道这个 transform。
**正确做法**:
```ts
// ❌ 不要用浏览器原生
tpc.scrollIntoView()  // 无效

// ✅ 用 MindElixir 的 API
mind.scrollIntoView(tpc)
// 内部检测：
// 1. 如果 tpc 在视口内（top/left/right/bottom 都在 container 范围内）→ 不动
// 2. 否则调用 mind.move(-dx, -dy, true) 平移画布
// 3. 真正把节点居中
```
**适用场景**: 大纲 → 主画布跳转、搜索跳转、键盘导航跳到节点、AI 生成结果定位。
**首次记录**: 2026-08-22 MindmapEditor 大纲跳转功能完善

### 8. MindElixir 5.15 `moveNodeBefore/After/In` 需要 me-tpc DOM 元素（不是 nodeObj）
**问题**: 调用 `mind.moveNodeIn([sourceObj], targetObj)` 报错 "Cannot read property 'parentElement' of null"。
**根因**: 这 3 个 API 内部直接读 `tpc.parentElement`、`tpc.offsetLeft` 等 DOM 属性，必须传 me-tpc DOM 元素。
**正确做法**:
```ts
const targetEl = mind.findEle(targetObj.id)  // nodeObj → DOM
mind.moveNodeIn([sourceTpc], targetEl)       // 传 DOM
mind.moveNodeBefore(sourceTpc, targetEl)
mind.moveNodeAfter(sourceTpc, targetEl)
```
**防止循环引用**: 目标节点不能是源节点的后代（否则会爆栈），代码必须先 `isDescendant(target, source)` 判定。
**适用场景**: 节点跨层级拖拽、UI 触发的层级重组、协同同步结构调整。
**首次记录**: 2026-08-22 MindmapEditor 节点移动对话框

### 9. MindElixir `editTopic` 把 contentEditable 设在外层 div 上，不是 me-tpc
**问题**: `mind.editTopic(tpc)` 后 `tpc.contentEditable === 'inherit'`，让我以为没生效。
**根因**: MindElixir `editTopic` 内部创建一个临时 `<div contenteditable="plaintext-only">` 覆盖在 me-tpc 上层（绝对定位），用户输入结束后再把文本写回 nodeObj.topic。所以检查 `tpc.contentEditable` 没用，要查 `document.querySelector('[contenteditable="plaintext-only"]')`。
**验证方法**:
```js
const editing = document.querySelector('[contenteditable="plaintext-only"]') !== null
```
**适用场景**: 检测 MindElixir 是否处于节点编辑态（用于判断快捷键是否生效、单元测试断言）。
**首次记录**: 2026-08-22 MindmapEditor 重命名按钮 E2E 测试

### 10. MindElixir `clearSelection` 不清 currentArrow 和 currentSummary
**问题**: 调用 `mind.clearSelection()` 后，`mind.currentArrow` 和 `mind.currentSummary` 仍指向旧对象，UI 上的关联线 / 摘要高亮不消失。
**根因**（`MindElixir.js:1961-1965`）：`clearSelection` 只清 `mind.currentNodes` 和 `mind.selection` 的 viselect，**不碰 currentArrow/currentSummary**。
**正确做法**:
```ts
function clearAllSelection() {
  if (!mind) return
  mind.clearSelection?.()
  mind.unselectSummary?.()
  mind.unselectArrow?.()
}
```
**适用场景**: 点击空白画布清选中、ESC 键退出、右键空白处。
**首次记录**: 2026-08-22 MindmapEditor 点击空白清选中

### 11. NodeMenu 插件和自定义 StylePanel 可并存（不是互斥）
**问题**: 以为装了 `@mind-elixir/node-menu` 就会跟我们的 `MindmapNodeStylePanel` 冲突。
**实测**: NodeMenu 只在节点**被双击**时弹出小浮窗（颜色/字体/标签/图标/URL），跟我们的右侧常驻 StylePanel 完全不冲突。
**两个 panel 各管各的**:
- **NodeMenu**：临时浮层，双击节点出现，操作完自动消失
- **MindmapNodeStylePanel**：右侧常驻，选中节点即显示，可一直操作
**唯一缺点**: NodeMenu 默认样式丑（英文 + 灰底），但功能 OK。
**适用场景**: 想同时拥有"官方开箱即用"+"完全自定义"两个面板做对比。
**首次记录**: 2026-08-22 MindmapEditor NodeMenu 集成

### 12. MindElixir 5.15 viselect 框选需要**不让自定义 mousedown 拦截空白点击**
**问题**: 自己写 `mousedown` listener 时无差别调用 `e.preventDefault()` 或 `e.stopPropagation()`，导致 MindElixir 内置 viselect 框选失效。
**根因**（`MindElixir.js:1150-1152`）：
```js
if (e.editable && b.className === "map-container" && f.button === 0 && f.pointerType === "mouse") {
  e.ptState = i.BoxSelect;
  return;
}
```
MindElixir 在 `.map-container` 空白处按左键自动进入 BoxSelect 模式，pointermove 时绘制 `.selection-area` 虚线框。
**修复**:
```ts
// ❌ 错误：无差别拦所有 mousedown
container.addEventListener('mousedown', (e) => { e.preventDefault(); ... })

// ✅ 正确：只在点击 me-tpc（节点）时拦
const tpc = e.target.closest?.('me-tpc')
if (!tpc) return  // 空白处 → 不拦截 → 让 MindElixir 进入 BoxSelect
```
**验证方法**:
```js
// 模拟：mouse.down(left) → mouse.move(...) → mouse.up
// 看 .selection-area 元素是否出现，且 currentNodes.length >= 2
```
**适用场景**: MindElixir 5.x 项目需要框选多节点 + 自定义节点点击逻辑时。
**首次记录**: 2026-08-22 MindmapEditor 框选 UX 优化

### 13. viselect `.selection-area` 必须在全局样式定义（不在 scoped）
**问题**: 想把框选蓝色虚线框改成琥珀橙主题色，写在 `<style scoped>` 内的 `:deep(.selection-area)` 不生效。
**根因**: viselect 把 `.selection-area` div **append 到 `document.body`**（不在 Vue 组件 DOM 树内），scoped data-v hash 永远不命中。Vue SFC scoped 只对当前组件模板渲染的元素加 `[data-v-xxx]` 属性。
**修复**: 把 `.selection-area` 样式放到 `src/styles/mindmap-theme.css`（main.ts 顶层 import，全局作用域）。
**适用场景**: 任何外部库（viselect/emoji-mart/tippy.js 等）动态注入 body 或 document 的元素。
**首次记录**: 2026-08-22 MindmapEditor 框选主题色

### 14. MindElixir `.selected` outline 和 `#input-box` border 是两个不同的"边框"
**问题**: 用户反馈「节点选中时文本框的实线边框不好看」，以为是单一处。
**根因**: MindElixir 有两个不同的视觉态：
1. **`.map-container .selected`** — 节点选中时显示，默认 `outline: 2px solid var(--selected)`（粗实线）
2. **`.map-container #input-box`** — 节点进入编辑态时的输入框，默认 `outline: 1px solid #ccc`（细灰线）
**修复**: 都改用琥珀橙主题色 + 柔和化：
```css
.map-container .selected {
  outline: 1.5px solid var(--mindmap-primary) !important;
  outline-offset: 2px !important;
}
.map-container #input-box {
  outline: none !important;
  border: 1.5px solid var(--mindmap-primary) !important;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15) !important;
}
```
**适用场景**: MindElixir 5.x 项目节点视觉态定制。
**首次记录**: 2026-08-22 MindmapEditor 节点边框优化

### 15. Vue `<component :is="xxx">` 传字符串名 vs 组件引用（图标不渲染）
**问题**: `MindmapNodeStylePanel` 8 个图标都不渲染。
**根因**:
```ts
// ❌ 字符串名 — 运行时无法解析
const ICONS = [{ elIcon: 'Star' }, ...]
// 模板：<component :is="icon.elIcon" />  ← 不渲染！

// ✅ 组件引用 + markRaw
import { markRaw, Star } from '...'
const ICONS = [{ elIcon: markRaw(Star) }, ...]
// 模板：<component :is="icon.elIcon" />  ← 渲染 SVG
```
**为什么 markRaw**: Vue 会对响应式数据中的组件做 `reactive()` 包装，会触发警告并可能破坏组件内部状态。`markRaw` 标记"这是组件，别包装"。
**验证**: `page.locator('.icon-grid svg').count() === 8`
**适用场景**: 所有动态 `<component :is>` 场景（图标库/动态组件/路由组件等）。
**首次记录**: 2026-08-22 MindmapNodeStylePanel 图标渲染修复
