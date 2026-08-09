<template>
  <el-dialog v-model="visible" title="编辑合同" width="700px" :close-on-click-modal="false">
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
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveContract">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { contractApi, type Contract } from '@/api/contract'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ saved: [] }>()

const props = defineProps<{
  contract: Contract | null
}>()

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

const saving = ref(false)

// 监听 visible 或 contract 变化,初始化表单
watch([visible, () => props.contract], ([v, c]) => {
  if (v && c) {
    form.contractNo = c.contractNo || ''
    form.contractType = c.contractType || ''
    form.partyA = c.partyA || ''
    form.partyB = c.partyB || null
    form.amount = c.amount ?? null
    form.signingDate = c.signingDate || ''
    form.effectiveDate = c.effectiveDate || ''
    form.expiryDate = c.expiryDate || ''
    form.remarks = c.remarks || ''
  }
}, { immediate: true })

async function saveContract() {
  if (!props.contract) return
  saving.value = true
  try {
    await contractApi.update(props.contract.id, { ...form })
    ElMessage.success('合同已更新')
    visible.value = false
    emit('saved')
  } catch {
    ElMessage.error('更新失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.contract-form {
  max-height: 500px;
  overflow-y: auto;
  padding-right: 12px;
}
</style>