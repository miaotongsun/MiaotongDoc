import api from './index'

export interface ShareItem {
  id: number
  documentId: number
  userId: number
  userName?: string
  employeeId?: string
  permission: 'view' | 'comment' | 'edit' | 'admin'
  sharedBy: number
  createdAt: string
}

export interface ShareRequest {
  documentId: number
  userId: number
  permission?: string
}

export const shareApi = {
  shareDocument(data: ShareRequest) {
    return api.post<any, ShareItem>('/shares', data)
  },

  shareByDepartment(data: { documentId: number; departmentId: number; permission?: string }) {
    return api.post<any, { message: string; count: number }>('/shares/department', data)
  },

  getDocumentShares(docId: number) {
    return api.get<any, ShareItem[]>(`/shares/document/${docId}`)
  },

  updatePermission(shareId: number, permission: string) {
    return api.put<any, any>(`/shares/${shareId}/permission`, { permission })
  },

  removeShare(shareId: number) {
    return api.delete<any, any>(`/shares/${shareId}`)
  }
}
