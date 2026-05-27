<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { loadTheme } from '@/utils/theme'

onMounted(() => {
  loadTheme()
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  width: 100%;
  height: 100vh;
}

/* 全局主题变量 */
:root {
  --el-color-primary: #409eff;
  --el-color-primary-light-3: #66b1ff;
  --el-color-primary-light-5: #8cc5ff;
  --el-color-primary-light-7: #b3d8ff;
  --el-color-primary-light-8: #d9ecff;
  --el-color-primary-light-9: #ecf5ff;
  --el-color-primary-dark-2: #337ecc;
  --primary-gradient: linear-gradient(135deg, #409eff, #66b1ff);
  --card-accent: #409eff;
  --hover-bg: #ecf5ff;
  --active-bg: #e6f0ff;
}

/* 全局按钮样式 */
.el-button--primary:not(.is-text):not(.is-link) {
  background: var(--primary-gradient) !important;
  border: none !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transition: all 0.3s;
}

.el-button--primary:not(.is-text):not(.is-link):hover {
  opacity: 0.9;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
}

/* 文字按钮：确保可读性 */
.el-button--primary.is-text {
  color: var(--el-color-primary) !important;
  background: transparent !important;
  box-shadow: none !important;
}

.el-button--primary.is-text:hover {
  color: var(--el-color-primary-light-3) !important;
  background: var(--hover-bg) !important;
  opacity: 1 !important;
  transform: none !important;
  box-shadow: none !important;
}

/* 标签页 */
.el-tabs__active-bar {
  background: var(--primary-gradient) !important;
}

/* 分页 */
.el-pagination.is-background .el-pager li:not(.disabled).active {
  background: var(--primary-gradient) !important;
}

/* 开关 */
.el-switch.is-checked .el-switch__core {
  background: var(--primary-gradient) !important;
}

/* 加载图标 */
.el-loading-spinner .circular .stroke {
  stroke: var(--el-color-primary) !important;
}
</style>
