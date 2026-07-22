/**
 * usePdfRenderer —— PDF 渲染封装（pdfjs-dist）
 *
 * 职责：
 *   - PDF 文档加载 / 销毁
 *   - 单页渲染（主画布 + 文字层）
 *   - 缩略图批量渲染
 *   - 缩放控制（zoomIn / zoomOut / fitWidth / setScale）
 *   - 重新渲染（reRenderAll）
 *
 * 设计原则：
 *   - 骨架阶段：不接业务，只暴露接口 + 最小实现
 *   - canvas/thumb/textLayer 的 DOM Ref 通过参数注入（不在 composable 内维护）
 *   - 状态用 ref/shallowRef 暴露，主组件按需解构
 *   - onUnmounted 自动 destroy
 *
 * Sprint 1 状态：骨架（TODO 标记），不接 PdfEditor.vue
 */

import { ref, shallowRef, onUnmounted, type Ref } from 'vue'
import type * as PdfJs from 'pdfjs-dist'

export interface PdfPageRenderResult {
  pageNumber: number
  viewport: PdfJs.PageViewport
  canvas: HTMLCanvasElement
}

export interface UsePdfRendererOptions {
  /** PDF 文件 URL（带 token 的 GET 接口） */
  fileUrl: string
  /** JWT token（用于 PDF.js 请求鉴权） */
  token?: string | null
  /** 缩略图渲染缩放比 */
  thumbScale?: number
  /** 主画布渲染缩放比（默认 1.2） */
  initialScale?: number
  /** PDF.js worker CDN（默认 unpkg） */
  workerCdn?: string
}

/**
 * PDF 渲染 composable
 *
 * @example
 * ```ts
 * const renderer = usePdfRenderer({
 *   fileUrl: props.fileUrl,
 *   token: sessionStorage.getItem('token'),
 * })
 * await renderer.load()
 * await renderer.renderPage(1, canvasEl, textLayerEl)
 * ```
 */
export function usePdfRenderer(options: UsePdfRendererOptions) {
  // ===== 状态 =====
  const pdfjsLib = shallowRef<typeof PdfJs | null>(null)
  const pdfDoc = shallowRef<PdfJs.PDFDocumentProxy | null>(null)
  const totalPages = ref(0)
  const scale = ref(options.initialScale ?? 1.2)
  const pageWidth = ref(0)
  const pageHeight = ref(0)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  // Phase 11.8: 缩略图渲染并发锁
  let thumbsRendering = false

  // ===== 内部工具 =====

  /** 动态加载 pdfjs-dist 并配置 worker + cMap */
  async function ensurePdfjs(): Promise<typeof PdfJs> {
    if (pdfjsLib.value) return pdfjsLib.value
    const lib = await import('pdfjs-dist')
    // Phase 11.8: worker 用本地 public/pdf.worker.min.mjs,避免内网不通 unpkg CDN 时 getDocument 卡住
    const cdn = options.workerCdn
      ?? `${window.location.origin}/pdf.worker.min.mjs`
    lib.GlobalWorkerOptions.workerSrc = cdn
    // 配置 cMap:中文扫描件 / PaddleOCR 输出 / pdf2htmlEX 等工具
    // 生成的 PDF 常用 CIDFont 子集嵌入,需要 cMap 才能正确解析中文字符
    // cMap 资源已复制到 public/cmaps(构建时由 Vite 打到 dist/)
    // worker 内部通过此 URL 异步加载 .bcmap 文件
    // 注:pdfjs-dist 4.x 类型定义未声明这 3 个属性,用 as any 绕过 vue-tsc
    const gwo = lib.GlobalWorkerOptions as any
    gwo.cMapUrl = `${window.location.origin}/cmaps/`
    gwo.cMapPacked = true
    gwo.standardFontDataUrl = `${window.location.origin}/standard_fonts/`
    pdfjsLib.value = lib as any
    return lib as any
  }

  /** 移除 pdfjs 注入的 hiddenCanvasElement */
  function cleanupHiddenCanvases() {
    document.querySelectorAll('.hiddenCanvasElement').forEach(el => el.remove())
  }

  // ===== 公开 API =====

  /** 加载 PDF 文档（自动渲染缩略图 + 自适应宽度 + 第 1 页） */
  async function load(): Promise<void> {
    if (loading.value) return
    loading.value = true
    error.value = null
    console.log('[renderer] load start, fileUrl=', options.fileUrl)
    try {
      const lib = await ensurePdfjs()
      console.log('[renderer] pdfjs lib ready, version=', lib.version)
      console.log('[renderer] calling getDocument...')
      const loadingTask = lib.getDocument({
        url: options.fileUrl,
        httpHeaders: options.token ? { Authorization: `Bearer ${options.token}` } : {},
        // 中文扫描件 / PaddleOCR 输出需要 cMap 才能正确解析
        cMapUrl: `${window.location.origin}/cmaps/`,
        cMapPacked: true,
        standardFontDataUrl: `${window.location.origin}/standard_fonts/`,
        // 不使用系统字体回退(中文 TTF 加载耗时且可能阻塞),直接用 cMap 渲染
        disableFontFace: false,
        // 启用字体回退,内嵌 CIDFont 找不到时尝试 cMap 解析
        useSystemFonts: true,
        // 旧 PDF 的字体兼容
        verbosity: 0,
      })
      console.log('[renderer] getDocument returned task, onProgress available=', typeof loadingTask.onProgress)
      loadingTask.onProgress = (p: any) => {
        console.log('[renderer] onProgress:', p.loaded, '/', p.total, 'pct=', p.total ? Math.round((p.loaded / p.total) * 100) : '?')
      }
      const doc = await loadingTask.promise
      console.log('[renderer] PDF doc loaded, numPages=', doc.numPages)
      pdfDoc.value = doc
      totalPages.value = doc.numPages
    } catch (e) {
      console.error('[renderer] load failed:', e)
      error.value = e as Error
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 渲染指定页（主画布 + 文字层） */
  async function renderPage(
    pageNum: number,
    canvasEl: HTMLCanvasElement | undefined,
    textLayerEl: HTMLDivElement | undefined,
  ): Promise<void> {
    const doc = pdfDoc.value
    if (!doc || !canvasEl) return
    const page = await doc.getPage(pageNum)
    const viewport = page.getViewport({ scale: scale.value })
    // Phase 11.8: pdfjs 要求 canvas.width/height 为整数,浮点 viewport 会导致渲染异常
    canvasEl.width = Math.ceil(viewport.width)
    canvasEl.height = Math.ceil(viewport.height)
    pageWidth.value = canvasEl.width
    pageHeight.value = canvasEl.height
    await page.render({ canvasContext: canvasEl.getContext('2d')!, viewport }).promise

    if (textLayerEl) {
      textLayerEl.innerHTML = ''
      textLayerEl.style.width = canvasEl.width + 'px'
      textLayerEl.style.height = canvasEl.height + 'px'
      // Phase 13.23: pdfjs 4.x TextLayer 依赖 --scale-factor 定位 span,
      // 不设会导致 span 堆左上角、与 canvas 文字位置不一致(视觉重叠/错位)
      textLayerEl.style.setProperty('--scale-factor', String(scale.value))
      const textContent = await page.getTextContent()
      if (textContent.items.length > 0) {
        const lib = await ensurePdfjs()
        const textLayer = new (lib as any).TextLayer({
          textContentSource: textContent,
          container: textLayerEl,
          viewport,
          // 关键:不传 pageWidth/pageHeight 时 pdfjs 走百分比分支但分母 undefined → 0 → 堆左上角
          // 传 pageWidth/Height 强制走 calc(*--scale-factor) 像素分支,span 按 PDF 坐标 × scale 精确定位
          pageWidth: viewport.width,
          pageHeight: viewport.height,
        })
        await textLayer.render()
        // Phase 13.23: pdfjs 4.x TextLayer span 顶 = baseline - ascent(字顶),
        // canvas 文字"实际可见顶"在 em-box 顶 = baseline + fontSize(因为字形渲染到 em-box)。
        // 差值 ≈ 0.22*fontSize(0.78 ascender 比例),把每个 span top 下移 0.22em 让顶对齐 canvas 文字实际可见顶。
        // 注意:inline transform 已被 pdfjs 设为 scaleX(宽度调整),不能直接覆盖。
        // 改 style.top 增加 0.22*fontSize 像素偏移(读取 fontSize 解析)。
        const spans = textLayerEl.querySelectorAll('span')
        spans.forEach((sp: HTMLElement) => {
          // Phase 13.24: 显式 contenteditable=false 阻止 Chrome 把 span 当成可编辑元素,
          // 否则选中时会弹"字号 B/I/U + 颜色"浮动工具栏(Chrome 默认 contenteditable 行为)。
          // -webkit-user-modify: read-only 在 Chrome 不生效,这是唯一可靠的方案。
          sp.setAttribute('contenteditable', 'false')
          const m = sp.style.fontSize.match(/([\d.]+)px/)
          if (m) {
            const fs = parseFloat(m[1])
            const curTop = sp.style.top
            // 把 top 从百分比转回像素再加偏移
            const topMatch = curTop.match(/(-?[\d.]+)%/)
            if (topMatch) {
              const topPct = parseFloat(topMatch[1])
              const topPx = (topPct / 100) * viewport.height
              sp.style.top = `${((topPx - fs * 0.16) / viewport.height * 100).toFixed(2)}%`
            }
          }
        })
      }
    }
    cleanupHiddenCanvases()
  }

  /**
   * Phase 13.5: 用 OCR 数据注入 text layer(扫描件无原生文字层时)
   * 让用户能用原生方式选择 OCR 识别的文字
   * @param tokens OCR 文字坐标(PDF pt,左下原点)
   */
  async function renderOcrTextLayer(
    pageNum: number,
    tokens: Array<{ text: string; x: number; y: number; width: number; height: number }>,
    textLayerEl: HTMLDivElement | undefined,
  ): Promise<void> {
    const doc = pdfDoc.value
    if (!doc || !textLayerEl || tokens.length === 0) return
    try {
      const page = await doc.getPage(pageNum)
      const viewport = page.getViewport({ scale: scale.value })
      const lib = await ensurePdfjs()
      // 构造 pdfjs textContent:transform = [fontSize, 0, 0, fontSize, x, y]
      const items = tokens.map(t => ({
        str: t.text,
        transform: [t.height, 0, 0, t.height, t.x, t.y],
        width: t.width,
        height: t.height,
        dir: 'ltr',
        fontName: 'OCR',
      }))
      const textContent = { items, styles: {} }
      textLayerEl.innerHTML = ''
      textLayerEl.style.width = Math.ceil(viewport.width) + 'px'
      textLayerEl.style.height = Math.ceil(viewport.height) + 'px'
      textLayerEl.style.setProperty('--scale-factor', String(scale.value))
      const textLayer = new (lib as any).TextLayer({
        textContentSource: textContent,
        container: textLayerEl,
        viewport,
        pageWidth: viewport.width,
        pageHeight: viewport.height,
      })
      await textLayer.render()
      // Phase 13.23: 同 renderPage,下移 0.22em 让 span 顶 = canvas 文字实际可见顶
      const spans = textLayerEl.querySelectorAll('span')
      spans.forEach((sp: HTMLElement) => {
        // Phase 13.24: 阻止 Chrome contenteditable 浮动工具栏
        sp.setAttribute('contenteditable', 'false')
        const m = sp.style.fontSize.match(/([\d.]+)px/)
        if (m) {
          const fs = parseFloat(m[1])
          const topMatch = sp.style.top.match(/(-?[\d.]+)%/)
          if (topMatch) {
            const topPct = parseFloat(topMatch[1])
            const topPx = (topPct / 100) * viewport.height
            sp.style.top = `${((topPx - fs * 0.16) / viewport.height * 100).toFixed(2)}%`
          }
        }
      })
    } catch (e) {
      console.error(`[renderer] renderOcrTextLayer(${pageNum}) failed:`, e)
    }
  }

  /** 批量渲染所有缩略图 */
  async function renderAllThumbs(
    thumbElMap: Map<number, HTMLCanvasElement>,
  ): Promise<void> {
    const doc = pdfDoc.value
    if (!doc) return
    // Phase 11.8: 防止并发调用导致同一 canvas 多次 render() 报错
    if (thumbsRendering) return
    thumbsRendering = true
    try {
      const ts = options.thumbScale ?? 0.4
      for (let i = 1; i <= totalPages.value; i++) {
        const page = await doc.getPage(i)
        const viewport = page.getViewport({ scale: ts })
        const canvas = thumbElMap.get(i)
        if (!canvas) continue
        canvas.width = viewport.width
        canvas.height = viewport.height
        await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
      }
    } finally {
      thumbsRendering = false
    }
  }

  /** 重新渲染所有页（缩放后调用） */
  async function reRenderAll(
    canvasElMap: Map<number, HTMLCanvasElement>,
    textLayerElMap: Map<number, HTMLDivElement>,
  ): Promise<void> {
    for (let i = 1; i <= totalPages.value; i++) {
      await renderPage(i, canvasElMap.get(i), textLayerElMap.get(i))
    }
  }

  /** 放大 */
  async function zoomIn(
    canvasElMap: Map<number, HTMLCanvasElement>,
    textLayerElMap: Map<number, HTMLDivElement>,
  ): Promise<void> {
    scale.value = Math.min(scale.value + 0.2, 4)
    await reRenderAll(canvasElMap, textLayerElMap)
  }

  /** 缩小 */
  async function zoomOut(
    canvasElMap: Map<number, HTMLCanvasElement>,
    textLayerElMap: Map<number, HTMLDivElement>,
  ): Promise<void> {
    scale.value = Math.max(scale.value - 0.2, 0.3)
    await reRenderAll(canvasElMap, textLayerElMap)
  }

  /** 自适应容器宽度 */
  async function fitWidth(
    containerWidth: number,
    canvasElMap: Map<number, HTMLCanvasElement>,
    textLayerElMap: Map<number, HTMLDivElement>,
  ): Promise<void> {
    const doc = pdfDoc.value
    if (!doc || containerWidth <= 0) return
    const page = await doc.getPage(1)
    const vp = page.getViewport({ scale: 1 })
    const availableWidth = containerWidth - 16
    scale.value = Math.max(0.3, availableWidth / vp.width)
    await reRenderAll(canvasElMap, textLayerElMap)
  }

  /** 直接设置缩放比 */
  function setScale(newScale: number) {
    scale.value = Math.max(0.3, Math.min(newScale, 4))
  }

  /** 销毁 PDF 文档，释放 worker */
  function destroy() {
    console.log('[renderer] destroy called (soft — 不取消进行中的 load)')
    // 不调用 pdfDoc.value?.destroy():会中断进行中的网络请求
    // 只标记 doc 为 null,新 load 会覆盖
    pdfDoc.value = null
    totalPages.value = 0
  }

  // 自动清理
  onUnmounted(destroy)

  return {
    // state
    pdfDoc,
    totalPages,
    scale,
    pageWidth,
    pageHeight,
    loading,
    error,
    // actions
    load,
    renderPage,
    renderOcrTextLayer,
    renderAllThumbs,
    reRenderAll,
    zoomIn,
    zoomOut,
    fitWidth,
    setScale,
    destroy,
  }
}

export type UsePdfRendererReturn = ReturnType<typeof usePdfRenderer>