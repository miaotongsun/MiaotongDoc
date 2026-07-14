/**
 * usePdfOcrProgress —— PDF OCR 识别进度（SSE 流式订阅）
 *
 * 设计：
 *   - 用 fetch + ReadableStream 替代原生 EventSource（后者不支持自定义 Header / POST）
 *   - 自动重连机制（断线 3s 后重试，最多 5 次）
 *   - 解析 SSE 格式：`event: <name>\ndata: <json>\n\n`
 *   - 暴露 percent / message / engine / done / error 状态
 *
 * 注意：浏览器 SSE 实现 EventSource 会自动重连，但无法带 JWT；本实现走 fetch 流
 */

import { ref, onUnmounted } from 'vue'

export type OcrProgressEvent =
  | { type: 'connected'; ts: number }
  | { type: 'progress'; percent: number; message: string; ts: number }
  | { type: 'done'; engine: string; ts: number }
  | { type: 'error'; error: string; ts: number }
  | { type: 'closed' }

export interface UsePdfOcrProgressOptions {
  docId: number
  endpoint?: string  // 默认 /api/pdf/{id}/recognize-stream
}

export function usePdfOcrProgress(options: UsePdfOcrProgressOptions) {
  const percent = ref(0)
  const message = ref('')
  const engine = ref<string>('')
  const status = ref<'idle' | 'connecting' | 'streaming' | 'done' | 'error'>('idle')
  const error = ref<string | null>(null)

  let abortCtrl: AbortController | null = null
  let retryCount = 0
  const MAX_RETRY = 5

  async function start() {
    if (status.value === 'streaming' || status.value === 'connecting') return
    retryCount = 0
    return connect()
  }

  async function connect() {
    if (abortCtrl) abortCtrl.abort()
    abortCtrl = new AbortController()

    status.value = 'connecting'
    error.value = null

    const endpoint = options.endpoint ?? `/api/pdf/${options.docId}/recognize-stream`
    const token = sessionStorage.getItem('token')

    try {
      const resp = await fetch(endpoint, {
        method: 'GET',
        headers: {
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        signal: abortCtrl.signal,
      })

      if (!resp.ok || !resp.body) {
        throw new Error(`HTTP ${resp.status}`)
      }

      status.value = 'streaming'
      retryCount = 0  // 连接成功，重置重试

      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      // 解析 SSE 数据块
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // 按 \n\n 切分完整事件
        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const eventBlock = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          parseEvent(eventBlock)
        }
      }

      // 流结束
      if (status.value === 'streaming') {
        status.value = 'done'
      }
    } catch (e: any) {
      if (e?.name === 'AbortError') return  // 主动取消
      error.value = e?.message ?? String(e)
      status.value = 'error'

      // 自动重连
      if (retryCount < MAX_RETRY) {
        retryCount++
        await new Promise(r => setTimeout(r, 3000))
        if (abortCtrl && !abortCtrl.signal.aborted) {
          return connect()
        }
      }
    }
  }

  function parseEvent(block: string) {
    let eventName = 'message'
    const dataLines: string[] = []

    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    const dataStr = dataLines.join('\n')

    try {
      const payload = dataStr ? JSON.parse(dataStr) : {}

      if (eventName === 'connected') {
        // 已连接，忽略
      } else if (eventName === 'progress') {
        percent.value = payload.percent ?? percent.value
        message.value = payload.message ?? message.value
      } else if (eventName === 'done') {
        percent.value = 100
        engine.value = payload.engine ?? ''
        message.value = '识别完成'
        status.value = 'done'
        // 自动关闭
        setTimeout(() => stop(), 500)
      } else if (eventName === 'error') {
        error.value = payload.error ?? '识别失败'
        status.value = 'error'
        setTimeout(() => stop(), 500)
      }
    } catch (e) {
      console.warn('SSE 数据解析失败:', dataStr, e)
    }
  }

  function stop() {
    if (abortCtrl) {
      abortCtrl.abort()
      abortCtrl = null
    }
    if (status.value !== 'done' && status.value !== 'error') {
      status.value = 'idle'
    }
  }

  function reset() {
    stop()
    percent.value = 0
    message.value = ''
    engine.value = ''
    error.value = null
    status.value = 'idle'
  }

  onUnmounted(stop)

  return { percent, message, engine, status, error, start, stop, reset }
}