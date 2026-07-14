<!--
  PdfAiFloatPanel —— PDF 编辑器 AI 浮窗（豆包风格）

  触发：在工具栏右侧"✨ AI"按钮点击后显示
  风格：玻璃拟态 + 渐变紫色 + 大圆角 + 消息气泡（区别于 MD 编辑器的抽屉）
  复用：useAiChat（项目通用 AI 流式聊天）
  依赖：Element Plus 图标
-->
<template>
  <Teleport to="body">
    <!-- 圆形触发按钮（fixed 右下角） -->
    <button
      v-if="!open"
      class="ai-trigger"
      :class="{ 'is-streaming': chat.status.value === 'streaming' }"
      aria-label="展开 AI 助手"
      @click="open = true"
    >
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 2 14 8.5l6.5 1.5-4.7 4.6 1.1 6.4L12 17.8 7.1 21l1.1-6.4L3.5 10l6.5-1.5z" />
      </svg>
    </button>

    <!-- 浮窗本体 -->
    <Transition name="float-slide">
      <aside
        v-if="open"
        class="ai-float"
        role="dialog"
        aria-label="PDF AI 助手"
        :aria-busy="chat.status.value === 'streaming'"
      >
        <!-- 头部 -->
        <header class="ai-header">
          <div class="ai-title">
            <div class="ai-avatar">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 2 14 8.5l6.5 1.5-4.7 4.6 1.1 6.4L12 17.8 7.1 21l1.1-6.4L3.5 10l6.5-1.5z" />
              </svg>
            </div>
            <div class="ai-title-text">
              <div class="ai-name">AI 助手</div>
              <div class="ai-sub">基于本文档智能问答</div>
            </div>
          </div>
          <button class="ai-close" aria-label="关闭" @click="open = false">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </header>

        <!-- 消息区 -->
        <main ref="messagesRef" class="ai-messages">
          <!-- VQA 附件缩略图条 -->
          <div v-if="vqaImage" class="ai-vqa-bar">
            <img :src="vqaImage" alt="PDF 区域截图" class="ai-vqa-thumb" />
            <div class="ai-vqa-meta">
              <div class="ai-vqa-title">📎 已附加截图</div>
              <div class="ai-vqa-sub">{{ vqaContext || 'PDF 区域' }}</div>
            </div>
            <button class="ai-vqa-clear" aria-label="移除截图" @click="emit('clear-vqa')">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <!-- 空状态 -->
          <div v-if="!chat.messages.value.length" class="ai-empty">
            <div class="ai-empty-icon">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                <line x1="9" y1="10" x2="9.01" y2="10" />
                <line x1="13" y1="10" x2="13.01" y2="10" />
                <line x1="17" y1="10" x2="17.01" y2="10" />
              </svg>
            </div>
            <div class="ai-empty-title">开始与文档对话</div>
            <div class="ai-empty-hint">试试下面的快捷问题</div>
            <div class="ai-quick">
              <button
                v-for="(q, i) in QUICK_QUESTIONS"
                :key="i"
                class="ai-quick-btn"
                :disabled="chat.status.value === 'streaming'"
                @click="askQuick(q)"
              >
                {{ q }}
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="msg in chat.messages.value"
            :key="msg.id"
            class="ai-msg"
            :class="msg.role"
          >
            <div class="ai-msg-avatar" v-if="msg.role === 'assistant'">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 2 14 8.5l6.5 1.5-4.7 4.6 1.1 6.4L12 17.8 7.1 21l1.1-6.4L3.5 10l6.5-1.5z" />
              </svg>
            </div>
            <div class="ai-msg-bubble">
              <div
                v-for="(part, pi) in msg.parts"
                :key="pi"
                class="ai-msg-text"
                :class="{ 'is-streaming': part.state === 'streaming' }"
              >
                {{ part.text }}<span v-if="part.state === 'streaming'" class="ai-cursor">▍</span>
              </div>
            </div>
          </div>
        </main>

        <!-- 输入区 -->
        <footer class="ai-input">
          <textarea
            v-model="input"
            class="ai-textarea"
            rows="1"
            placeholder="输入问题，回车发送（Shift+Enter 换行）"
            :disabled="chat.status.value === 'streaming'"
            @keydown.enter.exact.prevent="onSend"
            @input="autoResize"
            ref="textareaRef"
          />
          <button
            class="ai-send"
            :disabled="!input.trim() || chat.status.value === 'streaming'"
            :aria-label="chat.status.value === 'streaming' ? '生成中' : '发送'"
            @click="onSend"
          >
            <svg v-if="chat.status.value !== 'streaming'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
            <span v-else class="ai-spinner" aria-hidden="true" />
          </button>
        </footer>

        <!-- 中断按钮（流式时） -->
        <button
          v-if="chat.status.value === 'streaming'"
          class="ai-stop"
          @click="chat.stop()"
        >
          停止生成
        </button>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * PdfAiFloatPanel —— PDF 编辑器 AI 浮窗
 *
 * 设计要点（Element Plus 蓝主色，与 Home / PdfEditor 一致）：
 * - 蓝色渐变（#409EFF → #66B1FF）+ 玻璃拟态
 * - 圆形触发按钮（fixed 右下角，48px 直径）
 * - 浮窗宽 420px，圆角 20px
 * - 用户消息右侧实心蓝气泡；AI 消息左侧白色气泡 + 流式光标
 * - useAiChat 复用：节流、AbortController、XSS 消毒
 *
 * Props:
 *   docId: 当前 PDF 文档 ID
 *   docContent: 当前 PDF 内容（作为 AI 上下文，可选）
 *   visible: 受控显隐（v-model:visible），未传则内部 open 控制
 */
import { ref, computed, watch, nextTick, onUnmounted, triggerRef } from 'vue'
import { useAiChat } from '@/composables/useAiChat'
import type { PropType } from 'vue'

const props = defineProps({
  docId: { type: Number, required: true },
  docContent: { type: String, default: '' },
  visible: { type: Boolean, default: undefined },
  vqaImage: { type: String, default: undefined },  // dataURL，PDF 区域截图
  vqaContext: { type: String, default: '' },   // 上下文描述，如「第 3 页 表格区域」
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'clear-vqa'): void
}>()

const QUICK_QUESTIONS = [
  '这篇文章的主要内容是什么？',
  '有哪些关键信息？',
  '总结一下这份文档',
]

// ===== 受控/非受控双向 =====
const open = computed({
  get: () => props.visible ?? _open.value,
  set: (v) => {
    if (props.visible !== undefined) emit('update:visible', v)
    else _open.value = v
  },
})
const _open = ref(props.visible ?? false)
watch(() => props.visible, (v) => { if (v !== undefined) _open.value = v })

// 监听 vqa：自动填入 placeholder
watch(() => props.vqaImage, (v) => {
  if (v && !input.value) {
    input.value = '请描述这个 PDF 区域的内容'
  }
})

// ===== AI 聊天（复用 useAiChat） =====
const chat = useAiChat({
  docId: computed(() => props.docId),
  endpoint: 'chat-stream',
})

// ===== 输入区 =====
const input = ref('')
const messagesRef = ref<HTMLDivElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

// ===== 视觉问答（VQA）=====
import { usePdfAiVision } from '@/composables/pdf/usePdfAiVision'

const vision = usePdfAiVision({ docId: props.docId })

async function onSend() {
  const text = input.value.trim()
  if (!text) return
  if (chat.status.value === 'streaming' || vision.status.value === 'streaming') return

  // 有截图 → 走视觉问答
  if (props.vqaImage) {
    input.value = ''
    await nextTick()
    autoResize()

    // 1) 推送用户消息（含截图预览）
    chat.messages.value = [
      ...chat.messages.value,
      {
        id: `m-user-${Date.now()}`,
        role: 'user' as const,
        parts: [{ type: 'text' as const, text: text, state: 'done' as const }],
      },
    ]
    // 2) 占位 AI 消息（流式填充）
    const assistantId = `m-ai-${Date.now()}`
    chat.messages.value = [
      ...chat.messages.value,
      {
        id: assistantId,
        role: 'assistant' as const,
        parts: [{ type: 'text' as const, text: '', state: 'streaming' as const }],
      },
    ]
    // 3) 启动 VLM 流
    const context = props.vqaContext || 'PDF 区域'
    await vision.ask(text, props.vqaImage, context)
    // 4) 把结果写回 chat.messages（方便统一显示）
    if (vision.error.value) {
      chat.messages.value[chat.messages.value.length - 1].parts[0].text =
        '❌ ' + vision.error.value
      chat.messages.value[chat.messages.value.length - 1].parts[0].state = 'error' as any
    } else {
      chat.messages.value[chat.messages.value.length - 1].parts[0].text =
        vision.content.value || '(无回复)'
      chat.messages.value[chat.messages.value.length - 1].parts[0].state = 'done' as any
      // 用 triggerRef 强制更新（messages 是 shallowRef）
      triggerRef(chat.messages)
    }
    // 5) 清除截图附件
    emit('clear-vqa')
    return
  }

  // 普通文本对话
  input.value = ''
  await nextTick()
  autoResize()
  await chat.sendUserMessage(text, undefined, props.docContent)
}

async function askQuick(q: string) {
  input.value = q
  await onSend()
}

// ===== 自动滚动到底部（消息更新时） =====
let stopWatch: ReturnType<typeof watch> | null = null
watch(
  () => chat.messageVersion.value,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  },
)
stopWatch = watch(() => chat.messageVersion.value, () => {})

onUnmounted(() => {
  chat.clear()
})
</script>

<style scoped>
/* ============ 设计令牌（与 PdfEditor / Home 统一 Element Plus 蓝 #409EFF） ============ */
.ai-trigger,
.ai-float {
  --ai-primary: #409EFF;
  --ai-primary-light: #66B1FF;
  --ai-primary-soft: #ECF5FF;
  --ai-bg: rgba(255, 255, 255, 0.96);
  --ai-bg-solid: #FFFFFF;
  --ai-text: #303133;
  --ai-text-muted: #606266;
  --ai-border: #E4E7ED;
  --ai-shadow: 0 20px 60px -12px rgba(64, 158, 255, 0.22),
               0 8px 24px -6px rgba(0, 0, 0, 0.10);
}

/* ============ 触发按钮 ============ */
.ai-trigger {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 9998;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #409EFF 0%, #66B1FF 100%);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s ease-out, box-shadow 0.18s ease-out;
}
.ai-trigger:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.42);
}
.ai-trigger:active { transform: scale(0.94); }
.ai-trigger:focus-visible {
  outline: 3px solid var(--ai-primary-light);
  outline-offset: 4px;
}
/* streaming 状态：脉动点 */
.ai-trigger.is-streaming::after {
  content: '';
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #F56C6C;
  border: 2px solid #fff;
  animation: pulse 1.4s ease-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.3); opacity: 0.6; }
}

/* ============ 浮窗容器 ============ */
.ai-float {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9999;
  width: 420px;
  height: min(640px, calc(100vh - 48px));
  display: flex;
  flex-direction: column;
  background: var(--ai-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--ai-border);
  border-radius: 20px;
  box-shadow: var(--ai-shadow);
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
               'Microsoft YaHei', sans-serif;
  color: var(--ai-text);
}

/* ============ 头部 ============ */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.05) 0%, rgba(102, 177, 255, 0.08) 100%);
  border-bottom: 1px solid var(--ai-border);
  flex-shrink: 0;
}
.ai-title { display: flex; align-items: center; gap: 10px; }
.ai-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--ai-primary) 0%, var(--ai-primary-light) 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}
.ai-title-text { display: flex; flex-direction: column; gap: 2px; }
.ai-name { font-size: 14px; font-weight: 600; color: var(--ai-text); }
.ai-sub { font-size: 11px; color: var(--ai-text-muted); }
.ai-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  color: var(--ai-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.ai-close:hover { background: rgba(64, 158, 255, 0.08); color: var(--ai-primary); }

/* ============ 消息区 ============ */
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 8px;
  scroll-behavior: smooth;
}

/* VQA 附件条 */
.ai-vqa-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 12px;
  background: var(--ai-primary-soft);
  border: 1px dashed var(--ai-primary);
  border-radius: 12px;
  animation: msgIn 0.25s ease-out;
}
.ai-vqa-thumb {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--ai-border);
  flex-shrink: 0;
}
.ai-vqa-meta {
  flex: 1;
  min-width: 0;
}
.ai-vqa-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-primary);
  margin-bottom: 2px;
}
.ai-vqa-sub {
  font-size: 11px;
  color: var(--ai-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-vqa-clear {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--ai-text-muted);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}
.ai-vqa-clear:hover {
  background: rgba(245, 108, 108, 0.12);
  color: #F56C6C;
}
.ai-messages::-webkit-scrollbar { width: 6px; }
.ai-messages::-webkit-scrollbar-thumb {
  background: rgba(64, 158, 255, 0.15);
  border-radius: 3px;
}

/* 空状态 */
.ai-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 32px 16px;
  color: var(--ai-text-muted);
}
.ai-empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--ai-primary-soft) 0%, #EDE9FE 100%);
  color: var(--ai-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.ai-empty-title { font-size: 15px; font-weight: 600; color: var(--ai-text); margin-bottom: 4px; }
.ai-empty-hint { font-size: 12px; margin-bottom: 16px; }
.ai-quick { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.ai-quick-btn {
  padding: 6px 12px;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  background: var(--ai-bg-solid);
  font-size: 12px;
  color: var(--ai-primary);
  cursor: pointer;
  transition: all 0.15s;
}
.ai-quick-btn:hover:not(:disabled) {
  background: var(--ai-primary-soft);
  border-color: var(--ai-primary-light);
  transform: translateY(-1px);
}
.ai-quick-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 消息气泡 */
.ai-msg {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  animation: msgIn 0.25s ease-out;
}
@keyframes msgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.ai-msg.user {
  flex-direction: row-reverse;
}
.ai-msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--ai-primary) 0%, var(--ai-primary-light) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ai-msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
}
.ai-msg.user .ai-msg-bubble {
  background: linear-gradient(135deg, var(--ai-primary) 0%, var(--ai-primary-light) 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}
.ai-msg.assistant .ai-msg-bubble {
  background: var(--ai-bg-solid);
  color: var(--ai-text);
  border: 1px solid var(--ai-border);
  border-bottom-left-radius: 4px;
}
.ai-msg-text.is-streaming::after { content: ''; }
.ai-cursor {
  display: inline-block;
  color: var(--ai-primary);
  animation: blink 0.9s step-end infinite;
  margin-left: 2px;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ============ 输入区 ============ */
.ai-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--ai-border);
  background: rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
}
.ai-textarea {
  flex: 1;
  resize: none;
  padding: 10px 14px;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  background: var(--ai-bg-solid);
  font-size: 13px;
  line-height: 1.5;
  font-family: inherit;
  color: var(--ai-text);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  min-height: 40px;
  max-height: 120px;
}
.ai-textarea:focus {
  border-color: var(--ai-primary-light);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}
.ai-textarea:disabled { background: var(--ai-primary-soft); cursor: not-allowed; }

.ai-send {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--ai-primary) 0%, var(--ai-primary-light) 100%);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}
.ai-send:hover:not(:disabled) { transform: translateY(-1px); }
.ai-send:active:not(:disabled) { transform: scale(0.94); }
.ai-send:disabled {
  background: #E5E7EB;
  color: #9CA3AF;
  box-shadow: none;
  cursor: not-allowed;
}
.ai-send:focus-visible { outline: 3px solid var(--ai-primary-light); outline-offset: 2px; }

.ai-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 中断按钮 */
.ai-stop {
  position: absolute;
  bottom: 88px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 12px;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  background: var(--ai-bg-solid);
  font-size: 11px;
  color: var(--ai-text-muted);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.ai-stop:hover { color: var(--ai-primary); border-color: var(--ai-primary-light); }

/* ============ 过渡动画 ============ */
.float-slide-enter-active {
  transition: all 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.float-slide-leave-active {
  transition: all 0.18s ease-in;
}
.float-slide-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.92);
}
.float-slide-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}

/* ============ 响应式 ============ */
@media (max-width: 480px) {
  .ai-float {
    right: 12px;
    bottom: 12px;
    left: 12px;
    width: auto;
    height: calc(100vh - 24px);
  }
  .ai-trigger { right: 12px; bottom: 12px; }
}

/* 减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .float-slide-enter-active,
  .float-slide-leave-active,
  .ai-msg,
  .ai-cursor,
  .ai-spinner {
    animation: none !important;
    transition: none !important;
  }
}
</style>