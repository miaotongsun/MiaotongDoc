<template>
  <div class="mindmap-editor">
    <!-- ===== 顶部工具栏（移除节点操作按钮，其他保留） ===== -->
    <div class="mm-toolbar">
      <el-tooltip content="大纲视图" placement="bottom"><el-button size="small" plain @click="showOutline = !showOutline" class="tb-icon-btn" :class="{ active: showOutline }" title="大纲视图"><el-icon><Notebook /></el-icon></el-button></el-tooltip>

      <el-divider direction="vertical" />

      <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom"><el-button :disabled="!canEdit" size="small" plain @click="undo" class="tb-icon-btn" title="撤销 (Ctrl+Z)"><el-icon><RefreshLeft /></el-icon></el-button></el-tooltip>
      <el-tooltip content="重做 (Ctrl+Shift+Z)" placement="bottom"><el-button :disabled="!canEdit" size="small" plain @click="redo" class="tb-icon-btn" title="重做 (Ctrl+Shift+Z)"><el-icon><RefreshRight /></el-icon></el-button></el-tooltip>

      <el-divider direction="vertical" />

      <el-tooltip content="放大 (Ctrl+加号)" placement="bottom"><el-button size="small" plain @click="zoomIn" class="tb-icon-btn" title="放大 (Ctrl+加号)"><el-icon><ZoomIn /></el-icon></el-button></el-tooltip>
      <el-tooltip content="缩小 (Ctrl+减号)" placement="bottom"><el-button size="small" plain @click="zoomOut" class="tb-icon-btn" title="缩小 (Ctrl+减号)"><el-icon><ZoomOut /></el-icon></el-button></el-tooltip>
      <el-tooltip content="缩放到 100%" placement="bottom"><el-button size="small" plain @click="zoomReset" class="tb-text-btn" title="缩放到 100%">100%</el-button></el-tooltip>
      <el-tooltip content="适应窗口" placement="bottom"><el-button size="small" plain @click="fitContent" class="tb-icon-btn" title="适应窗口"><el-icon><FullScreen /></el-icon></el-button></el-tooltip>
      <el-tooltip content="居中 (F1)" placement="bottom"><el-button size="small" plain @click="toCenter" class="tb-icon-btn" title="居中 (F1)"><el-icon><Aim /></el-icon></el-button></el-tooltip>

      <el-divider direction="vertical" />

      <el-tooltip content="展开全部" placement="bottom"><el-button size="small" plain @click="expandAll" class="tb-icon-btn" title="展开全部"><el-icon><Expand /></el-icon></el-button></el-tooltip>
      <el-tooltip content="折叠全部" placement="bottom"><el-button size="small" plain @click="collapseAll" class="tb-icon-btn" title="折叠全部"><el-icon><Fold /></el-icon></el-button></el-tooltip>
      <el-tooltip content="专注模式（聚焦当前节点）" placement="bottom"><el-button :disabled="!hasSelection" size="small" plain @click="focusNode" class="tb-icon-btn" :class="{ active: isFocusMode }" title="专注模式（聚焦当前节点）"><el-icon><View /></el-icon></el-button></el-tooltip>
      <el-tooltip :content="readOnlyMode ? '退出只读模式' : '只读模式（看图）'" placement="bottom"><el-button size="small" plain @click="toggleReadOnly" class="tb-icon-btn" :class="{ active: readOnlyMode }" :title="readOnlyMode ? '退出只读模式' : '只读模式（看图）'"><el-icon><Lock v-if="!readOnlyMode" /><View v-else /></el-icon></el-button></el-tooltip>

      <el-divider direction="vertical" />

      <el-select v-model="themeName" size="small" style="width: 80px" @change="changeTheme" class="tb-select">
        <el-option label="多彩" value="rainbow" />
        <el-option label="默认" value="default" />
        <el-option label="活力" value="fresh" />
        <el-option label="暗色" value="dark" />
      </el-select>
      <el-select v-model="directionName" size="small" style="width: 72px" @change="changeDirection" class="tb-select">
        <el-option label="双向" :value="2" />
        <el-option label="左侧" :value="0" />
        <el-option label="右侧" :value="1" />
        <el-option label="向下" :value="3" />
      </el-select>
      <el-tooltip content="紧凑模式" placement="bottom">
        <el-switch v-model="compact" size="small" @change="changeCompact" class="tb-switch" />
      </el-tooltip>

      <el-divider direction="vertical" />

      <el-input v-model="searchQuery" size="small" placeholder="搜索节点" clearable style="width: 120px" @input="onSearch" class="tb-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-divider direction="vertical" />

      <el-tooltip content="导出 JSON" placement="bottom"><el-button size="small" plain @click="exportJson" class="tb-text-btn" title="导出 JSON">JSON</el-button></el-tooltip>
      <el-tooltip content="导出 PNG" placement="bottom"><el-button size="small" plain @click="exportPng" class="tb-text-btn" title="导出 PNG">PNG</el-button></el-tooltip>
      <el-tooltip content="导出 SVG" placement="bottom"><el-button size="small" plain @click="exportSvg" class="tb-text-btn" title="导出 SVG">SVG</el-button></el-tooltip>
      <el-tooltip content="导入 JSON" placement="bottom"><el-button size="small" plain @click="importJson" class="tb-icon-btn" title="导入 JSON"><el-icon><Upload /></el-icon></el-button></el-tooltip>

      <el-divider direction="vertical" />

      <el-button :disabled="!canEdit" :loading="ai.generateStatus.value === 'streaming'" size="small" class="ai-btn ai-btn-gen" @click="openAiGenerate">
        <el-icon><MagicStick /></el-icon>
        <span v-if="ai.generateStatus.value !== 'streaming'">AI 生成</span>
        <span v-else>...</span>
      </el-button>
      <el-button :disabled="!canEdit || !hasSelection" :loading="ai.expandStatus?.value === 'streaming'" size="small" class="ai-btn ai-btn-expand" @click="aiExpand">
        <el-icon><Plus /></el-icon>
        <span v-if="ai.expandStatus?.value !== 'streaming'">AI 扩写</span>
        <span v-else>...</span>
      </el-button>
      <el-button :disabled="!mind" :loading="ai.summarizeStatus.value === 'streaming'" size="small" class="ai-btn ai-btn-summary" @click="aiSummarize">
        <el-icon><Reading /></el-icon>
        <span v-if="ai.summarizeStatus.value !== 'streaming'">AI 总结</span>
        <span v-else>...</span>
      </el-button>

      <div class="toolbar-spacer" />
      <el-tooltip content="快捷键与小技巧" placement="bottom"><el-button size="small" plain @click="helpDialogRef?.show()" class="tb-icon-btn" title="快捷键与小技巧"><el-icon><QuestionFilled /></el-icon></el-button></el-tooltip>
      <input ref="fileInputRef" type="file" accept=".json" style="display: none" @change="onImportFileChange" />
    </div><!-- ===== 主画布 ===== -->
    <div class="mm-body">
      <MindmapOutline
        v-if="showOutline && rootData.topic"
        :key="`outline-${outlineRefreshTrigger}`"
        :root="rootData"
        :current-id="currentNodeId"
        @locate="onOutlineLocate"
        @expand-all="expandAllNodes"
        @collapse-all="collapseAllNodes"
        @toggle-node="onOutlineToggleNode"
      />
      <div ref="mapEl" class="mt-map" />
      <MindmapNodeStylePanel
        v-if="selectedNode"
        :node="selectedNode"
        @update="onNodeStyleUpdate"
        @close="selectedNode = null"
      />
    </div>

    <!-- ===== 右下角浮动协同指示器（不再占主工具栏） ===== -->
    <div class="mm-collab-floater" :class="{ connected: connected }">
      <el-tooltip content="按住空格 + 拖动 = 平移画布 / 滚轮 = 缩放" placement="left">
        <span class="dot" />
      </el-tooltip>
      <span class="status-text">{{ connected ? '已连接' : '连接中...' }}</span>
      <el-tooltip content="按住空格 + 拖动 = 平移画布 / 滚轮 = 缩放" placement="top">
        <span class="pan-hint">按空格拖动</span>
      </el-tooltip>
      <span class="divider">|</span>
      <span class="users-label">在线</span>
      <div class="users-list">
        <el-tooltip
          v-for="user in onlineUsers"
          :key="user.clientId"
          :content="user.userName || '匿名'"
        >
          <el-avatar :size="22" :style="{ background: user.color || '#999', border: '2px solid white' }">
            {{ (user.userName || '?').charAt(0) }}
          </el-avatar>
        </el-tooltip>
        <span v-if="onlineUsers.length === 0" class="no-users">无</span>
      </div>
    </div>

    <!-- ===== AI 浮窗（右侧固定） ===== -->
    <Transition name="ai-slide">
      <div v-if="ai.showAiPanel.value" class="ai-side-panel">
        <div class="ai-side-header">
          <span class="ai-side-title">
            <el-icon><MagicStick /></el-icon>
            AI 助手
          </span>
          <el-button link size="small" @click="ai.showAiPanel.value = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="ai-side-body">
          <AiPanel
            :doc-id="docId"
            :doc-type="'mindmap'"
            :quick-prompts="[
              { label: '帮我扩写这个节点', prompt: '请帮我扩写这个节点为 3-5 个子节点', icon: 'Plus' },
              { label: '总结这张思维导图', prompt: '请总结这张思维导图的核心内容（200 字内）', icon: 'Document' },
              { label: 'AI 生成新主题', prompt: '我想生成一个关于「' + (mind?.currentNode?.topic || 'AI') + '」的思维导图', icon: 'MagicStick' },
            ]"
            @close="ai.showAiPanel.value = false"
          />
        </div>
      </div>
    </Transition>

    <!-- ===== AI 生成对话框 ===== -->
    <el-dialog v-model="aiGenerateVisible" title="AI 生成思维导图" width="520px" :close-on-click-modal="false">
      <el-form @submit.prevent>
        <el-form-item label="主题">
          <el-input
            v-model="aiGenerateTopic"
            placeholder="例如: AI 在企业知识管理中的应用"
            autofocus
            @keydown.enter.prevent="confirmAiGenerate"
          />
        </el-form-item>
        <div class="ai-dialog-hint">
          💡 AI 将自动生成 4-7 个一级节点 + 15-40 总节点 + 2-3 级深度，并智能打图标。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="aiGenerateVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="ai.generateStatus.value === 'streaming'"
          class="ai-btn-primary"
          @click="confirmAiGenerate"
        >
          <span v-if="ai.generateStatus.value !== 'streaming'">开始生成</span>
          <span v-else>生成中...</span>
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== AI 总结展示（简易浮窗） ===== -->
    <el-dialog :model-value="ai.summary.value.length > 0" title="AI 总结" width="520px" @close="ai.summary.value = ''">
      <div class="ai-summary-content">{{ ai.summary.value }}</div>
      <template #footer>
        <el-button @click="ai.summary.value = ''">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 快捷键帮助弹窗 ===== -->
    <MindmapHelpDialog ref="helpDialogRef" />

    <!-- ===== 自定义右键菜单（监听 MindElixir showContextMenu 事件） ===== -->
    <ul
      v-if="contextMenuVisible"
      class="mm-ctx-menu"
      :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }"
      @click.stop
      @mousedown.stop
    >
      <li @click="addChild" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Plus /></el-icon>添加子节点 <kbd>Tab</kbd>
      </li>
      <li @click="() => insertSibling('after')" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Sort /></el-icon>插入兄弟 <kbd>Enter</kbd>
      </li>
      <li @click="renameCurrentNode" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Edit /></el-icon>重命名 <kbd>F2</kbd>
      </li>
      <li class="separator"></li>
      <li @click="() => openMoveDialog('in')" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Folder /></el-icon>移动到节点下...
      </li>
      <li class="separator"></li>
      <li @click="createArrow" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Link /></el-icon>创建关联线
      </li>
      <li @click="createSummary" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Collection /></el-icon>创建摘要 <kbd>Ctrl</kbd>+点 / 框选
      </li>
      <li class="separator"></li>
      <li @click="focusNode" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><View /></el-icon>专注模式
      </li>
      <li class="separator"></li>
      <li @click="removeNode" :class="{ disabled: !canEdit || !hasSelection }">
        <el-icon><Delete /></el-icon>删除节点 <kbd>Del</kbd>
      </li>
    </ul>

    <!-- ===== 移动节点对话框 ===== -->
    <el-dialog
      v-model="moveDialogVisible"
      :title="moveDialogMode === 'in' ? '移动到节点下' : (moveDialogMode === 'before' ? '移到节点之前' : '移到节点之后')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-input v-model="moveSearchQuery" placeholder="搜索目标节点" clearable class="mb-12">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-scrollbar max-height="360px">
        <ul class="move-target-list">
          <li
            v-for="n in moveFilteredNodes"
            :key="n.id"
            :class="{ selected: moveTargetId === n.id }"
            :style="{ paddingLeft: (n.depth * 14 + 8) + 'px' }"
            @click="moveTargetId = n.id"
          >
            <span class="topic">{{ n.topic || '(空)' }}</span>
            <el-icon v-if="moveTargetId === n.id" class="check"><Check /></el-icon>
          </li>
          <el-empty v-if="moveFilteredNodes.length === 0" description="未找到节点" :image-size="60" />
        </ul>
      </el-scrollbar>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!moveTargetId" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 思维导图编辑器 v1.2（2026-08-17）
 *
 * v1.2 改动：
 * - 工具栏重设计：分两行，11 个按钮组，27+ 个功能按钮
 * - 释放 MindElixir 5.15 全部能力：
 *   ✅ insertParent 父节点  ✅ copyNode 复制  ✅ moveUp/moveDown 上下移
 *   ✅ focusNode 专注模式  ✅ createSummary 摘要  ✅ createArrow 关联线
 *   ✅ exportSvg SVG 导出  ✅ toCenter 居中  ✅ zoomReset 100%
 *   ✅ changeCompact 紧凑  ✅ expandAll/collapseAll 展开折叠
 *   ✅ initDown 方向向下
 * - AI 体验：深橙色高对比按钮 + loading 状态 + 右侧浮窗（替代弹窗）
 * - 新增 MindmapHelpDialog 快捷键总提示按钮
 * - AI 浮窗从弹窗改为右侧固定（不遮挡思维导图）
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick, toRefs } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Delete, Sort, RefreshLeft, RefreshRight, Minus,
  ZoomIn, ZoomOut, FullScreen, Search,
  Document, Picture, Upload, Notebook, MagicStick,
  Top, Bottom, CopyDocument,
  Expand, Fold, Aim, Collection, Link, View,
  PictureFilled, Close, QuestionFilled,
  Edit, Folder, Back, Right,
  EditPen, CloseBold, CollectionTag, Brush, CircleClose, Check,
  Lock,
} from '@element-plus/icons-vue'
// 部分图标（Edit/Folder/Back/Right/EditPen/CloseBold/CollectionTag/Brush/CircleClose/Check/Lock）
// 虽工具栏不再用，但右键菜单 / 移动对话框仍在使用，保留
import MindElixir from 'mind-elixir'
// NodeMenu 插件已禁用（跟 StylePanel 功能重叠 + UI 风格不搭）
// import NodeMenu from '@mind-elixir/node-menu'
import 'mind-elixir/style.css'
import MindmapOutline from '@/components/MindmapOutline.vue'
import MindmapNodeStylePanel from '@/components/MindmapNodeStylePanel.vue'
import MindmapHelpDialog from '@/components/MindmapHelpDialog.vue'
import AiPanel from '@/components/AiPanel.vue'
import {
  useMindmapCollab,
  assignRainbowColors,
  deserializeMindmap,
  serializeMindmap,
} from '@/composables/mindmap/useMindmapCollab'
import { useMindmapAi } from '@/composables/mindmap/useMindmapAi'

// ===== Props & Emits =====

const props = defineProps<{
  docId: number
  docKey: string
  canEdit: boolean
  userName: string
  userId: number
  initialContent: string
}>()

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'state-change', state: string): void
  (e: 'content-change', content: string): void
}>()

// ===== Refs =====

const mapEl = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const helpDialogRef = ref<{ show: () => void; hide: () => void } | null>(null)

const themeName = ref<'default' | 'fresh' | 'dark' | 'rainbow'>('rainbow')
const directionName = ref<number>(2)
const compact = ref(false)
const searchQuery = ref('')
const hasSelection = ref(false)
// ★ 跟踪专注模式（从 MindElixir isFocusMode 同步）
const isFocusMode = ref(false)
// ★ 多选状态（MindElixir currentNodes 长度 > 1 时为 true）
const hasMultipleSelection = computed(() => (mind as any)?.currentNodes?.length > 1)
const showOutline = ref(true)
// 自定义右键菜单状态（监听 MindElixir 'showContextMenu' 事件）
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
// ★ 用 reactive 包装 mind.nodeData，使 MindElixir 直接修改 children/属性时能触发 Vue 响应式更新
//   这样大纲（MindmapOutline）会自动跟随主画布的节点增删改，无需手动重建
const rootData = reactive<any>({})
// 大纲刷新触发器：保留为兜底（远端 refresh / AI 生成 / 主题切换等 mind 整体替换场景）
//   日常节点增删改不再依赖它，靠 reactive 自动追踪
const outlineRefreshTrigger = ref(0)

/** 把 mind.nodeData 的内容同步进 rootData，触发响应式更新（保留引用，递归合并 children） */
function syncRootData(nodeData: any) {
  if (!nodeData) return
  // ★ 先清空 rootData 的现有 keys，避免删除的节点残留
  for (const k of Object.keys(rootData)) delete (rootData as any)[k]
  // 再把 mind.nodeData 的属性浅拷贝进来（children 数组保留同一引用 → 后续 in-place mutation 自动响应式）
  Object.assign(rootData, nodeData)
}
const selectedNode = ref<any>(null)
const currentNodeId = computed(() => selectedNode.value?.id || '')

// AI 对话框
const aiGenerateVisible = ref(false)
const aiGenerateTopic = ref('')

// mind 实例
let mind: any = null

// ===== 协同 =====

// 注意：collab.connected / onlineUsers 是 Ref 对象，用 toRefs 保持响应式
//      模板会自动 unwrap toRefs 后的 ref，所以可以直接用 {{ connected ? ... }}
const collab = useMindmapCollab({
  docKey: props.docKey,
  userId: props.userId,
  userName: props.userName,
})
const { connected, onlineUsers } = toRefs(collab)

// ===== AI =====

const docIdRef = computed(() => props.docId)
const ai = useMindmapAi({ docId: docIdRef })

// AI 扩写：先显示浮窗（让用户看到 loading/流式过程），再 await
async function aiExpand() {
  if (!mind?.currentNode) {
    ElMessage.warning('请先选中一个节点')
    return
  }
  // 立即显示 AI 浮窗，让用户看到流式进度
  ai.showAiPanel.value = true
  const ok = await ai.expand(
    mind.currentNode,
    mind.nodeData,
    (newChildren: any[]) => {
      try {
        for (const c of newChildren) {
          mind.addChild(mind.currentNode, c)
        }
        emitContentChange()
      } catch (e) {
        mind?.refresh?.(mind?.getData?.())
        emitContentChange()
      }
    }
  )
  if (!ok) {
    ElMessage.warning('AI 扩写未生成内容')
  }
}

// AI 总结
async function aiSummarize() {
  if (!mind?.nodeData) return
  await ai.summarize(mind.nodeData, true)
}

// AI 生成对话框
function openAiGenerate() {
  aiGenerateTopic.value = ''
  aiGenerateVisible.value = true
}

async function confirmAiGenerate() {
  const topic = aiGenerateTopic.value.trim()
  if (!topic) {
    ElMessage.warning('请输入主题')
    return
  }
  aiGenerateVisible.value = false
  await ai.generate(topic, (data: any) => {
    if (!mind) return
    mind.refresh(data)
    if (!hasAnyColor(data.nodeData)) assignRainbowColors(data.nodeData)
    syncRootData(mind.nodeData)
    outlineRefreshTrigger.value++  // 兜底：触发 :key 重建（rootData 是新对象，结构可能整体变了）
    emitContentChange()
  })
}

// ===== 自定义主题 =====

const RAINBOW_THEME: any = {
  name: 'rainbow',
  palette: ['#FF6B6B', '#FF9F43', '#FECA57', '#10AC84', '#3742FA', '#8E44AD', '#5F27CD'],
  cssVar: {
    '--main-color': '#F59E0B',
    '--main-bgcolor': '#FFFBEB',
    '--color-background': '#ffffff',
    '--panel-background': '#fef3c7',
    '--panel-color': '#1e293b',
    '--panel-bgcolor': '#FFFBEB',
    '--panel-border-color': '#F59E0B',
  },
}

const FRESH_THEME: any = {
  name: 'fresh',
  palette: ['#6BCB77', '#4D96FF', '#FFD93D', '#FF6B6B', '#6C5CE7'],
  cssVar: {
    '--main-color': '#10AC84',
    '--main-bgcolor': '#F0FDF4',
    '--color-background': '#ffffff',
    '--panel-background': '#ECFCCB',
    '--panel-color': '#1e293b',
  },
}

// ===== MindElixir 完整 i18n（19 字段全覆盖） =====

const ZH_CN_LOCALE = {
  addChild: '插入子节点',
  addParent: '插入父节点',
  addSibling: '插入同级节点',
  removeNode: '删除节点',
  focus: '专注模式',
  cancelFocus: '取消专注',
  moveUp: '上移',
  moveDown: '下移',
  link: '连接',
  linkBidirectional: '双向连接',
  clickTips: '请双击或右键目标节点',
  summary: '摘要',
  font: '字体',
  background: '背景',
  tag: '标签',
  icon: '图标',
  tagsSeparate: '多个标签用逗号分隔',
  iconsSeparate: '多个图标用逗号分隔',
  url: 'URL',
} as any

// ===== 生命周期 =====

onMounted(async () => {
  await nextTick()
  if (!mapEl.value) {
    ElMessage.error('思维导图容器未找到')
    return
  }

  // 解析初始内容
  let initialData: any
  const parsed = deserializeMindmap(props.initialContent)
  if (parsed) {
    initialData = parsed
  } else {
    initialData = {
      nodeData: { topic: '中心主题', id: 'root', children: [] },
    }
  }

  if (!hasAnyColor(initialData.nodeData)) {
    assignRainbowColors(initialData.nodeData)
  }

  mind = new MindElixir({
    el: mapEl.value,
    direction: directionName.value,
    draggable: true,
    editable: props.canEdit,
    contextMenu: false,  // 禁用 MindElixir 内置英文右键菜单（我们用自定义中文菜单）
    toolBar: false,  // 不显示官方内置工具栏（官方只有 4 个图标，方向切换是另一组，UI 跟主题色会冲突）
    keypress: true,  // ★ 开启后，MindElixir 才会注入 container keydown listener（用于 Space → spacePressed）
    mouseSelectionButton: 0,
    // ★ 关键修复 2/2：overflowHidden 必须为 false，否则 MindElixir.js:2869 会跳过 jn(this) 调用
    //   即不注册 panHelper 的 pointerdown/pointermove/pointerup/keydown/keyup listener
    //   → spacePressed 永远没机会被使用，Space+拖动平移画布完全失效
    //   这就是之前无论怎么改都无效的真正根因！
    overflowHidden: false,
    mobileMultiSelect: false,
    theme: MindElixir.THEME,
    customTheme: [FRESH_THEME, RAINBOW_THEME],
    locale: 'cn',
  } as any)

  mind.init(initialData)
  mind.locale = 'cn'  // 手动设 locale（MindElixir 5.15 constructor 不接收 locale 参数）

  // ★ 关键修复：初始加载后补全 me-epd
  //   MindElixir init 用 requestAnimationFrame 异步渲染，80ms 时 DOM 还没完成
  //   用 requestAnimationFrame 双帧 + 300ms 兜底确保 DOM 完成后再 repairEpds
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      setTimeout(() => {
        try { repairEpds() } catch (e) { /* ignore */ }
      }, 200)
    })
  })

  // ★ 关键修复 1/2：MindElixir 5.15 把 keydown/keyup 注册在 mind.container 上（MindElixir.js:1219-1220）
  //   container 没有焦点时，浏览器不把键盘事件路由到这里 → spacePressed 永远 false → panHelper 不接管
  //   MindElixir 自己 init() 时设置了 tabindex="0"（line 2869），但需用户先点击才能聚焦
  //   在 mousedown 时主动 focus，让用户首次点击后即可 Space+拖动 pan
  
  // 替换内置右键菜单文本为中文（MindElixir 5.15 内置菜单是英文硬编码）
  const ctxMenu = mind.container?.querySelector?.('.context-menu')
  if (ctxMenu) {
    const cnMap: Record<string, string> = {
      'cm-add_child': '添加子节点', 'cm-add_parent': '插入父节点',
      'cm-add_sibling': '插入兄弟', 'cm-remove_child': '删除节点',
      'cm-fucus': '专注模式', 'cm-unfucus': '取消专注',
      'cm-up': '上移', 'cm-down': '下移',
      'cm-summary': '摘要', 'cm-link': '关联线', 'cm-link-bidirectional': '双向关联',
    }
    Object.entries(cnMap).forEach(([id, cn]) => {
      const li = ctxMenu.querySelector(`#${id}`)
      if (li) {
        const span = li.querySelector('span')
        if (span) span.textContent = cn
      }
    })
  }

  // ★ 根节点折叠/展开：在 mind.container 上做事件委托（capture phase）
  //   一次绑定，无论 me-epd 是否被重建都能捕获
  //   根节点 → toggleRootNode + stopPropagation（阻止 MindElixir 的 expandNode 崩溃）
  //   子节点 → 不拦截，让 MindElixir 自己处理
  const onEpdClick = (e: Event) => {
    const me = e as MouseEvent
    if (me.button !== 0) return  // 只处理左键
    const target = e.target as HTMLElement | null
    if (!target) return
    // 找到 me-epd（可能是 target 本身或祖先）
    const epdEl = target.tagName === 'ME-EPD' ? target : (target.closest?.('me-epd') as HTMLElement | null)
    if (!epdEl) return
    // 找到对应的 me-tpc（me-epd 的前一个兄弟元素）
    const tpcEl = epdEl.previousElementSibling as HTMLElement | null
    if (!tpcEl || tpcEl.tagName !== 'ME-TPC') return
    // 判断是否根节点
    const isRoot = epdEl.parentElement?.tagName === 'ME-ROOT'
    if (isRoot) {
      // ★ 根节点：拦截 + 自定义处理（不让 MindElixir 的 expandNode 触发，会崩）
      e.stopPropagation()
      e.stopImmediatePropagation()
      e.preventDefault()
      toggleRootNode(tpcEl)
    }
    // 子节点：不拦截，让 MindElixir 的 click handler 正常处理 expandNode
  }
  mind.container.addEventListener('click', onEpdClick, true)  // capture phase
  ;(mind as any).__epdClickCleanup = () => {
    mind?.container?.removeEventListener?.('click', onEpdClick, true)
  }

  changeTheme('rainbow', false)

  setTimeout(() => {
    try { mind?.scaleFit?.() } catch (e) { /* ignore */ }
  }, 200)

  // 绑定协同
  collab.bindMindElixir(mind)

  // ★ 监听 MindElixir bus 同步本地状态（专注模式、hasSelection、me-epd 补全、大纲刷新）
  mind.bus?.addListener?.('operation', (op: any) => {
    if (op.name === 'focusNode') {
      isFocusMode.value = (mind as any).isFocusMode === true
    }
    if (op.name === 'finishEdit' || op.name === 'selectNode') {
      isFocusMode.value = (mind as any).isFocusMode === true
      hasSelection.value = !!mind.currentNode
    }
    // ★ 节点增删改 → 重新补 me-epd（MindElixir 重渲染会丢失 onclick）
    //   大纲由 reactive(rootData) 自动追踪 mutation，无需触发 :key 重建
    if (['addChild', 'insertSibling', 'insertParent', 'removeNode', 'removeNodes',
         'finishEdit', 'moveNodeAfter', 'moveNodeBefore', 'moveNodeIn', 'reshapeNode'].includes(op.name)) {
      setTimeout(() => repairEpds(), 0)
    }
  })
  // ★ MindElixir 5.15 expandNode 内部 children[1].remove() 会删 me-epd（design quirk）
  //   监听 expandNode 操作后立即补 me-epd
  mind.bus?.addListener?.('expandNode', () => {
    setTimeout(() => repairEpds(), 0)
  })
  // ★ 远程数据刷新后补 me-epd（Yjs 同步数据 → mind.refresh() → DOM 重建 → 需要重新补 epd）
  //   同时同步 rootData（mind.nodeData 已经被 refresh 替换为新对象）
  mind.bus?.addListener?.('remoteRefresh', () => {
    syncRootData(mind.nodeData)
    setTimeout(() => repairEpds(), 0)
  })

  // 不安装 node-menu 插件（2026-08-22 用户确认移除）
  // 原因：① UI 风格跟项目不搭（英文 + 蓝色实线边框 + 简陋 SVG 图标）
  //       ② 功能跟 MindmapNodeStylePanel 完全重叠（字体/背景/标签/图标/URL 我们都有）
  //       ③ 占据画布右上角固定位置，挡住 AI 浮窗和协同指示器
  // 我们的方案：右侧 MindmapNodeStylePanel（琥珀橙主题）+ 右键菜单（高频操作）
  // try { mind.install(NodeMenu) } catch (e) { console.warn('install NodeMenu failed', e) }

  // 节点选中：mousedown bubble（在 mind.container 上）
  // ★ 不处理 Ctrl+click 多选 — 让 MindElixir 自己处理（line 1158: e.selection?.select(b)）
  //   我们只负责同步 viselect 选中状态到 mind.currentNodes（在 pointerup 后做）
  //   之前我们手动调 mind.selectNodes/unselectNodes 跟 MindElixir 的 viselect 冲突
  const onMapMouseDown = (e: MouseEvent) => {
    try { (mind?.container as HTMLElement)?.focus?.({ preventScroll: true }) } catch { /* ignore */ }
    if (e.button === 2) return  // 右键交给 contextmenu listener
    const t = e.target as HTMLElement
    const tpc = t.tagName === 'ME-TPC' ? t : (t.closest?.('me-tpc') as HTMLElement | null)
    if (!tpc || !mind) return
    // ★ 不处理 Ctrl+click — MindElixir 内部 viselect 会处理
    //   只在非 Ctrl 单击时更新 selectedNode（右侧 StylePanel 显示的节点）
    if (!e.ctrlKey && !e.metaKey) {
      if (tpc.nodeObj && selectedNode.value?.id !== tpc.nodeObj.id) {
        selectedNode.value = tpc.nodeObj
        hasSelection.value = true
      }
    }
    // Ctrl+click: 不干预，让 MindElixir + viselect 处理
  }
  mind.container.addEventListener('mousedown', onMapMouseDown, false)
  ;(mind as any).__mdCleanup = () => {
    mind?.container?.removeEventListener?.('mousedown', onMapMouseDown, false)
  }

  // ★ 关键修复：mouseup 后同步 viselect 选中 → mind.currentNodes
  //   MindElixir 的 viselect（框选 + Ctrl+click）只更新 viselect 内部状态，
  //   不更新 mind.currentNodes。但 createSummary 用的是 mind.currentNodes！
  //   所以必须在每次 pointerup 后手动同步。
  const onContainerMouseUp = (e: MouseEvent) => {
    if (e.button !== 0) return
    const t = e.target as HTMLElement
    if (!t) return
    if (t.closest?.('.mm-toolbar')) return
    if (t.closest?.('.ai-side-panel')) return
    if (t.closest?.('.mm-collab-floater')) return
    if (t.closest?.('.move-target-list')) return
    // 延迟执行 — 让 viselect 的 pointerup handler 先完成
    setTimeout(() => {
      if (!mind) return
      // ★ 同步 viselect 选中 → mind.currentNodes
      try {
        const viselectSelected = mind.selection?.getSelection?.() || []
        if (viselectSelected.length > 0) {
          // viselect 有选中 → 同步到 currentNodes
          ;(mind as any).currentNodes = viselectSelected
          hasSelection.value = true
          // 如果只选了 1 个，也设为 currentNode
          if (viselectSelected.length === 1) {
            ;(mind as any).currentNode = viselectSelected[0]
            if (viselectSelected[0].nodeObj) selectedNode.value = viselectSelected[0].nodeObj
          }
          // 更新 hasSelection（hasMultipleSelection 是 computed 会自动更新）
          hasSelection.value = true
          return
        }
      } catch { /* ignore */ }
      // viselect 无选中 → 检查是否点空白
      if (!t.closest?.('me-tpc')) {
        const cur = mind.currentNode
        const selNodes = (mind as any).currentNodes || []
        if (selNodes.length < 2 && cur && !t.closest?.('.mm-ctx-menu')) {
          clearAllSelection()
        }
      }
    }, 50)
  }
  mind.container.addEventListener('mouseup', onContainerMouseUp, false)
  ;(mind as any).__mdCleanupUp = () => {
    mind?.container?.removeEventListener?.('mouseup', onContainerMouseUp, false)
  }

  // 右键菜单：直接监听浏览器的 contextmenu 事件（不依赖 MindElixir 内部 showContextMenu）
  // 这是最可靠的方案：浏览器右键 → contextmenu 事件 → 我们的 listener → 显示中文菜单
  const onContextMenu = (e: MouseEvent) => {
    const t = e.target as HTMLElement
    const tpc = t.tagName === 'ME-TPC' ? t : (t.closest?.('me-tpc') as HTMLElement | null)
    if (!tpc) return
    e.preventDefault()
    if (!mind) return
    // ★ 关键修复：如果已有多选且点击的节点在选中列表里 → 保留多选（不清选区）
    //   之前 mind.selectNode(tpc, false) 会把多选变成单选，导致框选视觉消失
    const currentNodes = (mind as any).currentNodes || []
    const isAlreadySelected = currentNodes.includes(tpc)
    if (!isAlreadySelected) {
      // 点击的节点不在选中列表 → 选中它（替换多选）
      try { mind.selectNode(tpc, false) } catch { /* ignore */ }
    }
    // 如果已有多选且点击的节点在列表里 → 不改变选区

    // ★ 防止菜单出界
    const menuWidth = 220
    const menuHeight = 360
    let x = e.clientX
    let y = e.clientY
    if (x + menuWidth > window.innerWidth) x = window.innerWidth - menuWidth - 8
    if (y + menuHeight > window.innerHeight) {
      y = e.clientY - menuHeight
      if (y < 0) y = 8
    }
    contextMenuX.value = x
    contextMenuY.value = y
    contextMenuVisible.value = true
  }
  mind.container.addEventListener('contextmenu', onContextMenu, false)
  ;(mind as any).__ctxMenuCleanup = () => {
    mind?.container?.removeEventListener?.('contextmenu', onContextMenu, false)
  }

  syncRootData(mind.nodeData)

  // 右键菜单自动关闭：点击外部或按 Esc
  document.addEventListener('mousedown', (e) => {
    if (e.button === 2) return  // 右键 mousedown 不关
    // 延迟关闭，让菜单项的 click 先执行
    setTimeout(() => { contextMenuVisible.value = false }, 150)
  })
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') contextMenuVisible.value = false })

  ;(window as any).__mind = mind  // 调试用：E2E 测试访问 mind 实例
  emit('ready')
  emit('state-change', 'ready')
})

onBeforeUnmount(() => {
  // ★ 退出前立即保存（不等 1.5s debounce）
  try {
    if (mind) {
      const json = serializeMindmap(mind.getData())
      emit('content-change', json)
    }
  } catch (e) { /* ignore */ }
  // 关闭右键菜单
  const closeContextMenu = () => { contextMenuVisible.value = false }
  document.removeEventListener('click', closeContextMenu)
  document.removeEventListener('keydown', closeContextMenu)
  if (mind && typeof mind.destroy === 'function') {
    try { mind.destroy() } catch (e) { /* ignore */ }
  }
  const cleanup = (mind as any)?.__mdCleanup
  if (cleanup) cleanup()
  const cleanupUp = (mind as any)?.__mdCleanupUp
  if (cleanupUp) cleanupUp()
  const epdCleanup = (mind as any)?.__epdClickCleanup
  if (epdCleanup) epdCleanup()
  const ctxCleanup = (mind as any).__ctxMenuCleanup
  if (ctxCleanup) ctxCleanup()
  mind = null
  collab.destroy()
})

// ===== 节点操作 =====

// ★ 强制刷新辅助：MindElixir 5.15 addChild/insertSibling/removeNodes 等操作已自动调用 linkDiv + 重建 DOM
//   我们只需要：1) 同步 selectedNode 引用 2) 给父节点手动补 me-epd（MindElixir addChild 不自动补）
function refreshAndReselect() {
  if (!mind) return
  try {
    const savedId = selectedNode.value?.id || mind.currentNode?.nodeObj?.id
    if (savedId) {
      const el = mind.findEle(savedId)
      if (el) {
        // ★ 只做视觉选中，不自动弹出样式面板（selectedNode 只在用户明确点击节点时设置）
        mind.selectNode(el, true)
      }
    }
    // ★ setTimeout 后补 me-epd（layout 完成后操作 DOM，避免冲突）
    setTimeout(() => repairEpds(), 80)
  } catch (e) { /* ignore */ }
}

// ★ 给有 children 的节点手动创建 me-epd（MindElixir 5.15 addChild 不自动给父节点加 epd）
//   **根因 + 解决方案**：
//   MindElixir 5.15 expandNode（MindElixir.js:793-796）hardcoded 读 `i.parentNode.children[1]` 删 me-epd。
//   这意味着 MindElixir 把 me-epd 当成 children 容器 —— 内部状态和 DOM 结构不一致。
//   我们的策略：用 ensureEpdFor 创建 me-epd 时**注册 capture-phase click handler**，
//     先于 MindElixir 的 bubble-phase listener 执行 + stopPropagation
//   MindElixir 删 epd 后，bus 'expandNode' 事件触发 repairEpds 补回
function repairEpds() {
  if (!mind || !mind.map) {
    console.warn('[repairEpds] mind or mind.map is null')
    return
  }
  const wrappers = mind.map.querySelectorAll('me-wrapper')
  console.log('[repairEpds] wrappers:', wrappers.length)
  wrappers.forEach((w: any) => {
    const tpc = w.querySelector('me-parent > me-tpc')
    if (!tpc || !tpc.nodeObj) return
    if (tpc.nodeObj.children?.length > 0) ensureEpdFor(tpc)
    else w.querySelectorAll('me-epd').forEach((e: any) => e.remove())
  })
  // root 节点
  const rootTpc = mind.map.querySelector('me-root > me-tpc') as HTMLElement | null
  console.log('[repairEpds] rootTpc:', !!rootTpc, 'children:', rootTpc?.nodeObj?.children?.length)
  if (rootTpc?.nodeObj?.children?.length > 0) {
    const result = ensureEpdFor(rootTpc)
    console.log('[repairEpds] ensureEpdFor result:', !!result)
  }
  else mind.map.querySelectorAll('me-root > me-epd').forEach((e: any) => e.remove())
}

// ★ 创建/补回 me-epd（只创建 DOM 元素 + onclick 属性）
//   onclick 属性比 addEventListener 更可靠（不会被 stopImmediatePropagation 影响）
function ensureEpdFor(tpc: HTMLElement): HTMLElement | null {
  const parent = tpc.parentElement  // me-parent 或 me-root
  if (!parent) return null
  let epd = parent.querySelector('me-epd') as HTMLElement | null
  if (!epd) {
    epd = document.createElement('me-epd')
    epd.className = 'minus'
    tpc.insertAdjacentElement('afterend', epd)
  }
  epd.className = (tpc as any).nodeObj?.expanded === false ? '' : 'minus'
  epd.setAttribute('aria-label', (tpc as any).nodeObj?.expanded === false ? '展开' : '折叠')
  // ★ 用 onclick 属性（最可靠，不会被 MindElixir 的事件机制干扰）
  //   之前用 addEventListener capture + stopImmediatePropagation 在真实浏览器不稳定
  ;(epd as any).onclick = (e: MouseEvent) => {
    if (e.button !== 0) return  // 只处理左键
    e.stopPropagation()
    e.preventDefault()
    const isRoot = tpc.parentElement?.tagName === 'ME-ROOT'
    if (isRoot) {
      toggleRootNode(tpc)
    } else {
      // 子节点：让 MindElixir 处理（不 stopPropagation 让 MindElixir 的 click handler 也能收到）
      mind?.expandNode?.(tpc)
    }
  }
  return epd
}

// ★ Root 节点自定义折叠/展开（绕开 MindElixir expandNode 的 linkDiv null 崩溃）
//   MindElixir 5.15 官方不支持根节点折叠（expandNode 内部 linkDiv(e.closest("me-main > me-wrapper"))
//   对 root 返回 null 会崩）。我们完全自己实现：直接改 DOM 显隐 + nodeObj.expanded。
//   nodeObj fallback：如果 me-tpc.nodeObj 丢失（MindElixir 重渲染后），用 mind.nodeData
function toggleRootNode(rootTpc: HTMLElement) {

  const rootEl = rootTpc.parentElement  // me-root
  if (!rootEl) return
  // ★ nodeObj fallback：me-tpc.nodeObj 可能在 MindElixir 重渲染后丢失
  const nodeObj = (rootTpc as any).nodeObj || mind?.nodeData
  if (!nodeObj) return

  // 切换 expanded 字段
  const wasExpanded = nodeObj.expanded !== false
  nodeObj.expanded = !wasExpanded

  // ★ 查找 me-nodes → 隐藏/显示 me-main（子节点容器）
  const mapCanvas = rootEl.parentElement  // me-nodes
  let nodesEl: HTMLElement | null = null
  if (mapCanvas?.tagName === 'ME-NODES') {
    nodesEl = mapCanvas
  } else {
    // 兜底：从 container 找
    nodesEl = mind?.map?.querySelector('me-nodes') || null
  }
  if (nodesEl) {
    // ★ 用 visibility:hidden（不是 display:none）— 不引起 CSS reflow，根节点位置保持稳定
    //   display:none 会让兄弟元素重新布局，导致根节点位置跳动
    nodesEl.querySelectorAll('me-main, svg.lines').forEach((el: HTMLElement) => {
      el.style.visibility = nodeObj.expanded ? '' : 'hidden'
    })
  }

  // 同步 epd className + aria-label
  const epd = rootEl.querySelector('me-epd') as HTMLElement | null
  if (epd) {
    epd.className = nodeObj.expanded ? 'minus' : ''
    epd.setAttribute('aria-label', nodeObj.expanded ? '折叠' : '展开')
  }

  // ★ 不调用 toCenter（会让根节点在画布中跳位）
  //   让 MindElixir 的 layout 自然处理位置，展开/折叠根节点位置保持一致

  // ★ toggleRootNode 不触发 MindElixir bus 事件，直接保存即可（rootData 由 reactive 自动追踪）
  emitContentChange()
}

function addChild() {
  if (!mind || !props.canEdit) return
  mind.addChild().then(() => {
    refreshAndReselect()
    emitContentChange()
  }).catch(() => {
    // addChild 失败也要尝试保存（防止数据丢失）
    emitContentChange()
  })
}

function insertSibling(position: 'before' | 'after' = 'after') {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.insertSibling(position, mind.currentNode).then(() => {
    refreshAndReselect()
    emitContentChange()
  }).catch(() => { emitContentChange() })
}

// ★ 批量复制（MindElixir copyNodes）
function copyNodes() {
  if (!mind || !props.canEdit) return
  if (!mind.currentNodes || mind.currentNodes.length === 0) return
  // 复制到选中节点的父节点下（MindElixir 原生行为）
  mind.copyNodes(mind.currentNodes, mind.currentNode || mind.currentNodes[0])
  ElMessage.success(`已复制 ${mind.currentNodes.length} 个节点`)
  emitContentChange()
}

// ★ 多选支持：按住 Ctrl/Meta 切换选中
function onNodeMultiSelect(tpc: HTMLElement, e: MouseEvent) {
  if (!mind) return
  if (e.ctrlKey || e.metaKey) {
    // 添加/移除选中
    if (mind.currentNodes?.includes(tpc)) {
      mind.unselectNodes([tpc])
    } else {
      mind.selectNodes([tpc])
    }
  } else {
    mind.selectNode(tpc, false)
  }
}

function insertParent() {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.insertParent(mind.currentNode).then(() => emitContentChange())
}

function copyNode() {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.copyNode(mind.currentNode, mind.currentNode).then(() => emitContentChange())
  ElMessage.success('节点已复制')
}

function removeNode() {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.removeNodes([mind.currentNode]).then(() => emitContentChange())
}

function moveNodeUp() {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.moveUpNode(mind.currentNode).then(() => emitContentChange())
}

function moveNodeDown() {
  if (!mind || !props.canEdit || !mind.currentNode) return
  mind.moveDownNode(mind.currentNode).then(() => emitContentChange())
}

// ===== 历史 =====

function undo() {
  if (!mind || !props.canEdit) return
  mind.undo()
  emitContentChange()
}

function redo() {
  if (!mind || !props.canEdit) return
  mind.redo()
  emitContentChange()
}

// ===== 视图 =====

function zoomIn() {
  if (!mind) return
  const next = Math.min((mind.scaleVal || 1) * 1.2, mind.scaleMax || 2)
  mind.scale(next)
}

function zoomOut() {
  if (!mind) return
  const next = Math.max((mind.scaleVal || 1) / 1.2, mind.scaleMin || 0.2)
  mind.scale(next)
}

function zoomReset() {
  if (!mind) return
  mind.scale(1)
}

function fitContent() {
  if (!mind) return
  mind.scaleFit()
  mind.toCenter()
}

function toCenter() {
  if (!mind) return
  mind.toCenter()
}

function expandAll() {
  if (!mind) return
  // expandNodeAll 需要 DOM 元素（me-tpc），不是 nodeObj
  const rootEl = mind.map?.querySelector('me-root me-tpc')
  if (rootEl) {
    try {
      mind.expandNodeAll(rootEl, true)
      emitContentChange()
    } catch (e) {
      // 根节点 fallback：递归设 expanded=true
      const setExpanded = (n: any, val: boolean) => {
        n.expanded = val
        if (n.children) n.children.forEach((c: any) => setExpanded(c, val))
      }
      setExpanded(mind.nodeData, true)
      try { mind.refresh(mind.getData()) } catch {}
      emitContentChange()
    }
  }
}

function collapseAll() {
  if (!mind) return
  const rootEl = mind.map?.querySelector('me-root me-tpc')
  if (rootEl) {
    try {
      mind.expandNodeAll(rootEl, false)
      emitContentChange()
    } catch (e) {
      // fallback
      const setExpanded = (n: any, val: boolean) => {
        // 根节点不折叠
        if (n.parent) n.expanded = val
        if (n.children) n.children.forEach((c: any) => setExpanded(c, val))
      }
      setExpanded(mind.nodeData, false)
      try { mind.refresh(mind.getData()) } catch {}
      emitContentChange()
    }
  }
}

// ★ 大纲面板专用的展开/折叠（不带 emitContentChange 避免不必要的协同广播）
function expandAllNodes() {
  if (!mind) return
  const rootEl = mind.map?.querySelector('me-root me-tpc')
  if (rootEl) {
    try { mind.expandNodeAll(rootEl, true) } catch {}
  }
  // ★ 关键修复：expandNodeAll 删除了 me-epd 后没补回
  setTimeout(() => { repairEpds(); outlineRefreshTrigger.value++ }, 50)
}

function collapseAllNodes() {
  if (!mind) return
  const rootEl = mind.map?.querySelector('me-root me-tpc')
  if (rootEl) {
    try { mind.expandNodeAll(rootEl, false) } catch {}
  }
  // ★ 同上
  setTimeout(() => { repairEpds(); outlineRefreshTrigger.value++ }, 50)
}

// ★ 大纲面板的单个节点折叠/展开
function onOutlineToggleNode(node: any) {
  if (!mind || !node) return
  try {
    const el = mind.findEle?.(node.id)
    if (!el) return
    if (el.parentElement?.tagName === 'ME-ROOT') {
      toggleRootNode(el as HTMLElement)  // toggleRootNode 内部已调 emitContentChange
    } else {
      mind.expandNode(el)  // 子节点
      emitContentChange()  // 保存折叠/展开状态
    }
  } catch (e) { /* ignore */ }
}

// ===== 关联/摘要/专注 =====

// ★ createSummary: MindElixir 5.15 官方 API
//   1. 需要 mind.currentNodes（非空数组，不能包含 root）
//   2. 选中的节点必须是同一父节点下的兄弟节点
//   3. createSummary({ style? }) 自动在选中节点范围上画包围线
//   4. 创建后自动调用 editSummary（让用户输入标签）
function createSummary() {
  if (!mind || !props.canEdit) return
  // ★ 同步 viselect 选中 → currentNodes（确保 createSummary 能读到）
  try {
    const viselectSelected = mind.selection?.getSelection?.() || []
    if (viselectSelected.length > 0) {
      ;(mind as any).currentNodes = viselectSelected
    }
  } catch { /* ignore */ }

  const currentNodes = (mind as any).currentNodes || []
  if (currentNodes.length === 0) {
    ElMessage.warning('请先选中节点（Ctrl+点击 或 空白处拖动框选），再创建摘要')
    return
  }
  // 检查是否有 root 节点（不能给 root 创建摘要）
  const hasRoot = currentNodes.some((n: any) => !n?.nodeObj?.parent)
  if (hasRoot) {
    ElMessage.warning('不能对根节点创建摘要，请选择子节点')
    return
  }
  try {
    mind.createSummary({ style: { color: '#F59E0B', background: '#FFFBEB' } })
    ElMessage.success(`已创建摘要（包围 ${currentNodes.length} 个节点）`)
    emitContentChange()
  } catch (e) {
    console.warn('createSummary failed', e)
    ElMessage.warning('创建摘要失败：' + (e as Error).message + '（需要选中同一父节点下的兄弟节点）')
  }
}

// ★ 关联线两步流程：MindElixir 5.15 createArrow(from, to, options?) 接收 me-tpc DOM 元素
//   之前传 nodeObj 导致失败 → 现在先通过 findEle(nodeId) 找 DOM
const arrowFromTpc = ref<any>(null)
function createArrow() {
  if (!mind || !props.canEdit) return
  if (!mind.currentNode) {
    ElMessage.warning('请先选中一个节点作为起点')
    return
  }
  // 第一次点：标记起点，进入 selecting 状态
  // currentNode 在 MindElixir 5.15 是 me-tpc DOM 元素 + .nodeObj 属性
  arrowFromTpc.value = mind.currentNode
  ElMessage.success(`已选起点【${mind.currentNode.nodeObj?.topic || '?'}】,再点画布上的目标节点`)
  // 监听 mousedown（在画布上第二次点击时完成连线）
  const onNextClick = (e: MouseEvent) => {
    if ((e as any).button === 2) return  // 右键忽略
    const t = e.target as HTMLElement
    if (!t) return
    const tpc = t.tagName === 'ME-TPC' ? t : (t.closest?.('me-tpc') as HTMLElement | null)
    if (!tpc || !mind) return
    // 不能连自己
    if (tpc === arrowFromTpc.value) {
      ElMessage.warning('起点和终点不能是同一节点')
      return
    }
    try {
      // MindElixir 5.15 createArrow 接收 me-tpc DOM 元素
      mind.createArrow(arrowFromTpc.value, tpc, { label: '关联', style: { color: '#F59E0B' } })
      ElMessage.success('关联线已创建')
      emitContentChange()
    } catch (err) {
      console.error('createArrow failed', err)
      ElMessage.error('创建失败：' + (err as Error).message)
    }
    arrowFromTpc.value = null
    document.removeEventListener('mousedown', onNextClick, true)
  }
  document.addEventListener('mousedown', onNextClick, true)
  // 10 秒超时自动取消
  setTimeout(() => {
    if (arrowFromTpc.value) {
      arrowFromTpc.value = null
      document.removeEventListener('mousedown', onNextClick, true)
      ElMessage.info('已取消关联线创建')
    }
  }, 10000)
}

// ★ 删除当前选中关联线（MindElixir 5.15 removeArrow(arrow?)）
function removeArrow() {
  if (!mind || !props.canEdit) return
  if (!mind.currentArrow) {
    ElMessage.warning('请先选中一条关联线')
    return
  }
  try {
    mind.removeArrow(mind.currentArrow)
    ElMessage.success('关联线已删除')
    emitContentChange()
  } catch (e) {
    console.warn('removeArrow failed', e)
    ElMessage.warning('删除失败：' + (e as Error).message)
  }
}

// ★ 编辑关联线标签（MindElixir 5.15 editArrowLabel(arrow)）
function editArrowLabel() {
  if (!mind || !mind.currentArrow) return
  try {
    mind.editArrowLabel(mind.currentArrow)
  } catch (e) {
    ElMessage.warning('编辑失败：' + (e as Error).message)
  }
}

// ★ 编辑摘要标签（MindElixir 5.15 editSummary(summary)）
function editSummary() {
  if (!mind || !mind.currentSummary) return
  try {
    mind.editSummary(mind.currentSummary)
  } catch (e) {
    ElMessage.warning('编辑摘要失败：' + (e as Error).message)
  }
}

function focusNode() {
  if (!mind || !mind.currentNode) return
  try {
    mind.focusNode(mind.currentNode)
    // ★ MindElixir focusNode 不触发 bus operation 事件，需手动更新 isFocusMode
    isFocusMode.value = (mind as any).isFocusMode === true
  } catch (e) {
    ElMessage.warning('专注失败')
  }
}

// ★ 退出专注（MindElixir 5.15 cancelFocus）
function cancelFocusMode() {
  if (!mind) return
  try {
    mind.cancelFocus()
    isFocusMode.value = false
  } catch (e) {
    ElMessage.warning('退出专注失败：' + (e as Error).message)
  }
}

// ===== MindElixir v5 全部 API 实现（2026-08-22 补全） =====

// ★ 清除所有选中（点击空白画布时自动调用）
function clearAllSelection() {
  if (!mind) return
  try {
    // clearSelection: mind.selection?.clear() + currentNodes=[] + currentSummary/Arrow=null
    if (typeof mind.clearSelection === 'function') {
      mind.clearSelection()
    } else {
      // 兜底：手动调
      if (Array.isArray(mind.currentNodes)) {
        mind.unselectNodes?.(mind.currentNodes)
      }
      mind.unselectSummary?.()
      mind.unselectArrow?.()
    }
    selectedNode.value = null
    hasSelection.value = false
  } catch (e) { console.warn('clearSelection failed', e) }
}

// ★ 切换只读模式（看图模式 vs 编辑模式）
function toggleReadOnly() {
  if (!mind) return
  readOnlyMode.value = !readOnlyMode.value
  if (readOnlyMode.value) {
    mind.disableEdit?.()
    ElMessage.success('已进入只读模式')
  } else {
    mind.enableEdit?.()
    ElMessage.success('已退出只读模式')
  }
}
const readOnlyMode = ref(false)

// ★ 移动节点：移到另一个节点下（change parent）
function moveNodeTo(targetNode: any) {
  if (!mind || !mind.currentNode) {
    ElMessage.warning('请先选中源节点')
    return
  }
  if (targetNode === mind.currentNode.nodeObj) {
    ElMessage.warning('不能移动到自身')
    return
  }
  // 防止循环引用：目标不能是源的后代
  if (isDescendant(targetNode, mind.currentNode.nodeObj)) {
    ElMessage.warning('不能移动到自身后代')
    return
  }
  try {
    const toEl = mind.findEle ? mind.findEle(targetNode.id) : mind.currentNode
    if (!toEl) return
    // moveNodeIn(tpcs, toTpc) — 把 source 移到 target 的子节点
    mind.moveNodeIn?.([mind.currentNode], toEl)
    ElMessage.success('已移动到「' + (targetNode.topic || '?') + '」下')
    emitContentChange()
  } catch (e) {
    ElMessage.error('移动失败：' + (e as Error).message)
  }
}

// ★ 移动到目标之前
function moveNodeBeforeTarget(targetNode: any) {
  if (!mind || !mind.currentNode || !targetNode.parent) {
    ElMessage.warning('只能在同级兄弟间移动')
    return
  }
  const targetEl = mind.findEle ? mind.findEle(targetNode.id) : null
  if (!targetEl) return
  try {
    mind.moveNodeBefore?.(mind.currentNode, targetEl)
    ElMessage.success('已移到「' + (targetNode.topic || '?') + '」之前')
    emitContentChange()
  } catch (e) {
    ElMessage.error('移动失败：' + (e as Error).message)
  }
}

// ★ 移动到目标之后
function moveNodeAfterTarget(targetNode: any) {
  if (!mind || !mind.currentNode || !targetNode.parent) {
    ElMessage.warning('只能在同级兄弟间移动')
    return
  }
  const targetEl = mind.findEle ? mind.findEle(targetNode.id) : null
  if (!targetEl) return
  try {
    mind.moveNodeAfter?.(mind.currentNode, targetEl)
    ElMessage.success('已移到「' + (targetNode.topic || '?') + '」之后')
    emitContentChange()
  } catch (e) {
    ElMessage.error('移动失败：' + (e as Error).message)
  }
}

// 兜底：检测 descendant 关系（防循环引用）
function isDescendant(potentialChild: any, potentialParent: any): boolean {
  if (!potentialChild || !potentialChild.parent) return false
  let p = potentialChild.parent
  while (p) {
    if (p === potentialParent) return true
    p = p.parent
  }
  return false
}

// ★ 直接编辑当前节点（不依赖焦点）
function renameCurrentNode() {
  if (!mind || !mind.currentNode) {
    ElMessage.warning('请先选中节点')
    return
  }
  try {
    // editTopic: 直接进入编辑态（不检查 isFocusMode，比 beginEdit 更轻量）
    if (typeof mind.editTopic === 'function') {
      mind.editTopic(mind.currentNode)
    } else {
      mind.beginEdit?.(mind.currentNode)
    }
  } catch (e) {
    ElMessage.warning('编辑失败：' + (e as Error).message)
  }
}

// ★ 静默设置节点 topic（不走编辑态，AI 改写/重命名节点时用）
function setTopicSilently(tpc: any, topic: string) {
  if (!mind || !tpc) return
  try {
    mind.setNodeTopic?.(tpc, topic)
    emitContentChange()
  } catch (e) { console.warn('setNodeTopic failed', e) }
}

// ★ createArrowFrom: 直接传对象创建关联线（适合批量重建 / 协同恢复）
function createArrowFromObj(arrowObj: any) {
  if (!mind) return
  try {
    mind.createArrowFrom?.(arrowObj)
    emitContentChange()
  } catch (e) {
    ElMessage.error('创建关联线失败：' + (e as Error).message)
  }
}

// ★ createSummaryFrom: 直接传对象创建摘要
function createSummaryFromObj(summaryObj: any) {
  if (!mind) return
  try {
    mind.createSummaryFrom?.(summaryObj)
    emitContentChange()
  } catch (e) {
    ElMessage.error('创建摘要失败：' + (e as Error).message)
  }
}

// ★ 关联线管理（高级）
function tidyAllArrows() {
  if (!mind) return
  try {
    mind.tidyArrow?.()
    ElMessage.success('已清理孤立关联线')
    emitContentChange()
  } catch (e) { console.warn('tidyArrow failed', e) }
}

function renderAllArrows() {
  if (!mind) return
  try {
    mind.renderArrow?.()
  } catch (e) { console.warn('renderArrow failed', e) }
}

function reshapeArrow(arrowObj: any, patch: any) {
  if (!mind || !arrowObj) return
  try {
    mind.reshapeArrow?.(arrowObj, patch)
    emitContentChange()
  } catch (e) { console.warn('reshapeArrow failed', e) }
}

function selectArrowByObj(arrowObj: any) {
  if (!mind) return
  try {
    // selectArrow 需要 SVG g 元素（不是 obj），先查找
    const g = mind.arrowSvg?.querySelector?.(`g[data-linkid="${arrowObj.id}"]`)
    if (g) mind.selectArrow?.(g)
  } catch (e) { console.warn('selectArrow failed', e) }
}

function deselectCurrentArrow() {
  if (!mind) return
  try { mind.unselectArrow?.() } catch (e) { /* ignore */ }
}

// ★ 摘要管理（高级）
function renderAllSummaries() {
  if (!mind) return
  try { mind.renderSummary?.() } catch (e) { console.warn('renderSummary failed', e) }
}

function selectSummaryByObj(summaryObj: any) {
  if (!mind) return
  try {
    const g = mind.summarySvg?.querySelector?.(`#s-${summaryObj.id}`)
    if (g) mind.selectSummary?.(g)
  } catch (e) { console.warn('selectSummary failed', e) }
}

function deselectCurrentSummary() {
  if (!mind) return
  try { mind.unselectSummary?.() } catch (e) { /* ignore */ }
}

// ★ 辅助：生成新节点对象（不挂到树）
function generateNewNodeObj(topic = '新节点') {
  if (!mind || typeof mind.generateNewObj !== 'function') {
    return { topic, id: 'n-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7), children: [] }
  }
  return mind.generateNewObj(topic)
}

// ★ 辅助：序列化当前数据为字符串（跟 getDataString 等价）
function getDataStringSafe() {
  if (!mind) return ''
  try {
    if (typeof mind.stringifyData === 'function') return mind.stringifyData(mind.getData())
    return mind.getDataString?.() || JSON.stringify(mind.getData())
  } catch (e) {
    console.warn('stringify failed', e)
    return ''
  }
}

// ★ 接收用户提供的目标节点，进行「移动到」对话框
const moveDialogVisible = ref(false)
const moveSearchQuery = ref('')
const moveTargetId = ref<string | null>(null)
const moveDialogMode = ref<'in' | 'before' | 'after'>('in')

function openMoveDialog(mode: 'in' | 'before' | 'after') {
  if (!mind || !mind.currentNode) {
    ElMessage.warning('请先选中要移动的节点')
    return
  }
  moveDialogMode.value = mode
  moveDialogVisible.value = true
  moveSearchQuery.value = ''
  moveTargetId.value = null
}

function flatNodes(node: any, depth = 0, result: any[] = []): any[] {
  if (!node) return result
  result.push({ id: node.id, topic: node.topic, depth, obj: node })
  if (node.children) node.children.forEach((c: any) => flatNodes(c, depth + 1, result))
  return result
}

const moveCandidateNodes = computed(() => {
  if (!mind?.nodeData) return []
  const cur = mind.currentNode?.nodeObj
  return flatNodes(mind.nodeData).filter((n) => !cur || n.obj !== cur)
})

const moveFilteredNodes = computed(() => {
  const q = moveSearchQuery.value.trim().toLowerCase()
  const list = moveCandidateNodes.value
  if (!q) return list
  return list.filter((n) => (n.topic || '').toLowerCase().includes(q))
})

function confirmMove() {
  const target = moveCandidateNodes.value.find((n) => n.id === moveTargetId.value)
  if (!target) {
    ElMessage.warning('请选择目标节点')
    return
  }
  if (moveDialogMode.value === 'in') moveNodeTo(target.obj)
  else if (moveDialogMode.value === 'before') moveNodeBeforeTarget(target.obj)
  else moveNodeAfterTarget(target.obj)
  moveDialogVisible.value = false
}

// ===== 主题/方向/紧凑 =====

function changeTheme(name: string | number | boolean | null | undefined, refresh = true) {
  if (!mind) return
  const n = String(name || 'rainbow')
  themeName.value = n as any
  if (n === 'default') mind.changeTheme(MindElixir.THEME, refresh)
  else if (n === 'dark') mind.changeTheme(MindElixir.DARK_THEME, refresh)
  else if (n === 'fresh') mind.changeTheme(FRESH_THEME, refresh)
  else if (n === 'rainbow') mind.changeTheme(RAINBOW_THEME, refresh)
}

function changeDirection(dir: string | number | boolean | null | undefined) {
  const d = Number(dir ?? 2)
  directionName.value = d
  if (!mind) return
  if (d === 0) mind.initLeft()
  else if (d === 1) mind.initRight()
  else if (d === 3) mind.initDown()
  else mind.initSide()
  emitContentChange()
}

function changeCompact(v: boolean | string | number) {
  if (!mind) return
  mind.changeCompact(Boolean(v))
}

// ===== 搜索 =====

function onSearch(q: string) {
  if (!mind || !q) return
  // 清除旧高亮
  mind.map?.querySelectorAll?.('me-tpc.search-hit')?.forEach?.((el: Element) => {
    el.classList.remove('search-hit')
  })
  try {
    const found = findNodeByTopic(mind.nodeData, q)
    if (!found) {
      ElMessage.info('未找到匹配节点')
      return
    }
    const tpc = mind.findEle(found.id)
    if (tpc) {
      tpc.classList.add('search-hit')
      mind.selectNode(tpc, true)
      mind.focusNode(tpc)  // 焦点移找到的节点
      setTimeout(() => tpc.classList.remove('search-hit'), 3000)
    }
  } catch (e) {
    console.warn('search failed', e)
  }
}

function findNodeByTopic(node: any, q: string): any {
  if (node?.topic?.includes(q)) return node
  if (node?.children) {
    for (const c of node.children) {
      const r = findNodeByTopic(c, q)
      if (r) return r
    }
  }
  return null
}

// ===== 导入导出 =====

async function exportPng() {
  if (!mind) return
  try {
    const blob = await mind.exportPng()
    if (blob) download(blob, `mindmap-${Date.now()}.png`)
    else ElMessage.warning('PNG 导出失败')
  } catch (e) { ElMessage.error('PNG 导出异常') }
}

function exportSvg() {
  if (!mind) return
  try {
    const svg = mind.exportSvg()
    download(new Blob([svg], { type: 'image/svg+xml' }), `mindmap-${Date.now()}.svg`)
  } catch (e) { ElMessage.error('SVG 导出失败') }
}

function exportJson() {
  if (!mind) return
  try {
    const json = mind.getDataString()
    download(new Blob([json], { type: 'application/json' }), `mindmap-${Date.now()}.json`)
  } catch (e) { ElMessage.error('JSON 导出失败') }
}

function importJson() { fileInputRef.value?.click() }

async function onImportFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    if (data?.nodeData) {
      mind?.refresh(data)
      collab.syncToYjs(data)
      emitContentChange()
      ElMessage.success('导入成功')
    } else {
      ElMessage.error('JSON 格式不合法（缺 nodeData）')
    }
  } catch { ElMessage.error('JSON 解析失败') }
}

function download(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// ===== 内部 =====

function emitContentChange() {
  if (!mind) return
  emit('state-change', 'editing')
  try {
    const json = serializeMindmap(mind.getData())
    emit('content-change', json)
    collab.syncToYjs(mind.getData())
  } catch (e) { console.error('serialize failed', e) }
  setTimeout(() => emit('state-change', 'saved'), 2000)
}

function hasAnyColor(node: any): boolean {
  if (node?.style?.color) return true
  if (node?.children) {
    return node.children.some((c: any) => hasAnyColor(c))
  }
  return false
}

function onOutlineLocate(node: any) {
  if (!mind) return
  try {
    // ★ 通过 findEle 找 DOM 元素（不接受 nodeObj），然后 scrollIntoView 滚动画布
    const el = mind.findEle ? mind.findEle(node.id) : null
    if (!el) {
      ElMessage.warning('节点不存在或已被删除')
      return
    }
    mind.selectNode(el, true)
    // scrollIntoView 内部检测元素是否在视口外，超出则自动平移画布居中
    if (typeof mind.scrollIntoView === 'function') {
      mind.scrollIntoView(el)
    } else {
      mind.focusNode?.(el)  // 兜底
    }
    if (el.nodeObj) selectedNode.value = el.nodeObj
    hasSelection.value = true
  } catch (e) { console.warn('outline locate failed', e) }
}

function onNodeStyleUpdate(patch: Record<string, any>) {
  if (!selectedNode.value || !mind) return
  const nodeId = selectedNode.value.id

  // 1. 重新查找 freshNode（避免 refresh 后引用失效）
  const freshNode = mind.getObjById ? mind.getObjById(nodeId, mind.nodeData) : null
  if (freshNode) {
    Object.assign(freshNode, patch)
    // 更新 selectedNode 引用，但**不重置面板**（避免闪烁退出）
    // 用 Object.assign 而不是直接赋值，保持响应式 watch 不触发重置
    Object.assign(selectedNode.value, freshNode)
  } else {
    Object.assign(selectedNode.value, patch)
  }

  // 2. MindElixir 重新渲染该节点（更新 DOM 颜色/字体）
  try {
    // reshapeNode 接受 DOM 元素（me-tpc）+ patch
    // 注意 mind.findEle 接受去前缀的 id（如 "root" 不是 "meroot"）
    const domEl = mind.findEle ? mind.findEle(nodeId) : null
    if (domEl) {
      mind.reshapeNode?.(domEl, patch)
    }
  } catch (e) {
    console.warn('reshapeNode failed', e)
  }

  // 3. 直接修改 DOM 兜底（确保视觉立即生效，即使 reshapeNode 内部未正确处理 style）
  try {
    const domEl = mind.findEle ? mind.findEle(nodeId) : null
    if (domEl) {
      const textEl = domEl.querySelector('.text') || domEl.querySelector('span') || domEl
      if (patch.style?.color) textEl.style.color = patch.style.color
      if (patch.style?.fontSize) textEl.style.fontSize = patch.style.fontSize + 'px'
      if (patch.style?.fontWeight) textEl.style.fontWeight = patch.style.fontWeight
      if (patch.style?.fontFamily) textEl.style.fontFamily = patch.style.fontFamily
      if (patch.style?.backgroundColor) textEl.style.backgroundColor = patch.style.backgroundColor
    }
  } catch (e) { /* ignore */ }

  // 注意：不调 mind.refresh()（避免 nodeData 重新创建导致 selectedNode 引用失效 → 面板闪烁退出）

  // 4. 触发 1.5s 去抖保存到后端
  emitContentChange()

  // 5. 同步给 Yjs 协同
  try {
    collab.syncToYjs(mind.getData())
  } catch (e) { /* ignore */ }
}

watch(searchQuery, (q) => onSearch(q || ''))

// ===== 全局快捷键 =====

function onKeydown(e: KeyboardEvent) {
  if (!mind) return
  const editing = isEditingInput(e.target)

  // Space → 不拦截 → 让 MindElixir 自己的 container keydown listener（注册在 mind.container 上）
  //   自动设置 spacePressed=true → 下次 pointerdown 走 panHelper → 画布平移
  //   我们只做一件事：preventDefault 防止页面滚动（MindElixir 不会 preventDefault Space）
  if (e.code === 'Space' && !editing) {
    e.preventDefault()  // 防止页面滚动
    // ★ 不调用 e.stopPropagation() → MindElixir 的 listener 同样能收到
    return
  }

  if (!props.canEdit) return

  // Del / Backspace → 删除节点
  if (e.key === 'Delete' && mind.currentNode && !editing) {
    mind.removeNodes([mind.currentNode]).then(() => emitContentChange())
    e.preventDefault()
    return
  }

  // Ctrl+Z → 撤销
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey && !editing) {
    mind.undo()
    emitContentChange()
    e.preventDefault()
    return
  }

  // Ctrl+Shift+Z → 重做
  if ((e.ctrlKey || e.metaKey) && ((e.key === 'Z') || (e.key === 'z' && e.shiftKey)) && !editing) {
    mind.redo()
    emitContentChange()
    e.preventDefault()
    return
  }

  // F1 → 居中
  if (e.key === 'F1' && !editing) {
    mind.toCenter()
    e.preventDefault()
    return
  }

  // F2 → 编辑节点
  if (e.key === 'F2' && mind.currentNode && !editing) {
    mind.beginEdit(mind.currentNode)
    e.preventDefault()
    return
  }

  // Ctrl+C → 复制
  if ((e.ctrlKey || e.metaKey) && e.key === 'c' && mind.currentNode && !editing) {
    mind.copyNode(mind.currentNode, mind.currentNode)
    ElMessage.success('节点已复制')
    e.preventDefault()
    return
  }
}

// ===== Space up → MindElixir 自己把 spacePressed=false，无需我们处理 =====
function onKeyUp(e: KeyboardEvent) {
  if (e.code === 'Space' && mind) {
    // 不做事 — MindElixir 自己的 keyup listener (注册在 mind.container 上) 会同步设置 spacePressed=false
    // 我们只阻止页面滚动后的回滚
  }
}

function isEditingInput(el: EventTarget | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false
  const tag = el.tagName.toLowerCase()
  return tag === 'input' || tag === 'textarea' || el.isContentEditable
}

// ===== 空格 pan =====
// 旧 pan 函数已移除（改用 onPanDown/onPanMove/onPanUp）

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keyup', onKeyUp)
}

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKeydown)
    window.removeEventListener('keyup', onKeyUp)
  }
})
</script>

<style scoped>
/* 全局 CSS 变量（--mindmap-primary 等）已移到 main.ts 引入，避免 :root 被 scoped hash 污染 */

/* 框选虚线框样式在 src/styles/mindmap-theme.css 全局定义（viselect 元素不在 Vue 树内） */

.mindmap-editor {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-background);
  position: relative;
}

/* ===== 单行工具栏（紧凑优雅） ===== */
.mm-toolbar {
  display: flex;
  align-items: center;
  gap: 4px !important;
  padding: 4px 8px !important;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: nowrap;
  overflow-x: auto;
  min-height: 36px !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.mm-toolbar::-webkit-scrollbar { height: 3px; }
.mm-toolbar::-webkit-scrollbar-thumb { background: rgba(245, 158, 11, 0.25); border-radius: 2px; }

.toolbar-spacer { flex: 1; min-width: 12px; }

/* ★ 工具栏 hint 文字（告诉用户框选快捷操作） */
.mm-toolbar-hint {
  display: flex;
  align-items: center;
  padding-right: 8px;
}
.mm-toolbar-hint .hint-text {
  font-size: 11px;
  color: var(--mindmap-primary, #F59E0B);
  background: rgba(245, 158, 11, 0.08);
  padding: 3px 8px;
  border-radius: 10px;
  cursor: help;
  user-select: none;
}
.mm-toolbar-hint .hint-text:hover {
  background: rgba(245, 158, 11, 0.15);
}

/* ★ 工具栏按钮：全部白底 + 灰边 + 小图标（MindElixir 官方风格） */
.mm-toolbar :deep(.el-button) {
  font-size: 12px;
  padding: 5px 8px;
  height: 28px;
  border-radius: 4px;
  font-weight: 500;
  background: #ffffff !important;
  color: #475569 !important;
  border: 1px solid #e2e8f0 !important;
  transition: all 0.15s ease !important;
}

.mm-toolbar :deep(.el-button:hover:not(:disabled)) {
  background: #fef3c7 !important;
  color: #b45309 !important;
  border-color: #fbbf24 !important;
}

.mm-toolbar :deep(.el-button:active:not(:disabled)) {
  background: #fde68a !important;
}

.mm-toolbar :deep(.el-button:disabled) {
  opacity: 0.4 !important;
  cursor: not-allowed !important;
}

/* 「子节点」主按钮 - 琥珀橙强调色（区别于其它按钮） */
.mm-toolbar :deep(.el-button.tb-icon-btn-primary) {
  background: linear-gradient(135deg, #F59E0B 0%, #FB923C 100%) !important;
  color: #ffffff !important;
  border-color: #D97706 !important;
  font-weight: 600 !important;
}

.mm-toolbar :deep(.el-button.tb-icon-btn-primary:hover) {
  background: linear-gradient(135deg, #D97706 0%, #F59E0B 100%) !important;
  border-color: #B45309 !important;
  color: #ffffff !important;
}

/* 删除按钮 - 红色文字区分 */
.mm-toolbar :deep(.el-button.tb-danger) {
  color: #dc2626 !important;
  border-color: #fecaca !important;
}
.mm-toolbar :deep(.el-button.tb-danger:hover) {
  background: #fef2f2 !important;
  color: #b91c1c !important;
  border-color: #f87171 !important;
}

/* 图标按钮 - icon-only（无文字） */
.mm-toolbar :deep(.el-button.tb-icon-btn) {
  min-width: 28px;
  padding: 5px;
}

/* 文字按钮 */
.mm-toolbar :deep(.el-button.tb-text-btn) {
  min-width: 32px;
  font-weight: 600;
}

.mm-toolbar :deep(.el-button .el-icon) { font-size: 14px; margin: 0; }
.mm-toolbar :deep(.el-button .dd-arrow) { font-size: 9px; margin-left: 2px; opacity: 0.6; }

.mm-toolbar :deep(.el-input) { font-size: 12px; }
.mm-toolbar :deep(.el-input__wrapper) {
  padding: 0 8px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
.mm-toolbar :deep(.el-input__inner) { height: 28px; font-size: 12px; }
.mm-toolbar :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #fbbf24 inset !important; }

.mm-toolbar :deep(.el-select) { font-size: 12px; }
.mm-toolbar :deep(.el-select .el-select__wrapper) {
  min-height: 28px;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.mm-toolbar :deep(.el-divider--vertical) { height: 16px; margin: 0 2px; background: #e2e8f0; }
.mm-toolbar :deep(.el-switch) { --el-switch-on-color: #F59E0B; }

/* ★ AI 按钮：保留七彩虹系列渐变（这是项目特色，不算乱发挥） */
.mm-toolbar :deep(.ai-btn.el-button) {
  font-weight: 700;
  border: none !important;
  color: #ffffff !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  letter-spacing: 0.3px;
  min-width: 70px;
}

.mm-toolbar :deep(.ai-btn-gen.el-button) {
  background: linear-gradient(135deg, #DC2626 0%, #EF4444 100%) !important;
}
.mm-toolbar :deep(.ai-btn-gen.el-button:hover) {
  background: linear-gradient(135deg, #B91C1C 0%, #DC2626 100%) !important;
  color: #ffffff !important;
}

.mm-toolbar :deep(.ai-btn-expand.el-button) {
  background: linear-gradient(135deg, #F59E0B 0%, #FB923C 100%) !important;
}
.mm-toolbar :deep(.ai-btn-expand.el-button:hover) {
  background: linear-gradient(135deg, #D97706 0%, #F59E0B 100%) !important;
  color: #ffffff !important;
}

.mm-toolbar :deep(.ai-btn-summary.el-button) {
  background: linear-gradient(135deg, #D97706 0%, #B45309 100%) !important;
}
.mm-toolbar :deep(.ai-btn-summary.el-button:hover) {
  background: linear-gradient(135deg, #B45309 0%, #92400E 100%) !important;
  color: #ffffff !important;
}

.mm-toolbar :deep(.ai-btn.el-button:disabled) {
  opacity: 0.5 !important;
  cursor: not-allowed !important;
}

.mm-toolbar :deep(.el-button.is-loading) {
  background: linear-gradient(135deg, #6b7280 0%, #9ca3af 100%) !important;
  color: #fff !important;
}

/* ===== 右下角浮动协同指示器 ===== */
.mm-collab-floater {
  position: fixed;
  bottom: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border: 1px solid var(--mindmap-primary-100);
  border-radius: 20px;
  font-size: 12px;
  color: #64748b;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 50;
  transition: all 0.3s ease;
}

.mm-collab-floater:hover {
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.18);
  border-color: var(--mindmap-primary);
}

.mm-collab-floater .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dc2626;
  box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4);
  animation: pulse-dot 1.5s ease-in-out infinite;
  cursor: help;
}

.mm-collab-floater .pan-hint {
  color: #94a3b8;
  font-size: 11px;
  padding: 0 4px;
  border-left: 1px solid #e2e8f0;
  margin-left: 2px;
  cursor: help;
}

.mm-collab-floater.connected .dot {
  background: #10b981;
  animation: none;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.5);
}

@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(220, 38, 38, 0); }
}

/* ===== 自定义右键菜单 ===== */
.mm-ctx-menu {
  position: fixed;
  z-index: 1000;
  min-width: 200px;
  padding: 4px 0;
  margin: 0;
  list-style: none;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  font-size: 13px;
  user-select: none;
  z-index: 9999;
}

.mm-ctx-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  cursor: pointer;
  color: #303133;
  transition: background 0.1s;
}

.mm-ctx-menu li:hover {
  background: #fef3c7;
  color: #b45309;
}

.mm-ctx-menu li.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  pointer-events: none;  /* ★ 真正禁用点击（之前只是视觉灰色但仍可点击） */
  opacity: 0.5;
}

.mm-ctx-menu li.disabled:hover {
  background: transparent;
  color: #c0c4cc;
}

.mm-ctx-menu li .el-icon {
  font-size: 14px;
  width: 16px;
}

.mm-ctx-menu li kbd {
  margin-left: auto;
  padding: 1px 6px;
  font-size: 10px;
  font-family: ui-monospace, monospace;
  color: #909399;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 3px;
}

.mm-ctx-menu li.separator {
  height: 1px;
  margin: 4px 8px;
  padding: 0;
  background: #ebeef5;
  cursor: default;
}

.mm-ctx-menu li.separator:hover {
  background: #ebeef5;
}

/* ===== 移动节点对话框 ===== */
.mb-12 { margin-bottom: 12px; }

.move-target-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}

.move-target-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 13px;
  color: #1e293b;
  transition: background 0.1s;
}

.move-target-list li:hover {
  background: #fef3c7;
}

.move-target-list li.selected {
  background: var(--mindmap-primary, #F59E0B);
  color: white;
  font-weight: 500;
}

.move-target-list li .topic {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.move-target-list li .check {
  color: white;
  font-size: 14px;
}

.mm-collab-floater .divider { color: #cbd5e1; margin: 0 2px; }
.mm-collab-floater .users-label { color: #94a3b8; font-size: 11px; }
.mm-collab-floater .users-list { display: flex; gap: 4px; }
.mm-collab-floater .users-list .el-avatar { margin-left: -4px; box-shadow: 0 0 0 1px white; }
.mm-collab-floater .no-users { color: #cbd5e1; font-size: 11px; }

/* ===== AI 浮窗（右侧固定） ===== */
.ai-side-panel {
  position: fixed;
  top: 100px;
  right: 12px;
  width: 380px;
  max-height: calc(100vh - 180px);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 100;
  border: 1px solid var(--mindmap-primary-100);
}

.ai-side-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: linear-gradient(to right, #F59E0B, #FB923C);
  color: white;
  font-weight: 600;
}

.ai-side-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.ai-side-body { flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.ai-side-body :deep(.ai-panel) { height: 100%; display: flex; flex-direction: column; }

.ai-slide-enter-active, .ai-slide-leave-active { transition: all 0.3s ease; }
.ai-slide-enter-from, .ai-slide-leave-to { transform: translateX(120%); opacity: 0; }

.ai-dialog-hint {
  padding: 8px 12px;
  background: var(--mindmap-primary-50);
  border-left: 3px solid var(--mindmap-primary);
  border-radius: 4px;
  font-size: 12px;
  color: #78350F;
  line-height: 1.5;
}

.ai-summary-content {
  white-space: pre-wrap;
  line-height: 1.9;
  font-size: 14px;
  color: #1e293b;
  padding: 8px 0;
}

:deep(.ai-btn-primary.el-button) {
  background: #DC2626 !important;
  color: #ffffff !important;
  border-color: #B91C1C !important;
  font-weight: 600;
}

/* ===== 主画布 ===== */
.mm-body { flex: 1; display: flex; overflow: hidden; min-height: 0; position: relative; }
.mt-map { flex: 1; position: relative; background: var(--color-background); overflow: hidden; }

:deep(.map-container) { width: 100%; height: 100%; background: var(--color-background); }

/* 节点选中态高亮（MindElixir 5.15 容器是 .map-container） */
:deep(.map-container me-tpc.selected > .text),
:deep(.map-container me-tpc.selected > span) {
  outline: 2px solid var(--mindmap-primary) !important;
  outline-offset: 2px !important;
}

/* 节点折叠/展开按钮 me-epd 样式已在 src/styles/mindmap-theme.css 全局定义
   （之前这里有 :deep(.map-container me-epd) scoped CSS 跟全局重复且是旧版本，
    会因为 scoped 哈希优先级覆盖全局纯白样式，已删除） */


/* 搜索命中高亮 */
:deep(.map-container me-tpc.search-hit) {
  background: rgba(250, 204, 21, 0.3) !important;
  outline: 2px solid #facc15 !important;
  outline-offset: 2px !important;
  animation: search-pulse 1.5s ease-in-out !important;
}

@keyframes search-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}
</style>