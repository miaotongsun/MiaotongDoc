<!--
  PdfExtractModeDialog.vue -- Phase 13.29 提取页面模式选择

  点击"提取"后弹出,让用户选择:
    - 保存为新文档(非破坏性,生成新 Document + 跳转)
    - 覆盖当前文档(破坏性,二次确认)
    - 下载 PDF 文件(不影响文档)
-->
<template>
  <el-dialog
    v-model="visible"
    :title="`提取 ${pages.length} 页到...`"
    width="460px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="pdf-extract-mode-list">
      <div
        v-for="m in modes"
        :key="m.id"
        class="pdf-extract-mode-card"
        :class="{ 'is-active': selected === m.id, 'is-danger': m.danger }"
        @click="selected = m.id"
      >
        <div class="pdf-extract-mode-icon">
          <PdfIcon :name="m.icon" :size="22" />
        </div>
        <div class="pdf-extract-mode-body">
          <div class="pdf-extract-mode-title">{{ m.title }}</div>
          <div class="pdf-extract-mode-desc">{{ m.desc }}</div>
        </div>
        <div class="pdf-extract-mode-radio">
          <span class="pdf-em-radio-dot" :class="{ 'is-checked': selected === m.id }"></span>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onConfirm">确认提取</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PdfIcon from './PdfIcon.vue'
import { pdfApi } from '@/api/pdf'
import { buildDownloadName as dlName, triggerDownload as dlTrigger } from '@/lib/download'

const props = defineProps<{
  modelValue: boolean
  docId: number
  /** Phase 13.30: 用于规范下载文件名 */
  docTitle?: string
  pages: number[]
  /** 提取覆盖模式用的 reload 回调(由父组件传) */
  onOverwriteReload?: () => Promise<void>
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 新文档模式:跳转新文档 */
  (e: 'goto-new', docId: number): void
  /** Phase 13.30: 下载模式完成后通知父组件清组织页面 busy(避免界面卡死) */
  (e: 'done'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const selected = ref<'new' | 'overwrite' | 'download'>('new')
const loading = ref(false)

const modes = [
  { id: 'new' as const, icon: 'insertFile', title: '保存为新文档', desc: '生成新 PDF 文档,原文档不变(推荐)', danger: false },
  { id: 'overwrite' as const, icon: 'merge', title: '覆盖当前文档', desc: '提取页替换当前文档,原页将丢失', danger: true },
  { id: 'download' as const, icon: 'export', title: '下载为 zip', desc: '每页一个独立 PDF 打包下载,不影响文档', danger: false },
]


async function onConfirm() {
  if (loading.value) return
  loading.value = true
  try {
    if (selected.value === 'new') {
      const r = await pdfApi.extractPagesToNew(props.docId, props.pages)
      ElMessage.success(r.message || '已提取到新文档')
      visible.value = false
      emit('goto-new', r.docId)
    } else if (selected.value === 'overwrite') {
      try {
        await ElMessageBox.confirm(
          '覆盖当前文档后,未选中的页将丢失且不可撤销。确认继续?',
          '覆盖确认',
          { type: 'warning', confirmButtonText: '确认覆盖', cancelButtonText: '取消' },
        )
      } catch {
        loading.value = false
        return
      }
      const r = await pdfApi.extractPages(props.docId, { pages: props.pages })
      ElMessage.success('已提取并覆盖当前文档')
      visible.value = false
      await props.onOverwriteReload?.()
    } else {
      // 下载模式:调 extractPagesBatch 返回 zip
      const blob = await pdfApi.extractPagesBatch(props.docId, props.pages)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = dlName('提取', props.pages.length === 1 ? 'pdf' : 'zip', props.docTitle)
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      ElMessage.success('已开始下载')
      visible.value = false
      emit('done')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '提取失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pdf-extract-mode-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pdf-extract-mode-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 2px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 120ms ease, background 120ms ease;
}
.pdf-extract-mode-card:hover {
  border-color: var(--color-primary, #3b6fe8);
  background: var(--color-primary-soft, #ebf1fe);
}
.pdf-extract-mode-card.is-active {
  border-color: var(--color-primary, #3b6fe8);
  background: var(--color-primary-soft, #ebf1fe);
}
.pdf-extract-mode-card.is-danger:hover,
.pdf-extract-mode-card.is-danger.is-active {
  border-color: var(--color-destructive, #ef4444);
  background: rgba(239, 68, 68, 0.08);
}
.pdf-extract-mode-icon {
  color: var(--color-primary, #3b6fe8);
  flex-shrink: 0;
}
.pdf-extract-mode-card.is-danger .pdf-extract-mode-icon {
  color: var(--color-destructive, #ef4444);
}
.pdf-extract-mode-body {
  flex: 1;
}
.pdf-extract-mode-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-foreground, #0f172a);
}
.pdf-extract-mode-desc {
  font-size: 12px;
  color: var(--color-foreground-3, #94a3b8);
  margin-top: 2px;
}
.pdf-em-radio-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--color-border, #cbd5e1);
  display: inline-block;
  transition: border-color 120ms ease;
}
.pdf-em-radio-dot.is-checked {
  border-color: var(--color-primary, #3b6fe8);
  background: radial-gradient(circle, var(--color-primary, #3b6fe8) 40%, transparent 45%);
}
.pdf-extract-mode-card.is-danger .pdf-em-radio-dot.is-checked {
  border-color: var(--color-destructive, #ef4444);
  background: radial-gradient(circle, var(--color-destructive, #ef4444) 40%, transparent 45%);
}
</style>
