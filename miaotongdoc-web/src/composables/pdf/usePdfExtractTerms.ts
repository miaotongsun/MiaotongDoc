/**
 * usePdfExtractTerms —— PDF 关键条款抽取（SSE 流式订阅）
 *
 * 调用 /api/pdf/{id}/ai/extract-terms/stream
 * 后端用 LLM 从已识别 Markdown 中抽取结构化字段（金额、日期、甲方乙方、违约责任等）
 *
 * 返回 Map<fieldName, value>，最终通过 terms.value 暴露
 */

import { ref, onUnmounted } from 'vue'

export type ExtractStatus = 'idle' | 'submitted' | 'streaming' | 'done' | 'error'

export interface UsePdfExtractTermsOptions {
  docId: number
  /** 自定义字段列表（可选，不传则用后端默认） */
  fields?: string[]
}

export const TERM_FIELD_LABELS: Record<string, string> = {
  amount: '合同金额',
  currency: '币种',
  effective_date: '生效日期',
  expire_date: '到期日期',
  party_a: '甲方',
  party_b: '乙方',
  breach_liability: '违约责任',
  payment_terms: '付款方式',
  delivery_date: '交付日期',
}

export function usePdfExtractTerms(options: UsePdfExtractTermsOptions) {
  const terms = ref<Record<string, string | null>>({})
  const rawJson = ref('')
  const model = ref<string>('')
  const status = ref<ExtractStatus>('idle')
  const error = ref<string | null>(null)

  let abortCtrl: AbortController | null = null

  async function extract() {
    if (status.value === 'submitted' || status.value === 'streaming') return

    terms.value = {}
    rawJson.value = ''
    model.value = ''
    error.value = null
    status.value = 'submitted'

    if (abortCtrl) abortCtrl.abort()
    abortCtrl = new AbortController()

    const token = sessionStorage.getItem('token')
    try {
      const resp = await fetch(`/api/pdf/${options.docId}/ai/extract-terms/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(options.fields ? { fields: options.fields } : {}),
        signal: abortCtrl.signal,
      })
      if (!resp.ok || !resp.body) throw new Error(`HTTP ${resp.status}`)

      status.value = 'streaming'
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let pendingTerms: Record<string, string | null> = {}

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          parseEvent(block, pendingTerms)
        }
      }

      // 收尾：尝试解析累积的 JSON 文本
      if (rawJson.value) {
        try {
          const parsed = JSON.parse(rawJson.value)
          if (parsed && typeof parsed === 'object') {
            pendingTerms = parsed
          }
        } catch {}
      }
      terms.value = pendingTerms

      if (status.value === 'streaming') status.value = 'done'
    } catch (e: any) {
      if (e?.name === 'AbortError') return
      error.value = e?.message ?? String(e)
      status.value = 'error'
    }
  }

  function parseEvent(block: string, pending: Record<string, string | null>) {
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
      rawJson.value += payload.content ?? ''
    } else if (eventName === 'done') {
      // done 事件可能直接携带 terms
      if (payload.terms && typeof payload.terms === 'object') {
        Object.assign(pending, payload.terms)
      }
      status.value = 'done'
    } else if (eventName === 'error') {
      error.value = payload.message ?? '服务异常'
      status.value = 'error'
    }
  }

  function reset() {
    if (abortCtrl) abortCtrl.abort()
    terms.value = {}
    rawJson.value = ''
    model.value = ''
    error.value = null
    status.value = 'idle'
  }

  onUnmounted(reset)

  return { terms, rawJson, model, status, error, extract, reset }
}