<template>
  <el-dialog v-model="visible" title="提交合同审批" width="560px">
    <el-form label-width="80px">
      <el-form-item label="审批人">
        <el-select v-model="selectedUsers" multiple filterable remote reserve-keyword
          :remote-method="searchUsers" placeholder="搜索并选择审批人..." style="width: 100%"
          :loading="userLoading" value-key="id">
          <el-option v-for="user in userOptions" :key="user.id"
            :label="`${user.realName} (${user.employeeId})`" :value="user" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="selectedUsers.length > 1" label="审批顺序">
        <div class="order-hint">点击上下箭头调整审批顺序（从上到下依次审批）</div>
        <div class="approver-list">
          <div v-for="(user, index) in selectedUsers" :key="user.id" class="approver-item">
            <span class="step-num">{{ index + 1 }}</span>
            <span class="approver-name">{{ user.realName }}</span>
            <div class="order-btns">
              <el-button text size="small" :disabled="index === 0" @click="moveUp(index)">
                <el-icon><Top /></el-icon>
              </el-button>
              <el-button text size="small" :disabled="index === selectedUsers.length - 1" @click="moveDown(index)">
                <el-icon><Bottom /></el-icon>
              </el-button>
              <el-button text size="small" type="danger" @click="removeUser(index)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-form-item>

      <el-form-item v-if="selectedUsers.length > 0" label="审批链预览">
        <div class="chain-preview">
          <span v-for="(user, index) in selectedUsers" :key="user.id">
            <el-tag size="small" :type="index === 0 ? 'warning' : 'info'">{{ user.realName }}</el-tag>
            <span v-if="index < selectedUsers.length - 1" class="chain-arrow"> → </span>
          </span>
        </div>
      </el-form-item>

      <el-form-item label="截止日期">
        <el-date-picker v-model="deadline" type="date" value-format="YYYY-MM-DD"
          placeholder="选择截止日期（可选）" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="selectedUsers.length === 0" :loading="submitting" @click="handleSubmit">
        提交审批
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, type UserItem } from '@/api/user'
import { contractApi } from '@/api/contract'

interface SelectUser {
  id: number
  realName: string
  employeeId: string
}

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ contractId: number }>()
const emit = defineEmits(['submitted'])

const selectedUsers = ref<SelectUser[]>([])
const userOptions = ref<UserItem[]>([])
const userLoading = ref(false)
const deadline = ref('')
const submitting = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(visible, (val) => {
  if (val) {
    selectedUsers.value = []
    userOptions.value = []
    deadline.value = ''
  }
})

function searchUsers(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  if (!keyword.trim()) {
    userOptions.value = []
    return
  }
  userLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      userOptions.value = await userApi.search(keyword.trim())
    } catch {} finally {
      userLoading.value = false
    }
  }, 300)
}

function moveUp(index: number) {
  if (index <= 0) return
  const arr = [...selectedUsers.value]
  const temp = arr[index]
  arr[index] = arr[index - 1]
  arr[index - 1] = temp
  selectedUsers.value = arr
}

function moveDown(index: number) {
  if (index >= selectedUsers.value.length - 1) return
  const arr = [...selectedUsers.value]
  const temp = arr[index]
  arr[index] = arr[index + 1]
  arr[index + 1] = temp
  selectedUsers.value = arr
}

function removeUser(index: number) {
  selectedUsers.value = selectedUsers.value.filter((_, i) => i !== index)
}

async function handleSubmit() {
  submitting.value = true
  try {
    await contractApi.submit(props.contractId, {
      approverIds: selectedUsers.value.map(u => u.id),
      deadline: deadline.value || undefined
    })
    ElMessage.success('已提交审批')
    visible.value = false
    emit('submitted')
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.order-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.approver-list {
  width: 100%;
}

.approver-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 6px;
  background: #fafafa;
}

.step-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.approver-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.order-btns {
  display: flex;
  gap: 0;
}

.chain-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.chain-arrow {
  color: #c0c4cc;
  margin: 0 2px;
}
</style>
