import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

// 检查 token 是否过期
function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    const exp = payload.exp * 1000 // JWT exp 是秒，转为毫秒
    return Date.now() >= exp
  } catch {
    return true // 解析失败视为过期
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/editor/:id',
      name: 'DocEditor',
      component: () => import('@/views/DocEditor.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/contracts/:id',
      name: 'ContractDetail',
      component: () => import('@/views/ContractDetail.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/signing',
      name: 'SigningTask',
      component: () => import('@/views/SigningTask.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/activity',
      name: 'ActivityFeed',
      component: () => import('@/views/ActivityFeed.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'Admin',
      component: () => import('@/views/Admin.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = sessionStorage.getItem('token')

  if (to.meta.requiresAuth) {
    if (!token) {
      next('/login')
      return
    }
    if (isTokenExpired(token)) {
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('userId')
      sessionStorage.removeItem('role')
      ElMessage.warning('登录已过期，请重新登录')
      next('/login')
      return
    }
  }

  if (to.path === '/login' && token) {
    if (!isTokenExpired(token)) {
      next('/home')
      return
    }
    // token 过期，清除并允许访问登录页
    sessionStorage.removeItem('token')
  }

  // 动态标题(改善 UX,用户能在浏览器标签看到当前页)
  const routeTitleMap: Record<string, string> = {
    Login: '登录',
    Home: '我的文档',
    DocEditor: '文档编辑器',
    ContractDetail: '合同详情',
    SigningTask: '签署任务',
    ActivityFeed: '动态',
    Admin: '管理后台',
  }
  const baseName = 'MiaotongDoc - 妙同文档'
  const subTitle = routeTitleMap[to.name as string] || ''
  document.title = subTitle ? `${subTitle} · ${baseName}` : baseName

  next()
})

export default router
