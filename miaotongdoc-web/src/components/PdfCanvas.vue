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
        :aria-label="`PDF 第 ${pageNum} 页`"
      ></canvas>

      <!-- 层 2: 文字层(选择用) -->
      <div
        ref="textLayerRef"
        class="pdf-text-layer"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
      ></div>

      <!-- 层 3: 文本编辑层(Phase 2 启用) -->
      <div
        v-if="activeTool === 'textEdit'"
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
        <!-- pending rect (drawing preview) -->
        <rect
          v-if="pendingRect && pendingRect.pageNumber === pageNum"
          :x="pendingRect.x"
          :y="pendingRect.y"
          :width="pendingRect.width"
          :height="pendingRect.height"
          :style="pendingRectStyle"
        />
        <!-- 渲染此页所有标注 -->
        <g v-for="ann in pageAnnotations" :key="ann.id">
          <rect
            v-if="ann.type === 'highlight' && ann.rect"
            :x="ann.rect.x"
            :y="ann.rect.y"
            :width="ann.rect.width"
            :height="ann.rect.height"
            :style="highlightStyle(ann)"
          />
          <circle
            v-else-if="ann.type === 'comment' && ann.rect"
            :cx="(ann.rect.x ?? 0) + 12"
            :cy="(ann.rect.y ?? 0) + 12"
            r="12"
            :fill="ann.color"
            class="pdf-comment-marker"
          />
          <path
            v-else-if="ann.type === 'draw' && ann.points && ann.points.length > 1"
            :d="drawPath(ann.points)"
            :stroke="ann.color"
            stroke-width="2"
            fill="none"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <!-- Phase 10: 矩形 -->
          <rect
            v-else-if="ann.type === 'rectangle' && ann.rect"
            :x="ann.rect.x"
            :y="ann.rect.y"
            :width="ann.rect.width"
            :height="ann.rect.height"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
            rx="2"
          />
          <!-- Phase 10: 椭圆 -->
          <ellipse
            v-else-if="ann.type === 'ellipse' && ann.rect"
            :cx="ann.rect.x + ann.rect.width / 2"
            :cy="ann.rect.y + ann.rect.height / 2"
            :rx="Math.abs(ann.rect.width / 2)"
            :ry="Math.abs(ann.rect.height / 2)"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
          />
          <!-- Phase 10: 箭头 -->
          <path
            v-else-if="ann.type === 'arrow' && ann.rect"
            :d="arrowPath(ann.rect)"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            fill="none"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <!-- Phase 10: 直线 -->
          <line
            v-else-if="ann.type === 'line' && ann.rect"
            :x1="ann.rect.x"
            :y1="ann.rect.y"
            :x2="ann.rect.x + ann.rect.width"
            :y2="ann.rect.y + ann.rect.height"
            :stroke="ann.color"
            :stroke-width="ann.strokeWidth || 2"
            stroke-linecap="round"
          />
          <!-- Phase 10: 下划线/删除线 -->
          <line
            v-else-if="(ann.type === 'underline' || ann.type === 'strikethrough') && ann.rect"
            :x1="ann.rect.x"
            :y1="ann.type === 'underline' ? ann.rect.y + ann.rect.height - 1 : ann.rect.y + ann.rect.height / 2"
            :x2="ann.rect.x + ann.rect.width"
            :y2="ann.type === 'underline' ? ann.rect.y + ann.rect.height - 1 : ann.rect.y + ann.rect.height / 2"
            :stroke="ann.color"
            stroke-width="2"
            stroke-linecap="round"
          />
          <!-- Phase 10: 图章 -->
          <g v-else-if="ann.type === 'stamp' && ann.rect">
            <rect
              :x="ann.rect.x"
              :y="ann.rect.y"
              :width="ann.rect.width"
              :height="ann.rect.height"
              fill="none"
              :stroke="ann.color"
              stroke-width="2"
              rx="4"
            />
            <text
              :x="ann.rect.x + ann.rect.width / 2"
              :y="ann.rect.y + ann.rect.height / 2 + 6"
              :fill="ann.color"
              text-anchor="middle"
              font-size="18"
              font-weight="700"
              font-family="-apple-system, BlinkMacSystemFont, sans-serif"
              style="text-transform: uppercase; letter-spacing: 1px"
            >{{ ann.stampText || 'DRAFT' }}</text>
          </g>
        </g>
        <!-- current drawing -->
        <path
          v-if="drawingPath"
          :d="drawingPath"
          :stroke="activeColor"
          stroke-width="2"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
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

function drawPath(points: number[]): string {
  if (points.length < 2) return ''
  const cmds: string[] = [`M ${points[0]} ${points[1]}`]
  for (let i = 2; i < points.length; i += 2) {
    cmds.push(`L ${points[i]} ${points[i + 1]}`)
  }
  return cmds.join(' ')
}

/**
 * 箭头路径:从矩形对角线画一条线 + 末端三角箭头
 */
function arrowPath(rect: { x: number; y: number; width: number; height: number }): string {
  const x1 = rect.x
  const y1 = rect.y
  const x2 = rect.x + rect.width
  const y2 = rect.y + rect.height
  const angle = Math.atan2(y2 - y1, x2 - x1)
  const headLen = 12
  const wingAngle = Math.PI / 7
  // 箭头两个翼
  const w1x = x2 - headLen * Math.cos(angle - wingAngle)
  const w1y = y2 - headLen * Math.sin(angle - wingAngle)
  const w2x = x2 - headLen * Math.cos(angle + wingAngle)
  const w2y = y2 - headLen * Math.sin(angle + wingAngle)
  return `M ${x1} ${y1} L ${x2} ${y2} M ${w1x} ${w1y} L ${x2} ${y2} L ${w2x} ${w2y}`
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

.pdf-canvas-el {
  display: block;
  position: relative;
  z-index: var(--z-canvas);
  /* canvas 是替换元素,默认 width/height 属性 = 渲染尺寸 */
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
  pointer-events: auto;
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
