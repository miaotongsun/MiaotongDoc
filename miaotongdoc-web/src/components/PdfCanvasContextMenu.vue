<!--
  PdfCanvasContextMenu.vue -- PDF 画布右键快捷菜单(Phase 13.8)

  参考 Adobe Acrobat DC,在画布右键唤出:
    - 编辑文字(进入编辑模式)/ OCR 快速 / OCR 高精度(优先)
    - 复制选中 / 全选(选中时)
    - 工具切换(选择/高亮/批注/画笔)
    - 页面操作(旋转/提取/删除)
    - AI(翻译选中/摘要/问答)

  上下文相关:
    - hasSelection=true 时显示"复制/翻译选中"
    - activeTool 当前工具打勾
-->
<template>
  <Teleport to="body">
    <Transition name="pdf-menu-fade">
      <div
        v-if="open"
        ref="menuRef"
        class="pdf-ctx-menu"
        :style="position"
        role="menu"
        aria-label="PDF 画布操作"
        @click.stop
      >
        <div class="pdf-ctx-header">第 {{ pageNum }} 页 / 共 {{ totalPages }} 页</div>

        <!-- 优先:编辑文字 + OCR -->
        <button class="pdf-ctx-item" role="menuitem" @click="onEditText">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          <span>编辑文字</span>
          <span class="pdf-ctx-shortcut">编辑模式</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onOcr('mobile')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7zM12 9v6M9 12h6"/></svg>
          <span>OCR 快速识别</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onOcr('server')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7zM12 9v6M9 12h6"/></svg>
          <span>OCR 高精度识别</span>
          <span class="pdf-ctx-shortcut">server</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <!-- 编辑(选中时) -->
        <button v-if="hasSelection" class="pdf-ctx-item" role="menuitem" @click="onCopy">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
          <span>复制选中文字</span>
          <span class="pdf-ctx-shortcut">Ctrl+C</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onSelectAll">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          <span>全选</span>
          <span class="pdf-ctx-shortcut">Ctrl+A</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <!-- 工具 -->
        <div class="pdf-ctx-section">工具</div>
        <button
          v-for="t in tools"
          :key="t.id"
          class="pdf-ctx-item"
          :class="{ 'is-active': activeTool === t.id }"
          role="menuitem"
          @click="onSelectTool(t.id)"
        >
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true" v-html="t.icon"></svg>
          <span>{{ t.label }}</span>
          <svg v-if="activeTool === t.id" class="pdf-ctx-check" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6L9 17l-5-5"/></svg>
        </button>

        <div class="pdf-ctx-divider"></div>

        <!-- 页面 -->
        <div class="pdf-ctx-section">页面</div>
        <button class="pdf-ctx-item" role="menuitem" @click="onRotate(90)">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12a9 9 0 1 0 9-9"/><path d="M3 5v7h7"/></svg>
          <span>旋转 90°</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onExtract">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          <span>提取此页</span>
        </button>
        <button class="pdf-ctx-item pdf-ctx-danger" :disabled="totalPages <= 1" role="menuitem" @click="onDelete">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
          <span>删除此页</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <!-- AI -->
        <div class="pdf-ctx-section">AI</div>
        <button v-if="hasSelection" class="pdf-ctx-item" role="menuitem" @click="onAiTranslate">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 8h6m-3-3v3M3 21l4-10 4 10M14 13l4 4 4-4M14 21h8M16 17h4"/></svg>
          <span>翻译选中(中)</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onAiSummarize">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M3 12h12M3 18h8"/></svg>
          <span>摘要当前页</span>
        </button>
        <button class="pdf-ctx-item" role="menuitem" @click="onAiChat">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <span>AI 问答</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import type { AnnotationTool } from '@/composables/pdf/usePdfAnnotation'

const props = defineProps<{
  open: boolean
  anchor: { x: number; y: number } | null
  pageNum: number
  totalPages: number
  activeTool: AnnotationTool
  hasSelection?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'copy'): void
  (e: 'select-all'): void
  (e: 'edit-text'): void
  (e: 'select-tool', tool: AnnotationTool): void
  (e: 'rotate', page: number, degrees: number): void
  (e: 'extract', page: number): void
  (e: 'delete', page: number): void
  (e: 'ai-translate'): void
  (e: 'ai-summarize'): void
  (e: 'ai-chat'): void
  (e: 'ocr-recognize', model: 'mobile' | 'server'): void
}>()

const menuRef = ref<HTMLElement | null>(null)

const tools: Array<{ id: AnnotationTool; label: string; icon: string }> = [
  { id: 'select', label: '选择(文字+标注)', icon: '<path d="M3 3l7 17 2-8 8-2L3 3z"/>' },
  { id: 'highlight', label: '高亮', icon: '<path d="M9 11l-6 6v4h4l6-6m-4-4l5-5 4 4-5 5m-4-4l4 4"/>' },
  { id: 'comment', label: '批注', icon: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>' },
  { id: 'draw', label: '画笔', icon: '<path d="M12 19l7-7 3 3-7 7-3-3zM18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"/>' },
]

const position = computed(() => {
  if (!props.anchor) return { top: '100px', left: '100px' }
  // 防止菜单超出视口右下
  const maxX = window.innerWidth - 240
  const maxY = window.innerHeight - 400
  return {
    top: `${Math.min(props.anchor.y, maxY)}px`,
    left: `${Math.min(props.anchor.x, maxX)}px`,
  }
})

function close() { emit('close') }
function onCopy() { emit('copy'); close() }
function onSelectAll() { emit('select-all'); close() }
function onEditText() { emit('edit-text'); close() }
function onSelectTool(t: AnnotationTool) { emit('select-tool', t); close() }
function onRotate(deg: number) { emit('rotate', props.pageNum, deg); close() }
function onExtract() { emit('extract', props.pageNum); close() }
function onDelete() { emit('delete', props.pageNum); close() }
function onAiTranslate() { emit('ai-translate'); close() }
function onAiSummarize() { emit('ai-summarize'); close() }
function onAiChat() { emit('ai-chat'); close() }
function onOcr(model: 'mobile' | 'server') { emit('ocr-recognize', model); close() }

function onDocClick(e: MouseEvent) {
  if (!props.open) return
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) close()
}
function onKey(e: KeyboardEvent) {
  if (props.open && e.key === 'Escape') close()
}
watch(() => props.open, (open) => {
  if (open) {
    setTimeout(() => {
      document.addEventListener('click', onDocClick)
      document.addEventListener('keydown', onKey)
    }, 0)
  } else {
    document.removeEventListener('click', onDocClick)
    document.removeEventListener('keydown', onKey)
  }
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<style scoped>
.pdf-ctx-menu {
  position: fixed;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-8);
  padding: var(--space-1);
  min-width: 220px;
  max-height: 80vh;
  overflow-y: auto;
  z-index: var(--z-modal);
  border: 1px solid var(--color-divider);
}
.pdf-ctx-header {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-foreground-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--color-divider);
  margin-bottom: var(--space-1);
}
.pdf-ctx-section {
  padding: var(--space-2) var(--space-3) var(--space-1);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-foreground-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.pdf-ctx-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  color: var(--color-foreground);
  transition: background var(--duration-fast) var(--ease-out);
  text-align: left;
}
.pdf-ctx-item:hover:not(:disabled) {
  background: var(--color-surface-2);
}
.pdf-ctx-item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pdf-ctx-item.is-active {
  color: var(--color-primary);
  font-weight: 600;
}
.pdf-ctx-item .ico {
  width: 14px;
  height: 14px;
  color: var(--color-foreground-2);
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pdf-ctx-item.is-active .ico {
  color: var(--color-primary);
}
.pdf-ctx-check {
  margin-left: auto;
  width: 14px;
  height: 14px;
  color: var(--color-primary);
  fill: none;
  stroke: currentColor;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pdf-ctx-danger {
  color: var(--color-destructive);
}
.pdf-ctx-danger .ico {
  color: var(--color-destructive);
}
.pdf-ctx-shortcut {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
}
.pdf-ctx-divider {
  height: 1px;
  background: var(--color-divider);
  margin: var(--space-1) 0;
}
.pdf-menu-fade-enter-active,
.pdf-menu-fade-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out);
}
.pdf-menu-fade-enter-from,
.pdf-menu-fade-leave-to {
  opacity: 0;
}
</style>