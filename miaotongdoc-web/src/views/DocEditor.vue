<template>
  <div class="doc-editor-page">
    <nav class="editor-nav">
      <button @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </button>
      <el-tag :color="docTypeConfig.color" effect="dark" size="small" class="doc-type-tag">
        {{ docTypeConfig.label }}
      </el-tag>
      <span class="doc-title">{{ docTitle }}</span>
      <span class="save-status">{{ saveStatus }}</span>
      <div class="nav-right">
        <CollaborationBar :doc-id="docId" />
        <NotificationBell />
        <el-button v-if="canAdmin" @click="showShareDialog = true">共享</el-button>
        <el-button @click="showCommentPanel = !showCommentPanel">评论</el-button>
        <el-button @click="showVersions = true">版本历史</el-button>
        <el-button v-if="isOwner && docStatus === 'draft'" @click="showSigningDialog = true">
          提交签署
        </el-button>
        <el-button v-if="docStatus === 'signed'" @click="exportPdf">
          导出PDF
        </el-button>
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
    <VersionHistory v-model="showVersions" :doc-id="docId" :current-version="doc?.currentVersion || 1"
      @restore="onVersionRestore" />
    <SigningDialog v-model="showSigningDialog" :doc-id="docId" @submitted="onSigningSubmitted" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { documentApi } from '@/api/document'
import { signingApi } from '@/api/signing'
import { getDocTypeConfig } from '@/utils/docType'
import type { Document } from '@/api/document'
import type { SigningTask } from '@/api/signing'
import CollaborationBar from '@/components/CollaborationBar.vue'
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
    const res = await signingApi.getMyTasks({ type: 'todo', page: 0, size: 100 })
    const task = res.content?.find((t: any) => t.documentId === docId.value)
    if (task) {
      signingTask.value = task
    }
  } catch (error) {
    console.error('加载签署任务失败', error)
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
    a.download = `${doc.value?.title || 'document'}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('PDF导出失败')
  }
}

function onVersionRestore() {
  location.reload()
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
  gap: 12px;
  padding: 10px 20px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
}

.doc-type-tag {
  border-color: transparent !important;
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
}

.doc-title {
  font-weight: 500;
  font-size: 16px;
}

.save-status {
  color: #909399;
  font-size: 12px;
}

.nav-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.signing-lock-banner {
  background: #e6a23c;
  color: white;
  text-align: center;
  padding: 8px;
  font-size: 14px;
}
</style>
