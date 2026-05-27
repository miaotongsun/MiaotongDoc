<template>
  <div class="admin-page">
    <div class="admin-body">
      <el-tabs v-model="activeTab" class="admin-tabs">

        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div class="tab-header">
            <el-input v-model="userSearch" placeholder="搜索工号、姓名、用户名..." style="width: 280px" clearable @clear="loadUsers" @keyup.enter="loadUsers" />
            <div style="flex:1"></div>
            <el-button type="primary" @click="openUserDialog()">
              <el-icon><Plus /></el-icon> 新增用户
            </el-button>
          </div>
          <el-table :data="users" stripe>
            <el-table-column prop="employeeId" label="工号" show-overflow-tooltip />
            <el-table-column prop="realName" label="姓名" show-overflow-tooltip />
            <el-table-column prop="username" label="用户名" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
            <el-table-column label="部门" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getDeptName(row.departmentId) }}
              </template>
            </el-table-column>
            <el-table-column label="角色" align="center">
              <template #default="{ row }">
                <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role === 'admin' ? '管理员' : '普通用户' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'danger'" size="small" effect="plain">{{ row.isActive ? '正常' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text @click="openUserDialog(row)">编辑</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text @click="handleResetPwd(row)">重置密码</el-button>
                  <el-divider direction="vertical" />
                  <el-dropdown trigger="click">
                    <el-button size="small" text type="primary">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="toggleRole(row)">
                          {{ row.role === 'admin' ? '设为普通用户' : '设为管理员' }}
                        </el-dropdown-item>
                        <el-dropdown-item :divided="true" @click="toggleStatus(row)">
                          <span :style="{ color: row.isActive ? 'var(--el-color-danger)' : 'var(--el-color-success)' }">
                            {{ row.isActive ? '禁用账号' : '启用账号' }}
                          </span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 部门管理 -->
        <el-tab-pane label="部门管理" name="departments">
          <div class="tab-header">
            <div style="flex:1"></div>
            <el-button type="primary" @click="openDeptDialog()">
              <el-icon><Plus /></el-icon> 新增部门
            </el-button>
          </div>
          <el-table :data="deptTree" stripe row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
            <el-table-column prop="code" label="部门编码" show-overflow-tooltip />
            <el-table-column prop="name" label="部门名称" show-overflow-tooltip />
            <el-table-column prop="level" label="层级" align="center" />
            <el-table-column prop="sortOrder" label="排序" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'danger'" size="small" effect="plain">{{ row.isActive ? '正常' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text @click="openDeptDialog(row)">编辑</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text type="primary" @click="openDeptDialog(null, row.id)">新增子部门</el-button>
                  <el-divider direction="vertical" />
                  <el-button size="small" text type="danger" @click="handleDeactivateDept(row)">停用</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 审计日志 -->
        <el-tab-pane label="审计日志" name="audit">
          <div class="tab-header">
            <el-date-picker v-model="auditDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
            <el-button type="primary" @click="handleAuditSearch">
              <el-icon><Search /></el-icon> 查询
            </el-button>
            <el-button @click="handleAuditReset">重置</el-button>
          </div>
          <el-table :data="auditLogs" stripe>
            <el-table-column label="时间" show-overflow-tooltip>
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="userName" label="操作用户" show-overflow-tooltip />
            <el-table-column label="操作类型">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ actionLabels[row.action] || row.action }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="resourceType" label="资源类型" show-overflow-tooltip />
            <el-table-column prop="ipAddress" label="IP 地址" show-overflow-tooltip />
          </el-table>
          <div class="pagination-bar" v-if="auditTotal > 0">
            <el-pagination background :total="auditTotal" :page-size="20"
              :current-page="auditPage + 1" @current-change="handleAuditPageChange"
              layout="total, prev, pager, next" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 用户弹窗 -->
    <el-dialog v-model="showUserDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form :model="userForm" label-width="90px" class="dialog-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" required>
              <el-input v-model="userForm.employeeId" :disabled="!!editingUser" maxlength="8" placeholder="如 10001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名" required>
              <el-input v-model="userForm.username" :disabled="!!editingUser" placeholder="登录账号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item v-if="!editingUser" label="初始密码" required>
              <el-input v-model="userForm.password" type="password" show-password placeholder="默认 123456" />
            </el-form-item>
            <el-form-item v-else label="姓名" required>
              <el-input v-model="userForm.realName" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!editingUser" label="姓名" required>
              <el-input v-model="userForm.realName" placeholder="真实姓名" />
            </el-form-item>
            <el-form-item v-else label="邮箱">
              <el-input v-model="userForm.email" placeholder="name@company.com" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-if="!editingUser">
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="userForm.email" placeholder="name@company.com" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="userForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16" v-else>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="userForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职位">
              <el-input v-model="userForm.position" placeholder="岗位/职位" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="部门">
          <el-select v-model="userForm.departmentId" placeholder="选择所属部门" clearable style="width:100%">
            <el-option v-for="d in allDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingUser" label="职位">
          <el-input v-model="userForm.position" placeholder="岗位/职位" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="角色">
          <el-select v-model="userForm.role" style="width:100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 部门弹窗 -->
    <el-dialog v-model="showDeptDialog" :title="editingDept ? '编辑部门' : '新增部门'" width="460px" destroy-on-close>
      <el-form :model="deptForm" label-width="90px" class="dialog-form">
        <el-form-item label="部门编码" required>
          <el-input v-model="deptForm.code" :disabled="!!editingDept" maxlength="20" placeholder="如 HR、FIN、IT" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="deptForm.name" placeholder="部门全称" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" placeholder="无（顶级部门）" clearable style="width:100%">
            <el-option v-for="d in allDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="deptForm.sortOrder" :min="0" :max="999" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeptDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDept" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '@/api/index'
import { userApi, type UserItem } from '@/api/user'
import { departmentApi, type Department } from '@/api/department'
import { auditApi, type AuditLogItem } from '@/api/audit'

const activeTab = ref('users')
const saving = ref(false)

// --- 用户管理 ---
const userSearch = ref('')
const users = ref<UserItem[]>([])
const showUserDialog = ref(false)
const editingUser = ref<UserItem | null>(null)
const userForm = ref<any>({})

// --- 部门管理 ---
const allDepts = ref<Department[]>([])
const showDeptDialog = ref(false)
const editingDept = ref<Department | null>(null)
const deptForm = ref<any>({})
const allDepartmentsFlat = ref<Department[]>([])

// --- 审计日志 ---
const auditLogs = ref<AuditLogItem[]>([])
const auditTotal = ref(0)
const auditPage = ref(0)
const auditDateRange = ref<string[] | null>(null)

const actionLabels: Record<string, string> = {
  CREATE: '创建文档', UPLOAD: '上传文档', RENAME: '重命名', DELETE: '删除',
  RESTORE: '恢复', RESTORE_VERSION: '恢复版本', SHARE: '共享',
  SIGN_INIT: '发起签署', SIGN_CANCEL: '取消签署'
}

// 部门树形结构
const deptTree = computed(() => {
  const map = new Map<number, any>()
  const roots: any[] = []
  for (const d of allDepartmentsFlat.value) {
    map.set(d.id, { ...d, children: [] })
  }
  for (const d of allDepartmentsFlat.value) {
    const node = map.get(d.id)!
    if (d.parentId && map.has(d.parentId)) {
      map.get(d.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

function getDeptName(id?: number) {
  if (!id) return '-'
  const d = allDepartmentsFlat.value.find(d => d.id === id)
  return d ? d.name : '-'
}

onMounted(() => {
  loadUsers()
  loadDepartments()
  loadAuditLogs()
})

async function loadUsers() {
  try {
    if (userSearch.value) {
      users.value = await api.get<any, any[]>('/admin/users/search', { params: { keyword: userSearch.value } })
    } else {
      const res = await api.get<any, any>('/admin/users', { params: { page: 0, size: 100 } })
      users.value = res.content || res
    }
  } catch {
    users.value = []
  }
}

async function loadDepartments() {
  try {
    allDepartmentsFlat.value = await departmentApi.getAll()
    allDepts.value = allDepartmentsFlat.value
  } catch {}
}

function openUserDialog(user?: UserItem) {
  if (user) {
    editingUser.value = user
    userForm.value = {
      employeeId: user.employeeId,
      username: user.username,
      realName: user.realName,
      email: user.email || '',
      phone: (user as any).phone || '',
      departmentId: user.departmentId || undefined,
      position: user.position || ''
    }
  } else {
    editingUser.value = null
    userForm.value = { employeeId: '', username: '', password: '123456', realName: '', email: '', phone: '', departmentId: undefined, position: '', role: 'user' }
  }
  showUserDialog.value = true
}

async function saveUser() {
  const f = userForm.value
  if (!f.employeeId || !f.username || !f.realName) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingUser.value) {
      await userApi.update(editingUser.value.id, {
        realName: f.realName, email: f.email, phone: f.phone,
        departmentId: f.departmentId, position: f.position
      })
      ElMessage.success('用户更新成功')
    } else {
      if (!f.password) f.password = '123456'
      await userApi.create(f)
      ElMessage.success('用户创建成功')
    }
    showUserDialog.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleResetPwd(user: UserItem) {
  try {
    await ElMessageBox.confirm(`确定要重置 ${user.realName} 的密码为 123456 吗？`, '重置密码', { type: 'warning' })
    await userApi.resetPassword(user.id)
    ElMessage.success('密码已重置为 123456')
  } catch {}
}

async function toggleRole(user: UserItem) {
  const newRole = user.role === 'admin' ? 'user' : 'admin'
  try {
    await userApi.updateRole(user.id, newRole)
    user.role = newRole
    ElMessage.success('角色已更新')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function toggleStatus(user: UserItem) {
  try {
    await userApi.updateStatus(user.id)
    user.isActive = !user.isActive
    ElMessage.success(user.isActive ? '已启用' : '已禁用')
  } catch {
    ElMessage.error('操作失败')
  }
}

// --- 部门管理 ---
function openDeptDialog(dept?: Department | null, parentId?: number) {
  if (dept) {
    editingDept.value = dept
    deptForm.value = { code: dept.code, name: dept.name, parentId: dept.parentId || undefined, sortOrder: dept.sortOrder || 0 }
  } else {
    editingDept.value = null
    deptForm.value = { code: '', name: '', parentId: parentId || undefined, sortOrder: 0 }
  }
  showDeptDialog.value = true
}

async function saveDept() {
  const f = deptForm.value
  if (!f.code || !f.name) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingDept.value) {
      await departmentApi.update(editingDept.value.id, { name: f.name, code: f.code, sortOrder: f.sortOrder })
      ElMessage.success('部门更新成功')
    } else {
      await departmentApi.create(f)
      ElMessage.success('部门创建成功')
    }
    showDeptDialog.value = false
    loadDepartments()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDeactivateDept(dept: Department) {
  try {
    await ElMessageBox.confirm(`确定要停用部门「${dept.name}」吗？`, '停用部门', { type: 'warning' })
    await departmentApi.deactivate(dept.id)
    ElMessage.success('部门已停用')
    loadDepartments()
  } catch {}
}

// --- 审计日志 ---
async function loadAuditLogs() {
  try {
    const params: any = { page: auditPage.value, size: 20 }
    if (auditDateRange.value && auditDateRange.value.length === 2) {
      const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      params.startDate = fmt(new Date(auditDateRange.value[0]))
      params.endDate = fmt(new Date(auditDateRange.value[1]))
    }
    const res = await auditApi.getMyLogs(params)
    auditLogs.value = res.content || []
    auditTotal.value = res.totalElements || 0
  } catch {}
}

function handleAuditSearch() {
  auditPage.value = 0
  loadAuditLogs()
}

function handleAuditReset() {
  auditDateRange.value = null
  auditPage.value = 0
  loadAuditLogs()
}

function handleAuditPageChange(page: number) {
  auditPage.value = page - 1
  loadAuditLogs()
}

function formatTime(str: string) {
  if (!str) return ''
  return new Date(str).toLocaleString('zh-CN')
}
</script>

<style scoped>
.admin-page {
  width: 100%;
}

.admin-body {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 24px 28px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.admin-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  padding: 0 24px;
  height: 44px;
  line-height: 44px;
}

.tab-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  overflow: visible;
}

.tab-header :deep(.el-button--primary):hover {
  transform: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.admin-tabs :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.admin-tabs :deep(.el-table th.el-table__cell) {
  background: #f7f8fa;
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

.admin-tabs :deep(.el-table td.el-table__cell) {
  padding: 12px 0;
  font-size: 13px;
  color: #4a4a4a;
}

.admin-tabs :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #fafbfc;
}

.admin-tabs :deep(.el-table .el-table__row:hover > td.el-table__cell) {
  background: #f0f5ff;
}

.action-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.action-btns .el-button {
  padding: 4px 6px;
  font-size: 13px;
}

.action-btns .el-divider--vertical {
  margin: 0 4px;
  border-left-color: #dcdfe6;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-form {
  padding: 0 8px;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.dialog-form :deep(.el-input__wrapper),
.dialog-form :deep(.el-textarea__inner) {
  width: 100%;
}
</style>
