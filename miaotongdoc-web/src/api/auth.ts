import api from './index'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: number
  employeeId: string
  username: string
  realName: string
  role: string
}

export const authApi = {
  login(data: LoginRequest) {
    return api.post<any, LoginResponse>('/auth/login', data)
  },

  getCurrentUser() {
    return api.get<any, any>('/auth/me')
  },

  changePassword(data: { oldPassword: string; newPassword: string }) {
    return api.put<any, any>('/auth/password', data)
  }
}
