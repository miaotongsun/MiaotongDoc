<!--
  PdfOcrLayer.vue —— Phase 11.4 OCR 可视化层
  在已识别页面渲染 OCR bbox 框(蓝色细线 + 文字)
  - 默认显示:仅蓝色边框
  - hover:边框变粗 + 浅蓝背景
  - 点击:不直接编辑(只展示,提示用户切到文本工具编辑)
-->
<template>
  <div
    v-if="positions.length > 0"
    class="pdf-ocr-layer"
    :class="{
      'is-selectable': selectable,
      'is-text-visible': showText,
    }"
    aria-label="OCR 识别结果"
  >
    <div
      v-for="(tok, idx) in positions"
      :key="`ocr-${idx}`"
      class="pdf-ocr-token"
      :style="tokenStyle(tok)"
      :title="`${tok.text} (置信度 ${Math.round((tok.confidence || 0) * 100)}%)`"
    >
      <span class="pdf-ocr-text">{{ tok.text }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface OcrToken {
  text: string
  x: number       // PDF pt
  y: number
  width: number
  height: number
  confidence?: number
}

const props = defineProps<{
  pageNum: number
  scale: number          // 渲染缩放比(等于 PdfCanvas 的 scale)
  pageRawHeight: number  // PDF 原始高度(用于 Y 翻转)
  tokens: OcrToken[]     // 当前页所有 OCR token
  /** Phase 13.4: 选择工具下允许选中 OCR 文字 */
  selectable?: boolean
  /** Phase 13.9: 是否显示 OCR 文字叠加(默认 false,只显示 bbox 边框) */
  showText?: boolean
}>()

const positions = computed(() => props.tokens)

function tokenStyle(tok: OcrToken) {
  const yCanvas = (props.pageRawHeight - tok.y - tok.height) * props.scale
  const xCanvas = tok.x * props.scale
  const w = tok.width * props.scale
  const h = tok.height * props.scale
  const fs = Math.max(8, tok.height * props.scale * 0.9)
  return {
    position: 'absolute' as const,
    left: `${xCanvas}px`,
    top: `${yCanvas}px`,
    width: `${w}px`,
    height: `${h}px`,
    fontSize: `${fs}px`,
    lineHeight: `${h}px`,
  }
}
</script>

<style scoped>
.pdf-ocr-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}

.pdf-ocr-token {
  position: absolute;
  border: 1px solid var(--color-primary, #3b6fe8);
  background: rgba(59, 111, 232, 0.04);
  border-radius: 2px;
  transition: all 150ms ease;
  overflow: hidden;
  pointer-events: auto;
  cursor: help;
}

.pdf-ocr-token:hover {
  border-width: 1.5px;
  background: rgba(59, 111, 232, 0.12);
  z-index: 3;
  box-shadow: 0 0 0 2px var(--color-primary-soft, #ebf1fe);
}

/* Phase 13.9: 选择工具下 token 文字可选但默认透明(不遮挡原图),hover 时显示蓝色提示 */
.pdf-ocr-layer.is-selectable .pdf-ocr-token {
  cursor: text;
  background: transparent;
  border: 1px solid rgba(59, 111, 232, 0.2);
  pointer-events: auto;
}
.pdf-ocr-layer.is-selectable .pdf-ocr-token:hover {
  background: rgba(59, 111, 232, 0.1);
  border-color: var(--color-primary, #3b6fe8);
  border-width: 1.5px;
  z-index: 3;
}
.pdf-ocr-layer.is-selectable .pdf-ocr-text {
  user-select: text;
  -webkit-user-select: text;
  /* 默认透明,不遮挡原图(文字仍可选中复制) */
  color: transparent;
}
.pdf-ocr-layer.is-selectable .pdf-ocr-token:hover .pdf-ocr-text {
  color: var(--color-foreground-3, #909399);
}

/* Phase 13.12: showText=true 时(用户开"识别后"显示),文字半透明覆盖在原图上,
   showText=false(默认)或纯选择模式下文字透明 */
.pdf-ocr-layer.is-text-visible .pdf-ocr-text {
  color: rgba(20, 30, 50, 0.55);
}
.pdf-ocr-layer.is-text-visible .pdf-ocr-token {
  background: rgba(59, 111, 232, 0.08);
}
.pdf-ocr-layer.is-text-visible.is-selectable .pdf-ocr-text {
  color: rgba(20, 30, 50, 0.55);
}
.pdf-ocr-layer.is-text-visible.is-selectable .pdf-ocr-token:hover .pdf-ocr-text {
  color: var(--color-foreground, #303133);
}

.pdf-ocr-text {
  display: block;
  width: 100%;
  height: 100%;
  font-family: var(--font-sans);
  /* Phase 13.9: 默认透明(只显示 bbox 边框,不遮挡原图) */
  color: transparent;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 4px;
  box-sizing: border-box;
  letter-spacing: 0.2px;
}

/* Phase 13.9: showText=true 时文字半透明可见(识别后内容叠加原图) */
.pdf-ocr-layer.is-text-visible .pdf-ocr-text {
  color: rgba(30, 40, 60, 0.55);
}
.pdf-ocr-layer.is-text-visible .pdf-ocr-token:hover .pdf-ocr-text {
  color: var(--color-primary, #3b6fe8);
}
</style>