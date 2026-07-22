<!--
  MergeDialog.vue —— 合并 PDF 多选对话框

  流程:
    1. 拉取当前用户可见的所有 PDF 文档(documentApi.list)
    2. 多选 checkbox(排除当前文档)
    3. 点击"开始合并"→ emit confirm([docIds])
    4. 调用方调 usePdfPageOps.merge(docIds)
-->
<template>
  <el-dialog
    v-model="visible"
    title="合并 PDF"
    width="560px"
    :close-on-click-modal="false"
    custom-class="pdf-dialog"
    @closed="onClosed"
  >
    <div class="merge-dialog">
      <p class="merge-hint">
        选择要合并的 PDF 文档。<strong>当前文档({{ excludeDocTitle }})</strong> 将作为第 1 份,选中的追加在后面。
      </p>

      <div class="merge-search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索 PDF 标题..."
          clearable
          size="default"
        >
          <template #prefix>
            <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
          </template>
        </el-input>
      </div>

      <el-checkbox-group v-model="selected" class="merge-list">
        <div v-if="loading" class="merge-loading">加载中…</div>
        <div v-else-if="filteredDocs.length === 0" class="merge-empty">
          没有可合并的 PDF
        </div>
        <el-checkbox
          v-for="doc in filteredDocs"
          v-else
          :key="doc.id"
          :value="doc.id"
          :label="doc.id"
          class="merge-item"
        >
          <div class="merge-item-row">
            <span class="merge-item-title">{{ doc.title }}</span>
            <span class="merge-item-meta">{{ doc.pageCount || '?' }} 页 · {{ formatSize(doc.fileSize) }}</span>
          </div>
        </el-checkbox>
      </el-checkbox-group>

      <div class="merge-summary">
        已选 <strong>{{ selected.length }}</strong> 个文档
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="selected.length === 0" @click="onConfirm">
        开始合并({{ selected.length + 1 }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { documentApi } from '@/api/document'

const props = defineProps<{
  modelValue: boolean
  excludeDocId: number
  excludeDocTitle?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirm', docIds: number[]): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const allDocs = ref<any[]>([])
const selected = ref<number[]>([])
const loading = ref(false)
const searchKeyword = ref('')

const filteredDocs = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  return allDocs.value.filter((d) => {
    if (d.id === props.excludeDocId) return false
    if (d.fileType !== 'pdf') return false
    if (!kw) return true
    return (d.title || '').toLowerCase().includes(kw)
  })
})

async function loadDocs() {
  loading.value = true
  try {
    const resp = await documentApi.list({ type: 'pdf', size: 200, page: 0 })
    const items = (resp as any)?.content || (resp as any)?.data?.content || []
    allDocs.value = items
  } catch (e) {
    console.error('[MergeDialog] loadDocs failed:', e)
    ElMessage.error('加载文档列表失败')
    allDocs.value = []
  } finally {
    loading.value = false
  }
}

function formatSize(bytes?: number): string {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function onConfirm() {
  if (selected.value.length === 0) {
    ElMessage.warning('请至少选择 1 个 PDF')
    return
  }
  // 目标文档 = 当前文档(docId 排第一)
  emit('confirm', [props.excludeDocId, ...selected.value])
  visible.value = false
}

function onClosed() {
  selected.value = []
  searchKeyword.value = ''
}

watch(visible, (v) => {
  if (v) {
    selected.value = []
    void loadDocs()
  }
})
</script>

<style scoped>
.merge-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.merge-hint {
  font-size: var(--text-sm);
  color: var(--color-foreground-2);
  margin: 0;
}
.merge-search {
  display: flex;
}
.merge-list {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.merge-loading,
.merge-empty {
  padding: var(--space-6);
  text-align: center;
  color: var(--color-foreground-3);
  font-size: var(--text-sm);
}
.merge-item {
  width: 100%;
  margin: 0 !important;
  padding: var(--space-2) var(--space-3) !important;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-out);
}
.merge-item:hover {
  background: var(--color-surface-2);
}
.merge-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.merge-item-title {
  font-size: var(--text-sm);
  color: var(--color-foreground);
  font-weight: 500;
}
.merge-item-meta {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
}
.merge-summary {
  font-size: var(--text-sm);
  color: var(--color-foreground-2);
  text-align: right;
}
.merge-summary strong {
  color: var(--color-primary);
}
.ico {
  stroke: currentColor;
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  width: 16px;
  height: 16px;
}
</style>
