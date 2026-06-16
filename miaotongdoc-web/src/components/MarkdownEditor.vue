<template>
  <div class="markdown-editor-wrapper">
    <div class="md-toolbar" v-if="editor">
      <button class="tb" :class="{on: editor.isActive('heading',{level:1})}" @click="editor.chain().focus().toggleHeading({level:1}).run()"><b>H1</b></button>
      <button class="tb" :class="{on: editor.isActive('heading',{level:2})}" @click="editor.chain().focus().toggleHeading({level:2}).run()"><b>H2</b></button>
      <button class="tb" :class="{on: editor.isActive('heading',{level:3})}" @click="editor.chain().focus().toggleHeading({level:3}).run()"><b>H3</b></button>
      <span class="sep" />
      <button class="tb" :class="{on: editor.isActive('bold')}" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button>
      <button class="tb" :class="{on: editor.isActive('italic')}" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button>
      <button class="tb" :class="{on: editor.isActive('underline')}" @click="editor.chain().focus().toggleUnderline().run()"><u>U</u></button>
      <button class="tb" :class="{on: editor.isActive('strike')}" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button>
      <button class="tb" :class="{on: editor.isActive('highlight')}" @click="editor.chain().focus().toggleHighlight().run()"><span style="background:#fef08a;padding:0 3px;border-radius:2px">H</span></button>
      <span class="sep" />
      <button class="tb" :class="{on: editor.isActive('bulletList')}" @click="editor.chain().focus().toggleBulletList().run()">• 列表</button>
      <button class="tb" :class="{on: editor.isActive('orderedList')}" @click="editor.chain().focus().toggleOrderedList().run()">1. 列表</button>
      <button class="tb" :class="{on: editor.isActive('taskList')}" @click="editor.chain().focus().toggleTaskList().run()">☑ 任务</button>
      <span class="sep" />
      <button class="tb" :class="{on: editor.isActive('codeBlock')}" @click="editor.chain().focus().toggleCodeBlock().run()">&lt;/&gt;</button>
      <button class="tb" :class="{on: editor.isActive('blockquote')}" @click="editor.chain().focus().toggleBlockquote().run()">❝</button>
      <button class="tb" @click="editor.chain().focus().setHorizontalRule().run()">—</button>
      <span class="sep" />
      <button class="tb" @click="insertTable">⊞ 表格</button>
      <button class="tb" @click="addImage">🖼 图片</button>
      <button class="tb" :class="{on: editor.isActive('link')}" @click="setLink">🔗 链接</button>
      <span class="sep" />
      <button class="tb" @click="editor.chain().focus().undo().run()" :disabled="!editor.can().undo()">↩</button>
      <button class="tb" @click="editor.chain().focus().redo().run()" :disabled="!editor.can().redo()">↪</button>
    </div>
    <div class="md-editor-body">
      <editor-content :editor="editor" class="md-editor-content" />
      <div v-if="collabUsers.length > 0" class="collab-bar">
        <div v-for="u in collabUsers" :key="u.clientId" class="collab-user" :style="{borderColor:u.color}">
          <span class="collab-dot" :style="{background:u.color}" /> {{ u.name }}
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * Yjs 实例管理 —— 纯 JS，完全在 Vue 响应式系统之外
 */
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

interface YjsEntry {
  ydoc: Y.Doc
  provider: WebsocketProvider
  refCount: number
}

const registry = new Map<string, YjsEntry>()

function getYjsEntry(docKey: string, initialContent?: string): YjsEntry {
  const key = `md-${docKey}`
  let entry = registry.get(key)
  if (!entry) {
    const ydoc = new Y.Doc()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/ws/yjs/`
    const provider = new WebsocketProvider(url, key, ydoc)
    // 初始内容写入 Yjs 的 XmlFragment
    if (initialContent) {
      const fragment = ydoc.getXmlFragment('content')
      if (fragment.length === 0) {
        const text = new Y.XmlText()
        text.insert(0, initialContent)
        fragment.insert(0, [text])
      }
    }
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
    entry.provider.destroy()
    entry.ydoc.destroy()
    registry.delete(key)
  }
}
</script>

<script setup lang="ts">
import { ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
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
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCursor from '@tiptap/extension-collaboration-cursor'
import { ElMessageBox } from 'element-plus'

const props = defineProps<{
  docId: number
  docKey: string
  initialContent?: string
  canEdit: boolean
  userName: string
  userId: number
}>()

const emit = defineEmits(['ready', 'stateChange', 'contentChange'])

const collabUsers = ref<Array<{ clientId: number; name: string; color: string }>>([])

const COLORS = ['#F44336','#E91E63','#9C27B0','#673AB7','#3F51B5','#2196F3','#03A9F4','#00BCD4','#009688','#4CAF50','#FF9800','#FF5722']
const myColor = COLORS[Math.floor(Math.random() * COLORS.length)]

// 获取 Yjs 实例 —— 纯 JS，不在任何 ref 中
const entry = getYjsEntry(props.docKey, props.initialContent)
const { ydoc, provider } = entry

// 标准架构：useEditor + Collaboration v2 + CollaborationCursor v2
// 两个扩展都用 y-prosemirror，同一个绑定库
const editor = useEditor({
  extensions: [
    StarterKit,
    Highlight.configure({ multicolor: true }),
    Image,
    Placeholder.configure({ placeholder: '开始编辑...' }),
    Table.configure({ resizable: true }),
    TableRow,
    TableCell,
    TableHeader,
    TaskList,
    TaskItem.configure({ nested: true }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Collaboration.configure({
      document: ydoc,
      field: 'content',
    }),
    CollaborationCursor.configure({
      provider,
      user: { name: props.userName, color: myColor },
    }),
  ],
  editable: props.canEdit,
  onUpdate: () => {
    emit('stateChange', 'editing')
    emit('contentChange', editor.value?.getHTML() || '')
  },
  onCreate: () => {
    emit('ready')
  },
})

// 协同用户监听
provider.awareness.on('change', () => {
  const states = Array.from(provider.awareness.getStates().entries())
  collabUsers.value = states
    .filter(([id]) => id !== ydoc.clientID)
    .map(([id, s]: [number, any]) => ({
      clientId: id,
      name: s.user?.name || '匿名',
      color: s.user?.color || '#999',
    }))
})

// 设置本地用户信息
provider.awareness.setLocalStateField('user', { name: props.userName, color: myColor })

watch(() => props.canEdit, v => { if (editor.value) editor.value.setEditable(v) })

onBeforeUnmount(() => {
  editor.value?.destroy()
  releaseYjsEntry(props.docKey)
})

function insertTable() { editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() }

async function addImage() {
  try {
    const { value } = await ElMessageBox.prompt('请输入图片 URL', '插入图片', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' })
    if (value) editor.value?.chain().focus().setImage({ src: value }).run()
  } catch {}
}

async function setLink() {
  if (editor.value?.isActive('link')) { editor.value.chain().focus().unsetLink().run(); return }
  try {
    const { value } = await ElMessageBox.prompt('请输入链接 URL', '插入链接', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' })
    if (value) editor.value?.chain().focus().setLink({ href: value }).run()
  } catch {}
}

function getContent(): string { return editor.value?.getHTML() || '' }
defineExpose({ getContent })
</script>

<style scoped>
.markdown-editor-wrapper { display: flex; flex-direction: column; height: 100%; background: #fff; }
.md-toolbar { display: flex; align-items: center; gap: 2px; padding: 6px 12px; border-bottom: 1px solid #e4e7ed; background: #fafafa; flex-shrink: 0; flex-wrap: wrap; }
.sep { width: 1px; height: 20px; background: #dcdfe6; margin: 0 4px; }
.tb { padding: 4px 8px; border: none; background: transparent; border-radius: 4px; cursor: pointer; font-size: 12px; color: #606266; transition: all 0.15s; white-space: nowrap; }
.tb:hover { background: #ecf5ff; color: #409eff; }
.tb.on { background: #ecf5ff; color: #409eff; font-weight: 600; }
.tb:disabled { opacity: 0.4; cursor: not-allowed; }
.md-editor-body { flex: 1; overflow-y: auto; position: relative; }
.md-editor-content { max-width: 860px; margin: 0 auto; padding: 24px 40px; min-height: 100%; }
.md-editor-content :deep(.tiptap) { outline: none; min-height: 400px; font-size: 15px; line-height: 1.75; color: #303133; }
.md-editor-content :deep(.tiptap p) { margin: 0.5em 0; }
.md-editor-content :deep(.tiptap h1) { font-size: 28px; font-weight: 700; margin: 1em 0 0.5em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
.md-editor-content :deep(.tiptap h2) { font-size: 22px; font-weight: 700; margin: 0.8em 0 0.4em; }
.md-editor-content :deep(.tiptap h3) { font-size: 18px; font-weight: 600; margin: 0.6em 0 0.3em; }
.md-editor-content :deep(.tiptap ul), .md-editor-content :deep(.tiptap ol) { padding-left: 1.5em; margin: 0.5em 0; }
.md-editor-content :deep(.tiptap blockquote) { border-left: 3px solid #409eff; padding: 8px 12px; margin: 0.5em 0; color: #909399; background: #f5f7fa; }
.md-editor-content :deep(.tiptap pre) { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px 16px; margin: 0.5em 0; overflow-x: auto; font-family: Consolas, monospace; font-size: 13px; }
.md-editor-content :deep(.tiptap code) { background: #f0f0f0; padding: 2px 5px; border-radius: 3px; font-size: 0.9em; font-family: Consolas, monospace; }
.md-editor-content :deep(.tiptap pre code) { background: transparent; padding: 0; }
.md-editor-content :deep(.tiptap table) { border-collapse: collapse; margin: 0.5em 0; width: 100%; }
.md-editor-content :deep(.tiptap th), .md-editor-content :deep(.tiptap td) { border: 1px solid #dcdfe6; padding: 8px 12px; text-align: left; }
.md-editor-content :deep(.tiptap th) { background: #f5f7fa; font-weight: 600; }
.md-editor-content :deep(.tiptap hr) { border: none; border-top: 2px solid #eee; margin: 1em 0; }
.md-editor-content :deep(.tiptap img) { max-width: 100%; border-radius: 4px; }
.md-editor-content :deep(.tiptap a) { color: #409eff; text-decoration: none; }
.md-editor-content :deep(.tiptap mark) { background: #fef08a; padding: 1px 2px; border-radius: 2px; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"]) { list-style: none; padding-left: 0; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"] li) { display: flex; align-items: flex-start; gap: 8px; }
.md-editor-content :deep(.tiptap p.is-editor-empty:first-child::before) { color: #adb5bd; content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
.md-editor-content :deep(.collaboration-cursor__caret) { border-left: 2px solid; margin-left: -1px; margin-right: -1px; pointer-events: none; position: relative; }
.md-editor-content :deep(.collaboration-cursor__label) { border-radius: 3px 3px 3px 0; color: #fff; font-size: 11px; font-weight: 600; left: -1px; padding: 2px 6px; position: absolute; top: -1.6em; user-select: none; white-space: nowrap; }
.collab-bar { position: absolute; top: 8px; right: 12px; display: flex; gap: 6px; z-index: 10; }
.collab-user { display: flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 12px; background: #fff; border: 2px solid; font-size: 11px; color: #606266; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
.collab-dot { width: 6px; height: 6px; border-radius: 50%; }
</style>
