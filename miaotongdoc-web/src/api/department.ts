import api from './index'

export interface Department {
  id: number
  code: string
  name: string
  parentId?: number
  level: number
  path: string
  sortOrder: number
}

export const departmentApi = {
  getAll() {
    return api.get<any, Department[]>('/departments')
  },

  getChildren(id: number) {
    return api.get<any, Department[]>(`/departments/${id}/children`)
  },

  create(data: { code: string; name: string; parentId?: number; sortOrder?: number }) {
    return api.post<any, Department>('/departments', data)
  },

  update(id: number, data: { name?: string; code?: string; sortOrder?: number }) {
    return api.put<any, Department>(`/departments/${id}`, data)
  },

  deactivate(id: number) {
    return api.delete(`/departments/${id}`)
  }
}
