import api from './index'

export interface DocumentTemplate {
  id: number
  name: string
  description?: string
  docType: string
  filePath: string
  fileSize: number
  category?: string
  isSystem: boolean
  isActive: boolean
  sortOrder: number
  createdBy?: number
  createdAt: string
  updatedAt: string
}

export const templateApi = {
  getAll(params?: { docType?: string; category?: string }) {
    return api.get<any, DocumentTemplate[]>('/templates', { params })
  },

  getCategories() {
    return api.get<any, string[]>('/templates/categories')
  },

  addCategory(name: string) {
    return api.post<any, any>('/templates/categories', { name })
  },

  deleteCategory(id: number) {
    return api.delete(`/templates/categories/${id}`)
  },

  getById(id: number) {
    return api.get<any, DocumentTemplate>(`/templates/${id}`)
  },

  create(data: FormData) {
    return api.post<any, DocumentTemplate>('/templates', data, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  update(id: number, data: Partial<DocumentTemplate>) {
    return api.put<any, DocumentTemplate>(`/templates/${id}`, data)
  },

  delete(id: number) {
    return api.delete(`/templates/${id}`)
  },

  download(id: number) {
    return api.get<any, Blob>(`/templates/${id}/download`, {
      responseType: 'blob' as any
    })
  }
}
