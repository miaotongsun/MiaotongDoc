<!--
  PdfCreateDialog.vue -- Phase 13.12-C 创建 PDF 对话框

  2 种创建方式:
    - 空白 PDF:自定义页数 + 纸张尺寸(A4/A5/Letter/自定义)
    - 图片转 PDF:多图上传,每图 1 页 A4 居中

  确认后调用 pdfApi.createBlank / createFromImages,跳转 /editor/{newDocId}
-->
<template>
  <Teleport to="body">
    <Transition name="pdf-create-fade">
      <div v-if="open" class="pdf-create-overlay" @click.self="$emit('close')">
        <div class="pdf-create-dialog" role="dialog" aria-label="创建 PDF">
          <header class="pdf-create-header">
            <h3>创建 PDF</h3>
            <button class="pdf-create-close" aria-label="关闭" @click="$emit('close')">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </header>

          <div class="pdf-create-tabs" role="tablist">
            <button
              v-for="t in tabs"
              :key="t.id"
              class="pdf-create-tab"
              :class="{ 'is-active': activeTab === t.id }"
              role="tab"
              @click="activeTab = t.id"
            >{{ t.label }}</button>
          </div>

          <!-- Tab 1: 空白 PDF -->
          <div v-show="activeTab === 'blank'" class="pdf-create-body">
            <div class="pdf-create-field">
              <label>页数</label>
              <input type="number" v-model.number="blankPages" min="1" max="100" class="pdf-create-input" />
            </div>
            <div class="pdf-create-field">
              <label>纸张尺寸</label>
              <select v-model="blankPreset" class="pdf-create-input" @change="onPresetChange">
                <option value="a4">A4 (210×297mm)</option>
                <option value="a5">A5 (148×210mm)</option>
                <option value="letter">Letter (216×279mm)</option>
                <option value="legal">Legal (216×356mm)</option>
                <option value="custom">自定义</option>
              </select>
            </div>
            <div v-if="blankPreset === 'custom'" class="pdf-create-field-row">
              <div class="pdf-create-field">
                <label>宽度 (pt)</label>
                <input type="number" v-model.number="blankWidth" min="100" max="2000" class="pdf-create-input" />
              </div>
              <div class="pdf-create-field">
                <label>高度 (pt)</label>
                <input type="number" v-model.number="blankHeight" min="100" max="2000" class="pdf-create-input" />
              </div>
            </div>
            <div class="pdf-create-field">
              <label>文档标题</label>
              <input type="text" v-model="blankTitle" placeholder="新建空白文档" class="pdf-create-input" />
            </div>
            <div class="pdf-create-preview">
              <div class="pdf-create-preview-page" :style="previewStyle">
                <span class="pdf-create-preview-label">{{ blankPages }} 页</span>
              </div>
            </div>
          </div>

          <!-- Tab 2: 图片转 PDF -->
          <div v-show="activeTab === 'images'" class="pdf-create-body">
            <div
              class="pdf-create-upload"
              @click="triggerUpload"
              @dragover.prevent="dragOver = true"
              @dragleave.prevent="dragOver = false"
              @drop.prevent="onDrop"
              :class="{ 'is-dragover': dragOver }"
            >
              <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
              </svg>
              <p>点击或拖拽图片到此处</p>
              <p class="pdf-create-upload-hint">支持 PNG / JPG,每图 1 页 A4 居中</p>
              <input ref="fileInputRef" type="file" multiple accept="image/png,image/jpeg" class="pdf-create-file-input" @change="onFileSelect" />
            </div>
            <div v-if="imageList.length > 0" class="pdf-create-img-list">
              <div v-for="(img, i) in imageList" :key="i" class="pdf-create-img-item">
                <img :src="img.preview" :alt="img.name" class="pdf-create-img-thumb" />
                <div class="pdf-create-img-meta">
                  <div class="pdf-create-img-name" :title="img.name">{{ img.name }}</div>
                  <div class="pdf-create-img-size">{{ formatSize(img.size) }}</div>
                </div>
                <button class="pdf-create-img-remove" aria-label="移除" @click="removeImage(i)">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                </button>
              </div>
            </div>
            <div v-if="imageList.length > 0" class="pdf-create-field">
              <label>文档标题</label>
              <input type="text" v-model="imagesTitle" placeholder="图片合集" class="pdf-create-input" />
            </div>
          </div>

          <footer class="pdf-create-footer">
            <button class="pdf-create-btn pdf-create-btn-cancel" @click="$emit('close')">取消</button>
            <button
              class="pdf-create-btn pdf-create-btn-ok"
              :disabled="!canSubmit || submitting"
              @click="onSubmit"
            >{{ submitting ? '创建中...' : '创建' }}</button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pdfApi } from '@/api/pdf'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'created', docId: number): void }>()

const router = useRouter()
const tabs = [
  { id: 'blank' as const, label: '空白 PDF' },
  { id: 'images' as const, label: '图片转 PDF' },
]
const activeTab = ref<'blank' | 'images'>('blank')

// 空白 PDF 状态
const PRESETS: Record<string, { w: number; h: number }> = {
  a4: { w: 595, h: 842 },
  a5: { w: 420, h: 595 },
  letter: { w: 612, h: 792 },
  legal: { w: 612, h: 1008 },
}
const blankPages = ref(1)
const blankPreset = ref('a4')
const blankWidth = ref(595)
const blankHeight = ref(842)
const blankTitle = ref('')

function onPresetChange() {
  const p = PRESETS[blankPreset.value]
  if (p) {
    blankWidth.value = p.w
    blankHeight.value = p.h
  }
}

const previewStyle = computed(() => {
  const ratio = blankWidth.value / blankHeight.value
  // 预览框最大 120x160,等比
  const maxW = 120, maxH = 160
  let w = maxW, h = maxW / ratio
  if (h > maxH) { h = maxH; w = maxH * ratio }
  return { width: w + 'px', height: h + 'px' }
})

// 图片转 PDF 状态
const imageList = ref<Array<{ file: File; preview: string; name: string; size: number }>>([])
const imagesTitle = ref('')
const dragOver = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerUpload() {
  fileInputRef.value?.click()
}

function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(Array.from(input.files))
  input.value = ''
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files) addFiles(Array.from(e.dataTransfer.files))
}

function addFiles(files: File[]) {
  for (const f of files) {
    if (!f.type.startsWith('image/')) continue
    imageList.value.push({
      file: f,
      preview: URL.createObjectURL(f),
      name: f.name,
      size: f.size,
    })
  }
}

function removeImage(i: number) {
  URL.revokeObjectURL(imageList.value[i].preview)
  imageList.value.splice(i, 1)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// 提交
const submitting = ref(false)
const canSubmit = computed(() => {
  if (activeTab.value === 'blank') return blankPages.value >= 1
  return imageList.value.length > 0
})

async function onSubmit() {
  submitting.value = true
  try {
    let docId: number
    if (activeTab.value === 'blank') {
      const r = await pdfApi.createBlank(blankPages.value, blankWidth.value, blankHeight.value, blankTitle.value || '新建空白文档')
      docId = r.docId
    } else {
      const files = imageList.value.map(i => i.file)
      const r = await pdfApi.createFromImages(files, imagesTitle.value || '图片合集')
      docId = r.docId
    }
    ElMessage.success('PDF 创建成功')
    emit('created', docId)
    emit('close')
    // 跳转到新文档编辑器
    router.push(`/editor/${docId}`)
    resetForm()
  } catch (e: any) {
    console.error('[PdfCreateDialog] create failed:', e)
    ElMessage.error(e?.message || '创建 PDF 失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  blankPages.value = 1
  blankPreset.value = 'a4'
  blankWidth.value = 595
  blankHeight.value = 842
  blankTitle.value = ''
  imageList.value.forEach(i => URL.revokeObjectURL(i.preview))
  imageList.value = []
  imagesTitle.value = ''
}
</script>

<style scoped>
.pdf-create-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.pdf-create-dialog {
  background: var(--color-surface, #fff);
  border-radius: 12px;
  box-shadow: 0 20px 60px -12px rgba(0,0,0,0.3);
  width: 480px;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pdf-create-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-divider, #e4e7ed);
}
.pdf-create-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-foreground, #303133);
}
.pdf-create-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-foreground-3, #909399);
  padding: 4px;
  border-radius: 4px;
}
.pdf-create-close:hover {
  background: var(--color-surface-2, #f5f7fa);
  color: var(--color-foreground, #303133);
}
.pdf-create-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-divider, #e4e7ed);
}
.pdf-create-tab {
  flex: 1;
  padding: 10px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-foreground-2, #606266);
  border-bottom: 2px solid transparent;
  transition: all 150ms ease;
}
.pdf-create-tab.is-active {
  color: var(--color-primary, #409eff);
  border-bottom-color: var(--color-primary, #409eff);
  font-weight: 600;
}
.pdf-create-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.pdf-create-field {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pdf-create-field label {
  font-size: 13px;
  color: var(--color-foreground-2, #606266);
  font-weight: 500;
}
.pdf-create-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border, #dcdfe6);
  border-radius: 6px;
  font-size: 14px;
  color: var(--color-foreground, #303133);
  background: var(--color-surface, #fff);
  outline: none;
  transition: border-color 150ms ease;
}
.pdf-create-input:focus {
  border-color: var(--color-primary, #409eff);
}
.pdf-create-field-row {
  display: flex;
  gap: 12px;
}
.pdf-create-field-row .pdf-create-field {
  flex: 1;
}
.pdf-create-preview {
  display: flex;
  justify-content: center;
  padding: 16px 0;
  background: var(--color-surface-2, #f5f7fa);
  border-radius: 6px;
  margin-top: 8px;
}
.pdf-create-preview-page {
  background: #fff;
  border: 1px solid var(--color-border, #dcdfe6);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}
.pdf-create-preview-label {
  font-size: 12px;
  color: var(--color-foreground-3, #909399);
}
.pdf-create-upload {
  border: 2px dashed var(--color-border, #dcdfe6);
  border-radius: 8px;
  padding: 32px 20px;
  text-align: center;
  cursor: pointer;
  color: var(--color-foreground-3, #909399);
  transition: all 150ms ease;
}
.pdf-create-upload:hover,
.pdf-create-upload.is-dragover {
  border-color: var(--color-primary, #409eff);
  color: var(--color-primary, #409eff);
  background: var(--color-primary-soft, #ecf5ff);
}
.pdf-create-upload p {
  margin: 8px 0 0;
  font-size: 14px;
}
.pdf-create-upload-hint {
  font-size: 12px !important;
  color: var(--color-foreground-3, #909399);
}
.pdf-create-file-input {
  display: none;
}
.pdf-create-img-list {
  margin-top: 12px;
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pdf-create-img-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: var(--color-surface-2, #f5f7fa);
  border-radius: 6px;
}
.pdf-create-img-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  background: #fff;
}
.pdf-create-img-meta {
  flex: 1;
  min-width: 0;
}
.pdf-create-img-name {
  font-size: 13px;
  color: var(--color-foreground, #303133);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pdf-create-img-size {
  font-size: 11px;
  color: var(--color-foreground-3, #909399);
}
.pdf-create-img-remove {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-foreground-3, #909399);
  padding: 4px;
  border-radius: 4px;
}
.pdf-create-img-remove:hover {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.1);
}
.pdf-create-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-divider, #e4e7ed);
}
.pdf-create-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 150ms ease;
}
.pdf-create-btn-cancel {
  background: var(--color-surface, #fff);
  color: var(--color-foreground-2, #606266);
  border-color: var(--color-border, #dcdfe6);
}
.pdf-create-btn-cancel:hover {
  color: var(--color-primary, #409eff);
  border-color: var(--color-primary, #409eff);
}
.pdf-create-btn-ok {
  background: var(--color-primary, #409eff);
  color: #fff;
}
.pdf-create-btn-ok:hover:not(:disabled) {
  background: var(--color-primary-hover, #66b1ff);
}
.pdf-create-btn-ok:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pdf-create-fade-enter-active,
.pdf-create-fade-leave-active {
  transition: opacity 200ms ease;
}
.pdf-create-fade-enter-from,
.pdf-create-fade-leave-to {
  opacity: 0;
}
</style>