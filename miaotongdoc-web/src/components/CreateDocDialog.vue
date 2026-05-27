<template>
  <el-dialog v-model="visible" title="新建文档" width="420px" class="create-doc-dialog">
    <el-form :model="form" label-position="top">
      <el-form-item label="文档类型">
        <div class="doc-type-group">
          <div v-for="(config, type) in DOC_TYPE_CONFIG" :key="type"
            class="doc-type-item" :class="{ selected: form.docType === type }"
            @click="form.docType = String(type)">
            <el-icon :size="28" :style="{ color: config.color }">
              <component :is="config.icon" />
            </el-icon>
            <span class="type-name">{{ config.brandName }}</span>
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
import { ref, reactive } from 'vue'
import { documentApi } from '@/api/document'
import { DOC_TYPE_CONFIG } from '@/utils/docType'
import { ElMessage } from 'element-plus'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['created'])

const form = reactive({
  docType: 'word',
  title: ''
})
const loading = ref(false)

async function handleCreate() {
  loading.value = true
  try {
    await documentApi.create({
      docType: form.docType,
      title: form.title || undefined
    })
    ElMessage.success('创建成功')
    visible.value = false
    emit('created')
    form.title = ''
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
</style>
