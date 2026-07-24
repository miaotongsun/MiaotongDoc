# tests/ —— E2E 自动化测试(Playwright)

本目录使用 **Playwright + Chromium** 模拟真实用户操作,验证 PDF 编辑器各功能。

## 设计原则

**所有测试相关文件(代码/报告/截图)集中在 `tests/` 内**。**Chromium 二进制装到根盘工具目录**(`D:\Tools\`),跨项目、跨开发者共享,不入项目,不依赖用户名。

| 组件 | 路径 | 说明 |
|---|---|---|
| `playwright` npm 包 | `miaotongdoc-web/node_modules/playwright/` | 项目内,版本锁定 1.61.1 |
| Chromium 浏览器 | **`D:\Tools\ms-playwright\`** (Windows)<br>`~/Tools/ms-playwright/` (macOS/Linux) | **跨项目共享工具目录**,根盘 `Tools/`,不受系统清理影响 |
| 测试报告 | `tests/phase14-e2e-report.md` | 本地生成,gitignore 排除 |
| 截图 | `tests/screenshots/*.png` | 本地生成,gitignore 排除 |

**统一入口**:`npm run e2e` 一条命令搞定(自动检测 + 安装 Chromium + 跑测试)。

## 快速开始

### 1. 前置条件

- Node.js 18+
- 后端服务运行:`docker compose -f MiaotongDoc-Docker/docker-compose.yml up -d nginx web-server`
- 前端 dist 已部署(若没部署,先 `npm run build` + cp dist 到 Docker volume)

### 2. 第一次跑测试(全自动)

```bash
cd miaotongdoc-web
npm i                              # 装 playwright 1.61.1
npm run e2e                        # 自动检查/下载 Chromium + 跑测试
```

**首次跑时**:
1. 检测 `D:\Tools\ms-playwright\chromium-1228\chrome-win64\chrome.exe` 不存在
2. 自动调用 `npx playwright install chromium` 下载 ≈170MB
3. 下载完成后自动跑测试
4. 后续跑(浏览器已就绪):直接跑测试,几秒启动

**输出**:
- `tests/phase14-e2e-report.md` — 通过/失败汇总
- `tests/screenshots/*.png` — 关键页面截图

### 3. 查看报告

```bash
npm run e2e:report
# 或:cat tests/phase14-e2e-report.md
```

## npm scripts 速查

| 命令 | 作用 |
|---|---|
| `npm run e2e` | **主入口** — 自动检查/安装 Chromium + 跑测试 |
| `npm run e2e:install` | 单独装/更新 Chromium(无需跑测试) |
| `npm run e2e:report` | 打印上次测试报告 |

## 环境变量

| 变量 | 默认值 | 作用 |
|---|---|---|
| `BASE` | `http://localhost:80` | 前端地址(Nginx) |
| `API_BASE` | `http://localhost:9004` | 后端 API 地址 |
| `PLAYWRIGHT_BROWSERS_PATH` | `D:\Tools\ms-playwright` (Windows)<br>`~/Tools/ms-playwright` (Unix) | Chromium 安装目录,自动检测 |

```bash
# 自定义路径(可选)
PLAYWRIGHT_BROWSERS_PATH=E:\Tools\ms-playwright npm run e2e
```

## 新开发者入职流程

```bash
# 1. clone 代码
git clone ...

# 2. 安装前端依赖(包含 playwright)
cd miaotongdoc-web
npm i

# 3. 跑测试(自动检测 + 装 Chromium)
npm run e2e
# → 若 D:\Tools\ms-playwright\ 不存在,自动下载(≈170MB,2-5 分钟)
# → 若已存在(公司共享盘/老员工机器),直接跑,几秒启动
```

**所有开发者共用** `D:\Tools\ms-playwright\`(跨项目、跨开发者、跨工具共享),不需要每个项目/每个人下载一次。

## 添加新测试场景

在 `phase14-e2e.mjs` 的 `main()` 函数中添加新的检查段,使用 `step()` 函数记录结果:

```javascript
function step(name, ok, detail = '') {
  const mark = ok ? '✅' : '❌'
  console.log(`${mark} ${name}${detail ? ' — ' + detail : ''}`)
  if (!ok) failures.push({ name, detail })
}

await page.locator('button:has-text("xxx")').click()
step('xxx 按钮可点', true)
```

新增的截图统一用 `SCREENSHOTS_DIR` 变量,不要硬编码路径。

## 常见问题

### 1. `page.goto` 超时(ERR_CONNECTION_REFUSED)
**原因**:Docker Desktop 挂了或容器没启动。
**解决**:重启 Docker Desktop,等容器 healthy,重新跑测试。

### 2. `Executable doesn't exist at .../chromium-1228/.../chrome.exe`
**原因**:Chromium 没装到 `PLAYWRIGHT_BROWSERS_PATH` 路径。
**解决**:`npm run e2e:install` 重新下载;或手动 `PLAYWRIGHT_BROWSERS_PATH=D:\Tools\ms-playwright npx playwright install chromium`。

### 3. `D:\Tools` 路径不存在
**原因**:新电脑没 `D:\Tools` 目录。
**解决**:`npm run e2e` 会自动 `mkdir -p` 创建,然后下载;或手动 `mkdir D:\Tools` 后重跑。

### 4. 测试偶尔失败
**原因**:网络/动画/竞态。
**解决**:增加 `await page.waitForTimeout(N)` 等待动画;用 `data-*` 属性优先于文本选择器。

### 5. 调试模式(可视化)
Playwright 默认 headless。改 `chromium.launch({ headless: false })` 看浏览器实时操作。

## CI/CD 集成

GitHub Actions 用项目内路径(隔离,不依赖外部):

```yaml
- name: 安装 Playwright + Chromium
  run: |
    cd miaotongdoc-web
    npm ci
    PLAYWRIGHT_BROWSERS_PATH=${{ github.workspace }}/.cache/ms-playwright \
      npx playwright install chromium

- name: 启动服务
  run: docker compose up -d nginx web-server

- name: 跑 E2E
  run: cd miaotongdoc-web && npm run e2e

- name: 上传报告
  uses: actions/upload-artifact@v4
  with:
    name: e2e-report
    path: miaotongdoc-web/tests/phase14-e2e-report.md
```

CI 干净环境,直接装项目内 `.cache/ms-playwright`,一次性。

## 目录结构

```
miaotongdoc-web/tests/
├── README.md                   ← 本文档
├── run.mjs                     ← 统一入口(检查+安装+跑测试)
├── phase14-e2e.mjs            ← 测试主体(≈340 行,Phase 14 11 项单元)
├── phase14-e2e-report.md       ← 最近一次报告(gitignore)
├── screenshots/                 ← 截图(gitignore)
│   ├── export-menu-home.png
│   ├── export-menu-rail.png
│   └── final-state.png
└── local/                      ← 个人临时测试脚本(不进 git,详见 local/README.md)
    ├── .gitkeep
    └── README.md
```

## 测试脚本两类位置

| 位置 | git | 用途 |
|---|---|---|
| `tests/*.mjs`(根) | ✅ 入库 | 固化的 E2E 测试,CI 跑,团队共享 |
| `tests/local/*.mjs` | ❌ 不入库 | 临时调试、一次性实验、个人探索 |

**新功能测试场景** → 先在 `tests/local/` 验证,稳定后移到 `tests/` 根并提交。
