<!--
  PdfSecurityDialog.vue -- Phase 12.4 PDF 保护对话框

  2 个功能:
    - 加密:设置打开密码(后端已有 encrypt API)
    - 解密:输入密码移除保护(后端已有 decrypt API)

  完成后 emit('done', blob),由父组件触发下载
-->
<template>
  <Teleport to="body">
    <Transition name="pdf-sec-fade">
      <div v-if="open" class="pdf-sec-overlay" @click.self="$emit('close')">
        <div class="pdf-sec-dialog" role="dialog" aria-label="PDF 保护">
          <header class="pdf-sec-header">
            <h3>保护 PDF</h3>
            <button class="pdf-sec-close" aria-label="关闭" @click="$emit('close')">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </header>

          <div class="pdf-sec-tabs" role="tablist">
            <button
              v-for="t in tabs"
              :key="t.id"
              class="pdf-sec-tab"
              :class="{ 'is-active': activeTab === t.id }"
              role="tab"
              @click="activeTab = t.id"
            >
              {{ t.label }}
            </button>
          </div>

          <div class="pdf-sec-body">
            <div v-if="activeTab === 'encrypt'" class="pdf-sec-pane">
              <p class="pdf-sec-tip">设置打开密码,加密后的 PDF 需要密码才能打开。</p>
              <label class="pdf-sec-label">密码(至少 4 位)</label>
              <input
                v-model="encryptPassword"
                type="password"
                class="pdf-sec-input"
                placeholder="请输入密码"
                minlength="4"
              />
              <label class="pdf-sec-label">确认密码</label>
              <input
                v-model="encryptPassword2"
                type="password"
                class="pdf-sec-input"
                placeholder="再次输入密码"
              />
              <div class="pdf-sec-warning">
                ⚠ 请妥善保管密码,丢失后无法恢复。
              </div>
            </div>

            <div v-else-if="activeTab === 'decrypt'" class="pdf-sec-pane">
              <p class="pdf-sec-tip">输入原密码,移除 PDF 保护。</p>
              <label class="pdf-sec-label">原密码</label>
              <input
                v-model="decryptPassword"
                type="password"
                class="pdf-sec-input"
                placeholder="请输入原密码"
              />
              <div class="pdf-sec-info">
                ℹ 解密后的 PDF 不再需要密码即可打开。
              </div>
            </div>
          </div>

          <footer class="pdf-sec-footer">
            <button class="pdf-sec-cancel" @click="$emit('close')">取消</button>
            <button
              class="pdf-sec-confirm"
              :disabled="!canConfirm || saving"
              @click="onConfirm"
            >
              <span v-if="saving" class="pdf-sec-spinner"></span>
              <span v-else>{{ activeTab === 'encrypt' ? '加密并下载' : '解密并下载' }}</span>
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { pdfApi } from '@/api/pdf'

const props = defineProps<{
  open: boolean
  docId: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'done', blob: Blob, action: 'encrypt' | 'decrypt'): void
}>()

const tabs = [
  { id: 'encrypt' as const, label: '加密' },
  { id: 'decrypt' as const, label: '解密' },
]
const activeTab = ref<'encrypt' | 'decrypt'>('encrypt')

const encryptPassword = ref('')
const encryptPassword2 = ref('')
const decryptPassword = ref('')
const saving = ref(false)

const canConfirm = computed(() => {
  if (activeTab.value === 'encrypt') {
    return encryptPassword.value.length >= 4 && encryptPassword.value === encryptPassword2.value
  }
  return decryptPassword.value.length > 0
})

async function onConfirm() {
  saving.value = true
  try {
    let blob: Blob
    if (activeTab.value === 'encrypt') {
      if (encryptPassword.value !== encryptPassword2.value) {
        ElMessage.error('两次密码不一致')
        return
      }
      blob = await pdfApi.encrypt(props.docId, { password: encryptPassword.value })
      ElMessage.success('加密完成')
    } else {
      blob = await pdfApi.decrypt(props.docId, { password: decryptPassword.value })
      ElMessage.success('解密完成')
    }
    emit('done', blob, activeTab.value)
    emit('close')
    // 清空字段
    encryptPassword.value = ''
    encryptPassword2.value = ''
    decryptPassword.value = ''
  } catch (e: any) {
    console.error('[PdfSecurityDialog] failed:', e)
    ElMessage.error(e?.message || '操作失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.pdf-sec-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal, 1000);
}
.pdf-sec-dialog {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px -12px rgba(0, 0, 0, 0.25);
  width: 420px;
  max-width: 90vw;
  overflow: hidden;
}
.pdf-sec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}
.pdf-sec-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.pdf-sec-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pdf-sec-close:hover {
  background: #f5f7fa;
  color: #303133;
}
.pdf-sec-tabs {
  display: flex;
  border-bottom: 1px solid #ebeef5;
  padding: 0 20px;
}
.pdf-sec-tab {
  padding: 10px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
}
.pdf-sec-tab:hover { color: #409EFF; }
.pdf-sec-tab.is-active {
  color: #409EFF;
  border-bottom-color: #409EFF;
  font-weight: 500;
}
.pdf-sec-body {
  padding: 20px;
}
.pdf-sec-pane {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pdf-sec-tip {
  font-size: 13px;
  color: #606266;
  margin: 0 0 8px 0;
}
.pdf-sec-label {
  font-size: 13px;
  color: #303133;
  margin-top: 4px;
}
.pdf-sec-input {
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  transition: border-color 0.2s;
}
.pdf-sec-input:focus {
  outline: none;
  border-color: #409EFF;
  box-shadow: 0 0 0 2px #ecf5ff;
}
.pdf-sec-warning {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 4px;
  font-size: 12px;
}
.pdf-sec-info {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f4f4f5;
  color: #909399;
  border-radius: 4px;
  font-size: 12px;
}
.pdf-sec-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
.pdf-sec-cancel,
.pdf-sec-confirm {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.pdf-sec-cancel {
  background: #fff;
  border-color: #dcdfe6;
  color: #606266;
}
.pdf-sec-cancel:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}
.pdf-sec-confirm {
  background: #409EFF;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pdf-sec-confirm:hover:not(:disabled) {
  background: #66b1ff;
}
.pdf-sec-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pdf-sec-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pdf-sec-spin 0.8s linear infinite;
}
@keyframes pdf-sec-spin {
  to { transform: rotate(360deg); }
}
.pdf-sec-fade-enter-active,
.pdf-sec-fade-leave-active {
  transition: opacity 0.2s;
}
.pdf-sec-fade-enter-from,
.pdf-sec-fade-leave-to {
  opacity: 0;
}
</style>
