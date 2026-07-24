<!--
  PdfCompareDialog.vue —— Phase 14.U6 文档对比弹窗
  选择两个文档,显示逐页对比结果(same/modified/added/removed)+ 行级 diff
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
          <label>文档 A</label>
          <div class="pdf-cmp-pick-row">
            <input v-model.number="docIdA" type="number" placeholder="输入文档 ID" class="pdf-cmp-input" :disabled="busy" />
            <button class="pdf-cmp-load" :disabled="!docIdA || busy" @click="loadTitle('A')">加载</button>
            <span class="pdf-cmp-title">{{ titleA || '—' }}</span>
          </div>
        </div>
        <div class="pdf-cmp-pick">
          <label>文档 B</label>
          <div class="pdf-cmp-pick-row">
            <input v-model.number="docIdB" type="number" placeholder="输入文档 ID" class="pdf-cmp-input" :disabled="busy" />
            <button class="pdf-cmp-load" :disabled="!docIdB || busy" @click="loadTitle('B')">加载</button>
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
        输入两个文档 ID 开始对比。
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  modelValue: boolean
  /** 默认填入的 A 文档 ID */
  defaultDocId?: number
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

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

watch(() => props.modelValue, (v) => {
  if (v && props.defaultDocId) {
    docIdA.value = props.defaultDocId
  }
})

async function loadTitle(which: 'A' | 'B') {
  const id = which === 'A' ? docIdA.value : docIdB.value
  if (!id) return
  try {
    const r: any = await pdfApi.getMetadata(id)
    const t = (which === 'A' ? titleA : titleB)
    t.value = r?.title || `文档 ${id}`
  } catch {
    (which === 'A' ? titleA : titleB).value = `文档 ${id}`
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
    ElMessage.success(`对比完成:${r.summary.modified} 处修改,${r.summary.added} 处新增,${r.summary.removed} 处删除`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '对比失败')
  } finally {
    busy.value = false
  }
}

function statusLabel(s: string) {
  return { same: '相同', modified: '有修改', added: 'B 新增', removed: 'B 删除' }[s] || s
}
function markOf(t: string) {
  return { eq: ' ', add: '+', del: '−' }[t] || ' '
}
</script>

<style scoped>
.pdf-cmp { display: flex; flex-direction: column; gap: 14px; }
.pdf-cmp-pickers { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.pdf-cmp-pick label { display: block; font-size: 12px; color: var(--color-foreground-2); margin-bottom: 4px; }
.pdf-cmp-pick-row { display: flex; align-items: center; gap: 6px; }
.pdf-cmp-input { flex: 0 0 110px; height: 28px; padding: 0 8px; border: 1px solid var(--color-border); border-radius: 4px; font-size: 12px; }
.pdf-cmp-load { padding: 4px 10px; border: 1px solid var(--color-border); border-radius: 4px; background: #fff; cursor: pointer; font-size: 12px; }
.pdf-cmp-load:hover:not(:disabled) { background: var(--color-surface-2); }
.pdf-cmp-title { font-size: 12px; color: var(--color-foreground-3); margin-left: 6px; }
.pdf-cmp-actions { display: flex; gap: 10px; align-items: center; }
.pdf-cmp-go { padding: 8px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-weight: 600; }
.pdf-cmp-go:hover:not(:disabled) { background: #2c5cd9; }
.pdf-cmp-go:disabled { opacity: 0.5; cursor: not-allowed; }
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
.pdf-cmp-page-head { display: flex; justify-content: space-between; padding: 6px 12px; background: var(--color-surface-2); font-size: 12px; }
.pdf-cmp-page-num { font-weight: 600; }
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
</style>