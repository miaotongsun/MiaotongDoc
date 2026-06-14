<template>
  <div class="activity-page">
    <div class="activity-list">
      <div v-for="(group, label) in groupedActivities" :key="label" class="activity-group">
        <div class="activity-group-label">{{ label }}</div>
        <div v-for="activity in group" :key="activity.id" class="activity-item" @click="goToDocument(activity.documentId)">
          <div class="activity-icon" :class="getActivityClass(activity.action)">
            <el-icon :size="18">
              <component :is="getActivityIcon(activity.action)" />
            </el-icon>
          </div>
          <div class="activity-content">
            <div class="activity-text">
              <span class="user-name">您</span>
              <span class="action">{{ getActivityLabel(activity.action) }}</span>
              <span v-if="activity.documentTitle" class="doc-title">
                {{ activity.documentTitle }}
              </span>
            </div>
            <div class="activity-time">{{ formatTime(activity.createdAt) }}</div>
          </div>
        </div>
      </div>
      <el-empty v-if="activities.length === 0" description="暂无动态" />
      <div class="pagination-bar" v-if="total > pageSize">
        <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
          :current-page="currentPage" @current-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi, type ActivityItem } from '@/api/activity'
import { formatDate } from '@/utils/date'

const router = useRouter()
const activities = ref<ActivityItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

onMounted(() => {
  loadActivities()
})

async function loadActivities() {
  try {
    const result = await activityApi.getFeed({ page: currentPage.value - 1, size: pageSize.value })
    activities.value = result.content || result
    total.value = result.totalElements || 0
  } catch {
    ElMessage.error('加载动态失败')
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadActivities()
}

// 按日期分组
const groupedActivities = computed(() => {
  const groups: Record<string, ActivityItem[]> = {}
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)

  activities.value.forEach(activity => {
    const date = new Date(activity.createdAt)
    let label: string

    if (date.toDateString() === today.toDateString()) {
      label = '今天'
    } else if (date.toDateString() === yesterday.toDateString()) {
      label = '昨天'
    } else {
      label = formatDate(activity.createdAt).split(' ')[0]
    }

    if (!groups[label]) {
      groups[label] = []
    }
    groups[label].push(activity)
  })

  return groups
})

function getActivityIcon(action: string) {
  switch (action) {
    case 'CREATE': return 'Plus'
    case 'UPLOAD': return 'Upload'
    case 'EDIT': return 'Edit'
    case 'RENAME': return 'Edit'
    case 'COMMENT': return 'ChatDotRound'
    case 'RESOLVE': return 'Check'
    case 'SHARE': return 'Share'
    case 'SAVE_VERSION': return 'DocumentCopy'
    case 'RESTORE_VERSION': return 'RefreshLeft'
    case 'SIGN_INIT': return 'EditPen'
    case 'SIGN_CONFIRM': return 'Check'
    case 'DELETE': return 'Delete'
    default: return 'InfoFilled'
  }
}

function getActivityClass(action: string) {
  switch (action) {
    case 'CREATE':
    case 'UPLOAD':
      return 'icon-create'
    case 'EDIT':
    case 'RENAME':
      return 'icon-edit'
    case 'COMMENT':
    case 'RESOLVE':
      return 'icon-comment'
    case 'SHARE':
      return 'icon-share'
    case 'SAVE_VERSION':
    case 'RESTORE_VERSION':
      return 'icon-version'
    case 'SIGN_INIT':
    case 'SIGN_CONFIRM':
      return 'icon-sign'
    case 'DELETE':
      return 'icon-delete'
    default:
      return ''
  }
}

function getActivityLabel(action: string) {
  switch (action) {
    case 'CREATE': return '创建了文档'
    case 'UPLOAD': return '上传了文档'
    case 'EDIT': return '编辑了文档'
    case 'RENAME': return '重命名了文档'
    case 'COMMENT': return '评论了文档'
    case 'RESOLVE': return '解决了评论'
    case 'SHARE': return '共享了文档'
    case 'SAVE_VERSION': return '保存了版本'
    case 'RESTORE_VERSION': return '恢复了版本'
    case 'SIGN_INIT': return '发起了签署'
    case 'SIGN_CONFIRM': return '确认了签署'
    case 'DELETE': return '删除了文档'
    default: return action
  }
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  return formatDate(dateStr)
}

function goToDocument(docId: number) {
  if (docId) {
    router.push(`/editor/${docId}`)
  }
}
</script>

<style scoped>
.activity-page {
  width: 100%;
}

.activity-group-label {
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  padding: 8px 0;
  margin-top: 8px;
}

.activity-group-label:first-child {
  margin-top: 0;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.activity-item {
  display: flex;
  gap: 14px;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  transition: all 0.2s;
  cursor: pointer;
}

.activity-item:hover {
  border-color: #d0d0d0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  background: #fafafa;
}

.activity-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-create, .icon-upload {
  background: #f0f9eb;
  color: #67c23a;
}

.icon-edit, .icon-rename {
  background: #ecf5ff;
  color: #409eff;
}

.icon-comment {
  background: #fdf6ec;
  color: #e6a23c;
}

.icon-share {
  background: #ecf5ff;
  color: #409eff;
}

.icon-version {
  background: #f4f4f5;
  color: #909399;
}

.icon-sign {
  background: #f0f9eb;
  color: #67c23a;
}

.icon-delete {
  background: #fef0f0;
  color: #f56c6c;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-text {
  font-size: 14px;
  line-height: 1.5;
}

.user-name {
  font-weight: 500;
  color: #303133;
}

.action {
  color: #606266;
  margin-left: 4px;
}

.doc-title {
  color: #409eff;
  margin-left: 4px;
}

.activity-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}
</style>
