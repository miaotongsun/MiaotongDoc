<!--
  PdfIcon.vue —— 内联 SVG 图标库(Lucide 风格,1.5px stroke)

  用法:<PdfIcon name="select" :size="18" />
-->
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="iconPath" />
  </svg>
</template>

<script setup lang="ts">
/**
 * Adobe 风格企业级 SVG 图标(16-24 网格,1.6px stroke,Lucide 风格)
 * 内联 30+ 工具/视图/AI 图标
 */
import { computed } from 'vue'

const ICONS: Record<string, string> = {
  // ===== 编辑工具 =====
  select:    'M3 3l7 17 2-8 8-2L3 3z',
  text:      'M4 7V4h16v3M12 4v16M9 20h6',
  highlight: 'M9 11l-6 6v4h4l6-6m-4-4l5-5 4 4-5 5m-4-4l4 4',
  comment:   'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  draw:      'M12 19l7-7 3 3-7 7-3-3zM18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z',
  eraser:    'M3 21h12M9 3l-6 6 9 9 9-9-9-9z',
  vqa:       'M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2zM12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  // ===== 文件 =====
  save:      'M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2zM17 21v-8H7v8M7 3v5h8',
  print:     'M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2M6 14h12v8H6z',
  share:     'M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13',
  signature: 'M3 17c2-2 5-4 7-2s3 4 5 2 4-5 7-3M3 21h18',
  // ===== 页面操作 =====
  merge:     'M16 2H8a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zM11 7h2M11 11h2M11 15h2',
  extract:   'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M9 13l3 3 3-3',
  rotate:    'M3 12a9 9 0 1 0 9-9M3 5v7h7',
  insert:    'M12 5v14M5 12h14',
  insertFile:'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M12 18v-6M9 15l3 3 3-3',
  watermark: 'M12 2C6 2 2 6 2 12s4 10 10 10 10-4 10-10S18 2 12 2zM8 12l2 2 6-6',
  header:    'M3 6h18M3 12h18M3 18h12',
  // ===== 导出 =====
  export:    'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  // ===== 视图模式 =====
  single:    'M4 4h16v16H4z',
  continuous:'M4 4h16M4 9h16M4 14h16M4 19h16',
  facing:    'M4 4h7v16H4zM13 4h7v16h-7z',
  // ===== 缩放 =====
  zoomIn:    'M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM21 21l-4.3-4.3M11 8v6M8 11h6',
  zoomOut:   'M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM21 21l-4.3-4.3M8 11h6',
  fitWidth:  'M4 4h16v16H4zM9 8v8M15 8v8M7 12h4M13 12h4',
  fitPage:   'M4 4h16v16H4zM7 8v8M17 8v8M9 12h6',
  actual:    'M3 3h18v18H3zM3 9h18M3 15h18M9 3v18M15 3v18',
  // ===== 面板 =====
  panelOutline: 'M4 4h16v3H4zM4 10h10v1H4zM4 14h12v1H4zM4 18h8v1H4z',
  panelSearch:  'M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM21 21l-4.3-4.3',
  panelComment: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2zM8 9h8M8 13h6',
  panelForm:    'M4 5h16v4H4zM4 13h10v6H4zM16 13h4v6h-4z',
  // ===== AI =====
  ai:        'M12 2l2.4 6.6L21 10l-5.4 4.6L17 22l-5-3.5L7 22l1.4-7.4L3 10l6.6-1.4z',
  // ===== 关闭 =====
  close:     'M18 6L6 18M6 6l12 12',
  // ===== 通用 =====
  chevronDown:'M6 9l6 6 6-6',
  menu:      'M4 6h16M4 12h16M4 18h16',
  more:      'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  panel:     'M3 3h18v18H3zM15 3v18',
  rotateAll: 'M3 12a9 9 0 1 0 9-9M3 5v7h7M21 12a9 9 0 0 1-9 9M21 19v-7h-7',
  // ===== Phase 10: 形状工具图标 =====
  rectangle:    'M4 5h16v14H4z',
  ellipse:      'M12 5a8 4 0 1 0 0 14 8 4 0 1 0 0-14',
  arrow:        'M5 19L19 5M11 5h8v8',
  line:         'M5 19L19 5',
  underline:    'M6 4v8a6 6 0 0 0 12 0V4M4 20h16',
  strikethrough:'M4 12h16M6 4h12v6a3 3 0 0 1-3 3M18 20H6v-6a3 3 0 0 1 3-3',
  stamp:        'M5 8h14v12H5zM9 8V5a3 3 0 0 1 6 0v3',
}

const props = withDefaults(defineProps<{
  name: string
  size?: number
}>(), { size: 16 })

const iconPath = computed(() => ICONS[props.name] || ICONS.ai)
</script>