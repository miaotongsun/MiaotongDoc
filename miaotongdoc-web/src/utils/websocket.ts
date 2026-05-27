export class ReconnectingWebSocket {
  private ws: WebSocket | null = null
  private retryCount = 0
  private maxRetries = 10
  private baseDelay = 1000
  private url: string = ''
  private token: string = ''
  private onMessage: ((data: any) => void) | null = null

  connect(url: string, token: string, onMessage: (data: any) => void) {
    this.url = url
    this.token = token
    this.onMessage = onMessage

    this.ws = new WebSocket(`${url}?token=${token}`)

    this.ws.onopen = () => {
      this.retryCount = 0
      console.log('[WS] 连接成功')
    }

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        this.onMessage?.(data)
      } catch (e) {
        console.error('[WS] 解析消息失败', e)
      }
    }

    this.ws.onclose = (event) => {
      if (event.code === 4001) return
      if (this.retryCount < this.maxRetries) {
        const delay = Math.min(this.baseDelay * Math.pow(2, this.retryCount), 30000)
        const jitter = delay * 0.2 * Math.random()
        this.retryCount++
        console.log(`[WS] ${delay + jitter}ms 后第 ${this.retryCount} 次重连`)
        setTimeout(() => this.connect(this.url, this.token, this.onMessage!), delay + jitter)
      }
    }

    this.ws.onerror = () => this.ws?.close()
  }

  disconnect() {
    this.ws?.close(1000, '用户离开')
    this.ws = null
  }

  send(data: any) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }
}
