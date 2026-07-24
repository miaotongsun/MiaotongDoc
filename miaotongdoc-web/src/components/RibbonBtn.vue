<!--
  RibbonBtn.vue —— Ribbon 紧凑按钮(V3.2)

  规格(Adobe 标准):
    - 大按钮(图标在上 + 标签在下):50px 高,最小宽 56px
    - 正方形按钮(仅图标):34px
    - 图标 18px(SVG,1.6px stroke)
    - 标签 11px / 500
    - hover: surface-2 背景
    - active: primary-soft 背景 + primary 文字
-->
<template>
  <button
    class="ribbon-btn"
    :class="{
      'is-active': active,
      'is-disabled': disabled,
      'is-large': columns !== 2,
      'is-square': columns === 2,
    }"
    :disabled="disabled"
    :aria-label="label"
    :data-tooltip="shortcut ? `${label} (${shortcut})` : label"
    :title="shortcut ? `${label} (${shortcut})` : label"
    @click="onClick"
  >
    <PdfIcon :name="icon" :size="18" class="ribbon-btn-icon" />
    <span v-if="columns !== 2" class="ribbon-btn-label">{{ label }}</span>
  </button>
</template>

<script setup lang="ts">
import PdfIcon from './PdfIcon.vue'

defineProps<{
  /** 图标名(PdfIcon 已注册的 key) */
  icon: string
  label: string
  active?: boolean
  disabled?: boolean
  shortcut?: string
  columns?: 1 | 2
}>()

const emit = defineEmits<{
  (e: 'click', evt: MouseEvent): void
}>()

function onClick(evt: MouseEvent) {
  emit('click', evt)
}
</script>

<style scoped>
.ribbon-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 3px 6px 2px;
  border-radius: var(--radius-sm, 4px);
  color: var(--color-foreground-2, #475569);
  font-size: 11px;
  line-height: 1.1;
  transition: background var(--duration-fast, 120ms) var(--ease-out, ease),
    color var(--duration-fast, 120ms) var(--ease-out, ease),
    box-shadow var(--duration-fast, 120ms) var(--ease-out, ease),
    transform var(--duration-fast, 120ms) var(--ease-out, ease);
  user-select: none;
  position: relative;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  min-width: 0;
}

.ribbon-btn.is-large {
  min-width: 60px;
  width: auto;
  height: 50px;
  padding: 3px 8px 2px;
}

.ribbon-btn.is-square {
  width: 34px;
  height: 34px;
  padding: 0;
}

.ribbon-btn:hover:not(:disabled) {
  background: var(--color-surface-2, #F1F5F9);
  color: var(--color-foreground, #0F172A);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.ribbon-btn:active:not(:disabled) {
  background: var(--color-surface-3, #E2E8F0);
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.08);
  transform: translateY(0);
}

.ribbon-btn.is-active {
  background: var(--color-primary-soft, #EBF1FE);
  color: var(--color-primary, #3B6FE8);
  font-weight: 600;
  border-color: transparent;
  box-shadow: 0 1px 2px rgba(59, 111, 232, 0.2);
}
.ribbon-btn.is-active::before {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: 0;
  height: 3px;
  background: var(--color-primary, #3B6FE8);
  border-radius: 2px 2px 0 0;
}

/* Phase 13.39: hover 浅色短横线 + 点击(按下)由短变长动画 */
.ribbon-btn::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 0;
  height: 2px;
  background: var(--color-primary, #3B6FE8);
  border-radius: 2px 2px 0 0;
  transform: translateX(-50%);
  transition: width 220ms var(--ease-out, ease);
  pointer-events: none;
  opacity: 0;
}
.ribbon-btn:hover:not(:disabled):not(.is-active)::after {
  width: 40%;
  opacity: 0.35;
}
.ribbon-btn:active:not(:disabled):not(.is-active)::after {
  width: 90%;
  opacity: 0.6;
}

.ribbon-btn.is-active:hover {
  background: var(--color-primary-soft, #EBF1FE);
  filter: brightness(0.97);
}

.ribbon-btn.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ribbon-btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ribbon-btn-label {
  font-size: 11px;
  line-height: 1.1;
  white-space: nowrap;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  letter-spacing: 0.01em;
}
</style>