<!--
  PdfStampPickerDialog.vue —— 图章库(仿 Acrobat)
  Phase 13.33
  - 8 个预设图章(DRAFT/APPROVED/REJECTED/CONFIDENTIAL/FINAL/REVIEWED/VOID/COPY)
    用 canvas 预渲染为 PNG(base64),文字 + 矩形外框
  - 自定义:用户上传 PNG/JPG(< 2MB)
  - 选完后 emit('selected', { imageBase64, label, origW, origH })
-->
<template>
  <el-dialog
    :model-value="open"
    title="图章库"
    width="640px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => v || $emit('close')"
  >
    <div class="pdf-stamp-library">
      <div class="pdf-stamp-section-title">预设图章</div>
      <div class="pdf-stamp-grid">
        <button
          v-for="stamp in presets"
          :key="stamp.label"
          class="pdf-stamp-cell"
          :class="{ 'is-active': selectedLabel === stamp.label }"
          :title="stamp.label"
          @click="onPickPreset(stamp)"
        >
          <img :src="stamp.imageBase64" :alt="stamp.label" />
          <div class="pdf-stamp-cell-label">{{ stamp.label }}</div>
        </button>
      </div>

      <el-divider />

      <div class="pdf-stamp-section-title">自定义图章</div>
      <div class="pdf-stamp-custom">
        <input
          ref="fileInputRef"
          class="pdf-stamp-file"
          type="file"
          accept="image/png,image/jpeg"
          @change="onPickFile"
        />
        <div v-if="customPreview" class="pdf-stamp-custom-preview">
          <img :src="customPreview" alt="自定义图章预览" />
          <el-button size="small" @click="onClearCustom">清除</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button
        type="primary"
        :disabled="!pendingSelection"
        @click="onConfirm"
      >
        应用图章
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface PresetStamp {
  label: string
  imageBase64: string
  origW: number
  origH: number
}

interface SelectedStamp {
  imageBase64: string
  label: string
  origW: number
  origH: number
}

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'selected', payload: SelectedStamp): void
}>()

// ===== 预设图章生成 =====
const PRESET_LABELS = ['DRAFT', 'APPROVED', 'REJECTED', 'CONFIDENTIAL', 'FINAL', 'REVIEWED', 'VOID', 'COPY']
const PRESET_COLORS: Record<string, string> = {
  DRAFT: '#9E9E9E',
  APPROVED: '#4CAF50',
  REJECTED: '#F44336',
  CONFIDENTIAL: '#E91E63',
  FINAL: '#3F51B5',
  REVIEWED: '#FF9800',
  VOID: '#795548',
  COPY: '#00BCD4',
}
const STAMP_IMG_W = 240
const STAMP_IMG_H = 80

function renderPresetBase64(label: string): string {
  const canvas = document.createElement('canvas')
  canvas.width = STAMP_IMG_W
  canvas.height = STAMP_IMG_H
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(0,0,0,0)'
  ctx.fillRect(0, 0, STAMP_IMG_W, STAMP_IMG_H)
  ctx.strokeStyle = PRESET_COLORS[label] || '#F44336'
  ctx.lineWidth = 4
  ctx.strokeRect(4, 4, STAMP_IMG_W - 8, STAMP_IMG_H - 8)
  ctx.fillStyle = PRESET_COLORS[label] || '#F44336'
  ctx.font = 'bold 28px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(label, STAMP_IMG_W / 2, STAMP_IMG_H / 2)
  return canvas.toDataURL('image/png')
}

const presets = ref<PresetStamp[]>([])
function rebuildPresets() {
  presets.value = PRESET_LABELS.map((label) => ({
    label,
    imageBase64: renderPresetBase64(label),
    origW: STAMP_IMG_W,
    origH: STAMP_IMG_H,
  }))
}
rebuildPresets()

// ===== 自定义上传 =====
const fileInputRef = ref<HTMLInputElement | null>(null)
const customPreview = ref<string | null>(null)
const customOrigW = ref(0)
const customOrigH = ref(0)

function onPickFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    alert('文件大小不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const dataUrl = reader.result as string
    const img = new Image()
    img.onload = () => {
      customOrigW.value = img.naturalWidth
      customOrigH.value = img.naturalHeight
      customPreview.value = dataUrl
    }
    img.src = dataUrl
  }
  reader.readAsDataURL(file)
}

function onClearCustom() {
  customPreview.value = null
  customOrigW.value = 0
  customOrigH.value = 0
  if (fileInputRef.value) fileInputRef.value.value = ''
}

// ===== 选中状态 =====
const selectedLabel = ref<string | null>(null)
const pendingSelection = ref<SelectedStamp | null>(null)

function onPickPreset(stamp: PresetStamp) {
  selectedLabel.value = stamp.label
  pendingSelection.value = {
    imageBase64: stamp.imageBase64,
    label: stamp.label,
    origW: stamp.origW,
    origH: stamp.origH,
  }
}

watch(customPreview, (v) => {
  if (v && customOrigW.value && customOrigH.value) {
    selectedLabel.value = '__custom__'
    pendingSelection.value = {
      imageBase64: v,
      label: 'CUSTOM',
      origW: customOrigW.value,
      origH: customOrigH.value,
    }
  }
})

function onConfirm() {
  if (pendingSelection.value) {
    emit('selected', pendingSelection.value)
  }
  emit('close')
}

watch(() => props.open, (v) => {
  if (!v) {
    // 关闭时重置
    onClearCustom()
    selectedLabel.value = null
    pendingSelection.value = null
  }
})
</script>

<style scoped>
.pdf-stamp-library {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pdf-stamp-section-title {
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}
.pdf-stamp-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.pdf-stamp-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: all 0.15s;
}
.pdf-stamp-cell:hover {
  border-color: var(--el-color-primary);
  transform: translateY(-1px);
}
.pdf-stamp-cell.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.pdf-stamp-cell img {
  width: 100%;
  max-height: 60px;
  object-fit: contain;
}
.pdf-stamp-cell-label {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.pdf-stamp-custom {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pdf-stamp-file {
  font-size: 13px;
}
.pdf-stamp-custom-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}
.pdf-stamp-custom-preview img {
  max-height: 60px;
  max-width: 200px;
  object-fit: contain;
}
</style>