import api from './index'

export interface ActivityItem {
  id: number
  documentId: number
  documentTitle: string
  userId: number
  userName: string
  action: string
  targetUserId?: number
  targetUserName?: string
  detail?: string
  createdAt: string
}

export interface ActivityPage {
  content: ActivityItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const activityApi = {
  getFeed(params?: { page?: number; size?: number }) {
    return api.get<any, ActivityPage>('/activities/feed', { params })
  },

  getDocumentActivities(docId: number, params?: { page?: number; size?: number }) {
    return api.get<any, ActivityPage>(`/activities/document/${docId}`, { params })
  }
}
