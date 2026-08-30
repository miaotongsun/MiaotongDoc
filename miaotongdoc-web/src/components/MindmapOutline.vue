<template>
  <aside class="mm-outline">
    <header class="mm-outline-header">
      <h4>大纲</h4>
      <div class="mm-outline-actions">
        <el-button link size="small" :title="'展开所有节点'" @click="expandAll">
          <el-icon><Plus /></el-icon>
        </el-button>
        <el-button link size="small" :title="'折叠所有节点'" @click="collapseAll">
          <el-icon><Minus /></el-icon>
        </el-button>
      </div>
    </header>

    <div class="mm-outline-tree">
      <OutlineNode
        v-if="root"
        :node="root"
        :depth="0"
        :current-id="currentId"
        :expanded-ids="expandedIds"
        @locate="onLocate"
        @toggle="onToggleNode"
      />
      <el-empty v-else description="暂无内容" :image-size="60" />
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * 思维导图大纲视图（2026-08-22 改进）
 *
 * 功能：
 * - 递归渲染节点树（缩进 + 缩略）
 * - 点击节点跳转定位 + 高亮当前节点
 * - 每个节点有 +/− 按钮可单独折叠/展开
 * - 顶部「全部展开」「全部折叠」按钮
 */
import { ref, type VNode } from 'vue'

const props = defineProps<{
  /** 节点数据（MindElixirData.nodeData） */
  root: any
  /** 当前选中节点 ID */
  currentId?: string
}>()

const emit = defineEmits<{
  (e: 'locate', node: any): void
  (e: 'expand-all'): void
  (e: 'collapse-all'): void
  (e: 'toggle-node', node: any): void
}>()

function onLocate(node: any) {
  emit('locate', node)
}

function onToggleNode(node: any) {
  if (!node?.id) return
  const id = node.id
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  // 触发响应式更新（Set 的 add/delete 不自动触发 Vue 响应式）
  expandedIds.value = new Set(expandedIds.value)
}

function expandAll() {
  emit('expand-all')
}

function collapseAll() {
  emit('collapse-all')
}

// 记录展开状态（用于渲染 +/- 状态）— 大纲自身的折叠/展开状态
// 默认所有节点都展开（用户能看到完整棵树，包括叶子节点）
// 点击 +/- 只控制大纲内的展开/折叠显示，不影响主画布的折叠状态
const expandedIds = ref<Set<string>>(new Set())
</script>

<script lang="ts">
import { defineComponent, h } from 'vue'

/** 递归渲染大纲节点 */
export const OutlineNode = defineComponent({
  name: 'OutlineNode',
  props: {
    node: { type: Object as () => any, required: true },
    depth: { type: Number, default: 0 },
    currentId: { type: String, default: '' },
    expandedIds: { type: Object as () => Set<string>, required: true },
  },
  emits: ['locate', 'toggle'],
  setup(props, { emit }): () => VNode {
    // ★ 使用父组件传入的 expandedIds 状态（function components 没有自己的响应式状态）
    //   点击 +/- 触发 toggle 事件 → 父组件 onToggleNode 更新 expandedIds → props.expandedIds 变化 → 重渲染
    return (): VNode => {
      const indent = { paddingLeft: `${props.depth * 16 + 8}px` }
      const isCurrent = props.node.id === props.currentId
      const children = (props.node.children || []) as any[]
      const hasChildren = children.length > 0
      // 默认展开（用户能看到完整棵树，包括叶子节点）
      // 父组件 expandedIds 里的 id 表示已折叠
      const isCollapsed = props.expandedIds.has(props.node.id)
      return h('div', { class: 'mm-outline-row' }, [
        h(
          'div',
          {
            class: ['mm-outline-row-item', { active: isCurrent }],
            style: indent,
            onClick: () => emit('locate', props.node),
          },
          [
            hasChildren
              ? h(
                  'span',
                  {
                    class: ['toggle-btn', { collapsed: isCollapsed }],
                    onClick: (e: Event) => {
                      e.stopPropagation()
                      emit('toggle', props.node)
                    },
                  },
                  isCollapsed ? '+' : '−'
                )
              : h('span', { class: 'toggle-placeholder' }, ' '),
            h('span', { class: 'dot' }, '●'),
            h('span', { class: 'topic' }, props.node.topic || '(空)'),
            hasChildren
              ? h('span', { class: 'child-count' }, String(children.length))
              : null,
          ]
        ),
        // 大纲始终显示所有节点（除非父组件 expandedIds 标记为已折叠）
        !isCollapsed
          ? children.map((c: any) =>
              h(OutlineNode, {
                node: c,
                depth: props.depth + 1,
                currentId: props.currentId,
                expandedIds: props.expandedIds,
                onLocate: (n: any) => emit('locate', n),
                onToggle: (n: any) => emit('toggle', n),
              })
            )
          : null,
      ]) as VNode
    }
  },
})

export default {}
</script>

<style scoped>
.mm-outline {
  width: 240px;
  flex-shrink: 0;
  background: var(--panel-bgcolor, #FFFBEB);
  border-right: 1px solid var(--mindmap-primary-100);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.mm-outline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--mindmap-primary-100);
  background: rgba(245, 158, 11, 0.05);
}

.mm-outline-header h4 {
  margin: 0;
  font-size: 14px;
  color: var(--mindmap-primary-dark);
  font-weight: 600;
}

.mm-outline-actions {
  display: flex;
  gap: 4px;
}

.mm-outline-actions .el-button {
  padding: 2px 6px;
  font-size: 13px;
  color: var(--mindmap-primary);
}

.mm-outline-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

:deep(.mm-outline-row) {
  font-size: 13px;
}

:deep(.mm-outline-row-item) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 4px;
  margin: 1px 4px;
  transition: background 0.15s;
  color: var(--panel-color);
}

:deep(.mm-outline-row-item:hover) {
  background: rgba(245, 158, 11, 0.1);
}

:deep(.mm-outline-row-item.active) {
  background: var(--mindmap-primary);
  color: white;
  font-weight: 500;
}

:deep(.mm-outline-row-item .dot) {
  font-size: 8px;
  color: var(--mindmap-primary);
  flex-shrink: 0;
}

:deep(.mm-outline-row-item.active .dot) {
  color: white;
}

:deep(.mm-outline-row-item .topic) {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  min-width: 0;
}

/* +/− 折叠按钮 */
:deep(.mm-outline-row-item .toggle-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 3px;
  background: var(--mindmap-primary-50, #FFFBEB);
  color: var(--mindmap-primary, #F59E0B);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
  transition: all 0.15s;
}

:deep(.mm-outline-row-item .toggle-btn:hover) {
  background: var(--mindmap-primary, #F59E0B);
  color: white;
}

:deep(.mm-outline-row-item .toggle-btn.collapsed) {
  background: transparent;
  color: var(--mindmap-primary, #F59E0B);
}

:deep(.mm-outline-row-item.active .toggle-btn) {
  background: rgba(255, 255, 255, 0.25);
  color: white;
}

:deep(.mm-outline-row-item.active .toggle-btn:hover) {
  background: rgba(255, 255, 255, 0.4);
}

:deep(.mm-outline-row-item .toggle-placeholder) {
  display: inline-block;
  width: 16px;
  flex-shrink: 0;
}

:deep(.mm-outline-row-item .child-count) {
  font-size: 10px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 0 4px;
  border-radius: 8px;
  flex-shrink: 0;
}

:deep(.mm-outline-row-item.active .child-count) {
  background: rgba(255, 255, 255, 0.25);
  color: white;
}
</style>
