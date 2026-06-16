<template>
  <div class="pdf-editor-wrapper">
    <!-- 工具栏 -->
    <div class="pdf-toolbar">
      <div class="toolbar-left">
        <button class="tb-btn" @click="prevPage" :disabled="currentPage <= 1">◀</button>
        <span class="page-info">
          <input v-model.number="pageInput" @keyup.enter="goToPage(pageInput)"
            class="page-input" type="number" :min="1" :max="totalPages" />
          / {{ totalPages }}
        </span>
        <button class="tb-btn" @click="nextPage" :disabled="currentPage >= totalPages">▶</button>
      </div>
      <div class="toolbar-center">
        <button class="tb-btn" @click="zoomOut">➖</button>
        <span class="zoom-info">{{ Math.round(scale * 100) }}%</span>
        <button class="tb-btn" @click="zoomIn">➕</button>
        <button class="tb-btn" @click="fitWidth" title="适合宽度">↔</button>
      </div>
      <div class="toolbar-right">
        <el-popover trigger="click" :width="180">
          <template #reference>
            <button class="tb-btn color-btn" :class="{ active: activeTool === 'highlight' }" title="高亮">
              <span class="color-dot" :style="{ background: highlightColor }" /> 高亮
            </button>
          </template>
          <div class="color-picker">
            <button v-for="c in highlightColors" :key="c.value" class="color-option"
              :style="{ background: c.value }" @click="highlightColor = c.value; activeTool = 'highlight'" />
          </div>
        </el-popover>
        <button class="tb-btn" :class="{ active: activeTool === 'comment' }"
          @click="activeTool = 'comment'" title="批注">💬 批注</button>
        <button class="tb-btn" :class="{ active: activeTool === 'draw' }"
          @click="activeTool = 'draw'" title="画笔">✏️ 画笔</button>
        <el-divider direction="vertical" />
        <el-button size="small" @click="extractText" :loading="extracting">
          📄 识别文字
        </el-button>
      </div>
    </div>

    <!-- 主体：左右分栏 -->
    <div class="pdf-body">
      <!-- 左栏：PDF 渲染 -->
      <div class="pdf-left" ref="mainContainer" @scroll="onScroll">
        <div v-for="page in totalPages" :key="page" class="pdf-page-container"
          :ref="(el: any) => setPageContainerRef(page, el)">
          <canvas :ref="(el: any) => setCanvasRef(page, el)" class="pdf-canvas" />
          <div class="annotation-layer"
            :ref="(el: any) => setAnnotationLayerRef(page, el)"
            @mousedown="onAnnotationMouseDown($event, page)"
            @mousemove="onAnnotationMouseMove($event, page)"
            @mouseup="onAnnotationMouseUp($event, page)">
            <div v-for="ann in getPageAnnotations(page)" :key="ann.id"
              class="annotation-item"
              :style="getAnnotationStyle(ann)"
              @dblclick="editAnnotation(ann)">
              <div v-if="ann.type === 'comment'" class="comment-icon">💬</div>
            </div>
            <svg v-if="getDrawingPaths(page).length > 0" class="drawing-svg"
              :width="pageWidth" :height="pageHeight">
              <path v-for="path in getDrawingPaths(page)" :key="path.id"
                :d="path.svgPath" fill="none" :stroke="path.color"
                stroke-width="2" stroke-linecap="round" />
            </svg>
          </div>
        </div>
      </div>

      <!-- 右栏：识别 & AI -->
      <div class="pdf-right">
        <el-tabs v-model="rightTab" class="right-tabs">
          <!-- 文字识别 -->
          <el-tab-pane label="文字识别" name="ocr">
            <div class="ocr-panel">
              <div class="ocr-actions">
                <el-button type="primary" size="small" @click="extractText" :loading="extracting" style="width:100%">
                  识别全文文字
                </el-button>
              </div>
              <div v-if="extractedText" class="ocr-result">
                <div class="ocr-text">{{ extractedText }}</div>
                <el-button size="small" @click="copyText(extractedText)" style="margin-top:8px">复制文字</el-button>
              </div>
              <el-empty v-else-if="!extracting" description="点击上方按钮识别文档文字" :image-size="48" />
            </div>
          </el-tab-pane>

          <!-- AI 问答 -->
          <el-tab-pane label="AI 问答" name="chat">
            <div class="chat-panel">
              <div class="chat-messages" ref="chatMessagesRef">
                <div v-for="(msg, i) in chatMessages" :key="i"
                  class="chat-msg" :class="[msg.role]">
                  <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
                  <div class="msg-content">{{ msg.content }}</div>
                </div>
                <div v-if="chatLoading" class="chat-msg assistant">
                  <div class="msg-avatar">🤖</div>
                  <div class="msg-content" style="color:#909399">思考中...</div>
                </div>
              </div>
              <div class="chat-input">
                <el-input v-model="chatQuestion" placeholder="输入关于文档的问题..."
                  @keyup.enter="sendChat" :disabled="chatLoading" size="small">
                  <template #append>
                    <el-button @click="sendChat" :loading="chatLoading">发送</el-button>
                  </template>
                </el-input>
              </div>
            </div>
          </el-tab-pane>

          <!-- AI 摘要 -->
          <el-tab-pane label="AI 摘要" name="summary">
            <div class="summary-panel">
              <el-button type="primary" size="small" @click="generateSummary" :loading="summaryLoading" style="width:100%">
                生成文档摘要
              </el-button>
              <div v-if="summaryText" class="summary-result">{{ summaryText }}</div>
            </div>
          </el-tab-pane>

          <!-- 注释列表 -->
          <el-tab-pane label="注释列表" name="annotations">
            <div class="ann-list-panel">
              <div v-for="ann in annotations" :key="ann.id" class="ann-item"
                @click="goToPage(ann.pageNumber)">
                <span class="ann-badge" :style="{ background: ann.color }">
                  {{ ann.type === 'highlight' ? '高亮' : ann.type === 'comment' ? '批注' : '画笔' }}
                </span>
                <span class="ann-page">P{{ ann.pageNumber }}</span>
                <span v-if="ann.content" class="ann-text">{{ ann.content }}</span>
              </div>
              <el-empty v-if="annotations.length === 0" description="暂无注释" :image-size="48" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 批注输入 -->
    <el-dialog v-model="showCommentDialog" title="添加批注" width="400px">
      <el-input v-model="commentText" type="textarea" :rows="4" placeholder="请输入批注内容..." />
      <template #footer>
        <el-button @click="showCommentDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmComment">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { documentAiApi } from '@/api/documentAi'
import { ElMessage } from 'element-plus'

interface PdfAnnotation {
  id: string
  type: 'highlight' | 'comment' | 'draw'
  pageNumber: number
  rect?: { x: number; y: number; width: number; height: number }
  color: string
  content?: string
  points?: number[][]
  userId: number
  userName: string
  createdAt: string
}

const props = defineProps<{
  docId: number
  docKey: string
  fileUrl: string
  canEdit: boolean
  userName: string
  userId: number
}>()

const emit = defineEmits(['ready', 'stateChange'])

let pdfjsLib: any = null
let pdfDoc: any = null

const currentPage = ref(1)
const totalPages = ref(0)
const scale = ref(1.2)
const pageInput = ref(1)
const pageWidth = ref(0)
const pageHeight = ref(0)
const activeTool = ref<'select' | 'highlight' | 'comment' | 'draw'>('select')
const highlightColor = ref('#FEF08A')
const rightTab = ref('ocr')

const highlightColors = [
  { value: '#FEF08A' }, { value: '#BBF7D0' }, { value: '#BFDBFE' },
  { value: '#FBCFE8' }, { value: '#FED7AA' },
]

const canvasRefs = reactive<Map<number, HTMLCanvasElement>>(new Map())
const annotationLayerRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const pageContainerRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const mainContainer = ref<HTMLDivElement | null>(null)

const annotations = ref<PdfAnnotation[]>([])
const showCommentDialog = ref(false)
const commentText = ref('')
const pendingAnnotation = reactive({ pageNumber: 0, rect: { x: 0, y: 0, width: 0, height: 0 }, color: '' })
const isDrawing = ref(false)
const currentPath = ref<number[]>([])

// OCR
const extracting = ref(false)
const extractedText = ref('')

// AI Chat
const chatQuestion = ref('')
const chatMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const chatLoading = ref(false)
const chatMessagesRef = ref<HTMLElement | null>(null)

// AI Summary
const summaryText = ref('')
const summaryLoading = ref(false)

// Yjs
const ydoc = new Y.Doc()
let provider: WebsocketProvider | null = null
const yAnnotations = ydoc.getArray<PdfAnnotation>('annotations')

onMounted(async () => {
  pdfjsLib = await import('pdfjs-dist')
  pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`
  await loadPdf()
  connectYjs()
})

onBeforeUnmount(() => {
  provider?.destroy()
  ydoc.destroy()
  pdfDoc?.destroy()
})

async function loadPdf() {
  const token = sessionStorage.getItem('token')
  const loadingTask = pdfjsLib.getDocument({
    url: props.fileUrl,
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  })
  pdfDoc = await loadingTask.promise
  totalPages.value = pdfDoc.numPages
  await nextTick()
  for (let i = 1; i <= totalPages.value; i++) await renderPage(i)
  emit('ready')
}

async function renderPage(pageNum: number) {
  if (!pdfDoc) return
  const page = await pdfDoc.getPage(pageNum)
  const viewport = page.getViewport({ scale: scale.value })
  const canvas = canvasRefs.get(pageNum)
  if (!canvas) return
  canvas.width = viewport.width
  canvas.height = viewport.height
  const ctx = canvas.getContext('2d')!
  await page.render({ canvasContext: ctx, viewport }).promise
  if (pageNum === 1) { pageWidth.value = viewport.width; pageHeight.value = viewport.height }
}

async function reRenderAll() {
  for (let i = 1; i <= totalPages.value; i++) await renderPage(i)
}

function prevPage() { if (currentPage.value > 1) goToPage(currentPage.value - 1) }
function nextPage() { if (currentPage.value < totalPages.value) goToPage(currentPage.value + 1) }

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  pageInput.value = page
  pageContainerRefs.get(page)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onScroll() {
  if (!mainContainer.value) return
  for (let i = 1; i <= totalPages.value; i++) {
    const el = pageContainerRefs.get(i)
    if (el) {
      const rect = el.getBoundingClientRect()
      const parentRect = mainContainer.value.getBoundingClientRect()
      if (rect.top <= parentRect.top + mainContainer.value.clientHeight / 2 && rect.bottom >= parentRect.top + mainContainer.value.clientHeight / 2) {
        currentPage.value = i; pageInput.value = i; break
      }
    }
  }
}

function zoomIn() { scale.value = Math.min(scale.value + 0.2, 3); reRenderAll() }
function zoomOut() { scale.value = Math.max(scale.value - 0.2, 0.5); reRenderAll() }
function fitWidth() {
  if (!mainContainer.value || !pdfDoc) return
  pdfDoc.getPage(1).then((page: any) => {
    scale.value = (mainContainer.value!.clientWidth - 40) / page.getViewport({ scale: 1 }).width
    reRenderAll()
  })
}

function setCanvasRef(page: number, el: any) { if (el) canvasRefs.set(page, el) }
function setAnnotationLayerRef(page: number, el: any) { if (el) annotationLayerRefs.set(page, el) }
function setPageContainerRef(page: number, el: any) { if (el) pageContainerRefs.set(page, el) }

function getPageAnnotations(page: number) { return annotations.value.filter(a => a.pageNumber === page && a.type !== 'draw') }
function getDrawingPaths(page: number) {
  return annotations.value.filter(a => a.pageNumber === page && a.type === 'draw' && a.points)
    .map(a => ({ id: a.id, color: a.color, svgPath: pointsToSvgPath(a.points!) }))
}
function pointsToSvgPath(points: number[][]): string {
  if (!points.length) return ''
  let d = `M ${points[0][0]} ${points[0][1]}`
  for (let i = 1; i < points.length; i++) d += ` L ${points[i][0]} ${points[i][1]}`
  return d
}
function getAnnotationStyle(ann: PdfAnnotation) {
  if (!ann.rect) return {}
  return {
    left: `${ann.rect.x}px`, top: `${ann.rect.y}px`,
    width: `${ann.rect.width}px`, height: `${ann.rect.height}px`,
    background: ann.type === 'highlight' ? ann.color + '60' : 'transparent',
    border: ann.type === 'comment' ? `2px solid ${ann.color}` : 'none',
  }
}
function getRelativePos(e: MouseEvent, page: number) {
  const layer = annotationLayerRefs.get(page)
  if (!layer) return { x: 0, y: 0 }
  const rect = layer.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

function onAnnotationMouseDown(e: MouseEvent, page: number) {
  if (!props.canEdit) return
  const pos = getRelativePos(e, page)
  if (activeTool.value === 'highlight' || activeTool.value === 'comment') {
    Object.assign(pendingAnnotation, { pageNumber: page, rect: { x: pos.x, y: pos.y, width: 0, height: 0 }, color: highlightColor.value })
  } else if (activeTool.value === 'draw') {
    isDrawing.value = true; currentPath.value = [pos.x, pos.y]
  }
}
function onAnnotationMouseMove(e: MouseEvent, page: number) {
  if (!props.canEdit) return
  const pos = getRelativePos(e, page)
  if ((activeTool.value === 'highlight' || activeTool.value === 'comment') && pendingAnnotation.pageNumber === page) {
    pendingAnnotation.rect.width = pos.x - pendingAnnotation.rect.x
    pendingAnnotation.rect.height = pos.y - pendingAnnotation.rect.y
  } else if (activeTool.value === 'draw' && isDrawing.value) {
    currentPath.value.push(pos.x, pos.y)
  }
}
function onAnnotationMouseUp(_e: MouseEvent, page: number) {
  if (!props.canEdit) return
  if ((activeTool.value === 'highlight' || activeTool.value === 'comment') && pendingAnnotation.pageNumber === page) {
    const r = pendingAnnotation.rect
    if (Math.abs(r.width) > 5 && Math.abs(r.height) > 5) {
      const norm = { x: r.width > 0 ? r.x : r.x + r.width, y: r.height > 0 ? r.y : r.y + r.height, width: Math.abs(r.width), height: Math.abs(r.height) }
      if (activeTool.value === 'comment') { pendingAnnotation.rect = norm; showCommentDialog.value = true }
      else addAnnotation({ type: 'highlight', pageNumber: page, rect: norm, color: highlightColor.value })
    }
    pendingAnnotation.pageNumber = 0
  } else if (activeTool.value === 'draw' && isDrawing.value) {
    isDrawing.value = false
    if (currentPath.value.length >= 4) addAnnotation({ type: 'draw', pageNumber: page, color: highlightColor.value, points: [currentPath.value] })
    currentPath.value = []
  }
}

function addAnnotation(data: Partial<PdfAnnotation>) {
  yAnnotations.push([{
    id: `ann-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type: data.type || 'highlight', pageNumber: data.pageNumber || 1,
    rect: data.rect, color: data.color || highlightColor.value,
    content: data.content, points: data.points,
    userId: props.userId, userName: props.userName, createdAt: new Date().toISOString(),
  }])
  emit('stateChange', 'editing')
}
function confirmComment() {
  if (commentText.value.trim()) addAnnotation({ type: 'comment', pageNumber: pendingAnnotation.pageNumber, rect: { ...pendingAnnotation.rect }, color: pendingAnnotation.color, content: commentText.value.trim() })
  commentText.value = ''; showCommentDialog.value = false
}
function editAnnotation(ann: PdfAnnotation) {
  if (ann.type === 'comment') {
    const newContent = prompt('编辑批注:', ann.content || '')
    if (newContent !== null) {
      const idx = yAnnotations.toArray().findIndex(a => a.id === ann.id)
      if (idx >= 0) { yAnnotations.delete(idx, 1); yAnnotations.insert(idx, [{ ...ann, content: newContent }]) }
    }
  }
}

function connectYjs() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  provider = new WebsocketProvider(`${protocol}//${window.location.host}/ws/yjs/`, `pdf-${props.docKey}`, ydoc)
  provider.on('sync', () => console.log('[PdfViewer] Yjs synced'))
  yAnnotations.observe(() => { annotations.value = yAnnotations.toArray().map(a => ({ ...a })) })
}

// OCR
async function extractText() {
  extracting.value = true
  try {
    const token = sessionStorage.getItem('token')
    const resp = await fetch(`/api/pdf/${props.docId}/text`, { headers: { Authorization: `Bearer ${token}` } })
    if (resp.ok) { const data = await resp.json(); extractedText.value = data.fullText || '' }
  } catch (e) { console.error('识别失败', e) }
  finally { extracting.value = false }
}

// AI Chat
async function sendChat() {
  const q = chatQuestion.value.trim()
  if (!q || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: q }); chatQuestion.value = ''; chatLoading.value = true
  await nextTick(); if (chatMessagesRef.value) chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  try {
    const res = await documentAiApi.chat(props.docId, { question: q })
    chatMessages.value.push({ role: 'assistant', content: res.content })
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: '失败：' + (e.message || '未知错误') })
  } finally { chatLoading.value = false; await nextTick(); if (chatMessagesRef.value) chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight }
}

// AI Summary
async function generateSummary() {
  summaryLoading.value = true
  try { const res = await documentAiApi.summarize(props.docId); summaryText.value = res.content }
  catch (e: any) { ElMessage.error('生成摘要失败') }
  finally { summaryLoading.value = false }
}

function copyText(text: string) { navigator.clipboard.writeText(text); ElMessage.success('已复制') }
</script>

<style scoped>
.pdf-editor-wrapper { display: flex; flex-direction: column; height: 100%; background: #f5f5f5; }

.pdf-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px; background: #fff; border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0; gap: 8px; flex-wrap: wrap;
}
.toolbar-left, .toolbar-center, .toolbar-right { display: flex; align-items: center; gap: 6px; }
.toolbar-right { flex-wrap: wrap; }

.tb-btn {
  display: inline-flex; align-items: center; gap: 3px; padding: 4px 8px;
  border: 1px solid #dcdfe6; background: #fff; border-radius: 4px;
  cursor: pointer; font-size: 12px; color: #606266; transition: all 0.15s; white-space: nowrap;
}
.tb-btn:hover { border-color: #409eff; color: #409eff; }
.tb-btn.active { background: #ecf5ff; border-color: #409eff; color: #409eff; }
.tb-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.page-input { width: 40px; text-align: center; border: 1px solid #dcdfe6; border-radius: 3px; padding: 2px; font-size: 12px; }
.page-info, .zoom-info { font-size: 12px; color: #606266; white-space: nowrap; }

.color-btn { display: flex; align-items: center; gap: 4px; }
.color-dot { width: 12px; height: 12px; border-radius: 50%; border: 1px solid rgba(0,0,0,0.2); }
.color-picker { display: flex; gap: 6px; padding: 4px; }
.color-option { width: 24px; height: 24px; border-radius: 50%; border: 2px solid transparent; cursor: pointer; }
.color-option:hover { border-color: #409eff; transform: scale(1.15); }

/* 主体左右分栏 */
.pdf-body { flex: 1; display: flex; overflow: hidden; }

.pdf-left {
  flex: 1; overflow-y: auto; padding: 16px;
  display: flex; flex-direction: column; align-items: center; gap: 16px;
}

.pdf-page-container { position: relative; box-shadow: 0 2px 8px rgba(0,0,0,0.15); background: #fff; }
.pdf-canvas { display: block; }

.annotation-layer { position: absolute; top: 0; left: 0; width: 100%; height: 100%; cursor: crosshair; }
.annotation-item { position: absolute; cursor: pointer; }
.comment-icon { position: absolute; top: -16px; left: 0; font-size: 16px; }
.drawing-svg { position: absolute; top: 0; left: 0; pointer-events: none; }

/* 右栏 */
.pdf-right {
  width: 340px; background: #fff; border-left: 1px solid #e4e7ed;
  display: flex; flex-direction: column; flex-shrink: 0; overflow: hidden;
}

.right-tabs { display: flex; flex-direction: column; height: 100%; }
.right-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 8px; flex-shrink: 0; }
.right-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.right-tabs :deep(.el-tab-pane) { height: 100%; display: flex; flex-direction: column; }

/* OCR */
.ocr-panel { padding: 12px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; flex: 1; }
.ocr-result { background: #f5f7fa; border-radius: 8px; padding: 12px; flex: 1; overflow-y: auto; }
.ocr-text { font-size: 13px; line-height: 1.6; color: #303133; white-space: pre-wrap; word-break: break-all; }

/* Chat */
.chat-panel { display: flex; flex-direction: column; height: 100%; }
.chat-messages { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.chat-msg { display: flex; gap: 8px; max-width: 90%; }
.chat-msg.user { align-self: flex-end; flex-direction: row-reverse; }
.msg-avatar { font-size: 18px; flex-shrink: 0; }
.msg-content { padding: 8px 12px; border-radius: 10px; font-size: 13px; line-height: 1.5; word-break: break-word; }
.chat-msg.user .msg-content { background: #409eff; color: #fff; border-bottom-right-radius: 2px; }
.chat-msg.assistant .msg-content { background: #f5f7fa; color: #303133; border-bottom-left-radius: 2px; }
.chat-input { padding: 8px 12px; border-top: 1px solid #e4e7ed; flex-shrink: 0; }

/* Summary */
.summary-panel { padding: 12px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; flex: 1; }
.summary-result { background: #f5f7fa; border-radius: 8px; padding: 12px; font-size: 13px; line-height: 1.6; color: #303133; flex: 1; overflow-y: auto; }

/* Annotation list */
.ann-list-panel { padding: 8px; overflow-y: auto; flex: 1; }
.ann-item {
  display: flex; align-items: center; gap: 8px; padding: 8px; margin-bottom: 6px;
  border: 1px solid #e4e7ed; border-radius: 6px; cursor: pointer; transition: all 0.15s;
}
.ann-item:hover { background: #f5f7fa; border-color: #c0c4cc; }
.ann-badge { padding: 1px 6px; border-radius: 3px; font-size: 11px; color: #fff; font-weight: 500; }
.ann-page { font-size: 11px; color: #909399; }
.ann-text { font-size: 12px; color: #606266; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
