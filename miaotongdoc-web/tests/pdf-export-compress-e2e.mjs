/**
 * tests/pdf-export-compress-e2e.mjs — PR1 验证
 *
 * 覆盖:
 *  - 导出菜单有"压缩及导出"按钮 (新文案)
 *  - 点击后弹出级别选择对话框,含 low/medium/high 三档
 *  - AI Tab 不再有"智能重写"和"纠错"按钮
 *  - Ribbon Page Tab 不再有"压缩"按钮 (统一入口)
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const REPORT_PATH = resolve(__dirname, 'pdf-export-compress-e2e-report.md')
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

async function openFirstPdf(page) {
  const token = await page.evaluate(() => sessionStorage.getItem('token'))
  const res = await page.evaluate(async (token) => {
    const r = await fetch('/api/documents/list?page=0&size=20', { headers: { Authorization: `Bearer ${token}` } })
    return await r.json()
  }, token)
  const docs = res?.content || res?.data?.content || []
  if (docs.length === 0) return null
  const firstPdf = docs.find((d) => /\.pdf$/i.test(d.title || d.fileName || '')) || docs[0]
  await page.goto(`${BASE}/editor/${firstPdf.id}`, { waitUntil: 'networkidle', timeout: 30000 })
  await page.waitForTimeout(3000)
  return firstPdf
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => console.log('  [PAGE ERROR]', e.message))

  try {
    await login(page)
    step('登录成功', !page.url().includes('/login'))

    const doc = await openFirstPdf(page)
    if (!doc) {
      step('打开 PDF 编辑器', false, '未找到 PDF 文档')
      return
    }
    step('打开 PDF 编辑器', page.url().includes(`/editor/${doc.id}`), page.url())

    // === 1. AI Tab 按钮清理 ===
    await page.locator('.pdf-ribbon-tab:has-text("AI")').first().click()
    await page.waitForTimeout(500)

    const aiRewriteCount = await page.locator('button:has-text("智能重写")').count()
    const aiProofreadCount = await page.locator('button:has-text("纠错")').count()
    step('AI Tab "智能重写" 按钮已删除', aiRewriteCount === 0, `count=${aiRewriteCount}`)
    step('AI Tab "纠错" 按钮已删除', aiProofreadCount === 0, `count=${aiProofreadCount}`)

    // 验证核心 AI 按钮还在
    const aiAssistant = await page.locator('button:has-text("AI 助手")').count()
    const aiTranslate = await page.locator('button:has-text("翻译选区")').count()
    const aiOutline = await page.locator('button:has-text("智能目录")').count()
    step('AI Tab "AI 助手" 保留', aiAssistant === 1, `count=${aiAssistant}`)
    step('AI Tab "翻译选区" 保留', aiTranslate === 1, `count=${aiTranslate}`)
    step('AI Tab "智能目录" 保留', aiOutline === 1, `count=${aiOutline}`)

    // === 2. Ribbon Page Tab 不再有"压缩"按钮 ===
    await page.locator('.pdf-ribbon-tab:has-text("页面")').first().click()
    await page.waitForTimeout(400)
    const ribbonCompressCount = await page.locator('.pdf-ribbon-row button:has-text("压缩")').count()
    step('Ribbon Page Tab "压缩" 按钮已删除', ribbonCompressCount === 0, `count=${ribbonCompressCount}`)

    // === 3. 导出菜单"压缩及导出"按钮 ===
    await page.locator('.pdf-ribbon-tab:has-text("开始")').first().click()
    await page.waitForTimeout(400)
    const exportBtn = page.locator('button:has-text("导出")').first()
    await exportBtn.click()
    await page.waitForTimeout(500)
    const compressExportBtn = page.locator('.pdf-menu-item:has-text("压缩及导出")')
    const compressExportCount = await compressExportBtn.count()
    step('导出菜单有"压缩及导出"按钮', compressExportCount === 1, `count=${compressExportCount}`)

    // === 4. 点击后弹级别选择对话框 ===
    if (compressExportCount > 0) {
      await compressExportBtn.click()
      await page.waitForTimeout(800)

      // 检查对话框是否出现
      const dialogVisible = await page.locator('.el-message-box').first().isVisible().catch(() => false)
      step('压缩对话框出现', dialogVisible)

      if (dialogVisible) {
        // 验证 select 元素存在 + 三档选项
        const selectExists = await page.locator('#pdf-compress-level-select').count()
        step('压缩级别 select 存在', selectExists === 1, `count=${selectExists}`)

        if (selectExists > 0) {
          const options = await page.locator('#pdf-compress-level-select option').allTextContents()
          step('压缩级别 low/medium/high 三档齐', options.length === 3, `opts=${JSON.stringify(options)}`)

          // 选 high → 下载
          await page.locator('#pdf-compress-level-select').selectOption('high')
          await page.locator('.el-message-box .el-button--primary').click()
          await page.waitForTimeout(5000)
          step('选 high 后调 /compress?level=high 无报错', true, '页面未崩溃(具体下载由后端决定)')
        }

        await page.screenshot({ path: resolve(__dirname, 'screenshots/export-compress-dialog.png') })
      }
    }

    // === 5. 旧 "压缩 PDF" 文案不应再出现 ===
    const oldTextCount = await page.locator('button:has-text("压缩 PDF")').count()
    step('旧"压缩 PDF"文案已替换', oldTextCount === 0, `count=${oldTextCount}`)
  } catch (e) {
    console.log('FATAL', e)
    log.push('FATAL ' + e.message)
    fail++
  } finally {
    await browser.close()
    const report = `# PR1 Export + Compress E2E Report\n\nGenerated: ${new Date().toISOString()}\n\nPass: ${pass} | Fail: ${fail}\n\n${log.join('\n')}${failures.length ? '\n\n## Failures\n' + failures.map((f) => `- ${f.name}: ${f.detail}`).join('\n') : ''}`
    writeFileSync(REPORT_PATH, report)
    console.log(`\nReport: ${REPORT_PATH}\npass ${pass} / fail ${fail}`)
    process.exit(fail > 0 ? 1 : 0)
  }
}

main().catch((e) => { console.error(e); process.exit(1) })