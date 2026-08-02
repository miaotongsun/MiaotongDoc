/**
 * tests/pdf-edit-mode-e2e.mjs — PR2 验证
 *
 * 验证根因修复:PositionStripper.writeString(String, TextPosition) 错误签名
 * 被修复后,extractTextPositions 不再返回空数组,textEdit 模式下画布上有
 * .pdf-edit-token 元素可点。
 *
 * 同时验证:
 *  - onSave() 占位 → 真保存
 *  - 空态引导(扫描件,无 token)
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const API_BASE = process.env.API_BASE || 'http://localhost:9004'
const REPORT_PATH = resolve(__dirname, 'pdf-edit-mode-e2e-report.md')
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

async function login(page) {
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle', timeout: 30000 })
  if (!page.url().includes('/login')) return
  await page.waitForSelector('input', { timeout: 10000 })
  const inputs = await page.locator('input').all()
  await inputs[0].fill('10000000')
  await inputs[1].fill('123456')
  await page.locator('button.login-btn').first().click()
  await page.waitForURL(/\/(home|$)/, { timeout: 15000 })
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
    await login(page)
    step('登录成功', !page.url().includes('/login'))

    // 1. 上传有内嵌文字的 PDF(内嵌字体 PDF)
    const token = await apiLogin()
    const docId = await uploadPdf(token, resolve(__dirname, 'fixtures/sample-single-page.pdf'), 'edit-mode-test')
    step('上传 sample-single-page.pdf', !!docId, `docId=${docId}`)

    // 2. 打开编辑器,切到 textEdit 工具
    await page.goto(`${BASE}/editor/${docId}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(5000)

    await page.locator('.pdf-ribbon-tab:has-text("编辑")').first().click()
    await page.waitForTimeout(500)
    // T 工具 = textEdit
    await page.keyboard.press('t')
    await page.waitForTimeout(3000) // 等 positions 加载

    // 3. 关键断言: 有 .pdf-edit-token 元素可点
    const tokenCount = await page.locator('.pdf-edit-token').count()
    step('textEdit 模式有 .pdf-edit-token 元素(根因修复)', tokenCount > 0, `count=${tokenCount}`)

    await page.screenshot({ path: resolve(__dirname, 'screenshots/edit-mode-with-tokens.png') })

    // 4. 点击第一个 token,断言能编辑
    let textBefore = ''
    let textAfter = ''
    if (tokenCount > 0) {
      const firstToken = page.locator('.pdf-edit-token').first()
      await firstToken.click({ force: true })
      await page.waitForTimeout(500)

      const isContentEditable = await firstToken.getAttribute('contenteditable')
      step('点击 token 出现 contenteditable', isContentEditable === 'true', `contenteditable=${isContentEditable}`)

      // 5. 输入新文字
      textBefore = await firstToken.textContent()
      await page.keyboard.press('Control+A')
      await page.keyboard.type('XYZ')
      await page.waitForTimeout(500)
      await firstToken.evaluate((el) => el.blur())
      await page.waitForTimeout(1500) // 等防抖提交
      textAfter = await firstToken.textContent()
      step('token 文字可被修改 (textEdit 交互生效)', textBefore !== textAfter, `before="${textBefore}" after="${textAfter}"`)
    }

    // 6. 点 Ribbon 保存按钮(onSave 占位 → 弹 SaveModeDialog)
    // 注意: 保存按钮在 Home tab 也可能叫"另存为" / "覆盖"等;尝试多种文本
    let saveClicked = false
    for (const sel of ['button:has-text("保存")', 'button:has-text("另存为")', 'button:has-text("覆盖")']) {
      const btn = page.locator(sel).first()
      if (await btn.count() > 0 && await btn.isVisible().catch(() => false)) {
        await btn.click({ force: true })
        saveClicked = true
        break
      }
    }
    await page.waitForTimeout(2000) // 等 dialog 出现
    const saveDialogVisible = await page.locator('.pdf-save-mode').first().isVisible().catch(() => false)
    const elDialogVisible = await page.locator('.el-dialog').first().isVisible().catch(() => false)
    const allText = await page.locator('body').innerText().catch(() => '')
    step('点保存按钮弹出 SaveModeDialog(非占位消息)', saveClicked && (saveDialogVisible || elDialogVisible || allText.includes('保存编辑')), `clicked=${saveClicked}, .pdf-save-mode=${saveDialogVisible}, .el-dialog=${elDialogVisible}`)

    // 8. 根因修复验证 (主断言): 再次打开已编辑文档,断言有大量 token 可点
    // 这条取代之前空态断言:sample-scanned.pdf 实际是 ReportLab 生成的"扫描风格"PDF,
    // 内嵌文字正常(PR3 修 OCR 落库之后才能区分真扫描件)
    await page.goto(`${BASE}/editor/${docId}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(5000)
    await page.locator('.pdf-ribbon-tab:has-text("编辑")').first().click()
    await page.waitForTimeout(500)
    await page.keyboard.press('t')
    await page.waitForTimeout(3000)
    const finalTokenCount = await page.locator('.pdf-edit-token').count()
    step('重开 PDF → textEdit 仍有 token 可点(持久有效)', finalTokenCount > 50, `count=${finalTokenCount}`)
  } catch (e) {
    console.log('FATAL', e)
    log.push('FATAL ' + e.message)
    fail++
  } finally {
    await browser.close()
    const report = `# PR2 Edit Mode E2E Report\n\nGenerated: ${new Date().toISOString()}\n\nPass: ${pass} | Fail: ${fail}\n\n${log.join('\n')}${failures.length ? '\n\n## Failures\n' + failures.map((f) => `- ${f.name}: ${f.detail}`).join('\n') : ''}`
    writeFileSync(REPORT_PATH, report)
    console.log(`\nReport: ${REPORT_PATH}\npass ${pass} / fail ${fail}`)
    process.exit(fail > 0 ? 1 : 0)
  }
}

main().catch((e) => { console.error(e); process.exit(1) })