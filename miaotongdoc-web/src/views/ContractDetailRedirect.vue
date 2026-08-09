<template>
  <div v-loading="true" style="min-height: 200px"></div>
</template>

<script setup lang="ts">
/**
 * /contracts/:id 路由占位页 — 合同详情已重构为弹窗
 * 此处保留路由仅用于旧书签/外部链接兼容:
 *   1. 写入 sessionStorage 标记 "待打开的合同详情"
 *   2. 跳转到 /home(主页含合同管理 tab)
 *   3. Home.vue 监听标记,自动打开对应弹窗
 */
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const id = Number(route.params.id)
  if (id > 0) {
    sessionStorage.setItem('miaotong:contract:openOnHome', String(id))
  }
  router.replace('/home')
})
</script>