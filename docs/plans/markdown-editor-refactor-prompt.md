# MarkdownEditor.vue 功能重构提示词

> 目标：基于 Tiptap + Yjs 重新开发 MarkdownEditor.vue，包含以下 50 个功能。
> 每个功能的实现以 7 月 6 日前最后一次提交为准。
> 当前文件 1905 行，目标约 4000+ 行。

---

## 一、编辑器基础（9 个功能）

### 1. Tiptap 编辑器核心
- 使用 `new Editor()` 在 `onMounted` 中初始化（不用 `useEditor()`，避免 Yjs 类型冲突）
- 导入 Tiptap 扩展：`StarterKit`、`Highlight`、`Image`、`Placeholder`、`Table/TableRow/TableCell/TableHeader`、`TaskList/TaskItem`、`TextAlign`、`Subscript`、`Superscript`、`Typography`、`CharacterCount`、`CodeBlockLowlight`
- `StarterKit` 配置 `{ link: { openOnClick: false }, codeBlock: false }`
- `CodeBlockLowlight` 配置 `{ lowlight }`，`lowlight` 用 `createLowlight(common)`
- 使用 `shallowRef` 存储 `editor`

### 2. Yjs 全局注册表
```ts
interface YjsEntry { ydoc: Y.Doc; provider: WebsocketProvider; refCount: number }
const registry = new Map<string, YjsEntry>()
function getYjsEntry(docKey: string): YjsEntry { /* 复用/创建，refCount 管理生命周期 */ }
function releaseYjsEntry(docKey: string) { /* refCount 归零时 destroy ydoc + provider */ }
```
- key 格式：`md-${docKey}`
- WebSocket 协议根据当前页面协议自动选择 `wss:` / `ws:`
- 路径：`${p}//${window.location.host}/ws/yjs/`

### 3. Yjs 协同扩展
```ts
Collaboration.configure({ document: ydoc, field: 'content' }),
CollaborationCursor.configure({ provider, user: { name: myName, color: myColor } }),
```
- 用户颜色：`COLORS` 数组随机选取
- Awareness 监听：过滤掉自己，提取他人光标信息到 `collabUsers`

### 4. 初始内容写入
```ts
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
}
```

### 5. 自动保存（防抖）
```ts
let saveTimer: ReturnType<typeof setTimeout> | null = null
function scheduleSave(editor: Editor) {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    const md = turndownService.turndown(editor.getHTML())
    emit('contentChange', md)  // 保存为 markdown 格式
  }, 1500)
}
```

### 6. Markdown ↔ HTML 转换
```ts
import { marked } from 'marked'
import TurndownService from 'turndown'
const turndownService = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced' })
const lowlight = createLowlight(common)
```
- 导入 .md 文件：`marked.parse(initialContent)` → `editor.commands.setContent(html)`
- 保存：`turndownService.turndown(editor.getHTML())` → emit markdown

### 7. 工具栏
- 块类型下拉：正文/标题1/标题2/标题3/代码块
- 文本格式：粗体(B)、斜体(I)、下划线(U)、删除线(S)、行内代码(< >)、上标(X²)、下标(X₂)
- 高亮颜色选择器：6 色（黄/绿/蓝/粉/橙/紫）+ 清除按钮
- 列表：无序/有序/任务列表
- 对齐方式下拉：左/中/右
- 插入：引用/代码块/表格(⊞)/图片/链接/分割线
- 格式：清除格式(🧹)
- 历史：撤销/重做
- 工具栏右侧：协同用户头像、大纲导航(☰)、/命令(/)、标题预览(👁)、Word导入(📥)、AI助手(✨ AI)

### 8. 选区变化处理
```ts
const onSelectionChange = () => {
  // 200ms 防抖检测选区
  // 计算 bubble 位置：fixed 定位，clamp 到 viewport
  // 更新 showBubble / bubbleStyle
}
document.addEventListener('selectionchange', onSelectionChange)
```

### 9. 属性监听与清理
```ts
watch(() => props.canEdit, v => { if (editor.value) editor.value.setEditable(v) })
onBeforeUnmount(() => { editor.value?.destroy(); releaseYjsEntry(props.docKey) })
```

---

## 二、AI 功能（12 个功能）

### 10. AI 侧边面板布局
```html
<transition name="slide">
  <div v-if="showAi" class="ai-panel">
    <el-tabs v-model="aiTab">
      <el-tab-pane label="问答" name="chat">
        <div class="ai-chat">
          <!-- 消息列表 -->
          <div class="ai-msgs" ref="aiMsgRef">
            <!-- user/assistant 消息，assistant 含 thinking + text 两部分 -->
          </div>
          <!-- 8 个快捷卡片 grid 布局 -->
          <div class="ai-quick-cards">
            <!-- 总结摘要/润色文字/解释说明/检查语法/内容扩写/内容缩写/续写内容/翻译英文 -->
          </div>
          <!-- 输入框 + 发送/停止按钮 -->
          <div class="ai-input-row">
            <el-input v-model="aiQuestion" @keydown.enter.ctrl.prevent="doSendAiChat" />
            <button v-if="!aiLoading" class="ai-send-btn" @click="doSendAiChat">↑</button>
            <button v-else class="ai-stop-btn" @click="stopAiChat">■</button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="摘要" name="summary">
        <!-- 生成摘要按钮 + 结果 -->
      </el-tab-pane>
      <el-tab-pane label="翻译" name="translate">
        <!-- 语言选择 + 文本输入 + 翻译按钮 + 结果 -->
      </el-tab-pane>
    </el-tabs>
  </div>
</transition>
```

### 11. useAiChat composable
```ts
// 使用 useAiChat 封装 SSE 流式消息管理
const {
  messages: aiMessages,
  messageVersion: aiMessageVersion,
  status: aiStatus,
  sendUserMessage: sendAiUserMessage,
  stop: stopAiChat,
  clear: clearAiChat,
} = useAiChat({ docId: docIdRef, endpoint: 'chat-stream' })
```
- `aiMessages`：包含 `role` / `parts[]`（type: 'reasoning' | 'text'，带 state: 'streaming' | 'done'）
- `aiLoading`：computed `{ aiStatus.value === 'streaming' || aiStatus.value === 'submitted' }`
- `aiThinkingExpanded`：控制思考气泡展开/折叠

### 12. 流式消息渲染
```html
<template v-for="(m, idx) in aiMessages" :key="m.id">
  <div v-if="m.role === 'user'" class="ai-msg user">
    <div class="bubble user-bubble">
      <span v-for="(p, pi) in m.parts" :key="pi">{{ p.text }}</span>
    </div>
  </div>
  <div v-else class="ai-msg assistant">
    <div class="bubble ai-bubble">
      <template v-for="(p, pi) in m.parts" :key="pi">
        <div v-if="p.type === 'reasoning' && p.text" class="ai-thinking-area">
          <button class="ai-thinking-hdr" @click="aiThinkingExpanded = !aiThinkingExpanded">
            <span>{{ aiThinkingExpanded ? '▾' : '▸' }}</span>
            <span>思考过程</span>
            <span v-if="p.state === 'streaming'" class="ai-thinking-dot">●●●</span>
          </button>
          <div v-if="aiThinkingExpanded" class="ai-thinking-content">{{ p.text }}</div>
        </div>
        <div v-else-if="p.type === 'text'" class="ai-text-content">
          {{ p.text }}<span v-if="p.state === 'streaming'" class="ai-streaming-cursor">▍</span>
        </div>
      </template>
    </div>
  </div>
</template>
```
- 流式输出时通过 `messageVersion` watcher 触发 `scrollAiToBottom()`

### 13. AI 问答发送
```ts
async function doSendAiChat() {
  const q = aiQuestion.value.trim()
  if (!q) return
  aiQuestion.value = ''
  const docContent = editor.value ? editor.value.state.doc.textBetween(0, editor.value.state.doc.content.size) : ''
  await sendAiUserMessage(q, undefined, docContent.slice(0, 6000))
  await nextTick()
  scrollAiToBottom()
}
function quickAiAsk(prompt: string) {
  if (aiLoading.value) return
  aiQuestion.value = prompt
  doSendAiChat()
}
```

### 14. AI 摘要/翻译
```ts
async function generateSummary() {
  summaryLoading.value = true
  try {
    const docContent = editor.value?.state.doc.textBetween(0, editor.value.state.doc.content.size) || ''
    const res = await documentAiApi.chat(props.docId, {
      question: `请直接生成以下文档的摘要，不加任何前缀：\n${docContent.slice(0, 6000)}`,
    })
    summaryText.value = res.content
  } catch { ElMessage.error('失败') } finally { summaryLoading.value = false }
}
async function doTranslate() {
  trLoading.value = true
  try {
    const text = trText.value || (editor.value?.state.doc.textBetween(0, editor.value.state.doc.content.size) || '')
    const res = await documentAiApi.translate(props.docId, { text, targetLang: trLang.value })
    trResult.value = res.content
  } catch { ElMessage.error('失败') } finally { trLoading.value = false }
}
```

### 15. AI 浮动菜单
```html
<transition name="fade">
  <div v-if="showBubble" class="bubble-bar" :style="bubbleStyle">
    <button @click="editor!.chain().focus().toggleBold().run()"><b>B</b></button>
    <button @click="editor!.chain().focus().toggleItalic().run()"><i>I</i></button>
    <button @click="editor!.chain().focus().toggleCode().run()"><code>&lt;&gt;</code></button>
    <button @click="setLink">🔗</button>
    <el-dropdown trigger="click" @command="handleAiAction">
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
</transition>
```

### 16. AI 浮动菜单操作
```ts
async function handleAiAction(cmd: string) {
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
    // 续写：直接插入
    if (cmd === 'continue') { editor.value.chain().focus().insertContent(result).run(); return }
    // 解释/摘要：弹窗展示
    if (cmd === 'explain' || cmd === 'summarize') {
      ElMessageBox.alert(result, cmd === 'explain' ? '解释' : '摘要', { confirmButtonText: '关闭' }); return
    }
    // 其他改写类：Diff 预览模式（见功能 18）
    showRewriteDiff(from, to, selectedText, result)
  } catch (e: any) { ElMessage.error('AI 操作失败：' + (e.response?.data?.message || e.message)) }
  finally { aiProcessing.value = false }
}
```

### 17. AI 改写弹窗（可拖拽三栏）
```ts
interface RewriteModalState {
  visible: boolean
  state: 'idle' | 'thinking' | 'streaming' | 'done' | 'error'
  cmd: string; cmdLabel: string
  selFrom: number; selTo: number
  originalText: string; thinkingText: string; newText: string
  thinkingExpanded: boolean
  abortController: AbortController | null
  injectedFrom: number; injectedTo: number
  errorMsg: string; regenCount: number
  position: { left: string; top: string }
  dragging: boolean; dragStartX: number; dragStartY: number; dragBaseLeft: number; dragBaseTop: number
}
function makeEmptyRewriteModal(): RewriteModalState { /* 初始值 */ }
const rewriteModal = ref(makeEmptyRewriteModal())
```
- 弹窗模板：顶部拖拽手柄 + 标题 + 状态标签（流式中/思考中/完成/出错）
- 原文区：`originalText` 只读展示
- 流式过渡线：科技感箭头动画
- 思考过程区：可折叠
- 新文区：`newText` 展示，流式中显示闪烁光标
- 底部：拒绝/重新生成/采用按钮
- 拖拽逻辑：`mousedown` 在 handle 上，`mousemove/mouseup` 全局监听

### 18. AI 改写 Diff 预览（采用流程）
```ts
function showRewriteDiff(from: number, to: number, original: string, result: string) {
  // 1. 原文加 strike mark（视觉"被替换"）
  editor.value.chain().focus().setTextSelection({ from, to }).setMark('strike').run()
  // 2. 在原文末尾插入 hardBreak + 新文（淡黄高亮）
  editor.value.chain().focus().insertContentAt(to, [
    { type: 'hardBreak' },
    result,
  ]).run()
  // 3. 给新文加淡黄高亮 mark
  // 4. 弹窗让用户选择：采用 → 删原文 + 保留新文 + 加高亮；拒绝 → 删新文 + 取消 strike
}
```
- 1.2s 后如果用户没操作，高亮 mark 自动 fade out
- 弹窗：ElMessageBox.confirm 展示结果，confirm → 采用，cancel → 拒绝

### 19. AI 新文淡黄高亮（渐隐动画）
```ts
const aiInsertHighlights = ref<Array<{ from: number; to: number }>>([])
function setAiInsertHighlight(from: number, to: number) {
  if (!editor.value || from >= to) return
  const highlightType = editor.value.state.schema.marks?.highlight
  if (!highlightType) return
  let tr = editor.value.state.tr
  tr = tr.addMark(from, to, highlightType.create({ color: '#fef3c7' }))
  editor.value.view.dispatch(tr)
  aiInsertHighlights.value.push({ from, to })
  // 1.2s 后移除高亮
  setTimeout(() => {
    const tr2 = editor.value.state.tr
    tr2.removeMark(from, to, highlightType)
    editor.value.view.dispatch(tr2)
  }, 1200)
}
```

### 20. AI Slash 命令
```ts
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
```

### 21. AI 消息自动滚动
```ts
watch(() => aiMessageVersion.value, () => {
  if (!aiLoading.value) return
  nextTick(() => scrollAiToBottom())
})
watch(() => aiStatus.value, () => {
  nextTick(() => scrollAiToBottom())
})
```

### 22. AI 输入区防重复提交
```ts
async function doSendAiChat() {
  if (aiLoading.value) return  // 流式中禁止重复发送
  // ...
}
```

---

## 三、大纲导航（5 个功能）

### 23. 大纲数据提取
```ts
interface OutlineItem { level: number; text: string; pos: number }
const outlineItems = ref<OutlineItem[]>([])
const activeOutlineIndex = ref(-1)
function updateOutline() {
  if (!editor.value) { outlineItems.value = []; return }
  const items: OutlineItem[] = []
  editor.value.state.doc.descendants((node: any, pos: number) => {
    if (node.type.name === 'heading') {
      const text = node.textContent || ''
      if (text.trim()) items.push({ level: (node.attrs.level as number) || 1, text: text.slice(0, 60), pos })
    }
  })
  outlineItems.value = items
}
```

### 24. 大纲侧栏 UI
```html
<transition name="slide-left">
  <div v-if="showOutline" class="outline-panel">
    <div class="outline-hdr">
      <span>☰ 文档大纲</span>
      <button class="outline-close" @click="showOutline=false">✕</button>
    </div>
    <div class="outline-list" v-if="outlineItems.length">
      <div v-for="(item, idx) in outlineItems" :key="idx"
           class="outline-item"
           :class="['level-' + item.level, {active: idx === activeOutlineIndex}]"
           @click="scrollToHeading(item.pos, idx)">
        <span class="outline-marker">H{{ item.level }}</span>
        <span class="outline-text">{{ item.text }}</span>
      </div>
    </div>
    <el-empty v-else :image-size="60" description="文档暂无标题" />
  </div>
</transition>
```
- 6 级缩进：level-1 padding 16px, level-2 32px, level-3 48px...

### 25. 大纲点击定位 + 闪烁
```ts
function scrollToHeading(pos: number, idx: number) {
  if (!editor.value) return
  activeOutlineIndex.value = idx
  const ed = editor.value
  ed.commands.focus(pos)
  const dom = ed.view.domAtPos(pos)
  if (dom.node instanceof HTMLElement) dom.node.scrollIntoView({ behavior: 'smooth', block: 'start' })
  flashOutlineHeading(pos)
}
function flashOutlineHeading(pos: number) {
  // 找到该 heading 到下一个同级别 heading 之间的所有 block
  // 给它们加 class 'outline-flash'，1.5s 后移除
  // outline-flash 用 CSS animation: outline-flash-anim 1.5s ease-out
}
```
- 闪烁动画：淡蓝背景从 35% → 0% 渐变

### 26. 大纲展开/收起
- 点击工具栏 ☰ 按钮切换 `showOutline`
- 展开时调用 `updateOutline()`

### 27. 大纲样式
```css
.outline-panel { position: fixed; left: 0; top: 50px; bottom: 0; width: 280px; background: #fff; border-right: 1px solid #e1e4e8; box-shadow: 4px 0 12px rgba(0,0,0,0.06); }
.outline-item:hover { background: rgba(99,102,241,0.06); border-left-color: rgba(99,102,241,0.3); }
.outline-item.active { background: rgba(99,102,241,0.1); border-left-color: #6366f1; color: #4338ca; }
```

---

## 四、/ 快捷命令（3 个功能）

### 28. / 触发检测
```ts
const slashTriggerPos = ref(-1)
const showSlashMenu = ref(false)
const slashMenuStyle = ref<Record<string, string>>({ top: '60px', left: '50%' })

function detectSlashTrigger(ed: any) {
  if (!ed) return
  const { selection } = ed.state
  if (!selection.empty) { showSlashMenu.value = false; slashTriggerPos.value = -1; return }
  const pos = selection.from
  if (pos < 1) { showSlashMenu.value = false; slashTriggerPos.value = -1; return }
  const prevChar = ed.state.doc.textBetween(pos - 1, pos)
  if (prevChar !== '/') { showSlashMenu.value = false; slashTriggerPos.value = -1; return }
  const before = ed.state.doc.textBetween(Math.max(0, pos - 30), pos - 1)
  const isAtStart = before.trim() === ''
  if (!isAtStart) { showSlashMenu.value = false; slashTriggerPos.value = -1; return }
  slashTriggerPos.value = pos - 1
  showSlashMenu.value = true
  const coords = ed.view.coordsAtPos(pos)
  slashMenuStyle.value = { position: 'fixed', top: (coords.bottom + 6) + 'px', left: coords.left + 'px', transform: 'translateX(0)', zIndex: '300' }
}
```
- 在 `ed.on('update', detectSlashTrigger)` 中调用

### 29. / 菜单 UI
```html
<transition name="fade">
  <div v-if="showSlashMenu" class="slash-menu" :style="slashMenuStyle" @mousedown.stop>
    <div class="slash-menu-hdr">⚡ 快捷命令</div>
    <div class="slash-menu-list">
      <div class="slash-menu-item" @click="runSlashCommand('h1')"><span class="si-icon">H₁</span><div class="si-info"><div class="si-name">标题 1</div><div class="si-desc">大标题</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('h2')"><span class="si-icon">H₂</span><div class="si-info"><div class="si-name">标题 2</div><div class="si-desc">中标题</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('h3')"><span class="si-icon">H₃</span><div class="si-info"><div class="si-name">标题 3</div><div class="si-desc">小标题</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('quote')"><span class="si-icon">❝</span><div class="si-info"><div class="si-name">引用</div><div class="si-desc">引用块</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('code')"><span class="si-icon">⌨</span><div class="si-info"><div class="si-name">代码块</div><div class="si-desc">行内/块代码</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('ul')"><span class="si-icon">•</span><div class="si-info"><div class="si-name">无序列表</div><div class="si-desc">项目符号列表</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('ol')"><span class="si-icon">1.</span><div class="si-info"><div class="si-name">有序列表</div><div class="si-desc">数字列表</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('task')"><span class="si-icon">☑</span><div class="si-info"><div class="si-name">任务列表</div><div class="si-desc">待办事项</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('table')"><span class="si-icon">⊞</span><div class="si-info"><div class="si-name">插入表格</div><div class="si-desc">3×3 表格</div></div></div>
      <div class="slash-menu-item" @click="runSlashCommand('ai')"><span class="si-icon">✨</span><div class="si-info"><div class="si-name">AI 生成</div><div class="si-desc">根据描述生成内容</div></div></div>
    </div>
  </div>
</transition>
```

### 30. / 命令执行 + Escape 关闭
```ts
function runSlashCommand(cmd: string) {
  if (!editor.value) { showSlashMenu.value = false; return }
  const ed = editor.value
  // 删除触发斜杠
  if (slashTriggerPos.value !== -1) {
    try { const tr = ed.state.tr.delete(slashTriggerPos.value, slashTriggerPos.value + 1); ed.view.dispatch(tr) } catch {}
  }
  slashTriggerPos.value = -1
  showSlashMenu.value = false

  switch (cmd) {
    case 'h1': ed.chain().focus().toggleHeading({ level: 1 }).run(); break
    case 'h2': ed.chain().focus().toggleHeading({ level: 2 }).run(); break
    case 'h3': ed.chain().focus().toggleHeading({ level: 3 }).run(); break
    case 'quote': ed.chain().focus().toggleBlockquote().run(); break
    case 'code': ed.chain().focus().toggleCodeBlock().run(); break
    case 'ul': ed.chain().focus().toggleBulletList().run(); break
    case 'ol': ed.chain().focus().toggleOrderedList().run(); break
    case 'task': ed.chain().focus().toggleTaskList().run(); break
    case 'table': ed.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(); break
    case 'ai': aiSlashCommand(); break
  }
}
function onEditorKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && showSlashMenu.value) { showSlashMenu.value = false; slashTriggerPos.value = -1; e.preventDefault() }
}
```

---

## 五、表格功能（7 个功能）

### 31. 插入表格
```ts
function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}
```

### 32. 豆包风格表格 UI — injectTableIcons
```ts
let tableMenuEl: HTMLElement | null = null
function injectTableIcons(rootEl: HTMLElement) {
  if (!editor.value || !props.canEdit) return
  // 清理旧浮层
  rootEl.querySelectorAll('.table-float-layer, .table-drag-handle, .table-col-header, .table-row-header, .table-insert-row, .table-insert-col').forEach(el => el.remove())

  // 合并重复 wrapper
  const wrappers = Array.from(rootEl.querySelectorAll<HTMLElement>('.tableWrapper'))
  const seen = new Set<string>()
  wrappers.forEach(w => {
    const rows = w.querySelectorAll('tr')
    if (rows.length === 0) { w.remove(); return }
    const key = w.outerHTML.length + '|' + (rows[0]?.textContent?.slice(0, 20) || '')
    if (seen.has(key)) w.remove()
    else seen.add(key)
  })

  Array.from(rootEl.querySelectorAll<HTMLElement>('.tableWrapper')).forEach((wrap, idx) => {
    const wrapId = '__tb_v10_' + idx + '_' + Date.now()
    if ((wrap as any).__tableIconsInstalled === wrapId) return
    ;(wrap as any).__tableIconsInstalled = wrapId

    // 创建 .table-float-layer（绝对定位，pointer-events: none，子元素 auto）
    const floatLayer = document.createElement('div')
    floatLayer.className = 'table-float-layer'
    floatLayer.setAttribute('contenteditable', 'false')

    // 1. 左上角拖拽手柄 .table-drag-handle
    //    内含 .tdh-icon (表格 SVG) + .tdh-drag (6 点拖拽 SVG)
    //    hover 时 opacity: 1, transform: translateY(0)
    //    点击拖拽手柄 → openTableMenu()

    // 2. 列头小条 .table-col-header（每列顶部，hover 显示，点击选中整列）
    // 3. 行头小点 .table-row-header（每行左侧，hover 显示，点击选中整行）
    // 4. 行插入器 .table-insert-row（每行下方，hover 显示 + 号按钮）
    // 5. 列插入器 .table-insert-col（每列右侧，hover 显示 + 号按钮）

    wrap.insertBefore(floatLayer, wrap.firstChild)

    // 位置更新函数（scroll/resize/mutation 时调用）
  })
}
```
- 在 `ed.on('update', () => injectTableIcons(ed.view.dom as HTMLElement))` 中调用

### 33. 表格操作菜单（8 按钮）
```ts
function openTableMenu(anchor: HTMLElement) {
  hideTableMenu()
  const menu = document.createElement('div')
  menu.className = 'table-menu'
  menu.setAttribute('contenteditable', 'false')
  const ics = (d: string) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`
  const icons = {
    rowAbove: ics('<path d="M12 5v14M5 12l7-7 7 7"/>'),
    rowBelow: ics('<path d="M12 5v14M5 12l7 7 7-7"/>'),
    colLeft:  ics('<path d="M5 12h14M12 5l-7 7 7 7"/>'),
    colRight: ics('<path d="M5 12h14M12 5l7 7-7 7"/>'),
    merge:    ics('<rect x="3" y="3" width="8" height="8" rx="1"/><rect x="13" y="3" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/>'),
    split:    ics('<rect x="3" y="3" width="18" height="8" rx="1"/><rect x="3" y="13" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/>'),
    del:      ics('<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/>'),
    delTable: ics('<path d="M3 3h18v18H3z"/>'),
  }
  const items = [
    { tip: '上方插入行', icon: icons.rowAbove, cmd: 'addRowBefore' },
    { tip: '下方插入行', icon: icons.rowBelow, cmd: 'addRowAfter' },
    { sep: true },
    { tip: '左侧插入列', icon: icons.colLeft, cmd: 'addColumnBefore' },
    { tip: '右侧插入列', icon: icons.colRight, cmd: 'addColumnAfter' },
    { sep: true },
    { tip: '合并单元格', icon: icons.merge, cmd: 'mergeCells' },
    { tip: '拆分单元格', icon: icons.split, cmd: 'splitCell' },
    { sep: true },
    { tip: '删除当前行', icon: icons.del, cmd: 'deleteRow', danger: true },
    { tip: '删除当前列', icon: icons.del, cmd: 'deleteColumn', danger: true },
    { sep: true },
    { tip: '删除整个表格', icon: icons.delTable, cmd: 'deleteTable', danger: true },
  ]
  // 创建按钮，绑定 mousedown 事件，switch(cmd) 执行对应操作
  // 点击外部关闭菜单
}
function hideTableMenu() { /* 移除 menu 元素 */ }
function runEditorCmd(fn: () => void) { try { fn() } catch (e) { console.warn('[runEditorCmd] failed', e) } }
```

### 34. 列宽拖拽
- Tiptap `Table.configure({ resizable: true })` 自带列宽拖拽
- CSS 样式：`.tableWrapper .column-resize-handle` hover 时显示深紫色（`rgba(99,102,241,0.20)`），拖动时 `rgba(79,70,229,0.85)`

### 35. 整列/整行选中
- 点击列头 → 该列所有 cell 加 `col-selected` class（浅紫背景）
- 点击行头 → 该行所有 cell 加 `row-selected` class（浅紫背景）

### 36. 表格行/列插入器
- hover 表格时，每行下方显示 + 号按钮（点击 → `addRowAfter`）
- 每列右侧显示 + 号按钮（点击 → `addColumnAfter`）

### 37. 自定义 TableView（防 ProseMirror 清理 DOM）
```ts
function makeCustomTableView() {
  return class CustomTableView {
    constructor(node: any, cellMinWidth: number, view: any, HTMLAttributes: any = {}) {
      this.node = node; this.cellMinWidth = cellMinWidth
      // 创建 .tableWrapper DOM，table + colgroup + tbody
      this.ignoreMutation = () => true  // 忽略所有 DOM mutation
    }
    update(node: any) { /* 更新 node 属性 */ }
    destroy() { this.dom.remove() }
  }
}
// 在 Table.configure 中使用：
// Table.configure({ resizable: true, tableView: makeCustomTableView() })
```

---

## 六、@提及（2 个功能）

### 38. @提及建议配置
```ts
const mentionSuggestion = {
  char: '@',
  items: async ({ query }: { query: string }) => {
    try {
      const token = sessionStorage.getItem('token')
      const url = query ? `/api/auth/users/search?keyword=${encodeURIComponent(query)}` : `/api/auth/users`
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      if (!resp.ok) return []
      const users = await resp.json()
      return users.slice(0, 8).map((u: any) => ({
        id: u.id, label: u.realName || u.username, avatar: u.avatarUrl, position: u.position || '',
      }))
    } catch { return [] }
  },
  render: () => {
    // 创建 .mention-popup DOM，支持键盘上下选择 + Enter 确认
    // 选中用户后插入 mention 节点
    // 输入 @ 后触发，退格删除 mention 需两下退格
  },
}
```

### 39. Mention 扩展配置
```ts
Mention.configure({ HTMLAttributes: { class: 'mention-node' }, suggestion: mentionSuggestion })
```
- mention 节点 CSS：`.mention-node { color: #6366f1; font-weight: 600; padding: 0 2px; border-radius: 3px; background: rgba(99,102,241,0.08); }`

---

## 七、图片处理（2 个功能）

### 40. 图片上传（文件选择）
```ts
async function addImage() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/png,image/jpeg,image/gif,image/webp'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    try {
      const compressed = await compressImage(file)
      const token = sessionStorage.getItem('token')
      const form = new FormData()
      form.append('file', compressed, file.name)
      const resp = await fetch('/api/documents/upload-image', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      if (!resp.ok) throw new Error('上传失败')
      const data = await resp.json()
      editor.value?.chain().focus().setImage({ src: data.url }).run()
    } catch (e: any) { ElMessage.error('图片上传失败: ' + (e.message || '未知错误')) }
  }
  input.click()
}
```

### 41. 图片压缩
```ts
function compressImage(file: File, maxWidth = 1920, quality = 0.8): Promise<File> {
  return new Promise((resolve) => {
    if (file.size < 500 * 1024) { resolve(file); return }  // 小于 500KB 不压缩
    const img = document.createElement('img')
    img.onload = () => {
      const canvas = document.createElement('canvas')
      let { width, height } = img
      if (width > maxWidth) { height = height * maxWidth / width; width = maxWidth }
      canvas.width = width; canvas.height = height
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, width, height)
      canvas.toBlob((blob) => { resolve(new File([blob!], file.name, { type: 'image/jpeg' })) }, 'image/jpeg', quality)
    }
    img.src = URL.createObjectURL(file)
  })
}
```

---

## 八、导出功能（5 个功能）

### 42. 导出 Markdown
```ts
function exportMarkdown() {
  try {
    const html = editor.value?.getHTML() || ''
    if (!html || html === '<p></p>') { ElMessage.warning('文档内容为空'); return }
    const md = turndownService.turndown(html)
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
    downloadBlob(blob, `${props.docTitle || 'document'}.md`)
  } catch (e) { console.error('导出 MD 失败', e); ElMessage.error('导出失败') }
}
```

### 43. 导出 PDF
```ts
async function exportPdf() {
  try {
    const token = sessionStorage.getItem('token')
    const resp = await fetch(`/api/documents/${props.docId}/export/pdf`, { headers: { Authorization: `Bearer ${token}` } })
    if (!resp.ok) throw new Error('导出失败')
    const blob = await resp.blob()
    downloadBlob(blob, `${props.docTitle || 'document'}.pdf`)
  } catch { ElMessage.error('PDF 导出失败') }
}
```

### 44. 导出 DOCX（docx.js）
```ts
import * as docx from 'docx'
async function exportDocx() {
  try {
    const html = editor.value?.getHTML() || ''
    if (!html || html === '<p></p>') { ElMessage.warning('文档内容为空'); return }
    ElMessage.info('正在生成 Word 文档...')
    const md = turndownService.turndown(html)
    const lines = md.split('\n')
    const children: docx.Paragraph[] = []
    for (const line of lines) {
      if (!line.trim()) { children.push(new docx.Paragraph({ text: '' })); continue }
      if (line.startsWith('# ')) {
        children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: line.substring(2), bold: true, size: 48 })] }))
      } else if (line.startsWith('## ')) {
        children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: line.substring(3), bold: true, size: 36 })] }))
      } else if (line.startsWith('### ')) {
        children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: line.substring(4), bold: true, size: 28 })] }))
      } else if (line.startsWith('- ') || line.startsWith('* ')) {
        children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: line.substring(2) })], bullet: { level: 0 } }))
      } else {
        children.push(new docx.Paragraph({ children: [new docx.TextRun({ text: line })] }))
      }
    }
    const doc = new docx.Document({ sections: [{ children }] })
    const blob = await docx.Packer.toBlob(doc)
    downloadBlob(blob, `${props.docTitle || 'document'}.docx`)
    ElMessage.success('Word 文档已导出')
  } catch (e) { console.error('导出 DOCX 失败', e); ElMessage.error('Word 导出失败') }
}
function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
```

### 45. 导出 HTML
```ts
function exportHtml() {
  const html = editor.value?.getHTML() || ''
  if (!html || html === '<p></p>') { ElMessage.warning('文档内容为空'); return }
  const fullHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${props.docTitle || 'document'}</title></head><body>${html}</body></html>`
  downloadBlob(new Blob([fullHtml], { type: 'text/html;charset=utf-8' }), `${props.docTitle || 'document'}.html`)
}
```

### 46. 导出纯文本
```ts
function exportTxt() {
  const text = editor.value?.state.doc.textBetween(0, editor.value.state.doc.content.size) || ''
  if (!text.trim()) { ElMessage.warning('文档内容为空'); return }
  downloadBlob(new Blob([text], { type: 'text/plain;charset=utf-8' }), `${props.docTitle || 'document'}.txt`)
}
```

---

## 九、Word 导入（1 个功能）

### 47. Word 文档导入
```ts
const wordInputRef = ref<HTMLInputElement | null>(null)
function triggerImportWord() {
  if (!wordInputRef.value) return
  wordInputRef.value.value = ''
  wordInputRef.value.click()
}
async function onWordFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!editor.value) { ElMessage.warning('编辑器未就绪'); return }
  try {
    ElMessage.info('正在解析 Word 文档...')
    const mammoth = (await import('mammoth')).default
    const result = await mammoth.convertToHtml({ arrayBuffer: await file.arrayBuffer() })
    const safeHtml = sanitizeAiMarkdown(result.value)
    try {
      const action = await ElMessageBox.confirm(
        'Word 文档已解析完成。是否替换当前编辑器内容？',
        '导入 Word',
        { confirmButtonText: '替换', cancelButtonText: '追加', distinguishCancelAndClose: true, closeOnClickModal: false }
      ).catch(() => 'cancel') as string
      if (action === 'replace') { editor.value.chain().focus().setContent(safeHtml).run() }
      else if (action === 'cancel') { ElMessage.info('已取消导入'); return }
      else { editor.value.chain().focus().insertContent(safeHtml).run() }
      ElMessage.success('Word 导入成功')
    } catch {}
  } catch (err: any) { console.error(err); ElMessage.error('Word 导入失败：' + (err.message || String(err))) }
}
```

---

## 十、UI/UX 细节（5 个功能）

### 48. 选区浅紫背景
```css
.ProseMirror ::selection,
.ProseMirror *::selection,
.md-editor-content ::selection,
.md-editor-content *::selection {
  background: rgba(167, 139, 250, 0.30) !important;
  color: inherit !important;
}
```

### 49. AI 改写高亮 CSS
```css
/* 淡黄高亮渐隐 */
.ProseMirror mark[data-color="#fef3c7"] {
  background: #fef3c7 !important;
  color: #78350f !important;
  padding: 1px 2px;
  border-radius: 2px;
  transition: background-color 1.2s ease-out, color 1.2s ease-out;
}
/* 删除线（diff 预览） */
.ProseMirror s, .ProseMirror strike {
  text-decoration: line-through;
  text-decoration-color: rgba(239, 68, 68, 0.85);
  text-decoration-thickness: 2px;
  color: #6b7280;
  background: rgba(254, 226, 226, 0.3);
}
```

### 50. 标题预览弹窗
```html
<transition name="fade">
  <div v-if="showPreviewModal" class="preview-modal" @mousedown.self="showPreviewModal=false">
    <div class="preview-modal-content">
      <div class="preview-modal-hdr"><span>👁 标题预览</span><button @click="showPreviewModal=false">✕</button></div>
      <div class="preview-modal-body">
        <h2>Markdown 标题预览</h2>
        <p style="color:#6b7280">将以下 Markdown 渲染为不同级别的标题样式：</p>
        <pre class="preview-code">{{ previewMarkdown }}</pre>
        <div class="preview-rendered">
          <h1>这是一级标题 H1</h1>
          <h2>这是二级标题 H2</h2>
          <h3>这是三级标题 H3</h3>
          <h4>这是四级标题 H4</h4>
          <h5>这是五级标题 H5</h5>
          <h6>这是六级标题 H6</h6>
          <p><strong>正文：</strong>文档的主要文字内容</p>
          <p><em>斜体：</em>强调内容</p>
          <p><code>行内代码</code> 和 <strong>加粗</strong></p>
          <ul><li>列表项 1</li><li>列表项 2</li></ul>
        </div>
      </div>
    </div>
  </div>
</transition>
```

---

## 关键依赖

```json
{
  "@tiptap/core": "^3.26",
  "@tiptap/vue-3": "^3.26",
  "@tiptap/starter-kit": "^3.26",
  "@tiptap/extension-highlight": "^3.26",
  "@tiptap/extension-image": "^3.26",
  "@tiptap/extension-placeholder": "^3.26",
  "@tiptap/extension-table": "^3.26",
  "@tiptap/extension-table-row": "^3.26",
  "@tiptap/extension-table-cell": "^3.26",
  "@tiptap/extension-table-header": "^3.26",
  "@tiptap/extension-task-list": "^3.26",
  "@tiptap/extension-task-item": "^3.26",
  "@tiptap/extension-text-align": "^3.26",
  "@tiptap/extension-subscript": "^3.26",
  "@tiptap/extension-superscript": "^3.26",
  "@tiptap/extension-typography": "^3.26",
  "@tiptap/extension-character-count": "^3.26",
  "@tiptap/extension-collaboration": "^3.26",
  "@tiptap/extension-collaboration-cursor": "^3.26",
  "@tiptap/extension-code-block-lowlight": "^3.26",
  "@tiptap/extension-mention": "^3.26",
  "@tiptap/suggestion": "^3.26",
  "@tiptap/pm": "^3.26",
  "yjs": "^13.6",
  "y-websocket": "^2.0",
  "marked": "^14.0",
  "turndown": "^7.2",
  "lowlight": "^3.1",
  "docx": "^9.0",
  "mammoth": "^1.8"
}
```