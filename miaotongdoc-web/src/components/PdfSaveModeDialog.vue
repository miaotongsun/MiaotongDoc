<!--
  PdfSaveModeDialog.vue -- Phase 13.11 编辑保存模式选择

  编辑模式下点"保存"后弹出,让用户选择:
    - 覆盖当前文档:创建新版本,留在当前编辑器
    - 另存为新文档:输入标题,复制为新文档,跳转新文档

  参考 Adobe Acrobat "另存为" 对话框
-->
<template>
  <el-dialog
    :model-value="open"
    title="保存编辑"
    width="440px"
    :close-on-click-modal="false"
    @update:model-value="$emit('close')"
  >
    <div class="pdf-save-mode">
      <div class="pdf-save-hint">检测到 {{ editCount }} 处文字修改,请选择保存方式:</div>

      <div
        class="pdf-save-option"
        :class="{ 'is-active': mode === 'overwrite' }"
        @click="mode = 'overwrite'"
      >
        <div class="pdf-save-option-radio">
          <input type="radio" :checked="mode === 'overwrite'" readonly />
        </div>
        <div class="pdf-save-option-body">
          <div class="pdf-save-option-title">
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            覆盖当前文档
          </div>
          <div class="pdf-save-option-desc">生成新版本,原文档历史保留,留在当前编辑器</div>
        </div>
      </div>

      <div
        class="pdf-save-option"
        :class="{ 'is-active': mode === 'new' }"
        @click="mode = 'new'"
      >
        <div class="pdf-save-option-radio">
          <input type="radio" :checked="mode === 'new'" readonly />
        </div>
        <div class="pdf-save-option-body">
          <div class="pdf-save-option-title">
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
            另存为新文档
          </div>
          <div class="pdf-save-option-desc">复制为新文档,原文档不变,跳转到新文档编辑器</div>
          <input
            v-if="mode === 'new'"
            v-model="newTitle"
            type="text"
            class="pdf-save-title-input"
            :placeholder="defaultTitle"
            maxlength="100"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <button class="pdf-save-cancel" @click="$emit('close')">取消</button>
      <button class="pdf-save-confirm" :disabled="saving" @click="onConfirm">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  open: boolean
  editCount: number
  defaultTitle: string
  saving?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', mode: 'overwrite' | 'new', newTitle?: string): void
}>()

const mode = ref<'overwrite' | 'new'>('overwrite')
const newTitle = ref('')

function onConfirm() {
  emit('confirm', mode.value, mode.value === 'new' ? (newTitle.value || props.defaultTitle) : undefined)
}
</script>

<style scoped>
.pdf-save-mode {
  padding: 0 4px;
}
.pdf-save-hint {
  margin-bottom: 16px;
  padding: 10px 14px;
  background: var(--color-primary-soft, #ecf5ff);
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-foreground-2, #606266);
}
.pdf-save-option {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 2px solid var(--color-border, #e4e7ed);
  border-radius: 10px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 150ms ease;
}
.pdf-save-option:hover {
  border-color: var(--color-primary, #409eff);
}
.pdf-save-option.is-active {
  border-color: var(--color-primary, #409eff);
  background: var(--color-primary-soft, #ecf5ff);
}
.pdf-save-option-radio {
  padding-top: 2px;
}
.pdf-save-option-radio input {
  width: 16px;
  height: 16px;
  accent-color: var(--color-primary, #409eff);
  cursor: pointer;
}
.pdf-save-option-body {
  flex: 1;
}
.pdf-save-option-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-foreground, #303133);
}
.pdf-save-option-title svg {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pdf-save-option-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-foreground-3, #909399);
  line-height: 1.5;
}
.pdf-save-title-input {
  margin-top: 10px;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.pdf-save-title-input:focus {
  border-color: var(--color-primary, #409eff);
}
.pdf-save-cancel {
  padding: 8px 20px;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 6px;
  background: var(--color-surface, #fff);
  color: var(--color-foreground-2, #606266);
  font-size: 13px;
  cursor: pointer;
  margin-right: 8px;
}
.pdf-save-cancel:hover {
  background: var(--color-surface-2, #f5f7fa);
}
.pdf-save-confirm {
  padding: 8px 24px;
  border: none;
  border-radius: 6px;
  background: var(--color-primary, #409eff);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.pdf-save-confirm:hover:not(:disabled) {
  background: var(--color-primary-hover, #66b1ff);
}
.pdf-save-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>