// 尽早清除所有 Service Worker（防止 socket.io 请求被拦截）
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then(regs => {
    regs.forEach(reg => reg.unregister())
  })
  if ('caches' in window) {
    caches.keys().then(names => names.forEach(n => caches.delete(n)))
  }
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
// 设计令牌(全局 CSS 变量,Fluent 风格 PDF 编辑器必需,必须在 Element Plus 之后)
import '@/styles/pdf-tokens.css'
// Phase 13.21: 统一弹窗样式(.pdf-dialog 类)
import '@/styles/pdf-dialogs.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)
const pinia = createPinia()

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
