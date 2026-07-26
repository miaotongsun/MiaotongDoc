/**
 * tests/phase15-e2e.mjs — Phase 15 新建文档对话框合并 PDF + 模板可折叠 E2E
 *
 * 覆盖:
 * - 首页只剩一个"新建文档"按钮(创建 PDF 入口已合并)
 * - 打开对话框,5 种 docType 切换
 * - PDF 模式专属 Tab(空白 PDF / 图片转 PDF)显示/隐藏
 * - 模板区可折叠展开
 * - 模板按 docType 过滤(后端 ?docType= 验证)
 * - "空白文档"始终是模板区第一个
 * - 空白 PDF 创建流程(跳编辑器)
 * - Word 普通创建流程(跳编辑器)
 *
 * 用法:
 *   BASE=http://localhost:80 node tests/phase15-e2e.mjs
 *
 * 输出:tests/phase15-e2e-report.md
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const API_BASE = process.env.API_BASE || 'http://localhost:9004'
const REPORT_PATH = resolve(__dirname, 'phase15-e2e-report.md')
const SCREENSHOTS_DIR = resolve(__dirname, 'screenshots')

mkdirSync(SCREENSHOTS_DIR, { recursive: true })

let pass = 0, fail = 0
const failures = []
const log = []

function step(name, ok, detail = '') {
  const mark = ok ? '✅' : '❌'
  const line = `${mark} ${name}${detail ? ' — ' + detail : ''}`
  console.log(line)
  log.push(line)
  ok ? pass++ : fail++
  if (!ok) failures.push({ name, detail })
}

async function login(page) {
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle', timeout: 30000 })
  if (!page.url().includes('/login')) return
  await page.waitForSelector('input', { timeout: 10000 })
  const inputs = await page.locator('input').all()
  await inputs[0].fill('10000000')
  await inputs[1].fill('123456')
  const btn = page.locator('button:has-text("登录"), button[type="submit"], button.el-button--primary').first()
  await btn.click()
  await page.waitForURL(/\/(home|$)/, { timeout: 15000 })
}

async function openCreateDialog(page) {
  // 点首页顶栏"新建文档"按钮
  const btn = page.locator('button:has-text("新建文档")').first()
  await btn.waitFor({ state: 'visible', timeout: 5000 })
  await btn.click()
  // dialog title
  await page.waitForSelector('.el-dialog__title:has-text("新建文档")', { timeout: 5000 })
  await page.waitForTimeout(300)
}

async function selectDocType(page, type) {
  const card = page.locator(`.doc-type-item[data-doc-type="${type}"]`).first()
  await card.waitFor({ state: 'visible', timeout: 5000 })
  await card.click()
  await page.waitForTimeout(250)
}

async function getSelectedDocType(page) {
  return await page.locator('.doc-type-item.selected').first().getAttribute('data-doc-type').catch(() => null)
}

async function getTemplateListCount(page) {
  // 只统计非空(空模板占位不算)
  const items = await page.locator('.template-list .template-item').all()
  return items.length
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true })
  const page = await context.newPage()
  page.on('pageerror', (e) => console.log('  [PAGE ERROR]', e.message))
  page.on('console', (msg) => { if (msg.type() === 'error') console.log('  [CONSOLE ERROR]', msg.text()) })

  try {
    // ===== 1) 登录 =====
    console.log('\n## 1) 登录')
    await login(page)
    step('登录成功', !page.url().includes('/login'), `URL=${page.url()}`)

    // ===== 2) 获取 token(供后续 API 调用) =====
    console.log('\n## 2) 获取 token')
    const token = await page.evaluate(() => sessionStorage.getItem('token'))
    step('token 已获取', !!token, `token=${token?.substring(0, 20)}...`)

    // ===== 3) 首页按钮合并校验 =====
    console.log('\n## 3) 首页按钮合并校验')
    await page.waitForTimeout(800) // 等顶栏按钮渲染
    const newBtn = page.locator('button:has-text("新建文档")')
    const pdfBtn = page.locator('button:has-text("创建 PDF")')
    step('首页"新建文档"按钮存在', await newBtn.count() > 0, `count=${await newBtn.count()}`)
    step('首页"创建 PDF"按钮已移除', await pdfBtn.count() === 0, `count=${await pdfBtn.count()}`)
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, 'phase15-home-topbar.png') })

    // ===== 4) 打开对话框 =====
    console.log('\n## 4) 打开新建文档对话框')
    await openCreateDialog(page)
    step('对话框打开', await page.locator('.el-dialog__title:has-text("新建文档")').count() > 0)

    // ===== 5) 5 种 docType 切换 =====
    console.log('\n## 5) 5 种 docType 切换')
    const docTypes = ['word', 'cell', 'slide', 'markdown', 'pdf']
    for (const t of docTypes) {
      await selectDocType(page, t)
      const sel = await getSelectedDocType(page)
      step(`切换到 docType=${t}`, sel === t, `selected=${sel}`)
    }
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, 'phase15-doctype-pdf.png') })

    // ===== 6) 模板区可折叠 =====
    console.log('\n## 6) 模板区可折叠')
    // 当前应展开(默认)
    const collapseHeader = page.locator('.template-section-header').first()
    await collapseHeader.waitFor({ state: 'visible', timeout: 5000 })
    const browserBefore = await page.locator('.template-browser').count()
    step('模板区默认展开', browserBefore > 0, `count=${browserBefore}`)
    // 折叠
    await collapseHeader.click()
    await page.waitForTimeout(300)
    // v-show 隐藏后元素仍在 DOM 但不可见,用 isVisible 检测
    const visibleAfter = await page.locator('.template-browser').first().isVisible().catch(() => false)
    step('点击头部后模板区折叠(不可见)', visibleAfter === false, `visible=${visibleAfter}`)
    // 再展开
    await collapseHeader.click()
    await page.waitForTimeout(300)
    const visibleFinal = await page.locator('.template-browser').first().isVisible().catch(() => false)
    step('再点击展开模板区(可见)', visibleFinal === true, `visible=${visibleFinal}`)

    // ===== 7) "空白文档"始终存在 =====
    console.log('\n## 7) 空白文档始终是第一项')
    await selectDocType(page, 'word')
    const blankFirst = page.locator('.template-list .template-item').first()
    const blankName = await blankFirst.locator('.template-name').textContent().catch(() => '')
    step('Word 模板区首项是"空白文档"', (blankName || '').includes('空白文档'), `name="${blankName}"`)

    await selectDocType(page, 'pdf')
    const blankFirstPdf = page.locator('.template-list .template-item').first()
    const blankNamePdf = await blankFirstPdf.locator('.template-name').textContent().catch(() => '')
    step('PDF 模板区首项也是"空白文档"', (blankNamePdf || '').includes('空白文档'), `name="${blankNamePdf}"`)

    // ===== 8) 分类目录始终显示(后端配置为准,与模板是否 active 无关) =====
    console.log('\n## 8) 分类目录始终显示')
    const catItems = await page.locator('.template-categories .category-item').all()
    const catTexts = []
    for (const c of catItems) catTexts.push((await c.textContent() || '').trim())
    step('模板区左侧分类项 ≥ 3', catItems.length >= 3, `count=${catItems.length}, cats=${JSON.stringify(catTexts)}`)
    step('分类列表包含"全部"', catTexts.includes('全部'), `cats=${JSON.stringify(catTexts)}`)
    // 即使 templates 为空,后端配置的所有分类也要展示
    const allCatsRes = await page.evaluate(async (token) => {
      const r = await fetch('/api/templates/categories', { headers: { Authorization: `Bearer ${token}` } })
      return await r.json()
    }, token)
    const expectedCats = Array.isArray(allCatsRes) ? allCatsRes : []
    step('后端返回分类数 ≥ 3', expectedCats.length >= 3, `count=${expectedCats.length}`)
    // UI 显示的分类应至少包含后端分类中的部分(允许 UI 截断)
    const overlap = expectedCats.filter(c => catTexts.includes(c)).length
    step('UI 显示的分类包含后端配置的分类', overlap >= Math.min(3, expectedCats.length), `overlap=${overlap}/${expectedCats.length}`)

    // ===== 9) PDF 模式专属 Tab 显示/隐藏 =====
    console.log('\n## 9) PDF 模式 Tab')
    await selectDocType(page, 'pdf')
    const blankPdfTab = page.locator('.pdf-mode-tab:has-text("空白 PDF")').first()
    const imagesPdfTab = page.locator('.pdf-mode-tab:has-text("图片转 PDF")').first()
    step('PDF 模式显示"空白 PDF" Tab', await blankPdfTab.count() > 0)
    step('PDF 模式显示"图片转 PDF" Tab', await imagesPdfTab.count() > 0)

    // 切到非 PDF 时 PDF Tab 消失
    await selectDocType(page, 'word')
    const pdfTabOnWord = await page.locator('.pdf-mode-tabs').count()
    step('Word 模式下 PDF Tab 不渲染', pdfTabOnWord === 0, `count=${pdfTabOnWord}`)

    // 切回 PDF
    await selectDocType(page, 'pdf')

    // ===== 10) PDF blank 子模式 =====
    console.log('\n## 10) PDF blank 子模式')
    // 验证页数 + 纸张尺寸 输入控件存在
    const pagesInput = page.locator('.el-form-item:has(label:has-text("页数")) .el-input-number').first()
    step('页数输入控件存在', await pagesInput.count() > 0)
    const sizeSelect = page.locator('.el-form-item:has(label:has-text("纸张尺寸")) .el-select').first()
    step('纸张尺寸下拉框存在', await sizeSelect.count() > 0)

    // ===== 11) PDF images 子模式 =====
    console.log('\n## 11) PDF images 子模式')
    await imagesPdfTab.click()
    await page.waitForTimeout(300)
    const uploadArea = page.locator('.pdf-upload').first()
    step('图片拖拽上传区存在', await uploadArea.count() > 0)
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, 'phase15-pdf-images-mode.png') })

    // ===== 12) 后端模板过滤验证 =====
    console.log('\n## 12) 后端模板按 docType 过滤')
    for (const t of docTypes) {
      const res = await page.evaluate(async ({ token, t }) => {
        const r = await fetch(`/api/templates?docType=${t}`, { headers: { Authorization: `Bearer ${token}` } })
        return await r.json()
      }, { token, t })
      const list = Array.isArray(res) ? res : []
      // 后端对未知 docType 返回 [];对已知 docType 返回该类型的模板
      step(`GET /api/templates?docType=${t} 返回数组`, Array.isArray(res), `count=${list.length}`)
    }

    // ===== 13) 创建流程:空白 PDF =====
    console.log('\n## 13) 空白 PDF 创建流程')
    // 当前已在 PDF + images 模式,先切回 blank
    await blankPdfTab.click()
    await page.waitForTimeout(300)
    await selectDocType(page, 'pdf')
    await selectDocType(page, 'pdf') // 确保
    // 选模板区第一项(空白文档,form.templateId 默认 0)
    const blankTpl = page.locator('.template-list .template-item').first()
    await blankTpl.click()
    await page.waitForTimeout(200)
    // 填标题(可选)
    const titleInput = page.locator('.el-form-item:has(label:has-text("文档标题")) input').first()
    await titleInput.fill('Phase15 空白 PDF 测试')
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, 'phase15-pdf-blank-ready.png') })
    // 点创建
    const createBtn = page.locator('.create-doc-dialog button:has-text("创建")').first()
    await createBtn.click()
    // 等待跳转
    try {
      await page.waitForURL(/\/editor\/\d+/, { timeout: 15000 })
      step('空白 PDF 创建后跳到 /editor/{id}', /\/editor\/\d+/.test(page.url()), `url=${page.url()}`)
    } catch {
      step('空白 PDF 创建后跳到 /editor/{id}', false, `url=${page.url()}`)
    }
    // 回到 home
    await page.goto(`${BASE}/home`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(800)

    // ===== 14) 创建流程:Word 普通 =====
    console.log('\n## 14) Word 普通创建流程')
    await openCreateDialog(page)
    await selectDocType(page, 'word')
    const wordTitle = page.locator('.el-form-item:has(label:has-text("文档标题")) input').first()
    await wordTitle.fill('Phase15 Word 测试')
    const createBtn2 = page.locator('.create-doc-dialog button:has-text("创建")').first()
    await createBtn2.click()
    try {
      await page.waitForURL(/\/editor\/\d+/, { timeout: 15000 })
      step('Word 创建后跳到 /editor/{id}', /\/editor\/\d+/.test(page.url()), `url=${page.url()}`)
    } catch {
      step('Word 创建后跳到 /editor/{id}', false, `url=${page.url()}`)
    }

    // ===== 最终截图 =====
    await page.goto(`${BASE}/home`, { waitUntil: 'networkidle', timeout: 15000 })
    await openCreateDialog(page)
    await selectDocType(page, 'pdf')
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, 'phase15-final.png') })

  } catch (e) {
    console.log('FATAL:', e.message, e.stack)
    log.push(`FATAL: ${e.message}`)
  }

  await browser.close()

  const report = `# Phase 15 E2E 自动化测试报告

生成时间: ${new Date().toISOString()}

## 总览
通过: ${pass} | 失败: ${fail}

## 详情

${log.join('\n')}

${failures.length ? `\n## 失败列表\n\n${failures.map(f => `- **${f.name}**: ${f.detail}`).join('\n')}\n` : ''}
`
  writeFileSync(REPORT_PATH, report)
  console.log(`\n报告已写入: ${REPORT_PATH}`)
  console.log(`通过: ${pass} / 失败: ${fail}`)
  process.exit(fail > 0 ? 1 : 0)
}

main().catch((e) => { console.error(e); process.exit(1) })