import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ReconnectingWebSocket } from '@/utils/websocket'
import { notificationApi } from '@/api/notification'

export interface NotificationItem {
  id: number
  fromUserId?: number
  fromUserName?: string
  fromEmployeeId?: string
  documentId?: number
  documentTitle?: string
  type: string
  content: string
  isRead: boolean
  createdAt: string
}

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationItem[]>([])
  const unreadCount = ref(0)
  const hasMore = ref(true)
  const currentPage = ref(0)
  const ws = new ReconnectingWebSocket()

  function connect(token: string) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/notifications`

    ws.connect(wsUrl, token, (data) => {
      if (data.type === 'notification') {
        notifications.value.unshift(data.data)
        unreadCount.value++
      }
    })
  }

  function disconnect() {
    ws.disconnect()
  }

  async function loadNotifications() {
    try {
      currentPage.value = 0
      const res = await notificationApi.getAll({ page: 0, size: 20 })
      notifications.value = res.content || []
      unreadCount.value = notifications.value.filter(n => !n.isRead).length
      hasMore.value = !res.last
    } catch {
      // ignore
    }
  }

  async function loadMore() {
    if (!hasMore.value) return
    try {
      currentPage.value++
      const res = await notificationApi.getAll({ page: currentPage.value, size: 20 })
      const newItems = res.content || []
      notifications.value.push(...newItems)
      hasMore.value = !res.last
    } catch {
      currentPage.value--
    }
  }

  async function markAsRead(id: number) {
    const notification = notifications.value.find(n => n.id === id)
    if (notification && !notification.isRead) {
      notification.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      try {
        await notificationApi.markAsRead(id)
      } catch {
        notification.isRead = false
        unreadCount.value++
      }
    }
  }

  async function markAllAsRead() {
    const unread = notifications.value.filter(n => !n.isRead)
    unread.forEach(n => n.isRead = true)
    unreadCount.value = 0
    try {
      await notificationApi.markAllAsRead()
    } catch {
      unread.forEach(n => n.isRead = false)
      unreadCount.value = unread.length
    }
  }

  return {
    notifications,
    unreadCount,
    hasMore,
    connect,
    disconnect,
    loadNotifications,
    loadMore,
    markAsRead,
    markAllAsRead
  }
})
