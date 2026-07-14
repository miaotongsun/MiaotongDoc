/**
 * usePdfAiVision —— PDF 视觉问答（VLM 流式订阅）
 *
 * 用例：用户在 PDF 上框选区域 → 截图 + 问题 → 后端多模态 LLM 回答
 *
 * 设计：
 *   - 复用 useAiChat 的 fetch + ReadableStream 模式
 *   - 调用 /api/pdf/{id}/ai/vision/stream（SSE）
 *   - 暴露 status / content / error，自动管理生命周期
 *
 * 注意：与 useAiChat 不同，这里发送的是 POST（携带 image base64 + question）
 *       使用 fetch 流而非 EventSource（EventSource 不支持 POST）
 */

import { ref, onUnmounted } from 'vue'

export type VisionStatus = 'idle' | 'submitted' | 'streaming' | 'done' | 'error'

export interface UsePdfAiVisionOptions {
  docId: number
  /** 后端端点，默认 /api/pdf/{id}/ai/vision/stream */
  endpoint?: string
}

export function usePdfAiVision(options: UsePdfAiVisionOptions) {
  const content = ref('')
  const model = ref<string>('')
  const status = ref<VisionStatus>('idle')
  const error = ref<string | null>(null)

  let abortCtrl: AbortController | null = null

  /**
   * 发送视觉问答请求
   * @param question 用户问题
   * @param image dataURL（"data:image/png;base64,..."）
   * @param context 上下文（如 "第 3 页 表格区域"）
   */
  async function ask(question: string, image: string, context?: string) {
    if (status.value === 'submitted' || status.value === 'streaming') {
      console.warn('usePdfAiVision: 已有进行中的请求')
      return
    }
    if (!question?.trim() || !image) return

    content.value = ''
    model.value = ''
    error.value = null
    status.value = 'submitted'

    if (abortCtrl) abortCtrl.abort()
    abortCtrl = new AbortController()

    const endpoint = options.endpoint ?? `/api/pdf/${options.docId}/ai/vision/stream`
    const token = sessionStorage.getItem('token')

    try {
      const resp = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ question, image, context: context ?? '' }),
        signal: abortCtrl.signal,
      })

      if (!resp.ok || !resp.body) {
        throw new Error(`HTTP ${resp.status}`)
      }

      status.value = 'streaming'
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const eventBlock = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          parseVisionEvent(eventBlock)
        }
      }

      if (status.value === 'streaming') status.value = 'done'
    } catch (e: any) {
      if (e?.name === 'AbortError') return
      error.value = e?.message ?? String(e)
      status.value = 'error'
    }
  }

  function parseVisionEvent(block: string) {
    let eventName = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    const dataStr = dataLines.join('\n')
    let payload: any = {}
    try { payload = dataStr ? JSON.parse(dataStr) : {} } catch {}

    if (eventName === 'docStatus') {
      model.value = payload.model ?? ''
    } else if (eventName === 'delta') {
      content.value += payload.content ?? ''
    } else if (eventName === 'done') {
      status.value = 'done'
    } else if (eventName === 'error') {
      error.value = payload.message ?? 'VLM 服务异常'
      status.value = 'error'
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
    content.value = ''
    model.value = ''
    error.value = null
    status.value = 'idle'
  }

  onUnmounted(stop)

  return { content, model, status, error, ask, stop, reset }
}