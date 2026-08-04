import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      component: () => import('@/views/login.vue'),
    },
    {
      path: '/',
      component: () => import('@/views/layout/index.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: () => import('@/views/dashboard/index.vue') },
        { path: 'map-plan',  component: () => import('@/views/map-plan/index.vue') },
        { path: 'report',    component: () => import('@/views/report/index.vue') },
        { path: 'device',    component: () => import('@/views/device/index.vue') },
      ]
    }
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  if (token) {
    // 已登录：访问登录页 → 重定向到仪表盘
    if (to.path === '/login') {
      return '/dashboard'
    }
    return true
  }
  // 未登录：访问登录页 → 放行；其他页面 → 强制跳登录
  if (to.path === '/login') {
    return true
  }
  return '/login'
})

export default router
