<!--
  RibbonGroup.vue —— Ribbon 内的分组容器(Adobe 风格)

  规格:
    - 高度 64px(填满 toolbar 72px 减去上下 4px padding)
    - label 在底部(11px / 500)
    - 按钮居中在 label 上方
    - 分隔条 1px 垂直(右侧 border-right)
-->
<template>
  <div class="ribbon-group" :class="{ 'is-columns-2': columns === 2 }">
    <div v-if="label" class="ribbon-group-label">{{ label }}</div>
    <div class="ribbon-group-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label?: string
  columns?: 1 | 2
}>()
</script>

<style scoped>
.ribbon-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding: 4px 6px 2px;
  border-right: 1px solid var(--color-divider);
  position: relative;
  flex-shrink: 0;
  min-width: 0;
  gap: 0;
}

.ribbon-group:last-child {
  border-right: none;
}

.ribbon-group-label {
  font-size: 11px;
  color: var(--color-foreground-3);
  font-weight: 500;
  letter-spacing: 0.02em;
  user-select: none;
  margin-top: 2px;
  line-height: 1;
  white-space: nowrap;
}

.ribbon-group-content {
  display: flex;
  gap: 2px;
  flex: 1;
  align-items: center;
  width: 100%;
  justify-content: center;
}

.ribbon-group.is-columns-2 .ribbon-group-content {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 2px;
  align-content: center;
}
</style>