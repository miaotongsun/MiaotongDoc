#!/usr/bin/env node
/**
 * tests/run.mjs —— E2E 统一入口
 *
 * 1. 检测 Chromium 是否存在(项目内 .cache/ms-playwright/)
 * 2. 没有则自动调用 playwright install 下载(≈170MB,首次)
 * 3. 自动确保 Docker 服务可连(Nginx + 后端)
 * 4. 运行 phase14-e2e.mjs
 * 5. 输出报告 + 截图
 *
 * 用法:
 *   npm run e2e          # 完整流程(检查+安装+跑)
 *   npm run e2e:install  # 只安装 Chromium
 *   npm run e2e:report   # 只看报告
 */
import { execSync, spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs'
import { resolve, join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import http from 'node:http'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const CHROMIUM_VERSION = '1228'
const CHROMIUM_HEADLESS_VERSION = '1228'

/**
 * 跨平台固定工具目录(根盘的 Tools/,跨项目共享,不入项目)
 * - Windows: D:\Tools\ms-playwright\
 * - macOS:   $HOME/Tools/ms-playwright/  (即 ~/Tools/)
 * - Linux:   $HOME/Tools/ms-playwright/
 */
function defaultToolsDir() {
  if (process.platform === 'win32') return 'D:\\Tools\\ms-playwright'
  return join(process.env.HOME || process.env.USERPROFILE || '~', 'Tools', 'ms-playwright')
}

const CACHE_DIR = process.env.PLAYWRIGHT_BROWSERS_PATH || defaultToolsDir()

// 平台特定的 chromium 路径(完整版 + headless-shell)
function chromiumExePath() {
  if (process.platform === 'win32') return join(CACHE_DIR, 'chromium-1228', 'chrome-win64', 'chrome.exe')
  if (process.platform === 'darwin') return join(CACHE_DIR, 'chromium-1228', 'chrome-mac', 'Google Chrome for Testing')
  return join(CACHE_DIR, 'chromium-1228', 'chrome-linux', 'chrome')
}
function headlessShellExePath() {
  if (process.platform === 'win32') return join(CACHE_DIR, 'chromium_headless_shell-1228', 'chrome-headless-shell-win64', 'chrome-headless-shell.exe')
  if (process.platform === 'darwin') return join(CACHE_DIR, 'chromium_headless_shell-1228', 'chrome-mac-arm64', 'headless_shell')
  return join(CACHE_DIR, 'chromium_headless_shell-1228', 'chrome-linux', 'headless_shell')
}

/**
 * 1. 检测 Playwright npm 包
 */
function ensurePlaywright() {
  try {
    const pkg = join(ROOT, 'node_modules', 'playwright', 'package.json')
    if (!existsSync(pkg)) {
      console.error('❌ Playwright 未安装,先跑: npm i')
      process.exit(1)
    }
    const data = JSON.parse(readFileSync(pkg, 'utf8'))
    console.log(`✅ Playwright ${data.version}`)
    return data
  } catch (e) {
    console.error('❌ Playwright 检查失败:', e.message)
    process.exit(1)
  }
}

/**
 * 2. 检测 Chromium,没有则自动下载
 */
function ensureChromium(silent = false) {
  // Phase 14.U15: Playwright 1.61+ headless 模式**只需要** chromium-headless-shell
  // chromium 完整版**可选**(仅 headed 模式 / 需要完整 UI 调试时用)
  // → 自动尝试装 chromium,失败也不致命(headless-shell 已够)
  const requiredTargets = [
    { name: 'chromium-headless-shell', version: CHROMIUM_HEADLESS_VERSION, exe: headlessShellExePath(), required: true },
  ]
  const optionalTargets = [
    { name: 'chromium', version: CHROMIUM_VERSION, exe: chromiumExePath(), required: false },
  ]

  let fail = false
  for (const t of [...requiredTargets, ...optionalTargets]) {
    if (existsSync(t.exe)) {
      const size = (statSync(t.exe).size / 1024 / 1024).toFixed(1)
      if (!silent) console.log(`✅ ${t.name} 已就绪 (${size}MB)`)
      continue
    }
    if (!silent) console.log(`📦 ${t.name} 未找到${t.required ? '' : '(可选)'},自动下载...`)
    mkdirSync(CACHE_DIR, { recursive: true })
    try {
      execSync(`npx playwright install ${t.name}`, {
        stdio: 'inherit',
        cwd: ROOT,
        env: { ...process.env, PLAYWRIGHT_BROWSERS_PATH: CACHE_DIR },
      })
      if (!existsSync(t.exe)) throw new Error('下载完成但二进制不存在')
      if (!silent) console.log(`✅ ${t.name} 安装完成`)
    } catch (e) {
      console.error(`❌ ${t.name} 下载失败:`, e.message)
      if (t.required) {
        fail = true
      } else {
        console.log(`   ⚠️  ${t.name} 缺失,headed 模式不可用(headless 仍可跑)`)
      }
    }
  }
  if (fail) {
    console.error('   请手动跑: PLAYWRIGHT_BROWSERS_PATH="D:\\Tools\\ms-playwright" npx playwright install chromium-headless-shell')
    process.exit(1)
  }
}

/**
 * 3. 检测服务可连(可选警告)
 */
async function checkServices() {
  const checks = [
    { name: 'Nginx 前端', url: 'http://localhost/' },
    { name: '后端 API', url: 'http://localhost:9004/api/auth/login' },
  ]
  for (const c of checks) {
    try {
      await new Promise((resolve, reject) => {
        const req = http.get(c.url, { timeout: 3000 }, (res) => { resolve(res.statusCode) })
        req.on('error', reject)
        req.on('timeout', () => { req.destroy(); reject(new Error('timeout')) })
      })
      console.log(`✅ ${c.name} 可达`)
    } catch (e) {
      console.log(`⚠️  ${c.name} 不可达(${e.message})`)
      console.log(`   提示: docker compose -f MiaotongDoc-Docker/docker-compose.yml up -d nginx web-server`)
    }
  }
}

/**
 * 4. 运行测试
 */
function runTest() {
  console.log('\n## 跑 E2E 测试\n')
  // 1) Phase 14 主回归
  const child1 = spawn('node', ['tests/phase14-e2e.mjs'], {
    stdio: 'inherit',
    cwd: ROOT,
    env: { ...process.env, PLAYWRIGHT_BROWSERS_PATH: CACHE_DIR },
  })
  child1.on('exit', () => {
    // 2) Home 状态路由化(2026-08-02 新增)
    const child2 = spawn('node', ['tests/home-state-restore-e2e.mjs'], {
      stdio: 'inherit',
      cwd: ROOT,
      env: { ...process.env, PLAYWRIGHT_BROWSERS_PATH: CACHE_DIR },
    })
    child2.on('exit', (code2) => process.exit(code2 || 1))
  })
}

async function main() {
  console.log('## Playwright E2E 启动\n')
  ensurePlaywright()
  ensureChromium()
  await checkServices()
  runTest()
}

main().catch((e) => { console.error(e); process.exit(1) })
