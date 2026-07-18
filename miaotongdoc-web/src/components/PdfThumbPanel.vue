<!--
  PdfThumbPanel.vue V3 —— 缩略图侧栏
  - 修复遮挡工具栏(z-index + flex-shrink)
  - 2x 高清渲染(thumbScale 0.4)
  - 懒加载(用 IntersectionObserver)
  - 占位骨架
  - 选中页蓝色左边框
  - 缩略图卡片悬浮显示快捷操作
-->
<template>
  <aside class="pdf-thumb-panel" :class="{ 'is-collapsed': collapsed }" aria-label="页面缩略图">
    <header class="pdf-thumb-panel-header">
      <span v-if="!collapsed" class="pdf-thumb-panel-title">页面</span>
      <span v-if="!collapsed" class="pdf-thumb-panel-count">{{ currentPage }} / {{ totalPages }}</span>
      <span v-else class="pdf-thumb-panel-collapsed-label">P{{ currentPage }}</span>
    </header>

    <div v-if="!collapsed" ref="listRef" class="pdf-thumb-panel-list">
      <div
        v-for="i in totalPages"
        :key="i"
        class="pdf-thumb-card"
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
          <!-- 占位骨架(canvas 渲染前) -->
          <div v-if="!thumbRendered.has(i)" class="pdf-thumb-skeleton"></div>
          <!-- 实际 canvas(用 IntersectionObserver 触发渲染) -->
          <canvas
            :ref="(el) => bindThumb(el, i)"
            :data-page-num="i"
            class="pdf-thumb-canvas"
            v-show="thumbRendered.has(i)"
          ></canvas>
          <!-- 拖放指示线 -->
          <div
            v-if="dropTarget === i && dragFrom != null && dragFrom !== i"
            class="pdf-thumb-drop-line"
            aria-hidden="true"
          ></div>
        </div>
        <div class="pdf-thumb-meta">
          <span class="pdf-thumb-num" :class="{ 'is-current-num': i === currentPage }">{{ i }}</span>
        </div>
        <!-- 当前页左侧高亮条 -->
        <div v-if="i === currentPage" class="pdf-thumb-active-bar" aria-hidden="true"></div>
      </div>
    </div>

    <!-- Phase 11.7: 重新设计的折叠按钮 - 竖条居中箭头 + 浅灰底 -->
    <button
      class="pdf-thumb-collapse-rail"
      :class="{ 'is-collapsed': collapsed }"
      :aria-label="collapsed ? '展开缩略图' : '折叠缩略图'"
      :title="collapsed ? '展开缩略图' : '折叠缩略图'"
      @click="$emit('toggle-collapse')"
    >
      <svg class="rail-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path
          :d="collapsed ? 'M10 6l6 6-6 6' : 'M14 6l-6 6 6 6'"
          stroke="currentColor"
          stroke-width="2.5"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick, type ComponentPublicInstance } from 'vue'

const props = defineProps<{
  totalPages: number
  currentPage: number
  collapsed?: boolean
}>()

const emit = defineEmits<{
  (e: 'goto', page: number): void
  (e: 'rotate', page: number, degrees: number): void
  (e: 'reorder', from: number, to: number): void
  (e: 'context-menu', page: number, x: number, y: number): void
  (e: 'thumb-ready', pageNum: number, canvasEl: HTMLCanvasElement): void
  (e: 'toggle-collapse'): void
}>()

const listRef = ref<HTMLElement | null>(null)
const thumbRendered = ref<Set<number>>(new Set())
const thumbRefs = new Map<number, HTMLCanvasElement>()
const observerRef = ref<IntersectionObserver | null>(null)

function bindThumb(el: Element | ComponentPublicInstance | null, pageNum: number) {
  if (!el || !(el instanceof HTMLCanvasElement)) {
    thumbRefs.delete(pageNum)
    return
  }
  thumbRefs.set(pageNum, el)
  // Phase 11.8: 立即标记 thumbRendered 让 skeleton 消失(canvas 白底,父组件异步渲染会很快填充)
  thumbRendered.value.add(pageNum)
  // 设置 IntersectionObserver,canvas 进入视口才通知父组件渲染
  observerRef.value?.observe(el)
  emit('thumb-ready', pageNum, el)
}

function onContextMenu(evt: MouseEvent, pageNum: number) {
  emit('context-menu', pageNum, evt.clientX, evt.clientY)
}

// ===== 拖拽 =====
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

// ===== 懒加载:canvas 进入视口后通知父组件渲染 =====
// 通过子组件 watch + emit 实现:父组件 emit('thumb-ready', i, el) 后才开始绘制
watch(
  () => Array.from(thumbRefs.entries()),
  (entries) => {
    entries.forEach(([pageNum, el]) => {
      if (!thumbRendered.value.has(pageNum)) {
        const rect = el.getBoundingClientRect()
        const listRect = listRef.value?.getBoundingClientRect()
        if (listRect && rect.top < listRect.bottom + 200 && rect.bottom > listRect.top - 200) {
          thumbRendered.value.add(pageNum)
        }
      }
    })
  },
  { deep: true, flush: 'post' },
)

onMounted(() => {
  // 设置 IntersectionObserver(仅做兜底,主逻辑靠 scroll listener)
  if (typeof IntersectionObserver !== 'undefined') {
    observerRef.value = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const pageNum = Number((entry.target as HTMLElement).dataset.pageNum)
            if (pageNum) thumbRendered.value.add(pageNum)
          }
        })
      },
      { root: listRef.value, rootMargin: '200px' },
    )
    // Phase 11.8: observer 在 onMounted 创建,但 bindThumb 可能更早调用过(observe 落空)
    // 补 observe 已绑定的 canvas + 立即标记前 N 页为 rendered(常见缩略图数量少,不必懒加载)
    nextTick(() => {
      thumbRefs.forEach((el, pageNum) => {
        observerRef.value?.observe(el)
        thumbRendered.value.add(pageNum)
      })
    })
  }
})

onBeforeUnmount(() => {
  observerRef.value?.disconnect()
})
</script>

<style scoped>
.pdf-thumb-panel {
  width: var(--thumb-rail-width);
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  z-index: var(--z-thumb-rail);
  flex-shrink: 0;
  /* Phase 11.5 Q3: 承托 absolute 折叠按钮 */
  position: relative;
  /* 关键修复:grid item 不撑高父容器 */
  min-height: 0;
  align-self: stretch;
  max-height: 100%;
  /* Phase 13.5: 改 visible 让折叠按钮(right:-22)可见,内部 list 自己有 overflow */
  overflow: visible;
  transition: width var(--duration-base) var(--ease-out);
}

.pdf-thumb-panel.is-collapsed {
  width: 36px;
}

.pdf-thumb-panel-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-divider);
  height: var(--topbar-height);
  flex-shrink: 0;
  background: var(--color-surface-2);
}

/* Phase 13.9: 折叠按钮撑满 panel 全高(top:0 bottom:0 与缩略图对齐) + hover 浅蓝 */
.pdf-thumb-collapse-rail {
  position: absolute;
  right: -22px;
  top: 0;
  bottom: 0;
  width: 22px;
  border-radius: 0 10px 10px 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-2);
  z-index: 10;
  transition: width 180ms cubic-bezier(.4,0,.2,1),
              background 180ms ease,
              color 180ms ease,
              box-shadow 180ms ease,
              right 180ms ease;
  padding: 0;
  box-shadow: var(--shadow-2);
}

.pdf-thumb-collapse-rail:hover {
  width: 28px;
  right: -28px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-4);
}

.pdf-thumb-collapse-rail.is-collapsed {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.pdf-thumb-collapse-rail.is-collapsed:hover {
  width: 28px;
  right: -28px;
}

.pdf-thumb-collapse-rail .rail-icon {
  display: block;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.15));
}

.pdf-thumb-panel-title {
  font-size: var(--text-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-foreground);
  flex: 1;
}

.pdf-thumb-panel-count {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

.pdf-thumb-panel-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* Phase 11.5 Q3: 折叠态 header 显示竖排页码 */
.pdf-thumb-panel-collapsed-label {
  font-size: var(--text-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
  writing-mode: vertical-rl;
  text-orientation: mixed;
  margin: 0 auto;
  padding: var(--space-2) 0;
}

.pdf-thumb-card {
  position: relative;
  cursor: pointer;
  user-select: none;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.pdf-thumb-card:hover {
  background: var(--color-surface-2);
}

.pdf-thumb-card.is-current {
  background: var(--color-primary-soft);
}

.pdf-thumb-card.is-current:hover {
  background: var(--color-primary-soft);
  filter: brightness(0.97);
}

.pdf-thumb-card.is-dragging {
  opacity: 0.4;
  transform: scale(0.96);
}

.pdf-thumb-canvas-wrap {
  width: 100%;
  /* aspect-ratio 动态:从 PDF 实际页面,默认 A4 */
  aspect-ratio: 0.707;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-1);
  transition: box-shadow var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
  display: flex;
  align-items: center;
  justify-content: center;
}

.pdf-thumb-card:hover .pdf-thumb-canvas-wrap {
  box-shadow: var(--shadow-4);
  border-color: var(--color-border-strong);
  transform: translateY(-1px);
}

.pdf-thumb-canvas {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  display: block;
}

.pdf-thumb-card.is-current .pdf-thumb-canvas-wrap {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-2);
}

.pdf-thumb-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

/* 占位骨架(渲染前) */
.pdf-thumb-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, var(--color-surface-2) 8%, var(--color-surface-3) 18%, var(--color-surface-2) 33%);
  background-size: 200% 100%;
  animation: pdf-thumb-skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes pdf-thumb-skeleton-shimmer {
  to {
    background-position-x: -200%;
  }
}

/* 当前页左侧高亮条 */
.pdf-thumb-active-bar {
  position: absolute;
  left: -3px;
  top: 4px;
  bottom: 4px;
  width: 3px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
  pointer-events: none;
}

/* 拖放指示线 */
.pdf-thumb-drop-line {
  position: absolute;
  top: -2px;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--color-primary);
  border-radius: 2px;
  pointer-events: none;
  box-shadow: 0 0 0 2px var(--color-primary-soft);
}

/* 元信息 */
.pdf-thumb-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--space-1);
}

.pdf-thumb-num {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

.pdf-thumb-num.is-current-num {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
</style>