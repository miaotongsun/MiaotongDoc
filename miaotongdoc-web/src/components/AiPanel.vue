<template>
  <div class="ai-panel">
    <div class="ai-header">
      <el-icon><MagicStick /></el-icon>
      <span>AI 助手</span>
      <button class="close-btn" @click="$emit('close')">✕</button>
    </div>

    <el-tabs v-model="activeTab" class="ai-tabs">
      <!-- 文档问答 -->
      <el-tab-pane label="问答" name="chat">
        <div class="chat-container">
          <div class="chat-messages" ref="chatMessagesRef">
            <div v-for="(msg, i) in chatMessages" :key="i"
              class="chat-msg" :class="[msg.role]">
              <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
              <div class="msg-content" v-html="renderMarkdown(msg.content)" />
            </div>
            <div v-if="chatLoading" class="chat-msg assistant">
              <div class="msg-avatar">🤖</div>
              <div class="msg-content typing">思考中...</div>
            </div>
          </div>
          <div class="chat-input">
            <el-input v-model="chatQuestion" placeholder="输入关于文档的问题..."
              @keyup.enter="sendChat" :disabled="chatLoading" size="small">
              <template #append>
                <el-button @click="sendChat" :loading="chatLoading" :disabled="!chatQuestion.trim()">
                  发送
                </el-button>
              </template>
            </el-input>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI 摘要 -->
      <el-tab-pane label="摘要" name="summary">
        <div class="summary-container">
          <el-button type="primary" @click="generateSummary" :loading="summaryLoading"
            style="width:100%">
            <el-icon><Document /></el-icon>
            生成文档摘要
          </el-button>
          <div v-if="summaryText" class="summary-result">
            <div class="result-content" v-html="renderMarkdown(summaryText)" />
            <el-button size="small" @click="copyText(summaryText)" style="margin-top:8px">
              复制摘要
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI 翻译 -->
      <el-tab-pane label="翻译" name="translate">
        <div class="translate-container">
          <div class="translate-form">
            <el-select v-model="targetLang" size="small" style="width:100%">
              <el-option label="中文" value="zh" />
              <el-option label="English" value="en" />
              <el-option label="日本語" value="ja" />
              <el-option label="한국어" value="ko" />
              <el-option label="Français" value="fr" />
              <el-option label="Deutsch" value="de" />
            </el-select>
            <el-input v-model="translateText" type="textarea" :rows="4"
              placeholder="输入要翻译的文本，或留空翻译全文..." />
            <el-button type="primary" @click="doTranslate" :loading="translateLoading"
              style="width:100%">
              翻译
            </el-button>
          </div>
          <div v-if="translatedText" class="translate-result">
            <div class="result-content" v-html="renderMarkdown(translatedText)" />
            <el-button size="small" @click="copyText(translatedText)" style="margin-top:8px">
              复制翻译
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { documentAiApi } from '@/api/documentAi'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  docId: number
  docType: string
}>()

defineEmits(['close'])

const activeTab = ref('chat')

// ============ 聊天 ============
const chatQuestion = ref('')
const chatMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const chatLoading = ref(false)
const chatMessagesRef = ref<HTMLElement | null>(null)

async function sendChat() {
  const q = chatQuestion.value.trim()
  if (!q || chatLoading.value) return

  chatMessages.value.push({ role: 'user', content: q })
  chatQuestion.value = ''
  chatLoading.value = true

  await nextTick()
  scrollChatToBottom()

  try {
    const res = await documentAiApi.chat(props.docId, { question: q })
    chatMessages.value.push({ role: 'assistant', content: res.content })
  } catch (e: any) {
    chatMessages.value.push({
      role: 'assistant',
      content: '抱歉，处理失败：' + (e.response?.data?.message || e.message || '未知错误'),
    })
  } finally {
    chatLoading.value = false
    await nextTick()
    scrollChatToBottom()
  }
}

function scrollChatToBottom() {
  if (chatMessagesRef.value) {
    chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  }
}

// ============ 摘要 ============
const summaryText = ref('')
const summaryLoading = ref(false)

async function generateSummary() {
  summaryLoading.value = true
  try {
    const res = await documentAiApi.summarize(props.docId)
    summaryText.value = res.content
  } catch (e: any) {
    ElMessage.error('生成摘要失败：' + (e.response?.data?.message || e.message))
  } finally {
    summaryLoading.value = false
  }
}

// ============ 翻译 ============
const targetLang = ref('en')
const translateText = ref('')
const translatedText = ref('')
const translateLoading = ref(false)

async function doTranslate() {
  translateLoading.value = true
  try {
    const res = await documentAiApi.translate(props.docId, {
      text: translateText.value,
      targetLang: targetLang.value,
    })
    translatedText.value = res.content
  } catch (e: any) {
    ElMessage.error('翻译失败：' + (e.response?.data?.message || e.message))
  } finally {
    translateLoading.value = false
  }
}

// ============ 工具函数 ============
function renderMarkdown(text: string): string {
  // 简单的 Markdown 渲染（粗体、斜体、换行、代码）
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制到剪贴板')
}
</script>

<style scoped>
.ai-panel {
  width: 320px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #e4e7ed;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}

.close-btn {
  margin-left: auto;
  background: none;
  border: none;
  cursor: pointer;
  color: #909399;
  font-size: 14px;
}

.ai-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
}

.ai-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.ai-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 聊天 */
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-msg {
  display: flex;
  gap: 8px;
  max-width: 90%;
}

.chat-msg.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  font-size: 18px;
  flex-shrink: 0;
}

.msg-content {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.msg-content :deep(code) {
  background: #f0f0f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.chat-msg.user .msg-content {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-msg.assistant .msg-content {
  background: #f5f7fa;
  color: #303133;
  border-bottom-left-radius: 2px;
}

.msg-content.typing {
  color: #909399;
  font-style: italic;
}

.chat-input {
  padding: 8px 12px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}

/* 摘要 */
.summary-container {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  flex: 1;
}

.summary-result, .translate-result {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
}

.result-content {
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}

/* 翻译 */
.translate-container {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  flex: 1;
}

.translate-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
