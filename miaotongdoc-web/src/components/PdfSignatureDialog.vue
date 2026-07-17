<!--
  PdfSignatureDialog.vue -- Phase 12.3 签名创建对话框

  3 种创建方式:
    - 键入:输入中文/英文名,选字体,生成签名图片
    - 绘制:canvas 手写签名板
    - 上传:本地上传图片

  确认后,emit('created', { imageBase64, width, height })
  由父组件 PdfEditor 处理拖拽位置 + 调用后端 embedSignature
-->
<template>
  <Teleport to="body">
    <Transition name="pdf-sig-fade">
      <div v-if="open" class="pdf-sig-overlay" @click.self="$emit('close')">
        <div class="pdf-sig-dialog" role="dialog" aria-label="创建签名">
          <header class="pdf-sig-header">
            <h3>创建签名</h3>
            <button class="pdf-sig-close" aria-label="关闭" @click="$emit('close')">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </header>

          <div class="pdf-sig-tabs" role="tablist">
            <button
              v-for="t in tabs"
              :key="t.id"
              class="pdf-sig-tab"
              :class="{ 'is-active': activeTab === t.id }"
              role="tab"
              @click="activeTab = t.id"
            >
              {{ t.label }}
            </button>
          </div>

          <div class="pdf-sig-body">
            <!-- 键入 -->
            <div v-show="activeTab === 'type'" class="pdf-sig-pane">
              <input
                v-model="typeName"
                type="text"
                class="pdf-sig-type-input"
                placeholder="请输入您的姓名"
                maxlength="20"
              />
              <div class="pdf-sig-type-fonts">
                <button
                  v-for="f in fonts"
                  :key="f.id"
                  class="pdf-sig-font-btn"
                  :class="{ 'is-active': font === f.id }"
                  :style="{ fontFamily: f.css }"
                  @click="font = f.id"
                >
                  {{ typeName || '示例签名' }}
                </button>
              </div>
              <canvas ref="typeCanvasRef" class="pdf-sig-preview-canvas" width="400" height="160"></canvas>
            </div>

            <!-- 绘制 -->
            <div v-show="activeTab === 'draw'" class="pdf-sig-pane">
              <canvas
                ref="drawCanvasRef"
                class="pdf-sig-draw-canvas"
                width="400"
                height="200"
                @mousedown="onDrawStart"
                @mousemove="onDrawMove"
                @mouseup="onDrawEnd"
                @mouseleave="onDrawEnd"
                @touchstart.prevent="onTouchStart"
                @touchmove.prevent="onTouchMove"
                @touchend.prevent="onDrawEnd"
              ></canvas>
              <div class="pdf-sig-draw-toolbar">
                <label class="pdf-sig-color-picker">
                  颜色:
                  <input v-model="drawColor" type="color" />
                </label>
                <label class="pdf-sig-width-picker">
                  粗细:
                  <input v-model.number="drawWidth" type="range" min="1" max="6" step="0.5" />
                  <span>{{ drawWidth }}px</span>
                </label>
                <button class="pdf-sig-clear-btn" @click="clearDraw">清空</button>
              </div>
            </div>

            <!-- 上传 -->
            <div v-show="activeTab === 'upload'" class="pdf-sig-pane">
              <label class="pdf-sig-upload-area" :class="{ 'has-image': uploadedImg }">
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  @change="onUploadChange"
                  hidden
                />
                <img v-if="uploadedImg" :src="uploadedImg" alt="签名预览" class="pdf-sig-upload-preview" />
                <div v-else class="pdf-sig-upload-placeholder">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.6">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
                  </svg>
                  <span>点击上传 PNG/JPG</span>
                  <span class="pdf-sig-upload-hint">建议透明背景,白底签名效果最佳</span>
                </div>
              </label>
              <button v-if="uploadedImg" class="pdf-sig-clear-btn" @click="uploadedImg = ''">重新上传</button>
            </div>
          </div>

          <footer class="pdf-sig-footer">
            <button class="pdf-sig-cancel" @click="$emit('close')">取消</button>
            <button class="pdf-sig-confirm" :disabled="!canConfirm" @click="onConfirm">
              <span v-if="confirming" class="pdf-sig-spinner"></span>
              <span v-else>使用此签名</span>
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', payload: { imageBase64: string; width: number; height: number }): void
}>()

const tabs = [
  { id: 'type' as const, label: '键入' },
  { id: 'draw' as const, label: '绘制' },
  { id: 'upload' as const, label: '上传' },
]
const activeTab = ref<'type' | 'draw' | 'upload'>('type')

// ===== 键入 =====
const typeName = ref('')
const font = ref('kaiti')
const fonts = [
  { id: 'kaiti', css: '"楷体", KaiTi, "STKaiti", serif' },
  { id: 'xingkai', css: '"行楷", "STXingkai", "Xingkai SC", cursive' },
  { id: 'baoli', css: '"宝丽", "Baoli SC", "STBaoli", cursive' },
  { id: 'libian', css: '"隶变", "Libian SC", "STLibian", serif' },
]
const typeCanvasRef = ref<HTMLCanvasElement | null>(null)
function renderTypeCanvas() {
  const canvas = typeCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (!typeName.value.trim()) return
  const f = fonts.find(x => x.id === font.value) || fonts[0]
  ctx.font = `48px ${f.css}`
  ctx.fillStyle = '#1a1a1a'
  ctx.textBaseline = 'middle'
  ctx.textAlign = 'center'
  ctx.fillText(typeName.value, canvas.width / 2, canvas.height / 2)
}
watch([typeName, font, activeTab], () => {
  if (activeTab.value === 'type') {
    nextTick(renderTypeCanvas)
  }
})
watch(() => props.open, (v) => {
  if (v) nextTick(() => {
    if (activeTab.value === 'type') renderTypeCanvas()
    if (activeTab.value === 'draw') initDrawCanvas()
  })
})

// ===== 绘制 =====
const drawCanvasRef = ref<HTMLCanvasElement | null>(null)
const drawColor = ref('#1a1a1a')
const drawWidth = ref(2.5)
const drawing = ref(false)
let lastX = 0, lastY = 0

function initDrawCanvas() {
  const canvas = drawCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
}
function getDrawPos(evt: MouseEvent | Touch) {
  const canvas = drawCanvasRef.value!
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  return {
    x: (evt.clientX - rect.left) * scaleX,
    y: (evt.clientY - rect.top) * scaleY,
  }
}
function onDrawStart(evt: MouseEvent) {
  drawing.value = true
  const pos = getDrawPos(evt)
  lastX = pos.x
  lastY = pos.y
}
function onDrawMove(evt: MouseEvent) {
  if (!drawing.value) return
  const ctx = drawCanvasRef.value!.getContext('2d')!
  const pos = getDrawPos(evt)
  ctx.strokeStyle = drawColor.value
  ctx.lineWidth = drawWidth.value
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  ctx.lineTo(pos.x, pos.y)
  ctx.stroke()
  lastX = pos.x
  lastY = pos.y
}
function onDrawEnd() {
  drawing.value = false
}
function onTouchStart(evt: TouchEvent) {
  if (evt.touches.length === 0) return
  const t = evt.touches[0]
  onDrawStart({ clientX: t.clientX, clientY: t.clientY } as any)
}
function onTouchMove(evt: TouchEvent) {
  if (evt.touches.length === 0) return
  const t = evt.touches[0]
  onDrawMove({ clientX: t.clientX, clientY: t.clientY } as any)
}
function clearDraw() {
  initDrawCanvas()
}

// ===== 上传 =====
const uploadedImg = ref('')
function onUploadChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    alert('图片不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    uploadedImg.value = reader.result as string
  }
  reader.readAsDataURL(file)
}

// ===== 确认 =====
const canConfirm = computed(() => {
  if (activeTab.value === 'type') return !!typeName.value.trim()
  if (activeTab.value === 'upload') return !!uploadedImg.value
  // draw:检查是否有非白色像素
  return true
})
const confirming = ref(false)
async function onConfirm() {
  confirming.value = true
  try {
    let dataUrl: string
    if (activeTab.value === 'type') {
      dataUrl = typeCanvasRef.value!.toDataURL('image/png')
    } else if (activeTab.value === 'draw') {
      dataUrl = drawCanvasRef.value!.toDataURL('image/png')
    } else {
      dataUrl = uploadedImg.value
    }
    // 去掉 data:image/png;base64, 前缀
    const base64 = dataUrl.split(',')[1]
    // 测量图片实际尺寸(为父组件拖拽定位提供参考)
    const img = new Image()
    img.src = dataUrl
    await new Promise(r => { img.onload = r; img.onerror = r })
    emit('created', {
      imageBase64: base64,
      width: img.width || 200,
      height: img.height || 80,
    })
    emit('close')
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.pdf-sig-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal, 1000);
}
.pdf-sig-dialog {
  background: var(--color-surface, #fff);
  border-radius: 12px;
  box-shadow: 0 20px 60px -12px rgba(0, 0, 0, 0.25);
  width: 480px;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pdf-sig-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}
.pdf-sig-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.pdf-sig-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pdf-sig-close:hover {
  background: #f5f7fa;
  color: #303133;
}
.pdf-sig-tabs {
  display: flex;
  border-bottom: 1px solid #ebeef5;
  padding: 0 20px;
}
.pdf-sig-tab {
  padding: 10px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
}
.pdf-sig-tab:hover {
  color: #409EFF;
}
.pdf-sig-tab.is-active {
  color: #409EFF;
  border-bottom-color: #409EFF;
  font-weight: 500;
}
.pdf-sig-body {
  padding: 20px;
  flex: 1;
  overflow-y: auto;
  min-height: 280px;
}
.pdf-sig-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pdf-sig-type-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.pdf-sig-type-input:focus {
  outline: none;
  border-color: #409EFF;
}
.pdf-sig-type-fonts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.pdf-sig-font-btn {
  padding: 12px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 18px;
  color: #303133;
  transition: all 0.2s;
  min-height: 48px;
}
.pdf-sig-font-btn:hover {
  border-color: #409EFF;
}
.pdf-sig-font-btn.is-active {
  border-color: #409EFF;
  background: #ecf5ff;
}
.pdf-sig-preview-canvas {
  width: 100%;
  height: 160px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background: #fafafa;
}
.pdf-sig-draw-canvas {
  width: 100%;
  height: 200px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: crosshair;
  touch-action: none;
}
.pdf-sig-draw-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}
.pdf-sig-color-picker,
.pdf-sig-width-picker {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pdf-sig-color-picker input[type="color"] {
  width: 28px;
  height: 28px;
  border: none;
  cursor: pointer;
  background: transparent;
}
.pdf-sig-width-picker input[type="range"] {
  width: 80px;
}
.pdf-sig-clear-btn {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
}
.pdf-sig-clear-btn:hover {
  background: #f5f7fa;
}
.pdf-sig-upload-area {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed #dcdfe6;
  border-radius: 6px;
  padding: 24px;
  cursor: pointer;
  min-height: 200px;
  transition: all 0.2s;
}
.pdf-sig-upload-area:hover {
  border-color: #409EFF;
  background: #fafcff;
}
.pdf-sig-upload-area.has-image {
  border-style: solid;
  padding: 12px;
}
.pdf-sig-upload-preview {
  max-width: 100%;
  max-height: 200px;
  object-fit: contain;
}
.pdf-sig-upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: #909399;
}
.pdf-sig-upload-hint {
  font-size: 12px;
  color: #c0c4cc;
}
.pdf-sig-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
.pdf-sig-cancel,
.pdf-sig-confirm {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.pdf-sig-cancel {
  background: #fff;
  border-color: #dcdfe6;
  color: #606266;
}
.pdf-sig-cancel:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}
.pdf-sig-confirm {
  background: #409EFF;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pdf-sig-confirm:hover:not(:disabled) {
  background: #66b1ff;
}
.pdf-sig-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pdf-sig-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pdf-sig-spin 0.8s linear infinite;
}
@keyframes pdf-sig-spin {
  to { transform: rotate(360deg); }
}
.pdf-sig-fade-enter-active,
.pdf-sig-fade-leave-active {
  transition: opacity 0.2s;
}
.pdf-sig-fade-enter-from,
.pdf-sig-fade-leave-to {
  opacity: 0;
}
</style>
