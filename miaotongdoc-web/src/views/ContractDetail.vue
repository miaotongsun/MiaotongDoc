<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="960px" :close-on-click-modal="false"
    top="5vh" class="contract-detail-dialog" :show-close="true" destroy-on-close>
    <div class="contract-detail" v-loading="loading">
      <!-- Header -->
      <div class="detail-header">
        <div class="header-left">
          <h2>{{ contract.contractNo || '合同详情' }}</h2>
          <el-tag :type="statusType" size="large">{{ statusLabel }}</el-tag>
          <el-tag v-if="contract.signingLocked" type="warning" size="small">
            <el-icon><Lock /></el-icon> 已锁定
          </el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="contract.status === 'draft' || contract.status === 'rejected'"
            type="primary" size="small" @click="showSubmit = true">提交审批</el-button>
          <el-button v-if="canEdit" plain size="small" @click="showEdit = true">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
          <el-button v-if="canApprove" type="success" size="small" @click="handleApprove">通过</el-button>
          <el-button v-if="canApprove" type="danger" size="small" @click="showReject = true">拒绝</el-button>
          <el-button v-if="canCancel" type="warning" plain size="small" @click="handleCancel">撤回</el-button>
          <el-button v-if="contract.status === 'draft'" type="danger" plain size="small" @click="handleDelete">删除</el-button>
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
            <div class="doc-preview-actions">
              <el-button type="primary" @click="openDocument">
                <el-icon><Edit /></el-icon> 编辑文档
              </el-button>
              <el-button type="success" plain @click="runDocPreview">
                <el-icon><View /></el-icon> 临时预览
              </el-button>
              <span v-if="contract.approvedVersion" style="margin-left: 12px; color: #909399; font-size: 13px">
                审批版本: v{{ contract.approvedVersion }}
              </span>
            </div>
            <!-- 2026-08-09 #4 优化:右侧临时预览面板(不离开弹窗) -->
            <div v-if="docPreviewText !== null" class="doc-preview-content">
              <div class="preview-header">
                <span><el-icon><Document /></el-icon> {{ contract.documentTitle || '文档预览' }}</span>
                <el-button text size="small" @click="docPreviewText = null">
                  <el-icon><Close /></el-icon> 关闭预览
                </el-button>
              </div>
              <pre class="preview-body">{{ docPreviewText || '(文档为空)' }}</pre>
              <div class="preview-footer">
                <el-button type="primary" @click="openDocument">
                  <el-icon><Edit /></el-icon> 编辑内容
                </el-button>
              </div>
            </div>
            <div v-else-if="docPreviewLoading" v-loading="true" style="min-height: 100px"></div>
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

        <!-- 2026-08-09:付款计划 Tab -->
        <el-tab-pane label="付款计划" name="payments">
          <div class="payment-panel" v-loading="paymentsLoading">
            <div class="payment-actions">
              <el-button type="primary" size="small" @click="openCreatePayment">
                <el-icon><Plus /></el-icon> 新增付款计划
              </el-button>
              <el-button type="success" plain size="small" :loading="aiExtracting" @click="extractPaymentsByAi">
                <el-icon><MagicStick /></el-icon> AI 提取付款计划
              </el-button>
            </div>

            <el-table :data="payments" stripe size="small" empty-text="暂无付款计划,点上方 AI 提取或手动添加">
              <el-table-column label="期次" prop="sequence" width="60" align="center" />
              <el-table-column label="付款标题" prop="title" min-width="120" />
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">
                  {{ row.amount ? `¥${Number(row.amount).toLocaleString()}` : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="应付款日" prop="dueDate" width="120" />
              <el-table-column label="实付日" prop="paidDate" width="120">
                <template #default="{ row }">
                  {{ row.paidDate || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="paymentStatusType(row.status)" size="small">
                    {{ paymentStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="备注" prop="remarks" min-width="100" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button text size="small" type="primary" @click="openEditPayment(row)">编辑</el-button>
                  <el-button v-if="row.status !== 'paid'" text size="small" type="success" @click="markPaymentPaid(row)">标记已付</el-button>
                  <el-button text size="small" type="danger" @click="deletePayment(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 风险审查" name="ai-review">
          <div class="ai-review-panel" v-loading="aiReviewLoading">
            <div v-if="!aiReview" class="ai-review-empty">
              <el-empty description="尚未进行 AI 风险审查">
                <el-button type="primary" :loading="aiReviewLoading" @click="runAiReview">
                  <el-icon><MagicStick /></el-icon>
                  开始 AI 审查
                </el-button>
              </el-empty>
            </div>

            <div v-else class="ai-review-content">
              <!-- 风险等级卡片 -->
              <div class="risk-summary" :class="`risk-${aiReview.riskLevel}`">
                <div class="risk-badge">
                  <el-icon :size="32">
                    <CircleCloseFilled v-if="aiReview.riskLevel === 'high'" />
                    <WarningFilled v-else-if="aiReview.riskLevel === 'medium'" />
                    <CircleCheckFilled v-else />
                  </el-icon>
                  <span class="risk-label">{{ riskLevelLabel(aiReview.riskLevel) }}</span>
                </div>
                <div class="risk-score">风险评分 {{ aiReview.riskScore }}/100</div>
              </div>

              <!-- 总体评估 -->
              <el-alert v-if="aiReview.summary" :title="aiReview.summary" type="info"
                show-icon :closable="false" style="margin-bottom: 16px" />

              <!-- 风险项 -->
              <el-collapse v-model="activeCollapse">
                <el-collapse-item name="riskItems" title="风险项" v-if="aiReview.riskItems && aiReview.riskItems.length">
                  <template #title>
                    <span class="section-title">
                      <el-icon><Warning /></el-icon>
                      风险项 ({{ aiReview.riskItems.length }})
                    </span>
                  </template>
                  <div v-for="(item, idx) in aiReview.riskItems" :key="idx" class="risk-item" :class="`severity-${item.severity || 'medium'}`">
                    <el-tag :type="severityTagType(item.severity)" size="small">{{ severityLabel(item.severity) }}</el-tag>
                    <span class="risk-category" v-if="item.category">{{ item.category }}</span>
                    <div class="risk-desc">{{ item.description }}</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item name="keyClauses" title="关键条款" v-if="aiReview.keyClauses && aiReview.keyClauses.length">
                  <template #title>
                    <span class="section-title">
                      <el-icon><Document /></el-icon>
                      关键条款 ({{ aiReview.keyClauses.length }})
                    </span>
                  </template>
                  <div v-for="(clause, idx) in aiReview.keyClauses" :key="idx" class="key-clause">
                    <div class="clause-title">{{ clause.title }}</div>
                    <div class="clause-summary">{{ clause.summary }}</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item name="missingClauses" title="缺失条款" v-if="aiReview.missingClauses && aiReview.missingClauses.length">
                  <template #title>
                    <span class="section-title">
                      <el-icon><WarningFilled /></el-icon>
                      缺失条款 ({{ aiReview.missingClauses.length }})
                    </span>
                  </template>
                  <ul class="clause-list">
                    <li v-for="(m, idx) in aiReview.missingClauses" :key="idx">{{ m }}</li>
                  </ul>
                </el-collapse-item>

                <el-collapse-item name="suggestions" title="修改建议" v-if="aiReview.suggestions && aiReview.suggestions.length">
                  <template #title>
                    <span class="section-title">
                      <el-icon><Edit /></el-icon>
                      修改建议 ({{ aiReview.suggestions.length }})
                    </span>
                  </template>
                  <ul class="clause-list">
                    <li v-for="(s, idx) in aiReview.suggestions" :key="idx">{{ s }}</li>
                  </ul>
                </el-collapse-item>
              </el-collapse>

              <div class="ai-review-footer">
                <el-button text @click="runAiReview" :loading="aiReviewLoading">
                  <el-icon><Refresh /></el-icon>
                  重新审查
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- Submit Dialog -->
      <ContractSubmitDialog v-model="showSubmit" :contract-id="contractId" @submitted="onSubmitted" />

      <!-- Edit Dialog -->
      <ContractEditDialog v-model="showEdit" :contract="contract" @saved="onSaved" />

      <!-- 2026-08-09:付款计划编辑对话框 -->
      <el-dialog v-model="paymentDialogVisible" :title="paymentDialogMode === 'create' ? '新增付款计划' : '编辑付款计划'" width="520px" append-to-body>
        <el-form :model="paymentEditing" label-width="100px">
          <el-form-item label="期次">
            <el-input-number v-model="paymentEditing.sequence" :min="1" />
          </el-form-item>
          <el-form-item label="付款标题">
            <el-input v-model="paymentEditing.title" placeholder="如:首付款 / 尾款 / 月供" />
          </el-form-item>
          <el-form-item label="金额(元)">
            <el-input-number v-model="paymentEditing.amount" :min="0" :precision="2" style="width: 200px" />
          </el-form-item>
          <el-form-item label="应付款日">
            <el-date-picker v-model="paymentEditing.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="paymentEditing.status">
              <el-option label="待付" value="pending" />
              <el-option label="已付" value="paid" />
              <el-option label="逾期" value="overdue" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="paymentEditing.remarks" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="paymentDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="savePayment">保存</el-button>
        </template>
      </el-dialog>

      <!-- 2026-08-09:AI 抽取付款计划确认 -->
      <el-dialog v-model="aiDraftDialogVisible" title="AI 抽取的付款计划(请确认)" width="700px" append-to-body>
        <p style="color: #909399; font-size: 13px">
          AI 从合同文档中识别出以下付款计划,确认无误后点击"全部添加"入库;若有误可直接关闭窗口后手动新增。
        </p>
        <el-table :data="aiDraftList" stripe size="small" max-height="400">
          <el-table-column label="付款标题" prop="title" />
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              {{ row.amount ? `¥${Number(row.amount).toLocaleString()}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="应付款日" prop="dueDate" width="120" />
          <el-table-column label="备注" prop="remarks" show-overflow-tooltip />
        </el-table>
        <template #footer>
          <el-button @click="aiDraftDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmAiDrafts">全部添加</el-button>
        </template>
      </el-dialog>

      <!-- Reject Dialog -->
      <el-dialog v-model="showReject" title="拒绝审批" width="400px" append-to-body>
        <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请填写拒绝原因（必填）" />
        <template #footer>
          <el-button @click="showReject = false">取消</el-button>
          <el-button type="danger" :disabled="!rejectRemark.trim()" :loading="submitting" @click="handleReject">确认拒绝</el-button>
        </template>
      </el-dialog>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi, type Contract, type ApprovalNode, type ContractApproval, type IntegrityResult, type ContractReview } from '@/api/contract'
import { contractPaymentApi, type ContractPayment, type PaymentPlanDraft } from '@/api/contract'
import ContractSubmitDialog from '@/components/ContractSubmitDialog.vue'
import ContractEditDialog from '@/components/ContractEditDialog.vue'

const props = defineProps<{
  /** 弹窗显示状态 */
  modelValue: boolean
  /** 合同 ID */
  contractId: number
}>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  /** 任何数据变化后通知父页面刷新 */
  refreshed: []
}>()

const router = useRouter()

// 弹窗可见性双向绑定
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const dialogTitle = computed(() => contract.value?.contractNo || '合同详情')

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
const showEdit = ref(false)

// AI 审查相关
const aiReview = ref<ContractReview | null>(null)
const aiReviewLoading = ref(false)
const activeCollapse = ref(['riskItems', 'keyClauses', 'missingClauses', 'suggestions'])

// 2026-08-09 #4:文档临时预览(在弹窗内展示,不跳编辑器)
const docPreviewText = ref<string | null>(null)
const docPreviewLoading = ref(false)

// 2026-08-09:付款计划(CRUD + AI 抽取)
const payments = ref<ContractPayment[]>([])
const paymentsLoading = ref(false)
const paymentDialogVisible = ref(false)
const paymentDialogMode = ref<'create' | 'edit'>('create')
const paymentEditing = ref<ContractPayment>({} as ContractPayment)
const aiExtracting = ref(false)
const aiDraftDialogVisible = ref(false)
const aiDraftList = ref<PaymentPlanDraft[]>([])

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
// 编辑权限:draft / rejected 状态,且当前用户是 owner
const canEdit = computed(() =>
  (contract.value.status === 'draft' || contract.value.status === 'rejected') &&
  contract.value.ownerUserId === getCurrentUserId()
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

function getToken(): string {
  return sessionStorage.getItem('token') || ''
}

// 监听 contractId / visible 变化,打开时拉数据,关闭时重置
watch([() => props.modelValue, () => props.contractId], ([v, id]) => {
  if (v && id) {
    loadContract()
    loadPayments()  // 2026-08-09:打开时加载付款计划
  } else if (!v) {
    // 关闭弹窗时重置数据,下次打开重新加载
    contract.value = {} as Contract
    approvalNodes.value = []
    approvals.value = []
    integrity.value = null
    aiReview.value = null
    activeTab.value = 'approval'
    showSubmit.value = false
    showReject.value = false
    showEdit.value = false
    rejectRemark.value = ''
    payments.value = []  // 2026-08-09:关闭时清空付款计划
    docPreviewText.value = null
  }
}, { immediate: true })

async function loadContract() {
  if (!props.contractId) return
  loading.value = true
  try {
    const data = await contractApi.detail(props.contractId)
    contract.value = data
    approvalNodes.value = data.approvalNodes || []
    approvals.value = data.approvals || []

    const userId = getCurrentUserId()
    isCurrentUserApprover.value = approvalNodes.value.some(
      n => n.status === 'pending' && n.approverId === userId
    )

    if (data.status === 'approved' || data.status === 'pending_approval') {
      try {
        integrity.value = await contractApi.integrity(props.contractId)
      } catch {}
    }
    emit('refreshed')
  } catch {
    ElMessage.error('加载合同详情失败')
  } finally {
    loading.value = false
  }
}

async function runAiReview() {
  aiReviewLoading.value = true
  aiReview.value = null
  try {
    await contractApi.reviewContract(props.contractId, (event: { type: string; data: any }) => {
      if (event.type === 'done') {
        aiReview.value = event.data.review as ContractReview
      } else if (event.type === 'error') {
        const code = event.data?.code
        const msg = event.data?.message || 'AI 审查失败'
        if (code === 'AI_NOT_CONFIGURED') {
          ElMessage.warning('LLM 服务未配置,请前往管理后台 → AI 配置')
        } else {
          ElMessage.error(msg)
        }
      }
    }, getToken())
  } catch (e) {
    ElMessage.error('AI 审查请求失败: ' + String(e))
  } finally {
    aiReviewLoading.value = false
  }
}

function riskLevelLabel(level: string) {
  const map: Record<string, string> = { low: '低风险', medium: '中等风险', high: '高风险' }
  return map[level] || level
}

function severityLabel(s?: string) {
  const map: Record<string, string> = { low: '轻微', medium: '中等', high: '严重' }
  return s ? (map[s] || s) : '中等'
}

function severityTagType(s?: string) {
  const map: Record<string, string> = { low: 'success', medium: 'warning', high: 'danger' }
  return (s && map[s]) ? map[s] : 'warning'
}

// 2026-08-09:付款计划状态标签
function paymentStatusLabel(s?: string) {
  const map: Record<string, string> = { pending: '待付', paid: '已付', overdue: '逾期' }
  return (s && map[s]) ? map[s] : '待付'
}

function paymentStatusType(s?: string) {
  const map: Record<string, string> = { pending: 'warning', paid: 'success', overdue: 'danger' }
  return (s && map[s]) ? map[s] : 'warning'
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
  // 2026-08-09 #4:跳转编辑器时,标记返回目标是合同管理(而非 /home)
  sessionStorage.setItem('miaotong:editor:returnTo', `contract:${props.contractId}`)
  visible.value = false
  router.push(`/editor/${contract.value.documentId}`)
}

// 2026-08-09 #4:在弹窗内临时预览文档纯文本(不离开)
async function runDocPreview() {
  if (!contract.value.documentId) return
  docPreviewLoading.value = true
  docPreviewText.value = null
  try {
    const res = await fetch(`/api/documents/${contract.value.documentId}/text`, {
      headers: { Authorization: 'Bearer ' + getToken() }
    })
    const json = await res.json().catch(() => ({}))
    docPreviewText.value = json.text || '(文档为空)'
  } catch (e) {
    ElMessage.error('文档预览失败: ' + String(e))
  } finally {
    docPreviewLoading.value = false
  }
}

async function handleApprove() {
  try {
    await ElMessageBox.confirm('确认通过此合同审批？', '审批通过', { type: 'success' })
    submitting.value = true
    await contractApi.approve(props.contractId)
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
    await contractApi.reject(props.contractId, rejectRemark.value.trim())
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
    await contractApi.cancel(props.contractId)
    ElMessage.success('已撤回')
    loadContract()
  } catch {} finally {
    submitting.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除此合同？', '删除确认', { type: 'warning' })
    await contractApi.delete(props.contractId)
    ElMessage.success('已删除')
    visible.value = false
    emit('refreshed')
  } catch {}
}

function onSubmitted() {
  loadContract()
}

function onSaved() {
  loadContract()
}

// ===== 付款计划相关 =====

async function loadPayments() {
  if (!props.contractId) return
  paymentsLoading.value = true
  try {
    payments.value = await contractPaymentApi.list(props.contractId)
  } catch {} finally {
    paymentsLoading.value = false
  }
}

function openCreatePayment() {
  paymentEditing.value = {
    sequence: (payments.value.length || 0) + 1,
    status: 'pending',
    currency: 'CNY',
    title: '',
    amount: undefined,
    dueDate: new Date().toISOString().slice(0, 10)
  } as ContractPayment
  paymentDialogMode.value = 'create'
  paymentDialogVisible.value = true
}

function openEditPayment(p: ContractPayment) {
  paymentEditing.value = { ...p }
  paymentDialogMode.value = 'edit'
  paymentDialogVisible.value = true
}

async function savePayment() {
  if (!paymentEditing.value.title || !paymentEditing.value.dueDate) {
    ElMessage.warning('请填写付款标题和到期日')
    return
  }
  try {
    if (paymentDialogMode.value === 'create') {
      await contractPaymentApi.create(props.contractId, paymentEditing.value)
    } else {
      await contractPaymentApi.update(props.contractId, paymentEditing.value.id!, paymentEditing.value)
    }
    ElMessage.success('付款计划已保存')
    paymentDialogVisible.value = false
    loadPayments()
  } catch (e) {
    ElMessage.error('保存失败: ' + String(e))
  }
}

async function deletePayment(p: ContractPayment) {
  try {
    await ElMessageBox.confirm(`确认删除付款计划【${p.title}】?`, '删除确认', { type: 'warning' })
    await contractPaymentApi.delete(props.contractId, p.id!)
    ElMessage.success('已删除')
    loadPayments()
  } catch {}
}

async function markPaymentPaid(p: ContractPayment) {
  try {
    await contractPaymentApi.markPaid(props.contractId, p.id!)
    ElMessage.success('已标记为已付款')
    loadPayments()
  } catch (e) {
    ElMessage.error('操作失败: ' + String(e))
  }
}

// AI 自动抽取付款计划(2026-08-09)
async function extractPaymentsByAi() {
  aiExtracting.value = true
  try {
    const drafts = await contractPaymentApi.extractByAi(props.contractId)
    if (!drafts || drafts.length === 0) {
      // 正常情况:AI 抽取成功,但合同没有付款条款
      ElMessage.info('AI 未识别到付款条款,可手动新增付款计划')
      return
    }
    aiDraftList.value = drafts
    aiDraftDialogVisible.value = true
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || String(e)
    // 后端已经按文档类型给了准确提示,这里直接透传
    ElMessage.warning(msg)
  } finally {
    aiExtracting.value = false
  }
}

async function confirmAiDrafts() {
  // 逐条入库
  let successCount = 0
  for (const draft of aiDraftList.value) {
    try {
      await contractPaymentApi.create(props.contractId, {
        sequence: (payments.value.length || 0) + successCount + 1,
        title: draft.title || `第${(payments.value.length || 0) + successCount + 1}期`,
        amount: typeof draft.amount === 'string' ? Number(draft.amount) : draft.amount,
        currency: draft.currency || 'CNY',
        dueDate: draft.dueDate,
        status: 'pending',
        remarks: draft.remarks
      } as ContractPayment)
      successCount++
    } catch {}
  }
  ElMessage.success(`已添加 ${successCount} 条付款计划`)
  aiDraftDialogVisible.value = false
  loadPayments()
}
</script>

<style scoped>
.contract-detail {
  padding: 4px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left h2 {
  margin: 0;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #ebeef5;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
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
  padding: 16px;
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
  padding: 12px;
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
  margin-top: -16px;
}

.doc-preview {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.doc-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 2026-08-09 #4:文档临时预览面板(在弹窗内) */
.doc-preview-content {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f0f2f5;
  font-size: 13px;
  color: #303133;
}

.preview-body {
  margin: 0;
  padding: 12px;
  max-height: 360px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, "Cascadia Code", "Source Code Pro", Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
}

.preview-footer {
  padding: 8px 12px;
  text-align: right;
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
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

.ai-review-panel {
  padding: 4px;
}

.ai-review-empty {
  text-align: center;
  padding: 32px 0;
}

.risk-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-radius: 8px;
  margin-bottom: 12px;
  border-left: 6px solid;
}

.risk-summary.risk-low {
  background: #f0f9eb;
  border-left-color: var(--el-color-success);
  color: var(--el-color-success);
}

.risk-summary.risk-medium {
  background: #fdf6ec;
  border-left-color: var(--el-color-warning);
  color: var(--el-color-warning);
}

.risk-summary.risk-high {
  background: #fef0f0;
  border-left-color: var(--el-color-danger);
  color: var(--el-color-danger);
}

.risk-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.risk-label {
  font-size: 16px;
  font-weight: 600;
}

.risk-score {
  font-size: 13px;
  opacity: 0.85;
}

.section-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}

.risk-item {
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 6px;
  background: #fafafa;
  border-left: 3px solid #dcdfe6;
}

.risk-item.severity-high {
  background: #fef0f0;
  border-left-color: var(--el-color-danger);
}

.risk-item.severity-medium {
  background: #fdf6ec;
  border-left-color: var(--el-color-warning);
}

.risk-item.severity-low {
  background: #f0f9eb;
  border-left-color: var(--el-color-success);
}

.risk-category {
  margin-left: 8px;
  font-weight: 600;
  color: #303133;
}

.risk-desc {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.key-clause {
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 6px;
  background: #f5f7fa;
}

.clause-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.clause-summary {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.clause-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  font-size: 13px;
  line-height: 1.8;
}

.ai-review-footer {
  margin-top: 12px;
  text-align: right;
}

/* 2026-08-09:付款计划 */
.payment-panel {
  padding: 4px;
}

.payment-actions {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
</style>

<style>
/* 全局样式,避免 scoped 影响 el-dialog body 高度 */
.contract-detail-dialog .el-dialog__body {
  max-height: 75vh;
  overflow-y: auto;
}
</style>