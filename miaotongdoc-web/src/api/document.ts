import api from './index'

export interface Document {
  id: number
  docKey: string
  title: string
  docType: string
  fileType: string
  fileSize: number
  ownerUserId: number
  ownerName: string
  departmentId?: number
  departmentName?: string
  status: string
  currentVersion: number
  isStarred: boolean
  signingLocked: boolean
  currentUserPermission?: string
  createdAt: string
  updatedAt: string
}

export interface CreateDocumentRequest {
  docType: string
  title?: string
}

export const documentApi = {
  create(data: CreateDocumentRequest) {
    return api.post<any, Document>('/documents/create', data)
  },

  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<any, Document>('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  list(params?: { type?: string; keyword?: string; departmentId?: number; sort?: string; page?: number; size?: number }) {
    return api.get<any, any>('/documents/list', { params })
  },

  getById(id: number) {
    return api.get<any, Document>(`/documents/${id}`)
  },

  rename(id: number, title: string) {
    return api.put<any, Document>(`/documents/${id}/rename`, { title })
  },

  delete(id: number) {
    return api.delete(`/documents/${id}`)
  },

  batchDelete(ids: number[]) {
    return api.delete<any, { deleted: number }>('/documents/batch', { data: ids })
  },

  restore(id: number) {
    return api.put(`/documents/${id}/restore`)
  },

  toggleStar(id: number) {
    return api.put<any, Document>(`/documents/${id}/star`)
  },

  getConfig(id: number) {
    return api.get(`/documents/${id}/config`)
  },

  exportPdf(id: number) {
    return api.get<any, Blob>(`/documents/${id}/export/pdf`, {
      responseType: 'blob' as any
    })
  },

  createVersion(id: number, summary?: string) {
    return api.post<any, { message: string; versionNumber: number }>(`/documents/${id}/versions`, { summary })
  }
}
