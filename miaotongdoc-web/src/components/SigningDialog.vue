<template>
  <el-dialog v-model="visible" title="提交签署" width="500px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="签署标题">
        <el-input v-model="form.title" placeholder="请输入签署任务标题" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="签署说明（可选）" />
      </el-form-item>
      <el-form-item label="截止时间">
        <el-date-picker v-model="form.deadline" type="datetime" placeholder="选择截止时间（可选）" />
      </el-form-item>
      <el-form-item label="签署人">
        <div class="signer-select">
          <el-input v-model="searchKeyword" placeholder="搜索用户..." :prefix-icon="Search" />
          <div class="selected-signers">
            <el-tag v-for="signer in selectedSigners" :key="signer.id" closable
              @remove="removeSigner(signer.id)">
              {{ signer.realName }} ({{ signer.employeeId }})
            </el-tag>
          </div>
          <div class="user-results" v-if="searchResults.length > 0">
            <div v-for="user in searchResults" :key="user.id" class="user-result-item"
              @click="addSigner(user)">
              <span>{{ user.realName }} ({{ user.employeeId }})</span>
            </div>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit" :disabled="!form.title || selectedSigners.length === 0">
        提交签署
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { signingApi } from '@/api/signing'
import { userApi, type UserItem } from '@/api/user'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ docId: number }>()
const emit = defineEmits(['submitted'])

const form = ref({
  title: '',
  description: '',
  deadline: '' as string
})

const searchKeyword = ref('')
const searchResults = ref<UserItem[]>([])
const selectedSigners = ref<UserItem[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(searchKeyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!val.trim()) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      searchResults.value = await userApi.search(val.trim())
    } catch {
      // ignore
    }
  }, 300)
})

function addSigner(user: UserItem) {
  if (!selectedSigners.value.find(s => s.id === user.id)) {
    selectedSigners.value.push(user)
  }
  searchKeyword.value = ''
  searchResults.value = []
}

function removeSigner(userId: number) {
  selectedSigners.value = selectedSigners.value.filter(s => s.id !== userId)
}

async function submit() {
  try {
    await signingApi.create({
      documentId: props.docId,
      signerUserIds: selectedSigners.value.map(s => s.id),
      dueDate: form.value.deadline || undefined,
      message: form.value.description || undefined
    })
    ElMessage.success('签署任务已创建')
    visible.value = false
    emit('submitted')
    form.value = { title: '', description: '', deadline: '' }
    selectedSigners.value = []
  } catch {
    ElMessage.error('创建签署任务失败')
  }
}
</script>

<style scoped>
.signer-select {
  width: 100%;
}

.selected-signers {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.user-results {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 200px;
  overflow-y: auto;
  margin-top: 4px;
}

.user-result-item {
  padding: 8px 12px;
  cursor: pointer;
}

.user-result-item:hover {
  background: #f5f7fa;
}
</style>
