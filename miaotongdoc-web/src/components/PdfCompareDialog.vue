<!--
  PdfCompareDialog.vue —— Phase 14.U6 + Phase 27 重设计
  选择两个文档,显示逐页对比结果(same/modified/added/removed)+ 行级 diff

  重设计(Phase 27):
  - 自动填入当前文档 ID
  - 可从文档列表中选择(最近文档下拉)
  - 显示文档标题,不再只显示 ID
  - 结果更清晰,可跳转到差异页
-->
<template>
  <el-dialog
    v-model="visible"
    title="文档对比"
    width="880px"
    :close-on-click-modal="false"
    append-to-body
    custom-class="pdf-dialog pdf-compare-dialog"
  >
    <div class="pdf-cmp">
      <!-- 顶部:文档选择 + 操作 -->
      <div class="pdf-cmp-pickers">
        <div class="pdf-cmp-pick">
          <label>文档 A（当前文档）</label>
          <div class="pdf-cmp-pick-row">
            <el-select
              v-model="docIdA"
              placeholder="选择文档"
              filterable
              :disabled="busy"
              class="pdf-cmp-select"
              @change="onDocAChanged"
            >
              <el-option
                v-for="d in docList"
                :key="d.id"
                :label="d.title"
                :value="d.id"
              >
                <span>{{ d.title }}</span>
                <span class="pdf-cmp-opt-id">#{{ d.id }}</span>
              </el-option>
            </el-select>
            <span class="pdf-cmp-title">{{ titleA || '—' }}</span>
          </div>
        </div>
        <div class="pdf-cmp-pick">
          <label>文档 B（对比对象）</label>
          <div class="pdf-cmp-pick-row">
            <el-select
              v-model="docIdB"
              placeholder="选择要对比的文档"
              filterable
              :disabled="busy"
              class="pdf-cmp-select"
              @change="onDocBChanged"
            >
              <el-option
                v-for="d in docList"
                :key="d.id"
                :label="d.title"
                :value="d.id"
                :disabled="d.id === docIdA"
              >
                <span>{{ d.title }}</span>
                <span class="pdf-cmp-opt-id">#{{ d.id }}</span>
              </el-option>
            </el-select>
            <span class="pdf-cmp-title">{{ titleB || '—' }}</span>
          </div>
        </div>
      </div>

      <div class="pdf-cmp-actions">
        <button
          class="pdf-cmp-go"
          :disabled="!docIdA || !docIdB || docIdA === docIdB || busy"
          @click="runCompare"
        >
          <span v-if="busy">对比中...</span>
          <span v-else>开始对比</span>
        </button>
        <span v-if="docIdA === docIdB && (docIdA || docIdB)" class="pdf-cmp-hint">请选择两个不同的文档</span>
        <button class="pdf-cmp-refresh" :disabled="busy" @click="loadDocList" title="刷新文档列表">
          ↻
        </button>
      </div>

      <!-- 结果摘要 -->
      <div v-if="summary" class="pdf-cmp-summary">
        <div class="pdf-cmp-sum-cell">
          <div class="pdf-cmp-sum-num">{{ summary.totalPages }}</div>
          <div class="pdf-cmp-sum-lbl">总页数</div>
        </div>
        <div class="pdf-cmp-sum-cell is-same">
          <div class="pdf-cmp-sum-num">{{ summary.same }}</div>
          <div class="pdf-cmp-sum-lbl">相同</div>
        </div>
        <div class="pdf-cmp-sum-cell is-mod">
          <div class="pdf-cmp-sum-num">{{ summary.modified }}</div>
          <div class="pdf-cmp-sum-lbl">修改</div>
        </div>
        <div class="pdf-cmp-sum-cell is-add">
          <div class="pdf-cmp-sum-num">{{ summary.added }}</div>
          <div class="pdf-cmp-sum-lbl">新增</div>
        </div>
        <div class="pdf-cmp-sum-cell is-del">
          <div class="pdf-cmp-sum-num">{{ summary.removed }}</div>
          <div class="pdf-cmp-sum-lbl">删除</div>
        </div>
      </div>

      <!-- 页面详情 -->
      <div v-if="pages.length" class="pdf-cmp-pages">
        <div
          v-for="p in pages"
          :key="p.page"
          class="pdf-cmp-page"
          :class="`is-${p.status}`"
        >
          <div class="pdf-cmp-page-head">
            <span class="pdf-cmp-page-num">第 {{ p.page }} 页</span>
            <span class="pdf-cmp-page-status">{{ statusLabel(p.status) }}</span>
            <button v-if="p.status !== 'same'" class="pdf-cmp-page-jump" @click="jumpTo(p.page)" title="跳转到该页">跳转</button>
          </div>
          <div v-if="p.diffHunks && p.diffHunks.length" class="pdf-cmp-hunks">
            <div
              v-for="(h, i) in p.diffHunks"
              :key="i"
              class="pdf-cmp-hunk"
              :class="`is-${h.type}`"
            >
              <span class="pdf-cmp-hunk-mark">{{ markOf(h.type) }}</span>
              <span class="pdf-cmp-hunk-text">{{ h.text }}</span>
            </div>
          </div>
          <div v-else class="pdf-cmp-hunks is-empty">
            <em>(无内容差异)</em>
          </div>
        </div>
      </div>

      <div v-else-if="!busy && !pages.length" class="pdf-cmp-empty">
        <p>选择两个文档点击"开始对比"</p>
        <p class="pdf-cmp-empty-hint">当前文档已自动填入文档 A,选择文档 B 即可开始</p>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  modelValue: boolean
  /** 默认填入的 A 文档 ID */
  defaultDocId?: number
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'jump-to', page: number): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const docIdA = ref<number | undefined>(props.defaultDocId)
const docIdB = ref<number | undefined>(undefined)
const titleA = ref('')
const titleB = ref('')
const busy = ref(false)
const summary = ref<any>(null)
const pages = ref<Array<any>>([])
const docList = ref<Array<{ id: number; title: string }>>([])

onMounted(() => {
  loadDocList()
})

watch(() => props.modelValue, (v) => {
  if (v) {
    docIdA.value = props.defaultDocId
    loadDocList()
    if (props.defaultDocId) loadTitleById(props.defaultDocId, 'A')
  }
})

async function loadDocList() {
  try {
    const token = sessionStorage.getItem('token') || ''
    // Phase 27: 一次拉全(不分页),用户可对比自己所有可见文档
    // size 设 200 是为防止文档过多导致 UI 卡顿(>200 时按时间排序取最新 200)
    const r = await fetch('/api/documents/list?page=0&size=200', {
      headers: { Authorization: `Bearer ${token}` },
    })
    const data = await r.json()
    const content = data?.content || data?.data?.content || []
    docList.value = content.map((d: any) => ({ id: d.id, title: d.title || `文档 ${d.id}` }))
  } catch (e) {
    console.warn('加载文档列表失败:', e)
  }
}

async function onDocAChanged() {
  titleA.value = ''
  if (docIdA.value) loadTitleById(docIdA.value, 'A')
}

async function onDocBChanged() {
  titleB.value = ''
  if (docIdB.value) loadTitleById(docIdB.value, 'B')
}

async function loadTitleById(id: number, which: 'A' | 'B') {
  try {
    const r: any = await pdfApi.getMetadata(id)
    const t = (which === 'A' ? titleA : titleB)
    t.value = r?.title || `文档 ${id}`
  } catch {
    ;(which === 'A' ? titleA : titleB).value = `文档 ${id}`
  }
}

async function runCompare() {
  if (!docIdA.value || !docIdB.value) return
  busy.value = true
  try {
    const r: any = await pdfApi.compare(docIdA.value, docIdB.value)
    if (!r.success) {
      ElMessage.error('对比失败')
      return
    }
    summary.value = r.summary
    pages.value = r.pages
    const diffCount = (r.summary.modified || 0) + (r.summary.added || 0) + (r.summary.removed || 0)
    if (diffCount > 0) {
      ElMessage.success(`对比完成: ${diffCount} 处差异`)
    } else {
      ElMessage.success('两个文档完全一致')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '对比失败')
  } finally {
    busy.value = false
  }
}

function jumpTo(page: number) {
  emit('jump-to', page)
  visible.value = false
}

function statusLabel(s: string) {
  return { same: '相同', modified: '有修改', added: 'B 新增', removed: 'B 删除' }[s] || s
}
function markOf(t: string) {
  return { eq: ' ', add: '+', del: '−' }[t] || ' '
}

import { computed } from 'vue'
</script>

<style scoped>
.pdf-cmp { display: flex; flex-direction: column; gap: 14px; }
.pdf-cmp-pickers { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.pdf-cmp-pick label { display: block; font-size: 12px; color: var(--color-foreground-2); margin-bottom: 4px; }
.pdf-cmp-pick-row { display: flex; align-items: center; gap: 6px; }
.pdf-cmp-select { flex: 1; }
.pdf-cmp-opt-id { color: var(--color-foreground-3); font-size: 11px; margin-left: 6px; }
.pdf-cmp-title { font-size: 12px; color: var(--color-foreground-3); margin-left: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 140px; }
.pdf-cmp-actions { display: flex; gap: 10px; align-items: center; }
.pdf-cmp-go { padding: 8px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; }
.pdf-cmp-go:hover:not(:disabled) { background: #2c5cd9; }
.pdf-cmp-go:disabled { opacity: 0.5; cursor: not-allowed; }
.pdf-cmp-refresh { padding: 6px 10px; border: 1px solid var(--color-border); border-radius: 4px; background: #fff; cursor: pointer; font-size: 14px; }
.pdf-cmp-refresh:hover:not(:disabled) { background: var(--color-surface-2); }
.pdf-cmp-hint { color: var(--color-destructive); font-size: 12px; }
.pdf-cmp-summary { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; padding: 10px 0; border-top: 1px solid var(--color-divider); border-bottom: 1px solid var(--color-divider); }
.pdf-cmp-sum-cell { text-align: center; padding: 8px; border-radius: 6px; background: var(--color-surface-2); }
.pdf-cmp-sum-cell.is-same { background: rgba(34, 197, 94, 0.1); }
.pdf-cmp-sum-cell.is-mod  { background: rgba(245, 158, 11, 0.1); }
.pdf-cmp-sum-cell.is-add  { background: rgba(59, 111, 232, 0.1); }
.pdf-cmp-sum-cell.is-del  { background: rgba(239, 68, 68, 0.1); }
.pdf-cmp-sum-num { font-size: 20px; font-weight: 700; color: var(--color-foreground); }
.pdf-cmp-sum-lbl { font-size: 11px; color: var(--color-foreground-3); margin-top: 2px; }
.pdf-cmp-pages { max-height: 360px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.pdf-cmp-page { border: 1px solid var(--color-divider); border-radius: 6px; overflow: hidden; }
.pdf-cmp-page-head { display: flex; justify-content: space-between; align-items: center; padding: 6px 12px; background: var(--color-surface-2); font-size: 12px; }
.pdf-cmp-page-num { font-weight: 600; }
.pdf-cmp-page-jump { padding: 2px 8px; border: 1px solid var(--color-border); border-radius: 4px; background: #fff; cursor: pointer; font-size: 11px; }
.pdf-cmp-page-jump:hover { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.pdf-cmp-page-status { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: #fff; }
.pdf-cmp-page.is-same .pdf-cmp-page-status { color: #16a34a; }
.pdf-cmp-page.is-modified .pdf-cmp-page-status { color: #d97706; }
.pdf-cmp-page.is-added .pdf-cmp-page-status { color: #3b6fe8; }
.pdf-cmp-page.is-removed .pdf-cmp-page-status { color: #ef4444; }
.pdf-cmp-hunks { padding: 8px 12px; font-family: var(--font-mono); font-size: 11px; line-height: 1.6; }
.pdf-cmp-hunk { display: flex; gap: 6px; padding: 2px 4px; border-radius: 3px; }
.pdf-cmp-hunk.is-eq { color: var(--color-foreground); }
.pdf-cmp-hunk.is-add { background: rgba(34, 197, 94, 0.12); color: #15803d; }
.pdf-cmp-hunk.is-del { background: rgba(239, 68, 68, 0.12); color: #b91c1c; }
.pdf-cmp-hunk-mark { width: 14px; color: var(--color-foreground-3); flex-shrink: 0; }
.pdf-cmp-hunk-text { flex: 1; word-break: break-all; }
.pdf-cmp-hunks.is-empty { color: var(--color-foreground-3); padding: 16px; text-align: center; }
.pdf-cmp-empty { color: var(--color-foreground-3); text-align: center; padding: 40px 0; }
.pdf-cmp-empty-hint { font-size: 12px; margin-top: 8px; color: var(--color-foreground-3); }
</style>