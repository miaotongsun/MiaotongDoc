<template>
  <div class="home-page">
    <aside class="sidebar">
      <div class="logo">
        <h2>MiaotongDoc</h2>
        <p class="slogan">妙思互通，同心同步</p>
      </div>
      <ul class="nav-list">
        <li :class="{ active: activeTab === 'all' }" @click="switchTab('all')">
          <el-icon><Files /></el-icon>
          <span>全部文档</span>
        </li>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'word' }" @click="switchTab('word')">
          <el-icon><Document /></el-icon>
          <span>MiaotongWord</span>
        </li>
        <li :class="{ active: activeTab === 'cell' }" @click="switchTab('cell')">
          <el-icon><Grid /></el-icon>
          <span>MiaotongSheet</span>
        </li>
        <li :class="{ active: activeTab === 'slide' }" @click="switchTab('slide')">
          <el-icon><Picture /></el-icon>
          <span>MiaotongPPT</span>
        </li>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'shared' }" @click="switchTab('shared')">
          <el-icon><Share /></el-icon>
          <span>与我共享</span>
        </li>
        <li :class="{ active: activeTab === 'starred' }" @click="switchTab('starred')">
          <el-icon><Star /></el-icon>
          <span>收藏文档</span>
        </li>
        <li class="nav-divider"></li>
        <li :class="{ active: activeTab === 'contract' }" @click="switchTab('contract')">
          <el-icon><Notebook /></el-icon>
          <span>合同管理</span>
        </li>
        <li :class="{ active: activeTab === 'activity' }" @click="switchTab('activity')">
          <el-icon><Bell /></el-icon>
          <span>团队动态</span>
        </li>
        <li v-if="isAdmin" :class="{ active: activeTab === 'admin' }" @click="switchTab('admin')">
          <el-icon><Setting /></el-icon>
          <span>管理后台</span>
        </li>
      </ul>
    </aside>

    <div class="main-content">
      <header class="top-bar">
        <div class="top-bar-left">
          <el-button type="primary" @click="showCreate = true" v-if="isDocView">
            <el-icon><Plus /></el-icon>
            新建文档
          </el-button>
          <el-upload v-if="isDocView" :show-file-list="false" :before-upload="handleUpload" accept=".docx,.xlsx,.pptx,.pdf">
            <el-button>
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
          </el-upload>
          <h3 v-if="!isDocView" class="page-title">{{ activeTabLabel }}</h3>
        </div>
        <el-input v-if="isDocView" v-model="searchKeyword" placeholder="搜索文档..." clearable
          :prefix-icon="Search" @input="handleSearchInput" @clear="handleSearchClear"
          class="search-input" />
        <div class="top-bar-right">
          <NotificationBell />
          <ThemeSwitch />
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ userName.charAt(0) }}</el-avatar>
              <span class="user-name">{{ userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ employeeId }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Document list view -->
      <main class="content-area" v-if="isDocView">
        <div class="content-header">
          <div class="header-left">
            <h3>{{ activeTabLabel }}</h3>
            <span class="doc-count">{{ documents.length }} 个文档</span>
          </div>
          <div class="header-right">
            <el-radio-group v-model="viewMode" size="small" class="view-toggle">
              <el-radio-button value="grid">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
              <el-radio-button value="list">
                <el-icon><List /></el-icon>
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- Batch action bar -->
        <transition name="slide-down">
          <div v-if="selectedIds.size > 0" class="batch-bar">
            <div class="batch-left">
              <el-button text @click="clearSelection">
                <el-icon><Close /></el-icon>
              </el-button>
              <span class="batch-count">已选择 {{ selectedIds.size }} 个文档</span>
              <el-button text size="small" @click="selectAll">全选</el-button>
            </div>
            <div class="batch-actions">
              <el-button size="small" @click="batchShare">
                <el-icon><Share /></el-icon> 批量共享
              </el-button>
              <el-button size="small" type="danger" plain @click="batchDelete">
                <el-icon><Delete /></el-icon> 批量删除
              </el-button>
            </div>
          </div>
        </transition>

        <div class="doc-grid" v-if="viewMode === 'grid' && documents.length > 0">
          <DocCard v-for="doc in documents" :key="doc.id" :doc="doc"
            :selected="selectedIds.has(doc.id)" @toggle-select="toggleDocSelection(doc.id)"
            @click="openDocument(doc.id)" @delete="handleDelete(doc.id)"
            @share="openShareDialog" />
        </div>
        <el-empty v-else-if="viewMode === 'grid'" :description="emptyText" />

        <el-table v-else :data="filteredDocuments"
          class="doc-table" @selection-change="handleTableSelectionChange" row-key="id"
          :row-class-name="tableRowClassName" @row-dblclick="handleRowDblClick">
          <el-table-column type="selection" width="40" />
          <el-table-column label="文档" min-width="240" sortable :sort-method="sortByTitle">
            <template #default="{ row }">
              <div class="doc-name-cell">
                <el-icon class="doc-type-icon" :style="{ color: docTypeColor(row.docType) }">
                  <Document v-if="row.docType === 'word'" />
                  <Grid v-else-if="row.docType === 'cell'" />
                  <Picture v-else-if="row.docType === 'slide'" />
                  <Files v-else />
                </el-icon>
                <span class="doc-title-text">{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="100" prop="ownerName" sortable />
          <el-table-column label="部门" width="140">
            <template #header>
              <el-popover trigger="click" :width="220" :show-arrow="false" placement="bottom-start"
                @show="onDeptFilterShow" @hide="onDeptFilterHide">
                <template #reference>
                  <span class="dept-filter-trigger">
                    部门
                    <span class="dept-caret" :class="{ 'is-active': selectedDeptIds.size > 0 }">
                      <i class="sort-caret descending"></i>
                    </span>
                  </span>
                </template>
                <el-tree ref="deptTreeRef" :data="deptTreeData" show-checkbox node-key="id"
                  default-expand-all check-strictly highlight-current
                  @check="onDeptTreeCheck" />
              </el-popover>
            </template>
            <template #default="{ row }">
              {{ row.departmentName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160" sortable :sort-method="sortByCreatedAt">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160" sortable :sort-method="sortByUpdatedAt">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="最近更新人" width="110" prop="ownerName" />
          <el-table-column label="版本号" width="80" align="center">
            <template #default="{ row }">
              v{{ row.currentVersion }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90" sortable :sort-method="sortBySize" align="right">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="更多操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="handleTableCommand($event, row)">
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="star">
                      <el-icon><Star /></el-icon>
                      {{ row.isStarred ? '取消收藏' : '收藏' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="rename">
                      <el-icon><Edit /></el-icon>
                      重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="share">
                      <el-icon><Share /></el-icon>
                      分享
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyText" />
          </template>
        </el-table>
      </main>

      <!-- Inline contract view -->
      <main class="content-area" v-else-if="activeTab === 'contract'">
        <ContractList />
      </main>

      <!-- Inline activity view -->
      <main class="content-area" v-else-if="activeTab === 'activity'">
        <ActivityFeed />
      </main>

      <!-- Inline admin view -->
      <main class="content-area" v-else-if="activeTab === 'admin'">
        <AdminPanel />
      </main>
    </div>

    <CreateDocDialog v-model="showCreate" @created="handleCreated" />
    <ShareDialog v-model="showShareDialog" :doc-id="shareDocId" :doc-ids="shareDocIds" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDocumentStore } from '@/stores/document'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import { Search, List, MoreFilled } from '@element-plus/icons-vue'
import { documentApi } from '@/api/document'
import { departmentApi, type Department } from '@/api/department'
import DocCard from '@/components/DocCard.vue'
import CreateDocDialog from '@/components/CreateDocDialog.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import ActivityFeed from '@/views/ActivityFeed.vue'
import AdminPanel from '@/views/Admin.vue'
import ContractList from '@/views/ContractList.vue'

const router = useRouter()
const documentStore = useDocumentStore()
const userStore = useUserStore()

const activeTab = ref('all')
const searchKeyword = ref('')
const showCreate = ref(false)
const departments = ref<Department[]>([])
const selectedDeptIds = ref<Set<number>>(new Set())
const showShareDialog = ref(false)
const shareDocId = ref(0)
const shareDocIds = ref<number[]>([])
const selectedIds = ref<Set<number>>(new Set())
const viewMode = ref<'grid' | 'list'>((localStorage.getItem('viewMode') as 'grid' | 'list') || 'grid')
watch(viewMode, (val) => localStorage.setItem('viewMode', val))
const deptTreeRef = ref<InstanceType<typeof ElTree>>()
const sortBy = ref('updatedAt')

const documents = computed(() => documentStore.documents)
const userName = computed(() => sessionStorage.getItem('name') || '用户')
const employeeId = computed(() => sessionStorage.getItem('employeeId') || '')
const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')

const isDocView = computed(() => !['activity', 'admin', 'contract'].includes(activeTab.value))

const tabLabels: Record<string, string> = {
  all: '全部文档',
  word: 'Word',
  cell: 'Sheet',
  slide: 'PPT',
  shared: '与我共享',
  starred: '收藏文档',
  activity: '团队动态',
  admin: '管理后台',
  contract: '合同管理'
}

const activeTabLabel = computed(() => tabLabels[activeTab.value] || '全部文档')

const emptyText = computed(() => {
  if (searchKeyword.value) return '未找到匹配的文档'
  if (activeTab.value === 'shared') return '暂无他人共享的文档'
  if (activeTab.value === 'starred') return '暂无收藏文档'
  if (selectedDeptIds.value.size > 0) return '该部门暂无文档'
  return '暂无文档，点击"新建文档"开始创建'
})

onMounted(async () => {
  documentStore.fetchDocuments({ sort: sortBy.value })
  try {
    departments.value = await departmentApi.getAll()
  } catch {
    ElMessage.warning('部门列表加载失败')
  }
})

function switchTab(tab: string) {
  activeTab.value = tab
  selectedIds.value = new Set()
  if (['activity', 'admin', 'contract'].includes(tab)) return

  selectedDeptIds.value = new Set()
  searchKeyword.value = ''

  const params: any = { page: 0, size: 50, sort: sortBy.value }
  if (tab !== 'all') {
    params.type = tab
  }
  documentStore.fetchDocuments(params)
}

let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (!searchKeyword.value.trim()) {
      documentStore.fetchDocuments({ sort: sortBy.value })
      return
    }
    activeTab.value = 'all'
    selectedDeptIds.value = new Set()
    documentStore.fetchDocuments({ keyword: searchKeyword.value.trim(), sort: sortBy.value })
  }, 300)
}

function handleSearchClear() {
  documentStore.fetchDocuments({ sort: sortBy.value })
}

async function handleUpload(file: File) {
  try {
    await documentApi.upload(file)
    ElMessage.success('上传成功')
    documentStore.fetchDocuments({ sort: sortBy.value })
  } catch {
    ElMessage.error('上传失败')
  }
  return false
}

function openDocument(id: number) {
  router.push(`/editor/${id}`)
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除这个文档吗？', '确认删除', { type: 'warning' })
    await documentStore.deleteDocument(id)
    ElMessage.success('删除成功')
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function handleCreated() {
  documentStore.fetchDocuments({ sort: sortBy.value })
}

function openShareDialog(docId: number) {
  shareDocId.value = docId
  shareDocIds.value = []
  showShareDialog.value = true
}

function toggleDocSelection(id: number) {
  const newSet = new Set(selectedIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedIds.value = newSet
}

function selectAll() {
  selectedIds.value = new Set(documents.value.map((d: any) => d.id))
}

function clearSelection() {
  selectedIds.value = new Set()
}

async function batchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.size} 个文档吗？`, '批量删除', { type: 'warning' })
    const ids = Array.from(selectedIds.value)
    await documentApi.batchDelete(ids)
    ElMessage.success(`成功删除 ${ids.length} 个文档`)
    selectedIds.value = new Set()
    documentStore.fetchDocuments({ sort: sortBy.value })
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

function batchShare() {
  shareDocIds.value = Array.from(selectedIds.value)
  shareDocId.value = 0
  showShareDialog.value = true
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function docTypeColor(type: string) {
  const map: Record<string, string> = { word: '#2b579a', cell: '#217346', slide: '#d24726' }
  return map[type] || '#909399'
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(time?: string) {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function handleTableSelectionChange(rows: any[]) {
  selectedIds.value = new Set(rows.map(r => r.id))
}

function tableRowClassName({ row }: { row: any }) {
  return selectedIds.value.has(row.id) ? 'selected-row' : ''
}

function handleRowDblClick(row: any) {
  openDocument(row.id)
}

// Sort methods for el-table
function sortByTitle(a: any, b: any) {
  return (a.title || '').localeCompare(b.title || '')
}

function sortByCreatedAt(a: any, b: any) {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
}

function sortByUpdatedAt(a: any, b: any) {
  return new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()
}

function sortBySize(a: any, b: any) {
  return (a.fileSize || 0) - (b.fileSize || 0)
}

// Department tree filter
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

// Filtered documents with client-side dept filter
const filteredDocuments = computed(() => {
  if (selectedDeptIds.value.size === 0) return documents.value
  const deptMap = new Map(departments.value.map(d => [d.id, d.name]))
  return documents.value.filter((d: any) => {
    // For new documents with departmentId set
    if (d.departmentId && selectedDeptIds.value.has(d.departmentId)) return true
    // Fallback for old documents: match by departmentName from owner
    if (d.departmentName) {
      for (const id of selectedDeptIds.value) {
        if (deptMap.get(id) === d.departmentName) return true
      }
    }
    return false
  })
})

function onDeptTreeCheck() {
  const tree = deptTreeRef.value
  if (!tree) return
  const checkedKeys = tree.getCheckedKeys(false) as number[]
  selectedDeptIds.value = new Set(checkedKeys)
}

function onDeptFilterShow() {
  if (!deptTreeRef.value) return
  nextTick(() => {
    deptTreeRef.value!.setCheckedKeys(Array.from(selectedDeptIds.value), false)
  })
}

function onDeptFilterHide() {
  // no-op, tree state persists
}

async function handleTableCommand(cmd: string, row: any) {
  if (cmd === 'star') {
    try {
      await documentApi.toggleStar(row.id)
      documentStore.fetchDocuments({ sort: sortBy.value })
    } catch {
      ElMessage.error('操作失败')
    }
  } else if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
        inputValue: row.title,
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
      if (value && value.trim()) {
        await documentApi.rename(row.id, value.trim())
        documentStore.fetchDocuments({ sort: sortBy.value })
      }
    } catch {
      // cancelled
    }
  } else if (cmd === 'share') {
    openShareDialog(row.id)
  } else if (cmd === 'delete') {
    handleDelete(row.id)
  }
}
</script>

<style scoped>
.home-page {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

/* Sidebar */
.sidebar {
  width: 220px;
  background: #fff;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.logo {
  padding: 20px;
  border-bottom: 1px solid #e8ecf4;
}

.logo h2 {
  font-size: 18px;
  font-weight: 700;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 4px;
}

.logo .slogan {
  font-size: 12px;
  color: #909399;
}

.nav-list {
  list-style: none;
  padding: 12px 0;
  margin: 0;
  flex: 1;
  overflow-y: auto;
}

.nav-divider {
  height: 1px;
  background: #e8ecf4;
  margin: 8px 16px;
}

.nav-list li:not(.nav-divider) {
  padding: 10px 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;
}

.nav-list li:not(.nav-divider):hover {
  background: var(--hover-bg);
  color: var(--el-color-primary);
}

.nav-list li:not(.nav-divider).active {
  background: var(--active-bg);
  color: var(--el-color-primary);
  font-weight: 500;
}

.nav-list li:not(.nav-divider) .el-icon {
  font-size: 18px;
}

/* Main content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.top-bar-left {
  display: flex;
  gap: 12px;
}

.top-bar-left :deep(.el-button--primary) {
  background: var(--primary-gradient);
  border: none;
}

.top-bar-left :deep(.el-button--primary:hover) {
  opacity: 0.9;
  filter: brightness(1.1);
}

.top-bar-left .el-button {
  border-radius: 6px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  line-height: 1;
}

.search-input {
  width: 300px;
  margin-left: auto;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 20px;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  padding: 6px 12px;
  border-radius: 20px;
  transition: all 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Content area */
.content-area {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.view-toggle :deep(.el-radio-button__inner) {
  padding: 6px 10px;
}

.view-toggle :deep(.el-radio-button__inner .el-icon) {
  font-size: 14px;
}

.content-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.doc-count {
  font-size: 14px;
  color: #909399;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

/* Batch action bar */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.batch-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.batch-count {
  font-size: 14px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

/* List / table view */
.doc-table {
  border-radius: 8px;
  overflow: hidden;
}

.doc-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

.doc-table :deep(.el-table__row) {
  cursor: pointer;
  transition: background 0.2s;
}

.doc-table :deep(.el-table__row:hover > td) {
  background: var(--hover-bg) !important;
}

.doc-table :deep(.selected-row) {
  background: var(--el-color-primary-light-9);
}

.doc-table :deep(.selected-row:hover > td) {
  background: var(--el-color-primary-light-8) !important;
}

/* Department filter in column header */
.doc-table :deep(.el-dropdown) {
  line-height: inherit;
}

.dept-filter-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
  width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.dept-caret {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  height: 14px;
  width: 10px;
  vertical-align: middle;
  cursor: pointer;
  overflow: initial;
  position: relative;
}

.dept-caret .sort-caret {
  display: inline-block;
  width: 0;
  height: 0;
  border: 5px solid transparent;
  position: absolute;
  left: 0;
}

.dept-caret .sort-caret.descending {
  top: 4px;
  border-top-color: #c0c4cc;
}

.dept-caret.is-active .sort-caret.descending {
  border-top-color: var(--el-color-primary);
}

/* More action icon */
.more-icon {
  font-size: 18px;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.more-icon:hover {
  color: var(--el-color-primary);
  background: var(--hover-bg);
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-type-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.doc-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
