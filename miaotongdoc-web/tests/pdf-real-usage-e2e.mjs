/**
 * tests/pdf-real-usage-e2e.mjs — 真实使用场景 E2E
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const API_BASE = process.env.API_BASE || 'http://localhost:9004'
const REPORT_PATH = resolve(__dirname, 'pdf-real-usage-e2e-report.md')
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
    method: 'POST', headers: { 'Content-Type': 'application/json' },
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
    method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd,
  })
  return (await r.json()).id
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'D:\\Tools\\ms-playwright\\chromium-1228\\chrome-win64\\chrome.exe',
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()

  try {
    const token = await apiLogin()
    step('API 登录', !!token)

    const docId = await uploadPdf(token, resolve(__dirname, 'fixtures/sample-multi-page.pdf'), 'real-usage')
    step('上传 PDF', !!docId, `docId=${docId}`)

    await page.goto(BASE)
    await page.evaluate((t) => {
      sessionStorage.setItem('token', t); localStorage.setItem('token', t)
    }, token)
    await page.goto(`${BASE}/editor/${docId}`, { waitUntil: 'domcontentloaded', timeout: 60000 })
    await page.waitForSelector('.pdf-ribbon-tab', { state: 'attached', timeout: 90000 })
    await page.waitForTimeout(10000)

    // 检查 1: 画布 fit-width
    const info = await page.evaluate(() => {
      const c = document.querySelector('.pdf-canvas-el')
      const p = document.querySelector('.pdf-page-canvas')
      if (!c || !p) return null
      const cr = c.getBoundingClientRect()
      const pr = p.getBoundingClientRect()
      return { cw: cr.width, ch: cr.height, pw: pr.width, r: cr.width / pr.width }
    })
    if (info) {
      step('画布 fit-width', info.r > 0.85 && info.r < 1.15, `ratio=${info.r.toFixed(2)}`)
    } else {
      step('画布存在', false, 'no canvas')
    }

    // 检查 2: textEdit 有 token
    await page.locator('.pdf-ribbon-tab:has-text("编辑")').first().click()
    await page.waitForTimeout(500)
    await page.keyboard.press('t')
    await page.waitForTimeout(5000)
    const tc = await page.locator('.pdf-edit-token').count()
    const es = await page.locator('.pdf-text-edit-empty').isVisible().catch(() => false)
    step('textEdit 有可编辑 token', tc > 0, `count=${tc}, empty=${es}`)
    await page.screenshot({ path: resolve(__dirname, 'screenshots/real-usage-textedit.png') })
  } catch (e) {
    console.log('FATAL', e.message)
    log.push('FATAL ' + e.message)
    fail++
  } finally {
    await browser.close()
    const report = `# Real Usage E2E\n\nPass: ${pass} | Fail: ${fail}\n\n${log.join('\n')}`
    writeFileSync(REPORT_PATH, report)
    console.log(`\npass ${pass} / fail ${fail}`)
    process.exit(fail > 0 ? 1 : 0)
  }
}
main().catch((e) => { console.error(e); process.exit(1) })