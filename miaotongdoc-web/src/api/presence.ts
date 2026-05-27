import api from './index'

export interface PresenceUser {
  userId: number
  userName: string
  color: string
  cursorPosition?: { line: number; column: number }
  lastActive: string
}

export const presenceApi = {
  getOnlineUsers(documentId: number) {
    return api.get<any, PresenceUser[]>(`/presence/document/${documentId}`)
  },

  joinDocument(documentId: number) {
    return api.post<any, any>(`/presence/document/${documentId}/join`)
  },

  leaveDocument(documentId: number) {
    return api.post<any, any>(`/presence/document/${documentId}/leave`)
  },

  heartbeat(documentId: number) {
    return api.post<any, any>(`/presence/document/${documentId}/heartbeat`)
  }
}
