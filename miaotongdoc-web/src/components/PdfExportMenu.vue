<!--
  PdfExportMenu.vue —— 工具栏"导出"下拉菜单

  提供:
    - 转换: Markdown / DOCX / TXT / PNG
    - 压缩: 高/中/低
    - 安全: 加密 / 解密
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
        aria-label="导出菜单"
        @click.stop
      >
        <div class="pdf-menu-section-title">格式转换</div>
        <button class="pdf-menu-item" :disabled="busy" @click="onConvert('md')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h12l4 4v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"/><path d="M14 4v4h4"/></svg>
          <span>Markdown</span>
          <span class="pdf-menu-shortcut">.md</span>
        </button>
        <button class="pdf-menu-item" :disabled="busy" @click="onConvert('docx')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h12l4 4v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"/><path d="M14 4v4h4M8 13h8M8 17h5"/></svg>
          <span>Word 文档</span>
          <span class="pdf-menu-shortcut">.docx</span>
        </button>
        <button class="pdf-menu-item" :disabled="busy" @click="onConvert('txt')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16v16H4z"/><path d="M7 8h10M7 12h10M7 16h6"/></svg>
          <span>纯文本</span>
          <span class="pdf-menu-shortcut">.txt</span>
        </button>
        <button class="pdf-menu-item" :disabled="busy" @click="onConvert('png')">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
          <span>图片</span>
          <span class="pdf-menu-shortcut">.png</span>
        </button>

        <div class="pdf-menu-divider"></div>

        <div class="pdf-menu-section-title">优化</div>
        <button class="pdf-menu-item" :disabled="busy" @click="onCompress">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7"/></svg>
          <span>压缩 PDF</span>
        </button>

        <div class="pdf-menu-divider"></div>

        <div class="pdf-menu-section-title">安全</div>
        <button class="pdf-menu-item" :disabled="busy" @click="onEncrypt">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <span>加密(设置密码)</span>
        </button>
        <button class="pdf-menu-item" :disabled="busy" @click="onDecrypt">
          <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg>
          <span>解密</span>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  open: boolean
  anchor: { x: number; y: number } | null
  docId: number
  /** 当前页码(导出 PNG 用) */
  currentPage: number
  /** 文件名(用于下载) */
  filename?: string
  /** Phase 13.21: 弹窗展开方向,'left' 时向左展开(右侧工具栏用) */
  anchorSide?: 'left' | 'right'
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const menuRef = ref<HTMLElement | null>(null)
const busy = ref(false)

const position = computed(() => {
  if (!props.anchor) return { top: '48px', left: '50%' }
  const top = `${props.anchor.y + 8}px`
  // anchorSide='left':anchor.x 为按钮右边缘,菜单向左展开(用 right 定位)
  if (props.anchorSide === 'left') {
    return { top, right: `${window.innerWidth - props.anchor.x}px`, left: 'auto' }
  }
  return { top, left: `${props.anchor.x}px` }
})

function triggerDownload(blob: Blob, defaultName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = defaultName
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 0)
}

async function onConvert(format: 'md' | 'docx' | 'txt' | 'png') {
  emit('close')
  busy.value = true
  try {
    const blob = await pdfApi.convert(props.docId, { targetFormat: format })
    const ext = format === 'docx' ? 'docx' : format
    const name = `${(props.filename || 'document').replace(/\.pdf$/i, '')}.${ext}`
    triggerDownload(blob, name)
    ElMessage.success(`已导出 ${name}`)
  } catch (e) {
    ElMessage.error(`导出失败:${(e as Error).message}`)
  } finally {
    busy.value = false
  }
}

async function onCompress() {
  emit('close')
  try {
    const result = await ElMessageBox({
      title: '压缩级别',
      message: '选择压缩强度(越高文件越小,但可能损失清晰度)',
      showInput: true,
      inputPlaceholder: 'high / medium / low',
      inputValue: 'medium',
      showCancelButton: true,
    } as any).catch(() => null)
    const level = (result as any)?.value
    if (!level) return
    busy.value = true
    const blob = await pdfApi.compress(props.docId, { level: level as 'high' | 'medium' | 'low' })
    triggerDownload(blob, `${props.filename || 'document'}_compressed.pdf`)
    ElMessage.success('已压缩')
  } catch (e) {
    ElMessage.error(`压缩失败:${(e as Error).message}`)
  } finally {
    busy.value = false
  }
}

async function onEncrypt() {
  emit('close')
  try {
    const result = await ElMessageBox({
      title: '加密 PDF',
      message: '设置打开密码',
      showInput: true,
      inputType: 'password',
      inputPlaceholder: '至少 6 位',
      showCancelButton: true,
    } as any).catch(() => null)
    const password = (result as any)?.value
    if (!password || password.length < 6) {
      ElMessage.warning('密码至少 6 位')
      return
    }
    busy.value = true
    const blob = await pdfApi.encrypt(props.docId, { password })
    triggerDownload(blob, `${props.filename || 'document'}_encrypted.pdf`)
    ElMessage.success('已加密')
  } catch (e) {
    ElMessage.error(`加密失败:${(e as Error).message}`)
  } finally {
    busy.value = false
  }
}

async function onDecrypt() {
  emit('close')
  try {
    const result = await ElMessageBox({
      title: '解密 PDF',
      message: '输入密码',
      showInput: true,
      inputType: 'password',
      showCancelButton: true,
    } as any).catch(() => null)
    const password = (result as any)?.value
    if (!password) return
    busy.value = true
    const blob = await pdfApi.decrypt(props.docId, { password })
    triggerDownload(blob, `${props.filename || 'document'}_decrypted.pdf`)
    ElMessage.success('已解密')
  } catch (e) {
    ElMessage.error(`解密失败:${(e as Error).message}`)
  } finally {
    busy.value = false
  }
}

function onDocClick(e: MouseEvent) {
  if (!props.open) return
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    emit('close')
  }
}
// 只在菜单打开时注册全局 click,避免与触发按钮竞态
watch(() => props.open, (open) => {
  if (open) {
    setTimeout(() => document.addEventListener('click', onDocClick), 0)
  } else {
    document.removeEventListener('click', onDocClick)
  }
})
onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<style scoped>
.pdf-menu-overlay {
  position: fixed;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-8);
  padding: var(--space-1);
  min-width: 220px;
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
