import api from './index'

export interface Folder {
  id: number
  name: string
  parentId?: number
  ownerUserId: number
  departmentId?: number
  color?: string
  createdAt: string
  updatedAt: string
}

export const folderApi = {
  getAll(parentId?: number) {
    return api.get<any, Folder[]>('/folders', { params: { parentId } })
  },

  create(data: { name: string; parentId?: number; departmentId?: number; color?: string }) {
    return api.post<any, Folder>('/folders', data)
  },

  /** 更新文件夹属性（名称、颜色、上级文件夹） */
  update(id: number, data: { name?: string; color?: string; parentId?: number | null }) {
    return api.put<any, Folder>(`/folders/${id}`, data)
  },

  rename(id: number, name: string) {
    return api.put<any, Folder>(`/folders/${id}`, { name })
  },

  updateColor(id: number, color: string) {
    return api.put<any, Folder>(`/folders/${id}`, { color })
  },

  delete(id: number, moveToParentId?: number | null) {
    return api.delete(`/folders/${id}`, { params: { moveToParentId } })
  },

  download(id: number) {
    return api.get<any, Blob>(`/folders/${id}/download`, {
      responseType: 'blob' as any
    })
  }
}
