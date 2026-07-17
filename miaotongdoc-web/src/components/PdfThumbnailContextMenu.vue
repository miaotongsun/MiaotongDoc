<!--
  PdfThumbnailContextMenu.vue —— 缩略图右键菜单

  触发:在缩略图上右键
  选项:
    - 旋转 90° / 旋转 180° / 旋转 270°
    - 删除此页
    - 提取此页为新文档
    - 跳转到此页
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
        aria-label="页面操作"
        @click.stop
      >
        <div class="pdf-ctx-header">第 {{ pageNum }} 页</div>

        <button class="pdf-ctx-item" role="menuitem" @click="onGoto">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
          <span>跳转到此页</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <div class="pdf-ctx-section">旋转</div>
        <button class="pdf-ctx-item" :disabled="busy" role="menuitem" @click="onRotate(90)">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12a9 9 0 1 0 9-9"/><path d="M3 5v7h7"/></svg>
          <span>顺时针 90°</span>
        </button>
        <button class="pdf-ctx-item" :disabled="busy" role="menuitem" @click="onRotate(180)">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 1 1-9-9"/><path d="M21 19v-7h-7"/></svg>
          <span>旋转 180°</span>
        </button>
        <button class="pdf-ctx-item" :disabled="busy" role="menuitem" @click="onRotate(270)">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 1 0-9 9"/><path d="M21 19v-7h-7"/></svg>
          <span>逆时针 90°</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <button class="pdf-ctx-item" :disabled="busy" role="menuitem" @click="onExtract">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          <span>提取此页为新文档</span>
        </button>

        <div class="pdf-ctx-divider"></div>

        <button class="pdf-ctx-item pdf-ctx-danger" :disabled="busy || totalPages <= 1" role="menuitem" @click="onDelete">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
          <span>删除此页</span>
          <span class="pdf-ctx-shortcut">Del</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps<{
  open: boolean
  /** 鼠标点击位置 */
  anchor: { x: number; y: number } | null
  pageNum: number
  totalPages: number
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'goto', page: number): void
  (e: 'rotate', page: number, degrees: number): void
  (e: 'extract', page: number): void
  (e: 'delete', page: number): void
}>()

const menuRef = ref<HTMLElement | null>(null)

const position = computed(() => {
  if (!props.anchor) return { top: '100px', left: '100px' }
  return { top: `${props.anchor.y}px`, left: `${props.anchor.x}px` }
})

function close() {
  emit('close')
}

function onGoto() {
  emit('goto', props.pageNum)
  close()
}
function onRotate(deg: number) {
  emit('rotate', props.pageNum, deg)
  close()
}
function onExtract() {
  emit('extract', props.pageNum)
  close()
}
function onDelete() {
  emit('delete', props.pageNum)
  close()
}

function onDocClick(e: MouseEvent) {
  if (!props.open) return
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    close()
  }
}

function onKey(e: KeyboardEvent) {
  if (props.open && e.key === 'Escape') close()
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
.pdf-ctx-menu {
  position: fixed;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-8);
  padding: var(--space-1);
  min-width: 200px;
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
  padding: var(--space-2) var(--space-3);
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
.pdf-ctx-item .ico {
  width: 14px;
  height: 14px;
  color: var(--color-foreground-2);
  flex-shrink: 0;
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
