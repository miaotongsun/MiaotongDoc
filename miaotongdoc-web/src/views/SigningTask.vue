<template>
  <div class="signing-page">
    <el-tabs v-model="activeTab" class="signing-tabs">
      <el-tab-pane label="我发起的" name="initiated">
        <div class="task-list">
          <div v-for="task in initiatedTasks" :key="task.id" class="task-card"
            @click="viewTask(task.id)">
            <div class="task-header">
              <span class="task-title">{{ task.title }}</span>
              <el-tag :type="getStatusType(task.status)" size="small">{{ getStatusLabel(task.status) }}</el-tag>
            </div>
            <div class="task-info">
              <span><el-icon><Document /></el-icon> {{ task.documentTitle }}</span>
              <span><el-icon><User /></el-icon> {{ task.completedCount }}/{{ task.requiredCount }} 已签署</span>
            </div>
            <div class="task-footer">
              <span>发起时间: {{ formatDate(task.createdAt) }}</span>
              <span v-if="task.deadline">截止时间: {{ formatDate(task.deadline) }}</span>
            </div>
          </div>
          <el-empty v-if="initiatedTasks.length === 0" description="暂无签署任务" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="待我签署" name="todo">
        <div class="task-list">
          <div v-for="task in todoTasks" :key="task.id" class="task-card">
            <div class="task-header">
              <span class="task-title">{{ task.title }}</span>
              <el-tag type="warning" size="small">待签署</el-tag>
            </div>
            <div class="task-info">
              <span><el-icon><User /></el-icon> 发起人: {{ task.creatorName }}</span>
              <span><el-icon><Document /></el-icon> {{ task.documentTitle }}</span>
            </div>
            <div class="task-actions">
              <el-button type="primary" size="small" @click="confirmSign(task.id)">
                <el-icon><Check /></el-icon> 确认签署
              </el-button>
              <el-button type="danger" size="small" plain @click="rejectSign(task.id)">
                <el-icon><Close /></el-icon> 拒绝签署
              </el-button>
            </div>
          </div>
          <el-empty v-if="todoTasks.length === 0" description="暂无待签署任务" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { signingApi, type SigningTask } from '@/api/signing'
import { formatDateTime as formatDate } from '@/utils/date'
import { Document, User, Check, Close } from '@element-plus/icons-vue'

const router = useRouter()
const activeTab = ref('initiated')
const initiatedTasks = ref<SigningTask[]>([])
const todoTasks = ref<SigningTask[]>([])

onMounted(() => {
  loadTasks()
})

async function loadTasks() {
  try {
    const [initiated, todo] = await Promise.all([
      signingApi.getMyTasks({ type: 'initiated' }),
      signingApi.getMyTasks({ type: 'todo' })
    ])
    initiatedTasks.value = initiated.content || initiated
    todoTasks.value = todo.content || todo
  } catch {
    ElMessage.error('加载签署任务失败')
  }
}

function getStatusType(status: string) {
  switch (status) {
    case 'pending': return 'info'
    case 'in_progress': return 'warning'
    case 'completed': return 'success'
    case 'cancelled': return 'danger'
    case 'expired': return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'pending': return '待处理'
    case 'in_progress': return '进行中'
    case 'completed': return '已完成'
    case 'cancelled': return '已取消'
    case 'expired': return '已过期'
    default: return status
  }
}

function viewTask(id: number) {
  router.push(`/signing/${id}`)
}

async function confirmSign(taskId: number) {
  try {
    await ElMessageBox.confirm('确认签署此文档？', '确认签署', { type: 'warning' })
    await signingApi.confirm(taskId)
    ElMessage.success('签署成功')
    loadTasks()
  } catch {}
}

async function rejectSign(taskId: number) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝理由', '拒绝签署', {
      inputType: 'textarea'
    })
    await signingApi.reject(taskId, value || '')
    ElMessage.success('已拒绝签署')
    loadTasks()
  } catch {}
}
</script>

<style scoped>
.signing-page {
  width: 100%;
}

.signing-tabs {
  background: transparent;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  background: white;
  padding: 16px 20px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.2s;
}

.task-card:hover {
  border-color: #d0d0d0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.task-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.task-info {
  display: flex;
  gap: 24px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 8px;
}

.task-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-footer {
  display: flex;
  gap: 24px;
  color: #909399;
  font-size: 12px;
}

.task-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}
</style>
