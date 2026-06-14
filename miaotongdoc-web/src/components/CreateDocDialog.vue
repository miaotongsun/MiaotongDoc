<template>
  <el-dialog v-model="visible" title="新建文档" width="680px" class="create-doc-dialog" @open="loadData">
    <el-form :model="form" label-position="top">
      <el-form-item label="文档类型">
        <div class="doc-type-group">
          <div v-for="(config, type) in DOC_TYPE_CONFIG" :key="type"
            class="doc-type-item" :class="{ selected: form.docType === type }"
            @click="form.docType = String(type); form.templateId = 0">
            <el-icon :size="28" :style="{ color: config.color }">
              <component :is="config.icon" />
            </el-icon>
            <span class="type-name">{{ config.brandName }}</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="选择模板">
        <div class="template-browser">
          <!-- 左侧分类列表 -->
          <div class="template-categories">
            <div class="category-item" :class="{ selected: selectedCategory === '' }"
              @click="selectedCategory = ''">
              全部
            </div>
            <div v-for="cat in categories" :key="cat"
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
              <div class="template-name">空白文档</div>
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
import { ref, reactive, computed } from 'vue'
import { documentApi } from '@/api/document'
import { templateApi, type DocumentTemplate } from '@/api/template'
import { DOC_TYPE_CONFIG } from '@/utils/docType'
import { ElMessage } from 'element-plus'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['created'])

const form = reactive({
  docType: 'word',
  title: '',
  templateId: 0
})
const loading = ref(false)
const templates = ref<DocumentTemplate[]>([])
const categories = ref<string[]>([])
const selectedCategory = ref('')

const filteredTemplates = computed(() => {
  let list = templates.value.filter(t => t.docType === form.docType && t.isActive)
  if (selectedCategory.value) {
    list = list.filter(t => t.category === selectedCategory.value)
  }
  return list
})

async function loadData() {
  try {
    const [cats, tmpls] = await Promise.all([
      templateApi.getCategories(),
      templateApi.getAll()
    ])
    categories.value = cats
    templates.value = tmpls
  } catch {}
}

async function handleCreate() {
  loading.value = true
  try {
    await documentApi.create({
      docType: form.docType,
      title: form.title || undefined,
      templateId: form.templateId || undefined
    })
    ElMessage.success('创建成功')
    visible.value = false
    emit('created')
    form.title = ''
    form.templateId = 0
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
  gap: 12px;
  width: 100%;
}

.doc-type-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
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
