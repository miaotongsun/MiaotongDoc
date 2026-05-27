<template>
  <el-dialog v-model="visible" :title="isBatch ? `批量共享 (${docIds?.length} 个文档)` : '共享文档'" width="960px" top="5vh">
    <div class="share-root">
      <!-- 左1：机构树 -->
      <div class="col-dept">
        <div class="col-head">机构</div>
        <div class="dept-tree-wrap">
          <el-tree ref="deptTreeRef" :data="deptTreeData" :props="{ label: 'name', children: 'children' }"
            node-key="id" highlight-current :default-expanded-keys="deptExpanded"
            @node-click="onDeptClick" />
        </div>
      </div>

      <!-- 左2：部门用户 -->
      <div class="col-users">
        <div class="col-head">
          <span>{{ currentDeptName || '选择机构' }}</span>
          <span v-if="currentDeptUsers.length > 0" class="head-sub">{{ currentDeptUsers.length }} 人</span>
        </div>
        <div class="users-search">
          <el-input v-model="searchKeyword" placeholder="搜索姓名或工号..." :prefix-icon="Search" clearable size="small" />
        </div>
        <div class="users-body">
          <!-- 搜索结果 -->
          <template v-if="searchKeyword">
            <label v-for="u in filteredUsers" :key="u.id" class="user-row"
              :class="{ disabled: isSelf(u.id), shared: isShared(u.id) }">
              <input type="checkbox" :checked="checkedIds.has(u.id)" :disabled="isSelf(u.id) || isShared(u.id)"
                @change="toggleUser(u.id)" />
              <span class="u-name">{{ u.realName }}</span>
              <span class="u-id">{{ u.employeeId }}</span>
              <span v-if="isSelf(u.id)" class="tag-self">自己</span>
              <span v-else-if="isShared(u.id)" class="tag-shared">已共享</span>
            </label>
            <div v-if="filteredUsers.length === 0" class="no-data">无匹配</div>
          </template>
          <!-- 部门用户列表 -->
          <template v-else>
            <div v-if="currentDeptUsers.length > 0" class="dept-check-all">
              <label class="user-row check-all-row">
                <input type="checkbox" :checked="isAllDeptChecked" :indeterminate="isDeptIndeterminate"
                  @change="toggleDeptAll" />
                <span class="u-name" style="font-weight: 600">全选</span>
              </label>
            </div>
            <label v-for="u in currentDeptUsers" :key="u.id" class="user-row"
              :class="{ disabled: isSelf(u.id), shared: isShared(u.id) }">
              <input type="checkbox" :checked="checkedIds.has(u.id)" :disabled="isSelf(u.id) || isShared(u.id)"
                @change="toggleUser(u.id)" />
              <span class="u-name">{{ u.realName }}</span>
              <span class="u-id">{{ u.employeeId }}</span>
              <span v-if="isSelf(u.id)" class="tag-self">自己</span>
              <span v-else-if="isShared(u.id)" class="tag-shared">已共享</span>
            </label>
            <div v-if="!selectedDeptId" class="no-data">点击左侧机构查看成员</div>
            <div v-else-if="currentDeptUsers.length === 0" class="no-data">该机构暂无成员</div>
          </template>
        </div>
      </div>

      <!-- 右1：待共享 -->
      <div class="col-pending">
        <div class="col-head">
          <span>待共享</span>
          <span v-if="pickedUsers.length > 0" class="count-badge">{{ pickedUsers.length }}</span>
          <span v-if="pickedUsers.length > 0" class="clear-btn" @click="clearAll">清空</span>
        </div>

        <div v-if="pickedDeptInfos.length > 0" class="dept-tags">
          <el-tag v-for="d in pickedDeptInfos" :key="d.name" size="small" effect="plain"
            :style="{ background: 'var(--el-color-primary-light-9)', borderColor: 'var(--el-color-primary-light-7)', color: 'var(--el-color-primary)' }">
            {{ d.name }} ({{ d.selected }}/{{ d.total }})
          </el-tag>
        </div>

        <div class="picked-list">
          <div v-for="u in pickedUsers" :key="u.id" class="picked-row">
            <span class="p-name">{{ u.realName }}</span>
            <span class="p-id">{{ u.employeeId }}</span>
            <span class="p-del" @click="removePicked(u.id)">&times;</span>
          </div>
          <div v-if="pickedUsers.length === 0" class="no-data">从左侧勾选</div>
        </div>

        <div v-if="pickedUsers.length > 0" class="action-bar">
          <div class="perm-row">
            <span class="perm-lbl">权限</span>
            <el-radio-group v-model="newPerm" size="small">
              <el-radio-button v-for="o in permOpts" :key="o.v" :value="o.v">{{ o.l }}</el-radio-button>
            </el-radio-group>
          </div>
          <el-button type="primary" @click="doShare" style="width: 100%; margin-top: 8px">
            共享给 {{ pickedUsers.length }} 人
          </el-button>
        </div>
      </div>

      <!-- 右2：已共享 -->
      <div v-if="!isBatch" class="col-existing">
        <div class="col-head">
          <span>已共享</span>
          <span v-if="enrichedShares.length > 0" class="count-badge exist">{{ enrichedShares.length }}</span>
        </div>

        <div v-if="enrichedShares.length > 0" class="batch-bar">
          <label class="select-all-label">
            <input type="checkbox" :checked="isAllExistChecked" :indeterminate="isExistIndeterminate"
              @change="toggleExistAll" />
            <span>全选</span>
          </label>
          <span v-if="existChecked.size > 0">已选 {{ existChecked.size }} 人</span>
          <template v-if="existChecked.size > 0">
            <el-dropdown trigger="click" @command="batchCmd">
              <el-button class="batch-btn" size="small">批量设置</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="o in permOpts" :key="o.v" :command="o.v">设为{{ o.l }}</el-dropdown-item>
                  <el-dropdown-item divided command="_revoke" style="color: var(--el-color-danger)">批量撤回</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button text size="small" @click="existChecked = new Set()">取消</el-button>
          </template>
        </div>

        <div class="exist-list">
          <div v-for="s in enrichedShares" :key="s.id" class="exist-row" :class="{ checked: existChecked.has(s.id) }">
            <input type="checkbox" :checked="existChecked.has(s.id)" @change="toggleExist(s.id)" />
            <div class="e-info">
              <span class="e-name">{{ s.userName }}</span>
              <span class="e-id">{{ s.employeeId }}</span>
            </div>
            <el-dropdown trigger="click" @command="onExistCmd(s, $event)">
              <el-tag size="small" :type="permColor(s.permission)" effect="plain" class="perm-tag">
                {{ permText(s.permission) }}
              </el-tag>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="o in permOpts" :key="o.v" :command="o.v">{{ o.l }}</el-dropdown-item>
                  <el-dropdown-item divided command="_revoke" style="color: var(--el-color-danger)">撤回</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-if="enrichedShares.length === 0" class="no-data">暂无共享</div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type UserItem } from '@/api/user'
import { shareApi, type ShareItem } from '@/api/share'
import { departmentApi, type Department } from '@/api/department'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ docId?: number; docIds?: number[] }>()

const isBatch = computed(() => !!props.docIds && props.docIds.length > 0)
const myId = ref(0)

const searchKeyword = ref('')
const allUsers = ref<UserItem[]>([])
const departments = ref<Department[]>([])
const selectedDeptId = ref<number | null>(null)
const checkedIds = ref(new Set<number>())
const sharedSet = ref(new Set<number>())
const newPerm = ref('view')
const existingShares = ref<(ShareItem & { permission: string })[]>([])
const existChecked = ref(new Set<number>())
const deptExpanded = ref<number[]>([])

const permOpts = [
  { l: '查看', v: 'view' },
  { l: '评论', v: 'comment' },
  { l: '编辑', v: 'edit' },
  { l: '管理', v: 'admin' }
]

function permText(p: string) { return permOpts.find(o => o.v === p)?.l || p }
function permColor(p: string): any { return { view: 'info', comment: '', edit: 'warning', admin: 'danger' }[p] || '' }
function isSelf(id: number) { return id === myId.value }
function isShared(id: number) { return sharedSet.value.has(id) }

// 机构树（只含部门，不含用户）
const deptTreeData = computed(() => {
  const map = new Map<number, Department & { children: Department[] }>()
  const roots: (Department & { children: Department[] })[] = []
  departments.value.forEach(d => map.set(d.id, { ...d, children: [] }))
  departments.value.forEach(d => {
    const node = map.get(d.id)!
    if (d.parentId && map.has(d.parentId)) map.get(d.parentId)!.children.push(node)
    else roots.push(node)
  })
  return roots
})

// 默认展开顶级
watch(deptTreeData, (data) => {
  deptExpanded.value = data.map(d => d.id)
}, { immediate: true })

// 点击部门
function onDeptClick(data: Department) {
  selectedDeptId.value = data.id
}

// 当前部门名称
const currentDeptName = computed(() => {
  if (!selectedDeptId.value) return ''
  return departments.value.find(d => d.id === selectedDeptId.value)?.name || ''
})

// 当前部门用户（含子部门用户递归收集）
const currentDeptUsers = computed(() => {
  if (!selectedDeptId.value) return []

  // 收集该部门及所有子部门的 ID
  const deptIds = new Set<number>()
  function collectDeptIds(id: number) {
    deptIds.add(id)
    departments.value.filter(d => d.parentId === id).forEach(d => collectDeptIds(d.id))
  }
  collectDeptIds(selectedDeptId.value)

  return allUsers.value
    .filter(u => u.departmentId && deptIds.has(u.departmentId) && !isSelf(u.id))
    .sort((a, b) => (a.realName || '').localeCompare(b.realName || ''))
})

// 当前部门全选状态
const isAllDeptChecked = computed(() => {
  const selectable = currentDeptUsers.value.filter(u => !isShared(u.id))
  return selectable.length > 0 && selectable.every(u => checkedIds.value.has(u.id))
})

const isDeptIndeterminate = computed(() => {
  const selectable = currentDeptUsers.value.filter(u => !isShared(u.id))
  const cnt = selectable.filter(u => checkedIds.value.has(u.id)).length
  return cnt > 0 && cnt < selectable.length
})

function toggleDeptAll() {
  const selectable = currentDeptUsers.value.filter(u => !isShared(u.id))
  const s = new Set(checkedIds.value)
  if (isAllDeptChecked.value) {
    selectable.forEach(u => s.delete(u.id))
  } else {
    selectable.forEach(u => s.add(u.id))
  }
  checkedIds.value = s
}

// 搜索（全局）
const filteredUsers = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return []
  return allUsers.value.filter(u =>
    !isSelf(u.id) && (
      (u.realName || '').toLowerCase().includes(kw) ||
      (u.employeeId || '').toLowerCase().includes(kw)
    )
  )
})

function toggleUser(id: number) {
  if (isSelf(id) || isShared(id)) return
  const s = new Set(checkedIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  checkedIds.value = s
}

function removePicked(id: number) {
  const s = new Set(checkedIds.value)
  s.delete(id)
  checkedIds.value = s
}

function clearAll() { checkedIds.value = new Set() }

// 已选用户
const pickedUsers = computed(() => allUsers.value.filter(u => checkedIds.value.has(u.id)))

// 已选部门信息（按用户直属部门分组，显示最具体的部门）
const pickedDeptInfos = computed(() => {
  const deptMap = new Map(departments.value.map(d => [d.id, d]))

  // 按用户直属部门分组统计
  const deptStats = new Map<number, { selected: Set<number>; total: Set<number> }>()
  allUsers.value.filter(u => !isSelf(u.id)).forEach(u => {
    const deptId = u.departmentId || 0
    if (!deptStats.has(deptId)) deptStats.set(deptId, { selected: new Set(), total: new Set() })
    deptStats.get(deptId)!.total.add(u.id)
    if (checkedIds.value.has(u.id)) deptStats.get(deptId)!.selected.add(u.id)
  })

  const result: { name: string; selected: number; total: number }[] = []
  deptStats.forEach((stat, deptId) => {
    if (stat.selected.size === 0) return
    // 如果该部门全部选中，父部门也全选时跳过（由父部门显示）
    if (stat.selected.size === stat.total.size && deptId !== 0) {
      const parent = deptMap.get(deptId)?.parentId
      if (parent) {
        const parentStat = deptStats.get(parent)
        if (parentStat && parentStat.selected.size === parentStat.total.size) {
          // 父部门也全部选中，跳过（会在父部门中显示）
          return
        }
      }
    }
    const dept = deptMap.get(deptId)
    result.push({
      name: dept?.name || '未分配',
      selected: stat.selected.size,
      total: stat.total.size
    })
  })

  return result
})

// 已共享
const enrichedShares = computed(() => {
  const userMap = new Map(allUsers.value.map(u => [u.id, u]))
  return existingShares.value.map(s => {
    const u = userMap.get(s.userId)
    return { ...s, userName: u?.realName || s.userName || '未知', employeeId: u?.employeeId || s.employeeId || '' }
  })
})

function toggleExist(id: number) {
  const s = new Set(existChecked.value)
  s.has(id) ? s.delete(id) : s.add(id)
  existChecked.value = s
}

const isAllExistChecked = computed(() =>
  enrichedShares.value.length > 0 && enrichedShares.value.every(s => existChecked.value.has(s.id))
)

const isExistIndeterminate = computed(() => {
  const cnt = enrichedShares.value.filter(s => existChecked.value.has(s.id)).length
  return cnt > 0 && cnt < enrichedShares.value.length
})

function toggleExistAll() {
  if (isAllExistChecked.value) {
    existChecked.value = new Set()
  } else {
    existChecked.value = new Set(enrichedShares.value.map(s => s.id))
  }
}

async function onExistCmd(share: ShareItem & { permission: string }, cmd: string) {
  if (cmd === '_revoke') {
    try {
      await ElMessageBox.confirm('确定撤回共享？', '提示', { type: 'warning' })
      await shareApi.removeShare(share.id)
      ElMessage.success('已撤回')
      loadExisting()
    } catch {}
    return
  }
  try {
    await shareApi.updatePermission(share.id, cmd)
    share.permission = cmd as any
    ElMessage.success('权限已更新')
  } catch { ElMessage.error('更新失败') }
}

async function batchCmd(cmd: string) {
  const ids = Array.from(existChecked.value)
  if (cmd === '_revoke') {
    try {
      await ElMessageBox.confirm(`确定撤回 ${ids.length} 人的共享？`, '提示', { type: 'warning' })
      let ok = 0
      for (const id of ids) { try { await shareApi.removeShare(id); ok++ } catch {} }
      ElMessage.success(`已撤回 ${ok} 人`)
      existChecked.value = new Set()
      loadExisting()
    } catch {}
    return
  }
  let ok = 0
  for (const id of ids) { try { await shareApi.updatePermission(id, cmd); ok++ } catch {} }
  ElMessage.success(`已更新 ${ok} 人权限`)
  existChecked.value = new Set()
  loadExisting()
}

async function doShare() {
  const docIds = isBatch.value ? props.docIds! : [props.docId!]
  const userIds = Array.from(checkedIds.value)
  let ok = 0
  for (const docId of docIds) {
    for (const uid of userIds) {
      try { await shareApi.shareDocument({ documentId: docId, userId: uid, permission: newPerm.value }); ok++ } catch {}
    }
  }
  if (ok > 0) {
    ElMessage.success(`成功共享 ${ok} 次`)
    checkedIds.value = new Set()
    searchKeyword.value = ''
    allUsers.value = await userApi.getAll()
    if (!isBatch.value) loadExisting()
  } else {
    ElMessage.error('共享失败')
  }
}

onMounted(async () => {
  myId.value = Number(sessionStorage.getItem('userId')) || 0
  try { departments.value = await departmentApi.getAll() } catch {}
})

watch(visible, async (val) => {
  if (!val) return
  myId.value = Number(sessionStorage.getItem('userId')) || 0
  searchKeyword.value = ''
  checkedIds.value = new Set()
  sharedSet.value = new Set()
  existChecked.value = new Set()
  newPerm.value = 'view'
  selectedDeptId.value = null
  try { allUsers.value = await userApi.getAll() } catch {}
  if (props.docId && !isBatch.value) loadExisting()
  else existingShares.value = []
})

async function loadExisting() {
  if (!props.docId) return
  try {
    const shares = await shareApi.getDocumentShares(props.docId)
    existingShares.value = shares.map(s => ({ ...s, permission: s.permission || 'view' }))
    sharedSet.value = new Set(shares.map(s => s.userId))
  } catch {}
}
</script>

<style scoped>
.share-root {
  display: flex;
  height: 520px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
}

/* 公共列头 */
.col-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
  white-space: nowrap;
  flex-shrink: 0;
}

.head-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

.count-badge {
  font-size: 11px; font-weight: 500;
  background: var(--el-color-primary); color: #fff;
  padding: 0 6px; border-radius: 10px; line-height: 18px;
}

.count-badge.exist { background: var(--el-color-success); }

.clear-btn {
  margin-left: auto;
  font-size: 12px; font-weight: 400;
  color: var(--el-text-color-placeholder);
  cursor: pointer;
}

.clear-btn:hover { color: var(--el-color-primary); }

/* 左1：机构树 */
.col-dept {
  width: 180px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.dept-tree-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

:deep(.el-tree-node__content) { height: 32px; }

/* 左2：部门用户 */
.col-users {
  width: 220px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.users-search {
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.users-body {
  flex: 1;
  overflow-y: auto;
  padding: 2px 0;
}

.dept-check-all {
  border-bottom: 1px solid var(--el-border-color-extra-light);
  margin-bottom: 2px;
}

.check-all-row { background: var(--el-fill-color-lighter); }

.user-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  cursor: pointer;
}

.user-row:hover { background: var(--el-fill-color-light); }
.user-row.disabled { opacity: 0.3; cursor: not-allowed; }
.user-row.shared { opacity: 0.45; }
.user-row input[type="checkbox"] { margin: 0; cursor: pointer; }
.u-name { font-size: 13px; color: var(--el-text-color-primary); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.u-id { font-size: 11px; color: var(--el-text-color-placeholder); flex-shrink: 0; }

.tag-self {
  font-size: 11px; color: var(--el-text-color-placeholder);
  background: var(--el-fill-color); padding: 0 5px;
  border-radius: var(--el-border-radius-small); flex-shrink: 0;
}

.tag-shared {
  font-size: 11px; color: var(--el-color-success);
  background: var(--el-color-success-light-9); padding: 0 5px;
  border-radius: var(--el-border-radius-small); flex-shrink: 0;
}

/* 右1：待共享 */
.col-pending {
  width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.dept-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.picked-list {
  flex: 1;
  overflow-y: auto;
  padding: 2px 0;
  min-height: 40px;
}

.picked-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
}

.picked-row:hover { background: var(--el-color-primary-light-9); }
.p-name { font-size: 13px; color: var(--el-text-color-primary); }
.p-id { font-size: 11px; color: var(--el-text-color-placeholder); }
.p-del {
  margin-left: auto;
  color: var(--el-text-color-placeholder); cursor: pointer;
  font-size: 15px; line-height: 1;
}
.p-del:hover { color: var(--el-color-danger); }

.action-bar {
  padding: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.perm-row { display: flex; align-items: center; gap: 8px; }
.perm-lbl { font-size: 12px; color: var(--el-text-color-regular); white-space: nowrap; }

/* 右2：已共享 */
.col-existing {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-blank);
  flex-shrink: 0;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  background: var(--el-color-primary-light-9);
  font-size: 12px;
  color: var(--el-color-primary);
}

.select-all-label {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  white-space: nowrap;
}

.select-all-label input[type="checkbox"] { margin: 0; cursor: pointer; }

.batch-btn {
  background: var(--el-color-primary) !important;
  border-color: var(--el-color-primary) !important;
  color: #fff !important;
}

.batch-btn:hover {
  background: var(--el-color-primary-light-3) !important;
  border-color: var(--el-color-primary-light-3) !important;
}

.exist-list {
  flex: 1;
  overflow-y: auto;
  padding: 2px 0;
}

.exist-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
}

.exist-row:hover { background: var(--el-color-primary-light-9); }
.exist-row.checked { background: var(--el-color-primary-light-8); }
.exist-row input[type="checkbox"] { margin: 0; cursor: pointer; flex-shrink: 0; }

.e-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.e-name { font-size: 13px; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.e-id { font-size: 11px; color: var(--el-text-color-placeholder); }

.perm-tag { cursor: pointer; flex-shrink: 0; }

.no-data {
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  padding: 24px 8px;
}
</style>
