import api from './index'

export interface UserItem {
  id: number
  employeeId: string
  username: string
  realName: string
  email?: string
  phone?: string
  avatarUrl?: string
  departmentId?: number
  position?: string
  role?: string
  isActive?: boolean
}

export const userApi = {
  getAll() {
    return api.get<any, UserItem[]>('/auth/users')
  },

  search(keyword: string) {
    return api.get<any, UserItem[]>('/auth/users/search', { params: { keyword } })
  },

  create(data: { employeeId: string; username: string; password: string; realName: string; email?: string; phone?: string; departmentId?: number; position?: string; role?: string }) {
    return api.post<any, UserItem>('/admin/users', data)
  },

  update(id: number, data: { realName?: string; email?: string; phone?: string; departmentId?: number; position?: string }) {
    return api.put<any, UserItem>(`/admin/users/${id}`, data)
  },

  deactivate(id: number) {
    return api.delete(`/admin/users/${id}`)
  },

  resetPassword(id: number) {
    return api.put(`/admin/users/${id}/reset-password`)
  },

  updateRole(id: number, role: string) {
    return api.put(`/admin/users/${id}/role`, { role })
  },

  updateStatus(id: number) {
    return api.put(`/admin/users/${id}/status`)
  }
}
