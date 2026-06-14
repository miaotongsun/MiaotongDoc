import api from './index'

export interface Notification {
  id: number
  userId: number
  fromUserId?: number
  fromUserName?: string
  documentId?: number
  documentTitle?: string
  type: string
  title: string
  content: string
  relatedId?: number
  relatedType?: string
  isRead: boolean
  readAt?: string
  createdAt: string
}

export const notificationApi = {
  getAll(params?: { page?: number; size?: number; unreadOnly?: boolean }) {
    return api.get<any, any>('/notifications', { params })
  },

  getUnreadCount() {
    return api.get<any, number>('/notifications/unread-count')
  },

  markAsRead(id: number) {
    return api.put<any, any>(`/notifications/${id}/read`)
  },

  markAllAsRead() {
    return api.put<any, any>('/notifications/read-all')
  },

  delete(id: number) {
    return api.delete<any, any>(`/notifications/${id}`)
  }
}
