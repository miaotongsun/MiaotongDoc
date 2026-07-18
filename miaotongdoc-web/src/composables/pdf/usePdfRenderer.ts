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
    lib.GlobalWorkerOptions.cMapUrl = `${window.location.origin}/cmaps/`
    lib.GlobalWorkerOptions.cMapPacked = true
    lib.GlobalWorkerOptions.standardFontDataUrl = `${window.location.origin}/standard_fonts/`
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
      const textContent = await page.getTextContent()
      if (textContent.items.length > 0) {
        const lib = await ensurePdfjs()
        const textLayer = new (lib as any).TextLayer({
          textContentSource: textContent,
          container: textLayerEl,
          viewport,
        })
        await textLayer.render()
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
      const textLayer = new (lib as any).TextLayer({
        textContentSource: textContent,
        container: textLayerEl,
        viewport,
      })
      await textLayer.render()
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