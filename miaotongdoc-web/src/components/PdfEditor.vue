<template>
  <div class="pdf-editor-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <!-- 左侧：翻页 -->
      <div class="toolbar-group">
        <el-button-group>
          <el-tooltip content="上一页" placement="bottom">
            <el-button size="small" :disabled="currentPage <= 1" @click="prevPage">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="下一页" placement="bottom">
            <el-button size="small" :disabled="currentPage >= totalPages" @click="nextPage">
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </el-tooltip>
        </el-button-group>
        <span class="page-indicator">{{ currentPage }} / {{ totalPages }}</span>
      </div>

      <el-divider direction="vertical" />

      <!-- 中间：缩放 -->
      <div class="toolbar-group">
        <el-tooltip content="缩小" placement="bottom">
          <el-button size="small" @click="zoomOut" :disabled="scale <= 0.3">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
        </el-tooltip>
        <span class="scale-indicator">{{ Math.round(scale * 100) }}%</span>
        <el-tooltip content="放大" placement="bottom">
          <el-button size="small" @click="zoomIn" :disabled="scale >= 4">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="适应宽度" placement="bottom">
          <el-button size="small" @click="fitWidth">
            <el-icon><FullScreen /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <el-divider direction="vertical" />

      <!-- 标注工具 -->
      <div class="toolbar-group">
        <el-tooltip content="选择（文本选择）" placement="bottom">
          <el-button size="small" :type="activeTool === 'select' ? 'primary' : 'default'" @click="activeTool = 'select'">
            <el-icon><Pointer /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="高亮" placement="bottom">
          <el-button size="small" :type="activeTool === 'highlight' ? 'primary' : 'default'" @click="activeTool = 'highlight'">
            <el-icon><Edit /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="批注" placement="bottom">
          <el-button size="small" :type="activeTool === 'comment' ? 'primary' : 'default'" @click="activeTool = 'comment'">
            <el-icon><ChatLineSquare /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="画笔" placement="bottom">
          <el-button size="small" :type="activeTool === 'draw' ? 'primary' : 'default'" @click="activeTool = 'draw'">
            <el-icon><EditPen /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="橡皮擦" placement="bottom">
          <el-button size="small" :type="activeTool === 'eraser' ? 'primary' : 'default'" @click="activeTool = 'eraser'">
            <el-icon><Brush /></el-icon>
          </el-button>
        </el-tooltip>
        <el-color-picker v-model="activeColor" size="small" :predefine="predefineColors" />
      </div>

      <el-divider direction="vertical" />

      <!-- 右侧：导出和侧边 -->
      <div class="toolbar-group toolbar-right">
        <el-tooltip content="导出" placement="bottom">
          <el-dropdown trigger="click" @command="handleConvert">
            <el-button size="small" :loading="converting">
              <el-icon><Download /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="md">Markdown</el-dropdown-item>
                <el-dropdown-item command="txt">纯文本</el-dropdown-item>
                <el-dropdown-item command="docx">Word</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>

        <el-tooltip :content="showThumbnails ? '隐藏缩略图' : '显示缩略图'" placement="bottom">
          <el-button size="small" @click="showThumbnails = !showThumbnails">
            <el-icon><Grid /></el-icon>
          </el-button>
        </el-tooltip>

        <el-tooltip content="AI 助手" placement="bottom">
          <el-button size="small" @click="openAiChat">
            <el-icon><MagicStick /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 主体区域 -->
    <div class="main-layout">
      <!-- 左侧缩略图 -->
      <transition name="slide-left">
        <div class="thumbnails-panel" v-if="showThumbnails">
          <div
            v-for="page in totalPages"
            :key="page"
            class="thumb-item"
            :class="{ active: currentPage === page }"
            @click="goToPage(page)"
          >
            <canvas :ref="el => setThumbRef(page, el)" class="thumb-canvas" />
            <span class="thumb-num">{{ page }}</span>
          </div>
        </div>
      </transition>

      <!-- PDF 预览区 -->
      <div class="pdf-viewer" ref="containerRef" @scroll="onScroll">
        <div
          v-for="page in totalPages"
          :key="page"
          class="page-wrapper"
          :ref="el => setPageRef(page, el)"
        >
          <div class="page-container" :style="{ width: pageWidth + 'px', height: pageHeight + 'px' }">
            <!-- PDF Canvas -->
            <canvas :ref="el => setCanvasRef(page, el)" class="page-canvas" />

            <!-- 文字层（用于选择复制） -->
            <div
              class="text-layer"
              :class="{ active: activeTool === 'select' }"
              :ref="el => setTextLayerRef(page, el)"
            />

            <!-- 标注层 -->
            <div
              class="annotation-layer"
              :class="{ editing: activeTool !== 'select' }"
              :ref="el => setAnnotationRef(page, el)"
              @mousedown="onMouseDown($event, page)"
              @mousemove="onMouseMove($event, page)"
              @mouseup="onMouseUp($event, page)"
              @mouseleave="onMouseLeave($event, page)"
            >
              <!-- 高亮 -->
              <div
                v-for="ann in getPageAnnotations(page, 'highlight')"
                :key="ann.id"
                class="ann-highlight"
                :style="getHighlightStyle(ann)"
              />

              <!-- 批注 -->
              <div
                v-for="ann in getPageAnnotations(page, 'comment')"
                :key="ann.id"
                class="ann-comment"
                :style="getAnnotationStyle(ann)"
                @click.stop="openComment(ann)"
              >
                💬
                <div class="ann-tooltip">{{ ann.content }}</div>
              </div>

              <!-- 画笔 SVG -->
              <svg class="draw-svg" :width="pageWidth" :height="pageHeight">
                <path
                  v-for="ann in getPageAnnotations(page, 'draw')"
                  :key="ann.id"
                  :d="getDrawPath(ann)"
                  :stroke="ann.color"
                  stroke-width="2"
                  fill="none"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  v-if="isDrawing && currentPageDraw === page && drawPoints.length > 0"
                  :d="getCurrentDrawPath()"
                  :stroke="activeColor"
                  stroke-width="2"
                  fill="none"
                  stroke-linecap="round"
                />
              </svg>

              <!-- 矩形预览 -->
              <div
                v-if="pendingRect && pendingRect.pageNumber === page"
                class="rect-preview"
                :style="getRectPreviewStyle()"
              />
            </div>
          </div>
        </div>

        <!-- 识别状态提示 -->
        <div v-if="recognizing" class="recognizing-overlay">
          <el-icon class="is-loading" :size="24" />
          <span>正在识别 PDF...</span>
        </div>
      </div>

      <!-- 右侧 Markdown 编辑器 -->
      <div class="markdown-panel">
        <div class="markdown-header">
          <span class="markdown-title">第 {{ currentPage }} 页内容</span>
          <span class="save-status" :class="{ saving: isSaving }">
            {{ isSaving ? '保存中...' : (hasUnsavedChanges ? '未保存' : '已保存') }}
          </span>
        </div>
        <div class="markdown-body">
          <textarea
            v-model="currentMarkdown"
            class="markdown-textarea"
            placeholder="正在加载识别内容..."
            @input="onMarkdownChange"
            spellcheck="false"
          />
        </div>
        <div class="markdown-footer">
          <span class="char-count">{{ currentMarkdown.length }} 字符</span>
          <el-button size="small" type="primary" @click="saveMarkdown" :loading="isSaving">
            保存
          </el-button>
        </div>
      </div>
    </div>

    <!-- 批注对话框 -->
    <el-dialog v-model="showCommentDialog" title="添加批注" width="400px">
      <el-input v-model="editingComment" type="textarea" :rows="3" placeholder="输入批注内容..." />
      <template #footer>
        <el-button @click="showCommentDialog = false">取消</el-button>
        <el-button type="primary" @click="saveComment">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 对话抽屉 -->
    <el-drawer v-model="showAiDrawer" title="AI 助手" size="400px" direction="rtl">
      <div class="ai-chat-container">
        <div class="chat-messages" ref="chatMessagesRef">
          <div v-if="chatMessages.length === 0 && !chatLoading" class="empty-state">
            <div class="empty-icon">🤖</div>
            <div class="empty-text">基于文档内容提问</div>
            <div class="quick-btns">
              <el-button size="small" @click="quickAsk('这篇文章的主要内容是什么？')">主要内容</el-button>
              <el-button size="small" @click="quickAsk('有哪些关键信息？')">关键信息</el-button>
            </div>
          </div>
          <div
            v-for="(msg, i) in chatMessages"
            :key="i"
            class="chat-msg"
            :class="msg.role"
          >
            <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
            <div class="msg-bubble">{{ msg.content }}</div>
          </div>
        </div>
        <div class="chat-input">
          <el-input
            v-model="chatInput"
            placeholder="输入问题..."
            @keyup.enter="sendChat"
            :disabled="chatLoading"
          />
          <el-button type="primary" @click="sendChat" :loading="chatLoading">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { pdfApi } from '@/api/pdf'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, ZoomIn, ZoomOut, Pointer, Edit, EditPen,
  ChatLineSquare, Delete, Download, FullScreen, Grid, Brush, MagicStick
} from '@element-plus/icons-vue'

interface PdfAnnotation {
  id: string
  type: 'highlight' | 'comment' | 'draw'
  pageNumber: number
  rect?: { x: number; y: number; width: number; height: number }
  color: string
  content?: string
  points?: number[]
  userId: number
  userName: string
  createdAt: string
}

interface PendingRect {
  pageNumber: number
  startX: number
  startY: number
  x: number
  y: number
  width: number
  height: number
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

// ========== 状态 ==========
const currentPage = ref(1)
const totalPages = ref(0)
const scale = ref(1.2)
const pageWidth = ref(0)
const pageHeight = ref(0)
const activeTool = ref<'select' | 'highlight' | 'comment' | 'draw' | 'eraser'>('select')
const activeColor = ref('#FFEB3B')
const predefineColors = ['#FFEB3B', '#FF9800', '#F44336', '#E91E63', '#9C27B0', '#3F51B5', '#2196F3', '#4CAF50']

const showThumbnails = ref(true)
const loading = ref(false)
const converting = ref(false)

// ========== Refs ==========
const containerRef = ref<HTMLDivElement | null>(null)
const chatMessagesRef = ref<HTMLDivElement | null>(null)
const canvasRefs = reactive<Map<number, HTMLCanvasElement>>(new Map())
const thumbRefs = reactive<Map<number, HTMLCanvasElement>>(new Map())
const textLayerRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const annotationRefs = reactive<Map<number, HTMLDivElement>>(new Map())
const pageRefs = reactive<Map<number, HTMLDivElement>>(new Map())

// ========== 标注 ==========
const annotations = ref<PdfAnnotation[]>([])
const showCommentDialog = ref(false)
const editingComment = ref('')
const pendingRect = ref<PendingRect | null>(null)
const isDrawing = ref(false)
const currentPageDraw = ref(0)
const drawPoints = ref<number[]>([])
const pendingCommentRect = ref<PendingRect | null>(null)

// ========== Markdown ==========
const pdfMarkdown = ref<Record<string, string>>({})
const currentMarkdown = ref('')
const isSaving = ref(false)
const hasUnsavedChanges = ref(false)
const recognizing = ref(false)

// ========== AI ==========
const showAiDrawer = ref(false)
const chatInput = ref('')
const chatMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const chatLoading = ref(false)

// ========== PDF ==========
let pdfjsLib: any = null
let pdfDoc: any = null

// Yjs
const ydoc = new Y.Doc()
let provider: WebsocketProvider | null = null
const yAnnotations = ydoc.getArray<PdfAnnotation>('annotations')

// ========== 生命周期 ==========
onMounted(async () => {
  pdfjsLib = await import('pdfjs-dist')
  pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`
  await loadPdf()
  await loadMarkdown()
  connectYjs()
  emit('ready')
})

onBeforeUnmount(() => {
  provider?.destroy()
  ydoc.destroy()
  pdfDoc?.destroy()
})

// ========== Ref 设置 ==========
function setCanvasRef(page: number, el: any) { if (el) canvasRefs.set(page, el) }
function setThumbRef(page: number, el: any) { if (el) thumbRefs.set(page, el) }
function setTextLayerRef(page: number, el: any) { if (el) textLayerRefs.set(page, el) }
function setAnnotationRef(page: number, el: any) { if (el) annotationRefs.set(page, el) }
function setPageRef(page: number, el: any) { if (el) pageRefs.set(page, el) }

// ========== PDF 加载 ==========
async function loadPdf() {
  const token = sessionStorage.getItem('token')
  pdfDoc = await pdfjsLib.getDocument({
    url: props.fileUrl,
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  }).promise
  totalPages.value = pdfDoc.numPages
  await nextTick()
  await renderAllThumbs()
  await nextTick()
  await fitWidth()
  await renderPage(1)
  cleanupHiddenCanvases()
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
  pageWidth.value = viewport.width
  pageHeight.value = viewport.height
  await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise

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
  cleanupHiddenCanvases()
}

async function renderAllThumbs() {
  for (let i = 1; i <= totalPages.value; i++) {
    const page = await pdfDoc.getPage(i)
    const viewport = page.getViewport({ scale: 0.15 })
    const canvas = thumbRefs.get(i)
    if (!canvas) continue
    canvas.width = viewport.width
    canvas.height = viewport.height
    await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
  }
}

async function reRenderAll() {
  for (let i = 1; i <= totalPages.value; i++) await renderPage(i)
}

// ========== Markdown 加载 ==========
async function loadMarkdown() {
  try {
    const resp = await pdfApi.getMarkdown(props.docId)
    if (resp.recognized && resp.markdown) {
      pdfMarkdown.value = resp.markdown
    } else if (Object.keys(resp.markdown || {}).length > 0) {
      pdfMarkdown.value = resp.markdown
    } else {
      // 未识别，触发识别
      recognizing.value = true
      try {
        await pdfApi.recognize(props.docId)
        const retryResp = await pdfApi.getMarkdown(props.docId)
        if (retryResp.markdown) {
          pdfMarkdown.value = retryResp.markdown
        }
      } catch (e) {
        console.error('识别失败:', e)
      } finally {
        recognizing.value = false
      }
    }
    updateCurrentMarkdown()
  } catch (e) {
    console.error('加载 Markdown 失败:', e)
    recognizing.value = false
  }
}

function updateCurrentMarkdown() {
  currentMarkdown.value = pdfMarkdown.value[currentPage.value] || ''
  hasUnsavedChanges.value = false
}

watch(currentPage, () => {
  updateCurrentMarkdown()
})

function onMarkdownChange() {
  hasUnsavedChanges.value = true
}

async function saveMarkdown() {
  isSaving.value = true
  try {
    pdfMarkdown.value[currentPage.value] = currentMarkdown.value
    await pdfApi.saveMarkdown(props.docId, pdfMarkdown.value)
    hasUnsavedChanges.value = false
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    isSaving.value = false
  }
}

// ========== 导航 ==========
function prevPage() { if (currentPage.value > 1) goToPage(currentPage.value - 1) }
function nextPage() { if (currentPage.value < totalPages.value) goToPage(currentPage.value + 1) }
function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  renderPage(page)
  const el = pageRefs.get(page)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function onScroll() {}

// ========== 缩放 ==========
function zoomIn() { scale.value = Math.min(scale.value + 0.2, 4); reRenderAll() }
function zoomOut() { scale.value = Math.max(scale.value - 0.2, 0.3); reRenderAll() }
async function fitWidth() {
  if (!containerRef.value || !pdfDoc) return
  const page = await pdfDoc.getPage(1)
  const vp = page.getViewport({ scale: 1 })
  const availableWidth = containerRef.value.clientWidth - 16
  scale.value = Math.max(0.3, availableWidth / vp.width)
  await reRenderAll()
}

// ========== 标注 ==========
function getPageAnnotations(page: number, type?: string) {
  let list = annotations.value.filter(a => a.pageNumber === page)
  if (type) list = list.filter(a => a.type === type)
  return list
}

function getHighlightStyle(ann: PdfAnnotation) {
  if (!ann.rect) return {}
  return {
    left: ann.rect.x + 'px',
    top: ann.rect.y + 'px',
    width: ann.rect.width + 'px',
    height: ann.rect.height + 'px',
    backgroundColor: ann.color,
    opacity: '0.4',
  }
}

function getAnnotationStyle(ann: PdfAnnotation) {
  if (!ann.rect) return {}
  return {
    left: ann.rect.x + 'px',
    top: ann.rect.y + 'px',
    width: ann.rect.width + 'px',
    height: ann.rect.height + 'px',
    border: `2px solid ${ann.color}`,
  }
}

function getDrawPath(ann: PdfAnnotation) {
  if (!ann.points || ann.points.length < 4) return ''
  let d = `M ${ann.points[0]} ${ann.points[1]}`
  for (let i = 2; i < ann.points.length; i += 2) {
    d += ` L ${ann.points[i]} ${ann.points[i + 1]}`
  }
  return d
}

function getCurrentDrawPath() {
  if (drawPoints.value.length < 4) return ''
  let d = `M ${drawPoints.value[0]} ${drawPoints.value[1]}`
  for (let i = 2; i < drawPoints.value.length; i += 2) {
    d += ` L ${drawPoints.value[i]} ${drawPoints.value[i + 1]}`
  }
  return d
}

function getRectPreviewStyle() {
  if (!pendingRect.value) return {}
  const r = pendingRect.value
  return {
    left: Math.min(r.startX, r.x) + 'px',
    top: Math.min(r.startY, r.y) + 'px',
    width: Math.abs(r.width) + 'px',
    height: Math.abs(r.height) + 'px',
    backgroundColor: activeColor.value,
    opacity: '0.4',
    border: `2px solid ${activeColor.value}`,
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
  if (activeTool.value === 'eraser') {
    eraseAt(pos.x, pos.y, page)
    return
  }
  if (activeTool.value === 'highlight' || activeTool.value === 'comment') {
    pendingRect.value = { pageNumber: page, startX: pos.x, startY: pos.y, x: pos.x, y: pos.y, width: 0, height: 0 }
  } else if (activeTool.value === 'draw') {
    isDrawing.value = true
    currentPageDraw.value = page
    drawPoints.value = [pos.x, pos.y]
  }
}

function onMouseMove(e: MouseEvent, page: number) {
  if (!props.canEdit) return
  const pos = getRelPos(e, page)
  if (activeTool.value === 'eraser' && e.buttons === 1) {
    eraseAt(pos.x, pos.y, page)
    return
  }
  if ((activeTool.value === 'highlight' || activeTool.value === 'comment') && pendingRect.value) {
    pendingRect.value.x = pos.x
    pendingRect.value.y = pos.y
    pendingRect.value.width = pos.x - pendingRect.value.startX
    pendingRect.value.height = pos.y - pendingRect.value.startY
  } else if (activeTool.value === 'draw' && isDrawing.value) {
    drawPoints.value.push(pos.x, pos.y)
  }
}

function onMouseUp(e: MouseEvent, page: number) {
  if (!props.canEdit) return
  if ((activeTool.value === 'highlight' || activeTool.value === 'comment') && pendingRect.value) {
    const r = pendingRect.value
    const width = Math.abs(r.width)
    const height = Math.abs(r.height)
    if (width > 10 && height > 10) {
      const rect = { x: Math.min(r.startX, r.x), y: Math.min(r.startY, r.y), width, height }
      if (activeTool.value === 'comment') {
        pendingCommentRect.value = { ...r, x: rect.x, y: rect.y, width: rect.width, height: rect.height }
        editingComment.value = ''
        showCommentDialog.value = true
      } else {
        addAnnotation({ type: 'highlight', pageNumber: page, rect, color: activeColor.value })
      }
    }
    pendingRect.value = null
  } else if (activeTool.value === 'draw' && isDrawing.value) {
    isDrawing.value = false
    if (drawPoints.value.length >= 4) {
      addAnnotation({ type: 'draw', pageNumber: page, color: activeColor.value, points: [...drawPoints.value] })
    }
    drawPoints.value = []
  }
}

function onMouseLeave(_e: MouseEvent, _page: number) {
  if (isDrawing.value && drawPoints.value.length >= 4) {
    addAnnotation({ type: 'draw', pageNumber: currentPageDraw.value, color: activeColor.value, points: [...drawPoints.value] })
  }
  isDrawing.value = false
  drawPoints.value = []
}

function eraseAt(x: number, y: number, page: number) {
  const toDelete: string[] = []
  for (const ann of annotations.value) {
    if (ann.pageNumber !== page || !ann.rect) continue
    const r = ann.rect
    if (x >= r.x - 5 && x <= r.x + r.width + 5 && y >= r.y - 5 && y <= r.y + r.height + 5) {
      toDelete.push(ann.id)
    }
  }
  for (const ann of annotations.value) {
    if (ann.pageNumber !== page || !ann.points) continue
    for (let i = 0; i < ann.points.length; i += 2) {
      if (Math.abs(ann.points[i] - x) < 15 && Math.abs(ann.points[i + 1] - y) < 15) {
        toDelete.push(ann.id)
        break
      }
    }
  }
  for (const id of toDelete) deleteAnnotation(id)
}

function addAnnotation(data: Partial<PdfAnnotation>) {
  yAnnotations.push([{
    id: `ann-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type: data.type || 'highlight',
    pageNumber: data.pageNumber || currentPage.value,
    rect: data.rect,
    color: data.color || activeColor.value,
    content: data.content,
    points: data.points,
    userId: props.userId,
    userName: props.userName,
    createdAt: new Date().toISOString(),
  }])
  emit('stateChange', 'editing')
}

function saveComment() {
  if (editingComment.value.trim() && pendingCommentRect.value) {
    const r = pendingCommentRect.value
    addAnnotation({
      type: 'comment',
      pageNumber: r.pageNumber,
      rect: { x: r.x, y: r.y, width: r.width, height: r.height },
      color: activeColor.value,
      content: editingComment.value.trim(),
    })
  }
  showCommentDialog.value = false
  pendingCommentRect.value = null
  editingComment.value = ''
}

function openComment(ann: PdfAnnotation) {
  editingComment.value = ann.content || ''
  pendingCommentRect.value = { ...ann.rect!, pageNumber: ann.pageNumber, startX: ann.rect!.x, startY: ann.rect!.y, x: ann.rect!.x, y: ann.rect!.y, width: ann.rect!.width, height: ann.rect!.height }
  showCommentDialog.value = true
}

function deleteAnnotation(id: string) {
  const idx = yAnnotations.toArray().findIndex(a => a.id === id)
  if (idx >= 0) yAnnotations.delete(idx, 1)
}

function connectYjs() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  provider = new WebsocketProvider(`${protocol}//${window.location.host}/ws/yjs/`, `pdf-${props.docKey}`, ydoc)
  yAnnotations.observe(() => {
    annotations.value = yAnnotations.toArray().map(a => ({ ...a }))
  })
}

// ========== 导出 ==========
async function handleConvert(format: string) {
  converting.value = true
  try {
    // 合并所有页面的 Markdown
    let content = ''
    for (let i = 1; i <= totalPages.value; i++) {
      const pageContent = pdfMarkdown.value[i] || ''
      if (pageContent) {
        content += `## 第 ${i} 页\n\n${pageContent}\n\n---\n\n`
      }
    }
    if (!content) {
      ElMessage.warning('没有可导出的内容')
      return
    }
    const blob = await pdfApi.exportEdited(props.docId, { content, format: format as any })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `document.${format}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    converting.value = false
  }
}

// ========== AI 对话 ==========
function openAiChat() {
  showAiDrawer.value = true
}

function quickAsk(q: string) {
  chatInput.value = q
  sendChat()
}

async function sendChat() {
  const q = chatInput.value.trim()
  if (!q || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: q })
  chatMessages.value.push({ role: 'assistant', content: '' })
  chatInput.value = ''
  chatLoading.value = true
  await nextTick()
  scrollChat()

  try {
    const token = sessionStorage.getItem('token')
    const resp = await fetch(`/api/documents/${props.docId}/ai/chat-stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({ question: q, history: chatMessages.value.filter(m => m.role !== 'assistant').map(m => ({ role: m.role, content: m.content })) }),
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    if (!resp.body) throw new Error('No response body')

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      const last = chatMessages.value[chatMessages.value.length - 1]
      if (last?.role !== 'assistant') continue
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const data = trimmed.slice(5).trim()
          if (data && data !== '[DONE]') {
            try {
              const parsed = JSON.parse(data)
              last.content += parsed.content || ''
            } catch {
              // 兼容纯文本
              last.content += data
            }
          }
        }
      }
    }
    await nextTick()
    scrollChat()
  } catch (e) {
    const last = chatMessages.value[chatMessages.value.length - 1]
    if (last?.role === 'assistant') last.content = '请求失败，请重试'
  } finally {
    chatLoading.value = false
  }
}

function scrollChat() {
  if (chatMessagesRef.value) chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
}
</script>

<style scoped>
/* ========== 容器 ========== */
.pdf-editor-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: #f5f5f5;
}

/* ========== 工具栏 ========== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-right {
  margin-left: auto;
}

.page-indicator, .scale-indicator {
  font-size: 12px;
  color: #606266;
  min-width: 50px;
  text-align: center;
}

/* ========== 主体布局 ========== */
.main-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

/* ========== 缩略图 ========== */
.thumbnails-panel {
  width: 80px;
  flex-shrink: 0;
  background: #fafafa;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.thumb-item {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 4px;
  padding: 2px;
  background: #fff;
  transition: all 0.2s;
}

.thumb-item:hover {
  border-color: #c0c4cc;
}

.thumb-item.active {
  border-color: #409eff;
}

.thumb-canvas {
  width: 100%;
  display: block;
}

.thumb-num {
  display: block;
  text-align: center;
  font-size: 10px;
  color: #909399;
  margin-top: 2px;
}

/* ========== PDF 预览 ========== */
.pdf-viewer {
  flex: 1;
  overflow: auto;
  background: #e8e8e8;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  min-width: 0;
}

.page-wrapper {
  margin-bottom: 8px;
}

.page-container {
  position: relative;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  background: #fff;
}

.page-canvas {
  display: block;
}

/* 文字层 */
.text-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.text-layer.active {
  pointer-events: auto;
  z-index: 10;
}

.text-layer :deep(span) {
  color: transparent;
  position: absolute;
  white-space: pre;
  cursor: text;
  pointer-events: all;
}

/* 标注层 */
.annotation-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.annotation-layer.editing {
  pointer-events: auto;
  cursor: crosshair;
}

.ann-highlight {
  position: absolute;
  pointer-events: none;
}

.ann-comment {
  position: absolute;
  font-size: 18px;
  cursor: pointer;
}

.ann-tooltip {
  display: none;
  position: absolute;
  top: 20px;
  left: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  max-width: 200px;
  white-space: pre-wrap;
  z-index: 100;
}

.ann-comment:hover .ann-tooltip {
  display: block;
}

.draw-svg {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.rect-preview {
  position: absolute;
  pointer-events: none;
}

/* 识别中覆盖层 */
.recognizing-overlay {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(255,255,255,0.95);
  padding: 20px 40px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 1000;
}

/* ========== Markdown 面板 ========== */
.markdown-panel {
  width: 40%;
  min-width: 280px;
  max-width: 600px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.markdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.markdown-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.save-status {
  font-size: 11px;
  color: #67c23a;
}

.save-status.saving {
  color: #e6a23c;
}

.markdown-body {
  flex: 1;
  overflow: hidden;
}

.markdown-textarea {
  width: 100%;
  height: 100%;
  padding: 12px;
  border: none;
  outline: none;
  resize: none;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  background: #fff;
}

.markdown-textarea::placeholder {
  color: #c0c4cc;
}

.markdown-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.char-count {
  font-size: 11px;
  color: #909399;
}

/* ========== AI 聊天 ========== */
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: #909399;
}

.empty-icon {
  font-size: 36px;
}

.empty-text {
  font-size: 14px;
}

.quick-btns {
  display: flex;
  gap: 8px;
}

.chat-msg {
  display: flex;
  gap: 8px;
  max-width: 85%;
}

.chat-msg.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.msg-bubble {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.chat-msg.user .msg-bubble {
  background: #409eff;
  color: #fff;
}

.chat-msg.assistant .msg-bubble {
  background: #f5f7fa;
  color: #303133;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #e4e7ed;
}

/* ========== 动画 ========== */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.3s ease;
}

.slide-left-enter-from,
.slide-left-leave-to {
  width: 0;
  opacity: 0;
}
</style>
