<template>
  <div class="ai-panel">
    <div class="panel-header">
      <div class="header-brand">
        <div class="brand-icon">✨</div>
        <span class="brand-text">AI 助手</span>
      </div>
      <div class="header-actions">
        <button class="header-btn" @click="clearChat" title="新建对话">＋</button>
        <button class="header-btn" @click="$emit('close')" title="关闭">✕</button>
      </div>
    </div>

    <div class="messages-area" ref="messagesRef">
      <div v-if="messages.length === 0" class="welcome-container">
        <h2>{{ docHasContent ? '正在阅读文档' : '开始创作' }}</h2>
        <p class="welcome-hint">{{ docHasContent ? '有什么关于文档的问题吗？' : '描述你想要创建的内容，我来帮你' }}</p>
        <div v-if="docHasContent" class="quick-prompts">
          <button class="prompt-chip" @click="quickAsk('总结这篇文档的核心内容')">📋 总结摘要</button>
          <button class="prompt-chip" @click="quickAsk('提取文档中的关键条款')">📌 提取要点</button>
          <button class="prompt-chip" @click="quickAsk('解释这段内容的含义')">💡 解释说明</button>
          <button class="prompt-chip" @click="quickAsk('润色并改进这段文字')">✏️ 润色文字</button>
        </div>
        <div v-else class="quick-prompts">
          <button class="prompt-chip" @click="quickAsk('帮我写一份项目计划书')">📝 项目计划</button>
          <button class="prompt-chip" @click="quickAsk('写一封正式的商务邮件')">📧 商务邮件</button>
          <button class="prompt-chip" @click="quickAsk('起草一份合同协议')">📄 合同协议</button>
        </div>
      </div>

      <div v-for="(msg, idx) in messages" :key="msg.id" class="message-item" :class="msg.role">
        <div class="message-avatar">
          <div v-if="msg.role === 'user'" class="user-icon">U</div>
          <div v-else class="ai-icon-small">✨</div>
        </div>
        <div class="message-bubble">
          <div v-if="msg.thinking" class="thinking-area">
            <button class="thinking-header" @click="msg.thinkingExpanded = !msg.thinkingExpanded">
              <span>{{ msg.thinkingExpanded ? '▼' : '▶' }} 思考过程</span>
            </button>
            <div v-show="msg.thinkingExpanded" class="thinking-content">{{ msg.thinking }}</div>
          </div>
          <div class="message-text">{{ msg.content }}</div>
          <div v-if="msg.isStreaming && !msg.content" class="typing">
            <span></span><span></span><span></span>
          </div>
          <div v-if="msg.content && !msg.isStreaming" class="message-toolbar">
            <button class="tool-btn" @click="copyMessage(msg.content)" title="复制">📋</button>
            <button v-if="msg.canInsert" class="tool-btn" @click="insertToEditor(msg.content)" title="插入文档">⬇</button>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="input-wrapper" :class="{ focused: inputFocused }">
        <textarea
          v-model="inputText"
          placeholder="输入问题..."
          @keydown="handleKeydown"
          @focus="inputFocused = true"
          @blur="handleInputBlur"
          @input="autoResize"
          ref="inputRef"
          rows="1"
        ></textarea>
        <button class="send-btn" :disabled="!canSend" @click="sendMessage">↑</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  docId: number
  docType: string
  docHasContent?: boolean
  editorContent?: string
}>()

const emit = defineEmits(['close', 'insert'])

const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const inputText = ref('')
const inputFocused = ref(false)
const docHasContent = ref(props.docHasContent ?? false)
const isLoading = ref(false)

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  thinking: string
  thinkingExpanded: boolean
  isStreaming: boolean
  canInsert: boolean
}

const messages = ref<Message[]>([])
let messageIdCounter = 0
function nextId() { return ++messageIdCounter }
let currentIdx = -1
let abortController: AbortController | null = null

const canSend = computed(() => inputText.value.trim() && !isLoading.value)

function clearChat() {
  abortController?.abort()
  messages.value = []
  currentIdx = -1
}

function quickAsk(q: string) {
  inputText.value = q
  sendMessage()
}

async function sendMessage() {
  const q = inputText.value.trim()
  if (!q || isLoading.value) return

  messages.value.push({ id: nextId(), role: 'user', content: q, thinking: '', thinkingExpanded: false, isStreaming: false, canInsert: false })
  messages.value.push({ id: nextId(), role: 'assistant', content: '', thinking: '', thinkingExpanded: true, isStreaming: true, canInsert: true })
  currentIdx = messages.value.length - 1
  inputText.value = ''
  isLoading.value = true

  await nextTick()
  scrollToBottom()

  const token = sessionStorage.getItem('token')
  if (!token) {
    finishWithError('未登录')
    return
  }

  abortController = new AbortController()

  try {
    const resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
      body: JSON.stringify({
        question: q,
        docId: String(props.docId),
        content: props.editorContent || '',
      }),
      signal: abortController.signal,
    })

    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`)
    }
    if (!resp.body) throw new Error('No response body')

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let nlIdx: number
      // SSE 以 \n\n 分割（也兼容 \n）
      while ((nlIdx = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, nlIdx)
        buffer = buffer.slice(nlIdx + 2)
        handleSSEBlock(block)
      }
    }
    if (buffer.trim()) handleSSEBlock(buffer)

    // 标记流结束
    const last = messages.value[currentIdx]
    if (last) {
      last.isStreaming = false
    }
    isLoading.value = false
  } catch (e: any) {
    if (e.name === 'AbortError') return
    finishWithError(e.message || 'AI 调用失败')
  }
}

function handleSSEBlock(block: string) {
  const last = messages.value[currentIdx]
  if (!last) return

  const lines = block.split(/\r?\n/)
  let eventName = 'message'
  let dataStr = ''
  for (const line of lines) {
    const t = line.trim()
    if (t.startsWith('event:')) {
      eventName = t.slice(6).trim()
    } else if (t.startsWith('data:')) {
      dataStr += t.slice(5).trim()
    }
  }
  if (!dataStr) return

  let parsed: any
  try { parsed = JSON.parse(dataStr) } catch { return }

  switch (eventName) {
    case 'docStatus':
      docHasContent.value = !!parsed.hasContent
      break
    case 'thinking':
      last.thinking += parsed.content || ''
      break
    case 'content':
      last.content += parsed.content || ''
      scrollToBottom()
      break
    case 'done':
      // 流结束，确认收到
      last.isStreaming = false
      isLoading.value = false
      break
    case 'error':
      finishWithError(parsed.message || 'AI 服务错误')
      break
  }
}

function finishWithError(msg: string) {
  const last = messages.value[currentIdx]
  if (last) {
    last.content = last.content ? last.content + '\n\n[错误] ' + msg : '[错误] ' + msg
    last.isStreaming = false
  }
  isLoading.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function handleInputBlur() {
  setTimeout(() => { inputFocused.value = false }, 200)
}

function autoResize() {
  const el = inputRef.value
  if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 120) + 'px' }
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function copyMessage(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

function insertToEditor(text: string) {
  emit('insert', text)
}

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<style scoped>
.ai-panel { width: 380px; height: 100%; display: flex; flex-direction: column; background: #fff; border-left: 1px solid #e8e8e8; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
.panel-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.header-brand { display: flex; align-items: center; gap: 10px; }
.brand-icon { width: 32px; height: 32px; border-radius: 8px; background: linear-gradient(135deg, #667eea, #764ba2); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 16px; }
.brand-text { font-size: 15px; font-weight: 600; }
.header-actions { display: flex; gap: 6px; }
.header-btn { width: 30px; height: 30px; border-radius: 6px; border: 1px solid #e8e8e8; background: #fff; cursor: pointer; font-size: 14px; color: #666; }
.header-btn:hover { background: #f5f5f5; }
.messages-area { flex: 1; overflow-y: auto; padding: 16px; }
.welcome-container { display: flex; flex-direction: column; align-items: center; min-height: 100%; text-align: center; padding: 30px 0; }
.welcome-container h2 { margin: 0 0 8px; font-size: 18px; font-weight: 600; }
.welcome-hint { margin: 0 0 24px; font-size: 13px; color: #888; }
.quick-prompts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; width: 100%; max-width: 320px; }
.prompt-chip { padding: 10px 12px; border: 1px solid #e8e8e8; border-radius: 8px; background: #fff; color: #444; font-size: 13px; cursor: pointer; text-align: left; }
.prompt-chip:hover { background: #f8f7ff; border-color: #667eea; color: #667eea; }
.message-item { display: flex; gap: 10px; margin-bottom: 16px; }
.message-item.user { flex-direction: row-reverse; }
.message-avatar { flex-shrink: 0; }
.user-icon, .ai-icon-small { width: 30px; height: 30px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 14px; font-weight: 600; }
.user-icon { background: linear-gradient(135deg, #10b981, #059669); }
.ai-icon-small { background: linear-gradient(135deg, #667eea, #764ba2); }
.message-bubble { flex: 1; min-width: 0; max-width: 85%; }
.message-text { padding: 10px 14px; border-radius: 14px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; color: #333; }
.message-item.user .message-text { background: linear-gradient(135deg, #10b981, #059669); color: #fff; border-bottom-right-radius: 4px; }
.message-item.assistant .message-text { background: #f7f7f8; border-bottom-left-radius: 4px; }
.thinking-area { margin-bottom: 6px; border-radius: 8px; background: #f0f0ff; border: 1px solid #e0e0ff; overflow: hidden; }
.thinking-header { width: 100%; padding: 6px 12px; background: transparent; border: none; color: #667eea; font-size: 12px; cursor: pointer; text-align: left; }
.thinking-content { padding: 6px 12px 10px; font-size: 12px; color: #667eea; line-height: 1.5; border-top: 1px solid #e0e0ff; white-space: pre-wrap; }
.typing { display: flex; gap: 4px; padding: 10px 14px; }
.typing span { width: 6px; height: 6px; border-radius: 50%; background: #667eea; animation: bounce 1.4s infinite ease-in-out; }
.typing span:nth-child(1) { animation-delay: -0.32s; }
.typing span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%,80%,100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.message-toolbar { display: flex; gap: 4px; margin-top: 6px; }
.tool-btn { padding: 4px 8px; border-radius: 4px; border: none; background: #f0f0f0; color: #888; cursor: pointer; font-size: 12px; }
.tool-btn:hover { background: #e8e8e8; color: #555; }
.input-area { padding: 12px 20px 16px; border-top: 1px solid #f0f0f0; background: #fafafa; flex-shrink: 0; }
.input-wrapper { display: flex; gap: 8px; align-items: flex-end; background: #fff; border: 1px solid #e0e0e0; border-radius: 12px; padding: 8px 8px 8px 14px; transition: all 0.2s; }
.input-wrapper.focused { border-color: #667eea; box-shadow: 0 0 0 3px rgba(102,126,234,0.1); }
.input-wrapper textarea { flex: 1; border: none; background: transparent; resize: none; font-size: 14px; line-height: 1.5; color: #333; outline: none; min-height: 24px; max-height: 120px; }
.input-wrapper textarea::placeholder { color: #aaa; }
.send-btn { width: 36px; height: 36px; border-radius: 8px; border: none; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
