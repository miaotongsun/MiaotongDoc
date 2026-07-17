<!--
  PdfAiMenu.vue —— 工具栏"AI"下拉菜单

  提供 AI 快捷入口:
    - 摘要本文档  → useAiChat 流式
    - 翻译选中文字 → useAiChat 流式
    - 抽取合同条款 → usePdfExtractTerms + 打开 PdfTermsPanel 抽屉
    - 优化 OCR 结果 → usePdfOptimizeOcr
    - 打开 AI 浮窗 → 切换 PdfAiFloatPanel

  设计:Fluent menu overlay + 段标题 + 快捷键提示
-->
<template>
  <Teleport to="body">
    <Transition name="pdf-menu-fade">
      <div
        v-if="open"
        ref="menuRef"
        class="pdf-menu-overlay"
        :style="position"
        role="menu"
        aria-label="AI 助手菜单"
        @click.stop
      >
        <div class="pdf-menu-section-title">智能问答</div>
        <button class="pdf-menu-item" role="menuitem" :disabled="busy" @click="onOpenChat">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <span>打开 AI 浮窗</span>
          <span class="pdf-menu-shortcut">Ctrl+I</span>
        </button>
        <button class="pdf-menu-item" role="menuitem" :disabled="busy" @click="onSummarize">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M3 12h12M3 18h8"/></svg>
          <span>摘要本文档</span>
        </button>
        <button class="pdf-menu-item" role="menuitem" :disabled="busy" @click="onTranslate">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 8h6m-3-3v3M3 21l4-10 4 10M14 13l4 4 4-4M14 21h8M16 17h4"/></svg>
          <span>翻译选中段(中)</span>
        </button>

        <div class="pdf-menu-divider"></div>

        <div class="pdf-menu-section-title">合同分析</div>
        <button class="pdf-menu-item" role="menuitem" :disabled="busy" @click="onExtractTerms">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="9" y1="13" x2="15" y2="13"/><line x1="9" y1="17" x2="13" y2="17"/></svg>
          <span>抽取合同条款</span>
        </button>
        <button class="pdf-menu-item" role="menuitem" :disabled="busy" @click="onOptimizeOcr">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
          <span>优化 OCR 识别结果</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePdfAiFloat } from '@/composables/pdf/usePdfAiFloat'

const props = defineProps<{
  open: boolean
  anchor: { x: number; y: number } | null
  docId: number
  /** 当前已识别的 markdown(若有),供 optimize 按钮使用 */
  recognizedMarkdown?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  /** 打开 AI 浮窗(由 PdfEditor 切换 aiVisible) */
  (e: 'open-chat'): void
  /** 打开合同条款抽屉(由 PdfEditor 控制 PdfTermsPanel 显示) */
  (e: 'open-terms'): void
}>()

const menuRef = ref<HTMLElement | null>(null)

const position = computed(() => {
  if (!props.anchor) return { top: '48px', left: '50%' }
  return { top: `${props.anchor.y + 8}px`, left: `${props.anchor.x}px` }
})

const aiFloat = usePdfAiFloat({ docId: props.docId })

const busy = computed(
  () =>
    aiFloat.chat.status.value === 'streaming' ||
    aiFloat.chat.status.value === 'submitted' ||
    aiFloat.vision.status.value === 'streaming',
)

// ====== 操作 ======
function onOpenChat() {
  emit('open-chat')
  emit('close')
}

async function onSummarize() {
  emit('open-chat')
  emit('close')
  await aiFloat.chat.sendUserMessage(
    '请用 100 字以内总结这份文档的核心内容,包括主要议题和关键结论。',
  )
}

async function onTranslate() {
  const selection = window.getSelection()?.toString()?.trim()
  if (!selection) {
    ElMessage.warning('请先在 PDF 中选中要翻译的文字')
    return
  }
  emit('open-chat')
  emit('close')
  const prompt = `请将以下文字翻译成中文(保持原意,无需解释):\n\n${selection}`
  await aiFloat.chat.sendUserMessage(prompt)
}

function onExtractTerms() {
  emit('open-terms')
  emit('close')
}

async function onOptimizeOcr() {
  emit('open-chat')
  emit('close')
  const markdown = props.recognizedMarkdown?.trim()
  if (!markdown) {
    ElMessage.warning('暂无可优化的 OCR 内容,请先执行 OCR 识别')
    return
  }
  await aiFloat.optimizeOcr.optimize(markdown)
}

// ====== 关闭外部点击 + Esc ======
function onDocClick(e: MouseEvent) {
  if (!props.open) return
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    emit('close')
  }
}
function onKey(e: KeyboardEvent) {
  if (props.open && e.key === 'Escape') emit('close')
}
// 只在菜单打开时注册全局 click + keydown,避免与触发按钮竞态
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
.pdf-menu-overlay {
  position: fixed;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-8);
  padding: var(--space-1);
  min-width: 240px;
  z-index: var(--z-modal);
  border: 1px solid var(--color-divider);
}
.pdf-menu-section-title {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-foreground-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.pdf-menu-item {
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
.pdf-menu-item:hover:not(:disabled) {
  background: var(--color-surface-2);
}
.pdf-menu-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pdf-menu-item .ico {
  width: 16px;
  height: 16px;
  color: var(--color-foreground-2);
  flex-shrink: 0;
}
.pdf-menu-shortcut {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
}
.pdf-menu-divider {
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