import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ReconnectingWebSocket } from '@/utils/websocket'
import { presenceApi } from '@/api/presence'

export interface PresenceUser {
  userId: number
  userName: string
  color: string
  joinedAt: string
}

export const usePresenceStore = defineStore('presence', () => {
  const onlineUsers = ref<Map<number, PresenceUser[]>>(new Map())
  const ws = new ReconnectingWebSocket()
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  function connect(docId: number) {
    const token = sessionStorage.getItem('token') || ''
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/presence/${docId}`

    ws.connect(wsUrl, token, (data) => {
      if (data.type === 'presence') {
        const users = onlineUsers.value.get(docId) || []

        if (data.action === 'join') {
          const existing = users.find(u => u.userId === data.userId)
          if (!existing) {
            users.push({
              userId: data.userId,
              userName: data.userInfo.userName,
              color: data.userInfo.color,
              joinedAt: data.userInfo.joinedAt
            })
          }
        } else if (data.action === 'leave') {
          const index = users.findIndex(u => u.userId === data.userId)
          if (index > -1) {
            users.splice(index, 1)
          }
        }

        onlineUsers.value.set(docId, users)
      }
    })

    // 注册在线状态到后端
    presenceApi.joinDocument(docId).catch(console.error)
    // 每30秒发送一次心跳
    heartbeatTimer = setInterval(() => {
      presenceApi.heartbeat(docId).catch(console.error)
    }, 30000)
  }

  function disconnect(docId: number) {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    presenceApi.leaveDocument(docId).catch(console.error)
    ws.disconnect()
    onlineUsers.value.delete(docId)
  }

  function getOnlineUsers(docId: number): PresenceUser[] {
    return onlineUsers.value.get(docId) || []
  }

  return {
    onlineUsers,
    connect,
    disconnect,
    getOnlineUsers
  }
})
