<!--
  PdfToolsRail.vue -- Phase 11.5 Q4 右侧快捷工具栏
  借鉴 Adobe Acrobat DC 右侧 Tools 面板,做竖排工具图标条

  布局:
    [导出/打印/分享/签署]  <- 顶部固定操作组
    [高亮/评论/画笔/形状/图章]  <- 中部工具切换组(单选 active)
    [AI/批注面板/大纲]  <- 底部面板切换组

  与 Ribbon 区别:
    - Ribbon 是 60×54 大按钮(分组+标签),用户主动切换 tab
    - ToolsRail 是 40×40 小图标(纯图标),常驻右侧,鼠标点一下就触发
-->
<template>
  <aside class="pdf-tools-rail" role="toolbar" aria-label="快捷工具">
    <!-- Phase 11.7: 重设计折叠按钮 - 左侧贴边 + 三角形更精致 + 留 12px 间距 -->
    <button
      class="pdf-tools-rail-toggle"
      :class="{ 'is-collapsed': collapsed }"
      :aria-label="collapsed ? '展开工具栏' : '折叠工具栏'"
      :title="collapsed ? '展开工具栏' : '折叠工具栏'"
      @click="$emit('toggle-collapse')"
    >
      <svg class="rail-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path
          :d="collapsed ? 'M14 6l-6 6 6 6' : 'M10 6l6 6-6 6'"
          stroke="currentColor"
          stroke-width="2.5"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <!-- 顶部:常用操作组 -->
    <div class="pdf-rail-group">
      <button
        v-for="t in topActions"
        :key="t.id"
        class="pdf-rail-btn"
        :class="{ 'is-active': t.active?.() }"
        :aria-label="t.label"
        :title="t.label"
        @click="t.handler"
      >
        <PdfIcon :name="t.icon" :size="18" />
      </button>
    </div>

    <div class="pdf-rail-divider"></div>

    <!-- 中部:工具切换组(单选) -->
    <div class="pdf-rail-group">
      <button
        v-for="t in toolActions"
        :key="t.id"
        class="pdf-rail-btn"
        :class="{ 'is-active': t.active?.() }"
        :aria-label="t.label"
        :title="t.label"
        @click="t.handler"
      >
        <PdfIcon :name="t.icon" :size="18" />
      </button>
    </div>

    <div class="pdf-rail-spacer"></div>

    <!-- 底部:面板切换 + AI -->
    <div class="pdf-rail-group">
      <button
        v-for="t in bottomActions"
        :key="t.id"
        class="pdf-rail-btn"
        :class="{ 'is-active': t.active?.() }"
        :aria-label="t.label"
        :title="t.label"
        @click="t.handler"
      >
        <PdfIcon :name="t.icon" :size="18" />
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import PdfIcon from './PdfIcon.vue'
import type { AnnotationTool } from '@/composables/pdf/usePdfAnnotation'

const props = defineProps<{
  activeTool: AnnotationTool
  rightPanel: 'outline' | 'search' | 'info' | 'annotations' | null
  aiVisible: boolean
  /** Phase 11.6: 工具栏是否折叠(隐藏按钮区,只留 rail) */
  collapsed?: boolean
}>()

const emit = defineEmits<{
  (e: 'select-tool', tool: AnnotationTool): void
  (e: 'export'): void
  (e: 'print'): void
  (e: 'share'): void
  (e: 'send-sign'): void
  (e: 'open-ai'): void
  (e: 'toggle-panel', panel: 'outline' | 'annotations'): void
  (e: 'toggle-collapse'): void
}>()

type RailAction = {
  id: string
  label: string
  icon: string
  handler: () => void
  active?: () => boolean
}

const topActions: RailAction[] = [
  { id: 'export', label: '导出 PDF', icon: 'export', handler: () => emit('export') },
  { id: 'print', label: '打印', icon: 'print', handler: () => emit('print') },
  { id: 'share', label: '分享', icon: 'share', handler: () => emit('share') },
  { id: 'send-sign', label: '发送签署', icon: 'signature', handler: () => emit('send-sign') },
]

const toolActions: RailAction[] = [
  {
    id: 'highlight',
    label: '高亮',
    icon: 'highlight',
    handler: () => emit('select-tool', 'highlight'),
    active: () => props.activeTool === 'highlight',
  },
  {
    id: 'comment',
    label: '评论',
    icon: 'comment',
    handler: () => emit('select-tool', 'comment'),
    active: () => props.activeTool === 'comment',
  },
  {
    id: 'draw',
    label: '画笔',
    icon: 'draw',
    handler: () => emit('select-tool', 'draw'),
    active: () => props.activeTool === 'draw',
  },
  {
    id: 'rectangle',
    label: '矩形',
    icon: 'rectangle',
    handler: () => emit('select-tool', 'rectangle'),
    active: () => props.activeTool === 'rectangle',
  },
  {
    id: 'stamp',
    label: '图章',
    icon: 'stamp',
    handler: () => emit('select-tool', 'stamp'),
    active: () => props.activeTool === 'stamp',
  },
]

const bottomActions: RailAction[] = [
  {
    id: 'ai',
    label: 'AI 助手',
    icon: 'ai',
    handler: () => emit('open-ai'),
    active: () => props.aiVisible,
  },
  {
    id: 'panel-annotations',
    label: '批注面板',
    icon: 'panelComment',
    handler: () => emit('toggle-panel', 'annotations'),
    active: () => props.rightPanel === 'annotations',
  },
  {
    id: 'panel-outline',
    label: '大纲',
    icon: 'panelOutline',
    handler: () => emit('toggle-panel', 'outline'),
    active: () => props.rightPanel === 'outline',
  },
]
</script>

<style scoped>
.pdf-tools-rail {
  width: 56px;
  flex-shrink: 0;
  background: var(--color-surface-2);
  border-left: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-2) 0;
  gap: var(--space-1);
  z-index: var(--z-toolbar);
  height: 100%;
  min-height: 0;
  /* Phase 13.9: overflow 必须全 visible(auto/scroll 会强制另一轴变 auto,裁掉折叠按钮) */
  overflow: visible;
  position: relative;
  transition: width 200ms ease;
}

/* Phase 11.7: 折叠态 - 只显示 rail 条,缩到 14px,蓝色主题 */
.pdf-tools-rail:has(.pdf-tools-rail-toggle.is-collapsed) {
  width: 14px;
  padding: 0;
  background: var(--color-primary);
}

.pdf-tools-rail:has(.pdf-tools-rail-toggle.is-collapsed) .pdf-rail-group,
.pdf-tools-rail:has(.pdf-tools-rail-toggle.is-collapsed) .pdf-rail-divider,
.pdf-tools-rail:has(.pdf-rail-toggle.is-collapsed) .pdf-rail-spacer {
  display: none;
}

/* Phase 13.9: 折叠按钮撑满 rail 全高(top:0 bottom:0 与缩略图对齐) + hover 浅蓝 */
.pdf-tools-rail-toggle {
  position: absolute;
  left: -22px;
  top: 0;
  bottom: 0;
  width: 22px;
  border-radius: 10px 0 0 10px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-right: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-foreground-2);
  z-index: 10;
  transition: width 180ms cubic-bezier(.4,0,.2,1),
              left 180ms ease,
              background 180ms ease,
              color 180ms ease,
              box-shadow 180ms ease;
  padding: 0;
  box-shadow: var(--shadow-2);
}

.pdf-tools-rail-toggle:hover {
  width: 28px;
  left: -28px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-4);
}

.pdf-tools-rail-toggle.is-collapsed {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.pdf-tools-rail-toggle.is-collapsed:hover {
  width: 28px;
  left: -28px;
}

.pdf-tools-rail-toggle .rail-icon {
  display: block;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.1));
}

.pdf-rail-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  width: 100%;
}

.pdf-rail-divider {
  width: 28px;
  height: 1px;
  background: var(--color-divider);
  margin: var(--space-2) 0;
}

.pdf-rail-spacer {
  flex: 1;
  min-height: var(--space-2);
}

.pdf-rail-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-foreground-2);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 120ms ease, color 120ms ease, border-color 120ms ease;
  padding: 0;
}

.pdf-rail-btn:hover {
  background: var(--color-surface);
  color: var(--color-foreground);
  border-color: var(--color-border);
}

.pdf-rail-btn.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary-soft);
}

/* active 时左侧 3px 蓝色高亮条 */
.pdf-rail-btn.is-active::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
}

.pdf-rail-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

/* 滚动条隐藏 */
.pdf-tools-rail::-webkit-scrollbar {
  width: 4px;
}
.pdf-tools-rail::-webkit-scrollbar-thumb {
  background: var(--color-border-strong);
  border-radius: 2px;
}
</style>