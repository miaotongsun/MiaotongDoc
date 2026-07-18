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
    :class="{ 'is-selectable': selectable }"
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

/* Phase 13.4: 选择工具下 token 可选中文字 */
.pdf-ocr-layer.is-selectable .pdf-ocr-token {
  cursor: text;
  background: transparent;
  border: 1px solid rgba(59, 111, 232, 0.25);
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
  color: transparent;
}
.pdf-ocr-layer.is-selectable .pdf-ocr-token:hover .pdf-ocr-text {
  color: var(--color-primary, #3b6fe8);
}

.pdf-ocr-text {
  display: block;
  width: 100%;
  height: 100%;
  font-family: var(--font-sans);
  color: var(--color-primary, #3b6fe8);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 4px;
  box-sizing: border-box;
  letter-spacing: 0.2px;
}
</style>