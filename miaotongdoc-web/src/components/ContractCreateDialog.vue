<template>
  <el-dialog v-model="visible" title="创建合同" width="700px" :close-on-click-modal="false">
    <!-- Step 1: Select document -->
    <div v-if="step === 1" class="step-content">
      <div class="step-title">选择合同文档</div>
      <div class="doc-select-area">
        <el-select v-model="selectedDocId" filterable remote reserve-keyword
          :remote-method="searchDocuments" placeholder="搜索文档名称..." style="width: 100%"
          :loading="docLoading" size="large">
          <el-option v-for="doc in docOptions" :key="doc.id"
            :label="`${doc.title} (${doc.docType === 'word' ? 'Word' : doc.docType})`" :value="doc.id">
            <span>{{ doc.title }}</span>
            <el-tag size="small" style="margin-left: 8px">{{ doc.ownerName }}</el-tag>
          </el-option>
        </el-select>
        <div class="upload-alt">
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".docx">
            <el-button text type="primary">或上传新的 Word 文档</el-button>
          </el-upload>
        </div>
      </div>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedDocId" :loading="parsing" @click="parseDoc">
          下一步：识别信息
        </el-button>
      </div>
    </div>

    <!-- Step 2: Review parsed data -->
    <div v-if="step === 2" class="step-content">
      <div class="step-title">
        确认合同信息
        <el-tag v-if="parsedFromDoc" size="small" type="success" style="margin-left: 8px">已自动识别</el-tag>
      </div>
      <el-form :model="form" label-width="100px" class="contract-form">
        <el-form-item label="合同编号">
          <el-input v-model="form.contractNo" placeholder="如 HT-2026-001" />
        </el-form-item>
        <el-form-item label="合同类型">
          <el-select v-model="form.contractType" placeholder="选择类型">
            <el-option label="采购合同" value="purchase" />
            <el-option label="销售合同" value="sale" />
            <el-option label="租赁合同" value="lease" />
            <el-option label="服务合同" value="service" />
            <el-option label="劳动合同" value="labor" />
            <el-option label="工程合同" value="construction" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="甲方">
          <el-input v-model="form.partyA" placeholder="甲方名称" />
        </el-form-item>
        <el-form-item label="乙方">
          <el-input v-model="form.partyB" placeholder="乙方名称" />
        </el-form-item>
        <el-form-item label="合同金额">
          <el-input-number v-model="form.amount" :precision="2" :min="0" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元</span>
        </el-form-item>
        <el-form-item label="签订日期">
          <el-date-picker v-model="form.signingDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remarks" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div class="dialog-footer">
        <el-button @click="step = 1">上一步</el-button>
        <el-button type="primary" :loading="saving" @click="saveContract">确认创建</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { documentApi } from '@/api/document'
import { contractApi } from '@/api/contract'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['created'])

const step = ref(1)
const selectedDocId = ref<number | undefined>(undefined)
const docOptions = ref<any[]>([])
const docLoading = ref(false)
const parsing = ref(false)
const saving = ref(false)
const parsedFromDoc = ref(false)

const form = reactive({
  contractNo: '',
  contractType: '',
  partyA: '',
  partyB: null as string | null,
  amount: null as number | null,
  signingDate: '',
  effectiveDate: '',
  expiryDate: '',
  remarks: ''
})

let searchTimer: ReturnType<typeof setTimeout> | null = null

function searchDocuments(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  if (!keyword.trim()) {
    docOptions.value = []
    return
  }
  docLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      const res = await documentApi.list({ keyword: keyword.trim(), type: 'word', size: 20 })
      docOptions.value = res.content || []
    } catch {} finally {
      docLoading.value = false
    }
  }, 300)
}

async function handleUpload(file: File) {
  try {
    const doc = await documentApi.upload(file)
    selectedDocId.value = doc.id
    docOptions.value = [doc]
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败')
  }
  return false
}

async function parseDoc() {
  if (!selectedDocId.value) return
  parsing.value = true
  try {
    const parsed = await contractApi.parseDocument(selectedDocId.value)
    form.contractNo = parsed.contractNo || ''
    form.contractType = parsed.contractType || ''
    form.partyA = parsed.partyA || ''
    form.partyB = parsed.partyB || null
    form.amount = parsed.amount || null
    form.signingDate = parsed.signingDate || ''
    form.effectiveDate = parsed.effectiveDate || ''
    form.expiryDate = parsed.expiryDate || ''
    parsedFromDoc.value = !!(parsed.contractNo || parsed.partyA || parsed.partyB)
    step.value = 2
  } catch {
    ElMessage.warning('文档解析失败，请手动填写')
    parsedFromDoc.value = false
    step.value = 2
  } finally {
    parsing.value = false
  }
}

async function saveContract() {
  saving.value = true
  try {
    await contractApi.create({
      documentId: selectedDocId.value,
      ...form
    })
    ElMessage.success('合同创建成功')
    visible.value = false
    emit('created')
  } catch {
    ElMessage.error('创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.step-content {
  min-height: 200px;
}

.step-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

.doc-select-area {
  margin-bottom: 24px;
}

.upload-alt {
  margin-top: 12px;
  text-align: center;
}

.upload-alt :deep(.el-button--primary.is-text) {
  font-size: 14px;
  font-weight: 500;
}

.contract-form {
  max-height: 400px;
  overflow-y: auto;
  padding-right: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
