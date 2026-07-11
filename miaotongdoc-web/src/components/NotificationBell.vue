<template>
  <el-popover placement="bottom" :width="360" trigger="click">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
        <el-button :icon="Bell" circle />
      </el-badge>
    </template>

    <div class="notification-list">
      <div class="notification-header">
        <span>通知</span>
        <el-button text size="small" @click="markAllAsRead">全部已读</el-button>
      </div>
      <el-scrollbar max-height="400px">
        <div v-for="notification in notifications" :key="notification.id"
          class="notification-item" :class="{ unread: !notification.isRead }"
          @click="handleClick(notification)">
          <div class="notification-dot" v-if="!notification.isRead" />
          <div class="notification-body">
            <div class="notification-content" v-html="renderNotificationContent(notification)" />
            <div class="notification-time">{{ formatDate(notification.createdAt) }}</div>
          </div>
        </div>
        <div v-if="notifications.length > 0 && hasMore" class="notification-load-more" @click="loadMore">
          加载更多
        </div>
        <el-empty v-if="notifications.length === 0" description="暂无通知" :image-size="60" />
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore, type NotificationItem } from '@/stores/notification'
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/date'
import { escapeHtml } from '@/utils/sanitize'

const router = useRouter()
const notificationStore = useNotificationStore()

const notifications = computed(() => notificationStore.notifications)
const unreadCount = computed(() => notificationStore.unreadCount)
const hasMore = computed(() => notificationStore.hasMore)

onMounted(() => {
  const token = sessionStorage.getItem('token')
  if (token) {
    notificationStore.connect(token)
    notificationStore.loadNotifications()
  }
})

onBeforeUnmount(() => {
  notificationStore.disconnect()
})

function renderNotificationContent(notification: NotificationItem): string {
  const fromUser = escapeHtml(notification.fromUserName || '系统')
  const empId = notification.fromEmployeeId ? `(${escapeHtml(notification.fromEmployeeId)})` : ''
  const docTitle = escapeHtml(notification.documentTitle || '未知文档')
  const userSpan = `<span class="notify-user">${fromUser}${empId}</span>`
  const docSpan = `<span class="notify-doc">《${docTitle}》</span>`

  switch (notification.type) {
    case 'SHARE':
      return `${userSpan} 邀请您协同编辑 ${docSpan}`
    case 'REVOKE':
      return `${userSpan} <span class="notify-danger">已撤回</span>您对 ${docSpan} 的共享权限`
    case 'PERMISSION_CHANGE':
      return `${userSpan} <span class="notify-action">变更了</span>您对 ${docSpan} 的权限`
    case 'SIGN_REQUEST':
      return `${userSpan} 邀请您 <span class="notify-action">签署</span> ${docSpan}`
    case 'SIGN_CONFIRM':
      return `${userSpan} <span class="notify-action">已确认签署</span> ${docSpan}`
    case 'SIGN_REJECT':
      return `${userSpan} <span class="notify-danger">已拒绝签署</span> ${docSpan}`
    case 'SIGN_CANCEL':
      return `${userSpan} <span class="notify-danger">已取消</span> ${docSpan} 的签署任务`
    case 'SIGN_EXPIRED':
      return `${docSpan} 的签署任务 <span class="notify-danger">已超期</span>`
    case 'COMMENT':
      return notification.content
        ? `${userSpan} 评论了 ${docSpan}: <span class="notify-quote">"${escapeHtml(notification.content.length > 30 ? notification.content.slice(0, 30) + '...' : notification.content)}"</span>`
        : `${userSpan} 评论了 ${docSpan}`
    case 'MENTION':
      return notification.content
        ? `${userSpan} 在评论 ${docSpan} 时@您: <span class="notify-quote">"${escapeHtml(notification.content.length > 30 ? notification.content.slice(0, 30) + '...' : notification.content)}"</span>`
        : `${userSpan} 在评论 ${docSpan} 时提到了您`
    case 'DOC_MENTION':
      return `${userSpan} 在 ${docSpan} 中@了您`
    case 'VERSION':
      return `${userSpan} 为 ${docSpan} <span class="notify-action">保存了新版本</span>`
    default:
      return notification.content ? escapeHtml(notification.content) : `${userSpan} 发送了一条通知`
  }
}

function handleClick(notification: NotificationItem) {
  notificationStore.markAsRead(notification.id)
  if (notification.documentId) {
    router.push(`/editor/${notification.documentId}`)
  }
}

function markAllAsRead() {
  notificationStore.markAllAsRead()
}

function loadMore() {
  notificationStore.loadMore()
}
</script>

<style scoped>
.notification-badge {
  margin-right: 16px;
}

.notification-list {
  max-height: 500px;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
  margin-bottom: 8px;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item.unread {
  background: #ecf5ff;
}

.notification-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
  margin-top: 6px;
}

.notification-body {
  flex: 1;
  min-width: 0;
}

.notification-content {
  font-size: 13px;
  color: #303133;
  margin-bottom: 4px;
  line-height: 1.5;
}

.notification-time {
  font-size: 12px;
  color: #909399;
}

:deep(.notify-user) {
  color: #409eff;
  font-weight: 500;
}

:deep(.notify-doc) {
  color: #67c23a;
  font-weight: 500;
}

:deep(.notify-action) {
  color: #e6a23c;
  font-weight: 500;
}

:deep(.notify-danger) {
  color: #f56c6c;
  font-weight: 500;
}

:deep(.notify-quote) {
  color: #909399;
  font-size: 12px;
  font-style: italic;
}

.notification-load-more {
  text-align: center;
  padding: 12px;
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  border-top: 1px solid #f0f0f0;
}

.notification-load-more:hover {
  background: #f5f7fa;
}
</style>
