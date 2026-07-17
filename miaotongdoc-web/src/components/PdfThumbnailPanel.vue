<!--
  PdfThumbnailPanel.vue —— 左侧缩略图栏

  职责:
    - 显示所有页面缩略图(由 usePdfRenderer.renderAllThumbs 渲染)
    - 点击跳转页面
    - 悬停显示旋转/更多按钮
    - HTML5 拖拽重排(emit reorder 事件)
    - 右键菜单(emit context-menu 事件)
-->
<template>
  <aside class="pdf-thumb-rail" aria-label="页面缩略图">
    <div class="pdf-thumb-header">
      <span>页面</span>
      <span>{{ totalPages }} 页</span>
    </div>

    <div class="pdf-thumb-list">
      <div
        v-for="i in totalPages"
        :key="i"
        class="pdf-thumb-item"
        :class="{
          'is-current': i === currentPage,
          'is-dragging': dragFrom === i,
        }"
        :draggable="true"
        @click="$emit('goto', i)"
        @contextmenu.prevent="onContextMenu($event, i)"
        @dragstart="onDragStart($event, i)"
        @dragover.prevent="onDragOver($event, i)"
        @dragend="onDragEnd"
        @drop.prevent="onDrop($event, i)"
      >
        <div class="pdf-thumb-canvas-wrap">
          <canvas
            :ref="el => setThumbRef(el as HTMLCanvasElement | null, i)"
            class="pdf-thumb-canvas"
            :data-page-num="i"
          ></canvas>
          <!-- drop indicator -->
          <div v-if="dropTarget === i && dragFrom != null && dragFrom !== i" class="pdf-drop-indicator" aria-hidden="true"></div>
        </div>
        <div class="pdf-thumb-meta">
          <span :class="{ 'is-current-label': i === currentPage }">第 {{ i }} 页</span>
          <div class="pdf-thumb-actions">
            <button
              class="pdf-thumb-action-btn"
              :aria-label="`旋转第 ${i} 页 90 度`"
              data-tooltip="旋转 90°"
              @click.stop="$emit('rotate', i, 90)"
            >
              <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 12a9 9 0 1 0 9-9" />
                <path d="M3 5v7h7" />
              </svg>
            </button>
            <button
              class="pdf-thumb-action-btn"
              :aria-label="`更多操作`"
              data-tooltip="更多"
              @click.stop="onContextMenu($event, i)"
            >
              <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="5" r="1.5" fill="currentColor" />
                <circle cx="12" cy="12" r="1.5" fill="currentColor" />
                <circle cx="12" cy="19" r="1.5" fill="currentColor" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  totalPages: number
  currentPage: number
}>()

const emit = defineEmits<{
  (e: 'goto', page: number): void
  (e: 'rotate', page: number, degrees: number): void
  (e: 'reorder', from: number, to: number): void
  (e: 'context-menu', page: number, x: number, y: number): void
  (e: 'thumb-ready', pageNum: number, canvasEl: HTMLCanvasElement): void
}>()

// 缩略图 DOM ref map(供父组件收集并传给 renderer.renderAllThumbs)
const thumbRefs = new Map<number, HTMLCanvasElement>()
function setThumbRef(el: HTMLCanvasElement | null, pageNum: number) {
  if (el) {
    thumbRefs.set(pageNum, el)
    emit('thumb-ready', pageNum, el)
  } else {
    thumbRefs.delete(pageNum)
  }
}

// 拖拽状态
const dragFrom = ref<number | null>(null)
const dropTarget = ref<number | null>(null)

function onDragStart(evt: DragEvent, pageNum: number) {
  if (!evt.dataTransfer) return
  dragFrom.value = pageNum
  evt.dataTransfer.effectAllowed = 'move'
  evt.dataTransfer.setData('text/plain', String(pageNum))
}

function onDragOver(evt: DragEvent, pageNum: number) {
  if (dragFrom.value === null) return
  evt.dataTransfer && (evt.dataTransfer.dropEffect = 'move')
  dropTarget.value = pageNum
}

function onDragEnd() {
  dragFrom.value = null
  dropTarget.value = null
}

function onDrop(evt: DragEvent, pageNum: number) {
  evt.preventDefault()
  if (dragFrom.value !== null && dragFrom.value !== pageNum) {
    emit('reorder', dragFrom.value, pageNum)
  }
  dragFrom.value = null
  dropTarget.value = null
}

function onContextMenu(evt: MouseEvent, pageNum: number) {
  emit('context-menu', pageNum, evt.clientX, evt.clientY)
}
</script>

<style scoped>
.pdf-thumb-rail {
  background: var(--color-surface);
  border-right: 1px solid var(--color-divider);
  overflow-y: auto;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.pdf-thumb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-1) var(--space-2) var(--space-2);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-foreground-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.pdf-thumb-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.pdf-thumb-item {
  position: relative;
  border-radius: var(--radius-md);
  padding: var(--space-2);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
  user-select: none;
}
.pdf-thumb-item:hover {
  background: var(--color-surface-2);
}
.pdf-thumb-item.is-current {
  background: var(--color-primary-soft);
}
.pdf-thumb-item.is-current::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
}
.pdf-thumb-item.is-dragging {
  opacity: 0.4;
  transform: scale(0.96);
}

.pdf-thumb-canvas-wrap {
  width: 100%;
  aspect-ratio: 0.707; /* A4 portrait */
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-1);
  overflow: hidden;
  position: relative;
}
.pdf-thumb-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.pdf-drop-indicator {
  position: absolute;
  top: 0;
  left: -3px;
  right: -3px;
  height: 3px;
  background: var(--color-primary);
  border-radius: 2px;
  pointer-events: none;
}

.pdf-thumb-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
}
.is-current-label {
  font-weight: 600;
  color: var(--color-primary);
}

.pdf-thumb-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.pdf-thumb-item:hover .pdf-thumb-actions {
  opacity: 1;
}

.pdf-thumb-action-btn {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-3);
  transition: background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
  position: relative;
}
.pdf-thumb-action-btn:hover {
  background: var(--color-surface);
  color: var(--color-foreground);
}
.pdf-thumb-action-btn .ico {
  width: 13px;
  height: 13px;
}
.pdf-thumb-action-btn[data-tooltip]:hover::after {
  content: attr(data-tooltip);
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  background: var(--color-foreground);
  color: var(--color-on-primary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
  z-index: var(--z-toast);
}
</style>
