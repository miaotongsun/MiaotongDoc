/**
 * usePdfAnnotation —— 标注管理（高亮 / 评论 / 画笔 / 橡皮擦）
 *
 * 职责：
 *   - 当前页标注 CRUD（通过 Y.Array 持久化）
 *   - 工具切换（select / highlight / comment / draw / eraser）
 *   - 鼠标事件 → 标注操作（onMouseDown / Move / Up / Leave）
 *   - 标注样式计算（highlight / comment / draw path / preview rect）
 *   - 橡皮擦：命中检测 + 删除
 *
 * 设计原则：
 *   - 通过 props 接收 yAnnotations（来自 usePdfCollaborate）
 *   - 不耦合渲染层（不直接调用 renderPage）
 *   - 标注样式以纯函数暴露，template 直接调用
 *
 * Sprint 1 状态：骨架（TODO 标记），不接 PdfEditor.vue
 */

import { ref, computed } from 'vue'
import type { PdfAnnotation, PdfAnnotationRect } from './usePdfCollaborate'

export type AnnotationTool =
  | 'select'
  | 'move'
  | 'highlight'
  | 'comment'
  | 'draw'
  | 'eraser'
  | 'vqa'
  | 'textEdit'
  // Phase 10 形状工具
  | 'rectangle'
  | 'ellipse'
  | 'arrow'
  | 'line'
  | 'stamp'

export interface PendingRect {
  pageNumber: number
  startX: number
  startY: number
  x: number
  y: number
  width: number
  height: number
}

export interface UsePdfAnnotationOptions {
  /** Yjs 标注数组（来自 usePdfCollaborate.yAnnotations） */
  yAnnotations: Y.Array<PdfAnnotation>
  /** 当前用户 ID */
  userId: number
  /** 当前用户名 */
  userName: string
  /** 是否可编辑（无权限时所有事件失效） */
  canEdit?: boolean | (() => boolean)
}

const PREDEFINE_COLORS = [
  '#FFEB3B', '#FF9800', '#F44336', '#E91E63',
  '#9C27B0', '#3F51B5', '#2196F3', '#4CAF50',
]

// Y.Array 类型导入（避免循环依赖）
import type * as Y from 'yjs'

/**
 * 标注管理 composable
 *
 * @example
 * ```ts
 * const ann = usePdfAnnotation({ yAnnotations, userId, userName, canEdit: true })
 * ann.add({ type: 'highlight', pageNumber: 1, rect, color: '#FFEB3B' })
 * ann.remove(id)
 * ```
 */
export function usePdfAnnotation(options: UsePdfAnnotationOptions) {
  // ===== 状态 =====
  const annotations = ref<PdfAnnotation[]>([])
  const activeTool = ref<AnnotationTool>('select')
  const activeColor = ref('#FFEB3B')
  const stampText = ref('DRAFT')
  /** 图章预设文字(常用审批标签) */
  const stampPresets = ['DRAFT', 'APPROVED', 'REJECTED', 'CONFIDENTIAL', 'FINAL', 'REVIEWED', 'VOID', 'COPY'] as const
  const customStampText = ref('')
  function setStampText(text: string): void {
    stampText.value = (text || '').toUpperCase().slice(0, 16)
  }
  /** Phase 13.33: 图章库图片字段 —— 选中后 stampImageBase64 非空时优先用图片 */
  const stampImageBase64 = ref<string | null>(null)
  const stampLabel = ref<string>('DRAFT')
  const stampOrigW = ref(120)
  const stampOrigH = ref(40)
  function setStampImage(opts: { imageBase64: string; label: string; origW: number; origH: number }): void {
    stampImageBase64.value = opts.imageBase64
    stampLabel.value = opts.label || 'STAMP'
    stampOrigW.value = opts.origW || 120
    stampOrigH.value = opts.origH || 40
  }
  function clearStampImage(): void {
    stampImageBase64.value = null
    stampLabel.value = 'DRAFT'
  }
  /** Phase 13.33: 图章 ghost 位置(画布像素 + 页码) */
  const stampGhostPos = ref<{ pageNumber: number; x: number; y: number } | null>(null)
  const predefineColors = PREDEFINE_COLORS

  // 绘制中状态
  const isDrawing = ref(false)
  const currentPageDraw = ref(0)
  const drawPoints = ref<number[]>([])
  const pendingRect = ref<PendingRect | null>(null)
  const pendingCommentRect = ref<PendingRect | null>(null)
  // Phase 13.25: 橡皮擦跟手光标(画布像素 + 页码)
  const eraserCursor = ref<{ x: number; y: number; pageNumber: number } | null>(null)
  const eraserRadius = 15

  // 评论弹窗
  const showCommentDialog = ref(false)
  const editingComment = ref('')
  /** Phase 13.34: 当前正在编辑的评论 id(非空时 saveComment 走 update 分支) */
  const editingCommentId = ref<string | null>(null)
  /** Phase 13.35: 评论创建时的 scale(用于 saveComment 中转 PDF pt) */
  const commentRectScale = ref(1)
  /** Phase 13.35: 评论创建时的 pageRawHeight(用于 saveComment 中转 PDF pt) */
  const commentRectPageHeight = ref(0)

  // ===== 同步层 =====
  function syncFromY() {
    annotations.value = options.yAnnotations.toArray().map(a => ({ ...a }))
  }

  // 订阅 Y.Array 变化
  options.yAnnotations.observe(syncFromY)
  syncFromY()

  // ===== 权限 =====
  function checkEdit(): boolean {
    if (typeof options.canEdit === 'function') return options.canEdit()
    return options.canEdit !== false
  }

  // ===== CRUD =====
  function add(data: Partial<PdfAnnotation>): PdfAnnotation {
    const ann: PdfAnnotation = {
      id: `ann-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      type: data.type ?? 'highlight',
      pageNumber: data.pageNumber ?? 1,
      rect: data.rect,
      color: data.color ?? activeColor.value,
      content: data.content,
      points: data.points,
      strokeWidth: data.strokeWidth,
      stampText: data.stampText,
      userId: options.userId,
      userName: options.userName,
      createdAt: new Date().toISOString(),
    }
    options.yAnnotations.push([ann])
    return ann
  }

  function remove(id: string): void {
    const arr = options.yAnnotations.toArray()
    const idx = arr.findIndex(a => a.id === id)
    if (idx >= 0) options.yAnnotations.delete(idx, 1)
  }

  function update(id: string, patch: Partial<PdfAnnotation>): void {
    const arr = options.yAnnotations.toArray()
    const idx = arr.findIndex(a => a.id === id)
    if (idx < 0) return
    const updated = { ...arr[idx], ...patch }
    options.yAnnotations.delete(idx, 1)
    options.yAnnotations.insert(idx, [updated])
  }

  function getByPage(page: number, type?: AnnotationTool): PdfAnnotation[] {
    let list = annotations.value.filter(a => a.pageNumber === page)
    if (type) list = list.filter(a => a.type === type)
    return list
  }

  function getById(id: string): PdfAnnotation | undefined {
    return annotations.value.find(a => a.id === id)
  }

  // ===== 鼠标交互（高亮 / 评论 / 画笔） =====
  // Phase 13.25: 加 scale + pageRawHeight 参数,存储改 PDF pt(左下原点),
  //               解决 zoom 后标注漂移问题。预览仍用画布像素。

  function onMouseDown(
    e: MouseEvent,
    page: number,
    annotationLayerEl: HTMLDivElement | DOMRect | undefined,
    scale: number,
    pageRawHeight: number,
  ) {
    if (!checkEdit() || activeTool.value === 'select' || activeTool.value === 'move') return
    const pos = getRelPos(e, page, annotationLayerEl)
    if (activeTool.value === 'eraser') {
      eraseAt(pos.x, pos.y, page, scale, pageRawHeight)
      eraserCursor.value = { x: pos.x, y: pos.y, pageNumber: page }
      return
    }
    // vqa / highlight / comment / 形状 / 下划线 / 删除线 都用矩形框选：mousedown 起一个 pendingRect
    const isRectTool =
      activeTool.value === 'highlight' ||
      activeTool.value === 'comment' ||
      activeTool.value === 'vqa' ||
      activeTool.value === 'rectangle' ||
      activeTool.value === 'ellipse' ||
      activeTool.value === 'arrow' ||
      activeTool.value === 'line'
    if (isRectTool) {
      pendingRect.value = {
        pageNumber: page, startX: pos.x, startY: pos.y,
        x: pos.x, y: pos.y, width: 0, height: 0,
      }
    } else if (activeTool.value === 'draw') {
      isDrawing.value = true
      currentPageDraw.value = page
      drawPoints.value = [pos.x, pos.y]
    }
  }

  function onMouseMove(
    e: MouseEvent,
    page: number,
    annotationLayerEl: HTMLDivElement | DOMRect | undefined,
    scale: number,
    pageRawHeight: number,
  ) {
    if (!checkEdit()) return
    if (activeTool.value === 'move') return  // Phase 13.26: 手型工具由 PdfEditor pan 逻辑接管
    const pos = getRelPos(e, page, annotationLayerEl)
    if (activeTool.value === 'eraser') {
      // Phase 13.25: 跟手光标 + 按下时连续擦除
      eraserCursor.value = { x: pos.x, y: pos.y, pageNumber: page }
      if (e.buttons === 1) {
        eraseAt(pos.x, pos.y, page, scale, pageRawHeight)
      }
      return
    }
    // vqa / highlight / comment / shape tools 都跟随鼠标调整矩形
    const isRectTool =
      activeTool.value === 'highlight' ||
      activeTool.value === 'comment' ||
      activeTool.value === 'vqa' ||
      activeTool.value === 'rectangle' ||
      activeTool.value === 'ellipse' ||
      activeTool.value === 'arrow' ||
      activeTool.value === 'line'
    if (isRectTool && pendingRect.value) {
      // Phase 13.25: 即时归一化(Math.min/Math.abs) -> SVG <rect> 永远正宽高,
      //               向左/上拖动时预览不再消失
      const sx = pendingRect.value.startX
      const sy = pendingRect.value.startY
      pendingRect.value = {
        ...pendingRect.value,
        x: Math.min(sx, pos.x),
        y: Math.min(sy, pos.y),
        width: Math.abs(pos.x - sx),
        height: Math.abs(pos.y - sy),
      }
    } else if (activeTool.value === 'draw' && isDrawing.value) {
      drawPoints.value.push(pos.x, pos.y)
    }
    // Phase 13.33: stamp 工具 ghost 位置更新(画布像素)
    if (activeTool.value === 'stamp') {
      stampGhostPos.value = { pageNumber: page, x: pos.x, y: pos.y }
    } else if (stampGhostPos.value) {
      stampGhostPos.value = null
    }
  }

  function onMouseUp(
    e: MouseEvent,
    page: number,
    annotationLayerEl: HTMLDivElement | DOMRect | undefined,
    scale: number,
    pageRawHeight: number,
  ) {
    if (!checkEdit()) return
    // vqa 由 PdfEditor.vue 的 onAnnotationMouseUp 接管（需要截图上传 AI）
    if (activeTool.value === 'vqa') {
      // 把矩形归一化（正宽高）后留在 pendingRect 里，外层会读取
      if (pendingRect.value) {
        const r = pendingRect.value
        pendingRect.value = { ...r, x: Math.min(r.startX, r.x), y: Math.min(r.startY, r.y), width: Math.abs(r.width), height: Math.abs(r.height) }
      }
      return
    }
    if (pendingRect.value && (
      activeTool.value === 'highlight' ||
      activeTool.value === 'comment' ||
      activeTool.value === 'rectangle' ||
      activeTool.value === 'ellipse' ||
      activeTool.value === 'arrow' ||
      activeTool.value === 'line'
    )) {
      const r = pendingRect.value
      const w = Math.abs(r.width)
      const h = Math.abs(r.height)
      if (w > 8 && h > 4) {
        // Phase 13.25: 转换为 PDF pt(左下原点)持久化,解决 zoom 后漂移
        // canvasX/scale = PDF pt X;Y 翻转:pdfY = pageRawHeight - canvasBottom/scale
        const canvasLeft = Math.min(r.startX, r.x)
        const canvasTop = Math.min(r.startY, r.y)
        const pdfRect: PdfAnnotationRect = {
          x: canvasLeft / scale,
          y: pageRawHeight - (canvasTop + h) / scale,
          width: w / scale,
          height: h / scale,
          pageHeight: pageRawHeight,
        }
        // Phase 13.35: 对 line/arrow 保留原始起始/终点方向
        if (activeTool.value === 'arrow' || activeTool.value === 'line') {
          const endX = (r.startX === canvasLeft) ? canvasLeft + w : canvasLeft
          const endY = (r.startY === canvasTop) ? canvasTop + h : canvasTop
          pdfRect.startX = r.startX / scale
          pdfRect.startY = pageRawHeight - r.startY / scale
          pdfRect.endX = endX / scale
          pdfRect.endY = pageRawHeight - endY / scale
        }
        if (activeTool.value === 'comment') {
          // comment 用画布像素 rect 给弹窗定位(pendingCommentRect 仅 UI 用)
          pendingCommentRect.value = { ...r, x: canvasLeft, y: canvasTop, width: w, height: h }
          editingComment.value = ''
          showCommentDialog.value = true
          // Phase 13.35: 保存 scale 和 pageHeight,供 saveComment 转 PDF pt
          commentRectScale.value = scale
          commentRectPageHeight.value = pageRawHeight
        } else {
          add({
            type: activeTool.value as any,
            pageNumber: page,
            rect: pdfRect,
            color: activeColor.value,
            strokeWidth: 2,
          })
        }
      }
      pendingRect.value = null
    } else if (activeTool.value === 'draw' && isDrawing.value) {
      isDrawing.value = false
      if (drawPoints.value.length >= 4) {
        // Phase 13.25: 画笔点也转 PDF pt
        const pdfPoints: number[] = []
        for (let i = 0; i < drawPoints.value.length; i += 2) {
          pdfPoints.push(drawPoints.value[i] / scale)
          pdfPoints.push((pageRawHeight - drawPoints.value[i + 1] / scale))
        }
        add({
          type: 'draw', pageNumber: page,
          color: activeColor.value, points: pdfPoints,
        })
      }
      drawPoints.value = []
    } else if (activeTool.value === 'stamp') {
      // Phase 13.33: 双轨 —— 图片图章按原图比例 100pt 宽;文字图章外框按文字长度自适应
      const pos2 = getRelPos(e, page, annotationLayerEl)
      stampGhostPos.value = null
      if (stampImageBase64.value) {
        // 图片图章:按原图比例缩放 100pt 宽
        const W_PT = 100
        const ratio = stampOrigH.value / stampOrigW.value
        const H_PT = W_PT * (ratio || 40 / 120)
        const rect: PdfAnnotationRect = {
          x: (pos2.x / scale) - W_PT / 2,
          y: (pageRawHeight - pos2.y / scale) - H_PT / 2,
          width: W_PT / scale,
          height: H_PT / scale,
          pageHeight: pageRawHeight,
        }
        add({
          type: 'stamp',
          pageNumber: page,
          rect,
          color: activeColor.value,
          stampImageBase64: stampImageBase64.value,
          stampLabel: stampLabel.value,
          origW: stampOrigW.value,
          origH: stampOrigH.value,
        })
      } else {
        // 文字图章:外框按文字长度自适应(Acrobat 风格)
        const text = (stampText.value || 'DRAFT').toUpperCase()
        const fontPt = 18  // 与 PdfCanvas 渲染 font-size 一致(画布像素)
        const widthPdf = Math.max(80, text.length * fontPt * 0.65 + 20) / scale
        const heightPdf = 40 / scale
        const rect: PdfAnnotationRect = {
          x: (pos2.x / scale) - widthPdf / 2,
          y: (pageRawHeight - pos2.y / scale) - heightPdf / 2,
          width: widthPdf,
          height: heightPdf,
          pageHeight: pageRawHeight,
        }
        add({
          type: 'stamp',
          pageNumber: page,
          rect,
          color: activeColor.value,
          stampText: text,
        })
      }
    }
    if (activeTool.value === 'eraser') {
      eraserCursor.value = null
    }
  }

  function onMouseLeave() {
    if (isDrawing.value && drawPoints.value.length >= 4) {
      add({
        type: 'draw', pageNumber: currentPageDraw.value,
        color: activeColor.value, points: [...drawPoints.value],
      })
    }
    isDrawing.value = false
    drawPoints.value = []
    eraserCursor.value = null
  }

  // ===== 橡皮擦命中检测 =====
  // Phase 13.25: 标注改 PDF pt 存储,橡皮擦命中测试需把鼠标画布像素转 PDF pt 再比对
  function eraseAt(canvasX: number, canvasY: number, page: number, scale: number, pageRawHeight: number): void {
    const pdfX = canvasX / scale
    const pdfY = pageRawHeight - canvasY / scale
    const tolPdf = 8 / scale  // 8px 容差转 PDF pt
    const toDelete: string[] = []
    for (const ann of annotations.value) {
      if (ann.pageNumber !== page) continue
      if (ann.rect) {
        const r = ann.rect
        // PDF rect 是左下原点,但 x/left 和 width 同向;Y 用 pdfY 比对 [r.y, r.y+height]
        if (pdfX >= r.x - tolPdf && pdfX <= r.x + r.width + tolPdf &&
            pdfY >= r.y - tolPdf && pdfY <= r.y + r.height + tolPdf) {
          toDelete.push(ann.id)
          continue
        }
      }
      if (ann.points) {
        // points 已是 PDF pt
        const tol = 15 / scale
        for (let i = 0; i < ann.points.length; i += 2) {
          if (Math.abs(ann.points[i] - pdfX) < tol && Math.abs(ann.points[i + 1] - pdfY) < tol) {
            toDelete.push(ann.id)
            break
          }
        }
      }
    }
    for (const id of toDelete) remove(id)
  }

  // ===== 工具函数 =====

  function getRelPos(e: MouseEvent, page: number, layerEl: HTMLDivElement | DOMRect | undefined) {
    if (!layerEl) return { x: 0, y: 0 }
    const rect = (layerEl as HTMLElement).getBoundingClientRect
      ? (layerEl as HTMLElement).getBoundingClientRect()
      : (layerEl as DOMRect)
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }

  // ===== 样式计算（纯函数） =====

  function getHighlightStyle(ann: PdfAnnotation) {
    if (!ann.rect) return {}
    return {
      left: ann.rect.x + 'px',
      top: ann.rect.y + 'px',
      width: ann.rect.width + 'px',
      height: ann.rect.height + 'px',
      backgroundColor: ann.color,
      opacity: '0.4',
    }
  }

  function getAnnotationStyle(ann: PdfAnnotation) {
    if (!ann.rect) return {}
    return {
      left: ann.rect.x + 'px',
      top: ann.rect.y + 'px',
      width: ann.rect.width + 'px',
      height: ann.rect.height + 'px',
      border: `2px solid ${ann.color}`,
    }
  }

  function getDrawPath(ann: PdfAnnotation): string {
    if (!ann.points || ann.points.length < 4) return ''
    let d = `M ${ann.points[0]} ${ann.points[1]}`
    for (let i = 2; i < ann.points.length; i += 2) {
      d += ` L ${ann.points[i]} ${ann.points[i + 1]}`
    }
    return d
  }

  const currentDrawPath = computed(() => {
    if (drawPoints.value.length < 4) return ''
    let d = `M ${drawPoints.value[0]} ${drawPoints.value[1]}`
    for (let i = 2; i < drawPoints.value.length; i += 2) {
      d += ` L ${drawPoints.value[i]} ${drawPoints.value[i + 1]}`
    }
    return d
  })

  const rectPreviewStyle = computed(() => {
    if (!pendingRect.value) return {}
    const r = pendingRect.value
    return {
      left: Math.min(r.startX, r.x) + 'px',
      top: Math.min(r.startY, r.y) + 'px',
      width: Math.abs(r.width) + 'px',
      height: Math.abs(r.height) + 'px',
      backgroundColor: activeColor.value,
      opacity: '0.4',
      border: `2px solid ${activeColor.value}`,
    }
  })

  // ===== 评论弹窗 =====

  function saveComment() {
    // Phase 13.34: 编辑现有评论时走 update 分支
    if (editingCommentId.value) {
      const id = editingCommentId.value
      const newContent = editingComment.value.trim()
      if (newContent) {
        const idx = annotations.value.findIndex(a => a.id === id)
        if (idx >= 0) {
          const target = annotations.value[idx]
          const updated = { ...target, content: newContent }
          annotations.value.splice(idx, 1, updated)
          // 同步到 Y.Array
          const yIdx = options.yAnnotations.toArray().findIndex((a: PdfAnnotation) => a.id === id)
          if (yIdx >= 0) {
            options.yAnnotations.delete(yIdx, 1)
            options.yAnnotations.insert(yIdx, [updated])
          }
        }
      }
    } else if (editingComment.value.trim() && pendingCommentRect.value) {
      // Phase 13.35: 评论必须转 PDF pt 存储(画布像素 → PDF pt,否则缩放后位置漂移)
      const r = pendingCommentRect.value
      const scale = commentRectScale.value || 1
      const ph = commentRectPageHeight.value || 0
      const pdfRect: PdfAnnotationRect = {
        x: r.x / scale,
        y: ph - (r.y + r.height) / scale,
        width: r.width / scale,
        height: r.height / scale,
        pageHeight: ph,
      }
      add({
        type: 'comment',
        pageNumber: r.pageNumber,
        rect: pdfRect,
        color: activeColor.value,
        content: editingComment.value.trim(),
      })
    }
    showCommentDialog.value = false
    pendingCommentRect.value = null
    editingComment.value = ''
    editingCommentId.value = null
  }

  function openCommentForEdit(id: string) {
    const target = annotations.value.find(a => a.id === id)
    if (!target) return
    editingCommentId.value = id
    openComment(target)
  }

  function openComment(ann: PdfAnnotation) {
    editingComment.value = ann.content || ''
    if (ann.rect) {
      pendingCommentRect.value = {
        pageNumber: ann.pageNumber,
        startX: ann.rect.x, startY: ann.rect.y,
        x: ann.rect.x, y: ann.rect.y,
        width: ann.rect.width, height: ann.rect.height,
      }
    }
    showCommentDialog.value = true
  }

  function cancelComment() {
    showCommentDialog.value = false
    pendingCommentRect.value = null
    editingComment.value = ''
    editingCommentId.value = null
  }

  return {
    // state
    annotations,
    activeTool,
    activeColor,
    predefineColors,
    stampText,
    stampPresets,
    customStampText,
    setStampText,
    // Phase 13.33: 图章库 + ghost
    stampImageBase64,
    stampLabel,
    stampOrigW,
    stampOrigH,
    setStampImage,
    clearStampImage,
    stampGhostPos,
    isDrawing,
    currentPageDraw,
    drawPoints,
    pendingRect,
    pendingCommentRect,
    showCommentDialog,
    editingComment,
    editingCommentId,
    openCommentForEdit,
    // Phase 13.25: 橡皮擦跟手光标
    eraserCursor,
    eraserRadius,
    // computed
    currentDrawPath,
    rectPreviewStyle,
    // crud
    add,
    remove,
    update,
    getByPage,
    getById,
    // mouse events
    onMouseDown,
    onMouseMove,
    onMouseUp,
    onMouseLeave,
    eraseAt,
    // style helpers
    getHighlightStyle,
    getAnnotationStyle,
    getDrawPath,
    getRelPos,
    // comment dialog
    saveComment,
    openComment,
    cancelComment,
  }
}

export type UsePdfAnnotationReturn = ReturnType<typeof usePdfAnnotation>