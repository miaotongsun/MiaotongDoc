<!--
  PdfCanvasToolbar.vue -- Phase 13.31 画布顶部工具栏(Acrobat DC 风格)

  吸顶在画布区顶部 (position: sticky; top: 0),三段布局:
    左:工具(选择/手型) — 快速切换 activeTool
    中:页面导航(上一页/页码/下一页)
    右:缩放(缩小/百分比/放大/适合宽度/适合页面/实际大小)

  所有操作 emit 给 PdfEditor 调对应处理函数。
-->
<template>
  <div v-if="visible" class="pdf-canvas-toolbar">
    <!-- 左:工具 -->
    <div class="pct-group pct-left">
      <button
        class="pct-btn"
        :class="{ 'is-active': activeTool === 'select' }"
        aria-label="选择工具"
        title="选择工具 (V)"
        @click="$emit('set-tool', 'select')"
      >
        <PdfIcon name="select" :size="16" />
      </button>
      <button
        class="pct-btn"
        :class="{ 'is-active': activeTool === 'move' }"
        aria-label="手型工具"
        title="手型工具 (M,平移文档)"
        @click="$emit('set-tool', 'move')"
      >
        <PdfIcon name="hand" :size="16" />
      </button>

      <span class="pct-sep"></span>

      <!-- 视图模式:单页 / 连续 / 双页 -->
      <button
        class="pct-btn"
        :class="{ 'is-active': viewMode === 'single' }"
        aria-label="单页视图"
        title="单页视图"
        @click="$emit('set-view', 'single')"
      >
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <rect x="6" y="3" width="12" height="18" rx="1"/>
        </svg>
      </button>
      <button
        class="pct-btn"
        :class="{ 'is-active': viewMode === 'continuous' }"
        aria-label="连续视图"
        title="连续视图(滚动多页)"
        @click="$emit('set-view', 'continuous')"
      >
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <rect x="6" y="3" width="12" height="5" rx="1"/>
          <rect x="6" y="10" width="12" height="5" rx="1"/>
          <rect x="6" y="17" width="12" height="4" rx="1"/>
        </svg>
      </button>
      <button
        class="pct-btn"
        :class="{ 'is-active': viewMode === 'facing' }"
        aria-label="双页视图"
        title="双页对照"
        @click="$emit('set-view', 'facing')"
      >
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <rect x="3" y="4" width="8" height="16" rx="1"/>
          <rect x="13" y="4" width="8" height="16" rx="1"/>
        </svg>
      </button>
    </div>

    <!-- 中:页面导航 -->
    <div class="pct-group pct-center">
      <button
        class="pct-btn pct-page-btn"
        aria-label="上一页"
        :disabled="currentPage <= 1"
        @click="$emit('go-prev')"
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      <input
        class="pct-page-input"
        type="number"
        :min="1"
        :max="totalPages"
        :value="currentPage"
        :title="`跳转到 1-${totalPages} 页`"
        @change="onPageChange($event)"
        @keyup.enter="onPageChange($event)"
      />
      <span class="pct-page-total">/ {{ totalPages }}</span>
      <button
        class="pct-btn pct-page-btn"
        aria-label="下一页"
        :disabled="currentPage >= totalPages"
        @click="$emit('go-next')"
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>
    </div>

    <!-- 右:缩放 -->
    <div class="pct-group pct-right">
      <button
        class="pct-btn"
        aria-label="缩小"
        :disabled="!canZoomOut"
        title="缩小 (-)"
        @click="$emit('zoom-out')"
      >
        <PdfIcon name="zoomOut" :size="16" />
      </button>
      <el-dropdown trigger="click" @command="onSelectPercent">
        <button class="pct-btn pct-percent" :title="`缩放: ${percent}%`">
          <span>{{ percent }}%</span>
          <PdfIcon name="chevronDown" :size="11" />
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="p in presets" :key="p" :command="p">{{ p }}%</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <button
        class="pct-btn"
        aria-label="放大"
        :disabled="!canZoomIn"
        title="放大 (+)"
        @click="$emit('zoom-in')"
      >
        <PdfIcon name="zoomIn" :size="16" />
      </button>
      <span class="pct-sep"></span>
      <button class="pct-btn" aria-label="适合宽度" title="适合宽度" @click="$emit('fit-width')">
        <PdfIcon name="fitWidth" :size="16" />
      </button>
      <button class="pct-btn" aria-label="适合页面" title="适合页面" @click="$emit('fit-page')">
        <PdfIcon name="fitPage" :size="16" />
      </button>
      <button class="pct-btn" aria-label="实际大小" title="实际大小 (100%)" @click="$emit('actual-size')">
        <PdfIcon name="actual" :size="16" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import PdfIcon from './PdfIcon.vue'

defineProps<{
  visible: boolean
  /** 当前激活工具(用于按钮高亮):'select' | 'move' | 其他 */
  activeTool: string
  /** 当前页(1-based) */
  currentPage: number
  /** 总页数 */
  totalPages: number
  /** 缩放百分比(如 120 表示 120%) */
  percent: number
  canZoomIn: boolean
  canZoomOut: boolean
  /** Phase 13.33: 视图模式(单页/连续/双页) */
  viewMode: 'single' | 'continuous' | 'facing'
}>()
const emit = defineEmits<{
  (e: 'set-tool', tool: 'select' | 'move'): void
  (e: 'set-view', mode: 'single' | 'continuous' | 'facing'): void
  (e: 'go-prev'): void
  (e: 'go-next'): void
  (e: 'go-page', page: number): void
  (e: 'zoom-in'): void
  (e: 'zoom-out'): void
  (e: 'fit-width'): void
  (e: 'fit-page'): void
  (e: 'actual-size'): void
  (e: 'set-scale', scale: number): void
}>()

const presets = [25, 50, 75, 100, 125, 150, 200, 300, 400]

function onSelectPercent(p: number) {
  emit('set-scale', p / 100)
}
function toInt(v: string | number): number {
  const n = parseInt(String(v), 10)
  return Number.isFinite(n) ? n : 1
}
function onPageChange(evt: Event) {
  const target = evt.target as HTMLInputElement | null
  if (!target) return
  emit('go-page', toInt(target.value))
}
</script>

<style scoped>
.pdf-canvas-toolbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 4px 10px;
  /* Phase 13.31: 紧贴 pdf-canvas-area 顶部,边距 0 */
  margin: 0 0 8px 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
  /* toolbar 宽度撑满画布区(去掉默认的居中 max-width) */
  align-self: stretch;
  width: 100%;
  border-radius: 0;
}
.pct-group {
  display: flex;
  align-items: center;
  gap: 1px;
}
.pct-center {
  flex: 0 0 auto;
  margin: 0 auto;
}
.pct-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 26px;
  min-width: 26px;
  padding: 0 6px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-foreground-2, #475569);
  transition: background 120ms ease, color 120ms ease;
}
.pct-btn:hover:not(:disabled) {
  background: var(--color-surface-2, #f1f5f9);
  color: var(--color-foreground, #0f172a);
}
.pct-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.pct-btn.is-active {
  background: var(--color-primary-soft, #ebf1fe);
  color: var(--color-primary, #3b6fe8);
}
.pct-page-btn {
  min-width: 22px;
}
.pct-page-input {
  width: 38px;
  height: 22px;
  padding: 0 3px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 3px;
  background: var(--color-surface, #fff);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  color: var(--color-foreground, #0f172a);
  font-variant-numeric: tabular-nums;
  outline: none;
  transition: border-color 120ms ease;
}
.pct-page-input:focus {
  border-color: var(--color-primary, #3b6fe8);
}
.pct-page-input::-webkit-inner-spin-button,
.pct-page-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.pct-page-total {
  font-size: 11px;
  color: var(--color-foreground-3, #94a3b8);
  font-variant-numeric: tabular-nums;
  margin: 0 3px 0 2px;
  min-width: 24px;
  text-align: left;
}
.pct-percent {
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  min-width: 50px;
  padding: 0 4px;
}
.pct-sep {
  width: 1px;
  height: 14px;
  background: var(--color-border, #e2e8f0);
  margin: 0 2px;
}
</style>
