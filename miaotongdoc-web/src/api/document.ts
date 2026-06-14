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
  departmentName?: number
  folderId?: number
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
  templateId?: number
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

  suggest(keyword: string) {
    return api.get<any, { suggestions: any[] }>('/documents/suggest', { params: { keyword } })
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
  },

  // 回收站
  getTrash() {
    return api.get<any, Document[]>('/documents/trash')
  },

  restoreFromTrash(id: number) {
    return api.post(`/documents/${id}/restore`)
  },

  permanentDelete(id: number) {
    return api.delete(`/documents/${id}/permanent`)
  },

  emptyTrash() {
    return api.delete<any, { deleted: number }>('/documents/trash/empty')
  },

  // 移动到文件夹
  moveToFolder(id: number, folderId: number | null) {
    return api.post(`/documents/${id}/move`, { folderId })
  },

  // 批量导出
  exportZip(ids: number[]) {
    return api.post<any, Blob>('/documents/export/zip', ids, {
      responseType: 'blob' as any
    })
  }
}
