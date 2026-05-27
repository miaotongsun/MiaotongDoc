<template>
  <div class="signing-bar" v-if="task">
    <div class="signing-info">
      <el-icon><Edit /></el-icon>
      <span class="task-status">
        <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
      </span>
      <span class="signer-info">
        {{ task.records?.length || 0 }} 人参与签署
      </span>
      <span v-if="task.dueDate" class="due-date">
        截止: {{ formatDate(task.dueDate) }}
      </span>
    </div>

    <div class="signing-actions">
      <template v-if="canSign">
        <el-button type="success" size="small" @click="handleSign">
          <el-icon><Check /></el-icon>
          签署通过
        </el-button>
        <el-button type="danger" size="small" @click="handleReject">
          <el-icon><Close /></el-icon>
          驳回
        </el-button>
      </template>
      <template v-if="canCancel">
        <el-button size="small" @click="handleCancel">取消签署</el-button>
      </template>
      <el-button text size="small" @click="showDetail = true">查看详情</el-button>
    </div>

    <!-- 签署详情弹窗 -->
    <el-dialog v-model="showDetail" title="签署详情" width="500px">
      <div class="signing-detail">
        <div class="detail-item">
          <span class="label">创建人：</span>
          <span>{{ task.creatorName }}</span>
        </div>
        <div class="detail-item">
          <span class="label">创建时间：</span>
          <span>{{ formatDate(task.createdAt) }}</span>
        </div>
        <div class="detail-item" v-if="task.dueDate">
          <span class="label">截止时间：</span>
          <span>{{ formatDate(task.dueDate) }}</span>
        </div>

        <el-divider />

        <h4>签署流程</h4>
        <el-steps direction="vertical" :active="activeStep" finish-status="success">
          <el-step
            v-for="record in task.records"
            :key="record.id"
            :title="record.signerName"
            :description="getStepDescription(record)"
            :status="getStepStatus(record)"
          />
        </el-steps>
      </div>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="showRejectDialog" title="驳回原因" width="400px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请输入驳回原因..."
      />
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Edit, Check, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate, formatDateTime } from '@/utils/date'

interface SigningRecord {
  id: number
  signerUserId: number
  signerName: string
  signerOrder: number
  status: 'pending' | 'signed' | 'rejected'
  comment?: string
  signedAt?: string
}

interface SigningTask {
  id: number
  creatorUserId: number
  creatorName: string
  documentTitle?: string
  status: 'pending' | 'in_progress' | 'completed' | 'rejected' | 'cancelled'
  dueDate?: string
  records: SigningRecord[]
  createdAt: string
}

const props = defineProps<{
  task: SigningTask | null
  currentUserId?: number
}>()

const emit = defineEmits(['sign', 'reject', 'cancel'])

const showDetail = ref(false)
const showRejectDialog = ref(false)
const rejectReason = ref('')

const statusType = computed(() => {
  const types: Record<string, string> = {
    pending: 'info',
    in_progress: 'warning',
    completed: 'success',
    rejected: 'danger',
    cancelled: 'info'
  }
  return types[props.task?.status || ''] || 'info'
})

const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    pending: '待签署',
    in_progress: '签署中',
    completed: '已完成',
    rejected: '已驳回',
    cancelled: '已取消'
  }
  return labels[props.task?.status || ''] || '未知'
})

const canSign = computed(() => {
  if (!props.task || !props.currentUserId) return false
  if (props.task.status !== 'in_progress') return false
  const myRecord = props.task.records?.find(r => r.signerUserId === props.currentUserId)
  return myRecord?.status === 'pending'
})

const canCancel = computed(() => {
  if (!props.task || !props.currentUserId) return false
  return props.task.creatorUserId === props.currentUserId &&
    ['pending', 'in_progress'].includes(props.task.status)
})

const activeStep = computed(() => {
  if (!props.task?.records) return 0
  return props.task.records.filter(r => r.status === 'signed').length
})

function getStepDescription(record: SigningRecord) {
  if (record.status === 'signed') {
    return `已签署 ${record.signedAt ? formatDateTime(record.signedAt) : ''}`
  }
  if (record.status === 'rejected') {
    return `已驳回${record.comment ? '：' + record.comment : ''}`
  }
  return '待签署'
}

function getStepStatus(record: SigningRecord) {
  if (record.status === 'signed') return 'success'
  if (record.status === 'rejected') return 'error'
  return 'wait'
}

function handleSign() {
  ElMessageBox.confirm('确认签署该文档？', '签署确认', {
    type: 'success'
  }).then(() => {
    emit('sign')
  }).catch(() => {})
}

function handleReject() {
  rejectReason.value = ''
  showRejectDialog.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  emit('reject', rejectReason.value)
  showRejectDialog.value = false
}

function handleCancel() {
  ElMessageBox.confirm('确认取消该签署任务？所有签署人将收到取消通知。', '取消签署', {
    type: 'warning'
  }).then(() => {
    emit('cancel')
  }).catch(() => {})
}
</script>

<style scoped>
.signing-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fdf6ec;
  border-bottom: 1px solid #faecd8;
}

.signing-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.signing-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.due-date {
  color: #e6a23c;
  font-size: 13px;
}

.signing-detail {
  padding: 0 16px;
}

.detail-item {
  margin-bottom: 12px;
}

.detail-item .label {
  color: #909399;
  margin-right: 8px;
}
</style>
