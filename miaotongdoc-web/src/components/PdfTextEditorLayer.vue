<!--
  PdfTextEditorLayer.vue —— 原生 PDF 文本编辑层

  工作原理:
    - 接收 usePdfTextEditor 提供的 positions(每个字符的 x/y/width/height)
    - 把 PDF 坐标系转换成 canvas 坐标系(乘 scale)
    - 为每个字符生成绝对定位的 contenteditable div
    - 用户 blur 时调 applyEdit(),由 composable 防抖提交

  视觉:
    - 默认:透明,不可见
    - hover:虚线 primary 边框 + soft 背景
    - editing(待保存):warning 边框 + warning-soft 背景
    - saved:success 边框 1.5s 后淡出

  性能:
    - 单页可能 100-500 token,使用 v-for + ref Map 避免全量重渲染
    - positions 按页缓存,切换页时才重新加载
-->
<template>
  <div class="pdf-text-edit-layer" @click.stop>
    <div
      v-for="(token, idx) in positions"
      :key="tokenKey(token, idx)"
      class="pdf-edit-token"
      :class="{
        'is-hover': hoveredKey === tokenKey(token, idx),
        'is-editing': editingKey === tokenKey(token, idx),
        'is-saving': savingKey === tokenKey(token, idx),
        'is-saved': savedKey === tokenKey(token, idx),
      }"
      :style="tokenStyle(token)"
      :contenteditable="canEdit"
      spellcheck="false"
      :data-token-idx="idx"
      :data-page-num="pageNum"
      @mouseenter="hoveredKey = tokenKey(token, idx)"
      @mouseleave="hoveredKey = null"
      @focus="onFocus(token, idx)"
      @blur="onBlur($event, token, idx)"
      @keydown.enter.prevent="onEnter($event, token, idx)"
      @keydown.esc.prevent="onCancel($event, token, idx)"
    >{{ displayText(token) }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { PdfTextPosition } from '@/api/pdf'
import { usePdfTextEditor } from '@/composables/pdf/usePdfTextEditor'

const props = defineProps<{
  /** 当前页码 */
  pageNum: number
  /** 缩放比例(与 canvas 一致) */
  scale: number
  /** 页面原始高度(用于 Y 翻转,PDF Y 轴向上为正) */
  pageRawHeight: number
  /** 是否可编辑 */
  canEdit: boolean
  /** composable 实例(由父组件传入,避免重复创建) */
  editor: ReturnType<typeof usePdfTextEditor>
}>()

const positions = computed<PdfTextPosition[]>(() => {
  return props.editor.positionsByPage.value.get(props.pageNum) || []
})

const hoveredKey = ref<string | null>(null)
const editingKey = ref<string | null>(null)
const savingKey = ref<string | null>(null)
const savedKey = ref<string | null>(null)

// ====== Token 唯一 key(避免 x/y/text 都相同时冲突) ======
function tokenKey(token: PdfTextPosition, idx: number): string {
  return `${props.pageNum}-${idx}-${Math.round(token.x)}-${Math.round(token.y)}`
}

// ====== 显示文字(可能已被 pending 编辑修改) ======
function displayText(token: PdfTextPosition): string {
  // 检查 pendingByPage 是否有针对此 token 的未保存编辑
  const pendings = props.editor.pendingByPage.value.get(props.pageNum) || []
  for (const p of pendings) {
    if (
      p.originalX === token.x &&
      p.originalY === token.y &&
      p.originalText === token.text
    ) {
      return p.text
    }
  }
  return token.text
}

// ====== Token 样式(坐标系转换) ======
function tokenStyle(token: PdfTextPosition) {
  // PDF 坐标 Y 轴向上为正,Canvas 坐标 Y 轴向下为正 — 需要翻转
  const yCanvas = (props.pageRawHeight - token.y - token.height) * props.scale
  const xCanvas = token.x * props.scale
  const w = token.width * props.scale
  const h = token.height * props.scale
  const fs = (token.fontSize || 12) * props.scale

  return {
    position: 'absolute' as const,
    left: `${xCanvas}px`,
    top: `${yCanvas}px`,
    minWidth: `${Math.max(w, 4)}px`,
    height: `${h}px`,
    fontSize: `${fs}px`,
    lineHeight: `${h}px`,
  }
}

// ====== 编辑事件 ======
function onFocus(token: PdfTextPosition, idx: number) {
  editingKey.value = tokenKey(token, idx)
}

function onBlur(evt: FocusEvent, token: PdfTextPosition, idx: number) {
  const el = evt.target as HTMLElement
  const newText = (el.textContent || '').trim()
  const k = tokenKey(token, idx)

  editingKey.value = null

  // 没变化则跳过
  if (newText === token.text) return
  // 空内容 → 视为取消
  if (!newText) {
    el.textContent = token.text
    return
  }

  // 应用编辑
  savingKey.value = k
  const edit = props.editor.applyEdit({
    position: token,
    newText,
  })

  // 监听此 edit 的状态变化(简单轮询)
  const watchState = setInterval(() => {
    const s = props.editor.getState(edit.id)
    if (s === 'saved') {
      savedKey.value = k
      savingKey.value = null
      setTimeout(() => {
        if (savedKey.value === k) savedKey.value = null
      }, 1500)
      clearInterval(watchState)
    } else if (s === 'error') {
      savingKey.value = null
      // 回滚显示
      el.textContent = token.text
      clearInterval(watchState)
    }
  }, 80)

  // 5s 后强制停止轮询(防卡死)
  setTimeout(() => clearInterval(watchState), 5000)
}

function onEnter(evt: KeyboardEvent, _token: PdfTextPosition, idx: number) {
  // Enter 触发 blur(等同于提交)
  ;(evt.target as HTMLElement).blur()
  void idx
}

function onCancel(evt: KeyboardEvent, token: PdfTextPosition, idx: number) {
  // Esc 回滚
  const el = evt.target as HTMLElement
  el.textContent = token.text
  el.blur()
  editingKey.value = null
  hoveredKey.value = null
  void idx
}

// ====== 监听 props.editor.positionsByPage 变化 ======
watch(
  () => positions.value.length,
  (newLen, oldLen) => {
    if (newLen > 0 && newLen !== oldLen) {
      // positions 已加载
    }
  },
)

onMounted(() => {
  // 父组件在挂载前会预加载,这里兜底
  if (positions.value.length === 0) {
    void props.editor.loadPositions(props.pageNum)
  }
})

// 切换页时重新加载
watch(
  () => props.pageNum,
  async (newPage) => {
    if (newPage > 0 && !props.editor.positionsByPage.value.has(newPage)) {
      await props.editor.loadPositions(newPage)
    }
  },
)
</script>

<style scoped>
.pdf-text-edit-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.pdf-edit-token {
  pointer-events: auto;
  border: 1px dashed var(--color-primary);
  border-radius: 2px;
  padding: 0 2px;
  margin: 0;
  display: inline-block;
  font-family: var(--font-sans);
  color: var(--color-foreground);
  background: rgba(64, 158, 255, 0.08);
  cursor: text;
  outline: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  user-select: text;
  transition: border-color var(--duration-fast) var(--ease-out),
    background var(--duration-fast) var(--ease-out);
}

.pdf-edit-token:hover,
.pdf-edit-token.is-hover {
  border-style: solid;
  border-color: var(--color-primary);
  background: rgba(64, 158, 255, 0.18);
  z-index: 1;
}

.pdf-edit-token:focus,
.pdf-edit-token.is-editing {
  border-style: solid;
  border-color: var(--color-primary);
  border-width: 1.5px;
  background: #fff;
  z-index: 2;
  white-space: normal;
  overflow: visible;
  width: auto;
  min-width: 60px;
  box-shadow: 0 0 0 2px var(--color-primary-soft), var(--shadow-2);
}

.pdf-edit-token.is-saving {
  border-style: solid;
  border-color: var(--color-info);
  background: var(--color-primary-soft);
}

.pdf-edit-token.is-saved {
  border-style: solid;
  border-color: var(--color-success);
  background: var(--color-success-soft);
  animation: pdf-saved-flash 1.5s var(--ease-out);
}

@keyframes pdf-saved-flash {
  0% { background: var(--color-success-soft); }
  100% { background: transparent; }
}
</style>
