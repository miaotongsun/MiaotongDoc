<!--
  PdfFloatingToolbar.vue —— 浮动文本格式工具栏(Phase 9)

  触发:用户在画布上选择文本段(> 0 chars)
  位置:跟随选区上方
  工具:字号 / 加粗 / 斜体 / 下划线 / 颜色 / 高亮

  实现:
  - 监听 canvas-area 的 selectionchange 事件
  - 选中文本时显示,选区消失时隐藏
  - 浮动定位 absolute,使用 selection.getRangeAt(0).getBoundingClientRect()
-->
<template>
  <Teleport v-if="visible" to="body">
    <div
      class="pdf-floating-toolbar"
      :style="positionStyle"
      role="toolbar"
      aria-label="文本格式"
      @mousedown="onToolbarMouseDown"
    >
      <!-- 字号 -->
      <div class="pdf-ft-group">
        <select
          class="pdf-ft-select"
          :value="fontSize"
          @change="onChangeFontSize"
          aria-label="字号"
        >
          <option v-for="s in fontSizes" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>

      <span class="pdf-ft-divider"></span>

      <!-- 加粗 / 斜体 / 下划线 -->
      <div class="pdf-ft-group">
        <button
          class="pdf-ft-btn"
          :class="{ 'is-active': styles.bold }"
          :aria-label="`加粗 (Ctrl+B)`"
          :aria-pressed="styles.bold"
          @click="$emit('format', { type: 'bold' })"
        >
          <strong>B</strong>
        </button>
        <button
          class="pdf-ft-btn"
          :class="{ 'is-active': styles.italic }"
          :aria-label="`斜体 (Ctrl+I)`"
          :aria-pressed="styles.italic"
          @click="$emit('format', { type: 'italic' })"
        >
          <em>I</em>
        </button>
        <button
          class="pdf-ft-btn"
          :class="{ 'is-active': styles.underline }"
          :aria-label="`下划线 (Ctrl+U)`"
          :aria-pressed="styles.underline"
          @click="$emit('format', { type: 'underline' })"
        >
          <span style="text-decoration: underline">U</span>
        </button>
      </div>

      <span class="pdf-ft-divider"></span>

      <!-- 颜色 -->
      <div class="pdf-ft-group pdf-ft-color">
        <button
          v-for="c in colorPalette"
          :key="c"
          class="pdf-ft-color-swatch"
          :class="{ 'is-active': textColor === c }"
          :style="{ background: c }"
          :aria-label="`文字颜色 ${c}`"
          @click="onChangeTextColor(c)"
        ></button>
      </div>

      <span class="pdf-ft-divider"></span>

      <!-- 高亮 -->
      <div class="pdf-ft-group pdf-ft-highlight">
        <button
          v-for="c in highlightPalette"
          :key="c"
          class="pdf-ft-highlight-swatch"
          :class="{ 'is-active': highlightColor === c }"
          :style="{ background: c }"
          :aria-label="`高亮 ${c}`"
          @click="onChangeHighlight(c)"
        ></button>
      </div>

      <span class="pdf-ft-divider"></span>

      <!-- Phase 13.25: 保存 / 取消按钮 (Acrobat 标准: 选区即生效 + 点保存才持久化) -->
      <div class="pdf-ft-group pdf-ft-actions">
        <button
          class="pdf-ft-btn pdf-ft-cancel"
          aria-label="取消格式修改"
          title="取消"
          @click="$emit('cancel')"
        >✗</button>
        <button
          class="pdf-ft-btn pdf-ft-confirm"
          aria-label="保存格式修改"
          title="保存"
          @click="$emit('confirm')"
        >✓</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  /** 监听目标元素(默认 document) */
  watchTarget?: HTMLElement | null
}>()

const emit = defineEmits<{
  (e: 'format', payload: { type: string; value?: string | number }): void
  /** Phase 13.25: 用户点 ✓ 保存 -> 批量持久化到后端 */
  (e: 'confirm'): void
  /** Phase 13.25: 用户点 ✗ 取消 / ESC -> 清空待保存 ops + 隐藏 */
  (e: 'cancel'): void
}>()

const visible = ref(false)
const positionStyle = ref({ top: '0px', left: '0px' })
const styles = ref({ bold: false, italic: false, underline: false })
const fontSize = ref(14)
const textColor = ref('#000000')
const highlightColor = ref('#FACC15')

const fontSizes = [8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 48, 72]
const colorPalette = ['#000000', '#DC2626', '#2563EB', '#16A34A', '#F59E0B', '#7C3AED']
const highlightPalette = ['#FACC15', '#86EFAC', '#93C5FD', '#FCA5A5', '#DDD6FE', '#FBCFE8']

let lastRange: Range | null = null

/** 监听选区变化 */
function onSelectionChange() {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) {
    visible.value = false
    return
  }
  const range = sel.getRangeAt(0)
  const text = sel.toString().trim()
  if (text.length < 1) {
    visible.value = false
    return
  }

  // 必须是 PDF 文本层内的选区
  const container = (range.commonAncestorContainer as Element).closest?.('.pdf-text-layer, .pdf-text-edit-layer')
  if (!container) {
    visible.value = false
    return
  }

  // 获取选区矩形,浮动条智能定位(靠顶部→下方,靠底部→上方)
  const rect = range.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    visible.value = false
    return
  }
  lastRange = range.cloneRange()
  positionStyle.value = computePosition(rect)
  visible.value = true

  // 读取选区首个 span 的字号(select 反映当前格式)
  const sampleSpan =
    (range.startContainer.parentElement as Element)?.closest?.('span, .pdf-edit-token') ||
    (container.querySelector?.('.pdf-edit-token') as Element | null) ||
    container
  const cs = window.getComputedStyle(sampleSpan as Element)
  const size = parseInt(cs.fontSize, 10)
  if (!isNaN(size) && size > 0) {
    // 找到最接近的预设(避免 select 显示空)
    const nearest = fontSizes.reduce((best, cur) =>
      Math.abs(cur - size) < Math.abs(best - size) ? cur : best,
    )
    fontSize.value = nearest
  }

  // 读取粗/斜/下划线(选区开始位置)
  const bold = cs.fontWeight === 'bold' || parseInt(cs.fontWeight, 10) >= 600
  const italic = cs.fontStyle === 'italic'
  const underline = cs.textDecorationLine?.includes('underline') || cs.textDecoration?.includes('underline')
  styles.value = {
    bold,
    italic,
    underline,
  }
}

/**
 * 浮动工具栏智能定位
 * - 默认显示在选区上方,靠顶部时翻转到下方
 * - 水平居中于选区,但左右贴边时收回视口(留 8px padding)
 */
function computePosition(rect: DOMRect): { top: string; left: string } {
  const TOOLBAR_H = 44
  const TOOLBAR_W = 360
  const PAD = 8
  const vw = window.innerWidth
  const vh = window.innerHeight

  // 垂直:默认选区上方 8px;若顶部空间不够则放到下方
  const wantTop = rect.top - TOOLBAR_H - 8
  const top = wantTop >= PAD ? wantTop : Math.min(vh - TOOLBAR_H - PAD, rect.bottom + 8)

  // 水平:居中于选区,贴边收回
  const centerX = rect.left + rect.width / 2
  let left = centerX - TOOLBAR_W / 2
  if (left < PAD) left = PAD
  if (left + TOOLBAR_W > vw - PAD) left = vw - TOOLBAR_W - PAD

  return { top: `${top}px`, left: `${left}px` }
}

function onMouseDown(e: MouseEvent) {
  // 点工具栏内部不关闭(用 @mousedown.prevent 抑制)
  // 点其他地方关闭
  const t = e.target as HTMLElement
  if (!t.closest('.pdf-floating-toolbar')) {
    visible.value = false
  }
}

/**
 * 工具栏内 mousedown:阻止默认行为(防止按钮点击清空 selection),
 * 然后恢复 selection 让 selectionchange 不会触发 hide。
 */
function onToolbarMouseDown(e: MouseEvent): void {
  // 阻止默认行为:让浏览器不清空当前 selection
  e.preventDefault()
  if (!lastRange) return
  try {
    const sel = window.getSelection()
    if (!sel) return
    // 仅当 selection 已经丢失时才恢复
    if (sel.rangeCount === 0) {
      sel.addRange(lastRange)
    } else {
      const cur = sel.getRangeAt(0)
      // 如果当前选区与原选区不一致(可能是工具栏按钮文字被选),恢复
      if (cur.toString() !== lastRange.toString()) {
        sel.removeAllRanges()
        sel.addRange(lastRange)
      }
    }
  } catch { /* ignore */ }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') visible.value = false
}

function onChangeFontSize(e: Event) {
  const v = parseInt((e.target as HTMLSelectElement).value, 10)
  fontSize.value = v
  emit('format', { type: 'fontSize', value: v })
}

function onChangeTextColor(c: string) {
  textColor.value = c
  emit('format', { type: 'color', value: c })
}

function onChangeHighlight(c: string) {
  highlightColor.value = c
  emit('format', { type: 'highlight', value: c })
}

onMounted(() => {
  document.addEventListener('selectionchange', onSelectionChange)
  document.addEventListener('mousedown', onMouseDown)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('selectionchange', onSelectionChange)
  document.removeEventListener('mousedown', onMouseDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.pdf-floating-toolbar {
  position: fixed;
  z-index: 200;
  display: inline-flex;
  align-items: center;
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: var(--radius-lg, 8px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12), 0 0 0 1px rgba(15, 23, 42, 0.04);
  padding: 4px;
  gap: 2px;
  user-select: none;
  font-family: var(--font-sans, -apple-system, sans-serif);
  font-size: 13px;
  color: var(--color-foreground, #0f172a);
  animation: pdf-ft-appear 120ms var(--ease-out, ease);
}

@keyframes pdf-ft-appear {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.pdf-ft-group {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 2px;
}

.pdf-ft-divider {
  width: 1px;
  height: 18px;
  background: var(--color-border, #e2e8f0);
  margin: 0 2px;
}

.pdf-ft-btn {
  width: 28px;
  height: 28px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: var(--radius-sm, 4px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-foreground-2, #475569);
  font-size: 13px;
  transition: background 120ms ease, color 120ms ease, border-color 120ms ease;
}
.pdf-ft-btn:hover {
  background: var(--color-surface-2, #f1f5f9);
  color: var(--color-foreground, #0f172a);
}
.pdf-ft-btn.is-active {
  background: var(--color-primary-soft, #ebf1fe);
  color: var(--color-primary, #3b6fe8);
  border-color: var(--color-primary-soft, #ebf1fe);
}

.pdf-ft-select {
  height: 26px;
  padding: 0 6px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: var(--radius-sm, 4px);
  background: var(--color-surface, #fff);
  color: var(--color-foreground, #0f172a);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  outline: none;
}
.pdf-ft-select:focus {
  border-color: var(--color-primary, #3b6fe8);
  box-shadow: 0 0 0 2px var(--color-primary-soft, #ebf1fe);
}

.pdf-ft-color-swatch,
.pdf-ft-highlight-swatch {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 120ms ease, border-color 120ms ease;
}
.pdf-ft-color-swatch:hover,
.pdf-ft-highlight-swatch:hover {
  transform: scale(1.15);
}
.pdf-ft-color-swatch.is-active,
.pdf-ft-highlight-swatch.is-active {
  border-color: var(--color-foreground, #0f172a);
}

/* Phase 13.25: 保存/取消按钮 */
.pdf-ft-confirm {
  color: var(--color-success, #16a34a);
  font-weight: 700;
}
.pdf-ft-confirm:hover {
  background: rgba(22, 163, 74, 0.12);
  color: var(--color-success, #16a34a);
}
.pdf-ft-cancel {
  color: var(--color-foreground-3, #94a3b8);
}
.pdf-ft-cancel:hover {
  background: rgba(245, 108, 108, 0.1);
  color: var(--color-destructive, #f56c6c);
}
</style>