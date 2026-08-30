<template>
  <el-dialog v-model="visible" title="新建文档" width="680px" class="create-doc-dialog" @open="loadData">
    <el-form :model="form" label-position="top" @keyup.enter.prevent="handleCreate">
      <!-- 1. 文档类型 -->
      <el-form-item label="文档类型">
        <div class="doc-type-group">
          <div v-for="(config, type) in DOC_TYPE_CONFIG" :key="type"
            class="doc-type-item" :class="{ selected: form.docType === type }"
            :data-doc-type="type"
            @click="onDocTypeChange(type)">
            <MindmapIcon v-if="type === 'mindmap'" :size="26" :style="{ color: config.color }" />
            <el-icon v-else :size="26" :style="{ color: config.color }">
              <component :is="config.icon" />
            </el-icon>
            <span class="type-name">{{ config.label }}</span>
            <span class="type-ext">.{{ config.ext }}</span>
          </div>
        </div>
      </el-form-item>

      <!-- 1.5 PDF 模式专属 Tab(空白 / 图片转) -->
      <template v-if="form.docType === 'pdf'">
        <el-form-item label="创建方式">
          <div class="pdf-mode-tabs">
            <div class="pdf-mode-tab"
              :class="{ active: pdfMode === 'blank' }"
              @click="pdfMode = 'blank'">空白 PDF</div>
            <div class="pdf-mode-tab"
              :class="{ active: pdfMode === 'images' }"
              @click="pdfMode = 'images'">图片转 PDF</div>
          </div>
        </el-form-item>

        <!-- 空白 PDF 表单 -->
        <template v-if="pdfMode === 'blank'">
          <el-form-item label="页数">
            <el-input-number v-model="pdfPages" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="纸张尺寸">
            <el-select v-model="pdfPreset" @change="onPdfPresetChange" style="width: 100%">
              <el-option label="A4 (210×297mm)" value="a4" />
              <el-option label="A5 (148×210mm)" value="a5" />
              <el-option label="Letter (216×279mm)" value="letter" />
              <el-option label="Legal (216×356mm)" value="legal" />
              <el-option label="自定义" value="custom" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="pdfPreset === 'custom'" label="自定义尺寸 (pt)">
            <div class="row-2">
              <el-input-number v-model="pdfWidth" :min="100" :max="2000" />
              <el-input-number v-model="pdfHeight" :min="100" :max="2000" />
            </div>
          </el-form-item>
        </template>

        <!-- 图片转 PDF 表单 -->
        <template v-else>
          <el-form-item label="图片">
            <div class="pdf-upload"
              :class="{ dragover: pdfDragOver }"
              @click="triggerUpload"
              @dragover.prevent="pdfDragOver = true"
              @dragleave.prevent="pdfDragOver = false"
              @drop.prevent="onPdfDrop">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
              </svg>
              <p>点击或拖拽图片到此处</p>
              <p class="hint">支持 PNG / JPG,每图 1 页 A4 居中</p>
              <input ref="pdfFileInputRef" type="file" multiple accept="image/png,image/jpeg" class="pdf-file-input" @change="onPdfFileSelect" />
            </div>
            <div v-if="pdfImages.length > 0" class="pdf-img-list">
              <div v-for="(img, i) in pdfImages" :key="i" class="pdf-img-item">
                <img :src="img.preview" :alt="img.name" class="pdf-img-thumb" />
                <div class="pdf-img-meta">
                  <div class="pdf-img-name">{{ img.name }}</div>
                  <div class="pdf-img-size">{{ formatFileSize(img.size) }}</div>
                </div>
                <el-button link size="small" type="danger" @click="removePdfImage(i)">移除</el-button>
              </div>
            </div>
          </el-form-item>
        </template>
      </template>

      <!-- 2. 选择模板(可折叠) -->
      <el-form-item>
        <div class="template-section-header" @click="templateSectionExpanded = !templateSectionExpanded">
          <span class="template-section-title">
            选择模板
            <span class="template-section-hint">（{{ filteredTemplates.length }} 个模板可选,可跳过）</span>
          </span>
          <el-icon class="toggle-icon" :class="{ collapsed: !templateSectionExpanded }">
            <ArrowDown />
          </el-icon>
        </div>
        <div v-show="templateSectionExpanded" class="template-browser">
          <!-- 左侧分类列表 -->
          <div class="template-categories">
            <div class="category-item" :class="{ selected: selectedCategory === '' }"
              @click="selectedCategory = ''">
              全部
            </div>
            <div v-for="cat in filteredCategories" :key="cat"
              class="category-item" :class="{ selected: selectedCategory === cat }"
              @click="selectedCategory = cat">
              {{ cat }}
            </div>
          </div>
          <!-- 右侧模板列表 -->
          <div class="template-list">
            <div class="template-item" :class="{ selected: form.templateId === 0 }"
              @click="form.templateId = 0">
              <div class="template-icon">📄</div>
              <div class="template-info">
                <div class="template-name">空白文档</div>
                <div class="template-desc">不使用模板,从零开始</div>
              </div>
            </div>
            <div v-for="tpl in filteredTemplates" :key="tpl.id"
              class="template-item" :class="{ selected: form.templateId === tpl.id }"
              @click="form.templateId = tpl.id">
              <div class="template-icon">📝</div>
              <div class="template-info">
                <div class="template-name">{{ tpl.name }}</div>
                <div v-if="tpl.description" class="template-desc">{{ tpl.description }}</div>
              </div>
            </div>
            <el-empty v-if="filteredTemplates.length === 0" description="该分类暂无模板" :image-size="60" />
          </div>
        </div>
      </el-form-item>

      <!-- 3. 文档标题 -->
      <el-form-item label="文档标题">
        <el-input v-model="form.title" placeholder="请输入文档标题（可选）" maxlength="100" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { documentApi } from '@/api/document'
import { pdfApi } from '@/api/pdf'
import { templateApi, type DocumentTemplate } from '@/api/template'
import { DOC_TYPE_CONFIG } from '@/utils/docType'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { formatFileSize } from '@/utils/docType'
import MindmapIcon from '@/components/MindmapIcon.vue'

const router = useRouter()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ (e: 'created', docId: number): void }>()

const form = reactive({
  docType: 'word',
  title: '',
  templateId: 0
})
const loading = ref(false)
const templates = ref<DocumentTemplate[]>([])
const categories = ref<string[]>([])
const selectedCategory = ref('')
const templateSectionExpanded = ref(true)

// PDF 专属状态
const pdfMode = ref<'blank' | 'images'>('blank')
const PDF_PRESETS: Record<string, { w: number; h: number }> = {
  a4: { w: 595, h: 842 },
  a5: { w: 420, h: 595 },
  letter: { w: 612, h: 792 },
  legal: { w: 612, h: 1008 },
}
const pdfPages = ref(1)
const pdfPreset = ref('a4')
const pdfWidth = ref(595)
const pdfHeight = ref(842)
const pdfImages = ref<Array<{ file: File; preview: string; name: string; size: number }>>([])
const pdfDragOver = ref(false)
const pdfFileInputRef = ref<HTMLInputElement | null>(null)

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (selectedCategory.value) return t.category === selectedCategory.value
  return true
}))

const filteredCategories = computed(() => categories.value)

// 切 docType 重置模板与 PDF 状态
function onDocTypeChange(type: string) {
  if (form.docType === type) return
  form.docType = type
  form.templateId = 0
  selectedCategory.value = ''
  // PDF 切换时初始化 PDF 表单默认值
  if (type === 'pdf') {
    pdfMode.value = 'blank'
    pdfPages.value = 1
    pdfPreset.value = 'a4'
    pdfWidth.value = PDF_PRESETS.a4.w
    pdfHeight.value = PDF_PRESETS.a4.h
  }
  // 重新加载模板
  loadTemplates()
}

function onPdfPresetChange() {
  const p = PDF_PRESETS[pdfPreset.value]
  if (p) {
    pdfWidth.value = p.w
    pdfHeight.value = p.h
  }
}

function triggerUpload() {
  pdfFileInputRef.value?.click()
}

function onPdfFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addPdfFiles(Array.from(input.files))
  input.value = ''
}

function onPdfDrop(e: DragEvent) {
  pdfDragOver.value = false
  if (e.dataTransfer?.files) addPdfFiles(Array.from(e.dataTransfer.files))
}

function addPdfFiles(files: File[]) {
  for (const f of files) {
    if (!f.type.startsWith('image/')) continue
    pdfImages.value.push({
      file: f,
      preview: URL.createObjectURL(f),
      name: f.name,
      size: f.size,
    })
  }
}

function removePdfImage(i: number) {
  URL.revokeObjectURL(pdfImages.value[i].preview)
  pdfImages.value.splice(i, 1)
}

async function loadTemplates() {
  try {
    const list = await templateApi.getAll({ docType: form.docType })
    templates.value = list
  } catch {
    templates.value = []
  }
}

async function loadData() {
  // 类别始终全量加载(避免每次切 docType 都打两次接口)
  try {
    categories.value = await templateApi.getCategories()
  } catch {}
  await loadTemplates()
}

// 监听 dialog 重新打开时初始化
watch(visible, (v) => {
  if (v) {
    // 重置 PDF 状态(防止上一次打开后留下图片内存泄漏)
    pdfImages.value.forEach(i => URL.revokeObjectURL(i.preview))
    pdfImages.value = []
  }
})

async function handleCreate() {
  loading.value = true
  try {
    let docId: number
    if (form.docType === 'pdf') {
      if (pdfMode.value === 'blank') {
        const r = await pdfApi.createBlank(
          pdfPages.value,
          pdfWidth.value,
          pdfHeight.value,
          form.title || '新建空白文档'
        )
        docId = r.docId
      } else {
        if (pdfImages.value.length === 0) {
          ElMessage.warning('请至少添加一张图片')
          loading.value = false
          return
        }
        const files = pdfImages.value.map(i => i.file)
        const r = await pdfApi.createFromImages(files, form.title || '图片合集')
        docId = r.docId
      }
    } else {
      const doc = await documentApi.create({
        docType: form.docType,
        title: form.title || undefined,
        templateId: form.templateId || undefined,
      })
      docId = doc.id
    }

    ElMessage.success('创建成功')
    visible.value = false
    emit('created', docId)
    router.push(`/editor/${docId}`)

    // 重置表单(但保留 docType 选择,提升连续创建体验)
    form.title = ''
    form.templateId = 0
    pdfImages.value.forEach(i => URL.revokeObjectURL(i.preview))
    pdfImages.value = []
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.doc-type-group {
  display: flex;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}

.doc-type-item {
  flex: 1;
  min-width: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 6px;
  border: 2px solid #e8e8e8;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.doc-type-item:hover {
  border-color: #c0c4cc;
  background: #fafafa;
}

.doc-type-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.type-name {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
}

.type-ext {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

/* PDF 模式 tabs */
.pdf-mode-tabs {
  display: flex;
  gap: 0;
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.pdf-mode-tab {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  background: #fafafa;
  transition: all 0.2s;
  border-right: 1px solid #e4e7ed;
}

.pdf-mode-tab:last-child {
  border-right: none;
}

.pdf-mode-tab:hover {
  background: #ecf5ff;
  color: #409eff;
}

.pdf-mode-tab.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.row-2 {
  display: flex;
  gap: 12px;
  width: 100%;
}

.row-2 > * {
  flex: 1;
}

/* PDF 图片上传 */
.pdf-upload {
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 24px 20px;
  text-align: center;
  cursor: pointer;
  color: #909399;
  transition: all 0.2s;
}

.pdf-upload:hover,
.pdf-upload.dragover {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}

.pdf-upload p {
  margin: 8px 0 0;
  font-size: 14px;
}

.pdf-upload .hint {
  font-size: 12px;
  color: #909399;
}

.pdf-file-input {
  display: none;
}

.pdf-img-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
}

.pdf-img-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 6px;
}

.pdf-img-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  background: #fff;
  flex-shrink: 0;
}

.pdf-img-meta {
  flex: 1;
  min-width: 0;
}

.pdf-img-name {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pdf-img-size {
  font-size: 11px;
  color: #909399;
}

/* 模板区折叠头 */
.template-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
  user-select: none;
  width: 100%;
}

.template-section-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.template-section-hint {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 4px;
}

.toggle-icon {
  color: #909399;
  transition: transform 0.2s;
}

.toggle-icon.collapsed {
  transform: rotate(-90deg);
}

.template-browser {
  display: flex;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  width: 100%;
  height: 280px;
}

.template-categories {
  width: 140px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  background: #fafafa;
}

.category-item {
  padding: 10px 14px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.category-item:hover {
  background: #ecf5ff;
}

.category-item.selected {
  background: #ecf5ff;
  color: #409eff;
  border-left-color: #409eff;
  font-weight: 500;
}

.template-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.template-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-item:hover {
  border-color: #c0c4cc;
  background: #fafafa;
}

.template-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.template-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.template-info {
  flex: 1;
  min-width: 0;
}

.template-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.template-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>