<template>
  <div class="doc-card" :class="{ 'is-dragging': isDragging, 'is-selected': selected }" draggable="true"
    @dblclick="handleCardOpen" @mousedown="onMouseDown" @dragstart="onDragStart" @dragend="onDragEnd"
    @mouseenter="hovered = true" @mouseleave="hovered = false">
    <div class="card-header">
      <div class="select-check" @click.stop="$emit('toggle-select')">
        <el-icon v-if="selected" class="check-on" :size="18"><CircleCheck /></el-icon>
        <el-icon v-else-if="hovered" class="check-off" :size="18"><CircleCheck /></el-icon>
      </div>
      <el-icon :size="24" :style="{ color: docTypeConfig.color }">
        <component :is="docTypeConfig.icon" />
      </el-icon>
      <el-tag size="small" :color="docTypeConfig.color" effect="dark">
        {{ docTypeConfig.label }}
      </el-tag>
      <div class="card-menu" @click.stop>
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="menu-btn">
            <el-icon :size="18"><MoreFilled /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="star">
                <el-icon><Star /></el-icon>
                {{ doc.isStarred ? '取消收藏' : '收藏' }}
              </el-dropdown-item>
              <el-dropdown-item v-if="canAdmin" command="share">
                <el-icon><Share /></el-icon>
                分享
              </el-dropdown-item>
              <el-dropdown-item v-if="canEdit" command="rename">
                <el-icon><Edit /></el-icon>
                重命名
              </el-dropdown-item>
              <el-dropdown-item v-if="canAdmin" command="delete" divided>
                <el-icon><Delete /></el-icon>
                删除
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="card-body">
      <h3 class="doc-title" :title="doc.title">{{ doc.title }}</h3>
      <div class="doc-meta">
        <span>{{ formatFileSize(doc.fileSize) }}</span>
        <span>v{{ doc.currentVersion }}</span>
        <span v-if="doc.ownerName">{{ doc.ownerName }}</span>
      </div>
    </div>

    <div class="card-footer">
      <span class="doc-time">{{ formatDate(doc.updatedAt) }}</span>
      <div class="footer-icons">
        <el-icon v-if="doc.isStarred" class="star-icon" color="#e6a23c"><Star /></el-icon>
        <el-icon v-if="doc.signingLocked" class="lock-icon" color="#f56c6c"><Lock /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getDocTypeConfig, formatFileSize } from '@/utils/docType'
import { formatDate } from '@/utils/date'
import { documentApi } from '@/api/document'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Document } from '@/api/document'

import { CircleCheck } from '@element-plus/icons-vue'

const props = defineProps<{
  doc: Document
  selected?: boolean
}>()

const emit = defineEmits(['click', 'delete', 'share', 'dragstart', 'toggle-select'])

const hovered = ref(false)
const isDragging = ref(false)
const mouseDownPos = ref({ x: 0, y: 0 })
const docTypeConfig = computed(() => getDocTypeConfig(props.doc.docType))

const perm = computed(() => props.doc.currentUserPermission || 'view')
const isSysAdmin = computed(() => sessionStorage.getItem('role') === 'admin')
const canEdit = computed(() => isSysAdmin.value || ['edit', 'admin'].includes(perm.value))
const canAdmin = computed(() => isSysAdmin.value || perm.value === 'admin')

function onMouseDown(e: MouseEvent) {
  mouseDownPos.value = { x: e.clientX, y: e.clientY }
}

function handleCardOpen(e: MouseEvent) {
  // 双击打开文档
  const target = e.target as HTMLElement
  if (target.closest('.card-menu') || target.closest('.select-check')) return
  emit('click')
}

function onDragStart(e: DragEvent) {
  isDragging.value = true
  e.dataTransfer?.setData('text/plain', String(props.doc.id))
  e.dataTransfer!.effectAllowed = 'move'
  emit('dragstart', e, props.doc)
}

function onDragEnd() {
  isDragging.value = false
}

async function handleCommand(command: string) {
  switch (command) {
    case 'star':
      try {
        await documentApi.toggleStar(props.doc.id)
        props.doc.isStarred = !props.doc.isStarred
        ElMessage.success(props.doc.isStarred ? '已收藏' : '已取消收藏')
      } catch {
        ElMessage.error('操作失败')
      }
      break
    case 'share':
      emit('share', props.doc.id)
      break
    case 'rename':
      try {
        const { value } = await ElMessageBox.prompt('请输入新标题', '重命名', {
          inputValue: props.doc.title
        })
        if (value && value !== props.doc.title) {
          await documentApi.rename(props.doc.id, value)
          props.doc.title = value
          ElMessage.success('重命名成功')
        }
      } catch {}
      break
    case 'delete':
      emit('delete')
      break
  }
}
</script>

<style scoped>
.doc-card {
  background: #fff;
  border-radius: 8px;
  padding: 0;
  cursor: pointer;
  border: 1px solid #e8e8e8;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}

.doc-card::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--primary-gradient);
  transform: scaleX(0);
  transition: transform 0.2s;
}

.doc-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.25);
  transform: translateY(-4px);
}

.doc-card:hover::after {
  transform: scaleX(1);
}

.doc-card.is-selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary);
}

.doc-card.is-dragging {
  opacity: 0.5;
}

.select-check {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.select-check:hover {
  transform: scale(1.1);
}

.check-on {
  color: var(--el-color-primary);
  filter: drop-shadow(0 2px 4px rgba(64, 158, 255, 0.3));
}

.check-off {
  color: #dcdfe6;
  transition: color 0.2s ease;
}

.check-off:hover {
  color: var(--el-color-primary);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
}

.card-body {
  padding: 0 16px 12px;
}

.doc-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.doc-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  padding: 12px 16px;
  border-top: 1px solid #f5f7fa;
}

.doc-time {
  font-size: 12px;
}

.footer-icons {
  display: flex;
  gap: 8px;
}

.star-icon,
.lock-icon {
  font-size: 14px;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.1));
}

.card-menu {
  margin-left: auto;
  cursor: pointer;
  color: #909399;
}

.card-menu .menu-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  color: #909399;
  transition: all 0.2s;
}

.card-menu .menu-btn:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

:deep(.el-tag) {
  border: none !important;
}
</style>
