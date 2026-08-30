<template>
  <aside class="mm-style-panel">
    <header class="mm-style-header">
      <div>
        <h4>节点样式</h4>
        <span class="mm-style-hint">选中节点后在此修改样式</span>
      </div>
      <el-button link size="small" @click="close">
        <el-icon><Close /></el-icon>
      </el-button>
    </header>

    <div class="mm-style-tabs">
      <el-tabs v-model="activeTab" size="small">
        <el-tab-pane label="颜色" name="color">
          <div class="rainbow-grid">
            <div
              v-for="c in RAINBOW_COLORS"
              :key="c"
              class="color-swatch"
              :class="{ active: node?.style?.color === c }"
              :style="{ background: c }"
              :title="c"
              @click="setColor(c)"
            />
          </div>

          <div class="color-picker-row">
            <el-color-picker v-model="customColor" size="small" @change="setColor(customColor)" />
            <span class="hint">自定义颜色</span>
          </div>

          <el-button size="small" plain class="reset-btn" @click="resetColor">
            <el-icon><Refresh /></el-icon>恢复默认
          </el-button>
        </el-tab-pane>

        <el-tab-pane label="字体" name="font">
          <el-form label-position="top" size="small">
            <el-form-item label="字号">
              <el-select v-model="fontSize" @change="applyFont" style="width: 100%">
                <el-option label="12px (小)" :value="12" />
                <el-option label="14px (中)" :value="14" />
                <el-option label="16px (默认)" :value="16" />
                <el-option label="20px (大)" :value="20" />
                <el-option label="24px (超大)" :value="24" />
              </el-select>
            </el-form-item>

            <el-form-item label="字重">
              <el-radio-group v-model="fontWeight" @change="applyFont" size="small">
                <el-radio-button value="normal">常规</el-radio-button>
                <el-radio-button value="bold">加粗</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="字体">
              <el-select v-model="fontFamily" @change="applyFont" style="width: 100%">
                <el-option label="系统默认" value="sans-serif" />
                <el-option label="思源黑体" value='"Noto Sans SC", sans-serif' />
                <el-option label="苹方" value='"PingFang SC", sans-serif' />
                <el-option label="微软雅黑" value='"Microsoft YaHei", sans-serif' />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="标签" name="tags">
          <div class="tag-list">
            <el-tag
              v-for="tag in tags"
              :key="tag"
              closable
              type="warning"
              effect="plain"
              @close="removeTag(tag)"
            >{{ tag }}</el-tag>
          </div>

          <el-input
            v-model="newTag"
            size="small"
            placeholder="输入标签按回车"
            @keydown.enter.prevent="addTag"
            style="margin-top: 8px"
          >
            <template #append>
              <el-button @click="addTag">添加</el-button>
            </template>
          </el-input>
        </el-tab-pane>

        <el-tab-pane label="图标" name="icons">
          <div class="icon-grid">
            <div
              v-for="icon in AVAILABLE_ICONS"
              :key="icon.name"
              class="icon-cell"
              :class="{ active: icons.includes(icon.name) }"
              :title="icon.label"
              @click="toggleIcon(icon.name)"
            >
              <el-icon class="icon-svg"><component :is="icon.elIcon" /></el-icon>
              <span class="icon-label">{{ icon.label }}</span>
            </div>
          </div>
          <div class="hint">MindElixir v5 支持 8 个内置图标（点击切换）</div>
        </el-tab-pane>

        <el-tab-pane label="超链接" name="link">
          <el-form label-position="top" size="small">
            <el-form-item label="URL">
              <el-input v-model="hyperLink" placeholder="https://..." @change="applyHyperLink" />
            </el-form-item>
            <el-button size="small" plain @click="clearHyperLink" :disabled="!hyperLink">
              <el-icon><Delete /></el-icon>清除链接
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="图片" name="image">
          <el-form label-position="top" size="small">
            <el-form-item label="图片 URL">
              <el-input v-model="imageUrl" placeholder="https://..." @change="applyImage" />
            </el-form-item>
            <el-button size="small" plain @click="clearImage" :disabled="!imageUrl">
              <el-icon><Delete /></el-icon>清除图片
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * 思维导图节点样式面板（2026-08-16）
 *
 * 七彩虹配色（7 色）+ 字体 / 标签 / 8 个内置图标 / 超链接 / 图片。
 * 通过 emit('update') 上抛变更，MindmapEditor 统一调用 MindElixir API 应用。
 */
import { ref, watch, markRaw } from 'vue'
import { Close, Refresh, Delete, Star, StarFilled, CircleCheck, Flag, Calendar, ChatDotRound, MagicStick, Collection } from '@element-plus/icons-vue'

// 七彩虹 7 色（与计划文档一致）
const RAINBOW_COLORS = [
  '#FF6B6B', '#FF9F43', '#FECA57',
  '#10AC84', '#3742FA', '#8E44AD', '#5F27CD',
]

// MindElixir v5 内置 8 个图标（用 Element Plus SVG 图标替代 emoji 确保跨平台渲染）
// ★ 关键修复：elIcon 必须是组件引用，不能是字符串名
//   之前 'Star' 字符串 + <component :is="'Star'" /> 不渲染（运行时无法解析）
//   现在直接 import 组件 + markRaw 避免 Vue 响应式包装组件
const AVAILABLE_ICONS = [
  { name: 'priority', elIcon: markRaw(Star), label: '重要' },
  { name: 'star', elIcon: markRaw(StarFilled), label: '关注' },
  { name: 'task', elIcon: markRaw(CircleCheck), label: '任务' },
  { name: 'flag', elIcon: markRaw(Flag), label: '标记' },
  { name: 'calendar', elIcon: markRaw(Calendar), label: '日程' },
  { name: 'message', elIcon: markRaw(ChatDotRound), label: '消息' },
  { name: 'idea', elIcon: markRaw(MagicStick), label: '灵感' },
  { name: 'heart', elIcon: markRaw(Collection), label: '喜欢' },
]

const props = defineProps<{
  node: any  // MindElixir 节点对象
}>()

const emit = defineEmits<{
  (e: 'update', patch: Record<string, any>): void
  (e: 'close'): void
}>()

const activeTab = ref('color')
const customColor = ref('#F59E0B')

const fontSize = ref<number>(16)
const fontWeight = ref<'normal' | 'bold'>('normal')
const fontFamily = ref('sans-serif')

const tags = ref<string[]>([])
const newTag = ref('')

const icons = ref<string[]>([])
const hyperLink = ref('')
const imageUrl = ref('')

// 监听 node 变化 → 重置表单
// 注意：只在 node.id 变化时重置（切换不同节点），
// 同一节点样式修改时 props.node 引用变但不重置（避免面板闪烁/退出）
let lastNodeId = ''
watch(() => props.node?.id, (newId) => {
  if (!newId) return
  if (newId === lastNodeId) return  // 同一节点，不重置表单
  lastNodeId = newId
  const n = props.node
  if (!n) return
  // style
  fontSize.value = n.style?.fontSize || 16
  fontWeight.value = n.style?.fontWeight || 'normal'
  fontFamily.value = n.style?.fontFamily || 'sans-serif'
  customColor.value = n.style?.color || '#F59E0B'
  // tags
  tags.value = Array.isArray(n.tags) ? n.tags.map((t: any) => typeof t === 'string' ? t : t.text || String(t)) : []
  // icons
  icons.value = Array.isArray(n.icons) ? n.icons : []
  // link/image
  hyperLink.value = n.hyperLink || ''
  imageUrl.value = n.image?.url || n.image || ''
}, { immediate: true })

// ===== Color =====
function setColor(c: string) {
  emit('update', { style: { ...props.node.style, color: c } })
}
function resetColor() {
  emit('update', { style: { ...props.node.style, color: undefined } })
}

// ===== Font =====
function applyFont() {
  emit('update', {
    style: {
      ...props.node.style,
      fontSize: fontSize.value,
      fontWeight: fontWeight.value,
      fontFamily: fontFamily.value,
    },
  })
}

// ===== Tags =====
function addTag() {
  const t = newTag.value.trim()
  if (!t) return
  if (tags.value.includes(t)) return
  tags.value = [...tags.value, t]
  newTag.value = ''
  emit('update', { tags: tags.value })
}
function removeTag(t: string) {
  tags.value = tags.value.filter((x) => x !== t)
  emit('update', { tags: tags.value })
}

// ===== Icons =====
function toggleIcon(name: string) {
  const i = icons.value.indexOf(name)
  if (i >= 0) {
    icons.value = icons.value.filter((x) => x !== name)
  } else if (icons.value.length < 2) {
    icons.value = [...icons.value, name]
  } else {
    // MindElixir 限制 ≤2 个，替换最早的
    icons.value = [icons.value[1], name]
  }
  emit('update', { icons: icons.value })
}

// ===== Hyperlink =====
function applyHyperLink() {
  emit('update', { hyperLink: hyperLink.value })
}
function clearHyperLink() {
  hyperLink.value = ''
  emit('update', { hyperLink: '' })
}

// ===== Image =====
function applyImage() {
  if (!imageUrl.value) {
    emit('update', { image: undefined })
  } else {
    // MindElixir image 字段支持 string URL 或 {url, width, height}
    emit('update', { image: imageUrl.value })
  }
}
function clearImage() {
  imageUrl.value = ''
  emit('update', { image: undefined })
}

function close() {
  emit('close')
}
</script>

<style scoped>
.mm-style-panel {
  width: 280px;
  flex-shrink: 0;
  background: var(--panel-background);
  border-left: 1px solid var(--mindmap-primary-100);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.mm-style-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--mindmap-primary-100);
  background: rgba(245, 158, 11, 0.05);
}

.mm-style-header h4 {
  margin: 0;
  font-size: 14px;
  color: var(--mindmap-primary-dark);
  font-weight: 600;
}

.mm-style-hint {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}

.mm-style-tabs {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}

.rainbow-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin: 12px 0;
}

.color-swatch {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.15s, border-color 0.15s;
}

.color-swatch:hover {
  transform: scale(1.1);
}

.color-swatch.active {
  border-color: var(--mindmap-primary-dark);
  box-shadow: 0 0 0 2px white inset;
}

.color-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}

.color-picker-row .hint {
  font-size: 12px;
  color: #909399;
}

.reset-btn {
  width: 100%;
  margin-top: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 12px 0;
  min-height: 32px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 12px 0;
}

.icon-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  background: rgba(245, 158, 11, 0.05);
  transition: all 0.15s;
}

.icon-cell:hover {
  background: rgba(245, 158, 11, 0.15);
}

.icon-cell.active {
  border-color: var(--mindmap-primary);
  background: rgba(245, 158, 11, 0.2);
}

.icon-svg {
  font-size: 18px;
  color: var(--mindmap-primary-dark, #B45309);
}

.icon-label {
  font-size: 10px;
  color: #606266;
}

.hint {
  font-size: 11px;
  color: #909399;
  margin-top: 8px;
}
</style>