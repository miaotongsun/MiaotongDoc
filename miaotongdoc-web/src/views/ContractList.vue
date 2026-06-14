<template>
  <div class="contract-page">
    <!-- Stats -->
    <div class="stats-row">
      <div class="stat-card" v-for="(val, key) in stats" :key="key">
        <div class="stat-value">{{ val }}</div>
        <div class="stat-label">{{ statusLabels[key] || key }}</div>
      </div>
    </div>

    <!-- Filters -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="状态" clearable size="small" @change="loadContracts" style="width: 130px">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filterType" placeholder="合同类型" clearable size="small" @change="loadContracts" style="width: 130px">
        <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-popover trigger="click" :width="220" :show-arrow="false" placement="bottom-start">
        <template #reference>
          <el-button size="small">
            {{ filterDeptName || '部门' }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
        </template>
        <div>
          <el-button v-if="filterDeptId" text size="small" @click="clearDeptFilter" style="margin-bottom: 8px">清除筛选</el-button>
          <el-tree :data="deptTreeData" node-key="id" default-expand-all highlight-current
            @node-click="onDeptTreeNodeClick" />
        </div>
      </el-popover>
      <el-input v-model="filterKeyword" placeholder="搜索合同编号/甲乙方..." clearable size="small"
        style="width: 250px" @keyup.enter="loadContracts" @clear="loadContracts" />
      <div style="flex: 1"></div>
      <el-button type="primary" size="small" @click="showCreate = true">
        <el-icon><Plus /></el-icon> 新建合同
      </el-button>
    </div>

    <!-- Table -->
    <el-table :data="contracts" style="width: 100%" v-loading="loading" @row-click="viewContract">
      <el-table-column prop="contractNo" label="合同编号" width="160" />
      <el-table-column prop="documentTitle" label="文档标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ typeLabels[row.contractType] || row.contractType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="partyA" label="甲方" width="150" show-overflow-tooltip />
      <el-table-column prop="partyB" label="乙方" width="150" show-overflow-tooltip />
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">
          {{ row.amount ? `¥${row.amount.toLocaleString()}` : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ statusLabels[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="departmentName" label="部门" width="120" show-overflow-tooltip />
      <el-table-column label="到期日" width="110">
        <template #default="{ row }">
          {{ row.expiryDate || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="primary" @click.stop="viewContract(row)">详情</el-button>
          <el-button v-if="row.status === 'draft'" text size="small" type="success" @click.stop="submitContract(row)">提交审批</el-button>
          <el-button v-if="row.status === 'draft'" text size="small" type="danger" @click.stop="deleteContract(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar" v-if="total > pageSize">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
        v-model:current-page="currentPage" @current-change="loadContracts" />
    </div>

    <ContractCreateDialog v-model="showCreate" @created="onCreated" />
    <ContractSubmitDialog v-model="showSubmit" :contract-id="submitContractId" @submitted="onSubmitted" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi, type Contract } from '@/api/contract'
import { departmentApi, type Department } from '@/api/department'
import ContractCreateDialog from '@/components/ContractCreateDialog.vue'
import ContractSubmitDialog from '@/components/ContractSubmitDialog.vue'

const router = useRouter()
const loading = ref(false)
const contracts = ref<Contract[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const departments = ref<Department[]>([])
const showCreate = ref(false)
const showSubmit = ref(false)
const submitContractId = ref(0)

const filterStatus = ref('')
const filterType = ref('')
const filterDeptId = ref<number | undefined>(undefined)
const filterDeptName = ref('')
const filterKeyword = ref('')

const deptTreeData = computed(() => {
  const list = departments.value
  const roots: any[] = []
  const map = new Map<number, any>()
  list.forEach(d => map.set(d.id, { id: d.id, label: d.name, children: [] }))
  list.forEach(d => {
    const node = map.get(d.id)!
    if (d.parentId && map.has(d.parentId)) {
      map.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})

function onDeptTreeNodeClick(data: any) {
  filterDeptId.value = data.id
  filterDeptName.value = data.label
  loadContracts()
}

function clearDeptFilter() {
  filterDeptId.value = undefined
  filterDeptName.value = ''
  loadContracts()
}

const stats = ref<Record<string, number>>({})

const statusLabels: Record<string, string> = {
  draft: '草稿',
  pending_approval: '审批中',
  approved: '已审批',
  rejected: '已拒绝',
  expired: '已过期',
  total: '总计'
}

const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '审批中', value: 'pending_approval' },
  { label: '已审批', value: 'approved' },
  { label: '已拒绝', value: 'rejected' },
  { label: '已过期', value: 'expired' }
]

const typeLabels: Record<string, string> = {
  purchase: '采购',
  sale: '销售',
  lease: '租赁',
  service: '服务',
  labor: '劳务',
  construction: '工程',
  other: '其他'
}

const typeOptions = Object.entries(typeLabels).map(([value, label]) => ({ label, value }))

onMounted(async () => {
  loadContracts()
  loadStats()
  try {
    departments.value = await departmentApi.getAll()
  } catch {}
})

async function loadContracts() {
  loading.value = true
  try {
    const res = await contractApi.list({
      status: filterStatus.value || undefined,
      contractType: filterType.value || undefined,
      departmentId: filterDeptId.value,
      keyword: filterKeyword.value || undefined,
      page: currentPage.value - 1,
      size: pageSize
    })
    contracts.value = res.content || []
    total.value = res.totalElements || 0
  } catch (e: any) {
    console.error('加载合同列表失败:', e)
    contracts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await contractApi.stats()
  } catch {}
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    draft: 'info', pending_approval: 'warning', approved: 'success',
    rejected: 'danger', expired: 'info'
  }
  return map[status] || ''
}

function viewContract(row: Contract) {
  router.push(`/contracts/${row.id}`)
}

function submitContract(row: Contract) {
  submitContractId.value = row.id
  showSubmit.value = true
}

async function deleteContract(row: Contract) {
  try {
    await ElMessageBox.confirm('确定删除此合同？', '删除确认', { type: 'warning' })
    await contractApi.delete(row.id)
    ElMessage.success('已删除')
    loadContracts()
    loadStats()
  } catch {}
}

function onCreated() {
  loadContracts()
  loadStats()
}

function onSubmitted() {
  loadContracts()
  loadStats()
}
</script>

<style scoped>
.contract-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  display: flex;
  gap: 12px;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  border: 1px solid #ebeef5;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
