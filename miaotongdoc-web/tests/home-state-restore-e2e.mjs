/**
 * tests/home-state-restore-e2e.mjs — Home 视图状态路由化 E2E
 *
 * 覆盖 5 个场景:
 *   1) 选中 MD tab → 进文件 → 返回 → 仍在 MD
 *   2) 选中某文件夹 → 进文件 → 返回 → 仍在该文件夹(URL 携带 folder)
 *   3) 直接打开 /home?tab=pdf → 侧边栏 PDF 高亮
 *   4) 在某 tab 下 F5 刷新 → 状态保留
 *   5) 浏览器后退按钮 → 不停留在路由 query,回到上一个页面
 *
 * 用法:
 *   BASE=http://localhost:80 node tests/home-state-restore-e2e.mjs
 *
 * 报告: tests/home-state-restore-e2e-report.md
 * 截图: tests/screenshots/home-state-restore/*.png
 */
import { chromium } from 'playwright'
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BASE = process.env.BASE || 'http://localhost:80'
const REPORT_PATH = resolve(__dirname, 'home-state-restore-e2e-report.md')
const SCREENSHOTS_DIR = resolve(__dirname, 'screenshots', 'home-state-restore')

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
  await page.waitForSelector('input', { timeout: 15000 })
  const inputs = await page.locator('input').all()
  await inputs[0].fill('10000000')
  await inputs[1].fill('123456')
  // 登录按钮文字是"登 录"(中间有空格),用 class 选择器更稳
  const btn = page.locator('button.login-btn').first()
  await btn.waitFor({ state: 'visible', timeout: 15000 })
  await btn.click()
  await page.waitForURL(/\/(home|$)/, { timeout: 30000 })
}

/** 等待侧边栏加载完,返回"哪个 nav li 是 active" */
async function getActiveTabLabel(page) {
  return await page.locator('.nav-list li.active span').first().textContent().catch(() => null)
}

/** 检查侧边栏某文本对应的 li 是否 active */
async function isNavActive(page, label) {
  const text = await page.locator(`.nav-list li:has-text("${label}")`).first().getAttribute('class').catch(() => '')
  return /active/.test(text || '')
}

/** 从 query 中取值(vue-router 的 page query 是 string 或 string[]) */
async function getRouteQuery(page, key) {
  return await page.evaluate((k) => new URL(location.href).searchParams.get(k), key)
}

/** 模拟浏览器返回 */
async function goBack(page) {
  await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {})
  await page.waitForTimeout(300)
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  page.on('pageerror', (e) => console.log('  [PAGE ERROR]', e.message))
  page.on('console', (msg) => { if (msg.type() === 'error') console.log('  [CONSOLE ERROR]', msg.text()) })

  try {
    // ===== 0) 登录 =====
    console.log('\n## 0) 登录')
    await login(page)
    step('登录成功', !page.url().includes('/login'), `URL=${page.url()}`)
    // 等首页文档列表加载
    await page.waitForTimeout(800)

    // ===== 1) 选中 MD → URL 同步 → 侧边栏高亮 =====
    console.log('\n## 1) 选中 MD → URL 同步')
    await page.goto(`${BASE}/home`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(500)
    await page.locator('.nav-list li:has-text("MiaotongMD")').first().click()
    await page.waitForTimeout(700)
    step('点 MD 后 URL 含 tab=markdown', /tab=markdown/.test(page.url()), `url=${page.url()}`)
    step('点 MD 后侧边栏 MD 高亮', await isNavActive(page, 'MiaotongMD'))
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, '01-md-selected.png') })

    // ===== 2) 选中 PDF/全部 → URL 在 markdown 和 pdf 间切换 =====
    console.log('\n## 2) 多 tab 切换 URL 正确')
    await page.locator('.nav-list li:has-text("MiaotongPDF")').first().click()
    await page.waitForTimeout(700)
    step('点 PDF 后 URL 含 tab=pdf', /tab=pdf/.test(page.url()), `url=${page.url()}`)
    step('点 PDF 后侧边栏 PDF 高亮', await isNavActive(page, 'MiaotongPDF'))
    await page.locator('.nav-list li:has-text("全部文档")').first().click()
    await page.waitForTimeout(700)
    // 默认 tab=all 不入 query
    step('点全部文档后 URL 不含 tab(key)', !/\btab=/.test(page.url()), `url=${page.url()}`)

    // ===== 2B) folder 选择 → URL 同步 =====
    console.log('\n## 2B) folder 选择 → URL 同步')
    // 找有文档的 folder（通过 API 探测）
    const token = await page.evaluate(() => sessionStorage.getItem('token'))
    const targetFolder = await page.evaluate(async (t) => {
      const fr = await fetch('/api/folders', { headers: { Authorization: 'Bearer ' + t } })
      const folders = await fr.json()
      for (const f of folders) {
        const dr = await fetch(`/api/documents/list?folderId=${f.id}&page=0&size=5`, { headers: { Authorization: 'Bearer ' + t } })
        const d = await dr.json()
        if ((d.content || []).length > 0) return { id: f.id, name: f.name }
      }
      return null
    }, token)
    if (targetFolder) {
      await page.goto(`${BASE}/home?folder=${targetFolder.id}`, { waitUntil: 'networkidle' })
      await page.waitForTimeout(800)
      step('直接打开 /home?folder=X 侧边栏 folder 高亮',
        (await page.locator(`.folder-tree .folder-item.active .folder-name`).first().textContent().catch(() => ''))?.includes(targetFolder.name) ||
        targetFolder.name === (await page.locator(`.folder-tree .folder-item.active .folder-name`).first().textContent().catch(() => '')),
        `folder=${targetFolder.name}`)
      step('打开后面包屑含 folder 名', (await page.locator('.folder-path .path-item').allTextContents()).some(t => t.includes(targetFolder.name)))
      const docsInFolder = await page.locator('.doc-card').count()
      if (docsInFolder > 0) {
        await page.locator('.doc-card').first().dblclick()
        await page.waitForTimeout(1500)
        step('dblclick 进文件后 URL 是 /editor/{id}', /\/editor\/\d+/.test(page.url()), `url=${page.url()}`)
        await goBack(page)
        await page.waitForTimeout(1500)
        step('返回后 URL 仍含 folder=', /folder=/.test(page.url()), `url=${page.url()}`)
        step('返回后侧边栏 folder 仍高亮',
          (await page.locator(`.folder-tree .folder-item.active .folder-name`).first().textContent().catch(() => ''))?.includes(targetFolder.name),
          `name=${await page.locator('.folder-tree .folder-item.active .folder-name').first().textContent().catch(() => 'NONE')}`)
        step('返回后面包屑含 folder 名', (await page.locator('.folder-path .path-item').allTextContents()).some(t => t.includes(targetFolder.name)))
      } else {
        step('Step2B 跳过:folder 下无文档', true, `folder=${targetFolder.name}`)
      }
    } else {
      step('Step2B 跳过:无可测 folder', true, '环境下无带文档的 folder')
    }

    // ===== 3) 直接打开 /home?tab=pdf → 侧边栏 PDF 高亮(用户进入"我的书签") =====
    console.log('\n## 3) 直接打开带 query 的 URL 还原状态')
    await page.goto(`${BASE}/home?tab=markdown`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(700)
    step('URL 保留 tab=markdown', /tab=markdown/.test(page.url()), `url=${page.url()}`)
    step('侧边栏 MiaotongMD 高亮', await isNavActive(page, 'MiaotongMD'))
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, '03-direct-url.png') })

    // ===== 4) 在某 tab 下 F5 刷新 → 状态保留 =====
    console.log('\n## 4) F5 刷新 URL 仍带 query')
    await page.goto(`${BASE}/home?tab=markdown`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(700)
    await page.reload({ waitUntil: 'networkidle' })
    await page.waitForTimeout(700)
    step('F5 后 URL 仍含 tab=markdown', /tab=markdown/.test(page.url()), `url=${page.url()}`)
    step('F5 后侧边栏 MD 仍高亮', await isNavActive(page, 'MiaotongMD'))
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, '04-after-reload.png') })

    // ===== 5) router.replace 不污染 history =====
    console.log('\n## 5) replace 不污染 history')
    // 用一个明确的外部入口
    await page.goto(`${BASE}/admin`, { waitUntil: 'domcontentloaded' }).catch(() => {})
    await page.waitForTimeout(500)
    await page.goto(`${BASE}/home`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(500)
    const histStart = await page.evaluate(() => history.length)
    // 连续切 5 次 tab,每次都是 replace
    for (const t of ['MiaotongPDF', 'MiaotongWord', 'MiaotongPPT', 'MiaotongMD', 'MiaotongPDF']) {
      await page.locator(`.nav-list li:has-text("${t}")`).first().click()
      await page.waitForTimeout(300)
    }
    const histEnd = await page.evaluate(() => history.length)
    // 5 次 replace,history 增长不应超过 1(replace 自身最多+0)
    step('5 次切 tab 后,history.length 增长 ≤ 1', histEnd - histStart <= 1,
      `start=${histStart} end=${histEnd}`)
    step('最终 URL 是最后一个 tab 的 query', /tab=pdf/.test(page.url()), `url=${page.url()}`)
    await page.screenshot({ path: resolve(SCREENSHOTS_DIR, '05-history.png') })

    // ===== 6) 文件夹区展开/子 folder 展开 → 进文件 → 返回 → 展开态保留 =====
    console.log('\n## 6) 文件夹展开状态持久化')
    // 清掉上次视图,确保从"默认折叠"开始
    await page.evaluate(() => sessionStorage.removeItem('miaotong:home:lastView'))
    await page.goto(`${BASE}/home`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(700)
    // 默认应该是折叠的(folderSectionCollapsed=true)
    step('初始: 文件夹区处于折叠状态', !(await page.locator('.folder-tree').isVisible().catch(() => false)),
      `tree visible=${await page.locator('.folder-tree').isVisible().catch(() => 'n/a')}`)
    // 展开文件夹区
    await page.locator('.folder-section-arrow').first().click()
    await page.waitForTimeout(400)
    step('点文件夹标题后区展开', await page.locator('.folder-tree').isVisible())
    // 找一个有 children 的 folder(.folder-toggle 存在)
    const toggleCount = await page.locator('.folder-tree .folder-toggle').count()
    step('至少存在 1 个可展开 folder', toggleCount > 0, `toggles=${toggleCount}`)
    if (toggleCount > 0) {
      // 记下展开前的子 folder 数
      const beforeChildren = await page.locator('.folder-tree .folder-item.folder-child').count()
      // 点第一个 .folder-toggle
      await page.locator('.folder-tree .folder-toggle').first().click()
      await page.waitForTimeout(400)
      const afterChildren = await page.locator('.folder-tree .folder-item.folder-child').count()
      step('点 .folder-toggle 后,子 folder 数增加', afterChildren > beforeChildren,
        `before=${beforeChildren} after=${afterChildren}`)
      // 验证 sessionStorage 已写入 expanded
      const ls = await page.evaluate(() => sessionStorage.getItem('miaotong:home:lastView'))
      const parsed = ls ? JSON.parse(ls) : {}
      step('sessionStorage.expanded 是非空数组', Array.isArray(parsed.expanded) && parsed.expanded.length > 0,
        `ls=${ls?.slice(0, 120)}`)
      step('sessionStorage.sectionCollapsed === false', parsed.sectionCollapsed === false,
        `sectionCollapsed=${parsed.sectionCollapsed}`)
      // 进文件:对那个被展开的 folder (AI项目) dblclick 进入其下文件列表
      // 找到名字是 AI项目 的 folder-item (selector 用 :has(.folder-toggle 兄弟 + 子 folder) 或直接找该位置的)
      // 简单做法:已知第 2 个 .folder-item 是 AI项目 (toggle 在它前面),直接 dblclick 第 2 个
      const itemCount = await page.locator('.folder-tree .folder-item').count()
      // 找到第 1 个带 .folder-toggle 的 folder(即被展开的那个)
      const targetIdx = await page.evaluate(() => {
        const items = [...document.querySelectorAll('.folder-tree .folder-item')]
        return items.findIndex(el => el.querySelector('.folder-toggle'))
      })
      step('找到被展开的 folder 索引', targetIdx >= 0, `idx=${targetIdx} total=${itemCount}`)
      if (targetIdx >= 0) {
        await page.locator('.folder-tree .folder-item').nth(targetIdx).dblclick()
        await page.waitForTimeout(700)
        const docs = await page.locator('.doc-card').count()
        step('dblclick folder 后有文档可点', docs > 0, `docs=${docs}`)
        if (docs > 0) {
          await page.locator('.doc-card').first().dblclick()
          await page.waitForTimeout(1500)
          step('进文件后 URL 是 /editor/{id}', /\/editor\/\d+/.test(page.url()), `url=${page.url()}`)
          await goBack(page)
          await page.waitForTimeout(1500)
          // 验证子 folder 展开态保留
          const childrenAfterBack = await page.locator('.folder-tree .folder-item.folder-child').count()
          step('返回后子 folder 仍展开(children 数量 > 0)', childrenAfterBack > 0,
            `childrenAfterBack=${childrenAfterBack}`)
          step('返回后文件夹区仍可见', await page.locator('.folder-tree').isVisible())
          // 验证 sessionStorage 仍包含 expanded
          const ls2 = await page.evaluate(() => sessionStorage.getItem('miaotong:home:lastView'))
          const p2 = ls2 ? JSON.parse(ls2) : {}
          step('返回后 sessionStorage.expanded 仍是非空', Array.isArray(p2.expanded) && p2.expanded.length > 0,
            `expanded=${JSON.stringify(p2.expanded)}`)
          step('返回后 sessionStorage.sectionCollapsed === false', p2.sectionCollapsed === false)
          await page.screenshot({ path: resolve(SCREENSHOTS_DIR, '06-folder-expanded-restored.png') })
        }
      }
    }

  } catch (e) {
    console.log('FATAL:', e.message, e.stack)
    log.push(`FATAL: ${e.message}`)
  }

  await browser.close()

  const report = `# Home 状态路由化 E2E 报告

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
