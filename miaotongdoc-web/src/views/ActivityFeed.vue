<template>
  <div class="activity-page">
    <div class="activity-list">
      <div v-for="activity in activities" :key="activity.id" class="activity-item">
        <div class="activity-icon">
          <el-icon :size="18">
            <component :is="getActivityIcon(activity.action)" />
          </el-icon>
        </div>
        <div class="activity-content">
          <div class="activity-text">
            <span class="user-name">{{ activity.userName }}</span>
            <span class="action">{{ getActivityLabel(activity.action) }}</span>
            <span v-if="activity.documentTitle" class="doc-title">
              {{ activity.documentTitle }}
            </span>
          </div>
          <div class="activity-time">{{ formatDate(activity.createdAt) }}</div>
        </div>
      </div>
      <el-empty v-if="activities.length === 0" description="暂无动态" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { activityApi, type ActivityItem } from '@/api/activity'

const activities = ref<ActivityItem[]>([])

onMounted(() => {
  loadActivities()
})

async function loadActivities() {
  try {
    const result = await activityApi.getFeed()
    activities.value = result.content || result
  } catch {
    ElMessage.error('加载动态失败')
  }
}

function getActivityIcon(action: string) {
  switch (action) {
    case 'CREATE': return 'Plus'
    case 'EDIT': return 'Edit'
    case 'COMMENT': return 'ChatDotRound'
    case 'SHARE': return 'Share'
    case 'SIGN_INIT': return 'EditPen'
    case 'SIGN_CONFIRM': return 'Check'
    case 'DELETE': return 'Delete'
    default: return 'InfoFilled'
  }
}

function getActivityLabel(action: string) {
  switch (action) {
    case 'CREATE': return '创建了文档'
    case 'EDIT': return '编辑了文档'
    case 'COMMENT': return '评论了文档'
    case 'SHARE': return '共享了文档'
    case 'SIGN_INIT': return '发起了签署'
    case 'SIGN_CONFIRM': return '确认了签署'
    case 'DELETE': return '删除了文档'
    default: return action
  }
}

import { formatDate } from '@/utils/date'
</script>

<style scoped>
.activity-page {
  width: 100%;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  transition: all 0.2s;
}

.activity-item:hover {
  border-color: #d0d0d0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.activity-icon {
  width: 36px;
  height: 36px;
  background: #ecf5ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  flex-shrink: 0;
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
</style>
