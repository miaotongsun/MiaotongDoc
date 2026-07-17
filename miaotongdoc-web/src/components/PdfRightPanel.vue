<!--
  PdfRightPanel.vue —— V3 右侧任务面板(Phase 8 完整实现)

  3 个 tab(Adobe 风格):
    - 大纲/书签 - 树形展示 + 点击跳转
    - 搜索 - 全文搜索 + 结果列表 + 高亮跳转
    - 信息 - PDF 元数据 + 文件信息
-->
<template>
  <aside class="pdf-rp" :class="{ 'is-collapsed': collapsed }" role="complementary">
    <!-- 头部(标签栏) -->
    <header class="pdf-rp-header">
      <div class="pdf-rp-tabs" role="tablist">
        <button
          v-for="t in tabs"
          :key="t.id"
          class="pdf-rp-tab"
          :class="{ 'is-active': activeTab === t.id }"
          :aria-selected="activeTab === t.id"
          role="tab"
          @click="activeTab = t.id"
        >
          <PdfIcon :name="t.icon" :size="14" />
          <span>{{ t.label }}</span>
        </button>
      </div>
      <button class="pdf-rp-collapse" :aria-label="collapsed ? '展开' : '折叠'" @click="$emit('collapse')">
        <PdfIcon :name="collapsed ? 'panel' : 'close'" :size="14" />
      </button>
    </header>

    <!-- 内容 -->
    <div v-show="!collapsed" class="pdf-rp-body">
      <!-- Tab 1: 大纲/书签 -->
      <div v-show="activeTab === 'outline'" class="pdf-rp-content">
        <div v-if="outline.length === 0" class="pdf-rp-empty">
          <PdfIcon name="panelOutline" :size="32" />
          <p>该 PDF 无大纲</p>
        </div>
        <ul v-else class="pdf-rp-tree">
          <li
            v-for="node in outline"
            :key="`${node.level}-${node.title}`"
            class="pdf-rp-tree-item"
            :style="{ paddingLeft: 12 + node.level * 14 + 'px' }"
            @click="onJump(node.page)"
          >
            <span class="pdf-rp-tree-icon" v-if="node.level === 0">▸</span>
            <span class="pdf-rp-tree-icon" v-else>·</span>
            <span class="pdf-rp-tree-title">{{ node.title }}</span>
            <span class="pdf-rp-tree-page">P{{ node.page }}</span>
          </li>
        </ul>
      </div>

      <!-- Tab 2: 搜索 -->
      <div v-show="activeTab === 'search'" class="pdf-rp-content">
        <div class="pdf-rp-search-bar">
          <input
            v-model="searchKeyword"
            type="text"
            class="pdf-rp-search-input"
            placeholder="搜索文档..."
            @keyup.enter="doSearch"
          />
          <button class="pdf-rp-search-btn" :disabled="searching" @click="doSearch">
            <svg v-if="!searching" class="ico" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.3-4.3" />
            </svg>
            <span v-else class="pdf-rp-search-spinner"></span>
          </button>
        </div>
        <div v-if="searchResults.length > 0" class="pdf-rp-search-meta">
          共 {{ searchResults.length }} 个匹配
        </div>
        <div v-if="searchResults.length === 0 && searchedKeyword && !searching" class="pdf-rp-empty">
          <PdfIcon name="panelSearch" :size="32" />
          <p>未找到 "{{ searchedKeyword }}"</p>
        </div>
        <ul v-if="searchResults.length > 0" class="pdf-rp-search-list">
          <li
            v-for="(hit, i) in searchResults"
            :key="i"
            class="pdf-rp-search-item"
            @click="onJump(hit.page)"
          >
            <div class="pdf-rp-search-page">第 {{ hit.page }} 页</div>
            <div class="pdf-rp-search-snippet">{{ hit.snippet }}</div>
          </li>
        </ul>
      </div>

      <!-- Tab 3: 批注 -->
      <div v-show="activeTab === 'annotations'" class="pdf-rp-content">
        <div class="pdf-rp-ann-filter">
          <button
            v-for="f in annFilters"
            :key="f.id"
            class="pdf-rp-ann-filter-btn"
            :class="{ 'is-active': annTypeFilter === f.id }"
            @click="annTypeFilter = f.id"
          >
            {{ f.label }}
            <span class="pdf-rp-ann-filter-count">{{ countByType(f.id) }}</span>
          </button>
        </div>
        <div v-if="filteredAnnotations.length === 0" class="pdf-rp-empty">
          <PdfIcon name="panelComment" :size="32" />
          <p>{{ annTypeFilter === 'all' ? '尚无批注' : '该类型无批注' }}</p>
          <p class="pdf-rp-empty-hint">在画布上使用工具栏的形状/高亮/评论即可添加</p>
        </div>
        <ul v-else class="pdf-rp-ann-list">
          <li
            v-for="ann in filteredAnnotations"
            :key="ann.id"
            class="pdf-rp-ann-item"
            :class="{ 'is-mine': ann.userId === props.currentUserId }"
            @click="onJump(ann.pageNumber)"
          >
            <div class="pdf-rp-ann-head">
              <span class="pdf-rp-ann-type-icon" :style="{ background: ann.color }">
                <PdfIcon :name="typeIcon(ann.type)" :size="11" />
              </span>
              <span class="pdf-rp-ann-type-label">{{ typeLabel(ann.type) }}</span>
              <span class="pdf-rp-ann-page">P{{ ann.pageNumber }}</span>
              <button
                v-if="ann.userId === props.currentUserId"
                class="pdf-rp-ann-delete"
                :aria-label="`删除批注`"
                @click.stop="onRemoveAnn(ann.id)"
              >
                <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
                  <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14z" />
                </svg>
              </button>
            </div>
            <div class="pdf-rp-ann-body">
              <div class="pdf-rp-ann-user">{{ ann.userName }} · {{ formatTime(ann.createdAt) }}</div>
              <div v-if="ann.content" class="pdf-rp-ann-text">{{ ann.content }}</div>
              <div v-else-if="ann.stampText" class="pdf-rp-ann-stamp" :style="{ color: ann.color }">{{ ann.stampText }}</div>
              <div v-else class="pdf-rp-ann-text pdf-rp-ann-empty-text">(无文本内容)</div>
            </div>
          </li>
        </ul>
      </div>

      <!-- Tab 4: 信息 -->
      <div v-show="activeTab === 'info'" class="pdf-rp-content">
        <div v-if="!metadata" class="pdf-rp-loading">加载中…</div>
        <table v-else class="pdf-rp-info-table">
          <tr v-for="(item, key) in displayMeta" :key="key">
            <td class="pdf-rp-info-label">{{ item.label }}</td>
            <td class="pdf-rp-info-value" :title="String(item.value)">{{ item.value }}</td>
          </tr>
        </table>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import PdfIcon from './PdfIcon.vue'
import { pdfApi } from '@/api/pdf'
import type { PdfAnnotation } from '@/composables/pdf/usePdfCollaborate'

const props = defineProps<{
  docId: number
  initialTab?: 'outline' | 'search' | 'info' | 'annotations'
  collapsed?: boolean
  /** 当前文档所有标注(由父组件 PdfEditor 注入) */
  annotations?: PdfAnnotation[]
  /** 当前登录用户 ID(用于显示"我的"标注) */
  currentUserId?: number
}>()

const emit = defineEmits<{
  (e: 'jump', page: number): void
  (e: 'collapse'): void
  (e: 'remove-annotation', id: string): void
}>()

const activeTab = ref(props.initialTab || 'outline')
const tabs = [
  { id: 'outline' as const, label: '大纲', icon: 'panelOutline' },
  { id: 'search' as const, label: '搜索', icon: 'panelSearch' },
  { id: 'annotations' as const, label: '批注', icon: 'panelComment' },
  { id: 'info' as const, label: '信息', icon: 'menu' },
]

// 大纲
const outline = ref<Array<{ title: string; level: number; page: number }>>([])
async function loadOutline() {
  try {
    const r = await pdfApi.getOutline(props.docId)
    outline.value = (r.outline || []) as any
  } catch (e) {
    console.error('[PdfRightPanel] loadOutline failed:', e)
  }
}

// 搜索
const searchKeyword = ref('')
const searchedKeyword = ref('')
const searchResults = ref<Array<{ page: number; snippet: string }>>([])
const searching = ref(false)
let searchTimer: number | null = null

watch(searchKeyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => doSearch(), 300)
})

async function doSearch() {
  const q = searchKeyword.value.trim()
  if (!q) {
    searchResults.value = []
    searchedKeyword.value = ''
    return
  }
  searching.value = true
  searchedKeyword.value = q
  try {
    const r = await pdfApi.searchPost(props.docId, { q })
    searchResults.value = (r.results || []) as any
  } catch (e) {
    console.error('[PdfRightPanel] search failed:', e)
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

// 批注(Phase 10.2)
type AnnTypeFilter = 'all' | 'comment' | 'highlight' | 'shape' | 'stamp'
const annTypeFilter = ref<AnnTypeFilter>('all')
const annFilters: Array<{ id: AnnTypeFilter; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'comment', label: '评论' },
  { id: 'highlight', label: '高亮' },
  { id: 'shape', label: '形状' },
  { id: 'stamp', label: '图章' },
]
const annotationList = computed<PdfAnnotation[]>(() => props.annotations || [])
const filteredAnnotations = computed(() => {
  const list = annotationList.value
  switch (annTypeFilter.value) {
    case 'comment':  return list.filter(a => a.type === 'comment')
    case 'highlight': return list.filter(a => a.type === 'highlight')
    case 'stamp':    return list.filter(a => a.type === 'stamp')
    case 'shape':    return list.filter(a => ['rectangle', 'ellipse', 'arrow', 'line', 'underline', 'strikethrough'].includes(a.type))
    default:         return list
  }
})
function countByType(t: AnnTypeFilter): number {
  if (t === 'all') return annotationList.value.length
  return filteredAnnotationsByType(t).length
}
function filteredAnnotationsByType(t: AnnTypeFilter): PdfAnnotation[] {
  const list = annotationList.value
  switch (t) {
    case 'comment':  return list.filter(a => a.type === 'comment')
    case 'highlight': return list.filter(a => a.type === 'highlight')
    case 'stamp':    return list.filter(a => a.type === 'stamp')
    case 'shape':    return list.filter(a => ['rectangle', 'ellipse', 'arrow', 'line', 'underline', 'strikethrough'].includes(a.type))
    default:         return list
  }
}

function typeLabel(t: string): string {
  const map: Record<string, string> = {
    highlight: '高亮',
    comment: '评论',
    draw: '画笔',
    rectangle: '矩形',
    ellipse: '椭圆',
    arrow: '箭头',
    line: '直线',
    underline: '下划线',
    strikethrough: '删除线',
    stamp: '图章',
  }
  return map[t] || t
}
function typeIcon(t: string): string {
  const map: Record<string, string> = {
    highlight: 'highlight',
    comment: 'comment',
    draw: 'draw',
    rectangle: 'rectangle',
    ellipse: 'ellipse',
    arrow: 'arrow',
    line: 'line',
    underline: 'underline',
    strikethrough: 'strikethrough',
    stamp: 'stamp',
  }
  return map[t] || 'panelComment'
}
function formatTime(iso: string): string {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch { return iso }
}
function onRemoveAnn(id: string) {
  emit('remove-annotation', id)
}

// 元数据
const metadata = ref<Record<string, any> | null>(null)
async function loadMetadata() {
  try {
    metadata.value = await pdfApi.getMetadata(props.docId)
  } catch (e) {
    console.error('[PdfRightPanel] loadMetadata failed:', e)
  }
}

// 监听 docId 变化 + 初次激活 tab 时加载
watch(
  () => props.docId,
  () => {
    outline.value = []
    searchResults.value = []
    metadata.value = null
    if (activeTab.value === 'outline') loadOutline()
    if (activeTab.value === 'info') loadMetadata()
  },
  { immediate: true },
)
watch(activeTab, (t) => {
  if (t === 'outline' && outline.value.length === 0) loadOutline()
  if (t === 'info' && !metadata.value) loadMetadata()
})

// 跳转
function onJump(page: number) {
  emit('jump', page)
}

// 元数据显示映射
const displayMeta = computed(() => {
  if (!metadata.value) return []
  const m = metadata.value as any
  return [
    { label: '标题', value: m.title || '—' },
    { label: '作者', value: m.author || '—' },
    { label: '主题', value: m.subject || '—' },
    { label: '创建者', value: m.creator || '—' },
    { label: '生产者', value: m.producer || '—' },
    { label: '创建时间', value: m.creationDate || '—' },
    { label: '修改时间', value: m.modificationDate || '—' },
    { label: '页数', value: m.pageCount || '—' },
    { label: 'PDF 版本', value: m.pdfVersion || '—' },
    { label: '文件大小', value: m.fileSize ? formatSize(m.fileSize as number) : '—' },
    { label: '页面尺寸', value: m.pageWidth ? `${Math.round(m.pageWidth)} × ${Math.round(m.pageHeight)} pt` : '—' },
  ]
})

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>

<style scoped>
.pdf-rp {
  width: 320px;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-4);
  z-index: var(--z-right-panel);
  flex-shrink: 0;
  transition: width var(--duration-base) var(--ease-out);
  height: 100%;
  overflow: hidden;
}
.pdf-rp.is-collapsed { width: 36px; }

.pdf-rp-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-2);
  flex-shrink: 0;
  min-height: 36px;
}

.pdf-rp-tabs {
  display: flex;
  flex: 1;
  height: 100%;
}

.pdf-rp-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 100%;
  padding: 0 var(--space-2);
  color: var(--color-foreground-3);
  font-size: var(--text-xs, 11px);
  font-weight: var(--font-weight-medium, 500);
  position: relative;
  transition: color var(--duration-fast, 120ms) var(--ease-out, ease);
  background: transparent;
}
.pdf-rp-tab:hover { color: var(--color-foreground); background: var(--color-surface); }
.pdf-rp-tab.is-active { color: var(--color-primary); background: var(--color-surface); }
.pdf-rp-tab.is-active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 8px;
  right: 8px;
  height: 2px;
  background: var(--color-primary);
  border-radius: 1px 1px 0 0;
}

.pdf-rp-collapse {
  width: 32px;
  height: 32px;
  margin: 2px 4px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-3);
  background: transparent;
  transition: background 120ms ease;
}
.pdf-rp-collapse:hover { background: var(--color-surface); color: var(--color-foreground); }

.pdf-rp-body {
  flex: 1;
  overflow-y: auto;
  background: var(--color-surface);
}

.pdf-rp-content {
  padding: var(--space-3);
}

/* 大纲 */
.pdf-rp-tree {
  list-style: none;
  margin: 0;
  padding: 0;
}
.pdf-rp-tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--text-sm, 12px);
  color: var(--color-foreground-2);
  transition: background 120ms ease;
}
.pdf-rp-tree-item:hover { background: var(--color-surface-2); color: var(--color-foreground); }
.pdf-rp-tree-icon { color: var(--color-foreground-4); font-size: 10px; flex-shrink: 0; }
.pdf-rp-tree-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pdf-rp-tree-page {
  color: var(--color-foreground-3);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

/* 搜索 */
.pdf-rp-search-bar {
  display: flex;
  gap: 4px;
  margin-bottom: var(--space-2);
}
.pdf-rp-search-input {
  flex: 1;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  background: var(--color-surface);
  color: var(--color-foreground);
  outline: none;
}
.pdf-rp-search-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px var(--color-primary-soft); }

.pdf-rp-search-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-foreground-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.pdf-rp-search-btn:hover:not(:disabled) { background: var(--color-primary-soft); border-color: var(--color-primary); color: var(--color-primary); }
.pdf-rp-search-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.pdf-rp-search-spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: pdf-rp-spin 0.8s linear infinite;
}
@keyframes pdf-rp-spin { to { transform: rotate(360deg); } }

.pdf-rp-search-meta {
  font-size: 10px;
  color: var(--color-foreground-3);
  padding: 4px 4px 8px;
}

.pdf-rp-search-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.pdf-rp-search-item {
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  transition: all 120ms ease;
}
.pdf-rp-search-item:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.pdf-rp-search-page {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 4px;
}
.pdf-rp-search-snippet {
  font-size: 12px;
  color: var(--color-foreground-2);
  line-height: 1.4;
  word-break: break-word;
}

/* 信息 */
.pdf-rp-info-table {
  width: 100%;
  border-collapse: collapse;
}
.pdf-rp-info-table tr {
  border-bottom: 1px solid var(--color-divider);
}
.pdf-rp-info-table tr:last-child { border-bottom: none; }
.pdf-rp-info-label {
  padding: 8px 4px 8px 0;
  font-size: 11px;
  color: var(--color-foreground-3);
  font-weight: 500;
  width: 80px;
  vertical-align: top;
}
.pdf-rp-info-value {
  padding: 8px 0;
  font-size: 11px;
  color: var(--color-foreground);
  word-break: break-word;
  max-width: 200px;
}

/* 空态 */
.pdf-rp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-3);
  color: var(--color-foreground-3);
  text-align: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
}
.pdf-rp-empty p { margin: 0; }

.pdf-rp-loading {
  padding: var(--space-6);
  text-align: center;
  color: var(--color-foreground-3);
  font-size: var(--text-sm);
}

/* ===== Phase 10.2 批注面板 ===== */
.pdf-rp-ann-filter {
  display: flex;
  gap: 4px;
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-divider);
  flex-wrap: wrap;
}
.pdf-rp-ann-filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-foreground-2);
  font-size: 11px;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease, color 120ms ease;
}
.pdf-rp-ann-filter-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.pdf-rp-ann-filter-btn.is-active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}
.pdf-rp-ann-filter-count {
  font-variant-numeric: tabular-nums;
  font-size: 10px;
  color: var(--color-foreground-3);
}
.pdf-rp-ann-filter-btn.is-active .pdf-rp-ann-filter-count {
  color: var(--color-primary);
}

.pdf-rp-empty-hint {
  font-size: 11px !important;
  color: var(--color-foreground-3);
  opacity: 0.7;
  margin-top: 4px;
}

.pdf-rp-ann-list {
  list-style: none;
  margin: 0;
  padding: var(--space-2) 0;
}
.pdf-rp-ann-item {
  position: relative;
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-divider);
  cursor: pointer;
  transition: background 120ms ease;
}
.pdf-rp-ann-item:hover {
  background: var(--color-surface-2);
}
.pdf-rp-ann-item.is-mime,
.pdf-rp-ann-item.is-mine {
  background: var(--color-primary-soft);
}
.pdf-rp-ann-item.is-mine:hover {
  filter: brightness(0.97);
}
.pdf-rp-ann-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.pdf-rp-ann-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-sm);
  color: #fff;
  flex-shrink: 0;
}
.pdf-rp-ann-type-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-foreground);
}
.pdf-rp-ann-page {
  margin-left: auto;
  font-size: 10px;
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
}
.pdf-rp-ann-delete {
  background: transparent;
  border: none;
  padding: 2px;
  color: var(--color-foreground-3);
  cursor: pointer;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 120ms ease, color 120ms ease;
}
.pdf-rp-ann-delete:hover {
  background: var(--color-destructive);
  color: #fff;
}
.pdf-rp-ann-body {
  padding-left: 24px;
}
.pdf-rp-ann-user {
  font-size: 10px;
  color: var(--color-foreground-3);
  margin-bottom: 2px;
}
.pdf-rp-ann-text {
  font-size: 12px;
  color: var(--color-foreground);
  word-break: break-word;
  white-space: pre-wrap;
}
.pdf-rp-ann-empty-text {
  color: var(--color-foreground-3);
  font-style: italic;
}
.pdf-rp-ann-stamp {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.ico {
  stroke: currentColor;
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>