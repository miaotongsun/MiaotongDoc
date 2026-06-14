<template>
  <el-dialog v-model="visible" title="提交签署" width="860px" top="5vh">
    <div class="signing-root">
      <!-- 表单区 -->
      <div class="form-section">
        <el-form :model="form" label-width="80px" size="small">
          <el-form-item label="签署标题" required>
            <el-input v-model="form.title" placeholder="请输入签署任务标题" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="签署说明（可选）" />
          </el-form-item>
          <el-form-item label="截止时间">
            <el-date-picker v-model="form.deadline" type="datetime" placeholder="选择截止时间（可选）" style="width: 100%" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 选人区 -->
      <div class="picker-root">
        <!-- 左：机构树 -->
        <div class="col-dept">
          <div class="col-head">机构</div>
          <div class="dept-tree-wrap">
            <el-tree :data="deptTreeData" :props="{ label: 'name', children: 'children' }"
              node-key="id" highlight-current :default-expanded-keys="deptExpanded"
              @node-click="onDeptClick" />
          </div>
        </div>

        <!-- 中：部门用户 -->
        <div class="col-users">
          <div class="col-head">
            <span>{{ currentDeptName || '选择机构' }}</span>
            <span v-if="currentDeptUsers.length > 0" class="head-sub">{{ currentDeptUsers.length }} 人</span>
          </div>
          <div class="users-search">
            <el-input v-model="searchKeyword" placeholder="搜索姓名或工号..." :prefix-icon="Search" clearable size="small" />
          </div>
          <div class="users-body">
            <template v-if="searchKeyword">
              <label v-for="u in filteredUsers" :key="u.id" class="user-row"
                :class="{ disabled: isSelf(u.id) }">
                <input type="checkbox" :checked="checkedIds.has(u.id)" :disabled="isSelf(u.id)"
                  @change="toggleUser(u.id)" />
                <span class="u-name">{{ u.realName }}</span>
                <span class="u-id">{{ u.employeeId }}</span>
                <span v-if="isSelf(u.id)" class="tag-self">自己</span>
              </label>
              <div v-if="filteredUsers.length === 0" class="no-data">无匹配</div>
            </template>
            <template v-else>
              <div v-if="currentDeptUsers.length > 0" class="dept-check-all">
                <label class="user-row check-all-row">
                  <input type="checkbox" :checked="isAllDeptChecked" :indeterminate="isDeptIndeterminate"
                    @change="toggleDeptAll" />
                  <span class="u-name" style="font-weight: 600">全选</span>
                </label>
              </div>
              <label v-for="u in currentDeptUsers" :key="u.id" class="user-row"
                :class="{ disabled: isSelf(u.id) }">
                <input type="checkbox" :checked="checkedIds.has(u.id)" :disabled="isSelf(u.id)"
                  @change="toggleUser(u.id)" />
                <span class="u-name">{{ u.realName }}</span>
                <span class="u-id">{{ u.employeeId }}</span>
                <span v-if="isSelf(u.id)" class="tag-self">自己</span>
              </label>
              <div v-if="!selectedDeptId" class="no-data">点击左侧机构查看成员</div>
              <div v-else-if="currentDeptUsers.length === 0" class="no-data">该机构暂无成员</div>
            </template>
          </div>
        </div>

        <!-- 右：已选签署人 -->
        <div class="col-pending">
          <div class="col-head">
            <span>已选签署人</span>
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
            <div v-if="pickedUsers.length === 0" class="no-data">从左侧勾选签署人</div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit" :disabled="!form.title || pickedUsers.length === 0">
        提交给 {{ pickedUsers.length }} 人签署
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { signingApi } from '@/api/signing'
import { userApi, type UserItem } from '@/api/user'
import { departmentApi, type Department } from '@/api/department'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ docId: number }>()
const emit = defineEmits(['submitted'])

const form = ref({
  title: '',
  description: '',
  deadline: '' as string
})

const allUsers = ref<UserItem[]>([])
const departments = ref<Department[]>([])
const selectedDeptId = ref<number | null>(null)
const checkedIds = ref(new Set<number>())
const deptExpanded = ref<number[]>([])
const searchKeyword = ref('')
const myId = ref(0)

function isSelf(id: number) { return id === myId.value }

// 机构树
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

watch(deptTreeData, (data) => {
  deptExpanded.value = data.map(d => d.id)
}, { immediate: true })

function onDeptClick(data: Department) {
  selectedDeptId.value = data.id
}

const currentDeptName = computed(() => {
  if (!selectedDeptId.value) return ''
  return departments.value.find(d => d.id === selectedDeptId.value)?.name || ''
})

// 当前部门用户（含子部门）
const currentDeptUsers = computed(() => {
  if (!selectedDeptId.value) return []
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

// 全选状态
const isAllDeptChecked = computed(() => {
  const selectable = currentDeptUsers.value.filter(u => !isSelf(u.id))
  return selectable.length > 0 && selectable.every(u => checkedIds.value.has(u.id))
})

const isDeptIndeterminate = computed(() => {
  const selectable = currentDeptUsers.value.filter(u => !isSelf(u.id))
  const cnt = selectable.filter(u => checkedIds.value.has(u.id)).length
  return cnt > 0 && cnt < selectable.length
})

function toggleDeptAll() {
  const selectable = currentDeptUsers.value.filter(u => !isSelf(u.id))
  const s = new Set(checkedIds.value)
  if (isAllDeptChecked.value) {
    selectable.forEach(u => s.delete(u.id))
  } else {
    selectable.forEach(u => s.add(u.id))
  }
  checkedIds.value = s
}

function toggleUser(id: number) {
  if (isSelf(id)) return
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

// 已选部门信息
const pickedDeptInfos = computed(() => {
  const deptMap = new Map(departments.value.map(d => [d.id, d]))
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
    const dept = deptMap.get(deptId)
    result.push({ name: dept?.name || '未分配', selected: stat.selected.size, total: stat.total.size })
  })
  return result
})

async function submit() {
  try {
    await signingApi.create({
      documentId: props.docId,
      title: form.value.title,
      description: form.value.description || undefined,
      signerUserIds: pickedUsers.value.map(s => s.id),
      deadline: form.value.deadline || undefined
    })
    ElMessage.success('签署任务已创建')
    visible.value = false
    emit('submitted')
    form.value = { title: '', description: '', deadline: '' }
    checkedIds.value = new Set()
  } catch {
    ElMessage.error('创建签署任务失败')
  }
}

watch(visible, async (val) => {
  if (!val) return
  myId.value = Number(sessionStorage.getItem('userId')) || 0
  form.value = { title: '', description: '', deadline: '' }
  checkedIds.value = new Set()
  selectedDeptId.value = null
  searchKeyword.value = ''
  try {
    allUsers.value = await userApi.getAll()
    departments.value = await departmentApi.getAll()
  } catch {}
})
</script>

<style scoped>
.signing-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-section {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 12px;
}

.picker-root {
  display: flex;
  height: 380px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
}

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
  font-size: 11px; font-weight: 400;
  color: var(--el-text-color-placeholder);
}

.count-badge {
  font-size: 11px; font-weight: 500;
  background: var(--el-color-primary); color: #fff;
  padding: 0 6px; border-radius: 10px; line-height: 18px;
}

.clear-btn {
  margin-left: auto;
  font-size: 12px; font-weight: 400;
  color: var(--el-text-color-placeholder);
  cursor: pointer;
}
.clear-btn:hover { color: var(--el-color-primary); }

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
.user-row input[type="checkbox"] { margin: 0; cursor: pointer; }
.u-name { font-size: 13px; color: var(--el-text-color-primary); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.u-id { font-size: 11px; color: var(--el-text-color-placeholder); flex-shrink: 0; }

.tag-self {
  font-size: 11px; color: var(--el-text-color-placeholder);
  background: var(--el-fill-color); padding: 0 5px;
  border-radius: var(--el-border-radius-small); flex-shrink: 0;
}

.col-pending {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
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

.no-data {
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  padding: 24px 8px;
}
</style>
