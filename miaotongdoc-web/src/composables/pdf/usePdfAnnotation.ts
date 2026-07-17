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
  | 'underline'
  | 'strikethrough'
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
  const predefineColors = PREDEFINE_COLORS

  // 绘制中状态
  const isDrawing = ref(false)
  const currentPageDraw = ref(0)
  const drawPoints = ref<number[]>([])
  const pendingRect = ref<PendingRect | null>(null)
  const pendingCommentRect = ref<PendingRect | null>(null)

  // 评论弹窗
  const showCommentDialog = ref(false)
  const editingComment = ref('')

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

  function onMouseDown(e: MouseEvent, page: number, annotationLayerEl: HTMLDivElement | DOMRect | undefined) {
    if (!checkEdit() || activeTool.value === 'select') return
    const pos = getRelPos(e, page, annotationLayerEl)
    if (activeTool.value === 'eraser') {
      eraseAt(pos.x, pos.y, page)
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
      activeTool.value === 'line' ||
      activeTool.value === 'underline' ||
      activeTool.value === 'strikethrough'
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

  function onMouseMove(e: MouseEvent, page: number, annotationLayerEl: HTMLDivElement | DOMRect | undefined) {
    if (!checkEdit()) return
    const pos = getRelPos(e, page, annotationLayerEl)
    if (activeTool.value === 'eraser' && e.buttons === 1) {
      eraseAt(pos.x, pos.y, page)
      return
    }
    // vqa / highlight / comment / shape tools / underline / strikethrough 都跟随鼠标调整矩形
    const isRectTool =
      activeTool.value === 'highlight' ||
      activeTool.value === 'comment' ||
      activeTool.value === 'vqa' ||
      activeTool.value === 'rectangle' ||
      activeTool.value === 'ellipse' ||
      activeTool.value === 'arrow' ||
      activeTool.value === 'line' ||
      activeTool.value === 'underline' ||
      activeTool.value === 'strikethrough'
    if (isRectTool && pendingRect.value) {
      pendingRect.value.x = pos.x
      pendingRect.value.y = pos.y
      pendingRect.value.width = pos.x - pendingRect.value.startX
      pendingRect.value.height = pos.y - pendingRect.value.startY
    } else if (activeTool.value === 'draw' && isDrawing.value) {
      drawPoints.value.push(pos.x, pos.y)
    }
  }

  function onMouseUp(e: MouseEvent, page: number, annotationLayerEl: HTMLDivElement | DOMRect | undefined) {
    if (!checkEdit()) return
    // vqa 由 PdfEditor.vue 的 onAnnotationMouseUp 接管（需要截图上传 AI）
    if (activeTool.value === 'vqa') {
      // 把矩形归一化（正宽高）后留在 pendingRect 里，外层会读取
      if (pendingRect.value) {
        const r = pendingRect.value
        const x = Math.min(r.startX, r.x)
        const y = Math.min(r.startY, r.y)
        const w = Math.abs(r.width)
        const h = Math.abs(r.height)
        pendingRect.value = { ...r, x, y, width: w, height: h }
      }
      return
    }
    if (pendingRect.value && (
      activeTool.value === 'highlight' ||
      activeTool.value === 'comment' ||
      activeTool.value === 'rectangle' ||
      activeTool.value === 'ellipse' ||
      activeTool.value === 'arrow' ||
      activeTool.value === 'line' ||
      activeTool.value === 'underline' ||
      activeTool.value === 'strikethrough'
    )) {
      const r = pendingRect.value
      const w = Math.abs(r.width)
      const h = Math.abs(r.height)
      if (w > 8 && h > 4) {
        const rect: PdfAnnotationRect = {
          x: Math.min(r.startX, r.x), y: Math.min(r.startY, r.y),
          width: w, height: h,
        }
        if (activeTool.value === 'comment') {
          pendingCommentRect.value = { ...r, x: rect.x, y: rect.y, width: rect.width, height: rect.height }
          editingComment.value = ''
          showCommentDialog.value = true
        } else {
          add({
            type: activeTool.value as any,
            pageNumber: page,
            rect,
            color: activeColor.value,
            strokeWidth: 2,
          })
        }
      }
      pendingRect.value = null
    } else if (activeTool.value === 'draw' && isDrawing.value) {
      isDrawing.value = false
      if (drawPoints.value.length >= 4) {
        add({
          type: 'draw', pageNumber: page,
          color: activeColor.value, points: [...drawPoints.value],
        })
      }
      drawPoints.value = []
    } else if (activeTool.value === 'stamp') {
      // stamp - 点击放置,默认 120x40 居中
      const pos2 = getRelPos(e, page, annotationLayerEl)
      const w = 120, h = 40
      const rect: PdfAnnotationRect = { x: pos2.x - w / 2, y: pos2.y - h / 2, width: w, height: h }
      add({
        type: 'stamp',
        pageNumber: page,
        rect,
        color: activeColor.value,
        stampText: stampText.value,
      })
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
  }

  // ===== 橡皮擦命中检测 =====
  function eraseAt(x: number, y: number, page: number): void {
    const toDelete: string[] = []
    for (const ann of annotations.value) {
      if (ann.pageNumber !== page) continue
      if (ann.rect) {
        const r = ann.rect
        if (x >= r.x - 5 && x <= r.x + r.width + 5 && y >= r.y - 5 && y <= r.y + r.height + 5) {
          toDelete.push(ann.id)
          continue
        }
      }
      if (ann.points) {
        for (let i = 0; i < ann.points.length; i += 2) {
          if (Math.abs(ann.points[i] - x) < 15 && Math.abs(ann.points[i + 1] - y) < 15) {
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
    if (editingComment.value.trim() && pendingCommentRect.value) {
      const r = pendingCommentRect.value
      add({
        type: 'comment',
        pageNumber: r.pageNumber,
        rect: { x: r.x, y: r.y, width: r.width, height: r.height },
        color: activeColor.value,
        content: editingComment.value.trim(),
      })
    }
    showCommentDialog.value = false
    pendingCommentRect.value = null
    editingComment.value = ''
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
    isDrawing,
    currentPageDraw,
    drawPoints,
    pendingRect,
    pendingCommentRect,
    showCommentDialog,
    editingComment,
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