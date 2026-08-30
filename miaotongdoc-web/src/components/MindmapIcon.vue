<template>
  <svg
    class="mindmap-brand-icon"
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    aria-hidden="true"
  >
    <!-- 中心节点（实心圆） -->
    <circle cx="6" cy="12" r="3.2" fill="currentColor" />

    <!-- 3 条分支线（向右辐射，弧线） -->
    <g stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round">
      <path d="M 9 10 Q 14 7 16.5 5.5" />
      <path d="M 9 12 L 16.5 12" />
      <path d="M 9 14 Q 14 17 16.5 18.5" />
    </g>

    <!-- 3 个叶节点（空心圆环） -->
    <g fill="none" stroke="currentColor" stroke-width="1.6">
      <circle cx="19.5" cy="5.5" r="2.4" />
      <circle cx="19.5" cy="12" r="2.4" />
      <circle cx="19.5" cy="18.5" r="2.4" />
    </g>
  </svg>
</template>

<script setup lang="ts">
/**
 * MindmapIcon —— 标志性脑图 SVG 图标（v5，2026-08-18）
 *
 * 参考橙色脑图：1 中心实心圆 + 3 条弧形分支 + 3 个空心叶圆。
 * 颜色：currentColor（默认琥珀橙 #F59E0B）。
 */
withDefaults(defineProps<{
  size?: number | string
}>(), {
  size: 16,
})
</script>

<style scoped>
.mindmap-brand-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>
