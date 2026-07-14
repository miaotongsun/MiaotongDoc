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

  // ===== 内部工具 =====

  /** 动态加载 pdfjs-dist 并配置 worker */
  async function ensurePdfjs(): Promise<typeof PdfJs> {
    if (pdfjsLib.value) return pdfjsLib.value
    const lib = await import('pdfjs-dist')
    const cdn = options.workerCdn
      ?? `https://unpkg.com/pdfjs-dist@${lib.version}/build/pdf.worker.min.mjs`
    lib.GlobalWorkerOptions.workerSrc = cdn
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
    try {
      const lib = await ensurePdfjs()
      const doc = await lib.getDocument({
        url: options.fileUrl,
        httpHeaders: options.token ? { Authorization: `Bearer ${options.token}` } : {},
      }).promise
      pdfDoc.value = doc
      totalPages.value = doc.numPages
    } catch (e) {
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
    canvasEl.width = viewport.width
    canvasEl.height = viewport.height
    pageWidth.value = viewport.width
    pageHeight.value = viewport.height
    await page.render({ canvasContext: canvasEl.getContext('2d')!, viewport }).promise

    if (textLayerEl) {
      textLayerEl.innerHTML = ''
      textLayerEl.style.width = viewport.width + 'px'
      textLayerEl.style.height = viewport.height + 'px'
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

  /** 批量渲染所有缩略图 */
  async function renderAllThumbs(
    thumbElMap: Map<number, HTMLCanvasElement>,
  ): Promise<void> {
    const doc = pdfDoc.value
    if (!doc) return
    const ts = options.thumbScale ?? 0.15
    for (let i = 1; i <= totalPages.value; i++) {
      const page = await doc.getPage(i)
      const viewport = page.getViewport({ scale: ts })
      const canvas = thumbElMap.get(i)
      if (!canvas) continue
      canvas.width = viewport.width
      canvas.height = viewport.height
      await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
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
    pdfDoc.value?.destroy()
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