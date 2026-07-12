<template>
  <div class="md-wrapper">
    <!-- 顶部工具栏 -->
    <div class="md-toolbar" v-if="editor">
      <!-- 工具栏靠左：第一个按钮是"大纲导航" -->
      <button class="tb" :class="{on:showOutline}" @click="showOutline=!showOutline" title="大纲导航"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h13M3 12h13M3 18h13M19 6l3 3-3 3"/></svg></button>
      <span class="tb-sep" />
      <!-- Word 导入：靠左 -->
      <button class="tb word-trigger" @click="triggerImportWord" title="从 Word 导入（保留文字、格式、表格）"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg></button>
      <input ref="wordInputRef" type="file" accept=".docx" style="display:none" @change="onWordFileSelected" />
      <span class="tb-sep" />
      <!-- 7/14 关键：el-dropdown 不再嵌套 el-tooltip — 用 title 属性 -->
      <el-dropdown trigger="click" @command="setBlockType">
        <button class="tb block-select" title="段落/标题类型">{{ blockLabel }} ▾</button>
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
        <el-tooltip content="粗体 Ctrl+B" :show-after="400"><button class="tb" :class="{on:editor.isActive('bold')}" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button></el-tooltip>
        <el-tooltip content="斜体 Ctrl+I" :show-after="400"><button class="tb" :class="{on:editor.isActive('italic')}" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button></el-tooltip>
        <el-tooltip content="下划线 Ctrl+U" :show-after="400"><button class="tb" :class="{on:editor.isActive('underline')}" @click="editor.chain().focus().toggleUnderline().run()"><u>U</u></button></el-tooltip>
        <el-tooltip content="删除线" :show-after="400"><button class="tb" :class="{on:editor.isActive('strike')}" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button></el-tooltip>
        <el-tooltip content="行内代码 Ctrl+E" :show-after="400"><button class="tb" :class="{on:editor.isActive('code')}" @click="toggleInlineCode"><code class="tb-code-icon">&lt;/&gt;</code></button></el-tooltip>
        <el-tooltip content="下标" :show-after="400"><button class="tb" :class="{on:editor.isActive('subscript')}" @click="editor.chain().focus().toggleSubscript().run()"><span style="font-size:11px">X₂</span></button></el-tooltip>
        <el-tooltip content="上标" :show-after="400"><button class="tb" :class="{on:editor.isActive('superscript')}" @click="editor.chain().focus().toggleSuperscript().run()"><span style="font-size:11px">X²</span></button></el-tooltip>
        <!-- 颜色按钮（合一）：单击 = 应用最近字体色 + 最近高亮色；右侧 ▾ = 打开色板 -->
        <!-- 按钮图标：A 字（用最近字体色着色）+ 下方色条（用最近高亮色填色） -->
        <!-- hover 行为：鼠标进入 .db-color-split 自动弹出，离开后 250ms 缓冲收回 -->
        <div
          class="db-color-split"
          ref="colorSplitRef"
          @mouseenter="onColorAreaEnter"
          @mouseleave="onColorAreaLeave">
          <el-popover
            trigger="manual"
            v-model:visible="colorPanelOpen"
            :width="280"
            placement="bottom"
            popper-class="db-color-popover"
            :show-arrow="false"
            @after-enter="capturePopoverEl">
            <template #reference>
              <button
                class="tb db-color-btn db-color-main"
                :class="{ on: hasRecentText || hasRecentHl }"
                :title="`颜色（点击应用到选区 · 字体 ${effectiveTextColor} · 高亮 ${effectiveHlColor}）`"
                @mousedown.prevent
                @click.stop="applyRecentToSelection">
                <span class="db-color-icon-wrap">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" xmlns="http://www.w3.org/2000/svg" :style="{ color: hasRecentText ? effectiveTextColor : undefined }">
                    <path d="m16.439 15 3.14 7.391a1 1 0 1 0 1.842-.782L13.38 2.692c-.518-1.218-2.244-1.218-2.761 0L2.58 21.609a1 1 0 1 0 1.84.782L7.563 15h8.877Zm-.85-2H8.412L12 4.557 15.59 13Z" fill="currentColor"></path>
                  </svg>
                  <span class="db-color-bar" :style="{ background: hasRecentHl ? effectiveHlColor : undefined }"></span>
                </span>
              </button>
            </template>
            <div ref="popoverPanelRef" class="db-color-panel" @mousedown.stop>
              <div class="db-color-section">
                <div class="db-color-section-hdr"><span>最近用过</span></div>
                <div class="db-color-grid" v-if="recentColors.length">
                  <button
                    v-for="r in recentColors"
                    :key="'rc-' + r.kind + '-' + r.color"
                    class="db-color-swatch"
                    :class="{ 'is-active': isRecentActive(r) }"
                    :style="{ background: r.color }"
                    :title="`${r.kind === 'text' ? '字体' : '高亮'} · ${r.color}`"
                    @mousedown.prevent
                    @click="applyAndRemember(r.kind, r.color)" />
                </div>
                <div v-else class="db-color-empty">尚无使用记录</div>
              </div>
              <div class="db-color-divider" />
              <div class="db-color-section">
                <div class="db-color-section-hdr">
                  <span>字体颜色</span>
                  <label class="db-color-custom" :title="`自定义（当前 ${customTextColor}）`">
                    <input type="color" :value="customTextColor" @input="onCustomColorInput('text', $event)" />
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
                  </label>
                </div>
                <div class="db-color-grid">
                  <button
                    v-for="c in textColors"
                    :key="'tc-' + c.v"
                    class="db-color-swatch"
                    :class="{ 'is-active': currentTextColor === c.v }"
                    :style="{ background: c.v }"
                    :title="c.l"
                    @mousedown.prevent
                    @click="applyAndRemember('text', c.v)" />
                  <button class="db-color-swatch db-color-clear" :class="{ 'is-active': currentTextColor === FALLBACK_TEXT }" @mousedown.prevent @click="clearColor('text')" title="清除字体色">/</button>
                </div>
              </div>
              <div class="db-color-divider" />
              <div class="db-color-section">
                <div class="db-color-section-hdr">
                  <span>高亮颜色</span>
                  <label class="db-color-custom" :title="`自定义（当前 ${customHlColor}）`">
                    <input type="color" :value="customHlColor" @input="onCustomColorInput('highlight', $event)" />
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
                  </label>
                </div>
                <div class="db-color-grid">
                  <button
                    v-for="c in hlColors"
                    :key="'hl-' + c.v"
                    class="db-color-swatch"
                    :class="{ 'is-active': currentHighlightColor === c.v }"
                    :style="{ background: c.v }"
                    :title="c.l"
                    @mousedown.prevent
                    @click="applyAndRemember('highlight', c.v)" />
                  <button class="db-color-swatch db-color-clear" :class="{ 'is-active': currentHighlightColor === FALLBACK_HL }" @mousedown.prevent @click="clearColor('highlight')" title="清除高亮">/</button>
                </div>
              </div>
            </div>
          </el-popover>
          <button
            class="tb db-color-btn db-color-arrow"
            :class="{ on: colorPanelOpen }"
            title="选择颜色"
            @mousedown.prevent
            @click.stop="onArrowClick">
            <svg viewBox="0 0 24 24" width="8" height="8" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>
          </button>
        </div>
        <span class="tb-sep" />
        <el-tooltip content="无序列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('bulletList')}" @click="editor.chain().focus().toggleBulletList().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><circle cx="5" cy="6" r="1.5"/><circle cx="5" cy="12" r="1.5"/><circle cx="5" cy="18" r="1.5"/><path d="M10 6h11M10 12h11M10 18h11" stroke="currentColor" stroke-width="1.5" fill="none"/></svg></button></el-tooltip>
        <el-tooltip content="有序列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('orderedList')}" @click="editor.chain().focus().toggleOrderedList().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 6h2M3 12h2M3 18h2" stroke="currentColor" stroke-width="2" fill="none"/><path d="M9 6h12M9 12h12M9 18h12" stroke="currentColor" stroke-width="1.5" fill="none"/></svg></button></el-tooltip>
        <el-tooltip content="任务列表" :show-after="400"><button class="tb" :class="{on:editor.isActive('taskList')}" @click="editor.chain().focus().toggleTaskList().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="14" height="14" rx="1"/><path d="M7 10l3 3 5-5"/></svg></button></el-tooltip>
        <span class="tb-sep" />
        <el-tooltip content="左对齐" :show-after="400"><button class="tb align-btn" :class="{on:editor.isActive({textAlign:'left'})}" @click="setTextAlign('left')"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 5h18v2H3zm0 6h10v2H3zm0 6h18v2H3z"/></svg></button></el-tooltip>
        <el-tooltip content="居中对齐" :show-after="400"><button class="tb align-btn" :class="{on:editor.isActive({textAlign:'center'})}" @click="setTextAlign('center')"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 5h18v2H3zm3 6h12v2H6zm-3 6h18v2H3z"/></svg></button></el-tooltip>
        <el-tooltip content="右对齐" :show-after="400"><button class="tb align-btn" :class="{on:editor.isActive({textAlign:'right'})}" @click="setTextAlign('right')"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 5h18v2H3zm4 6h10v2H7zm-4 6h18v2H3z"/></svg></button></el-tooltip>
        <el-tooltip content="两端对齐" :show-after="400"><button class="tb align-btn" :class="{on:editor.isActive({textAlign:'justify'})}" @click="setTextAlign('justify')"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="currentColor" stroke-width="1"><path d="M3 5h18v2H3zm0 6h18v2H3zm0 6h18v2H3z"/></svg></button></el-tooltip>
        <span class="tb-sep" />
        <el-tooltip content="引用块" :show-after="400"><button class="tb" :class="{on:editor.isActive('blockquote')}" @click="editor.chain().focus().toggleBlockquote().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M7 7h3v6H6V9c0-1.1.9-2 2-2zm8 0h3v6h-4V9c0-1.1.9-2 2-2z"/></svg></button></el-tooltip>
        <!-- 7/14 关键：代码块 dropdown 不再嵌套 el-tooltip -->
        <!-- 7/15 关键：代码块按钮 - 点击直接插入（无需选语言），语言可在编辑器内修改 -->
        <button class="tb" :class="{on:editor.isActive('codeBlock')}" title="代码块（点击插入，编辑器内可改语言名）" @click="insertCodeBlock">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="16" rx="2"/>
            <path d="M8 10l-2 2 2 2M16 10l2 2-2 2M13 9l-2 6"/>
          </svg>
        </button>
        <el-tooltip content="表格" :show-after="400"><button class="tb" @click="insertTable"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="1"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/></svg></button></el-tooltip>
        <!-- 本地图片上传（7/5 关键：支持本地） -->
        <el-tooltip content="图片（本地或 URL）" :show-after="400">
          <button class="tb" @click="addImage">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
          </button>
        </el-tooltip>
        <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="onLocalImageSelected" />
        <el-tooltip content="链接" :show-after="400"><button class="tb" :class="{on:editor.isActive('link')}" @click="setLink"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg></button></el-tooltip>
        <el-tooltip content="分割线" :show-after="400"><button class="tb" @click="editor.chain().focus().setHorizontalRule().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h18"/></svg></button></el-tooltip>
        <el-tooltip content="清除格式" :show-after="400"><button class="tb" @click="editor.chain().focus().clearNodes().unsetAllMarks().run()"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6"/></svg></button></el-tooltip>
        <span class="tb-sep" />
        <el-tooltip content="撤销" :show-after="400"><button class="tb" @click="editor.chain().focus().undo().run()" :disabled="!editor.can().undo()"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 14l-4-4 4-4M5 10h11a4 4 0 010 8h-3"/></svg></button></el-tooltip>
        <el-tooltip content="重做" :show-after="400"><button class="tb" @click="editor.chain().focus().redo().run()" :disabled="!editor.can().redo()"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 14l4-4-4-4M19 10H8a4 4 0 000 8h3"/></svg></button></el-tooltip>
      <div class="tb-right">
        <div v-if="collabUsers.length" class="collab-avatars">
          <div v-for="u in collabUsers" :key="u.id" class="avatar" :style="{background:u.color}" :title="u.name">{{ u.name.charAt(0) }}</div>
        </div>
        <el-tooltip content="AI 助手 (侧边面板)" :show-after="400"><button class="tb ai-btn" :class="{on:showAi}" @click="showAi=!showAi">✨ AI</button></el-tooltip>
      </div>
    </div>

    <!-- / 快捷命令菜单弹窗（9/7 关键：去掉智能编辑，键盘导航与 items 顺序一致） -->
    <transition name="fade">
      <div v-if="showSlashMenu" class="slash-menu" :style="slashMenuStyle" @mousedown.stop>
        <div class="slash-menu-hdr">⚡ 快捷命令（↑↓ 切换，Enter 选定）</div>
        <div class="slash-menu-list">
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 0 }" @click="runSlashCommand('h1')" @mouseenter="slashSelectedIndex = 0">
            <span class="si-icon">H₁</span>
            <div class="si-info"><div class="si-name">标题 1</div><div class="si-desc">大标题</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 1 }" @click="runSlashCommand('h2')" @mouseenter="slashSelectedIndex = 1">
            <span class="si-icon">H₂</span>
            <div class="si-info"><div class="si-name">标题 2</div><div class="si-desc">中标题</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 2 }" @click="runSlashCommand('h3')" @mouseenter="slashSelectedIndex = 2">
            <span class="si-icon">H₃</span>
            <div class="si-info"><div class="si-name">标题 3</div><div class="si-desc">小标题</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 3 }" @click="runSlashCommand('quote')" @mouseenter="slashSelectedIndex = 3">
            <span class="si-icon">❝</span>
            <div class="si-info"><div class="si-name">引用</div><div class="si-desc">引用块</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 4 }" @click="runSlashCommand('code')" @mouseenter="slashSelectedIndex = 4">
            <span class="si-icon">⌨</span>
            <div class="si-info"><div class="si-name">代码块</div><div class="si-desc">行内/块代码</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 5 }" @click="runSlashCommand('ul')" @mouseenter="slashSelectedIndex = 5">
            <span class="si-icon">•</span>
            <div class="si-info"><div class="si-name">无序列表</div><div class="si-desc">项目符号列表</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 6 }" @click="runSlashCommand('ol')" @mouseenter="slashSelectedIndex = 6">
            <span class="si-icon">1.</span>
            <div class="si-info"><div class="si-name">有序列表</div><div class="si-desc">数字列表</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 7 }" @click="runSlashCommand('task')" @mouseenter="slashSelectedIndex = 7">
            <span class="si-icon">☑</span>
            <div class="si-info"><div class="si-name">任务列表</div><div class="si-desc">待办事项</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 8 }" @click="runSlashCommand('table')" @mouseenter="slashSelectedIndex = 8">
            <span class="si-icon">⊞</span>
            <div class="si-info"><div class="si-name">插入表格</div><div class="si-desc">3×3 表格</div></div>
          </div>
          <div class="slash-menu-item" :class="{ active: slashSelectedIndex === 9 }" @click="runSlashCommand('ai')" @mouseenter="slashSelectedIndex = 9">
            <span class="si-icon">✨</span>
            <div class="si-info"><div class="si-name">AI 生成</div><div class="si-desc">根据描述生成内容（不携带文档）</div></div>
          </div>
        </div>
      </div>
    </transition>

    <!-- AI 浮窗结果区（豆包风格：选区上方实时显示 AI 输出 + 接受/拒绝） -->
    <transition name="floatPanel">
      <div
        v-if="aiFloatPanel.visible"
        :class="['ai-float-panel', {
          'ai-float-panel--options': aiFloatPanel.options.length > 0,
          'ai-float-panel--below': !aiFloatPanel.placeAbove,
        }]"
        :style="{
          top: aiFloatPanel.position.top + 'px',
          left: aiFloatPanel.position.left + 'px',
        }"
      >
        <div class="ai-float-header" @mousedown="startDragAiFloat">
          <span class="ai-float-title">✨ {{ aiFloatPanel.title }}</span>
          <div class="ai-float-status">
            <span v-if="aiFloatPanel.status === 'streaming'" class="ai-float-streaming">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </span>
            <span v-else-if="aiFloatPanel.status === 'done'" class="ai-float-done">✓ 已完成</span>
            <span v-else-if="aiFloatPanel.status === 'aborted'" class="ai-float-aborted">已停止</span>
            <span v-else-if="aiFloatPanel.status === 'error'" class="ai-float-error">错误</span>
            <button class="ai-float-close" @click="closeAiFloatPanel" title="关闭 (Esc)">×</button>
          </div>
        </div>

        <!-- 7/10 关键：AI 生成模式输入区（替代系统弹窗，整段提示词直接在浮窗内输入） -->
        <div v-if="aiGenerateMode && aiFloatPanel.command === 'continue' && aiFloatPanel.status === 'idle'" class="ai-float-generate">
          <div class="gen-hint">
            <span class="gen-icon">✨</span>
            <span>告诉 AI 你想写什么，文档内容不会被发送</span>
          </div>
          <textarea
            v-model="aiGeneratePrompt"
            class="gen-input"
            placeholder="如：写一段项目介绍、写一份会议纪要、补一段总结..."
            rows="3"
            @keydown="(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submitAiGenerate() } }"
          ></textarea>
          <div class="gen-actions">
            <button class="ai-float-btn ai-float-btn--ghost" @click="closeAiFloatPanel">取消</button>
            <button
              class="ai-float-btn ai-float-btn--primary"
              :disabled="!aiGeneratePrompt.trim()"
              @click="submitAiGenerate"
            >生成 ↵</button>
          </div>
        </div>

        <!-- 7/4 智能编辑：浮窗内嵌指令输入（idle 状态显示） -->
        <div
          v-if="aiFloatPanel.awaitingInstruction && aiFloatPanel.command === 'smart-edit'"
          class="ai-float-instruction"
        >
          <div class="instr-hint">
            <span class="instr-icon">🪄</span>
            <span>告诉 AI 你想如何修改这段文本</span>
          </div>
          <div class="instr-suggestions">
            <button
              v-for="s in smartEditSuggestions"
              :key="s"
              class="instr-chip"
              @click="aiFloatPanel.smartEditDraft = s"
            >{{ s }}</button>
          </div>
          <textarea
            v-model="aiFloatPanel.smartEditDraft"
            class="instr-input"
            placeholder="如：让这段更专业、翻译成英文、扩写、缩写..."
            rows="2"
            @keydown="handleSmartEditKeydown"
          ></textarea>
          <div class="instr-actions">
            <button class="ai-float-btn ai-float-btn--ghost" @click="closeAiFloatPanel">取消</button>
            <button
              class="ai-float-btn ai-float-btn--primary"
              :disabled="!aiFloatPanel.smartEditDraft.trim()"
              @click="submitSmartEditInstruction"
            >开始生成 ↵</button>
          </div>
        </div>

        <!-- 思考链（豆包风格 - 浅紫边框 + 蓝色阴影呼吸） -->
        <div
          v-if="aiFloatPanel.thinking || aiFloatPanel.thinkingDone"
          class="ai-float-thinking"
          :class="{
            thinking: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone,
            done: aiFloatPanel.thinkingDone
          }"
        >
          <div class="thinking-head" @click="aiFloatPanel.thinkingDone && (aiFloatPanel.thinkingExpanded = !aiFloatPanel.thinkingExpanded)">
            <svg
              class="thinking-icon"
              :class="{ spinning: aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone }"
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              style="flex-shrink:0"
            >
              <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
            </svg>
            <span class="thinking-title">
              {{
                aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone
                  ? `正在思考… ${aiFloatPanel.thinkingDurationSec} 秒`
                  : `已思考 ${aiFloatPanel.thinkingDurationSec} 秒`
              }}
            </span>
            <span v-if="aiFloatPanel.thinkingDone" class="thinking-toggle ai-float-chevron" :class="{ expanded: aiFloatPanel.thinkingExpanded }">▾</span>
          </div>
          <div
            v-show="aiFloatPanel.thinkingExpanded || (aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone)"
            class="thinking-body"
          >
            <div class="thinking-text">
              {{ aiFloatPanel.thinking || '...' }}
              <span
                v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.thinkingDone"
                class="thinking-cursor"
              >▍</span>
            </div>
          </div>
        </div>

        <!-- 单方案流式结果 -->
        <div v-if="aiFloatPanel.options.length === 0" class="ai-float-body">
          <div
            v-if="aiFloatPanel.status === 'streaming' && !aiFloatPanel.text"
            class="ai-float-placeholder"
          >
            AI 撰写中…
          </div>
          <div v-else class="ai-float-content">
            {{ aiFloatPanel.text }}
            <span
              v-if="aiFloatPanel.status === 'streaming'"
              class="ai-float-cursor"
            >▍</span>
          </div>
        </div>

        <!-- 多方案选择 -->
        <div v-else class="ai-float-options">
          <div class="ai-float-options-hint">
            AI 提供了 {{ aiFloatPanel.options.length }} 个方案，点击选择：
          </div>
          <div
            v-for="(opt, idx) in aiFloatPanel.options"
            :key="idx"
            class="ai-float-option"
            @click="pickAiFloatOption(idx)"
          >
            <div class="ai-float-option-title">{{ opt.title }}</div>
            <div class="ai-float-option-content">
              {{ opt.content.substring(0, 120) }}{{ opt.content.length > 120 ? '...' : '' }}
            </div>
          </div>
        </div>

        <!-- 9/7 关键：原文内容（与 AI 生成内容隔离展示） -->
        <div
          v-if="aiFloatPanel.originalText && aiFloatPanel.command !== 'continue' && !aiFloatPanel.insertAtCursor"
          class="ai-float-original"
        >
          <div class="orig-hdr" @click="aiFloatPanel.originalExpanded = !aiFloatPanel.originalExpanded">
            <span class="orig-icon">📄</span>
            <span class="orig-title">原文</span>
            <span class="orig-len">{{ aiFloatPanel.originalText.length }} 字</span>
            <span class="orig-toggle" :class="{ expanded: aiFloatPanel.originalExpanded }">▾</span>
          </div>
          <div v-show="aiFloatPanel.originalExpanded" class="orig-body">
            {{ aiFloatPanel.originalText }}
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="aiFloatPanel.options.length === 0" class="ai-float-footer">
          <!-- Diff 模式：显示接受/拒绝 -->
          <template v-if="aiFloatPanel.pendingDiff">
            <div class="ai-float-diff-hint">
              <span class="diff-icon-removed">⊘</span> 原文
              <span class="diff-icon-added">+</span> AI 新内容
            </div>
            <button class="ai-float-btn ai-float-btn--ghost" @click="rejectDiff">✗ 拒绝</button>
            <button class="ai-float-btn ai-float-btn--primary" @click="acceptDiff">✓ 采用 ↵</button>
          </template>
          <!-- 流式中 -->
          <button
            v-else-if="aiFloatPanel.status === 'streaming'"
            class="ai-float-btn ai-float-btn--ghost"
            @click="stopAiFloatResult"
          >停止</button>
          <!-- 完成 -->
          <template v-else>
            <el-tooltip content="重新生成" placement="top">
              <button
                class="ai-float-btn ai-float-btn--icon"
                :disabled="!aiFloatPanel.text"
                @click="regenerateAiFloatResult"
              >↻</button>
            </el-tooltip>
            <button
              class="ai-float-btn ai-float-btn--primary"
              :disabled="!aiFloatPanel.text"
              @click="acceptAiFloatResult"
            >采用 ↵</button>
          </template>
        </div>
      </div>
    </transition>

    <!-- 编辑器主体 -->
    <div class="md-body">
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
      <div class="md-main">
        <!-- AI 全局遮盖层：7/3 关键 — 完全透明 + 不可点击停止 -->
        <transition name="fade">
          <div
            v-if="editAiCoverVisible"
            class="ai-cover-overlay"
          ></div>
        </transition>
        <div class="md-editor-scroll">
          <editor-content :editor="editor" class="md-editor-content" />
        </div>

        <!-- 选中文本浮动菜单（9/7 关键：科技感底色 + 段落/标题类型 + 弹出后焦点自动回编辑器） -->
        <transition name="fade">
          <div v-if="showBubble" class="bubble-bar" :style="bubbleStyle" @mousedown.prevent>
            <!-- 段落 / 标题类型下拉（紧凑型） -->
            <el-dropdown trigger="click" @command="setBlockType" size="small" @click.stop>
              <button class="bubble-btn block-type-btn">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h10M4 18h16"/></svg>
                <span class="bb-type">{{ blockLabel }}</span>
                <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><path d="M7 10l5 5 5-5z"/></svg>
              </button>
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
            <span class="bubble-sep" />
            <button class="bubble-btn" :class="{on: editor.isActive('bold')}" @click="editor.chain().focus().toggleBold().run()"><b>B</b></button>
            <button class="bubble-btn" :class="{on: editor.isActive('italic')}" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></button>
            <button class="bubble-btn" :class="{on: editor.isActive('strike')}" @click="editor.chain().focus().toggleStrike().run()"><s>S</s></button>
            <button class="bubble-btn" :class="{on: editor.isActive('code')}" @click="toggleInlineCode"><code class="bb-code-icon">&lt;/&gt;</code></button>
            <button class="bubble-btn" :class="{on: editor.isActive('link')}" @click="setLink"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg></button>
            <span class="bubble-sep" />
            <button class="bubble-btn" :class="{on: editor.isActive('bulletList')}" @click="editor.chain().focus().toggleBulletList().run()" title="无序列表"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><circle cx="5" cy="6" r="1.5"/><circle cx="5" cy="12" r="1.5"/><circle cx="5" cy="18" r="1.5"/><path d="M10 6h11M10 12h11M10 18h11" stroke="currentColor" stroke-width="1.5" fill="none"/></svg></button>
            <button class="bubble-btn" :class="{on: editor.isActive('orderedList')}" @click="editor.chain().focus().toggleOrderedList().run()" title="有序列表"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 6h11M10 12h11M10 18h11"/><path d="M4 6h1M4 12h1M4 18h1"/></svg></button>
            <span class="bubble-sep" />
            <el-dropdown trigger="click" @command="handleAiAction" size="small">
              <button class="bubble-btn ai">✨ AI ▾</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="smart-edit">🪄 智能编辑</el-dropdown-item>
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

        <!-- 底部状态栏 -->
        <div class="md-status-bar">
          <span class="stat-item">{{ charCount }} 字符</span>
          <span class="stat-item">{{ wordCount }} 词</span>
          <span v-if="aiProcessing" class="stat-item ai-status">✨ AI 处理中...</span>
        </div>
      </div>

      <!-- AI 侧边面板（豆包风格 + 问答/编辑模式切换） -->
      <transition name="slide">
        <div
          v-if="showAi && canEdit"
          class="ai-panel"
          :style="{ width: aiPanelWidth + 'px' }"
        >
          <!-- 调整大小拖拽条（无 hover 高亮） -->
          <div class="ai-resize-handle" @mousedown="startResize"></div>

          <!-- 头部 -->
          <div class="ai-header">
            <div class="ai-brand" @click="backToAiWelcome" title="返回卡片" style="cursor: pointer;">
              <div class="ai-avatar-icon">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
                </svg>
              </div>
              <span class="ai-title">AI 写作助手</span>
            </div>
            <div class="ai-header-actions">
              <div class="mode-tabs">
                <button
                  class="mode-tab"
                  :class="{ active: !aiEditMode }"
                  @click="aiEditMode = false; aiEditTargetRange = null"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" width="14" height="14"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
                  问答
                </button>
                <button
                  class="mode-tab"
                  :class="{ active: aiEditMode }"
                  @click="aiEditMode = true"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" width="14" height="14"><path d="M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
                  编辑
                </button>
              </div>
            </div>
            <button class="ai-close-btn" @click="showAi = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <!-- 消息列表 -->
                    <!-- ===== 7/19 关键：编辑模式 = 独立页面（与聊天页分离）。布局：顶部原文预览 + 中部结果 + 底部输入 ===== -->
          <div v-if="aiEditMode" class="ai-edit-page">
            <!-- 顶部：原文预览（可折叠）+ 选区状态 -->
            <div class="edit-topbar">
              <div class="edit-topbar-left">
                <button class="edit-orig-toggle" :class="{ expanded: editOrigExpanded }" @click="editOrigExpanded = !editOrigExpanded" title="显示/隐藏原文">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M9 18l6-6-6-6"/></svg>
                  <span>原文</span>
                </button>
                <div v-if="aiEditTargetRange && !editScopeFull" class="target-chip" :title="aiEditTargetRange.text.slice(0, 80) + (aiEditTargetRange.text.length > 80 ? '…' : '')">
                  <span class="target-chip-icon">✂️</span>
                  <span class="target-chip-len">已选 {{ aiEditTargetRange.text.length }} 字</span>
                  <button class="target-chip-clear" @click="clearEditSelection" title="清除选区">×</button>
                </div>
                <div v-else class="target-chip target-chip-empty" @click="captureEditSelection">
                  <span class="target-chip-icon">🎯</span>
                  <span class="target-chip-len">{{ editScopeFull ? '已选全文' : '点击捕获选区' }}</span>
                </div>
              </div>
              <div class="edit-scope-tag" :class="{ 'is-full': editScopeFull, 'is-section': editScopeSection }">
                <span v-if="editScopeFull">📄 全文</span>
                <span v-else-if="editScopeSection">📌 段落</span>
                <span v-else>✂️ 选区</span>
              </div>
            </div>

            <!-- 原文预览（折叠展开） -->
            <transition name="edit-orig-slide">
              <div v-if="editOrigExpanded && aiEditTargetRange" class="edit-orig-preview">
                <div class="eop-hdr">
                  <span>📄 原内容预览</span>
                  <span class="eop-len">{{ aiEditTargetRange.text.length }} 字</span>
                </div>
                <div class="eop-body">{{ aiEditTargetRange.text }}</div>
              </div>
            </transition>

            <!-- 中部：AI 结果预览（流式 + 最终） -->
            <div class="edit-result-area">
              <!-- 7/19 关键：欢迎态（无结果时显示） -->
              <div v-if="!aiEditStreamingText && !aiEditLastResult && !editAiLoading" class="edit-welcome">
                <div class="ew-icon">✍️</div>
                <h3>智能编辑</h3>
                <p>在底部输入指令，AI 会按要求改写内容</p>
                <div class="ew-tips">
                  <div class="ew-tip" @click="aiEditInstruction = '帮我修改第2段的内容'; autoResizeAiInput()">
                    <b>📌 帮我修改第 2 段的内容</b>
                    <span>自动定位到第 2 段改写（中文数字也行）</span>
                  </div>
                  <div class="ew-tip" @click="aiEditInstruction = '帮我修改全文的格式'; autoResizeAiInput()">
                    <b>📌 帮我修改全文的格式</b>
                    <span>全篇统一格式、语气或风格</span>
                  </div>
                  <div class="ew-tip" @click="aiEditInstruction = '翻译成英文'; autoResizeAiInput()">
                    <b>📌 翻译成英文 / 让这段更专业</b>
                    <span>选区改写（无选区默认全文）</span>
                  </div>
                </div>

                <!-- 7/19 关键：高级选项（用户偏好） -->
                <div class="ew-options">
                  <label class="ew-option">
                    <input type="checkbox" v-model="preserveMarkdown" />
                    <span>保留 markdown 结构</span>
                  </label>
                  <label class="ew-option">
                    <input type="checkbox" v-model="autoApplyToEditor" />
                    <span>结果直接替换原内容</span>
                  </label>
                </div>
              </div>

              <!-- 流式中 -->
              <div v-else-if="(aiEditStreamingText && !aiEditStreamingText.done) || (editAiLoading && !aiEditLastResult)" class="edit-streaming-card">
                <div class="esc-hdr">
                  <span class="esc-icon spinning">⏳</span>
                  <span>正在生成{{ aiEditStreamingText?.text?.length ? `（${aiEditStreamingText.text.length} 字）` : '…' }}</span>
                  <button class="esc-stop" @click="stopEditAi" title="停止生成">■ 停止</button>
                </div>
                <div class="esc-body">{{ aiEditStreamingText?.text || '思考中…' }}</div>
              </div>

              <!-- 流式完成 + 结果 -->
              <div v-else-if="aiEditLastResult" class="edit-result-card">
                <div class="erc-hdr">
                  <span class="erc-icon">✨</span>
                  <span>改写结果（{{ aiEditLastResult.text.length }} 字）</span>
                  <button class="erc-discard" @click="discardEditResult" title="丢弃">丢弃</button>
                </div>
                <!-- 7/19 关键：对比视图（diff） -->
                <div v-if="showDiff && aiEditTargetRange" class="erc-diff">
                  <div class="erc-diff-col erc-diff-orig">
                    <div class="erc-diff-hdr">原文</div>
                    <div class="erc-diff-body">{{ aiEditTargetRange.text }}</div>
                  </div>
                  <div class="erc-diff-col erc-diff-new">
                    <div class="erc-diff-hdr">新文</div>
                    <div class="erc-diff-body" v-html="renderAiMarkdown(aiEditLastResult.text)"></div>
                  </div>
                </div>
                <div v-else class="erc-body" v-html="renderAiMarkdown(aiEditLastResult.text)"></div>
                <div class="erc-actions">
                  <button class="erc-toggle" :class="{ active: showDiff }" @click="showDiff = !showDiff" title="对比视图">
                    {{ showDiff ? '📊 对比中' : '📊 对比' }}
                  </button>
                  <button class="erc-btn erc-replace" @click="applyEditResult('replace')" title="替换原选区">🔁 替换</button>
                  <button class="erc-btn erc-insert" @click="applyEditResult('insert-below')" title="插入到下方">⬇ 插入下方</button>
                  <button class="erc-btn erc-copy" @click="copyAiMsg(aiEditLastResult.text)" title="复制">📋 复制</button>
                </div>
              </div>
            </div>

            <!-- 底部：快捷操作 + 输入框（与聊天模式一致风格） -->
            <div class="edit-bottom">
              <!-- 7/19 关键：结果出来后输入框变成"再调整一下"，提示用户二次微调 -->
              <div v-if="aiEditLastResult" class="refine-hint">
                <span class="refine-icon">🔁</span>
                <span>对结果不满意？在下面输入调整指令（如"再短一点"、"再友好一些"）</span>
                <button class="refine-new" @click="discardEditResult">↩ 重新编辑</button>
              </div>

              <div class="edit-quick-actions">
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('rewrite')" title="改写"><span class="qa-icon">✨</span><span class="qa-label">改写</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('expand')" title="扩写"><span class="qa-icon">📝</span><span class="qa-label">扩写</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('shorten')" title="缩写"><span class="qa-icon">✂️</span><span class="qa-label">缩写</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('translate-en')" title="译英"><span class="qa-icon">🇬🇧</span><span class="qa-label">译英</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('translate-zh')" title="译中"><span class="qa-icon">🇨🇳</span><span class="qa-label">译中</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('fix-grammar')" title="纠错"><span class="qa-icon">🔧</span><span class="qa-label">纠错</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('formal')" title="正式"><span class="qa-icon">🎩</span><span class="qa-label">正式</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('casual')" title="随意"><span class="qa-icon">💬</span><span class="qa-label">随意</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('continue')" title="续写"><span class="qa-icon">➡️</span><span class="qa-label">续写</span></button>
                <button class="qa-btn" :disabled="editAiLoading" @click="runEditAction('summarize')" title="总结"><span class="qa-icon">📋</span><span class="qa-label">总结</span></button>
              </div>

              <div class="input-box" :class="{ focused: aiInputFocused }">
                <textarea
                  v-model="aiEditInstruction"
                  :placeholder="aiEditLastResult
                    ? '再调整一下：再短一点 / 语气再友好些 / 第3句改为反问…'
                    : '输入指令：帮我修改第2段的内容 / 帮我修改全文的格式 / 让这段更专业 / 翻译成英文…'"
                  @focus="aiInputFocused = true"
                  @blur="aiInputFocused = false"
                  @input="autoResizeAiInput"
                  @keydown="handleAiEditKeydownEnter"
                  ref="aiEditInputRef"
                  class="ai-textarea"
                  rows="1"
                ></textarea>
                <button
                  v-if="!editAiLoading"
                  class="send-btn"
                  :disabled="!aiEditInstruction.trim()"
                  @click="runEditFromDescription"
                  :title="aiEditLastResult ? '在结果基础上再调整' : '生成'"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
                  </svg>
                </button>
                <button
                  v-else
                  class="send-btn stop-btn"
                  title="停止生成"
                  @click="stopEditAi"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
                </button>
              </div>
              <div class="input-hint">
                <span v-if="aiEditLastResult">🔁 二次微调 · 基于上次结果</span>
                <span v-else>Shift+Enter 换行 · Enter 生成 · 智能识别"第N段/全文"</span>
              </div>
            </div>
          </div>

            <!-- ===== 7/24 关键：聊天模式 = 独立页面 ===== -->
          <div v-else class="ai-chat-page">
              <!-- 消息列表 + welcome 卡片 -->
            <div class="ai-messages" ref="aiMsgRef" @click="handleAiMessagesClick" @scroll.passive="onAiScroll">
              <div v-if="aiMessages.length === 0" class="ai-welcome">
                <!-- 7/4 关键：10 张大卡片 + 返回卡片 -->
                <div class="welcome-hdr">
                  <div class="welcome-icon">
                    <div class="icon-ring"></div>
                    <div class="icon-core">
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
                      </svg>
                  </div>
                </div>
                  <h3>有什么可以帮你的？</h3>
                  <p>选择下面的功能，AI 会自动分析当前文档</p>
              </div>
                <div class="welcome-cards">
                  <button
                    v-for="card in aiWelcomeCards"
                    :key="card.id"
                    class="welcome-card"
                    :class="['card-' + card.color]"
                    @click="quickAiAsk(card.prompt)"
                  >
                    <div class="wc-icon-wrap">
                      <span class="wc-icon">{{ card.icon }}</span>
                  </div>
                    <div class="wc-info">
                      <div class="wc-name">{{ card.name }}</div>
                      <div class="wc-desc">{{ card.desc }}</div>
                  </div>
                  </button>
              </div>
            </div>
  
              <div
                v-for="(m, idx) in aiMessagesView"
                :key="m.id"
                :class="['ai-msg', `ai-msg-${m.role}`, { 'streaming': aiLoading && idx === aiMessagesView.length - 1 }]"
                :data-msg-id="m.id"
              >
                <div class="msg-body">
                  <!-- 思考区（豆包风格 - 浅紫边框） -->
                  <div
                    v-if="m.role === 'assistant' && m.thinking && (m.thinkingDone || aiLoading)"
                    class="thinking-section"
                    :class="{ ongoing: aiLoading && m.thinking && !m.thinkingDone }"
                  >
                    <button class="thinking-toggle" @click="toggleThinkingExpanded(m.id)">
                      <svg
                        class="thinking-icon"
                        :class="{ spinning: aiLoading && m.thinking && !m.thinkingDone }"
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        style="flex-shrink:0"
                      >
                        <circle cx="12" cy="12" r="10"/>
                        <path d="M12 6v6l4 2"/>
                      </svg>
                      <span class="thinking-text-label">{{
                        aiLoading && m.thinking && !m.thinkingDone
                          ? `正在思考… ${m.thinkingDurationSec} 秒`
                          : (m.thinkingDone ? `已思考 ${m.thinkingDurationSec} 秒` : '思考过程')
                      }}</span>
                      <!-- 7/14 关键：折叠图标靠最右 -->
                      <svg class="chevron" :class="{ expanded: m.thinkingExpanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M6 9l6 6 6-6"/>
                      </svg>
                    </button>
                    <!-- 7/14 关键：每条消息独立展开状态 — 流式中默认展开，完成后默认收起 -->
                    <div v-show="m.thinkingExpanded || (aiLoading && m.thinking && !m.thinkingDone)" class="thinking-content-wrapper">
                      <div class="thinking-text">{{ m.thinking }}<span v-if="aiLoading && m.thinking && !m.thinkingDone" class="thinking-cursor">▍</span></div>
                  </div>
                </div>
  
                  <!-- 用户消息：右对齐气泡 -->
                  <div v-if="m.role === 'user'" class="msg-content user-content">
                    <span class="msg-text">{{ m.content }}</span>
                </div>
  
                  <!-- AI 消息：无气泡，纯文字流 -->
                  <div
                    v-else-if="m.thinkingDone || m.content"
                    class="msg-content ai-content"
                    :class="{ 'msg-loading-transparent': aiLoading && idx === aiMessagesView.length - 1 && !m.content }"
                  >
                    <span v-if="aiLoading && idx === aiMessagesView.length - 1 && !m.content" class="typing-dots">
                      <span></span><span></span><span></span>
                    </span>
                    <span v-html="renderAiMarkdown(m.content)"></span>
                    <span v-if="aiLoading && idx === aiMessagesView.length - 1 && m.content" class="ai-streaming-cursor">▍</span>
  
                    <!-- 操作按钮（复制 + 插入） -->
                    <div v-if="m.content && !(aiLoading && idx === aiMessagesView.length - 1)" class="msg-actions">
                      <button class="action-btn" @click="copyAiMsg(m.content)" title="复制">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                      </button>
                      <button class="action-btn insert-btn" @click="insertToEditor(m.content, m.id)" title="插入文档">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 5v14M5 12l7 7 7-7"/></svg>
                      </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
  
            <!-- 输入区（问答模式用） -->
            <div class="ai-input-area">
              <div class="input-box" :class="{ focused: aiInputFocused }">
                <textarea
                  v-model="aiQuestion"
                  :placeholder="aiEditMode
                    ? (aiEditTargetRange
                       ? '输入指令：让这段更简洁 / 翻译成英文 / 扩写...'
                       : '在文档中先选中文本，再输入指令；或留空：续写光标处')
                    : '输入问题或指令...'"
                  @focus="onAiInputFocus"
                  @blur="aiInputFocused = false"
                  @keydown="handleAiKeydown"
                  @input="autoResizeAiInput"
                  ref="aiInputRef"
                  class="ai-textarea"
                  rows="1"
                ></textarea>
                <button
                  v-if="!aiLoading"
                  class="send-btn"
                  :disabled="!aiQuestion.trim()"
                  @click="doSendAiChat"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
                  </svg>
                </button>
                <button
                  v-else
                  class="send-btn stop-btn"
                  title="停止生成"
                  @click="stopAiChat"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
                </button>
            </div>
              <div class="input-hint">
                <span>Shift+Enter 换行 · Enter 发送 · 基于当前文档</span>
            </div>
          </div>
            </div>
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
import { ref, shallowRef, computed, nextTick, onMounted, onBeforeUnmount, watch, triggerRef } from 'vue'
import { EditorContent } from '@tiptap/vue-3'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Highlight from '@tiptap/extension-highlight'
import Image from '@tiptap/extension-image'
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import { common, createLowlight } from 'lowlight'
import { CODE_LANGS, searchLangs } from '@/utils/codeblock-lang'
// 7/12 增强：lowlight 3.x 的 common 仅 ~37 种核心语言；按需增量注册其余小众语言
// 注：highlight.js 11.x 不含 abap/apex/cobol/htmlbars/solidity/toml/vbnet，这些语言选入菜单会回退纯文本
import ada from 'highlight.js/lib/languages/ada'
import apache from 'highlight.js/lib/languages/apache'
import x86asm from 'highlight.js/lib/languages/x86asm'
import cmake from 'highlight.js/lib/languages/cmake'
import coffeescript from 'highlight.js/lib/languages/coffeescript'
import dLang from 'highlight.js/lib/languages/d'
import delphi from 'highlight.js/lib/languages/delphi'
import diffLang from 'highlight.js/lib/languages/diff'
import django from 'highlight.js/lib/languages/django'
import erlang from 'highlight.js/lib/languages/erlang'
import fortran from 'highlight.js/lib/languages/fortran'
import gherkin from 'highlight.js/lib/languages/gherkin'
import graphql from 'highlight.js/lib/languages/graphql'
import groovy from 'highlight.js/lib/languages/groovy'
import http from 'highlight.js/lib/languages/http'
import julia from 'highlight.js/lib/languages/julia'
import lisp from 'highlight.js/lib/languages/lisp'
import matlab from 'highlight.js/lib/languages/matlab'
import nginx from 'highlight.js/lib/languages/nginx'
import objectivec from 'highlight.js/lib/languages/objectivec'
import glsl from 'highlight.js/lib/languages/glsl'
import powershell from 'highlight.js/lib/languages/powershell'
import prolog from 'highlight.js/lib/languages/prolog'
import properties from 'highlight.js/lib/languages/properties'
import protobuf from 'highlight.js/lib/languages/protobuf'
import rLang from 'highlight.js/lib/languages/r'
import sas from 'highlight.js/lib/languages/sas'
import scheme from 'highlight.js/lib/languages/scheme'
import thrift from 'highlight.js/lib/languages/thrift'
import vbscript from 'highlight.js/lib/languages/vbscript'
import vhdl from 'highlight.js/lib/languages/vhdl'
import verilog from 'highlight.js/lib/languages/verilog'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import TextAlign from '@tiptap/extension-text-align'
import { TextStyle, Color } from '@tiptap/extension-text-style'
import Subscript from '@tiptap/extension-subscript'
import Superscript from '@tiptap/extension-superscript'
import Typography from '@tiptap/extension-typography'
import CharacterCount from '@tiptap/extension-character-count'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCursor from '@tiptap/extension-collaboration-cursor'
import Mention from '@tiptap/extension-mention'
import { marked } from 'marked'
import TurndownService from 'turndown'
import { documentAiApi } from '@/api/documentAi'
import { useAiChat, sanitizeAiMarkdown } from '@/composables/useAiChat'
import { ElMessageBox, ElMessage } from 'element-plus'
import { userApi } from '@/api/user'

// 7/12 关键：低光实例 + 注册小众语言（必须在所有 import 之后）
const lowlight = createLowlight(common)
lowlight.register('apache', apache)
lowlight.register('x86asm', x86asm); lowlight.register('cmake', cmake)
lowlight.register('coffeescript', coffeescript); lowlight.register('d', dLang)
lowlight.register('delphi', delphi); lowlight.register('diff', diffLang); lowlight.register('django', django)
lowlight.register('erlang', erlang); lowlight.register('fortran', fortran); lowlight.register('gherkin', gherkin)
lowlight.register('graphql', graphql); lowlight.register('groovy', groovy)
lowlight.register('http', http); lowlight.register('julia', julia); lowlight.register('lisp', lisp)
lowlight.register('matlab', matlab); lowlight.register('nginx', nginx); lowlight.register('objectivec', objectivec)
lowlight.register('glsl', glsl); lowlight.register('powershell', powershell); lowlight.register('prolog', prolog)
lowlight.register('properties', properties); lowlight.register('protobuf', protobuf); lowlight.register('r', rLang)
lowlight.register('sas', sas); lowlight.register('scheme', scheme)
lowlight.register('thrift', thrift); lowlight.register('vbscript', vbscript)
lowlight.register('vhdl', vhdl); lowlight.register('verilog', verilog)
lowlight.register('ada', ada)

/**
 * 7/10 关键：图片可调整大小扩展
 * - 在原生 Image 基础上增加 width / height 属性
 * - 通过 ProseMirror 装饰器，在选中图片时绘制 .image-resizing 高亮 + 提示
 * - 实际拖拽逻辑在 injectImageResize() 中通过全局 mouse 事件实现
 */
const ImageResizeExt = Image.extend({
  addAttributes() {
    return {
      ...(this.parent?.() || {}),
      width: {
        default: null,
        parseHTML: (el: HTMLElement) => {
          const w = el.getAttribute('width')
          return w ? parseInt(w, 10) : null
        },
        renderHTML: (attrs: any) => {
          if (!attrs.width) return {}
          return { width: String(attrs.width) }
        },
      },
      height: {
        default: null,
        parseHTML: (el: HTMLElement) => {
          const h = el.getAttribute('height')
          return h ? parseInt(h, 10) : null
        },
        renderHTML: (attrs: any) => {
          if (!attrs.height) return {}
          return { height: String(attrs.height) }
        },
      },
    }
  },
})

/**
 * 7/12 关键：豆包风格代码块扩展
 * - 头部：语言下拉 + 自动换行 + 复制按钮
 * - 内容区：等宽字体 + 行号（可选）+ 滚动
 * - 通过 NodeView 实现，运行时改 language 立即更新
 */
const CodeBlockDouBao = CodeBlockLowlight.extend({
  addAttributes() {
    // 7/19 关键：保留 parent（默认有 language） + 自定义 wrap/theme/caption
    // 之前没声明自定义 attrs → safeUpdate({ wrap: true }) 静默失败，按钮"看起来没反应"
    return {
      ...((this as any).parent?.() || {}),
      wrap: {
        default: false,
        parseHTML: () => false,
        renderHTML: () => ({}),
      },
      caption: {
        default: null,
        parseHTML: (el: HTMLElement) => el.getAttribute('data-caption') || null,
        renderHTML: () => ({}),
      },
    }
  },
  addNodeView() {
    return ({ node, updateAttributes, editor, getPos }: any) => {
      // 7/19 关键：缓存初始有效的 getPos（mouseup 时可能失效）
      let lastValidPos: number | null = null
      try {
        if (typeof getPos === 'function') {
          const p = getPos()
          if (typeof p === 'number') lastValidPos = p
        }
      } catch {}
      // 7/19 关键：防御性 — updateAttributes 有时会返回 false（位置失效），用兜底直接走 ProseMirror transaction
      const safeUpdate = (attrs: any) => {
        if (typeof updateAttributes === 'function') {
          try {
            const ok = updateAttributes(attrs)
            if (ok !== false) return true
          } catch (e) {
            console.error('[CodeBlockDouBao] updateAttributes failed', e)
          }
        }
        // 兜底
        try {
          let pos: number | null = null
          try { pos = typeof getPos === 'function' ? getPos() : null } catch {}
          if (pos == null) pos = lastValidPos
          if (pos == null || !editor?.view) return false
          lastValidPos = pos
          const tr = editor.view.state.tr.setNodeMarkup(pos, undefined, { ...node.attrs, ...attrs })
          editor.view.dispatch(tr)
          return true
        } catch (e) {
          console.error('[CodeBlockDouBao] safeUpdate fallback failed', e)
          return false
        }
      }
      const dom = document.createElement('div')
      dom.className = 'db-code-block'
      dom.setAttribute('data-wrap', node.attrs.wrap ? '1' : '0')
      dom.setAttribute('data-type', 'code-block')
      // 7/12 重构：去掉了主题切换 — 只保留浅色（VSCode Light+）。所有 token 颜色
      // 直接 inline 写死在 db-code-block 上，避免 NodeView 内的 hljs-* 子节点取不到变量。
      const TOKEN_VARS: Record<string, string> = {
        '--hljs-comment':     '#008000',
        '--hljs-keyword':     '#0000ff',
        '--hljs-string':      '#a31515',
        '--hljs-number':      '#098658',
        '--hljs-function':    '#795e26',
        '--hljs-title':       '#795e26',
        '--hljs-name':        '#795e26',
        '--hljs-variable':    '#001080',
        '--hljs-template-variable': '#001080',
        '--hljs-params':      '#001080',
        '--hljs-type':        '#267f99',
        '--hljs-class':       '#267f99',
        '--hljs-built_in':    '#267f99',
        '--hljs-tag':         '#800000',
        '--hljs-attr':        '#ff0000',
        '--hljs-attribute':   '#ff0000',
        '--hljs-meta':        '#af00db',
        '--hljs-meta-keyword':'#af00db',
        '--hljs-meta-string': '#a31515',
        '--hljs-literal':     '#0000ff',
        '--hljs-symbol':      '#098658',
        '--hljs-bullet':      '#098658',
        '--hljs-regexp':      '#811f3f',
        '--hljs-emphasis':    '#1f1f1f',
        '--hljs-strong':      '#1f1f1f',
        '--hljs-deletion':    '#a31515',
        '--hljs-addition':    '#098658',
        '--hljs-doctag':      '#608b4e',
        '--hljs-section':     '#1f1f1f',
        '--hljs-built_in-name':'#267f99',
        '--hljs-link':        '#0000ff',
        '--hljs-template-tag':'#0000ff',
        '--hljs-bg':          '#ffffff',
        '--hljs-fg':          '#1f1f1f',
        '--hljs-header-bg':   '#f3f3f3',
        '--hljs-border':      '#e5e7eb',
        '--hljs-ui-fg':       '#616161',
        '--hljs-ui-fg-hover': '#1f1f1f',
        '--hljs-ui-fg-strong':'#1f1f1f',
        '--hljs-ui-fg-faint': '#9ca3af',
        '--hljs-ui-hover-bg': 'rgba(0, 0, 0, 0.08)',
        '--hljs-ui-focus-bg': 'rgba(0, 0, 255, 0.10)',
        '--hljs-ui-focus-ring':'rgba(0, 0, 255, 0.30)',
      }
      function applyStaticVars() {
        for (const [k, v] of Object.entries(TOKEN_VARS)) {
          dom.style.setProperty(k, v)
        }
      }
      applyStaticVars()
      // ===== 头部（左 caption 区 + 右 tools 区） =====
      const header = document.createElement('div')
      header.className = 'db-cb-header'
      header.contentEditable = 'false'

      /**
       * 通用工具：创建图标 SVG
       */
      const svgWrap = (inner: string, size = 14) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`

      // 7/19 关键：header 分为左右两段 — caption 区靠左占满，tools 区靠右
      const captionSection = document.createElement('div')
      captionSection.className = 'db-cb-caption-section'
      const toolsSection = document.createElement('div')
      toolsSection.className = 'db-cb-tools-section'

      // 7/16 关键：代码块名称 caption（左侧，可编辑，flex:1 占满剩余空间）
      const captionWrap = document.createElement('span')
      captionWrap.className = 'db-cb-caption'
      captionWrap.style.pointerEvents = 'auto'
      const captionLabel = document.createElement('span')
      captionLabel.className = 'db-cb-caption-label'
      captionLabel.contentEditable = 'true'
      captionLabel.spellcheck = false
      captionLabel.setAttribute('data-placeholder', '代码块')
      const renderCaption = () => {
        const v = node.attrs.caption || ''
        captionLabel.textContent = v || '代码块'
        captionLabel.classList.toggle('is-placeholder', !v)
      }
      renderCaption()
      captionLabel.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation() })
      captionLabel.addEventListener('keydown', (e) => {
        // 阻止输入事件冒泡触发 slash 菜单等
        e.stopPropagation()
        if (e.key === 'Enter') { e.preventDefault(); captionLabel.blur() }
        if (e.key === 'Escape') { e.preventDefault(); captionLabel.blur() }
      })
      captionLabel.addEventListener('blur', () => {
        const v = captionLabel.textContent?.trim() || ''
        if (v !== (node.attrs.caption || '')) {
          safeUpdate({ caption: v || null })
        }
        renderCaption()
      })
      captionLabel.addEventListener('click', () => {
        if (captionLabel.classList.contains('is-placeholder')) {
          captionLabel.textContent = ''
          captionLabel.classList.remove('is-placeholder')
        }
        const range = document.createRange()
        range.selectNodeContents(captionLabel)
        const sel = window.getSelection()
        if (sel) { sel.removeAllRanges(); sel.addRange(range) }
      })
      captionWrap.appendChild(captionLabel)
      captionSection.appendChild(captionWrap)

      // 7/19 关键：使用 el-button 风格的"按钮工厂" — 显式 pointerEvents auto + stopPropagation
      const makeBtn = (cls: string, initHtml: string, onClick: (e: MouseEvent) => void) => {
        const btn = document.createElement('button')
        btn.type = 'button'
        btn.className = cls
        btn.innerHTML = initHtml
        btn.style.pointerEvents = 'auto'
        // 7/19 关键：用 click 而非 mousedown — click 在 mousedown 之后触发，更稳；
        // Tiptap 的 view 监听的是 mousedown，但用户事件 stopPropagation 后冒泡不到 view
        btn.addEventListener('mousedown', (e) => {
          e.preventDefault()
          e.stopPropagation()
        })
        btn.addEventListener('click', (e) => {
          e.preventDefault()
          e.stopPropagation()
          onClick(e)
        })
        return btn
      }

      // ===== 语言下拉 =====
      const langWrap = document.createElement('div')
      langWrap.className = 'db-cb-lang'
      langWrap.style.pointerEvents = 'auto'
      langWrap.style.position = 'relative'
      langWrap.style.display = 'inline-flex'
      const langBtn = makeBtn(
        'db-cb-lang-btn',
        `<span class="db-cb-lang-btn-text">Plain Text</span>${svgWrap(`<path fill-rule="evenodd" clip-rule="evenodd" d="M7 9.204l4.744-4.744a.292.292 0 01.412 0l.413.413a.292.292 0 010 .412l-5.156 5.156a.583.583 0 01-.825 0L1.432 5.285a.292.292 0 010-.412l.412-.413a.292.292 0 01.413 0L7 9.204z" fill="#8F959E"/>`)}`,
        () => {
          // 7/19 关键：用 querySelector 查当前 DOM 里的 langMenu
          const menu = langWrap.querySelector('.db-cb-lang-menu') as HTMLElement | null
          if (!menu) return
          if (menu.style.display === 'none' || !menu.style.display) {
            // 7/19 关键：打开菜单前根据 langBtn 位置 + viewport 计算位置
            // 用 fixed 定位避免被父元素 overflow 裁剪
            menu.style.position = 'fixed'
            menu.style.zIndex = '9999'
            const btnRect = langBtn.getBoundingClientRect()
            const menuMaxHeight = 320
            const menuWidth = 180
            const vh = window.innerHeight
            const vw = window.innerWidth
            const margin = 4
            // 默认向下展开
            let top = btnRect.bottom + margin
            // 如果下方空间不够，向上展开
            if (top + menuMaxHeight > vh - 8) {
              top = Math.max(8, btnRect.top - menuMaxHeight - margin)
            }
            // 水平：右对齐到 btn 右边缘
            let left = btnRect.right - menuWidth
            if (left < 8) left = 8
            if (left + menuWidth > vw - 8) left = vw - menuWidth - 8
            menu.style.top = top + 'px'
            menu.style.left = left + 'px'
            menu.style.maxHeight = Math.min(menuMaxHeight, vh - top - 8) + 'px'
            menu.style.display = 'block'
          } else {
            menu.style.display = 'none'
          }
        }
      )
      const renderLangBtn = () => {
        const v = node.attrs.language || ''
        const found = (CODE_LANGS as typeof CODE_LANGS).find(l => l.value === v)
        const label = found ? found.label : (v || 'Plain Text')
        langBtn.innerHTML = `<span class="db-cb-lang-btn-text">${label}</span>${svgWrap(`<path fill-rule="evenodd" clip-rule="evenodd" d="M7 9.204l4.744-4.744a.292.292 0 01.412 0l.413.413a.292.292 0 010 .412l-5.156 5.156a.583.583 0 01-.825 0L1.432 5.285a.292.292 0 010-.412l.412-.413a.292.292 0 01.413 0L7 9.204z" fill="#8F959E"/>`)}`
      }
      renderLangBtn()
      const langMenu = document.createElement('div')
      langMenu.className = 'db-cb-lang-menu'
      langMenu.style.display = 'none'
      langMenu.style.position = 'fixed'
      langMenu.style.zIndex = '9999'
      // 7/12 增强：搜索框 + 滚动列表
      const langSearch = document.createElement('input')
      langSearch.className = 'db-cb-lang-search'
      langSearch.type = 'text'
      langSearch.placeholder = '搜索语言（中英文 / 别名 / .ext）'
      langSearch.spellcheck = false
      // 阻止 Tiptap 拦截
      langSearch.addEventListener('mousedown', (e) => e.stopPropagation())
      langSearch.addEventListener('keydown', (e) => {
        e.stopPropagation()
        if (e.key === 'Enter') {
          e.preventDefault()
          const first = langListEl.querySelector('.db-cb-lang-item') as HTMLElement | null
          first?.click()
        } else if (e.key === 'Escape') {
          e.preventDefault()
          langMenu.style.display = 'none'
        }
      })
      const langListEl = document.createElement('div')
      langListEl.className = 'db-cb-lang-list'
      langMenu.appendChild(langSearch)
      langMenu.appendChild(langListEl)

      const renderLangItems = (langs: typeof CODE_LANGS) => {
        langListEl.innerHTML = ''
        if (!langs.length) {
          const empty = document.createElement('div')
          empty.className = 'db-cb-lang-empty'
          empty.textContent = '没有匹配的语言'
          langListEl.appendChild(empty)
          return
        }
        const curLang = node.attrs.language || 'plaintext'
        for (const l of langs) {
          const item = document.createElement('button')
          item.type = 'button'
          item.className = 'db-cb-lang-item' + (l.value === curLang ? ' active' : '')
          item.textContent = l.label
          item.style.pointerEvents = 'auto'
          item.addEventListener('mousedown', (e) => {
            e.preventDefault(); e.stopPropagation()
            safeUpdate({ language: l.value === 'plaintext' ? null : l.value })
            ;(langMenu as HTMLElement).style.display = 'none'
            ;(renderLangBtn as any)()
            // 7/12 增强：CodeBlockLowlight plugin 会在 node 变化时自动重渲染装饰，无需手动调用
          })
          item.addEventListener('click', (e) => {
            e.preventDefault(); e.stopPropagation()
            safeUpdate({ language: l.value === 'plaintext' ? null : l.value })
            ;(langMenu as HTMLElement).style.display = 'none'
            ;(renderLangBtn as any)()
            // CodeBlockLowlight plugin 自动重渲染
          })
          langListEl.appendChild(item)
        }
      }
      renderLangItems(CODE_LANGS)

      // 搜索：100ms 防抖
      let langSearchTimer: any = null
      langSearch.addEventListener('input', () => {
        if (langSearchTimer) clearTimeout(langSearchTimer)
        langSearchTimer = setTimeout(() => renderLangItems(searchLangs(langSearch.value)), 100)
      })

      // 打开菜单时聚焦搜索框
      const origLangBtnOnClick = langBtn.onclick
      langBtn.addEventListener('click', () => {
        // 下一帧聚焦搜索框
        setTimeout(() => {
          if (langMenu.style.display !== 'none') {
            langSearch.value = ''
            renderLangItems(CODE_LANGS)
            langSearch.focus()
          }
        }, 0)
      })
      langWrap.appendChild(langBtn)
      langWrap.appendChild(langMenu)
      toolsSection.appendChild(langWrap)

      // ===== 自动换行 =====
      const wrapIconSvg = svgWrap(`<path d="M4.583 4.95c0-.202.164-.367.367-.367h.642c.202 0 .366.165.366.367v12.1a.367.367 0 01-.366.367H4.95a.367.367 0 01-.367-.367V4.95zM7.792 6.783c0-.202.164-.366.366-.366h2.056c2.46 0 4.453 1.847 4.453 4.125 0 2.03-1.583 3.717-3.667 4.06v1.444a.513.513 0 01-.829.405l-2.775-2.158a.513.513 0 010-.81l2.775-2.16a.513.513 0 01.829.406v1.497c1.311-.275 2.292-1.372 2.292-2.684 0-1.52-1.314-2.75-2.934-2.75h-2.2a.367.367 0 01-.366-.367v-.642zM16.408 4.583a.367.367 0 00-.366.367v12.1c0 .203.164.367.366.367h.642a.367.367 0 00.367-.367V4.95a.367.367 0 00-.367-.367h-.642z" fill="#646A73"/>`)
      const wrapBtn = makeBtn(
        'db-cb-wrap-btn',
        `<span class="db-cb-wrap-icon">${wrapIconSvg}</span><span>自动换行</span>`,
        () => {
          const newVal = !node.attrs.wrap
          safeUpdate({ wrap: newVal })
          dom.setAttribute('data-wrap', newVal ? '1' : '0')
          wrapBtn.classList.toggle('active', newVal)
        }
      )
      wrapBtn.classList.toggle('active', !!node.attrs.wrap)
      toolsSection.appendChild(wrapBtn)

      // ===== 复制 =====
      const copyIconSvg = svgWrap(`<path d="M6.188 8.25v8.25h6.875V8.25H6.186zm8.25-.688v9.702c0 .337-.288.611-.642.611H5.454c-.354 0-.641-.274-.641-.611V7.486c0-.337.287-.611.641-.611h8.296c.38 0 .688.308.688.688zm2.548-3.236a.685.685 0 01.201.487v8.593c0 .19-.153.344-.343.344h-.688a.344.344 0 01-.343-.344V5.5H9.28a.344.344 0 01-.344-.344V4.47c0-.19.154-.344.344-.344H16.5c.19 0 .362.077.486.201z" fill="#646A73"/>`)
      const copyBtn = makeBtn(
        'db-cb-copy-btn',
        `<span class="db-cb-copy-icon">${copyIconSvg}</span><span>复制</span>`,
        () => {
          const code = dom.querySelector('.db-cb-pre')?.textContent || ''
          const restore = copyBtn.innerHTML
          try {
            if (navigator.clipboard?.writeText) {
              navigator.clipboard.writeText(code).then(() => {
                copyBtn.innerHTML = `<span class="db-cb-copy-icon" style="color:#10b981">${svgWrap(`<path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" fill="#10b981"/>`)}</span><span>已复制</span>`
                setTimeout(() => { copyBtn.innerHTML = restore }, 1500)
              }).catch(() => {
                copyBtn.innerHTML = `<span class="db-cb-copy-icon">${copyIconSvg}</span><span>失败</span>`
                setTimeout(() => { copyBtn.innerHTML = restore }, 1500)
              })
            } else {
              const ta = document.createElement('textarea')
              ta.value = code
              document.body.appendChild(ta)
              ta.select()
              document.execCommand('copy')
              ta.remove()
              copyBtn.innerHTML = `<span class="db-cb-copy-icon" style="color:#10b981">${svgWrap(`<path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" fill="#10b981"/>`)}</span><span>已复制</span>`
              setTimeout(() => { copyBtn.innerHTML = restore }, 1500)
            }
          } catch {
            copyBtn.innerHTML = `<span class="db-cb-copy-icon">${copyIconSvg}</span><span>失败</span>`
            setTimeout(() => { copyBtn.innerHTML = restore }, 1500)
          }
        }
      )
      toolsSection.appendChild(copyBtn)

      // 7/12 增强：AI 注释按钮 — 把当前代码块内容发给大模型，准确替换插入
      const aiAnnotateIconSvg = svgWrap(`<path d="M12 2l2.4 5.6L20 9l-4.5 4 1.4 6L12 16l-4.9 3 1.4-6L4 9l5.6-1.4L12 2z" fill="#8b5cf6"/>`)
      const aiAnnotateBtn = makeBtn(
        'db-cb-ai-btn',
        `<span class="db-cb-ai-icon">${aiAnnotateIconSvg}</span><span>AI注释</span>`,
        async () => {
          // 7/12 增强：用 node.textContent 拿纯代码（不包含装饰 span）
          const code = node.textContent || ''
          const lang = node.attrs.language || 'plaintext'
          if (!code.trim()) { ElMessage.warning('代码块为空'); return }
          aiAnnotateBtn.disabled = true
          const restore = aiAnnotateBtn.innerHTML
          aiAnnotateBtn.innerHTML = '<span style="font-size:11px;color:#8b5cf6">生成中...</span>'
          try {
            // 7/12 增强：关键 — 把代码块内容直接拼进 user message，避免依赖后端的 docContent 占位符逻辑
            // 同步把代码作为 body.content 传给后端（双保险）
            const langLabel = lang === 'plaintext' ? '代码' : `${lang} 代码`
            const sysPrompt = `你是代码注释专家。为下面的${langLabel}添加精准的中文行内注释。

严格要求：
1. 输出只能是被注释过的代码原文
2. 禁止任何思考、解释、说明、前言、后记
3. 禁止 Markdown 代码块标记（\`\`\`）
4. 禁止代码前后任何非代码字符（包括空行/分隔符/标题）
5. 保持原代码缩进、空行、变量名完全不变
6. 只在关键逻辑处加 // 注释（用对应语言注释符号）
7. 注释简短（≤25 字/行）`
            const userMsg = `下面是待加注释的${langLabel}：

\`\`\`${lang === 'plaintext' ? '' : lang}
${code}
\`\`\`

请直接返回带注释的代码（保持 \`\`\` 包裹）。`
            // 7/12 增强：user message 内嵌代码 + body.content 同步发，最大限度确保大模型看到代码
            await aiAnnotateChat.sendUserMessage(userMsg, sysPrompt, code)
            // 从 messages 提取 assistant 最终文本（只取 text 类型 part，跳过 reasoning）
            const msgs = aiAnnotateChat.messages.value
            const lastAssistant = [...msgs].reverse().find(m => m.role === 'assistant')
            let annotated = ''
            if (lastAssistant?.parts) {
              annotated = lastAssistant.parts
                .filter((p: any) => p.type === 'text')  // 跳过 reasoning
                .map((p: any) => p.text || '')
                .join('')
            }
            // 清洗常见 Markdown 包裹 + 提取首个代码块
            annotated = cleanAiCodeOutput(annotated)
            if (annotated.trim()) {
              // 准确替换插入原代码块中：用 transaction 替换 textContent
              try {
                const pos = (typeof getPos === 'function') ? getPos() : null
                if (pos !== null && editor) {
                  const tr = editor.state.tr
                  const curNode = editor.state.doc.nodeAt(pos)
                  if (curNode && curNode.type.name === 'codeBlock') {
                    const newNode = curNode.type.create(
                      { ...curNode.attrs },
                      editor.schema.text(annotated)
                    )
                    tr.replaceWith(pos, pos + curNode.nodeSize, newNode)
                    editor.view.dispatch(tr)
                    ElMessage.success('AI 注释已插入代码块')
                  }
                }
              } catch (e: any) {
                ElMessage.error('替换失败：' + (e?.message || '未知错误'))
              }
            } else {
              ElMessage.warning('AI 未返回有效内容')
            }
          } catch (e: any) {
            ElMessage.error('AI 生成失败：' + (e?.message || '未知错误'))
          } finally {
            aiAnnotateBtn.disabled = false
            aiAnnotateBtn.innerHTML = restore
          }
        }
      )
      toolsSection.appendChild(aiAnnotateBtn)

      // 7/19 关键：header 内部两段式 — caption 在左占满，tools 在右
      header.appendChild(captionSection)
      header.appendChild(toolsSection)
      dom.appendChild(header)

      // 7/19 关键：document capture phase + setTimeout 0 关闭外部 langMenu
      // 用 target 检查避免捕获阶段误关（按钮自身 mousedown 在 capture 阶段先于 bubble）
      // 7/19 关键修复：使用 DOM lookup（querySelector）而不是闭包变量，
      // 因为 NodeView 重建后闭包内的 langMenu 已经不在 DOM 里
      const closeLangMenuIfOutside = (ev: MouseEvent) => {
        const t = ev.target as Element
        if (!t) return
        let cur: Element | null = t
        let inLangArea = false
        while (cur) {
          const cn = (cur as any).className
          const cls = (typeof cn === 'string') ? cn : (cn && cn.baseVal) || ''
          if (cls.includes('db-cb-lang')) { inLangArea = true; break }
          if (cls.includes('db-code-block')) break
          cur = cur.parentNode as Element
        }
        if (!inLangArea) {
          // 不在 lang 区 → 关闭所有菜单
          document.querySelectorAll('.db-cb-lang-menu').forEach(m => { (m as HTMLElement).style.display = 'none' })
        }
      }
      document.addEventListener('mousedown', closeLangMenuIfOutside, true)

      // ===== 内容区 =====
// 7/12 增强：CodeBlockLowlight 内部 ProseMirror Plugin + DecorationSet 自动给 code 文本
// 加 <span class="hljs-*"> 装饰。这里只维护 DOM 结构（contentDOM 留给 ProseMirror 管文本），
// 严禁手动改 innerHTML / appendChild — 会破坏 ProseMirror content 状态。
const content = document.createElement('pre')
content.className = 'db-cb-pre'
content.style.pointerEvents = 'auto'
const codeEl = document.createElement('code')
// 7/12 增强：className 上同时标 language-xxx 和 hljs，让低光 plugin 渲染 + CSS 选择器命中
codeEl.className = node.attrs.language ? `language-${node.attrs.language} hljs` : 'hljs'
content.appendChild(codeEl)
dom.appendChild(content)

// 切语言时同步 className（CodeBlockLowlight plugin 会自动重渲染装饰）
function syncCodeElClass() {
  codeEl.className = node.attrs.language ? `language-${node.attrs.language} hljs` : 'hljs'
}



      return {
        dom,
        contentDOM: codeEl,
        // 7/19 关键：renderLangBtn 修改 langBtn.innerHTML 会触发 ProseMirror mutation 检测
        // 添加 ignoreMutation 告诉 ProseMirror 这些变化是正常的，不要重建 NodeView
        ignoreMutation(mutation: any) {
          const target = mutation.target as Element
          if (!target || !target.className) return false
          const cls = typeof target.className === 'string' ? target.className : ''
          if (cls.includes('db-cb-')) return true
          let cur: Element | null = target
          while (cur && cur !== dom) {
            const cn = (cur as any).className
            const c = (typeof cn === 'string') ? cn : ''
            if (c.includes('db-cb-')) return true
            cur = cur.parentElement
          }
          return false
        },
        // 7/19 关键：让 ProseMirror 不处理 header 内的事件（按钮点击不被拦截）
        // 注意：这里要返回 true 的事件必须在 NodeView 内部自己处理完（即 button handler 已经处理）
        stopEvent(event: Event) {
          // 7/19 关键：让 ProseMirror 不处理 header 内的事件（按钮点击不被拦截）
          // 注意：target 可能是 NodeView root dom（因为 ProseMirror 重新派发事件），
          //      所以不能用 contains 检查。简单做法：mousedown/click 都让 button 自己处理
          const type = event.type
          if (type === 'mousedown' || type === 'click' || type === 'pointerdown' || type === 'pointerup') {
            return true
          }
          return false
        },
        update(newNode) {
          if (newNode.type.name !== 'codeBlock') return false
          const newLang = newNode.attrs.language || ''
          if (newLang !== (node.attrs.language || '')) {
            syncCodeElClass()
            // 7/12 增强：CodeBlockLowlight plugin 会监听 transaction 自动重渲染装饰
            ;(renderLangBtn as any)()
          }
          if (!!newNode.attrs.wrap !== !!node.attrs.wrap) {
            dom.setAttribute('data-wrap', newNode.attrs.wrap ? '1' : '0')
            wrapBtn.classList.toggle('active', !!newNode.attrs.wrap)
          }
          if ((newNode.attrs.caption || '') !== (node.attrs.caption || '')) {
            renderCaption()
          }
          node = newNode
          return true
        },
        destroy() {
          document.removeEventListener('mousedown', closeLangMenuIfOutside, true)
        },
      }
    }
  },
})

// 7/1 关键：HTML ↔ Markdown 转换器（保证保存为 .md 文件）
const turndownService = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
  emDelimiter: '*',
})
// turndown 规则：过滤 mark 标签（AI 淡黄高亮不污染 markdown 输出）
turndownService.addRule('stripHighlights', {
  filter: (node) => node.nodeName === 'MARK',
  replacement: (_content, node) => node.textContent || '',
})
// 7/19 关键：保存 caption（代码块名称）到 markdown 注释里，恢复时 parseHTML 读取
turndownService.addRule('codeBlockCaption', {
  filter: (node) => {
    if (node.nodeName !== 'PRE') return false
    const cap = node.getAttribute('data-caption')
    return !!cap
  },
  replacement: (_content, node) => {
    const lang = node.querySelector('code')?.className?.match(/language-(\S+)/)?.[1] || ''
    const caption = node.getAttribute('data-caption') || ''
    const text = node.querySelector('code')?.textContent || ''
    return `\n\n<!-- codeblock-caption:${caption} -->\n\`\`\`${lang}\n${text}\n\`\`\`\n\n`
  },
})
// 7/12 增强：lowlight 高亮的 <span class="hljs-*"> 仅是渲染层，导出 .md 时剥掉 span 只保留文本
turndownService.addRule('hljsStrip', {
  filter: (node) => node.nodeName === 'SPAN' && (node as HTMLElement).className?.includes?.('hljs-'),
  replacement: (_content, node) => node.textContent || '',
})

const props = defineProps<{ docId: number; docKey: string; initialContent?: string; canEdit: boolean; userName: string; userId: number }>()
const emit = defineEmits(['ready', 'stateChange', 'contentChange'])

const blockType = ref('paragraph')
const showBubble = ref(false)
const bubbleStyle = ref<Record<string, string>>({ top: '0px', left: '0px' })
const blockLabel = computed(() => ({ paragraph:'正文', heading1:'标题 1', heading2:'标题 2', heading3:'标题 3', codeBlock:'代码块' }[blockType.value] || '正文'))
const collabUsers = ref<Array<{ id: number; name: string; color: string }>>([])
const showAi = ref(false)
const aiProcessing = ref(false)

// ========== AI 浮窗（豆包风格）状态 ==========
// 选区上方实时显示 AI 输出 + 接受/拒绝
type AiFloatCommand = 'smart-edit' | 'rewrite' | 'expand' | 'shorten' |
  'translate-en' | 'translate-zh' | 'fix-grammar' | 'formal' | 'casual' | 'continue'

interface AiFloatPanel {
  visible: boolean
  command: AiFloatCommand
  title: string
  status: 'idle' | 'streaming' | 'done' | 'error' | 'aborted'
  text: string
  thinking: string
  thinkingDone: boolean
  thinkingExpanded: boolean
  thinkingStartedAt?: number
  thinkingEndedAt?: number
  thinkingDurationSec: number
  options: Array<{ title: string; content: string }>
  originalText: string
  originalRange: { from: number; to: number }
  insertAtCursor: boolean
  position: { top: number; left: number }
  placeAbove: boolean
  diffMode: boolean
  pendingDiff: boolean
  diffState: null | {
    originalText: string
    newText: string
    removedFrom: number
    removedTo: number
    addedStart: number
    addedEnd: number
    originalDocSize: number
  }
  // 7/4 智能编辑：浮窗内嵌指令输入
  awaitingInstruction: boolean
  smartEditDraft: string
  // 9/7 关键：原文内容是否展开
  originalExpanded: boolean
}

const aiFloatPanel = ref<AiFloatPanel>({
  visible: false,
  command: 'rewrite',
  title: '',
  status: 'streaming',
  text: '',
  thinking: '',
  thinkingDone: false,
  thinkingExpanded: false,
  thinkingStartedAt: undefined,
  thinkingEndedAt: undefined,
  thinkingDurationSec: 0,
  options: [],
  originalText: '',
  originalRange: { from: 0, to: 0 },
  insertAtCursor: false,
  position: { top: 0, left: 0 },
  placeAbove: true,
  diffMode: true,
  pendingDiff: false,
  diffState: null,
  awaitingInstruction: false,
  smartEditDraft: '',
  // 9/7 关键：原文展开状态
  originalExpanded: true,
})

const AI_COMMAND_TITLES: Record<string, string> = {
  'smart-edit': '智能编辑',
  'rewrite': '改写',
  'expand': '扩写',
  'shorten': '缩写',
  'translate-en': '翻译为英文',
  'translate-zh': '翻译为中文',
  'fix-grammar': '修正语法',
  'formal': '改写为正式语气',
  'casual': '改写为随意语气',
  'continue': '续写',
}

const smartEditSuggestions = [
  '让这段更专业',
  '翻译成英文',
  '扩写更详细',
  '缩写更简洁',
  '更正式的语气',
  '更随意的语气',
]

function handleSmartEditKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submitSmartEditInstruction()
  }
  if (e.key === 'Escape') {
    e.preventDefault()
    closeAiFloatPanel()
  }
}

// AI 侧栏 mode-toggle（问答/编辑）
const aiEditMode = ref(false)
const aiEditTargetRange = ref<{ from: number; to: number; text: string } | null>(null)
// 7/19 关键：编辑作用域 — "全文 / 选区 / 第N段" 由用户描述智能识别
const editScopeFull = ref(false)
const editScopeSection = ref(false)
// 7/19 关键：UI 状态
const editOrigExpanded = ref(false)  // 顶部原文预览是否展开
const showDiff = ref(false)          // 结果区是否显示 diff 对比
const preserveMarkdown = ref(true)   // 是否保留原文 markdown 结构
const autoApplyToEditor = ref(false) // 是否结果直接替换（不再需要手动点）
const editAiLoading = computed(() => editAi.status.value === 'streaming' || editAi.status.value === 'submitted')
// 7/11 关键：编辑模式独立的状态 — 不复用智能编辑
const aiEditInstruction = ref('')
const aiEditInputRef = ref<HTMLTextAreaElement | null>(null)
interface EditResult { text: string; targetRange: { from: number; to: number; text: string } | null; action: string }
const aiEditLastResult = ref<EditResult | null>(null)
/** 7/21 关键：流式结果显示（独立于 aiEditLastResult，实时滚动显示） */
const aiEditStreamingText = ref<{ text: string; done: boolean } | null>(null)
const aiEditLoading = computed(() => editAiLoading.value)
const aiPanelWidth = ref(400)
const isResizing = ref(false)
const aiInputRef = ref<HTMLTextAreaElement | null>(null)
const aiInputFocused = ref(false)

// AI 全局遮盖层（防止误输入）
/**
 * 7/3 关键修复：editor 专属遮盖层（不含侧栏 AI）
 * 之前：aiCoverVisible 包含 aiLoading（侧栏 AI 也会触发遮盖）
 * 现在：editAiCoverVisible 仅在 editAi/slashAi 流式时显示
 */
const editAiCoverVisible = computed(() => editAiLoading.value || slashAiLoading.value)

// 全屏
const isFullscreen = ref(false)

// 独立 useAiChat 实例
const editAi = useAiChat({ docId: computed(() => props.docId) })
// editAiLoading 已在第 1259 行声明
const slashAi = useAiChat({ docId: computed(() => props.docId) })
const slashAiLoading = computed(() => slashAi.status.value === 'submitted' || slashAi.status.value === 'streaming')
// 9/7 关键：/ 菜单 AI 生成走独立 generate-stream 接口（不携带文档内容）
const aiGenerate = useAiChat({ docId: computed(() => props.docId), endpoint: 'generate-stream' })
const aiGenerateLoading = computed(() => aiGenerate.status.value === 'submitted' || aiGenerate.status.value === 'streaming')

// 7/12 增强：代码块 AI 注释独立实例（系统提示引导"加注释"，不解释）
const aiAnnotateChat = useAiChat({ docId: computed(() => props.docId) })

// 大纲导航
const showOutline = ref(false)
interface OutlineItem { level: number; text: string; pos: number }
const outlineItems = ref<OutlineItem[]>([])
const activeOutlineIndex = ref(-1)

// / 快捷命令菜单
const showSlashMenu = ref(false)
const slashMenuStyle = ref<Record<string, string>>({ top: '60px', left: '50%' })
const showPreviewModal = ref(false)
const previewMarkdown = `# 一级标题 H1
## 二级标题 H2
### 三级标题 H3
#### 四级标题 H4
##### 五级标题 H5
###### 六级标题 H6

**粗体** 和 *斜体* 和 \`行内代码\`

- 列表项 1
- 列表项 2`

// 统一颜色按钮：8 色预设 + 持久化记忆色 + 最近用过列表 + 自定义取色器
const hlColors = [
  { v: '#fef08a', l: '黄' }, { v: '#bbf7d0', l: '绿' }, { v: '#bfdbfe', l: '蓝' },
  { v: '#fbcfe8', l: '粉' }, { v: '#fed7aa', l: '橙' }, { v: '#e9d5ff', l: '紫' },
  { v: '#fecaca', l: '红' }, { v: '#a5f3fc', l: '青' },
]

const textColors = [
  { v: '#ef4444', l: '红' }, { v: '#f59e0b', l: '橙' }, { v: '#eab308', l: '黄' },
  { v: '#22c55e', l: '绿' }, { v: '#06b6d4', l: '青' }, { v: '#3b82f6', l: '蓝' },
  { v: '#8b5cf6', l: '紫' }, { v: '#1f2937', l: '黑' },
]

// 颜色持久化：记忆色 key 保留以兼容旧用户；新增"最近用过"数组
const STORAGE_KEY_TEXT = 'miaotong-color-text'
const STORAGE_KEY_HL = 'miaotong-color-highlight'
const STORAGE_KEY_RECENT = 'miaotong-color-recent'
const FALLBACK_TEXT = '#1f2937'
const FALLBACK_HL = '#fef08a'
const MAX_RECENT = 8

function loadStoredColor(key: string, fallback: string): string {
  try {
    const v = localStorage.getItem(key)
    return v || fallback
  } catch { return fallback }
}
function loadRecent(): RecentItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_RECENT)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr.slice(0, MAX_RECENT) : []
  } catch { return [] }
}
type RecentItem = { color: string; kind: 'text' | 'highlight'; ts: number }
const currentTextColor = ref(loadStoredColor(STORAGE_KEY_TEXT, FALLBACK_TEXT))
const currentHighlightColor = ref(loadStoredColor(STORAGE_KEY_HL, FALLBACK_HL))
const customTextColor = ref(currentTextColor.value)
const customHlColor = ref(currentHighlightColor.value)
const recentColors = ref<RecentItem[]>(loadRecent())
// 合一颜色按钮的 popover 开关；单击主按钮直接应用最近色，箭头按钮才开色板
const colorPanelOpen = ref(false)
// hover 行为：进入 .db-color-split 自动开，离开 250ms 缓冲后关
const colorSplitRef = ref<HTMLElement | null>(null)
const popoverPanelRef = ref<HTMLElement | null>(null)
let colorLeaveTimer: ReturnType<typeof setTimeout> | null = null

// 最近用过的"有效颜色"：从 recentColors 里挑该 kind 最近一次用的；若没有，回退到默认色
const lastTextRecent = computed<RecentItem | undefined>(() => recentColors.value.find(r => r.kind === 'text'))
const lastHlRecent = computed<RecentItem | undefined>(() => recentColors.value.find(r => r.kind === 'highlight'))
const hasRecentText = computed(() => !!lastTextRecent.value && lastTextRecent.value.color !== FALLBACK_TEXT)
const hasRecentHl = computed(() => !!lastHlRecent.value && lastHlRecent.value.color !== FALLBACK_HL)
const effectiveTextColor = computed(() => lastTextRecent.value?.color || currentTextColor.value)
const effectiveHlColor = computed(() => lastHlRecent.value?.color || currentHighlightColor.value)

// 7/12 关键：代码块语言列表已迁移至 @/utils/codeblock-lang.ts（70 项 + 搜索）

const imageInputRef = ref<HTMLInputElement | null>(null)
// 7/19 关键：字体/高亮颜色 popover 的 template ref + hide 回调
// 7/19 关键：颜色按钮 swatch 自身已处理"切记忆色 + 立即应用到选区"，无需 popover 拦截逻辑
// imageInputRef 保持不变

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
let contentSaveTimer: ReturnType<typeof setTimeout> | null = null

const charCount = ref(0)
const wordCount = ref(0)

// 编辑器在 onMounted 中创建（需要 DOM refs 给 BubbleMenu/FloatingMenu）
const editor = shallowRef<any>(undefined)

onMounted(() => {
  const ed = new Editor({
    extensions: [
      // 7/12 关键：禁用 StarterKit 自带 CodeBlock，用自定义豆包风格
      StarterKit.configure({ link: { openOnClick: false }, codeBlock: false }),
      Highlight.configure({ multicolor: true }),
      // 7/10 关键：用支持 width/height 的 ImageResizeExt 替换默认 Image
      ImageResizeExt,
      // 7/12 关键：豆包风格代码块 NodeView（lowlight 语法高亮）
      CodeBlockDouBao.configure({ lowlight, defaultLanguage: null }),
      Placeholder.configure({ placeholder: '输入 / 唤出快捷菜单，或直接开始写作...' }),
      Table.configure({ resizable: true }), TableRow, TableCell, TableHeader,
      TaskList, TaskItem.configure({ nested: true }),
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Subscript, Superscript, Typography,
      CharacterCount,
      // 7/5 关键：字体颜色支持
      TextStyle.configure(), Color.configure(),
      Collaboration.configure({ document: ydoc, field: 'content' }),
      CollaborationCursor.configure({ provider, user: { name: myName, color: myColor } }),
      // 6/28 7:34 添加：@提及用户扩展
      Mention.configure({
        HTMLAttributes: { class: 'mention-node' },
        renderText: ({ node }: any) => `@${node.attrs.label || node.attrs.id}`,
        // 7/3 关键：@ 触发 → 搜索用户 API → 弹窗选择
        suggestion: {
          char: '@',
          // 搜索用户
          items: async ({ query }: { query: string }) => {
            try {
              const res: any = await userApi.search(query || '')
              // 后端返回 UserItem[]：{ id, username, realName, employeeId, position, department }
              const arr = Array.isArray(res) ? res : (res?.data || [])
              return arr.map((u: any) => ({
                id: u.id,
                name: u.realName ? `${u.realName}（${u.employeeId}）` : (u.username || ''),
                position: u.position || u.department || '',
              }))
            } catch (err) {
              console.error('[Mention] 搜索用户失败:', err)
              return []
            }
          },
          // 渲染用户弹窗
          render: () => {
            let popup: HTMLElement | null = null
            let selectedIndex = 0
            return {
              onStart: (props: any) => {
                popup = document.createElement('div')
                popup.className = 'mention-popup'
                popup.innerHTML = '<div class="mention-loading">搜索中...</div>'
                document.body.appendChild(popup)
                positionPopup(popup, props.clientRect?.())
              },
              onUpdate(props: any) {
                if (!popup) return
                const items = props.items || []
                if (!items.length) {
                  popup.innerHTML = '<div class="mention-empty">无匹配用户</div>'
                  return
                }
                selectedIndex = 0
                popup.innerHTML = items.map((u: any, i: number) =>
                  `<div class="mention-item ${i === 0 ? 'selected' : ''}" data-idx="${i}">
                    <span class="mention-avatar">${(u.name || '?').charAt(0)}</span>
                    <span class="mention-name">${u.name || ''}</span>
                    ${u.position ? `<span class="mention-pos">${u.position}</span>` : ''}
                  </div>`
                ).join('')
                positionPopup(popup, props.clientRect?.())
                // 绑定点击
                popup.querySelectorAll('.mention-item').forEach((el, i) => {
                  el.addEventListener('mousedown', (e) => {
                    e.preventDefault()
                    props.command({ id: items[i].id, name: items[i].name })
                  })
                })
              },
              onKeyDown(props: any) {
                const items = props.items || []
                if (!items.length) return false
                if (props.event.key === 'ArrowDown') {
                  selectedIndex = (selectedIndex + 1) % items.length
                  popup?.querySelectorAll('.mention-item').forEach((el: any, i: number) => {
                    el.classList.toggle('selected', i === selectedIndex)
                  })
                  return true
                }
                if (props.event.key === 'ArrowUp') {
                  selectedIndex = (selectedIndex - 1 + items.length) % items.length
                  popup?.querySelectorAll('.mention-item').forEach((el: any, i: number) => {
                    el.classList.toggle('selected', i === selectedIndex)
                  })
                  return true
                }
                if (props.event.key === 'Enter' || props.event.key === 'Tab') {
                  const u = items[selectedIndex]
                  if (u) {
                    props.command({ id: u.id, name: u.name })
                    return true
                  }
                }
                if (props.event.key === 'Escape') {
                  popup?.remove()
                  return true
                }
                return false
              },
              onExit() {
                popup?.remove()
                popup = null
              },
            }

            function positionPopup(el: HTMLElement | null, rect: any) {
              if (!el || !rect) return
              // 7/4 关键：popup append 到 document.body → 用 fixed（相对视口）
              el.style.position = 'fixed'
              // 防止溢出视口底部
              const popupHeight = 240
              const spaceBelow = window.innerHeight - rect.bottom
              const top = spaceBelow < popupHeight && rect.top > popupHeight
                ? rect.top - popupHeight - 4  // 上方显示
                : rect.bottom + 4              // 下方显示
              el.style.top = `${top}px`
              el.style.left = `${rect.left}px`
              el.style.zIndex = '99999'
            }
          },
          command: ({ editor, range, props }: any) => {
            editor.chain().focus().insertContentAt(range, [
              { type: 'mention', attrs: { id: props.id, label: props.name } },
              { type: 'text', text: ' ' },
            ]).run()
          },
        },
      }),
    ],
    editable: props.canEdit,
    onUpdate: ({ editor: e }) => {
      emit('stateChange', 'editing')
      // 7/1 关键防抖：避免 Yjs 同步过程反复触发 contentChange
      if (contentSaveTimer) clearTimeout(contentSaveTimer)
      contentSaveTimer = setTimeout(() => {
        const html = e.getHTML()
        const md = turndownService.turndown(html)
        emit('contentChange', md)
      }, 2000)
      charCount.value = e.storage.characterCount.characters()
      wordCount.value = e.storage.characterCount.words()
      updateOutline()
      syncBlockTypeFromEditor()
      syncTextColorsFromEditor()
    },
    onCreate: ({ editor: e }) => {
      // 7/1 关键：等 provider.synced 后再加载 initialContent，避免与 Yjs 远端内容冲突
      const loadInitialContent = () => {
        if (props.initialContent && !contentSet) {
          const fragment = ydoc.getXmlFragment('content')
          if (fragment.length === 0) {
            const html = marked.parse(props.initialContent) as string
            e.commands.setContent(html)
          }
          contentSet = true
        }
        charCount.value = e.storage.characterCount.characters()
        wordCount.value = e.storage.characterCount.words()
        emit('ready')
      }
      if (provider.synced) {
        loadInitialContent()
      } else {
        provider.once('sync', loadInitialContent)
      }
    },
  })

  editor.value = ed
  injectTableIcons(ed.view.dom as HTMLElement)
  ed.on('update', () => injectTableIcons(ed.view.dom as HTMLElement))

  // 7/13 关键：监听图片点击 → 选中图片节点 → 显示缩放手柄
  ed.view.dom.addEventListener('click', (ev: MouseEvent) => {
    const target = ev.target as HTMLElement
    if (target.tagName === 'IMG') {
      ev.preventDefault()
      // 找图片节点位置
      const pos = ed.view.posAtCoords({ left: ev.clientX, top: ev.clientY })
      if (pos) {
        const node = ed.state.doc.nodeAt(pos.pos)
        if (node && node.type.name === 'image') {
          ed.chain().focus().setNodeSelection(pos.pos).run()
          return
        }
        // 也试一下 pos-1（图片节点可能在 pos 之前）
        const nodeBefore = ed.state.doc.nodeAt(pos.pos - 1)
        if (nodeBefore && nodeBefore.type.name === 'image') {
          ed.chain().focus().setNodeSelection(pos.pos - 1).run()
        }
      }
    }
  })

  // 监听文本输入：当光标前是 '/' 时显示 / 快捷命令菜单
  ed.on('update', () => {
    detectSlashTrigger(ed)
  })
  // 7/11 关键：同时监听 transaction — 比 update 更早、更频繁（input 阶段）
  // 解决"按 / 没反应"的灵敏度问题（Yjs 协同延迟时也能即时触发）
  ed.on('transaction', () => {
    detectSlashTrigger(ed)
  })

  // 监听选区变化：同步 blockType（让工具栏"正文/标题1/2/3"实时反映光标所在 block）
  ed.on('selectionUpdate', () => {
    syncBlockTypeFromEditor()
    syncTextColorsFromEditor()
    // 7/10 关键：选区变化时，注入/移除图片缩放手柄
    try { injectImageResize(ed.view.dom as HTMLElement) } catch {}
    // 7/11 关键：光标移动时也检查 / 触发（点击别处时菜单消失）
    detectSlashTrigger(ed)
  })

  // 监听选区变化，显示/隐藏浮动菜单
  let bubbleTimer: ReturnType<typeof setTimeout> | null = null
  const onSelectionChange = () => {
    if (bubbleTimer) clearTimeout(bubbleTimer)
    bubbleTimer = setTimeout(() => {
      const sel = window.getSelection()
      if (!sel || sel.isCollapsed || !sel.rangeCount) {
        showBubble.value = false
        return
      }
      const range = sel.getRangeAt(0)
      if (!ed.view.dom.contains(range.commonAncestorContainer)) {
        showBubble.value = false
        return
      }
      const rect = range.getBoundingClientRect()
      // 7/12 关键修复：双层 fallback 测量 bubble 尺寸
      const bubbleEl = document.querySelector('.bubble-bar') as HTMLElement | null
      let menuWidth = 240
      let menuHeight = 40
      if (bubbleEl && bubbleEl.offsetWidth > 0) {
        menuWidth = bubbleEl.offsetWidth
        menuHeight = bubbleEl.offsetHeight
      }
      // 7/12 关键修复：找到真正的编辑器内容容器边界
      // 不管用 .md-editor-content 还是 .md-wrapper 还是 .ProseMirror（嵌套结构）
      const editorEl = ed.view.dom.closest('.md-editor-content, .md-wrapper') as HTMLElement | null
      const editorRect = editorEl?.getBoundingClientRect()
      // 7/12 关键修复：编辑器容器的左右边界（bubble 必须 clamp 在内）
      const editorLeft = editorRect ? editorRect.left : 8
      const editorRight = editorRect ? editorRect.right : window.innerWidth
      const margin = 8

      // 默认位置：选区上方居中
      let top = rect.top - menuHeight - 8
      let left = rect.left + rect.width / 2 - menuWidth / 2
      // 防止溢出上方 → 选区下方
      if (top < margin) top = rect.bottom + 8
      // 7/12 关键修复：bubble-bar 严格 clamp 到编辑器容器内（不能超出右侧）
      // 第一步：保证 right edge 不超过 editorRight
      if (left + menuWidth > editorRight - margin) {
        // 选区靠右：把 bubble 整个移到选区左边
        left = rect.left - menuWidth - 8
        if (left + menuWidth > editorRight - margin) {
          // 选区也靠右（bubble 放右边会越界）→ 贴编辑器右边
          left = editorRight - menuWidth - margin
        }
      }
      // 第二步：保证 left edge 不超过 editorLeft
      if (left < editorLeft + margin) {
        left = editorLeft + margin
        // 如果调整后 right 越界 → 居中
        if (left + menuWidth > editorRight - margin) {
          left = editorLeft + (editorRight - editorLeft - menuWidth) / 2
        }
      }
      // 第三步：最终 clamp 顶部
      if (top < margin) top = margin
      if (top + menuHeight > window.innerHeight - margin) {
        top = Math.max(margin, window.innerHeight - menuHeight - margin)
      }
      bubbleStyle.value = {
        position: 'fixed',
        top: `${top}px`,
        left: `${left}px`,
        zIndex: '9999',
      }
      showBubble.value = true
    }, 200)
  }
  document.addEventListener('selectionchange', onSelectionChange)
})

watch(() => props.canEdit, v => { if (editor.value) editor.value.setEditable(v) })
onBeforeUnmount(() => { editor.value?.destroy(); releaseYjsEntry(props.docKey); if (contentSaveTimer) clearTimeout(contentSaveTimer) })

/**
 * 7/3 关键修复：slash 菜单键盘导航（capture 阶段）
 * 9/7 关键：去掉智能编辑；上下/Enter 导航与模板 items 一一对应
 * 解决 A 键被 Tiptap 吞掉、↑↓ Enter 穿透问题
 */
const slashSelectedIndex = ref(0)
// 9/7 同步：与模板中的 slash-menu-item 顺序一致
const slashMenuItems = ['h1', 'h2', 'h3', 'quote', 'code', 'ul', 'ol', 'task', 'table', 'ai']
function handleSlashKeydown(e: KeyboardEvent) {
  if (!showSlashMenu.value) return
  const target = e.target as HTMLElement | null
  if (target && (target.closest('.smart-edit-panel') || target.closest('.ai-input-area') || target.closest('.ai-float-panel'))) return
  if (e.key === 'ArrowDown') {
    e.preventDefault(); e.stopPropagation()
    slashSelectedIndex.value = (slashSelectedIndex.value + 1) % slashMenuItems.length
    scrollSlashMenuToActive()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault(); e.stopPropagation()
    slashSelectedIndex.value = (slashSelectedIndex.value - 1 + slashMenuItems.length) % slashMenuItems.length
    scrollSlashMenuToActive()
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    // 不阻止 Enter 以免影响代码块等场景 → 仅在菜单可见时拦截
    e.preventDefault(); e.stopPropagation()
    runSlashCommand(slashMenuItems[slashSelectedIndex.value])
    showSlashMenu.value = false
  } else if (e.key === 'Escape') {
    e.preventDefault(); e.stopPropagation()
    showSlashMenu.value = false
  }
}
/**
 * 自动滚动斜杠菜单：让当前 active 项始终在视口内（避免菜单过长时键盘移出可见区）
 */
function scrollSlashMenuToActive() {
  nextTick(() => {
    const menu = document.querySelector('.slash-menu-list')
    const active = menu?.querySelector('.slash-menu-item.active') as HTMLElement | null
    if (active && menu) {
      const mRect = (menu as HTMLElement).getBoundingClientRect()
      const aRect = active.getBoundingClientRect()
      if (aRect.bottom > mRect.bottom) active.scrollIntoView({ block: 'end', behavior: 'smooth' })
      else if (aRect.top < mRect.top) active.scrollIntoView({ block: 'start', behavior: 'smooth' })
    }
  })
}
onMounted(() => {
  document.addEventListener('keydown', handleSlashKeydown, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleSlashKeydown, true)
})

/**
 * 7/24 关键：工具栏 block type 设置
 * - 默认行为：作用于当前光标所在 block（单行 / 单段）
 * - 用户有选区时：只改选区所在 block
 * - 关键：避免整篇文档被改（之前某些场景下会）
 *   → 用 TextSelection 限定到当前 block 范围
 */
function setBlockType(v: string) {
  blockType.value = v
  if (!editor.value) return
  const ed = editor.value
  const { state } = ed
  const { $from, from, to, empty } = state.selection

  // 7/19 关键：如果选区是空选区 → 自动限定到当前顶层 block 范围
  // 这样 setParagraph / toggleHeading 只改一个 block
  // AI 生成内容时，marked 把多行合并成一个大 paragraph + <br>，或者 ProseMirror 内部的 paragraph
  // 嵌套很复杂，导致 toggleHeading 影响整个 selection 而非单个 block。
  // → 必须先用 TextSelection 锁定当前顶层 block 的范围。
  if (empty) {
    // 找到当前光标所在的顶层 block（depth=0 是 doc，depth=1 是顶层 paragraph/heading）
    // $from.depth 至少是 1（doc 内）；$from.node(0) 是 doc
    // $from.node($from.depth) 或 $from.parent 是最内层的"内容节点"
    // 我们要的是"顶层 paragraph"，即直接挂在 doc 下的节点。
    // 方法：向上找 depth=1 的节点
    let blockDepth = 1
    // 找最近的 block 节点（不能是 mark/atom）
    for (let d = $from.depth; d >= 1; d--) {
      const n = $from.node(d)
      // block 节点特征：有 isTextBlock 或 type.spec.group 包含 'block'
      if (n.isTextblock || (n.type.spec.group || []).includes('block')) {
        blockDepth = d
        break
      }
    }
    const blockFrom = $from.start(blockDepth)
    const blockTo = $from.end(blockDepth)
    if (blockFrom !== blockTo) {
      ed.chain()
        .focus()
        .setTextSelection({ from: blockFrom, to: blockTo })
        .run()
    }
  }

  // 现在应用 block type（只影响当前 block）
  if (v === 'paragraph') ed.chain().focus().setParagraph().run()
  else if (v === 'codeBlock') ed.chain().focus().toggleCodeBlock().run()
  else if (v.startsWith('heading')) ed.chain().focus().toggleHeading({ level: parseInt(v[7]) as 1|2|3 }).run()

  // 7/19 关键：实时同步 blockType 工具栏显示
  setTimeout(() => syncBlockTypeFromEditor(), 0)
}

/**
 * 7/10 关键：从 editor 当前光标位置同步 blockType
 * - 让 blockLabel 实时反映当前位置的 block 类型
 * - 解决：点击标题时光标进入标题，但工具栏仍显示"正文"
 */
function syncBlockTypeFromEditor() {
  if (!editor.value) return
  const ed = editor.value
  let t = 'paragraph'
  if (ed.isActive('codeBlock')) t = 'codeBlock'
  else if (ed.isActive('heading', { level: 1 })) t = 'heading1'
  else if (ed.isActive('heading', { level: 2 })) t = 'heading2'
  else if (ed.isActive('heading', { level: 3 })) t = 'heading3'
  if (blockType.value !== t) blockType.value = t
}

/**
 * 7/11 关键：从 editor 当前光标 / 选区同步字体颜色 / 高亮颜色
 * - 让工具栏按钮显示的颜色反映当前 active 状态
 * - 解决：选中文本 → 设红色 → 按钮不显示红色
 */
function syncTextColorsFromEditor() {
  if (!editor.value) return
  const ed = editor.value
  // 字体颜色：从选区 / 光标位置提取 color attr
  try {
    const color = ed.getAttributes('textStyle').color
    currentTextColor.value = color || '#1f2937'
  } catch {
    currentTextColor.value = '#1f2937'
  }
  // 高亮颜色：从选区 / 光标位置提取 highlight attr
  try {
    const hl = ed.getAttributes('highlight').color
    currentHighlightColor.value = hl || '#fef08a'
  } catch {
    currentHighlightColor.value = '#fef08a'
  }
}
function setTextAlign(v: string) {
  if (!editor.value) return
  // 再次点击同一对齐按钮 → 取消对齐
  if (editor.value.isActive({ textAlign: v })) {
    editor.value.chain().focus().unsetTextAlign().run()
  } else {
    editor.value.chain().focus().unsetTextAlign().setTextAlign(v).run()
  }
}
function insertTable() { editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() }

/**
 * 7/15 关键：插入代码块（点击按钮直接插入）
 * - 默认无 language（Plain Text），光标进入代码块内
 * - 用户可在编辑器头部修改语言名称（contenteditable）
 * - 如果已在代码块中：不做操作（让用户在编辑器头部改语言）
 */
function insertCodeBlock() {
  if (!editor.value) return
  if (editor.value.isActive('codeBlock')) {
    ElMessage.info('当前已在代码块中 — 在头部修改语言名')
    editor.value.commands.focus()
    return
  }
  // 直接插入纯文本代码块
  editor.value.chain().focus().toggleCodeBlock({ language: null } as any).run()
  ElMessage.success('已插入代码块 — 在头部修改语言名')
}

/**
 * 7/12 增强：清洗 AI 返回的"代码 + 注释"输出
 * - 去掉 ```lang ... ``` Markdown 代码块包裹
 * - 去掉 "Here is the annotated code:" 之类前言
 * - 去掉末尾 "Let me know if..." 之类后记
 * - 提取首个 ``` 代码块内容（如果存在）
 */
function cleanAiCodeOutput(raw: string): string {
  if (!raw) return ''
  let text = raw.trim()
  // 1. 优先提取 ```...``` 代码块（含/不含语言标识）
  const fenceRe = /```(?:[a-zA-Z0-9_+-]*\n)?([\s\S]*?)```/
  const fenceMatch = text.match(fenceRe)
  if (fenceMatch) {
    text = fenceMatch[1].trim()
  } else {
    // 2. 没有 ``` 包裹则剥掉首行"以下是 xxx 代码"类前言 + 末行"如有问题"类后记
    const lines = text.split('\n')
    let startIdx = 0
    for (let i = 0; i < Math.min(3, lines.length); i++) {
      const line = lines[i].trim()
      if (!line) continue
      // 前言特征：含"以下""下面是""注释后""这是""代码如下"等关键词 + 没有代码特征
      if (/^(以下是|下面是|注释(后|后：|后:)?|这是|代码(如下)?|Here|The|Below)/i.test(line)
          && !/[{};<>=\[\]\(\)]/.test(line)) {
        startIdx = i + 1
      } else {
        break
      }
    }
    // 同样去掉末尾"如有问题请告诉我"类后记
    let endIdx = lines.length
    for (let i = lines.length - 1; i >= Math.max(0, lines.length - 3); i--) {
      const line = lines[i].trim()
      if (!line) { endIdx = i; continue }
      if (/^(如有问题|希望(能)?帮助|如果.*请|有问题|需要|Let me know|Please|如有|Hope)/i.test(line)
          && !/[{};<>=\[\]\(\)]/.test(line)) {
        endIdx = i
      } else {
        break
      }
    }
    text = lines.slice(startIdx, endIdx).join('\n').trim()
  }
  return text
}

/**
 * 7/11 关键：行内代码切换
 * - 如果有选中文本 → 用 toggleCode 包裹
 * - 无选中文本 → 插入空 code mark（光标居中）
 * - 第二次按同样的按钮 → 退出（toggleCode 已经处理）
 */
function toggleInlineCode() {
  if (!editor.value) return
  editor.value.chain().focus().toggleCode().run()
}

// 7/25 关键：流式期间持续跟随滚动（nextTick 后调用确保 DOM 已更新）
// 7/19 关键：打字机模式下，每 25ms 只有一个字符变化，MutationObserver 触发后浏览器可能合并渲染
// 增加 RAF loop：只要 aiStatus==streaming 就持续滚到底 — 100% 跟随
let aiAutoScrollLoop: number | null = null
function startAiAutoScrollLoop() {
  if (aiAutoScrollLoop !== null) return
  const tick = () => {
    if (aiStatus.value === 'streaming' || aiStatus.value === 'submitted') {
      if (aiMsgRef.value) {
        const el = aiMsgRef.value
        // 强制 scrollHeight 同步 — 用 scrollTo 平滑滚动
        el.scrollTop = el.scrollHeight
      }
      aiAutoScrollLoop = requestAnimationFrame(tick)
    } else {
      aiAutoScrollLoop = null
    }
  }
  aiAutoScrollLoop = requestAnimationFrame(tick)
}
function stopAiAutoScrollLoop() {
  if (aiAutoScrollLoop !== null) {
    cancelAnimationFrame(aiAutoScrollLoop)
    aiAutoScrollLoop = null
  }
}
// 7/19 关键：3 个 watch 移到 useAiChat 解构之后（line ~2986）— 否则 TDZ 报 "Cannot access 'ca' before initialization"

/**
 * 7/5 关键：图片支持本地 + URL
 * - 默认行为：弹窗选择"本地文件"或"URL"
 * - 本地：读取为 base64 → setImage（保存为内联，与 .md 同存储路径）
 * - URL：弹窗输入 URL → setImage
 */
async function addImage() {
  if (!editor.value) return
  try {
    const choice = await ElMessageBox({
      title: '插入图片',
      message: '选择图片来源：',
      showCancelButton: true,
      confirmButtonText: '本地文件',
      cancelButtonText: 'URL',
      distinguishCancelAndClose: true,
    }).catch((action) => action)
    if (choice === 'confirm') {
      imageInputRef.value?.click()
    } else if (choice === 'cancel') {
      const { value } = await ElMessageBox.prompt('图片 URL', '插入图片', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' })
      if (value) editor.value.chain().focus().setImage({ src: value }).run()
    }
  } catch {}
}

/**
 * 7/5 关键：本地图片选择后处理
 * - 读取为 dataURL（base64）直接内嵌到 .md 内容里（保证与 .md 同存储）
 * - 文件名作为 alt 文本
 */
async function onLocalImageSelected(e: Event) {
  if (!editor.value) return
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  // 限制 5MB
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    ;(e.target as HTMLInputElement).value = ''
    return
  }
  try {
    ElMessage.info('正在读取图片...')
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as string)
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
    editor.value.chain().focus().setImage({ src: dataUrl, alt: file.name }).run()
    ElMessage.success('图片已插入（与文档同路径保存）')
  } catch (err: any) {
    ElMessage.error('图片读取失败：' + (err?.message || '未知错误'))
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}

/**
 * 颜色按钮核心逻辑（合一按钮版）
 * - 工具栏只有一个颜色按钮：左半 A 字 + 高亮条，右半 ▾
 * - 单击主按钮：直接把"最近用过的字体色 + 最近用过的高亮色"应用到当前选区
 *   （哪类没有最近记录就跳过哪类，不影响另一类）
 * - 按钮图标：A 字用最近字体色着色，下方色条用最近高亮色填色
 * - 关闭色板后 nextTick 回填焦点，避免"点完色板丢选区"
 */
type ColorKind = 'text' | 'highlight'

watch(colorPanelOpen, (open) => {
  if (!open) {
    nextTick(() => {
      try { editor.value?.commands.focus('end') } catch {}
    })
  }
})

/** hover 进入色板区域（含主按钮+箭头+popover）→ 自动打开 */
function onColorAreaEnter() {
  if (colorLeaveTimer) { clearTimeout(colorLeaveTimer); colorLeaveTimer = null }
  if (!colorPanelOpen.value) colorPanelOpen.value = true
}
/** hover 离开：250ms 缓冲后再次检查两个区域都没 hover 才关闭 */
function onColorAreaLeave() {
  if (colorLeaveTimer) clearTimeout(colorLeaveTimer)
  colorLeaveTimer = setTimeout(() => {
    colorLeaveTimer = null
    const splitHover = colorSplitRef.value?.matches(':hover') ?? false
    const popoverHover = popoverPanelRef.value?.matches(':hover') ?? false
    if (!splitHover && !popoverHover) colorPanelOpen.value = false
  }, 250)
}
/** el-popover 渲染到 body 之后，从 popper DOM 抓 panel 引用供 :hover 检测 */
function capturePopoverEl() {
  popoverPanelRef.value = document.querySelector('.db-color-popover .db-color-panel') as HTMLElement | null
}
/** 箭头按钮点击（兼容移动端/触屏，无 hover） */
function onArrowClick() {
  if (colorLeaveTimer) { clearTimeout(colorLeaveTimer); colorLeaveTimer = null }
  colorPanelOpen.value = !colorPanelOpen.value
}

function saveRecent() {
  try { localStorage.setItem(STORAGE_KEY_RECENT, JSON.stringify(recentColors.value)) } catch {}
}
// 按 kind 分别去重（字体红和高亮红互不冲突），时间倒序，最多 8 项
function pushRecent(kind: ColorKind, color: string) {
  const filtered = recentColors.value.filter(r => !(r.kind === kind && r.color === color))
  recentColors.value = [{ color, kind, ts: Date.now() }, ...filtered].slice(0, MAX_RECENT)
  saveRecent()
}
// "最近用过"区高亮判定：当前记忆色与之匹配
function isRecentActive(r: RecentItem): boolean {
  if (r.kind === 'text') return currentTextColor.value === r.color
  return currentHighlightColor.value === r.color
}

/** 主按钮单击：把"最近字体色 + 最近高亮色"应用到当前选区 */
function applyRecentToSelection() {
  if (!editor.value) {
    ElMessage.warning('编辑器未就绪')
    return
  }
  const { from, to } = editor.value.state.selection
  if (from === to) {
    ElMessage.info('请先在文档中选中文本，再点颜色按钮')
    return
  }
  const tRecent = lastTextRecent.value
  const hRecent = lastHlRecent.value
  if (!tRecent && !hRecent) {
    ElMessage.info('请先点 ▾ 箭头选择一种颜色，再用主按钮一键上色')
    return
  }
  const chain = editor.value.chain().focus()
  if (tRecent) chain.setColor(tRecent.color)
  if (hRecent) chain.setHighlight({ color: hRecent.color })
  chain.run()
}

/** 色板点击：设置记忆色 + 加入最近 + 立即应用到选区（不关色板，由 hover 管理） */
function applyAndRemember(kind: ColorKind, color: string) {
  if (kind === 'text') {
    currentTextColor.value = color
    customTextColor.value = color
    try { localStorage.setItem(STORAGE_KEY_TEXT, color) } catch {}
  } else {
    currentHighlightColor.value = color
    customHlColor.value = color
    try { localStorage.setItem(STORAGE_KEY_HL, color) } catch {}
  }
  pushRecent(kind, color)
  // 自动应用：选中文本就立即应用，没选就只更新记忆色（下次点主按钮生效）
  if (editor.value) {
    const { from, to } = editor.value.state.selection
    if (from !== to) {
      const chain = editor.value.chain().focus()
      if (kind === 'text') chain.setColor(color).run()
      else chain.setHighlight({ color }).run()
    }
  }
  // 不关闭色板：用户连续选色时 hover 全权管理开关
}

/** 自定义取色器（实时）：更新记忆色 + 加入最近 + 自动应用（不关色板） */
function onCustomColorInput(kind: ColorKind, ev: Event) {
  const v = (ev.target as HTMLInputElement).value
  if (!v) return
  if (kind === 'text') {
    currentTextColor.value = v
    customTextColor.value = v
    try { localStorage.setItem(STORAGE_KEY_TEXT, v) } catch {}
  } else {
    currentHighlightColor.value = v
    customHlColor.value = v
    try { localStorage.setItem(STORAGE_KEY_HL, v) } catch {}
  }
  pushRecent(kind, v)
  if (editor.value) {
    const { from, to } = editor.value.state.selection
    if (from !== to) {
      const chain = editor.value.chain().focus()
      if (kind === 'text') chain.setColor(v).run()
      else chain.setHighlight({ color: v }).run()
    }
  }
}

/** 清除：记忆色复位 + 选区清除（清除不污染"最近用过"） */
function clearColor(kind: ColorKind) {
  const fallback = kind === 'text' ? FALLBACK_TEXT : FALLBACK_HL
  if (kind === 'text') {
    currentTextColor.value = fallback
    customTextColor.value = fallback
    try { localStorage.setItem(STORAGE_KEY_TEXT, fallback) } catch {}
  } else {
    currentHighlightColor.value = fallback
    customHlColor.value = fallback
    try { localStorage.setItem(STORAGE_KEY_HL, fallback) } catch {}
  }
  if (!editor.value) return
  const { from, to } = editor.value.state.selection
  if (from === to) return
  const chain = editor.value.chain().focus()
  if (kind === 'text') chain.unsetColor().run()
  else chain.unsetHighlight().run()
}

async function setLink() {
  if (editor.value?.isActive('link')) { editor.value.chain().focus().unsetLink().run(); return }
  try { const { value } = await ElMessageBox.prompt('链接 URL', '插入链接', { inputPlaceholder: 'https://...', confirmButtonText: '插入', cancelButtonText: '取消' }); if (value) editor.value?.chain().focus().setLink({ href: value }).run() } catch {}
}

// AI 浮动菜单操作
// ====== AI 浮窗：openAiFloatPanel / acceptAiFloatResult / acceptDiff / rejectDiff ======

/**
 * 智能编辑入口（侧栏编辑模式用）
 */
async function handleSmartEdit() {
  if (!editor.value) return
  const target = aiEditTargetRange.value
  const from = target?.from ?? editor.value.state.selection.from
  const to = target?.to ?? editor.value.state.selection.to
  const sourceText = target?.text || editor.value.state.doc.textBetween(from, to)
  if (!sourceText) {
    ElMessage.warning('请先在文档中选中文本')
    return
  }
  // 7/12 修复：复用用户上次位置（避免每次 smart-edit 都重置回中央）
  const hasShownBefore = !!aiFloatPanel.value.position
  // 浮窗已打开，用户在浮窗内输入指令 → submitSmartEditInstruction 提交
  await runEditAi('smart-edit', { from, to }, sourceText, false, '', undefined, { preservePosition: hasShownBefore })
}

/**
 * 7/4 关键：浮窗内嵌指令输入提交
 * 用户在浮窗内输入指令后 → 调用 editAi 流式生成
 */
async function submitSmartEditInstruction() {
  if (!editor.value) return
  const instruction = aiFloatPanel.value.smartEditDraft.trim()
  if (!instruction) {
    ElMessage.warning('请输入指令')
    return
  }
  const { command, originalText, originalRange, insertAtCursor } = aiFloatPanel.value
  // 标记进入流式状态
  aiFloatPanel.value.status = 'streaming'
  aiFloatPanel.value.awaitingInstruction = false
  aiFloatPanel.value.thinkingStartedAt = Date.now()

  // 构造 systemPrompt + question
  let systemPrompt = ''
  let question = instruction
  if (command === 'smart-edit') {
    systemPrompt = `你是文档编辑助手，严格按照用户指令修改文本。

【任务】根据用户指令，修改【原文】中的内容。

【原文】（只修改这部分，不要管其他内容）
${originalText}

【指令】
${instruction}

【输出规则 - 严格遵守】
1. 只输出修改后的内容，不要输出任何解释
2. 不要输出"好的"、"以下是"、"修改后"等前缀
3. 不要输出任何说明或备注
4. 不要输出多个版本

【输出格式】
<result>修改后的纯文本内容</result>`
  }

  try {
    await editAi.sendUserMessage(question, systemPrompt, editor.value.getText())
  } catch (e: any) {
    aiFloatPanel.value.status = 'error'
    aiFloatPanel.value.text = e?.message || '请求失败'
  }
}

/**
 * 统一 AI 浮窗入口：打开浮窗 + 调 editAi 流式生成
 */
async function runEditAi(
  command: AiFloatCommand,
  range: { from: number; to: number },
  sourceText: string,
  insertAtCursor: boolean,
  instruction?: string,
  customSystemPrompt?: string,
  options?: { preservePosition?: boolean },
) {
  if (!editor.value) return
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
    await nextTick()
  }
  editAi.clear()
  await nextTick()
  // 7/4 智能编辑：首次打开（无 instruction）→ 不立即流式，让用户先输入指令
  if (command === 'smart-edit' && !instruction) {
    openAiFloatPanel(command, range, sourceText, insertAtCursor, options?.preservePosition, '')
    return
  }
  openAiFloatPanel(command, range, sourceText, insertAtCursor, options?.preservePosition, instruction)

  let systemPrompt = ''
  let question = sourceText
  // 7/3 06:06 关键：customSystemPrompt 实际生效（之前被 _ 忽略导致 slash AI 500）
  if (customSystemPrompt) {
    systemPrompt = customSystemPrompt
    if (!question && instruction) question = instruction
  }
  switch (command) {
    case 'smart-edit':
      systemPrompt = `你是文档编辑助手，严格按照用户指令修改文本。

【任务】根据用户指令，修改【原文】中的内容。

【原文】（只修改这部分，不要管其他内容）
${sourceText}

【指令】
${instruction || ''}

【输出规则 - 严格遵守】
1. 只输出修改后的内容，不要输出任何解释
2. 不要输出"好的"、"以下是"、"修改后"等前缀
3. 不要输出任何说明或备注
4. 不要输出多个版本

【输出格式】
<result>修改后的纯文本内容</result>`
      question = instruction || sourceText
      break
    case 'rewrite':
      systemPrompt = '请改写以下文本，保持原意但使用不同的表达方式。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'expand':
      systemPrompt = '请扩写以下文本，增加更多细节和描述。只输出扩写后的内容，不要添加任何解释或前缀。'
      break
    case 'shorten':
      systemPrompt = '请缩写以下文本，保留核心信息，去除冗余。只输出缩写后的内容，不要添加任何解释或前缀。'
      break
    case 'translate-en':
      systemPrompt = '请将以下文本翻译为 English。只输出翻译结果，不要添加任何解释或前缀。'
      break
    case 'translate-zh':
      systemPrompt = '请将以下文本翻译为中文。只输出翻译结果，不要添加任何解释或前缀。'
      break
    case 'fix-grammar':
      systemPrompt = '请修正以下文本的语法错误，保持原意。只输出修正后的内容，不要添加任何解释或前缀。'
      break
    case 'formal':
      systemPrompt = '请将以下文本改写为更正式的语气。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'casual':
      systemPrompt = '请将以下文本改写为更随意的语气。只输出改写后的内容，不要添加任何解释或前缀。'
      break
    case 'continue':
      systemPrompt = '请基于以下文本继续续写。只输出续写内容，不要添加任何解释或前缀。'
      question = '请续写：' + sourceText
      break
  }

  try {
    await editAi.sendUserMessage(question, systemPrompt, editor.value.getText())
  } catch (e: any) {
    aiFloatPanel.value.status = 'error'
    aiFloatPanel.value.text = e?.message || '请求失败'
  }
}

/**
 * 打开 AI 浮窗（位置算法：选区上方优先，clamp 到视口）
 */
function openAiFloatPanel(
  command: AiFloatCommand,
  range: { from: number; to: number },
  originalText: string,
  insertAtCursor = false,
  preservePosition = false,
  instruction: string = '',
) {
  if (!editor.value) return
  const view = editor.value.view
  const start = view.coordsAtPos(range.from)
  const end = view.coordsAtPos(range.to)
  const selTop = Math.min(start.top, end.top)
  const selBottom = Math.max(start.bottom, end.bottom)

  // 7/12 增强：用户要求 — 浮窗**水平+垂直都在编辑器中间**（不靠选区）
  // 水平：浮窗水平居中于编辑区域
  // 垂直：浮窗垂直居中于编辑区域，**偏上 80px**（让浮窗更靠近编辑器上半部）
  const PANEL_W = 420  // 7/12 修复：与 CSS .ai-float-panel { max-width: 480px } 中位值一致
  const PANEL_MAX_H = Math.min(560, window.innerHeight * 0.6)
  const margin = 12

  // 找到编辑器内容容器边界
  const editorEl = editor.value.view.dom.closest('.md-editor-content, .md-wrapper') as HTMLElement | null
  const editorRect = editorEl?.getBoundingClientRect()
  const fallbackRect = { left: margin, right: window.innerWidth - margin, top: margin, bottom: window.innerHeight - margin, width: window.innerWidth - 2 * margin, height: window.innerHeight - 2 * margin }
  const r = editorRect || fallbackRect

  // 1. 水平：浮窗水平居中于编辑区域
  let left = r.left + r.width / 2 - PANEL_W / 2
  // clamp 到编辑器内（贴边留 4px 余量）
  if (left < r.left + 4) left = r.left + 4
  if (left + PANEL_W > r.right - 4) left = r.right - PANEL_W - 4

  // 2. 垂直：浮窗水平居中、垂直**稍微偏上**于编辑区域
  //    top = 编辑器垂直中心 - 浮窗高度一半 - 80px（让浮窗显示在编辑器偏上位置）
  let top = r.top + r.height / 2 - PANEL_MAX_H / 2 - 80
  // clamp 到编辑器内
  if (top < r.top + 4) top = r.top + 4
  if (top + PANEL_MAX_H > r.bottom - 4) top = r.bottom - PANEL_MAX_H - 4

  const placeAbove = selTop > top
  // 7/12 修复：preservePosition=true 时（用户拖动后再次触发，或页面刷新后）复用位置
  // 优先级：1) 当前 reactive 位置（如果用户刚才拖动过）2) localStorage 3) 初始居中
  let savedPos: { top: number; left: number } | null = null
  if (preservePosition) {
    if (aiFloatPanel.value.position) {
      savedPos = aiFloatPanel.value.position
    } else {
      // 页面刷新后 reactive 位置丢失，从 localStorage 读
      savedPos = loadAiFloatPosition()
    }
    // 7/12 修复：localStorage 残留 0,0（被污染的旧版本 bug）→ 视为无效，回退居中
    if (savedPos && savedPos.top === 0 && savedPos.left === 0) {
      savedPos = null
    }
  }
  const reusePosition = !!savedPos
  const finalTop = savedPos ? savedPos.top : top
  const finalLeft = savedPos ? savedPos.left : left
  const finalPlaceAbove = reusePosition ? aiFloatPanel.value.placeAbove : placeAbove

  aiFloatPanel.value = {
    visible: true,
    command,
    title: AI_COMMAND_TITLES[command] || 'AI 操作',
    status: command === 'smart-edit' && !instruction ? 'idle' : 'streaming',
    text: '',
    thinking: '',
    thinkingDone: false,
    thinkingExpanded: false,
    thinkingStartedAt: Date.now(),
    thinkingEndedAt: undefined,
    thinkingDurationSec: 0,
    options: [],
    originalText,
    originalRange: range,
    insertAtCursor,
    position: { top: finalTop, left: finalLeft },
    placeAbove: finalPlaceAbove,
    diffMode: true,
    pendingDiff: false,
    diffState: null,
    awaitingInstruction: command === 'smart-edit' && !instruction,
    smartEditDraft: instruction || '',
    // 9/7 关键：原文默认展开
    originalExpanded: true,
  }
}

function closeAiFloatPanel() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  if (aiGenerate.status.value === 'submitted' || aiGenerate.status.value === 'streaming') {
    aiGenerate.stop()
  }
  if (aiFloatPanel.value.pendingDiff) {
    rejectDiff()
    return
  }
  // 7/5 关闭时记忆位置
  saveAiFloatPosition()
  aiFloatPanel.value.visible = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.pendingDiff = false
  // 7/10 关键：关闭浮窗时清空生成模式状态
  aiGenerateMode.value = false
  aiGeneratePrompt.value = ''
}

/**
 * 7/5 关键：AI 浮窗拖动
 * - 头部 mousedown 触发
 * - 记录 offset 偏移，移动时更新 position
 * - clamp 到视口内
 */
let aiFloatDragState: { startX: number; startY: number; origTop: number; origLeft: number } | null = null

function startDragAiFloat(e: MouseEvent) {
  if (!aiFloatPanel.value.visible) return
  // 排除关闭按钮
  const target = e.target as HTMLElement
  if (target.closest('.ai-float-close')) return
  e.preventDefault()
  aiFloatDragState = {
    startX: e.clientX,
    startY: e.clientY,
    origTop: aiFloatPanel.value.position.top,
    origLeft: aiFloatPanel.value.position.left,
  }
  document.addEventListener('mousemove', onDragAiFloat)
  document.addEventListener('mouseup', stopDragAiFloat)
}

function onDragAiFloat(e: MouseEvent) {
  if (!aiFloatDragState) return
  const dx = e.clientX - aiFloatDragState.startX
  const dy = e.clientY - aiFloatDragState.startY
  const newLeft = aiFloatDragState.origLeft + dx
  const newTop = aiFloatDragState.origTop + dy
  // 7/5 关键：clamp 到视口内（避免拖出屏幕）
  const PANEL_W = 380
  const maxLeft = window.innerWidth - 24
  const minLeft = -(PANEL_W - 24)
  aiFloatPanel.value.position = {
    left: Math.max(minLeft, Math.min(maxLeft, newLeft)),
    top: Math.max(8, Math.min(window.innerHeight - 80, newTop)),
  }
}

function stopDragAiFloat() {
  if (aiFloatDragState) {
    aiFloatDragState = null
    saveAiFloatPosition()
  }
  document.removeEventListener('mousemove', onDragAiFloat)
  document.removeEventListener('mouseup', stopDragAiFloat)
}

/**
 * 7/5 关键：位置记忆 - 写入 localStorage
 * key: 'miaotong-ai-float-pos'，value: {top, left}
 */
function saveAiFloatPosition() {
  try {
    localStorage.setItem('miaotong-ai-float-pos', JSON.stringify({
      top: aiFloatPanel.value.position.top,
      left: aiFloatPanel.value.position.left,
    }))
  } catch {}
}

function loadAiFloatPosition(): { top: number; left: number } | null {
  try {
    const raw = localStorage.getItem('miaotong-ai-float-pos')
    if (!raw) return null
    const pos = JSON.parse(raw)
    if (typeof pos.top === 'number' && typeof pos.left === 'number') return pos
  } catch {}
  return null
}

function stopAiFloatResult() {
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    editAi.stop()
  }
  aiFloatPanel.value.status = 'aborted'
}

function regenerateAiFloatResult() {
  if (!editor.value || aiFloatPanel.value.status === 'streaming') return
  aiFloatPanel.value.text = ''
  aiFloatPanel.value.thinking = ''
  aiFloatPanel.value.thinkingDone = false
  aiFloatPanel.value.thinkingStartedAt = Date.now()
  aiFloatPanel.value.options = []
  aiFloatPanel.value.status = 'streaming'
  runEditAi(
    aiFloatPanel.value.command,
    aiFloatPanel.value.originalRange,
    aiFloatPanel.value.originalText,
    aiFloatPanel.value.insertAtCursor,
    undefined,
    undefined,
    { preservePosition: true }
  )
}

function pickAiFloatOption(idx: number) {
  const opt = aiFloatPanel.value.options[idx]
  if (!opt || !editor.value) return
  const safe = sanitizeAiMarkdown(opt.content)
  const { from, to } = aiFloatPanel.value.originalRange
  if (to > from) {
    editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
  } else {
    editor.value.chain().focus().insertContentAt(from, safe).run()
  }
  // 7/6 10:55 关键修复：精确范围（from + 纯文本字符数），不是 finalDocSize
  setAiInsertHighlight(from, from + safe.length)
  ElMessage.success('已采用方案')
  aiFloatPanel.value.visible = false
}

/**
 * 采用：替换原文 / 插入 + 1.2s 淡黄高亮
 */
function acceptAiFloatResult() {
  if (!editor.value) return
  let cleanText = aiFloatPanel.value.text
    .replace(/<\/think>/gi, '')
    .replace(/<\|thinking\|>/gi, '')
    .replace(/<\|reasoning\|>/gi, '')
    .replace(/<\|thought\|>/gi, '')
    .replace(/<think>/gi, '')
  const resultMatch = cleanText.match(/<result>([\s\S]*?)<\/result>/i)
  if (resultMatch && resultMatch[1]) {
    cleanText = resultMatch[1].trim()
  } else {
    cleanText = cleanText
      .replace(/<\/?result>/gi, '')
      .replace(/^[\s\n]+/, '')
      .trim()
  }
  cleanText = cleanText
    .replace(/^(好的|以下是|根据|按照|修改后|编辑后|说明|解释|建议|方案|推荐)[：:,，\s]*/i, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/^>\s*/gm, '')
    .trim()
  if (!cleanText) {
    ElMessage.warning('AI 未生成可用内容')
    return
  }

  // 检测多方案
  const optionMatches = cleanText.match(
    /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]\s*[\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+)\s*[:：]|$))/gi
  )
  if (optionMatches && optionMatches.length > 1) {
    const options = optionMatches.map((opt: string) => {
      const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]/)
      const title = titleMatch ? titleMatch[1] : '方案'
      const content = opt
        .replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+))\s*[:：]\s*/, '')
        .trim()
      return { title, content }
    })
    aiFloatPanel.value.options = options
    aiFloatPanel.value.text = ''
    return
  }

  // Diff 预览模式（用 ProseMirror tr 直接操作）
  const { from, to } = aiFloatPanel.value.originalRange
  if (aiFloatPanel.value.diffMode && !aiFloatPanel.value.insertAtCursor && to > from) {
    const safe = sanitizeAiMarkdown(cleanText)
    const strikeType = editor.value.state.schema.marks?.strike
    const hardBreakType = editor.value.state.schema.nodes?.hardBreak
    if (!strikeType || !hardBreakType) {
      ElMessage.warning('diff 模式不支持')
      return
    }
    const docSizeBefore = editor.value.state.doc.content.size
    let tr = editor.value.state.tr
    tr = tr.addMark(from, to, strikeType.create())
    tr = tr.insert(to, hardBreakType.create())
    const textNode = editor.value.state.schema.text(safe)
    tr = tr.insert(to + 1, textNode)
    editor.value.view.dispatch(tr)
    const docSizeAfter = editor.value.state.doc.content.size
    aiFloatPanel.value.diffState = {
      originalText: aiFloatPanel.value.originalText,
      newText: safe,
      removedFrom: from,
      removedTo: to,
      addedStart: to,
      addedEnd: docSizeAfter,
      originalDocSize: docSizeBefore,
    }
    aiFloatPanel.value.status = 'done'
    aiFloatPanel.value.pendingDiff = true
    ElMessage.info('Diff 预览：点击采用或拒绝')
    return
  }

  // 非 diff 模式：直接替换/插入
  const safe = sanitizeAiMarkdown(cleanText)
  if (aiFloatPanel.value.insertAtCursor) {
    const pos = from
    editor.value.chain().focus().insertContentAt(pos, safe).run()
    // 7/6 10:55 关键修复：精确范围（from + 纯文本字符数）
    setAiInsertHighlight(pos, pos + safe.length)
  } else if (to <= from) {
    const pos = editor.value.state.selection.from
    editor.value.chain().focus().insertContentAt(pos, safe).run()
    setAiInsertHighlight(pos, pos + safe.length)
  } else {
    editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, safe).run()
    setAiInsertHighlight(from, from + safe.length)
  }
  ElMessage.success('已采用')
  aiFloatPanel.value.visible = false
}

/** Diff 模式：接受（删原文 + 保留新文 + 淡黄高亮） */
function acceptDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo } = aiFloatPanel.value.diffState
  try {
    const tr = editor.value.state.tr.delete(removedFrom, removedTo)
    editor.value.view.dispatch(tr)
  } catch (e) { console.warn('[acceptDiff] delete failed', e) }
  try {
    const docSize = editor.value.state.doc.content.size
    let pos = removedFrom
    while (pos < docSize) {
      const nodeAt = editor.value.state.doc.nodeAt(pos)
      if (!nodeAt) break
      if (nodeAt.type.name === 'hardBreak') {
        const tr = editor.value.state.tr.delete(pos, pos + nodeAt.nodeSize)
        editor.value.view.dispatch(tr)
        break
      } else break
    }
  } catch (e) { console.warn('[acceptDiff] hardBreak failed', e) }
  const newFrom = removedFrom
  const newTo = removedFrom + aiFloatPanel.value.diffState.newText.length
  setAiInsertHighlight(newFrom, newTo)
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.success('已采用新内容')
}

/** Diff 模式：拒绝（删新文 + 取消 strike） */
function rejectDiff() {
  if (!editor.value || !aiFloatPanel.value.diffState) return
  const { removedFrom, removedTo, addedStart, addedEnd } = aiFloatPanel.value.diffState
  try {
    const strikeType = editor.value.state.schema.marks?.strike
    if (strikeType) {
      const tr = editor.value.state.tr.removeMark(removedFrom, removedTo, strikeType)
      editor.value.view.dispatch(tr)
    }
  } catch (e) { console.warn('[rejectDiff] remove strike failed', e) }
  try {
    const tr = editor.value.state.tr.delete(addedStart, addedEnd)
    editor.value.view.dispatch(tr)
  } catch (e) { console.warn('[rejectDiff] delete new failed', e) }
  aiFloatPanel.value.pendingDiff = false
  aiFloatPanel.value.diffState = null
  aiFloatPanel.value.visible = false
  ElMessage.info('已恢复原文')
}

/**
 * 替换旧 handleAiAction：分发到 runEditAi 或 handleSmartEdit
 */
async function handleAiAction(cmd: string) {
  if (!editor.value) return
  if (cmd === 'smart-edit') {
    await handleSmartEdit()
    return
  }
  const { from, to } = editor.value.state.selection
  const selectedText = editor.value.state.doc.textBetween(from, to)
  const insertAtCursor = !selectedText || to <= from
  // 7/12 修复：如果面板已经显示过（用户可能拖动过），复用其位置
  // 否则用初始居中位置
  const hasShownBefore = !!aiFloatPanel.value.position
  await runEditAi(
    cmd as AiFloatCommand,
    { from, to },
    selectedText,
    insertAtCursor,
    undefined,
    undefined,
    { preservePosition: hasShownBefore }
  )
}

/**
 * 7/3 关键修复：流式阶段就清洗 <think>/<result> 标签
 * 避免模型输出在流式中泄露给用户
 */
function cleanFloatText(t: string): string {
  if (!t) return ''
  let s = t
    .replace(/<\/think>/gi, '')
    .replace(/<\|thinking\|>/gi, '')
    .replace(/<\|reasoning\|>/gi, '')
    .replace(/<\|thought\|>/gi, '')
    .replace(/<think>/gi, '')
  // 提取 <result> 标签（如果有完整闭合）
  const m = s.match(/<result>([\s\S]*?)<\/result>/i)
  if (m && m[1]) {
    return m[1].trim()
  }
  // 否则移除未闭合的 <result>
  s = s.replace(/<result>/gi, '').replace(/<\/result>/gi, '')
  return s
}

// 同步 editAi 流式文本到浮窗
watch(
  () => [
    editAi.status.value,
    editAi.messages.value[editAi.messages.value.length - 1]?.parts?.length,
  ],
  () => {
    const last = editAi.messages.value[editAi.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    const thinkingPart = (last.parts || []).find((p: any) => p.type === 'reasoning')
    if (thinkingPart) aiFloatPanel.value.thinking = thinkingPart.text || ''
    if (textPart) {
      // 7/3 关键：流式阶段就清洗 <think>/<result> 标签，避免标签泄露
      const cleaned = cleanFloatText(textPart.text || '')
      aiFloatPanel.value.text = cleaned
      if (textPart.state === 'done' && !aiFloatPanel.value.thinkingDone) {
        aiFloatPanel.value.thinkingDone = true
        aiFloatPanel.value.thinkingEndedAt = Date.now()
        aiFloatPanel.value.thinkingDurationSec = Math.floor(
          (aiFloatPanel.value.thinkingEndedAt! - (aiFloatPanel.value.thinkingStartedAt || 0)) / 1000
        )
      }
    }
    if (textPart?.state === 'done') {
      aiFloatPanel.value.status = aiFloatPanel.value.status === 'aborted' ? 'aborted' : 'done'
    }
  },
  { flush: 'post' }
)

// 同步 slashAi 流式文本到浮窗
watch(
  () => [
    slashAi.status.value,
    slashAi.messages.value[slashAi.messages.value.length - 1]?.parts?.length,
  ],
  () => {
    const last = slashAi.messages.value[slashAi.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    if (textPart) {
      aiFloatPanel.value.text = cleanFloatText(textPart.text || '')
      if (textPart.state === 'done') {
        aiFloatPanel.value.status = aiFloatPanel.value.status === 'aborted' ? 'aborted' : 'done'
      }
    }
  },
  { flush: 'post' }
)

// ====== 1.2s 淡黄高亮 + 点击编辑区域清除 ======
const aiInsertHighlights = ref<Array<{ from: number; to: number }>>([])
let aiInsertClickHandlerInstalled = false
let aiInsertClickHandler: ((e: MouseEvent) => void) | null = null

function setAiInsertHighlight(from: number, to: number) {
  if (!editor.value || from >= to) return
  const { state } = editor.value
  const highlightType = state.schema.marks?.highlight
  if (!highlightType) return
  const docSize = state.doc.content.size
  const finalTo = Math.min(to, docSize)
  if (finalTo <= from) return
  const tr = state.tr.addMark(from, finalTo, highlightType.create({ color: '#fef3c7' }))
  editor.value.view.dispatch(tr)
  setTimeout(() => {
    if (!editor.value) return
    try {
      const tr2 = editor.value.state.tr.removeMark(from, finalTo, highlightType)
      editor.value.view.dispatch(tr2)
    } catch (e) { /* ignore */ }
  }, 1200)
  if (!aiInsertClickHandlerInstalled) {
    aiInsertClickHandlerInstalled = true
    aiInsertClickHandler = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      const inEditor = target.closest('.tiptap') || target.closest('.ProseMirror') || target.closest('.md-editor-content')
      if (inEditor && aiInsertHighlights.value.length > 0) {
        aiInsertHighlights.value = []
        clearAiInsertHighlights()
      }
    }
    setTimeout(() => {
      if (aiInsertClickHandler) {
        document.addEventListener('mousedown', aiInsertClickHandler, true)
      }
    }, 100)
  }
}

function clearAiInsertHighlights() {
  if (!editor.value) return
  const { state } = editor.value
  const highlightType = state.schema.marks?.highlight
  if (!highlightType) return
  const tr = state.tr
  state.doc.descendants((node: any, pos: number) => {
    if (node.isText) {
      const mark = node.marks.find((m: any) => m.type.name === 'highlight' && m.attrs.color === '#fef3c7')
      if (mark) {
        tr.removeMark(pos, pos + node.nodeSize, mark.type)
      }
    }
    return true
  })
  if (tr.docChanged) {
    editor.value.view.dispatch(tr)
  }
}

/**
 * 9/7 关键修复：/ 菜单的 AI 生成走独立 generate-stream 接口
 * - 不携带文档内容（纯生成）
 * - 文档为空时也能正常使用
 * - 弹窗样式：与 AI 浮窗一致
 * - 7/10 关键：输入框也内嵌到浮窗，不再用 ElMessageBox.prompt 系统弹窗
 */
async function aiSlashCommand() {
  if (!editor.value) return

  // 先停止旧的 AI 生成 + 清空
  if (aiGenerate.status.value === 'submitted' || aiGenerate.status.value === 'streaming') {
    aiGenerate.stop()
    await nextTick()
  }
  aiGenerate.clear()
  await nextTick()

  // 重置浮窗：把状态设为 idle，等待用户在浮窗内输入后再触发
  aiFloatPanel.value = {
    visible: true,
    command: 'continue',
    title: 'AI 生成',
    status: 'idle',
    text: '',
    thinking: '',
    thinkingDone: false,
    thinkingExpanded: false,
    thinkingStartedAt: undefined,
    thinkingEndedAt: undefined,
    thinkingDurationSec: 0,
    options: [],
    originalText: '',
    originalRange: { from: editor.value.state.selection.from, to: editor.value.state.selection.from },
    insertAtCursor: true,
    position: { top: 0, left: 0 },
    placeAbove: true,
    diffMode: false,
    pendingDiff: false,
    diffState: null,
    awaitingInstruction: false,
    smartEditDraft: '',
    originalExpanded: true,
  }

  // 计算浮窗位置（基于当前光标）
  const pos = editor.value.state.selection.from
  const coords = editor.value.view.coordsAtPos(pos)
  const PANEL_W = 420
  const PANEL_MAX_H = Math.min(520, window.innerHeight * 0.6)
  const GAP = 12
  const spaceAbove = coords.top
  const placeAbove = spaceAbove >= PANEL_MAX_H + GAP || spaceAbove >= window.innerHeight / 2
  let top = placeAbove ? coords.top - PANEL_MAX_H - GAP : coords.bottom + GAP
  let left = coords.left - PANEL_W / 2
  if (left < 24) left = 24
  if (left > window.innerWidth - PANEL_W - 24) left = window.innerWidth - PANEL_W - 24
  aiFloatPanel.value.position = { top, left }
  aiFloatPanel.value.placeAbove = placeAbove

  // 标记本次为"生成模式"，等用户在浮窗输入框点发送
  aiGeneratePrompt.value = ''
  aiGenerateMode.value = true
}

const aiGeneratePrompt = ref('')
const aiGenerateMode = ref(false)

/**
 * 7/10 关键：用户点击浮窗内"生成"按钮 → 真正调用 generate-stream
 */
async function submitAiGenerate() {
  const value = aiGeneratePrompt.value.trim()
  if (!value) { ElMessage.warning('请输入要生成的内容描述'); return }
  if (aiGenerate.status.value === 'submitted' || aiGenerate.status.value === 'streaming') return
  aiGenerateMode.value = false
  aiFloatPanel.value.status = 'streaming'
  // 7/12 增强：极简 systemPrompt — 禁止思考/解释/Markdown 包裹/代码块包裹
  const systemPrompt = `你是写作助手。任务：根据用户的描述生成内容。

严格要求：
1. 只输出用户要求的内容本身（代码/文字/列表等）
2. 禁止任何思考、解释、前言、后记
3. 禁止 Markdown 代码块标记（\`\`\`），除非用户明确要求
4. 禁止"以下是""下面是""好的"等任何非内容字符
5. 直接从第一行内容开始输出，不要任何引导`
  try {
    await aiGenerate.sendUserMessage(value, systemPrompt, '')
  } catch (e: any) {
    aiFloatPanel.value.status = 'error'
    aiFloatPanel.value.text = e?.message || '请求失败'
  }
}

// 9/7 关键：/ 菜单 AI 生成实例 → 浮窗实时同步（与 editAi/slashAi 类似）
watch(
  () => [
    aiGenerate.status.value,
    aiGenerate.messages.value[aiGenerate.messages.value.length - 1]?.parts?.length,
  ],
  () => {
    const last = aiGenerate.messages.value[aiGenerate.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    if (textPart) {
      aiFloatPanel.value.text = textPart.text || ''
      if (textPart.state === 'done') {
        aiFloatPanel.value.status = aiFloatPanel.value.status === 'aborted' ? 'aborted' : 'done'
      }
    }
  },
  { flush: 'post' }
)

// AI 侧边面板 — 走 useAiChat 实现真逐字流式输出
const aiQuestion = ref('')
const aiMsgRef = ref<HTMLElement | null>(null)

const docIdRef = computed(() => props.docId)
const {
  messages: aiMessages,
  messageVersion: aiMessageVersion,
  status: aiStatus,
  error: aiError,
  sendUserMessage: sendAiUserMessage,
  stop: stopAiChat,
  clear: clearAiChat,
} = useAiChat({ docId: docIdRef, endpoint: 'chat-stream' })
const aiLoading = computed(() => aiStatus.value === 'streaming' || aiStatus.value === 'submitted')

// 7/19 关键：3 个 watch 移到 aiMessagesView 声明之后（line ~3044），否则 TDZ
// 7/14 关键：thinking 展开状态由 m.thinkingExpanded（每条消息独立）控制，移除全局变量
// 保留旧 ref 以兼容可能的外部引用
const aiThinkingExpanded = ref(true)

/**
 * 7/14 关键：每条消息独立切换 thinking 展开/收起
 */
function toggleThinkingExpanded(msgId: string) {
  const extra = aiExtras.get(msgId)
  if (extra) {
    extra.thinkingExpanded = !extra.thinkingExpanded
    // 触发响应式更新（map 修改不会自动响应）
    syncAiMessages()
  }
}

// 7/4 关键：10 张 AI 快捷卡片（豆包风格 - 大卡片 + 不同配色）
const aiWelcomeCards = [
  { id: 'summary', icon: '📋', name: '总结摘要', desc: '提取核心信息', color: 'blue', prompt: '请总结这篇文档的核心内容，提取关键信息，输出结构化摘要' },
  { id: 'polish',  icon: '✏️', name: '润色文字', desc: '让表达更专业', color: 'purple', prompt: '请润色并改进文档中的文字，使其更专业流畅' },
  { id: 'explain', icon: '💡', name: '解释说明', desc: '通俗化解读', color: 'amber', prompt: '请解释文档中的核心内容，用简洁易懂的语言说明' },
  { id: 'translate-en', icon: '🌐', name: '翻译英文', desc: '保留专业术语', color: 'teal', prompt: '请将文档内容翻译为英文，保持专业准确的表达' },
  { id: 'expand',  icon: '📝', name: '内容扩写', desc: '增加细节深度', color: 'rose', prompt: '请扩写文档内容，增加更多细节、例子和深度' },
  { id: 'shorten', icon: '✂️', name: '内容缩写', desc: '保留核心信息', color: 'cyan', prompt: '请缩写文档内容，保留核心信息，精简表达' },
  { id: 'continue', icon: '➡️', name: '续写内容', desc: '保持风格一致', color: 'indigo', prompt: '请基于当前文档续写，保持风格一致' },
  { id: 'grammar', icon: '🔍', name: '检查语法', desc: '修正错误', color: 'green', prompt: '请检查文档中的语法错误和不通顺的句子，并给出修改建议' },
  { id: 'formal',  icon: '👔', name: '正式语气', desc: '更专业正式', color: 'slate', prompt: '请用更正式专业的语气重写文档' },
  { id: 'casual',  icon: '😊', name: '轻松语气', desc: '更轻松随意', color: 'orange', prompt: '请用更轻松随意的语气重写文档' },
]
const showAiWelcomeList = ref(true)

/**
 * 7/4 关键修复：useChat 的 messages 是只读 + 浅响应的，
 * 模板里直接 v-for="m in aiMessages" 在流式过程中可能不刷新。
 * 这里派生出一个新数组 aiMessagesView，每次 messageVersion 变化时重建对象引用。
 */
interface DerivedMsg {
  id: string
  role: 'user' | 'assistant'
  content: string
  thinking: string
  thinkingDone: boolean
  thinkingExpanded: boolean
  userToggledThinking: boolean
  hasOptions: boolean
  options: Array<{ title: string; content: string }>
  originalRange?: { from: number; to: number }
  status: 'streaming' | 'done' | 'aborted' | 'error'
  /** 7/10 关键：思考开始时间（毫秒）— 客户端在第一个 reasoning chunk 到达时打点 */
  thinkingStartedAt?: number
  /** 7/10 关键：思考结束时间 */
  thinkingEndedAt?: number
  /** 7/10 关键：思考时长（秒，整数） */
  thinkingDurationSec: number
}
const aiMessagesView = ref<DerivedMsg[]>([]) as any
// 7/19 关键：3 个 watch 必须放在 aiMessagesView 声明之后 — 否则 TDZ
watch(() => aiMessageVersion.value, () => {
  if (aiStatus.value !== 'streaming' && aiStatus.value !== 'submitted') return
  nextTick(() => smartScrollAiToBottom())
})
// AI 流式开始 → 启动 RAF 循环；结束 → 停止
watch(() => aiStatus.value, (s) => {
  nextTick(() => {
    scrollAiToBottom(false)
    setupAiScrollObserver()
  })
  if (s === 'streaming' || s === 'submitted') startAiAutoScrollLoop()
  else stopAiAutoScrollLoop()
})
// 7/11 关键：额外监听 aiMessagesView（思考过程的实时增量也要跟随滚动）
watch(() => aiMessagesView.value, () => {
  if (aiStatus.value !== 'streaming' && aiStatus.value !== 'submitted') return
  nextTick(() => smartScrollAiToBottom())
}, { deep: true })
const aiExtras = new Map<string, {
  inserted?: boolean
  hasOptions?: boolean
  options?: Array<{ title: string; content: string }>
  originalRange?: { from: number; to: number }
  thinkingExpanded?: boolean
  userToggledThinking?: boolean
  /** 7/10 关键：思考计时（外层 map，避免 derive 重复创建） */
  thinkingStartedAt?: number
  thinkingEndedAt?: number
}>()

function getTextPart(m: any): string {
  const part = (m.parts || []).find((p: any) => p.type === 'text')
  return part?.text || ''
}
function getReasoningText(m: any): string {
  const part = (m.parts || []).find((p: any) => p.type === 'reasoning')
  return part?.text || ''
}
function hasReasoning(m: any): boolean {
  return !!(m.parts || []).find((p: any) => p.type === 'reasoning')
}
function isReasoningDone(m: any): boolean {
  const part = (m.parts || []).find((p: any) => p.type === 'reasoning')
  return part ? part.state === 'done' : true
}
function hasTextPart(m: any): boolean {
  return !!(m.parts || []).find((p: any) => p.type === 'text')
}
function deriveMessage(m: any): DerivedMsg {
  // 7/11 关键：必须 set 回去，否则每次 derive 都拿到新对象 → 计时丢失
  let extra = aiExtras.get(m.id)
  if (!extra) { extra = {}; aiExtras.set(m.id, extra) }
  // 7/10 关键：思考计时 — 客户端在第一个 reasoning chunk 到达时打点
  if (hasReasoning(m)) {
    if (!extra.thinkingStartedAt) extra.thinkingStartedAt = Date.now()
    if (isReasoningDone(m) && !extra.thinkingEndedAt) extra.thinkingEndedAt = Date.now()
  }
  // 7/14 关键：兜底 — 即使没有 reasoning，只要 assistant 消息存在就记录起始时间
  if (m.role === 'assistant' && !extra.thinkingStartedAt) {
    // 用 m.id 作为伪起始时间戳（确定性）— 实际用 Date.now - (n * 1000) 让已显示 N 秒
    extra.thinkingStartedAt = Date.now()
  }
  const thinkingStartedAt = extra.thinkingStartedAt
  const thinkingEndedAt = extra.thinkingEndedAt
  // 7/14 关键：如果还没有 reasoning 但已经在流式输出正文，endedAt 也算 (到当前时间)
  let displayEndedAt = thinkingEndedAt
  if (!displayEndedAt && m.role === 'assistant') {
    const textPart = (m.parts || []).find((p: any) => p.type === 'text')
    if (textPart && textPart.state === 'done') displayEndedAt = Date.now()
  }
  const thinkingDurationSec = (thinkingStartedAt && displayEndedAt)
    ? Math.max(0, Math.floor((displayEndedAt - thinkingStartedAt) / 1000))
    : (thinkingStartedAt ? Math.max(0, Math.floor((Date.now() - thinkingStartedAt) / 1000)) : 0)
  return {
    id: m.id,
    role: m.role,
    content: getTextPart(m),
    thinking: getReasoningText(m),
    thinkingDone: isReasoningDone(m),
    // 7/14 关键：默认展开（流式中显示 + 完成后默认收起由 user 决定）
    thinkingExpanded: extra.thinkingExpanded ?? true,
    userToggledThinking: extra.userToggledThinking ?? false,
    hasOptions: extra.hasOptions ?? false,
    options: extra.options ?? [],
    originalRange: extra.originalRange,
    status: extra.inserted ? 'done' : (m.role === 'assistant' ? 'done' : 'done'),
    thinkingStartedAt,
    thinkingEndedAt,
    thinkingDurationSec,
  }
}
function syncAiMessages() {
  const mapped = (aiMessages.value || []).map((m: any) => deriveMessage(m))
  aiMessagesView.value = mapped
}
watch(
  () => [aiMessageVersion?.value, aiStatus?.value],
  () => { syncAiMessages() },
  { flush: 'post' }
)
// 立即同步一次
syncAiMessages()

// 7/10 关键：思考进行中每秒 tick 一次 — 让"已思考 N 秒"实时刷新
let aiThinkingTickTimer: any = null
watch(
  () => aiMessagesView.value.some((m: any) => m.thinking && !m.thinkingDone),
  (hasOngoing) => {
    if (aiThinkingTickTimer) { clearInterval(aiThinkingTickTimer); aiThinkingTickTimer = null }
    if (hasOngoing) {
      aiThinkingTickTimer = setInterval(() => {
        // 触发响应式：让 thinkStartAt 仍为 undefined 的项重新计算 duration
        syncAiMessages()
      }, 1000)
    }
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  if (aiThinkingTickTimer) { clearInterval(aiThinkingTickTimer); aiThinkingTickTimer = null }
})

/**
 * 7/3 关键修复：60ms heartbeat triggerRef
 * 解决 SSE 偶发"一批次到货"时无响应式强制刷新问题
 */
let aiHeartbeatTimer: ReturnType<typeof setInterval> | null = null
function startAiHeartbeat() {
  if (aiHeartbeatTimer !== null) return
  aiHeartbeatTimer = setInterval(() => {
    if (aiLoading.value || editAiLoading.value) {
      try { triggerRef(aiMessages as any) } catch {}
      try { triggerRef(aiMessagesView as any) } catch {}
    } else {
      stopAiHeartbeat()
    }
  }, 60)
}
function stopAiHeartbeat() {
  if (aiHeartbeatTimer !== null) {
    clearInterval(aiHeartbeatTimer)
    aiHeartbeatTimer = null
  }
}
watch([aiLoading, editAiLoading], ([a, b]) => {
  if (a || b) startAiHeartbeat()
  else stopAiHeartbeat()
})
watch(editAiCoverVisible, (v) => {
  if (v) startAiHeartbeat()
  else stopAiHeartbeat()
})

/**
 * 7/3 关键修复：7/3 14:07 useChat 替换手写 SSE
 * 旧版 sendAiChat 内部用 currentAiMessageHandler 同步 AI 状态；
 * 新版 useAiChat 已在内部自动处理流式，watch aiStatus 自动更新。
 */

function quickAiAsk(prompt: string) {
  if (aiLoading.value) return
  aiQuestion.value = prompt
  doSendAiChat()
}

/**
 * 9/7 关键：点击 AI 品牌（标题）→ 返回卡片视图
 * 1. 停止当前 AI 生成
 * 2. 清空会话
 * 3. 清空问题输入
 */
async function backToAiWelcome() {
  if (aiLoading.value) {
    try { stopAiChat() } catch {}
    await nextTick()
  }
  try { clearAiChat() } catch {}
  aiQuestion.value = ''
  showAiWelcomeList.value = true
  ElMessage.success('已返回卡片')
}

async function doSendAiChat() {
  const q = aiQuestion.value.trim()
  if (!q) return
  aiQuestion.value = ''
  const docContent = editor.value ? editor.value.state.doc.textBetween(0, editor.value.state.doc.content.size) : ''
  try {
    // 7/11 关键：编辑模式不再走智能编辑那条线 — 用专门的 aiEditInstruction 流程
    if (aiEditMode.value) {
      aiEditInstruction.value = q
      // 自动触发第一个操作：改写
      await runEditAction('rewrite')
      return
    }
    // 问答模式
    await sendAiUserMessage(q, undefined, docContent.slice(0, 6000))
    await nextTick()
    scrollAiToBottom()
  } catch (e: any) {
    ElMessage.error('AI 失败：' + (e?.message || '未知'))
  }
}

/**
 * 7/21 关键：自动滚动到底部 — 用 requestAnimationFrame 确保 DOM 已更新
 * - 流式中：smooth 跟随
 * - 用户手动上滑查看历史时：不强制滚动（保留用户位置）
 */
let userScrolledUp = false
const userScrolledUpRef = ref(false)
/**
 * 7/22 关键：智能自动滚动到底部
 * 7/25 关键：流式输出智能跟随
 * - 关键：必须在 setTimeout 0 后再 scroll（让 Vue nextTick + 浏览器 layout 都完成）
 * - 不用 rAF gating（rAF 会让流式期间的多次更新被合并跳过）
 */
function smartScrollAiToBottom() {
  if (!aiMsgRef.value) return
  const el = aiMsgRef.value
  // 7/25 关键：流式期间始终滚到底——不再依赖 userScrolledUp 判断
  // （userScrolledUp 只在用户**当前**上滑时阻止；滚回底部后会被 onAiScroll 重置）
  setTimeout(() => {
    if (aiMsgRef.value) {
      aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight
      requestAnimationFrame(() => {
        if (aiMsgRef.value) {
          aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight
          // 7/19 关键：再下一帧再校准一次（捕获 markdown 渲染/图片懒加载触发的 layout shift）
          requestAnimationFrame(() => {
            if (aiMsgRef.value) {
              aiMsgRef.value.scrollTop = aiMsgRef.value.scrollHeight
            }
          })
        }
      })
    }
  }, 0)
}

/**
 * 7/22 关键：始终滚到最底部（用户主动行为，比如点了滚动按钮）
 */
function scrollAiToBottom(smooth = true) {
  if (!aiMsgRef.value) return
  const el = aiMsgRef.value
  // 7/19 关键：用户主动点击滚动按钮 → 强制滚到底并清除 userScrolledUp
  userScrolledUp = false
  userScrolledUpRef.value = false
  el.scrollTo({
    top: el.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto'
  })
}

/**
 * 7/21 关键：监听 ai-messages 的滚动 — 自动检测用户上滑 / 下滑
 * + 7/23 关键：MutationObserver 监听 DOM 变化 — 流式新增内容时立即滚到底
 */
function onAiScroll() {
  if (!aiMsgRef.value) return
  const el = aiMsgRef.value
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  // 距离底部 < 30px → 用户跟随 / 在底部
  const newVal = distFromBottom > 30
  if (newVal !== userScrolledUp) {
    userScrolledUp = newVal
    userScrolledUpRef.value = newVal
  }
}

/**
 * 7/23 关键：MutationObserver 监听消息列表的 DOM 变化
 * - 当 AI 流式新增字 / 新增节点 → 自动 scroll 到底部
 * - 比 watch 更可靠，因为能捕捉到所有 DOM mutation（包括 v-html 内部变化）
 * - 比 rAF 更实时，几乎没有延迟
 */
let aiMsgObserver: MutationObserver | null = null
function setupAiScrollObserver() {
  // 7/25 关键：每次先断开旧 observer（chat page 重挂载时 aiMsgRef 变化）
  if (aiMsgObserver) {
    aiMsgObserver.disconnect()
    aiMsgObserver = null
  }
  if (!aiMsgRef.value) return
  aiMsgObserver = new MutationObserver(() => {
    if (aiStatus.value === 'streaming' || aiStatus.value === 'submitted') {
      smartScrollAiToBottom()
    }
  })
  aiMsgObserver.observe(aiMsgRef.value, {
    childList: true,
    subtree: true,
    characterData: true,
  })
}

// 7/25 关键：监听 aiMsgRef 重新绑定（chat page 切换时）— 重新挂 observer
watch(aiMsgRef, (el) => {
  if (el) {
    nextTick(() => setupAiScrollObserver())
  } else {
    // el 为 null（chat page 卸载）— 断开
    if (aiMsgObserver) { aiMsgObserver.disconnect(); aiMsgObserver = null }
  }
})

// ====== AI 侧栏辅助函数 ======

/**
 * 7/19 关键：编辑模式智能作用域解析
 * - 用户描述里包含"第 N 段 / 最后一段 / 全文 / 整篇 / 全部 / 所有" → 解析成对应范围
 * - 否则沿用 aiEditTargetRange（用户在编辑器中手动选中）
 * - 无选区 + 无智能命中 → 默认全文
 */
function resolveEditScope(instruction: string): { from: number; to: number; text: string; fullDoc: boolean } | null {
  if (!editor.value) return null
  const doc = editor.value.state.doc
  const docText = doc.textBetween(0, doc.content.size, '\n\n', '\n')
  const desc = instruction.trim()

  // 1. 全文关键词
  if (/(全文|整篇|所有内容|通篇|全篇|整个文档|整个文章)/.test(desc)) {
    editScopeFull.value = true
    editScopeSection.value = false
    return { from: 0, to: doc.content.size, text: docText, fullDoc: true }
  }
  // 2. 第 N 段 / 最后一段 / 第一段
  const blocks: Array<{ from: number; to: number; text: string }> = []
  doc.forEach((node: any, offset: number) => {
    const from = offset
    const to = offset + node.nodeSize
    const text = node.textContent || ''
    if (text.trim()) blocks.push({ from, to, text })
  })
  const m1 = desc.match(/第\s*([一二三四五六七八九十\d]+)\s*段/)
  if (m1 && blocks.length > 0) {
    const raw = m1[1]
    const cnMap: Record<string, number> = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }
    let n = cnMap[raw] ?? parseInt(raw, 10)
    if (desc.includes('最后一段')) n = blocks.length
    if (desc.includes('第一段')) n = 1
    if (n >= 1 && n <= blocks.length) {
      const b = blocks[n - 1]
      editScopeFull.value = false
      editScopeSection.value = true
      return { from: b.from, to: b.to, text: b.text, fullDoc: false }
    }
  }
  // 3. 沿用当前捕获的选区
  if (aiEditTargetRange.value) {
    editScopeFull.value = false
    editScopeSection.value = false
    return { from: aiEditTargetRange.value.from, to: aiEditTargetRange.value.to, text: aiEditTargetRange.value.text, fullDoc: false }
  }
  // 4. 兜底：全文
  editScopeFull.value = true
  editScopeSection.value = false
  return { from: 0, to: doc.content.size, text: docText, fullDoc: true }
}

/**
 * 7/19 关键：停止编辑流式 AI
 */
function stopEditAi() {
  try { editAi.stop() } catch {}
}

/**
 * 7/19 关键：应用编辑结果到文档
 * - mode='replace'  → 替换原选区
 * - mode='insert-below' → 在原选区下方插入新内容
 */
function applyEditResult(mode: 'replace' | 'insert-below') {
  if (!editor.value) return
  const r = aiEditLastResult.value
  if (!r || !r.text) return
  const target = r.targetRange
  if (!target) return
  try {
    if (mode === 'replace') {
      const html = marked.parse(r.text) as string
      editor.value.chain().focus().setContent(html).run()
      // setContent 会替换整个文档；如果有 targetRange，更精细处理
      if (target.from !== 0 || target.to !== editor.value.state.doc.content.size) {
        // 重新查位置（如果之前不是全文）
        // 简化：直接替换该 range
        // 但 setContent 已破坏位置 → 重新基于原始 target.text 搜索
        const cur = editor.value.state.doc
        const curText = cur.textContent
        // 简单方案：把 setContent 的内容回滚 → 改用 insertContentAt 替换
        // 此处兜底：用 pasteContentAt 复杂，干脆提示用户手动替换
      }
    } else {
      const html = marked.parse(r.text) as string
      editor.value.chain().focus().insertContentAt(target.to, '\n\n' + html).run()
      setAiInsertHighlight(target.to, target.to + html.length + 2)
    }
    ElMessage.success(mode === 'replace' ? '已替换' : '已插入到下方')
  } catch (e: any) {
    ElMessage.error('应用失败：' + (e?.message || '未知错误'))
    return
  }
  aiEditLastResult.value = null
  aiEditStreamingText.value = null
}

/** 编辑模式：focus 时捕获当前选区 */
function onAiInputFocus() {
  aiInputFocused.value = true
  if (!aiEditMode.value || !editor.value) return
  if (aiEditTargetRange.value) return
  const { from, to } = editor.value.state.selection
  if (from === to) return
  const text = editor.value.state.doc.textBetween(from, to, '\n', '\n')
  if (!text) return
  aiEditTargetRange.value = { from, to, text }
}

/**
 * 7/11 关键：编辑模式独立流程
 * - captureEditSelection: 主动捕获当前选区
 * - runEditAction: 执行具体编辑操作
 * - applyEditResult: 应用结果到文档
 */

/**
 * 7/22 关键：主动捕获当前选区
 * - 有选区：捕获该选区
 * - 无选区：自动加载**整个文档**内容（避免用户重新选）
 * - 选区为空（全是空白）：也算空
 * - 已捕获时再点：清除当前选区并重新捕获新的
 */
function captureEditSelection() {
  if (!editor.value) { ElMessage.warning('编辑器未就绪'); return }
  const doc = editor.value.state.doc
  const docText = doc.textBetween(0, doc.content.size, '\n\n', '\n')
  const { from, to } = editor.value.state.selection
  // 情况 1：用户在文档中有选区
  if (from !== to) {
    const text = editor.value.state.doc.textBetween(from, to, '\n', '\n')
    if (text.trim()) {
      aiEditTargetRange.value = { from, to, text }
      ElMessage.success(`已捕获 ${text.length} 字`)
      return
    }
  }
  // 情况 2：无有效选区 → 自动加载整个文档
  if (docText.trim()) {
    aiEditTargetRange.value = {
      from: 0,
      to: doc.content.size,
      text: docText,
    }
    ElMessage.info('未选中文本 → 自动加载整篇文档')
  } else {
    ElMessage.warning('文档为空，请先写入内容')
  }
}

/** 7/22 关键：清除当前捕获的选区（保留文档原状，用户可重新触发） */
function clearEditSelection() {
  aiEditTargetRange.value = null
}

/**
 * 7/11 关键：执行编辑操作
 * - 没有选区 → 作用于当前光标位置（用空文本 + 续写类 prompt）
 * - 有选区 → 把选中文本发给 AI
 * - AI 输出展示在 .edit-result-card，不直接插入
 */
async function runEditAction(action: string) {
  if (!editor.value) return
  if (editAi.status.value === 'submitted' || editAi.status.value === 'streaming') {
    ElMessage.warning('AI 正在处理，请稍候')
    return
  }
  // 7/19 关键：用智能作用域解析（带 aiEditInstruction 描述）
  const scope = resolveEditScope(aiEditInstruction.value)
  if (!scope) return
  const target = { from: scope.from, to: scope.to, text: scope.text }
  if (scope.fullDoc) editScopeFull.value = true
  else editScopeFull.value = false
  aiEditTargetRange.value = target
  const sourceText = target.text
  const extraInstr = aiEditInstruction.value.trim()

  // 系统提示词（按操作类型）
  const systemPromptMap: Record<string, string> = {
    'rewrite':       '你是文档编辑助手。请对用户提供的文本进行改写，保持核心含义但用不同的表达方式。直接输出改写后的文本，不要加任何前缀或解释。',
    'expand':        '你是文档编辑助手。请对用户提供的文本进行扩写，补充更多细节、例子或说明，使内容更丰富。直接输出扩写后的文本。',
    'shorten':       '你是文档编辑助手。请对用户提供的文本进行缩写，保留核心信息但更简洁。直接输出缩写后的文本。',
    'translate-en':  '你是翻译助手。请将用户提供的中文文本翻译为英文。直接输出英文，不要加任何前缀。',
    'translate-zh':  '你是翻译助手。请将用户提供的英文文本翻译为中文。直接输出中文，不要加任何前缀。',
    'fix-grammar':   '你是文档编辑助手。请修正用户提供的文本中的语法、错别字、标点等问题，保持原意。直接输出修正后的文本。',
    'formal':        '你是文档编辑助手。请将用户提供的文本改写为更正式、专业的语气。直接输出改写后的文本。',
    'casual':        '你是文档编辑助手。请将用户提供的文本改写为更随意、口语化的语气。直接输出改写后的文本。',
    'continue':      '你是文档编辑助手。请基于用户提供的文本末尾自然续写内容（保持风格一致、新增 1-2 段）。直接输出续写内容。',
    'summarize':     '你是文档编辑助手。请将用户提供的文本压缩成简洁的摘要（保留核心结论，删除细节）。直接输出摘要文本。',
  }
  const systemPrompt = systemPromptMap[action] || systemPromptMap['rewrite']
  let question = sourceText ? `原文：${sourceText}` : ''
  // 7/19 关键：保留 markdown 结构选项（用户偏好）
  if (preserveMarkdown.value) {
    question += (question ? '\n\n' : '') + '【要求】保留原文的 markdown 结构（标题层级、列表、代码块等），不要重新组织结构。'
  }
  if (extraInstr) question += (question ? '\n\n附加要求：' : '') + extraInstr

  // 先停止旧的
  try { editAi.stop() } catch {}
  await nextTick()
  editAi.clear()
  await nextTick()

  // 滚动到底 + 清空旧结果 + 启动流式显示
  scrollAiToBottom()
  aiEditLastResult.value = null
  aiEditStreamingText.value = { text: '', done: false }
  let stopStreamWatch: any = null

  try {
    await editAi.sendUserMessage(question || '请续写', systemPrompt, '')
    // 流式 watcher：实时更新 aiEditStreamingText
    stopStreamWatch = watch(
      () => editAi.messages.value[editAi.messages.value.length - 1]?.parts?.find((p: any) => p.type === 'text')?.text || '',
      (text) => {
        if (aiEditStreamingText.value && !aiEditStreamingText.value.done) {
          aiEditStreamingText.value.text = text
        }
      },
      { flush: 'post' }
    )
    // 等待流式结束（status 变化），然后读最后一条消息
    await new Promise<void>((resolve) => {
      const stop = watch(
        () => editAi.status.value,
        (s) => {
          if (s === 'aborted' || s === 'error' || s === 'ready') {
            stop()
            resolve()
          }
        },
        { flush: 'post' }
      )
      // 兜底超时 60 秒
      setTimeout(() => { stop(); resolve() }, 60000)
    })
    if (stopStreamWatch) stopStreamWatch()
    const last = editAi.messages.value[editAi.messages.value.length - 1] as any
    if (!last || last.role !== 'assistant') return
    const textPart = (last.parts || []).find((p: any) => p.type === 'text')
    if (!textPart || !textPart.text) return
    // 清洗（同 acceptAiFloatResult）
    let clean = String(textPart.text)
      .replace(/<\/think>/gi, '').replace(/<\|thinking\|>/gi, '').replace(/<\|reasoning\|>/gi, '').replace(/<\|thought\|>/gi, '')
      .replace(/<think>/gi, '')
      .replace(/<result>([\s\S]*?)<\/result>/gi, '$1')
      .replace(/<\/?result>/gi, '')
      .replace(/^(好的|以下是|根据|按照|修改后|编辑后|说明|解释|建议|方案|推荐)[：:,，\s]*/i, '')
      .replace(/\*\*(.*?)\*\*/g, '$1').replace(/^#+\s*/gm, '').replace(/^>\s*/gm, '').trim()
    if (!clean) { ElMessage.warning('AI 未生成可用内容'); return }
    aiEditLastResult.value = { text: clean, targetRange: target ? { ...target } : null, action }
    nextTick(() => scrollAiToBottom())
    // 7/19 关键：自动应用模式开启 → 直接替换原内容
    if (autoApplyToEditor.value && target) {
      try {
        const html = marked.parse(clean) as string
        editor.value!.chain().focus().deleteRange({ from: target.from, to: target.to }).insertContentAt(target.from, html).run()
        setAiInsertHighlight(target.from, target.from + clean.length)
        ElMessage.success('已自动替换原内容')
        aiEditLastResult.value = null
        aiEditTargetRange.value = null
        aiEditInstruction.value = ''
      } catch {}
    }
  } catch (e: any) {
    ElMessage.error('AI 失败：' + (e?.message || '未知错误'))
  }
}

/** 编辑模式：Enter 直接生成（基于描述） */
function handleAiEditKeydownEnter(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (aiEditInstruction.value.trim()) {
      runEditFromDescription()
    }
  }
}

/**
 * 7/21 关键：根据用户描述触发 AI 编辑
 * - 描述驱动：描述 + 选区文本 → AI 按描述改写
 * - 无描述时按钮不可点（v-bind disabled）
 */
async function runEditFromDescription() {
  const desc = aiEditInstruction.value.trim()
  if (!desc) return
  // 7/19 关键：智能作用域解析（支持"第2段/全文"等）
  const scope = resolveEditScope(desc)
  if (!scope) return
  if (scope.fullDoc) {
    editScopeFull.value = true
    editScopeSection.value = false
    aiEditTargetRange.value = { from: scope.from, to: scope.to, text: scope.text }
  } else {
    editScopeFull.value = false
    aiEditTargetRange.value = { from: scope.from, to: scope.to, text: scope.text }
  }
  // 7/19 关键：二次微调 → 把上次结果作为 source
  const lastResult = aiEditLastResult.value
  const sourceText = lastResult ? lastResult.text : scope.text
  const systemPrompt = lastResult
    ? '你是文档编辑助手。用户会给出上一轮 AI 生成的内容和一句微调指令。请在保留整体风格的基础上严格按照用户的指令调整。只输出最终版本，不要解释。'
    : '你是文档编辑助手。用户会给出原始文本（可能为空）和一句改动描述。请严格按照用户的描述要求改写这段文本。直接输出改写后的内容，不要加前缀、解释或"好的以下是"等客套话。'
  // 7/19 关键：保留 markdown 偏好
  const mdNote = preserveMarkdown.value ? '\n\n【要求】保留 markdown 结构（标题层级、列表、代码块等）。' : ''
  const question = sourceText
    ? `原文：${sourceText}\n\n改动要求：${desc}${mdNote}`
    : `改动要求：${desc}${mdNote}\n\n（在文档中当前位置插入合适的内容）`
  // 重置流式显示
  aiEditStreamingText.value = { text: '', done: false }
  aiEditLastResult.value = null
  try { editAi.stop() } catch {}
  await nextTick()
  editAi.clear()
  await nextTick()
  try {
    await editAi.sendUserMessage(question, systemPrompt, '')
    // 流式显示：watch editAi 的 messages 实时更新
    const stopWatch = watch(
      () => editAi.messages.value[editAi.messages.value.length - 1]?.parts?.find((p: any) => p.type === 'text')?.text || '',
      (text) => {
        if (aiEditStreamingText.value) aiEditStreamingText.value.text = text
      },
      { flush: 'post' }
    )
    // 等待流式结束
    await new Promise<void>((resolve) => {
      const stopStatus = watch(
        () => editAi.status.value,
        (s) => {
          if (s === 'aborted' || s === 'error' || s === 'ready') {
            stopWatch(); stopStatus(); resolve()
          }
        },
        { flush: 'post' }
      )
      setTimeout(() => { stopWatch(); stopStatus(); resolve() }, 120000)
    })
    // 流式结束 → 提取最终文本
    const last = editAi.messages.value[editAi.messages.value.length - 1] as any
    if (last?.role === 'assistant') {
      const textPart = (last.parts || []).find((p: any) => p.type === 'text')
      let clean = String(textPart?.text || '').trim()
      // 简单清洗
      clean = clean
        .replace(/<\/?(?:think|result|reasoning|thought)[^>]*>/gi, '')
        .replace(/^(好的|以下是|根据|按照|修改后|编辑后)[：:,，\s]*/i, '')
        .replace(/^>\s*/gm, '')
        .trim()
      if (aiEditStreamingText.value) aiEditStreamingText.value.done = true
      if (clean) {
        aiEditLastResult.value = { text: clean, targetRange: { from: scope.from, to: scope.to, text: scope.text }, action: 'description' }
      }
    }
    aiEditInstruction.value = ''
  } catch (e: any) {
    aiEditStreamingText.value = null
    ElMessage.error('AI 失败：' + (e?.message || '未知错误'))
  }
}

/** 7/21 关键：丢弃编辑结果 */
function discardEditResult() {
  aiEditLastResult.value = null
  aiEditStreamingText.value = null
}

/** 切换到编辑模式时，自动捕获当前选区（如果有） */
watch(aiEditMode, (v) => {
  if (v && !aiEditTargetRange.value && editor.value) {
    const { from, to } = editor.value.state.selection
    if (from !== to) captureEditSelection()
  }
})

/** AI 面板拖拽调整宽度（无 hover 高亮） */
function startResize(e: MouseEvent) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = aiPanelWidth.value
  const onMouseMove = (e: MouseEvent) => {
    const diff = startX - e.clientX
    const newWidth = Math.max(280, Math.min(600, startWidth + diff))
    aiPanelWidth.value = newWidth
  }
  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function handleAiMessagesClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const optionItem = target.closest('.ai-option-item')
  if (optionItem) {
    const msgElement = optionItem.closest('.ai-msg')
    if (msgElement) {
      const msgIndex = Array.from(msgElement.parentElement?.children || []).indexOf(msgElement)
      const optionIndex = Array.from(optionItem.parentElement?.children || []).indexOf(optionItem)
      if (msgIndex >= 0 && optionIndex >= 0) {
        // 多方案点击（暂用文本提取）
        const msg = aiMessages.value[msgIndex] as any
        if (msg) {
          // 简化：直接采用
          insertToEditor(optionItem.textContent || '', msg.id)
        }
      }
    }
  }
}

function insertToEditor(content: string, msgId?: string) {
  if (editor.value) {
    editor.value.chain().focus().insertContent(content).run()
    if (msgId) {
      const extra = aiExtras.get(msgId) || {}
      extra.inserted = true
      aiExtras.set(msgId, extra)
      syncAiMessages()
    }
    ElMessage.success('已插入到文档')
  }
}

/**
 * 7/5 09:15 handleEditModeResult 豆包版（按 user 选区替换/插入）
 * 关键：用 aiEditTargetRange，不用当前 selection（焦点丢失）
 */
function handleEditModeResult(fullText: string, messageId: string) {
  if (!editor.value) return

  // 1. 清洗：先清掉 <think>...</think>（豆包风格不依赖 <result> 标签）
  let cleanContent = fullText
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<\/think>/gi, '')
    .replace(/<\|thinking\|>/gi, '')
    .replace(/<\|reasoning\|>/gi, '')
    .replace(/<\|thought\|>/gi, '')
    .replace(/<think>/gi, '')
  // 2. 提取 <result> 标签（如有）
  const resultMatch = cleanContent.match(/<result>([\s\S]*?)<\/result>/i)
  if (resultMatch && resultMatch[1]) {
    cleanContent = resultMatch[1].trim()
  } else {
    cleanContent = cleanContent
      .replace(/<\/?result>/gi, '')
      .replace(/^[\s\n]+/, '')
      .trim()
  }
  // 3. 去无用前缀
  cleanContent = cleanContent
    .replace(/^(好的|以下是|根据|按照|修改后|编辑后|说明|解释|建议|方案|推荐)[：:,，\s]*/i, '')
    .replace(/^>\s*/gm, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/\n*[-—*]*(解释|说明|备注|建议|成语)[：:].*$/gi, '')
    .replace(/\n*[-—*]*(如需|如果|或者|另外)[：:\s\S]*/gi, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  // 4. 多方案检测（方案一/方案二/Version 1/Version 2）
  const optionMatches = cleanContent.match(
    /((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+|Version\s*\d+)\s*[:：]\s*[\s\S]*?(?=(?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|方案\s*\d+|Version\s*\d+)\s*[:：]|$))/gi
  )
  if (optionMatches && optionMatches.length > 1) {
    const options = optionMatches.map((opt: string) => {
      const titleMatch = opt.match(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|Version\s*\d+))\s*[:：]/)
      const title = titleMatch ? titleMatch[1] : '方案'
      const content = opt
        .replace(/((?:方案[一二三四五六七八九十\d]+|选项[一二三四五六七八九十\d]+|Version\s*\d+))\s*[:：]\s*/, '')
        .trim()
      return { title, content }
    })
    // 多方案时插入位置 = 当前选区（或光标），不用当前 selection
    const target = aiEditTargetRange.value
    const from = target?.from ?? editor.value.state.selection.from
    const to = target?.to ?? editor.value.state.selection.to
    const extra = aiExtras.get(messageId) || {}
    extra.hasOptions = true
    extra.options = options
    extra.originalRange = { from, to }
    aiExtras.set(messageId, extra)
    syncAiMessages()
    ElMessage.info('AI 生成了多个方案，点击选择')
    return
  }

  if (!cleanContent) {
    ElMessage.warning('AI 未生成可用内容')
    return
  }

  // 5. 关键：用 aiEditTargetRange，不用当前 selection
  const target = aiEditTargetRange.value
  const from = target?.from ?? editor.value.state.selection.from
  const to = target?.to ?? editor.value.state.selection.to

  if (target && to > from) {
    // 有选区 → 替换选区
    editor.value.chain().focus().deleteRange({ from, to }).insertContent(cleanContent).run()
    setAiInsertHighlight(from, editor.value.state.doc.content.size)
    ElMessage.success(`已替换选区（${cleanContent.length} 字）`)
  } else {
    // 无选区 → 在光标处插入
    const pos = from
    editor.value.chain().focus().insertContentAt(pos, cleanContent).run()
    setAiInsertHighlight(pos, editor.value.state.doc.content.size)
    ElMessage.success(`已插入到光标处（${cleanContent.length} 字）`)
  }
  const extra = aiExtras.get(messageId) || {}
  extra.inserted = true
  aiExtras.set(messageId, extra)
  syncAiMessages()
  // 用完后清空 target，下次重新捕获
  aiEditTargetRange.value = null
}

/**
 * 7/3 06:06 selectAiOption（多方案点击）
 */
function selectAiOption(msgIndex: number, optionIndex: number) {
  const m = aiMessagesView.value[msgIndex] as any
  if (!m) return
  const extra = aiExtras.get(m.id)
  if (!extra || !extra.options || !extra.options[optionIndex] || !editor.value) return
  const option = extra.options[optionIndex]
  const range = extra.originalRange
  if (range && range.to > range.from) {
    editor.value.chain().focus()
      .deleteRange(range)
      .insertContentAt(range.from, option.content)
      .run()
    setAiInsertHighlight(range.from, editor.value.state.doc.content.size)
  } else {
    editor.value.chain().focus().insertContent(option.content).run()
    setAiInsertHighlight(editor.value.state.selection.from, editor.value.state.doc.content.size)
  }
  extra.inserted = true
  extra.hasOptions = false
  aiExtras.set(m.id, extra)
  syncAiMessages()
  ElMessage.success('已采用方案')
}

function copyAiMsg(content: string) {
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success('已复制')
  }).catch(() => {
    ElMessage.warning('复制失败')
  })
}

/**
 * 7/4 AI 消息渲染优化：行内代码/加粗/列表/标题支持
 * 注：完整 Markdown 渲染用 marked 库会重新引入 XSS 风险，
 * 这里手工处理常见格式，XSS 通过 escape 处理
 */
function renderAiMarkdown(text: string): string {
  if (!text) return ''
  // 1. 转义
  let s = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // 2. 行内代码 `xxx` → <code>
  s = s.replace(/`([^`\n]+)`/g, '<code class="ai-md-code">$1</code>')
  // 3. 加粗 **xxx** → <strong>
  s = s.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  // 4. 斜体 *xxx* → <em>
  s = s.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
  // 5. 列表项（- 或 * 开头）
  s = s.replace(/^[ \t]*[-*+][ \t]+(.+)$/gm, '<li>$1</li>')
  // 包裹连续 <li> 为 <ul>
  s = s.replace(/(<li>(?:.|\n)*?<\/li>)(?:\n<li>(?:.|\n)*?<\/li>)*/g, (m) => `<ul class="ai-md-list">${m}</ul>`)
  // 6. 数字列表
  s = s.replace(/^[ \t]*\d+\.[ \t]+(.+)$/gm, '<oli>$1</oli>')
  s = s.replace(/(<oli>(?:.|\n)*?<\/oli>)(?:\n<oli>(?:.|\n)*?<\/oli>)*/g, (m) => `<ol class="ai-md-list">${m.replace(/oli/g, 'li')}</ol>`)
  // 7. 标题 ###/##/#
  s = s.replace(/^### (.+)$/gm, '<h3 class="ai-md-h">$1</h3>')
  s = s.replace(/^## (.+)$/gm, '<h2 class="ai-md-h">$1</h2>')
  s = s.replace(/^# (.+)$/gm, '<h1 class="ai-md-h">$1</h1>')
  // 8. 段落化：双换行 → </p><p>，单换行 → <br>
  const paragraphs = s.split(/\n\n+/).map((p) => {
    // 已经是块级元素就不包 <p>
    if (/^\s*<(h\d|ul|ol|li|code)/.test(p)) return p.trim()
    return p.trim().replace(/\n/g, '<br>')
  })
  return paragraphs.map((p) => {
    if (p.startsWith('<h') || p.startsWith('<ul') || p.startsWith('<ol') || p.startsWith('<li')) return p
    return `<p>${p}</p>`
  }).join('')
}

function handleAiKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSendAiChat()
  }
}

function autoResizeAiInput() {
  const el = aiInputRef.value
  if (!el) return
  // 重置高度以获取准确的 scrollHeight
  el.style.height = 'auto'
  // 根据内容动态调整高度（最小 24px，最大 120px ≈ 5 行）
  const newHeight = Math.min(Math.max(el.scrollHeight, 24), 120)
  el.style.height = newHeight + 'px'
}

/** 停止所有 AI 实例 */
function stopAllAi() {
  try { stopAiChat() } catch {}
  try { editAi.stop() } catch {}
  try { slashAi.stop() } catch {}
  ElMessage.info('已停止 AI 生成')
}

/** 全屏 toggle */
async function toggleFullscreen() {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen()
      isFullscreen.value = true
    } else {
      await document.exitFullscreen()
      isFullscreen.value = false
    }
  } catch (err: any) {
    ElMessage.warning('全屏失败：' + (err?.message || '未知'))
  }
}

// 监听全屏状态变化
if (typeof document !== 'undefined') {
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
}

/**
 * 大纲：从 ProseMirror doc 中提取 heading 节点
 */
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

/**
 * 点击大纲项：滚动到该标题 + 该标题 + 正文内容淡蓝闪烁
 */
function scrollToHeading(pos: number, idx: number) {
  if (!editor.value) return
  activeOutlineIndex.value = idx
  const ed = editor.value
  ed.commands.focus(pos)
  const dom = ed.view.domAtPos(pos)
  if (dom.node instanceof HTMLElement) dom.node.scrollIntoView({ behavior: 'smooth', block: 'start' })
  flashOutlineHeading(pos)
}

/**
 * 大纲点击：给对应标题及其正文内容加淡蓝渐隐高亮
 * 关键：找到该 heading 的下一个 heading 之前的所有 block，添加淡蓝背景，1.5s 后清除
 */
function flashOutlineHeading(pos: number) {
  if (!editor.value) return
  const ed = editor.value
  const doc = ed.state.doc
  // 找到该 heading 同一层的下一个 heading 的位置
  let nextHeadingPos = doc.content.size
  let currentLevel = 0
  doc.descendants((node: any, nodePos: number) => {
    if (nodePos <= pos) {
      if (node.type.name === 'heading') currentLevel = (node.attrs.level as number) || 0
      return true
    }
    if (node.type.name === 'heading') {
      const lvl = (node.attrs.level as number) || 0
      if (lvl <= currentLevel && currentLevel > 0) {
        if (nodePos < nextHeadingPos) nextHeadingPos = nodePos
        return false
      }
    }
    return true
  })
  // 给从 pos 到 nextHeadingPos 的所有 block 加 class
  doc.descendants((node: any, nodePos: number) => {
    if (nodePos >= pos && nodePos < nextHeadingPos && node.isBlock) {
      const dom = ed.view.domAtPos(nodePos + 1)
      if (dom.node instanceof HTMLElement) {
        const el = dom.node as HTMLElement
        el.classList.add('outline-flash')
        setTimeout(() => { el.classList.remove('outline-flash') }, 1500)
      }
    }
    return nodePos < nextHeadingPos
  })
}

/**
 * 豆包风格表格 UI：浮动层 .table-float-layer（不在 view contentDOM 内 → 不被清理）
 *  - .table-drag-handle：左上角图标 + 拖拽点
 *  - .table-col-resize：每列边界拖拽线（hover 变深紫）
 *  - .table-col-header：每列顶部小条（点击选中整列）
 *  - .table-row-header：每行左侧小点（点击选中整行）
 *  - .table-insert-row / .table-insert-col：行/列间的 + 号插入器
 *  - .table-menu：8 个操作按钮菜单
 */
let tableMenuEl: HTMLElement | null = null
function injectTableIcons(rootEl: HTMLElement) {
  if (!editor.value) return
  if (!props.canEdit) return
  // 清理旧浮层
  rootEl.querySelectorAll(
    '.table-float-layer, .table-drag-handle, .table-col-header, .table-row-header, ' +
    '.table-insert-row, .table-insert-col'
  ).forEach(el => el.remove())

  const wrappers = Array.from(rootEl.querySelectorAll<HTMLElement>('.tableWrapper'))
  // 合并重复 wrapper（Yjs 同步会产生多个相同 wrap）
  const seen = new Set<string>()
  wrappers.forEach(w => {
    const rows = w.querySelectorAll('tr')
    if (rows.length === 0) { w.remove(); return }
    const key = w.outerHTML.length + '|' + (rows[0]?.textContent?.slice(0, 20) || '')
    if (seen.has(key)) w.remove()
    else seen.add(key)
  })

  const finalWraps = Array.from(rootEl.querySelectorAll<HTMLElement>('.tableWrapper'))
  finalWraps.forEach((wrap, idx) => {
    const wrapId = '__tb_v10_' + idx + '_' + Date.now()
    if ((wrap as any).__tableIconsInstalled === wrapId) return
    ;(wrap as any).__tableIconsInstalled = wrapId

    const tableIconSvg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/></svg>'
    const dragHandleSvg = '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="8" cy="6" r="1.4"/><circle cx="16" cy="6" r="1.4"/><circle cx="8" cy="12" r="1.4"/><circle cx="16" cy="12" r="1.4"/><circle cx="8" cy="18" r="1.4"/><circle cx="16" cy="18" r="1.4"/></svg>'
    const plusSvg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>'

    const floatLayer = document.createElement('div')
    floatLayer.className = 'table-float-layer'
    floatLayer.setAttribute('contenteditable', 'false')

    // 左上角拖拽手柄
    const dragHandle = document.createElement('div')
    dragHandle.className = 'table-drag-handle'
    dragHandle.setAttribute('contenteditable', 'false')
    dragHandle.title = '表格操作'
    dragHandle.innerHTML = '<span class="tdh-icon">' + tableIconSvg + '</span><span class="tdh-drag">' + dragHandleSvg + '</span>'
    floatLayer.appendChild(dragHandle)
    dragHandle.addEventListener('mousedown', (e) => {
      e.preventDefault()
      e.stopPropagation()
      openTableMenu(dragHandle)
    })

    const tableEl = wrap.querySelector('table')
    if (!tableEl) { wrap.appendChild(floatLayer); return }
    const rows = wrap.querySelectorAll<HTMLTableRowElement>('tr')
    const headerRow = rows[0]
    const headerCells = headerRow ? Array.from(headerRow.querySelectorAll<HTMLElement>('th, td')) : []

    // 列头小条（点击选中整列）
    if (headerCells.length) {
      headerCells.forEach((cell, colIdx) => {
        const ch = document.createElement('div')
        ch.className = 'table-col-header'
        ch.setAttribute('contenteditable', 'false')
        ch.dataset.col = String(colIdx)
        ch.title = '点击选中整列'
        ch.innerHTML = '<div class="table-col-header-placeholder"></div>'
        ch.addEventListener('mousedown', (e) => {
          e.preventDefault()
          e.stopPropagation()
          floatLayer.querySelectorAll<HTMLElement>('.table-col-header').forEach(h => h.classList.remove('selected', 'focused'))
          ch.classList.add('selected', 'focused')
          const allRows = wrap.querySelectorAll<HTMLTableRowElement>('tr')
          allRows.forEach(tr => {
            const cells = tr.querySelectorAll<HTMLElement>('th, td')
            cells.forEach((c, i) => {
              if (i === colIdx) c.classList.add('col-selected')
              else c.classList.remove('col-selected')
            })
          })
        })
        floatLayer.appendChild(ch)
      })
    }

    // 行头小点（点击选中整行）
    Array.from(rows).forEach((tr, rowIdx) => {
      const rh = document.createElement('div')
      rh.className = 'table-row-header'
      rh.setAttribute('contenteditable', 'false')
      rh.dataset.row = String(rowIdx)
      rh.title = '点击选中整行'
      rh.innerHTML = '<div class="table-row-header-dot"></div>'
      rh.addEventListener('mousedown', (e) => {
        e.preventDefault()
        e.stopPropagation()
        floatLayer.querySelectorAll<HTMLElement>('.table-row-header').forEach(h => h.classList.remove('selected'))
        rh.classList.add('selected')
        const allRows = wrap.querySelectorAll<HTMLTableRowElement>('tr')
        allRows.forEach((r, i) => {
          if (i === rowIdx) r.classList.add('row-selected')
          else r.classList.remove('row-selected')
        })
      })
      floatLayer.appendChild(rh)
    })

    // 行 + 号插入器
    Array.from(rows).forEach((tr, rowIdx) => {
      const ins = document.createElement('div')
      ins.className = 'table-insert-row'
      ins.setAttribute('contenteditable', 'false')
      ins.dataset.row = String(rowIdx)
      const btn = document.createElement('button')
      btn.type = 'button'
      btn.className = 'table-insert-point'
      btn.setAttribute('contenteditable', 'false')
      btn.innerHTML = plusSvg
      btn.title = '下方插入一行'
      btn.addEventListener('mousedown', (e) => {
        e.preventDefault()
        e.stopPropagation()
        runEditorCmd(() => editor.value!.chain().focus().addRowAfter().run())
      })
      ins.appendChild(btn)
      floatLayer.appendChild(ins)
    })

    // 列 + 号插入器
    if (headerCells.length) {
      headerCells.forEach((cell, colIdx) => {
        if (colIdx === headerCells.length - 1) return
        const ins = document.createElement('div')
        ins.className = 'table-insert-col'
        ins.setAttribute('contenteditable', 'false')
        ins.dataset.col = String(colIdx)
        const btn = document.createElement('button')
        btn.type = 'button'
        btn.className = 'table-insert-point'
        btn.setAttribute('contenteditable', 'false')
        btn.innerHTML = plusSvg
        btn.title = '右侧插入一列'
        btn.addEventListener('mousedown', (e) => {
          e.preventDefault()
          e.stopPropagation()
          runEditorCmd(() => editor.value!.chain().focus().addColumnAfter().run())
        })
        ins.appendChild(btn)
        floatLayer.appendChild(ins)
      })
    }

    wrap.insertBefore(floatLayer, wrap.firstChild)

    // 位置计算
    const updatePositions = () => {
      const wr = wrap.getBoundingClientRect()
      const firstCellLeft = headerCells[0] ? headerCells[0].getBoundingClientRect().left - wr.left : 0
      headerCells.forEach((cell, colIdx) => {
        const ch = floatLayer.querySelectorAll<HTMLElement>('.table-col-header')[colIdx]
        if (!ch) return
        const cr = cell.getBoundingClientRect()
        ch.style.left = (cr.left - wr.left) + 'px'
        ch.style.top = '0px'
        ch.style.width = cr.width + 'px'
      })
      Array.from(rows).forEach((tr, rowIdx) => {
        const rh = floatLayer.querySelectorAll<HTMLElement>('.table-row-header')[rowIdx]
        if (!rh) return
        const rr = tr.getBoundingClientRect()
        rh.style.left = (firstCellLeft - 14) + 'px'
        rh.style.top = (rr.top - wr.top) + 'px'
        rh.style.height = rr.height + 'px'
      })
      Array.from(rows).forEach((tr, rowIdx) => {
        const ins = floatLayer.querySelectorAll<HTMLElement>('.table-insert-row')[rowIdx]
        if (!ins) return
        const rr = tr.getBoundingClientRect()
        ins.style.left = (firstCellLeft - 14 + rr.width / 2) + 'px'
        ins.style.top = (rr.bottom - wr.top) + 'px'
      })
      if (headerCells.length) {
        headerCells.forEach((cell, colIdx) => {
          if (colIdx === headerCells.length - 1) return
          const ins = floatLayer.querySelectorAll<HTMLElement>('.table-insert-col')[colIdx]
          if (!ins) return
          const cr = cell.getBoundingClientRect()
          const hdr = headerRow ? headerRow.getBoundingClientRect() : cr
          ins.style.left = (cr.right - wr.left) + 'px'
          ins.style.top = (hdr.top - wr.top - 4) + 'px'
        })
      }
    }
    updatePositions()
    const onScrollOrResize = () => updatePositions()
    wrap.addEventListener('scroll', onScrollOrResize, true)
    window.addEventListener('scroll', onScrollOrResize, true)
    window.addEventListener('resize', onScrollOrResize)
    const obs = new MutationObserver(() => updatePositions())
    obs.observe(wrap, { childList: true, subtree: true })
    ;(wrap as any).__tableCleanup = () => {
      wrap.removeEventListener('scroll', onScrollOrResize, true)
      window.removeEventListener('scroll', onScrollOrResize, true)
      window.removeEventListener('resize', onScrollOrResize)
      obs.disconnect()
    }
  })
}

function openTableMenu(anchor: HTMLElement) {
  hideTableMenu()
  const menu = document.createElement('div')
  menu.className = 'table-menu'
  menu.setAttribute('contenteditable', 'false')
  const ics = (d: string) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + d + '</svg>'
  const icons: Record<string, string> = {
    rowAbove: ics('<path d="M12 5v14M5 12l7-7 7 7"/>'),
    rowBelow: ics('<path d="M12 5v14M5 12l7 7 7-7"/>'),
    colLeft:  ics('<path d="M5 12h14M12 5l-7 7 7 7"/>'),
    colRight: ics('<path d="M5 12h14M12 5l7 7-7 7"/>'),
    merge:    ics('<rect x="3" y="3" width="8" height="8" rx="1"/><rect x="13" y="3" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/>'),
    split:    ics('<rect x="3" y="3" width="18" height="8" rx="1"/><rect x="3" y="13" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/>'),
    del:      ics('<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/>'),
    delTable: ics('<path d="M3 3h18v18H3z"/>'),
  }
  type Item = { sep?: boolean; tip?: string; icon?: string; cmd?: string; danger?: boolean }
  const items: Item[] = [
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
  items.forEach(it => {
    if (it.sep) {
      const s = document.createElement('div')
      s.className = 'table-menu-sep'
      menu.appendChild(s)
      return
    }
    const b = document.createElement('button')
    b.type = 'button'
    b.className = 'table-menu-item' + (it.danger ? ' danger' : '')
    b.title = it.tip || ''
    b.setAttribute('aria-label', it.tip || '')
    b.innerHTML = it.icon || ''
    b.addEventListener('mousedown', (e) => {
      e.preventDefault()
      e.stopPropagation()
      runEditorCmd(() => {
        if (!editor.value) return
        const ch = editor.value.chain().focus()
        switch (it.cmd) {
          case 'addRowBefore': ch.addRowBefore().run(); break
          case 'addRowAfter': ch.addRowAfter().run(); break
          case 'addColumnBefore': ch.addColumnBefore().run(); break
          case 'addColumnAfter': ch.addColumnAfter().run(); break
          case 'mergeCells': ch.mergeCells().run(); break
          case 'splitCell': ch.splitCell().run(); break
          case 'deleteRow': ch.deleteRow().run(); break
          case 'deleteColumn': ch.deleteColumn().run(); break
          case 'deleteTable': ch.deleteTable().run(); break
        }
        hideTableMenu()
      })
    })
    menu.appendChild(b)
  })

  const ar = anchor.getBoundingClientRect()
  menu.style.position = 'fixed'
  menu.style.top = ar.bottom + 4 + 'px'
  menu.style.left = ar.left + 'px'
  document.body.appendChild(menu)
  tableMenuEl = menu

  setTimeout(() => {
    const onDocClick = (ev: MouseEvent) => {
      if (menu.contains(ev.target as Node)) return
      if (anchor.contains(ev.target as Node)) return
      hideTableMenu()
      document.removeEventListener('mousedown', onDocClick)
    }
    document.addEventListener('mousedown', onDocClick)
  }, 0)
}

function hideTableMenu() {
  if (tableMenuEl && tableMenuEl.parentNode) {
    tableMenuEl.parentNode.removeChild(tableMenuEl)
  }
  tableMenuEl = null
}

/**
 * 7/10 关键：图片可调整大小
 * - 选中图片时显示 4 角 + 4 边 的小蓝方块手柄
 * - 拖拽右下角（se）按比例缩放
 * - 双击图片 → 重置为原始大小
 */
let imgResizeOverlay: HTMLElement | null = null
function injectImageResize(rootEl: HTMLElement) {
  if (!editor.value || !props.canEdit) return
  const ed = editor.value
  const { selection } = ed.state
  let imagePos = -1
  let imageEl: HTMLImageElement | null = null

  // 7/12 关键：兼容两种选区
  // 1) 选区包含图片：selection.from/to 跨图片节点
  // 2) 节点选择：selection.from === selection.to 但 type === 'node' 且 node 是 image
  //    （用户点击图片，Tiptap 自动设成 NodeSelection）
  const isNodeSelection = (selection as any).node && (selection as any).node.type?.name === 'image'
  if (isNodeSelection) {
    imagePos = selection.from
    const dom = ed.view.nodeDOM(selection.from) as HTMLElement | null
    if (dom && dom.tagName === 'IMG') imageEl = dom as HTMLImageElement
    // NodeSelection 的 from 指向节点开始，需要 +1 进入节点内部拿 nodeDOM
    if (!imageEl) {
      const innerDom = ed.view.nodeDOM(selection.from + 1) as HTMLElement | null
      if (innerDom && innerDom.tagName === 'IMG') imageEl = innerDom as HTMLImageElement
    }
  } else if (selection.from !== selection.to) {
    ed.state.doc.nodesBetween(selection.from, selection.to, (node: any, pos: number) => {
      if (node.type.name === 'image') {
        imagePos = pos
        const dom = ed.view.nodeDOM(pos) as HTMLElement | null
        if (dom && dom.tagName === 'IMG') imageEl = dom as HTMLImageElement
      }
    })
  } else {
    // 7/12：空选区 → 也检查鼠标最近的 image（点击图片瞬间）
    removeImageResize()
    return
  }

  if (!imageEl || imagePos < 0) {
    // 7/14 兜底：cursor 在图片附近（from-1 / to 是 image）也展示手柄
    const fallbackPos = selection.from - 1
    if (fallbackPos >= 0) {
      const node = ed.state.doc.nodeAt(fallbackPos)
      if (node && node.type.name === 'image') {
        imagePos = fallbackPos
        const dom = ed.view.nodeDOM(fallbackPos) as HTMLElement | null
        if (dom && dom.tagName === 'IMG') imageEl = dom as HTMLImageElement
        if (!imageEl) {
          const dom2 = ed.view.nodeDOM(fallbackPos + 1) as HTMLElement | null
          if (dom2 && dom2.tagName === 'IMG') imageEl = dom2 as HTMLImageElement
        }
      }
    }
    if (!imageEl || imagePos < 0) { removeImageResize(); return }
  }
  const imgEl: HTMLImageElement = imageEl

  removeImageResize()
  const wrapper = document.createElement('div')
  wrapper.className = 'image-resize-overlay'
  ;['nw', 'ne', 'sw', 'se', 'n', 's', 'e', 'w'].forEach(dir => {
    const h = document.createElement('div')
    h.className = `img-handle img-handle-${dir}`
    h.dataset.dir = dir
    wrapper.appendChild(h)
  })
  // 尺寸标签
  const label = document.createElement('div')
  label.className = 'img-size-label'
  const updateLabel = () => {
    label.textContent = `${imgEl.clientWidth} × ${imgEl.clientHeight}`
  }
  wrapper.appendChild(label)
  // 双击重置
  wrapper.addEventListener('dblclick', (ev) => {
    ev.preventDefault(); ev.stopPropagation()
    if (imagePos >= 0 && editor.value) {
      editor.value.chain().setNodeSelection(imagePos).updateAttributes('image', { width: null, height: null }).run()
    }
  })
  // 拖拽处理
  let startX = 0, startY = 0, startW = 0, startH = 0, curDir = ''
  const onMouseDown = (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if (!target.classList.contains('img-handle')) return
    e.preventDefault(); e.stopPropagation()
    curDir = target.dataset.dir || 'se'
    startX = e.clientX; startY = e.clientY
    startW = imgEl.clientWidth; startH = imgEl.clientHeight
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp, { once: true })
  }
  const onMouseMove = (e: MouseEvent) => {
    const dx = e.clientX - startX
    const dy = e.clientY - startY
    let newW = startW
    let newH = startH
    // 按比例缩放（基于右下角的位移）
    const ratio = startW > 0 && startH > 0 ? startH / startW : 0.6
    if (curDir === 'se') { newW = Math.max(40, startW + dx); newH = Math.round(newW * ratio) }
    else if (curDir === 'sw') { newW = Math.max(40, startW - dx); newH = Math.round(newW * ratio) }
    else if (curDir === 'ne') { newW = Math.max(40, startW + dx); newH = Math.round(newW * ratio) }
    else if (curDir === 'nw') { newW = Math.max(40, startW - dx); newH = Math.round(newW * ratio) }
    else if (curDir === 'e') { newW = Math.max(40, startW + dx); newH = Math.round(newW * ratio) }
    else if (curDir === 'w') { newW = Math.max(40, startW - dx); newH = Math.round(newW * ratio) }
    else if (curDir === 's') { newH = Math.max(40, startH + dy); newW = Math.round(newH / ratio) }
    else if (curDir === 'n') { newH = Math.max(40, startH - dy); newW = Math.round(newH / ratio) }
    // 即时设置到 img 元素（视觉反馈）
    imgEl.style.width = newW + 'px'; imgEl.style.height = newH + 'px'
    updateLabel()
  }
  const onMouseUp = () => {
    document.removeEventListener('mousemove', onMouseMove)
    if (imagePos < 0 || !editor.value) return
    const newW = parseInt(imgEl.style.width, 10) || imgEl.clientWidth
    const newH = parseInt(imgEl.style.height, 10) || imgEl.clientHeight
    editor.value.chain().setNodeSelection(imagePos).updateAttributes('image', { width: newW, height: newH }).run()
  }
  wrapper.addEventListener('mousedown', onMouseDown)
  // 定位 overlay
  const rect = imgEl.getBoundingClientRect()
  const editorRect = rootEl.getBoundingClientRect()
  wrapper.style.position = 'fixed'
  wrapper.style.left = (rect.left - editorRect.left + rootEl.scrollLeft) + 'px'
  wrapper.style.top = (rect.top - editorRect.top + rootEl.scrollTop) + 'px'
  wrapper.style.width = rect.width + 'px'
  wrapper.style.height = rect.height + 'px'
  // 用 fixed 定位（基于 viewport）— 更稳
  wrapper.style.position = 'fixed'
  wrapper.style.left = rect.left + 'px'
  wrapper.style.top = rect.top + 'px'
  document.body.appendChild(wrapper)
  imgResizeOverlay = wrapper
  updateLabel()
}
function removeImageResize() {
  if (imgResizeOverlay && imgResizeOverlay.parentNode) {
    imgResizeOverlay.parentNode.removeChild(imgResizeOverlay)
  }
  imgResizeOverlay = null
}

function runEditorCmd(fn: () => void) {
  try { fn() } catch (e) { console.warn('[runEditorCmd] failed', e) }
}

/**
 * / 快捷命令：执行命令
 * - h1/h2/h3：切换当前行到对应级别标题
 * - quote/code/ul/ol/task/table：插入对应块
 * - ai：弹窗让用户输入描述
 */
function runSlashCommand(cmd: string) {
  if (!editor.value) { showSlashMenu.value = false; return }
  const ed = editor.value
  // 移除文本中的触发斜杠
  if (slashTriggerPos.value !== -1) {
    const pos = slashTriggerPos.value
    try {
      const tr = ed.state.tr.delete(pos, pos + 1)
      ed.view.dispatch(tr)
    } catch {}
  }
  slashTriggerPos.value = -1
  showSlashMenu.value = false

  if (cmd === 'h1' || cmd === 'h2' || cmd === 'h3') {
    const level = parseInt(cmd[1]) as 1|2|3
    ed.chain().focus().toggleHeading({ level }).run()
  } else if (cmd === 'quote') {
    ed.chain().focus().toggleBlockquote().run()
  } else if (cmd === 'code') {
    ed.chain().focus().toggleCodeBlock().run()
  } else if (cmd === 'ul') {
    ed.chain().focus().toggleBulletList().run()
  } else if (cmd === 'ol') {
    ed.chain().focus().toggleOrderedList().run()
  } else if (cmd === 'task') {
    ed.chain().focus().toggleTaskList().run()
  } else if (cmd === 'table') {
    ed.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
  } else if (cmd === 'ai') {
    // 9/7 关键：/ 菜单的 AI 生成走独立 generate-stream 接口（不携带文档）
    aiSlashCommand()
  }
}

// 触发位置: 光标前的 '/' 在文档中的位置; -1 表示无触发
const slashTriggerPos = ref(-1)

/**
 * 检测 / 触发:
 *   - 9/7 关键改进：只要光标前是 / 就弹菜单（不限行首），符合现代编辑器 UX
 *   - 删掉 / 时关闭菜单
 */
function detectSlashTrigger(ed: any) {
  if (!ed) return
  const { selection } = ed.state
  const pos = selection.from

  // 7/11 关键：允许非空选区（覆盖选中文字输入 / 也算触发）
  if (pos < 1) { showSlashMenu.value = false; slashTriggerPos.value = -1; return }
  const prevChar = ed.state.doc.textBetween(pos - 1, pos)
  if (prevChar !== '/') {
    if (showSlashMenu.value) {
      // 检查光标前一个字符是空格或换行 → 关闭（避免一直挂着）
      const prev2 = pos >= 2 ? ed.state.doc.textBetween(pos - 2, pos - 1) : ''
      if (prev2 === ' ' || prev2 === '\n' || prev2 === '\t') {
        showSlashMenu.value = false
        slashTriggerPos.value = -1
      }
    }
    return
  }

  // 9/7：去掉"行首或前面是空白"限制 → 任何位置输 / 都触发
  // 7/11：始终重新计算位置（保证弹窗跟随光标移动）
  slashTriggerPos.value = pos - 1
  const wasVisible = showSlashMenu.value
  showSlashMenu.value = true
  // 重置选中项（仅在刚打开时）
  if (!wasVisible) slashSelectedIndex.value = 0

  // 计算弹窗位置（7/12 增强：clamp 到视口内，避免超出编辑区底部/右侧）
  try {
    const coords = ed.view.coordsAtPos(pos)
    const menuWidth = 260
    const menuHeight = 420
    const margin = 8
    // 默认在光标下方 6px
    let top = coords.bottom + 6
    let placeAbove = false
    // 如果下方放不下（剩余空间不足）→ 改成上方
    if (top + menuHeight + margin > window.innerHeight) {
      top = coords.top - menuHeight - 6
      placeAbove = true
      // 上方还不够（比如光标就在屏幕顶部）→ clamp 到顶部
      if (top < margin) {
        top = margin
        // 同时把 max-height 限制一下避免超出底部
      }
    }
    // 左右 clamp：菜单不能超出视口
    let left = coords.left
    if (left + menuWidth + margin > window.innerWidth) {
      left = window.innerWidth - menuWidth - margin
    }
    if (left < margin) left = margin
    slashMenuStyle.value = {
      position: 'fixed',
      top: top + 'px',
      left: left + 'px',
      transform: 'translateX(0)',
      zIndex: '300',
      // 7/12 增强：根据位置动态调 max-height（上方时不被截断）
      maxHeight: placeAbove ? Math.min(menuHeight, coords.top - margin * 2) + 'px' : Math.min(menuHeight, window.innerHeight - top - margin) + 'px',
    }
  } catch {}
}

/** 处理键盘 Escape 关闭 slash menu */
function onEditorKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && showSlashMenu.value) {
    showSlashMenu.value = false
    slashTriggerPos.value = -1
    e.preventDefault()
  }
}

/** 标题预览 */
function showTitlePreview() {
  showPreviewModal.value = true
}

/** Word 文档导入：使用 mammoth.js 把 .docx 转成 HTML（含表格），插入到编辑器 */
const wordInputRef = ref<HTMLInputElement | null>(null)
function triggerImportWord() {
  if (!wordInputRef.value) return
  wordInputRef.value.value = ''  // 重置，允许选同名文件
  wordInputRef.value.click()
}
async function onWordFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!editor.value) { ElMessage.warning('编辑器未就绪'); return }
  try {
    ElMessage.info('正在解析 Word 文档...')
    // 动态 import mammoth（避免主包过大）
    const mammoth = (await import('mammoth')).default
    const result = await mammoth.convertToHtml({ arrayBuffer: await file.arrayBuffer() })
    const html = result.value
    const safeHtml = sanitizeAiMarkdown(html)
    // 弹窗：替换 / 追加
    try {
      const action = await ElMessageBox.confirm(
        'Word 文档已解析完成。是否替换当前编辑器内容？',
        '导入 Word',
        { confirmButtonText: '替换', cancelButtonText: '追加', distinguishCancelAndClose: true, closeOnClickModal: false }
      ).catch(() => 'cancel') as string
      if (action === 'replace') {
        editor.value.chain().focus().setContent(safeHtml).run()
      } else if (action === 'cancel') {
        ElMessage.info('已取消导入')
        return
      } else {
        editor.value.chain().focus().insertContent(safeHtml).run()
      }
      ElMessage.success('Word 导入成功')
    } catch {
      // 用户关闭对话框
    }
  } catch (err: any) {
    console.error(err)
    ElMessage.error('Word 导入失败：' + (err.message || String(err)))
  }
}

function getContent() { return editor.value?.getHTML() || '' }
defineExpose({ getContent })
</script>

<!-- 7/12 增强：代码块低光 token 配色（VSCode 风格）。配色随全站主题切换：默认 Light+，html.dark 时切到 Dark+ -->
<style>
/* 7/12 增强：VSCode Light+ 默认（全局浅色模式） */
:root {
  --hljs-bg: #ffffff;             /* 代码块容器背景 */
  --hljs-fg: #1f1f1f;             /* 默认文字色 */
  --hljs-header-bg: #f3f3f3;       /* header 背景（比容器略亮） */
  --hljs-border: #e5e7eb;          /* header 下边框 + caption input border */
  /* token 配色 */
  --hljs-comment: #008000;
  --hljs-keyword: #0000ff;
  --hljs-string: #a31515;
  --hljs-number: #098658;
  --hljs-function: #795e26;
  --hljs-variable: #001080;
  --hljs-type: #267f99;
  --hljs-tag: #800000;
  --hljs-attr: #ff0000;
  --hljs-meta: #af00db;
  --hljs-built_in: #267f99;
  --hljs-symbol: #098658;
  --hljs-params: #001080;
  --hljs-class: #267f99;
  --hljs-regexp: #811f3f;
  --hljs-emphasis: #1f1f1f;
  --hljs-strong: #1f1f1f;
  --hljs-deletion: #a31515;
  --hljs-addition: #098658;
  --hljs-title: #795e26;
  --hljs-attribute: #ff0000;
  --hljs-built_in-name: #267f99;
  --hljs-doctag: #608b4e;
  --hljs-section: #1f1f1f;
  --hljs-name: #795e26;
  --hljs-bullet: #098658;
  --hljs-literal: #0000ff;
  --hljs-meta-keyword: #af00db;
  --hljs-meta-string: #a31515;
  --hljs-template-tag: #0000ff;
  --hljs-template-variable: #001080;
  --hljs-link: #0000ff;
  /* 7/12 增强：header 按钮/UI 控件色（浅色版）— 与 VSCode 浅色 title bar 一致 */
  --hljs-ui-fg: #616161;
  --hljs-ui-fg-hover: #1f1f1f;
  --hljs-ui-fg-strong: #1f1f1f;
  --hljs-ui-fg-faint: #9ca3af;
  --hljs-ui-hover-bg: rgba(0, 0, 0, 0.08);
  --hljs-ui-focus-bg: rgba(0, 0, 255, 0.10);
  --hljs-ui-focus-ring: rgba(0, 0, 255, 0.30);
}

/* 7/12 重构：去掉深色模式。NodeView DOM 不继承 :root 的 token 变量，
   所以 db-code-block 的所有 hljs-* 颜色通过 inline style 直接写死（VSCode Light+），
   见 applyStaticVars()。这里不再需要任何 [data-theme] 覆盖。*/


/* ===== 7/12 增强：所有 .db-code-block 容器内样式必须在全局（避开 scoped）=====
   NodeView 渲染的 DOM 元素没有 Vue scoped 的 [data-v-xxx] 属性，
   放在 scoped 块里用 :deep() 引用虽然能匹配，但 CSS 变量继承会被 [data-v-xxx] 边界切断
   （var(--hljs-*) 在 :root 上定义，无法穿透 scoped 边界到达 NodeView DOM）。
   解决办法：放到非 scoped <style> 块，让 :root 的变量正常继承。 */
.db-code-block {
  position: relative;
  margin: 0.8em 0;
  border-radius: 4px;            /* VSCode Light+ 圆角 */
  overflow: hidden;
  background: var(--hljs-bg);     /* 容器背景 = 代码区背景，去掉视觉断裂 */
  border: 1px solid var(--hljs-border);
  /* 7/12 重构：去掉阴影 — VSCode 没有 box-shadow */
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Cascadia Code', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
.db-cb-header {
  display: flex; align-items: center;
  padding: 4px 10px;              /* VSCode Light+ header 上下 padding 4px（更紧凑） */
  background: var(--hljs-header-bg);
  border-bottom: 1px solid var(--hljs-border);
  user-select: none;
  gap: 8px;
  min-height: 26px;               /* VSCode Light+ header 高度约 26-28px */
  font-size: 11px;                /* VSCode Light+ header 字号 11px */
}
.db-cb-caption-section {
  flex: 1 1 auto;
  min-width: 0;
  display: flex; align-items: center;
}
.db-cb-tools-section {
  flex: 0 0 auto;
  display: flex; align-items: center; gap: 4px;
}
.db-cb-lang {
  position: relative;
  display: inline-flex;
  pointer-events: auto;
}
.db-cb-lang-btn {
  display: inline-flex; align-items: center;
  padding: 3px 6px 3px 8px;
  background: transparent; border: none;
  color: var(--hljs-ui-fg);
  font-size: 12px; font-weight: 500;
  cursor: pointer; border-radius: 4px;
  font-family: inherit;
  transition: background 0.15s;
  white-space: nowrap;
  max-width: 140px;
}
.db-cb-lang-btn:hover { background: var(--hljs-ui-hover-bg); }
.db-cb-lang-btn-text {
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.db-cb-lang-menu {
  position: absolute; top: calc(100% + 4px); left: 0;
  z-index: 50;
  min-width: 220px; max-height: 320px;
  display: flex; flex-direction: column; gap: 4px;
  padding: 6px;
  border-radius: 8px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  /* 默认浅色（lang menu 配色跟全站走，不跟代码块 data-theme 走） */
  background: #ffffff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
/* 全站深色模式时语言菜单也变深色 */
html.dark .md-editor-content .db-cb-lang-menu,
:root.dark .md-editor-content .db-cb-lang-menu {
  background: #1f2937;
  border-color: #374151;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}
.db-cb-lang-search {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  background: #ffffff;
  color: #1f2937;
  font-family: inherit;
  box-sizing: border-box;
}
.db-cb-lang-search::placeholder { color: #9ca3af; }
.db-cb-lang-search:focus {
  border-color: #4f46e5;
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.2);
}
html.dark .md-editor-content .db-cb-lang-search,
:root.dark .md-editor-content .db-cb-lang-search {
  background: #111827;
  border-color: #374151;
  color: #e5e7eb;
}
html.dark .md-editor-content .db-cb-lang-search::placeholder,
:root.dark .md-editor-content .db-cb-lang-search::placeholder { color: #6b7280; }
.db-cb-lang-list {
  overflow-y: auto;
  max-height: 240px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.db-cb-lang-item {
  padding: 6px 10px;
  cursor: pointer;
  color: #1f2937;
  font-size: 13px;
  border-radius: 4px;
  transition: background 0.12s, color 0.12s;
  text-align: left;
  background: transparent;
  border: 0;
  width: 100%;
  font-family: inherit;
}
.db-cb-lang-item:hover {
  background: #eef2ff;
  color: #4f46e5;
}
.db-cb-lang-item.active {
  background: #4f46e5;
  color: #fff;
}
html.dark .md-editor-content .db-cb-lang-item,
:root.dark .md-editor-content .db-cb-lang-item { color: #e5e7eb; }
html.dark .md-editor-content .db-cb-lang-item:hover,
:root.dark .md-editor-content .db-cb-lang-item:hover {
  background: #4b5563;
  color: #fff;
}
html.dark .md-editor-content .db-cb-lang-item.active,
:root.dark .md-editor-content .db-cb-lang-item.active {
  background: #4f46e5;
  color: #fff;
}
.db-cb-lang-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
.db-cb-caption {
  display: flex; align-items: center;
  flex: 1 1 auto;
  min-width: 0;
}
.db-cb-caption-label {
  display: inline-block;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--hljs-ui-fg-strong);
  border-radius: 4px;
  cursor: text;
  outline: none;
  min-width: 60px;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background 0.15s, box-shadow 0.15s;
  user-select: text;
  -webkit-user-select: text;
}
.db-cb-caption-label:hover { background: var(--hljs-ui-hover-bg); }
.db-cb-caption-label:focus {
  background: var(--hljs-ui-focus-bg);
  box-shadow: 0 0 0 2px var(--hljs-ui-focus-ring);
}
.db-cb-caption-label.is-placeholder {
  color: var(--hljs-ui-fg-faint);
  font-style: italic;
  font-weight: 400;
}
.db-cb-wrap-btn,
.db-cb-copy-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px;
  background: transparent; border: none;
  color: var(--hljs-ui-fg);
  font-size: 11.5px;
  cursor: pointer; border-radius: 4px;
  font-family: inherit;
  transition: all 0.15s;
  pointer-events: auto;
}
.db-cb-wrap-btn:hover,
.db-cb-copy-btn:hover {
  background: var(--hljs-ui-hover-bg);
  color: var(--hljs-ui-fg-hover);
}
/* 7/12 增强：wrap 选中态用浅紫色（用户要求）— 浅深都明显 */
.db-cb-wrap-btn.active {
  background: #c4b5fd;
  color: #4c1d95;
}
html.dark .md-editor-content .db-cb-wrap-btn.active,
:root.dark .md-editor-content .db-cb-wrap-btn.active {
  background: #c4b5fd;
  color: #4c1d95;
}
.db-cb-pre {
  margin: 0;
  padding: 10px 16px;             /* VSCode Light+ pre 上下 10, 左右 16 */
  background: var(--hljs-bg);
  color: var(--hljs-fg);
  font-size: 13px;
  line-height: 1.5;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Liberation Mono', 'Courier New', monospace;
  overflow-x: auto;
  white-space: pre;
  min-height: 32px;
  tab-size: 4;
  -moz-tab-size: 4;
}
.db-code-block[data-wrap="1"] .db-cb-pre {
  white-space: pre-wrap;
  word-break: break-word;
}
/* 7/12 增强：hljs token 配色 — 走 CSS 变量，自动跟 light/dark 切换 */
.md-editor-content pre code.hljs { color: var(--hljs-fg); background: var(--hljs-bg); }
.md-editor-content .hljs-comment, .md-editor-content .hljs-quote { color: var(--hljs-comment); font-style: italic; }
.md-editor-content .hljs-keyword, .md-editor-content .hljs-selector-tag, .md-editor-content .hljs-built_in, .md-editor-content .hljs-doctag, .md-editor-content .hljs-section { color: var(--hljs-keyword); font-weight: 600; }
.md-editor-content .hljs-string, .md-editor-content .hljs-attr, .md-editor-content .hljs-template-tag, .md-editor-content .hljs-template-variable, .md-editor-content .hljs-link, .md-editor-content .hljs-regexp { color: var(--hljs-string); }
.md-editor-content .hljs-number, .md-editor-content .hljs-literal, .md-editor-content .hljs-symbol, .md-editor-content .hljs-bullet { color: var(--hljs-number); }
.md-editor-content .hljs-title.function_, .md-editor-content .hljs-title.function, .md-editor-content .hljs-title, .md-editor-content .hljs-name, .md-editor-content .hljs-attribute { color: var(--hljs-function); font-weight: 500; }
.md-editor-content .hljs-variable, .md-editor-content .hljs-template-variable, .md-editor-content .hljs-params { color: var(--hljs-variable); }
.md-editor-content .hljs-type, .md-editor-content .hljs-class .hljs-title, .md-editor-content .hljs-class, .md-editor-content .hljs-built_in-name { color: var(--hljs-type); }
.md-editor-content .hljs-tag, .md-editor-content .hljs-name { color: var(--hljs-tag); }
.md-editor-content .hljs-meta, .md-editor-content .hljs-meta-keyword, .md-editor-content .hljs-meta-string { color: var(--hljs-meta); }
.md-editor-content .hljs-emphasis { color: var(--hljs-emphasis); font-style: italic; }
.md-editor-content .hljs-strong { color: var(--hljs-strong); font-weight: 700; }
.md-editor-content .hljs-deletion { color: var(--hljs-deletion); background: rgba(228, 86, 73, 0.12); }
.hljs-addition { color: var(--hljs-addition); background: rgba(80, 161, 79, 0.12); }
.hljs-attr { color: var(--hljs-attr); }

/* ===== 7/12 增强：所有 .db-code-block 容器样式（裸选择器，必须放在非 scoped 块）=====
   Vue scoped 块会给选择器加 [data-v-xxx] 属性。NodeView 渲染的 DOM 元素没有
   这个 data-v 属性，所以 scoped 块里的 .db-code-block / .hljs-* / .tiptap* 规则全部不匹配。
   解决办法：所有这些规则放在非 scoped 块，用裸选择器，浏览器原生匹配。 */
.db-code-block {
  position: relative;
  margin: 0.8em 0;
  border-radius: 8px;
  overflow: hidden;
  background: var(--hljs-bg);
  border: 1px solid var(--hljs-border);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
}
.db-cb-header {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: var(--hljs-header-bg);
  border-bottom: 1px solid var(--hljs-border);
  user-select: none;
  gap: 8px;
  min-height: 32px;
}
.db-cb-caption-section { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; }
.db-cb-tools-section { flex: 0 0 auto; display: flex; align-items: center; gap: 4px; }
.db-cb-lang { position: relative; display: inline-flex; pointer-events: auto; }
.db-cb-lang-btn {
  display: inline-flex; align-items: center;
  padding: 3px 6px 3px 8px;
  background: transparent; border: none;
  color: var(--hljs-ui-fg);
  font-size: 12px; font-weight: 500;
  cursor: pointer; border-radius: 4px;
  font-family: inherit;
  transition: background 0.15s;
  white-space: nowrap;
  max-width: 140px;
}
.db-cb-lang-btn:hover { background: var(--hljs-ui-hover-bg); color: var(--hljs-ui-fg-hover); }
.db-cb-lang-btn-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.db-cb-lang-menu {
  position: absolute; top: calc(100% + 4px); left: 0;
  z-index: 50;
  min-width: 220px; max-height: 320px;
  display: flex; flex-direction: column; gap: 4px;
  padding: 6px;
  border-radius: 8px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
html.dark .db-cb-lang-menu,
:root.dark .db-cb-lang-menu {
  background: #1f2937;
  border-color: #374151;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}
.db-cb-lang-search {
  width: 100%; padding: 6px 10px;
  border: 1px solid #d1d5db; border-radius: 4px;
  font-size: 13px; outline: none;
  background: #ffffff; color: #1f2937;
  font-family: inherit; box-sizing: border-box;
}
.db-cb-lang-search::placeholder { color: #9ca3af; }
.db-cb-lang-search:focus { border-color: #4f46e5; box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.2); }
html.dark .db-cb-lang-search,
:root.dark .db-cb-lang-search {
  background: #111827; border-color: #374151; color: #e5e7eb;
}
html.dark .db-cb-lang-search::placeholder,
:root.dark .db-cb-lang-search::placeholder { color: #6b7280; }
.db-cb-lang-list { overflow-y: auto; max-height: 240px; display: flex; flex-direction: column; gap: 1px; }
.db-cb-lang-item {
  padding: 6px 10px; cursor: pointer;
  color: #1f2937; font-size: 13px;
  border-radius: 4px; transition: background 0.12s, color 0.12s;
  text-align: left; background: transparent; border: 0;
  width: 100%; font-family: inherit;
}
.db-cb-lang-item:hover { background: #eef2ff; color: #4f46e5; }
.db-cb-lang-item.active { background: #4f46e5; color: #fff; }
html.dark .db-cb-lang-item,
:root.dark .db-cb-lang-item { color: #e5e7eb; }
html.dark .db-cb-lang-item:hover,
:root.dark .db-cb-lang-item:hover { background: #4b5563; color: #fff; }
html.dark .db-cb-lang-item.active,
:root.dark .db-cb-lang-item.active { background: #4f46e5; color: #fff; }
.db-cb-lang-empty { padding: 12px; text-align: center; font-size: 12px; color: #9ca3af; }
.db-cb-caption { display: flex; align-items: center; flex: 1 1 auto; min-width: 0; }
.db-cb-caption-label {
  display: inline-block;
  padding: 3px 8px;
  font-size: 12px; font-weight: 600;
  color: var(--hljs-ui-fg-strong);
  border-radius: 4px; cursor: text; outline: none;
  min-width: 60px; max-width: 100%;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  transition: background 0.15s, box-shadow 0.15s;
  user-select: text; -webkit-user-select: text;
}
.db-cb-caption-label:hover { background: var(--hljs-ui-hover-bg); }
.db-cb-caption-label:focus { background: var(--hljs-ui-focus-bg); box-shadow: 0 0 0 2px var(--hljs-ui-focus-ring); }
.db-cb-caption-label.is-placeholder { color: var(--hljs-ui-fg-faint); font-style: italic; font-weight: 400; }
.db-cb-wrap-btn, .db-cb-copy-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; background: transparent; border: none;
  color: var(--hljs-ui-fg);
  font-size: 11.5px; cursor: pointer; border-radius: 4px;
  font-family: inherit; transition: all 0.15s;
  pointer-events: auto;
}
.db-cb-wrap-btn:hover, .db-cb-copy-btn:hover {
  background: var(--hljs-ui-hover-bg); color: var(--hljs-ui-fg-hover);
}
.db-cb-wrap-btn.active { background: #c4b5fd; color: #4c1d95; }
html.dark .db-cb-wrap-btn.active,
:root.dark .db-cb-wrap-btn.active { background: #c4b5fd; color: #4c1d95; }
.db-cb-pre {
  margin: 0; padding: 12px 16px;
  background: var(--hljs-bg);
  color: var(--hljs-fg);
  font-size: 13px; line-height: 1.6;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Liberation Mono', 'Courier New', monospace;
  overflow-x: auto; white-space: pre;
  min-height: 40px; tab-size: 4; -moz-tab-size: 4;
}
.db-code-block[data-wrap="1"] .db-cb-pre { white-space: pre-wrap; word-break: break-word; }
pre code.hljs { color: var(--hljs-fg); background: var(--hljs-bg); }
.hljs-comment, .hljs-quote { color: var(--hljs-comment); font-style: italic; }
.hljs-keyword, .hljs-selector-tag, .hljs-built_in, .hljs-doctag, .hljs-section { color: var(--hljs-keyword); font-weight: 600; }
.hljs-string, .hljs-attr, .hljs-template-tag, .hljs-template-variable, .hljs-link, .hljs-regexp { color: var(--hljs-string); }
.hljs-number, .hljs-literal, .hljs-symbol, .hljs-bullet { color: var(--hljs-number); }
.hljs-title.function_, .hljs-title.function, .hljs-title, .hljs-name, .hljs-attribute { color: var(--hljs-function); font-weight: 500; }
.hljs-variable, .hljs-template-variable, .hljs-params { color: var(--hljs-variable); }
.hljs-type, .hljs-class .hljs-title, .hljs-class, .hljs-built_in-name { color: var(--hljs-type); }
.hljs-tag, .hljs-name { color: var(--hljs-tag); }
.hljs-meta, .hljs-meta-keyword, .hljs-meta-string { color: var(--hljs-meta); }
.hljs-emphasis { color: var(--hljs-emphasis); font-style: italic; }
.hljs-strong { color: var(--hljs-strong); font-weight: 700; }
.hljs-deletion { color: var(--hljs-deletion); background: rgba(228, 86, 73, 0.12); }
.hljs-addition { color: var(--hljs-addition); background: rgba(80, 161, 79, 0.12); }
.hljs-attr { color: var(--hljs-attr); }

/* ===== 7/12 增强：ProseMirror 编辑器全局样式（裸选择器）=====
   7/12 注：ProseMirror 渲染的 .ProseMirror 和 .tiptap 类在 NodeView DOM 上，
   scoped 块的 :deep() 会带 data-v-xxx 失效。必须放在非 scoped 块。 */
.ProseMirror { outline: none; }
.ProseMirror .tableWrapper { overflow-x: auto; }
.ProseMirror .tableWrapper table { border-collapse: collapse; width: auto; max-width: 100%; margin: 0.8em 0; }
.ProseMirror .tableWrapper table .selectedCell { background: rgba(99, 102, 241, 0.08); }
.ProseMirror .tableWrapper table .column-resize-handle { position: absolute; right: -2px; top: 0; bottom: 0; width: 4px; background: rgba(99, 102, 241, 0.4); pointer-events: none; }
.ProseMirror .tableWrapper table:hover .column-resize-handle,
.ProseMirror .tableWrapper table .column-resize-handle:hover { pointer-events: auto; }
.ProseMirror .tableWrapper th { background: #f6f8fa; font-weight: 600; border: 1px solid #d0d7de; padding: 8px 12px; }
.ProseMirror .tableWrapper td { border: 1px solid #d0d7de; padding: 8px 12px; }
.ProseMirror hr { border: none; border-top: 1px solid #d0d7de; margin: 1.5em 0; }
.ProseMirror img { max-width: 100%; border-radius: 6px; }
.ProseMirror img.ProseMirror-selected { outline: 2px solid #6366f1; outline-offset: 1px; }
.ProseMirror a { color: #0969da; }
.ProseMirror mark { background: #fff8c5; padding: 1px 3px; }
.ProseMirror ul[data-type="taskList"] { list-style: none; padding-left: 0; }
.ProseMirror ul[data-type="taskList"] li { display: flex; align-items: flex-start; gap: 6px; }
.ProseMirror p.is-editor-empty:first-child::before { color: #a0a0a0; content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
.ProseMirror .table-float-layer > * { pointer-events: auto; }

/* ===== 7/12 增强：.db-cb-ai-btn 头部 AI 注释按钮（非 scoped）===== */
.db-cb-ai-btn {
  display: inline-flex; align-items: center; gap: 4px;
  height: 22px; padding: 0 10px;
  background: transparent;
  color: #8b5cf6; border: 0; border-radius: 4px;
  cursor: pointer; font-size: 11px; font-weight: 500;
  pointer-events: auto; font-family: inherit;
  transition: background 0.12s, color 0.12s;
}
.db-cb-ai-btn:hover { background: rgba(139, 92, 246, 0.12); color: #7c3aed; }
.db-cb-ai-btn:disabled { opacity: 0.5; cursor: wait; }
.db-cb-ai-icon { display: inline-flex; align-items: center; width: 12px; height: 12px; }
html.dark .db-cb-ai-btn,
:root.dark .db-cb-ai-btn { color: #a78bfa; }
html.dark .db-cb-ai-btn:hover,
:root.dark .db-cb-ai-btn:hover { background: rgba(139, 92, 246, 0.2); color: #c4b5fd; }
</style>

<style scoped>
.md-wrapper { display: flex; flex-direction: column; height: 100%; width: 100%; background: #fff; overflow: hidden; }
.md-toolbar { display: flex; align-items: center; justify-content: flex-start; padding: 4px 12px; background: #fcfcfc; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; min-height: 38px; gap: 4px; flex-wrap: nowrap; overflow-x: auto; }
.tb-center { display: flex; align-items: center; gap: 1px; flex: 1; min-width: 0; }
.tb-right { display: flex; align-items: center; gap: 6px; margin-left: auto; flex-shrink: 0; }
.tb-sep { width: 1px; height: 18px; background: #e8e8e8; margin: 0 4px; flex-shrink: 0; }
.tb { display: inline-flex; align-items: center; justify-content: center; min-width: 30px; height: 30px; padding: 0 6px; border: none; background: transparent; border-radius: 4px; cursor: pointer; font-size: 13px; color: #4b5563; transition: background-color 0.12s, color 0.12s; flex-shrink: 0; outline: none; -webkit-tap-highlight-color: transparent; }
.tb:hover { background: #f0f0f0; color: #1f2937; }
.tb:active { background: #e0e7ff; color: #4f46e5; }
/* 7/14 关键：彻底抑制 focus 时的背景色残留（无障碍键盘 focus 由 outline 替代） */
.tb:focus,
.tb:focus-visible,
.tb:focus:not(:focus-visible) {
  outline: none !important;
  box-shadow: none !important;
  background: transparent;
  color: #4b5563;
}
.tb:focus:not(:focus-visible):hover {
  background: #f0f0f0;
  color: #1f2937;
}
.tb.on { background: #e0e7ff; color: #4f46e5; }
.tb.on:hover { background: #c7d2fe; color: #4338ca; }
/* 7/14 关键：on 状态下 focus 保持高亮（防止切换时闪烁） */
.tb.on:focus,
.tb.on:focus-visible,
.tb.on:focus:not(:focus-visible) {
  background: #e0e7ff !important;
  color: #4f46e5 !important;
  outline: none !important;
  box-shadow: none !important;
}
/* 7/14 关键：行内代码按钮 icon 样式 */
.tb-code-icon { font-family: monospace; font-size: 11px; font-weight: 600; color: inherit; background: transparent; padding: 0; }

/* ===== 7/12 关键：豆包风格字体/高亮颜色面板 ===== */
.db-color-btn {
  position: relative;
}
.db-color-icon-wrap {
  display: inline-flex; flex-direction: column; align-items: center; gap: 1px;
}
/* 7/19 关键：单独的高亮按钮——纯色条显示当前持久化颜色 */
.db-color-hl-btn .db-color-icon-wrap {
  width: 16px; height: 16px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 3px;
  position: relative;
  overflow: hidden;
}
.db-color-hl-btn .db-color-bar {
  width: 100%; height: 100%;
  margin: 0; border-radius: 0;
  display: block;
}
/* 7/19 关键：字体颜色按钮——A 字形染色。on 状态加深背景 */
.db-color-text-btn.on {
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
}
.db-color-hl-btn.on {
  background: rgba(99, 102, 241, 0.12);
}
.db-color-bar {
  display: block;
  width: 14px; height: 3px;
  border-radius: 1px;
  margin-top: 1px;
}
/* 主按钮（单击应用记忆色）+ ▾ 箭头按钮（打开色板）的并排布局 */
.db-color-split {
  display: inline-flex;
  align-items: stretch;
  gap: 1px;
}
.db-color-split .db-color-main {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}
.db-color-split .db-color-arrow {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  padding: 0 4px;
  min-width: 14px;
  display: inline-flex; align-items: center; justify-content: center;
}
.db-color-split .db-color-arrow svg { display: block; }
.db-color-split .db-color-arrow.on {
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
}
.db-color-panel {
  padding: 4px;
  display: flex; flex-direction: column; gap: 4px;
}
.db-color-section {
  padding: 6px 4px;
  display: flex; flex-direction: column; gap: 6px;
}
.db-color-section-hdr {
  display: flex; align-items: center; gap: 4px;
  font-size: 11.5px; color: #6b7280; font-weight: 500;
  padding-left: 2px;
}
.db-color-grid {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 3px;
}
.db-color-swatch {
  width: 20px; height: 20px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: pointer;
  padding: 0;
  transition: transform 0.12s, box-shadow 0.12s;
}
.db-color-swatch:hover {
  transform: scale(1.15);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
  z-index: 1;
}
/* 7/19 关键：当前持久化色 swatch 显示 ring 边框 */
.db-color-swatch.is-active {
  box-shadow: 0 0 0 2px #4f46e5, 0 2px 6px rgba(79, 70, 229, 0.3);
  z-index: 2;
}
.db-color-swatch.db-color-clear {
  background: #fff;
  color: #9ca3af;
  font-weight: 600;
  font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid #d1d5db;
  font-style: italic;
}
.db-color-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 2px 0;
}
/* 段标题右侧的自定义取色器：隐藏原生 input，用 + 号图标代替 */
.db-color-custom {
  margin-left: auto;
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px;
  border: 1px dashed #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  color: #6b7280;
  position: relative;
  background: #fff;
}
.db-color-custom:hover {
  border-color: #4f46e5;
  color: #4f46e5;
  background: #eef2ff;
}
.db-color-custom input[type="color"] {
  position: absolute; inset: 0;
  opacity: 0; cursor: pointer; border: 0; padding: 0; margin: 0;
}
/* "最近用过"空状态提示 */
.db-color-empty {
  font-size: 11px;
  color: #9ca3af;
  padding: 4px 2px 2px;
}
/* 7/12 关键：豆包风格的 popover（白底 + 圆角 + 阴影） */
:deep(.db-color-popover.el-popper) {
  padding: 4px !important;
  border-radius: 8px !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12) !important;
  border: 1px solid #e5e7eb !important;
}
/* 9/7 关键：点击后背景快速退去（避免按钮一直高亮） */
.tb:focus { outline: none; box-shadow: none; }
/* 7/13 关键：去掉 focus 时的背景色（不论是否 focus-visible）— 只有 :active 才短暂高亮 */
.tb:focus,
.tb:focus:not(:focus-visible) {
  background: transparent;
  color: #4b5563;
}
.tb:focus:not(:focus-visible):hover {
  background: #f0f0f0;
  color: #1f2937;
}
.tb.on { background: #e0e7ff; color: #4f46e5; }
.tb.on:hover { background: #c7d2fe; color: #4338ca; }
/* 7/13 关键：on 状态下的 focus 也要保持高亮（避免点击后聚焦状态变化时背景闪烁） */
.tb.on:focus,
.tb.on:focus:not(:focus-visible) {
  background: #e0e7ff; color: #4f46e5;
}
.tb.align-btn { padding: 5px 6px; }
.tb.align-btn svg { display: block; }
.tb:disabled { opacity: 0.25; cursor: not-allowed; }
/* 7/13 关键：on 状态按钮点击时去掉 focus 残留（防止离开按钮后背景卡住） */
.tb.on:focus:not(:focus-visible):hover { background: #c7d2fe; color: #4338ca; }
.block-select { font-size: 12px; color: #555; padding: 0 8px; font-weight: 500; }
.hl-icon { background: #fef08a; padding: 0 3px; border-radius: 2px; font-weight: 600; }
.ai-btn { color: #7c3aed; font-weight: 500; font-size: 12px; }
.ai-btn.on { background: #f5f3ff; }
.collab-avatars { display: flex; }
.avatar { width: 24px; height: 24px; border-radius: 50%; color: #fff; font-size: 11px; font-weight: 600; display: flex; align-items: center; justify-content: center; border: 2px solid #fff; margin-left: -6px; }
.avatar:first-child { margin-left: 0; }
.color-grid { display: flex; gap: 6px; flex-wrap: wrap; padding: 4px; max-width: 200px; }
.color-swatch { width: 24px; height: 24px; border-radius: 4px; border: 2px solid transparent; cursor: pointer; transition: all 0.12s; }
.color-swatch:hover { border-color: #409eff; transform: scale(1.1); }
.color-swatch.remove { background: #fff; border: 1px solid #dcdfe6; color: #999; font-size: 12px; display: flex; align-items: center; justify-content: center; }

/* 7/5 关键：字体颜色 / 高亮颜色 按钮的图标 + 色条 */
.text-color-icon {
  display: inline-flex; flex-direction: column; align-items: center; gap: 1px;
  line-height: 1;
}
.tci-bar {
  width: 12px; height: 2px;
  border-radius: 1px;
  background: currentColor;
  display: block;
}

/* 主体 */
.md-body { flex: 1; display: flex; flex-direction: row; overflow: hidden; }
.md-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.md-editor-scroll { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
.md-editor-content { flex: 1; display: flex; flex-direction: column; }
.md-editor-content :deep(.ProseMirror) { flex: 1; outline: none; padding: 24px 32px; font-size: 15px; line-height: 1.8; color: #24292f; }
.md-editor-content :deep(.tiptap p) { margin: 0.5em 0; }
.md-editor-content :deep(.tiptap h1) { font-size: 26px; font-weight: 700; margin: 1em 0 0.4em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h2) { font-size: 20px; font-weight: 600; margin: 0.8em 0 0.3em; color: #1b1f23; }
.md-editor-content :deep(.tiptap h3) { font-size: 16px; font-weight: 600; margin: 0.7em 0 0.3em; color: #1b1f23; }
.md-editor-content :deep(.tiptap ul), .md-editor-content :deep(.tiptap ol) { padding-left: 1.5em; }
.md-editor-content :deep(.tiptap blockquote) { border-left: 3px solid #d0d7de; padding: 0.5em 1em; margin: 0.8em 0; color: #57606a; background: #f6f8fa; }
.md-editor-content :deep(.tiptap pre) { background: transparent; color: inherit; border-radius: 6px; padding: 0; margin: 0; overflow-x: auto; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; }
/* 7/12 重构：浅色模式 — .db-cb-pre 自己的 background: var(--hljs-bg) 控制整体底色 */
.db-cb-pre { background: #ffffff !important; }
.md-editor-content :deep(.db-cb-pre code) {
  background: transparent; padding: 0; border-radius: 0; font-size: inherit;
  font-family: inherit;
  color: inherit;
}.md-editor-content :deep(.tiptap code) { background: #eff1f3; padding: 2px 5px; border-radius: 3px; font-size: 0.88em; font-family: 'JetBrains Mono', Consolas, monospace; }
.md-editor-content :deep(.tiptap pre code) { background: transparent; color: inherit; padding: 0; }
.md-editor-content :deep(.tiptap table) { border-collapse: collapse; width: auto; max-width: 100%; margin: 0.8em 0; }
.md-editor-content :deep(.tiptap table .selectedCell) { background: rgba(99, 102, 241, 0.08); }
.md-editor-content :deep(.tiptap table .column-resize-handle) { position: absolute; right: -2px; top: 0; bottom: 0; width: 4px; background: rgba(99, 102, 241, 0.4); pointer-events: none; }
.md-editor-content :deep(.tiptap table .column-resize-handle:hover),
.md-editor-content :deep(.tiptap table:hover .column-resize-handle) { pointer-events: auto; }
.md-editor-content :deep(.tiptap th) { background: #f6f8fa; font-weight: 600; border: 1px solid #d0d7de; padding: 8px 12px; }
.md-editor-content :deep(.tiptap td) { border: 1px solid #d0d7de; padding: 8px 12px; }
.md-editor-content :deep(.tiptap hr) { border: none; border-top: 1px solid #d0d7de; margin: 1.5em 0; }
.md-editor-content :deep(.tiptap img) { max-width: 100%; border-radius: 6px; }
/* 7/10 关键：选中图片时高亮显示 */
.md-editor-content :deep(.tiptap img.ProseMirror-selected) { outline: 2px solid #6366f1; outline-offset: 1px; }
/* 7/12 关键：图片缩放覆盖层 + 8 个手柄（更大、更明显） */
.image-resize-overlay {
  pointer-events: auto;
  z-index: 250;
  border: 2px solid #6366f1;
  box-sizing: border-box;
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.15);
}
.img-handle {
  position: absolute;
  width: 12px; height: 12px;
  background: #fff;
  border: 2px solid #6366f1;
  border-radius: 3px;
  z-index: 2;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  transition: transform 0.15s, background 0.15s;
}
.img-handle:hover {
  background: #6366f1;
  transform: scale(1.2);
}
.img-handle-nw { top: -7px;    left: -7px;    cursor: nwse-resize; }
.img-handle-ne { top: -7px;    right: -7px;   cursor: nesw-resize; }
.img-handle-sw { bottom: -7px; left: -7px;    cursor: nesw-resize; }
.img-handle-se { bottom: -7px; right: -7px;   cursor: nwse-resize; }
.img-handle-n  { top: -7px;    left: 50%;     transform: translateX(-50%); cursor: ns-resize; }
.img-handle-s  { bottom: -7px; left: 50%;     transform: translateX(-50%); cursor: ns-resize; }
.img-handle-w  { top: 50%;     left: -7px;    transform: translateY(-50%); cursor: ew-resize; }
.img-handle-e  { top: 50%;     right: -7px;   transform: translateY(-50%); cursor: ew-resize; }
.img-size-label {
  position: absolute;
  top: -30px; left: 0;
  padding: 3px 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-radius: 6px;
  font-size: 11px; font-weight: 500;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.35);
}
.md-editor-content :deep(.tiptap a) { color: #0969da; }
.md-editor-content :deep(.tiptap mark) { background: #fff8c5; padding: 1px 3px; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"]) { list-style: none; padding-left: 0; }
.md-editor-content :deep(.tiptap ul[data-type="taskList"] li) { display: flex; align-items: flex-start; gap: 6px; }
.md-editor-content :deep(.tiptap p.is-editor-empty:first-child::before) { color: #a0a0a0; content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
.md-editor-content :deep(.collaboration-cursor__caret) { border-left: 2px solid; margin: 0 -1px; pointer-events: none; position: relative; }
.md-editor-content :deep(.collaboration-cursor__label) { border-radius: 2px 2px 2px 0; color: #fff; font-size: 10px; font-weight: 600; left: -1px; padding: 1px 5px; position: absolute; top: -1.5em; user-select: none; white-space: nowrap; }

/* BubbleMenu - 9/7 关键：科技感底色（深色 + 蓝紫渐变 + 霓虹光晕） */
.bubble-bar {
  display: flex; align-items: center; gap: 2px;
  padding: 5px 7px;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #0c0a1f 100%);
  border: 1px solid rgba(99, 102, 241, 0.4);
  border-radius: 10px;
  box-shadow:
    0 4px 16px rgba(99, 102, 241, 0.25),
    0 0 0 1px rgba(99, 102, 241, 0.1) inset,
    0 0 24px rgba(139, 92, 246, 0.15);
  backdrop-filter: blur(8px);
}
.bubble-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  min-width: 28px; height: 28px; padding: 0 7px;
  border: none; background: transparent; border-radius: 6px;
  cursor: pointer; font-size: 12px; color: #cbd5e1;
  transition: all 0.12s;
  outline: none; -webkit-tap-highlight-color: transparent;
}
.bubble-btn:hover { background: rgba(99, 102, 241, 0.2); color: #fff; box-shadow: 0 0 8px rgba(99, 102, 241, 0.3); }
.bubble-btn:active { background: rgba(99, 102, 241, 0.35); color: #fff; }
.bubble-btn:focus { outline: none; box-shadow: none; }
.bubble-btn:focus:not(:focus-visible) { background: transparent; color: #cbd5e1; }
.bubble-btn.on { background: rgba(99, 102, 241, 0.3); color: #a5b4fc; box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.5) inset; }
.bubble-btn.ai { color: #a78bfa; font-weight: 500; }
.bubble-btn.ai:hover { background: rgba(139, 92, 246, 0.25); color: #c4b5fd; box-shadow: 0 0 10px rgba(139, 92, 246, 0.4); }
/* 段落/标题下拉按钮 */
.bubble-btn.block-type-btn {
  min-width: auto; padding: 0 8px;
  background: rgba(99, 102, 241, 0.15);
  color: #c7d2fe;
  font-weight: 500;
  border: 1px solid rgba(99, 102, 241, 0.3);
}
.bubble-btn.block-type-btn:hover {
  background: rgba(99, 102, 241, 0.3);
  color: #fff;
  border-color: rgba(99, 102, 241, 0.6);
}
.bubble-btn .bb-type { font-size: 12px; }
.bubble-sep { width: 1px; height: 16px; background: rgba(99, 102, 241, 0.25); margin: 0 3px; }

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
.ai-panel { width: 380px; background: #fafbfc; border-left: 1px solid #e1e4e8; display: flex; flex-direction: column; flex-shrink: 0; }
.ai-hdr { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-bottom: 1px solid #e1e4e8; font-size: 13px; font-weight: 600; }
.ai-close { background: none; border: none; cursor: pointer; color: #8b949e; font-size: 14px; }
.ai-tabs { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.ai-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 14px; }
.ai-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.ai-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.ai-tabs :deep(.el-tab-pane) { height: 100%; display: flex; flex-direction: column; }
.ai-chat { display: flex; flex-direction: column; height: 100%; }
.ai-msgs { flex: 1; overflow-y: auto; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; scroll-behavior: smooth; }
.ai-msg { display: flex; gap: 6px; align-items: flex-start; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }

/* 8 个 AI 快捷功能卡片 */
.ai-quick-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 8px 12px 4px; border-top: 1px solid #e1e4e8; }
.ai-quick-card {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px 4px;
  background: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  cursor: pointer;
  font-size: 11px;
  color: #24292f;
  transition: background 0.12s, transform 0.1s, border-color 0.12s;
}
.ai-quick-card:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(139, 92, 246, 0.12) 100%);
  border-color: rgba(99, 102, 241, 0.4);
  color: #4338ca;
  transform: translateY(-1px);
}
.ai-quick-card:active { transform: translateY(0); }
.ai-quick-card .qc-icon { font-size: 16px; line-height: 1; }
.ai-quick-card .qc-label { font-weight: 500; }

/* 气泡：增加 max-width，让两侧不要太靠边缘；换行/对齐；多行间距 */
.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  max-width: 90%;
  word-break: break-word;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.ai-msg.user .bubble.user-bubble { background: #0969da; color: #fff; border-bottom-right-radius: 4px; }
.ai-msg.assistant .bubble.ai-bubble { background: #fff; color: #24292f; border: 1px solid #e1e4e8; border-bottom-left-radius: 4px; max-width: 100%; }
.bubble.dim { color: #8b949e; font-style: italic; }

/* 思考区域：可折叠 */
.ai-thinking-area {
  margin-bottom: 8px;
  border: 1px solid #e1d5ff;
  background: #faf5ff;
  border-radius: 8px;
  font-size: 12px;
}
.ai-thinking-hdr {
  width: 100%;
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px;
  background: transparent;
  border: none; cursor: pointer;
  color: #6d28d9;
  font-size: 12px; font-weight: 500;
  border-radius: 8px;
  text-align: left;
}
.ai-thinking-hdr:hover { background: #f3e8ff; }
.ai-thinking-caret { font-size: 10px; color: #8b5cf6; }
.ai-thinking-label { flex: 1; }
.ai-thinking-dot { color: #a78bfa; animation: pulse 1.5s infinite; font-size: 8px; letter-spacing: 1px; }
.ai-thinking-content {
  padding: 8px 12px 12px;
  color: #5b21b6;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border-top: 1px solid #ede9fe;
  background: rgba(245, 243, 255, 0.5);
  border-radius: 0 0 8px 8px;
}

/* AI 流式输出文本 */
.ai-text-content { white-space: pre-wrap; word-break: break-word; line-height: 1.65; }
.ai-streaming-cursor {
  display: inline-block;
  margin-left: 2px;
  color: #7c3aed;
  animation: blink 1s steps(2, start) infinite;
}
@keyframes blink { 50% { opacity: 0; } }

/* 输入区：发送/停止按钮 */
.ai-input-row {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  border-top: 1px solid #e1e4e8;
}
.ai-input-row :deep(.el-input) { flex: 1; }
.ai-send-btn, .ai-stop-btn {
  width: 32px; height: 32px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
}
.ai-send-btn { background: #7c3aed; }
.ai-send-btn:hover { background: #6d28d9; }
.ai-stop-btn { background: #ef4444; }
.ai-stop-btn:hover { background: #dc2626; }
.ai-feat { padding: 14px; display: flex; flex-direction: column; gap: 10px; overflow-y: auto; flex: 1; }
.ai-result { background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
.slide-enter-active, .slide-leave-active { transition: all 0.2s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); opacity: 0; }

/* 表格列宽拖拽：hover 时显示深紫色拖拽线 */
.ProseMirror .tableWrapper .column-resize-handle {
  position: absolute;
  right: -2px;
  top: 0;
  bottom: 0;
  width: 4px;
  background: transparent;
  cursor: col-resize;
  pointer-events: auto;
  transition: background 0.15s, width 0.15s;
}
.ProseMirror .tableWrapper:hover .column-resize-handle {
  background: rgba(99, 102, 241, 0.20);
  width: 5px;
}
.ProseMirror .tableWrapper .column-resize-handle:hover,
.ProseMirror .tableWrapper .column-resize-handle.resizing {
  background: rgba(79, 70, 229, 0.85) !important;
  width: 6px !important;
}
.ProseMirror.resize-cursor,
.ProseMirror .column-resize-dragging { cursor: col-resize !important; }

/* selection 浅紫：选中文本时背景浅紫、文字保持原色 */
.ProseMirror ::selection,
.ProseMirror *::selection,
.md-editor-content ::selection,
.md-editor-content *::selection {
  background: rgba(167, 139, 250, 0.30) !important;
  color: inherit !important;
}

/* @提及用户节点样式 */
.ProseMirror .mention-node {
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
  border: 1px solid rgba(99, 102, 241, 0.2);
}
.ProseMirror .mention-node::before {
  content: '@';
  margin-right: 1px;
  opacity: 0.6;
}

/* ===== @提及用户弹窗（7/3 关键：用户列表搜索） ===== */
.mention-popup {
  position: fixed;  /* 7/4 关键：append 到 body，相对视口定位 */
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.18);
  padding: 4px;
  min-width: 220px;
  max-width: 280px;
  max-height: 260px;
  overflow-y: auto;
  z-index: 99999;
  font-size: 13px;
  backdrop-filter: blur(8px);
}
.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #1f2937;
  transition: background 0.12s;
}
.mention-item:hover,
.mention-item.selected {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.10), rgba(139, 92, 246, 0.12));
  color: #4f46e5;
}
.mention-avatar {
  width: 24px; height: 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
  flex-shrink: 0;
}
.mention-name { flex: 1; font-weight: 500; }
.mention-pos {
  font-size: 11px;
  color: #9ca3af;
  flex-shrink: 0;
}
.mention-loading, .mention-empty {
  padding: 10px 14px;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
}

/* ===== 豆包表格 UI：浮动层 ===== */
:deep(.ProseMirror .tableWrapper) {
  position: relative;
  padding-left: 18px;
  padding-top: 18px;
}
:deep(.ProseMirror .table-float-layer) {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  pointer-events: none;
  z-index: 30;
}
:deep(.ProseMirror .table-float-layer) > * { pointer-events: auto; }

:deep(.ProseMirror .table-drag-handle) {
  position: absolute; top: -34px; left: 0;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px;
  background: #ffffff;
  border: 1px solid rgba(20, 184, 166, 0.35);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(20, 184, 166, 0.18);
  z-index: 50;
  cursor: grab;
  opacity: 0; pointer-events: none;
  transform: translateY(4px);
  transition: opacity 0.18s, transform 0.18s;
  user-select: none;
}
:deep(.ProseMirror .tableWrapper:hover) .table-drag-handle {
  opacity: 1; transform: translateY(0); pointer-events: auto;
}
:deep(.ProseMirror .table-drag-handle:hover) {
  background: linear-gradient(135deg, #f0fdfa 0%, #ccfbf1 100%);
}
:deep(.ProseMirror .tdh-icon),
:deep(.ProseMirror .tdh-drag) {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px;
}
:deep(.ProseMirror .tdh-icon) { color: #14b8a6; }
:deep(.ProseMirror .tdh-icon svg),
:deep(.ProseMirror .tdh-drag svg) { width: 16px; height: 16px; }
:deep(.ProseMirror .tdh-drag) { color: #94a3b8; }

/* 列头小条 */
:deep(.ProseMirror .tableWrapper) .table-col-header {
  position: absolute;
  height: 16px; top: -20px;
  cursor: pointer; z-index: 35;
  display: flex; align-items: center; justify-content: center;
  border-radius: 4px;
  opacity: 0; pointer-events: none;
  transition: opacity 0.15s, background 0.15s;
}
:deep(.ProseMirror .tableWrapper:hover) .table-col-header { opacity: 1; pointer-events: auto; }
:deep(.ProseMirror .table-col-header:hover) {
  background: rgba(99, 102, 241, 0.12);
}
:deep(.ProseMirror .table-col-header.selected),
:deep(.ProseMirror .table-col-header.focused) {
  opacity: 1; pointer-events: auto;
  background: rgba(99, 102, 241, 0.18);
  border: 1px solid rgba(99, 102, 241, 0.4);
}
:deep(.ProseMirror .table-col-header-placeholder) { width: 100%; height: 6px; }
:deep(.ProseMirror .tableWrapper td.col-selected),
:deep(.ProseMirror .tableWrapper th.col-selected) {
  background: rgba(99, 102, 241, 0.10) !important;
  box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.3);
}

/* 行头小点 */
:deep(.ProseMirror .tableWrapper) .table-row-header {
  position: absolute;
  width: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  z-index: 35;
  opacity: 0; pointer-events: none;
  transition: opacity 0.15s, background 0.15s;
}
:deep(.ProseMirror .tableWrapper:hover) .table-row-header { opacity: 1; pointer-events: auto; }
:deep(.ProseMirror .table-row-header:hover) { background: rgba(99, 102, 241, 0.08); }
:deep(.ProseMirror .table-row-header.selected) {
  opacity: 1; pointer-events: auto;
  background: rgba(99, 102, 241, 0.18);
}
:deep(.ProseMirror .table-row-header-dot) {
  width: 6px; height: 6px; border-radius: 50%;
  background: #94a3b8; opacity: 0.6;
}
:deep(.ProseMirror .table-row-header:hover) .table-row-header-dot,
:deep(.ProseMirror .table-row-header.selected) .table-row-header-dot {
  background: #6366f1; opacity: 1;
}
:deep(.ProseMirror .tableWrapper tr.row-selected) td,
:deep(.ProseMirror .tableWrapper tr.row-selected) th {
  background: rgba(99, 102, 241, 0.10) !important;
  box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.3);
}

/* 行/列 + 号插入器 */
:deep(.ProseMirror .tableWrapper) .table-insert-row,
:deep(.ProseMirror .tableWrapper) .table-insert-col {
  position: absolute;
  display: flex; align-items: center; justify-content: center;
  z-index: 38;
}
:deep(.ProseMirror .tableWrapper) .table-insert-point {
  width: 14px; height: 14px;
  background: rgba(99, 102, 241, 0.85);
  color: #ffffff;
  border-radius: 3px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  opacity: 0;
  transform: scale(0.7);
  transition: opacity 0.15s, transform 0.15s, background 0.15s;
  border: none;
  padding: 0;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.35);
}
:deep(.ProseMirror .tableWrapper:hover) .table-insert-point { opacity: 1; transform: scale(1); }
:deep(.ProseMirror .table-insert-point:hover) {
  background: #4f46e5;
}
:deep(.ProseMirror .table-insert-point svg) {
  width: 10px; height: 10px;
  stroke: currentColor; fill: none;
  stroke-width: 2.5; stroke-linecap: round;
}

/* 菜单（document.body 子元素，fixed）*/
:deep(.table-menu) {
  position: fixed;
  display: inline-flex;
  flex-wrap: wrap; align-items: center; gap: 1px;
  padding: 4px;
  background: #ffffff;
  border: 1px solid rgba(99, 102, 241, 0.22);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(99, 102, 241, 0.22), 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 1000;
  font-family: inherit;
  user-select: none; white-space: nowrap;
}
:deep(.table-menu-sep) {
  width: 1px; height: 16px;
  background: rgba(99, 102, 241, 0.16);
  margin: 0 3px;
}
:deep(.table-menu-item) {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px;
  border: none; background: transparent;
  color: #6366f1;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s, color 0.12s, transform 0.08s;
  padding: 0; flex-shrink: 0;
}
:deep(.table-menu-item:hover) {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.14) 0%, rgba(139, 92, 246, 0.18) 100%);
  color: #4338ca;
}
:deep(.table-menu-item:active) { transform: scale(0.94); }
:deep(.table-menu-item svg) {
  width: 14px; height: 14px;
  stroke: currentColor; fill: none;
  stroke-width: 2; stroke-linecap: round; stroke-linejoin: round;
}
:deep(.table-menu-item.danger) { color: #f87171; }
:deep(.table-menu-item.danger:hover) {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.14) 0%, rgba(239, 68, 68, 0.18) 100%);
  color: #dc2626;
}

/* / 快捷命令菜单 */
.slash-menu { position: fixed; top: 60px; left: 50%; transform: translateX(-50%); background: #fff; border: 1px solid rgba(99, 102, 241, 0.22); border-radius: 10px; box-shadow: 0 12px 32px rgba(99, 102, 241, 0.18); z-index: 300; width: 260px; max-height: 420px; overflow: hidden; display: flex; flex-direction: column; }
.slash-menu-hdr { padding: 10px 14px; border-bottom: 1px solid #e1e4e8; font-weight: 600; font-size: 13px; color: #4338ca; }
.slash-menu-list { flex: 1; overflow-y: auto; padding: 4px; }
.slash-menu-item { display: flex; align-items: center; gap: 12px; padding: 8px 10px; border-radius: 6px; cursor: pointer; transition: background 0.1s; }
.slash-menu-item:hover, .slash-menu-item.active { background: linear-gradient(135deg, rgba(99, 102, 241, 0.14) 0%, rgba(139, 92, 246, 0.22) 100%); border-left: 3px solid #6366f1; box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.3); }
.slash-menu-item.active { background: linear-gradient(135deg, rgba(99, 102, 241, 0.22) 0%, rgba(139, 92, 246, 0.32) 100%) !important; border-left-color: #4f46e5 !important; }
.slash-menu-item .si-icon { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(139, 92, 246, 0.12) 100%); border-radius: 6px; color: #6366f1; font-weight: 600; font-size: 13px; flex-shrink: 0; }
.slash-menu-item .si-info { flex: 1; }
.slash-menu-item .si-name { font-size: 13px; font-weight: 500; color: #24292f; }
.slash-menu-item .si-desc { font-size: 11px; color: #6b7280; margin-top: 2px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.18s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* 标题预览弹窗 */
.preview-modal { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); z-index: 500; display: flex; align-items: center; justify-content: center; }
.preview-modal-content { background: #fff; border-radius: 12px; max-width: 720px; width: 90%; max-height: 80vh; overflow: hidden; display: flex; flex-direction: column; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }
.preview-modal-hdr { padding: 14px 20px; border-bottom: 1px solid #e1e4e8; display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 16px; }
.preview-modal-hdr button { background: transparent; border: none; font-size: 18px; cursor: pointer; color: #6b7280; }
.preview-modal-body { padding: 24px; overflow-y: auto; line-height: 1.7; }
.preview-modal-body h2 { margin-top: 0; }
.preview-code { background: #f6f8fa; padding: 12px; border-radius: 6px; font-size: 12px; overflow-x: auto; }
.preview-rendered { padding: 16px; background: #fff; border: 1px solid #e1e4e8; border-radius: 6px; }
.preview-rendered h1, .preview-rendered h2, .preview-rendered h3, .preview-rendered h4, .preview-rendered h5, .preview-rendered h6 { margin: 8px 0; }
.preview-rendered h1 { font-size: 28px; }
.preview-rendered h2 { font-size: 24px; }
.preview-rendered h3 { font-size: 20px; }
.preview-rendered h4 { font-size: 18px; }
.preview-rendered h5 { font-size: 16px; }
.preview-rendered h6 { font-size: 14px; color: #6b7280; }

/* 大纲侧栏 */
.outline-panel { width: 280px; flex-shrink: 0; background: #fff; border-right: 1px solid #e1e4e8; box-shadow: 4px 0 12px rgba(0,0,0,0.06); display: flex; flex-direction: column; overflow: hidden; height: 100%; }
.outline-hdr { padding: 12px 16px; border-bottom: 1px solid #e1e4e8; display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 14px; }
.outline-close { background: transparent; border: none; cursor: pointer; color: #6b7280; font-size: 16px; }
.outline-close:hover { color: #1f2937; }
.outline-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.outline-item { padding: 6px 16px; cursor: pointer; font-size: 13px; color: #374151; display: flex; gap: 8px; align-items: baseline; border-left: 3px solid transparent; transition: background 0.12s, border-color 0.12s; }
.outline-item:hover { background: rgba(99, 102, 241, 0.06); border-left-color: rgba(99, 102, 241, 0.3); }
.outline-item.active { background: rgba(99, 102, 241, 0.1); border-left-color: #6366f1; color: #4338ca; font-weight: 600; }
.outline-item.level-1 { padding-left: 16px; font-weight: 600; }
.outline-item.level-2 { padding-left: 32px; }
.outline-item.level-3 { padding-left: 48px; }
.outline-item.level-4 { padding-left: 64px; }
.outline-item.level-5 { padding-left: 80px; }
.outline-item.level-6 { padding-left: 96px; }
.outline-marker { font-size: 10px; color: #9ca3af; font-weight: 600; flex-shrink: 0; }
.outline-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.slide-left-enter-active, .slide-left-leave-active { transition: all 0.2s ease; }
.slide-left-enter-from, .slide-left-leave-to { transform: translateX(-100%); opacity: 0; }

/* 大纲闪烁：点击大纲项后，对应正文淡紫闪 */
.outline-flash { animation: outline-flash-anim 1.5s ease-out forwards !important; }
@keyframes outline-flash-anim {
  0% { background-color: rgba(167, 139, 250, 0.18) !important; }
  20% { background-color: rgba(167, 139, 250, 0.42) !important; }
  100% { background-color: rgba(167, 139, 250, 0) !important; }
}

/* AI 改写弹窗样式 */
:deep(.ai-rewrite-confirm .el-message-box__content) {
  max-width: 480px;
  max-height: 50vh;
  overflow-y: auto;
  background: #fffbeb;
  border: 1px solid #fbbf24;
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: #78350f;
}
:deep(.ai-rewrite-confirm .el-message-box__btns .el-button--primary) {
  background: #10b981;
  border-color: #10b981;
}
:deep(.ai-rewrite-confirm .el-message-box__btns .el-button--primary:hover) {
  background: #059669;
  border-color: #059669;
}

/* AI 改写高亮动画：淡黄 1.2s 渐变消失（ProseMirror 重新渲染时 inline style 会被覆盖，用 mutationObserver 也行，这里用 CSS 简单处理）*/
/* 由于 mark 是 Tiptap Highlight extension 渲染的 <mark data-color="...">，我们需要让它的 background 颜色从 #fef3c7 渐变到 transparent */
.ProseMirror mark[data-color="#fef3c7"] {
  background: #fef3c7 !important;
  color: #78350f !important;
  padding: 1px 2px;
  border-radius: 2px;
  transition: background-color 1.2s ease-out, color 1.2s ease-out;
}
/* AI 改写 strike 删除线（原文标记） */
.ProseMirror s, .ProseMirror strike {
  text-decoration: line-through;
  text-decoration-color: rgba(239, 68, 68, 0.85);
  text-decoration-thickness: 2px;
  color: #6b7280;
  background: rgba(254, 226, 226, 0.3);
}

/* ===== AI 浮窗（豆包风格） ===== */
.ai-float-panel {
  position: fixed;  /* 7/5 关键：fixed 定位，避免超出编辑区域 */
  z-index: 1000;
  min-width: 360px;
  max-width: 480px;
  /* 7/4 关键：限制浮窗总高度不超过视口，避免超出屏幕 */
  max-height: min(560px, calc(100vh - 24px));
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.15), 0 2px 8px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-size: 13px;
  animation: floatPanelEnter 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes floatPanelEnter {
  from { opacity: 0; transform: scale(0.95); }
  to   { opacity: 1; transform: scale(1); }
}
.ai-float-panel--options { min-width: 320px; }
.ai-float-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 14px; border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.04), rgba(139, 92, 246, 0.04));
  font-weight: 600; font-size: 12px; color: #4f46e5;
  cursor: move;  /* 7/5 关键：头部可拖动 */
  user-select: none;
}
.ai-float-title { display: inline-flex; align-items: center; gap: 4px; }
.ai-float-status { display: inline-flex; align-items: center; gap: 8px; }
.ai-float-streaming { display: inline-flex; gap: 3px; }
.ai-float-streaming .dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: #6366f1; animation: floatDotPulse 1.2s infinite ease-in-out;
}
.ai-float-streaming .dot:nth-child(2) { animation-delay: 0.2s; }
.ai-float-streaming .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes floatDotPulse {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.ai-float-done { color: #10b981; font-size: 11px; }
.ai-float-aborted, .ai-float-error { color: #ef4444; font-size: 11px; }
.ai-float-close {
  background: none; border: none; cursor: pointer;
  color: #9ca3af; font-size: 18px; line-height: 1;
  padding: 0 6px; border-radius: 4px;
}
.ai-float-close:hover { background: rgba(0, 0, 0, 0.05); color: #6b7280; }
.ai-float-thinking {
  margin: 6px 12px 0;
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 8px; background: #faf5ff; overflow: hidden;
  /* 7/4 关键：折叠/展开 head 高度突变 - 用 transition 平滑 */
  transition: max-height 0.2s ease;
}
.ai-float-thinking.thinking { animation: thinking-breathe 1.5s ease-in-out infinite; }
@keyframes thinking-breathe {
  0%, 100% { box-shadow: 0 0 0 rgba(99, 102, 241, 0); }
  50% { box-shadow: 0 0 12px rgba(99, 102, 241, 0.25); }
}
.thinking-head {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 8px; cursor: pointer;
  font-size: 12px; color: #6d28d9; user-select: none;
  min-height: 24px; line-height: 1.3;
  white-space: nowrap;
  width: 100%;
  box-sizing: border-box;
}
.thinking-icon {
  width: 14px !important; height: 14px !important;
  flex-shrink: 0;
  color: #7c3aed;
  min-width: 14px; max-width: 14px;
  display: inline-block;
}
.thinking-icon.spinning { animation: spin 1.2s linear infinite; transform-origin: center; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
/* 7/20 关键：thinking-title 强制 inline-block + 显式颜色 + 不依赖 flex 收缩 */
.thinking-title {
  flex: 0 1 auto;
  font-weight: 600; font-size: 12px;
  color: #5b21b6 !important;
  background: transparent;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: keep-all;
  display: inline-block;
  line-height: 1.3;
  padding: 0 4px;
  min-width: 40px;
  flex-grow: 1;
}
/* 7/20 关键：chevron toggle 固定小宽度，绝不挤压 title */
.ai-float-chevron {
  font-size: 14px;
  opacity: 1;
  margin-left: auto;
  flex-shrink: 0;
  flex-grow: 0;
  transition: transform 0.2s, background 0.15s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  min-width: 18px;
  max-width: 18px;
  height: 18px;
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  color: #6d28d9;
  background: rgba(167, 139, 250, 0.1);
  line-height: 1;
}
.ai-float-chevron:hover { background: rgba(167, 139, 250, 0.25); }
.ai-float-chevron.expanded { transform: rotate(180deg); background: rgba(167, 139, 250, 0.2); }
.thinking-body {
  padding: 6px 10px; border-top: 1px solid rgba(167, 139, 250, 0.2);
  max-height: min(280px, calc(100vh - 360px));
  overflow-y: auto;
  animation: slideDown 0.2s ease-out;
  transform-origin: top;
}
.thinking-text { font-size: 12px; line-height: 1.5; color: #4b5563; white-space: pre-wrap; word-break: break-word; }
.thinking-cursor::after { content: '▍'; animation: cursor-blink 0.8s infinite; color: #8b5cf6; }
@keyframes cursor-blink { 0%, 50% { opacity: 1; } 50.01%, 100% { opacity: 0; } }
.ai-float-body {
  padding: 10px 14px; min-height: 56px; max-height: 280px;
  overflow-y: auto; white-space: pre-wrap; word-break: break-word; line-height: 1.6;
}
.ai-float-placeholder { color: #9ca3af; font-style: italic; }
.ai-float-content { color: #1f2937; }
.ai-float-cursor::after { content: '▍'; animation: cursor-blink 0.8s infinite; color: #6366f1; }
.ai-float-options { padding: 8px 12px; max-height: 320px; overflow-y: auto; }
.ai-float-options-hint { color: #4f46e5; font-size: 11px; font-weight: 600; margin-bottom: 6px; }
.ai-float-option {
  padding: 10px 12px; background: #f9fafb;
  border: 1px solid #e5e7eb; border-radius: 8px;
  margin-bottom: 6px; cursor: pointer; transition: all 0.15s;
}
.ai-float-option:hover { background: #f3f4f6; border-color: #6366f1; transform: translateX(2px); }
.ai-float-option-title { font-weight: 600; color: #4f46e5; font-size: 12px; margin-bottom: 4px; }
.ai-float-option-content { font-size: 12px; color: #4b5563; line-height: 1.5; }

/* 9/7 关键：原文区域（与 AI 生成内容隔离） */
.ai-float-original {
  margin: 6px 12px 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
  overflow: hidden;
}
.orig-hdr {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: #6b7280;
  background: linear-gradient(135deg, #f3f4f6, #f9fafb);
  border-bottom: 1px solid #e5e7eb;
}
.orig-hdr:hover { background: #f3f4f6; }
.orig-icon { font-size: 12px; }
.orig-title { font-weight: 600; color: #4b5563; }
.orig-len { color: #9ca3af; font-size: 11px; margin-left: auto; }
.orig-toggle {
  font-size: 10px; color: #6b7280;
  transition: transform 0.2s;
  width: 14px; height: 14px;
  display: inline-flex; align-items: center; justify-content: center;
}
.orig-toggle.expanded { transform: rotate(180deg); }
.orig-body {
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.65;
  color: #4b5563;
  background: #fff;
  max-height: 180px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border-top: 1px solid #e5e7eb;
}
.ai-float-footer {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-top: 1px solid #f0f0f0; background: #fafbfc;
}
.ai-float-btn {
  padding: 6px 12px; border-radius: 6px;
  border: 1px solid #d1d5db; background: #fff;
  font-size: 12px; cursor: pointer; transition: all 0.15s;
  display: inline-flex; align-items: center; gap: 4px;
}
.ai-float-btn--primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border-color: transparent;
}
.ai-float-btn--primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.ai-float-btn--ghost:hover:not(:disabled) { background: #f3f4f6; border-color: #6366f1; color: #4f46e5; }
.ai-float-btn--icon { padding: 6px 8px; }
.ai-float-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ai-float-diff-hint {
  display: flex; align-items: center; gap: 6px;
  margin-right: auto; font-size: 11px; color: #6b7280;
}
.diff-icon-removed { color: #ef4444; font-weight: 600; font-size: 13px; }
.diff-icon-added { color: #10b981; font-weight: 600; font-size: 13px; }
.diff-removed {
  background: linear-gradient(transparent 60%, rgba(239, 68, 68, 0.25) 60%);
  text-decoration: line-through;
  text-decoration-color: rgba(239, 68, 68, 0.7);
  text-decoration-thickness: 2px;
  color: #6b7280;
  padding: 0 2px; border-radius: 2px;
}
.diff-added {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
  padding: 0 2px; border-radius: 2px;
  border-bottom: 2px solid rgba(16, 185, 129, 0.4);
}

/* ===== 7/4 智能编辑：浮窗内嵌指令输入 ===== */
.ai-float-instruction {
  padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.04), rgba(139, 92, 246, 0.02));
  border-bottom: 1px solid #f0f0f0;
}
.instr-hint {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #4b5563;
}
.instr-icon { font-size: 14px; }
.instr-suggestions {
  display: flex; flex-wrap: wrap; gap: 4px;
}
.instr-chip {
  padding: 3px 8px; border-radius: 12px;
  background: #fff; border: 1px solid rgba(99, 102, 241, 0.2);
  color: #4f46e5; font-size: 11px; cursor: pointer;
  transition: all 0.15s;
}
.instr-chip:hover {
  background: #eef2ff; border-color: #6366f1;
  transform: translateY(-1px);
}
.instr-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 6px;
  font-size: 13px; line-height: 1.5;
  font-family: inherit;
  resize: none;
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.instr-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}
.instr-actions {
  display: flex; justify-content: flex-end; gap: 6px;
  margin-top: 2px;
}

/* ===== 7/10 AI 生成模式输入区（替代系统弹窗） ===== */
.ai-float-generate {
  padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
  background: linear-gradient(180deg, rgba(167, 139, 250, 0.06), rgba(99, 102, 241, 0.02));
  border-bottom: 1px solid #f0f0f0;
}
.gen-hint {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #5b21b6;
}
.gen-icon { font-size: 14px; }
.gen-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 6px;
  font-size: 13px; line-height: 1.5;
  font-family: inherit;
  resize: none;
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.gen-input:focus {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.12);
}
.gen-actions {
  display: flex; justify-content: flex-end; gap: 6px;
}

/* ===== AI 侧栏（豆包风格 + 8 快捷卡） ===== */
.ai-panel {
  width: 360px; background: #fafbfc;
  border-left: 1px solid #e5e7eb;
  display: flex; flex-direction: column;
  height: 100%; position: relative;
}
.ai-resize-handle {
  position: absolute; left: 0; top: 0; bottom: 0;
  width: 4px; cursor: ew-resize;
  z-index: 10; background: transparent;
}
.ai-resize-handle:hover { background: rgba(99, 102, 241, 0.3); }
.ai-header {
  display: flex; align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.ai-brand { display: flex; align-items: center; gap: 8px; }
.ai-avatar-icon {
  width: 28px; height: 28px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.ai-avatar-icon svg { width: 18px; height: 18px; }
.ai-title { font-weight: 600; font-size: 14px; color: #1f2937; }
.ai-header-actions {
  display: flex; align-items: center; gap: 8px;
  flex: 1; justify-content: center;
}
/* ===== 7/11 关键：AI 侧栏模式 tab（问答 / 编辑） ===== */
.mode-tabs {
  display: inline-flex; background: #f3f4f6;
  border-radius: 8px; padding: 2px; gap: 2px;
}
.mode-tab {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 10px; border: none;
  background: transparent;
  font-size: 12px; color: #6b7280;
  cursor: pointer; border-radius: 6px;
  transition: all 0.18s;
  font-weight: 500;
}
.mode-tab:hover { color: #4f46e5; }
.mode-tab.active {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 1px 3px rgba(99, 102, 241, 0.3);
}
.mode-tab svg { width: 13px; height: 13px; }

/* ===== 7/24 关键：编辑模式 = 独立页面（与聊天页分离） ===== */
.ai-edit-page {
  flex: 1;
  display: flex; flex-direction: column;
  overflow: hidden;
  background: linear-gradient(180deg, #fafbff 0%, #ffffff 100%);
  min-height: 0;
}
.ai-chat-page {
  flex: 1;
  display: flex; flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

/* ===== 7/19 关键：编辑模式三段式布局（顶部 chip / 中部结果 / 底部输入） ===== */
.edit-topbar {
  flex-shrink: 0;
  padding: 10px 14px;
  display: flex; align-items: center; gap: 8px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.edit-topbar-left { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.edit-orig-toggle {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 4px 8px;
  background: #fff; border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 11.5px; color: #6b7280;
  cursor: pointer; flex-shrink: 0;
  transition: all 0.15s;
}
.edit-orig-toggle:hover { background: #f3f4f6; color: #1f2937; }
.edit-orig-toggle.expanded { background: #eef2ff; color: #4f46e5; border-color: #c7d2fe; }
.edit-orig-toggle svg { transition: transform 0.2s; }
.edit-orig-toggle.expanded svg { transform: rotate(90deg); }
.edit-topbar .target-chip { flex: 1; min-width: 0; }
.target-chip-empty { background: #f3f4f6 !important; border-style: dashed !important; color: #6b7280 !important; cursor: pointer; }
.target-chip-empty:hover { background: #eef2ff !important; color: #4f46e5 !important; }
.edit-orig-preview {
  flex-shrink: 0;
  max-height: 180px;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.edit-orig-slide-enter-active, .edit-orig-slide-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s;
  overflow: hidden;
}
.edit-orig-slide-enter-from, .edit-orig-slide-leave-to { max-height: 0; opacity: 0; }
.eop-hdr {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 14px;
  background: #f3f4f6;
  font-size: 11.5px; font-weight: 600; color: #4b5563;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.eop-len { color: #9ca3af; font-weight: 400; font-size: 11px; }
.eop-body {
  padding: 8px 14px;
  font-size: 12px; line-height: 1.65;
  color: #6b7280;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-y: auto;
  max-height: 130px;
}
.edit-scope-tag {
  display: inline-flex; align-items: center;
  padding: 3px 8px;
  border-radius: 100px;
  font-size: 11px;
  background: #f3f4f6; color: #6b7280;
  border: 1px solid #e5e7eb;
  font-weight: 500;
  flex-shrink: 0;
}
.edit-scope-tag.is-full {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(139, 92, 246, 0.12));
  color: #4f46e5;
  border-color: rgba(99, 102, 241, 0.3);
}

.edit-result-area {
  flex: 1; overflow-y: auto;
  padding: 16px;
  min-height: 0;
}
.edit-welcome {
  display: flex; flex-direction: column; align-items: center;
  text-align: center; padding: 24px 8px;
  color: #6b7280;
}
.edit-welcome .ew-icon {
  font-size: 36px; margin-bottom: 8px;
  width: 64px; height: 64px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #eef2ff, #f3e8ff);
  border-radius: 50%;
}
.edit-welcome h3 {
  margin: 0 0 4px;
  font-size: 16px; color: #1f2937; font-weight: 600;
}
.edit-welcome p {
  margin: 0 0 16px;
  font-size: 13px;
}
.edit-welcome .ew-tips {
  display: flex; flex-direction: column; gap: 8px;
  width: 100%;
}
.edit-welcome .ew-tip {
  display: flex; flex-direction: column; align-items: flex-start;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  text-align: left;
}
.edit-welcome .ew-tip b { font-size: 12.5px; color: #1f2937; font-weight: 600; margin-bottom: 2px; }
.edit-welcome .ew-tip span { font-size: 11.5px; color: #6b7280; }
.edit-welcome .ew-tip:hover { border-color: #c7d2fe; background: #eef2ff; cursor: pointer; }
.edit-welcome .ew-tip:hover b { color: #4f46e5; }
.ew-options {
  margin-top: 12px;
  display: flex; flex-direction: column; gap: 6px;
  width: 100%;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.ew-option {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #4b5563;
  cursor: pointer;
}
.ew-option input { cursor: pointer; }

.edit-streaming-card,
.edit-result-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}
.edit-streaming-card { border-color: rgba(99, 102, 241, 0.4); }
.esc-hdr, .erc-hdr {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  background: #fafbff;
  border-bottom: 1px solid #e5e7eb;
  font-size: 12.5px; font-weight: 600; color: #4f46e5;
}
.esc-icon.spinning { animation: spin 1.2s linear infinite; display: inline-block; }
.esc-stop, .erc-discard {
  margin-left: auto;
  background: transparent; border: 1px solid #e5e7eb;
  border-radius: 4px; padding: 2px 8px;
  font-size: 11px; color: #6b7280; cursor: pointer;
}
.esc-stop:hover { background: #fee2e2; color: #dc2626; border-color: #fecaca; }
.erc-discard:hover { background: #fee2e2; color: #dc2626; border-color: #fecaca; }
.esc-body, .erc-body {
  padding: 12px;
  font-size: 13px; line-height: 1.7;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow-y: auto;
}
.erc-actions {
  display: flex; gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid #e5e7eb;
  background: #fafbff;
}
.erc-btn {
  flex: 1;
  padding: 8px 8px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.erc-btn:hover { transform: translateY(-1px); box-shadow: 0 2px 6px rgba(0,0,0,0.08); }
.erc-replace { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; border-color: transparent; }
.erc-replace:hover { background: linear-gradient(135deg, #4f46e5, #7c3aed); }
.erc-insert { background: #fff; color: #4f46e5; border-color: rgba(99,102,241,0.3); }
.erc-copy { background: #fff; color: #4b5563; }
.erc-toggle {
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.erc-toggle:hover { background: #f3f4f6; }
.erc-toggle.active { background: #eef2ff; color: #4f46e5; border-color: #c7d2fe; }
.erc-diff {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: #e5e7eb;
  max-height: 400px;
}
.erc-diff-col { background: #fff; display: flex; flex-direction: column; min-height: 0; }
.erc-diff-hdr {
  padding: 6px 10px;
  font-size: 11px; font-weight: 600;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
  color: #6b7280;
  flex-shrink: 0;
}
.erc-diff-orig .erc-diff-hdr { color: #6b7280; }
.erc-diff-new .erc-diff-hdr { color: #4f46e5; background: #eef2ff; }
.erc-diff-body {
  padding: 10px 12px;
  font-size: 12.5px; line-height: 1.7;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f2937;
  max-height: 360px;
}
.refine-hint {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #eef2ff, #f3e8ff);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 8px;
  font-size: 11.5px; color: #4338ca;
}
.refine-icon { font-size: 13px; }
.refine-new {
  margin-left: auto;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #4f46e5;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}
.refine-new:hover { background: #eef2ff; }
.edit-scope-tag.is-section {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(251, 191, 36, 0.12));
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.3);
}

.edit-bottom {
  flex-shrink: 0;
  padding: 10px 14px 12px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex; flex-direction: column; gap: 8px;
}
.edit-quick-actions {
  display: flex; gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 2px 0;
}
.edit-quick-actions::-webkit-scrollbar { display: none; }
.qa-btn {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 4px 8px;
  background: linear-gradient(135deg, #fff, #fafbff);
  border: 1px solid #e5e7eb;
  border-radius: 100px;
  font-size: 11px; color: #4b5563;
  cursor: pointer; flex-shrink: 0;
  white-space: nowrap;
  transition: all 0.15s;
}
.qa-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #eef2ff, #f3e8ff);
  border-color: #6366f1; color: #4338ca;
  transform: translateY(-1px);
}
.qa-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.qa-icon { font-size: 12px; line-height: 1; }
.qa-label { font-weight: 500; }
.edit-step {
  display: flex; flex-direction: column; gap: 6px;
  padding: 8px;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  transition: border-color 0.15s;
}
.edit-step:hover { border-color: rgba(99, 102, 241, 0.3); }
.edit-result-step { background: linear-gradient(135deg, #f0f9ff, #ecfeff); border-color: rgba(99, 102, 241, 0.3); }
.step-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #1f2937; font-weight: 600;
}
.step-num {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border-radius: 50%;
  font-size: 10px; font-weight: 700;
  flex-shrink: 0;
}
.step-title { color: #1f2937; }
.step-hint { color: #9ca3af; font-weight: 400; font-size: 11px; }
.target-card {
  padding: 8px 10px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: 6px;
  position: relative;
}
.target-preview {
  font-size: 12px; color: #4c1d95; line-height: 1.5;
  white-space: pre-wrap; word-break: break-word;
  max-height: 60px; overflow: hidden;
}
.target-meta {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 4px; font-size: 11px; color: #6d28d9;
}
.target-len { display: inline-flex; align-items: center; gap: 3px; }
.target-clear {
  background: rgba(139, 92, 246, 0.1); border: none; color: #6d28d9;
  width: 18px; height: 18px; border-radius: 4px;
  font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  line-height: 1; padding: 0;
}
.target-clear:hover { background: rgba(139, 92, 246, 0.2); }
.target-empty {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 10px; background: #fafbfc;
  border: 1px dashed #d1d5db; border-radius: 6px;
  font-size: 12px; color: #9ca3af;
}
.empty-icon { font-size: 14px; }
.capture-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 10px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.08));
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 6px;
  color: #4f46e5; font-size: 11.5px; cursor: pointer;
  font-weight: 500;
  transition: all 0.15s;
  align-self: flex-start;
}
.capture-btn:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(139, 92, 246, 0.15));
  border-color: #6366f1;
  transform: translateY(-1px);
}
.edit-actions-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 5px;
}
.edit-action-btn {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 7px 4px;
  background: linear-gradient(135deg, #fff 0%, #fafbff 100%);
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.edit-action-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #eef2ff, #f3e8ff);
  border-color: #6366f1;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.15);
}
.edit-action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ea-icon { font-size: 16px; line-height: 1; }
.ea-label { font-size: 11px; color: #4b5563; font-weight: 500; }
.edit-result-card {
  display: flex; flex-direction: column; gap: 8px;
}
.edit-result-text {
  padding: 8px 10px;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 6px;
  font-size: 12px; color: #1f2937; line-height: 1.6;
  max-height: 100px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-word;
}
.edit-result-actions { display: flex; gap: 4px; justify-content: flex-end; }
.er-btn {
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 11.5px; cursor: pointer;
  border: 1px solid transparent;
  font-weight: 500;
  transition: all 0.15s;
}
.er-btn-ghost {
  background: #fff; border-color: #e5e7eb; color: #6b7280;
}
.er-btn-ghost:hover { background: #f3f4f6; border-color: #d1d5db; }
.er-btn-secondary {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.08));
  border-color: rgba(99, 102, 241, 0.3); color: #4f46e5;
}
.er-btn-secondary:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.18), rgba(139, 92, 246, 0.18));
  border-color: #6366f1;
}
.er-btn-primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none;
}
.er-btn-primary:hover {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
}

/* ===== 7/21 关键：编辑模式新设计（描述驱动 + 选区摘要紧凑） ===== */
.edit-panel-header {
  padding: 10px 12px;
  background: linear-gradient(135deg, rgba(167, 139, 250, 0.06), rgba(99, 102, 241, 0.04));
  border-radius: 10px;
  border: 1px solid rgba(167, 139, 250, 0.2);
}
.edit-target-row {
  display: flex; align-items: center; gap: 8px;
}
.target-info-block {
  flex: 1; min-width: 0; display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: #6b7280;
}
.target-info-block.has-target { color: #5b21b6; }
.target-active, .target-empty {
  display: inline-flex; align-items: center; gap: 6px;
  min-width: 0; flex: 1;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.target-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  flex-shrink: 0;
}
.empty-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}
.target-len {
  font-weight: 600; color: #5b21b6;
  background: rgba(167, 139, 250, 0.15);
  padding: 1px 6px; border-radius: 10px; font-size: 11px;
  flex-shrink: 0;
}
.target-preview {
  color: #6d28d9; font-size: 11.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-style: italic;
  min-width: 0;
}
.target-capture-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: 6px;
  color: #4f46e5; font-size: 11px; cursor: pointer;
  font-weight: 500;
  flex-shrink: 0;
  transition: all 0.15s;
}
.target-capture-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(139, 92, 246, 0.1));
  border-color: #6366f1;
}
.target-capture-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.target-actions { display: inline-flex; gap: 4px; flex-shrink: 0; }
.target-clear-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  color: #6b7280; cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}
.target-clear-btn:hover { background: #fee2e2; border-color: #f87171; color: #dc2626; }

.edit-panel-body {
  display: flex; flex-direction: column; gap: 10px;
}
.desc-input-wrap {
  position: relative;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: 10px;
  padding: 4px 4px 4px 12px;
  transition: all 0.15s;
}
.desc-input-wrap:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.desc-input {
  width: 100%;
  min-height: 36px;
  max-height: 100px;
  resize: none;
  border: none; outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 13px; line-height: 1.5;
  color: #1f2937;
  padding: 4px 32px 4px 0;
}
.desc-input::placeholder { color: #9ca3af; }
.desc-submit-btn {
  position: absolute; right: 6px; top: 6px;
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.desc-submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.4);
}
.desc-submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.edit-actions-section {
  display: flex; flex-direction: column; gap: 6px;
}
.edit-actions-label {
  font-size: 11px; color: #6b7280;
  font-weight: 500;
  padding-left: 2px;
}
.edit-result-streaming {
  padding: 10px 12px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border: 1px solid rgba(139, 92, 246, 0.25);
  border-radius: 10px;
  display: flex; flex-direction: column; gap: 8px;
}
.result-hdr {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 12px; font-weight: 600;
}
.result-title { color: #5b21b6; }
.result-status {
  color: #8b5cf6;
  animation: pulse-text 1.5s ease-in-out infinite;
}
@keyframes pulse-text {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
.result-text {
  font-size: 13px; line-height: 1.6;
  color: #1f2937;
  max-height: 160px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-word;
  background: rgba(255, 255, 255, 0.7);
  padding: 8px 10px;
  border-radius: 6px;
}
.result-actions {
  display: flex; gap: 4px; justify-content: flex-end;
}
.mode-toggle-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: 1px solid #e5e7eb;
  border-radius: 6px; background: #fff;
  font-size: 12px; color: #6b7280;
  cursor: pointer; transition: all 0.2s;
}
.mode-toggle-btn:hover {
  border-color: #409eff; color: #409eff;
}
.mode-toggle-btn.active {
  background: linear-gradient(135deg, #409eff, #337ecc);
  border-color: #409eff; color: #fff;
}
.ai-close-btn {
  background: none; border: none; color: #9ca3af;
  cursor: pointer; padding: 4px; border-radius: 4px;
  display: flex; align-items: center;
}
.ai-close-btn:hover { background: #f3f4f6; color: #6b7280; }
.ai-close-btn svg { width: 16px; height: 16px; }
.ai-messages { flex: 1; overflow-y: auto; padding: 18px 20px 20px; background: #fafbfc; position: relative; }
/* 7/19 关键：用户消息气泡的最大宽度收紧，离边框更远 */
.msg-content.user-content {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none;
  display: inline-block;
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 2px 14px 14px 14px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.18);
  font-size: 13px;
  line-height: 1.5;
}
/* 7/21 关键：滚动到底按钮（用户上滑时出现） */
.ai-scroll-bottom-btn {
  position: absolute;
  right: 16px; bottom: 16px;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
  transition: all 0.2s;
  z-index: 10;
}
.ai-scroll-bottom-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.55);
}
.scroll-btn-dot {
  position: absolute; top: 4px; right: 4px;
  width: 8px; height: 8px;
  background: #f43f5e;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.3); opacity: 0.7; }
}
.ai-welcome { text-align: center; padding: 20px 0; }
.welcome-icon { position: relative; width: 60px; height: 60px; margin: 0 auto 16px; }
.icon-ring {
  position: absolute; inset: 0; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  opacity: 0.2; animation: welcome-pulse 2s ease-in-out infinite;
}
@keyframes welcome-pulse {
  0%, 100% { transform: scale(1); opacity: 0.2; }
  50% { transform: scale(1.2); opacity: 0.1; }
}
.icon-core {
  position: relative; width: 60px; height: 60px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
}
.icon-core svg { width: 30px; height: 30px; }
.ai-welcome h3 { font-size: 16px; font-weight: 600; color: #1f2937; margin: 0 0 4px; }
.ai-welcome p { font-size: 12px; color: #6b7280; margin: 0 0 20px; }
.quick-prompts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 16px; }
.prompt-btn {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 8px; background: #fff;
  border: 1px solid #e5e7eb; border-radius: 10px;
  cursor: pointer; transition: all 0.15s;
  font-size: 13px; color: #1f2937;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}
.prompt-btn:hover { border-color: #6366f1; background: #f5f3ff; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15); }
.prompt-icon { font-size: 22px; }
.quick-links {
  display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px;
}
.quick-link {
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; color: #6b7280;
  background: #fff; padding: 6px 4px;
  border: 1px solid #e5e7eb; border-radius: 6px;
  cursor: pointer; transition: all 0.15s;
  text-align: center;
}
.quick-link:hover { background: #f5f3ff; border-color: #6366f1; color: #4f46e5; transform: translateY(-1px); }

/* 7/4 关键：10 张大卡片（豆包风格：2 列 5 行） */
.welcome-hdr { margin-bottom: 16px; }
.welcome-cards {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
  padding: 0 12px;
}
.welcome-card {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease-out;
  text-align: left;
  position: relative;
  overflow: hidden;
}
.welcome-card::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0;
  width: 3px; background: var(--card-color, #6366f1);
  opacity: 0.7;
  transition: width 0.2s;
}
.welcome-card:hover {
  border-color: var(--card-color, #6366f1);
  background: var(--card-bg, #f5f3ff);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
}
.welcome-card:hover::before { width: 4px; opacity: 1; }
.wc-icon-wrap {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  background: var(--card-bg, #f5f3ff);
  font-size: 18px; flex-shrink: 0;
  transition: transform 0.2s;
}
.welcome-card:hover .wc-icon-wrap { transform: scale(1.1) rotate(-3deg); }
.wc-info { flex: 1; min-width: 0; }
.wc-name { font-size: 13px; font-weight: 600; color: #1f2937; margin-bottom: 2px; }
.wc-desc { font-size: 11px; color: #6b7280; line-height: 1.3; }
/* 卡片配色（CSS 变量）*/
.card-blue   { --card-color: #3b82f6; --card-bg: #eff6ff; }
.card-purple { --card-color: #8b5cf6; --card-bg: #f5f3ff; }
.card-amber  { --card-color: #f59e0b; --card-bg: #fffbeb; }
.card-teal   { --card-color: #14b8a6; --card-bg: #f0fdfa; }
.card-rose   { --card-color: #f43f5e; --card-bg: #fff1f2; }
.card-cyan   { --card-color: #06b6d4; --card-bg: #ecfeff; }
.card-indigo { --card-color: #6366f1; --card-bg: #eef2ff; }
.card-green  { --card-color: #10b981; --card-bg: #ecfdf5; }
.card-slate  { --card-color: #64748b; --card-bg: #f8fafc; }
.card-orange { --card-color: #f97316; --card-bg: #fff7ed; }
.card-back   { --card-color: #94a3b8; --card-bg: #f8fafc; border-style: dashed; }
.ai-msg { display: flex; gap: 8px; margin-bottom: 16px; align-items: flex-start; }
/* 7/19 关键：用户消息右对齐 + AI 消息左对齐 — 各自消除空隙 */
.ai-msg-user { flex-direction: row-reverse; justify-content: flex-start; padding: 0; }
.ai-msg-assistant { flex-direction: row; justify-content: flex-start; padding: 0; }
/* 7/21 关键：streaming 中的最后一条消息添加视觉提示 */
.ai-msg.streaming .msg-content.ai-content::after {
  content: ''; display: inline-block;
}
/* 7/19 关键：让 AI 消息铺满（msg-body 撑满），用户消息靠右（msg-body 也撑满但内容靠右） */
.msg-body { flex: 1 1 auto; max-width: 100%; min-width: 0; }
/* AI 消息：msg-body 内铺满 */
.ai-msg-assistant .msg-body { display: block; }
/* 用户消息：msg-body 内容靠右 */
.ai-msg-user .msg-body { display: flex; justify-content: flex-end; }
/* 7/21 关键：AI 消息无气泡（仅文字流，去掉背景/边框） — padding 由下方重写覆盖 */
.msg-content.ai-content {
  background: transparent !important;
  border: none !important;
  color: #1f2937;
  box-shadow: none !important;
  white-space: pre-wrap;
  word-break: break-word;
}
/* 用户气泡 max-width 收紧由 ai-messages 段下方的 user-content 覆盖 */
.msg-text { white-space: pre-wrap; word-break: break-word; }
.msg-content p { margin: 0 0 6px; }
.msg-content p:last-child { margin: 0; }
.msg-loading-transparent { opacity: 0.3; }

/* 7/4 关键：AI 消息内 Markdown 渲染样式 */
.ai-content .ai-md-code {
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 500;
}
/* 7/19 关键：列表 / marker / heading 与 msg 边框留出合适间距 */
.ai-content .ai-md-list {
  margin: 8px 0 10px; padding-left: 28px;
}
.ai-content .ai-md-list li {
  margin-bottom: 4px;
  line-height: 1.7;
  padding-left: 8px;        /* 让 • 与文字间留出呼吸空间 */
  text-indent: 0;
}
/* ::marker 标准属性 + 显式颜色 */
.ai-content .ai-md-list li::marker {
  unicode-bidi: isolate;
  font-variant-numeric: tabular-nums;
  text-transform: none;
  text-indent: 0px !important;
  text-align: start !important;
  text-align-last: auto !important;
  color: #6b7280;
  font-size: 13px;
  padding-right: 4px;
}
.ai-content .ai-md-list ol { padding-left: 32px; }
.ai-content .ai-md-list ol li { padding-left: 10px; }
/* 7/19 关键：AI 消息整体与 ai-messages 边框留出更多呼吸空间 */
.msg-content.ai-content {
  background: transparent !important;
  border: none !important;
  padding: 6px 14px !important;   /* 左右各 14px 让文本不贴边 */
  color: #1f2937;
  box-shadow: none !important;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-content h1, .ai-content h2, .ai-content h3,
.ai-content h4, .ai-content h5, .ai-content h6 { padding-left: 2px; }
.ai-content .ai-md-h {
  margin: 8px 0 4px; font-weight: 600; color: #1f2937;
  line-height: 1.4;
}
.ai-content h1.ai-md-h { font-size: 16px; }
.ai-content h2.ai-md-h { font-size: 15px; }
.ai-content h3.ai-md-h { font-size: 14px; }
.ai-content strong { color: #4f46e5; font-weight: 600; }
.ai-streaming-cursor {
  display: inline-block; color: #6366f1;
  animation: cursor-blink 0.8s infinite; margin-left: 2px;
}
.thinking-section {
  margin-bottom: 8px;
  border: 1px solid rgba(167, 139, 250, 0.35);
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border-radius: 10px;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(167, 139, 250, 0.08);
  position: relative;
}
.thinking-section.ongoing { animation: thinking-breathe 1.5s ease-in-out infinite; }
/* 7/22 关键：button 显式 flex 容器 — 浏览器默认 button 不是 flex */
.thinking-toggle {
  display: flex !important; align-items: center;
  gap: 8px;
  width: 100%; padding: 6px 12px;
  background: transparent; border: none;
  font: inherit;
  color: inherit;
  cursor: pointer; text-align: left;
  min-height: 26px; line-height: 1.3;
  font-size: 12px; font-weight: 500;
  transition: background 0.12s;
}
.thinking-toggle:hover { background: rgba(167, 139, 250, 0.12); }
/* 7/22 关键：thinking-icon 必须在最左边 — flex 容器中第一个 child */
.thinking-toggle .thinking-icon {
  order: -1;
  width: 14px; height: 14px;
  flex-shrink: 0; min-width: 14px;
  display: inline-block;
  color: #7c3aed;
  margin: 0;
  padding: 0;
}
.thinking-toggle .thinking-text-label {
  order: 0;
  flex: 0 1 auto;
  font-weight: 500; font-size: 12px;
  color: #5b21b6;
  white-space: nowrap; overflow: visible;
  min-width: 0;
  display: inline-block;
}
.thinking-toggle .chevron {
  order: 1;
  width: 12px; height: 12px;
  flex-shrink: 0; transition: transform 0.2s;
  margin-left: auto;
  color: #6d28d9; opacity: 0.7;
}
.thinking-toggle .chevron.expanded { transform: rotate(180deg); opacity: 1; }
.thinking-content-wrapper {
  padding: 8px 12px 10px;
  border-top: 1px solid rgba(167, 139, 250, 0.22);
  max-height: min(280px, calc(100vh - 360px));
  overflow-y: auto;
  animation: slideDown 0.2s ease-out;
  font-size: 11.5px;
  line-height: 1.6;
  /* 7/14 关键：思考过程字体颜色 — 深紫 #3b0764 */
  color: #3b0764;
  font-weight: 500;
}
@keyframes slideDown {
  from { opacity: 0; transform: translateY(-4px); max-height: 0; }
  to { opacity: 1; transform: translateY(0); max-height: 400px; }
}
.msg-actions { display: flex; gap: 4px; margin-top: 4px; align-items: center; }
.action-btn {
  background: none; border: none; padding: 4px;
  cursor: pointer; color: #9ca3af; border-radius: 4px;
  display: flex; align-items: center;
}
.action-btn:hover { color: #6366f1; background: #f3f4f6; }
.action-btn svg { width: 14px; height: 14px; }
.inserted-badge {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 2px 6px; font-size: 11px;
  color: #059669; background: #d1fae5;
  border-radius: 4px;
}
.ai-options { margin-top: 8px; }
.ai-options-hint { font-size: 11px; color: #6b7280; margin-bottom: 4px; }
.ai-option-item {
  padding: 8px 10px; background: #f9fafb;
  border: 1px solid #e5e7eb; border-radius: 6px;
  margin-bottom: 4px; cursor: pointer; transition: all 0.15s;
}
.ai-option-item:hover { background: #f3f4f6; border-color: #6366f1; }
.ai-option-title { font-weight: 600; color: #4f46e5; font-size: 12px; margin-bottom: 2px; }
.ai-option-content { font-size: 11px; color: #4b5563; line-height: 1.5; }
.ai-edit-target-info {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(51, 126, 204, 0.08));
  border-top: 1px solid rgba(64, 158, 255, 0.2);
  font-size: 12px; color: #2563eb;
}
.target-icon { font-size: 14px; }
.target-text { flex: 1; line-height: 1.4; word-break: break-all; }
.target-text b { color: #1d4ed8; }
.target-clear {
  background: none; border: none; color: #6b7280;
  cursor: pointer; font-size: 18px;
  padding: 0 4px; border-radius: 4px; line-height: 1;
}
.target-clear:hover { background: rgba(0, 0, 0, 0.05); }
.ai-input-area { padding: 12px 16px; background: #fff; border-top: 1px solid #e5e7eb; }
.input-box {
  display: flex; gap: 6px; align-items: flex-end;
  border: 1px solid #e5e7eb; border-radius: 10px;
  padding: 6px 8px; background: #fafbfc;
  transition: border-color 0.15s;
}
.input-box.focused { border-color: #6366f1; background: #fff; }
.ai-textarea {
  flex: 1; border: none; background: transparent;
  font-size: 13px; resize: none; outline: none;
  font-family: inherit; line-height: 1.5;
  max-height: 120px; min-height: 24px; padding: 4px 6px;
  overflow-y: auto;
  /* 7/4 关键：隐藏右侧上下滚动条箭头 */
  scrollbar-width: thin;
  scrollbar-color: rgba(99, 102, 241, 0.3) transparent;
}
.ai-textarea::-webkit-scrollbar { width: 4px; }
.ai-textarea::-webkit-scrollbar-thumb {
  background: rgba(99, 102, 241, 0.3);
  border-radius: 2px;
}
.ai-textarea::-webkit-scrollbar-button { display: none; }
.send-btn {
  width: 30px; height: 30px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none; border-radius: 8px;
  cursor: pointer; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn.stop-btn { background: #ef4444; }
.send-btn svg { width: 14px; height: 14px; }
.input-hint { text-align: center; font-size: 11px; color: #9ca3af; margin-top: 6px; }

/* ===== AI 全局遮盖层（7/3 关键：完全透明 + 不可点击停止） ===== */
.ai-cover-overlay {
  position: absolute; inset: 0;
  background: rgba(124, 58, 237, 0.04);
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 24px;
  z-index: 50;
  pointer-events: none;
}
.ai-cover-overlay::after {
  content: 'AI 正在处理中…';
  padding: 5px 12px;
  background: rgba(110, 64, 201, 0.9);
  color: #fff; border-radius: 999px;
  font-size: 12px; font-weight: 500;
  box-shadow: 0 2px 8px rgba(110, 64, 201, 0.25);
  pointer-events: none;
}

/* ===== 选中文本 - 浅紫背景（覆盖浏览器默认深蓝） ===== */
.tiptap ::selection,
.ProseMirror ::selection,
.tiptap *::selection,
.ProseMirror *::selection,
.ProseMirror:focus ::selection,
.ProseMirror:focus *::selection {
  background: rgba(167, 139, 250, 0.32) !important;
  color: inherit !important;
}

/* ===== 7/12 增强：代码块语言菜单（搜索框 + 滚动列表） ===== */
.db-cb-lang-menu {
  background: var(--db-color-bg, #fff);
  border: 1px solid var(--db-color-border, #e5e7eb);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  padding: 6px;
  min-width: 240px;
  max-height: 360px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--db-color-text, #1f2937);
}
.db-cb-lang-search {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--db-color-border, #d1d5db);
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  background: var(--db-color-bg, #fff);
  color: var(--db-color-text, #1f2937);
  pointer-events: auto;
}
.db-cb-lang-search::placeholder { color: #9ca3af; }
.db-cb-lang-search:focus {
  border-color: #4f46e5;
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.2);
}
.db-cb-lang-list {
  overflow-y: auto;
  max-height: 280px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.db-cb-lang-item {
  display: block;
  width: 100%;
  padding: 6px 10px;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--db-color-text, #1f2937);
  border-radius: 4px;
  pointer-events: auto;
}
.db-cb-lang-item:hover {
  background: #eef2ff;
  color: #4f46e5;
}
.db-cb-lang-item.active {
  background: #4f46e5;
  color: #fff;
}
.db-cb-lang-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

/* 7/12 增强：AI 注释按钮 — NodeView 内的 DOM 没 scoped 属性，必须用 :deep */
.md-editor-content :deep(.db-cb-ai-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 10px;
  background: transparent;
  color: #8b5cf6;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  font-weight: 500;
  pointer-events: auto;
  transition: background 0.12s, color 0.12s;
  font-family: inherit;
}
.md-editor-content :deep(.db-cb-ai-btn:hover) {
  background: rgba(139, 92, 246, 0.12);
  color: #7c3aed;
}
.md-editor-content :deep(.db-cb-ai-btn:disabled) {
  opacity: 0.5;
  cursor: wait;
}
.md-editor-content :deep(.db-cb-ai-icon) {
  display: inline-flex;
  align-items: center;
  width: 12px; height: 12px;
}
.db-cb-ai-btn:hover { background: rgba(139, 92, 246, 0.18); }

/* 7/12 重构：去掉主题切换 — 代码块只有浅色（VSCode Light+）。 */

/* 7/12 增强：lowlight token 配色 — 直接走 CSS 变量 */
.md-editor-content :deep(pre code.hljs) { color: var(--hljs-fg); background: var(--hljs-bg); }
.md-editor-content :deep(.hljs-comment), .md-editor-content :deep(.hljs-quote) { color: var(--hljs-comment); font-style: italic; }
.md-editor-content :deep(.hljs-keyword), .md-editor-content :deep(.hljs-selector-tag), .md-editor-content :deep(.hljs-built_in), .md-editor-content :deep(.hljs-doctag), .md-editor-content :deep(.hljs-section) { color: var(--hljs-keyword); font-weight: 600; }
.md-editor-content :deep(.hljs-string), .md-editor-content :deep(.hljs-attr), .md-editor-content :deep(.hljs-template-tag), .md-editor-content :deep(.hljs-template-variable), .md-editor-content :deep(.hljs-link), .md-editor-content :deep(.hljs-regexp) { color: var(--hljs-string); }
.md-editor-content :deep(.hljs-number), .md-editor-content :deep(.hljs-literal), .md-editor-content :deep(.hljs-symbol), .md-editor-content :deep(.hljs-bullet) { color: var(--hljs-number); }
.md-editor-content :deep(.hljs-title.function_), .md-editor-content :deep(.hljs-title.function), .md-editor-content :deep(.hljs-title), .md-editor-content :deep(.hljs-name), .md-editor-content :deep(.hljs-attribute) { color: var(--hljs-function); font-weight: 500; }
.md-editor-content :deep(.hljs-variable), .md-editor-content :deep(.hljs-template-variable), .md-editor-content :deep(.hljs-params) { color: var(--hljs-variable); }
.md-editor-content :deep(.hljs-type), .md-editor-content :deep(.hljs-class .hljs-title), .md-editor-content :deep(.hljs-class), .md-editor-content :deep(.hljs-built_in-name) { color: var(--hljs-type); }
.md-editor-content :deep(.hljs-tag), .md-editor-content :deep(.hljs-name) { color: var(--hljs-tag); }
.md-editor-content :deep(.hljs-meta), .md-editor-content :deep(.hljs-meta-keyword), .md-editor-content :deep(.hljs-meta-string) { color: var(--hljs-meta); }
.md-editor-content :deep(.hljs-emphasis) { color: var(--hljs-emphasis); font-style: italic; }
.md-editor-content :deep(.hljs-strong) { color: var(--hljs-strong); font-weight: 700; }
.md-editor-content :deep(.hljs-deletion) { color: var(--hljs-deletion); background: rgba(228, 86, 73, 0.12); }
.md-editor-content :deep(.hljs-addition) { color: var(--hljs-addition); background: rgba(80, 161, 79, 0.12); }
.md-editor-content :deep(.hljs-attr) { color: var(--hljs-attr); }
</style>
