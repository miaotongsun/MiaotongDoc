/**
 * usePdfOptimizeOcr —— OCR 结果 AI 优化（流式订阅）
 *
 * 调用 /api/pdf/{id}/ai/optimize-ocr/stream
 * 用例：去页眉页脚 + 合并断行 + 修正错别字
 */

import { ref, onUnmounted } from 'vue'

export type OptimizeStatus = 'idle' | 'submitted' | 'streaming' | 'done' | 'error'

export interface UsePdfOptimizeOcrOptions {
  docId: number
}

export function usePdfOptimizeOcr(options: UsePdfOptimizeOcrOptions) {
  const optimized = ref('')
  const model = ref<string>('')
  const status = ref<OptimizeStatus>('idle')
  const error = ref<string | null>(null)
  const stats = ref<{ originalLength: number; optimizedLength: number; savedChars: number } | null>(null)

  let abortCtrl: AbortController | null = null

  /**
   * 优化 Markdown
   * @param markdown 待优化的文本
   * @param pageNum 可选页码（优化单页时传）
   */
  async function optimize(markdown: string, pageNum?: number) {
    if (status.value === 'submitted' || status.value === 'streaming') return
    if (!markdown?.trim()) return

    optimized.value = ''
    model.value = ''
    error.value = null
    stats.value = null
    status.value = 'submitted'

    if (abortCtrl) abortCtrl.abort()
    abortCtrl = new AbortController()

    const token = sessionStorage.getItem('token')
    try {
      const resp = await fetch(`/api/pdf/${options.docId}/ai/optimize-ocr/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ markdown, pageNum: pageNum ?? null }),
        signal: abortCtrl.signal,
      })
      if (!resp.ok || !resp.body) throw new Error(`HTTP ${resp.status}`)

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
          const block = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          parseEvent(block)
        }
      }

      if (status.value === 'streaming') status.value = 'done'
    } catch (e: any) {
      if (e?.name === 'AbortError') return
      error.value = e?.message ?? String(e)
      status.value = 'error'
    }
  }

  function parseEvent(block: string) {
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
      optimized.value += payload.content ?? ''
    } else if (eventName === 'done') {
      stats.value = {
        originalLength: payload.originalLength ?? 0,
        optimizedLength: payload.optimizedLength ?? 0,
        savedChars: payload.savedChars ?? 0,
      }
      status.value = 'done'
    } else if (eventName === 'error') {
      error.value = payload.message ?? '服务异常'
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
    optimized.value = ''
    model.value = ''
    error.value = null
    stats.value = null
    status.value = 'idle'
  }

  onUnmounted(reset)

  return { optimized, model, status, error, stats, optimize, stop, reset }
}