/**
 * AI Chat WebSocket 服务
 * 支持流式输出和思考模式
 */

let ws: WebSocket | null = null
let pingTimer: number | null = null
let currentToken: string = ''
let messageHandler: ((data: any) => void) | null = null

/**
 * 连接到 AI WebSocket 服务器
 */
export function connectAiChat(token: string): Promise<void> {
  return new Promise((resolve, reject) => {
    // 如果已经连接且是同一个 token，直接返回
    if (ws && ws.readyState === WebSocket.OPEN && currentToken === token) {
      resolve()
      return
    }

    // 关闭旧连接
    disconnectAiChat()
    currentToken = token

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const wsUrl = `${protocol}//${host}/ws/ai/chat?token=${encodeURIComponent(token)}`

    console.log('[AI WS] 正在连接...')
    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('[AI WS] 连接已建立')
      startPing()
      resolve()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (messageHandler) {
          messageHandler(data)
        }
      } catch (e) {
        console.error('[AI WS] 解析消息失败:', e)
      }
    }

    ws.onerror = (error) => {
      console.error('[AI WS] 连接错误:', error)
    }

    ws.onclose = (event) => {
      console.log('[AI WS] 连接已关闭:', event.code)
      stopPing()
      // 不自动重连，让用户手动重连
    }
  })
}

/**
 * 断开连接
 */
export function disconnectAiChat() {
  if (ws) {
    ws.close(1000, '主动断开')
    ws = null
  }
  stopPing()
  currentToken = ''
  messageHandler = null
}

/**
 * 发送聊天请求
 */
export function sendChat(question: string, systemPrompt?: string, docId?: string) {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    console.error('[AI WS] 连接未建立')
    return false
  }
  ws.send(JSON.stringify({
    action: 'chat',
    question,
    systemPrompt: systemPrompt || '',
    docId: docId || ''
  }))
  return true
}

/**
 * 设置消息处理器
 */
export function setAiMessageHandler(handler: (data: any) => void) {
  messageHandler = handler
}

/**
 * 清除消息处理器
 */
export function clearAiMessageHandler() {
  messageHandler = null
}

/**
 * 启动心跳
 */
function startPing() {
  stopPing()
  pingTimer = window.setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ action: 'ping' }))
    }
  }, 30000)
}

/**
 * 停止心跳
 */
function stopPing() {
  if (pingTimer !== null) {
    clearInterval(pingTimer)
    pingTimer = null
  }
}

/**
 * 检查连接状态
 */
export function isConnected(): boolean {
  return ws !== null && ws.readyState === WebSocket.OPEN
}
