<!--
  PdfPageOpsMenu.vue —— 工具栏"页面"下拉菜单

  提供操作入口:
    - 合并 PDF → 打开 MergeDialog
    - 提取当前页 → usePdfPageOps.extractPages
    - 旋转全部 90°
    - 重新识别 OCR

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
        aria-label="页面操作菜单"
        @click.stop
      >
        <div class="pdf-menu-section-title">合并 PDF</div>
        <button class="pdf-menu-item" role="menuitem" @click="onMerge">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/></svg>
          <span>合并其他 PDF</span>
          <span class="pdf-menu-shortcut">Ctrl+M</span>
        </button>

        <div class="pdf-menu-divider"></div>

        <div class="pdf-menu-section-title">提取</div>
        <button class="pdf-menu-item" role="menuitem" @click="onExtractCurrent">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          <span>提取当前页为新文档</span>
        </button>

        <div class="pdf-menu-divider"></div>

        <div class="pdf-menu-section-title">批量操作</div>
        <button class="pdf-menu-item" role="menuitem" :disabled="pageOps.busy.value" @click="onRotateAll">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12a9 9 0 1 0 9-9"/><path d="M3 5v7h7"/></svg>
          <span>{{ pageOps.busy.value ? '旋转中…' : '旋转全部 90°' }}</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { usePdfPageOps } from '@/composables/pdf/usePdfPageOps'
import type { PageOpResult } from '@/api/pdf'
import MergeDialog from './MergeDialog.vue'

const props = defineProps<{
  open: boolean
  /** 触发按钮位置(用于菜单定位) */
  anchor: { x: number; y: number } | null
  docId: number
  fileUrl: string
  currentPage: number
  totalPages: number
  /** 文本编辑器 flush 钩子(防止丢编辑) */
  flushTextEdits?: () => Promise<void>
  /** 操作成功回调(父组件 reload) */
  onSaved?: (result: PageOpResult, newFileUrl: string) => void
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const menuRef = ref<HTMLElement | null>(null)

const position = computed(() => {
  if (!props.anchor) return { top: '48px', left: '50%' }
  // 菜单定位在按钮下方
  return {
    top: `${props.anchor.y + 8}px`,
    left: `${props.anchor.x}px`,
  }
})

const pageOps = usePdfPageOps({
  docId: props.docId,
  fileUrl: props.fileUrl,
  beforeOp: async () => {
    if (props.flushTextEdits) {
      await props.flushTextEdits()
    }
    return true
  },
  onSaved: (result) => {
    const newUrl = pageOps.bustUrl(result)
    props.onSaved?.(result, newUrl)
    emit('close')
  },
})

// ====== Merge Dialog ======
const mergeDialogOpen = ref(false)
function onMerge() {
  mergeDialogOpen.value = true
  emit('close')
}

function onMergeConfirmed(documentIds: number[]) {
  pageOps.merge(documentIds)
}

function onExtractCurrent() {
  pageOps.extractPages([props.currentPage])
  emit('close')
}

async function onRotateAll() {
  const allPages = Array.from({ length: props.totalPages }, (_, i) => i + 1)
  await pageOps.rotatePages(allPages)
}

// ====== 关闭外部点击 ======
function onDocClick(e: MouseEvent) {
  if (!props.open) return
  const target = e.target as HTMLElement
  if (menuRef.value && !menuRef.value.contains(target)) {
    emit('close')
  }
}

// 只在菜单打开时注册全局 click 监听,避免与触发按钮的 click 事件竞态
watch(
  () => props.open,
  (open) => {
    if (open) {
      // 推迟到下一帧绑定,确保本次 click 事件完全派发完
      setTimeout(() => {
        document.addEventListener('click', onDocClick)
      }, 0)
    } else {
      document.removeEventListener('click', onDocClick)
    }
  },
)

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
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
