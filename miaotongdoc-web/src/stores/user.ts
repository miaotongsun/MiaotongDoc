import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export interface UserInfo {
  userId: number
  employeeId: string
  username: string
  realName: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(sessionStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.token
    sessionStorage.setItem('token', res.token)
    sessionStorage.setItem('userId', String(res.userId))
    sessionStorage.setItem('employeeId', res.employeeId)
    sessionStorage.setItem('name', res.realName)
    sessionStorage.setItem('role', res.role)
    userInfo.value = {
      userId: res.userId,
      employeeId: res.employeeId,
      username: res.username,
      realName: res.realName,
      role: res.role
    }
    return res
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    sessionStorage.clear()
  }

  async function fetchUserInfo() {
    try {
      const res = await authApi.getCurrentUser()
      userInfo.value = res
    } catch (e) {
      logout()
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    logout,
    fetchUserInfo
  }
})
