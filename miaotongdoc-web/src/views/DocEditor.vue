<template>
  <div class="doc-editor-page">
    <nav class="editor-nav">
      <div class="nav-left">
        <button @click="goBack" class="back-btn">
          <el-icon :size="16"><ArrowLeft /></el-icon>
        </button>
        <el-tag :color="docTypeConfig.color" effect="dark" size="small" class="doc-type-tag">
          {{ docTypeConfig.label }}
        </el-tag>
        <span class="doc-title">{{ docTitle }}</span>
        <span class="save-status">
          <span class="save-dot" :class="{ editing: saveStatus === '编辑中...' }" />
          {{ saveStatus }}
        </span>
        <span v-if="doc?.ownerName" class="info-chip">
          <el-icon><User /></el-icon>创建人：{{ doc.ownerName }}
        </span>
        <span v-if="doc" class="info-chip">版本：V{{ doc.currentVersion }}</span>
        <span v-if="permLabel" class="info-chip">权限：{{ permLabel }}</span>
      </div>

      <div class="nav-right">
        <NotificationBell />
        <div class="nav-actions">
          <el-button v-if="canAdmin" size="small" plain @click="showShareDialog = true">
            <el-icon><Share /></el-icon>共享
          </el-button>
          <el-button v-if="canComment" size="small" plain @click="showCommentPanel = !showCommentPanel">
            <el-icon><ChatDotRound /></el-icon>评论
          </el-button>
          <el-button v-if="canComment" size="small" plain @click="showVersions = true">
            <el-icon><Clock /></el-icon>版本
          </el-button>
          <el-button v-if="isOwner" size="small" type="primary" plain @click="handleSaveVersion">
            保存版本
          </el-button>
          <el-button v-if="isOwner && docStatus === 'draft'" size="small" type="primary" plain @click="showSigningDialog = true">
            提交签署
          </el-button>
          <el-button v-if="docStatus === 'signed'" size="small" type="success" plain @click="exportPdf">
            导出PDF
          </el-button>
        </div>
      </div>
    </nav>

    <SigningBar v-if="signingTask" :task="signingTask" :current-user-id="currentUserId"
      @sign="onSign" @reject="onReject" @cancel="onCancelSigning" />

    <div class="editor-body">
      <DocumentEditor v-if="config" :server-url="editorServerUrl" :config="config"
        @ready="onReady" @state-change="onStateChange" />
      <CommentPanel v-if="showCommentPanel" :doc-id="docId"
        @close="showCommentPanel = false" />
    </div>

    <ShareDialog v-model="showShareDialog" :doc-id="docId" />
    <VersionHistory v-model="showVersions" :doc-id="docId" :doc-title="docTitle"
      :doc-type="doc?.fileType || 'docx'" :current-version="doc?.currentVersion || 1" @restore="onVersionRestore" />
    <SigningDialog v-model="showSigningDialog" :doc-id="docId" @submitted="onSigningSubmitted" />

    <el-dialog v-model="showSaveVersionDialog" title="保存版本" width="400px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="版本说明">
          <el-input v-model="versionSummary" type="textarea" :rows="3" placeholder="请输入版本说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveVersionDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmSaveVersion">确定保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Share, ChatDotRound, Clock, User } from '@element-plus/icons-vue'
import { documentApi } from '@/api/document'
import { signingApi } from '@/api/signing'
import { getDocTypeConfig } from '@/utils/docType'
import type { Document } from '@/api/document'
import type { SigningTask } from '@/api/signing'
import NotificationBell from '@/components/NotificationBell.vue'
import DocumentEditor from '@/components/DocumentEditor.vue'
import CommentPanel from '@/components/CommentPanel.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import VersionHistory from '@/components/VersionHistory.vue'
import SigningDialog from '@/components/SigningDialog.vue'
import SigningBar from '@/components/SigningBar.vue'

const route = useRoute()
const router = useRouter()

const docId = computed(() => Number(route.params.id))
const doc = ref<Document | null>(null)
const config = ref<any>(null)
const showCommentPanel = ref(false)
const showShareDialog = ref(false)
const showVersions = ref(false)
const showSigningDialog = ref(false)
const showSaveVersionDialog = ref(false)
const versionSummary = ref('')
const saveStatus = ref('')
const signingTask = ref<SigningTask | null>(null)
const currentUserId = computed(() => Number(sessionStorage.getItem('userId')) || 0)

const docTitle = computed(() => doc.value?.title || '加载中...')
const docStatus = computed(() => doc.value?.status || 'draft')
const isOwner = computed(() => {
  const userId = Number(sessionStorage.getItem('userId'))
  return doc.value?.ownerUserId === userId
})
const canAdmin = computed(() => {
  return sessionStorage.getItem('role') === 'admin' || doc.value?.currentUserPermission === 'admin'
})
const canComment = computed(() => {
  const perm = doc.value?.currentUserPermission
  return canAdmin.value || perm === 'comment' || perm === 'edit'
})
const permLabel = computed(() => {
  if (isOwner.value || canAdmin.value) return '管理'
  const map: Record<string, string> = { view: '只读', comment: '评论', edit: '编辑' }
  return map[doc.value?.currentUserPermission || ''] || ''
})

const docTypeConfig = computed(() => {
  return getDocTypeConfig(doc.value?.docType || 'word')
})

const editorServerUrl = computed(() => {
  return import.meta.env.VITE_EDITOR_SERVER_URL || '/ds-vpath'
})

onMounted(async () => {
  // 清除协作模式设置，防止用户卡在严格模式
  localStorage.removeItem('de-settings-coauthmode')

  try {
    doc.value = await documentApi.getById(docId.value)
    config.value = await documentApi.getConfig(docId.value)
    if (doc.value?.status === 'signing') {
      await loadSigningTask()
    }
  } catch (error) {
    console.error('加载文档失败', error)
  }
})

async function loadSigningTask() {
  try {
    const task = await signingApi.getByDocumentId(docId.value)
    signingTask.value = task
  } catch {
    // no active signing task for this document
  }
}

function goBack() {
  router.push('/home')
}

function onReady() {
  saveStatus.value = '已保存'
}

function onStateChange(state: string) {
  if (state === 'editing') {
    saveStatus.value = '编辑中...'
  } else if (state === 'saved') {
    saveStatus.value = '已保存'
  }
}

async function exportPdf() {
  try {
    const blob = await documentApi.exportPdf(docId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${doc.value?.title || 'document'}_v${doc.value?.currentVersion || 1}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('PDF导出失败')
  }
}

function onVersionRestore() {
  location.reload()
}

async function handleSaveVersion() {
  versionSummary.value = ''
  showSaveVersionDialog.value = true
}

async function confirmSaveVersion() {
  try {
    const res = await documentApi.createVersion(docId.value, versionSummary.value || undefined)
    ElMessage.success(`版本 v${res.versionNumber} 已保存`)
    showSaveVersionDialog.value = false
    // 更新本地版本号显示
    if (doc.value) {
      doc.value.currentVersion = res.versionNumber
    }
  } catch {
    ElMessage.error('保存版本失败')
  }
}

function onSigningSubmitted() {
  doc.value && (doc.value.status = 'signing')
  loadSigningTask()
}

async function onSign() {
  if (!signingTask.value) return
  try {
    await signingApi.confirm(signingTask.value.id)
    ElMessage.success('签署成功')
    signingTask.value = null
    doc.value && (doc.value.status = 'signed')
  } catch {
    ElMessage.error('签署失败')
  }
}

async function onReject(reason: string) {
  if (!signingTask.value) return
  try {
    await signingApi.reject(signingTask.value.id, reason)
    ElMessage.success('已驳回')
    signingTask.value = null
    doc.value && (doc.value.status = 'draft')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function onCancelSigning() {
  if (!signingTask.value) return
  try {
    await signingApi.cancel(signingTask.value.id)
    ElMessage.success('签署已取消')
    signingTask.value = null
    doc.value && (doc.value.status = 'draft')
  } catch {
    ElMessage.error('取消失败')
  }
}
</script>

<style scoped>
.doc-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.editor-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  height: 40px;
  flex-shrink: 0;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.back-btn {
  background: none;
  border: 1px solid var(--el-color-primary-light-7);
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 4px 6px;
  border-radius: 4px;
  color: var(--el-color-primary);
  transition: all 0.2s;
  flex-shrink: 0;
}

.back-btn:hover {
  background: var(--hover-bg);
  border-color: var(--el-color-primary);
}

.doc-type-tag {
  border-color: transparent !important;
  flex-shrink: 0;
}

.doc-title {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
  margin-right: 12px;
}

.save-status {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

.save-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #67c23a;
}

.save-dot.editing {
  background: #e6a23c;
  animation: pulse 1.5s ease-in-out infinite;
}

.info-chip {
  padding: 0 6px;
  border-radius: 3px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 所有按钮统一样式 */
.nav-actions :deep(.el-button) {
  transition: all 0.2s;
  border-radius: 6px;
  font-weight: 500;
}

/* 所有 plain 按钮：白底 + 渐变边框 */
.nav-actions :deep(.el-button.is-plain) {
  position: relative;
  background: #fff !important;
  border: none !important;
  z-index: 1;
}

.nav-actions :deep(.el-button.is-plain)::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: var(--primary-gradient);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  z-index: -1;
}

/* 所有 plain 按钮 hover：渐变背景 + 白色文字 */
.nav-actions :deep(.el-button.is-plain):hover {
  color: #fff !important;
  background: var(--primary-gradient) !important;
  border: none !important;
}

.nav-actions :deep(.el-button.is-plain):hover::before {
  display: none;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
