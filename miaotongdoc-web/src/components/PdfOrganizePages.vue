<!--
  PdfOrganizePages.vue -- Phase 13.12-D 全屏"组织页面"视图

  Adobe Acrobat Organize Pages 风格的全屏页面管理:
    - 顶部:标题 + 缩放控件 + 关闭
    - 工具栏:插入(空白/文件) | 提取 | 删除 | 旋转(左/右) | 裁剪 | 拆分 | 合并 | 全选/清空
    - 主区:大缩略图响应式网格,checkbox 多选 + shift 区间 + 拖拽调序 + 悬浮快捷操作
    - 底部:共 N 页 · 已选 M 页

  修改当前文档的操作(删除/旋转/提取/插入空白/合并/重排)emit 给 PdfEditor 走 pageOps + reload;
  下载类操作(拆分 zip / 批量导出 zip)在本组件内直接调 pdfApi。
  缩略图由父组件注入的 pdfDoc 渲染,懒加载 + zoom 控制网格列宽。
-->
<template>
  <Teleport to="body">
    <div class="pdf-org-overlay" role="dialog" aria-label="组织页面">
        <!-- 顶部:标题 + 缩放 + 关闭 -->
        <header class="pdf-org-header">
          <div class="pdf-org-title">
            <PdfIcon name="organize" :size="18" />
            <span>组织页面</span>
            <span class="pdf-org-title-meta">{{ totalPages }} 页</span>
          </div>
          <button class="pdf-org-close" aria-label="关闭" @click="$emit('close')">
            <PdfIcon name="close" :size="18" />
          </button>
        </header>

        <!-- 工具栏 -->
        <div class="pdf-org-toolbar">
          <div class="pdf-org-tool-group">
            <button class="pdf-org-tool" :disabled="totalPages === 0 || busy" @click="$emit('op-insert-blank', lastPage)">
              <PdfIcon name="insert" :size="14" /><span>插入空白</span>
            </button>
            <button class="pdf-org-tool" :disabled="busy" @click="$emit('op-insert-file')">
              <PdfIcon name="insertFile" :size="14" /><span>从文件</span>
            </button>
          </div>
          <span class="pdf-org-tool-sep"></span>
          <div class="pdf-org-tool-group">
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onExtract">
              <PdfIcon name="extract" :size="14" /><span>提取</span>
            </button>
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onReplace">
              <PdfIcon name="insertFile" :size="14" /><span>替换<span v-if="selected.size">({{ selected.size }})</span></span>
            </button>
            <button class="pdf-org-tool pdf-org-tool-danger" :disabled="selected.size === 0 || busy" @click="onDelete">
              <PdfIcon name="close" :size="14" /><span>删除<span v-if="selected.size">({{ selected.size }})</span></span>
            </button>
          </div>
          <span class="pdf-org-tool-sep"></span>
          <div class="pdf-org-tool-group">
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onRotate(-90)">
              <PdfIcon name="rotate" :size="14" :style="{ transform: 'scaleX(-1)' }" /><span>左旋</span>
            </button>
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onRotate(90)">
              <PdfIcon name="rotate" :size="14" /><span>右旋</span>
            </button>
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onCrop">
              <PdfIcon name="crop" :size="14" /><span>裁剪</span>
            </button>
          </div>
          <span class="pdf-org-tool-sep"></span>
          <div class="pdf-org-tool-group">
            <button class="pdf-org-tool" :disabled="totalPages === 0 || busy" @click="onSplit">
              <PdfIcon name="extract" :size="14" /><span>拆分</span>
            </button>
            <button class="pdf-org-tool" :disabled="busy" @click="$emit('op-merge')">
              <PdfIcon name="merge" :size="14" /><span>合并</span>
            </button>
            <button class="pdf-org-tool" :disabled="selected.size === 0 || busy" @click="onBatchExport">
              <PdfIcon name="export" :size="14" /><span>导出 zip</span>
            </button>
          </div>
          <span class="pdf-org-tool-sep"></span>
          <div class="pdf-org-tool-group">
            <button class="pdf-org-tool" @click="selectAll">全选</button>
            <button class="pdf-org-tool" :disabled="selected.size === 0" @click="clearSelection">清空</button>
          </div>
        </div>

        <!-- Phase 13.29: 缩放控件移到画布顶部居中 -->
        <div class="pdf-org-zoom-bar">
          <button class="pdf-org-icon-btn" aria-label="缩小" :disabled="zoomIdx <= 0" @click="zoomOut">
            <PdfIcon name="zoomOut" :size="16" />
          </button>
          <span class="pdf-org-zoom-val">{{ zoomLevels[zoomIdx] }}px</span>
          <button class="pdf-org-icon-btn" aria-label="放大" :disabled="zoomIdx >= zoomLevels.length - 1" @click="zoomIn">
            <PdfIcon name="zoomIn" :size="16" />
          </button>
        </div>

        <!-- 主区:缩略图网格 -->
        <div class="pdf-org-body">
          <div v-if="totalPages === 0" class="pdf-org-empty">暂无页面</div>
          <TransitionGroup
            v-else
            ref="gridRef"
            name="pdf-org-grid"
            tag="div"
            class="pdf-org-grid"
            :style="{ '--thumb-size': zoomLevels[zoomIdx] + 'px' }"
          >
            <div
              v-for="i in totalPages"
              :key="i"
              class="pdf-org-thumb"
              :class="{
                'is-selected': selected.has(i),
                'is-dragging': dragFrom === i,
                'is-drop-target': dropTarget === i && dragFrom != null && dragFrom !== i,
              }"
              :draggable="true"
              @click="onCardClick($event, i)"
              @dragstart="onDragStart($event, i)"
              @dragover.prevent="onDragOver(i)"
              @dragleave="onDragLeave(i)"
              @drop.prevent="onDrop(i)"
              @dragend="onDragEnd"
            >
              <div class="pdf-org-thumb-canvas-wrap">
                <div v-if="!rendered.has(i)" class="pdf-org-skeleton"></div>
                <canvas
                  :ref="(el) => bindThumb(el, i)"
                  :data-page-num="i"
                  v-show="rendered.has(i)"
                  class="pdf-org-thumb-canvas"
                ></canvas>
                <!-- 悬浮快捷操作 -->
                <div v-if="rendered.has(i)" class="pdf-org-thumb-actions" @click.stop>
                  <button class="pdf-org-thumb-action" title="右旋 90°" :disabled="busy" @click="rotateOne(i, 90)">
                    <PdfIcon name="rotate" :size="13" />
                  </button>
                  <button class="pdf-org-thumb-action pdf-org-tool-danger-icon" title="删除" :disabled="busy" @click="deleteOne(i)">
                    <PdfIcon name="close" :size="13" />
                  </button>
                </div>
              </div>
              <div class="pdf-org-thumb-meta">
                <label class="pdf-org-checkbox" @click.stop>
                  <input type="checkbox" :checked="selected.has(i)" @click.stop="onToggle(i, $event)" />
                  <span class="pdf-org-thumb-num">第 {{ i }} 页</span>
                </label>
              </div>
            </div>
          </TransitionGroup>
        </div>

        <!-- 底部状态栏 -->
        <footer class="pdf-org-footer">
          <span>共 <strong>{{ totalPages }}</strong> 页</span>
          <span class="pdf-org-footer-sep">·</span>
          <span>已选 <strong>{{ selected.size }}</strong> 页</span>
          <span v-if="selected.size > 0" class="pdf-org-footer-hint">支持 shift 区间选择 · 拖拽缩略图调序</span>
          <span v-else class="pdf-org-footer-hint">点击选择 · shift 区间 · 拖拽调序</span>
        </footer>

        <!-- 拆分子弹窗(el-dialog 统一风格) -->
        <el-dialog
          v-model="splitDialogOpen"
          title="拆分 PDF"
          width="480px"
          :close-on-click-modal="false"
          custom-class="pdf-dialog"
          append-to-body
        >
          <div class="pdf-org-sub-body">
            <div class="pdf-org-sub-card">
              <div class="pdf-org-sub-card-title">模式 A · 每页一个 PDF</div>
              <div class="pdf-org-sub-card-desc">将 {{ totalPages }} 页拆分为 {{ totalPages }} 个独立 PDF,打包 zip 下载。</div>
              <button class="pdf-org-primary-btn" :disabled="totalPages === 0 || busy" @click="onSplitEveryPage">拆分并下载 zip</button>
            </div>
            <div class="pdf-org-sub-card">
              <div class="pdf-org-sub-card-title">模式 B · 按区间拆分</div>
              <div class="pdf-org-sub-card-desc">输入区间(如 <code>1-3,5,7-9</code>),<b>每个区间生成一个含该区间所有页的 PDF</b>(如 1-3 生成含第1-3页的 PDF),打包 zip 下载。</div>
              <input v-model="splitRanges" type="text" class="pdf-org-input" placeholder="1-3,5,7-9" />
              <button class="pdf-org-primary-btn" :disabled="!splitRanges.trim() || busy" @click="onSplitByRanges">按区间拆分 zip</button>
              <div v-if="splitError" class="pdf-org-error">{{ splitError }}</div>
            </div>
          </div>
        </el-dialog>

        <!-- Phase 13.37: 替换页面子弹窗 -->
        <el-dialog
          v-model="replaceDialogOpen"
          title="替换页面"
          width="480px"
          :close-on-click-modal="false"
          custom-class="pdf-dialog"
          append-to-body
        >
          <div class="pdf-org-sub-body">
            <div class="pdf-org-sub-card">
              <div class="pdf-org-sub-card-title">替换选中页</div>
              <div class="pdf-org-sub-card-desc">
                将选中的 <b>{{ replaceTargetPages.length }}</b> 页(第 {{ replaceTargetPages.join('、') }} 页)替换为上传 PDF 的对应页。
                上传 PDF 从「源起始页」开始,按顺序取相同数量页逐页替换。
              </div>
              <div class="pdf-org-sub-card-title" style="margin-top:12px">1. 上传源 PDF</div>
              <input type="file" accept="application/pdf" class="pdf-org-input" @change="onReplaceFileChange" />
              <div v-if="replaceFileName" class="pdf-org-footer-hint" style="margin-top:4px">已选: {{ replaceFileName }}(共 {{ replaceFilePages }} 页)</div>
              <div class="pdf-org-sub-card-title" style="margin-top:12px">2. 源起始页</div>
              <input v-model.number="replaceSourceStart" type="number" min="1" class="pdf-org-input" placeholder="1" />
              <div class="pdf-org-footer-hint" style="margin-top:4px">从源 PDF 的该页开始,取 {{ replaceTargetPages.length }} 页替换选中页</div>
              <div v-if="replaceError" class="pdf-org-error">{{ replaceError }}</div>
              <button class="pdf-org-primary-btn" :disabled="!replaceFile || replaceTargetPages.length === 0 || busy" @click="onReplaceConfirm">
                {{ busy ? '替换中...' : '确认替换' }}
              </button>
            </div>
          </div>
        </el-dialog>
  </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick, type ComponentPublicInstance } from 'vue'
import { buildDownloadName as dlName } from '@/lib/download'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pdfApi } from '@/api/pdf'
import PdfIcon from './PdfIcon.vue'

const props = defineProps<{
  open: boolean
  docId: number
  totalPages: number
  currentPage?: number
  pdfDoc?: any
  /** Phase 13.30: 文档标题,用于规范下载文件名 */
  title?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'op-merge'): void
  (e: 'op-delete-pages', pages: number[]): void
  (e: 'op-rotate-pages', pages: number[], degrees: number): void
  (e: 'op-extract-pages', pages: number[]): void
  (e: 'op-insert-blank', afterPage: number): void
  (e: 'op-insert-file'): void
  (e: 'op-reorder', newOrder: number[]): void
  (e: 'op-crop', pages: number[]): void
  /** Phase 13.37: 替换页面完成后,通知父组件 reload */
  (e: 'op-replaced'): void
}>()

const busy = ref(false)
const lastPage = ref(props.totalPages)

// ===== 缩放(控制网格列宽) =====
const zoomLevels = [140, 180, 220, 260]
const zoomIdx = ref(1)

function zoomIn() { zoomIdx.value = Math.min(zoomIdx.value + 1, zoomLevels.length - 1) }
function zoomOut() { zoomIdx.value = Math.max(zoomIdx.value - 1, 0) }

// ===== 缩略图渲染 =====
const gridRef = ref<HTMLElement | null>(null)
const rendered = ref<Set<number>>(new Set())
const thumbRefs = new Map<number, HTMLCanvasElement>()
const renderingSet = new Set<number>() // 正在渲染的页,防止并发同 canvas 多次 render
let observer: IntersectionObserver | null = null
let pdfjsLib: any = null

function bindThumb(el: Element | ComponentPublicInstance | null, pageNum: number) {
  if (!el || !(el instanceof HTMLCanvasElement)) {
    thumbRefs.delete(pageNum)
    return
  }
  thumbRefs.set(pageNum, el)
  observer?.observe(el)
}

async function ensurePdfjs() {
  if (pdfjsLib) return pdfjsLib
  pdfjsLib = await import('pdfjs-dist')
  return pdfjsLib
}

async function renderThumb(pageNum: number) {
  const doc = props.pdfDoc
  if (!doc) return
  const canvas = thumbRefs.get(pageNum)
  if (!canvas || rendered.value.has(pageNum)) return
  // 防止并发:同一页正在渲染则跳过(zoom/reload 时多个 watcher 可能同时触发)
  if (renderingSet.has(pageNum)) return
  renderingSet.add(pageNum)
  try {
    await ensurePdfjs()
    const page = await doc.getPage(pageNum)
    // 按 zoom 级别算渲染 scale,保证清晰度
    const targetW = zoomLevels[zoomIdx.value]
    const vp1 = page.getViewport({ scale: 1 })
    const scale = Math.max(0.3, targetW / vp1.width)
    const viewport = page.getViewport({ scale })
    canvas.width = Math.ceil(viewport.width)
    canvas.height = Math.ceil(viewport.height)
    await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
    rendered.value.add(pageNum)
  } catch (e) {
    console.error('[PdfOrganizePages] renderThumb', pageNum, e)
  } finally {
    renderingSet.delete(pageNum)
  }
}

async function rerenderAll() {
  // zoom 变化时重新渲染(清理后重绘);串行避免并发 render 冲突
  rendered.value = new Set()
  await nextTick()
  for (const pn of thumbRefs.keys()) {
    await renderThumb(pn)
  }
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  // root 用 viewport(null)而非 gridRef:reload 时 totalPages 短暂变 0 会让网格 v-else 卸载,
  // gridRef 变成分离节点,以它为 root 的 observer 不再触发。用 viewport 避免此问题。
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const pageNum = Number((entry.target as HTMLElement).dataset.pageNum)
          if (pageNum) void renderThumb(pageNum)
        }
      })
    },
    { root: null, rootMargin: '200px' },
  )
}

/** 显式渲染当前视口内的缩略图(reload 后 observer 可能滞后,兜底) */
function renderVisibleThumbs() {
  thumbRefs.forEach((el, pn) => {
    if (rendered.value.has(pn)) return
    const rect = el.getBoundingClientRect()
    if (rect.top < window.innerHeight + 200 && rect.bottom > -200) {
      void renderThumb(pn)
    }
  })
}

// ===== 多选 =====
const selected = ref<Set<number>>(new Set())
let lastSelected: number | null = null

function onToggle(pageNum: number, evt: MouseEvent) {
  if (evt.shiftKey && lastSelected != null && lastSelected !== pageNum) {
    const from = Math.min(lastSelected, pageNum)
    const to = Math.max(lastSelected, pageNum)
    const next = new Set(selected.value)
    for (let i = from; i <= to; i++) next.add(i)
    selected.value = next
  } else {
    const next = new Set(selected.value)
    if (next.has(pageNum)) next.delete(pageNum)
    else next.add(pageNum)
    selected.value = next
    lastSelected = pageNum
  }
}

function onCardClick(evt: MouseEvent, pageNum: number) {
  if (evt.shiftKey && lastSelected != null) {
    evt.preventDefault()
    onToggle(pageNum, evt)
    return
  }
  // 单击卡片切换选中(非 checkbox 区域)
  const next = new Set(selected.value)
  if (next.has(pageNum)) next.delete(pageNum)
  else next.add(pageNum)
  selected.value = next
  lastSelected = pageNum
}

function selectAll() {
  const next = new Set<number>()
  for (let i = 1; i <= props.totalPages; i++) next.add(i)
  selected.value = next
}

function clearSelection() {
  selected.value = new Set()
  lastSelected = null
}

// ===== 拖拽调序 =====
const dragFrom = ref<number | null>(null)
const dropTarget = ref<number | null>(null)

function onDragStart(evt: DragEvent, pageNum: number) {
  if (!evt.dataTransfer) return
  dragFrom.value = pageNum
  evt.dataTransfer.effectAllowed = 'move'
  evt.dataTransfer.setData('text/plain', String(pageNum))
}
function onDragOver(pageNum: number) {
  if (dragFrom.value === null) return
  dropTarget.value = pageNum
}
function onDragLeave(pageNum: number) {
  if (dropTarget.value === pageNum) dropTarget.value = null
}
function onDrop(pageNum: number) {
  if (dragFrom.value !== null && dragFrom.value !== pageNum) {
    const from = dragFrom.value
    const order = Array.from({ length: props.totalPages }, (_, i) => i + 1)
    const [moved] = order.splice(from - 1, 1)
    order.splice(pageNum - 1, 0, moved)
    busy.value = true
    emit('op-reorder', order)
  }
  dragFrom.value = null
  dropTarget.value = null
}
function onDragEnd() {
  dragFrom.value = null
  dropTarget.value = null
}

// ===== 操作 =====
async function onDelete() {
  if (busy.value) return
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  // 立即置 busy,防止连点弹多个确认框
  busy.value = true
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${pages.length} 页?此操作不可撤销。`,
      '批量删除页面',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消,释放 busy
    busy.value = false
    return
  }
  emit('op-delete-pages', pages)
}

function onExtract() {
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  busy.value = true
  emit('op-extract-pages', pages)
}

function onRotate(degrees: number) {
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  busy.value = true
  emit('op-rotate-pages', pages, degrees)
}

function onCrop() {
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  emit('op-crop', pages)
}

async function rotateOne(pageNum: number, degrees: number) {
  busy.value = true
  emit('op-rotate-pages', [pageNum], degrees)
}

async function deleteOne(pageNum: number) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm(`确定删除第 ${pageNum} 页?`, '删除页面', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch {
    busy.value = false
    return
  }
  emit('op-delete-pages', [pageNum])
}

async function onBatchExport() {
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  busy.value = true
  try {
    const blob = await pdfApi.extractPagesBatch(props.docId, pages)
    downloadBlob(blob, dlName('提取导出', pages.length === 1 ? 'pdf' : 'zip', props.title))
    ElMessage.success(`已导出 ${pages.length} 页为 zip`)
  } catch (e: any) {
    console.error('[PdfOrganizePages] batchExport failed:', e)
    ElMessage.error(e?.message || '导出失败')
  } finally {
    busy.value = false
  }
}

// ===== 拆分 =====
const splitDialogOpen = ref(false)
const splitRanges = ref('')
const splitError = ref('')

// ===== Phase 13.37: 替换页面 =====
const replaceDialogOpen = ref(false)
const replaceTargetPages = ref<number[]>([])
const replaceFile = ref<File | null>(null)
const replaceFileName = ref('')
const replaceFilePages = ref(0)
const replaceSourceStart = ref(1)
const replaceError = ref('')

function onReplace() {
  const pages = Array.from(selected.value).sort((a, b) => a - b)
  if (pages.length === 0) return
  replaceTargetPages.value = pages
  replaceFile.value = null
  replaceFileName.value = ''
  replaceFilePages.value = 0
  replaceSourceStart.value = 1
  replaceError.value = ''
  replaceDialogOpen.value = true
}

async function onReplaceFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  replaceFile.value = f
  replaceFileName.value = f.name
  replaceError.value = ''
  // 读取源 PDF 页数(用 pdfjs)
  try {
    const lib = await import('pdfjs-dist')
    lib.GlobalWorkerOptions.workerSrc = `${window.location.origin}/pdf.worker.min.mjs`
    const buf = await f.arrayBuffer()
    const doc = await lib.getDocument({ data: buf }).promise
    replaceFilePages.value = doc.numPages
  } catch {
    replaceFilePages.value = 0
    replaceError.value = '无法读取源 PDF'
  }
}

async function onReplaceConfirm() {
  if (!replaceFile.value || replaceTargetPages.value.length === 0) return
  replaceError.value = ''
  busy.value = true
  try {
    await pdfApi.replacePages(props.docId, replaceTargetPages.value, replaceFile.value, replaceSourceStart.value)
    ElMessage.success(`已替换 ${replaceTargetPages.value.length} 页`)
    replaceDialogOpen.value = false
    emit('op-replaced')
  } catch (e: any) {
    console.error('[PdfOrganizePages] replace failed:', e)
    replaceError.value = e?.message || '替换失败'
  } finally {
    busy.value = false
  }
}

function onSplit() {
  splitError.value = ''
  splitDialogOpen.value = true
}

function validateRanges(input: string): number[][] | null {
  const raw = input.trim()
  if (!raw) return null
  const parts = raw.split(',').map(s => s.trim()).filter(Boolean)
  const result: number[][] = []
  for (const p of parts) {
    const m = p.match(/^(\d+)(?:-(\d+))?$/)
    if (!m) return null
    const a = Number(m[1])
    const b = m[2] ? Number(m[2]) : a
    if (a < 1 || b < 1 || a > props.totalPages || b > props.totalPages || a > b) return null
    result.push([a, b])
  }
  return result.length > 0 ? result : null
}

async function onSplitEveryPage() {
  if (props.totalPages === 0) return
  busy.value = true
  try {
    const ranges = Array.from({ length: props.totalPages }, (_, i) => i + 1).join(',')
    const blob = await pdfApi.splitByRanges(props.docId, ranges)
    downloadBlob(blob, dlName('拆分每页', props.totalPages === 1 ? 'pdf' : 'zip', props.title))
    ElMessage.success(`已拆分为 ${props.totalPages} 个 PDF`)
    splitDialogOpen.value = false
  } catch (e: any) {
    console.error('[PdfOrganizePages] splitEveryPage failed:', e)
    ElMessage.error(e?.message || '拆分失败')
  } finally {
    busy.value = false
  }
}

async function onSplitByRanges() {
  splitError.value = ''
  const ranges = validateRanges(splitRanges.value)
  if (!ranges) {
    splitError.value = `格式错误,应为 "1-3,5,7-9"(页码 1-${props.totalPages})`
    return
  }
  busy.value = true
  try {
    const blob = await pdfApi.splitByRanges(props.docId, splitRanges.value.trim())
    downloadBlob(blob, dlName('拆分区间', ranges.length === 1 ? 'pdf' : 'zip', props.title))
    ElMessage.success(`已按区间拆分为 ${ranges.length} 个 PDF`)
    splitDialogOpen.value = false
  } catch (e: any) {
    console.error('[PdfOrganizePages] splitByRanges failed:', e)
    ElMessage.error(e?.message || '拆分失败')
  } finally {
    busy.value = false
  }
}

// ===== 工具 =====

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// ===== 响应变化 =====
watch(
  () => props.totalPages,
  (n, old) => {
    if (n !== old) {
      lastPage.value = n
      selected.value = new Set()
      lastSelected = null
      rendered.value = new Set()
      busy.value = false
      // reload 后 observer 可能滞后,延迟显式渲染视口内缩略图
      nextTick(() => {
        thumbRefs.forEach((el) => observer?.observe(el))
        setTimeout(renderVisibleThumbs, 300)
      })
    }
  },
)

watch(
  () => props.pdfDoc,
  (doc) => {
    if (doc) {
      // pdfDoc 更新(reload 完成):清理重渲染
      rendered.value = new Set()
      nextTick(() => {
        thumbRefs.forEach((el) => observer?.observe(el))
        setTimeout(renderVisibleThumbs, 300)
      })
    }
  },
)

watch(
  () => props.docId,
  () => {
    selected.value = new Set()
    lastSelected = null
    rendered.value = new Set()
    splitRanges.value = ''
    splitError.value = ''
    busy.value = false
  },
)

watch(zoomIdx, () => { void rerenderAll() })

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      // 打开时重新渲染缩略图
      rendered.value = new Set()
      nextTick(() => {
        thumbRefs.forEach((el) => observer?.observe(el))
        setTimeout(renderVisibleThumbs, 300)
      })
    }
  },
)

defineExpose({
  markDone() { busy.value = false },
  clearSelection() { selected.value = new Set() },
})

onMounted(() => {
  setupObserver()
  nextTick(() => {
    thumbRefs.forEach((el) => observer?.observe(el))
    // 兜底:observer 可能不立即触发,显式渲染视口内缩略图
    setTimeout(renderVisibleThumbs, 200)
    setTimeout(renderVisibleThumbs, 800)
  })
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<style scoped>
.pdf-org-overlay {
  position: fixed;
  inset: 0;
  /* z-index 1000:高于 editor chrome(≤200),低于 el-dialog(≥2000),弹窗自然在上层 */
  z-index: 1000;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
}

/* 顶部 */
.pdf-org-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.pdf-org-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-foreground);
}
.pdf-org-title-meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-foreground-3);
  margin-left: 4px;
}
.pdf-org-zoom-bar {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0 14px;
}
.pdf-org-zoom-bar .pdf-org-icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: var(--color-surface, #fff);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
  color: var(--color-foreground-2, #475569);
  transition: background 120ms ease, color 120ms ease, transform 120ms ease;
}
.pdf-org-zoom-bar .pdf-org-icon-btn:hover:not(:disabled) {
  background: var(--color-primary-soft, #ebf1fe);
  color: var(--color-primary, #3b6fe8);
  transform: translateY(-1px);
}
.pdf-org-zoom-bar .pdf-org-icon-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.pdf-org-zoom-bar .pdf-org-zoom-val {
  min-width: 64px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--color-surface, #fff);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-foreground, #0f172a);
  text-align: center;
}
.pdf-org-zoom-val {
  font-size: 12px;
  color: var(--color-foreground-2);
  font-variant-numeric: tabular-nums;
  min-width: 44px;
  text-align: center;
}
.pdf-org-icon-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-foreground-2);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 120ms ease;
}
.pdf-org-icon-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.pdf-org-icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pdf-org-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-foreground-2);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.pdf-org-close:hover {
  background: var(--color-surface-2);
  color: var(--color-destructive, #f56c6c);
}

/* 工具栏 */
.pdf-org-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-surface-2);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.pdf-org-tool-group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.pdf-org-tool-sep {
  width: 1px;
  height: 22px;
  background: var(--color-divider);
}
.pdf-org-tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-foreground-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 120ms ease;
}
.pdf-org-tool:hover:not(:disabled) {
  background: var(--color-surface);
  color: var(--color-primary);
  border-color: var(--color-border);
}
.pdf-org-tool:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pdf-org-tool-danger {
  color: var(--color-destructive, #f56c6c);
}
.pdf-org-tool-danger:hover:not(:disabled) {
  background: rgba(245, 108, 108, 0.08);
  border-color: var(--color-destructive, #f56c6c);
  color: var(--color-destructive, #f56c6c);
}

/* 主区 */
.pdf-org-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--space-5);
  background: var(--color-surface-3, #f5f7fa);
}
.pdf-org-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--thumb-size, 180px), 1fr));
  gap: var(--space-4);
  max-width: 1600px;
  margin: 0 auto;
}
.pdf-org-thumb {
  position: relative;
  cursor: pointer;
  user-select: none;
  border-radius: var(--radius-md);
  padding: var(--space-2);
  border: 2px solid transparent;
  background: var(--color-surface);
  transition: border-color 120ms ease, background 120ms ease, transform 120ms ease;
}
.pdf-org-thumb:hover {
  background: var(--color-surface-2);
}
.pdf-org-thumb.is-selected {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
.pdf-org-thumb.is-dragging {
  opacity: 0.35;
  transform: scale(0.92);
  box-shadow: var(--shadow-2);
}
.pdf-org-thumb.is-drop-target {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}
/* 插入位置指示线:拖到目标卡左侧显示粗蓝竖线 + 脉冲,明确"将插入到此页之前" */
.pdf-org-thumb.is-drop-target::before {
  content: '';
  position: absolute;
  left: -3px;
  top: 4px;
  bottom: 4px;
  width: 4px;
  background: var(--color-primary);
  border-radius: 2px;
  box-shadow: 0 0 8px var(--color-primary), 0 0 0 2px var(--color-primary-soft);
  animation: pdf-org-drop-pulse 0.9s ease-in-out infinite;
  z-index: 3;
  pointer-events: none;
}
@keyframes pdf-org-drop-pulse {
  0%, 100% { transform: scaleY(1); opacity: 1; box-shadow: 0 0 8px var(--color-primary), 0 0 0 2px var(--color-primary-soft); }
  50% { transform: scaleY(1.04); opacity: 0.75; box-shadow: 0 0 14px var(--color-primary), 0 0 0 4px var(--color-primary-soft); }
}
/* TransitionGroup FLIP:调序时其他卡片平滑滑动 */
.pdf-org-grid-move {
  transition: transform 120ms ease;
}
.pdf-org-grid-enter-active,
.pdf-org-grid-leave-active {
  transition: none;
}
.pdf-org-grid-enter-from,
.pdf-org-grid-leave-to {
  opacity: 1;
}
.pdf-org-grid-leave-active {
  position: static;
}
.pdf-org-thumb-canvas-wrap {
  width: 100%;
  aspect-ratio: 0.707;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-1);
}
.pdf-org-thumb-canvas {
  max-width: 100%;
  max-height: 100%;
  display: block;
}
.pdf-org-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, var(--color-surface-2) 8%, var(--color-surface-3) 18%, var(--color-surface-2) 33%);
  background-size: 200% 100%;
  animation: pdf-org-shimmer 1.4s ease-in-out infinite;
}
@keyframes pdf-org-shimmer { to { background-position-x: -200%; } }

.pdf-org-thumb-actions {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 120ms ease;
}
.pdf-org-thumb:hover .pdf-org-thumb-actions,
.pdf-org-thumb.is-selected .pdf-org-thumb-actions,
.pdf-org-thumb:focus-within .pdf-org-thumb-actions {
  opacity: 1;
}
.pdf-org-thumb-action {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.9);
  color: var(--color-foreground-2);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-1);
  transition: all 120ms ease;
}
.pdf-org-thumb-action:hover:not(:disabled) {
  background: var(--color-primary);
  color: #fff;
}
.pdf-org-thumb-action.pdf-org-tool-danger-icon:hover:not(:disabled) {
  background: var(--color-destructive, #f56c6c);
  color: #fff;
}
.pdf-org-thumb-action:disabled { opacity: 0.4; cursor: not-allowed; }

.pdf-org-thumb-meta {
  display: flex;
  justify-content: center;
  margin-top: var(--space-2);
}
.pdf-org-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.pdf-org-checkbox input {
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: var(--color-primary);
}
.pdf-org-thumb-num {
  font-size: 12px;
  color: var(--color-foreground-2);
  font-variant-numeric: tabular-nums;
}
.pdf-org-thumb.is-selected .pdf-org-thumb-num {
  color: var(--color-primary);
  font-weight: 600;
}

/* 底部 */
.pdf-org-footer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-surface-2);
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-foreground-3);
  flex-shrink: 0;
}
.pdf-org-footer strong {
  color: var(--color-foreground);
  font-variant-numeric: tabular-nums;
}
.pdf-org-footer-sep { color: var(--color-foreground-4); }
.pdf-org-footer-hint {
  margin-left: auto;
  color: var(--color-foreground-4);
}

.pdf-org-empty {
  padding: var(--space-12);
  text-align: center;
  color: var(--color-foreground-3);
  font-size: 14px;
}

/* 拆分子弹窗(用 el-dialog,仅保留内部 body/card 样式) */
.pdf-org-sub-body {
  padding: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  overflow-y: auto;
}
.pdf-org-sub-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.pdf-org-sub-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-foreground);
}
.pdf-org-sub-card-desc {
  font-size: 12px;
  color: var(--color-foreground-3);
  line-height: 1.5;
}
.pdf-org-sub-card-desc code {
  background: var(--color-surface-2);
  padding: 1px 4px;
  border-radius: 3px;
  color: var(--color-primary);
}
.pdf-org-input {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--color-surface);
  color: var(--color-foreground);
  outline: none;
}
.pdf-org-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-soft);
}
.pdf-org-primary-btn {
  align-self: flex-start;
  padding: 7px 14px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 120ms ease;
}
.pdf-org-primary-btn:hover:not(:disabled) {
  background: var(--color-primary-hover, #66b1ff);
}
.pdf-org-primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.pdf-org-error {
  font-size: 12px;
  color: var(--color-destructive, #f56c6c);
}
</style>
