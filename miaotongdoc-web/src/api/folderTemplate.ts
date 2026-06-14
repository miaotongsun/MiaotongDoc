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

  getById(id: number) {
    return api.get<any, FolderTemplate>(`/folder-templates/${id}`)
  }
}
