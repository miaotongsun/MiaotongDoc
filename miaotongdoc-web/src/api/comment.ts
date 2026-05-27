import api from './index'

export interface Comment {
  id: number
  documentId: number
  parentId?: number
  userId: number
  userName: string
  employeeId?: string
  content: string
  quoteText?: string
  pageNumber?: number
  position?: string
  isResolved?: boolean
  resolvedBy?: number
  resolvedAt?: string
  createdAt: string
  updatedAt: string
  replies?: Comment[]
  mentions: Mention[]
}

export interface Mention {
  id: number
  commentId: number
  mentionedUserId: number
  mentionedUserName: string
}

export interface CreateCommentRequest {
  documentId: number
  content: string
  parentId?: number
  mentionedUserIds?: number[]
}

export const commentApi = {
  getByDocument(documentId: number) {
    return api.get<any, Comment[]>(`/comments/document/${documentId}`)
  },

  create(data: CreateCommentRequest) {
    return api.post<any, Comment>('/comments', data)
  },

  delete(commentId: number) {
    return api.delete<any, any>(`/comments/${commentId}`)
  },

  resolve(commentId: number) {
    return api.put<any, any>(`/comments/${commentId}/resolve`)
  }
}
