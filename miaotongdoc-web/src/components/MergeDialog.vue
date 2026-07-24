<!--
  MergeDialog.vue -- Phase 13.29 合并 PDF 完整重写

  功能:
    1. 文档列表(当前文档默认第一,可上下调整顺序,可删除)
    2. 每文档可设页区间(默认"全部",可改"1-3,5")
    3. 添加文档:从文档库搜索勾选 + 上传本地 PDF
    4. 目标:覆盖当前文档 / 保存为新文档(可设标题)
    5. emit confirm({documents, target})
-->
<template>
  <el-dialog
    v-model="visible"
    title="合并 PDF"
    width="720px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <div class="md-steps">1. 添加文档 · 2. 调整顺序 · 3. 设置页区间 · 4. 选择目标</div>

    <!-- 已选文档列表(可排序) -->
    <div class="md-section-title">待合并文档({{ mergeList.length }})</div>
    <div class="md-list">
      <div v-if="mergeList.length === 0" class="md-empty">请从下方添加文档</div>
      <div
        v-for="(item, idx) in mergeList"
        :key="item.docId"
        class="md-row"
        :class="{ 'is-current': item.docId === excludeDocId }"
      >
        <div class="md-row-handle">{{ idx + 1 }}</div>
        <div class="md-row-info">
          <div class="md-row-title">{{ item.title }}<span v-if="item.docId === excludeDocId" class="md-current-tag">当前</span></div>
          <div class="md-row-meta">{{ item.pageCount || '?' }} 页</div>
        </div>
        <div class="md-row-ranges">
          <el-input
            v-model="item.pageRanges"
            size="small"
            placeholder="全部"
            :disabled="item.loading"
            class="md-range-input"
          />
          <span class="md-range-hint">页区间</span>
        </div>
        <div class="md-row-actions">
          <button class="md-icon-btn" :disabled="idx === 0" title="上移" @click="moveUp(idx)">↑</button>
          <button class="md-icon-btn" :disabled="idx === mergeList.length - 1" title="下移" @click="moveDown(idx)">↓</button>
          <button class="md-icon-btn md-danger" title="移除" @click="removeItem(idx)">✕</button>
        </div>
      </div>
    </div>

    <!-- 添加文档区 -->
    <div class="md-add">
      <div class="md-add-bar">
        <el-input v-model="searchKeyword" placeholder="搜索文档库添加..." size="small" clearable class="md-search">
          <template #prefix><span class="md-search-ico">🔍</span></template>
        </el-input>
        <el-upload
          :show-file-list="false"
          :before-upload="onUploadFile"
          accept="application/pdf"
        >
          <el-button size="small" :loading="uploading">上传本地 PDF</el-button>
        </el-upload>
      </div>
      <div v-if="searchKeyword" class="md-search-list">
        <div v-if="filteredCandidates.length === 0" class="md-search-empty">无匹配文档</div>
        <div
          v-for="doc in filteredCandidates"
          :key="doc.id"
          class="md-candidate"
          @click="addDoc(doc)"
        >
          <span class="md-candidate-title">{{ doc.title }}</span>
          <span class="md-candidate-meta">{{ doc.pageCount || '?' }} 页 · {{ formatSize(doc.fileSize) }}</span>
          <span class="md-candidate-add">+ 添加</span>
        </div>
      </div>
    </div>

    <!-- 目标选择 -->
    <div class="md-section-title">合并结果</div>
    <div class="md-target">
      <label class="md-target-opt" :class="{ 'is-active': targetMode === 'new' }">
        <input type="radio" v-model="targetMode" value="new" />
        <span>保存为新文档</span>
      </label>
      <label class="md-target-opt" :class="{ 'is-active': targetMode === 'overwrite' }">
        <input type="radio" v-model="targetMode" value="overwrite" />
        <span>覆盖当前文档</span>
      </label>
      <el-input
        v-if="targetMode === 'new'"
        v-model="newTitle"
        size="small"
        placeholder="新文档标题"
        class="md-title-input"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="merging" :disabled="mergeList.length === 0" @click="onConfirm">
        开始合并({{ mergeList.length }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import { documentApi } from '@/api/document'

const props = defineProps<{
  modelValue: boolean
  excludeDocId: number
  excludeDocTitle?: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirm', payload: {
    documents: Array<{ docId: number; pageRanges?: string }>
    target: { mode: 'new' | 'overwrite'; docId?: number; title?: string }
  }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

interface MergeItem { docId: number; title: string; pageCount?: number; pageRanges: string; loading?: boolean }
const mergeList = ref<MergeItem[]>([])
const allDocs = ref<any[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const uploading = ref(false)
const merging = ref(false)
const targetMode = ref<'new' | 'overwrite'>('new')
const newTitle = ref('')

const filteredCandidates = computed(() => {
  const inList = new Set(mergeList.value.map((m) => m.docId))
  const kw = searchKeyword.value.trim().toLowerCase()
  return allDocs.value.filter((d) => {
    if (inList.has(d.id)) return false
    if (d.fileType !== 'pdf') return false
    if (!kw) return true
    return (d.title || '').toLowerCase().includes(kw)
  }).slice(0, 20)
})

async function loadDocs() {
  loading.value = true
  try {
    const resp = await documentApi.list({ type: 'pdf', size: 200, page: 0 })
    const items = (resp as any)?.content || (resp as any)?.data?.content || []
    allDocs.value = items
  } catch (e) {
    console.error('[MergeDialog] loadDocs failed:', e)
    allDocs.value = []
  } finally {
    loading.value = false
  }
}

function addDoc(doc: any) {
  if (mergeList.value.some((m) => m.docId === doc.id)) return
  mergeList.value.push({
    docId: doc.id,
    title: doc.title || '未命名',
    pageCount: doc.pageCount,
    pageRanges: '',
  })
  searchKeyword.value = ''
}

async function onUploadFile(file: UploadRawFile) {
  uploading.value = true
  try {
    const resp = await documentApi.upload(file as any)
    const newDoc = (resp as any)?.data || resp
    mergeList.value.push({
      docId: newDoc.id,
      title: newDoc.title || file.name,
      pageCount: newDoc.pageCount,
      pageRanges: '',
    })
    ElMessage.success('上传成功,已加入列表')
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false  // 阻止 el-upload 默认上传
}

function moveUp(idx: number) {
  if (idx === 0) return
  const arr = mergeList.value
  ;[arr[idx - 1], arr[idx]] = [arr[idx], arr[idx - 1]]
}
function moveDown(idx: number) {
  const arr = mergeList.value
  if (idx === arr.length - 1) return
  ;[arr[idx + 1], arr[idx]] = [arr[idx], arr[idx + 1]]
}
function removeItem(idx: number) {
  mergeList.value.splice(idx, 1)
}

function formatSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function onConfirm() {
  if (mergeList.value.length === 0) {
    ElMessage.warning('请至少添加 1 个文档')
    return
  }
  merging.value = true
  try {
    const documents = mergeList.value.map((m) => ({
      docId: m.docId,
      pageRanges: m.pageRanges || undefined,
    }))
    const target: { mode: 'new' | 'overwrite'; docId?: number; title?: string } = {
      mode: targetMode.value,
    }
    if (targetMode.value === 'overwrite') {
      target.docId = props.excludeDocId
    } else {
      target.title = newTitle.value || '合并文档'
    }
    emit('confirm', { documents, target })
    visible.value = false
  } finally {
    merging.value = false
  }
}

function onClosed() {
  mergeList.value = []
  searchKeyword.value = ''
  newTitle.value = ''
  targetMode.value = 'new'
}

watch(visible, (v) => {
  if (v) {
    mergeList.value = [{
      docId: props.excludeDocId,
      title: props.excludeDocTitle || '当前文档',
      pageCount: undefined,
      pageRanges: '',
    }]
    void loadDocs()
  }
}, { immediate: true })
</script>

<style scoped>
.md-steps {
  font-size: 12px;
  color: var(--color-foreground-3, #94a3b8);
  margin-bottom: 12px;
}
.md-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-foreground, #0f172a);
  margin: 12px 0 6px;
}
.md-list {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.md-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-foreground-3, #94a3b8);
  font-size: 13px;
}
.md-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-surface-2, #f8fafc);
  transition: background 120ms ease;
}
.md-row:hover {
  background: var(--color-surface-2, #f1f5f9);
}
.md-row.is-current {
  background: var(--color-primary-soft, #ebf1fe);
}
.md-row-handle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-primary, #3b6fe8);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.md-row-info {
  flex: 1;
  min-width: 0;
}
.md-row-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-foreground, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.md-current-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 10px;
  background: var(--color-primary, #3b6fe8);
  color: #fff;
  border-radius: 3px;
}
.md-row-meta {
  font-size: 11px;
  color: var(--color-foreground-3, #94a3b8);
  margin-top: 2px;
}
.md-row-ranges {
  display: flex;
  align-items: center;
  gap: 6px;
}
.md-range-input {
  width: 100px;
}
.md-range-hint {
  font-size: 11px;
  color: var(--color-foreground-3, #94a3b8);
}
.md-row-actions {
  display: flex;
  gap: 2px;
}
.md-icon-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border, #e2e8f0);
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-foreground-2, #475569);
  transition: background 120ms ease;
}
.md-icon-btn:hover:not(:disabled) {
  background: var(--color-surface-2, #f1f5f9);
}
.md-icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.md-icon-btn.md-danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-destructive, #ef4444);
}
.md-add {
  margin-top: 10px;
}
.md-add-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.md-search {
  flex: 1;
}
.md-search-list {
  margin-top: 6px;
  max-height: 160px;
  overflow-y: auto;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 6px;
}
.md-candidate {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: background 120ms ease;
}
.md-candidate:hover {
  background: var(--color-surface-2, #f1f5f9);
}
.md-candidate-title {
  flex: 1;
  font-size: 13px;
  color: var(--color-foreground, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.md-candidate-meta {
  font-size: 11px;
  color: var(--color-foreground-3, #94a3b8);
}
.md-candidate-add {
  font-size: 12px;
  color: var(--color-primary, #3b6fe8);
  font-weight: 600;
}
.md-search-empty {
  padding: 16px;
  text-align: center;
  color: var(--color-foreground-3, #94a3b8);
  font-size: 13px;
}
.md-target {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.md-target-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 2px solid var(--color-border, #e2e8f0);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: border-color 120ms ease, background 120ms ease;
}
.md-target-opt.is-active {
  border-color: var(--color-primary, #3b6fe8);
  background: var(--color-primary-soft, #ebf1fe);
}
.md-target-opt input {
  margin: 0;
}
.md-title-input {
  flex: 1;
  min-width: 160px;
}
.md-search-ico {
  font-size: 12px;
}
</style>
