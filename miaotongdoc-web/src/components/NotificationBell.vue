<template>
  <el-popover placement="bottom" :width="300" trigger="click">
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
          <div class="notification-content">
            <span>{{ notification.content }}</span>
          </div>
          <div class="notification-time">{{ formatDate(notification.createdAt) }}</div>
        </div>
        <el-empty v-if="notifications.length === 0" description="暂无通知" :image-size="60" />
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import { useRouter } from 'vue-router'

const router = useRouter()
const notificationStore = useNotificationStore()

const notifications = computed(() => notificationStore.notifications)
const unreadCount = computed(() => notificationStore.unreadCount)

onMounted(() => {
  const token = sessionStorage.getItem('token')
  if (token) {
    notificationStore.connect(token)
  }
})

onBeforeUnmount(() => {
  notificationStore.disconnect()
})

import { formatDate } from '@/utils/date'

function handleClick(notification: any) {
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

.notification-content {
  font-size: 13px;
  color: #303133;
  margin-bottom: 4px;
}

.notification-time {
  font-size: 12px;
  color: #909399;
}
</style>
