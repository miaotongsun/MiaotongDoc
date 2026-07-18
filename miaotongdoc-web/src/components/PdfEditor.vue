<!--
  PdfEditor.vue V3 —— Adobe 风格主壳

  5 段式布局:
    [Ribbon 多标签顶栏]
    [缩略图侧栏] [中央画布 + 间隙] [右侧任务面板(可选)]
    [状态条]

  V2 → V3 关键修复:
    - 缩略图栏 z-index + flex-shrink:0,不再遮挡工具栏
    - 真正 5 段布局(Ribbon / Thumb / Canvas / RightPanel / Status)
    - 状态条简洁(页码 + 缩放 + 工具 + 标注数 + 保存状态)
    - 保留 V2 的所有功能(文本编辑 / 页面操作 / AI 浮窗 / 协同)
-->
<template>
  <div class="pdf-editor-v3" :data-active-tool="activeTool" :data-view-mode="viewMode">
    <!-- 1. Ribbon 多标签顶栏 -->
    <PdfRibbon
      :active-tab="activeRibbonTab"
      :active-tool="activeTool"
      :active-color="activeColor"
      :view-mode="viewMode"
      :right-panel="rightPanelOpen"
      :stamp-text="stampText"
      :stamp-presets="stampPresets"
      :show-ocr-overlay="showOcrOverlay"
      @change-tab="onChangeRibbonTab"
      @select-tool="selectTool"
      @select-color="selectColor"
      @update:stamp-text="onStampTextChange"
      @set-view-mode="setViewMode"
      @cycle-view-mode="cycleViewMode"
      @toggle-panel="toggleRightPanel"
      @toggle-ocr-overlay="onToggleOcrOverlay"
      @zoom-in="onZoomIn"
      @zoom-out="onZoomOut"
      @fit-width="onFitWidth"
      @fit-page="onFitPage"
      @actual-size="onActualSize"
      @zoom-menu="onZoomMenu"
      @save="onSave"
      @print="onPrint"
      @share="onShare"
      @send-sign="onSendSign"
      @open-ai="onOpenAi"
      @place-signature="onOpenSignatureDialog"
      @protect="onOpenSecurityDialog"
      @ocr-recognize="onOcrRecognize"
      @page-merge="onOpenMerge"
      @page-extract="onExtractCurrent"
      @page-rotate-all="onRotateAll"
      @page-insert="onInsertBlank"
      @page-insert-from-file="onInsertFromFile"
      @watermark="onWatermark"
      @header-footer="onHeaderFooter"
      @export-menu="onOpenExport" />
    />

    <!-- 2. 主体三栏 -->
    <div class="pdf-editor-body">
      <!-- 左侧:缩略图 -->
      <PdfThumbPanel
        :total-pages="totalPages"
        :current-page="currentPage"
        :collapsed="thumbCollapsed"
        @goto="goToPage"
        @rotate="onRotatePage"
        @reorder="onReorderPages"
        @context-menu="onThumbContextMenu"
        @thumb-ready="onThumbReady"
        @toggle-collapse="thumbCollapsed = !thumbCollapsed"
      />

      <!-- 中央:画布 + 多页 + 间隙 -->
      <main class="pdf-canvas-area" ref="canvasAreaRef">
        <div v-if="loading" class="pdf-state pdf-state-loading">
          <div class="pdf-state-spinner"></div>
          <span>正在加载 PDF...</span>
        </div>
        <div v-else-if="error" class="pdf-state pdf-state-error">
          <span>⚠ {{ error }}</span>
        </div>
        <!-- Phase 11.8: PDF 加载完但 pageRawHeight 还没就绪时显示骨架占位,模拟真实文档布局 -->
        <div v-else-if="pageRawHeight === 0" class="pdf-canvas-skeleton">
          <div class="pdf-canvas-skeleton-card">
            <div class="pdf-canvas-skeleton-head">
              <div class="pdf-canvas-skeleton-line w-30 h-20"></div>
              <div class="pdf-canvas-skeleton-line w-50 h-12"></div>
            </div>
            <div class="pdf-canvas-skeleton-body">
              <div class="pdf-canvas-skeleton-line w-90"></div>
              <div class="pdf-canvas-skeleton-line w-75"></div>
              <div class="pdf-canvas-skeleton-line w-95"></div>
              <div class="pdf-canvas-skeleton-line w-60"></div>
              <div class="pdf-canvas-skeleton-line w-88"></div>
              <div class="pdf-canvas-skeleton-line w-70"></div>
              <div class="pdf-canvas-skeleton-chart"></div>
              <div class="pdf-canvas-skeleton-line w-92"></div>
              <div class="pdf-canvas-skeleton-line w-65"></div>
              <div class="pdf-canvas-skeleton-line w-80"></div>
              <div class="pdf-canvas-skeleton-line w-50"></div>
            </div>
            <div class="pdf-canvas-skeleton-footer">
              <span class="pdf-canvas-skeleton-text">正在准备画布...</span>
              <div class="pdf-canvas-skeleton-line w-15 h-8"></div>
            </div>
          </div>
        </div>
        <template v-else>
          <!-- V3: 单页 / 连续 / 双页 三种视图 -->
          <template v-if="viewMode === 'single'">
            <PdfCanvas
              :key="currentPage"
              :page-num="currentPage"
              :total-pages="totalPages"
              :scale="scale"
              :page-raw-width="pageRawWidth"
              :page-raw-height="pageRawHeight"
              :active-tool="activeTool"
              :active-color="activeColor"
              :annotations="annotations"
              :pending-rect="pendingRect"
              :drawing-path="drawingPath"
              :recognized="recognizedPages.has(currentPage)"
              :form-highlight="formHighlightFor(currentPage)"
              :is-editing="activeTool === 'textEdit'"
              @ready="onPageReady"
              @mouse-down="onCanvasMouseDown"
              @mouse-move="onCanvasMouseMove"
              @mouse-up="onCanvasMouseUp"
              @mouse-leave="onCanvasMouseLeave"
              @context-menu="onCanvasContextMenu"
            >
              <template #text-edit="{ pageNum: pn, scale: sc }">
                <PdfTextEditorLayer
                  v-if="activeTool === 'textEdit'"
                  :page-num="pn"
                  :scale="sc"
                  :page-raw-height="pageRawHeight"
                  :can-edit="canEdit"
                  :editor="textEditor"
                />
              </template>
              <template #ocr="{ pageNum: pn, scale: sc }">
                <PdfOcrLayer
                  v-if="recognizedPages.has(pn) && activeTool !== 'textEdit'"
                  :page-num="pn"
                  :scale="sc"
                  :page-raw-height="pageRawHeight"
                  :tokens="ocrTokensForPage(pn)"
                  :selectable="activeTool === 'select'"
                  :show-text="showOcrOverlay"
                />
              </template>
            </PdfCanvas>
          </template>

          <!-- 双页对照(facing) -->
          <template v-else-if="viewMode === 'facing'">
            <div class="pdf-facing-pair">
              <PdfCanvas
                v-if="currentPage <= totalPages"
                :key="`L-${currentPage}`"
                :page-num="currentPage"
                :total-pages="totalPages"
                :scale="scale"
                :page-raw-width="pageRawWidth"
                :page-raw-height="pageRawHeight"
                :active-tool="activeTool"
                :active-color="activeColor"
                :annotations="annotations"
                :pending-rect="pendingRect"
                :drawing-path="drawingPath"
                :recognized="recognizedPages.has(currentPage)"
                :form-highlight="formHighlightFor(currentPage)"
                :is-editing="activeTool === 'textEdit'"
                @ready="onPageReady"
                @mouse-down="onCanvasMouseDown"
                @mouse-move="onCanvasMouseMove"
                @mouse-up="onCanvasMouseUp"
                @mouse-leave="onCanvasMouseLeave"
              />
              <PdfCanvas
                v-if="currentPage + 1 <= totalPages"
                :key="`R-${currentPage + 1}`"
                :page-num="currentPage + 1"
                :total-pages="totalPages"
                :scale="scale"
                :page-raw-width="pageRawWidth"
                :page-raw-height="pageRawHeight"
                :active-tool="activeTool"
                :active-color="activeColor"
                :annotations="annotations"
                :pending-rect="pendingRect"
                :drawing-path="drawingPath"
                :recognized="recognizedPages.has(currentPage + 1)"
                :form-highlight="formHighlightFor(currentPage + 1)"
                :is-editing="activeTool === 'textEdit'"
                @ready="onPageReady"
                @context-menu="onCanvasContextMenu"
              />
            </div>
          </template>

          <!-- 默认:连续 -->
          <template v-else>
            <PdfCanvas
              v-for="i in totalPages"
              :key="i"
              :page-num="i"
              :total-pages="totalPages"
              :scale="scale"
              :page-raw-width="pageRawWidth"
              :page-raw-height="pageRawHeight"
              :active-tool="activeTool"
              :active-color="activeColor"
              :annotations="annotations"
              :pending-rect="pendingRect"
              :drawing-path="drawingPath"
              :recognized="recognizedPages.has(i)"
              :form-highlight="formHighlightFor(i)"
              :is-editing="activeTool === 'textEdit'"
              @ready="onPageReady"
              @mouse-down="onCanvasMouseDown"
              @mouse-move="onCanvasMouseMove"
              @mouse-up="onCanvasMouseUp"
              @mouse-leave="onCanvasMouseLeave"
              @context-menu="onCanvasContextMenu"
            >
              <template #text-edit="{ pageNum: pn, scale: sc }">
                <PdfTextEditorLayer
                  v-if="activeTool === 'textEdit'"
                  :page-num="pn"
                  :scale="sc"
                  :page-raw-height="pageRawHeight"
                  :can-edit="canEdit"
                  :editor="textEditor"
                />
              </template>
              <template #ocr="{ pageNum: pn, scale: sc }">
                <PdfOcrLayer
                  v-if="recognizedPages.has(pn) && activeTool !== 'textEdit'"
                  :page-num="pn"
                  :scale="sc"
                  :page-raw-height="pageRawHeight"
                  :tokens="ocrTokensForPage(pn)"
                  :selectable="activeTool === 'select'"
                  :show-text="showOcrOverlay"
                />
              </template>
            </PdfCanvas>
          </template>
        </template>
      </main>

      <!-- 右侧:任务面板(可选,Phase 8 完整实现) -->
      <Transition name="pdf-panel-fade">
        <PdfRightPanel
          v-if="rightPanelOpen"
          :doc-id="docId"
          :initial-tab="rightPanelOpen"
          :annotations="annotations"
          :current-user-id="userId"
          @jump="goToPage"
          @collapse="rightPanelOpen = null"
          @remove-annotation="onRemoveAnnotation"
          @focus-field="onFocusFormField"
          @form-filled="onFormFilled"
        />
      </Transition>

      <!-- Phase 11.5 Q4: 右侧快捷工具栏(借鉴 Adobe Acrobat DC Tools 面板) -->
      <PdfToolsRail
        :active-tool="activeTool"
        :right-panel="rightPanelOpen"
        :ai-visible="aiVisible"
        :collapsed="toolsRailCollapsed"
        @select-tool="selectTool"
        @export="onOpenExport"
        @print="onPrint"
        @share="onShare"
        @send-sign="onSendSign"
        @open-ai="onOpenAi"
        @toggle-panel="toggleRightPanel"
        @toggle-collapse="toolsRailCollapsed = !toolsRailCollapsed"
      />

      <!-- Phase 9: 浮动文本格式工具栏(选区出现时浮现) -->
      <PdfFloatingToolbar @format="onFloatingFormat" />
    </div>

    <!-- 3. 状态条(Adobe 风格 V3.2) -->
    <footer class="pdf-statusbar">
      <!-- 左:页码导航 -->
      <div class="pdf-sb-group pdf-sb-left">
        <span class="pdf-sb-filename" :title="filename">
          <svg class="ico" viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h5"/>
          </svg>
          {{ filename || 'document.pdf' }}
        </span>
        <span class="pdf-sb-divider"></span>
        <button class="pdf-sb-page-nav" :disabled="currentPage <= 1" @click="goPrev" aria-label="上一页">‹</button>
        <span class="pdf-sb-page-info">
          <span class="pdf-sb-page-current">{{ currentPage }}</span>
          <span class="pdf-sb-page-sep">/</span>
          <span>{{ totalPages }}</span>
        </span>
        <button class="pdf-sb-page-nav" :disabled="currentPage >= totalPages" @click="goNext" aria-label="下一页">›</button>
      </div>

      <!-- 中:工具 / 状态 -->
      <div class="pdf-sb-group pdf-sb-center">
        <span class="pdf-sb-chip">
          <svg class="ico" viewBox="0 0 24 24" width="11" height="11" aria-hidden="true">
            <path d="M3 3l7 17 2-8 8-2L3 3z"/>
          </svg>
          {{ toolLabel }}
        </span>
        <span class="pdf-sb-divider"></span>
        <span class="pdf-sb-chip">
          <svg class="ico" viewBox="0 0 24 24" width="11" height="11" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z M8 9h8 M8 13h6"/>
          </svg>
          {{ annotations.length }} 标注
        </span>
        <span v-if="onlineUsers.length > 0" class="pdf-sb-divider"></span>
        <span v-if="onlineUsers.length > 0" class="pdf-sb-chip pdf-sb-online">
          <span class="pdf-sb-online-dot"></span>
          {{ onlineUsers.length }} 在线
        </span>
        <span v-if="recognizeStatus" class="pdf-sb-divider"></span>
        <span v-if="recognizeStatus" class="pdf-sb-chip" :class="`pdf-sb-ocr-${recognizeStatus}`">
          <svg class="ico" viewBox="0 0 24 24" width="11" height="11" aria-hidden="true">
            <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7zM12 9v6 M9 12h6"/>
          </svg>
          {{ recognizeStatusLabel }}
        </span>
      </div>

      <!-- 右:缩放 + 保存状态 -->
      <div class="pdf-sb-group pdf-sb-right">
        <span v-if="saveStatus === 'saving'" class="pdf-sb-save pdf-sb-saving">
          <span class="pdf-sb-save-spinner"></span>
          保存中...
        </span>
        <span v-else-if="saveStatus === 'saved'" class="pdf-sb-save pdf-sb-saved">
          <span class="pdf-sb-dot"></span>
          已保存 · {{ saveTime }}
        </span>
        <span v-else-if="saveStatus === 'error'" class="pdf-sb-save pdf-sb-error">⚠ 保存失败</span>

        <span v-if="saveStatus === 'saved' || saveStatus === 'error'" class="pdf-sb-divider"></span>

        <button class="pdf-sb-zoom-btn" @click="onZoomOut" aria-label="缩小" title="缩小 (-)">−</button>
        <input
          class="pdf-sb-zoom-slider"
          type="range"
          min="25" max="400" step="5"
          :value="zoomPercent"
          @input="onZoomSliderChange"
          title="缩放(拖动滑块或输入数字)"
          aria-label="缩放"
        />
        <span class="pdf-sb-zoom-display" :title="`${zoomPercent}%`" @click="onFitWidth">{{ zoomPercent }}%</span>
        <button class="pdf-sb-zoom-btn" @click="onZoomIn" aria-label="放大" title="放大 (+)">+</button>
        <button class="pdf-sb-zoom-btn pdf-sb-zoom-fit" @click="onFitWidth" title="适合宽度 (W)" aria-label="适合宽度">⊡</button>
        <button class="pdf-sb-zoom-btn pdf-sb-zoom-fit" @click="onFitPage" title="适合页面" aria-label="适合页面">⊒</button>
        <button class="pdf-sb-zoom-btn pdf-sb-zoom-fit" @click="onActualSize" title="实际大小" aria-label="实际大小">1:1</button>
      </div>
    </footer>

    <!-- V2 保留组件(PdfPageOpsMenu / MergeDialog 等,Phase 3 已实现) -->
    <PdfPageOpsMenu
      :open="pageMenuOpen"
      :anchor="pageMenuAnchor"
      :doc-id="docId"
      :file-url="fileUrl"
      :current-page="currentPage"
      :total-pages="totalPages"
      :flush-text-edits="flushTextEdits"
      :on-saved="onPageOpSaved"
      @close="pageMenuOpen = false"
    />
    <PdfExportMenu
      :open="exportMenuOpen"
      :anchor="exportMenuAnchor"
      :doc-id="docId"
      :current-page="currentPage"
      :filename="filename"
      @close="exportMenuOpen = false"
    />
    <PdfThumbnailContextMenu
      :open="ctxMenuOpen"
      :anchor="ctxMenuAnchor"
      :page-num="ctxMenuPageNum"
      :total-pages="totalPages"
      :busy="pageOps.busy.value"
      @close="ctxMenuOpen = false"
      @goto="onCtxGoto"
      @rotate="onCtxRotate"
      @extract="onCtxExtract"
      @delete="onCtxDelete"
    />
    <MergeDialog
      v-if="mergeDialogOpen"
      v-model="mergeDialogOpen"
      :exclude-doc-id="docId"
      :exclude-doc-title="filename"
      @confirm="onMergeConfirmed"
    />
    <!-- Phase 11: 页面操作(插入空白/裁剪/水印/页眉页脚) -->
    <PdfPageOpsDialog
      v-if="pageOpsDialogOpen"
      v-model="pageOpsDialogOpen"
      :doc-id="docId"
      :current-page="currentPage"
      :total-pages="totalPages"
      :initial-tab="pageOpsInitialTab"
      @success="onPageOpSuccess"
    />
    <!-- Phase 12.3: 签名创建对话框 -->
    <PdfSignatureDialog
      :open="signatureDialogOpen"
      @close="signatureDialogOpen = false"
      @created="onSignatureCreated"
    />
    <!-- Phase 12.4: 保护 PDF 对话框 -->
    <PdfSecurityDialog
      :open="securityDialogOpen"
      :doc-id="docId"
      @close="securityDialogOpen = false"
      @done="onSecurityDone"
    />
    <!-- Phase 13.8: PDF 画布右键快捷菜单 -->
    <PdfCanvasContextMenu
      :open="canvasMenuOpen"
      :anchor="canvasMenuAnchor"
      :page-num="canvasMenuPage"
      :total-pages="totalPages"
      :active-tool="activeTool"
      :has-selection="canvasMenuHasSelection"
      @close="canvasMenuOpen = false"
      @copy="onCanvasMenuCopy"
      @select-all="onCanvasMenuSelectAll"
      @edit-text="onCanvasMenuEditText"
      @select-tool="onCanvasMenuSelectTool"
      @rotate="onCtxRotate"
      @extract="onCtxExtract"
      @delete="onCtxDelete"
      @ai-translate="onCanvasMenuAiTranslate"
      @ai-summarize="onCanvasMenuAiSummarize"
      @ai-chat="onCanvasMenuAiChat"
      @ocr-recognize="onCanvasMenuOcr"
    />
    <!-- Phase 13.8: 编辑模式提示条(Acrobat DC 风格) -->
    <div v-if="activeTool === 'textEdit'" class="pdf-edit-banner">
      <span class="pdf-edit-banner-icon">✏️</span>
      <span class="pdf-edit-banner-text">编辑模式 - 点击文字修改,按 Esc 退出</span>
      <button class="pdf-edit-banner-exit" @click="exitEditMode">退出编辑</button>
    </div>
    <PdfAiMenu
      :open="aiMenuOpen"
      :anchor="aiMenuAnchor"
      :doc-id="docId"
      :recognized-markdown="recognizedMarkdown"
      @close="aiMenuOpen = false"
      @open-chat="onOpenAiChatFromMenu"
      @open-terms="onOpenTermsFromMenu"
    />
    <Transition name="pdf-drawer-fade">
      <aside v-if="termsPanelOpen" class="pdf-terms-drawer" role="complementary">
        <header class="pdf-terms-drawer-header">
          <h3>合同条款抽取</h3>
          <button class="pdf-terms-drawer-close" @click="termsPanelOpen = false">
            <svg class="ico" viewBox="0 0 24 24" width="14" height="14"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </header>
        <div class="pdf-terms-drawer-body">
          <PdfTermsPanel :doc-id="docId" />
        </div>
      </aside>
    </Transition>

    <!-- Phase 11.8: AI 浮窗(始终挂载,通过 visible 双向绑定,合并重复 FAB) -->
    <PdfAiFloatPanel
      :doc-id="docId"
      :visible="aiVisible"
      :vqa-image="vqaImage"
      :vqa-context="vqaContext"
      @update:visible="aiVisible = $event"
      @clear-vqa="clearVqa"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * PdfEditor V3 —— 主壳
 *
 * V2 → V3 关键变化:
 *   - 引入 PdfRibbon 多标签顶栏(替代单一工具栏)
 *   - 引入 PdfThumbPanel V3(z-index 修复 + 2x 缩略图 + 懒加载)
 *   - 引入右侧任务面板(PdfRightPanel,Phase 8 完整实现,本 Phase 仅占位)
 *   - 引入视图模式(usePdfViewMode: single/continuous/facing)
 *   - 状态条精简 + V3 设计
 *   - 保留 V2 所有功能(AI/页面操作/文本编辑/协同)
 */
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import '@/styles/pdf-tokens.css'

import PdfRibbon from './PdfRibbon.vue'
import PdfThumbPanel from './PdfThumbPanel.vue'
import PdfCanvas from './PdfCanvas.vue'
import PdfAiFloatPanel from './PdfAiFloatPanel.vue'
import PdfTextEditorLayer from './PdfTextEditorLayer.vue'
import PdfOcrLayer from './PdfOcrLayer.vue'
import PdfRightPanel from './PdfRightPanel.vue'
import PdfToolsRail from './PdfToolsRail.vue'
import PdfFloatingToolbar from './PdfFloatingToolbar.vue'
import PdfPageOpsMenu from './PdfPageOpsMenu.vue'
import PdfExportMenu from './PdfExportMenu.vue'
import PdfThumbnailContextMenu from './PdfThumbnailContextMenu.vue'
import PdfCanvasContextMenu from './PdfCanvasContextMenu.vue'
import PdfAiMenu from './PdfAiMenu.vue'
import PdfSignatureDialog from './PdfSignatureDialog.vue'
import PdfSecurityDialog from './PdfSecurityDialog.vue'
import MergeDialog from './MergeDialog.vue'
import PdfPageOpsDialog from './PdfPageOpsDialog.vue'
import PdfTermsPanel from './PdfTermsPanel.vue'

import { usePdfRenderer } from '@/composables/pdf/usePdfRenderer'
import { usePdfCollaborate } from '@/composables/pdf/usePdfCollaborate'
import { usePdfAnnotation, type AnnotationTool } from '@/composables/pdf/usePdfAnnotation'
import { usePdfTextEditor } from '@/composables/pdf/usePdfTextEditor'
import { usePdfPageOps } from '@/composables/pdf/usePdfPageOps'
import { usePdfAiFloat } from '@/composables/pdf/usePdfAiFloat'
import { usePdfViewMode, type ViewMode } from '@/composables/pdf/usePdfViewMode'
import type { PageOpResult } from '@/api/pdf'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  docId: number
  docKey: string
  fileUrl: string
  canEdit: boolean
  userName: string
  userId: number
  filename?: string
}>()

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'stateChange', state: string): void
  (e: 'fileUrlChanged', newUrl: string): void
}>()

const router = useRouter()

// ========== 渲染层 ==========
const token = sessionStorage.getItem('token')
const renderer = usePdfRenderer({
  fileUrl: props.fileUrl,
  token,
  thumbScale: 0.4,
  initialScale: 1.2,
})

const totalPages = computed(() => renderer.totalPages.value)
const scale = computed(() => renderer.scale.value)
const loading = computed(() => renderer.loading.value)
const error = computed(() => {
  const e = renderer.error.value
  return e ? (e.message || 'PDF 加载失败') : null
})

const pageRawWidth = ref(595)
const pageRawHeight = ref(842)

// ========== 视图模式 ==========
const viewModeLogic = usePdfViewMode()
const viewMode = computed(() => viewModeLogic.viewMode.value)
const setViewMode = (m: ViewMode) => viewModeLogic.setViewMode(m)
const cycleViewMode = () => viewModeLogic.cycleViewMode()

// ========== 协同层 ==========
const collab = usePdfCollaborate({
  docKey: props.docKey,
  userId: props.userId,
  userName: props.userName,
})

// ========== 标注层 ==========
const annot = usePdfAnnotation({
  yAnnotations: collab.yAnnotations,
  userId: props.userId,
  userName: props.userName,
  canEdit: props.canEdit,
})

const activeTool = computed(() => annot.activeTool.value)
const activeColor = computed(() => annot.activeColor.value)
const stampText = computed(() => (annot as any).stampText?.value ?? 'DRAFT')
const stampPresets = computed(() => (annot as any).stampPresets?.value ?? [])
const annotations = computed(() => annot.annotations.value)
const pendingRect = computed(() => annot.pendingRect.value)
const drawingPath = computed(() => (annot as any).drawPath ?? null)
const onlineUsers = computed(() => collab.onlineUsers.value)

const selectTool = (t: AnnotationTool) => (annot.activeTool.value = t)
const selectColor = (c: string) => (annot.activeColor.value = c)

// ========== 文本编辑 ==========
const textEditor = usePdfTextEditor({
  docId: props.docId,
  onSaved: () => {
    saveStatus.value = 'saved'
    saveTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    void reloadAfterTextEdit()
  },
  onError: () => (saveStatus.value = 'error'),
})

async function reloadAfterTextEdit() {
  try {
    renderer.destroy()
    textEditor.clearCache()
    await renderer.load()
    if (renderer.pdfDoc.value) {
      const page = await renderer.pdfDoc.value.getPage(1)
      const vp = page.getViewport({ scale: 1 })
      pageRawWidth.value = vp.width
      pageRawHeight.value = vp.height
    }
    if (thumbRefs.size > 0) {
      await renderer.renderAllThumbs(thumbRefs)
    }
  } catch (e) {
    console.error('[PdfEditor] reloadAfterTextEdit failed:', e)
  }
}

// ========== 页面操作 ==========
const pageOps = usePdfPageOps({
  docId: props.docId,
  fileUrl: props.fileUrl,
  beforeOp: async () => {
    if (textEditor.dirty.value) {
      try {
        await textEditor.flush()
      } catch {
        return false
      }
    }
    return true
  },
  onSaved: () => {
    saveStatus.value = 'saved'
    saveTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  },
  onError: () => (saveStatus.value = 'error'),
})

async function onPageOpSaved(result: PageOpResult, newFileUrl: string) {
  await reloadAfterPageOp(newFileUrl)
}

async function reloadAfterPageOp(newFileUrl: string) {
  try {
    saveStatus.value = 'saving'
    renderer.destroy()
    textEditor.clearCache()
    emit('fileUrlChanged', newFileUrl)
    await renderer.load()
    if (renderer.pdfDoc.value) {
      const page = await renderer.pdfDoc.value.getPage(1)
      const vp = page.getViewport({ scale: 1 })
      pageRawWidth.value = vp.width
      pageRawHeight.value = vp.height
    }
    currentPage.value = 1
    canvasRefs.clear()
    textLayerRefs.clear()
    annotationRefs.clear()
    thumbRefs.clear()
    saveStatus.value = 'saved'
    saveTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch (e) {
    console.error('[PdfEditor] reloadAfterPageOp failed:', e)
    saveStatus.value = 'error'
  }
}

async function flushTextEdits(): Promise<void> {
  if (textEditor.dirty.value) {
    await textEditor.flush()
  }
}

// ========== Phase 9: 浮动格式工具栏 ==========
/**
 * 浮动工具栏发来 format 事件:
 * - fontSize: 改字号(对当前选中 token/段)
 * - color: 改文字颜色
 * - highlight: 高亮(走 document.execCommand 浏览器原生 hiliteColor)
 * - bold / italic / underline: 走浏览器原生 execCommand(即时视觉),后端持久化下一阶段
 */
function onFloatingFormat(payload: { type: string; value?: string | number }): void {
  // 先抓选区(mousedown.prevent 后 selection 还在,但 execCommand 后会丢)
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return
  const range = sel.getRangeAt(0)
  const container = (range.commonAncestorContainer as Element).closest?.(
    '.pdf-text-edit-layer, .pdf-text-layer',
  )
  if (!container) return

  // 高亮走浏览器原生实现(选中文本即时着色)
  if (payload.type === 'highlight' && typeof payload.value === 'string') {
    let ok = false
    try {
      ok = !!document.execCommand('hiliteColor', false, payload.value)
    } catch { /* ignore */ }
    if (!ok) {
      // 退化:给选区内每个 span 加 inline 背景色
      paintRangeInline(range, 'backgroundColor', payload.value)
    }
    // 重新选中(防止浏览器清空 selection 后浮动条消失)
    requestAnimationFrame(() => restoreSelection(range))
    return
  }

  // 粗/斜/下划线走浏览器原生 execCommand(即时视觉)
  if (payload.type === 'bold') {
    document.execCommand('bold')
    requestAnimationFrame(() => restoreSelection(range))
    return
  }
  if (payload.type === 'italic') {
    document.execCommand('italic')
    requestAnimationFrame(() => restoreSelection(range))
    return
  }
  if (payload.type === 'underline') {
    document.execCommand('underline')
    requestAnimationFrame(() => restoreSelection(range))
    return
  }

  // 字号/颜色:尝试找到对应 token 并走持久化
  const focusedEl = (document.activeElement as HTMLElement | null)?.closest?.(
    '.pdf-edit-token',
  ) as HTMLElement | null
  if (!focusedEl) {
    // 没有可编辑 token 聚焦,只是文字层选区 → 仅做即时视觉反馈
    if (payload.type === 'color' && typeof payload.value === 'string') {
      try {
        const ok = document.execCommand('foreColor', false, payload.value as string)
        if (!ok) throw new Error('execCommand returned false')
      } catch {
        // 退化方案:直接给选区每个 span 加 inline color
        paintRangeInline(range, 'color', payload.value as string)
      }
      requestAnimationFrame(() => restoreSelection(range))
      return
    }
    ElMessage.info('请先用文本工具点击段落进入编辑,再使用浮动格式工具栏')
    return
  }

  const tokenIdx = Number(focusedEl.dataset.tokenIdx)
  const pageNum = Number(focusedEl.dataset.pageNum)
  if (Number.isNaN(tokenIdx) || Number.isNaN(pageNum)) return
  const positions = textEditor.positionsByPage.value.get(pageNum) || []
  const token = positions[tokenIdx]
  if (!token) return

  const newEdit = {
    position: token,
    newText: focusedEl.textContent ?? token.text ?? '',
    color:
      payload.type === 'color' && typeof payload.value === 'string'
        ? payload.value
        : undefined,
  }
  textEditor.applyEdit(newEdit)
}

/**
 * execCommand 后浏览器常常清空 selection,
 * 这里克隆原 range 并重新设为当前 selection,
 * 防止浮动工具栏的 selectionchange 监听触发 hide。
 */
function restoreSelection(range: Range): void {
  try {
    const sel = window.getSelection()
    if (!sel) return
    sel.removeAllRanges()
    sel.addRange(range)
  } catch { /* ignore */ }
}

/**
 * 当 document.execCommand 在非 contenteditable 节点上失败时,
 * 退化方案:遍历选区内的 span,给每个 span 加 inline style,
 * 达到即时视觉反馈。
 */
function paintRangeInline(range: Range, prop: 'color' | 'backgroundColor', value: string): void {
  try {
    const container = range.commonAncestorContainer.nodeType === 1
      ? (range.commonAncestorContainer as Element)
      : (range.commonAncestorContainer.parentElement as Element | null)
    if (!container) return

    // 找选区所在的最近 .pdf-text-layer 作为搜索根
    const root = container.closest('.pdf-text-layer') || container
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT)
    const targets: HTMLElement[] = []
    let node = walker.nextNode() as HTMLElement | null
    while (node) {
      if (node.tagName === 'SPAN' && range.intersectsNode(node)) {
        targets.push(node)
      }
      node = walker.nextNode() as HTMLElement | null
    }
    targets.forEach((el) => {
      el.style.setProperty(prop, value)
    })
  } catch (e) {
    console.warn('[PdfEditor] paintRangeInline failed', e)
  }
}

// ========== AI ==========
const aiFloat = usePdfAiFloat({ docId: props.docId })
const aiVisible = ref(false)
const aiStreaming = computed(() => aiFloat.isStreaming.value)
const vqaImage = ref<string | undefined>(undefined)
const vqaContext = ref('')

const recognizedMarkdown = computed(() => {
  const map = textEditor.positionsByPage.value as Map<number, any>
  const parts: string[] = []
  for (const [, arr] of map.entries()) {
    if (Array.isArray(arr)) {
      parts.push(arr.map((p: any) => p.text || '').join('\n'))
    }
  }
  return parts.join('\n\n')
})

function onOpenAi() {
  aiVisible.value = !aiVisible.value
}
function clearVqa() {
  vqaImage.value = undefined
  vqaContext.value = ''
}

/**
 * Phase 11.4: OCR 识别(PaddleOCR 路径)
 * 1) 状态:unrecognized → recognizing → recognized
 * 2) 后端存 ocrData → text-positions
 * 3) 重新加载 positions,渲染 bbox 框
 */
async function onOcrRecognize(model: 'mobile' | 'server' = 'mobile') {
  if (recognizeStatus.value === 'recognizing') return
  recognizeStatus.value = 'recognizing'
  const modelLabel = model === 'server' ? '高精度' : '快速'
  ElMessage.info(`OCR ${modelLabel}识别中(PaddleOCR ${model})...`)
  try {
    const r: any = await pdfApi.recognizePaddle(props.docId, model)
    if (r.status !== 'success') {
      recognizeStatus.value = 'error'
      ElMessage.error(r.error || 'OCR 识别失败')
      return
    }
    // server 降级提示
    if (r.degraded) {
      ElMessage.warning('server 引擎不可用,已降级 mobile 识别')
    }
    // 标记所有页已识别 + 重新加载 positions
    const total = r.totalPages || 1
    recognizedPages.value = new Set(Array.from({ length: total }, (_, i) => i + 1))
    await textEditor.loadAllPositions()
    // Phase 11.4: 重新触发所有挂载页面的渲染,让 PdfTextEditorLayer 显示 OCR bbox
    for (const [pageNum, canvasEl] of canvasRefs.entries()) {
      const textLayerEl = textLayerRefs.get(pageNum)
      if (textLayerEl) {
        try { await renderer.renderPage(pageNum, canvasEl, textLayerEl) } catch {}
      }
    }
    recognizeStatus.value = 'recognized'
    ElMessage.success(`OCR ${modelLabel}识别完成(共 ${(r.pages || []).reduce((s: number, p: any) => s + (p.regions?.length || 0), 0)} 个文字区域)`)
  } catch (e: any) {
    console.error('[PdfEditor] OCR failed:', e)
    recognizeStatus.value = 'error'
    ElMessage.error(e?.message || 'OCR 调用失败')
  }
}

/**
 * Phase 11.4: 取指定页 OCR tokens(PdfTextEditorLayer 使用的同源数据)
 */
function ocrTokensForPage(pageNum: number) {
  const arr = (textEditor as any).positionsByPage?.value?.get?.(pageNum) || []
  return arr as Array<{ text: string; x: number; y: number; width: number; height: number; confidence?: number }>
}

// ========== Ribbon 状态 ==========
type RibbonTab = 'home' | 'edit' | 'page' | 'view'
const activeRibbonTab = ref<RibbonTab>('edit')

function onChangeRibbonTab(tab: RibbonTab) {
  activeRibbonTab.value = tab
}

function onRemoveAnnotation(id: string) {
  // 仅删除当前用户的批注
  const ann = annot.annotations.value.find(a => a.id === id)
  if (!ann) return
  if (ann.userId !== props.userId) {
    ElMessage.warning('只能删除自己的批注')
    return
  }
  annot.remove(id)
  ElMessage.success('已删除批注')
}

function onStampTextChange(text: string) {
  ;(annot as any).setStampText?.(text)
}

// ========== 右侧任务面板 ==========
type RightPanel = 'outline' | 'search' | 'info' | 'annotations' | null
const rightPanelOpen = ref<RightPanel>(null)
function toggleRightPanel(p: 'outline' | 'search' | 'info' | 'annotations') {
  rightPanelOpen.value = rightPanelOpen.value === p ? null : p
}

// ========== 页面导航 ==========
const currentPage = ref(1)
const zoomPercent = computed(() => Math.round(scale.value * 100))

const saveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const saveTime = ref('')

const toolLabel = computed(() => {
  const map: Record<AnnotationTool, string> = {
    select: '选择',
    textEdit: '文本',
    highlight: '高亮',
    comment: '评论',
    draw: '画笔',
    eraser: '橡皮',
    vqa: '识图',
    rectangle: '矩形',
    ellipse: '椭圆',
    arrow: '箭头',
    line: '直线',
    underline: '下划线',
    strikethrough: '删除线',
    stamp: '图章',
  }
  return map[activeTool.value] ?? '选择'
})

function goToPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
  nextTickScroll()
}

// Phase 12.1: 聚焦表单字段(跳转 + 临时高亮矩形)
const formFieldHighlight = ref<{ pageNum: number; x: number; y: number; w: number; h: number; name: string; expireAt: number } | null>(null)
let formHighlightTimer: number | null = null
function onFocusFormField(field: { page: number; rect: [number, number, number, number]; name: string }) {
  if (field.page <= 0) {
    ElMessage.info(`字段 "${field.name}" 未定位到页面`)
    return
  }
  goToPage(field.page)
  const [llx, lly, urx, ury] = field.rect
  formFieldHighlight.value = {
    pageNum: field.page,
    x: llx, y: lly, w: urx - llx, h: ury - lly,
    name: field.name,
    expireAt: Date.now() + 4000,
  }
  if (formHighlightTimer) window.clearTimeout(formHighlightTimer)
  formHighlightTimer = window.setTimeout(() => {
    formFieldHighlight.value = null
  }, 4000)
  ElMessage.success(`已定位到字段: ${field.name}`)
}
function formHighlightFor(pageNum: number) {
  if (!formFieldHighlight.value || formFieldHighlight.value.pageNum !== pageNum) return null
  const f = formFieldHighlight.value
  return { x: f.x, y: f.y, w: f.w, h: f.h, name: f.name }
}

// Phase 12.2: 表单填充完成后,触发下载
function onFormFilled(blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `filled-${props.docId}-${Date.now()}.pdf`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function nextTickScroll() {
  if (viewMode.value === 'continuous') {
    nextTickSmooth(() => {
      const card = canvasAreaRef.value?.querySelector(`.pdf-page-card:nth-of-type(${currentPage.value})`) as HTMLElement | null
      card?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }
}

function nextTickSmooth(fn: () => void) {
  // 兼容 import 顺序:放到下面
  ;(nextTick as any)(fn)
}
import { nextTick } from 'vue'

function goPrev() { goToPage(currentPage.value - 1) }
function goNext() {
  if (viewMode.value === 'facing') {
    goToPage(currentPage.value + 2)
  } else {
    goToPage(currentPage.value + 1)
  }
}

// ========== 缩放 ==========
async function onZoomIn() {
  await renderer.zoomIn(canvasRefs, textLayerRefs)
  viewModeLogic.setZoom('custom', renderer.scale.value)
}
async function onZoomOut() {
  await renderer.zoomOut(canvasRefs, textLayerRefs)
  viewModeLogic.setZoom('custom', renderer.scale.value)
}
async function onZoomSliderChange(e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v)) {
    renderer.setScale(v / 100)
    await renderer.reRenderAll(canvasRefs, textLayerRefs)
    viewModeLogic.setZoom('custom', v / 100)
  }
}
async function onFitWidth() {
  const w = canvasAreaRef.value?.clientWidth ?? 720
  await renderer.fitWidth(w, canvasRefs, textLayerRefs)
  viewModeLogic.setZoom('fit-width')
}
async function onFitPage() {
  // fit-page: 让整页(高度+宽度)在视口内可见,取宽高比中较小的缩放
  const ca = canvasAreaRef.value
  if (!ca || !renderer.pdfDoc.value) return
  const page = await renderer.pdfDoc.value.getPage(1)
  const vp = page.getViewport({ scale: 1 })
  const availW = ca.clientWidth - 96   // 减 padding
  const availH = ca.clientHeight - 96
  const scaleW = availW / vp.width
  const scaleH = availH / vp.height
  renderer.setScale(Math.max(0.3, Math.min(scaleW, scaleH, 4)))
  await renderer.reRenderAll(canvasRefs, textLayerRefs)
  viewModeLogic.setZoom('fit-page')
}
async function onActualSize() {
  renderer.setScale(1.0)
  await renderer.reRenderAll(canvasRefs, textLayerRefs)
  viewModeLogic.setZoom('actual')
}

// ========== Ribbon 事件占位(Phase 8-12 完整实现) ==========
function onSave() { ElMessage.success('已保存(占位)') }
function onPrint() { ElMessage.info('打印功能开发中') }
function onShare() { ElMessage.info('分享功能开发中') }
function onSendSign() { ElMessage.info('发送签署开发中') }

// Phase 12.3: 签名创建 + 放置
const signatureDialogOpen = ref(false)
const pendingSignature = ref<{ imageBase64: string; width: number; height: number } | null>(null)
const signaturePlacing = ref(false)
const signatureSaving = ref(false)
function onOpenSignatureDialog() {
  signatureDialogOpen.value = true
}
function onSignatureCreated(payload: { imageBase64: string; width: number; height: number }) {
  pendingSignature.value = payload
  signaturePlacing.value = true
  ElMessage.info('请在 PDF 画布上点击放置签名')
}

// Phase 12.4: 保护 PDF 对话框
const securityDialogOpen = ref(false)
function onOpenSecurityDialog() {
  securityDialogOpen.value = true
}
function onSecurityDone(blob: Blob, action: 'encrypt' | 'decrypt') {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${action}-${props.docId}-${Date.now()}.pdf`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success(action === 'encrypt' ? '加密 PDF 已下载' : '解密 PDF 已下载')
}
async function placeSignature(pageNum: number, screenX: number, screenY: number) {
  if (!pendingSignature.value || signatureSaving.value) return
  signatureSaving.value = true
  try {
    // 屏幕 X/Y 是相对 canvas 的左上角,需要转 PDF 坐标(左下原点)
    // scale 已包含在 canvasWidth/canvasHeight 中
    const scaleX = 1 / scale.value  // 屏幕 pt -> PDF pt 的倒数
    const pdfX = screenX * scaleX
    // PDF Y 是从底部起算:pdfY = pageRawHeight - screenY/scale - signatureHeight/scale
    const sigW = pendingSignature.value.width / scale.value * 0.5  // 缩小到 50% 适配显示
    const sigH = pendingSignature.value.height / scale.value * 0.5
    const pdfY = pageRawHeight.value - (screenY * scaleX) - sigH
    const blob = await pdfApi.embedSignature(props.docId, {
      image: pendingSignature.value.imageBase64,
      page: pageNum,
      x: pdfX,
      y: pdfY,
      width: sigW,
      height: sigH,
    })
    // 下载签名后的 PDF
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `signed-${props.docId}-${Date.now()}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('签名已嵌入并下载')
    pendingSignature.value = null
    signaturePlacing.value = false
  } catch (e: any) {
    console.error('[PdfEditor] placeSignature failed:', e)
    ElMessage.error(e?.message || '签名嵌入失败')
  } finally {
    signatureSaving.value = false
  }
}
function onZoomMenu() { ElMessage.info('缩放菜单开发中') }
function onOpenExport(_evt?: MouseEvent) {
  const anchor = canvasAreaRef.value?.getBoundingClientRect()
  exportMenuAnchor.value = { x: anchor?.left ?? 0, y: anchor?.top ?? 56 }
  exportMenuOpen.value = true
  pageMenuOpen.value = false
}
function onOpenMerge(_e?: MouseEvent) { mergeDialogOpen.value = true }
function onExtractCurrent() {
  pageOps.extractPages([currentPage.value]).then((r) => {
    if (r) reloadAfterPageOp(pageOps.bustUrl(r))
  })
}
function onRotateAll() {
  const all = Array.from({ length: totalPages.value }, (_, i) => i + 1)
  pageOps.rotatePages(all).then((r) => {
    if (r) reloadAfterPageOp(pageOps.bustUrl(r))
  })
}
function onInsertBlank() {
  pageOpsInitialTab.value = 'insertBlank'
  pageOpsDialogOpen.value = true
}
function onInsertFromFile() {
  ElMessage.info('从文件插入开发中(Phase 11.1 后续)')
}
function onWatermark() {
  pageOpsInitialTab.value = 'watermark'
  pageOpsDialogOpen.value = true
}
function onHeaderFooter() {
  pageOpsInitialTab.value = 'headerFooter'
  pageOpsDialogOpen.value = true
}

async function onPageOpSuccess(_op: string) {
  // 后端已替换 filePath,前端 bustUrl 触发 reload
  try {
    saveStatus.value = 'saving'
    renderer.destroy()
    textEditor.clearCache()
    canvasRefs.clear()
    textLayerRefs.clear()
    annotationRefs.clear()
    thumbRefs.clear()
    await nextTick()
    await renderer.load()
    // 等待 pdfDoc + totalPages 真正加载完成
    let waited = 0
    while ((!renderer.pdfDoc.value || renderer.totalPages.value === 0) && waited < 10000) {
      await new Promise(r => setTimeout(r, 100))
      waited += 100
    }
    if (renderer.pdfDoc.value) {
      const page = await renderer.pdfDoc.value.getPage(1)
      const vp = page.getViewport({ scale: 1 })
      pageRawWidth.value = vp.width
      pageRawHeight.value = vp.height
    }
    currentPage.value = 1
    saveStatus.value = 'saved'
    saveTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch (e) {
    console.error('[PdfEditor] reload after page op failed:', e)
    saveStatus.value = 'error'
  }
}

// ========== DOM refs ==========
const canvasRefs = new Map<number, HTMLCanvasElement>()
const textLayerRefs = new Map<number, HTMLDivElement>()
const annotationRefs = new Map<number, SVGSVGElement>()
const thumbRefs = new Map<number, HTMLCanvasElement>()
const canvasAreaRef = ref<HTMLElement | null>(null)

const recognizedPages = ref<Set<number>>(new Set())
/** OCR 状态:'unrecognized' | 'recognizing' | 'recognized' | 'error' */
const recognizeStatus = ref<'unrecognized' | 'recognizing' | 'recognized' | 'error'>('unrecognized')
const recognizeStatusLabel = computed(() => {
  return {
    unrecognized: '未识别',
    recognizing: '识别中...',
    recognized: '已识别',
    error: '识别失败',
  }[recognizeStatus.value]
})

function onPageReady(
  pageNum: number,
  canvasEl: HTMLCanvasElement,
  textLayerEl: HTMLDivElement,
  annotationEl: SVGSVGElement,
) {
  canvasRefs.set(pageNum, canvasEl)
  textLayerRefs.set(pageNum, textLayerEl)
  annotationRefs.set(pageNum, annotationEl)
  nextTick(() => renderPageIfReady(pageNum))
}

function onThumbReady(pageNum: number, canvasEl: HTMLCanvasElement) {
  thumbRefs.set(pageNum, canvasEl)
  nextTick(() => renderThumbIfReady(pageNum))
}

async function renderPageIfReady(pageNum: number) {
  const canvasEl = canvasRefs.get(pageNum)
  const textLayerEl = textLayerRefs.get(pageNum)
  if (!canvasEl || !textLayerEl) return
  try {
    await renderer.renderPage(pageNum, canvasEl, textLayerEl)
    // Phase 13.7: 不覆盖 pageRawWidth/Height(onMounted 已设 PDF pt 正确值)
    // renderer.pageWidth/Height 是 canvas size(含 scale),覆盖会导致 OCR 坐标偏移
  } catch (e) {
    console.error(`[PdfEditor] renderPage(${pageNum}) failed:`, e)
  }
}

async function renderThumbIfReady(_pageNum: number) {
  try {
    if (renderer.pdfDoc.value && thumbRefs.size > 0) {
      await renderer.renderAllThumbs(thumbRefs)
    }
  } catch (e) {
    console.error('[PdfEditor] renderThumbs failed:', e)
  }
}

// ========== 缩略图事件 ==========
async function onRotatePage(pageNum: number, degrees: number) {
  const result = await pageOps.rotatePage(pageNum, degrees)
  if (result) await reloadAfterPageOp(pageOps.bustUrl(result))
}
async function onReorderPages(from: number, to: number) {
  const newOrder = pageOps.computeReorder(from, to, totalPages.value)
  const result = await pageOps.reorderPages(newOrder)
  if (result) await reloadAfterPageOp(pageOps.bustUrl(result))
}
function onThumbContextMenu(pageNum: number, x: number, y: number) {
  ctxMenuPageNum.value = pageNum
  ctxMenuAnchor.value = { x, y }
  ctxMenuOpen.value = true
}

function onCtxGoto(p: number) { goToPage(p) }
function onCtxRotate(p: number, d: number) { void onRotatePage(p, d) }
function onCtxExtract(p: number) {
  pageOps.extractPages([p]).then((r) => { if (r) reloadAfterPageOp(pageOps.bustUrl(r)) })
}
function onCtxDelete(p: number) {
  pageOps.deletePage(p).then((r) => { if (r) reloadAfterPageOp(pageOps.bustUrl(r)) })
}

// ========== Phase 13.8: 画布右键菜单 ==========
const canvasMenuOpen = ref(false)
const canvasMenuAnchor = ref<{ x: number; y: number } | null>(null)
const canvasMenuPage = ref(1)
const canvasMenuHasSelection = ref(false)

function onCanvasContextMenu(x: number, y: number, pageNum: number) {
  canvasMenuPage.value = pageNum
  canvasMenuAnchor.value = { x, y }
  canvasMenuHasSelection.value = !!window.getSelection()?.toString().trim()
  canvasMenuOpen.value = true
}

function onCanvasMenuCopy() {
  const sel = window.getSelection()?.toString() || ''
  if (sel) {
    navigator.clipboard?.writeText(sel).then(
      () => ElMessage.success('已复制'),
      () => ElMessage.warning('复制失败,请手动 Ctrl+C'),
    )
  }
}
function onCanvasMenuSelectAll() {
  // 选当前页 text layer 所有文字
  const tl = document.querySelector(`.pdf-page-card[data-page-num="${canvasMenuPage.value}"] .pdf-text-layer`) as HTMLElement | null
  if (tl) {
    const range = document.createRange()
    range.selectNodeContents(tl)
    const sel = window.getSelection()
    sel?.removeAllRanges()
    sel?.addRange(range)
    ElMessage.success('已全选当前页')
  }
}
function onCanvasMenuEditText() {
  selectTool('textEdit')
  ElMessage.info('已进入编辑模式,点击文字可修改,按 Esc 退出')
}
function onCanvasMenuSelectTool(tool: AnnotationTool) {
  selectTool(tool)
}
async function onCanvasMenuAiTranslate() {
  const sel = window.getSelection()?.toString().trim()
  if (!sel) {
    ElMessage.warning('请先选中要翻译的文字')
    return
  }
  aiVisible.value = true
  await aiFloat.chat.sendUserMessage(`请将以下文字翻译成中文(保持原意,无需解释):\n\n${sel}`)
}
async function onCanvasMenuAiSummarize() {
  aiVisible.value = true
  await aiFloat.chat.sendUserMessage(`请摘要第 ${canvasMenuPage.value} 页的核心内容(100 字以内)。`)
}
function onCanvasMenuAiChat() {
  onOpenAi()
}
function onCanvasMenuOcr(model: 'mobile' | 'server') {
  void onOcrRecognize(model)
}
function exitEditMode() {
  selectTool('select')
}

// ========== 标注鼠标事件 ==========
function onCanvasMouseDown(evt: MouseEvent, pageNum: number, containerRect: DOMRect) {
  // Phase 12.3: 签名放置模式优先
  if (signaturePlacing.value) {
    const x = evt.clientX - containerRect.left
    const y = evt.clientY - containerRect.top
    void placeSignature(pageNum, x, y)
    return
  }
  ;(annot as any).onMouseDown?.(evt, pageNum, containerRect)
}
function onCanvasMouseMove(evt: MouseEvent, pageNum: number, containerRect: DOMRect) {
  ;(annot as any).onMouseMove?.(evt, pageNum, containerRect)
}
function onCanvasMouseUp(evt: MouseEvent, pageNum: number, containerRect: DOMRect) {
  ;(annot as any).onMouseUp?.(evt, pageNum, containerRect)
}
function onCanvasMouseLeave(evt: MouseEvent, pageNum: number, containerRect: DOMRect) {
  ;(annot as any).onMouseLeave?.(evt, pageNum, containerRect)
}

// ========== Phase 3 菜单状态(保留 V2 菜单,Phase 3-4 功能) ==========
const pageMenuOpen = ref(false)
const pageMenuAnchor = ref<{ x: number; y: number } | null>(null)
const exportMenuOpen = ref(false)
const exportMenuAnchor = ref<{ x: number; y: number } | null>(null)
const aiMenuOpen = ref(false)
const aiMenuAnchor = ref<{ x: number; y: number } | null>(null)
const mergeDialogOpen = ref(false)
// Phase 11: 页面操作对话框(插入/裁剪/水印/页眉页脚)
const pageOpsDialogOpen = ref(false)
const pageOpsInitialTab = ref<'insertBlank' | 'crop' | 'watermark' | 'headerFooter'>('insertBlank')
const termsPanelOpen = ref(false)
// 默认折叠缩略图(V3.3 优化:让画布占据更多主空间)
// 缩略图默认展示(V3.6 — user 反馈要默认展开)
const thumbCollapsed = ref(false)
// Phase 11.6: 右侧 ToolsRail 折叠状态(默认展开)
const toolsRailCollapsed = ref(false)
/** Phase 13.9: OCR 叠加层开关(默认 false=识别前原图,true=识别后叠加 OCR 文字) */
const showOcrOverlay = ref(false)
function onToggleOcrOverlay() {
  showOcrOverlay.value = !showOcrOverlay.value
}

// 右键菜单
const ctxMenuOpen = ref(false)
const ctxMenuAnchor = ref<{ x: number; y: number } | null>(null)
const ctxMenuPageNum = ref(1)

function onMergeConfirmed(docIds: number[]) {
  pageOps.merge(docIds).then((r) => {
    if (r) reloadAfterPageOp(pageOps.bustUrl(r))
  })
}

function onOpenAiChatFromMenu() { aiVisible.value = true }
function onOpenTermsFromMenu() { termsPanelOpen.value = true }

// ========== 生命周期 ==========
onMounted(async () => {
  try {
    await renderer.load()
    if (renderer.pdfDoc.value) {
      const page = await renderer.pdfDoc.value.getPage(1)
      const vp = page.getViewport({ scale: 1 })
      pageRawWidth.value = vp.width
      pageRawHeight.value = vp.height
    }
    collab.connect()
    void textEditor.loadAllPositions()
    // Phase 13.4: 自动检查 OCR 识别状态,已识别的文档自动加载 bbox + 文字层
    try {
      const status = await pdfApi.getRecognizeStatus(props.docId)
      if (status.recognized) {
        recognizedPages.value = new Set(Array.from({ length: totalPages.value }, (_, i) => i + 1))
        recognizeStatus.value = 'recognized'
      } else {
        recognizeStatus.value = 'unrecognized'
      }
    } catch {
      recognizeStatus.value = 'unrecognized'
    }
    emit('ready')
  } catch (e) {
    console.error('[PdfEditor] 加载失败:', e)
  }
})

onBeforeUnmount(() => {
  collab.destroy()
  renderer.destroy()
})

let currentFileUrl = props.fileUrl
watch(() => props.fileUrl, async (newUrl) => {
  if (!newUrl || newUrl === currentFileUrl) return
  currentFileUrl = newUrl
  console.log('[PdfEditor] fileUrl changed:', newUrl)
  // 等当前正在进行的 render 完成,再切换
  renderer.destroy()
  await renderer.load()
  canvasRefs.clear()
  textLayerRefs.clear()
  annotationRefs.clear()
  // 重新触发已挂载页面的渲染
  for (const [pageNum, canvasEl] of canvasRefs.entries()) {
    const textLayerEl = textLayerRefs.get(pageNum)
    if (textLayerEl) {
      try {
        await renderer.renderPage(pageNum, canvasEl, textLayerEl)
      } catch (e) {
        console.error('[PdfEditor] re-render failed:', e)
      }
    }
  }
})

// 键盘快捷键
function onKeydown(e: KeyboardEvent) {
  // 只在没有焦点输入框时响应
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return
  if (e.ctrlKey || e.metaKey) return

  // Phase 13.8: 编辑模式下 Esc 退出(优先处理)
  if (e.key === 'Escape' && activeTool.value === 'textEdit') {
    e.preventDefault()
    selectTool('select')
    return
  }

  switch (e.key.toLowerCase()) {
    case 'v': selectTool('select'); break
    case 't': selectTool('textEdit'); break
    case 'h': selectTool('highlight'); break
    case 'c': selectTool('comment'); break
    case 'p': selectTool('draw'); break
    case 'e': selectTool('eraser'); break
    case 'q': selectTool('vqa'); break
    case 'u': selectTool('underline'); break
    case 'c': selectTool('comment'); break
    case 'p': selectTool('draw'); break
    case 'e': selectTool('eraser'); break
    case 'q': selectTool('vqa'); break
    case 'arrowleft': if (e.shiftKey) { goPrev(); } break
    case 'arrowright': if (e.shiftKey) { goNext(); } break
  }
}
onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
/* ==================== V3 主壳布局 ==================== */
.pdf-editor-v3 {
  display: flex;
  flex-direction: column;
  /* Phase 11.5 修复:作为 DocEditor .editor-body 的 flex 子项,需要 flex:1 撑满宽度
     原来没设 flex/width,默认 flex:0 1 auto 导致右侧大量空白 */
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  height: calc(100vh - 40px);
  position: relative;
  background: var(--color-background);
  color: var(--color-foreground);
  font-family: var(--font-sans);
  font-size: var(--text-base);
  overflow: hidden;
}

.pdf-editor-body {
  display: grid;
  grid-template-columns: auto 1fr auto 56px;  /* Phase 11.5 Q4: 第 4 列固定 ToolsRail */
  flex: 1;
  min-height: 0;
  max-height: 100%;
  position: relative;
  /* 创建独立 stacking context,确保子项 z-index 生效 */
  isolation: isolate;
  z-index: 0;
  overflow: hidden;
}

/* ==================== 中央画布 ==================== */
.pdf-canvas-area {
  background: linear-gradient(180deg, #E8EEF5 0%, #E2E8F0 100%);
  overflow-y: auto;
  overflow-x: auto;  /* Phase 11.5 Q2: 超宽画布水平滚动,不裁切 */
  padding: var(--space-8) var(--space-12);  /* Q2: 上下对称 32px,去底部 64px 浪费 */
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  scroll-behavior: smooth;
  position: relative;
  /* Phase 11.5 Q2: 不用 contain:strict(会让子元素 canvas 尺寸为 0)
     改用 overscroll-behavior 防滚动穿透即可 */
  overscroll-behavior: contain;
}

/* 双页对照 */
.pdf-facing-pair {
  display: grid;
  grid-template-columns: 1fr var(--space-6) 1fr;
  gap: 0;
  align-items: start;
  justify-content: center;
  width: 100%;
}

.pdf-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  height: 100%;
  color: var(--color-foreground-3);
  font-size: var(--text-md);
}
.pdf-state-error { color: var(--color-destructive); }
.pdf-state-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: pdf-spin 0.8s linear infinite;
}
@keyframes pdf-spin { to { transform: rotate(360deg); } }

/* Phase 11.5 Q1: 画布骨架占位 */
.pdf-canvas-skeleton {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E8EEF5 0%, #DCE3EC 100%);
  padding: var(--space-6) var(--space-10);
}
.pdf-canvas-skeleton-card {
  width: 620px;
  max-width: 90%;
  height: min(80vh, 820px);
  max-height: 90%;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
  padding: var(--space-8) var(--space-10);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  position: relative;
  overflow: hidden;
}
.pdf-canvas-skeleton-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  margin-bottom: var(--space-2);
}
.pdf-canvas-skeleton-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-height: 0;
}
.pdf-canvas-skeleton-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  margin-top: var(--space-2);
}
.pdf-canvas-skeleton-line {
  height: 14px;
  border-radius: var(--radius-sm);
  background: linear-gradient(110deg, var(--color-surface-2) 8%, var(--color-surface-3) 18%, var(--color-surface-2) 33%);
  background-size: 200% 100%;
  animation: pdf-canvas-shimmer 1.6s ease-in-out infinite;
}
.pdf-canvas-skeleton-line.w-30 { width: 30%; }
.pdf-canvas-skeleton-line.w-50 { width: 50%; }
.pdf-canvas-skeleton-line.w-60 { width: 60%; }
.pdf-canvas-skeleton-line.w-65 { width: 65%; }
.pdf-canvas-skeleton-line.w-70 { width: 70%; }
.pdf-canvas-skeleton-line.w-75 { width: 75%; }
.pdf-canvas-skeleton-line.w-80 { width: 80%; }
.pdf-canvas-skeleton-line.w-88 { width: 88%; }
.pdf-canvas-skeleton-line.w-90 { width: 90%; }
.pdf-canvas-skeleton-line.w-92 { width: 92%; }
.pdf-canvas-skeleton-line.w-95 { width: 95%; }
.pdf-canvas-skeleton-line.w-15 { width: 15%; }
.pdf-canvas-skeleton-line.h-8 { height: 8px; }
.pdf-canvas-skeleton-line.h-12 { height: 12px; }
.pdf-canvas-skeleton-line.h-20 { height: 20px; }
.pdf-canvas-skeleton-chart {
  height: 80px;
  margin: var(--space-2) var(--space-8);
  border-radius: var(--radius-md);
  background: linear-gradient(110deg, var(--color-surface-2) 8%, var(--color-surface-3) 18%, var(--color-surface-2) 33%);
  background-size: 200% 100%;
  animation: pdf-canvas-shimmer 1.6s ease-in-out infinite;
}
.pdf-canvas-skeleton-text {
  color: var(--color-foreground-3);
  font-size: var(--text-sm);
}
@keyframes pdf-canvas-shimmer {
  to { background-position-x: -200%; }
}

/* ==================== 右侧任务面板 ==================== */
.pdf-right-panel {
  width: var(--right-panel-width);
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-4);
  z-index: var(--z-right-panel);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pdf-right-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  height: var(--topbar-height);
  background: var(--color-surface-2);
  flex-shrink: 0;
}

.pdf-right-panel-header h3 {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: var(--font-weight-semibold);
}

.pdf-right-panel-close {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-3);
  transition: background var(--duration-fast) var(--ease-out);
}
.pdf-right-panel-close:hover {
  background: var(--color-surface);
  color: var(--color-foreground);
}

.pdf-right-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
}

.pdf-panel-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6) var(--space-3);
  text-align: center;
  color: var(--color-foreground-3);
  font-size: var(--text-sm);
}

.pdf-panel-hint {
  font-size: var(--text-xs);
  color: var(--color-foreground-4);
}

.pdf-search-input {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  background: var(--color-surface);
}

/* ==================== 状态条 V3.2(Adobe 风格 3 段) ==================== */
.pdf-statusbar {
  height: var(--statusbar-height, 30px);
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 var(--space-3);
  font-size: 11px;
  color: var(--color-foreground-2);
  font-feature-settings: 'tnum';
  flex-shrink: 0;
  gap: var(--space-2);
}

.pdf-sb-group {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.pdf-sb-left { justify-self: start; }
.pdf-sb-center { justify-self: center; gap: var(--space-1); }
.pdf-sb-right { justify-self: end; }

.pdf-sb-filename {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-foreground-3);
  font-size: 11px;
}

.pdf-sb-divider {
  width: 1px;
  height: 12px;
  background: var(--color-border);
  flex-shrink: 0;
}

.pdf-sb-page-nav {
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: var(--color-foreground-2);
  border-radius: 3px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: background 120ms ease;
}
.pdf-sb-page-nav:hover:not(:disabled) {
  background: var(--color-surface-2);
  color: var(--color-foreground);
}
.pdf-sb-page-nav:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.pdf-sb-page-info {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  min-width: 48px;
  justify-content: center;
}
.pdf-sb-page-current {
  color: var(--color-foreground);
  font-weight: 600;
}
.pdf-sb-page-sep {
  color: var(--color-foreground-4);
}

.pdf-sb-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-surface-2);
  color: var(--color-foreground-2);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}
.pdf-sb-chip .ico {
  color: var(--color-foreground-3);
  flex-shrink: 0;
}

.pdf-sb-online {
  background: var(--color-success-soft);
  color: var(--color-success);
}
.pdf-sb-online-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 0 2px rgba(22, 163, 74, 0.2);
}

.pdf-sb-ocr-recognized {
  background: var(--color-success-soft);
  color: var(--color-success);
}
.pdf-sb-ocr-recognizing {
  background: var(--color-info-soft);
  color: var(--color-info);
}
.pdf-sb-ocr-unrecognized {
  background: var(--color-surface-2);
  color: var(--color-foreground-3);
}
.pdf-sb-ocr-error {
  background: var(--color-destructive-soft);
  color: var(--color-destructive);
}

.pdf-sb-save {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
}
.pdf-sb-saving { color: var(--color-info); }
.pdf-sb-saved { color: var(--color-success); }
.pdf-sb-error { color: var(--color-destructive); }
.pdf-sb-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.pdf-sb-save-spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: pdf-sb-spin 0.8s linear infinite;
}
@keyframes pdf-sb-spin {
  to { transform: rotate(360deg); }
}

.pdf-sb-zoom-btn {
  width: 22px;
  height: 22px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-foreground-2);
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: all 120ms ease;
  font-weight: 500;
}
.pdf-sb-zoom-btn:hover {
  background: var(--color-surface-2);
  border-color: var(--color-border-strong);
  color: var(--color-foreground);
}
.pdf-sb-zoom-btn:active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.pdf-sb-zoom-display {
  min-width: 52px;
  text-align: center;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--color-foreground);
  font-weight: 500;
  padding: 0 4px;
  cursor: pointer;
}
.pdf-sb-zoom-display:hover {
  color: var(--color-primary);
  text-decoration: underline;
}
.pdf-sb-zoom-slider {
  width: 80px;
  height: 4px;
  margin: 0 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--color-border);
  border-radius: 999px;
  outline: none;
  cursor: pointer;
}
.pdf-sb-zoom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  border-radius: 50%;
  cursor: grab;
  border: 2px solid var(--color-surface);
  box-shadow: var(--shadow-2);
}
.pdf-sb-zoom-slider::-webkit-slider-thumb:active {
  cursor: grabbing;
  background: var(--color-primary-hover);
  transform: scale(1.1);
}
.pdf-sb-zoom-slider:hover {
  background: var(--color-border-strong);
}
.pdf-sb-zoom-fit {
  margin-left: 2px;
  font-size: 13px;
}

.ico {
  stroke: currentColor;
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ==================== V2 保留:合同条款抽屉 ==================== */
.pdf-terms-drawer {
  position: fixed;
  top: calc(var(--topbar-height) + 96px);
  right: 0;
  bottom: var(--statusbar-height);
  width: var(--right-panel-width);
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-8);
  z-index: var(--z-overlay);
  display: flex;
  flex-direction: column;
}
.pdf-terms-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}
.pdf-terms-drawer-header h3 { margin: 0; font-size: var(--text-md); font-weight: 600; }
.pdf-terms-drawer-close {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-3);
}
.pdf-terms-drawer-close:hover { background: var(--color-surface-2); }
.pdf-terms-drawer-body { flex: 1; overflow-y: auto; padding: var(--space-4); }

/* ==================== Transitions ==================== */
.pdf-panel-fade-enter-active,
.pdf-panel-fade-leave-active {
  transition: transform var(--duration-slow) var(--ease-out),
    opacity var(--duration-slow) var(--ease-out);
}
.pdf-panel-fade-enter-from,
.pdf-panel-fade-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.pdf-drawer-fade-enter-active,
.pdf-drawer-fade-leave-active {
  transition: transform var(--duration-slow) var(--ease-out);
}
.pdf-drawer-fade-enter-from,
.pdf-drawer-fade-leave-to {
  transform: translateX(100%);
}

/* Phase 13.8: 编辑模式提示条(Acrobat DC 风格 - 蓝色横幅) */
.pdf-edit-banner {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--z-toolbar, 50);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: var(--color-primary);
  color: #fff;
  border-radius: 0 0 8px 8px;
  box-shadow: var(--shadow-4);
  font-size: var(--text-sm);
  font-weight: 500;
}
.pdf-edit-banner-icon {
  font-size: 16px;
}
.pdf-edit-banner-text {
  letter-spacing: 0.2px;
}
.pdf-edit-banner-exit {
  margin-left: 12px;
  padding: 4px 12px;
  background: rgba(255,255,255,0.2);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.4);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: background 150ms ease;
}
.pdf-edit-banner-exit:hover {
  background: rgba(255,255,255,0.35);
}
</style>
