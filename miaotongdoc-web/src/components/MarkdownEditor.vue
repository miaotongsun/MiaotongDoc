<template>
  <div class="md-wrapper">
    <!-- 轻量工具栏 -->
    <div class="md-toolbar" v-if="editor">
      <div class="tb-center">
        <el-dropdown trigger="click" @command="setBlockType">
          <button class="tb block-select">{{ blockLabel }} ▾</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="paragraph">正文</el-dropdown-item>
              <el-dropdown-item command="heading1">标题 1</el-dropdown-item>
              <el-dropdown-item command="heading2">标题 2</el-dropdown-item>
              <el-dropdown-item command="heading3">标题 3</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="tb-sep" />
        <button class="tb" :class="{on: editor.isActive('bold')}" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button>
        <button class="tb" :class="{on: editor.isActive('italic')}" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button>
        <button class="tb" :class="{on: editor.isActive('underline')}" @click="editor.chain().focus().toggleUnderline().run()"><u>U</u></button>
        <button class="tb" :class="{on: editor.isActive('strike')}" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button>
        <span class="tb-sep" />
        <button class="tb" :class="{on: editor.isActive('bulletList')}" @click="editor.chain().focus().toggleBulletList().run()">•</button>
        <button class="tb" :class="{on: editor.isActive('orderedList')}" @click="editor.chain().focus().toggleOrderedList().run()">1.</button>
        <button class="tb" :class="{on: editor.isActive('taskList')}" @click="editor.chain().focus().toggleTaskList().run()">☑</button>
        <span class="tb-sep" />
        <button class="tb" :class="{on: editor.isActive('blockquote')}" @click="editor.chain().focus().toggleBlockquote().run()">❝</button>
        <button class="tb" :class="{on: editor.isActive('codeBlock')}" @click="editor.chain().focus().toggleCodeBlock().run()">{ }</button>
        <button class="tb" @click="insertTable">⊞</button>
        <span class="tb-sep" />
        <button class="tb" @click="editor.chain().focus().undo().run()" :disabled="!editor.can().undo()">↩</button>
        <button class="tb" @click="editor.chain().focus().redo().run()" :disabled="!editor.can().redo()">↪</button>
      </div>
      <div class="tb-right">
        <div v-if="collabUsers.length > 0" class="collab-avatars">
          <div v-for="u in collabUsers" :key="u.clientId" class="avatar" :style="{background:u.color}" :title="u.name">{{ u.name.charAt(0) }}</div>
        </div>
        <button class="tb ai-btn" :class="{on: showAi}" @click="showAi = !showAi">✨ AI</button>
      </div>
    </div>

    <!-- 主体：编辑区 + AI 面板 -->
    <div class="md-body">
      <div class="md-editor-scroll">
        <editor-content :editor="editor" class="md-editor-content" />
      </div>

      <!-- AI 侧边面板 -->
      <transition name="slide">
        <div v-if="showAi" class="ai-panel">
          <div class="ai-hdr">
            <span class="ai-title">✨ AI 助手</span>
            <button class="ai-close" @click="showAi = false">✕</button>
          </div>
          <el-tabs v-model="aiTab" class="ai-tabs">
            <el-tab-pane label="问答" name="chat">
              <div class="ai-chat">
                <div class="ai-msgs" ref="aiMsgRef">
                  <div v-for="(m,i) in aiMessages" :key="i" class="ai-msg" :class="m.role">
                    <div class="bubble">{{ m.content }}</div>
                  </div>
                  <div v-if="aiLoading" class="ai-msg assistant"><div class="bubble dim">思考中...</div></div>
                </div>
                <div class="ai-input">
                  <el-input v-model="aiQuestion" placeholder="输入问题... Ctrl+Enter 发送" @keydown.enter.ctrl="sendAiChat" :disabled="aiLoading" size="small" />
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="摘要" name="summary">
              <div class="ai-feat">
                <el-button type="primary" @click="generateSummary" :loading="summaryLoading" size="small" style="width:100%">生成文档摘要</el-button>
                <div v-if="summaryText" class="ai-result">{{ summaryText }}</div>
                <el-empty v-else :image-size="40" description="点击生成摘要" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="翻译" name="translate">
              <div class="ai-feat">
                <el-select v-model="translateLang" size="small" style="width:100%">
                  <el-option label="中文" value="zh" /><el-option label="English" value="en" />
                  <el-option label="日本語" value="ja" /><el-option label="한국어" value="ko" />
                </el-select>
                <el-input v-model="translateText" type="textarea" :rows="2" placeholder="输入文本（留空翻译全文）" />
                <el-button type="primary" @click="doTranslate" :loading="translateLoading" size="small" style="width:100%">翻译</el-button>
                <div v-if="translatedText" class="ai-result">{{ translatedText }}</div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="改写" name="rewrite">
              <div class="ai-feat">
                <el-input v-model="rewriteInstruction" type="textarea" :rows="2" placeholder="改写指令：更正式、更简洁、扩写..." />
                <el-button type="primary" @click="doRewrite" :loading="rewriteLoading" size="small" style="width:100%">改写选中文本</el-button>
                <div v-if="rewrittenText" class="ai-result">
                  {{ rewrittenText }}
                  <el-button size="small" @click="applyRewrite" style="margin-top:8px">应用到文档</el-button>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </transition>
    </div>
  </div>
</template>

<script lang="ts">
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

interface YjsEntry { ydoc: Y.Doc; provider: WebsocketProvider; refCount: number }
const registry = new Map<string, YjsEntry>()

function getYjsEntry(docKey: string): YjsEntry {
  const key = `md-${docKey}`
  let entry = registry.get(key)
  if (!entry) {
    const ydoc = new Y.Doc()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/ws/yjs/`
    const provider = new WebsocketProvider(url, key, ydoc)
    entry = { ydoc, provider, refCount: 0 }
    registry.set(key, entry)
  }
  entry.refCount++
  return entry
}

function releaseYjsEntry(docKey: string) {
  const key = `md-${docKey}`
  const entry = registry.get(key)
  if (!entry) return
  entry.refCount--
  if (entry.refCount <= 0) {
    entry.provider.awareness.setLocalState(null)
    entry.provider.disconnect()
    entry.provider.destroy()
    entry.ydoc.destroy()
    registry.delete(key)
  }
}
</script>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Highlight from '@tiptap/extension-highlight'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import TextAlign from '@tiptap/extension-text-align'
import Code from '@tiptap/extension-code'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCursor from '@tiptap/extension-collaboration-cursor'
import { documentAiApi } from '@/api/documentAi'
import { ElMessageBox, ElMessage } from 'element-plus'

const props = defineProps<{
  docId: number
  docKey: string
  initialContent?: string
  canEdit: boolean
  userName: string
  userId: number
}>()

const emit = defineEmits(['ready', 'stateChange', 'contentChange'])

const blockType = ref('paragraph')
const blockLabel = computed(() => {
  const map: Record<string, string> = { paragraph: '正文', heading1: '标题 1', heading2: '标题 2', heading3: '标题 3' }
  return map[blockType.value] || '正文'
})
const collabUsers = ref<Array<{ clientId: number; name: string; color: string }>>([])
const showAi = ref(false)

// AI state
const aiTab = ref('chat')
const aiQuestion = ref('')
const aiMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const aiLoading = ref(false)
const aiMsgRef = ref<HTMLElement | null>(null)
const summaryText = ref('')
const summaryLoading = ref(false)
const translateLang = ref('en')
const translateText = ref('')
const translatedText = ref('')
const translateLoading = ref(false)
const rewriteInstruction = ref('')
const rewrittenText = ref('')
const rewriteLoading = ref(false)

const COLORS = ['#F44336','#E91E63','#9C27B0','#673AB7','#3F51B5','#2196F3','#03A9F4','#00BCD4','#009688','#4CAF50','#FF9800','#FF5722']
const myColor = COLORS[Math.floor(Math.random() * COLORS.length)]
const myName = props.userName || '匿名用户'

const entry = getYjsEntry(props.docKey)
const { ydoc, provider } = entry

provider.awareness.setLocalStateField('user', { name: myName, color: myColor })

provider.awareness.on('change', () => {
  const states = Array.from(provider.awareness.getStates().entries())
  collabUsers.value = states
    .filter(([id]) => id !== ydoc.clientID)
    .map(([id, s]: [number, any]) => ({ clientId: id, name: s.user?.name || '匿名', color: s.user?.color || '#999' }))
})

let contentSet = false

const editor = useEditor({
  extensions: [
    StarterKit,
    Highlight.configure({ multicolor: true }),
    Image,
    Link.configure({ openOnClick: false }),
    Placeholder.configure({ placeholder: '开始输入...' }),
    Table.configure({ resizable: true }),
    TableRow, TableCell, TableHeader,
    TaskList, TaskItem.configure({ nested: true }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Code,
    Collaboration.configure({ document: ydoc, field: 'content' }),
    CollaborationCursor.configure({ provider, user: { name: myName, color: myColor } }),
  ],
  editable: props.canEdit,
  onUpdate: () => {
    emit('stateChange', 'editing')
    emit('contentChange', editor.value?.getHTML() || '')
  },
  onCreate: ({ editor: ed }) => {
    if (props.initialContent && !contentSet) {
      const fragment = ydoc.getXmlFragment('content')
      if (fragment.length === 0) {
        ed.commands.setContent(props.initialContent)
        contentSet = true
      }
    }
    emit('ready')
  },
})

watch(() => props.canEdit, v => { if (editor.value) editor.value.setEditable(v) })
onBeforeUnmount(() => { editor.value?.destroy(); releaseYjsEntry(props.docKey) })

function setBlockType(val: string) {
  blockType.value = val
  if (!editor.value) return
  if (val === 'paragraph') editor.value.chain().focus().setParagraph().run()
  else if (val.startsWith('heading')) {
    editor.value.chain().focus().toggleHeading({ level: parseInt(val[7]) as 1|2|3 }).run()
  }
}

function insertTable() { editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() }

async function addImage() {
  try {
    const { value } = await ElMessageBox.prompt('图片 URL', '插入图片', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' })
    if (value) editor.value?.chain().focus().setImage({ src: value }).run()
  } catch {}
}

// AI
async function sendAiChat() {
  const q = aiQuestion.value.trim(); if (!q || aiLoading.value) return
  aiMessages.value.push({ role: 'user', content: q }); aiQuestion.value = ''; aiLoading.value = true
  await nextTick(); if (aiMsgRef.value) aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight
  try { aiMessages.value.push({ role: 'assistant', content: (await documentAiApi.chat(props.docId, { question: q })).content }) }
  catch (e: any) { aiMessages.value.push({ role: 'assistant', content: '错误：' + (e.response?.data?.message || e.message) }) }
  finally { aiLoading.value = false; await nextTick(); if (aiMsgRef.value) aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight }
}

async function generateSummary() {
  summaryLoading.value = true
  try { summaryText.value = (await documentAiApi.summarize(props.docId)).content }
  catch { ElMessage.error('生成失败') } finally { summaryLoading.value = false }
}

async function doTranslate() {
  translateLoading.value = true
  try { translatedText.value = (await documentAiApi.translate(props.docId, { text: translateText.value, targetLang: translateLang.value })).content }
  catch { ElMessage.error('翻译失败') } finally { translateLoading.value = false }
}

async function doRewrite() {
  if (!editor.value) return
  const { from, to } = editor.value.state.selection
  const text = editor.value.state.doc.textBetween(from, to)
  if (!text) { ElMessage.warning('请先选中文本'); return }
  rewriteLoading.value = true
  try { rewrittenText.value = (await documentAiApi.rewrite(props.docId, { text, instruction: rewriteInstruction.value || '改写以下文本' })).content }
  catch { ElMessage.error('改写失败') } finally { rewriteLoading.value = false }
}

function applyRewrite() {
  if (!editor.value || !rewrittenText.value) return
  const { from, to } = editor.value.state.selection
  editor.value.chain().focus().deleteRange({ from, to }).insertContent(rewrittenText.value).run()
  rewrittenText.value = ''; ElMessage.success('已应用')
}

function getContent(): string { return editor.value?.getHTML() || '' }
defineExpose({ getContent })
</script>

<style scoped>
.md-wrapper { display: flex; flex-direction: column; height: 100%; background: #fff; }

/* 工具栏 */
.md-toolbar {
  display: flex; align-items: center; justify-content: center;
  padding: 4px 24px; background: #fcfcfc; border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0; min-height: 36px; position: relative;
}
.tb-center { display: flex; align-items: center; gap: 1px; }
.tb-right { position: absolute; right: 24px; display: flex; align-items: center; gap: 8px; }
.tb-right { gap: 8px; }
.tb-sep { width: 1px; height: 16px; background: #e8e8e8; margin: 0 4px; }
.tb {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 28px; height: 28px; padding: 0 5px; border: none; background: transparent;
  border-radius: 3px; cursor: pointer; font-size: 13px; color: #666;
  transition: all 0.12s;
}
.tb:hover { background: #f5f5f5; color: #333; }
.tb.on { background: #e8f0fe; color: #1a73e8; }
.tb:disabled { opacity: 0.25; cursor: not-allowed; }
.block-select { font-size: 12px; color: #555; padding: 0 8px; font-weight: 500; }
.ai-btn { color: #7c3aed; font-weight: 500; font-size: 12px; }
.ai-btn.on { background: #f5f3ff; }

.collab-avatars { display: flex; }
.avatar {
  width: 24px; height: 24px; border-radius: 50%; color: #fff; font-size: 11px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #fff; margin-left: -6px; cursor: default;
}
.avatar:first-child { margin-left: 0; }

/* 主体 */
.md-body { flex: 1; display: flex; overflow: hidden; }

.md-editor-scroll { flex: 1; overflow-y: auto; }
.md-editor-content { height: 100%; padding: 0; }

/* 编辑器排版 */
.md-editor-content :deep(.tiptap) { outline: none; height: 100%; min-height: 100%; font-size: 15px; line-height: 1.8; color: #24292f; padding: 24px 32px; box-sizing: border-box; }
.md-editor-content :deep(.tiptap p) { margin: 0.5em 0; }
.md-editor-content :deep(.tiptap h1) { font-size: 26px; font-weight: 700; margin: 1.2em 0 0.4em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h2) { font-size: 20px; font-weight: 600; margin: 1em 0 0.3em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h3) { font-size: 16px; font-weight: 600; margin: 0.8em 0 0.3em; color: #1b1f23; }
.md-editor-content :deep(.tiptap ul), .md-editor-content :deep(.tiptap ol) { padding-left: 1.5em; }
.md-editor-content :deep(.tiptap blockquote) { border-left: 3px solid #d0d7de; padding: 0.5em 1em; margin: 0.8em 0; color: #57606a; background: #f6f8fa; }
.md-editor-content :deep(.tiptap pre) { background: #161b22; color: #c9d1d9; border-radius: 6px; padding: 14px 18px; margin: 0.8em 0; overflow-x: auto; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; }
.md-editor-content :deep(.tiptap code) { background: #eff1f3; padding: 2px 5px; border-radius: 3px; font-size: 0.88em; font-family: 'JetBrains Mono', Consolas, monospace; }
.md-editor-content :deep(.tiptap pre code) { background: transparent; color: inherit; padding: 0; }
.md-editor-content :deep(.tiptap table) { border-collapse: collapse; width: 100%; margin: 0.8em 0; }
.md-editor-content :deep(.tiptap th) { background: #f6f8fa; font-weight: 600; border: 1px solid #d0d7de; padding: 8px 12px; }
.md-editor-content :deep(.tiptap td) { border: 1px solid #d0d7de; padding: 8px 12px; }
.md-editor-content :deep(.tiptap hr) { border: none; border-top: 1px solid #d0d7de; margin: 1.5em 0; }
.md-editor-content :deep(.tiptap img) { max-width: 100%; border-radius: 6px; }
.md-editor-content :deep(.tiptap a) { color: #0969da; }
.md-editor-content :deep(.tiptap mark) { background: #fff8c5; padding: 1px 3px; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"]) { list-style: none; padding-left: 0; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"] li) { display: flex; align-items: flex-start; gap: 6px; }
.md-editor-content :deep(.tiptap p.is-editor-empty:first-child::before) { color: #a0a0a0; content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
.md-editor-content :deep(.collaboration-cursor__caret) { border-left: 2px solid; margin: 0 -1px; pointer-events: none; position: relative; }
.md-editor-content :deep(.collaboration-cursor__label) { border-radius: 2px 2px 2px 0; color: #fff; font-size: 10px; font-weight: 600; left: -1px; padding: 1px 5px; position: absolute; top: -1.5em; user-select: none; white-space: nowrap; }

/* AI 面板 */
.ai-panel {
  width: 360px; background: #fafbfc; border-left: 1px solid #e1e4e8;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.ai-hdr { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-bottom: 1px solid #e1e4e8; }
.ai-title { font-size: 13px; font-weight: 600; color: #24292f; }
.ai-close { background: none; border: none; cursor: pointer; color: #8b949e; font-size: 14px; padding: 4px; }
.ai-close:hover { color: #24292f; }
.ai-tabs { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.ai-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 14px; }
.ai-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.ai-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.ai-tabs :deep(.el-tab-pane) { height: 100%; display: flex; flex-direction: column; }

.ai-chat { display: flex; flex-direction: column; height: 100%; }
.ai-msgs { flex: 1; overflow-y: auto; padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; }
.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.bubble { padding: 8px 12px; border-radius: 8px; font-size: 13px; line-height: 1.5; max-width: 90%; word-break: break-word; }
.ai-msg.user .bubble { background: #0969da; color: #fff; }
.ai-msg.assistant .bubble { background: #f0f2f5; color: #24292f; }
.bubble.dim { color: #8b949e; font-style: italic; }
.ai-input { padding: 10px 14px; border-top: 1px solid #e1e4e8; }

.ai-feat { padding: 14px; display: flex; flex-direction: column; gap: 10px; overflow-y: auto; flex: 1; }
.ai-result { background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.6; color: #24292f; white-space: pre-wrap; }

.slide-enter-active, .slide-leave-active { transition: all 0.2s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); opacity: 0; }
</style>
