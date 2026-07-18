<!--
  PdfRibbon.vue —— Adobe Ribbon 多标签顶栏(V3.2 严格按 Adobe 规范)

  结构:
    [Tab 栏 28px] - 4 标签,蓝色下划线
    [Toolbar 72px] - 按钮分组,垂直分隔条,大按钮(图 18 + 标签 11)
    [Status Hint 22px] - 当前工具提示(可选)

  关键规范:
    - 标签 13px / 500 weight
    - 工具图标 18x18(1.6 stroke)
    - 工具按钮 60x54(图标 + 标签)
    - 分组间 1px 垂直分隔条
    - hover: surface-2 背景
    - active: primary-soft 背景 + primary 文字
-->
<template>
  <header class="pdf-ribbon" role="toolbar" aria-label="PDF 编辑器工具栏">
    <!-- Tab 栏(28px) -->
    <nav class="pdf-ribbon-tabs" role="tablist" aria-label="主标签">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="pdf-ribbon-tab"
        :class="{ 'is-active': activeTab === tab.id }"
        :aria-selected="activeTab === tab.id"
        role="tab"
        @click="$emit('change-tab', tab.id)"
      >
        <span class="pdf-ribbon-tab-label">{{ tab.label }}</span>
      </button>
    </nav>

    <!-- Toolbar 栏(72px) -->
    <div class="pdf-ribbon-toolbar">
      <!-- Home -->
      <div v-show="activeTab === 'home'" class="pdf-ribbon-row">
        <RibbonGroup label="文件">
          <RibbonBtn icon="save" label="保存" @click="$emit('save')" />
          <RibbonBtn icon="print" label="打印" @click="$emit('print')" />
        </RibbonGroup>
        <RibbonGroup label="分享">
          <RibbonBtn icon="share" label="复制链接" @click="$emit('share')" />
          <RibbonBtn icon="signature" label="发送签署" @click="$emit('send-sign')" />
          <RibbonBtn icon="signature" label="签名" @click="$emit('place-signature')" />
          <RibbonBtn icon="menu" label="保护" @click="$emit('protect')" />
        </RibbonGroup>
        <RibbonGroup label="AI">
          <RibbonBtn icon="ai" label="AI 助手" @click="$emit('open-ai')" />
          <RibbonBtn icon="vqa" label="OCR 快速" @click="$emit('ocr-recognize', 'mobile')" />
          <RibbonBtn icon="vqa" label="OCR 高精度" @click="$emit('ocr-recognize', 'server')" />
        </RibbonGroup>
      </div>

      <!-- Edit -->
      <div v-show="activeTab === 'edit'" class="pdf-ribbon-row">
        <RibbonGroup label="工具">
          <RibbonBtn
            v-for="t in editTools"
            :key="t.id"
            :icon="t.icon"
            :label="t.label"
            :active="activeTool === t.id"
            :shortcut="t.shortcut"
            @click="$emit('select-tool', t.id)"
          />
        </RibbonGroup>
        <RibbonGroup label="形状" v-if="showShapeRow">
          <RibbonBtn
            v-for="t in shapeTools"
            :key="t.id"
            :icon="t.icon"
            :label="t.label"
            :active="activeTool === t.id"
            :shortcut="t.shortcut"
            @click="$emit('select-tool', t.id)"
          />
        </RibbonGroup>
        <RibbonGroup label="图章文字" v-if="activeTool === 'stamp'">
          <select
            class="ribbon-stamp-select"
            :value="effectiveStampText"
            aria-label="图章预设"
            @change="onStampPresetChange($event)"
          >
            <option v-for="p in (props.stampPresets && props.stampPresets.length ? props.stampPresets : stampPresets)" :key="p" :value="p">{{ p }}</option>
            <option value="__custom__">自定义...</option>
          </select>
          <input
            v-if="customMode"
            class="ribbon-stamp-input"
            type="text"
            placeholder="输入图章文字"
            maxlength="16"
            :value="customStampDraft"
            @input="onStampCustomInput($event)"
            @blur="onStampCustomBlur()"
            @keyup.enter="onStampCustomBlur()"
            aria-label="自定义图章文字"
          />
        </RibbonGroup>
        <RibbonGroup label="颜色" v-if="showColorRow">
          <button
            v-for="c in colorPalette"
            :key="c"
            class="ribbon-color-swatch"
            :class="{ 'is-active': activeColor === c }"
            :style="{ background: c }"
            :aria-label="`颜色 ${c}`"
            @click="$emit('select-color', c)"
          />
        </RibbonGroup>
      </div>

      <!-- Page -->
      <div v-show="activeTab === 'page'" class="pdf-ribbon-row">
        <RibbonGroup label="操作">
          <RibbonBtn icon="merge" label="合并 PDF" @click="$emit('page-merge', $event)" />
          <RibbonBtn icon="extract" label="提取选中页" :disabled="!hasSelection" @click="$emit('page-extract', $event)" />
          <RibbonBtn icon="rotateAll" label="旋转全部" @click="$emit('page-rotate-all', $event)" />
        </RibbonGroup>
        <RibbonGroup label="插入">
          <RibbonBtn icon="insert" label="空白页" @click="$emit('page-insert')" />
          <RibbonBtn icon="insertFile" label="从文件插入" @click="$emit('page-insert-from-file')" />
        </RibbonGroup>
        <RibbonGroup label="装饰">
          <RibbonBtn icon="watermark" label="水印" @click="$emit('watermark')" />
          <RibbonBtn icon="header" label="页眉页脚" @click="$emit('header-footer')" />
        </RibbonGroup>
        <RibbonGroup label="导出">
          <RibbonBtn icon="export" label="导出" @click="$emit('export-menu')" />
        </RibbonGroup>
      </div>

      <!-- View -->
      <div v-show="activeTab === 'view'" class="pdf-ribbon-row">
        <RibbonGroup label="页面显示">
          <RibbonBtn
            v-for="m in viewModes"
            :key="m.id"
            :icon="m.icon"
            :label="m.label"
            :active="viewMode === m.id"
            :columns="2"
            @click="$emit('set-view-mode', m.id)"
          />
        </RibbonGroup>
        <RibbonGroup label="缩放">
          <RibbonBtn icon="zoomOut" label="缩小" :columns="2" @click="$emit('zoom-out')" />
          <RibbonBtn icon="zoomIn" label="放大" :columns="2" @click="$emit('zoom-in')" />
          <RibbonBtn icon="fitWidth" label="适合宽度" @click="$emit('fit-width')" />
          <RibbonBtn icon="fitPage" label="适合页面" @click="$emit('fit-page')" />
          <RibbonBtn icon="actual" label="实际大小" @click="$emit('actual-size')" />
        </RibbonGroup>
        <RibbonGroup label="面板">
          <RibbonBtn icon="panelOutline" label="大纲" :columns="2" :active="rightPanel === 'outline'" @click="$emit('toggle-panel', 'outline')" />
          <RibbonBtn icon="panelSearch" label="搜索" :columns="2" :active="rightPanel === 'search'" @click="$emit('toggle-panel', 'search')" />
          <RibbonBtn icon="panelComment" label="批注" :columns="2" :active="rightPanel === 'annotations'" @click="$emit('toggle-panel', 'annotations')" />
        </RibbonGroup>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import RibbonGroup from './RibbonGroup.vue'
import RibbonBtn from './RibbonBtn.vue'

type RibbonTab = 'home' | 'edit' | 'page' | 'view'
type AnnotationTool = 'select' | 'highlight' | 'comment' | 'draw' | 'eraser' | 'vqa' | 'textEdit'
type ViewMode = 'single' | 'continuous' | 'facing'
type RightPanel = 'outline' | 'search' | 'info' | null

const props = defineProps<{
  activeTab: RibbonTab
  activeTool: AnnotationTool
  activeColor: string
  viewMode: ViewMode
  rightPanel: RightPanel
  hasSelection?: boolean
  /** Phase 10.3: 当前图章文字(从父组件双向绑定) */
  stampText?: string
  /** Phase 10.3: 图章预设列表 */
  stampPresets?: readonly string[]
}>()

const emit = defineEmits<{
  (e: 'change-tab', tab: RibbonTab): void
  (e: 'select-tool', tool: AnnotationTool): void
  (e: 'select-color', color: string): void
  (e: 'set-view-mode', mode: ViewMode): void
  (e: 'cycle-view-mode'): void
  (e: 'toggle-panel', panel: 'outline' | 'search' | 'info' | 'annotations'): void
  (e: 'zoom-in' | 'zoom-out' | 'fit-width' | 'fit-page' | 'actual-size'): void
  (e: 'zoom-menu', evt: MouseEvent): void
  (e: 'save' | 'print' | 'share' | 'send-sign' | 'open-ai' | 'place-signature' | 'protect'): void
  (e: 'ocr-recognize', model: 'mobile' | 'server'): void
  (e: 'page-merge' | 'page-extract' | 'page-rotate-all'): void
  (e: 'page-merge' | 'page-extract' | 'page-rotate-all', evt: MouseEvent): void
  (e: 'page-insert' | 'page-insert-from-file'): void
  (e: 'watermark' | 'header-footer' | 'export-menu'): void
  /** Phase 10.3: 用户改了图章文字 */
  (e: 'update:stampText', text: string): void
}>()

const tabs = [
  { id: 'home' as const, label: '开始' },
  { id: 'edit' as const, label: '编辑' },
  { id: 'page' as const, label: '页面' },
  { id: 'view' as const, label: '视图' },
]

const editTools = [
  { id: 'select' as AnnotationTool, label: '选择', icon: 'select', shortcut: 'V' },
  { id: 'textEdit' as AnnotationTool, label: '文本', icon: 'text', shortcut: 'T' },
  { id: 'highlight' as AnnotationTool, label: '高亮', icon: 'highlight', shortcut: 'H' },
  { id: 'comment' as AnnotationTool, label: '评论', icon: 'comment', shortcut: 'C' },
  { id: 'draw' as AnnotationTool, label: '画笔', icon: 'draw', shortcut: 'P' },
  { id: 'eraser' as AnnotationTool, label: '橡皮', icon: 'eraser', shortcut: 'E' },
  { id: 'vqa' as AnnotationTool, label: '识图', icon: 'vqa', shortcut: 'Q' },
]

// Phase 10: 形状工具(图章/矩形/椭圆/箭头/直线/下划线/删除线)
const shapeTools = [
  { id: 'rectangle' as AnnotationTool, label: '矩形', icon: 'rectangle', shortcut: '' },
  { id: 'ellipse' as AnnotationTool, label: '椭圆', icon: 'ellipse', shortcut: '' },
  { id: 'arrow' as AnnotationTool, label: '箭头', icon: 'arrow', shortcut: '' },
  { id: 'line' as AnnotationTool, label: '直线', icon: 'line', shortcut: '' },
  { id: 'underline' as AnnotationTool, label: '下划线', icon: 'underline', shortcut: 'U' },
  { id: 'strikethrough' as AnnotationTool, label: '删除线', icon: 'strikethrough', shortcut: '' },
  { id: 'stamp' as AnnotationTool, label: '图章', icon: 'stamp', shortcut: '' },
]

const stampPresets = ['DRAFT', 'APPROVED', 'REJECTED', 'CONFIDENTIAL', 'FINAL', 'REVIEWED', 'VOID', 'COPY']
const customMode = ref(false)
const customStampDraft = ref('')

// 当前选中的图章文字(优先用 props 注入,否则默认 DRAFT)
const effectiveStampText = computed(() => props.stampText || 'DRAFT')

// 监听 props.stampText 变化:如果在预设列表里,关闭 customMode
watch(() => props.stampText, (text) => {
  if (!text) return
  if ((props.stampPresets && props.stampPresets.length ? props.stampPresets : stampPresets).includes(text)) {
    customMode.value = false
  }
})

function onStampPresetChange(e: Event) {
  const v = (e.target as HTMLSelectElement).value
  if (v === '__custom__') {
    customMode.value = true
    customStampDraft.value = ''
    return
  }
  customMode.value = false
  emit('update:stampText', v)
}
function onStampCustomInput(e: Event) {
  customStampDraft.value = (e.target as HTMLInputElement).value
}
function onStampCustomBlur() {
  const text = customStampDraft.value.trim().toUpperCase()
  if (text) {
    customMode.value = false
    emit('update:stampText', text)
    customStampDraft.value = ''
  }
}

const viewModes = [
  { id: 'single' as ViewMode, label: '单页', icon: 'single' },
  { id: 'continuous' as ViewMode, label: '连续', icon: 'continuous' },
  { id: 'facing' as ViewMode, label: '双页', icon: 'facing' },
]

const colorPalette = ['#FACC15', '#34D399', '#F87171', '#60A5FA', '#A78BFA', '#F472B6']

const showColorRow = computed(() =>
  ['highlight', 'comment', 'draw'].includes(props.activeTool),
)

// 形状组始终显示(用户可点击切换);颜色组仅在选高亮/评论/画笔时显示
const showShapeRow = ref(true)
const showShapeColors = computed(() =>
  ['rectangle', 'ellipse', 'arrow', 'line', 'underline', 'strikethrough', 'stamp'].includes(props.activeTool),
)
</script>

<style scoped>
/* ========== Adobe 风格 Ribbon V3.2 ========== */
.pdf-ribbon {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  /* 关键:确保 z-index 高于缩略图栏 */
  position: relative;
  z-index: var(--z-toolbar, 50);
  flex-shrink: 0;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.04);
  width: 100%;
}

/* ----- Tab 栏(28px) ----- */
.pdf-ribbon-tabs {
  display: flex;
  align-items: stretch;
  height: 28px;
  padding: 0 var(--space-2);
  background: var(--color-surface-2);
  border-bottom: 1px solid var(--color-divider);
  gap: 0;
  position: relative;
}

.pdf-ribbon-tab {
  display: inline-flex;
  align-items: center;
  padding: 0 var(--space-3);
  height: 100%;
  color: var(--color-foreground-2);
  font-size: var(--text-sm, 13px);
  font-weight: 500;
  position: relative;
  transition: color var(--duration-fast) var(--ease-out);
  border: none;
  background: transparent;
  cursor: pointer;
}

.pdf-ribbon-tab:hover {
  color: var(--color-foreground);
  background: transparent;
}

.pdf-ribbon-tab.is-active {
  color: var(--color-primary);
  font-weight: 600;
  background: var(--color-surface);
}

.pdf-ribbon-tab.is-active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 6px;
  right: 6px;
  height: 2px;
  background: var(--color-primary);
  border-radius: 1px 1px 0 0;
  pointer-events: none;
}

/* ----- Toolbar 栏(72px) ----- */
.pdf-ribbon-toolbar {
  height: 72px;
  display: flex;
  align-items: stretch;
  padding: 0 var(--space-2);
  overflow-x: auto;
  overflow-y: hidden;
  background: var(--color-surface);
  flex: 0 0 auto;
}

.pdf-ribbon-row {
  display: flex;
  align-items: stretch;
  gap: 0;
  flex-shrink: 0;
  min-width: 100%;
}

/* ===== Phase 10.3: 图章文字控件 ===== */
.ribbon-stamp-select,
.ribbon-stamp-input {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-foreground);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  outline: none;
  transition: border-color 120ms ease, box-shadow 120ms ease;
}
.ribbon-stamp-select {
  cursor: pointer;
  min-width: 140px;
  text-transform: uppercase;
}
.ribbon-stamp-input {
  min-width: 140px;
  margin-left: 4px;
}
.ribbon-stamp-select:hover,
.ribbon-stamp-input:hover {
  border-color: var(--color-primary);
}
.ribbon-stamp-select:focus,
.ribbon-stamp-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-soft);
}
</style>