<template>
  <div class="md-wrapper">
    <!-- 顶部工具栏 -->
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
              <el-dropdown-item command="codeBlock">代码块</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="tb-sep" />
        <el-tooltip content="粗体 Ctrl+B" :show-after="400"><button class="tb" :class="{on:editor.isActive('bold')}" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button></el-tooltip>
        <el-tooltip content="斜体 Ctrl+I" :show-after="400"><button class="tb" :class="{on:editor.isActive('italic')}" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button></el-tooltip>
        <el-tooltip content="下划线 Ctrl+U" :show-after="400"><button class="tb" :class="{on:editor.isActive('underline')}" @click="editor.chain().focus().toggleUnderline().run()"><u>U</u></button></el-tooltip>
        <el-tooltip content="删除线" :show-after="400"><button class="tb" :class="{on:editor.isActive('strike')}" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button></el-tooltip>
        <el-tooltip content="行内代码" :show-after="400"><button class="tb" :class="{on:editor.isActive('code')}" @click="editor.chain().focus().toggleCode().run()"><code>&lt;&gt;</code></button></el-tooltip>
        <el-tooltip content="下标" :show-after="400"><button class="tb" :class="{on:editor.isActive('subscript')}" @click="editor.chain().focus().toggleSubscript().run()"><span style="font-size:11px">X₂</span></button></el-tooltip>
        <el-tooltip content="上标" :show-after="400"><button class="tb" :class="{on:editor.isActive('superscript')}" @click="editor.chain().focus().toggleSuperscript().run()"><span style="font-size:11px">X²</span></button></el-tooltip>
        <el-popover trigger="click" :width="160" placement="bottom">
          <template #reference><el-tooltip content="高亮" :show-after="400"><button class="tb" :class="{on:editor.isActive('highlight')}"><span class="hl-icon">H</span></button></el-tooltip></template>
          <div class="color-grid">
            <button v-for="c in hlColors" :key="c.v" class="color-swatch" :style="{background:c.v}" :title="c.l" @click="editor.chain().focus().toggleHighlight({color:c.v}).run()" />
            <button class="color-swatch remove" @click="editor.chain().focus().unsetHighlight().run()">✕</button>
          </div>
        </el-popover>
        <span class="tb-sep" />
        <el-tooltip content="无序列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('bulletList')}" @click="editor.chain().focus().toggleBulletList().run()">•</button></el-tooltip>
        <el-tooltip content="有序列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('orderedList')}" @click="editor.chain().focus().toggleOrderedList().run()">1.</button></el-tooltip>
        <el-tooltip content="任务列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('taskList')}" @click="editor.chain().focus().toggleTaskList().run()">☑</button></el-tooltip>
        <span class="tb-sep" />
        <el-dropdown trigger="click" @command="setAlign">
          <el-tooltip content="对齐" :show-after="400"><button class="tb">≡</button></el-tooltip>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="left">左对齐</el-dropdown-item>
              <el-dropdown-item command="center">居中</el-dropdown-item>
              <el-dropdown-item command="right">右对齐</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="tb-sep" />
        <el-tooltip content="引用块" :show-after="400"><button class="tb" :class="{on:editor.isActive('blockquote')}" @click="editor.chain().focus().toggleBlockquote().run()">❝</button></el-tooltip>
        <el-tooltip content="代码块" :show-after="400"><button class="tb" :class="{on:editor.isActive('codeBlock')}" @click="editor.chain().focus().toggleCodeBlock().run()">{ }</button></el-tooltip>
        <el-tooltip content="表格" :show-after="400"><button class="tb" @click="insertTable">⊞</button></el-tooltip>
        <el-tooltip content="图片" :show-after="400"><button class="tb" @click="addImage">🖼</button></el-tooltip>
        <el-tooltip content="链接" :show-after="400"><button class="tb" :class="{on:editor.isActive('link')}" @click="setLink">🔗</button></el-tooltip>
        <el-tooltip content="分割线" :show-after="400"><button class="tb" @click="editor.chain().focus().setHorizontalRule().run()">—</button></el-tooltip>
        <el-tooltip content="清除格式" :show-after="400"><button class="tb" @click="editor.chain().focus().clearNodes().unsetAllMarks().run()">🧹</button></el-tooltip>
        <span class="tb-sep" />
        <el-tooltip content="撤销" :show-after="400"><button class="tb" @click="editor.chain().focus().undo().run()" :disabled="!editor.can().undo()">↩</button></el-tooltip>
        <el-tooltip content="重做" :show-after="400"><button class="tb" @click="editor.chain().focus().redo().run()" :disabled="!editor.can().redo()">↪</button></el-tooltip>
      </div>
      <div class="tb-right">
        <div v-if="collabUsers.length" class="collab-avatars">
          <div v-for="u in collabUsers" :key="u.id" class="avatar" :style="{background:u.color}" :title="u.name">{{ u.name.charAt(0) }}</div>
        </div>
        <el-tooltip content="AI 助手 (侧边面板)" :show-after="400"><button class="tb ai-btn" :class="{on:showAi}" @click="showAi=!showAi">✨ AI</button></el-tooltip>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="md-body">
      <div class="md-editor-scroll">
        <editor-content :editor="editor" class="md-editor-content" />

        <!-- BubbleMenu DOM 元素（由 BubbleMenu 扩展管理显示/隐藏） -->
        <div ref="bubbleMenuEl" class="bubble-bar" v-show="false">
          <button class="bubble-btn" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button>
          <button class="bubble-btn" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button>
          <button class="bubble-btn" @click="editor.chain().focus().toggleCode().run()"><code>&lt;&gt;</code></button>
          <button class="bubble-btn" @click="setLink">🔗</button>
          <span class="bubble-sep" />
          <el-dropdown trigger="click" @command="handleAiAction" size="small">
            <button class="bubble-btn ai">✨ AI ▾</button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rewrite">🔄 改写</el-dropdown-item>
                <el-dropdown-item command="expand">📝 扩写</el-dropdown-item>
                <el-dropdown-item command="shorten">✂️ 缩写</el-dropdown-item>
                <el-dropdown-item command="formal">👔 更正式</el-dropdown-item>
                <el-dropdown-item command="casual">😊 更随意</el-dropdown-item>
                <el-dropdown-item command="translate-en">🌐 翻译英文</el-dropdown-item>
                <el-dropdown-item command="translate-zh">🌐 翻译中文</el-dropdown-item>
                <el-dropdown-item command="explain">💡 解释</el-dropdown-item>
                <el-dropdown-item command="summarize">📋 摘要</el-dropdown-item>
                <el-dropdown-item command="fix-grammar">✏️ 修正语法</el-dropdown-item>
                <el-dropdown-item command="continue">➡️ 续写</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- FloatingMenu DOM 元素 -->
        <div ref="floatMenuEl" class="float-bar" v-show="false">
          <button class="float-btn" @click="editor.chain().focus().toggleHeading({level:1}).run()">H1</button>
          <button class="float-btn" @click="editor.chain().focus().toggleHeading({level:2}).run()">H2</button>
          <button class="float-btn" @click="editor.chain().focus().toggleBulletList().run()">•</button>
          <button class="float-btn" @click="editor.chain().focus().toggleCodeBlock().run()">{ }</button>
          <button class="float-btn" @click="insertTable">⊞</button>
          <button class="float-btn ai" @click="aiSlashCommand">✨ AI</button>
        </div>
      </div>

      <!-- 底部状态栏 -->
      <div class="md-status-bar">
        <span class="stat-item">{{ charCount }} 字符</span>
        <span class="stat-item">{{ wordCount }} 词</span>
        <span v-if="aiProcessing" class="stat-item ai-status">✨ AI 处理中...</span>
      </div>

      <!-- AI 侧边面板 -->
      <transition name="slide">
        <div v-if="showAi" class="ai-panel">
          <div class="ai-hdr"><span>✨ AI 助手</span><button class="ai-close" @click="showAi=false">✕</button></div>
          <el-tabs v-model="aiTab" class="ai-tabs">
            <el-tab-pane label="问答" name="chat">
              <div class="ai-chat">
                <div class="ai-msgs" ref="aiMsgRef">
                  <div v-for="(m,i) in aiMessages" :key="i" class="ai-msg" :class="m.role">
                    <div class="bubble">{{ m.content }}</div>
                  </div>
                  <div v-if="aiLoading" class="ai-msg assistant"><div class="bubble dim">思考中...</div></div>
                </div>
                <div class="ai-input"><el-input v-model="aiQuestion" placeholder="Ctrl+Enter 发送" @keydown.enter.ctrl="sendAiChat" :disabled="aiLoading" size="small" /></div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="摘要" name="summary">
              <div class="ai-feat">
                <el-button type="primary" @click="generateSummary" :loading="summaryLoading" size="small" style="width:100%">生成摘要</el-button>
                <div v-if="summaryText" class="ai-result">{{ summaryText }}</div>
                <el-empty v-else :image-size="40" description="点击生成摘要" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="翻译" name="translate">
              <div class="ai-feat">
                <el-select v-model="trLang" size="small" style="width:100%"><el-option label="中文" value="zh" /><el-option label="English" value="en" /><el-option label="日本語" value="ja" /></el-select>
                <el-input v-model="trText" type="textarea" :rows="2" placeholder="留空翻译全文" />
                <el-button type="primary" @click="doTranslate" :loading="trLoading" size="small" style="width:100%">翻译</el-button>
                <div v-if="trResult" class="ai-result">{{ trResult }}</div>
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
  const key = `md-${docKey}`; let e = registry.get(key)
  if (!e) { const ydoc = new Y.Doc(); const p = window.location.protocol === 'https:' ? 'wss:' : 'ws:'; const provider = new WebsocketProvider(`${p}//${window.location.host}/ws/yjs/`, key, ydoc); e = { ydoc, provider, refCount: 0 }; registry.set(key, e) }
  e.refCount++; return e
}
function releaseYjsEntry(docKey: string) {
  const key = `md-${docKey}`, e = registry.get(key); if (!e) return
  e.refCount--; if (e.refCount <= 0) { e.provider.awareness.setLocalState(null); e.provider.disconnect(); e.provider.destroy(); e.ydoc.destroy(); registry.delete(key) }
}
</script>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorContent } from '@tiptap/vue-3'
import { Editor } from '@tiptap/core'
import BubbleMenu from '@tiptap/extension-bubble-menu'
import FloatingMenu from '@tiptap/extension-floating-menu'
import StarterKit from '@tiptap/starter-kit'
import Highlight from '@tiptap/extension-highlight'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import TextAlign from '@tiptap/extension-text-align'
import Subscript from '@tiptap/extension-subscript'
import Superscript from '@tiptap/extension-superscript'
import Typography from '@tiptap/extension-typography'
import CharacterCount from '@tiptap/extension-character-count'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCursor from '@tiptap/extension-collaboration-cursor'
import { documentAiApi } from '@/api/documentAi'
import { ElMessageBox, ElMessage } from 'element-plus'

const props = defineProps<{ docId: number; docKey: string; initialContent?: string; canEdit: boolean; userName: string; userId: number }>()
const emit = defineEmits(['ready', 'stateChange', 'contentChange'])

const bubbleMenuEl = ref<HTMLElement | null>(null)
const floatMenuEl = ref<HTMLElement | null>(null)
const blockType = ref('paragraph')
const blockLabel = computed(() => ({ paragraph:'正文', heading1:'标题 1', heading2:'标题 2', heading3:'标题 3', codeBlock:'代码块' }[blockType.value] || '正文'))
const collabUsers = ref<Array<{ id: number; name: string; color: string }>>([])
const showAi = ref(false)
const aiProcessing = ref(false)

const hlColors = [
  { v: '#fef08a', l: '黄' }, { v: '#bbf7d0', l: '绿' }, { v: '#bfdbfe', l: '蓝' },
  { v: '#fbcfe8', l: '粉' }, { v: '#fed7aa', l: '橙' }, { v: '#e9d5ff', l: '紫' },
]

const COLORS = ['#F44336','#E91E63','#9C27B0','#673AB7','#3F51B5','#2196F3','#03A9F4','#00BCD4','#009688','#4CAF50','#FF9800','#FF5722']
const myColor = COLORS[Math.floor(Math.random() * COLORS.length)]
const myName = props.userName || '匿名用户'

const entry = getYjsEntry(props.docKey)
const { ydoc, provider } = entry
provider.awareness.setLocalStateField('user', { name: myName, color: myColor })
provider.awareness.on('change', () => {
  collabUsers.value = Array.from(provider.awareness.getStates().entries())
    .filter(([id]) => id !== ydoc.clientID)
    .map(([id, s]: [number, any]) => ({ id, name: s.user?.name || '匿名', color: s.user?.color || '#999' }))
})

let contentSet = false

const charCount = ref(0)
const wordCount = ref(0)

// 编辑器在 onMounted 中创建（需要 DOM refs 给 BubbleMenu/FloatingMenu）
const editor = ref<Editor | null>(null)

onMounted(() => {
  const ed = new Editor({
    extensions: [
      StarterKit.configure({ link: { openOnClick: false } }),
      Highlight.configure({ multicolor: true }),
      Image,
      Placeholder.configure({ placeholder: '输入 / 唤出快捷菜单，或直接开始写作...' }),
      Table.configure({ resizable: true }), TableRow, TableCell, TableHeader,
      TaskList, TaskItem.configure({ nested: true }),
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Subscript, Superscript, Typography,
      CharacterCount,
      Collaboration.configure({ document: ydoc, field: 'content' }),
      CollaborationCursor.configure({ provider, user: { name: myName, color: myColor } }),
      // BubbleMenu：选中文本时弹出
      BubbleMenu.configure({
        element: bubbleMenuEl.value,
        pluginKey: 'bubbleMenu',
        shouldShow: ({ editor: e, state }) => {
          const { from, to } = state.selection
          return from !== to && !e.isActive('codeBlock')
        },
      }),
      // FloatingMenu：空行时弹出
      FloatingMenu.configure({
        element: floatMenuEl.value,
        pluginKey: 'floatingMenu',
      }),
    ],
    editable: props.canEdit,
    onUpdate: ({ editor: e }) => {
      emit('stateChange', 'editing')
      emit('contentChange', e.getHTML())
      charCount.value = e.storage.characterCount.characters()
      wordCount.value = e.storage.characterCount.words()
    },
    onCreate: ({ editor: e }) => {
      if (props.initialContent && !contentSet) {
        const fragment = ydoc.getXmlFragment('content')
        if (fragment.length === 0) {
          const text = new Y.XmlText()
          text.insert(0, props.initialContent)
          ydoc.transact(() => { fragment.insert(0, [text]) })
          contentSet = true
        }
      }
      charCount.value = e.storage.characterCount.characters()
      wordCount.value = e.storage.characterCount.words()
      emit('ready')
    },
  })

  editor.value = ed

  // v-show=false 默认隐藏，BubbleMenu/FloatingMenu 扩展会自动控制显示
  if (bubbleMenuEl.value) bubbleMenuEl.value.style.display = ''
  if (floatMenuEl.value) floatMenuEl.value.style.display = ''
})

watch(() => props.canEdit, v => { if (editor.value) editor.value.setEditable(v) })
onBeforeUnmount(() => { editor.value?.destroy(); releaseYjsEntry(props.docKey) })

// 工具栏操作
function setBlockType(v: string) {
  blockType.value = v; if (!editor.value) return
  if (v === 'paragraph') editor.value.chain().focus().setParagraph().run()
  else if (v === 'codeBlock') editor.value.chain().focus().toggleCodeBlock().run()
  else if (v.startsWith('heading')) editor.value.chain().focus().toggleHeading({ level: parseInt(v[7]) as 1|2|3 }).run()
}
function setAlign(v: string) { editor.value?.chain().focus().setTextAlign(v).run() }
function insertTable() { editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() }
async function addImage() {
  try { const { value } = await ElMessageBox.prompt('图片 URL', '插入图片', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' }); if (value) editor.value?.chain().focus().setImage({ src: value }).run() } catch {}
}
async function setLink() {
  if (editor.value?.isActive('link')) { editor.value.chain().focus().unsetLink().run(); return }
  try { const { value } = await ElMessageBox.prompt('链接 URL', '插入链接', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' }); if (value) editor.value?.chain().focus().setLink({ href: value }).run() } catch {}
}

// AI 浮动菜单操作
async function handleAiAction(cmd: string) {
  if (!editor.value) return
  const { from, to } = editor.value.state.selection
  const selectedText = editor.value.state.doc.textBetween(from, to)
  if (!selectedText && cmd !== 'continue') { ElMessage.warning('请先选中文本'); return }

  aiProcessing.value = true
  try {
    let result = ''
    const instructions: Record<string, string> = {
      rewrite: '改写以下文本，保持原意但使用不同的表达方式',
      expand: '扩写以下文本，增加更多细节和描述',
      shorten: '缩写以下文本，保留核心信息，去除冗余',
      formal: '将以下文本改写为更正式、专业的语气',
      casual: '将以下文本改写为更轻松、随意的语气',
      'fix-grammar': '修正以下文本的语法错误，保持原意',
    }
    if (cmd.startsWith('translate-')) {
      const lang = cmd === 'translate-en' ? 'English' : '中文'
      const res = await documentAiApi.translate(props.docId, { text: selectedText, targetLang: lang === 'English' ? 'en' : 'zh' })
      result = res.content
    } else if (cmd === 'explain') {
      const res = await documentAiApi.chat(props.docId, { question: `请用简洁的语言解释以下内容：\n${selectedText}` })
      result = res.content
    } else if (cmd === 'summarize') {
      const res = await documentAiApi.chat(props.docId, { question: `请摘要以下内容：\n${selectedText}` })
      result = res.content
    } else if (cmd === 'continue') {
      const context = editor.value.state.doc.textBetween(Math.max(0, from - 500), from)
      const res = await documentAiApi.chat(props.docId, { question: `请根据以下上下文继续写作，直接输出续写内容，不要加任何前缀：\n${context}` })
      result = res.content
    } else {
      const res = await documentAiApi.rewrite(props.docId, { text: selectedText, instruction: instructions[cmd] || '改写' })
      result = res.content
    }
    if (result) {
      if (cmd === 'continue') {
        editor.value.chain().focus().insertContent(result).run()
      } else if (cmd === 'explain' || cmd === 'summarize') {
        ElMessageBox.alert(result, cmd === 'explain' ? '解释' : '摘要', { confirmButtonText: '关闭' })
      } else {
        editor.value.chain().focus().deleteRange({ from, to }).insertContent(result).run()
      }
    }
  } catch (e: any) {
    ElMessage.error('AI 操作失败：' + (e.response?.data?.message || e.message))
  } finally { aiProcessing.value = false }
}

async function aiSlashCommand() {
  try {
    const { value } = await ElMessageBox.prompt('告诉 AI 你想写什么', '✨ AI 生成', {
      inputPlaceholder: '如：写一段项目介绍、写一份会议纪要...',
      confirmButtonText: '生成',
      cancelButtonText: '取消',
    })
    if (!value) return
    aiProcessing.value = true
    const res = await documentAiApi.chat(props.docId, { question: `请直接输出以下内容，不要加任何前缀或解释：${value}` })
    editor.value?.chain().focus().insertContent(res.content).run()
  } catch {} finally { aiProcessing.value = false }
}

// AI 侧边面板
const aiTab = ref('chat')
const aiQuestion = ref('')
const aiMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const aiLoading = ref(false)
const aiMsgRef = ref<HTMLElement | null>(null)
const summaryText = ref('')
const summaryLoading = ref(false)
const trLang = ref('en')
const trText = ref('')
const trResult = ref('')
const trLoading = ref(false)

async function sendAiChat() {
  const q = aiQuestion.value.trim(); if (!q || aiLoading.value) return
  aiMessages.value.push({ role: 'user', content: q }); aiQuestion.value = ''; aiLoading.value = true
  await nextTick(); if (aiMsgRef.value) aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight
  try { aiMessages.value.push({ role: 'assistant', content: (await documentAiApi.chat(props.docId, { question: q })).content }) }
  catch (e: any) { aiMessages.value.push({ role: 'assistant', content: '错误：' + (e.response?.data?.message || e.message) }) }
  finally { aiLoading.value = false; await nextTick(); if (aiMsgRef.value) aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight }
}
async function generateSummary() { summaryLoading.value = true; try { summaryText.value = (await documentAiApi.summarize(props.docId)).content } catch { ElMessage.error('失败') } finally { summaryLoading.value = false } }
async function doTranslate() { trLoading.value = true; try { trResult.value = (await documentAiApi.translate(props.docId, { text: trText.value, targetLang: trLang.value })).content } catch { ElMessage.error('失败') } finally { trLoading.value = false } }

function getContent() { return editor.value?.getHTML() || '' }
defineExpose({ getContent })
</script>

<style scoped>
.md-wrapper { display: flex; flex-direction: column; height: 100%; width: 100%; background: #fff; overflow: hidden; }
.md-toolbar { display: flex; align-items: center; justify-content: center; padding: 4px 16px; background: #fcfcfc; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; min-height: 36px; position: relative; }
.tb-center { display: flex; align-items: center; gap: 1px; }
.tb-right { position: absolute; right: 16px; display: flex; align-items: center; gap: 8px; }
.tb-sep { width: 1px; height: 16px; background: #e8e8e8; margin: 0 4px; }
.tb { display: inline-flex; align-items: center; justify-content: center; min-width: 28px; height: 28px; padding: 0 5px; border: none; background: transparent; border-radius: 3px; cursor: pointer; font-size: 13px; color: #666; transition: all 0.12s; }
.tb:hover { background: #f0f0f0; color: #333; }
.tb.on { background: #e8f0fe; color: #1a73e8; }
.tb:disabled { opacity: 0.25; cursor: not-allowed; }
.block-select { font-size: 12px; color: #555; padding: 0 8px; font-weight: 500; }
.hl-icon { background: #fef08a; padding: 0 3px; border-radius: 2px; font-weight: 600; }
.ai-btn { color: #7c3aed; font-weight: 500; font-size: 12px; }
.ai-btn.on { background: #f5f3ff; }
.collab-avatars { display: flex; }
.avatar { width: 24px; height: 24px; border-radius: 50%; color: #fff; font-size: 11px; font-weight: 600; display: flex; align-items: center; justify-content: center; border: 2px solid #fff; margin-left: -6px; }
.avatar:first-child { margin-left: 0; }
.color-grid { display: flex; gap: 6px; flex-wrap: wrap; padding: 4px; }
.color-swatch { width: 24px; height: 24px; border-radius: 4px; border: 2px solid transparent; cursor: pointer; transition: all 0.12s; }
.color-swatch:hover { border-color: #409eff; transform: scale(1.1); }
.color-swatch.remove { background: #fff; border: 1px solid #dcdfe6; color: #999; font-size: 12px; display: flex; align-items: center; justify-content: center; }

/* 主体 */
.md-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.md-editor-scroll { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
.md-editor-content { flex: 1; display: flex; flex-direction: column; }
.md-editor-content :deep(.ProseMirror) { flex: 1; outline: none; padding: 24px 32px; font-size: 15px; line-height: 1.8; color: #24292f; }
.md-editor-content :deep(.tiptap p) { margin: 0.5em 0; }
.md-editor-content :deep(.tiptap h1) { font-size: 26px; font-weight: 700; margin: 1em 0 0.4em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h2) { font-size: 20px; font-weight: 600; margin: 0.8em 0 0.3em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h3) { font-size: 16px; font-weight: 600; margin: 0.7em 0 0.3em; color: #1b1f23; }
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

/* BubbleMenu */
.bubble-bar { display: flex; align-items: center; gap: 2px; padding: 4px 6px; background: #1f2937; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
.bubble-btn { display: inline-flex; align-items: center; justify-content: center; min-width: 28px; height: 28px; padding: 0 6px; border: none; background: transparent; border-radius: 4px; cursor: pointer; font-size: 12px; color: #d1d5db; transition: all 0.12s; }
.bubble-btn:hover { background: #374151; color: #fff; }
.bubble-btn.on { background: #4b5563; color: #60a5fa; }
.bubble-btn.ai { color: #a78bfa; font-weight: 500; }
.bubble-btn.ai:hover { background: #374151; color: #c4b5fd; }
.bubble-sep { width: 1px; height: 18px; background: #4b5563; margin: 0 2px; }

/* FloatingMenu */
.float-bar { display: flex; align-items: center; gap: 4px; padding: 4px 8px; background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.12); border: 1px solid #e5e7eb; }
.float-btn { display: inline-flex; align-items: center; justify-content: center; min-width: 32px; height: 32px; padding: 0 8px; border: none; background: transparent; border-radius: 4px; cursor: pointer; font-size: 13px; color: #6b7280; transition: all 0.12s; }
.float-btn:hover { background: #f3f4f6; color: #1f2937; }
.float-btn.ai { color: #7c3aed; font-weight: 500; }
.float-btn.ai:hover { background: #f5f3ff; }

/* 底部状态栏 */
.md-status-bar { display: flex; align-items: center; gap: 16px; padding: 4px 16px; background: #fafafa; border-top: 1px solid #f0f0f0; font-size: 11px; color: #9ca3af; flex-shrink: 0; }
.stat-item { display: flex; align-items: center; gap: 4px; }
.ai-status { color: #7c3aed; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }

/* AI 面板 */
.ai-panel { width: 360px; background: #fafbfc; border-left: 1px solid #e1e4e8; display: flex; flex-direction: column; flex-shrink: 0; }
.ai-hdr { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-bottom: 1px solid #e1e4e8; font-size: 13px; font-weight: 600; }
.ai-close { background: none; border: none; cursor: pointer; color: #8b949e; font-size: 14px; }
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
.ai-result { background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
.slide-enter-active, .slide-leave-active { transition: all 0.2s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); opacity: 0; }
</style>
