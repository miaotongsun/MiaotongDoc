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
          <span>{{ t.label }}</span>
        </button>
      </div>
      <button class="pdf-rp-collapse" :aria-label="collapsed ? '展开' : '折叠'" @click="$emit('collapse')">
        <PdfIcon :name="collapsed ? 'panel' : 'close'" :size="14" />
      </button>
    </header>

    <!-- Phase 14.U10: 功能应用提示区(顶部小卡片,说明此 tab 用途) -->
    <div v-show="!collapsed && activeTip" class="pdf-rp-tip">
      <span>💡</span>
      <span>{{ activeTip.text }}</span>
    </div>

    <!-- 内容 -->
    <div v-show="!collapsed" class="pdf-rp-body">
      <!-- Tab 1: 大纲/书签 -->
      <div v-show="activeTab === 'outline'" class="pdf-rp-content">
        <div v-if="outline.length === 0" class="pdf-rp-empty">
          <PdfIcon name="panelOutline" :size="32" />
          <p>该 PDF 无大纲</p>
          <p class="pdf-rp-empty-hint">PDF 未内嵌目录书签</p>
          <button class="pdf-rp-empty-action" @click="$emit('generate-outline')">
            <PdfIcon name="ai" :size="14" />
            <span>AI 生成智能目录</span>
          </button>
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
              <div v-else-if="ann.type && ['rectangle','ellipse','arrow','line','underline','strikethrough','highlight','draw'].includes(ann.type)" class="pdf-rp-ann-text pdf-rp-ann-empty-text">{{ typeLabel(ann.type) }}标注</div>
              <div v-else class="pdf-rp-ann-text pdf-rp-ann-empty-text">(无文本内容)</div>
            </div>
          </li>
        </ul>
      </div>

      <!-- Tab 4: 表单(Phase 12.1 + 12.2) -->
      <div v-show="activeTab === 'form'" class="pdf-rp-content">
        <div v-if="formFields.length > 0" class="pdf-rp-form-toolbar">
          <button class="pdf-rp-form-apply" :disabled="formSaving" @click="applyFormFill">
            <span v-if="formSaving" class="pdf-rp-form-spinner"></span>
            <span v-else>应用填充</span>
          </button>
          <button class="pdf-rp-form-reset" @click="resetFormFill">重置</button>
        </div>
        <div class="pdf-rp-form-filter">
          <button
            v-for="f in formTypeFilters"
            :key="f.id"
            class="pdf-rp-form-filter-btn"
            :class="{ 'is-active': formTypeFilter === f.id }"
            @click="formTypeFilter = f.id"
          >
            {{ f.label }}
          </button>
        </div>
        <div v-if="formLoading" class="pdf-rp-loading">识别中…</div>
        <div v-else-if="filteredFormFields.length === 0" class="pdf-rp-empty">
          <PdfIcon name="panelForm" :size="32" />
          <p>{{ formFields.length === 0 ? '该 PDF 无表单字段' : '该类型无字段' }}</p>
          <p class="pdf-rp-empty-hint" v-if="formFields.length === 0">仅 AcroForm 交互式表单可识别(普通文本非表单)</p>
          <button class="pdf-rp-empty-action" v-if="formFields.length === 0" :disabled="formLoading" @click="loadFormFields">
            <span v-if="formLoading">识别中...</span>
            <span v-else>重新识别表单</span>
          </button>
        </div>
        <ul v-else class="pdf-rp-form-list">
          <li
            v-for="f in filteredFormFields"
            :key="f.name"
            class="pdf-rp-form-item"
            :class="{ 'is-readonly': f.readOnly, 'is-required': f.required, 'is-dirty': formDraft[f.name] !== undefined && formDraft[f.name] !== f.value }"
            @click="onFocusField(f)"
          >
            <div class="pdf-rp-form-head">
              <span class="pdf-rp-form-type" :style="{ background: formTypeColor(f.type) }">
                {{ formTypeLabel(f.type) }}
              </span>
              <span class="pdf-rp-form-name" :title="f.name">{{ f.partialName || f.name }}</span>
              <span v-if="f.page > 0" class="pdf-rp-form-page">P{{ f.page }}</span>
            </div>
            <div class="pdf-rp-form-body">
              <!-- Phase 12.2: 内联编辑控件 -->
              <div v-if="!f.readOnly" class="pdf-rp-form-input" @click.stop>
                <input
                  v-if="f.type === 'text'"
                  v-model="formDraft[f.name]"
                  type="text"
                  class="pdf-rp-form-input-el"
                  :placeholder="f.value || '请输入'"
                />
                <label v-else-if="f.type === 'checkbox'" class="pdf-rp-form-checkbox">
                  <input
                    v-model="formDraft[f.name]"
                    type="checkbox"
                    true-value="true"
                    false-value="false"
                  />
                  <span>{{ formDraft[f.name] === 'true' ? '已勾选' : '未勾选' }}</span>
                </label>
                <select
                  v-else-if="f.type === 'radio' || f.type === 'combobox' || f.type === 'listbox'"
                  v-model="formDraft[f.name]"
                  class="pdf-rp-form-input-el"
                >
                  <option value="">{{ f.value || '(未选)' }}</option>
                  <option v-for="opt in (f.options || [])" :key="opt" :value="opt">{{ opt }}</option>
                </select>
                <span v-else-if="f.type === 'signature'" class="pdf-rp-form-signature-hint">
                  签名字段请在画布上签署
                </span>
                <span v-else class="pdf-rp-form-readonly-hint">不支持编辑</span>
              </div>
              <div v-else class="pdf-rp-form-value pdf-rp-form-readonly-value" :title="f.value">
                只读: {{ f.value || '(空)' }}
              </div>
              <div v-if="f.options && f.options.length && f.type !== 'radio' && f.type !== 'combobox' && f.type !== 'listbox'" class="pdf-rp-form-options">
                选项: {{ f.options.slice(0, 5).join(' / ') }}{{ f.options.length > 5 ? '...' : '' }}
              </div>
              <div class="pdf-rp-form-flags">
                <span v-if="f.required" class="pdf-rp-form-flag is-required">必填</span>
                <span v-if="f.readOnly" class="pdf-rp-form-flag is-readonly">只读</span>
              </div>
            </div>
          </li>
        </ul>
      </div>

      <!-- Tab 5: 信息 -->
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
import { ref, computed, watch, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import PdfIcon from './PdfIcon.vue'
import { pdfApi } from '@/api/pdf'
import type { PdfFormField } from '@/api/pdf'
import type { PdfAnnotation } from '@/composables/pdf/usePdfCollaborate'

const props = defineProps<{
  docId: number
  initialTab?: 'outline' | 'search' | 'info' | 'annotations' | 'form'
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
  /** Phase 12.1: 聚焦表单字段(跳转 + 高亮) */
  (e: 'focus-field', field: { page: number; rect: [number, number, number, number]; name: string }): void
  /** Phase 12.2: 表单填充后下载新 PDF */
  (e: 'form-filled', blob: Blob): void
  /** Phase 13.26: 表单 in-place 填充后通知父组件 reload */
  (e: 'form-filled-inplace'): void
  /** Phase 13.36: 大纲为空时,触发 AI 生成智能目录 */
  (e: 'generate-outline'): void
}>()

const activeTab = ref(props.initialTab || 'outline')
const tabs = [
  { id: 'outline' as const, label: '大纲', icon: 'panelOutline' },
  { id: 'search' as const, label: '搜索', icon: 'panelSearch' },
  { id: 'annotations' as const, label: '批注', icon: 'panelComment' },
  { id: 'form' as const, label: '表单', icon: 'panelForm' },
  { id: 'info' as const, label: '信息', icon: 'menu' },
]

/** Phase 14.U10: 功能应用提示(顶部小卡片) */
const tips: Record<string, { text: string }> = {
  outline: { text: '文档书签/目录。点击跳转到对应页面;空时可在 AI tab 生成。' },
  search: { text: '全文关键字搜索。输入词后自动检索,点击结果跳页定位。' },
  annotations: { text: '我的全部批注(高亮/评论/形状/图章)。可按类型筛选、删除、跳转。' },
  form: { text: 'PDF 表单字段识别与填充。仅 AcroForm 交互式表单可识别。' },
  info: { text: '文档元信息:标题、作者、创建时间、页数等。' },
}
const activeTip = computed(() => tips[activeTab.value] || null)

/** Phase 13.31: 父组件切换 initialTab 时,同步 activeTab(避免重复点击切不到) */
watch(() => props.initialTab, (t) => {
  if (t && t !== activeTab.value) activeTab.value = t
})

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

// Phase 12.1: 表单字段
const formFields = ref<PdfFormField[]>([])
const formLoading = ref(false)
const formTypeFilter = ref<'all' | PdfFormField['type']>('all')
const formTypeFilters: Array<{ id: 'all' | PdfFormField['type']; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'text', label: '文本' },
  { id: 'checkbox', label: '复选' },
  { id: 'radio', label: '单选' },
  { id: 'combobox', label: '下拉' },
  { id: 'signature', label: '签名' },
]
const filteredFormFields = computed(() => {
  if (formTypeFilter.value === 'all') return formFields.value
  return formFields.value.filter(f => f.type === formTypeFilter.value)
})
function formTypeLabel(t: PdfFormField['type']): string {
  const m: Record<string, string> = {
    text: '文本', checkbox: '复选', radio: '单选', combobox: '下拉', listbox: '列表', signature: '签名', unknown: '未知',
  }
  return m[t] || t
}
function formTypeColor(t: PdfFormField['type']): string {
  const m: Record<string, string> = {
    text: '#409EFF', checkbox: '#67C23A', radio: '#E6A23C', combobox: '#F56C6C', listbox: '#909399', signature: '#9254DE', unknown: '#909399',
  }
  return m[t] || '#909399'
}
function onFocusField(f: PdfFormField) {
  if (f.page > 0) {
    emit('focus-field', { page: f.page, rect: f.rect, name: f.partialName || f.name })
  }
}

// Phase 12.2: 表单填充
const formDraft = reactive<Record<string, string>>({})
const formSaving = ref(false)
async function loadFormFields() {
  formLoading.value = true
  try {
    formFields.value = await pdfApi.getFormFields(props.docId)
    // 初始化草稿为当前值
    formFields.value.forEach(f => {
      formDraft[f.name] = f.value || ''
    })
  } catch (e) {
    console.error('[PdfRightPanel] loadFormFields failed:', e)
    formFields.value = []
  } finally {
    formLoading.value = false
  }
}
function resetFormFill() {
  formFields.value.forEach(f => {
    formDraft[f.name] = f.value || ''
  })
  ElMessage.info('已重置为原始值')
}
async function applyFormFill() {
  // 收集修改过的字段
  const dirty: Record<string, string> = {}
  formFields.value.forEach(f => {
    const original = f.value || ''
    const current = formDraft[f.name] ?? ''
    if (current !== original && !f.readOnly) {
      dirty[f.name] = current
    }
  })
  if (Object.keys(dirty).length === 0) {
    ElMessage.warning('没有修改的字段')
    return
  }
  // 必填校验
  const missingRequired = formFields.value.filter(f => f.required && !formDraft[f.name])
  if (missingRequired.length > 0) {
    ElMessage.warning(`有 ${missingRequired.length} 个必填字段未填写`)
    return
  }
  formSaving.value = true
  try {
    // Phase 13.26: in-place 填充(落盘 + 父组件 reload),不再下载 Blob
    await pdfApi.fillFormFieldsInPlace(props.docId, dirty)
    ElMessage.success(`已填充 ${Object.keys(dirty).length} 个字段并保存到文档`)
    emit('form-filled-inplace')
  } catch (e: any) {
    console.error('[PdfRightPanel] applyFormFill failed:', e)
    ElMessage.error(e?.message || '填充失败')
  } finally {
    formSaving.value = false
  }
}

// 监听 docId 变化 + 初次激活 tab 时加载
watch(
  () => props.docId,
  () => {
    outline.value = []
    searchResults.value = []
    metadata.value = null
    formFields.value = []
    if (activeTab.value === 'outline') loadOutline()
    if (activeTab.value === 'info') loadMetadata()
    if (activeTab.value === 'form') loadFormFields()
  },
  { immediate: true },
)
watch(activeTab, (t) => {
  if (t === 'outline' && outline.value.length === 0) loadOutline()
  if (t === 'info' && !metadata.value) loadMetadata()
  if (t === 'form' && formFields.value.length === 0 && !formLoading.value) loadFormFields()
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

/* Phase 14.U10: 功能应用提示 */
.pdf-rp-tip {
  padding: 8px 14px;
  background: linear-gradient(135deg, #f0f7ff 0%, #e8f0fe 100%);
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-foreground-2, #475569);
  line-height: 1.5;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

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

/* Phase 13.36: 空状态操作按钮(生成目录/重新识别) */
.pdf-rp-empty-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 6px 14px;
  border: 1px solid var(--color-primary, #3b6fe8);
  border-radius: 6px;
  background: var(--color-primary-soft, #ebf1fe);
  color: var(--color-primary, #3b6fe8);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 120ms ease, transform 120ms ease;
}
.pdf-rp-empty-action:hover:not(:disabled) {
  background: var(--color-primary, #3b6fe8);
  color: #fff;
  transform: translateY(-1px);
}
.pdf-rp-empty-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

/* ============ Phase 12.1: 表单 tab ============ */
.pdf-rp-form-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-divider);
}
.pdf-rp-form-filter-btn {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-foreground-2);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}
.pdf-rp-form-filter-btn:hover {
  background: var(--color-surface-2);
  border-color: var(--color-border-strong);
}
.pdf-rp-form-filter-btn.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.pdf-rp-form-list {
  list-style: none;
  margin: 0;
  padding: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.pdf-rp-form-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  background: var(--color-surface);
}
.pdf-rp-form-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-2);
  transform: translateY(-1px);
}
.pdf-rp-form-item.is-readonly {
  opacity: 0.7;
}
.pdf-rp-form-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 4px;
}
.pdf-rp-form-type {
  font-size: 11px;
  color: #fff;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-weight: 600;
  flex-shrink: 0;
}
.pdf-rp-form-name {
  flex: 1;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pdf-rp-form-page {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.pdf-rp-form-body {
  font-size: var(--text-xs);
  color: var(--color-foreground-2);
}
.pdf-rp-form-value {
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pdf-rp-form-empty {
  color: var(--color-foreground-3);
  font-style: italic;
}
.pdf-rp-form-options {
  margin-bottom: 2px;
  color: var(--color-foreground-3);
}
.pdf-rp-form-flags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
.pdf-rp-form-flag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  font-weight: 600;
}
.pdf-rp-form-flag.is-required {
  background: #FEF0F0;
  color: #F56C6C;
}
.pdf-rp-form-flag.is-readonly {
  background: var(--color-surface-2);
  color: var(--color-foreground-3);
}

/* Phase 12.2: 表单填充 UI */
.pdf-rp-form-toolbar {
  display: flex;
  gap: 6px;
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-divider);
}
.pdf-rp-form-apply,
.pdf-rp-form-reset {
  flex: 1;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all var(--duration-fast) var(--ease-out);
}
.pdf-rp-form-apply {
  background: var(--color-primary);
  color: #fff;
}
.pdf-rp-form-apply:hover:not(:disabled) {
  background: var(--color-primary-hover, #66B1FF);
}
.pdf-rp-form-apply:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.pdf-rp-form-reset {
  flex: 0 0 auto;
  background: var(--color-surface);
  border-color: var(--color-border);
  color: var(--color-foreground-2);
}
.pdf-rp-form-reset:hover {
  background: var(--color-surface-2);
  border-color: var(--color-border-strong);
}
.pdf-rp-form-spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pdf-rp-form-spin 0.8s linear infinite;
}
@keyframes pdf-rp-form-spin {
  to { transform: rotate(360deg); }
}
.pdf-rp-form-item.is-dirty {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
.pdf-rp-form-input {
  margin-top: 4px;
  margin-bottom: 4px;
}
.pdf-rp-form-input-el {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  background: var(--color-surface);
  color: var(--color-foreground);
  transition: border-color var(--duration-fast) var(--ease-out);
}
.pdf-rp-form-input-el:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-soft);
}
.pdf-rp-form-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: var(--text-sm);
}
.pdf-rp-form-checkbox input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}
.pdf-rp-form-signature-hint,
.pdf-rp-form-readonly-hint {
  font-size: var(--text-xs);
  color: var(--color-foreground-3);
  font-style: italic;
}
.pdf-rp-form-readonly-value {
  font-style: italic;
  color: var(--color-foreground-3);
  padding: 4px 0;
}
</style>