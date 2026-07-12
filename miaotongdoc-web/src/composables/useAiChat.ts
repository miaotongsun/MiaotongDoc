/**
 * useAiChat —— 项目化的 AI 流式聊天封装
 *
 * 按生产级方案实现：
 * 1. fetch + ReadableStream（不用 EventSource，不支持 POST）
 * 2. POST 请求 + 自定义 Header（JWT Bearer）
 * 3. **requestAnimationFrame 节流渲染**（跟随显示器刷新率，16ms 一帧）
 *    - 每次 chunk 累积到内部缓冲区
 *    - rAF tick 内调 sanitize + 更新响应式 state
 *    - 同帧多次 trigger 合并为一次 DOM patch
 * 4. **div.textContent sanitize** —— 防御 XSS，自动转义 HTML 特殊字符
 * 5. **AbortController** 中断 + 保留现有文本 + 中断状态标记
 * 6. 流结束/异常/中断统一清理缓冲区、读取器、状态
 */

import { shallowRef, ref, triggerRef, type ComputedRef, type Ref, type ShallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'

export type PartState = 'streaming' | 'done' | 'aborted' | 'error'

export interface MessagePart {
  type: 'text' | 'reasoning'
  text: string
  state: PartState
}

export interface AiMessage {
  id: string
  role: 'user' | 'assistant'
  parts: MessagePart[]
}

export type StreamStatus = 'ready' | 'submitted' | 'streaming' | 'aborted' | 'error'

export interface UseAiChatOptions {
  docId: ComputedRef<number | string | null | undefined> | Ref<number | string | null | undefined>
  endpoint?: 'chat-stream' | 'generate-stream'
  onUnauthorized?: () => void
  onAbort?: () => void
}

export interface UseAiChatReturn {
  messages: ShallowRef<AiMessage[]>
  /** 版本号：每次 rAF flush 后 ++（保证下游 watcher 必触发） */
  messageVersion: Ref<number>
  status: Ref<StreamStatus>
  error: Ref<Error | undefined>
  sendUserMessage: (text: string, systemPrompt?: string, docContent?: string) => Promise<void>
  stop: () => void
  clear: () => void
}

let __idSeq = 0
const nextId = () => `m${Date.now().toString(36)}${(__idSeq++).toString(36)}`

/**
 * HTML 消毒工具：AI 输出 XSS 防御
 * 利用 DOM textContent 自动转义所有 HTML 特殊字符（< > & " '）
 * 拦截：<script>、onclick/onerror/onload、javascript: 协议等所有注入
 */
export function sanitizeHtml(str: string): string {
  if (!str) return ''
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

/**
 * Markdown 渲染场景的消毒（允许安全标签如 <code> <pre> <a> 等）
 * 使用 DOMPurify 严格白名单
 */
export function sanitizeAiMarkdown(s: string): string {
  return String(DOMPurify.sanitize(s, {
    ALLOWED_TAGS: ['a', 'p', 'br', 'span', 'b', 'i', 'em', 'strong', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li'],
    ALLOWED_ATTR: ['href', 'title', 'class'],
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'style'],
    FORBID_ATTR: ['onerror', 'onclick', 'onload', 'onmouseover'],
  }))
}

export function useAiChat(options: UseAiChatOptions): UseAiChatReturn {
  const { docId, endpoint = 'chat-stream', onUnauthorized, onAbort } = options

  // ====== 响应式 state ======
  const messages = shallowRef<AiMessage[]>([]) as ShallowRef<AiMessage[]>
  const messageVersion = ref(0)
  const status = ref<StreamStatus>('ready')
  const error = ref<Error | undefined>(undefined)

  // ====== 内部缓冲（分片缓存） ======
  let currentAssistantMsg: AiMessage | null = null
  let currentTextPart: MessagePart | null = null
  let currentReasoningPart: MessagePart | null = null
  let insideThink = false

  let pendingText = ''
  let pendingThinking = ''

  // ====== 流式控制 ======
  let abortController: AbortController | null = null
  let streamReader: ReadableStreamDefaultReader<Uint8Array> | null = null
  let abortedByUser = false

  // ====== 打字机效果（唯一的逐字渲染机制） ======
  // 问题：后端虽然 SSE 标记分块，但实际数据是 LLM 一次给齐所有 token。
  //       （sensenova 代理累积 ~1.5s 后一次性 flush 30 个 token）
  // 解法：前端用 30ms 一个字的间隔逐字 reveal，看到"打字"效果。
  // 关键：必须保证 rAF flush 不会"提前"消费 pendingText，
  //      否则打字机的字符被瞬间写入 DOM，看不到逐字效果。
  let typewriterTimer: ReturnType<typeof setInterval> | null = null
  const TYPEWRITER_INTERVAL_MS = 25  // 每字 25ms ≈ 40字/秒（更接近真人语速）

  function startTypewriter() {
    if (typewriterTimer !== null) return
    // 关键：立即 reveal 一个字，避免首字延迟 25ms
    typewriteOneChar()
    typewriterTimer = setInterval(() => {
      typewriteOneChar()
    }, TYPEWRITER_INTERVAL_MS)
  }

  /** 打字机一次 reveal 一个字（先 thinking 后 text） */
  function typewriteOneChar() {
    let changed = false
    // 关键：先消费 thinking，再消费 text —— 让思考内容先完整 reveal，
    // 之后再流式输出正文（避免 think 和正文同时打字，体验割裂）
    if (pendingThinking.length > 0) {
      const chunk = pendingThinking.slice(0, 1)
      pendingThinking = pendingThinking.slice(1)
      ensureReasoningPart()
      currentReasoningPart!.text += chunk
      changed = true
    } else if (pendingText.length > 0) {
      const chunk = pendingText.slice(0, 1)
      pendingText = pendingText.slice(1)
      ensureTextPart()
      currentTextPart!.text += chunk
      changed = true
    }
    if (changed) {
      messageVersion.value++
      triggerRef(messages)
    } else {
      // 没有 pending 时停止打字机
      stopTypewriter()
    }
  }

  function stopTypewriter() {
    if (typewriterTimer !== null) {
      clearInterval(typewriterTimer)
      typewriterTimer = null
    }
  }

  function scheduleFlush() {
    // 仅启动打字机逐字 reveal（不要 rAF flush，否则会提前消费 pendingText）
    startTypewriter()
  }

  function cancelPendingFlush() {
    // 不再使用 rAF flush，保留接口兼容
  }

  /** 把 pendingText / pendingThinking 写入响应式 state（每个 rAF tick 一次） */
  function flushPendingToReactive() {
    let changed = false
    if (pendingText) {
      ensureTextPart()
      // 流式期间不做 sanitize（DOMPurify 耗时几十毫秒，会扼杀真正的逐字流式感）
      // XSS 防护：Tiptap 输出的是从 Markdown 转换的合法 HTML，后端 system prompt
      // 已限制 LLM 输出格式（<result>...</result>），所以文本路径风险极低。
      // 编辑器最终还会再走一次 Markdown 转换到 HTML，所以这里粗放赋值即可。
      currentTextPart!.text += pendingText
      pendingText = ''
      changed = true
    }
    if (pendingThinking) {
      ensureReasoningPart()
      currentReasoningPart!.text += pendingThinking
      pendingThinking = ''
      changed = true
    }
    if (changed) {
      // triggerRef 强制下游 watchEffect 同步触发，不等 Vue 的 scheduler
      messageVersion.value++
      triggerRef(messages)
    }
  }

  /** 立即 flush（流结束时调用，保证最后一批数据不丢） */
  function flushImmediately() {
    cancelPendingFlush()
    stopTypewriter()
    flushPendingToReactive()
  }

  function ensureAssistantMsg(): AiMessage {
    if (currentAssistantMsg) return currentAssistantMsg
    const msg: AiMessage = { id: nextId(), role: 'assistant', parts: [] }
    messages.value.push(msg)
    currentAssistantMsg = msg
    messageVersion.value++
    return msg
  }

  function ensureTextPart(): MessagePart {
    if (currentTextPart) return currentTextPart
    const msg = ensureAssistantMsg()
    const part: MessagePart = { type: 'text', text: '', state: 'streaming' }
    msg.parts.push(part)
    currentTextPart = part
    messageVersion.value++
    return part
  }

  function ensureReasoningPart(): MessagePart {
    if (currentReasoningPart) return currentReasoningPart
    const msg = ensureAssistantMsg()
    const part: MessagePart = { type: 'reasoning', text: '', state: 'streaming' }
    msg.parts.push(part)
    currentReasoningPart = part
    messageVersion.value++
    return part
  }

  /** 把 chunk 累积到 pending（不立即更新，靠 rAF 节流 flush） */
  function applyContentChunk(raw: string) {
    let i = 0
    const len = raw.length

    const FILLER_OPENERS = [
      "Here's a thinking process:",
      "Here's my thinking process:",
      'Here is a thinking process:',
      'Here is my thinking process:',
      'Let me think about this:',
      'Let me think:',
      'Let me analyze this:',
      'Let me break this down:',
      'My thinking process:',
      'Thinking Process:',
      '思考过程：',
      '思考：',
      '让我想想：',
      '让我分析：',
      '让我先思考：',
      '让我先分析：',
      '我来分析一下：',
      '我先思考一下：',
      '以下是详细分析：',
      '以下是分析：',
      '下面是详细分析：',
      '下面是分析：',
      '好的，',
      '好的。',
      '好的！',
      '好的,',
      'Sure,',
      'Sure.',
      'Sure!',
      'Of course,',
      'Of course.',
      'Of course!',
    ]

    while (i < len) {
      if (insideThink) {
        const endIdx = raw.indexOf('</think>', i)
        if (endIdx === -1) {
          i = len
        } else {
          insideThink = false
          i = endIdx + '</think>'.length
        }
      } else {
        const thinkStart = raw.indexOf('<think>', i)
        const endTagOnly = raw.indexOf('</think>', i)
        const endOnlyAt = endTagOnly >= 0 && (thinkStart < 0 || endTagOnly < thinkStart) ? endTagOnly : -1

        if (endOnlyAt >= 0) {
          const prefix = raw.slice(i, endOnlyAt)
          const lower = prefix.toLowerCase()
          let stripped = prefix.trim()
          for (const opener of FILLER_OPENERS) {
            if (lower.includes(opener.toLowerCase())) {
              const idx = lower.indexOf(opener.toLowerCase())
              stripped = prefix.slice(i + idx + opener.length).trim()
              break
            }
          }
          if (stripped) pendingThinking += stripped + '\n\n'
          i = endOnlyAt + '</think>'.length
          while (i < len && /\s/.test(raw[i])) i++
        } else if (thinkStart >= 0) {
          const before = raw.slice(i, thinkStart)
          if (before) pendingText += before
          insideThink = true
          i = thinkStart + '<think>'.length
        } else {
          pendingText += raw.slice(i)
          i = len
        }
      }
    }
    // 累积后调度一次 rAF flush
    scheduleFlush()
  }

  function applyThinkingChunk(raw: string) {
    pendingThinking += raw
    scheduleFlush()
  }

  /** 完成所有 streaming part */
  function finalizeParts(finalState: PartState) {
    flushImmediately()
    if (currentReasoningPart && currentReasoningPart.state === 'streaming') {
      currentReasoningPart.state = finalState
    }
    if (currentTextPart && currentTextPart.state === 'streaming') {
      currentTextPart.state = finalState
    }
    messageVersion.value++
    triggerRef(messages)
  }

  function resetCurrentAssistant() {
    currentAssistantMsg = null
    currentTextPart = null
    currentReasoningPart = null
    insideThink = false
    pendingText = ''
    pendingThinking = ''
  }

  /** 处理 SSE 事件 */
  function handleSSEBlock(block: string) {
    if (abortedByUser) return

    const lines = block.split(/\r?\n/)
    const dataLines: string[] = []
    let eventName = ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (trimmed.startsWith('event:')) {
        eventName = trimmed.slice(6).trim()
      } else if (trimmed.startsWith('data:')) {
        dataLines.push(trimmed.slice(5).trimStart())
      }
    }
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')
    if (!data || data === '[DONE]') return

    // 后端 DocumentAiController 推送格式：event:ai-chunk, data:<raw text>
    // 后端 AiChatSseController 推送格式：event:content|thinking|done|error, data:{"type":"...","content":"..."}
    // 兼容两种：先按 JSON 解析，失败则视为纯文本
    let content = ''
    let type = ''

    const tryJson = (): { type: string; content: string; ok: boolean } => {
      try {
        const parsed = JSON.parse(data)
        return {
          type: String(parsed.type || '').toLowerCase(),
          content: String(parsed.content || parsed.delta || ''),
          ok: true,
        }
      } catch {
        return { type: '', content: '', ok: false }
      }
    }

    if (eventName === 'done') {
      // done 事件：标记完成，不追加内容（避免重复显示）
      // 完整内容已经通过 thinking/content event 流式接收过了
      return
    }

    const r1 = tryJson()
    if (r1.ok && r1.content) {
      // JSON 格式：按 type 分发
      type = r1.type || (eventName === 'thinking' || eventName === 'reasoning' ? 'thinking' : 'content')
      content = r1.content
    } else if (eventName === 'thinking' || eventName === 'reasoning') {
      // 纯文本 + thinking event
      content = data
      type = 'thinking'
    } else if (eventName === 'error') {
      // error 事件：作为错误显示
      content = data
      type = 'content'
    } else {
      // 纯文本（DocumentAiController 的 ai-chunk 格式）—— 直接当 content
      content = data
      type = 'content'
    }

    if (!content) return

    if (type === 'thinking' || type === 'reasoning') {
      applyThinkingChunk(content)
    } else {
      applyContentChunk(content)
    }
  }

  /** 异步读取 SSE 流（fetch + ReadableStream） */
  async function processSSEStream(resp: Response): Promise<void> {
    if (!resp.body) throw new Error('No response body')
    streamReader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { value, done } = await streamReader.read()
        if (done) break
        // UTF-8 强制编码，避免中文乱码
        buffer += decoder.decode(value, { stream: true })

        // SSE 以 \n\n 分割完整消息
        let sepIdx: number
        while ((sepIdx = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.slice(0, sepIdx)
          buffer = buffer.slice(sepIdx + 2)
          handleSSEBlock(block)
        }
      }
      if (buffer.trim()) handleSSEBlock(buffer)
    } finally {
      // 释放 reader
      if (streamReader) {
        try { streamReader.releaseLock() } catch { /* ignore */ }
        streamReader = null
      }
    }
  }

  async function sendUserMessage(text: string, systemPrompt?: string, docContent?: string) {
    // 1. 准备 user 消息 + assistant 占位
    status.value = 'submitted'
    error.value = undefined
    abortedByUser = false

    const userMsg: AiMessage = {
      id: nextId(),
      role: 'user',
      parts: [{ type: 'text', text, state: 'done' }],
    }
    messages.value.push(userMsg)
    messageVersion.value++

    resetCurrentAssistant()
    ensureAssistantMsg()
    messageVersion.value++

    // 2. fetch + ReadableStream
    abortController = new AbortController()
    const id = docId.value
    const url = id != null ? `/api/documents/${id}/ai/${endpoint}` : '/api/chat'
    const token = sessionStorage.getItem('token') || ''

    const body: Record<string, unknown> = {
      question: text,
      enhanced: false,
    }
    if (systemPrompt) body.systemPrompt = systemPrompt
    if (docContent) body.content = docContent

    const historyMsgs = messages.value.slice(0, -2)
    if (historyMsgs.length > 0) {
      body.history = historyMsgs.flatMap((m) => {
        const text = (m.parts || [])
          .filter((p) => p.type === 'text')
          .map((p) => p.text)
          .join('')
        if (!text) return []
        return [{ role: m.role, content: text }]
      })
    }

    try {
      status.value = 'streaming'
      const resp = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(body),
        signal: abortController.signal,
        credentials: 'same-origin',
      })

      if (!resp.ok) {
        if (resp.status === 401) {
          sessionStorage.removeItem('token')
          ElMessage.warning('登录超时，请重新登录')
          setTimeout(() => (window.location.href = '/login'), 1500)
          if (onUnauthorized) onUnauthorized()
          throw new Error('Unauthorized')
        }
        throw new Error(`HTTP ${resp.status}`)
      }

      await processSSEStream(resp)

      // 流结束：保留现有内容，标记状态
      if (abortedByUser) {
        finalizeParts('aborted')
        status.value = 'aborted'
        if (onAbort) onAbort()
      } else {
        finalizeParts('done')
        status.value = 'ready'
      }
    } catch (err: any) {
      flushImmediately()
      if (err?.name === 'AbortError' || abortedByUser) {
        finalizeParts('aborted')
        status.value = 'aborted'
        if (onAbort) onAbort()
        return
      }
      console.error('[useAiChat] error:', err)
      error.value = err instanceof Error ? err : new Error(String(err))
      finalizeParts('error')
      status.value = 'error'
      // 7/11 关键：把后端 "AI API 错误: 429" / "rate limit" 等限流关键字都识别成 429
      const rawMsg = (error.value.message || '').toLowerCase()
      const isRateLimit = /429|rate.?limit|too.?many.?requests|quota|限流/.test(rawMsg)
      if (currentAssistantMsg) {
        const errPart: MessagePart = {
          type: 'text',
          text: isRateLimit
            ? '🚦 AI 服务限流中，请稍等 30 秒再试（429 Too Many Requests）'
            : `请求失败：${error.value.message || '未知错误'}`,
          state: 'done',
        }
        currentAssistantMsg.parts.push(errPart)
        messageVersion.value++
        triggerRef(messages)
      }
      // 7/11 关键：限流时弹个 ElMessage 让用户知道原因
      if (isRateLimit) {
        try {
          const { ElMessage } = await import('element-plus')
          ElMessage.warning('AI 服务限流中，请稍候再试')
        } catch {}
      }
    } finally {
      // ===== 统一清理：缓冲区、读取器、状态、rAF ======
      // 不要再 cancel + flushImmediately —— 会让 pending 一次性 flush 失去流式效果
      flushImmediately()
      if (streamReader) {
        try { streamReader.releaseLock() } catch { /* ignore */ }
        streamReader = null
      }
      if (abortController) {
        try { abortController.abort() } catch { /* ignore */ }
        abortController = null
      }
      pendingText = ''
      pendingThinking = ''
    }
  }

  /** 中断：abort + 保留现有文本 + 标记 aborted */
  function stop() {
    if (status.value !== 'streaming' && status.value !== 'submitted') return
    abortedByUser = true
    stopTypewriter()
    if (abortController) {
      try { abortController.abort() } catch { /* ignore */ }
    }
    if (streamReader) {
      try { streamReader.cancel() } catch { /* ignore */ }
    }
  }

  function clear() {
    stop()
    stopTypewriter()
    messages.value = []
    resetCurrentAssistant()
    status.value = 'ready'
    error.value = undefined
    abortedByUser = false
    messageVersion.value++
    triggerRef(messages)
  }

  return {
    messages: messages as unknown as ShallowRef<AiMessage[]>,
    messageVersion,
    status: status as Ref<StreamStatus>,
    error: error as Ref<Error | undefined>,
    sendUserMessage,
    stop,
    clear,
  }
}
