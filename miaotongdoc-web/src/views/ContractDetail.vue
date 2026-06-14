<template>
  <div class="contract-detail" v-loading="loading">
    <!-- Header -->
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="router.push('/home')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ contract.contractNo || '合同详情' }}</h2>
        <el-tag :type="statusType" size="large">{{ statusLabel }}</el-tag>
        <el-tag v-if="contract.signingLocked" type="warning" size="small">
          <el-icon><Lock /></el-icon> 已锁定
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button v-if="contract.status === 'draft' || contract.status === 'rejected'"
          type="primary" @click="showSubmit = true">提交审批</el-button>
        <el-button v-if="canApprove" type="success" @click="handleApprove">通过</el-button>
        <el-button v-if="canApprove" type="danger" @click="showReject = true">拒绝</el-button>
        <el-button v-if="canCancel" type="warning" plain @click="handleCancel">撤回</el-button>
        <el-button v-if="contract.status === 'draft'" type="danger" plain @click="handleDelete">删除</el-button>
      </div>
    </div>

    <!-- Integrity Banner -->
    <el-alert v-if="integrity && integrity.warning"
      :title="integrity.message" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />
    <el-alert v-else-if="integrity && integrity.intact === true"
      :title="integrity.message" type="success" show-icon :closable="false" style="margin-bottom: 16px" />

    <!-- Info Cards -->
    <div class="info-grid">
      <div class="info-item">
        <span class="label">合同编号</span>
        <span class="value">{{ contract.contractNo || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">合同类型</span>
        <span class="value">{{ typeLabels[contract.contractType || ''] || contract.contractType || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">甲方</span>
        <span class="value">{{ contract.partyA || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">乙方</span>
        <span class="value">{{ contract.partyB || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">合同金额</span>
        <span class="value amount">{{ contract.amount ? `¥${contract.amount.toLocaleString()}` : '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">签订日期</span>
        <span class="value">{{ contract.signingDate || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">生效日期</span>
        <span class="value">{{ contract.effectiveDate || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">到期日期</span>
        <span class="value" :class="{ 'text-danger': isNearExpiry }">{{ contract.expiryDate || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">创建人</span>
        <span class="value">{{ contract.ownerName || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="label">部门</span>
        <span class="value">{{ contract.departmentName || '-' }}</span>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" style="margin-top: 20px">
      <el-tab-pane label="审批流程" name="approval">
        <div v-if="approvalNodes.length > 0" class="approval-flow">
          <div v-for="(node, index) in approvalNodes" :key="node.id"
            class="flow-node" :class="nodeClass(node)">
            <div class="node-step">{{ node.stepOrder }}</div>
            <div class="node-info">
              <div class="node-approver">{{ node.approverName }}</div>
              <div class="node-status">
                <el-tag :type="nodeStatusType(node.status)" size="small">
                  {{ nodeStatusLabel(node.status) }}
                </el-tag>
              </div>
              <div v-if="node.remark" class="node-remark">"{{ node.remark }}"</div>
              <div v-if="node.actedAt" class="node-time">{{ formatTime(node.actedAt) }}</div>
            </div>
            <div v-if="index < approvalNodes.length - 1" class="flow-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无审批流程" />
      </el-tab-pane>

      <el-tab-pane label="合同正文" name="document">
        <div v-if="contract.documentId" class="doc-preview">
          <el-button type="primary" @click="openDocument">
            <el-icon><Document /></el-icon> 打开文档
          </el-button>
          <span v-if="contract.approvedVersion" style="margin-left: 12px; color: #909399; font-size: 13px">
            审批版本: v{{ contract.approvedVersion }}
          </span>
        </div>
      </el-tab-pane>

      <el-tab-pane label="操作记录" name="history">
        <el-timeline v-if="approvals.length > 0">
          <el-timeline-item v-for="a in approvals" :key="a.id"
            :timestamp="formatTime(a.createdAt)" placement="top"
            :type="actionType(a.action)">
            <div class="history-item">
              <el-tag :type="actionType(a.action)" size="small">{{ actionLabel(a.action) }}</el-tag>
              <span class="history-operator">{{ a.operatorName || '系统' }}</span>
              <span v-if="a.remark" class="history-remark">{{ a.remark }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无操作记录" />
      </el-tab-pane>
    </el-tabs>

    <!-- Submit Dialog -->
    <ContractSubmitDialog v-model="showSubmit" :contract-id="contractId" @submitted="onSubmitted" />

    <!-- Reject Dialog -->
    <el-dialog v-model="showReject" title="拒绝审批" width="400px">
      <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请填写拒绝原因（必填）" />
      <template #footer>
        <el-button @click="showReject = false">取消</el-button>
        <el-button type="danger" :disabled="!rejectRemark.trim()" :loading="submitting" @click="handleReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi, type Contract, type ApprovalNode, type ContractApproval, type IntegrityResult } from '@/api/contract'
import ContractSubmitDialog from '@/components/ContractSubmitDialog.vue'

const route = useRoute()
const router = useRouter()
const contractId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const contract = ref<Contract>({} as Contract)
const approvalNodes = ref<ApprovalNode[]>([])
const approvals = ref<ContractApproval[]>([])
const integrity = ref<IntegrityResult | null>(null)
const activeTab = ref('approval')
const showSubmit = ref(false)
const showReject = ref(false)
const rejectRemark = ref('')

const typeLabels: Record<string, string> = {
  purchase: '采购', sale: '销售', lease: '租赁',
  service: '服务', labor: '劳务', construction: '工程', other: '其他'
}

const statusLabels: Record<string, string> = {
  draft: '草稿', pending_approval: '审批中', approved: '已审批',
  rejected: '已拒绝', expired: '已过期'
}

const statusLabel = computed(() => statusLabels[contract.value.status] || contract.value.status)
const statusType = computed(() => {
  const map: Record<string, string> = {
    draft: 'info', pending_approval: 'warning', approved: 'success',
    rejected: 'danger', expired: 'info'
  }
  return map[contract.value.status] || ''
})

const isCurrentUserApprover = ref(false)
const canApprove = computed(() =>
  contract.value.status === 'pending_approval' && isCurrentUserApprover.value
)
const canCancel = computed(() =>
  contract.value.status === 'pending_approval' && contract.value.ownerUserId === getCurrentUserId()
)

const isNearExpiry = computed(() => {
  if (!contract.value.expiryDate || contract.value.status !== 'approved') return false
  const expiry = new Date(contract.value.expiryDate)
  const now = new Date()
  const diff = expiry.getTime() - now.getTime()
  return diff > 0 && diff < 7 * 24 * 60 * 60 * 1000
})

function getCurrentUserId(): number {
  try {
    const user = JSON.parse(sessionStorage.getItem('user') || '{}')
    return user.id || 0
  } catch { return 0 }
}

onMounted(() => {
  loadContract()
})

async function loadContract() {
  loading.value = true
  try {
    const data = await contractApi.detail(contractId)
    contract.value = data
    approvalNodes.value = data.approvalNodes || []
    approvals.value = data.approvals || []

    // Check if current user is the pending approver
    const userId = getCurrentUserId()
    isCurrentUserApprover.value = approvalNodes.value.some(
      n => n.status === 'pending' && n.approverId === userId
    )

    // Load integrity
    if (data.status === 'approved' || data.status === 'pending_approval') {
      try {
        integrity.value = await contractApi.integrity(contractId)
      } catch {}
    }
  } catch {
    ElMessage.error('加载合同详情失败')
  } finally {
    loading.value = false
  }
}

function nodeClass(node: ApprovalNode) {
  return `node-${node.status}`
}

function nodeStatusType(status: string) {
  const map: Record<string, string> = {
    waiting: 'info', pending: 'warning', approved: 'success', rejected: 'danger'
  }
  return map[status] || ''
}

function nodeStatusLabel(status: string) {
  const map: Record<string, string> = {
    waiting: '等待中', pending: '待审批', approved: '已通过', rejected: '已拒绝'
  }
  return map[status] || status
}

function actionType(action: string) {
  const map: Record<string, string> = {
    submit: 'primary', approve: 'success', reject: 'danger',
    cancel: 'warning', expire: 'info', reminder: 'warning'
  }
  return map[action] || ''
}

function actionLabel(action: string) {
  const map: Record<string, string> = {
    submit: '提交审批', approve: '审批通过', reject: '审批拒绝',
    cancel: '撤回审批', expire: '自动过期', reminder: '到期提醒'
  }
  return map[action] || action
}

function formatTime(str?: string) {
  if (!str) return ''
  return new Date(str).toLocaleString('zh-CN')
}

function openDocument() {
  router.push(`/editor/${contract.value.documentId}`)
}

async function handleApprove() {
  try {
    await ElMessageBox.confirm('确认通过此合同审批？', '审批通过', { type: 'success' })
    submitting.value = true
    await contractApi.approve(contractId)
    ElMessage.success('审批通过')
    loadContract()
  } catch {} finally {
    submitting.value = false
  }
}

async function handleReject() {
  if (!rejectRemark.value.trim()) return
  try {
    submitting.value = true
    await contractApi.reject(contractId, rejectRemark.value.trim())
    ElMessage.success('已拒绝')
    showReject.value = false
    rejectRemark.value = ''
    loadContract()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确认撤回审批？撤回后合同将回到草稿状态。', '撤回审批', { type: 'warning' })
    submitting.value = true
    await contractApi.cancel(contractId)
    ElMessage.success('已撤回')
    loadContract()
  } catch {} finally {
    submitting.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除此合同？', '删除确认', { type: 'warning' })
    await contractApi.delete(contractId)
    ElMessage.success('已删除')
    router.push('/home')
  } catch {}
}

function onSubmitted() {
  loadContract()
}
</script>

<style scoped>
.contract-detail {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #ebeef5;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item .label {
  font-size: 12px;
  color: #909399;
}

.info-item .value {
  font-size: 14px;
  color: #303133;
}

.info-item .amount {
  font-weight: 600;
  color: var(--el-color-primary);
}

.text-danger {
  color: var(--el-color-danger) !important;
  font-weight: 600;
}

.approval-flow {
  display: flex;
  align-items: flex-start;
  gap: 0;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow-x: auto;
}

.flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 140px;
  padding: 16px;
  border-radius: 8px;
  border: 2px solid #ebeef5;
  background: #fafafa;
  position: relative;
}

.flow-node.node-pending {
  border-color: var(--el-color-warning);
  background: #fdf6ec;
}

.flow-node.node-approved {
  border-color: var(--el-color-success);
  background: #f0f9eb;
}

.flow-node.node-rejected {
  border-color: var(--el-color-danger);
  background: #fef0f0;
}

.node-step {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.node-approved .node-step { background: var(--el-color-success); }
.node-rejected .node-step { background: var(--el-color-danger); }
.node-pending .node-step { background: var(--el-color-warning); }

.node-info {
  text-align: center;
}

.node-approver {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.node-remark {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.flow-arrow {
  display: flex;
  align-items: center;
  padding: 0 8px;
  color: #c0c4cc;
  font-size: 20px;
  margin-top: -20px;
}

.doc-preview {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-operator {
  font-weight: 600;
  color: #303133;
}

.history-remark {
  color: #909399;
  font-size: 13px;
}
</style>
