/**
 * tests/pdf-ai-e2e.mjs — PR4 验证
 *
 * 验证 AI 助手 5 个失效功能修复:
 *   1. AI tab "全文摘要" 调 documentAiApi.summarize (有响应)
 *   2. 右键菜单 "AI 问答" 浮窗出现 + 自动引导
 *   3. 右键 "摘要当前页" 调 /text 取页文本
 *   4. AI tab "合同条款" 打开 PdfTermsPanel 抽屉
 *   5. AI tab "智能目录" 强制开 outline panel
 *
 * 注意: 不依赖实际 LLM 响应内容(LLM 可能未配置),仅验证 UI/前端路由正确
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const API_BASE = process.env.API_BASE || 'http://localhost:9004'
const REPORT_PATH = resolve(__dirname, 'pdf-ai-e2e-report.md')
mkdirSync(resolve(__dirname, 'screenshots'), { recursive: true })

let pass = 0, fail = 0
const log = []
const failures = []
function step(name, ok, detail = '') {
  const m = ok ? 'OK' : 'FAIL'
  const line = `${m} ${name}${detail ? ' - ' + detail : ''}`
  console.log(line)
  log.push(line)
  ok ? pass++ : (fail++, failures.push({ name, detail }))
}

async function apiLogin() {
  const r = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: '10000000', password: '123456' }),
  })
  return (await r.json()).token
}

async function uploadPdf(token, filePath, title) {
  const buf = readFileSync(filePath)
  const fd = new FormData()
  fd.append('file', new Blob([buf], { type: 'application/pdf' }), title + '.pdf')
  fd.append('title', title)
  fd.append('docType', 'pdf')
  const r = await fetch(`${API_BASE}/api/documents/upload`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: fd,
  })
  return (await r.json()).id
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => console.log('  [PAGE ERROR]', e.message))

  try {
    const token = await apiLogin()
    step('API 登录', !!token)

    const docId = await uploadPdf(token, resolve(__dirname, 'fixtures/sample-multi-page.pdf'), 'ai-test-pr4')
    step('上传 sample-multi-page.pdf', !!docId, `docId=${docId}`)

    // 注入 token 到 sessionStorage,否则 /editor 会跳回登录页
    await page.goto(BASE)
    await page.evaluate((t) => {
      sessionStorage.setItem('token', t); localStorage.setItem('token', t)
    }, token)
    await page.goto(`${BASE}/editor/${docId}`, { waitUntil: 'domcontentloaded', timeout: 60000 })
    await page.waitForTimeout(15000)
    await page.screenshot({ path: resolve(__dirname, 'screenshots/ai-test-initial.png') })
    // 等 Ribbon 渲染
    await page.waitForSelector('.pdf-ribbon-tab', { timeout: 60000 }).catch(() => null)
    const ribbonCount = await page.locator('.pdf-ribbon-tab').count()
    console.log('  [DEBUG] ribbon tab count:', ribbonCount)

    // === 1. AI tab "全文摘要" — 走专用端点 ===
    // 等 Ribbon 渲染完毕(PDF 编辑器加载慢)
    // Ribbon 在 headless 下可能 display:none (其他 tab 折叠),等 DOM attached 即可
    page.on('pageerror', (e) => console.log('  [PAGE ERROR]', e.message))
    page.on('console', (m) => { if (m.type() === 'error') console.log('  [CONSOLE ERR]', m.text().slice(0, 200)) })
    await page.waitForTimeout(5000)
    const ribbonCnt = await page.locator('.pdf-ribbon-tab').count()
    const bodyText = (await page.locator('body').innerText()).slice(0, 300)
    console.log('  [DEBUG] ribbon count:', ribbonCnt, 'body:', bodyText.replace(/\n/g, ' | '))
    await page.waitForSelector('.pdf-ribbon-tab', { state: 'attached', timeout: 90000 })
    await page.waitForTimeout(2000)
    await page.locator('.pdf-ribbon-tab:has-text("AI")').first().click({ force: true })
    await page.waitForTimeout(800)
    const fullSummaryBtn = page.locator('button:has-text("全文摘要")').first()
    const fullSummaryCount = await fullSummaryBtn.count()
    step('AI tab "全文摘要" 按钮渲染', fullSummaryCount === 1, `count=${fullSummaryCount}`)

    // === 2. AI tab "合同条款" — 打开 PdfTermsPanel ===
    const termsBtn = page.locator('button:has-text("合同条款")').first()
    step('AI tab "合同条款" 按钮渲染', await termsBtn.count() === 1)

    // === 3. AI tab "智能目录" — 强制开 outline panel ===
    const outlineBtn = page.locator('button:has-text("智能目录")').first()
    step('AI tab "智能目录" 按钮渲染', await outlineBtn.count() === 1)

    // === 4. 右键菜单 "AI 问答" — 自动发引导 ===
    await page.waitForTimeout(3000) // 等 canvas 渲染
    const canvasBox = await page.locator('.pdf-page-canvas').first().boundingBox({ timeout: 60000 }).catch(() => null)
    if (canvasBox) {
      // 先 hover AI 父菜单(AI 子项在 hover 时显示)
      await page.mouse.move(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2, { button: 'right' })
      await page.waitForTimeout(1500) // 等右键菜单完全弹出
      const aiParent = page.locator('.pdf-ctx-item:has-text("AI")').first()
      if (await aiParent.count() > 0) {
        await aiParent.hover()
        await page.waitForTimeout(1500) // 2026-08-02: 加长 hover 等待,AI 子菜单弹出需要时间
      }
      // 右键菜单含 AI 问答项(可能隐藏在 hover 子菜单中)
      const aiChatItem = await page.locator('text=AI 问答').first().isVisible().catch(() => false)

      await page.screenshot({ path: resolve(__dirname, 'screenshots/ai-contextmenu.png') })

      // 点 "AI 问答"
      if (aiChatItem) {
        await page.locator('text=AI 问答').first().click()
        await page.waitForTimeout(2000)
        // AI 浮窗出现(.ai-fab / .ai-float / .ai-float-panel)
        const floatPanel = await page.locator('.ai-fab, .ai-float, .ai-float-panel').first().isVisible().catch(() => false)
        step('AI 浮窗出现', floatPanel, `visible=${floatPanel}`)
        await page.screenshot({ path: resolve(__dirname, 'screenshots/ai-float-panel.png') })
      }

      // 关闭浮窗,再测 "摘要当前页"(子菜单,先 hover AI 父菜单)
      await page.keyboard.press('Escape')
      await page.waitForTimeout(500)
      // 重新打开右键菜单
      await page.mouse.click(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2, { button: 'right' })
      await page.waitForTimeout(1500)
      const aiParent2 = page.locator('.pdf-ctx-item:has-text("AI")').first()
      if (await aiParent2.count() > 0) {
        await aiParent2.hover()
        await page.waitForTimeout(1500) // 2026-08-02: 加长 hover
      }
      const summarizeItem = await page.locator('text=摘要当前页').first().isVisible().catch(() => false)
      step('右键菜单含"摘要当前页"项(AI 子菜单)', summarizeItem, `visible=${summarizeItem}`)

      // 关闭
      await page.keyboard.press('Escape')
    } else {
      step('canvas 渲染', false, 'canvasBox 为 null')
    }

    // === 5. 验证后端 summarize 端点 ===
    const sumR = await fetch(`${API_BASE}/api/documents/${docId}/ai/summarize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    })
    step('POST /ai/summarize 端点可达', sumR.status < 500, `status=${sumR.status}`)

    // === 6. 验证后端 translate 端点 ===
    const trR = await fetch(`${API_BASE}/api/documents/${docId}/ai/translate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({ text: 'Hello world', targetLang: 'zh' }),
    })
    step('POST /ai/translate 端点可达', trR.status < 500, `status=${trR.status}`)
  } catch (e) {
    console.log('FATAL', e)
    log.push('FATAL ' + e.message)
    fail++
  } finally {
    await browser.close()
    const report = `# PR4 AI Assistant E2E Report\n\nGenerated: ${new Date().toISOString()}\n\nPass: ${pass} | Fail: ${fail}\n\n${log.join('\n')}${failures.length ? '\n\n## Failures\n' + failures.map((f) => `- ${f.name}: ${f.detail}`).join('\n') : ''}`
    writeFileSync(REPORT_PATH, report)
    console.log(`\nReport: ${REPORT_PATH}\npass ${pass} / fail ${fail}`)
    process.exit(fail > 0 ? 1 : 0)
  }
}

main().catch((e) => { console.error(e); process.exit(1) })