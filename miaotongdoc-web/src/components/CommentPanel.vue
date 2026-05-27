<template>
  <div class="comment-panel">
    <div class="panel-header">
      <span>评论讨论</span>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unresolved">待解决</el-radio-button>
      </el-radio-group>
      <el-button text @click="$emit('close')">关闭</el-button>
    </div>

    <div class="comment-list">
      <div v-for="comment in filteredComments" :key="comment.id" class="comment-item">
        <el-avatar :size="36">{{ comment.userName?.charAt(0) }}</el-avatar>
        <div class="comment-body">
          <div class="comment-header">
            <span class="user-name">{{ comment.userName }}</span>
            <span class="emp-id">{{ comment.employeeId }}</span>
            <span class="time">{{ formatDate(comment.createdAt) }}</span>
            <el-tag v-if="comment.isResolved" type="success" size="small">已解决</el-tag>
          </div>
          <div class="comment-content" v-html="renderContent(comment.content)" />
          <div v-if="comment.quoteText" class="quote-text">"{{ comment.quoteText }}"</div>
          <div class="comment-actions">
            <el-button text size="small" @click="reply(comment)">回复</el-button>
            <el-button text size="small" @click="resolve(comment)" v-if="!comment.isResolved">
              标记解决
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="comment-input">
      <el-input v-model="newComment" type="textarea" :rows="2"
        placeholder="发表评论，输入 @ 提及同事..." />
      <el-button type="primary" @click="submitComment" :disabled="!newComment.trim()">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { commentApi, type Comment } from '@/api/comment'
import { renderMentions } from '@/utils/sanitize'

import { formatDateTime as formatDate } from '@/utils/date'

const props = defineProps<{ docId: number }>()
const emit = defineEmits(['close'])

const filter = ref('all')
const comments = ref<Comment[]>([])
const newComment = ref('')
const replyingTo = ref<Comment | null>(null)

const filteredComments = computed(() => {
  if (filter.value === 'unresolved') {
    return comments.value.filter(c => !c.isResolved)
  }
  return comments.value
})

onMounted(() => {
  loadComments()
})

async function loadComments() {
  try {
    comments.value = await commentApi.getByDocument(props.docId)
  } catch {
    ElMessage.error('加载评论失败')
  }
}

function renderContent(content: string) {
  return renderMentions(content)
}

function reply(comment: Comment) {
  replyingTo.value = comment
  newComment.value = `@{userId:${comment.userId}:${comment.userName}} `
}

async function resolve(comment: Comment) {
  try {
    await commentApi.resolve(comment.id)
    const found = comments.value.find(c => c.id === comment.id)
    if (found) found.isResolved = true
    ElMessage.success('已标记为解决')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function submitComment() {
  if (!newComment.value.trim()) return
  try {
    const mentionedUserIds: number[] = []
    const mentionRegex = /@\{userId:(\d+):[^}]*\}/g
    let match
    while ((match = mentionRegex.exec(newComment.value)) !== null) {
      mentionedUserIds.push(parseInt(match[1]))
    }
    const comment = await commentApi.create({
      documentId: props.docId,
      content: newComment.value,
      parentId: replyingTo.value?.id,
      mentionedUserIds: mentionedUserIds.length > 0 ? mentionedUserIds : undefined
    })
    comments.value.push(comment)
    newComment.value = ''
    replyingTo.value = null
  } catch {
    ElMessage.error('发送失败')
  }
}
</script>

<style scoped>
.comment-panel {
  width: 360px;
  background: white;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.comment-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.comment-body {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.user-name {
  font-weight: 500;
}

.emp-id {
  color: #909399;
  font-size: 12px;
}

.time {
  color: #909399;
  font-size: 12px;
}

.comment-content {
  font-size: 14px;
  line-height: 1.6;
}

.quote-text {
  background: #f5f7fa;
  padding: 8px 12px;
  border-left: 3px solid #409eff;
  margin: 8px 0;
  font-size: 13px;
  color: #606266;
}

.comment-actions {
  margin-top: 8px;
}

.comment-input {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:deep(.mention-tag) {
  color: #409eff;
  font-weight: 500;
}
</style>
