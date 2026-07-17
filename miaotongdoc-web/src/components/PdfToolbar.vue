<!--
  PdfToolbar.vue —— PDF 编辑器顶栏(Fluent Mica 风格)

  8 段式布局:
    返回 | 导航 | 缩放 | 工具面板 | 颜色 | 页面 | 导出 | AI

  设计要点:
    - 高 48px,background: var(--color-surface),border-bottom: 1px solid var(--color-divider)
    - 工具按钮组用 surface-2 容器 + 32x32 按钮,激活态用 primary
    - AI 主按钮:蓝紫渐变,hover 上浮 1px
    - 全部按钮 keyboard-accessible,带 aria-label / aria-pressed
-->
<template>
  <header class="pdf-toolbar" role="toolbar" aria-label="PDF 编辑工具栏">
    <!-- 段 1:返回 -->
    <div class="tb-group">
      <button
        class="tb-nav-btn"
        aria-label="返回文档列表"
        data-tooltip="返回 (Esc)"
        @click="$emit('back')"
      >
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
    </div>
    <div class="tb-divider" aria-hidden="true"></div>

    <!-- 段 2:页面导航 -->
    <div class="tb-group">
      <button
        class="tb-nav-btn"
        :disabled="currentPage <= 1"
        aria-label="上一页"
        data-tooltip="上一页 (PgUp)"
        @click="$emit('prev')"
      >
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
      <input
        class="tb-page-input"
        type="number"
        :value="currentPage"
        :min="1"
        :max="totalPages"
        aria-label="页码"
        @change="onPageChange"
      />
      <span class="tb-page-total">/ {{ totalPages }}</span>
      <button
        class="tb-nav-btn"
        :disabled="currentPage >= totalPages"
        aria-label="下一页"
        data-tooltip="下一页 (PgDn)"
        @click="$emit('next')"
      >
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 6l6 6-6 6" />
        </svg>
      </button>
    </div>
    <div class="tb-divider" aria-hidden="true"></div>

    <!-- 段 3:缩放 -->
    <div class="tb-group">
      <button class="tb-nav-btn" aria-label="缩小" data-tooltip="缩小 (-)" @click="$emit('zoom-out')">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 12h14" />
        </svg>
      </button>
      <span class="tb-zoom-display">{{ zoomPercent }}%</span>
      <button class="tb-nav-btn" aria-label="放大" data-tooltip="放大 (+)" @click="$emit('zoom-in')">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
      <button class="tb-nav-btn" aria-label="适合宽度" data-tooltip="适合宽度 (W)" @click="$emit('fit-width')">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 8h18M3 16h18M8 3v18M16 3v18" />
        </svg>
      </button>
    </div>
    <div class="tb-divider" aria-hidden="true"></div>

    <!-- 段 4:工具面板 -->
    <div class="tb-group">
      <div class="tb-tool-palette" role="group" aria-label="编辑工具">
        <button
          v-for="t in tools"
          :key="t.id"
          class="tb-tool-btn"
          :class="{ 'is-active': activeTool === t.id }"
          :aria-label="t.label"
          :aria-pressed="activeTool === t.id"
          :data-tooltip="`${t.label} (${t.shortcut})`"
          @click="$emit('select-tool', t.id)"
        >
          <span v-html="t.icon" class="ico" aria-hidden="true"></span>
        </button>
      </div>
      <!-- 颜色选择(标注工具激活时显示) -->
      <div v-if="showColorPicker" class="tb-color-row" aria-label="颜色选择">
        <button
          v-for="c in activeColors"
          :key="c"
          class="tb-swatch"
          :class="{ 'is-active': activeColor === c }"
          :style="{ background: c }"
          :aria-label="`颜色 ${c}`"
          @click="$emit('select-color', c)"
        ></button>
      </div>
    </div>
    <div class="tb-divider" aria-hidden="true"></div>

    <!-- 段 5:页面操作下拉 -->
    <div class="tb-group">
      <button class="tb-dropdown-trigger" @click="$emit('open-page-menu', $event)">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
        页面
        <svg class="ico tb-caret" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <button class="tb-dropdown-trigger" @click="$emit('open-export-menu', $event)">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" />
        </svg>
        导出
        <svg class="ico tb-caret" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <button class="tb-dropdown-trigger" @click="$emit('open-ai-menu', $event)">
        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 2l2.4 6.6L21 10l-5.4 4.6L17 22l-5-3.5L7 22l1.4-7.4L3 10l6.6-1.4z" />
        </svg>
        AI
        <svg class="ico tb-caret" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
    </div>

    <!-- 用户徽章(可选) -->
    <div v-if="userBadge" class="tb-persona-badge">
      <span class="tb-persona-dot"></span>
      {{ userBadge }}
    </div>

    <!-- 段 7:AI 助手 -->
    <button class="tb-ai-btn" :aria-label="aiStreaming ? 'AI 正在生成' : '打开 AI 助手'" @click="$emit('open-ai')">
      <span v-if="aiStreaming" class="tb-ai-pulse" aria-hidden="true"></span>
      <svg class="ico" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 2l2.4 6.6L21 10l-5.4 4.6L17 22l-5-3.5L7 22l1.4-7.4L3 10l6.6-1.4z" />
      </svg>
      AI 助手
    </button>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AnnotationTool } from '@/composables/pdf/usePdfAnnotation'

interface ToolDef {
  id: AnnotationTool
  label: string
  shortcut: string
  icon: string
}

const TOOL_DEFS: ToolDef[] = [
  {
    id: 'select',
    label: '选择',
    shortcut: 'V',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M5 3l7 17 2-8 8-2z"/></svg>',
  },
  {
    id: 'textEdit',
    label: '文本编辑',
    shortcut: 'T',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M4 7V4h16v3M9 20h6M12 4v16"/></svg>',
  },
  {
    id: 'highlight',
    label: '高亮',
    shortcut: 'H',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M9 11l-6 6v4h4l6-6m-4-4l5-5 4 4-5 5m-4-4l4 4m-4-4h6"/></svg>',
  },
  {
    id: 'comment',
    label: '评论',
    shortcut: 'C',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
  },
  {
    id: 'draw',
    label: '画笔',
    shortcut: 'P',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M12 19l7-7 3 3-7 7-3-3zM18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"/></svg>',
  },
  {
    id: 'eraser',
    label: '橡皮',
    shortcut: 'E',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M3 21h12M9 3l-6 6 9 9 9-9-9-9z"/></svg>',
  },
  {
    id: 'vqa',
    label: '视觉问答',
    shortcut: 'Q',
    icon: '<svg viewBox="0 0 24 24" class="ico"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>',
  },
]

const PALETTE = ['#FACC15', '#34D399', '#F87171', '#60A5FA', '#A78BFA', '#F472B6']

const props = defineProps<{
  currentPage: number
  totalPages: number
  scale: number
  activeTool: AnnotationTool
  activeColor: string
  userBadge?: string
  aiStreaming?: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'goto', page: number): void
  (e: 'zoom-in'): void
  (e: 'zoom-out'): void
  (e: 'fit-width'): void
  (e: 'select-tool', tool: AnnotationTool): void
  (e: 'select-color', color: string): void
  (e: 'open-page-menu', evt: MouseEvent): void
  (e: 'open-export-menu', evt: MouseEvent): void
  (e: 'open-ai-menu', evt: MouseEvent): void
  (e: 'open-ai'): void
}>()

const tools = TOOL_DEFS
const activeColors = PALETTE
const zoomPercent = computed(() => Math.round(props.scale * 100))

// 标注类工具激活时才显示颜色选择
const showColorPicker = computed(() =>
  ['highlight', 'comment', 'draw'].includes(props.activeTool),
)

function onPageChange(e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v) && v >= 1 && v <= props.totalPages) emit('goto', v)
}
</script>

<style scoped>
.pdf-toolbar {
  height: var(--topbar-height);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-divider);
  display: flex;
  align-items: center;
  padding: 0 var(--space-4);
  gap: var(--space-2);
  position: relative;
  z-index: var(--z-toolbar);
  box-shadow: var(--shadow-1);
}

.tb-group {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.tb-divider {
  width: 1px;
  height: 24px;
  background: var(--color-divider);
  margin: 0 var(--space-2);
  flex-shrink: 0;
}

.tb-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 var(--space-2);
  gap: var(--space-1);
  border-radius: var(--radius-sm);
  color: var(--color-foreground-2);
  transition: background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
  font-size: var(--text-sm);
  font-weight: 500;
  position: relative;
}
.tb-nav-btn:hover:not(:disabled) {
  background: var(--color-surface-2);
  color: var(--color-foreground);
}
.tb-nav-btn:active:not(:disabled) {
  background: var(--color-border);
}
.tb-nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.tb-nav-btn .ico {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.tb-page-input {
  width: 42px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  text-align: center;
  font: inherit;
  font-variant-numeric: tabular-nums;
  font-size: var(--text-sm);
  background: var(--color-surface);
  color: var(--color-foreground);
  padding: 0;
}
.tb-page-input::-webkit-outer-spin-button,
.tb-page-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.tb-page-input:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 2px var(--color-primary-soft);
}
.tb-page-total {
  font-size: var(--text-sm);
  color: var(--color-foreground-3);
  font-variant-numeric: tabular-nums;
  user-select: none;
}

.tb-zoom-display {
  min-width: 52px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-size: var(--text-sm);
  color: var(--color-foreground-2);
  user-select: none;
}

.tb-tool-palette {
  display: inline-flex;
  background: var(--color-surface-2);
  padding: 2px;
  border-radius: var(--radius-md);
  gap: 2px;
}
.tb-tool-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  color: var(--color-foreground-2);
  transition: background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
  position: relative;
}
.tb-tool-btn:hover {
  background: var(--color-surface);
  color: var(--color-foreground);
}
.tb-tool-btn.is-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: var(--shadow-1);
}
.tb-tool-btn .ico {
  width: 16px;
  height: 16px;
}

/* Tooltip on hover (data-tooltip) */
[data-tooltip]:hover::after {
  content: attr(data-tooltip);
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--color-foreground);
  color: var(--color-on-primary);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  white-space: nowrap;
  pointer-events: none;
  z-index: var(--z-toast);
}

.tb-color-row {
  display: inline-flex;
  gap: 4px;
  padding: 0 var(--space-2);
}
.tb-swatch {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-out);
}
.tb-swatch:hover {
  transform: scale(1.15);
}
.tb-swatch.is-active {
  border-color: var(--color-foreground);
}

.tb-dropdown-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-3);
  height: 32px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-foreground-2);
  transition: background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}
.tb-dropdown-trigger:hover {
  background: var(--color-surface-2);
  color: var(--color-foreground);
}
.tb-dropdown-trigger .ico {
  width: 14px;
  height: 14px;
}
.tb-caret {
  opacity: 0.7;
}

.tb-persona-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
  margin-left: auto;
}
.tb-persona-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
}

.tb-ai-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
  height: 32px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--color-primary) 0%, #4F46E5 100%);
  color: var(--color-on-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  box-shadow: var(--shadow-1);
  transition: transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
  position: relative;
}
.tb-ai-btn:hover {
  box-shadow: var(--shadow-2);
  transform: translateY(-1px);
}
.tb-ai-btn:active {
  transform: translateY(0);
}
.tb-ai-btn .ico {
  width: 16px;
  height: 16px;
}
.tb-ai-pulse {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  animation: ai-pulse 1.6s ease-out infinite;
}
@keyframes ai-pulse {
  0%, 100% { opacity: 0.5; transform: translateY(-50%) scale(1); }
  50% { opacity: 1; transform: translateY(-50%) scale(1.4); }
}
</style>
