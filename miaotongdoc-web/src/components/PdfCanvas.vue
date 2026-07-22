<!--
  PdfCanvas.vue —— 单页 PDF 渲染容器

  4 层堆叠(从下到上):
    1. <canvas>           PDF.js 原始渲染
    2. <div.text-layer>   pdfjs TextLayer(选择用,透明)
    3. <div.text-edit-layer>  文本编辑层(Phase 2 启用)
    4. <svg.annotation-layer>  标注层(高亮 / 评论 / 画笔 / 橡皮)

  职责:
    - 接收 pageNum + scale + pageWidth/Height
    - 触发 usePdfRenderer.renderPage() 渲染
    - 鼠标事件冒泡给父组件(标注系统)
    - emit ready(canvasEl, textLayerEl, pageEl) 给父组件建立 refs Map
-->
<template>
  <article class="pdf-page-card" :class="{ 'is-editing': isEditing }" :style="cardStyle">
    <header class="pdf-page-card-header">
      <div class="pdf-page-card-title">第 {{ pageNum }} 页 / 共 {{ totalPages }} 页</div>
      <div class="pdf-page-card-meta">
        <span>A4 · {{ pageMeta }}</span>
        <span aria-hidden="true">·</span>
        <span :class="recognizeStatusClass">
          {{ recognizeStatusLabel }}
        </span>
      </div>
    </header>

    <div
      class="pdf-page-canvas"
      :class="{ 'is-editing': isEditing, 'is-pan-mode': activeTool === 'move' }"
      :data-page-num="pageNum"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseLeave"
      @contextmenu.prevent="onContextMenu"
    >
      <!-- 层 1: canvas -->
      <!-- Phase 11.8: 移除 :width/:height 绑定,由 usePdfRenderer.renderPage 直接设置 canvas.width/height
           避免 Vue 响应式更新与 pdfjs render() 并发时清空 canvas 导致画布空白 -->
      <canvas
        ref="canvasRef"
        class="pdf-canvas-el"
        :class="{ 'is-edit-mode': activeTool === 'textEdit' }"
        :aria-label="`PDF 第 ${pageNum} 页`"
      ></canvas>

      <!-- 层 2: 文字层(选择用) -->
      <div
        ref="textLayerRef"
        class="pdf-text-layer"
        :class="{ 'is-edit-mode': activeTool === 'textEdit', 'is-pan-mode': activeTool === 'move' }"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
      ></div>

      <!-- 层 3: 文本编辑层(Phase 2 + Phase 13.22: 默认可见文字) -->
      <div
        class="pdf-text-edit-layer"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
      >
        <slot name="text-edit" :page-num="pageNum" :scale="scale"></slot>
      </div>

      <!-- 层 3.5: OCR 识别结果可视化(Phase 11.4 启用,始终显示) -->
      <div
        v-if="props.recognized"
        class="pdf-ocr-layer-wrap"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
      >
        <slot name="ocr" :page-num="pageNum" :scale="scale"></slot>
      </div>

      <!-- 层 4: 标注层 -->
      <svg
        ref="annotationRef"
        class="pdf-annotation-layer"
        :width="canvasWidth"
        :height="canvasHeight"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
        :viewBox="`0 0 ${canvasWidth} ${canvasHeight}`"
      >
        <!-- Phase 13.26: pending rect (drawing preview) 按 activeTool 分支渲染各自形状 -->
        <template v-if="pendingRect && pendingRect.pageNumber === pageNum">
          <!-- 矩形:实线边框 -->
          <rect
            v-if="activeTool === 'rectangle'"
            :x="pendingRect.x" :y="pendingRect.y"
            :width="pendingRect.width" :height="pendingRect.height"
            :stroke="activeColor" stroke-width="2" fill="none" rx="2"
            stroke-dasharray="4 2"
          />
          <!-- 椭圆 -->
          <ellipse
            v-else-if="activeTool === 'ellipse'"
            :cx="pendingRect.x + pendingRect.width / 2"
            :cy="pendingRect.y + pendingRect.height / 2"
            :rx="Math.abs(pendingRect.width / 2)"
            :ry="Math.abs(pendingRect.height / 2)"
            :stroke="activeColor" stroke-width="2" fill="none"
            stroke-dasharray="4 2"
          />
          <!-- 箭头 -->
          <path
            v-else-if="activeTool === 'arrow'"
            :d="arrowPathCanvas(toCanvasRectFromPending(pendingRect))"
            :stroke="activeColor" stroke-width="2" fill="none"
            stroke-linecap="round" stroke-linejoin="round"
          />
          <!-- 直线 -->
          <line
            v-else-if="activeTool === 'line'"
            :x1="pendingRect.x" :y1="pendingRect.y"
            :x2="pendingRect.x + pendingRect.width" :y2="pendingRect.y + pendingRect.height"
            :stroke="activeColor" stroke-width="2" stroke-linecap="round"
            stroke-dasharray="4 2"
          />
          <!-- 下划线(贴矩形底部) -->
          <line
            v-else-if="activeTool === 'underline'"
            :x1="pendingRect.x" :y1="pendingRect.y + pendingRect.height - 1"
            :x2="pendingRect.x + pendingRect.width" :y2="pendingRect.y + pendingRect.height - 1"
            :stroke="activeColor" stroke-width="2" stroke-linecap="round"
            stroke-dasharray="4 2"
          />
          <!-- 删除线(贴矩形中部) -->
          <line
            v-else-if="activeTool === 'strikethrough'"
            :x1="pendingRect.x" :y1="pendingRect.y + pendingRect.height / 2"
            :x2="pendingRect.x + pendingRect.width" :y2="pendingRect.y + pendingRect.height / 2"
            :stroke="activeColor" stroke-width="2" stroke-linecap="round"
            stroke-dasharray="4 2"
          />
          <!-- 高亮/评论/识图:保留半透明填充矩形(框选语义) -->
          <rect
            v-else
            :x="pendingRect.x" :y="pendingRect.y"
            :width="pendingRect.width" :height="pendingRect.height"
            :style="pendingRectStyle"
          />
        </template>
        <!-- 渲染此页所有标注 -->
        <g v-for="ann in pageAnnotations" :key="ann.id">
          <!-- Phase 13.25: ann.rect 存 PDF pt,渲染时 toCanvasRect 转 SVG 画布像素 + Y 翻转 -->
          <rect
            v-if="ann.type === 'highlight' && ann.rect"
            :x="toCanvasRect(ann.rect).x"
            :y="toCanvasRect(ann.rect).y"
            :width="toCanvasRect(ann.rect).w"
            :height="toCanvasRect(ann.rect).h"
            :style="highlightStyle(ann)"
          />
          <circle
            v-else-if="ann.type === 'comment' && ann.rect"
            :cx="toCanvasRect(ann.rect).x + 12"
            :cy="toCanvasRect(ann.rect).y + 12"
            r="12"
            :fill="ann.color"
            class="pdf-comment-marker"
          />
          <path
            v-else-if="ann.type === 'draw' && ann.points && ann.points.length > 1"
            :d="drawPathCanvas(ann.points)"
            :stroke="ann.color"
            stroke-width="2"
            fill="none"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <!-- Phase 10: 矩形 -->
          <rect
            v-else-if="ann.type === 'rectangle' && ann.rect"
            :x="toCanvasRect(ann.rect).x"
            :y="toCanvasRect(ann.rect).y"
            :width="toCanvasRect(ann.rect).w"
            :height="toCanvasRect(ann.rect).h"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
            rx="2"
          />
          <!-- Phase 10: 椭圆 -->
          <ellipse
            v-else-if="ann.type === 'ellipse' && ann.rect"
            :cx="toCanvasRect(ann.rect).x + toCanvasRect(ann.rect).w / 2"
            :cy="toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h / 2"
            :rx="Math.abs(toCanvasRect(ann.rect).w / 2)"
            :ry="Math.abs(toCanvasRect(ann.rect).h / 2)"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
          />
          <!-- Phase 10: 箭头 -->
          <path
            v-else-if="ann.type === 'arrow' && ann.rect"
            :d="arrowPathCanvas(toCanvasRect(ann.rect))"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <!-- Phase 10: 直线 -->
          <line
            v-else-if="ann.type === 'line' && ann.rect"
            :x1="toCanvasRect(ann.rect).x"
            :y1="toCanvasRect(ann.rect).y"
            :x2="toCanvasRect(ann.rect).x + toCanvasRect(ann.rect).w"
            :y2="toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            stroke-linecap="round"
          />
          <!-- Phase 10: 下划线/删除线 -->
          <line
            v-else-if="(ann.type === 'underline' || ann.type === 'strikethrough') && ann.rect"
            :x1="toCanvasRect(ann.rect).x"
            :y1="ann.type === 'underline' ? toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h - 1 : toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h / 2"
            :x2="toCanvasRect(ann.rect).x + toCanvasRect(ann.rect).w"
            :y2="ann.type === 'underline' ? toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h - 1 : toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h / 2"
            :stroke="ann.color"
            stroke-width="2"
            stroke-linecap="round"
          />
          <!-- Phase 10: 图章 -->
          <g v-else-if="ann.type === 'stamp' && ann.rect">
            <rect
              :x="toCanvasRect(ann.rect).x"
              :y="toCanvasRect(ann.rect).y"
              :width="toCanvasRect(ann.rect).w"
              :height="toCanvasRect(ann.rect).h"
              fill="none"
              :stroke="ann.color"
              stroke-width="2"
              rx="4"
            />
            <text
              :x="toCanvasRect(ann.rect).x + toCanvasRect(ann.rect).w / 2"
              :y="toCanvasRect(ann.rect).y + toCanvasRect(ann.rect).h / 2 + 6"
              :fill="ann.color"
              text-anchor="middle"
              font-size="18"
              font-weight="700"
              font-family="-apple-system, BlinkMacSystemFont, sans-serif"
              style="text-transform: uppercase; letter-spacing: 1px"
            >{{ ann.stampText || 'DRAFT' }}</text>
          </g>
        </g>
        <!-- current drawing (画笔实时轨迹,points 是画布像素,直接用) -->
        <path
          v-if="drawingPath"
          :d="drawingPath"
          :stroke="activeColor"
          stroke-width="2"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <!-- Phase 13.25: 橡皮擦跟手光标 -->
        <circle
          v-if="eraserCursor && eraserCursor.pageNumber === pageNum"
          :cx="eraserCursor.x"
          :cy="eraserCursor.y"
          :r="eraserRadius"
          fill="rgba(220, 38, 38, 0.12)"
          stroke="#DC2626"
          stroke-width="1.5"
          stroke-dasharray="3 2"
          pointer-events="none"
        />
        <!-- Phase 12.1: 表单字段临时高亮矩形(4 秒) -->
        <rect
          v-if="formHighlightBox"
          :x="formHighlightBox.x"
          :y="formHighlightBox.y"
          :width="formHighlightBox.w"
          :height="formHighlightBox.h"
          fill="rgba(64, 158, 255, 0.18)"
          stroke="#409EFF"
          stroke-width="2"
          stroke-dasharray="4 2"
          class="pdf-form-highlight"
        >
          <title>{{ formHighlight?.name }}</title>
        </rect>
      </svg>

      <!-- 加载占位 -->
      <div v-if="!rendered" class="pdf-page-skeleton" aria-hidden="true">
        <div class="pdf-page-skeleton-line" v-for="i in 6" :key="i" :style="{ width: 50 + Math.random() * 40 + '%' }"></div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import type { PdfAnnotation, PdfAnnotationRect } from '@/composables/pdf/usePdfCollaborate'
import type { AnnotationTool, PendingRect } from '@/composables/pdf/usePdfAnnotation'

const props = defineProps<{
  pageNum: number
  totalPages: number
  scale: number
  /** PDF 页面原始宽度 (pt) */
  pageRawWidth: number
  /** PDF 页面原始高度 (pt) */
  pageRawHeight: number
  /** 当前激活工具 */
  activeTool: AnnotationTool
  /** 当前标注颜色 */
  activeColor: string
  /** 此页所有标注(响应式) */
  annotations: PdfAnnotation[]
  /** 当前正在绘制的矩形(由父组件传入) */
  pendingRect: PendingRect | null
  /** 当前正在绘制的画笔路径 */
  drawingPath: string | null
  /** OCR 状态 */
  recognized?: boolean
  recognizing?: boolean
  /** 渲染完成的回调(供父组件收集 refs) */
  isRendered?: boolean
  /** Phase 12.1: 表单字段临时高亮(在画布上画矩形 4 秒) */
  formHighlight?: { x: number; y: number; w: number; h: number; name: string } | null
  /** Phase 13.8: 编辑模式(画布蓝色边框) */
  isEditing?: boolean
  /** Phase 13.25: 橡皮擦跟手光标(画布像素 + 页码) */
  eraserCursor?: { x: number; y: number; pageNumber: number } | null
  /** Phase 13.25: 橡皮擦光标半径(px) */
  eraserRadius?: number
}>()

const emit = defineEmits<{
  (e: 'ready', pageNum: number, canvasEl: HTMLCanvasElement, textLayerEl: HTMLDivElement, annotationEl: SVGSVGElement): void
  (e: 'mouse-down', evt: MouseEvent, pageNum: number, rect: DOMRect): void
  (e: 'mouse-move', evt: MouseEvent, pageNum: number, rect: DOMRect): void
  (e: 'mouse-up', evt: MouseEvent, pageNum: number, rect: DOMRect): void
  (e: 'mouse-leave', evt: MouseEvent, pageNum: number, rect: DOMRect): void
  /** Phase 13.8: 右键菜单 */
  (e: 'context-menu', x: number, y: number, pageNum: number): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const textLayerRef = ref<HTMLDivElement | null>(null)
const annotationRef = ref<SVGSVGElement | null>(null)

// 渲染状态(由父组件控制是否进入实际渲染)
const rendered = computed(() => props.isRendered ?? true)

// canvas 尺寸 = 原始 PDF 尺寸 * scale
const canvasWidth = computed(() => Math.round(props.pageRawWidth * props.scale))
const canvasHeight = computed(() => Math.round(props.pageRawHeight * props.scale))

// Phase 12.1: PDF 坐标(左下原点) -> SVG 坐标(左上原点)
const formHighlightBox = computed(() => {
  if (!props.formHighlight) return null
  const { x, y, w, h } = props.formHighlight
  const svgX = x * props.scale
  const svgY = canvasHeight.value - (y + h) * props.scale  // PDF 左下角 -> SVG 左上角
  return {
    x: svgX,
    y: svgY,
    w: w * props.scale,
    h: h * props.scale,
  }
})

/**
 * Phase 13.25: 把存储的 PDF pt rect(左下原点)转成 SVG 画布像素 rect(左上原点)
 * 用于渲染 highlight/rectangle/ellipse/arrow/line/underline/strikethrough/stamp。
 * @param r PDF pt rect { x, y, width, height }(y 是距 PDF 页底的距离)
 * @returns 画布像素 rect { x, y, w, h }(y 是距 SVG 顶的距离)
 */
function toCanvasRect(r: { x: number; y: number; width: number; height: number }) {
  return {
    x: r.x * props.scale,
    y: canvasHeight.value - (r.y + r.height) * props.scale,
    w: r.width * props.scale,
    h: r.height * props.scale,
  }
}

/**
 * Phase 13.25: 画笔 path(points 已存 PDF pt,左下原点) -> SVG d 字符串(画布像素,左上原点)
 */
function drawPathCanvas(points: number[]): string {
  if (!points || points.length < 4) return ''
  const cmds: string[] = [`M ${points[0] * props.scale} ${canvasHeight.value - points[1] * props.scale}`]
  for (let i = 2; i < points.length; i += 2) {
    cmds.push(`L ${points[i] * props.scale} ${canvasHeight.value - points[i + 1] * props.scale}`)
  }
  return cmds.join(' ')
}

/** Phase 13.25: 箭头 path(SVG 画布像素坐标) */
function arrowPathCanvas(r: { x: number; y: number; w: number; h: number }): string {
  const x1 = r.x
  const y1 = r.y
  const x2 = r.x + r.w
  const y2 = r.y + r.h
  const angle = Math.atan2(y2 - y1, x2 - x1)
  const headLen = 12
  const wingAngle = Math.PI / 7
  const w1x = x2 - headLen * Math.cos(angle - wingAngle)
  const w1y = y2 - headLen * Math.sin(angle - wingAngle)
  const w2x = x2 - headLen * Math.cos(angle + wingAngle)
  const w2y = y2 - headLen * Math.sin(angle + wingAngle)
  return `M ${x1} ${y1} L ${x2} ${y2} M ${w1x} ${w1y} L ${x2} ${y2} L ${w2x} ${w2y}`
}

/** Phase 13.26: pendingRect(画布像素,{x,y,width,height}) -> {x,y,w,h} 给 arrowPathCanvas 用 */
function toCanvasRectFromPending(r: { x: number; y: number; width: number; height: number }) {
  return { x: r.x, y: r.y, w: r.width, h: r.height }
}

const cardStyle = computed(() => ({
  width: canvasWidth.value + 'px',
}))

const pageMeta = computed(() => {
  const w = Math.round(props.pageRawWidth * 0.353) // pt → mm
  const h = Math.round(props.pageRawHeight * 0.353)
  return `${w} × ${h} mm`
})

const recognizeStatusLabel = computed(() => {
  if (props.recognizing) return '识别中…'
  if (props.recognized) return '已识别'
  return '未识别'
})
const recognizeStatusClass = computed(() => ({
  'is-recognized': !!props.recognized,
  'is-recognizing': !!props.recognizing,
}))

const pageAnnotations = computed(() =>
  props.annotations.filter(a => a.pageNumber === props.pageNum),
)

const pendingRectStyle = computed(() => ({
  fill: props.activeColor,
  fillOpacity: 0.2,
  stroke: props.activeColor,
  strokeWidth: 1.5,
  strokeDasharray: '4 4',
  pointerEvents: 'none' as const,
}))

function highlightStyle(ann: PdfAnnotation) {
  return {
    fill: ann.color,
    fillOpacity: 0.35,
    stroke: ann.color,
    strokeWidth: 1,
    pointerEvents: 'auto' as const,
  }
}

onMounted(async () => {
  await nextTick()
  if (canvasRef.value && textLayerRef.value && annotationRef.value) {
    emit('ready', props.pageNum, canvasRef.value, textLayerRef.value, annotationRef.value)
  }
})

// 监听 pageNum 变化,确保 ref 仍然 emit
watch(() => props.pageNum, async () => {
  await nextTick()
  if (canvasRef.value && textLayerRef.value && annotationRef.value) {
    emit('ready', props.pageNum, canvasRef.value, textLayerRef.value, annotationRef.value)
  }
})

// ====== 鼠标事件转发 ======
function makeRect(evt: MouseEvent): DOMRect {
  const target = evt.currentTarget as HTMLElement
  return target.getBoundingClientRect()
}

function onMouseDown(evt: MouseEvent) {
  emit('mouse-down', evt, props.pageNum, makeRect(evt))
}
function onMouseMove(evt: MouseEvent) {
  emit('mouse-move', evt, props.pageNum, makeRect(evt))
}
function onMouseUp(evt: MouseEvent) {
  emit('mouse-up', evt, props.pageNum, makeRect(evt))
}
function onMouseLeave(evt: MouseEvent) {
  emit('mouse-leave', evt, props.pageNum, makeRect(evt))
}
// Phase 13.8: 右键唤出快捷菜单
function onContextMenu(evt: MouseEvent) {
  emit('context-menu', evt.clientX, evt.clientY, props.pageNum)
}
</script>

<style scoped>
.pdf-page-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
  /* 不限制 overflow,让 canvas 撑出 card */
  position: relative;
  margin: 0 auto var(--space-6);
  transition: box-shadow var(--duration-base) var(--ease-out);
  /* 关键:不设 height,让内容自然撑开 */
  align-self: center;
  flex-shrink: 0;
}
.pdf-page-card:hover {
  box-shadow: var(--shadow-4);
}

/* Phase 13.8: 编辑模式 - Acrobat DC 风格蓝色边框 */
.pdf-page-card.is-editing {
  border: 2px solid var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-soft), var(--shadow-4);
}

.pdf-page-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-surface);
  font-size: var(--text-sm);
}
.pdf-page-card-title {
  font-weight: 600;
  color: var(--color-foreground);
}
.pdf-page-card-meta {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-variant-numeric: tabular-nums;
}
.pdf-page-card-meta .is-recognized {
  color: var(--color-success);
}
.pdf-page-card-meta .is-recognizing {
  color: var(--color-info);
}

.pdf-page-canvas {
  position: relative;
  background: var(--color-surface);
  user-select: text;
  margin: 0 auto;
}
/* Phase 13.26: 手型工具平移光标 */
.pdf-page-canvas.is-pan-mode {
  cursor: grab;
  user-select: none;
}
.pdf-page-canvas.is-pan-mode:active {
  cursor: grabbing;
}

.pdf-canvas-el {
  display: block;
  position: relative;
  z-index: var(--z-canvas);
  /* canvas 是替换元素,默认 width/height 属性 = 渲染尺寸 */
}
/* Phase 13.23: 编辑模式下淡化原文层,让编辑 token 成视觉主体 */
.pdf-canvas-el.is-edit-mode {
  opacity: 0.25;
  filter: grayscale(0.6);
  transition: opacity 200ms ease, filter 200ms ease;
}

.pdf-text-layer {
  position: absolute;
  inset: 0;
  left: 0;
  top: 0;
  z-index: calc(var(--z-canvas) + 1);
  color: transparent;
  line-height: 1;
  user-select: text;
  /* Phase 13.22: 默认接收事件允许框选复制;textEdit 工具下让可见编辑层接管 */
  pointer-events: auto;
}
/* Phase 13.24: pdfjs 给 span 显式 inline style.color,
   这里强制覆盖 → 文字隐形,只有 ::selection 高亮时显示色块。 */
.pdf-text-layer > span {
  color: transparent !important;
}
/* Phase 13.23: pdfjs 4.x TextLayer span 需 position:absolute,
   全局 CSS 在 pdf-dialogs.css(scoped [data-v-xxx] 无法匹配 pdfjs 动态 span) */

.pdf-text-layer.is-edit-mode {
  pointer-events: none;
}
/* Phase 13.27: 手型工具下文字层让出事件,避免点文字触发原生选区干扰平移 */
.pdf-text-layer.is-pan-mode {
  pointer-events: none;
  user-select: none !important;
  -webkit-user-select: none !important;
}
.pdf-text-layer.is-pan-mode span,
.pdf-text-layer.is-pan-mode * {
  user-select: none !important;
  -webkit-user-select: none !important;
  pointer-events: none !important;
}

/* Phase 13.10 + 13.23: 选中文字高亮改成极淡(几乎不可见),Acrobat 幽灵理念:
   文字层完全透明,选区高亮仅微弱提示,不影响复制。
   原色 rgba(100, 149, 237, 0.25) 太显眼 → 改为 rgba(100, 149, 237, 0.06)。
   pdfjs TextLayer 自身有 ::selection 默认蓝灰,需 !important 覆盖。 */
.pdf-text-layer ::selection,
.pdf-text-layer ::selection *,
.pdf-ocr-text ::selection,
.pdf-ocr-text ::selection * {
  background: rgba(100, 149, 237, 0.06) !important;
  color: inherit !important;
}

.pdf-text-edit-layer {
  position: absolute;
  inset: 0;
  left: 0;
  top: 0;
  z-index: calc(var(--z-canvas) + 2);
  pointer-events: none;
}

.pdf-annotation-layer {
  position: absolute;
  inset: 0;
  left: 0;
  top: 0;
  z-index: calc(var(--z-canvas) + 3);
  pointer-events: none;
}
.pdf-annotation-layer > * {
  pointer-events: auto;
}

.pdf-comment-marker {
  filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.3));
  cursor: pointer;
}

.pdf-page-skeleton {
  position: absolute;
  inset: var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  z-index: 0;
}
.pdf-page-skeleton-line {
  height: 8px;
  background: var(--color-surface-2);
  border-radius: var(--radius-sm);
  animation: shimmer 1.4s ease-in-out infinite;
}
@keyframes shimmer {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* Phase 12.1: 表单字段高亮闪烁 */
.pdf-form-highlight {
  animation: pdf-form-highlight-pulse 1s ease-in-out infinite;
  pointer-events: none;
}
@keyframes pdf-form-highlight-pulse {
  0%, 100% { stroke-opacity: 1; fill-opacity: 0.18; }
  50% { stroke-opacity: 0.6; fill-opacity: 0.32; }
}
</style>
