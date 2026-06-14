<template>
  <div class="comment-panel" :style="{ width: panelWidth + 'px' }">
    <div class="resize-handle" :class="{ resizing: isResizing }" @mousedown="startResize" @dblclick="resetWidth">
      <div class="resize-line" />
    </div>
    <div class="panel-header">
      <span>评论讨论</span>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="unresolved">待解决</el-radio-button>
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
      <div class="input-row">
        <div class="input-wrapper">
          <el-input ref="commentInputRef" v-model="displayText" type="textarea" :rows="2"
            placeholder="发表评论，输入 @ 提及同事..." @input="onInput" @keydown="handleKeydown" />
          <div v-if="showMentionDropdown" class="mention-dropdown">
            <div v-if="mentionLoading" class="mention-loading">搜索中...</div>
            <div v-else-if="mentionUsers.length === 0" class="mention-empty">无匹配用户</div>
            <div v-else class="mention-list">
              <div v-for="(user, index) in mentionUsers" :key="user.id" class="mention-item"
                :class="{ active: mentionActiveIndex === index }"
                @mousedown.prevent="selectMention(user)">
                <span class="mention-name">{{ user.realName }}</span>
                <span class="mention-empid">{{ user.employeeId }}</span>
              </div>
            </div>
          </div>
        </div>
        <el-button type="primary" @click="submitComment" :disabled="!displayText.trim()">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { commentApi, type Comment } from '@/api/comment'
import { userApi, type UserItem } from '@/api/user'
import { renderMentions } from '@/utils/sanitize'
import { formatDateTime as formatDate } from '@/utils/date'

const props = defineProps<{ docId: number }>()
const emit = defineEmits(['close'])

const filter = ref('all')
const comments = ref<Comment[]>([])
const displayText = ref('')
const replyingTo = ref<Comment | null>(null)
const commentInputRef = ref<any>(null)

// 存储提及用户信息 { 显示文本: { id, name, empId } }
const mentionMap = new Map<string, { id: number; name: string; empId: string }>()

// 转换显示文本为内部格式
function toInternalFormat(text: string): string {
  let result = text
  mentionMap.forEach((user, display) => {
    result = result.replace(new RegExp(display.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), `@{userId:${user.id}:${user.name}:${user.empId}}`)
  })
  return result
}

// Resizable panel
const DEFAULT_WIDTH = 360
const MIN_WIDTH = 280
const MAX_WIDTH = 600
const panelWidth = ref(DEFAULT_WIDTH)
const isResizing = ref(false)
let startX = 0
let startWidth = 0

function startResize(e: MouseEvent) {
  e.preventDefault()
  isResizing.value = true
  startX = e.clientX
  startWidth = panelWidth.value
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  addResizeOverlay()
}

function onResize(e: MouseEvent) {
  if (!isResizing.value) return
  const diff = startX - e.clientX
  panelWidth.value = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, startWidth + diff))
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  removeResizeOverlay()
}

function resetWidth() {
  panelWidth.value = DEFAULT_WIDTH
}

function addResizeOverlay() {
  const overlay = document.createElement('div')
  overlay.id = 'resize-overlay'
  overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:9999;cursor:col-resize'
  document.body.appendChild(overlay)
}

function removeResizeOverlay() {
  const overlay = document.getElementById('resize-overlay')
  if (overlay) overlay.remove()
}

// @mention dropdown
const showMentionDropdown = ref(false)
const mentionUsers = ref<UserItem[]>([])
const mentionLoading = ref(false)
const mentionActiveIndex = ref(0)
let mentionSearchText = ''
let mentionStartPos = 0
let isSelectingMention = false

function onInput() {
  if (!isSelectingMention) {
    checkMention()
  }
}

function getCursorPosition(): number {
  if (commentInputRef.value) {
    const textarea = commentInputRef.value.$el?.querySelector('textarea')
    if (textarea) return textarea.selectionStart
  }
  return displayText.value.length
}

function checkMention() {
  const cursorPos = getCursorPosition()
  const textBefore = displayText.value.substring(0, cursorPos)

  const atIndex = textBefore.lastIndexOf('@')
  if (atIndex === -1) {
    hideMentionDropdown()
    return
  }

  const textAfterAt = textBefore.substring(atIndex + 1)
  if (textAfterAt.includes(' ') || textAfterAt.includes('\n')) {
    hideMentionDropdown()
    return
  }

  mentionStartPos = atIndex
  mentionSearchText = textAfterAt
  showMentionDropdown.value = true
  mentionActiveIndex.value = 0
  searchUsers(mentionSearchText)
}

async function searchUsers(keyword: string) {
  mentionLoading.value = true
  try {
    mentionUsers.value = await userApi.search(keyword)
  } catch {
    mentionUsers.value = []
  } finally {
    mentionLoading.value = false
  }
}

function hideMentionDropdown() {
  showMentionDropdown.value = false
  mentionUsers.value = []
}

function selectMention(user: UserItem) {
  isSelectingMention = true
  const before = displayText.value.substring(0, mentionStartPos)
  const after = displayText.value.substring(mentionStartPos + mentionSearchText.length + 1)
  const display = `@${user.realName}(${user.employeeId}) `

  // 存储映射关系
  mentionMap.set(display.replace(/\s+$/, ''), { id: user.id, name: user.realName, empId: user.employeeId || '' })

  displayText.value = before + display + after
  hideMentionDropdown()

  nextTick(() => {
    isSelectingMention = false
    if (commentInputRef.value) {
      const textarea = commentInputRef.value.$el?.querySelector('textarea')
      if (textarea) {
        textarea.focus()
        const newPos = before.length + display.length
        textarea.setSelectionRange(newPos, newPos)
      }
    }
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (showMentionDropdown.value && mentionUsers.value.length > 0) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionActiveIndex.value = (mentionActiveIndex.value + 1) % mentionUsers.value.length
      return
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionActiveIndex.value = (mentionActiveIndex.value - 1 + mentionUsers.value.length) % mentionUsers.value.length
      return
    } else if (e.key === 'Enter') {
      e.preventDefault()
      selectMention(mentionUsers.value[mentionActiveIndex.value])
      return
    } else if (e.key === 'Escape') {
      hideMentionDropdown()
      return
    }
  }

  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    submitComment()
  }
}

const filteredComments = computed(() => {
  if (filter.value === 'unresolved') {
    return comments.value.filter(c => !c.isResolved)
  }
  return comments.value
})

onMounted(() => {
  loadComments()
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  removeResizeOverlay()
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
  const empId = comment.employeeId || ''
  const display = `@${comment.userName}(${empId})`
  mentionMap.set(display, { id: comment.userId, name: comment.userName, empId })
  displayText.value = display + ' '
  nextTick(() => {
    if (commentInputRef.value) {
      const textarea = commentInputRef.value.$el?.querySelector('textarea')
      if (textarea) textarea.focus()
    }
  })
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
  if (!displayText.value.trim()) return
  try {
    // 转换为内部格式
    const internalContent = toInternalFormat(displayText.value)

    const mentionedUserIds: number[] = []
    mentionMap.forEach(user => {
      if (!mentionedUserIds.includes(user.id)) {
        mentionedUserIds.push(user.id)
      }
    })

    const comment = await commentApi.create({
      documentId: props.docId,
      content: internalContent,
      parentId: replyingTo.value?.id,
      mentionedUserIds: mentionedUserIds.length > 0 ? mentionedUserIds : undefined
    })
    comments.value.push(comment)
    displayText.value = ''
    mentionMap.clear()
    replyingTo.value = null
  } catch {
    ElMessage.error('发送失败')
  }
}
</script>

<style scoped>
.comment-panel {
  min-width: 280px;
  max-width: 600px;
  background: white;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  position: relative;
}

.resize-handle {
  position: absolute;
  left: -8px;
  top: 0;
  bottom: 0;
  width: 16px;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resize-handle .resize-line {
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.2s, width 0.2s;
}

.resize-handle:hover .resize-line,
.resize-handle.resizing .resize-line {
  width: 3px;
  background: #409eff;
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
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row .input-wrapper {
  flex: 1;
}

.input-row .el-button {
  flex-shrink: 0;
  height: 56px;
}

.input-wrapper {
  position: relative;
}

.mention-dropdown {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  max-height: 200px;
  overflow-y: auto;
  z-index: 100;
}

.mention-loading,
.mention-empty {
  padding: 12px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.mention-list {
  padding: 4px 0;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.mention-item:hover,
.mention-item.active {
  background: #f5f7fa;
}

.mention-name {
  font-weight: 500;
}

.mention-empid {
  color: #909399;
  font-size: 12px;
}

:deep(.mention-tag) {
  color: #409eff;
  font-weight: 500;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
</style>
