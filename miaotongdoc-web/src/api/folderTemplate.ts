import api from './index'

export interface FolderTemplate {
  id: number
  name: string
  description?: string
  structure: any[]
  isActive: boolean
  createdAt: string
}

export const folderTemplateApi = {
  getAll() {
    return api.get<any, FolderTemplate[]>('/folder-templates')
  },

  getActive() {
    return api.get<any, FolderTemplate[]>('/folder-templates', { params: { active: true } })
  },

  getById(id: number) {
    return api.get<any, FolderTemplate>(`/folder-templates/${id}`)
  },

  create(data: { name: string; description?: string; structure: any[]; isActive?: boolean }) {
    return api.post<any, FolderTemplate>('/folder-templates', data)
  },

  update(id: number, data: { name?: string; description?: string; structure?: any[]; isActive?: boolean }) {
    return api.put<any, FolderTemplate>(`/folder-templates/${id}`, data)
  },

  delete(id: number) {
    return api.delete(`/folder-templates/${id}`)
  },

  reorder(ids: number[]) {
    return api.put('/folder-templates/reorder', { ids })
  }
}
