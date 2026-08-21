import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue') },
  { path: '/403', component: () => import('@/views/error/403.vue') },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '监控大屏', permissions: ['ppdv:device:list', 'ppdv:inspection:query'] } },
      { path: 'cockpit', name: 'cockpit', component: () => import('@/views/cockpit/index.vue'), meta: { title: '驾驶舱', permissions: ['ppdv:device:list', 'ppdv:device:query'] } },
      { path: 'planning', name: 'planning', component: () => import('@/views/planning/index.vue'), meta: { title: '作业规划', permissions: ['ppdv:device:list', 'ppdv:device:command'] } },
      { path: 'device', name: 'device', component: () => import('@/views/device/index.vue'), meta: { title: '设备管理', permissions: ['ppdv:device:list'] } },
      { path: 'report', name: 'report', component: () => import('@/views/report/index.vue'), meta: { title: '数据报表', permissions: ['ppdv:inspection:query'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  if (to.path === '/login') return next()
  if (!getToken()) return next('/login')
  if (to.path === '/403') return next()

  const userStore = useUserStore()
  try {
    await userStore.ensureInfo()
  } catch {
    return next('/login')
  }
  const permissions = to.meta.permissions || []
  if (!userStore.hasAllPermissions(permissions)) return next('/403')
  next()
})

export default router
