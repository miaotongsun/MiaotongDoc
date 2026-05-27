<template>
  <el-dialog
    v-model="dialogVisible"
    title="版本历史"
    width="650px"
    :close-on-click-modal="false"
    @open="onDialogOpen"
  >
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="error" class="error-state">
      <el-alert type="error" :title="error" :closable="false" show-icon />
    </div>

    <div v-else-if="versions.length === 0" class="empty-state">
      <el-empty description="暂无历史版本" :image-size="100" />
    </div>

    <div v-else class="version-list">
      <el-timeline>
        <el-timeline-item
          v-for="version in versions"
          :key="version.id"
          :timestamp="formatDateTime(version.createdAt)"
          :type="version.versionNumber === currentVersion ? 'primary' : ''"
          placement="top"
        >
          <el-card shadow="never" class="version-card">
            <div class="version-header">
              <span class="version-tag">v{{ version.versionNumber }}</span>
              <el-tag v-if="version.versionNumber === currentVersion" type="success" size="small">当前版本</el-tag>
              <span class="version-user">{{ version.createdByName || '未知用户' }}</span>
            </div>
            <div class="version-info">
              <span class="file-size">{{ formatSize(version.fileSize) }}</span>
              <span v-if="version.changeSummary" class="change-desc">{{ version.changeSummary }}</span>
            </div>
            <div class="version-actions">
              <el-button size="small" @click="handlePreview(version)">预览</el-button>
              <el-button
                v-if="isAdmin && version.versionNumber !== currentVersion"
                size="small"
                type="primary"
                @click="handleRestore(version)"
              >
                恢复此版本
              </el-button>
              <el-button size="small" @click="handleDownload(version)">下载</el-button>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { versionApi } from '@/api/version'
import { formatDateTime } from '@/utils/date'

interface VersionItem {
  id: number
  documentId: number
  versionNumber: number
  filePath: string
  fileSize: number
  fileHash?: string
  changeSummary?: string
  createdBy: number
  createdByName?: string
  createdAt: string
}

const props = defineProps<{
  modelValue: boolean
  docId: number
  currentVersion: number
  canAdmin?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'restore': [version: VersionItem]
}>()

const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin' || props.canAdmin)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const versions = ref<VersionItem[]>([])
const loading = ref(false)
const error = ref('')

// 每次打开对话框都重新加载
watch(dialogVisible, (visible) => {
  if (visible) {
    loadVersions()
  }
})

async function onDialogOpen() {
  await loadVersions()
}

async function loadVersions() {
  loading.value = true
  error.value = ''

  try {
    const data = await versionApi.getVersions(props.docId)
    versions.value = data || []
  } catch (err: any) {
    console.error('[VersionHistory] 加载失败:', err)
    error.value = err.message || '加载版本历史失败'
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}

async function handlePreview(version: VersionItem) {
  try {
    const token = sessionStorage.getItem('token') || ''
    const response = await fetch(
      `/api/versions/${props.docId}/${version.versionNumber}/preview`,
      { headers: { 'Authorization': `Bearer ${token}` } }
    )
    if (!response.ok) throw new Error('预览失败')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (err: any) {
    ElMessage.error('预览失败: ' + (err.message || ''))
  }
}

async function handleRestore(version: VersionItem) {
  try {
    await ElMessageBox.confirm(
      `确定要恢复到 v${version.versionNumber} 吗？当前版本将被保存为新版本。`,
      '确认恢复',
      { type: 'warning' }
    )
    await versionApi.restoreVersion(props.docId, version.versionNumber)
    emit('restore', version)
    ElMessage.success('版本恢复成功')
    dialogVisible.value = false
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error('恢复失败')
    }
  }
}

async function handleDownload(version: VersionItem) {
  try {
    const blob = await versionApi.downloadVersion(props.docId, version.versionNumber)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `v${version.versionNumber}.${props.currentVersion > 0 ? 'docx' : 'xlsx'}`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}
</script>

<style scoped>
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
}

.error-state {
  padding: 20px;
}

.version-list {
  max-height: 450px;
  overflow-y: auto;
  padding-right: 10px;
}

.version-card {
  border: 1px solid #ebeef5;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.version-tag {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.version-user {
  color: #909399;
  margin-left: auto;
  font-size: 13px;
}

.version-info {
  font-size: 13px;
  color: #606266;
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}

.file-size {
  color: #909399;
}

.change-desc {
  color: #409eff;
}

.version-actions {
  display: flex;
  gap: 8px;
}

:deep(.el-timeline-item__content) {
  flex: 1;
}

:deep(.el-card__body) {
  padding: 14px;
}
</style>