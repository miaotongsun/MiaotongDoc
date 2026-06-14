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
  const docTitle = escapeHtml(notification.documentTitle || '未知文档')
  const userSpan = `<span class="notify-user">${fromUser}</span>`
  const docSpan = `<span class="notify-doc">《${docTitle}》</span>`

  switch (notification.type) {
    case 'share':
      return `${userSpan} 邀请您协同编辑 ${docSpan}`
    case 'signing':
      return `${userSpan} 邀请您签署 ${docSpan}`
    case 'comment':
      return `${userSpan} 评论了 ${docSpan}`
    case 'mention':
      return `${userSpan} 在 ${docSpan} 中提到了您`
    case 'version':
      return `${userSpan} 更新了 ${docSpan}`
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
</style>
