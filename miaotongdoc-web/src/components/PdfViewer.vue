<template>
  <div class="pdf-editor-wrapper">
    <!-- 工具栏 -->
    <div class="pdf-toolbar">
      <div class="toolbar-group">
        <el-button size="small" :icon="ArrowLeft" @click="prevPage" :disabled="currentPage <= 1" />
        <el-input v-model.number="pageInput" size="small" style="width:50px" @keyup.enter="goToPage(pageInput)" />
        <span class="toolbar-text">/ {{ totalPages }}</span>
        <el-button size="small" :icon="ArrowRight" @click="nextPage" :disabled="currentPage >= totalPages" />
      </div>
      <div class="toolbar-group">
        <el-button size="small" :icon="ZoomOut" @click="zoomOut" />
        <span class="toolbar-text">{{ Math.round(scale * 100) }}%</span>
        <el-button size="small" :icon="ZoomIn" @click="zoomIn" />
        <el-button size="small" @click="fitWidth">适应宽度</el-button>
      </div>
      <div class="toolbar-group">
        <el-button size="small" :type="activeTool === 'select' ? 'primary' : 'default'" @click="activeTool = 'select'">
          🔤 选择
        </el-button>
        <el-popover trigger="click" :width="200">
          <template #reference>
            <el-button size="small" :type="activeTool === 'highlight' ? 'primary' : 'default'">
              🖍 高亮
            </el-button>
          </template>
          <div class="color-picker">
            <div v-for="c in highlightColors" :key="c.value" class="color-option"
              :class="{ active: highlightColor === c.value }"
              :style="{ background: c.value }"
              @click="highlightColor = c.value; activeTool = 'highlight'" />
          </div>
        </el-popover>
        <el-button size="small" :type="activeTool === 'comment' ? 'primary' : 'default'" @click="activeTool = 'comment'">
          💬 批注
        </el-button>
        <el-button size="small" :type="activeTool === 'draw' ? 'primary' : 'default'" @click="activeTool = 'draw'">
          ✏️ 画笔
        </el-button>
      </div>
      <div class="toolbar-group">
        <el-dropdown @command="doConvert" :disabled="convertLoading">
          <el-button size="small" :loading="convertLoading">📥 转换</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="docx">转为 Word</el-dropdown-item>
              <el-dropdown-item command="md">转为 Markdown</el-dropdown-item>
              <el-dropdown-item command="txt">转为纯文本</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 主体 -->
    <div class="pdf-body">
      <!-- 左栏：缩略图 -->
      <div class="pdf-thumbnails">
        <div v-for="page in totalPages" :key="page"
          class="thumb-item" :class="{ active: currentPage === page }"
          @click="goToPage(page)">
          <canvas :ref="(el: any) => setThumbRef(page, el)" class="thumb-canvas" />
          <span class="thumb-label">{{ page }}</span>
        </div>
      </div>

      <!-- 中栏：PDF 渲染 -->
      <div class="pdf-main" ref="mainContainer" @scroll="onScroll">
        <div v-for="page in totalPages" :key="page" class="pdf-page-wrapper"
          :ref="(el: any) => setPageRef(page, el)">
          <div class="pdf-page-container" :style="{ width: pageWidth + 'px', height: pageHeight + 'px' }">
            <canvas :ref="(el: any) => setCanvasRef(page, el)" class="pdf-canvas" />
            <div class="text-layer" :class="{ selectable: activeTool === 'select' }"
              :ref="el => setTextLayerRef(page, el)" />
            <div class="annotation-layer"
              :class="{ active: activeTool !== 'select' }"
              :ref="(el: any) => setAnnotationRef(page, el)"
              @mousedown="onMouseDown($event, page)"
              @mousemove="onMouseMove($event, page)"
              @mouseup="onMouseUp($event, page)">
              <div v-for="ann in getPageAnnotations(page)" :key="ann.id"
                class="annotation-item" :style="getAnnotationStyle(ann)">
                <div v-if="ann.type === 'comment'" class="comment-icon" @dblclick="editAnnotation(ann)">💬</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏 -->
      <div class="pdf-right">
        <div class="right-tabs">
          <button v-for="tab in tabs" :key="tab.key" class="tab-btn" :class="{ active: currentTab === tab.key }"
            @click="currentTab = tab.key">{{ tab.label }}</button>
        </div>
        <div class="right-body">
          <!-- 文字识别 -->
          <div v-show="currentTab === 'text'" class="tab-panel">
            <el-button type="primary" size="small" @click="extractText" :loading="extracting" style="width:100%">
              识别全文文字
            </el-button>
            <div v-if="extractedText" class="result-box">
              <div class="result-content">{{ extractedText }}</div>
              <el-button size="small" @click="copyText(extractedText)" style="margin-top:8px">复制</el-button>
            </div>
          </div>
          <!-- AI 问答 -->
          <div v-show="currentTab === 'chat'" class="tab-panel chat-panel">
            <div class="chat-messages" ref="chatRef">
              <div v-for="(msg, i) in chatMessages" :key="i" class="chat-msg" :class="msg.role">
                <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
                <div class="msg-bubble">{{ msg.content }}</div>
              </div>
              <div v-if="chatLoading" class="chat-msg assistant">
                <div class="msg-avatar">🤖</div>
                <div class="msg-bubble loading">思考中...</div>
              </div>
            </div>
            <div class="chat-input">
              <el-input v-model="chatQuestion" placeholder="输入问题..." @keyup.enter="sendChat" :disabled="chatLoading" size="small">
                <template #append>
                  <el-button @click="sendChat" :loading="chatLoading">发送</el-button>
                </template>
              </el-input>
            </div>
          </div>
          <!-- AI 摘要 -->
          <div v-show="currentTab === 'summary'" class="tab-panel">
            <el-button type="primary" size="small" @click="generateSummary" :loading="summaryLoading" style="width:100%">
              生成摘要
            </el-button>
            <div v-if="summaryText" class="result-box">
              <div class="result-content">{{ summaryText }}</div>
            </div>
          </div>
          <!-- 批注列表 -->
          <div v-show="currentTab === 'annotations'" class="tab-panel">
            <div v-if="annotations.length === 0" class="empty-tip">暂无批注</div>
            <div v-for="ann in annotations" :key="ann.id" class="ann-item" @click="goToPage(ann.pageNumber)">
              <span class="ann-badge" :style="{ background: ann.color }">
                {{ ann.type === 'highlight' ? '高亮' : ann.type === 'comment' ? '批注' : '画笔' }}
              </span>
              <span>P{{ ann.pageNumber }}</span>
              <span v-if="ann.content" class="ann-text">{{ ann.content }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 批注对话框 -->
    <el-dialog v-model="showCommentDialog" title="添加批注" width="400px">
      <el-input v-model="commentText" type="textarea" :rows="4" placeholder="输入批注内容..." />
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
import { pdfApi } from '@/api/pdf'
import { documentAiApi } from '@/api/documentAi'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue'

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

// 状态
const currentPage = ref(1)
const totalPages = ref(0)
const scale = ref(1.2)
const pageInput = ref(1)
const pageWidth = ref(0)
const pageHeight = ref(0)
const activeTool = ref<'select' | 'highlight' | 'comment' | 'draw'>('select')
const highlightColor = ref('#FEF08A')
const currentTab = ref('text')

const highlightColors = [
  { value: '#FEF08A' }, { value: '#BBF7D0' }, { value: '#BFDBFE' },
  { value: '#FBCFE8' }, { value: '#FED7AA' },
]

const tabs = [
  { key: 'text', label: '📝 识别' },
  { key: 'chat', label: '🤖 AI问答' },
  { key: 'summary', label: '📋 摘要' },
  { key: 'annotations', label: '✏️ 批注' },
]

// Refs
const mainContainer = ref<HTMLDivElement | null>(null)
const chatRef = ref<HTMLElement | null>(null)
const canvasRefs = reactive<Map<number, HTMLCanvasElement>>(new Map())
const thumbRefs = reactive<Map<number, HTMLCanvasElement>>(new Map())
const textLayerRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const annotationRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const pageRefs = reactive<Map<number, HTMLDivElement>>(new Map())

// 批注
const annotations = ref<PdfAnnotation[]>([])
const showCommentDialog = ref(false)
const commentText = ref('')
const pendingAnnotation = reactive({ pageNumber: 0, rect: { x: 0, y: 0, width: 0, height: 0 }, color: '' })
const isDrawing = ref(false)
const currentPath = ref<number[]>([])

// 识别
const extracting = ref(false)
const extractedText = ref('')

// AI
const chatQuestion = ref('')
const chatMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const chatLoading = ref(false)
const summaryText = ref('')
const summaryLoading = ref(false)
const convertLoading = ref(false)

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

// Ref 设置
function setCanvasRef(page: number, el: any) { if (el) canvasRefs.set(page, el) }
function setThumbRef(page: number, el: any) { if (el) thumbRefs.set(page, el) }
function setTextLayerRef(page: number, el: any) { if (el) textLayerRefs.set(page, el) }
function setAnnotationRef(page: number, el: any) { if (el) annotationRefs.set(page, el) }
function setPageRef(page: number, el: any) { if (el) pageRefs.set(page, el) }

// PDF 加载
async function loadPdf() {
  const token = sessionStorage.getItem('token')
  pdfDoc = await pdfjsLib.getDocument({
    url: props.fileUrl,
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  }).promise
  totalPages.value = pdfDoc.numPages
  await nextTick()
  // 渲染缩略图
  for (let i = 1; i <= totalPages.value; i++) await renderThumb(i)
  // 渲染页面
  await fitWidth()
  // 清理 pdfjs-dist 内部创建的隐藏 canvas
  cleanupHiddenCanvases()
  emit('ready')
}

function cleanupHiddenCanvases() {
  document.querySelectorAll('.hiddenCanvasElement').forEach(el => el.remove())
}

async function renderPage(pageNum: number) {
  if (!pdfDoc) return
  const page = await pdfDoc.getPage(pageNum)
  const viewport = page.getViewport({ scale: scale.value })
  const canvas = canvasRefs.get(pageNum)
  if (!canvas) return

  canvas.width = viewport.width
  canvas.height = viewport.height
  await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise

  pageWidth.value = viewport.width
  pageHeight.value = viewport.height

  // 渲染文字层
  const textDiv = textLayerRefs.get(pageNum)
  if (textDiv) {
    textDiv.innerHTML = ''
    textDiv.style.width = viewport.width + 'px'
    textDiv.style.height = viewport.height + 'px'
    const textContent = await page.getTextContent()
    if (textContent.items.length > 0) {
      const textLayer = new pdfjsLib.TextLayer({
        textContentSource: textContent,
        container: textDiv,
        viewport,
      })
      await textLayer.render()
    }
  }

  // 每页渲染后清理隐藏 canvas
  cleanupHiddenCanvases()
}

async function renderThumb(pageNum: number) {
  if (!pdfDoc) return
  const page = await pdfDoc.getPage(pageNum)
  const viewport = page.getViewport({ scale: 0.2 })
  const canvas = thumbRefs.get(pageNum)
  if (!canvas) return
  canvas.width = viewport.width
  canvas.height = viewport.height
  await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
}

async function reRenderAll() {
  for (let i = 1; i <= totalPages.value; i++) await renderPage(i)
}

// 导航
function prevPage() { if (currentPage.value > 1) goToPage(currentPage.value - 1) }
function nextPage() { if (currentPage.value < totalPages.value) goToPage(currentPage.value + 1) }
function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  pageInput.value = page
  pageRefs.get(page)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function onScroll() {
  if (!mainContainer.value) return
  for (let i = 1; i <= totalPages.value; i++) {
    const el = pageRefs.get(i)
    if (el) {
      const rect = el.getBoundingClientRect()
      const parentRect = mainContainer.value.getBoundingClientRect()
      if (rect.top <= parentRect.top + mainContainer.value.clientHeight / 2 && rect.bottom >= parentRect.top + mainContainer.value.clientHeight / 2) {
        currentPage.value = i; pageInput.value = i; break
      }
    }
  }
}

// 缩放
function zoomIn() { scale.value = Math.min(scale.value + 0.2, 3); reRenderAll() }
function zoomOut() { scale.value = Math.max(scale.value - 0.2, 0.5); reRenderAll() }
function fitWidth() {
  if (!mainContainer.value || !pdfDoc) return
  pdfDoc.getPage(1).then((page: any) => {
    scale.value = (mainContainer.value!.clientWidth - 60) / page.getViewport({ scale: 1 }).width
    reRenderAll()
  })
}

// 批注
function getPageAnnotations(page: number) {
  return annotations.value.filter(a => a.pageNumber === page)
}
function getAnnotationStyle(ann: PdfAnnotation) {
  if (!ann.rect) return {}
  return {
    left: `${ann.rect.x}px`, top: `${ann.rect.y}px`,
    width: `${ann.rect.width}px`, height: `${ann.rect.height}px`,
    background: ann.type === 'highlight' ? ann.color + '50' : 'transparent',
    border: ann.type === 'comment' ? `2px solid ${ann.color}` : 'none',
  }
}
function getRelPos(e: MouseEvent, page: number) {
  const layer = annotationRefs.get(page)
  if (!layer) return { x: 0, y: 0 }
  const rect = layer.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}
function onMouseDown(e: MouseEvent, page: number) {
  if (!props.canEdit || activeTool.value === 'select') return
  const pos = getRelPos(e, page)
  if (activeTool.value === 'highlight' || activeTool.value === 'comment') {
    Object.assign(pendingAnnotation, { pageNumber: page, rect: { x: pos.x, y: pos.y, width: 0, height: 0 }, color: highlightColor.value })
  } else if (activeTool.value === 'draw') {
    isDrawing.value = true; currentPath.value = [pos.x, pos.y]
  }
}
function onMouseMove(e: MouseEvent, page: number) {
  if (!props.canEdit) return
  const pos = getRelPos(e, page)
  if ((activeTool.value === 'highlight' || activeTool.value === 'comment') && pendingAnnotation.pageNumber === page) {
    pendingAnnotation.rect.width = pos.x - pendingAnnotation.rect.x
    pendingAnnotation.rect.height = pos.y - pendingAnnotation.rect.y
  } else if (activeTool.value === 'draw' && isDrawing.value) {
    currentPath.value.push(pos.x, pos.y)
  }
}
function onMouseUp(_e: MouseEvent, page: number) {
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
  if (commentText.value.trim()) {
    addAnnotation({ type: 'comment', pageNumber: pendingAnnotation.pageNumber, rect: { ...pendingAnnotation.rect }, color: pendingAnnotation.color, content: commentText.value.trim() })
  }
  commentText.value = ''; showCommentDialog.value = false
}
function editAnnotation(ann: PdfAnnotation) {
  const newContent = prompt('编辑批注:', ann.content || '')
  if (newContent !== null) {
    const idx = yAnnotations.toArray().findIndex(a => a.id === ann.id)
    if (idx >= 0) { yAnnotations.delete(idx, 1); yAnnotations.insert(idx, [{ ...ann, content: newContent }]) }
  }
}
function connectYjs() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  provider = new WebsocketProvider(`${protocol}//${window.location.host}/ws/yjs/`, `pdf-${props.docKey}`, ydoc)
  yAnnotations.observe(() => { annotations.value = yAnnotations.toArray().map(a => ({ ...a })) })
}

// 识别
async function extractText() {
  extracting.value = true
  try {
    const res = await pdfApi.getText(props.docId)
    extractedText.value = res.fullText || ''
    currentTab.value = 'text'
    ElMessage.success('识别完成')
  } catch (e: any) {
    ElMessage.error('识别失败: ' + (e.message || '未知错误'))
  } finally { extracting.value = false }
}

// AI 问答
async function sendChat() {
  const q = chatQuestion.value.trim()
  if (!q || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: q })
  chatQuestion.value = ''
  chatLoading.value = true
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  try {
    const res = await documentAiApi.chat(props.docId, { question: q, enhanced: true })
    chatMessages.value.push({ role: 'assistant', content: res.content })
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: '错误: ' + (e.message || '未知') })
  } finally {
    chatLoading.value = false
    await nextTick()
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  }
}

// AI 摘要
async function generateSummary() {
  summaryLoading.value = true
  try {
    const res = await documentAiApi.summarize(props.docId)
    summaryText.value = res.content
  } catch (e: any) { ElMessage.error('生成摘要失败') }
  finally { summaryLoading.value = false }
}

// 转换
async function doConvert(format: string) {
  convertLoading.value = true
  try {
    const blob = await pdfApi.convert(props.docId, { targetFormat: format as any })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = `document.${format}`; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('转换完成')
  } catch (e: any) { ElMessage.error('转换失败') }
  finally { convertLoading.value = false }
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}
</script>

<style scoped>
.pdf-editor-wrapper { display: flex; flex-direction: column; height: 100%; background: #f0f2f5; }
.pdf-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 12px; background: #fff; border-bottom: 1px solid #e4e7ed; flex-shrink: 0; flex-wrap: wrap; }
.toolbar-group { display: flex; align-items: center; gap: 4px; }
.toolbar-text { font-size: 12px; color: #606266; min-width: 30px; text-align: center; }
.color-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; border: 1px solid rgba(0,0,0,0.2); margin-right: 4px; }
.color-picker { display: flex; gap: 6px; padding: 4px; }
.color-option { width: 22px; height: 22px; border-radius: 50%; border: 2px solid transparent; cursor: pointer; }
.color-option:hover { transform: scale(1.15); }
.color-option.active { border-color: #409eff; }

.pdf-body { flex: 1; display: flex; overflow: hidden; }
.pdf-thumbnails { width: 100px; background: #fafafa; border-right: 1px solid #e4e7ed; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; align-items: center; gap: 8px; flex-shrink: 0; }
.thumb-item { cursor: pointer; border: 2px solid transparent; border-radius: 4px; padding: 4px; text-align: center; width: 100%; }
.thumb-item:hover { border-color: #c0c4cc; background: #fff; }
.thumb-item.active { border-color: #409eff; background: #ecf5ff; }
.thumb-canvas { width: 100%; display: block; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }
.thumb-label { font-size: 11px; color: #909399; }

.pdf-main { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 16px; }
.pdf-page-wrapper { position: relative; }
.pdf-page-container { position: relative; box-shadow: 0 2px 8px rgba(0,0,0,0.12); background: #fff; }
.pdf-canvas { display: block; }

/* 文字层 */
.text-layer {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  overflow: hidden; line-height: 1.0; pointer-events: none;
}
.text-layer.selectable { pointer-events: auto; }
.text-layer :deep(span) {
  color: transparent; position: absolute; white-space: pre;
  cursor: text; transform-origin: 0% 0%; pointer-events: all;
}
.text-layer :deep(span::selection) {
  background: rgba(64, 158, 255, 0.5);
  color: transparent;
}
.text-layer :deep(span::-moz-selection) {
  background: rgba(64, 158, 255, 0.5);
  color: transparent;
}

/* 批注层 */
.annotation-layer { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; }
.annotation-layer.active { pointer-events: auto; cursor: crosshair; }
.annotation-item { position: absolute; cursor: pointer; }
.comment-icon { position: absolute; top: -16px; left: 0; font-size: 16px; cursor: pointer; }

/* 右栏 */
.pdf-right { width: 360px; background: #fff; border-left: 1px solid #e4e7ed; display: flex; flex-direction: column; flex-shrink: 0; }
.right-tabs { display: flex; border-bottom: 1px solid #e4e7ed; flex-shrink: 0; }
.tab-btn { flex: 1; padding: 10px 4px; font-size: 12px; border: none; background: none; cursor: pointer; color: #606266; border-bottom: 2px solid transparent; }
.tab-btn:hover { color: #409eff; }
.tab-btn.active { color: #409eff; border-bottom-color: #409eff; font-weight: 600; }
.right-body { flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.tab-panel { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 12px; }
.result-box { background: #f5f7fa; border-radius: 8px; padding: 12px; flex: 1; overflow-y: auto; }
.result-content { font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-all; }
.empty-tip { text-align: center; color: #909399; padding: 40px 0; font-size: 13px; }

/* 批注列表 */
.ann-item { display: flex; align-items: center; gap: 8px; padding: 8px; border: 1px solid #e4e7ed; border-radius: 6px; cursor: pointer; font-size: 12px; }
.ann-item:hover { background: #f5f7fa; }
.ann-badge { padding: 1px 6px; border-radius: 3px; font-size: 11px; color: #fff; }
.ann-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; }

/* AI 对话 */
.chat-panel { padding: 0 !important; }
.chat-messages { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.chat-msg { display: flex; gap: 8px; max-width: 90%; }
.chat-msg.user { align-self: flex-end; flex-direction: row-reverse; }
.msg-avatar { font-size: 16px; }
.msg-bubble { padding: 8px 12px; border-radius: 10px; font-size: 13px; line-height: 1.5; word-break: break-word; }
.msg-bubble.loading { color: #909399; }
.chat-msg.user .msg-bubble { background: #409eff; color: #fff; }
.chat-msg.assistant .msg-bubble { background: #f5f7fa; color: #303133; }
.chat-input { padding: 8px 12px; border-top: 1px solid #e4e7ed; flex-shrink: 0; }
</style>
