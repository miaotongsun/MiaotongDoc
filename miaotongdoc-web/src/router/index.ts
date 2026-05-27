import { createRouter, createWebHistory } from 'vue-router'

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

  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/home')
  } else {
    next()
  }
})

export default router
